package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * Godzilla Minus Zero's look (tools/make_minus_zero.py): Minus One's model, every bone of it (so every animation of
 * Minus One's plays on him), with a second, staggered row of plates along his lower back and tail, armour round his
 * arms and over his shoulders, spines along his tail, and a darker skin (and scorched skin, when he's down).
 */
public class GodzillaMinusZeroModel extends GodzillaModel {
    private static final ResourceLocation MODEL = GodzillaMinusOne.id("geo/entity/godzilla_minus_zero.geo.json");
    private static final ResourceLocation NORMAL = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero.png");
    private static final ResourceLocation DEAD = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero_dead.png");

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
