package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

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
     * His breath surges: his beam fires, his plates' glow fades for 2 s and comes back a sharper blue, and a second
     * laser joins the first, wider (by SURGE_WIDTH) and hitting twice as often, and his blast goes off with it...
     */
    @Override
    public boolean surges() {
        return true;
    }

    /** ...twice as big as Minus One's (every radius of it: kill, radiation, crater, scorch, ruins, flattening). */
    @Override
    public double blastScale() {
        return 2.0;
    }

    /** Brought down, he falls for good: no Godzilla after him, and he doesn't regenerate. */
    @Override
    protected Defeat defeatOutcome() {
        return Defeat.FALL;
    }

    /** (Bringing him down doesn't unlock a shape: playing as Minus Zero isn't offered.) */
    @Override
    protected boolean creditsDefeat() {
        return false;
    }
}
