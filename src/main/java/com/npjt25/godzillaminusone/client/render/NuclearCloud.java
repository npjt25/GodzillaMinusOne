package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The nuclear explosion (Godzilla's atomic breath, the nuclear bomb) as a huge 3D mushroom cloud, after Alex's Caves'
 * nuclear explosion: a white-hot fireball that swells, rises and cools into the rolling cap of a mushroom cloud of
 * dark, billowing smoke whose underside keeps glowing like embers, on a column of fire wrapped in smoke, with an
 * orange shockwave racing out along the ground, a condensation ring and a base surge of dust. The smoke is see-through
 * and softly textured. Every part is a noise-displaced shape (an ellipsoid, tori, a lathe) re-shaped every frame;
 * tools/preview_cloud.py renders the same cloud offline.
 *
 * <p>It is a client-side effect (started by the ATOMIC_BLAST packet) rather than an entity, so it is seen from as
 * far away as the packet reaches, not just within entity tracking range.
 */
public final class NuclearCloud {
    private static final ResourceLocation SMOKE = GodzillaMinusOne.id("textures/effect/nuclear_smoke.png");
    private static final ResourceLocation FIRE = GodzillaMinusOne.id("textures/effect/nuclear_fire.png");
    /** Seconds the cloud hangs in the sky (it fades out over the last 15). */
    private static final float LIFE = 60.0F;
    /** Everything below is measured at this scale; the cloud is drawn this much bigger. */
    private static final float SCALE = 1.7F;
    private static final int LIGHT = LightTexture.pack(0, 15);

    /** The fire's colour over time (seconds: r, g, b): white-hot, yellow, orange, then embers that die away. */
    private static final float[][] FIRE_KEYS = {
            {0.0F, 1.0F, 1.0F, 1.0F}, {0.5F, 1.0F, 0.93F, 0.7F}, {1.5F, 1.0F, 0.72F, 0.3F}, {4.0F, 1.0F, 0.5F, 0.12F},
            {10.0F, 0.9F, 0.32F, 0.06F}, {20.0F, 0.62F, 0.17F, 0.03F}, {32.0F, 0.26F, 0.06F, 0.01F}, {42.0F, 0.0F, 0.0F, 0.0F}};

    private static final Grid HEAD = new Grid(56, 28, NuclearCloud::sphereNoise);
    private static final Grid TORUS = new Grid(56, 18, NuclearCloud::torusNoise);
    private static final Grid STEM = new Grid(36, 22, NuclearCloud::stemNoise);
    private static final Grid RING = new Grid(64, 8, NuclearCloud::torusNoise);
    private static final Grid BASE = new Grid(64, 10, NuclearCloud::torusNoise);
    private static final Grid SHOCK = new Grid(72, 4, NuclearCloud::torusNoise);

    private static final List<NuclearCloud> CLOUDS = new ArrayList<>();

    private final Vec3 pos;
    private final float yaw;
    private final boolean airburst;
    private int age;

    private enum Pass {
        SMOKE, FIRE
    }

    private NuclearCloud(Vec3 pos, float yaw, boolean airburst) {
        this.pos = pos;
        this.yaw = yaw;
        this.airburst = airburst;
    }

    public static void spawn(Vec3 pos, float yaw, boolean airburst) {
        CLOUDS.add(new NuclearCloud(pos, yaw, airburst));
    }

    public static void tick() {
        for (NuclearCloud cloud : CLOUDS) {
            cloud.age++;
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
        // the cloud towers over the landscape: it is seen through the haze of the render distance, not lost in it
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        for (Pass pass : Pass.values()) {
            RenderType type = pass == Pass.SMOKE ? Types.SMOKE : Types.FIRE;
            VertexConsumer buffer = buffers.getBuffer(type);
            for (NuclearCloud cloud : CLOUDS) {
                poseStack.pushPose();
                poseStack.translate(cloud.pos.x - cam.x, cloud.pos.y - cam.y, cloud.pos.z - cam.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(cloud.yaw));
                poseStack.scale(SCALE, SCALE, SCALE);
                cloud.draw(pass, buffer, poseStack, (cloud.age + partialTick) / 20.0F);
                poseStack.popPose();
            }
            buffers.endBatch(type);
        }
        RenderSystem.setShaderFogStart(fogStart);
    }

    // ------------------------------------------------------------------ shape of the cloud over time (seconds)

    private static float smooth(float e0, float e1, float x) {
        float f = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return f * f * (3.0F - 2.0F * f);
    }

    /** The fireball's radius: it swells to ~28 blocks (before {@link #SCALE}) in about a second. */
    private static float fireballRadius(float t) {
        return 2.0F + 26.0F * (1.0F - (float) Math.exp(-2.2F * t));
    }

    /** The head (fireball, then the cap of the mushroom): horizontal radius, vertical radius, height of its centre. */
    private float[] head(float t) {
        float a0 = fireballRadius(t);
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

    private void draw(Pass pass, VertexConsumer buffer, PoseStack poseStack, float t) {
        float fade = 1.0F - smooth(LIFE - 15.0F, LIFE, t);
        float[] head = head(t);
        float a = head[0];
        float b = head[1];
        float h = head[2];
        float[] fire = fireColor(t);
        boolean burning = Math.max(fire[0], Math.max(fire[1], fire[2])) > 0.01F;
        // the smoke: dark and sooty while it still burns, cooling to a lighter grey
        float cool = smooth(4.0F, 30.0F, t);
        float sr = Mth.lerp(cool, 0.34F, 0.56F);
        float sg = Mth.lerp(cool, 0.29F, 0.53F);
        float sb = Mth.lerp(cool, 0.26F, 0.51F);
        float headAlpha = 0.74F * smooth(0.8F, 3.5F, t) * fade;
        float under = smooth(1.5F, 4.5F, t);
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // The ground shockwave: a ring of orange fire racing out along the ground.
        if (pass == Pass.FIRE && !this.airburst) {
            float shock = smooth(0.0F, 0.3F, t) * (1.0F - smooth(1.5F, 4.5F, t));
            if (shock > 0.01F) {
                float radius = 10.0F + 75.0F * t;
                torusPoints(SHOCK, 0.8F, radius, 1.6F + 0.4F * t, 0.6F, 0.25F, 2.5F);
                SHOCK.draw(buffer, pose, normal, 1.0F * shock, 0.52F * shock, 0.14F * shock, 1.0F, 12, 1, 0.0F, t * 0.3F, null);
            }
        }

        // The head: the fireball, then the cap.
        if (pass == Pass.SMOKE) {
            headPoints(t, 1.0F);
            HEAD.draw(buffer, pose, normal, sr, sg, sb, headAlpha, 4, 2, 0.0F, 0.0F, null);
        } else if (burning) {
            headPoints(t, 1.0F + 0.06F * under);
            // at first the whole fireball burns; then the underside of the cap and the rim of the roll glow like embers
            HEAD.draw(buffer, pose, normal, fire[0], fire[1], fire[2], 1.0F, 4, 2, 0.0F, -t * 0.05F,
                    (ny, v) -> (1.0F - under) + under * Mth.clamp(-ny * 1.4F + 0.15F, 0.0F, 1.0F));
        }

        if (this.airburst) {
            float ringAlpha = 0.45F * smooth(0.5F, 2.5F, t) * (1.0F - smooth(8.0F, 14.0F, t)) * fade;
            if (pass == Pass.SMOKE && ringAlpha > 0.01F) {
                torusPoints(RING, h, a * 1.3F + 4.0F * t, 3.0F, 0.5F, 0.3F, 2.2F);
                RING.draw(buffer, pose, normal, 0.95F, 0.95F, 0.97F, ringAlpha, 6, 1, 0.0F, 0.0F, null);
            }
            return;
        }

        // The ring of smoke rolling under the rim of the cap, its underside glowing.
        float torusAlpha = 0.74F * smooth(2.5F, 7.0F, t) * fade;
        if (torusAlpha > 0.01F) {
            float cy = h - b * 0.35F;
            float roll = t * 0.04F;
            if (pass == Pass.SMOKE) {
                torusPoints(TORUS, cy, a * 0.78F, b * 0.42F, 0.85F, 0.22F, 1.0F);
                TORUS.draw(buffer, pose, normal, sr * 0.9F, sg * 0.9F, sb * 0.9F, torusAlpha, 5, 1, 0.0F, roll, null);
            } else if (burning) {
                torusPoints(TORUS, cy, a * 0.78F, b * 0.42F * 1.06F, 0.85F, 0.22F, 1.0F);
                float glow = smooth(2.5F, 7.0F, t) * 0.8F;
                TORUS.draw(buffer, pose, normal, fire[0] * glow, fire[1] * glow, fire[2] * glow, 1.0F, 5, 1, 0.3F, roll,
                        (ny, v) -> Mth.clamp(-ny * 1.5F, 0.0F, 1.0F));
            }
        }

        // The stem: a column of fire inside a sheath of smoke.
        float top = h - b * 0.45F;
        float width = 2.5F + 11.0F * (1.0F - (float) Math.exp(-t / 5.0F));
        float stemAlpha = 0.6F * smooth(0.6F, 3.0F, t) * fade;
        if (stemAlpha > 0.01F) {
            if (pass == Pass.SMOKE) {
                stemPoints(top, width, 1.0F);
                STEM.draw(buffer, pose, normal, sr, sg, sb, stemAlpha, 3, 3, 0.0F, -t * 0.06F, null);
            } else if (burning) {
                stemPoints(top, width, 0.72F);
                STEM.draw(buffer, pose, normal, fire[0], Math.min(1.0F, fire[1] * 1.15F), fire[2], 1.0F, 3, 4, 0.0F, -t * 0.12F,
                        (ny, v) -> 1.0F - 0.55F * v);
            }
        }

        // The condensation ring that flashes around the stem.
        float ringAlpha = 0.45F * smooth(2.5F, 4.5F, t) * (1.0F - smooth(8.0F, 13.0F, t)) * fade;
        if (pass == Pass.SMOKE && ringAlpha > 0.01F) {
            torusPoints(RING, top * 0.55F, width * 2.6F + 8.0F + 10.0F * smooth(2.0F, 10.0F, t), 4.0F, 0.35F, 0.45F, 2.2F);
            RING.draw(buffer, pose, normal, 0.95F, 0.95F, 0.97F, ringAlpha, 6, 1, 0.0F, 0.0F, null);
        }

        // The base surge: a low, wide skirt of dust rolling out along the ground, lit from within at first.
        float baseAlpha = 0.6F * smooth(0.3F, 1.5F, t) * (1.0F - smooth(25.0F, 45.0F, t)) * fade;
        if (baseAlpha > 0.01F) {
            float radius = 8.0F + 62.0F * (1.0F - (float) Math.exp(-t / 6.0F));
            float height = 3.0F + 7.0F * (1.0F - (float) Math.exp(-t / 4.0F));
            if (pass == Pass.SMOKE) {
                torusPoints(BASE, height * 0.4F, radius * 0.5F, height, 1.0F, 0.3F, radius * 0.5F / height);
                BASE.draw(buffer, pose, normal, 0.55F, 0.49F, 0.43F, baseAlpha, 8, 1, 0.0F, 0.0F, null);
            } else if (burning) {
                float glow = 0.6F * (1.0F - smooth(4.0F, 14.0F, t));
                if (glow > 0.01F) {
                    torusPoints(BASE, height * 0.4F, radius * 0.5F, height * 0.9F, 1.0F, 0.3F, radius * 0.5F / height);
                    BASE.draw(buffer, pose, normal, fire[0] * glow, fire[1] * glow, fire[2] * glow, 1.0F, 8, 1, 0.5F, 0.0F,
                            (ny, v) -> 0.4F + 0.6F * Mth.clamp(-ny, 0.0F, 1.0F));
                }
            }
        }
    }

    private void headPoints(float t, float scale) {
        float[] head = head(t);
        float a = head[0];
        float b = head[1];
        float h = head[2];
        float amp = 0.12F + 0.1F * smooth(1.0F, 8.0F, t);
        float flat = this.airburst ? 1.0F : 1.0F - 0.45F * smooth(2.0F, 10.0F, t);
        Grid g = HEAD;
        for (int i = 0; i <= g.nu; i++) {
            float th = (float) i / g.nu * Mth.TWO_PI;
            for (int j = 0; j <= g.nv; j++) {
                float ph = ((float) j / g.nv - 0.5F) * Mth.PI;
                float dx = Mth.cos(ph) * Mth.cos(th);
                float dy = Mth.sin(ph);
                float dz = Mth.cos(ph) * Mth.sin(th);
                float r = scale * (1.0F + amp * g.noise[g.index(i, j)]);
                float y = dy * b * r * (dy < 0.0F ? flat : 1.0F);
                g.set(i, j, dx * a * r, h + y, dz * a * r);
            }
        }
        g.computeNormals();
    }

    /** A torus around the vertical axis; {@code wide} stretches the tube horizontally (a flat skirt). */
    private static void torusPoints(Grid g, float cy, float major, float tube, float squash, float amp, float wide) {
        for (int i = 0; i <= g.nu; i++) {
            float th = (float) i / g.nu * Mth.TWO_PI;
            for (int j = 0; j <= g.nv; j++) {
                float ph = (float) j / g.nv * Mth.TWO_PI;
                float r = tube * (1.0F + amp * g.noise[g.index(i, j)]);
                float rr = major + wide * r * Mth.cos(ph);
                g.set(i, j, Mth.cos(th) * rr, cy + squash * r * Mth.sin(ph), Mth.sin(th) * rr);
            }
        }
        g.computeNormals();
    }

    /** The stem: wide at the ground, narrow in the middle, flaring into the cap. */
    private static void stemPoints(float top, float width, float scale) {
        Grid g = STEM;
        for (int i = 0; i <= g.nu; i++) {
            float th = (float) i / g.nu * Mth.TWO_PI;
            for (int j = 0; j <= g.nv; j++) {
                float hh = (float) j / g.nv;
                float low = 1.0F - hh;
                float r = scale * width * (0.5F + 1.5F * low * low * low * low * low + hh * hh * hh)
                        * (1.0F + 0.2F * g.noise[g.index(i, j)]);
                g.set(i, j, Mth.cos(th) * r, hh * top, Mth.sin(th) * r);
            }
        }
        g.computeNormals();
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

    /** Billowing noise: rounded lumps (1 - |noise|), big ones with smaller ones on top, like cauliflower. */
    private static float fbm(float x, float y, float z) {
        return billow(x, y, z) * 0.55F + billow(x * 2.1F + 17.0F, y * 2.1F, z * 2.1F) * 0.3F
                + billow(x * 4.3F, y * 4.3F + 31.0F, z * 4.3F) * 0.15F;
    }

    private static float billow(float x, float y, float z) {
        return 1.0F - 2.0F * Math.abs(valueNoise(x, y, z));
    }

    private static float sphereNoise(float u, float v) {
        float th = u * Mth.TWO_PI;
        float ph = (v - 0.5F) * Mth.PI;
        return fbm(Mth.cos(ph) * Mth.cos(th) * 2.6F + 5.0F, Mth.sin(ph) * 2.6F, Mth.cos(ph) * Mth.sin(th) * 2.6F);
    }

    private static float torusNoise(float u, float v) {
        float th = u * Mth.TWO_PI;
        float ph = v * Mth.TWO_PI;
        return fbm(Mth.cos(th) * 3.5F + 11.0F, Mth.sin(th) * 3.5F, (Mth.cos(ph) + Mth.sin(ph)) * 0.9F);
    }

    private static float stemNoise(float u, float v) {
        float th = u * Mth.TWO_PI;
        return fbm(Mth.cos(th) * 1.5F, v * 4.0F, Mth.sin(th) * 1.5F + 23.0F);
    }

    // ------------------------------------------------------------------ mesh

    @FunctionalInterface
    private interface Weight {
        /** Fire intensity at a vertex with world normal y {@code ny}, {@code v} along the grid (0-1). */
        float at(float ny, float v);
    }

    /** A (nu+1) x (nv+1) grid of vertices wrapping around in u, re-shaped every frame. */
    private static final class Grid {
        final int nu;
        final int nv;
        final float[] noise;
        final float[] p;
        final float[] n;

        @FunctionalInterface
        interface NoiseFn {
            float at(float u, float v);
        }

        Grid(int nu, int nv, NoiseFn fn) {
            this.nu = nu;
            this.nv = nv;
            this.noise = new float[(nu + 1) * (nv + 1)];
            this.p = new float[this.noise.length * 3];
            this.n = new float[this.noise.length * 3];
            for (int i = 0; i <= nu; i++) {
                for (int j = 0; j <= nv; j++) {
                    this.noise[index(i, j)] = fn.at((float) (i % nu) / nu, (float) j / nv);
                }
            }
        }

        int index(int i, int j) {
            return i * (this.nv + 1) + j;
        }

        void set(int i, int j, float x, float y, float z) {
            int k = index(i, j) * 3;
            this.p[k] = x;
            this.p[k + 1] = y;
            this.p[k + 2] = z;
        }

        void computeNormals() {
            Vector3f a = new Vector3f();
            Vector3f b = new Vector3f();
            for (int i = 0; i <= this.nu; i++) {
                int ip = index((i + 1) % this.nu, 0) * 3;
                int im = index((i - 1 + this.nu) % this.nu, 0) * 3;
                for (int j = 0; j <= this.nv; j++) {
                    int jp = index(i, Math.min(j + 1, this.nv)) * 3;
                    int jm = index(i, Math.max(j - 1, 0)) * 3;
                    int o = j * 3;
                    a.set(this.p[ip + o] - this.p[im + o], this.p[ip + o + 1] - this.p[im + o + 1], this.p[ip + o + 2] - this.p[im + o + 2]);
                    b.set(this.p[jp] - this.p[jm], this.p[jp + 1] - this.p[jm + 1], this.p[jp + 2] - this.p[jm + 2]);
                    b.cross(a);
                    float len = b.length();
                    int k = index(i, j) * 3;
                    if (len > 1.0E-6F) {
                        this.n[k] = b.x / len;
                        this.n[k + 1] = b.y / len;
                        this.n[k + 2] = b.z / len;
                    } else {
                        this.n[k] = 0.0F;
                        this.n[k + 1] = 1.0F;
                        this.n[k + 2] = 0.0F;
                    }
                }
            }
        }

        /**
         * Emits the grid as quads wound to face outwards (the inner faces are culled, so the see-through smoke is
         * one layer thick). Smoke is darkened underneath; fire ({@code weight} != null) glows where weighted.
         */
        void draw(VertexConsumer buffer, Matrix4f pose, Matrix3f normalMatrix, float r, float g, float b, float alpha,
                  int repeatU, int repeatV, float offsetU, float offsetV, Weight weight) {
            if (alpha <= 0.01F) {
                return;
            }
            Vector3f pos = new Vector3f();
            Vector3f nrm = new Vector3f();
            for (int i = 0; i < this.nu; i++) {
                for (int j = 0; j < this.nv; j++) {
                    vertex(buffer, pose, normalMatrix, i, j, r, g, b, alpha, repeatU, repeatV, offsetU, offsetV, weight, pos, nrm);
                    vertex(buffer, pose, normalMatrix, i, j + 1, r, g, b, alpha, repeatU, repeatV, offsetU, offsetV, weight, pos, nrm);
                    vertex(buffer, pose, normalMatrix, i + 1, j + 1, r, g, b, alpha, repeatU, repeatV, offsetU, offsetV, weight, pos, nrm);
                    vertex(buffer, pose, normalMatrix, i + 1, j, r, g, b, alpha, repeatU, repeatV, offsetU, offsetV, weight, pos, nrm);
                }
            }
        }

        private void vertex(VertexConsumer buffer, Matrix4f pose, Matrix3f normalMatrix, int i, int j, float r, float g, float b,
                            float alpha, int repeatU, int repeatV, float offsetU, float offsetV, Weight weight, Vector3f pos, Vector3f nrm) {
            int k = index(i, j) * 3;
            float ny = this.n[k + 1];
            float shade;
            if (weight != null) {
                shade = weight.at(ny, (float) j / this.nv);
            } else {
                shade = 0.72F + 0.28F * (ny * 0.5F + 0.5F);
            }
            pose.transformPosition(this.p[k], this.p[k + 1], this.p[k + 2], pos);
            normalMatrix.transform(this.n[k], ny, this.n[k + 2], nrm);
            buffer.vertex(pos.x, pos.y, pos.z, r * shade, g * shade, b * shade, alpha,
                    (float) i / this.nu * repeatU + offsetU, (float) j / this.nv * repeatV + offsetV,
                    OverlayTexture.NO_OVERLAY, LIGHT, nrm.x, nrm.y, nrm.z);
        }
    }

    // ------------------------------------------------------------------ render types

    /**
     * Smoke: see-through, smoothly filtered, outer faces only, and not writing depth (so the fire inside glows through
     * it). Fire: added on top of whatever is behind it, like the vanilla glowing eyes, but smoothly filtered.
     */
    private static final class Types {
        static final RenderType SMOKE = RenderType.create(GodzillaMinusOne.MODID + ":nuclear_smoke", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1 << 18, false, true, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(NuclearCloud.SMOKE, true, false))
                        .setTransparencyState(new RenderStateShard.TransparencyStateShard("nuclear_smoke_transparency", () -> {
                            RenderSystem.enableBlend();
                            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                        }, () -> {
                            RenderSystem.disableBlend();
                            RenderSystem.defaultBlendFunc();
                        }))
                        .setCullState(new RenderStateShard.CullStateShard(true))
                        .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                        .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                        .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                        .createCompositeState(false));

        static final RenderType FIRE = RenderType.create(GodzillaMinusOne.MODID + ":nuclear_fire", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1 << 18, false, true, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEyesShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(NuclearCloud.FIRE, true, false))
                        .setTransparencyState(new RenderStateShard.TransparencyStateShard("nuclear_fire_transparency", () -> {
                            RenderSystem.enableBlend();
                            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                        }, () -> {
                            RenderSystem.disableBlend();
                            RenderSystem.defaultBlendFunc();
                        }))
                        .setCullState(new RenderStateShard.CullStateShard(true))
                        .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                        .createCompositeState(false));

        private Types() {
        }
    }
}
