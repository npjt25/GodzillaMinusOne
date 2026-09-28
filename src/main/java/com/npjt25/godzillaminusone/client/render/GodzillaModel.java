package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** geo/entity/godzilla.geo.json + animations/entity/godzilla.animation.json, texture picked by state. */
public class GodzillaModel extends DefaultedEntityGeoModel<GodzillaEntity> {
    static final ResourceLocation NORMAL = GodzillaMinusOne.id("textures/entity/godzilla.png");
    static final ResourceLocation CHARGED = GodzillaMinusOne.id("textures/entity/godzilla_charged.png");
    static final ResourceLocation DEAD = GodzillaMinusOne.id("textures/entity/godzilla_dead.png");

    public GodzillaModel() {
        super(GodzillaMinusOne.id("godzilla"));
    }

    @Override
    public ResourceLocation getTextureResource(GodzillaEntity entity) {
        return switch (entity.getPhase()) {
            case COLLAPSING, DEAD -> DEAD;
            default -> entity.getChargeState() == GodzillaEntity.CHARGE_GLOWING ? CHARGED : NORMAL;
        };
    }
}
