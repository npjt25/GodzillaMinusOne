package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.npjt25.godzillaminusone.entity.KingGhidorahEntity;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * King Ghidorah (entity.KingGhidorahEntity): his model, drawn as tall as Godzilla Minus Zero, and his gravity beams:
 * from each firing head's mouth (bone mouth_c / _r / _l, where it really is in the animation) a beam of golden
 * lightning to where it strikes, jagged like a bolt (its zigzag struck anew every other tick), blazing and white-hot
 * at its heart, glowing as Godzilla's breath does (drawn emissive, as his is: RenderType.eyes, which shader packs
 * light up too), with more bolts crackling and forking round it, and a blaze of light where it strikes. (The world
 * round it is lit up by the server: light along the beams, KingGhidorahEntity.lightBeams.) His lift's beams are the
 * same, narrower.
 */
public class KingGhidorahRenderer extends GeoEntityRenderer<KingGhidorahEntity> {
    /**
     * The crackling bolts, outermost first: {half width (blocks), red, green, blue, alpha, added on (1) or laid over
     * (0)}. The golden body is laid over what's behind it (light added to a blue sky only whitens it); the glow round
     * it and the white-hot core are added on.
     */
    private static final float[][] LAYERS = {
            {2.6F, 1.0F, 0.6F, 0.1F, 0.35F, 1.0F},
            {0.95F, 0.98F, 0.76F, 0.18F, 0.85F, 0.0F},
            {0.4F, 1.0F, 0.96F, 0.7F, 0.95F, 1.0F}};
    /**
     * The beam itself: {half width, red, green, blue, alpha, emissive (1: glowing, added on, as Godzilla's breath; 0:
     * the golden body, laid over what's behind it so it stays gold against a bright sky)}. A wide glow, a nearer one,
     * the golden body, its white-hot core.
     */
    private static final float[][] BEAM = {
            {7.0F, 0.75F, 0.5F, 0.08F, 1.0F, 1.0F},
            {3.6F, 1.0F, 0.78F, 0.2F, 1.0F, 1.0F},
            {2.0F, 1.0F, 0.8F, 0.22F, 0.9F, 0.0F},
            {0.9F, 1.0F, 1.0F, 0.88F, 1.0F, 1.0F}};
    private static final ResourceLocation BEAM_GLOW = GodzillaMinusOne.id("textures/entity/king_ghidorah_beam.png");

