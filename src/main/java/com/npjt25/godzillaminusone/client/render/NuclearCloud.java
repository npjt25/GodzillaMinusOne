package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The nuclear explosion (Godzilla's atomic breath, the nuclear bomb) as a blocky 3D cloud, in Minecraft's own
 * style: a fireball that swells, rises and cools into the rolling cap of a mushroom cloud on a column of smoke,
 * with a condensation ring and a base surge of dust along the ground. Each part is a noisy shape (an ellipsoid,
 * tori, a lathe) filled with {@link #VOXEL}-block cubes a few times a second; only their outer faces are drawn,
 * shaded like blocks. tools/preview_cloud.py renders the same cloud offline.
 *
 * <p>It is a client-side effect (started by the ATOMIC_BLAST packet) rather than an entity, so it is seen from as
 * far away as the packet reaches, not just within entity tracking range.
 */
public final class NuclearCloud {
    private static final ResourceLocation SMOKE = GodzillaMinusOne.id("textures/effect/nuclear_smoke.png");
    private static final ResourceLocation FIRE = GodzillaMinusOne.id("textures/effect/nuclear_fire.png");
    /** Seconds the cloud hangs in the sky (it fades out over the last 14). */
    private static final float LIFE = 55.0F;
    private static final int LIGHT = LightTexture.pack(0, 15);
    /** The size of the cloud's cubes, in blocks. */
    private static final float VOXEL = 4.0F;
    /** The cloud's shape is rebuilt every this many ticks. */
    private static final int REBUILD_TICKS = 2;
    /** The texture repeats every this many blocks across the cloud. */
    private static final float TEXTURE_SPAN = 32.0F;

    private static final int EMPTY = 0;
    private static final int HEAD = 1;
    private static final int TORUS = 2;
    private static final int STEM = 3;
    private static final int BASE = 4;
    private static final int RING = 5;

    /** Face directions: +y, -y, +x, -x, +z, -z, with the brightness blocks give them. */
    private static final int[][] DIRS = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final float[] SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.65F, 0.65F};

    private static final float[][] FIRE_KEYS = {
            {0.0F, 1.0F, 1.0F, 1.0F}, {0.5F, 1.0F, 0.92F, 0.65F}, {1.5F, 1.0F, 0.62F, 0.25F}, {4.0F, 0.95F, 0.35F, 0.08F},
            {8.0F, 0.55F, 0.13F, 0.03F}, {14.0F, 0.25F, 0.05F, 0.01F}, {20.0F, 0.0F, 0.0F, 0.0F}};

    private static final NoiseTable HEAD_NOISE = new NoiseTable(36, 20, NuclearCloud::sphereNoise);
    private static final NoiseTable TORUS_NOISE = new NoiseTable(40, 16, NuclearCloud::torusNoise);
    private static final NoiseTable STEM_NOISE = new NoiseTable(28, 18, NuclearCloud::stemNoise);
    private static final NoiseTable RING_NOISE = new NoiseTable(40, 8, NuclearCloud::torusNoise);
    private static final NoiseTable BASE_NOISE = new NoiseTable(48, 10, NuclearCloud::torusNoise);

    private static final List<NuclearCloud> CLOUDS = new ArrayList<>();

    private final Vec3 pos;
    private final float yaw;
    private final boolean airburst;
    private int age;
    /** The outer faces of the cloud's cubes, packed by {@link #face}. */
    private final IntArrayList faces = new IntArrayList();
    private byte[] cells = new byte[0];

    private enum Pass {
        SMOKE, FIRE
    }

    private NuclearCloud(Vec3 pos, float yaw, boolean airburst) {
        this.pos = pos;
        this.yaw = yaw;
        this.airburst = airburst;
        rebuild();
    }

    public static void spawn(Vec3 pos, float yaw, boolean airburst) {
        CLOUDS.add(new NuclearCloud(pos, yaw, airburst));
    }

    public static void tick() {
        for (NuclearCloud cloud : CLOUDS) {
            cloud.age++;
            if (cloud.age % REBUILD_TICKS == 0) {
                cloud.rebuild();
            }
        }
        CLOUDS.removeIf(c -> c.age > LIFE * 20.0F);
    }

    public static void clear() {
        CLOUDS.clear();
    }

    /** Draws every cloud; called after the level's particles are drawn. */
    public static void renderAll(PoseStack poseStack, Camera camera, float partialTick) {
        if (CLOUDS.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        for (Pass pass : Pass.values()) {
            RenderType type = pass == Pass.SMOKE ? RenderType.entityTranslucent(SMOKE) : RenderType.eyes(FIRE);
            VertexConsumer buffer = buffers.getBuffer(type);
            for (NuclearCloud cloud : CLOUDS) {
                poseStack.pushPose();
                poseStack.translate(cloud.pos.x - cam.x, cloud.pos.y - cam.y, cloud.pos.z - cam.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(cloud.yaw));
                cloud.draw(pass, buffer, poseStack, (cloud.age + partialTick) / 20.0F);
                poseStack.popPose();
            }
            buffers.endBatch(type);
        }
    }

    // ------------------------------------------------------------------ shape of the cloud over time (seconds)

    private static float smooth(float e0, float e1, float x) {
        float f = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return f * f * (3.0F - 2.0F * f);
    }

    /** The head (fireball, then the cap of the mushroom): horizontal radius, vertical radius, height of its centre. */
    private float[] head(float t) {
        float a0 = 2.0F + 26.0F * (1.0F - (float) Math.exp(-2.2F * t));
        float a = a0 + 32.0F * (1.0F - (float) Math.exp(-t / 10.0F));
        float b = a * (1.0F - 0.48F * smooth(1.5F, 12.0F, t));
        float h = this.airburst ? 3.0F * t : a0 * 0.8F + 92.0F * (1.0F - (float) Math.exp(-t / 7.5F));
        return new float[]{a, b, h};
    }

    private static float[] fireColor(float t) {
        for (int i = 1; i < FIRE_KEYS.length; i++) {
            float[] k0 = FIRE_KEYS[i - 1];
            float[] k1 = FIRE_KEYS[i];
            if (t <= k1[0]) {
                float f = (t - k0[0]) / (k1[0] - k0[0]);
                return new float[]{Mth.lerp(f, k0[1], k1[1]), Mth.lerp(f, k0[2], k1[2]), Mth.lerp(f, k0[3], k1[3])};
            }
        }
        return new float[]{0.0F, 0.0F, 0.0F};
    }

    private static boolean burning(float[] fire) {
        return Math.max(fire[0], Math.max(fire[1], fire[2])) > 0.01F;
    }

    /** How opaque each part of the cloud is at time t (indexed by material). */
    private float[] alphas(float t) {
        float fade = 1.0F - smooth(LIFE - 14.0F, LIFE, t);
        float[] alpha = new float[6];
        alpha[HEAD] = smooth(1.0F, 4.0F, t) * fade;
        if (this.airburst) {
            alpha[RING] = 0.6F * smooth(0.5F, 2.5F, t) * (1.0F - smooth(8.0F, 14.0F, t)) * fade;
            return alpha;
        }
        alpha[TORUS] = smooth(2.5F, 7.0F, t) * fade;
        alpha[STEM] = smooth(0.6F, 3.0F, t) * fade;
        alpha[RING] = 0.6F * smooth(2.5F, 4.5F, t) * (1.0F - smooth(8.0F, 13.0F, t)) * fade;
        alpha[BASE] = smooth(0.3F, 1.5F, t) * (1.0F - smooth(25.0F, 45.0F, t)) * fade;
        return alpha;
    }

    private static float stemWidth(float t) {
        return 2.5F + 11.0F * (1.0F - (float) Math.exp(-t / 5.0F));
    }

    // ------------------------------------------------------------------ building the cubes

    /** The grid the cloud's cells live in (cell indices relative to ground zero). */
    private int minX;
    private int minZ;
    private int sizeX;
    private int sizeY;
    private int sizeZ;

    private void rebuild() {
        float t = this.age / 20.0F;
        float[] alpha = alphas(t);
        boolean burning = burning(fireColor(t));
        float[] head = head(t);
        float a = head[0];
        float b = head[1];
        float h = head[2];
        float top = h - b * 0.45F;
        float w = stemWidth(t);
        float baseRadius = 8.0F + 62.0F * (1.0F - (float) Math.exp(-t / 6.0F));
        float baseHeight = 3.0F + 7.0F * (1.0F - (float) Math.exp(-t / 4.0F));
        float ringMajor = this.airburst ? a * 1.3F + 4.0F * t : w * 2.6F + 8.0F + 10.0F * smooth(2.0F, 10.0F, t);

        // the grid: wide enough for the widest part, tall enough for the top of the cap
        float half = Math.max(a * 1.2F, Math.max(baseRadius * 1.1F, ringMajor + 16.0F));
        int n = Mth.ceil(half / VOXEL) + 1;
        this.minX = -n;
        this.minZ = -n;
        this.sizeX = 2 * n;
        this.sizeZ = 2 * n;
        this.sizeY = Mth.ceil((h + b * 1.2F + 4.0F) / VOXEL) + 1;
        int total = this.sizeX * this.sizeY * this.sizeZ;
        if (this.cells.length < total) {
            this.cells = new byte[total];
        } else {
            Arrays.fill(this.cells, 0, total, (byte) EMPTY);
        }

        if (alpha[HEAD] > 0.01F || burning) {
            float amp = 0.10F + 0.08F * smooth(1.0F, 8.0F, t);
            float flat = this.airburst ? 1.0F : 1.0F - 0.45F * smooth(2.0F, 10.0F, t);
            float solid = 0.94F + 0.06F * smooth(1.5F, 4.0F, t);
            fillHead(a, b, h, amp, flat, solid);
        }
        if (this.airburst) {
            if (alpha[RING] > 0.01F) {
                fillTorus(RING, RING_NOISE, h, ringMajor, 3.0F, 0.5F, 0.3F, 2.2F);
            }
        } else {
            if (alpha[TORUS] > 0.01F) {
                fillTorus(TORUS, TORUS_NOISE, h - b * 0.35F, a * 0.78F, b * 0.42F, 0.85F, 0.2F, 1.0F);
            }
            if (alpha[STEM] > 0.01F || burning) {
                fillStem(top, w);
            }
            if (alpha[BASE] > 0.01F) {
                fillTorus(BASE, BASE_NOISE, baseHeight * 0.4F, baseRadius * 0.5F, baseHeight, 1.0F, 0.3F, baseRadius * 0.5F / baseHeight);
            }
            if (alpha[RING] > 0.01F) {
                fillTorus(RING, RING_NOISE, top * 0.55F, ringMajor, 4.0F, 0.35F, 0.45F, 2.2F);
            }
        }

        // keep only the faces that look out of the cloud
        this.faces.clear();
        for (int x = 0; x < this.sizeX; x++) {
            for (int y = 0; y < this.sizeY; y++) {
                for (int z = 0; z < this.sizeZ; z++) {
                    int material = this.cells[index(x, y, z)];
                    if (material == EMPTY) {
                        continue;
                    }
                    for (int d = 0; d < DIRS.length; d++) {
                        if (cell(x + DIRS[d][0], y + DIRS[d][1], z + DIRS[d][2]) == EMPTY) {
                            this.faces.add(face(x, y, z, d, material));
                        }
                    }
                }
            }
        }
    }

    private int index(int x, int y, int z) {
        return (x * this.sizeY + y) * this.sizeZ + z;
    }

    private int cell(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.sizeX || y >= this.sizeY || z >= this.sizeZ) {
            return EMPTY;
        }
        return this.cells[index(x, y, z)];
    }

    /** Marks a cell (unless an earlier part of the cloud already has it). */
    private void put(int x, int y, int z, int material) {
        if (x >= 0 && y >= 0 && z >= 0 && x < this.sizeX && y < this.sizeY && z < this.sizeZ && this.cells[index(x, y, z)] == EMPTY) {
            this.cells[index(x, y, z)] = (byte) material;
        }
    }

    private static int face(int x, int y, int z, int dir, int material) {
        return x | y << 8 | z << 16 | dir << 24 | material << 28;
    }

    private static float centre(int cell) {
        return (cell + 0.5F) * VOXEL;
    }

    /** The fireball / cap: a noisy ellipsoid, flattened underneath as it becomes the cap of the mushroom. */
    private void fillHead(float a, float b, float h, float amp, float flat, float solid) {
        float max = solid * (1.0F + amp);
        int nx = Mth.ceil(a * max / VOXEL) + 1;
        int y0 = Math.max(0, Mth.floor((h - b * flat * max) / VOXEL));
        int y1 = Math.min(this.sizeY - 1, Mth.ceil((h + b * max) / VOXEL));
        for (int i = -nx; i < nx; i++) {
            for (int k = -nx; k < nx; k++) {
                float qx = centre(i) / a;
                float qz = centre(k) / a;
                for (int j = y0; j <= y1; j++) {
                    float dy = centre(j) - h;
                    float qy = dy / (dy < 0.0F ? b * flat : b);
                    float r = Mth.sqrt(qx * qx + qy * qy + qz * qz);
                    if (r > max) {
                        continue;
                    }
                    if (r > solid * (1.0F - amp)) {
                        float u = (float) (Mth.atan2(qz, qx) / Mth.TWO_PI);
                        float v = (float) (Math.asin(Mth.clamp(qy / Math.max(r, 1.0E-6F), -1.0F, 1.0F)) / Math.PI) + 0.5F;
                        if (r > solid * (1.0F + amp * HEAD_NOISE.at(u, v))) {
                            continue;
                        }
                    }
                    put(i - this.minX, j, k - this.minZ, HEAD);
                }
            }
        }
    }

    /** A noisy torus around the vertical axis; {@code wide} stretches its tube sideways (a flat skirt). */
    private void fillTorus(int material, NoiseTable noise, float cy, float major, float tube, float squash, float amp, float wide) {
        float max = tube * (1.0F + amp);
        int nx = Mth.ceil((major + wide * max) / VOXEL) + 1;
        int y0 = Math.max(0, Mth.floor((cy - squash * max) / VOXEL));
        int y1 = Math.min(this.sizeY - 1, Mth.ceil((cy + squash * max) / VOXEL));
        for (int i = -nx; i < nx; i++) {
            for (int k = -nx; k < nx; k++) {
                float x = centre(i);
                float z = centre(k);
                float dr = (Mth.sqrt(x * x + z * z) - major) / wide;
                if (Math.abs(dr) > max) {
                    continue;
                }
                float u = (float) (Mth.atan2(z, x) / Mth.TWO_PI);
                for (int j = y0; j <= y1; j++) {
                    float dy = (centre(j) - cy) / squash;
                    float r = Mth.sqrt(dr * dr + dy * dy);
                    if (r > max) {
                        continue;
                    }
                    float v = (float) (Mth.atan2(dy, dr) / Mth.TWO_PI);
                    if (r <= tube * (1.0F + amp * noise.at(u, v))) {
                        put(i - this.minX, j, k - this.minZ, material);
                    }
                }
            }
        }
    }

    /** The stem: wide at the ground, narrow in the middle, flaring into the cap. */
    private void fillStem(float top, float width) {
        if (top <= 0.0F) {
            return;
        }
        int nx = Mth.ceil(width * 2.4F / VOXEL) + 1;
        int y1 = Math.min(this.sizeY - 1, Mth.ceil(top / VOXEL));
        for (int j = 0; j <= y1; j++) {
            float y = centre(j);
            if (y > top) {
                break;
            }
            float hh = y / top;
            float low = 1.0F - hh;
            float radius = width * (0.5F + 1.5F * low * low * low * low * low + hh * hh * hh);
            for (int i = -nx; i < nx; i++) {
                for (int k = -nx; k < nx; k++) {
                    float x = centre(i);
                    float z = centre(k);
                    float rho = Mth.sqrt(x * x + z * z);
                    if (rho > radius * 1.16F) {
                        continue;
                    }
                    float u = (float) (Mth.atan2(z, x) / Mth.TWO_PI);
                    if (rho <= radius * (1.0F + 0.16F * STEM_NOISE.at(u, hh))) {
                        put(i - this.minX, j, k - this.minZ, STEM);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private void draw(Pass pass, VertexConsumer buffer, PoseStack poseStack, float t) {
        if (this.faces.isEmpty()) {
            return;
        }
        float[] alpha = alphas(t);
        float[] fire = fireColor(t);
        if (pass == Pass.FIRE && !burning(fire)) {
            return;
        }
        float warm = smooth(3.0F, 20.0F, t);
        float[][] colour = new float[6][];
        colour[HEAD] = new float[]{Mth.lerp(warm, 0.50F, 0.68F), Mth.lerp(warm, 0.44F, 0.64F), Mth.lerp(warm, 0.38F, 0.60F)};
        colour[TORUS] = new float[]{colour[HEAD][0] * 0.92F, colour[HEAD][1] * 0.92F, colour[HEAD][2] * 0.92F};
        colour[STEM] = new float[]{0.48F, 0.42F, 0.37F};
        colour[BASE] = new float[]{0.62F, 0.56F, 0.49F};
        colour[RING] = new float[]{0.95F, 0.95F, 0.97F};
        float under = smooth(2.0F, 5.0F, t);
        float[] head = head(t);
        float top = head[2] - head[1] * 0.45F;

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Vector3f p = new Vector3f();
        Vector3f nrm = new Vector3f();
        for (int f = 0; f < this.faces.size(); f++) {
            int packed = this.faces.getInt(f);
            int x = packed & 0xFF;
            int y = packed >> 8 & 0xFF;
            int z = packed >> 16 & 0xFF;
            int dir = packed >> 24 & 0xF;
            int material = packed >>> 28;
            float r;
            float g;
            float b;
            float a;
            if (pass == Pass.SMOKE) {
                a = alpha[material];
                if (a <= 0.01F) {
                    continue;
                }
                float shade = SHADE[dir];
                r = colour[material][0] * shade;
                g = colour[material][1] * shade;
                b = colour[material][2] * shade;
            } else {
                // at first the whole fireball burns; later only the underside of the cap, and the foot of the stem
                float weight;
                if (material == HEAD) {
                    weight = (1.0F - under) + under * Mth.clamp(-DIRS[dir][1] * 1.3F + 0.1F, 0.0F, 1.0F);
                } else if (material == STEM) {
                    float hh = Mth.clamp(centre(y) / Math.max(top, 1.0E-3F), 0.0F, 1.0F);
                    weight = 0.8F * (1.0F - hh) * (1.0F - hh);
                } else {
                    continue;
                }
                if (weight <= 0.01F) {
                    continue;
                }
                r = fire[0] * weight;
                g = fire[1] * weight;
                b = fire[2] * weight;
                a = 1.0F;
            }
            quad(buffer, pose, normal, x + this.minX, y, z + this.minZ, dir, r, g, b, a, p, nrm);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose, Matrix3f normal, int cx, int cy, int cz, int dir,
                             float r, float g, float b, float a, Vector3f p, Vector3f nrm) {
        float x0 = cx * VOXEL;
        float y0 = cy * VOXEL;
        float z0 = cz * VOXEL;
        float x1 = x0 + VOXEL;
        float y1 = y0 + VOXEL;
        float z1 = z0 + VOXEL;
        normal.transform(DIRS[dir][0], DIRS[dir][1], DIRS[dir][2], nrm);
        switch (dir) {
            case 0 -> {
                vertex(buffer, pose, x0, y1, z0, x0, z0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x0, y1, z1, x0, z1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y1, z1, x1, z1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y1, z0, x1, z0, r, g, b, a, nrm, p);
            }
            case 1 -> {
                vertex(buffer, pose, x0, y0, z0, x0, z0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y0, z0, x1, z0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y0, z1, x1, z1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x0, y0, z1, x0, z1, r, g, b, a, nrm, p);
            }
            case 2 -> {
                vertex(buffer, pose, x1, y0, z0, y0, z0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y1, z0, y1, z0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y1, z1, y1, z1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y0, z1, y0, z1, r, g, b, a, nrm, p);
            }
            case 3 -> {
                vertex(buffer, pose, x0, y0, z0, y0, z0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x0, y0, z1, y0, z1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x0, y1, z1, y1, z1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x0, y1, z0, y1, z0, r, g, b, a, nrm, p);
            }
            case 4 -> {
                vertex(buffer, pose, x0, y0, z1, x0, y0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y0, z1, x1, y0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y1, z1, x1, y1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x0, y1, z1, x0, y1, r, g, b, a, nrm, p);
            }
            default -> {
                vertex(buffer, pose, x0, y0, z0, x0, y0, r, g, b, a, nrm, p);
                vertex(buffer, pose, x0, y1, z0, x0, y1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y1, z0, x1, y1, r, g, b, a, nrm, p);
                vertex(buffer, pose, x1, y0, z0, x1, y0, r, g, b, a, nrm, p);
            }
        }
    }

    /** One corner; the texture is laid across the cloud by position, so neighbouring cubes continue it. */
    private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, float s, float t,
                               float r, float g, float b, float a, Vector3f nrm, Vector3f p) {
        pose.transformPosition(x, y, z, p);
        buffer.vertex(p.x, p.y, p.z, r, g, b, a, s / TEXTURE_SPAN, t / TEXTURE_SPAN, OverlayTexture.NO_OVERLAY, LIGHT,
                nrm.x, nrm.y, nrm.z);
    }

    // ------------------------------------------------------------------ noise

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0F * 2.0F - 1.0F;
    }

    private static float valueNoise(float x, float y, float z) {
        int xi = Mth.floor(x);
        int yi = Mth.floor(y);
        int zi = Mth.floor(z);
        float fx = x - xi;
        float fy = y - yi;
        float fz = z - zi;
        fx = fx * fx * (3.0F - 2.0F * fx);
        fy = fy * fy * (3.0F - 2.0F * fy);
        fz = fz * fz * (3.0F - 2.0F * fz);
        float x00 = Mth.lerp(fx, hash(xi, yi, zi), hash(xi + 1, yi, zi));
        float x10 = Mth.lerp(fx, hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi));
        float x01 = Mth.lerp(fx, hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1));
        float x11 = Mth.lerp(fx, hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1));
        return Mth.lerp(fz, Mth.lerp(fy, x00, x10), Mth.lerp(fy, x01, x11));
    }

    private static float fbm(float x, float y, float z) {
        return valueNoise(x, y, z) * 0.6F + valueNoise(x * 2.1F + 17.0F, y * 2.1F, z * 2.1F) * 0.28F
                + valueNoise(x * 4.3F, y * 4.3F + 31.0F, z * 4.3F) * 0.12F;
    }

    private static float sphereNoise(float u, float v) {
        float th = u * Mth.TWO_PI;
        float ph = (v - 0.5F) * Mth.PI;
        return fbm(Mth.cos(ph) * Mth.cos(th) * 2.2F + 5.0F, Mth.sin(ph) * 2.2F, Mth.cos(ph) * Mth.sin(th) * 2.2F);
    }

    private static float torusNoise(float u, float v) {
        float th = u * Mth.TWO_PI;
        float ph = v * Mth.TWO_PI;
        return fbm(Mth.cos(th) * 3.0F + 11.0F, Mth.sin(th) * 3.0F, (Mth.cos(ph) + Mth.sin(ph)) * 0.8F);
    }

    private static float stemNoise(float u, float v) {
        float th = u * Mth.TWO_PI;
        return fbm(Mth.cos(th) * 1.5F, v * 4.0F, Mth.sin(th) * 1.5F + 23.0F);
    }

    /** Noise sampled once over a shape's surface parameters (u wraps around, v doesn't), looked up bilinearly. */
    private static final class NoiseTable {
        final int nu;
        final int nv;
        final float[] noise;

        @FunctionalInterface
        interface NoiseFn {
            float at(float u, float v);
        }

        NoiseTable(int nu, int nv, NoiseFn fn) {
            this.nu = nu;
            this.nv = nv;
            this.noise = new float[(nu + 1) * (nv + 1)];
            for (int i = 0; i <= nu; i++) {
                for (int j = 0; j <= nv; j++) {
                    this.noise[i * (nv + 1) + j] = fn.at((float) (i % nu) / nu, (float) j / nv);
                }
            }
        }

        float at(float u, float v) {
            float x = (u - Mth.floor(u)) * this.nu;
            float y = Mth.clamp(v, 0.0F, 1.0F) * this.nv;
            int i = Math.min(Mth.floor(x), this.nu - 1);
            int j = Math.min(Mth.floor(y), this.nv - 1);
            float fx = x - i;
            float fy = y - j;
            float n00 = this.noise[i * (this.nv + 1) + j];
            float n10 = this.noise[(i + 1) * (this.nv + 1) + j];
            float n01 = this.noise[i * (this.nv + 1) + j + 1];
            float n11 = this.noise[(i + 1) * (this.nv + 1) + j + 1];
            return Mth.lerp(fy, Mth.lerp(fx, n00, n10), Mth.lerp(fx, n01, n11));
        }
    }
}
