package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.NuclearBombEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The falling bomb: a plain model, built nose-down. */
public class NuclearBombRenderer extends GeoEntityRenderer<NuclearBombEntity> {
    public NuclearBombRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GodzillaMinusOne.id("nuclear_bomb")));
        this.shadowRadius = 0.0F;
    }
}
