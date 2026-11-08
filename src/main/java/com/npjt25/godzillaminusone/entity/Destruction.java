package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import java.util.Iterator;
import java.util.List;

/**
 * Block damage: the footprints Godzilla crushes into the ground, and the atomic blast's crater and blast wave
 * (spread over a few seconds so it neither lags the server nor happens all at once).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Destruction {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final List<BlastWave> WAVES = new ArrayList<>();

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
     * flattens trees and buildings and scorches the ground; out to {@code ruins}, what it leaves standing is left in
     * ruins. It races outwards from the impact.
     */
    public static void atomicBlast(ServerLevel level, @Nullable LivingEntity breaker, Vec3 center, int crater, int radius, int ruins) {
        if (crater <= 0 && radius <= 0 && ruins <= 0) {
            return;
        }
        int scorch = Math.max(crater, radius);
        WAVES.add(new BlastWave(level, breaker, BlockPos.containing(center), crater, scorch, Math.max(scorch, ruins)));
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
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WAVES.clear();
    }

    /**
     * For the game tests, as each one ends: the blast waves still spreading through {@code level} stop. (A blast's
     * ruins run out 100 blocks over a few seconds, on their own: left to it, it wrecks whichever test comes next nearby.)
     */
    public static void stopBlasts(ServerLevel level) {
        WAVES.removeIf(wave -> wave.level == level);
    }

    private static final class BlastWave {
        /** Blocks the wave front advances per tick. */
        private static final double SPEED = 4.0;
        /** Block checks per tick, so a big blast is spread over several ticks. */
        private static final int BUDGET = 24000;

        final ServerLevel level;
        @Nullable
        final LivingEntity breaker;
        final BlockPos center;
        final int crater;
        /** Out to here the wave knocks things down; beyond it, out to {@link #radius}, it leaves them in ruins. */
        final int scorch;
        final int radius;
        /** Columns sorted by distance from the center: (dx, dz) packed into an int, and their distances. */
        final int[] columns;
        final float[] distances;
        final RandomSource random;
        int next;
        double front;

        BlastWave(ServerLevel level, @Nullable LivingEntity breaker, BlockPos center, int crater, int scorch, int radius) {
            this.level = level;
            this.breaker = breaker;
            this.center = center;
            this.crater = crater;
            this.scorch = scorch;
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
                work += d < this.crater ? craterColumn(x, z, d) : d <= this.scorch ? blastColumn(x, z, d) : ruinColumn(x, z, d);
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
            double f = (d - this.crater) / Math.max(1.0, this.scorch - this.crater);
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
                if (!canBreak(this.level, pos, state, this.breaker)) {
                    continue;
                }
                if (this.random.nextDouble() < chance * resist) {
                    this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                } else {
                    ruin(pos, state, 1.0);   // what's left standing is a ruin
                }
            }
            return work;
        }

        /**
         * Beyond the blast wave's reach: trees and buildings are left standing, but in ruins (less so further out), the
         * grass dead and scorched in patches.
         */
        private int ruinColumn(int x, int z, float d) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            double f = (d - this.scorch) / Math.max(1.0, this.radius - this.scorch);
            double intensity = 0.3 + 0.7 * Math.pow(1.0 - f, 1.3);
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
                if (!canBreak(this.level, pos, state, this.breaker)) {
                    if (isGround(state)) {
                        break;
                    }
                    continue;
                }
                if (isGround(state)) {
                    if (this.random.nextDouble() < intensity * 0.6) {
                        BlockState burnt = scorch(state, false);   // (no gravel: nothing out here that could fall and drop)
                        if (burnt != null) {
                            this.level.setBlock(pos, burnt, FLAGS);
                        }
                    }
                    if (this.random.nextDouble() < intensity * 0.012) {
                        setFire(pos.setY(y + 1));
                    }
                    break;
                }
                ruin(pos, state, intensity);
            }
            return work;
        }

        /**
         * A block of a tree or building the blast has passed over, ruined: leaves burnt away and trunks charred, plants and
         * crops dead, glass blown in, cloth burnt, wooden walls and roofs holed and charred, stonework cracked and holed,
         * now and then a fire. Doors and beds are left (half of one would be left floating). {@code intensity}, 0 to 1:
         * how badly.
         */
        private void ruin(BlockPos pos, BlockState state, double intensity) {
            RandomSource r = this.random;
            BlockState to = null;
            if (state.is(BlockTags.LEAVES)) {
                to = r.nextDouble() < 0.5 + 0.5 * intensity ? Blocks.AIR.defaultBlockState() : null;
            } else if (state.is(BlockTags.LOGS)) {
                to = r.nextDouble() < 0.12 * intensity ? Blocks.AIR.defaultBlockState()
                        : r.nextDouble() < 0.4 + 0.6 * intensity ? charred(state) : null;
            } else if (state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                    || (state.canBeReplaced() && !state.is(Blocks.FIRE))) {
                to = r.nextInt(4) == 0 && state.is(BlockTags.FLOWERS) ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.AIR.defaultBlockState();
            } else if (state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(BlockTags.WOOL)
                    || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.HAY_BLOCK) || state.is(BlockTags.BANNERS)) {
                to = Blocks.AIR.defaultBlockState();
            } else if (state.is(BlockTags.DOORS) || state.is(BlockTags.BEDS)) {
                return;
            } else if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                    || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_TRAPDOORS) || state.is(BlockTags.FENCE_GATES)) {
                double roll = r.nextDouble();
                to = roll < 0.15 + 0.4 * intensity ? Blocks.AIR.defaultBlockState()
                        : roll < 0.3 + 0.55 * intensity && state.is(BlockTags.PLANKS) ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : null;
                if (to == null && r.nextDouble() < 0.02 * intensity) {
                    setFire(pos.above());
                }
            } else if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)) {
                double roll = r.nextDouble();
                to = roll < 0.1 * intensity ? Blocks.AIR.defaultBlockState() : roll < 0.25 * intensity ? Blocks.COBBLESTONE.defaultBlockState()
                        : roll < 0.8 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : null;
            } else if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.BRICKS) || state.is(Tags.Blocks.COBBLESTONE)) {
                to = r.nextDouble() < 0.2 * intensity ? Blocks.AIR.defaultBlockState() : null;
            } else if (state.getDestroySpeed(this.level, pos) >= 0.0F && state.getDestroySpeed(this.level, pos) < 20.0F) {
                to = r.nextDouble() < 0.12 * intensity ? Blocks.AIR.defaultBlockState() : null;   // anything else built: holed
            }
            if (to != null) {
                this.level.setBlock(pos, to, FLAGS);
            }
        }

        /** A trunk burnt black, its grain (axis) kept. */
        private static BlockState charred(BlockState log) {
            BlockState basalt = Blocks.BASALT.defaultBlockState();
            return log.hasProperty(BlockStateProperties.AXIS) ? basalt.setValue(BlockStateProperties.AXIS, log.getValue(BlockStateProperties.AXIS)) : basalt;
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

    /**
     * Natural ground: where the blast wave stops digging down a column, and what Godzilla and the Odo Island Monster
     * climb rather than break. Only the rock as the world makes it: polished stone, bricks, tiles, cut and smooth
     * sandstone and the like are built (forge:stone and forge:sandstone count some of those as stone).
     */
    static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Tags.Blocks.SAND) || state.is(Tags.Blocks.GRAVEL) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Tags.Blocks.ORES) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK)
                || state.is(BlockTags.ICE) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.BEDROCK) || state.is(Blocks.MUD)
                || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.END_STONE)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.INFESTED_STONE) || state.is(Blocks.INFESTED_DEEPSLATE);
    }

    /**
     * A block of a building Godzilla hits comes away and topples: a solid block flies off as a falling block, thrown
     * along {@code push}, and lands as rubble wherever it comes down (nothing drops). Anything else, and solid blocks once
     * {@code budget} is used up, just breaks (with Minecraft's own break effects, which a physics mod can pick up).
     * Returns whether it toppled.
     */
    static boolean topple(ServerLevel level, BlockPos pos, BlockState state, Vec3 push, boolean budget) {
        if (budget && state.isCollisionShapeFullBlock(level, pos) && !state.hasBlockEntity() && state.getFluidState().isEmpty()) {
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.dropItem = false;
            block.setDeltaMovement(push);
            block.hurtMarked = true;
            return true;
        }
        level.destroyBlock(pos, false);
        return false;
    }

    /**
     * Godzilla's claws (or jaws) rake through what's built within {@code radius} of {@code center}: solid blocks topple
     * along {@code push}, the rest breaks (the ground and liquids are left; nothing drops).
     */
    static void tearThrough(ServerLevel level, LivingEntity breaker, Vec3 center, double radius, Vec3 push) {
        int r = Mth.ceil(radius);
        BlockPos middle = BlockPos.containing(center);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int toppled = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    pos.set(middle.getX() + dx, middle.getY() + dy, middle.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!GodzillaEntity.isBuilt(state) || !canBreak(level, pos, state, breaker)) {
                        continue;
                    }
                    if (topple(level, pos, state, push, toppled < 60)) {
                        toppled++;
                    }
                }
            }
        }
    }

    /**
     * Godzilla's tail sweeps round him, low: what's built between {@code inner} and {@code outer} blocks from
     * {@code center} (horizontally), from his feet up to {@code height} above them, is knocked away, flung outwards and
     * round the way the tail goes. Returns how many blocks it took.
     */
    static int tailSweep(ServerLevel level, LivingEntity breaker, Vec3 center, double inner, double outer, int height) {
        return tailSweep(level, breaker, center, inner, outer, height, false);
    }

    /**
     * The same; {@code everything}: not only what's built but every block there (above the ground he stands on: his
     * feet's row and up), the ground of a hill, trees and all (Minus Zero's tail strike), but liquids and what can't
     * be broken (bedrock). The first few hundred break with their effects, the rest just go. Nearest him first, so
     * that if it takes as many blocks as it can at once (a mountainside) it has cleared evenly all round him as far
     * out as it got.
     */
    static int tailSweep(ServerLevel level, LivingEntity breaker, Vec3 center, double inner, double outer, int height, boolean everything) {
        int r = Mth.ceil(outer);
        int cx = Mth.floor(center.x);
        int cz = Mth.floor(center.z);
        int y0 = Mth.floor(center.y);
        List<int[]> columns = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= outer * outer && d2 >= inner * inner) {
                    columns.add(new int[] {dx, dz, d2});
                }
            }
        }
        columns.sort((a, b) -> Integer.compare(a[2], b[2]));
        int cap = everything ? 20000 : 1500;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int taken = 0;
        int toppled = 0;
        for (int[] column : columns) {
            int dx = column[0];
            int dz = column[1];
            if (!level.hasChunkAt(pos.set(cx + dx, y0, cz + dz))) {
                continue;
            }
            double d = Math.sqrt(column[2]);
            Vec3 push = new Vec3(dx / d * 1.2 - dz / d * 0.8, 0.6, dz / d * 1.2 + dx / d * 0.8);
            for (int y = y0; y <= y0 + height; y++) {
                BlockState state = level.getBlockState(pos.set(cx + dx, y, cz + dz));
                if (everything ? state.isAir() || !state.getFluidState().isEmpty() : !GodzillaEntity.isBuilt(state)) {
                    continue;
                }
                if (!canBreak(level, pos, state, breaker)) {
                    continue;
                }
                if (everything && taken >= 400) {
                    level.removeBlock(pos, false);
                } else if (topple(level, pos, state, push, toppled < 60)) {
                    toppled++;
                }
                if (++taken >= cap) {
                    GodzillaMinusOne.LOGGER.debug("tail sweep at {}: {} blocks at once, the most it takes; out to {} blocks",
                            BlockPos.containing(center), taken, String.format("%.1f", d));
                    return taken;
                }
            }
        }
        return taken;
    }

    @Nullable
    private static Boolean physicsMod;

    /**
     * Whether Physics Mod (mod id "physicsmod") is installed. It has no API this mod calls: it shows a block broken the
     * vanilla way (Level.destroyBlock: the break sound and particles) as pieces flying under physics, on its own. So with
     * it installed the destruction leaves the breaking to Minecraft (and to it) and adds no particles of its own; the
     * toppling of buildings is this mod's own (falling blocks thrown off a building land as rubble), with or without it.
     */
    public static boolean physicsMod() {
        if (physicsMod == null) {
            net.minecraftforge.fml.ModList mods = net.minecraftforge.fml.ModList.get();
            physicsMod = mods != null && mods.isLoaded("physicsmod");
        }
        return physicsMod;
    }

    /** Whether a block may be destroyed; {@code breaker} is the mob doing it, or null for a bomb. */
    static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable LivingEntity breaker) {
        return state.getDestroySpeed(level, pos) >= 0.0F
                && !state.is(BlockTags.WITHER_IMMUNE)
                && (breaker == null || (state.canEntityDestroy(level, pos, breaker) && ForgeEventFactory.onEntityDestroyBlock(breaker, pos, state)));
    }

    private Destruction() {
    }
}