    public KingGhidorahRenderer(EntityRendererProvider.Context context) {
        super(context, new KingGhidorahModel());
        withScale(KingGhidorahEntity.RENDER_SCALE);
        this.shadowRadius = 10.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, KingGhidorahEntity entity, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        for (KingGhidorahEntity.Head head : KingGhidorahEntity.Head.values()) {
            model.getBone("mouth_" + head.bone).ifPresent(bone -> bone.setTrackingMatrices(true));
        }
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    @Override
    public void renderFinal(PoseStack poseStack, KingGhidorahEntity entity, BakedGeoModel model, MultiBufferSource bufferSource,
                            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay,
                            float red, float green, float blue, float alpha) {
        super.renderFinal(poseStack, entity, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        Vec3 origin = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        for (KingGhidorahEntity.Head head : KingGhidorahEntity.Head.values()) {
            GeoBone bone = model.getBone("mouth_" + head.bone).orElse(null);
            if (bone == null) {
                continue;
            }
            Vector3d local = bone.getLocalPosition();
            Vec3 mouth = new Vec3(local.x, local.y, local.z);
            entity.drawnMouths[head.ordinal()] = mouth;
            if (!entity.isFiring(head)) {
                continue;
            }
            Vec3 end = entity.beamEnd(head, origin.add(mouth), entity.getAim()).subtract(origin);
            int ticks = entity.getActionTicks() - head.fireAt;
            float grow = Mth.clamp((ticks + partialTick) / 3.0F, 0.0F, 1.0F);
            Vec3 to = mouth.lerp(end, grow);
            float width = entity.getAction() == KingGhidorahEntity.ACTION_LIFT ? 0.6F : 1.0F;
            float flicker = 0.88F + 0.12F * Mth.sin((entity.tickCount + partialTick) * 2.3F + head.ordinal() * 1.7F);
            // (struck anew every other tick, as lightning flickers)
            RandomSource random = RandomSource.create(entity.getId() * 31L + head.ordinal() * 7919L + (entity.tickCount / 2));
            drawBeam(poseStack, bufferSource, mouth, to, width * flicker, random);
            // more lightning crackling round it
            for (int k = 0; k < 3; k++) {
                drawBolt(poseStack, bufferSource, mouth, to, random, 0.5F * width);
            }
            if (grow >= 1.0F) {
                drawFlare(poseStack, bufferSource, to, 10.0F * width * flicker);
            }
        }
    }

    /**
     * The beam from a to b (relative to him): a bolt of lightning, zigzagging about the straight line between them (its
     * kinks a few blocks apart, furthest out in the middle, none at either end), in layers, each three ways round so
     * it's round from any side.
     */
    private static void drawBeam(PoseStack poseStack, MultiBufferSource buffers, Vec3 a, Vec3 b, float scale, RandomSource random) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Vec3 d = b.subtract(a);
        double length = d.length();
        if (length < 0.5) {
            return;
        }
        Vec3 dir = d.scale(1.0 / length);
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = dir.cross(u).normalize();
        int n = Mth.clamp((int) (length / 5.0), 3, 64);
        Vec3[] pts = new Vec3[n + 1];
        double zig = Math.min(2.6, 0.35 + length * 0.02) * scale;
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            double jag = (i == 0 || i == n) ? 0.0 : zig * (0.35 + 0.65 * Math.sin(Math.PI * f));
            double side = (i % 2 == 0 ? 1.0 : -1.0) * (0.4 + 0.6 * random.nextDouble());
            pts[i] = a.add(d.scale(f)).add(u.scale(side * jag)).add(v.scale((random.nextDouble() - 0.5) * 1.4 * jag));
        }
        for (float[] layer : BEAM) {
            boolean glows = layer[5] > 0.0F;
            VertexConsumer vc = buffers.getBuffer(glows ? RenderType.eyes(BEAM_GLOW) : RenderType.debugQuads());
            float w = layer[0] * scale;
            for (int i = 0; i < n; i++) {
                Vec3 seg = pts[i + 1].subtract(pts[i]).normalize();
                Vec3 su = seg.cross(Math.abs(seg.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
                Vec3 sv = seg.cross(su).normalize();
                for (int k = 0; k < 3; k++) {
                    double angle = k * Math.PI / 3.0;
                    Vec3 side = su.scale(Math.cos(angle) * w).add(sv.scale(Math.sin(angle) * w));
                    // (each joint a little longer, so the zigzag's kinks are closed)
                    Vec3 p = pts[i].subtract(seg.scale(w * 0.3));
                    Vec3 q = pts[i + 1].add(seg.scale(w * 0.3));
                    if (glows) {
                        glowQuad(vc, pose, normal, p, q, side, layer);
                    } else {
                        quad(vc, pose, p, q, side, layer);
                    }
                }
            }
        }
    }

    /** A quad of glowing light (RenderType.eyes, BEAM_GLOW across its width: brightest down its middle). */
    private static void glowQuad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vec3 p, Vec3 q, Vec3 side, float[] c) {
        glowVertex(vc, pose, normal, p.add(side), c, 0.0F, 0.0F);
        glowVertex(vc, pose, normal, p.subtract(side), c, 1.0F, 0.0F);
        glowVertex(vc, pose, normal, q.subtract(side), c, 1.0F, 1.0F);
        glowVertex(vc, pose, normal, q.add(side), c, 0.0F, 1.0F);
    }

    private static void glowVertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vec3 p, float[] c, float u, float v) {
        vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(c[1], c[2], c[3], c[4]).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** A blaze of light where a beam strikes: a few crossed quads, glowing. */
    private static void drawFlare(PoseStack poseStack, MultiBufferSource buffers, Vec3 at, float size) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(BEAM_GLOW));
        float[] hot = {0.0F, 1.0F, 0.95F, 0.7F, 1.0F};
        float[] glow = {0.0F, 0.9F, 0.6F, 0.12F, 1.0F};
        Vec3[] axes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        for (int i = 0; i < 3; i++) {
            Vec3 p = axes[i].scale(size);
            Vec3 q = axes[(i + 1) % 3];
            glowQuad(vc, pose, normal, at.subtract(p), at.add(p), q.scale(size), glow);
            glowQuad(vc, pose, normal, at.subtract(p.scale(0.45)), at.add(p.scale(0.45)), q.scale(size * 0.45), hot);
        }
    }

    /** A jagged bolt from a to b (relative to him), in layers of glow, with a couple of forks off it. */
    private static void drawBolt(PoseStack poseStack, MultiBufferSource buffers, Vec3 a, Vec3 b, RandomSource random, float scale) {
        Matrix4f pose = poseStack.last().pose();
        Vec3 d = b.subtract(a);
        double length = d.length();
        if (length < 0.5) {
            return;
        }
        Vec3 dir = d.scale(1.0 / length);
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = dir.cross(u).normalize();
        int n = Mth.clamp((int) (length / 4.0), 4, 48);
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            double jag = (i == 0 || i == n) ? 0.0 : 2.2 * scale * Math.sin(Math.PI * f) + 0.6;
            pts[i] = a.add(d.scale(f)).add(u.scale((random.nextDouble() - 0.5) * 2 * jag)).add(v.scale((random.nextDouble() - 0.5) * 2 * jag));
        }
        for (float[] layer : LAYERS) {
            VertexConsumer vc = buffers.getBuffer(layer[5] > 0.0F ? RenderType.lightning() : RenderType.debugQuads());
            float w = layer[0] * scale;
            for (int i = 0; i < n; i++) {
                quad(vc, pose, pts[i], pts[i + 1], u.scale(w), layer);
                quad(vc, pose, pts[i], pts[i + 1], v.scale(w), layer);
            }
        }
        // forks: short jagged branches off the bolt
        if (scale > 0.4F) {
            for (int k = 0; k < 4; k++) {
                int at = 1 + random.nextInt(Math.max(1, n - 1));
                Vec3 off = dir.scale(length * 0.08).add(u.scale((random.nextDouble() - 0.5) * length * 0.12))
                        .add(v.scale((random.nextDouble() - 0.5) * length * 0.12));
                drawBolt(poseStack, buffers, pts[at], pts[at].add(off), random, 0.35F);
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, Vec3 p, Vec3 q, Vec3 side, float[] c) {
        vertex(vc, pose, p.add(side), c);
        vertex(vc, pose, p.subtract(side), c);
        vertex(vc, pose, q.subtract(side), c);
        vertex(vc, pose, q.add(side), c);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Vec3 p, float[] c) {
        vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(c[1], c[2], c[3], c[4]).endVertex();
    }

    /** While he fires, drawn whenever his beams are in view, not only his body. */
    @Override
    public boolean shouldRender(KingGhidorahEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return true;
        }
        if (entity.getAction() != KingGhidorahEntity.ACTION_BEAM && entity.getAction() != KingGhidorahEntity.ACTION_LIFT) {
            return false;
        }
        Vec3 aim = entity.getAim();
        return frustum.isVisible(entity.getBoundingBox().minmax(new AABB(aim, aim)).inflate(8.0));
    }

    /** Brought down, he falls where he stands (not over sideways as small mobs do). */
    @Override
    protected float getDeathMaxRotation(KingGhidorahEntity animatable) {
        return 0.0F;
    }
}
