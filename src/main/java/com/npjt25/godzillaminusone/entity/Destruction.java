package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Block damage: the footprints Godzilla crushes into the ground, and the atomic blast's crater and blast wave
 * (spread over a few seconds so it neither lags the server nor happens all at once).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Destruction {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final List<BlastWave> WAVES = new ArrayList<>();
    private static final Map<ResourceKey<Level>, Long> CLEAR_SKIES_AT = new HashMap<>();

    // ------------------------------------------------------------------ footprints

    /**
     * Crushes a footprint (an ellipse {@code halfWidth} x {@code halfLength} along {@code yaw}) into the ground at
     * {@code foot}: whatever stood there is flattened, the ground sinks one or two blocks and turns to rubble.
     */
    public static void footprint(ServerLevel level, LivingEntity breaker, Vec3 foot, float yaw, double halfWidth, double halfLength) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, yaw);
        RandomSource random = level.getRandom();
        int footY = Mth.floor(breaker.getY() + 0.01);
        int reach = Mth.ceil(Math.max(halfWidth, halfLength));
        int cx = Mth.floor(foot.x);
        int cz = Mth.floor(foot.z);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                double px = x + 0.5 - foot.x;
                double pz = z + 0.5 - foot.z;
                double across = px * -fwd.z + pz * fwd.x;
                double along = px * fwd.x + pz * fwd.z;
                double e = Mth.square(across / halfWidth) + Mth.square(along / halfLength);
                if (e <= 1.0) {
                    crushColumn(level, breaker, x, z, footY, e < 0.45 ? 2 : 1, random);
                }
            }
        }
    }

    private static void crushColumn(ServerLevel level, LivingEntity breaker, int x, int z, int footY, int depth, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Under water (or lava) the ground is left alone.
        for (int y = footY - 1; y <= footY + 1; y++) {
            if (!level.getFluidState(pos.set(x, y, z)).isEmpty()) {
                return;
            }
        }
        // Everything he treads on (plants, fences, walls, the corner of a house...) up to his shins is flattened.
        for (int y = footY + 10; y >= footY; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty() && canBreak(level, pos, state, breaker)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        // The ground under his foot sinks and turns to rubble.
        int ground = Integer.MIN_VALUE;
        for (int y = footY - 1; y >= footY - 4; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!state.getFluidState().isEmpty()) {
                return;
            }
            if (state.isCollisionShapeFullBlock(level, pos) && !state.is(BlockTags.LEAVES)) {
                ground = y;
                break;
            }
            if (!state.isAir() && canBreak(level, pos, state, breaker)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        if (ground == Integer.MIN_VALUE) {
            return;
        }
        for (int k = 0; k < depth; k++) {
            BlockState state = level.getBlockState(pos.set(x, ground - k, z));
            if (!canBreak(level, pos, state, breaker)) {
                return;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        BlockState floor = level.getBlockState(pos.set(x, ground - depth, z));
        if (floor.isCollisionShapeFullBlock(level, pos) && canBreak(level, pos, floor, breaker)) {
            level.setBlock(pos, rubble(random), FLAGS);
        }
    }

    private static BlockState rubble(RandomSource random) {
        int roll = random.nextInt(20);
        if (roll < 8) {
            return Blocks.GRAVEL.defaultBlockState();
        } else if (roll < 13) {
            return Blocks.COBBLESTONE.defaultBlockState();
        } else if (roll < 18) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        return Blocks.ANDESITE.defaultBlockState();
    }

    // ------------------------------------------------------------------ the atomic blast

    /**
     * The atomic breath's impact: a crater of radius {@code crater} and, out to {@code radius}, a blast wave that
     * flattens trees and buildings and scorches the ground. It races outwards from the impact over ~1 second.
     */
    public static void atomicBlast(ServerLevel level, LivingEntity breaker, Vec3 center, int crater, int radius) {
        if (crater <= 0 && radius <= 0) {
            return;
        }
        WAVES.add(new BlastWave(level, breaker, BlockPos.containing(center), crater, Math.max(crater, radius)));
    }

    /** The sky clouds over (rain) for {@code seconds}, then clears. */
    public static void storm(ServerLevel level, int seconds) {
        if (seconds <= 0) {
            return;
        }
        int ticks = seconds * 20;
        level.setWeatherParameters(0, ticks, true, false);
        CLEAR_SKIES_AT.put(level.dimension(), level.getGameTime() + ticks);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!WAVES.isEmpty()) {
            Iterator<BlastWave> it = WAVES.iterator();
            while (it.hasNext()) {
                BlastWave wave = it.next();
                if (wave.level == level && wave.tick()) {
                    it.remove();
                }
            }
        }
        Long clearAt = CLEAR_SKIES_AT.get(level.dimension());
        if (clearAt != null && level.getGameTime() >= clearAt) {
            CLEAR_SKIES_AT.remove(level.dimension());
            if (level.isRaining()) {
                level.setWeatherParameters(12000 + level.getRandom().nextInt(12000), 0, false, false);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WAVES.clear();
        CLEAR_SKIES_AT.clear();
    }

    private static final class BlastWave {
        /** Blocks the wave front advances per tick. */
        private static final double SPEED = 4.0;
        /** Block checks per tick, so a big blast is spread over several ticks. */
        private static final int BUDGET = 24000;

        final ServerLevel level;
        final LivingEntity breaker;
        final BlockPos center;
        final int crater;
        final int radius;
        /** Columns sorted by distance from the center: (dx, dz) packed into an int, and their distances. */
        final int[] columns;
        final float[] distances;
        final RandomSource random;
        int next;
        double front;

        BlastWave(ServerLevel level, LivingEntity breaker, BlockPos center, int crater, int radius) {
            this.level = level;
            this.breaker = breaker;
            this.center = center;
            this.crater = crater;
            this.radius = radius;
            this.random = level.getRandom();
            List<long[]> cols = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 <= radius * radius) {
                        cols.add(new long[]{d2, dx, dz});
                    }
                }
            }
            long[][] sorted = cols.toArray(new long[0][]);
            Arrays.sort(sorted, (a, b) -> Long.compare(a[0], b[0]));
            this.columns = new int[sorted.length];
            this.distances = new float[sorted.length];
            for (int i = 0; i < sorted.length; i++) {
                this.columns[i] = (int) ((sorted[i][1] & 0xFFFF) << 16 | (sorted[i][2] & 0xFFFF));
                this.distances[i] = (float) Math.sqrt(sorted[i][0]);
            }
        }

        /** Returns true when finished. */
        boolean tick() {
            this.front += SPEED;
            int work = 0;
            while (this.next < this.columns.length && this.distances[this.next] <= this.front && work < BUDGET) {
                int packed = this.columns[this.next];
                int dx = (short) (packed >> 16);
                int dz = (short) packed;
                float d = this.distances[this.next];
                this.next++;
                int x = this.center.getX() + dx;
                int z = this.center.getZ() + dz;
                if (!this.level.isLoaded(new BlockPos(x, this.center.getY(), z))) {
                    continue;
                }
                work += d < this.crater ? craterColumn(x, z, d) : blastColumn(x, z, d);
            }
            return this.next >= this.columns.length;
        }

        /** Inside the crater: everything goes, down to a bowl-shaped floor of scorched rubble. */
        private int craterColumn(int x, int z, float d) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int surface = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (!this.level.getFluidState(pos.set(x, surface, z)).isEmpty()) {
                return 1; // lakes and the sea keep their bed
            }
            double f = d / this.crater;
            int floor = this.center.getY() - (int) Math.round(this.crater * 0.45 * Math.sqrt(Math.max(0.0, 1.0 - f * f)));
            // Everything inside the fireball goes (tall buildings and hills above it are left standing).
            int top = Math.min(surface, this.center.getY() + this.crater * 3 / 2);
            int work = 1;
            for (int y = top; y > floor; y--) {
                BlockState state = this.level.getBlockState(pos.set(x, y, z));
                work++;
                if (state.isAir()) {
                    continue;
                }
                if (!state.getFluidState().isEmpty()) {
                    return work;
                }
                if (canBreak(this.level, pos, state, this.breaker)) {
                    this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            BlockState ground = this.level.getBlockState(pos.set(x, floor, z));
            if (!ground.isAir() && ground.getFluidState().isEmpty() && canBreak(this.level, pos, ground, this.breaker)) {
                this.level.setBlock(pos, scorched(f), FLAGS);
                if (this.random.nextInt(14) == 0) {
                    setFire(pos.setY(floor + 1));
                }
            }
            return work;
        }

        /** The blast wave: trees and buildings are knocked down (fewer further out), grass is scorched, fires start. */
        private int blastColumn(int x, int z, float d) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            double f = (d - this.crater) / Math.max(1.0, this.radius - this.crater);
            double chance = Math.pow(1.0 - f, 1.6);
            int top = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int work = 1;
            for (int y = top; y > top - 40; y--) {
                BlockState state = this.level.getBlockState(pos.set(x, y, z));
                work++;
                if (state.isAir()) {
                    continue;
                }
                if (!state.getFluidState().isEmpty()) {
                    break;
                }
                if (isGround(state)) {
                    if (this.random.nextDouble() < chance) {
                        BlockState burnt = scorch(state, chance > 0.6 && this.random.nextInt(3) == 0);
                        if (burnt != null && canBreak(this.level, pos, state, this.breaker)) {
                            this.level.setBlock(pos, burnt, FLAGS);
                        }
                    }
                    if (this.random.nextDouble() < chance * 0.05) {
                        setFire(pos.setY(y + 1));
                    }
                    break;
                }
                // Plants, trees and buildings. Obsidian-strength blocks survive.
                float hardness = state.getDestroySpeed(this.level, pos);
                double resist = hardness < 0.0F || hardness > 20.0F ? 0.0 : hardness < 3.5F ? 1.0 : 0.35;
                if (this.random.nextDouble() < chance * resist && canBreak(this.level, pos, state, this.breaker)) {
                    this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            return work;
        }

        private void setFire(BlockPos pos) {
            if (this.level.getBlockState(pos).isAir() && this.level.getBlockState(pos.below()).isFaceSturdy(this.level, pos.below(), Direction.UP)) {
                this.level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        private BlockState scorched(double f) {
            int roll = this.random.nextInt(20);
            if (f < 0.5 && roll < 2) {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (roll < 8) {
                return Blocks.COARSE_DIRT.defaultBlockState();
            } else if (roll < 12) {
                return Blocks.BLACKSTONE.defaultBlockState();
            } else if (roll < 15) {
                return Blocks.BASALT.defaultBlockState();
            } else if (roll < 18) {
                return Blocks.GRAVEL.defaultBlockState();
            }
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }

        @Nullable
        private static BlockState scorch(BlockState state, boolean rubble) {
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT)
                    || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.DIRT_PATH)) {
                return rubble ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            }
            if (state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.ICE)) {
                return Blocks.AIR.defaultBlockState();
            }
            return null;
        }
    }

    /** Natural ground: where the blast wave stops digging down a column. */
    private static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Tags.Blocks.STONE) || state.is(Tags.Blocks.SAND) || state.is(Tags.Blocks.GRAVEL)
                || state.is(Tags.Blocks.SANDSTONE) || state.is(Tags.Blocks.ORES) || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.ICE) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.BEDROCK) || state.is(Blocks.MUD);
    }

    static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state, LivingEntity breaker) {
        return state.getDestroySpeed(level, pos) >= 0.0F
                && !state.is(BlockTags.WITHER_IMMUNE)
                && state.canEntityDestroy(level, pos, breaker)
                && ForgeEventFactory.onEntityDestroyBlock(breaker, pos, state);
    }

    private Destruction() {
    }
}
