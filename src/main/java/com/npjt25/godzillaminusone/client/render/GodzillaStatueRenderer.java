package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaStatueEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Set;

/**
 * A posed Godzilla statue: his model at his size, with his glowing eyes, and, as its stance has it, his tail's plates
 * aglow (as a charge begins) or every plate and the inside of his mouth (fully charged).
 */
public class GodzillaStatueRenderer extends GeoEntityRenderer<GodzillaStatueEntity> {
    private static final ResourceLocation EYES = GodzillaMinusOne.id("textures/entity/godzilla_glow.png");
    private static final ResourceLocation PLATE_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_charged_glow.png");
    private static final ResourceLocation MOUTH_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_mouth_glow.png");
    /** The dorsal plates along the tail (the first to light up when he charges). */
    private static final Set<String> TAIL_PLATES = Set.of("T15", "T16", "T17", "T18", "T19", "T20", "T21", "T22");
    /** All his dorsal plates: down his back and neck too. */
    private static final Set<String> ALL_PLATES = Set.of("T", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11",
            "T12", "T13", "T14", "T15", "T16", "T17", "T18", "T19", "T20", "T21", "T22");
    /** His head and jaw: the mouth's glow is painted on the inside of them (textures/entity/godzilla_mouth_glow.png). */
    private static final Set<String> MOUTH = Set.of("Head", "Jew");

    private enum Pass {
        MODEL, EYES, PLATES, MOUTH
    }

    private Pass pass = Pass.MODEL;
    private Set<String> plates = TAIL_PLATES;

    public GodzillaStatueRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GodzillaMinusOne.id("godzilla")));
        withScale(GodzillaRenderer.SCALE);
        this.shadowRadius = 8.0F;
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    protected void applyRotations(GodzillaStatueEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, entity.getYRot(), partialTick);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        String name = bone.getName();
        if ("B".equals(name) || (this.pass == Pass.PLATES && !this.plates.contains(name)) || (this.pass == Pass.MOUTH && !MOUTH.contains(name))) {
            return; // (bone B is the beam, stowed inside his head)
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private static class GlowLayer extends GeoRenderLayer<GodzillaStatueEntity> {
        private final GodzillaStatueRenderer statue;

        GlowLayer(GodzillaStatueRenderer renderer) {
            super(renderer);
            this.statue = renderer;
        }

        @Override
        public void render(PoseStack poseStack, GodzillaStatueEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            pass(Pass.EYES, poseStack, entity, model, buffers, RenderType.eyes(EYES), partialTick, 1.0F);
            GodzillaStatueEntity.Glow glow = entity.getStance().glow;
            if (glow == GodzillaStatueEntity.Glow.NONE) {
                return;
            }
            float time = entity.tickCount + partialTick;
            float pulse = 0.85F + 0.15F * Mth.sin(time * 0.35F);
            this.statue.plates = glow == GodzillaStatueEntity.Glow.CHARGED ? ALL_PLATES : TAIL_PLATES;
            pass(Pass.PLATES, poseStack, entity, model, buffers, RenderType.eyes(PLATE_GLOW), partialTick, pulse);
            if (glow == GodzillaStatueEntity.Glow.CHARGED) {
                pass(Pass.MOUTH, poseStack, entity, model, buffers, RenderType.eyes(MOUTH_GLOW), partialTick, pulse);
            }
        }

        private void pass(Pass pass, PoseStack poseStack, GodzillaStatueEntity entity, BakedGeoModel model, MultiBufferSource buffers,
                          RenderType type, float partialTick, float glow) {
            this.statue.pass = pass;
            try {
                getRenderer().reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F);
            } finally {
                this.statue.pass = Pass.MODEL;
            }
        }
    }
}
