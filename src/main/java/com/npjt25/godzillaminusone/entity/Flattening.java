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
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The atomic breath levelling the land: everything above a given height (Y 60) within hundreds of blocks of where it
 * lands, trees, grass, buildings and hills alike, is simply erased (nothing drops, so the game doesn't grind to a
 * halt under a sea of items). It sweeps outwards from the impact a chunk at a time, a set number of blocks each tick.
 * Chunks that aren't loaded yet are flattened when they load: the flattened areas are saved with the world, along with
 * which of their chunks are done (so what is built there afterwards stays).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Flattening {
    /** No drops, and no neighbour updates (a whole landscape of them would take forever and knock things loose). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Blocks the sweep advances per tick. */
    private static final double SPEED = 12.0;
    /** Work per tick (a block looked at counts 1, a block erased 4), so a mountain range doesn't freeze the server. */
    private static final int BUDGET = 60000;
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
        return work == null || work.waves.isEmpty() && work.pending.isEmpty();
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

    /** Erases everything above the zone's height in the chunk's columns within its radius. Returns the work done. */
    private static int flatten(ServerLevel level, LevelChunk chunk, Zone zone) {
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long r2 = (long) zone.radius * zone.radius;
        int work = 0;
        boolean changed = false;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx;
                int z = cp.getMinBlockZ() + lz;
                long dx = x - zone.x;
                long dz = z - zone.z;
                if (dx * dx + dz * dz > r2) {
                    continue;
                }
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                for (int y = top; y > zone.y; y--) {
                    work++;
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
                    Clearable.tryClear(level.getBlockEntity(pos)); // chests and the like don't spill their contents
                    level.setBlock(pos, after, FLAGS);
                    work += 4;
                    changed = true;
                }
            }
        }
        if (changed) {
            // paintings and item frames that hung on what's gone, and anything knocked loose, vanish with it
            AABB box = new AABB(cp.getMinBlockX(), zone.y - 1, cp.getMinBlockZ(), cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
            level.getEntitiesOfClass(HangingEntity.class, box).forEach(e -> e.discard());
            level.getEntitiesOfClass(ItemEntity.class, box).forEach(e -> e.discard());
        }
        return work;
    }

    /** The flattening going on in one level. */
    private static final class Work {
        final List<Wave> waves = new ArrayList<>();
        final Deque<Pending> pending = new ArrayDeque<>();

        void tick(ServerLevel level) {
            int[] budget = {BUDGET};
            Iterator<Wave> it = this.waves.iterator();
            while (it.hasNext()) {
                Wave wave = it.next();
                if (wave.tick(level, budget)) {
                    wave.zone.front = Double.MAX_VALUE;
                    it.remove();
                }
            }
            while (budget[0] > 0 && !this.pending.isEmpty()) {
                Pending next = this.pending.poll();
                Zone zone = next.zone;
                // (the sweep itself will get to it)
                if (zone.done.contains(next.pos.toLong()) || zone.distance(next.pos) > zone.front) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(next.pos.x, next.pos.z);
                if (chunk != null) {
                    budget[0] -= flatten(level, chunk, zone);
                    zone.markDone(level, next.pos);
                }
            }
        }
    }

    private record Pending(Zone zone, ChunkPos pos) {
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

        /** Returns true when it has swept the whole zone. */
        boolean tick(ServerLevel level, int[] budget) {
            this.front += SPEED;
            this.zone.front = this.front;
            while (this.next < this.chunks.length && this.distances[this.next] <= this.front && budget[0] > 0) {
                long packed = this.chunks[this.next++];
                if (this.zone.done.contains(packed)) {
                    continue;
                }
                // a chunk that isn't loaded is done when it loads
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
                if (chunk != null) {
                    budget[0] -= flatten(level, chunk, this.zone);
                    this.zone.markDone(level, chunk.getPos());
                }
            }
            return this.next >= this.chunks.length;
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
