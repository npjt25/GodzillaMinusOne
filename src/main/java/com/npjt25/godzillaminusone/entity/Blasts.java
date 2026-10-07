package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared explosion logic (the add-on's animation.gojira.mine timeline). */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Blasts {
    /** Entities an explosion going off right now must leave alone (a pilot bailing out of their plane). */
    private static final Set<Entity> SPARED = new HashSet<>();
    /** Set while an explosion whose blocks should vanish rather than drop is going off. */
    private static boolean disintegrate;
    /** Set while that explosion's blocks are being cleared. */
    private static boolean clearing;

    /**
     * A naval mine (or a Shinden flown into Godzilla) going off: huge damage to everything nearby,
     * Shinden pilots (and the one who just bailed out, {@code attacker} when {@code mine} is their plane) and the
     * Shinsei Maru's crew only get scratched, and Godzilla loses a slice of his health and is stunned.
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
            } else if (isPilot(target) || isMinesweeperCrew(target) || (mine instanceof ShindenEntity && target == attacker)) {
                target.hurt(source, Config.PILOT_BLAST_DAMAGE.get().floatValue());
            } else {
                target.hurt(source, clampDamage(Config.MINE_BLAST_DAMAGE.get()));
            }
        }
    }

    /**
     * A plane flown into the ground: a block-breaking explosion of the given power, which spares the pilot bailing
     * out of it. The blocks it breaks just disintegrate: nothing drops (hundreds of dropped items would bring the
     * game to a crawl).
     */
    public static void impact(ServerLevel level, Vec3 pos, float power, Entity source, @Nullable Entity pilot) {
        if (pilot != null) {
            SPARED.add(pilot);
        }
        disintegrate = true;
        try {
            level.explode(source, pos.x, pos.y, pos.z, power, Level.ExplosionInteraction.TNT);
        } finally {
            SPARED.clear();
            disintegrate = false;
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!SPARED.isEmpty()) {
            event.getAffectedEntities().removeIf(SPARED::contains);
        }
        if (disintegrate) {
            // clear the blocks ourselves, without drops, and leave the explosion nothing to break (or drop)
            Level level = event.getLevel();
            clearing = true;
            try {
                for (BlockPos pos : event.getAffectedBlocks()) {
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            } finally {
                clearing = false;
            }
            event.getAffectedBlocks().clear();
        }
    }

    /**
     * What the cleared blocks knock loose goes with them: a lantern or torch hanging from one, a chest's contents.
     * (The explosion hurts its entities after this, so the mobs it kills still drop their loot.)
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (clearing && event.getEntity() instanceof ItemEntity) {
            event.setCanceled(true);
        }
    }

    /**
     * A nuclear blast at {@code impact} (Godzilla's atomic breath landing, or a nuclear bomb): everything within
     * the kill radius dies, there's radiation further out, and (when {@code grounded}) a crater and blast wave;
     * then, for everyone within 1024 blocks, its flash, fireball, dust wave and mushroom cloud
     * (client.render.NuclearCloud: a single cloud, at ground zero), the boom, the shaking and a blurred view.
     *
     * @param burnsOdo an Odo Island Monster caught in it is burnt (and later rises as Godzilla) instead of killed
     * @return ground zero
     */
    public static Vec3 nuclear(ServerLevel level, Vec3 impact, boolean grounded, DamageSource source, @Nullable Entity except,
                               @Nullable LivingEntity breaker, boolean destroyBlocks, boolean burnsOdo, float yaw) {
        for (LivingEntity entity : livingNear(level, impact, Config.ATOMIC_BLAST_KILL_RADIUS.get(), except)) {
            nuclearHit(entity, source, burnsOdo);
        }
        int amplifier = Config.ATOMIC_BLAST_RADIATION_AMPLIFIER.get();
        for (LivingEntity entity : livingNear(level, impact, Config.ATOMIC_BLAST_RADIATION_RADIUS.get(), except)) {
            if (!(entity instanceof GodzillaEntity) && !(entity instanceof OdoIslandMonsterEntity odo && odo.isBurnt())) {
                entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, amplifier), source.getEntity());
            }
        }
        // Ground zero: the ground under the impact, not the tree top or roof that happened to be hit.
        Vec3 zero = impact;
        if (grounded) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(impact.x), Mth.floor(impact.z));
            zero = new Vec3(impact.x, Math.min(impact.y, ground), impact.z);
            if (destroyBlocks) {
                Destruction.atomicBlast(level, breaker, zero, Config.ATOMIC_BLAST_CRATER_RADIUS.get(), Config.ATOMIC_BLAST_DESTRUCTION_RADIUS.get(),
                        Config.ATOMIC_BLAST_RUIN_RADIUS.get());
            }
        }
        ModNetwork.sendNear(level, zero, 1024.0, new FxPacket(FxPacket.Type.ATOMIC_BLAST, zero, yaw, grounded ? 1.0F : 0.0F));
        level.playSound(null, impact.x, impact.y, impact.z, ModSounds.GODZILLA_ATOMIC_BLAST.get(), SoundSource.HOSTILE, 24.0F, 1.0F);
        ModNetwork.shake(level, impact, 640.0, 4.0F, 6.0F);
        ModNetwork.blur(level, impact, 320.0, 1.0F, 0.15F, 2.5F, 4.0F);
        return zero;
    }

    /** Caught in a nuclear blast (or Godzilla's beam). */
    public static void nuclearHit(LivingEntity entity, DamageSource source, boolean burnsOdo) {
        if (entity instanceof GodzillaEntity) {
            return;
        }
        if (burnsOdo && entity instanceof OdoIslandMonsterEntity odo) {
            odo.nuke();
        } else if (isPilot(entity)) {
            entity.hurt(source, 15.0F); // "plot armor"
        } else if (entity instanceof ShindenEntity shinden && shinden.isVehicle()) {
            // a piloted Shinden rides out the blast, like its pilot (only the beam itself brings it down)
        } else {
            entity.hurt(source, 1.0E9F);
        }
    }

    /** Players (or anything) flying a Shinden have the add-on's "plot armor". */
    public static boolean isPilot(Entity entity) {
        return entity.getVehicle() instanceof ShindenEntity;
    }

    /**
     * Aboard the Shinsei Maru: its crew handle naval mines for a living, so a mine going off (say the one they just
     * dropped astern for Godzilla) only scratches them, like a Shinden's pilot.
     */
    public static boolean isMinesweeperCrew(Entity entity) {
        return entity.getVehicle() instanceof ShinseiMaruEntity;
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
