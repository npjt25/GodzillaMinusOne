package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Godzilla Minus Zero and King Ghidorah, rivals.
 * <ul>
 * <li>Within {@link #RANGE} of each other, each makes the other his target before anything else, mobs and players
 * alike (GodzillaEntity's and KingGhidorahEntity's preferRival); Minus Zero, seeing King Ghidorah, roars and then
 * charges his breath at once.</li>
 * <li>Between them, an attack takes {@link #SHARE} of the other's health (neither is hurt by anything but naval mines
 * otherwise); a heavy one ({@link #HEAVY} times as much): Minus Zero's atomic breath (his first beam and its blast, his
 * second beam and its blast), King Ghidorah's gravity beams, his lift and his flying slam. An attack lands once, however
 * long it burns or however many times it touches him: a beam that sweeps over him for seconds is one blow.</li>
 * </ul>
 */
public final class Rivals {
    /** How close they have to be for each to put the other first. */
    public static final double RANGE = 160.0;
    /** What an attack of one takes from the other: a twentieth of his health... */
    public static final float SHARE = 1.0F / 20.0F;
    /** ...and a heavy one four times that. */
    public static final float HEAVY = 4.0F;

    /** The last attack that landed on each of them (whose, and which). */
    private static final Map<LivingEntity, Landed> LANDED = new WeakHashMap<>();

    private record Landed(int attacker, long attack) {
    }

    private Rivals() {
    }

    /** Whether {@code a} and {@code b} are rivals: one Godzilla Minus Zero, the other King Ghidorah. */
    public static boolean rivals(@Nullable Entity a, @Nullable Entity b) {
        return a instanceof GodzillaMinusZeroEntity && b instanceof KingGhidorahEntity
                || a instanceof KingGhidorahEntity && b instanceof GodzillaMinusZeroEntity;
    }

    /** Whether a rival is in the fight: alive, Minus Zero on his feet, King Ghidorah out of the ground. */
    static boolean fighting(LivingEntity e) {
        if (!e.isAlive()) {
            return false;
        }
        if (e instanceof GodzillaEntity godzilla) {
            return godzilla.isActive();
        }
        return !(e instanceof KingGhidorahEntity ghidorah) || !ghidorah.inEntrance();
    }

    /** The nearest rival of {@code self} that's in the fight within {@link #RANGE}, or null (none: or he has no rival). */
    @Nullable
    public static LivingEntity nearest(ServerLevel level, LivingEntity self) {
        Class<? extends LivingEntity> rival = self instanceof GodzillaMinusZeroEntity ? KingGhidorahEntity.class
                : self instanceof KingGhidorahEntity ? GodzillaMinusZeroEntity.class : null;
        if (rival == null) {
            return null;
        }
        LivingEntity best = null;
        double nearest = RANGE * RANGE;
        for (LivingEntity e : level.getEntitiesOfClass(rival, self.getBoundingBox().inflate(RANGE), Rivals::fighting)) {
            double d = e.distanceToSqr(self);
            if (d <= nearest) {
                nearest = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * What a blow takes from {@code victim}, if it's his rival's attack: null if it isn't (his own rules decide: the
     * heat round Minus Zero as he charges, or his footsteps, aren't attacks); 0 if this attack has landed on him
     * already; else his share of his health ({@link #SHARE}, or {@link #HEAVY} times it).
     */
    @Nullable
    public static Float blow(LivingEntity victim, DamageSource source) {
        Entity attacker = source.getEntity();
        if (victim.level().isClientSide || !rivals(victim, attacker) || source.is(ModDamageTypes.RADIATION)
                || source.is(ModDamageTypes.STOMP) || source.is(ModDamageTypes.KAIJU)) {
            return null;
        }
        long attack;
        boolean heavy;
        if (attacker instanceof KingGhidorahEntity ghidorah) {
            attack = ghidorah.rivalAttackId();
            heavy = ghidorah.heavyRivalAttack();
        } else {
            GodzillaEntity zero = (GodzillaEntity) attacker;
            attack = zero.rivalAttackId(source);
            heavy = zero.heavyRivalAttack(source);
        }
        Landed landed = new Landed(attacker.getId(), attack);
        if (landed.equals(LANDED.get(victim))) {
            return 0.0F;
        }
        LANDED.put(victim, landed);
        return victim.getMaxHealth() * SHARE * (heavy ? HEAVY : 1.0F);
    }

    /** The blow itself: from the same attacker, through his rival's armour (so it takes just his share). */
    public static DamageSource damage(LivingEntity victim, DamageSource blow) {
        return ModDamageTypes.source(victim.level(), ModDamageTypes.KAIJU, blow.getDirectEntity(), blow.getEntity());
    }

    /** For the game tests: forget what has landed on him (a new fight). */
    public static void forget(LivingEntity victim) {
        LANDED.remove(victim);
    }
}
