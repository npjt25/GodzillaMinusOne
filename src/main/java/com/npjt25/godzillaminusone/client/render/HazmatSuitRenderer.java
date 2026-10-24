package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.item.HazmatSuitItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * The hazmat suit as worn (tools/make_hazmat.py builds its model and texture): GeckoLib's armour renderer, which
 * shows the hood's bones for the helmet, the coverall's for the chestplate (its harness and air cylinders too), the
 * trousers' for the leggings and the boots' for the boots, each posed like the wearer's own part.
 */
public class HazmatSuitRenderer extends GeoArmorRenderer<HazmatSuitItem> {
    private static final ResourceLocation MODEL = GodzillaMinusOne.id("geo/armor/hazmat_suit.geo.json");
    private static final ResourceLocation TEXTURE = GodzillaMinusOne.id("textures/armor/hazmat_suit.png");
    private static final ResourceLocation ANIMATIONS = GodzillaMinusOne.id("animations/armor/hazmat_suit.animation.json");

    public HazmatSuitRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(HazmatSuitItem animatable) {
                return MODEL;
            }

            @Override
            public ResourceLocation getTextureResource(HazmatSuitItem animatable) {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(HazmatSuitItem animatable) {
                return ANIMATIONS;
            }
        });
    }
}
