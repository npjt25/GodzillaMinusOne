package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.compat.Shapes;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.network.ShapeAnimPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player playing as the Odo Island Monster (Walkers / ReMorphed: compat.Shapes), server side: he moves at
 * playerOdoBlocksPerSecond (12 blocks a second on level ground) whenever he moves, sprinting or not (no key to hold),
 * and a left click (client.PlayerOdoControls) is his bite (his "bite" animation, seen by everyone; the jaws close 0.75 s in and hurt what's in
 * front of them, playerOdoBiteDamage).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOdo {
    private static final UUID RUN_ID = UUID.fromString("0b6e3c52-8f1d-4a7e-b0c9-5e2d7f4a8b13");
    /** A player's walking speed attribute (0.1) makes 4.317 blocks a second on level ground; it scales in proportion. */
    private static final double BLOCKS_PER_SECOND_PER_SPEED = 43.17;
    /** Ticks between bites, and into one before the jaws close (animation.odo.bite: 0.75 s). */
    private static final int BITE_COOLDOWN = 20;
    private static final int BITE_HIT = 15;
    private static final double BITE_REACH = 6.5;
    private static final double BITE_RADIUS = 4.5;

    private static final Map<UUID, Integer> NEXT_BITE = new HashMap<>();
    private static final Map<UUID, Integer> BITE_LANDS = new HashMap<>();

    public static boolean isOdo(Player player) {
        return Shapes.shapeOf(player) instanceof OdoIslandMonsterEntity;
    }

    /** A left click: his bite, if he's the Odo Island Monster and it's ready. */
    public static void bite(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !isOdo(player)) {
            return;
        }
        UUID id = player.getUUID();
        if (player.tickCount < NEXT_BITE.getOrDefault(id, 0)) {
            return;
        }
        NEXT_BITE.put(id, player.tickCount + BITE_COOLDOWN);
        BITE_LANDS.put(id, player.tickCount + BITE_HIT);
        ModNetwork.sendNear(player.serverLevel(), player.position(), 256.0, new ShapeAnimPacket(player.getId(), "bite"));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        boolean odo = player.isAlive() && isOdo(player);
        if (!odo) {
            BITE_LANDS.remove(id);
        }
        setRunSpeed(player, odo);
        Integer lands = BITE_LANDS.get(id);
        if (lands != null && player.tickCount >= lands) {
            BITE_LANDS.remove(id);
            jaws(player);
        }
    }

    /** The jaws close in front of him: whatever is there is bitten. */
    private static void jaws(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 jaws = player.getEyePosition().add(player.getLookAngle().scale(BITE_REACH));
        DamageSource source = level.damageSources().playerAttack(player);
        float damage = Config.PLAYER_ODO_BITE_DAMAGE.get().floatValue();
        for (LivingEntity entity : Blasts.livingNear(level, jaws, BITE_RADIUS, player)) {
            entity.hurt(source, damage);
        }
        ModNetwork.shake(level, jaws, 24.0, 0.2F, 0.5F);
    }

    /**
     * As him, his speed attribute is made whatever gives playerOdoBlocksPerSecond blocks a second (whatever else is on
     * it, sprinting included: the modifier makes up the difference), changed only when that difference changes; changed
     * back, it's taken off.
     */
    private static void setRunSpeed(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(RUN_ID);
        if (!on) {
            if (current != null) {
                speed.removeModifier(RUN_ID);
            }
            return;
        }
        double have = current != null ? current.getAmount() : 0.0;
        double without = speed.getValue() / (1.0 + have);
        if (without <= 1.0E-6) {
            return;
        }
        double wanted = Config.PLAYER_ODO_SPEED.get() / BLOCKS_PER_SECOND_PER_SPEED / without - 1.0;
        if (current == null || Math.abs(wanted - have) > 0.005) {
            speed.removeModifier(RUN_ID);
            speed.addTransientModifier(new AttributeModifier(RUN_ID, "Odo Island Monster's pace", wanted,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** For the game tests: his speed attribute as his pace makes it. */
    public static double runSpeedAttribute() {
        return Config.PLAYER_ODO_SPEED.get() / BLOCKS_PER_SECOND_PER_SPEED;
    }

    private static void forget(Player player) {
        NEXT_BITE.remove(player.getUUID());
        BITE_LANDS.remove(player.getUUID());
        if (player instanceof ServerPlayer serverPlayer) {
            setRunSpeed(serverPlayer, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        forget(event.getEntity());
    }

    private PlayerOdo() {
    }
}
