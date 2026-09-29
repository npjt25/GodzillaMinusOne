package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The searchlight (tools/make_props.py): the turret turns and the lamp tilts to where its operator looks; while lit
 * the lens glows and a beam of light is drawn out to where it lands.
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

    /** A cone of light from the lens to where the beam lands, bright at the lamp and fading with distance. */
    private static void renderBeam(SearchlightEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        Vec3 from = entity.lens().subtract(entity.position());
        Vec3 dir = entity.beamDirection();
        double length = entity.beamEnd().distanceTo(entity.lens());
        if (length < 0.5) {
            return;
        }
        // two vectors across the beam
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(up).normalize();
        Vec3 b = dir.cross(a).normalize();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poseStack.last().pose();
        int steps = Math.max(4, (int) (length / 8.0));
        for (int s = 0; s < steps; s++) {
            double d0 = length * s / steps;
            double d1 = length * (s + 1) / steps;
            float r0 = (float) (0.7 + d0 * 0.035);
            float r1 = (float) (0.7 + d1 * 0.035);
            float alpha0 = (float) (0.32 * Math.pow(1.0 - d0 / length, 1.3));
            float alpha1 = (float) (0.32 * Math.pow(1.0 - d1 / length, 1.3));
            Vec3 c0 = from.add(dir.scale(d0));
            Vec3 c1 = from.add(dir.scale(d1));
            for (int i = 0; i < SEGMENTS; i++) {
                double t0 = Math.PI * 2.0 * i / SEGMENTS;
                double t1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
                Vec3 e0 = a.scale(Math.cos(t0)).add(b.scale(Math.sin(t0)));
                Vec3 e1 = a.scale(Math.cos(t1)).add(b.scale(Math.sin(t1)));
                // both windings, so the beam shows from inside and outside
                quad(buffer, pose, c0.add(e0.scale(r0)), c0.add(e1.scale(r0)), c1.add(e1.scale(r1)), c1.add(e0.scale(r1)), alpha0, alpha1);
                quad(buffer, pose, c0.add(e1.scale(r0)), c0.add(e0.scale(r0)), c1.add(e0.scale(r1)), c1.add(e1.scale(r1)), alpha0, alpha1);
            }
        }
    }

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
