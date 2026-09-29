package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The add-on's skeleton and animations (geo/entity/godzilla.geo.json, animations/entity/godzilla.animation.json).
 * The visible model is the skinned mesh drawn by {@link GodzillaRenderer}.
 */
public class GodzillaModel extends DefaultedEntityGeoModel<GodzillaEntity> {
    static final ResourceLocation BODY = GodzillaMinusOne.id("textures/entity/godzilla_body.png");

    public GodzillaModel() {
        super(GodzillaMinusOne.id("godzilla"));
    }

    @Override
    public ResourceLocation getTextureResource(GodzillaEntity entity) {
        return BODY;
    }

    /** Tilts the head (and with it the atomic breath, bone "B") towards his target. */
    @Override
    public void setCustomAnimations(GodzillaEntity entity, long instanceId, AnimationState<GodzillaEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        float aim = entity.getAimPitch(animationState.getPartialTick());
        if (aim != 0.0F) {
            getBone("Head").ifPresent(head -> head.setRotX(head.getRotX() + aim * Mth.DEG_TO_RAD));
        }
    }
}
