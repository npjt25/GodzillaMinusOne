package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a player playing as Godzilla (entity.PlayerGodzilla) gets about, so he never gets stuck: each player chooses
 * (client.PlayerGodzillaControls' key, M by default, or the config screen; client.ClientConfig) between
 *
 * <ul>
 *   <li>auto-climb (the default): he steps straight up onto anything up to playerGodzillaStepHeight blocks high (walls,
 *       roofs, ledges, cliffs) as a player steps up a slab, and</li>
 *   <li>walk through: he walks through anything, buildings and hills alike, as if it weren't there (still standing on
 *       the ground and falling as usual; never smothered inside a wall).</li>
 * </ul>
 *
 * <p>Walking through: his own client lets him move through blocks sideways (mixin.EntityCollideMixin) and doesn't push
 * him out of them (mixin.LocalPlayerMixin); the server takes his moves as they come (the player's noPhysics, between
 * his ticks, as for a spectator: no "moved wrongly" snapping him back) and spares him suffocating.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GodzillaStride {
    private static final UUID STEP_ID = UUID.fromString("2b7c9f4e-6a1d-4c3b-8e5f-9d0a1b2c3e4f");
    /** Who walks through things (by player): set by their client (network.GodzillaStridePacket). */
    private static final Map<UUID, Boolean> WALK_THROUGH = new ConcurrentHashMap<>();

    /** {@code player} chooses to walk through things (true) or to climb them (false). */
    public static void setWalkThrough(Player player, boolean on) {
        WALK_THROUGH.put(player.getUUID(), on);
    }

    /** Whether {@code player} is playing as Godzilla and walks through things. */
    public static boolean walksThrough(Player player) {
        return WALK_THROUGH.getOrDefault(player.getUUID(), Boolean.FALSE) && PlayerGodzilla.isGodzilla(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        boolean godzilla = player.isAlive() && PlayerGodzilla.isGodzilla(player);
        boolean through = godzilla && WALK_THROUGH.getOrDefault(player.getUUID(), Boolean.FALSE);
        if (through) {
            player.noPhysics = true; // (until his next tick: his moves in between are taken as they come)
        }
        climb(player, godzilla && !through);
    }

    /** Climbing: he steps up as high as playerGodzillaStepHeight (on top of a player's own step). */
    private static void climb(ServerPlayer player, boolean on) {
        AttributeInstance step = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (step == null) {
            return;
        }
        double height = Config.PLAYER_GODZILLA_STEP_HEIGHT.get();
        AttributeModifier current = step.getModifier(STEP_ID);
        if (on && (current == null || current.getAmount() != height)) {
            step.removeModifier(STEP_ID);
            step.addTransientModifier(new AttributeModifier(STEP_ID, "Godzilla climbing", height, AttributeModifier.Operation.ADDITION));
        } else if (!on && current != null) {
            step.removeModifier(STEP_ID);
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && event.getSource().is(DamageTypes.IN_WALL) && walksThrough(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            WALK_THROUGH.remove(event.getEntity().getUUID());
        }
    }

    private GodzillaStride() {
    }
}
