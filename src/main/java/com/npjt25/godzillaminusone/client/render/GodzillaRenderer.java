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
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;

/**
 * Renders Godzilla (the add-on's model, with the mod's extra detail) in four passes:
 * <ol>
 *   <li>the model (cut-out),</li>
 *   <li>the glowing eyes,</li>
 *   <li>while charging, the dorsal plates lighting up blue one after another, from the tail tip to the neck, at the
 *       moment the charge animation punches each of them up,</li>
 *   <li>the atomic breath: bone "B", which the charge animation stretches into the beam, drawn as glowing energy.</li>
 * </ol>
 * The add-on's model is scaled 7.13x across and 7.83x up: 10 blocks taller (45) and 10 blocks longer than the
 * add-on's Godzilla.
 */
public class GodzillaRenderer extends GeoEntityRenderer<GodzillaEntity> {
    public static final float SCALE_WIDTH = 7.13F;
    public static final float SCALE_HEIGHT = 7.83F;

    private static final ResourceLocation EYES = GodzillaMinusOne.id("textures/entity/godzilla_glow.png");
    private static final ResourceLocation PLATE_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_charged_glow.png");
    private static final ResourceLocation BEAM_CORE = GodzillaMinusOne.id("textures/entity/godzilla_beam.png");
    private static final ResourceLocation BEAM_SWIRL = new ResourceLocation("textures/entity/creeper/creeper_armor.png");
    private static final String BEAM_BONE = "B";

    /** Dorsal plate bones and the time (s into the charge animation) each one is punched up. */
    private static final Map<String, Float> PLATE_TIMES = Map.ofEntries(
            Map.entry("T22", 0.08F), Map.entry("T21", 0.17F), Map.entry("T20", 0.25F), Map.entry("T19", 3.29F),
            Map.entry("T18", 4.38F), Map.entry("T17", 5.12F), Map.entry("T16", 5.58F), Map.entry("T15", 6.0F),
            Map.entry("T12", 6.29F), Map.entry("T13", 6.29F), Map.entry("T14", 6.29F),
            Map.entry("T7", 6.67F), Map.entry("T8", 6.67F), Map.entry("T9", 6.67F),
            Map.entry("T4", 7.21F), Map.entry("T5", 7.21F), Map.entry("T6", 7.21F),
            Map.entry("T", 7.88F), Map.entry("T2", 7.88F), Map.entry("T3", 7.88F),
            Map.entry("T10", 8.54F), Map.entry("T11", 8.54F));

    private enum Pass {
        MODEL, EYES, PLATES, BEAM
    }

    private Pass pass = Pass.MODEL;
    private float chargeSeconds;
    private float coolingFade;

    public GodzillaRenderer(EntityRendererProvider.Context context) {
        super(context, new GodzillaModel());
        withScale(SCALE_WIDTH, SCALE_HEIGHT);
        this.shadowRadius = 8.0F;
        addRenderLayer(new GlowLayer(this));
        addRenderLayer(new BeamLayer(this));
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        String name = bone.getName();
        switch (this.pass) {
            case MODEL, EYES -> {
                if (!BEAM_BONE.equals(name)) {
                    super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                }
            }
            case PLATES -> {
                Float time = PLATE_TIMES.get(name);
                if (time != null) {
                    float glow = plateGlow(time);
                    if (glow > 0.01F) {
                        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, glow, glow, glow, 1.0F);
                    }
                }
            }
            case BEAM -> {
                if (BEAM_BONE.equals(name)) {
                    super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                }
            }
        }
    }

    /** How brightly a plate glows: it lights up over 0.4 s once punched up, and pulses while the charge builds. */
    private float plateGlow(float time) {
        if (this.coolingFade >= 0.0F) {
            return this.coolingFade;
        }
        float x = Mth.clamp((this.chargeSeconds - time) / 0.4F, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x) * (0.85F + 0.15F * Mth.sin(this.chargeSeconds * 7.0F));
    }

    private void renderPass(Pass pass, PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, MultiBufferSource buffers,
                            RenderType type, float partialTick, float red, float green, float blue) {
        this.pass = pass;
        try {
            reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, red, green, blue, 1.0F);
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
            this.godzilla.renderPass(Pass.EYES, poseStack, entity, model, buffers, RenderType.eyes(EYES), partialTick, 1.0F, 1.0F, 1.0F);
            int state = entity.getChargeState();
            if (state == GodzillaEntity.CHARGE_NONE) {
                return;
            }
            this.godzilla.chargeSeconds = entity.getChargeSeconds(partialTick);
            this.godzilla.coolingFade = state == GodzillaEntity.CHARGE_COOLING
                    ? Mth.clamp(1.0F - entity.getCoolingSeconds(partialTick) / 1.5F, 0.0F, 1.0F) : -1.0F;
            this.godzilla.renderPass(Pass.PLATES, poseStack, entity, model, buffers, RenderType.eyes(PLATE_GLOW), partialTick, 1.0F, 1.0F, 1.0F);
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
            this.godzilla.renderPass(Pass.BEAM, poseStack, entity, model, buffers, RenderType.eyes(BEAM_CORE), partialTick, 1.0F, 1.0F, 1.0F);
            this.godzilla.renderPass(Pass.BEAM, poseStack, entity, model, buffers,
                    RenderType.energySwirl(BEAM_SWIRL, (t * 0.03F) % 1.0F, (t * 0.02F) % 1.0F), partialTick, 0.3F, 0.7F, 1.0F);
        }
    }
}
