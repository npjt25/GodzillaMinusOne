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
        // the beam (a cube on his mouth, stretched out ahead of it) drawn only as far as the first block in its way:
        // its length and how far it's pushed out ahead (down as well as forward: the charge animation tilts it down
        // from his mouth, and pushes it out along that tilt) cut down alike, so it still starts at his mouth. (Cutting
        // down only how far forward it went left it pushed down as far as ever: a short beam, at something close,
        // came out of his chest.)
        float reach = entity.getBeamReach(animationState.getPartialTick());
        getBone("B").ifPresent(beam -> {
            beam.setPosX(beam.getPosX() * reach);
            beam.setPosY(beam.getPosY() * reach - BEAM_DOWN);
            beam.setPosZ(beam.getPosZ() * reach - BEAM_FORWARD);
            beam.setScaleZ(beam.getScaleZ() * reach);
        });
    }

    /** How far the beam is set down and forward in his head (model pixels), into his open mouth. */
    private static final float BEAM_DOWN = 1.5F;
    private static final float BEAM_FORWARD = 7.0F;
}
