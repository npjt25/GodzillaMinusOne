package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Set;

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

    /** His breath burns his chest too (tools/make_minus_zero.py: the burns over it). */
    private static final Set<String> BURN_BONES = Set.of("Head", "Jew", "neck2", "Neck", "Chest", "Metalbody");
    private static final ResourceLocation BURNS = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero_burns.png");
    private static final ResourceLocation BURNS_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero_burns_glow.png");

    @Override
    protected Set<String> burnBones() {
        return BURN_BONES;
    }

    @Override
    protected ResourceLocation burns() {
        return BURNS;
    }

    @Override
    protected ResourceLocation burnsGlow() {
        return BURNS_GLOW;
    }

    private static final ResourceLocation PLATE_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_minus_zero_charged_glow.png");

    @Override
    protected ResourceLocation plateGlow() {
        return PLATE_GLOW;
    }

    /**
     * His glow, once he's fired: on through his first beam, until its explosion; dimming out over 2 s; lighting up
     * again over 2 s; on through his second beam.
     */
    @Override
    protected float firedGlow(float seconds) {
        if (seconds < FIRST_BLAST) {
            return 1.0F;
        }
        if (seconds < DIMMED) {
            float x = (seconds - FIRST_BLAST) / (DIMMED - FIRST_BLAST);
            return 1.0F - x * x * (3.0F - 2.0F * x);
        }
        float x = Mth.clamp((seconds - DIMMED) / (SURGE - DIMMED), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** As they light up again, a sharper blue over them, full by his second beam and on through it. */
    @Override
    protected float sharpGlow(float seconds) {
        float x = Mth.clamp((seconds - DIMMED) / (SURGE - DIMMED), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
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
