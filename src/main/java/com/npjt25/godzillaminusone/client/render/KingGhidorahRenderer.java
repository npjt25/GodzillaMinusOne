package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.npjt25.godzillaminusone.entity.KingGhidorahEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * King Ghidorah (entity.KingGhidorahEntity): his model, drawn as tall as Godzilla Minus Zero, and his gravity beams
 * (as in the second reference picture): from each firing head's mouth (bone mouth_c / _r / _l, where it really is in
 * the animation) a concentrated beam of golden light, straight and blazing, white-hot at its heart and flickering,
 * with jagged bolts of lightning crackling round it, to where it strikes; a blaze of light there. His lift's beams are
 * the same, narrower.
 */
public class KingGhidorahRenderer extends GeoEntityRenderer<KingGhidorahEntity> {
    /**
     * The crackling bolts, outermost first: {half width (blocks), red, green, blue, alpha, added on (1) or laid over
     * (0)}. The golden body is laid over what's behind it (light added to a blue sky only whitens it); the glow round
     * it and the white-hot core are added on.
     */
    private static final float[][] LAYERS = {
            {2.4F, 1.0F, 0.55F, 0.08F, 0.2F, 1.0F},
            {0.95F, 0.96F, 0.72F, 0.16F, 0.8F, 0.0F},
            {0.35F, 1.0F, 0.93F, 0.6F, 0.75F, 1.0F}};
    /** The beam itself, the same way: a wide glow, the golden beam, its white-hot core. */
    private static final float[][] BEAM = {
            {4.2F, 1.0F, 0.58F, 0.1F, 0.22F, 1.0F},
            {2.0F, 1.0F, 0.76F, 0.2F, 0.88F, 0.0F},
            {0.8F, 1.0F, 0.98F, 0.82F, 0.95F, 1.0F}};

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
            drawBeam(poseStack, bufferSource, mouth, to, width * flicker);
            // lightning crackling round it
            RandomSource random = RandomSource.create(entity.getId() * 31L + head.ordinal() * 7919L + (entity.tickCount / 2));
            for (int k = 0; k < 2; k++) {
                drawBolt(poseStack, bufferSource, mouth, to, random, 0.45F * width);
            }
            if (grow >= 1.0F) {
                drawFlare(poseStack, bufferSource, to, 7.0F * width * flicker);
            }
        }
    }

    /** A straight beam from a to b (relative to him), in layers, each three ways round so it's round from any side. */
    private static void drawBeam(PoseStack poseStack, MultiBufferSource buffers, Vec3 a, Vec3 b, float scale) {
        Matrix4f pose = poseStack.last().pose();
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 0.25) {
            return;
        }
        Vec3 dir = d.normalize();
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = dir.cross(u).normalize();
        for (float[] layer : BEAM) {
            VertexConsumer vc = buffers.getBuffer(layer[5] > 0.0F ? RenderType.lightning() : RenderType.debugQuads());
            float w = layer[0] * scale;
            for (int k = 0; k < 3; k++) {
                double angle = k * Math.PI / 3.0;
                Vec3 side = u.scale(Math.cos(angle) * w).add(v.scale(Math.sin(angle) * w));
                quad(vc, pose, a, b, side, layer);
            }
        }
    }

    /** A blaze of light where a beam strikes: a few crossed quads, added on. */
    private static void drawFlare(PoseStack poseStack, MultiBufferSource buffers, Vec3 at, float size) {
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        float[] hot = {0.0F, 1.0F, 0.9F, 0.55F, 0.6F};
        float[] glow = {0.0F, 1.0F, 0.6F, 0.12F, 0.3F};
        Vec3[] axes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        for (int i = 0; i < 3; i++) {
            Vec3 p = axes[i].scale(size);
            Vec3 q = axes[(i + 1) % 3];
            quad(vc, pose, at.subtract(p), at.add(p), q.scale(size), glow);
            quad(vc, pose, at.subtract(p.scale(0.45)), at.add(p.scale(0.45)), q.scale(size * 0.45), hot);
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
        if (scale > 0.5F) {
            for (int k = 0; k < 3; k++) {
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
