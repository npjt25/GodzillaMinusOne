package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Godzilla Minus Zero: Godzilla's renderer (his plates lighting up in the charge, his glowing mouth, his beam, his
 * burns), scaled up to twice Minus One's height ({@link GodzillaMinusZeroEntity#SCALE}). For now he wears Minus One's
 * model and skin (a placeholder: his own look is still to come).
 */
public class GodzillaMinusZeroRenderer extends GodzillaRenderer {
    public GodzillaMinusZeroRenderer(EntityRendererProvider.Context context) {
        super(context);
        withScale(SCALE * (float) GodzillaMinusZeroEntity.SCALE);
        this.shadowRadius *= (float) GodzillaMinusZeroEntity.SCALE;
    }

    /** Fallen for good, he lies as his collapse left him (no toppling over sideways as other mobs do). */
    @Override
    protected float getDeathMaxRotation(GodzillaEntity animatable) {
        return 0.0F;
    }
}
