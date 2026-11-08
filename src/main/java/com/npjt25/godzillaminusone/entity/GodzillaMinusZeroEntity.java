package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Godzilla Minus Zero: what Godzilla Minus One becomes when he's brought down (GodzillaEntity's evolve, once per Minus
 * One), or what his own spawn egg brings. He is Minus One in every way but his size, twice Minus One's standing
 * height ({@link #SCALE}: his whole body, and with it every point on it and every reach that comes from it: his mouth
 * and beam, his claws, tail, feet, face; his hitbox is twice Minus One's), and his end: brought down, he collapses and
 * never gets up again (no evolving, no regenerating). His numbers (health, damage, speed, cooldowns) and every move,
 * sound and rule are Minus One's.
 */
public class GodzillaMinusZeroEntity extends GodzillaEntity {
    /** Twice Minus One's standing height (the same model, client.render.GodzillaMinusZeroRenderer, scaled by it). */
    public static final double SCALE = 2.0;

    public GodzillaMinusZeroEntity(EntityType<? extends GodzillaMinusZeroEntity> type, Level level) {
        super(type, level);
        bossBarName(Component.translatable("boss." + GodzillaMinusOne.MODID + ".godzilla_minus_zero"));
    }

    @Override
    public double bodyScale() {
        return SCALE;
    }

    /**
     * His breath has a second blast: his first beam lasts 2 s longer; after its explosion his plates dim for 2 s, light
     * up again for 2 s, then punch down as he fires a second beam (wider by SURGE_WIDTH, hitting twice as often), and 2
     * s after it lands, a second explosion...
     */
    @Override
    public boolean surges() {
        return true;
    }

    /** ...each twice as big as Minus One's (every radius: kill, radiation, crater, scorch, ruins, flattening; the cloud). */
    @Override
    public double blastScale() {
        return 2.0;
    }

    /** Every other spin of his, his tail strike instead (the tip of his tail punched up and glowing, then twice as hard). */
    @Override
    public boolean tailStrikes() {
        return true;
    }

    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlayAndHold("animation.gojira.charge_zero");

    /** His charge with his second blast in it (tools/make_minus_zero_animations.py). */
    @Override
    protected RawAnimation chargeAnimation() {
        return CHARGE;
    }

    /** Brought down, he falls for good: no Godzilla after him, and he doesn't regenerate. */
    @Override
    protected Defeat defeatOutcome() {
        return Defeat.FALL;
    }

    /**
     * Bringing him down unlocks his shape (ReMorphed), as Minus One's does: a player playing as him has his tail strike
     * and his breath with its second blast (entity.PlayerGodzilla).
     */
    @Override
    protected boolean creditsDefeat() {
        return true;
    }
}
