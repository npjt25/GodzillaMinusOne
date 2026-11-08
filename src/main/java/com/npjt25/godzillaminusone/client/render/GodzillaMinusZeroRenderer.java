package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

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

    /** Charge animation seconds his second laser joins the first (for his model: the beam widening). */
    static final float SURGE_SECONDS = SURGE;

    private static final ResourceLocation PLATE_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero_charged_glow.png");

    @Override
    protected ResourceLocation plateGlow() {
        return PLATE_GLOW;
    }

    /** His glow fades out over the 2 s after he fires, then comes back in 0.3 s as his second laser joins the first. */
    @Override
    protected float firedGlow(float seconds) {
        if (seconds < FIRE) {
            return 1.0F;
        }
        if (seconds < SURGE_DIM) {
            float x = (seconds - FIRE) / (SURGE_DIM - FIRE);
            return 1.0F - x * x * (3.0F - 2.0F * x);
        }
        return Mth.clamp((seconds - SURGE_DIM) / (SURGE - SURGE_DIM), 0.0F, 1.0F);
    }

    @Override
    protected float sharpGlow(float seconds) {
        return Mth.clamp((seconds - SURGE_DIM) / (SURGE - SURGE_DIM), 0.0F, 1.0F);
    }

    @Override
    protected float secondLaser(float seconds) {
        return Mth.clamp((seconds - SURGE) / 0.15F, 0.0F, 1.0F);
    }

    /** Fallen for good, he lies as his collapse left him (no toppling over sideways as other mobs do). */
    @Override
    protected float getDeathMaxRotation(GodzillaEntity animatable) {
        return 0.0F;
    }
}
