package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Godzilla Minus Zero: Godzilla's renderer (his plates lighting up in the charge, his glowing mouth, his beam, his
 * burns) with his own look (client.render.GodzillaMinusZeroModel), scaled up to twice Minus One's height
 * ({@link GodzillaMinusZeroEntity#SCALE}: none of what he has that Minus One hasn't reaches above Minus One's top).
 */
public class GodzillaMinusZeroRenderer extends GodzillaRenderer {
    public GodzillaMinusZeroRenderer(EntityRendererProvider.Context context) {
        super(context, new GodzillaMinusZeroModel());
        withScale(SCALE * (float) GodzillaMinusZeroEntity.SCALE);
        this.shadowRadius *= (float) GodzillaMinusZeroEntity.SCALE;
    }

    private static final ResourceLocation PLATE_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero_charged_glow.png");

    @Override
    protected ResourceLocation plateGlow() {
        return PLATE_GLOW;
    }

    /** Fallen for good, he lies as his collapse left him (no toppling over sideways as other mobs do). */
    @Override
    protected float getDeathMaxRotation(GodzillaEntity animatable) {
        return 0.0F;
    }
}
