package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
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
     * His middle row's glow (his first charge's), once he's fired: on through his first beam, until its explosion, then
     * dimming out over 2 s, and dark from then on: his second charge is the rows either side of it (sidePlateTimes).
     */
    @Override
    protected float firedGlow(float seconds) {
        if (seconds < FIRST_BLAST) {
            return 1.0F;
        }
        float x = Mth.clamp((seconds - FIRST_BLAST) / (DIMMED - FIRST_BLAST), 0.0F, 1.0F);
        return 1.0F - x * x * (3.0F - 2.0F * x);
    }

    /**
     * His second charge: the rows of plates either side of his middle one (tools/make_minus_zero.py's T12_side ...),
     * punching up one after another from the end of his tail as his middle row goes dark (tools/make_minus_zero_
     * animations.py's SIDE_ORDER, SIDE_STEP apart from DIMMED + 0.05 s), each lighting as it rises, and on through his
     * second beam.
     */
    private static final String[] SIDE_ORDER = {"T22", "T21", "T20", "T19", "T18", "T17", "T16", "T15", "T12"};
    private static final float SIDE_STEP = 0.1F;
    private static final Map<String, Float> SIDE_TIMES = sideTimes();

    private static Map<String, Float> sideTimes() {
        Map<String, Float> times = new HashMap<>();
        for (int i = 0; i < SIDE_ORDER.length; i++) {
            times.put(SIDE_ORDER[i] + "_side", DIMMED + 0.05F + i * SIDE_STEP);
        }
        return Map.copyOf(times);
    }

    @Override
    protected Map<String, Float> sidePlateTimes() {
        return SIDE_TIMES;
    }

    /** As his second row lights up, a sharper blue over it, full by his second beam and on through it. */
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
