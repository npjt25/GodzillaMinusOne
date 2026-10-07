package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The searchlight (tools/make_props.py): the turret turns and the lamp tilts to where its operator looks; while lit
 * the lens glows, a cone of light widens out to where it lands, and a pool of light spreads over what it lands on.
 */
public class SearchlightRenderer extends GeoEntityRenderer<SearchlightEntity> {
    private static final ResourceLocation LENS = GodzillaMinusOne.id("textures/entity/searchlight_glow.png");
    private static final int SEGMENTS = 16;

    public SearchlightRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<SearchlightEntity>(GodzillaMinusOne.id("searchlight")) {
            @Override
            public void setCustomAnimations(SearchlightEntity entity, long instanceId, AnimationState<SearchlightEntity> state) {
                super.setCustomAnimations(entity, instanceId, state);
                // the model faces the entity's own yaw; the turret makes up the rest
                float turn = Mth.wrapDegrees(entity.getAimYaw() - entity.getYRot());
                getBone("turret").ifPresent(b -> b.setRotY(-turn * Mth.DEG_TO_RAD));
                getBone("lamp").ifPresent(b -> b.setRotX(-entity.getAimPitch() * Mth.DEG_TO_RAD));
            }
        });
        this.shadowRadius = 1.0F;
        addRenderLayer(new LensLayer(this));
    }

    @Override
    protected void applyRotations(SearchlightEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, entity.getYRot(), partialTick);
        float wobble = entity.getWobble() - partialTick;
        if (wobble > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(wobble) * wobble * 0.8F));
        }
    }

    @Override
    public void render(SearchlightEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        if (entity.isLit()) {
            renderBeam(entity, partialTick, poseStack, buffers);
        }
    }

    /**
     * A soft shaft of light from the lens to where the beam lands: drawn facing you, brightest down its middle and fading
     * to nothing at its edges (no hard outline), inside a fainter halo, bright at the lamp and fading with distance; a very
     * faint, finely divided cone round it gives it body when you look along it.
     */
    private static void renderBeam(SearchlightEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        Vec3 from = entity.lens().subtract(entity.position());
        Vec3 dir = entity.beamDirection();
        HitResult hit = entity.beamHit();
        double length = hit.getLocation().distanceTo(entity.lens());
        if (length < 0.5) {
            return;
        }
        // two vectors across the beam
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(up).normalize();
        Vec3 b = dir.cross(a).normalize();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poseStack.last().pose();
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(entity.getPosition(partialTick));
        int steps = Math.max(8, (int) (length / 4.0));
        for (int s = 0; s < steps; s++) {
            double d0 = length * s / steps;
            double d1 = length * (s + 1) / steps;
            float r0 = (float) SearchlightEntity.beamRadius(d0);
            float r1 = (float) SearchlightEntity.beamRadius(d1);
            float alpha0 = (float) (0.42 * Math.pow(1.0 - d0 / length, 1.3));
            float alpha1 = (float) (0.42 * Math.pow(1.0 - d1 / length, 1.3));
            Vec3 c0 = from.add(dir.scale(d0));
            Vec3 c1 = from.add(dir.scale(d1));
            Vec3 w0 = across(dir, eye.subtract(c0), a);
            Vec3 w1 = across(dir, eye.subtract(c1), a);
            shaft(buffer, pose, c0, c1, w0.scale(r0), w1.scale(r1), alpha0, alpha1);                 // the shaft
            shaft(buffer, pose, c0, c1, w0.scale(r0 * 1.9), w1.scale(r1 * 1.9), alpha0 * 0.3F, alpha1 * 0.3F); // its halo
            for (int i = 0; i < SHELL_SEGMENTS; i++) {
                double t0 = Math.PI * 2.0 * i / SHELL_SEGMENTS;
                double t1 = Math.PI * 2.0 * (i + 1) / SHELL_SEGMENTS;
                Vec3 e0 = a.scale(Math.cos(t0)).add(b.scale(Math.sin(t0)));
                Vec3 e1 = a.scale(Math.cos(t1)).add(b.scale(Math.sin(t1)));
                float s0 = alpha0 * 0.12F;
                float s1 = alpha1 * 0.12F;
                // both windings, so it shows from inside and outside
                quad(buffer, pose, c0.add(e0.scale(r0)), c0.add(e1.scale(r0)), c1.add(e1.scale(r1)), c1.add(e0.scale(r1)), s0, s1);
                quad(buffer, pose, c0.add(e1.scale(r0)), c0.add(e0.scale(r0)), c1.add(e0.scale(r1)), c1.add(e1.scale(r1)), s0, s1);
            }
        }
        renderSpot(entity, hit, length, buffer, pose);
    }

    private static final int SHELL_SEGMENTS = 40;

    /** Across the beam and across the line of sight to it (so a strip along that faces you); {@code fallback} end-on. */
    private static Vec3 across(Vec3 dir, Vec3 toEye, Vec3 fallback) {
        Vec3 w = dir.cross(toEye);
        return w.lengthSqr() < 1.0E-6 ? fallback : w.normalize();
    }

    /** A strip from c0 to c1, {@code w} each side: bright down the middle, clear at the edges (both windings). */
    private static void shaft(VertexConsumer buffer, Matrix4f pose, Vec3 c0, Vec3 c1, Vec3 w0, Vec3 w1, float alpha0, float alpha1) {
        for (int side = -1; side <= 1; side += 2) {
            Vec3 e0 = c0.add(w0.scale(side));
            Vec3 e1 = c1.add(w1.scale(side));
            vertex(buffer, pose, c0, alpha0);
            vertex(buffer, pose, e0, 0.0F);
            vertex(buffer, pose, e1, 0.0F);
            vertex(buffer, pose, c1, alpha1);
            vertex(buffer, pose, c1, alpha1);
            vertex(buffer, pose, e1, 0.0F);
            vertex(buffer, pose, e0, 0.0F);
            vertex(buffer, pose, c0, alpha0);
        }
    }

    /** Rings of the pool of light it casts where it lands: radius (fraction of the pool's) and brightness. */
    private static final float[][] SPOT_RINGS = {{0.0F, 0.55F}, {0.35F, 0.45F}, {0.7F, 0.22F}, {1.0F, 0.0F}};

    /** A soft, bright pool of light lying on the surface the beam lands on (a little off it, so it doesn't flicker). */
    private static void renderSpot(SearchlightEntity entity, HitResult beamHit, double length, VertexConsumer buffer, Matrix4f pose) {
        if (!(beamHit instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 centre = hit.getLocation().subtract(entity.position()).add(normal.scale(0.06));
        Vec3 u = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 v = normal.cross(u);
        float radius = (float) SearchlightEntity.spotRadius(length);
        // the further it lands, the dimmer the pool
        float fade = (float) Math.max(0.25, Math.pow(1.0 - Math.min(length / SearchlightEntity.RANGE, 1.0), 0.8));
        for (int k = 0; k + 1 < SPOT_RINGS.length; k++) {
            float ri = SPOT_RINGS[k][0] * radius;
            float ro = SPOT_RINGS[k + 1][0] * radius;
            float ai = SPOT_RINGS[k][1] * fade;
            float ao = SPOT_RINGS[k + 1][1] * fade;
            for (int i = 0; i < SEGMENTS * 2; i++) {
                double t0 = Math.PI * 2.0 * i / (SEGMENTS * 2);
                double t1 = Math.PI * 2.0 * (i + 1) / (SEGMENTS * 2);
                Vec3 e0 = u.scale(Math.cos(t0)).add(v.scale(Math.sin(t0)));
                Vec3 e1 = u.scale(Math.cos(t1)).add(v.scale(Math.sin(t1)));
                Vec3 i0 = centre.add(e0.scale(ri));
                Vec3 i1 = centre.add(e1.scale(ri));
                Vec3 o0 = centre.add(e0.scale(ro));
                Vec3 o1 = centre.add(e1.scale(ro));
                quad(buffer, pose, i0, i1, o1, o0, ai, ao);
                quad(buffer, pose, i1, i0, o0, o1, ai, ao);
            }
        }
    }

    /** p0, p1 get alpha0 and p2, p3 alpha1. */
    private static void quad(VertexConsumer buffer, Matrix4f pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float alpha0, float alpha1) {
        vertex(buffer, pose, p0, alpha0);
        vertex(buffer, pose, p1, alpha0);
        vertex(buffer, pose, p2, alpha1);
        vertex(buffer, pose, p3, alpha1);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 p, float alpha) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(1.0F, 0.96F, 0.82F, alpha).endVertex();
    }

    private static class LensLayer extends GeoRenderLayer<SearchlightEntity> {
        LensLayer(SearchlightRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, SearchlightEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (entity.isLit()) {
                RenderType type = RenderType.eyes(LENS);
                getRenderer().reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
}
