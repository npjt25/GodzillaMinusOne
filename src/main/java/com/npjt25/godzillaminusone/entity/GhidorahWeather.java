package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.network.GhidorahWeatherPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * King Ghidorah's own weather, after his entrance (entranceStormDelayTicks after it ends, for
 * entranceStormDurationTicks): no rain, the sky overcast, dark with heavy cloud (each client's
 * client.GhidorahSky), and lightning striking twice as often as in a thunderstorm. The game's own weather is kept
 * clear while it lasts. It's kept with the level's saved data, so it carries on after a reload, and every player in
 * the level is told when it starts and ends (and on joining it).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GhidorahWeather extends SavedData {
    private static final String NAME = GodzillaMinusOne.MODID + "_ghidorah_weather";
    /**
     * In a thunderstorm the game strikes each ticking chunk once in 100000 ticks on average (ServerLevel.tickChunk);
     * his weather strikes twice as often.
     */
    public static final int THUNDERSTORM_ONE_IN = 100000;
    public static final int STRIKE_ONE_IN = THUNDERSTORM_ONE_IN / 2;

    /** Game time it lasts until (-1: none). */
    private long until = -1L;
    /** Strikes so far (this session), for the harness. */
    private static int strikes;

    private GhidorahWeather() {
    }

    public static GhidorahWeather get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GhidorahWeather::load, GhidorahWeather::new, NAME);
    }

    private static GhidorahWeather load(CompoundTag tag) {
        GhidorahWeather weather = new GhidorahWeather();
        weather.until = tag.getLong("Until");
        return weather;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("Until", this.until);
        return tag;
    }

    /** His weather over {@code level} for {@code duration} ticks (only where there's a sky: not the Nether, not the End). */
    public static void start(ServerLevel level, int duration) {
        if (duration <= 0 || !level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            return;
        }
        GhidorahWeather weather = get(level);
        weather.until = level.getGameTime() + duration;
        weather.setDirty();
        level.setWeatherParameters(duration, 0, false, false);     // (none of the game's own rain or thunder meanwhile)
        sync(level);
    }

    /** It ends now (the game tests, the harness). */
    public static void stop(ServerLevel level) {
        GhidorahWeather weather = get(level);
        if (weather.until >= 0) {
            weather.until = -1L;
            weather.setDirty();
            sync(level);
        }
    }

    /** Whether his weather is over {@code level} now. */
    public static boolean active(ServerLevel level) {
        return get(level).until > level.getGameTime();
    }

    /** Game time it lasts until, or -1. */
    public static long until(ServerLevel level) {
        return get(level).until;
    }

    public static int strikes() {
        return strikes;
    }

    private static void sync(ServerLevel level) {
        GhidorahWeatherPacket packet = new GhidorahWeatherPacket(active(level));
        for (ServerPlayer player : level.players()) {
            ModNetwork.send(player, packet);
        }
    }

    private static void syncTo(ServerPlayer player) {
        ModNetwork.send(player, new GhidorahWeatherPacket(active(player.serverLevel())));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        GhidorahWeather weather = get(level);
        if (weather.until < 0) {
            return;
        }
        if (level.getGameTime() >= weather.until) {
            weather.until = -1L;
            weather.setDirty();
            sync(level);
            return;
        }
        if (level.isRaining()) {
            level.setWeatherParameters((int) (weather.until - level.getGameTime()), 0, false, false);
        }
        strikeAround(level);
    }

    /** Lightning: each chunk the players keep ticking, one strike in STRIKE_ONE_IN ticks, as a thunderstorm does at half that. */
    private static void strikeAround(ServerLevel level) {
        int reach = level.getServer().getPlayerList().getSimulationDistance();
        LongSet seen = new LongOpenHashSet();
        for (ServerPlayer player : level.players()) {
            ChunkPos at = player.chunkPosition();
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    int x = at.x + dx;
                    int z = at.z + dz;
                    if (seen.add(ChunkPos.asLong(x, z)) && level.random.nextInt(STRIKE_ONE_IN) == 0 && level.hasChunk(x, z)) {
                        strike(level, x, z);
                    }
                }
            }
        }
    }

    /**
     * A strike somewhere in chunk ({@code cx}, {@code cz}): on the highest thing there, or (as the game's own lightning
     * does) at something living out under the sky near it.
     */
    public static void strike(ServerLevel level, int cx, int cz) {
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.getBlockRandomPos(cx * 16, 0, cz * 16, 15));
        AABB column = new AABB(pos, pos.atY(level.getMaxBuildHeight())).inflate(3.0);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, column,
                e -> e.isAlive() && !e.isSpectator() && level.canSeeSky(e.blockPosition()));
        if (!near.isEmpty()) {
            pos = near.get(level.random.nextInt(near.size())).blockPosition();
        }
        if (pos.getY() < level.getMinBuildHeight()) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(bolt);
        strikes++;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }
}
