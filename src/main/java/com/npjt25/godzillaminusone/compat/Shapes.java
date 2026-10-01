package com.npjt25.godzillaminusone.compat;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Optional support for Walkers (and ReMorphed, which builds on it), both by To_Craft (MIT): a player can take
 * Godzilla's shape and play as him (entity.PlayerGodzilla). Neither is needed: everything here is looked up by
 * name, once, and does nothing when they aren't installed.
 *
 * <ul>
 *   <li>Walkers' {@code tocraft.walkers.api.PlayerShape.getCurrentShape(Player)}: the creature a player has turned into
 *       (Walkers draws it in the player's place, and gives the player its size).</li>
 *   <li>ReMorphed unlocks a shape when you kill the creature, but Godzilla doesn't die: he collapses and regenerates.
 *       So bringing him down counts as the kill ({@code tocraft.remorphed.impl.PlayerMorph.addKill}).</li>
 * </ul>
 */
public final class Shapes {
    private static boolean looked;
    @Nullable
    private static Method currentShape;
    @Nullable
    private static Method shapeTypeOf;
    @Nullable
    private static Method addKill;

    private static void lookUp() {
        if (looked) {
            return;
        }
        looked = true;
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("walkers")) {
            return;
        }
        try {
            currentShape = Class.forName("tocraft.walkers.api.PlayerShape").getMethod("getCurrentShape", Player.class);
            Class<?> shapeType = Class.forName("tocraft.walkers.api.variant.ShapeType");
            shapeTypeOf = shapeType.getMethod("from", LivingEntity.class);
            if (mods.isLoaded("remorphed")) {
                addKill = Class.forName("tocraft.remorphed.impl.PlayerMorph").getMethod("addKill", Player.class, shapeType);
            }
            GodzillaMinusOne.LOGGER.info("Walkers found: players can play as Godzilla{}", addKill != null ? " (ReMorphed too)" : "");
        } catch (ReflectiveOperationException | LinkageError e) {
            GodzillaMinusOne.LOGGER.warn("Walkers is installed, but its API isn't what this version expects: playing as Godzilla is off", e);
            currentShape = null;
            addKill = null;
        }
    }

    /** The creature {@code player} has turned into, if any (with Walkers). */
    @Nullable
    public static LivingEntity shapeOf(Player player) {
        lookUp();
        if (currentShape == null) {
            return null;
        }
        try {
            return currentShape.invoke(null, player) instanceof LivingEntity shape ? shape : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The Godzilla {@code player} has become (with Walkers), if they have. */
    @Nullable
    public static GodzillaEntity godzillaOf(Player player) {
        return shapeOf(player) instanceof GodzillaEntity godzilla ? godzilla : null;
    }

    /** {@code player} brought Godzilla down: with ReMorphed, that unlocks his shape, as killing anything else would. */
    public static void creditDefeat(Player player, GodzillaEntity godzilla) {
        lookUp();
        if (addKill == null || shapeTypeOf == null) {
            return;
        }
        try {
            Object type = shapeTypeOf.invoke(null, godzilla);
            if (type != null) {
                addKill.invoke(null, player, type);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            GodzillaMinusOne.LOGGER.debug("Couldn't credit Godzilla's defeat to ReMorphed", e);
        }
    }

    private Shapes() {
    }
}
