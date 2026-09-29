package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Shared explosion logic (the add-on's animation.gojira.mine timeline). */
public final class Blasts {
    /**
     * A naval mine (or a Shinden flown into Godzilla) going off: huge damage to everything nearby,
     * Shinden pilots only get scratched, and Godzilla loses a slice of his health and is stunned.
     */
    public static void navalMine(ServerLevel level, Vec3 pos, @Nullable Entity mine, @Nullable Entity attacker) {
        double radius = Config.MINE_BLAST_RADIUS.get();

        ModNetwork.sendNear(level, pos, 512.0, new FxPacket(FxPacket.Type.MINE_BLAST, pos));
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.NAVAL_MINE_EXPLODE.get(), SoundSource.BLOCKS, 12.0F, 1.0F);
        ModNetwork.shake(level, pos, 50.0, 1.0F, 3.0F);
        ModNetwork.blur(level, pos, radius * 2.0, 0.6F, 0.1F, 0.3F, 1.5F);

        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.NAVAL_MINE, mine, attacker);
        for (LivingEntity target : livingNear(level, pos, radius, mine)) {
            if (target instanceof GodzillaEntity godzilla) {
                godzilla.hitByNavalMine(source);
            } else if (isPilot(target)) {
                target.hurt(source, Config.PILOT_BLAST_DAMAGE.get().floatValue());
            } else {
                target.hurt(source, clampDamage(Config.MINE_BLAST_DAMAGE.get()));
            }
        }
    }

    /** Players (or anything) flying a Shinden have the add-on's "plot armor". */
    public static boolean isPilot(Entity entity) {
        return entity.getVehicle() instanceof ShindenEntity;
    }

    /** Living entities whose hitbox is within {@code radius} of {@code pos}. */
    public static List<LivingEntity> livingNear(ServerLevel level, Vec3 pos, double radius, @Nullable Entity except) {
        double r2 = radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(radius),
                e -> e != except && e.isAlive() && distanceSqrToBox(e.getBoundingBox(), pos) <= r2);
    }

    public static double distanceSqrToBox(AABB box, Vec3 pos) {
        double dx = Math.max(0.0, Math.max(box.minX - pos.x, pos.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - pos.y, pos.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - pos.z, pos.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    public static float clampDamage(double damage) {
        return (float) Math.min(damage, 1.0E9);
    }

    private Blasts() {
    }
}
