package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;

/**
 * Godzilla Minus Zero's look (tools/make_minus_zero.py): Minus One's model, every bone of it (so every animation of
 * Minus One's plays on him), with a second, staggered row of plates along his lower back and tail, armour round his
 * arms and over his shoulders, spines along his tail, and a darker skin (and scorched skin, when he's down).
 */
public class GodzillaMinusZeroModel extends GodzillaModel {
    private static final ResourceLocation MODEL = GodzillaMinusOne.id("geo/entity/godzilla_minus_zero.geo.json");
    /** Minus One's animations and his own: his charge with its second blast, his tail strike (tools/make_minus_zero_animations.py). */
    private static final ResourceLocation ANIMATIONS = GodzillaMinusOne.id("animations/entity/godzilla_minus_zero.animation.json");
    private static final ResourceLocation NORMAL = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero.png");
    private static final ResourceLocation DEAD = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero_dead.png");

    /**
     * As in Minus One's, his head aimed at what he's breathing at; and once he fires his second beam
     * (GodzillaMinusZeroRenderer.SURGE seconds into his charge), the beam (bone "B") as much wider as it then hits
     * (GodzillaEntity.SURGE_WIDTH), growing out over 0.15 s.
     */
    @Override
    public void setCustomAnimations(GodzillaEntity entity, long instanceId, AnimationState<GodzillaEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        if (entity.getChargeState() != GodzillaEntity.CHARGE_GLOWING) {
            return;
        }
        float seconds = entity.getChargeSeconds(animationState.getPartialTick());
        float surge = Mth.clamp((seconds - GodzillaMinusZeroRenderer.SURGE_SECONDS) / 0.15F, 0.0F, 1.0F);
        if (surge > 0.0F) {
            float wider = 1.0F + (float) (GodzillaEntity.SURGE_WIDTH - 1.0) * surge;
            getBone("B").ifPresent(beam -> {
                beam.setScaleX(beam.getScaleX() * wider);
                beam.setScaleY(beam.getScaleY() * wider);
            });
        }
    }

    @Override
    public ResourceLocation getAnimationResource(GodzillaEntity entity) {
        return ANIMATIONS;
    }

    @Override
    public ResourceLocation getModelResource(GodzillaEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GodzillaEntity entity) {
        return switch (entity.getPhase()) {
            case COLLAPSING, DEAD -> DEAD;
            default -> NORMAL;
        };
    }
}
