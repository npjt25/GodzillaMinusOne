package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The atomic breath levelling the land: everything above a given height (Y 60) within hundreds of blocks of where it
 * lands, trees, grass, buildings and hills alike, is simply erased (nothing drops, so the game doesn't grind to a
 * halt under a sea of items). It sweeps outwards from the impact a chunk at a time, a few milliseconds' work each tick.
 * Chunks that aren't loaded yet are flattened when they load: the flattened areas are saved with the world, along with
 * which of their chunks are done (so what is built there afterwards stays).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Flattening {
    /** Blocks the sweep advances per tick. */
    private static final double SPEED = 12.0;
    /** Server time spent flattening per tick at most (nanoseconds), so a mountain range doesn't freeze the game. */
    private static final long TIME_BUDGET = 8_000_000L;
    /** The most recent flattened areas kept (older ones are forgotten: their unloaded chunks are left as they are). */
    private static final int MAX_ZONES = 16;
    private static final String DATA_NAME = GodzillaMinusOne.MODID + "_flattened";

    private static final Map<ServerLevel, Work> WORK = new IdentityHashMap<>();

    /** Erases everything above {@code y} within {@code radius} blocks of {@code center}, sweeping outwards from it. */
    public static void start(ServerLevel level, Vec3 center, int radius, int y) {
        if (radius <= 0) {
            return;
        }
        Zone zone = zones(level).add(new Zone(Mth.floor(center.x), Mth.floor(center.z), radius, y, new LongOpenHashSet()));
        work(level).waves.add(new Wave(zone));
    }

    /** Whether nothing is left to flatten in this level right now (for the game tests). */
    public static boolean idle(ServerLevel level) {
        Work work = WORK.get(level);
        return work == null || work.waves.isEmpty() && work.pending.isEmpty() && work.current == null;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            Work work = WORK.get(level);
            if (work != null) {
                work.tick(level);
            }
        }
    }

    /** A chunk of a flattened area that wasn't loaded when the blast swept past gets its turn now. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        Zones zones = zones(level);
        if (zones.list.isEmpty()) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        for (Zone zone : zones.list) {
            if (zone.covers(pos) && !zone.done.contains(pos.toLong())) {
                work(level).pending.add(new Pending(zone, pos));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WORK.clear();
    }

    private static Work work(ServerLevel level) {
        return WORK.computeIfAbsent(level, l -> new Work());
    }

    private static Zones zones(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Zones::load, Zones::new, DATA_NAME);
    }

    /** The flattening going on in one level. */
    private static final class Work {
        final List<Wave> waves = new ArrayList<>();
        final Deque<Pending> pending = new ArrayDeque<>();
        /** The chunk being flattened (a big one takes a few ticks). */
        @Nullable
        ChunkJob current;
        /** Which sweep's turn it is. */
        int turn;

        void tick(ServerLevel level) {
            for (Wave wave : this.waves) {
                wave.front += SPEED;
                wave.zone.front = wave.front;
            }
            long deadline = System.nanoTime() + TIME_BUDGET;
            while (System.nanoTime() < deadline) {
                if (this.current == null) {
                    this.current = nextJob(level);
                    if (this.current == null) {
                        break;
                    }
                }
                if (this.current.step(level, deadline)) {
                    this.current = null;
                }
            }
        }

        /**
         * The next chunk to flatten: the sweeps' first (taking turns, so a big sweep doesn't hold up another), then
         * the chunks that loaded after a sweep went past.
         */
        @Nullable
        private ChunkJob nextJob(ServerLevel level) {
            for (int tried = 0; tried < this.waves.size(); tried++) {
                this.turn = this.turn % this.waves.size();
                Wave wave = this.waves.get(this.turn);
                ChunkJob job = null;
                while (job == null && wave.next < wave.chunks.length && wave.distances[wave.next] <= wave.front) {
                    ChunkPos pos = new ChunkPos(wave.chunks[wave.next++]);
                    // (a chunk that isn't loaded is done when it loads)
                    if (!wave.zone.done.contains(pos.toLong()) && level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                        job = new ChunkJob(wave.zone, pos);
                    }
                }
                if (wave.next >= wave.chunks.length) {
                    wave.zone.front = Double.MAX_VALUE;
                    this.waves.remove(this.turn);
                    tried--;
                } else {
                    this.turn++;
                }
                if (job != null) {
                    return job;
                }
                if (this.waves.isEmpty()) {
                    break;
                }
            }
            while (!this.pending.isEmpty()) {
                Pending next = this.pending.poll();
                // (a chunk the sweep hasn't reached yet is left to it)
                if (!next.zone.done.contains(next.pos.toLong()) && next.zone.distance(next.pos) <= next.zone.front) {
                    return new ChunkJob(next.zone, next.pos);
                }
            }
            return null;
        }
    }

    private record Pending(Zone zone, ChunkPos pos) {
    }

    /**
     * Erasing everything above the zone's height in one chunk's columns within its radius. The blocks are written
     * straight into the chunk (with the lighting and the players' view kept up to date), skipping what a block
     * placed by hand sets off, from neighbour updates to every mob rethinking its path, which over a whole landscape
     * would bring the server to its knees.
     */
    private static final class ChunkJob {
        final Zone zone;
        final ChunkPos pos;
        int column;
        boolean changed;

        ChunkJob(Zone zone, ChunkPos pos) {
            this.zone = zone;
            this.pos = pos;
        }

        /** Works through the columns until done (returns true) or out of time. */
        boolean step(ServerLevel level, long deadline) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(this.pos.x, this.pos.z);
            if (chunk == null) {
                return true; // unloaded: it gets its turn when it loads again
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            long r2 = (long) this.zone.radius * this.zone.radius;
            while (this.column < 256) {
                int lx = this.column >> 4;
                int lz = this.column & 15;
                this.column++;
                int x = this.pos.getMinBlockX() + lx;
                int z = this.pos.getMinBlockZ() + lz;
                long dx = x - this.zone.x;
                long dz = z - this.zone.z;
                if (dx * dx + dz * dz <= r2) {
                    flattenColumn(level, chunk, pos, x, z);
                }
                if ((this.column & 15) == 0 && System.nanoTime() > deadline) {
                    return false;
                }
            }
            if (this.changed) {
                // paintings and item frames that hung on what's gone, and anything knocked loose, vanish with it
                AABB box = new AABB(this.pos.getMinBlockX(), this.zone.y - 1, this.pos.getMinBlockZ(),
                        this.pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), this.pos.getMaxBlockZ() + 1);
                level.getEntitiesOfClass(HangingEntity.class, box).forEach(Entity::discard);
                level.getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
            }
            this.zone.markDone(level, this.pos);
            return true;
        }

        private void flattenColumn(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z) {
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            for (int y = top; y > this.zone.y; y--) {
                BlockState state = chunk.getBlockState(pos.set(x, y, z));
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F || state.is(BlockTags.WITHER_IMMUNE)) {
                    continue;
                }
                // seas and rivers stay where they are (only what stands in them goes)
                FluidState fluid = state.getFluidState();
                BlockState after = Blocks.AIR.defaultBlockState();
                if (!fluid.isEmpty()) {
                    if (state.getBlock() instanceof LiquidBlock) {
                        continue;
                    }
                    after = fluid.createLegacyBlock();
                }
                if (state.hasBlockEntity()) {
                    Clearable.tryClear(chunk.getBlockEntity(pos)); // chests and the like don't spill their contents
                }
                BlockState old = chunk.setBlockState(pos, after, false);
                if (old != null) {
                    level.getChunkSource().blockChanged(pos);
                    level.onBlockStateChange(pos, old, after);
                    this.changed = true;
                }
            }
        }
    }

    /** The blast sweeping outwards over the chunks of a zone, nearest first. */
    private static final class Wave {
        final Zone zone;
        final long[] chunks;
        final double[] distances;
        int next;
        double front;

        Wave(Zone zone) {
            this.zone = zone;
            zone.front = 0.0;
            int cr = (zone.radius >> 4) + 2;
            int cx = zone.x >> 4;
            int cz = zone.z >> 4;
            List<double[]> found = new ArrayList<>();
            for (int x = cx - cr; x <= cx + cr; x++) {
                for (int z = cz - cr; z <= cz + cr; z++) {
                    ChunkPos pos = new ChunkPos(x, z);
                    if (zone.covers(pos)) {
                        found.add(new double[]{zone.distance(pos), x, z});
                    }
                }
            }
            double[][] sorted = found.toArray(new double[0][]);
            Arrays.sort(sorted, (a, b) -> Double.compare(a[0], b[0]));
            this.chunks = new long[sorted.length];
            this.distances = new double[sorted.length];
            for (int i = 0; i < sorted.length; i++) {
                this.chunks[i] = ChunkPos.asLong((int) sorted[i][1], (int) sorted[i][2]);
                this.distances[i] = sorted[i][0];
            }
        }
    }

    /** A flattened area: its centre, radius and height, and the chunks already done. */
    private static final class Zone {
        final int x;
        final int z;
        final int radius;
        final int y;
        final LongOpenHashSet done;
        /** How far the sweep has got (once it is over, or after a restart: everywhere). */
        double front = Double.MAX_VALUE;

        Zone(int x, int z, int radius, int y, LongOpenHashSet done) {
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.y = y;
            this.done = done;
        }

        /** Distance from the centre to the nearest block of the chunk. */
        double distance(ChunkPos pos) {
            double dx = Mth.clamp(this.x, pos.getMinBlockX(), pos.getMaxBlockX()) - this.x;
            double dz = Mth.clamp(this.z, pos.getMinBlockZ(), pos.getMaxBlockZ()) - this.z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        boolean covers(ChunkPos pos) {
            return distance(pos) <= this.radius;
        }

        void markDone(ServerLevel level, ChunkPos pos) {
            this.done.add(pos.toLong());
            zones(level).setDirty();
        }
    }

    /** The flattened areas of a level, saved with it. */
    private static final class Zones extends SavedData {
        final List<Zone> list = new ArrayList<>();

        Zone add(Zone zone) {
            this.list.add(zone);
            while (this.list.size() > MAX_ZONES) {
                this.list.remove(0);
            }
            setDirty();
            return zone;
        }

        static Zones load(CompoundTag tag) {
            Zones zones = new Zones();
            for (Tag t : tag.getList("Zones", Tag.TAG_COMPOUND)) {
                CompoundTag z = (CompoundTag) t;
                zones.list.add(new Zone(z.getInt("X"), z.getInt("Z"), z.getInt("Radius"), z.getInt("Y"),
                        new LongOpenHashSet(z.getLongArray("Done"))));
            }
            return zones;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Zone zone : this.list) {
                CompoundTag z = new CompoundTag();
                z.putInt("X", zone.x);
                z.putInt("Z", zone.z);
                z.putInt("Radius", zone.radius);
                z.putInt("Y", zone.y);
                z.putLongArray("Done", zone.done.toLongArray());
                list.add(z);
            }
            tag.put("Zones", list);
            return tag;
        }
    }

    private Flattening() {
    }
}
