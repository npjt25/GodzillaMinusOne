package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Renders Godzilla at the add-on's scale (6.1x) in three passes, mirroring its two Bedrock render controllers:
 * <ol>
 *   <li>the model (cut-out),</li>
 *   <li>a glow pass for the emissive parts (eyes; the dorsal plates while charging),</li>
 *   <li>the atomic breath: bone "B", which the charge animation stretches into the beam, drawn as glowing
 *       blue energy (Bedrock's charged_creeper material).</li>
 * </ol>
 */
public class GodzillaRenderer extends GeoEntityRenderer<GodzillaEntity> {
    private static final ResourceLocation GLOW = GodzillaMinusOne.id("textures/entity/godzilla_glow.png");
    private static final ResourceLocation CHARGED_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_charged_glow.png");
    private static final ResourceLocation BEAM_CORE = GodzillaMinusOne.id("textures/entity/godzilla_beam.png");
    private static final ResourceLocation BEAM_SWIRL = new ResourceLocation("textures/entity/creeper/creeper_armor.png");
    private static final String BEAM_BONE = "B";

    private enum Pass {
        MODEL, GLOW, BEAM
    }

    private Pass pass = Pass.MODEL;

    public GodzillaRenderer(EntityRendererProvider.Context context) {
        super(context, new GodzillaModel());
        withScale(6.1F);
        this.shadowRadius = 6.0F;
        addRenderLayer(new GlowLayer(this));
        addRenderLayer(new BeamLayer(this));
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        boolean beamBone = BEAM_BONE.equals(bone.getName());
        if (beamBone == (this.pass == Pass.BEAM)) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    private void renderPass(Pass pass, PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, MultiBufferSource buffers,
                            RenderType type, float partialTick, float red, float green, float blue, float alpha) {
        this.pass = pass;
        try {
            reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, red, green, blue, alpha);
        } finally {
            this.pass = Pass.MODEL;
        }
    }

    private static class GlowLayer extends GeoRenderLayer<GodzillaEntity> {
        private final GodzillaRenderer godzilla;

        GlowLayer(GodzillaRenderer renderer) {
            super(renderer);
            this.godzilla = renderer;
        }

        @Override
        public void render(PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            GodzillaEntity.Phase phase = entity.getPhase();
            if (phase == GodzillaEntity.Phase.COLLAPSING || phase == GodzillaEntity.Phase.DEAD) {
                return;
            }
            ResourceLocation texture = entity.getChargeState() == GodzillaEntity.CHARGE_GLOWING ? CHARGED_GLOW : GLOW;
            this.godzilla.renderPass(Pass.GLOW, poseStack, entity, model, buffers, RenderType.eyes(texture), partialTick, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static class BeamLayer extends GeoRenderLayer<GodzillaEntity> {
        private final GodzillaRenderer godzilla;

        BeamLayer(GodzillaRenderer renderer) {
            super(renderer);
            this.godzilla = renderer;
        }

        @Override
        public void render(PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!entity.isCharging()) {
                return;
            }
            float t = entity.tickCount + partialTick;
            this.godzilla.renderPass(Pass.BEAM, poseStack, entity, model, buffers, RenderType.eyes(BEAM_CORE), partialTick, 1.0F, 1.0F, 1.0F, 1.0F);
            this.godzilla.renderPass(Pass.BEAM, poseStack, entity, model, buffers,
                    RenderType.energySwirl(BEAM_SWIRL, (t * 0.03F) % 1.0F, (t * 0.02F) % 1.0F), partialTick, 0.3F, 0.7F, 1.0F, 1.0F);
        }
    }
}
