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
import java.util.Set;

/**
 * Renders Godzilla (GojiDraw's add-on model and textures, unchanged) in these passes:
 * <ol>
 *   <li>the model (cut-out),</li>
 *   <li>the glowing eyes,</li>
 *   <li>after the atomic breath, the burns round his mouth (head, jaw and upper neck only): raw, blistered flesh
 *       that closes up as it heals (godzilla_burns.png's alpha is the order it heals in: fading the pass cuts
 *       away its rim first), and glows hot for the first seconds,</li>
 *   <li>while charging, the dorsal plates lighting up blue one after another, from the tail tip to the neck, at the
 *       moment the charge animation punches each of them up,</li>
 *   <li>the atomic breath: bone "B", which the charge animation stretches into the beam, drawn as glowing energy.</li>
 * </ol>
 * The add-on draws him at 6.1x (about 35 blocks tall); here he is scaled up evenly, 7.83x, to stand 10 blocks
 * taller: about 45 blocks tall, 22 wide and 76 long, tail and dorsal plates included.
 */
public class GodzillaRenderer extends GeoEntityRenderer<GodzillaEntity> {
    public static final float SCALE = 7.83F;

    private static final ResourceLocation EYES = GodzillaMinusOne.id("textures/entity/godzilla_glow.png");
    private static final ResourceLocation PLATE_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_charged_glow.png");
    /** The inside of his mouth (tools/godzilla_mouth_glow.py), lit as he breathes in and while he fires. */
    private static final ResourceLocation MOUTH_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_mouth_glow.png");
    private static final Set<String> MOUTH_BONES = Set.of("Head", "Jew");
    /** Charge animation seconds: the in-breath begins (jaw opening), and the beam appears (tools/edit_animations.py). */
    private static final float INHALE_START = 10.42F;
    protected static final float FIRE = 14.92F;
    private static final ResourceLocation BEAM_CORE = GodzillaMinusOne.id("textures/entity/godzilla_beam.png");
    private static final ResourceLocation BEAM_SWIRL = new ResourceLocation("textures/entity/creeper/creeper_armor.png");
    private static final ResourceLocation BURNS = GodzillaMinusOne.id("textures/entity/godzilla_burns.png");
    private static final ResourceLocation BURNS_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_burns_glow.png");
    private static final String BEAM_BONE = "B";
    /** The plate at the tip of his tail (Minus Zero's tail strike: punched up and glowing). */
    private static final String TAIL_TIP_BONE = "T22";
    /**
     * Its glow, drawn over itself (additive): {across, up, along} size, as many times the plate's own, and {red, green,
     * blue}: twice at its own size, white-hot, then ever bigger and bluer.
     */
    private static final float[][] TAIL_HALO = {
            {1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F}, {1.0F, 1.0F, 1.0F, 0.8F, 0.95F, 1.0F},
            {2.6F, 1.18F, 1.18F, 0.45F, 0.8F, 1.0F}, {4.5F, 1.4F, 1.4F, 0.3F, 0.6F, 1.0F}, {7.0F, 1.7F, 1.7F, 0.15F, 0.35F, 0.9F}};
    private static final Set<String> BURN_BONES = Set.of("Head", "Jew", "neck2");
    /** Seconds the burnt flesh keeps glowing hot. */
    private static final float BURN_HEAT_SECONDS = 8.0F;

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
        MODEL, EYES, BURNS, PLATES, MOUTH, BEAM, TAIL_TIP
    }

    private Pass pass = Pass.MODEL;
    /** The TAIL_TIP pass's size (across, up, along his tail), as many times the plate's own. */
    private float[] tipHalo = {1.0F, 1.0F, 1.0F};
    private float chargeSeconds;
    private float coolingFade;

    public GodzillaRenderer(EntityRendererProvider.Context context) {
        this(context, new GodzillaModel());
    }

    /** With {@code model} (Minus Zero's: client.render.GodzillaMinusZeroModel). */
    protected GodzillaRenderer(EntityRendererProvider.Context context, GodzillaModel model) {
        super(context, model);
        withScale(SCALE);
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
            case BURNS -> {
                if (burnBones().contains(name)) {
                    super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                }
            }
            case PLATES -> {
                Float time = PLATE_TIMES.get(name);
                if (time != null) {
                    float glow = plateGlow(time);
                    if (glow > 0.01F) {
                        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, glow * red, glow * green, glow * blue, 1.0F);
                    }
                }
            }
            case MOUTH -> {
                if (MOUTH_BONES.contains(name)) {
                    super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                }
            }
            case BEAM -> {
                if (BEAM_BONE.equals(name)) {
                    super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                }
            }
            case TAIL_TIP -> {
                if (TAIL_TIP_BONE.equals(name)) {
                    // a halo: the plate drawn again, bigger (out from its foot on his tail; thicker most of all)
                    poseStack.pushPose();
                    poseStack.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
                    poseStack.scale(this.tipHalo[0], this.tipHalo[1], this.tipHalo[2]);
                    poseStack.translate(-bone.getPivotX() / 16.0F, -bone.getPivotY() / 16.0F, -bone.getPivotZ() / 16.0F);
                    super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                    poseStack.popPose();
                }
            }
        }
    }

    /** Where his breath burns him: his mouth (Minus Zero: his chest too). */
    protected Set<String> burnBones() {
        return BURN_BONES;
    }

    protected ResourceLocation burns() {
        return BURNS;
    }

    protected ResourceLocation burnsGlow() {
        return BURNS_GLOW;
    }

    /** His plates' charge glow (Minus Zero's has his own plates in it). */
    protected ResourceLocation plateGlow() {
        return PLATE_GLOW;
    }

    /** How brightly a plate glows: it lights up over 0.4 s once punched up, and pulses while the charge builds. */
    private float plateGlow(float time) {
        if (this.coolingFade >= 0.0F) {
            return this.coolingFade;
        }
        float x = Mth.clamp((this.chargeSeconds - time) / 0.4F, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x) * (0.85F + 0.15F * Mth.sin(this.chargeSeconds * 7.0F)) * firedGlow(this.chargeSeconds);
    }

    /**
     * Charge animation seconds of Minus Zero's second blast (entity.GodzillaEntity's ticks, less the blend into the
     * charge): his first explosion, when his plates start to dim...
     */
    protected static final float FIRST_BLAST = FIRE + (GodzillaEntity.CHARGE_BLAST_TICK - GodzillaEntity.BEAM_START_TICK) / 20.0F;
    /** ...dark 2 s later, when they start to light up again (and rise)... */
    protected static final float DIMMED = FIRST_BLAST + 2.0F;
    /** ...lit up again 2 s after that, when they punch down and he fires his second beam. */
    protected static final float SURGE = FIRE + (GodzillaEntity.SURGE_TICK - GodzillaEntity.BEAM_START_TICK) / 20.0F;

    /** How much of the plates' glow is left once he's fired (Minus One's stays; Minus Zero's fades, then comes back). */
    protected float firedGlow(float seconds) {
        return 1.0F;
    }

    /** How strong the sharper blue over his plates is (Minus Zero's, once his glow comes back: 0 to 1). */
    protected float sharpGlow(float seconds) {
        return 0.0F;
    }

    /** How strong the second laser is (Minus Zero's: 0 to 1; a second, brighter layer of the beam). */
    protected float secondLaser(float seconds) {
        return 0.0F;
    }

    /**
     * How brightly the inside of his mouth glows: from nothing as he starts to breathe in, slowly at first, then ever
     * faster and brighter until it blazes as he fires (and on while the beam lasts), fading as he cools down.
     */
    private float mouthGlow() {
        if (this.coolingFade >= 0.0F) {
            return this.coolingFade;
        }
        if (this.chargeSeconds >= FIRE) {
            return 1.0F;
        }
        float x = Mth.clamp((this.chargeSeconds - INHALE_START) / (FIRE - INHALE_START), 0.0F, 1.0F);
        float flicker = 0.92F + 0.08F * Mth.sin(this.chargeSeconds * (9.0F + 14.0F * x));
        return x * x * x * flicker;
    }

    private void renderPass(Pass pass, PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, MultiBufferSource buffers,
                            RenderType type, float partialTick, float red, float green, float blue) {
        renderPass(pass, poseStack, entity, model, buffers, type, partialTick, LightTexture.FULL_BRIGHT, red, green, blue, 1.0F);
    }

    private void renderPass(Pass pass, PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, MultiBufferSource buffers,
                            RenderType type, float partialTick, int light, float red, float green, float blue, float alpha) {
        this.pass = pass;
        try {
            reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick, light,
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
            this.godzilla.renderPass(Pass.EYES, poseStack, entity, model, buffers, RenderType.eyes(EYES), partialTick, 1.0F, 1.0F, 1.0F);
            float burns = entity.getBurns(partialTick);
            if (burns > 0.0F) {
                // the wounds close up from their edges; the flesh still writhes as it knits
                float time = entity.tickCount + partialTick;
                float heal = (float) Math.pow(burns, 0.6) * (0.94F + 0.06F * Mth.sin(time * 0.9F));
                this.godzilla.renderPass(Pass.BURNS, poseStack, entity, model, buffers, RenderType.entityTranslucent(this.godzilla.burns()), partialTick,
                        packedLight, 1.0F, 1.0F, 1.0F, heal);
                float seconds = (1.0F - burns) * GodzillaEntity.BURN_TICKS / 20.0F;
                float heat = Mth.clamp(1.0F - seconds / BURN_HEAT_SECONDS, 0.0F, 1.0F);
                if (heat > 0.0F) {
                    heat = heat * heat * (0.85F + 0.15F * Mth.sin(time * 1.7F));
                    this.godzilla.renderPass(Pass.BURNS, poseStack, entity, model, buffers, RenderType.eyes(this.godzilla.burnsGlow()), partialTick,
                            heat, heat, heat);
                }
            }
            if (entity.isTailGlowing()) {
                // Minus Zero's tail strike wound up: the plate at the tip of his tail blazing blue-white, its glow spilling
                // out round it in a broad halo, pulsing
                float pulse = 0.85F + 0.15F * Mth.sin((entity.tickCount + partialTick) * 0.9F);
                RenderType glow = RenderType.eyes(this.godzilla.plateGlow());
                for (float[] layer : TAIL_HALO) {
                    this.godzilla.tipHalo = new float[] {layer[0], layer[1], layer[2]};
                    this.godzilla.renderPass(Pass.TAIL_TIP, poseStack, entity, model, buffers, glow, partialTick,
                            layer[3] * pulse, layer[4] * pulse, layer[5] * pulse);
                }
                this.godzilla.tipHalo = new float[] {1.0F, 1.0F, 1.0F};
            }
            int state = entity.getChargeState();
            if (state == GodzillaEntity.CHARGE_NONE) {
                return;
            }
            this.godzilla.chargeSeconds = entity.getChargeSeconds(partialTick);
            this.godzilla.coolingFade = state == GodzillaEntity.CHARGE_COOLING
                    ? Mth.clamp(1.0F - entity.getCoolingSeconds(partialTick) / 1.5F, 0.0F, 1.0F) : -1.0F;
            this.godzilla.renderPass(Pass.PLATES, poseStack, entity, model, buffers, RenderType.eyes(this.godzilla.plateGlow()), partialTick,
                    1.0F, 1.0F, 1.0F);
            float sharp = this.godzilla.sharpGlow(this.godzilla.chargeSeconds) * (this.godzilla.coolingFade >= 0.0F ? this.godzilla.coolingFade : 1.0F);
            if (sharp > 0.01F) {
                // Minus Zero's glow back, a sharper blue: a second layer over it, cold and bright
                this.godzilla.renderPass(Pass.PLATES, poseStack, entity, model, buffers, RenderType.eyes(this.godzilla.plateGlow()), partialTick,
                        0.35F * sharp, 0.75F * sharp, 1.0F * sharp);
            }
            float mouth = this.godzilla.mouthGlow();
            if (mouth > 0.01F) {
                this.godzilla.renderPass(Pass.MOUTH, poseStack, entity, model, buffers, RenderType.eyes(MOUTH_GLOW), partialTick, mouth, mouth, mouth);
                if (mouth > 0.6F) {
                    // right before he fires: a second, brighter layer, the blue turning white-hot
                    float hot = (mouth - 0.6F) / 0.4F;
                    this.godzilla.renderPass(Pass.MOUTH, poseStack, entity, model, buffers, RenderType.eyes(MOUTH_GLOW), partialTick,
                            hot * 0.8F, hot, hot);
                }
            }
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
            float second = entity.getChargeState() == GodzillaEntity.CHARGE_GLOWING ? this.godzilla.secondLaser(entity.getChargeSeconds(partialTick)) : 0.0F;
            if (second > 0.01F) {
                // Minus Zero's second laser joining the first: the beam's core twice as bright
                this.godzilla.renderPass(Pass.BEAM, poseStack, entity, model, buffers, RenderType.eyes(BEAM_CORE), partialTick,
                        0.6F * second, 0.85F * second, 1.0F * second);
            }
            this.godzilla.renderPass(Pass.BEAM, poseStack, entity, model, buffers,
                    RenderType.energySwirl(BEAM_SWIRL, (t * 0.03F) % 1.0F, (t * 0.02F) % 1.0F), partialTick, 0.3F, 0.7F, 1.0F);
        }
    }
}
