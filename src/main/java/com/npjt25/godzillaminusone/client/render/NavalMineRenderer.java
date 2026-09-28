package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NavalMineRenderer extends GeoEntityRenderer<NavalMineEntity> {
    public NavalMineRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GodzillaMinusOne.id("naval_mine")));
        withScale(1.3F); // add-on: minecraft:scale 1.3
        this.shadowRadius = 1.5F;
    }
}
