package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The atomic blast's mushroom cloud, as GojiDraw's add-on makes it: five smoke emitters
 * ({@code gojinuclear:smoke1} to {@code smoke5}) going at once at ground zero, each throwing out 200 smoke sprites a
 * second for 25 seconds (26 for the cap) from a disc (a ring for the cap's rim), that accelerate straight up and
 * live one second:
 *
 * <pre>
 *   layer        start y  radius          billboard (half size)  up accel. (blocks/s^2)  emits
 *   smoke1 stalk     0     20                 15                    100                  25 s
 *   smoke2 cap      55    200                 50                    100                  26 s
 *   smoke3 rim      70    200, edge only      60                    100                  25 s
 *   smoke4 core      0     20                 10                    200                  25 s
 *   smoke5 top      80    150                 50                    100                  25 s
 * </pre>
 *
 * Each sprite is the add-on's pixel smoke texture tinted by its layer's colour gradient over its life. The add-on
 * draws them with Bedrock's alpha-tested particle material, which tests the texture's own alpha (at 0.5) before
 * tinting: only the sprite's denser texels show, solid, for its whole life, so the gradients' alphas change
 * nothing. The sprite is cut out that way beforehand (textures/effect/mushroom_smoke.png,
 * tools/make_cloud_textures.py), and drawn unlit and opaque, so the sprites need no depth sorting.
 *
 * <p>It is a client-side effect (started by the ATOMIC_BLAST packet, at the beam's impact) rather than an entity,
 * so it is seen from as far away as the packet reaches, not just within entity tracking range.
 */
public final class NuclearCloud {
    private static final ResourceLocation SPRITE = GodzillaMinusOne.id("textures/effect/mushroom_smoke.png");
    private static final RenderType TYPE = RenderType.text(SPRITE);
    /** The sprite is 16 x 15 texels in a 16 x 16 texture. */
    private static final float SPRITE_V = 15.0F / 16.0F;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    /** smoke1 to smoke5 of the add-on. */
    private static final Layer[] LAYERS = {
            new Layer(0, 20, false, 15, 100, 25, new float[]{0.0F, 0.54F, 1.0F}, new int[]{0xFF2E2D2D, 0x59484848, 0x00848484}),
            new Layer(55, 200, false, 50, 100, 26, new float[]{0.0F, 0.99F}, new int[]{0xFF6E6E6E, 0x8C595959}),
            new Layer(70, 200, true, 60, 100, 25, new float[]{0.0F, 0.5F, 0.9F}, new int[]{0xFF4B4B4B, 0xFF646464, 0x6E202020}),
            new Layer(0, 20, false, 10, 200, 25, new float[]{0.0F, 0.4F, 0.58F, 1.0F},
                    new int[]{0xFF2B2B2B, 0xFF2E2E2E, 0xFF272727, 0x00242222}),
            new Layer(80, 150, false, 50, 100, 25, new float[]{0.0F, 0.24F, 0.57F, 1.0F},
                    new int[]{0xFF5A5047, 0xFF423D2E, 0xFF382A18, 0x52261608}),
    };
    /** Sprites a layer emits per second, and at most alive at once (both as in the add-on). */
    private static final int RATE = 200;
    private static final int MAX_PARTICLES = 400;
    /** Seconds a sprite lives. */
    private static final float LIFE = 1.0F;

    private static final List<NuclearCloud> CLOUDS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private final Vec3 pos;
    private final Emitter[] emitters = new Emitter[LAYERS.length];
    private int age;

    private NuclearCloud(Vec3 pos) {
        this.pos = pos;
        for (int i = 0; i < LAYERS.length; i++) {
            this.emitters[i] = new Emitter(LAYERS[i]);
        }
    }

    /**
     * A mushroom cloud at ground zero. (The atomic breath's blast and the nuclear bomb both make the same one; the
     * add-on's cloud always rises from the ground, so an air burst gets it too, centred where it goes off.)
     */
    public static void spawn(Vec3 pos, float yaw, boolean airburst) {
        CLOUDS.add(new NuclearCloud(pos));
    }

    public static void tick() {
        for (NuclearCloud cloud : CLOUDS) {
            cloud.age++;
            for (Emitter emitter : cloud.emitters) {
                emitter.tick(cloud.age);
            }
        }
        CLOUDS.removeIf(c -> c.age > 26 * 20 + LIFE * 20 + 1);
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
        VertexConsumer buffer = buffers.getBuffer(TYPE);
        Vec3 cam = camera.getPosition();
        Quaternionf facing = camera.rotation();
        // (the camera's own x axis is its left: the corners go round as vanilla's particles do, facing the camera)
        Vector3f side = new Vector3f(1.0F, 0.0F, 0.0F).rotate(facing);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(facing);
        Matrix4f pose = poseStack.last().pose();
        // the cloud towers over the landscape: it is seen through the haze of the render distance, not lost in it
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        for (NuclearCloud cloud : CLOUDS) {
            float now = cloud.age + partialTick;
            for (Emitter emitter : cloud.emitters) {
                emitter.draw(buffer, pose, cloud.pos.subtract(cam), now, side, up);
            }
        }
        buffers.endBatch(TYPE);
        RenderSystem.setShaderFogStart(fogStart);
    }

    // ------------------------------------------------------------------ layers and their sprites

    /** One of the add-on's smoke emitters. */
    private record Layer(float y, float radius, boolean rimOnly, float size, float acceleration, float seconds,
                         float[] stops, int[] colours) {
        /** The tint (ARGB) at a fraction of a sprite's life. */
        int tint(float f) {
            if (f <= this.stops[0]) {
                return this.colours[0];
            }
            for (int i = 1; i < this.stops.length; i++) {
                if (f <= this.stops[i]) {
                    float x = (f - this.stops[i - 1]) / (this.stops[i] - this.stops[i - 1]);
                    return lerp(this.colours[i - 1], this.colours[i], x);
                }
            }
            return this.colours[this.colours.length - 1];
        }

        private static int lerp(int a, int b, float f) {
            int out = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int ca = a >>> shift & 0xFF;
                int cb = b >>> shift & 0xFF;
                out |= Math.round(ca + (cb - ca) * f) << shift;
            }
            return out;
        }
    }

    /** A layer's live sprites: where each was emitted (relative to ground zero) and when (ticks, fractional). */
    private static final class Emitter {
        final Layer layer;
        final float[] x = new float[MAX_PARTICLES];
        final float[] z = new float[MAX_PARTICLES];
        final float[] born = new float[MAX_PARTICLES];
        int count;

        Emitter(Layer layer) {
            this.layer = layer;
        }

        void tick(int age) {
            // retire the sprites that have lived out their second
            int kept = 0;
            for (int i = 0; i < this.count; i++) {
                if (age - this.born[i] < LIFE * 20.0F) {
                    this.x[kept] = this.x[i];
                    this.z[kept] = this.z[i];
                    this.born[kept] = this.born[i];
                    kept++;
                }
            }
            this.count = kept;
            if (age > this.layer.seconds() * 20.0F) {
                return;
            }
            // 200 a second: 10 this tick, spread evenly over it so they don't rise in step
            int n = RATE / 20;
            for (int k = 0; k < n && this.count < MAX_PARTICLES; k++) {
                float angle = RANDOM.nextFloat() * Mth.TWO_PI;
                float r = this.layer.rimOnly() ? this.layer.radius() : this.layer.radius() * Mth.sqrt(RANDOM.nextFloat());
                this.x[this.count] = Mth.cos(angle) * r;
                this.z[this.count] = Mth.sin(angle) * r;
                this.born[this.count] = age - 1 + (k + 1) / (float) n;
                this.count++;
            }
        }

        void draw(VertexConsumer buffer, Matrix4f pose, Vec3 origin, float now, Vector3f side, Vector3f up) {
            float size = this.layer.size();
            for (int i = 0; i < this.count; i++) {
                float seconds = (now - this.born[i]) / 20.0F;
                if (seconds < 0.0F || seconds > LIFE) {
                    continue;
                }
                int tint = this.layer.tint(seconds / LIFE);
                // no initial speed; accelerating straight up
                float cx = (float) origin.x + this.x[i];
                float cy = (float) origin.y + this.layer.y() + 0.5F * this.layer.acceleration() * seconds * seconds;
                float cz = (float) origin.z + this.z[i];
                int r = tint >> 16 & 0xFF;
                int g = tint >> 8 & 0xFF;
                int b = tint & 0xFF;
                vertex(buffer, pose, cx, cy, cz, side, up, -size, -size, r, g, b, 1.0F, SPRITE_V);
                vertex(buffer, pose, cx, cy, cz, side, up, -size, size, r, g, b, 1.0F, 0.0F);
                vertex(buffer, pose, cx, cy, cz, side, up, size, size, r, g, b, 0.0F, 0.0F);
                vertex(buffer, pose, cx, cy, cz, side, up, size, -size, r, g, b, 0.0F, SPRITE_V);
            }
        }

        private static void vertex(VertexConsumer buffer, Matrix4f pose, float cx, float cy, float cz, Vector3f side, Vector3f up,
                                   float a, float b, int red, int green, int blue, float u, float v) {
            buffer.vertex(pose, cx + side.x * a + up.x * b, cy + side.y * a + up.y * b, cz + side.z * a + up.z * b)
                    .color(red, green, blue, 255).uv(u, v).uv2(LIGHT).endVertex();
        }
    }
}
