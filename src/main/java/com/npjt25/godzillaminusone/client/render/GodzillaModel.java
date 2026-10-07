package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** geo/entity/godzilla.geo.json + animations/entity/godzilla.animation.json; the scorched skin once he collapses. */
public class GodzillaModel extends DefaultedEntityGeoModel<GodzillaEntity> {
    static final ResourceLocation NORMAL = GodzillaMinusOne.id("textures/entity/godzilla.png");
    static final ResourceLocation DEAD = GodzillaMinusOne.id("textures/entity/godzilla_dead.png");

    public GodzillaModel() {
        super(GodzillaMinusOne.id("godzilla"));
    }

    @Override
    public ResourceLocation getTextureResource(GodzillaEntity entity) {
        return switch (entity.getPhase()) {
            case COLLAPSING, DEAD -> DEAD;
            default -> NORMAL;
        };
    }

    /**
     * Points the head (and with it the atomic breath, bone "B") at his target: tilted up or down, turned left or right.
     * The beam is set down and forward in his head, so that it pours out between his jaws (not out of the top of his
     * snout).
     */
    @Override
    public void setCustomAnimations(GodzillaEntity entity, long instanceId, AnimationState<GodzillaEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        float aim = entity.getAimPitch(animationState.getPartialTick());
        float yaw = entity.getAimYaw(animationState.getPartialTick());
        if (aim != 0.0F || yaw != 0.0F) {
            getBone("Head").ifPresent(head -> {
                head.setRotX(head.getRotX() + aim * Mth.DEG_TO_RAD);
                head.setRotY(head.getRotY() - yaw * Mth.DEG_TO_RAD);
            });
        }
        getBone("B").ifPresent(beam -> {
            beam.setPosY(beam.getPosY() - BEAM_DOWN);
            beam.setPosZ(beam.getPosZ() - BEAM_FORWARD);
        });
    }

    /** How far the beam is set down and forward in his head (model pixels), into his open mouth. */
    private static final float BEAM_DOWN = 1.5F;
    private static final float BEAM_FORWARD = 7.0F;
}
