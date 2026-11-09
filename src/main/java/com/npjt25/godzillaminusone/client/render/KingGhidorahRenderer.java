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
 * King Ghidorah (entity.KingGhidorahEntity): his model, drawn as tall as Godzilla Minus Zero, and his gravity beams:
 * crackling golden bolts of lightning, jagged and flickering, from each firing head's mouth (bone mouth_c / _r / _l,
 * where it really is in the animation) to where the beam lands (his aim, stopped at the first block in its way).
 */
public class KingGhidorahRenderer extends GeoEntityRenderer<KingGhidorahEntity> {
    /**
     * The bolts, outermost first: {half width (blocks), red, green, blue, alpha, added on (1) or laid over (0)}. The
     * golden body is laid over what's behind it (light added to a blue sky only whitens it); the glow round it and the
     * white-hot core are added on.
     */
    private static final float[][] LAYERS = {
            {2.4F, 1.0F, 0.55F, 0.08F, 0.2F, 1.0F},
            {0.95F, 0.96F, 0.72F, 0.16F, 0.8F, 0.0F},
            {0.35F, 1.0F, 0.93F, 0.6F, 0.75F, 1.0F}};

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
            drawBolt(poseStack, bufferSource, mouth, mouth.lerp(end, grow),
                    RandomSource.create(entity.getId() * 31L + head.ordinal() * 7919L + (entity.tickCount / 2)), 1.0F);
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
        if (entity.getAction() != KingGhidorahEntity.ACTION_BEAM) {
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
