package com.npjt25.godzillaminusone.gametest;

import com.mojang.authlib.GameProfile;
import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.client.render.BlastTimeline;
import com.npjt25.godzillaminusone.entity.Blasts;
import com.npjt25.godzillaminusone.entity.Destruction;
import com.npjt25.godzillaminusone.entity.Flattening;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import com.npjt25.godzillaminusone.entity.GodzillaStatueEntity;
import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import com.npjt25.godzillaminusone.entity.NuclearBombEntity;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import com.npjt25.godzillaminusone.entity.PlayerGodzilla;
import com.npjt25.godzillaminusone.entity.PlayerOdo;
import com.npjt25.godzillaminusone.entity.Radiation;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruPart;
import com.npjt25.godzillaminusone.item.RemoteDetonatorItem;
import com.npjt25.godzillaminusone.menu.ShinseiMaruMenu;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModEffects;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModItems;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side checks of the add-on behaviour, run headless in CI with {@code ./gradlew runGameTestServer}.
 * Forge only loads game tests in development/test runs, never in a normal game.
 *
 * <p>Every batch reuses the same spot, and a 45 block Godzilla easily outlives the test that spawned him (he can
 * stand or swim above the area that gets cleared), so each test removes what it spawned when it passes, and any
 * Godzilla left over from a failed test is removed before the next one starts.
 */
@GameTestHolder(GodzillaMinusOne.MODID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private static final String FLOOR = "test_floor";

    /**
     * (The tests' pocket is at y -60: below walkBreaksFromY, 60, he'd destroy nothing as he walks there. Each test's
     * Godzilla walks as he did before that rule, through whatever is in his way; walkingHeDestroysOnlyFromY60Up checks the
     * rule itself.)
     */
    private static GodzillaEntity spawnGodzilla(GameTestHelper helper) {
        removeLeftoverGodzillas(helper);
        GodzillaEntity godzilla = helper.spawn(ModEntities.GODZILLA.get(), 4, 1, 4);
        godzilla.setWalkFloorY(Integer.MIN_VALUE);
        return godzilla;
    }

    /** Air in place of everything round {@code center} (absolute), {@code radius} out and from its row to {@code height} above. */
    private static void clearAround(GameTestHelper helper, Vec3 center, int radius, int height) {
        BlockPos middle = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-radius, 0, -radius), middle.offset(radius, height, radius))) {
            if (!helper.getLevel().getBlockState(pos).isAir()) {
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void removeLeftoverGodzillas(GameTestHelper helper) {
        List<GodzillaEntity> leftovers = new ArrayList<>(helper.getLevel().getEntities(ModEntities.GODZILLA.get(), e -> true));
        leftovers.addAll(helper.getLevel().getEntities(ModEntities.GODZILLA_MINUS_ZERO.get(), e -> true));
        for (GodzillaEntity leftover : leftovers) {
            GodzillaMinusOne.LOGGER.info("test at {}: removing a leftover {} at {}", helper.absolutePos(BlockPos.ZERO),
                    leftover.getType().toShortString(), leftover.position());
            leftover.discard();
        }
    }

    private static GodzillaEntity spawnActiveGodzilla(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnGodzilla(helper);
        godzilla.skipIntro();
        return godzilla;
    }

    /** The layer of the test floor (the template sits one block above the test's structure block). */
    private static int floorY(GameTestHelper helper) {
        for (int y = -1; y <= 3; y++) {
            if (helper.getBlockState(new BlockPos(4, y, 4)).is(Blocks.STONE)) {
                return y;
            }
        }
        throw new IllegalStateException("no test floor");
    }

    /**
     * The test floor is only 9 x 9, in a pocket of a normal world: anything put beyond it sits in whatever rock, cave,
     * lava or ruin the world has there, in a chunk the test doesn't keep loaded. This keeps the chunks from (x0, z0)
     * to (x1, z1) loaded and ticking and carves a sealed room of air there, {@code height} blocks high, walled in
     * stone; the returned action lets go of the chunks it forced.
     */
    private static Runnable room(GameTestHelper helper, int floor, int x0, int z0, int x1, int z1, int height) {
        BlockPos a = helper.absolutePos(new BlockPos(x0 - 1, floor, z0 - 1));
        BlockPos b = helper.absolutePos(new BlockPos(x1 + 1, floor, z1 + 1));
        List<long[]> chunks = new ArrayList<>();
        for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++) {
            for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++) {
                if (helper.getLevel().setChunkForced(cx, cz, true)) {
                    chunks.add(new long[]{cx, cz});
                }
            }
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int y = floor; y <= floor + height + 1; y++) {
                    boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1 && y > floor && y <= floor + height;
                    helper.setBlock(new BlockPos(x, y, z), (inside ? Blocks.AIR : Blocks.STONE).defaultBlockState());
                }
            }
        }
        return () -> chunks.forEach(c -> helper.getLevel().setChunkForced((int) c[0], (int) c[1], false));
    }

    /**
     * At the end of a test: its entities go, and so does whatever its blasts set going that would carry on into the
     * next test's ground (the tests run one after another, on ground near each other): blast waves still spreading, and
     * blocks still flying as rubble. (An atomic breath test's ruins once buried the Odo Island Monster of the test after
     * it, in the corridor it had just cleared.)
     */
    private static void remove(Entity... entities) {
        for (Entity entity : entities) {
            entity.discard();
        }
        if (entities.length > 0 && entities[0].level() instanceof ServerLevel level) {
            Destruction.stopBlasts(level);
            List<Entity> flying = new ArrayList<>();
            level.getAllEntities().forEach(e -> {
                if (e instanceof FallingBlockEntity) {
                    flying.add(e);
                }
            });
            flying.forEach(Entity::discard);
        }
    }

    private static void detonateMine(GameTestHelper helper, int x, int z) {
        NavalMineEntity mine = helper.spawn(ModEntities.NAVAL_MINE.get(), x, 1, z);
        mine.hurt(helper.getLevel().damageSources().generic(), 5.0F);
        helper.assertTrue(mine.isRemoved(), "a hurt naval mine should detonate");
    }

    /**
     * When each of Godzilla's phases began, by his own ticks. Not the test's: the tests stand in a world that's still
     * being generated round them, and a mob can go a while before its chunk lets it tick (once, Godzilla was still
     * emerging when the test had counted his 9 s), so a timer of his runs behind the test's.
     */
    private static Map<GodzillaEntity.Phase, Integer> phaseStarts(GameTestHelper helper, GodzillaEntity godzilla) {
        Map<GodzillaEntity.Phase, Integer> starts = new EnumMap<>(GodzillaEntity.Phase.class);
        starts.put(godzilla.getPhase(), godzilla.tickCount);
        GodzillaEntity.Phase[] last = {godzilla.getPhase()};
        helper.onEachTick(() -> {
            if (godzilla.getPhase() != last[0]) {
                last[0] = godzilla.getPhase();
                starts.putIfAbsent(last[0], godzilla.tickCount);
            }
        });
        return starts;
    }

    /** How many of his ticks went by from the start of phase {@code from} to the start of {@code to} (-1: not yet). */
    private static int ticksBetween(Map<GodzillaEntity.Phase, Integer> starts, GodzillaEntity.Phase from, GodzillaEntity.Phase to) {
        return starts.containsKey(from) && starts.containsKey(to) ? starts.get(to) - starts.get(from) : -1;
    }

    @GameTest(template = FLOOR, batch = "gmo_emerge", timeoutTicks = 1200)
    public static void godzillaEmergesThenBecomesActive(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnGodzilla(helper);
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.EMERGING, "Godzilla should start by emerging");
        Map<GodzillaEntity.Phase, Integer> starts = phaseStarts(helper, godzilla);
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.isActive(), "Godzilla should become active, was " + godzilla.getPhase());
            int emerging = ticksBetween(starts, GodzillaEntity.Phase.EMERGING, GodzillaEntity.Phase.RISING);
            helper.assertTrue(Math.abs(emerging - 180) <= 2, "he should emerge for 9 s (180 of his ticks) before he roars, took " + emerging);
            remove(godzilla);
        });
    }

    @GameTest(template = FLOOR, batch = "gmo_immune")
    public static void godzillaIgnoresOrdinaryDamage(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        float before = godzilla.getHealth();
        helper.assertFalse(godzilla.hurt(helper.getLevel().damageSources().generic(), 500.0F), "generic damage should be ignored");
        helper.assertFalse(godzilla.hurt(helper.getLevel().damageSources().explosion(null, null), 500.0F), "TNT should be ignored");
        helper.assertFalse(godzilla.hurt(helper.getLevel().damageSources().lava(), 500.0F), "lava should be ignored");
        helper.assertTrue(godzilla.getHealth() == before, "health should be unchanged");
        remove(godzilla);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_mine")
    public static void navalMineTakesATenthOfHisHealth(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        float expected = godzilla.getMaxHealth() * 0.9F;
        detonateMine(helper, 4, 14);
        helper.assertTrue(Math.abs(godzilla.getHealth() - expected) < 1.0F,
                "one mine should take 10% of his health: expected " + expected + ", was " + godzilla.getHealth());
        helper.assertTrue(godzilla.isStunned(), "a mine blast should stun him");
        remove(godzilla);
        helper.succeed();
    }

    /** With evolvesIntoMinusZero off, he gets up again himself, as before there was a Minus Zero. */
    @GameTest(template = FLOOR, batch = "gmo_regenerate", timeoutTicks = 1600)
    public static void godzillaCollapsesAndRegenerates(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setHealth(10.0F);
        godzilla.setEvolves(false);
        detonateMine(helper, 4, 14);
        helper.assertTrue(godzilla.getDefeat() == GodzillaEntity.Defeat.REGENERATE, "evolving off, he should regenerate, was " + godzilla.getDefeat());
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING, "at zero health he should collapse, was " + godzilla.getPhase());
        helper.assertTrue(godzilla.isAlive(), "he should not really die");
        Map<GodzillaEntity.Phase, Integer> starts = phaseStarts(helper, godzilla);
        // downedSeconds counts from the end of his 5 s collapse
        int downed = Config.DOWNED_SECONDS.get() * 20;
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.isActive() && godzilla.getHealth() >= godzilla.getMaxHealth() - 0.5F,
                    "he should rise again at full health, was " + godzilla.getPhase() + " / " + godzilla.getHealth());
            int collapse = ticksBetween(starts, GodzillaEntity.Phase.COLLAPSING, GodzillaEntity.Phase.DEAD);
            int down = ticksBetween(starts, GodzillaEntity.Phase.DEAD, GodzillaEntity.Phase.EMERGING);
            helper.assertTrue(Math.abs(collapse - 100) <= 2, "his collapse should take 5 s (100 of his ticks), took " + collapse);
            helper.assertTrue(Math.abs(down - downed) <= 2, "he should lie down " + downed / 20 + " s (" + downed + " of his ticks) after his collapse, lay " + down);
            remove(godzilla);
        });
    }

    /** The Minus Zeros (alive) in the world within {@code radius} of {@code at}. */
    private static List<GodzillaMinusZeroEntity> minusZeros(GameTestHelper helper, Vec3 at, double radius) {
        return new ArrayList<>(helper.getLevel().getEntities(ModEntities.GODZILLA_MINUS_ZERO.get(),
                z -> z.isAlive() && z.position().distanceTo(at) < radius));
    }

    /**
     * Brought down once, Minus One gets up as Godzilla Minus Zero: exactly one, where he lay, facing as he faced, at
     * full health (Minus One's maximum), twice his size; Minus One is gone. More blows while he's down change nothing.
     * Minus Zero rises and roars, then fights.
     */
    @GameTest(template = FLOOR, batch = "gmo_evolve", timeoutTicks = 900)
    public static void oneDefeatMakesExactlyOneMinusZero(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setEvolves(true);
        godzilla.setDownedTicks(20);
        godzilla.setYRot(37.0F);
        godzilla.setYBodyRot(37.0F);
        Vec3 at = godzilla.position();
        float max = godzilla.getMaxHealth();
        godzilla.setHealth(10.0F);
        detonateMine(helper, 4, 14);
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING && godzilla.getDefeat() == GodzillaEntity.Defeat.EVOLVE,
                "brought down, he should collapse to evolve, was " + godzilla.getPhase() + " / " + godzilla.getDefeat());
        // more blows while he's down, as a crowd of players or a chain of mines would land them
        detonateMine(helper, 4, 14);
        godzilla.die(helper.getLevel().damageSources().generic());
        godzilla.hurt(helper.getLevel().damageSources().generic(), 10000.0F);
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING && godzilla.isAlive(), "more blows shouldn't change anything");
        GodzillaMinusZeroEntity[] zero = {null};
        helper.onEachTick(() -> {
            List<GodzillaMinusZeroEntity> zeros = minusZeros(helper, at, 64.0);
            if (zeros.size() > 1) {
                helper.fail(zeros.size() + " Minus Zeros");
            }
            if (zero[0] == null && !zeros.isEmpty()) {
                zero[0] = zeros.get(0);
                helper.assertTrue(godzilla.isRemoved(), "Minus One should be gone the moment Minus Zero is there");
                helper.assertTrue(zero[0].position().distanceTo(at) < 0.01, "Minus Zero should be where Minus One lay, was "
                        + zero[0].position() + " not " + at);
                helper.assertTrue(Math.abs(Mth.wrapDegrees(zero[0].getYRot() - 37.0F)) < 0.5F, "facing as Minus One faced, was " + zero[0].getYRot());
                helper.assertTrue(zero[0].getMaxHealth() == max && zero[0].getHealth() == max,
                        "at full health, Minus One's maximum " + max + ", was " + zero[0].getHealth() + " / " + zero[0].getMaxHealth());
                helper.assertTrue(zero[0].getPhase() == GodzillaEntity.Phase.EMERGING, "getting up, was " + zero[0].getPhase());
                helper.assertTrue(Math.abs(zero[0].getBbHeight() - 2.0 * godzilla.getBbHeight()) < 1.0E-3
                        && Math.abs(zero[0].getBbWidth() - 2.0 * godzilla.getBbWidth()) < 1.0E-3, "twice his size (hitbox)");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(zero[0] != null && zero[0].isActive(), "Minus Zero should be up and about");
            helper.assertTrue(minusZeros(helper, at, 64.0).size() == 1, "exactly one Minus Zero");
            remove(zero[0]);
        });
    }

    /**
     * Saved while he lies there to evolve (the world saved, or his chunk unloaded and loaded again), he's read back
     * still going to evolve: one Minus Zero comes of it, no more, none fewer.
     */
    @GameTest(template = FLOOR, batch = "gmo_evolve_reload", timeoutTicks = 900)
    public static void aPendingEvolutionSurvivesSavingAndLoading(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        Vec3 at = godzilla.position();
        godzilla.setEvolves(true);
        godzilla.setHealth(10.0F);
        detonateMine(helper, 4, 14);
        helper.assertTrue(godzilla.getDefeat() == GodzillaEntity.Defeat.EVOLVE, "he should be going to evolve");
        GodzillaEntity[] loaded = {null};
        helper.onEachTick(() -> {
            if (loaded[0] == null && godzilla.getPhase() == GodzillaEntity.Phase.DEAD) {
                // as a chunk does: saved, gone from the world, then read back and in it again
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                godzilla.saveWithoutId(tag);
                godzilla.discard();
                GodzillaEntity copy = ModEntities.GODZILLA.get().create(helper.getLevel());
                copy.load(tag);
                copy.setDownedTicks(20); // (the test's own short rest; the evolution is what was saved)
                helper.getLevel().addFreshEntity(copy);
                loaded[0] = copy;
                helper.assertTrue(copy.getDefeat() == GodzillaEntity.Defeat.EVOLVE && copy.getPhase() == GodzillaEntity.Phase.DEAD,
                        "read back, he should still be lying there to evolve, was " + copy.getPhase() + " / " + copy.getDefeat());
            }
            if (minusZeros(helper, at, 64.0).size() > 1) {
                helper.fail("more than one Minus Zero");
            }
        });
        helper.succeedWhen(() -> {
            List<GodzillaMinusZeroEntity> zeros = minusZeros(helper, at, 64.0);
            helper.assertTrue(loaded[0] != null && loaded[0].isRemoved() && zeros.size() == 1,
                    "one Minus Zero, from the Minus One read back, was " + zeros.size());
            remove(zeros.get(0));
        });
    }

    /**
     * Minus Zero brought down collapses and falls for good: no Godzilla rises after him, and he doesn't get up again.
     * (And he's twice Minus One wherever it counts: his mouth, where the beam starts, his claws, his face, his reach.)
     */
    @GameTest(template = FLOOR, batch = "gmo_minus_zero_falls", timeoutTicks = 400)
    public static void minusZeroBroughtDownFallsForGood(GameTestHelper helper) {
        removeLeftoverGodzillas(helper);
        GodzillaMinusZeroEntity zero = helper.spawn(ModEntities.GODZILLA_MINUS_ZERO.get(), 4, 1, 4);
        GodzillaEntity one = helper.spawn(ModEntities.GODZILLA.get(), 4, 1, 4);
        zero.skipIntro();
        one.skipIntro();
        for (GodzillaEntity g : new GodzillaEntity[] {zero, one}) {
            g.setYRot(0.0F);
            g.setYBodyRot(0.0F);
        }
        Vec3 at = zero.position();
        for (Vec3 point : new Vec3[] {new Vec3(-0.17, 35.80, 17.83), new Vec3(0.0, 35.6, 16.5), new Vec3(2.2, 10.0, 16.5)}) {
            Vec3 a = one.locator(point.x, point.y, point.z).subtract(at);
            Vec3 b = zero.locator(point.x, point.y, point.z).subtract(at);
            helper.assertTrue(b.distanceTo(a.scale(2.0)) < 1.0E-6, "a point on him should be twice as far out on Minus Zero: " + a + " / " + b);
        }
        helper.assertTrue(zero.facePosition().subtract(at).distanceTo(one.facePosition().subtract(at).scale(2.0)) < 1.0E-6, "his face, twice as high and far");
        helper.assertTrue(zero.clawPosition(1.0).subtract(at).distanceTo(one.clawPosition(1.0).subtract(at).scale(2.0)) < 1.0E-6, "his claws");
        helper.assertTrue(zero.getMaxHealth() == one.getMaxHealth() && zero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                == one.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), "his numbers are Minus One's");
        // his breath: Minus One's as it was (one blast); Minus Zero's first beam lasts until his plates are lit again, then
        // he fires a second laser (wider, hitting twice as often) and a second blast goes up: both twice as big
        helper.assertTrue(!one.surges() && one.blastScale() == 1.0 && one.blastsAt(GodzillaEntity.CHARGE_BLAST_TICK)
                && !one.blastsAt(GodzillaEntity.SECOND_BLAST_TICK) && one.beamEndTick() == GodzillaEntity.BEAM_END_TICK
                && one.chargeLength() == GodzillaEntity.CHARGE_LENGTH && one.beamRadius(GodzillaEntity.SURGE_TICK) == 4.0,
                "Minus One's breath should be as it was");
        helper.assertTrue(zero.surges() && zero.blastScale() == 2.0 && zero.blastsAt(GodzillaEntity.CHARGE_BLAST_TICK)
                && zero.blastsAt(GodzillaEntity.SECOND_BLAST_TICK) && !zero.blastsAt(GodzillaEntity.SURGE_TICK)
                && GodzillaEntity.SURGE_TICK == GodzillaEntity.CHARGE_BLAST_TICK + 80
                && zero.beamEndTick() > GodzillaEntity.SECOND_BLAST_TICK && zero.chargeLength() == zero.beamEndTick() + GodzillaEntity.ROAR_TICKS,
                "Minus Zero's breath: a blast, his plates dimmed and lit again (4 s), his second laser and a second blast, twice as big");
        helper.assertTrue(zero.beamRadius(GodzillaEntity.SURGE_TICK) > zero.beamRadius(GodzillaEntity.SURGE_TICK - 1)
                && zero.beamRadius(GodzillaEntity.SURGE_TICK - 1) == 8.0, "his second laser should hit wider than his first");
        one.discard();
        zero.setHealth(10.0F);
        detonateMine(helper, 4, 14);
        helper.assertTrue(zero.getPhase() == GodzillaEntity.Phase.COLLAPSING && zero.getDefeat() == GodzillaEntity.Defeat.FALL,
                "brought down, Minus Zero should collapse to fall for good, was " + zero.getPhase() + " / " + zero.getDefeat());
        helper.onEachTick(() -> {
            int godzillas = helper.getLevel().getEntities(ModEntities.GODZILLA.get(), g -> g.isAlive() && g.position().distanceTo(at) < 64.0).size();
            if (godzillas > 0 || minusZeros(helper, at, 64.0).stream().anyMatch(z -> z != zero)) {
                helper.fail("a Godzilla rose after Minus Zero");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(zero.isRemoved() || zero.isDeadOrDying(), "he should die, was " + zero.getPhase() + " / " + zero.getHealth());
            helper.assertTrue(zero.getPhase() != GodzillaEntity.Phase.EMERGING && zero.getPhase() != GodzillaEntity.Phase.ACTIVE, "and never get up");
        });
    }

    /** Godzilla Minus Zero's spawn egg brings one Minus Zero, rising out of the ground as Godzilla does from his. */
    @GameTest(template = FLOOR, batch = "gmo_minus_zero_egg", timeoutTicks = 20)
    public static void minusZerosSpawnEggBringsOneMinusZero(GameTestHelper helper) {
        removeLeftoverGodzillas(helper);
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        ItemStack egg = new ItemStack(ModItems.GODZILLA_MINUS_ZERO_SPAWN_EGG.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, egg);
        BlockPos ground = helper.absolutePos(new BlockPos(4, floorY(helper), 4));
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false)));
        List<GodzillaMinusZeroEntity> zeros = minusZeros(helper, Vec3.atCenterOf(ground), 8.0);
        helper.assertTrue(zeros.size() == 1, "one Minus Zero, was " + zeros.size());
        helper.assertTrue(zeros.get(0).getPhase() == GodzillaEntity.Phase.EMERGING, "rising out of the ground, was " + zeros.get(0).getPhase());
        helper.assertTrue(level.getEntities(ModEntities.GODZILLA.get(), g -> g.position().distanceTo(Vec3.atCenterOf(ground)) < 8.0).isEmpty(),
                "and no Minus One");
        remove(zeros.get(0));
        helper.succeed();
    }

    /**
     * His resting time is whatever downedSeconds says (here 3 s, not the 15 of the default), counted from the end of
     * his 5 s collapse, which is left as it is.
     */
    @GameTest(template = FLOOR, batch = "gmo_regenerate_config", timeoutTicks = 1200)
    public static void hisRestingTimeComesFromTheConfig(GameTestHelper helper) {
        int was = Config.DOWNED_SECONDS.get();
        Config.DOWNED_SECONDS.set(3);
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setHealth(10.0F);
        godzilla.setEvolves(false);
        detonateMine(helper, 4, 14);
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING, "at zero health he should collapse, was " + godzilla.getPhase());
        Map<GodzillaEntity.Phase, Integer> starts = phaseStarts(helper, godzilla);
        helper.onEachTick(() -> {
            if (starts.containsKey(GodzillaEntity.Phase.EMERGING)) {
                Config.DOWNED_SECONDS.set(was); // (he's up: the other tests get the file's own value back)
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.isActive(), "he should get back up, was " + godzilla.getPhase());
            Config.DOWNED_SECONDS.set(was);
            int collapse = ticksBetween(starts, GodzillaEntity.Phase.COLLAPSING, GodzillaEntity.Phase.DEAD);
            int down = ticksBetween(starts, GodzillaEntity.Phase.DEAD, GodzillaEntity.Phase.EMERGING);
            helper.assertTrue(Math.abs(collapse - 100) <= 2, "his collapse should still take its 5 s (100 of his ticks), took " + collapse);
            helper.assertTrue(Math.abs(down - 60) <= 2, "with downedSeconds = 3 he should lie down 60 of his ticks after his collapse, lay " + down);
            remove(godzilla);
        });
    }

    @GameTest(template = FLOOR, batch = "gmo_kamikaze", timeoutTicks = 60)
    public static void shindenFlownIntoGodzillaDetonates(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        ShindenEntity shinden = helper.spawn(ModEntities.SHINDEN.get(), 4, 3, 16);
        float before = godzilla.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(shinden.isRemoved(), "a Shinden this close to Godzilla should blow up");
            helper.assertTrue(godzilla.getHealth() < before, "the blast should hurt Godzilla");
            remove(godzilla);
        });
    }

    /** A piloted Shinden, its box centred on {@code at}, for flying into Godzilla. */
    private static ShindenEntity planeAt(GameTestHelper helper, Vec3 at, Player pilot) {
        ShindenEntity plane = helper.spawn(ModEntities.SHINDEN.get(), 4, 3, 4);
        plane.moveTo(at.x, at.y - plane.getBbHeight() / 2.0, at.z, 0.0F, 0.0F);
        pilot.startRiding(plane, true);
        return plane;
    }

    /** Burnt from his breath, a Shinden flown into his face at full speed takes all his health: he collapses. */
    @GameTest(template = FLOOR, batch = "gmo_face_crash", timeoutTicks = 20)
    public static void aShindenInHisBurntFaceBringsHimDown(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setBurnTicks(GodzillaEntity.BURN_TICKS);
        Vec3 forward = Vec3.directionFromRotation(0.0F, godzilla.yBodyRot);
        Player pilot = helper.makeMockPlayer();
        ShindenEntity plane = planeAt(helper, godzilla.facePosition().add(forward.scale(3.0)), pilot);
        helper.assertTrue(godzilla.shindenStrike(plane, forward.scale(-1.6)), "flying into his face should hit it");
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING, "burnt, he should collapse, was " + godzilla.getPhase());
        helper.assertTrue(plane.isRemoved(), "the plane should crash");
        pilot.stopRiding();
        remove(godzilla);
        helper.succeed();
    }

    /** ...but not when his mouth isn't burnt: then it's an ordinary crash (a naval mine's worth). */
    @GameTest(template = FLOOR, batch = "gmo_face_crash_unburnt", timeoutTicks = 20)
    public static void aShindenInHisUnburntFaceIsAnOrdinaryCrash(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        helper.assertTrue(godzilla.getBurnTicks() == 0, "he shouldn't start out burnt");
        Vec3 forward = Vec3.directionFromRotation(0.0F, godzilla.yBodyRot);
        Player pilot = helper.makeMockPlayer();
        ShindenEntity plane = planeAt(helper, godzilla.facePosition().add(forward.scale(3.0)), pilot);
        helper.assertTrue(godzilla.shindenStrike(plane, forward.scale(-1.6)), "flying into his face should hit it");
        helper.assertTrue(godzilla.isActive() && godzilla.getHealth() > godzilla.getMaxHealth() * 0.5F,
                "unburnt, he should only lose a mine's worth, was " + godzilla.getPhase() + " / " + godzilla.getHealth());
        pilot.stopRiding();
        remove(godzilla);
        helper.succeed();
    }

    /**
     * Near him, a mob gets Radiation (not Wither); one in the whole hazmat suit gets nothing, and radiation can't hurt
     * it (his charge's heat included).
     */
    @GameTest(template = FLOOR, batch = "gmo_radiation", timeoutTicks = 100)
    public static void hisRadiationGivesRadiationAndTheHazmatSuitKeepsItOff(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        Zombie bare = helper.spawn(EntityType.ZOMBIE, 2, 3, 2);
        Zombie suited = helper.spawn(EntityType.ZOMBIE, 6, 3, 2);
        for (Zombie zombie : new Zombie[] {bare, suited}) {
            zombie.setNoAi(true);
            zombie.setSilent(true);
        }
        suited.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.HAZMAT_HOOD.get()));
        suited.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.HAZMAT_SUIT.get()));
        suited.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.HAZMAT_TROUSERS.get()));
        suited.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.HAZMAT_BOOTS.get()));
        helper.assertTrue(Radiation.shielded(suited) && !Radiation.shielded(bare), "only the suited one should be shielded");
        float before = suited.getHealth();
        suited.hurt(ModDamageTypes.source(helper.getLevel(), ModDamageTypes.RADIATION, godzilla), 5.0F);
        helper.assertTrue(suited.getHealth() == before, "radiation shouldn't hurt someone in the suit");
        helper.succeedWhen(() -> {
            helper.assertTrue(bare.hasEffect(ModEffects.RADIATION.get()), "near him, Radiation");
            helper.assertFalse(bare.hasEffect(MobEffects.WITHER), "Radiation, not Wither");
            helper.assertFalse(suited.hasEffect(ModEffects.RADIATION.get()), "the hazmat suit should keep Radiation off");
            remove(godzilla, bare, suited);
        });
    }

    /** Taking his deep breath in before the beam, a Shinden flown into his face brings him down in one hit. */
    @GameTest(template = FLOOR, batch = "gmo_inhale_crash", timeoutTicks = 20)
    public static void aShindenInHisFaceWhileHeBreathesInBringsHimDown(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.startInhaling();
        helper.assertTrue(godzilla.isInhaling() && godzilla.getBurnTicks() == 0, "he should be breathing in, unburnt");
        Vec3 forward = Vec3.directionFromRotation(0.0F, godzilla.yBodyRot);
        Player pilot = helper.makeMockPlayer();
        ShindenEntity plane = planeAt(helper, godzilla.facePosition().add(forward.scale(3.0)), pilot);
        helper.assertTrue(godzilla.shindenStrike(plane, forward.scale(-1.6)), "flying into his face should hit it");
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING, "breathing in, he should collapse, was " + godzilla.getPhase());
        helper.assertFalse(godzilla.isCharging(), "his charge should be over");
        pilot.stopRiding();
        remove(godzilla);
        helper.succeed();
    }

    /** ...and so does one blown up by his radiation anywhere near him then (not only in his face)... */
    @GameTest(template = FLOOR, batch = "gmo_inhale_kamikaze", timeoutTicks = 120)
    public static void aShindenBlownUpOnHimWhileHeBreathesInBringsHimDown(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.startInhaling();
        Vec3 forward = Vec3.directionFromRotation(0.0F, godzilla.yBodyRot);
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        Player pilot = helper.makeMockPlayer();
        ShindenEntity plane = planeAt(helper, godzilla.position().add(side.scale(godzilla.getBbWidth() / 2.0 + 6.0)).add(0.0, 20.0, 0.0), pilot);
        helper.succeedWhen(() -> {
            helper.assertTrue(plane.isRemoved(), "the Shinden should blow up so close to him");
            helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING, "breathing in, he should collapse, was "
                    + godzilla.getPhase() + " / " + godzilla.getHealth());
            pilot.stopRiding();
            remove(godzilla);
        });
    }

    /** ...and so does a Torrid burst into him; outside the in-breath the Torrid does nothing to him. */
    @GameTest(template = FLOOR, batch = "gmo_inhale_torrid", timeoutTicks = 20)
    public static void aTorridBurstWhileHeBreathesInBringsHimDown(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        Vec3 forward = Vec3.directionFromRotation(0.0F, godzilla.yBodyRot);
        Player pilot = helper.makeMockPlayer();
        Vec3 face = godzilla.facePosition();
        ShindenEntity plane = planeAt(helper, face.add(forward.scale(24.0)), pilot);
        plane.moveTo(plane.getX(), plane.getY(), plane.getZ(), godzilla.yBodyRot + 180.0F, 0.0F);
        plane.setXRot(0.0F);
        // (the test's pocket is deep in the rock: a clear line of fire from the plane to his face, as in the open air)
        Vec3 muzzle = plane.position().add(0.0, 1.5, 0.0);
        for (double t = 0.0; t <= 1.0; t += 0.02) {
            BlockPos at = BlockPos.containing(muzzle.add(face.subtract(muzzle).scale(t)));
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        plane.fireGuns(pilot);
        helper.assertTrue(godzilla.isActive() && godzilla.getHealth() == godzilla.getMaxHealth(),
                "not breathing in, the Torrid shouldn't hurt him, was " + godzilla.getPhase() + " / " + godzilla.getHealth());
        godzilla.startInhaling();
        plane.fireGuns(pilot);
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING, "breathing in, one burst should bring him down, was "
                + godzilla.getPhase() + " / " + godzilla.getHealth());
        pilot.stopRiding();
        remove(godzilla, plane);
        helper.succeed();
    }

    /** Burnt or not, a Shinden into his chest, or one only drifting into his face, doesn't count as a face crash. */
    @GameTest(template = FLOOR, batch = "gmo_face_crash_miss", timeoutTicks = 20)
    public static void onlyAFullSpeedHitOnHisFaceCounts(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setBurnTicks(GodzillaEntity.BURN_TICKS);
        Vec3 forward = Vec3.directionFromRotation(0.0F, godzilla.yBodyRot);
        Player pilot = helper.makeMockPlayer();
        ShindenEntity chest = planeAt(helper, godzilla.position().add(forward.scale(7.0)).add(0.0, 20.0, 0.0), pilot);
        helper.assertFalse(godzilla.shindenStrike(chest, forward.scale(-1.6)), "his chest is not his face");
        pilot.stopRiding();
        ShindenEntity slow = planeAt(helper, godzilla.facePosition().add(forward.scale(3.0)), pilot);
        helper.assertFalse(godzilla.shindenStrike(slow, forward.scale(-0.3)), "drifting into him is no crash");
        helper.assertFalse(godzilla.shindenStrike(slow, forward.scale(1.6)), "flying away from his face is no crash");
        helper.assertTrue(godzilla.isActive() && godzilla.getHealth() == godzilla.getMaxHealth(), "he should be unhurt");
        pilot.stopRiding();
        remove(godzilla, chest, slow);
        helper.succeed();
    }

    /**
     * Naval mines turn up in end city, nether fortress and ancient city chests, on top of their usual loot (added by a
     * global loot modifier, nothing replaced): 1 to 3 of them, three the rarest (weights 6:3:1).
     */
    @GameTest(template = FLOOR, batch = "gmo_loot", timeoutTicks = 20)
    public static void navalMinesTurnUpInEndCityFortressAndAncientCityChests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(4, 4, 4)))
                .create(LootContextParamSets.CHEST);
        int[] sizes = new int[4];
        ItemStack found = ItemStack.EMPTY;
        for (ResourceLocation id : new ResourceLocation[]{BuiltInLootTables.END_CITY_TREASURE, BuiltInLootTables.NETHER_BRIDGE,
                BuiltInLootTables.ANCIENT_CITY}) {
            LootTable table = level.getServer().getLootData().getLootTable(id);
            int chests = 1000;
            int withMines = 0;
            int others = 0;
            for (int i = 0; i < chests; i++) {
                int mines = 0;
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (stack.is(ModItems.NAVAL_MINE_SPAWN_EGG.get())) {
                        mines += stack.getCount();
                        found = stack;
                    } else {
                        others++;
                    }
                }
                if (mines > 0) {
                    withMines++;
                    helper.assertTrue(mines <= 3, id + ": " + mines + " mines in one chest");
                    sizes[mines]++;
                }
            }
            helper.assertTrue(withMines > chests / 20 && withMines < chests / 2, id + ": mines in " + withMines + " of " + chests + " chests");
            helper.assertTrue(others > chests, id + " should still hold its usual loot");
        }
        helper.assertTrue(sizes[1] > sizes[2] && sizes[2] > sizes[3] && sizes[3] > 0,
                "1 mine most often, 3 the rarest: " + sizes[1] + " / " + sizes[2] + " / " + sizes[3]);
        ItemStack mine = new ItemStack(ModItems.NAVAL_MINE_SPAWN_EGG.get());
        helper.assertTrue(level.getRecipeManager().getRecipes().stream().anyMatch(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), mine)),
                "the naval mine's recipe should still be there");
        // what a chest gives is the mod's own naval mine, the one its recipe makes: used on the ground, it puts a mine there
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, found.copyWithCount(1));
        BlockPos ground = helper.absolutePos(new BlockPos(4, floorY(helper), 4));
        // (its own bit of floor, clear above: the tests run on ground near each other, and a crash or a blast next door
        // can have holed this one)
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        for (int up = 1; up <= 3; up++) {
            level.setBlockAndUpdate(ground.above(up), Blocks.AIR.defaultBlockState());
        }
        found.copyWithCount(1).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false)));
        List<NavalMineEntity> placed = level.getEntitiesOfClass(NavalMineEntity.class, new AABB(ground).inflate(2.0));
        helper.assertTrue(placed.size() == 1, "a naval mine from a chest should put a naval mine down, put " + placed.size());
        remove(placed.toArray(new Entity[0]));
        helper.succeed();
    }

    /**
     * A freshly spawned Odo Island Monster's spawn cry is all that's heard: his growl waits its full interval (vanilla
     * starts it at zero, which made it come within a couple of seconds), and he doesn't roar over the cry.
     */
    @GameTest(template = FLOOR, batch = "gmo_odo_spawn", timeoutTicks = 20)
    public static void theOdoMonstersSpawnCryIsHeardAlone(GameTestHelper helper) {
        OdoIslandMonsterEntity odo = spawnOdo(helper, floorY(helper), 6);
        odo.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(odo.blockPosition()), MobSpawnType.SPAWN_EGG, null, null);
        helper.assertTrue(odo.getAmbientSoundTime() <= -odo.getAmbientSoundInterval() + 1, "his growl should wait, timer " + odo.getAmbientSoundTime());
        helper.assertTrue(odo.getSpawnCryTicks() > 60, "he shouldn't roar over his spawn cry");
        remove(odo);
        helper.succeed();
    }

    /**
     * Playing as Godzilla, his third key (J) is his bite, and plays his bite (not the claw swing he came with); playing
     * as the Odo Island Monster, running is made 12 blocks a second on level ground (a player walks 4.317 at 0.1; the
     * real distance a second is measured in the game itself, client.harness.ShapeScenes).
     */
    @GameTest(template = FLOOR, batch = "gmo_playable", timeoutTicks = 20)
    public static void thePlayableShapesMovesAreWiredUp(GameTestHelper helper) {
        PlayerGodzilla.Move bite = PlayerGodzilla.Move.values()[2];
        helper.assertTrue(bite == PlayerGodzilla.Move.BITE && "bite".equals(bite.animation),
                "J (the third key) should bite with his bite animation, was " + bite + " / " + bite.animation);
        helper.assertTrue(PlayerGodzilla.Move.ATOMIC_BREATH.length == GodzillaEntity.BEAM_END_TICK + GodzillaEntity.ROAR_TICKS,
                "his breath (and his being rooted) should last until the roar after the beam is over");
        PlayerGodzilla.Move strike = PlayerGodzilla.Move.values()[5];
        helper.assertTrue(strike == PlayerGodzilla.Move.TAIL_STRIKE && "tail_strike".equals(strike.animation)
                        && strike.length == 64, "X (the sixth key) should be Minus Zero's tail strike, was " + strike + " / " + strike.animation);
        helper.assertTrue(Math.abs(PlayerOdo.runSpeedAttribute() * 43.17 - 12.0) < 1.0E-6, "the Odo should run at 12 blocks a second");
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_kill")
    public static void killCommandStillWorks(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.kill();
        helper.assertFalse(godzilla.isAlive(), "/kill should kill him for good");
        helper.succeed();
    }

    /** A pig sealed in a one-block cell at (4, floor + 1, z), its chunk kept loaded until {@code release} runs. */
    private static Pig cellPig(GameTestHelper helper, int z, List<Runnable> release) {
        int floor = floorY(helper);
        BlockPos cell = new BlockPos(4, floor + 1, z);
        for (BlockPos wall : new BlockPos[]{cell.below(), cell.above(), cell.north(), cell.south(), cell.east(), cell.west()}) {
            helper.setBlock(wall, Blocks.STONE.defaultBlockState());
        }
        helper.setBlock(cell, Blocks.AIR.defaultBlockState());
        BlockPos abs = helper.absolutePos(cell);
        helper.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, true);
        release.add(() -> helper.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, false));
        return helper.spawn(EntityType.PIG, cell);
    }

    /**
     * Game tests run in a pocket dug out at y -60 of a normal world, so the beam hits rock right at his mouth.
     * The pig waits in a sealed cell 40 blocks ahead: out of his sight and beyond his footsteps and the heat around him
     * while he charges, but well inside the blast (closer than minTargetDistance, so that's lifted for this test).
     */
    @GameTest(template = FLOOR, batch = "gmo_atomic_breath", timeoutTicks = 560)
    public static void atomicBreathKillsHisTargetThenRecharges(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setFirstBreathDelay(0);
        godzilla.setBreathMinDistance(0.0);
        List<Runnable> release = new ArrayList<>();
        Pig pig = cellPig(helper, 44, release);
        godzilla.setTarget(pig);
        helper.runAtTickTime(120, () -> helper.assertTrue(godzilla.isCharging(), "with a target he should be charging his atomic breath"));
        // The charge starts ~2 s after he spots his target; with the 4 s in-breath the blast is ~17.6 s later.
        helper.runAtTickTime(320, () -> {
            helper.assertTrue(godzilla.getBreathCooldown() == 0, "the 4 s in-breath comes before the blast, but he already fired");
            helper.assertTrue(pig.isAlive(), "nothing but the blast should have killed the pig");
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(pig.isAlive(), "the atomic breath should kill his target");
            helper.assertTrue(godzilla.getBreathCooldown() > 170 * 20, "after firing he needs ~3 minutes to recharge, cooldown "
                    + godzilla.getBreathCooldown());
            helper.assertTrue(godzilla.getBurnTicks() > 0, "once the beam is over his mouth should be burnt");
            remove(godzilla);
            release.forEach(Runnable::run);
        });
    }

    /** A target closer than minTargetDistance (75 blocks) gets walked up to and bitten or clawed, not breathed on. */
    @GameTest(template = FLOOR, batch = "gmo_breath_near", timeoutTicks = 200)
    public static void heUsesMeleeOnTargetsCloserThan75Blocks(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setFirstBreathDelay(0);
        helper.assertTrue(Config.BREATH_MIN_TARGET_DISTANCE.get() == 75, "the default minTargetDistance should be 75");
        List<Runnable> release = new ArrayList<>();
        Pig pig = cellPig(helper, 44, release);
        pig.setInvulnerable(true);
        godzilla.setTarget(pig);
        helper.runAtTickTime(160, () -> {
            helper.assertFalse(godzilla.isCharging(), "a target 40 blocks away is too close for his atomic breath");
            helper.assertTrue(godzilla.getTarget() == pig, "he should still be after it");
            remove(godzilla, pig);
            release.forEach(Runnable::run);
            helper.succeed();
        });
    }

    /** A target 75 blocks away or more is fair game for the atomic breath. */
    @GameTest(template = FLOOR, batch = "gmo_breath_far", timeoutTicks = 200)
    public static void heBreathesAtTargets75BlocksAwayOrMore(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setFirstBreathDelay(0);
        List<Runnable> release = new ArrayList<>();
        Pig pig = cellPig(helper, 120, release); // (~115 blocks: he closes in a little before he decides)
        pig.setInvulnerable(true);
        godzilla.setTarget(pig);
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(godzilla.isCharging(), "a target this far off should get his atomic breath, distance "
                    + (int) godzilla.distanceTo(pig));
            remove(godzilla, pig);
            release.forEach(Runnable::run);
            helper.succeed();
        });
    }

    /**
     * After the beam he roars once, the whole roar, and then he's done: not charging, not roaring (a roar of his own
     * that fell due during the charge isn't tacked on), free to move again.
     */
    @GameTest(template = FLOOR, batch = "gmo_breath_recovery", timeoutTicks = 700)
    public static void afterHisBreathHeRoarsOnceThenMovesOn(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setFirstBreathDelay(0);
        godzilla.setBreathMinDistance(0.0);
        godzilla.setRoarTimer(60); // falls due while he charges
        List<Runnable> release = new ArrayList<>();
        Pig pig = cellPig(helper, 44, release);
        godzilla.setTarget(pig);
        long[] roarAt = {-1};
        long[] endAt = {-1};
        helper.succeedWhen(() -> {
            long now = helper.getTick();
            if (roarAt[0] < 0 && godzilla.getChargeState() == GodzillaEntity.CHARGE_COOLING) {
                helper.assertTrue(godzilla.isRoaring(), "he should roar as the beam ends");
                roarAt[0] = now;
            }
            if (roarAt[0] >= 0 && endAt[0] < 0 && !godzilla.isCharging()) {
                endAt[0] = now;
                helper.assertTrue(now - roarAt[0] >= GodzillaEntity.ROAR_TICKS - 2, "the roar was cut short: "
                        + (now - roarAt[0]) + " ticks");
            }
            helper.assertTrue(endAt[0] >= 0 && now >= endAt[0] + 60, "waiting for the charge to end, then 3 s more");
            helper.assertFalse(godzilla.isCharging(), "the charge should be over");
            helper.assertFalse(godzilla.isRoaring(), "he shouldn't roar again right after his post-breath roar");
            helper.assertTrue(godzilla.getChargeState() == GodzillaEntity.CHARGE_NONE, "his charge state should be reset");
            remove(godzilla, pig);
            release.forEach(Runnable::run);
        });
    }

    /** Within 200 blocks a roar is the roar itself; from 200 on it's a distant roar, the two taking turns. */
    @GameTest(template = FLOOR, batch = "gmo_far_roar", timeoutTicks = 20)
    public static void listenersFarAwayHearTheDistantRoars(GameTestHelper helper) {
        double far = GodzillaEntity.FAR_ROAR_DISTANCE;
        helper.assertTrue(far == 200.0, "the distant roars should start at 200 blocks");
        helper.assertFalse(GodzillaEntity.isFarListener(199.9 * 199.9, far), "199.9 blocks away should hear the roar itself");
        helper.assertTrue(GodzillaEntity.isFarListener(200.0 * 200.0, far), "200 blocks away should hear a distant roar");
        helper.assertTrue(GodzillaEntity.isFarListener(2500.0 * 2500.0, far), "2500 blocks away should hear a distant roar");
        Vec3 at = helper.absoluteVec(new Vec3(4, 4, 4));
        var first = GodzillaEntity.nextFarRoar();
        GodzillaEntity.broadcastRoar(helper.getLevel(), at.x, at.y, at.z, ModSounds.GODZILLA_ROAR.get(), 0L);
        var second = GodzillaEntity.nextFarRoar();
        GodzillaEntity.broadcastRoar(helper.getLevel(), at.x, at.y, at.z, ModSounds.GODZILLA_ROAR.get(), 0L);
        helper.assertTrue(first != second, "the two distant roars should take turns");
        helper.assertTrue(GodzillaEntity.nextFarRoar() == first, "...back to the first one");
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_plane_first", timeoutTicks = 60)
    public static void aPilotedPlaneBecomesHisTarget(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        int floor = floorY(helper);
        Runnable pigRoom = room(helper, floor, 3, 19, 5, 21, 2);
        Runnable hangar = room(helper, floor, 1, 41, 7, 47, 4);
        Pig pig = helper.spawn(EntityType.PIG, 4, floor + 1, 20);
        godzilla.setTarget(pig);
        ShindenEntity plane = helper.spawn(ModEntities.SHINDEN.get(), 4, floor + 1, 44);
        Pig pilot = helper.spawn(EntityType.PIG, 4, floor + 1, 44);
        pilot.startRiding(plane, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.getTarget() == plane, "a plane with someone in it should come first, target was " + godzilla.getTarget());
            remove(godzilla, pig, pilot, plane);
            pigRoom.run();
            hangar.run();
        });
    }

    @GameTest(template = FLOOR, batch = "gmo_swim", timeoutTicks = 420)
    public static void godzillaSwimsAtTheSurfaceOfDeepWater(GameTestHelper helper) {
        // A 20 deep pool with unbreakable walls and bottom.
        int depth = 20;
        int floor = floorY(helper);
        fillPool(helper, floor, depth, false);
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.moveTo(helper.absoluteVec(new Vec3(4.5, floor + 1, 4.5)));
        double surface = helper.absoluteVec(new Vec3(0, floor + depth + 1, 0)).y - 0.1;
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.isSwimmingAtSurface(), "in water this deep he should swim");
            double expected = surface - GodzillaEntity.SWIM_SINK;
            helper.assertTrue(Math.abs(godzilla.getY() - expected) < 1.0,
                    "he should float with his back just under the surface: y " + godzilla.getY() + ", expected ~" + expected);
            helper.assertTrue(godzilla.getSwimPeeks() >= 1, "every 10 seconds his head should come up out of the water");
            remove(godzilla);
            fillPool(helper, floor, depth, true);
        });
    }

    /**
     * Floating at the surface, his 40 block tall hitbox reaches far above the test's pocket into whatever rock the
     * (random) test world has there, which would stop him rising: the pool gets a clear, walled shaft above it.
     */
    private static final int POOL_HEADROOM = 44;

    private static void fillPool(GameTestHelper helper, int floor, int depth, boolean drain) {
        for (int y = floor + depth + POOL_HEADROOM; y >= floor; y--) {
            for (int x = -3; x <= 11; x++) {
                for (int z = -3; z <= 11; z++) {
                    boolean wall = x == -3 || x == 11 || z == -3 || z == 11 || y == floor;
                    BlockState state;
                    if (drain) {
                        state = y == floor && x >= 0 && x <= 8 && z >= 0 && z <= 8 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    } else if (y > floor + depth) {
                        state = wall ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    } else {
                        state = wall ? Blocks.BARRIER.defaultBlockState() : Blocks.WATER.defaultBlockState();
                    }
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    @GameTest(template = FLOOR, batch = "gmo_footprint")
    public static void footstepsLeaveAFootprint(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        int floor = floorY(helper);
        godzilla.moveTo(helper.absoluteVec(new Vec3(4.5, floor + 1, 4.5)));
        helper.setBlock(new BlockPos(4, floor + 1, 6), Blocks.POPPY.defaultBlockState());
        Vec3 foot = helper.absoluteVec(new Vec3(4.5, floor + 1, 4.5));
        // below walkBreaksFromY (60; the test is at -60) his foot comes down and nothing gives
        godzilla.setWalkFloorY(null);
        helper.assertTrue(godzilla.walkFloorY() == 60, "by default he should destroy nothing below Y 60 as he walks, was " + godzilla.walkFloorY());
        Destruction.footprint(helper.getLevel(), godzilla, foot, 0.0F, 2.6, 4.6);
        helper.assertBlockPresent(Blocks.POPPY, new BlockPos(4, floor + 1, 6));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, floor, 4));
        // at it or above, the footprint
        godzilla.setWalkFloorY(floor - 10 + helper.absolutePos(BlockPos.ZERO).getY());
        Destruction.footprint(helper.getLevel(), godzilla, foot, 0.0F, 2.6, 4.6);
        remove(godzilla);
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, floor + 1, 6));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, floor, 4));
        helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(4, floor, 7));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(0, floor, 4));
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_plane_item")
    public static void hittingAPlaneKnocksItIntoItsItem(GameTestHelper helper) {
        ShindenEntity plane = helper.spawn(ModEntities.SHINDEN.get(), 4, 1, 4);
        Player player = helper.makeMockPlayer();
        float health = plane.getHealth();
        plane.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertFalse(plane.isRemoved(), "one light hit should only rock it");
        helper.assertTrue(plane.getHealth() == health, "it isn't hurt like a mob");
        helper.assertTrue(plane.getWobbleTime() > 0, "it should wobble");
        plane.hurt(helper.getLevel().damageSources().playerAttack(player), 5.0F);
        helper.assertTrue(plane.isRemoved(), "a few hits should knock it down like a boat");
        helper.assertItemEntityPresent(ModItems.SHINDEN_SPAWN_EGG.get(), new BlockPos(4, 1, 4), 3.0);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_combo", timeoutTicks = 300)
    public static void meleeComboIsTwoSwipesThenASpin(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setBreathCooldown(100000);
        godzilla.setYRot(0.0F);
        godzilla.setYBodyRot(0.0F);
        godzilla.setYHeadRot(0.0F);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(4.5, floorY(helper) + 1, 20.5));   // right in front of him
        pig.setNoAi(true);
        pig.setInvulnerable(true);
        godzilla.setTarget(pig);
        List<String> seen = new ArrayList<>();
        seen.add(godzilla.nextComboAttack());
        helper.succeedWhen(() -> {
            String next = godzilla.nextComboAttack();
            if (!next.equals(seen.get(seen.size() - 1))) {
                seen.add(next);
            }
            helper.assertTrue(seen.size() >= 4, "waiting for three attacks, so far " + seen);
            helper.assertTrue(seen.subList(0, 4).equals(List.of("swipe_right", "swipe_left", "spin_whip", "swipe_right")),
                    "a swipe of each hand, then the spin: " + seen);
            remove(godzilla, pig);
        });
    }

    @GameTest(template = FLOOR, batch = "gmo_mine_pickup")
    public static void hittingANavalMineKnocksItLoose(GameTestHelper helper) {
        NavalMineEntity mine = helper.spawn(ModEntities.NAVAL_MINE.get(), new Vec3(4.5, floorY(helper) + 1, 4.5));
        Player player = helper.makeMockPlayer();
        for (int i = 1; i < NavalMineEntity.HITS_TO_PICK_UP; i++) {
            mine.hurt(helper.getLevel().damageSources().playerAttack(player), 3.0F);
            helper.assertFalse(mine.isRemoved(), "hit " + i + " should only rock it");
        }
        helper.assertTrue(mine.getHealth() == mine.getMaxHealth(), "hitting it by hand mustn't hurt (or set off) the mine");
        mine.hurt(helper.getLevel().damageSources().playerAttack(player), 3.0F);
        helper.assertTrue(mine.isRemoved(), "a few hits should knock it loose");
        helper.assertItemEntityPresent(ModItems.NAVAL_MINE_SPAWN_EGG.get(), new BlockPos(4, floorY(helper) + 1, 4), 3.0);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_detonator")
    public static void theRemoteDetonatorSetsOffTheMineInSight(GameTestHelper helper) {
        int floor = floorY(helper);
        for (int x = 2; x <= 6; x++) {
            for (int y = floor + 1; y <= floor + 4; y++) {
                for (int z = 0; z <= 12; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        NavalMineEntity mine = helper.spawn(ModEntities.NAVAL_MINE.get(), new Vec3(4.5, floor + 1, 10.5));
        Player player = helper.makeMockPlayer();
        player.moveTo(helper.absoluteVec(new Vec3(4.5, floor + 1, 1.5)));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, mine.getBoundingBox().getCenter());
        ItemStack remote = new ItemStack(ModItems.REMOTE_DETONATOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        helper.assertTrue(RemoteDetonatorItem.aimedMine(helper.getLevel(), player) == mine, "the mine should be in its sights");
        remote.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(mine.isRemoved(), "the mine should go off");
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_statue")
    public static void aPoseStatueHoldsStill(GameTestHelper helper) {
        List<GodzillaStatueEntity> statues = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        int y = floorY(helper) + 4;
        for (EntityType<GodzillaStatueEntity> type : List.of(ModEntities.GODZILLA_POSE_CHARGING_ROAR.get(), ModEntities.GODZILLA_POSE_TAIL_WHIP.get(),
                ModEntities.GODZILLA_POSE_SKY_ROAR.get(), ModEntities.GODZILLA_POSE_POSTER_SNARL.get(), ModEntities.GODZILLA_POSE_TAIL_HOOK.get(),
                ModEntities.GODZILLA_POSE_HUNCHED_SNARL.get())) {
            GodzillaStatueEntity statue = helper.spawn(type, new Vec3(4.5, y + 2 * statues.size(), 4.5));
            helper.assertFalse(statue.hurt(helper.getLevel().damageSources().generic(), 1000.0F), "a statue can't be hurt");
            statues.add(statue);
            spots.add(statue.position());
        }
        helper.assertTrue(statues.get(3).getStance().glow == GodzillaStatueEntity.Glow.CHARGED, "the poster's statue should be fully charged");
        helper.assertTrue(statues.get(4).getStance().glow == GodzillaStatueEntity.Glow.NONE, "the tail hook's statue should be unlit");
        helper.runAfterDelay(20, () -> {
            for (int i = 0; i < statues.size(); i++) {
                GodzillaStatueEntity statue = statues.get(i);
                helper.assertTrue(statue.isAlive() && statue.position().equals(spots.get(i)),
                        statue.getStance().id + " should stay just where it was put, even in mid-air");
                statue.discard();
            }
            helper.succeed();
        });
    }

    private static OdoIslandMonsterEntity spawnOdo(GameTestHelper helper, int floor, int z) {
        return spawnOdo(helper, floor, 4.5, z);
    }

    private static OdoIslandMonsterEntity spawnOdo(GameTestHelper helper, int floor, double x, int z) {
        for (GodzillaEntity leftover : helper.getLevel().getEntities(ModEntities.GODZILLA.get(), e -> true)) {
            leftover.discard();
        }
        for (OdoIslandMonsterEntity leftover : helper.getLevel().getEntities(ModEntities.ODO_ISLAND_MONSTER.get(), e -> true)) {
            leftover.discard();
        }
        return helper.spawn(ModEntities.ODO_ISLAND_MONSTER.get(), new Vec3(x, floor + 1, z + 0.5));
    }

    @GameTest(template = FLOOR, batch = "gmo_odo_burnt", timeoutTicks = 120)
    public static void aNukedOdoMonsterLiesBurntThenRisesAsGodzilla(GameTestHelper helper) {
        OdoIslandMonsterEntity odo = spawnOdo(helper, floorY(helper), 4);
        Blasts.nuclearHit(odo, ModDamageTypes.source(helper.getLevel(), ModDamageTypes.NUCLEAR_BOMB, null), true);
        helper.assertTrue(odo.isBurnt() && odo.isAlive(), "a nuke should burn him, not kill him");
        helper.assertTrue(odo.getBurntTicksLeft() == 3 * 60 * 20, "he should lie burnt for 3 minutes, " + odo.getBurntTicksLeft() + " ticks");
        helper.assertFalse(odo.hurt(helper.getLevel().damageSources().generic(), 1000.0F), "burnt, he can't be hurt any more");
        odo.setBurntTicksLeft(20);
        Vec3 where = odo.position();
        helper.succeedWhen(() -> {
            helper.assertTrue(odo.isRemoved(), "after lying burnt he should be gone");
            List<GodzillaEntity> godzillas = helper.getLevel().getEntitiesOfClass(GodzillaEntity.class, new AABB(where, where).inflate(4.0));
            helper.assertTrue(godzillas.size() == 1, "he should rise again as Godzilla");
            helper.assertTrue(godzillas.get(0).getPhase() == GodzillaEntity.Phase.RISING, "Godzilla should rise roaring, was "
                    + godzillas.get(0).getPhase());
            remove(godzillas.get(0));
        });
    }

    /** A bomb dropped from a few blocks up (it falls from 200 in the game): the monster is burnt, a pig is not so lucky. */
    @GameTest(template = FLOOR, batch = "gmo_bomb", timeoutTicks = 160)
    public static void aNuclearBombBurnsTheOdoMonster(GameTestHelper helper) {
        int floor = floorY(helper);
        OdoIslandMonsterEntity odo = spawnOdo(helper, floor, 4);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 1, 16.5));
        NuclearBombEntity bomb = NuclearBombEntity.drop(helper.getLevel(), helper.absoluteVec(new Vec3(4.5, floor + 1, 10.5)), null, 30, 8.0);
        helper.assertTrue(bomb != null, "the bomb should be dropped");
        helper.runAtTickTime(25, () -> {
            helper.assertFalse(bomb.isRemoved(), "the bomb should still be falling");
            helper.assertFalse(odo.isBurnt(), "nothing should have happened yet");
        });
        long[] boom = {-1};
        helper.succeedWhen(() -> {
            helper.assertTrue(bomb.isRemoved(), "the bomb should have gone off");
            helper.assertTrue(odo.isBurnt() && odo.isAlive(), "the monster should be burnt, not dead");
            helper.assertFalse(pig.isAlive(), "the blast should kill the pig");
            if (boom[0] < 0) {
                boom[0] = helper.getTick();
            }
            // let the blast wave finish before the next test is set up next door
            helper.assertTrue(helper.getTick() - boom[0] >= 60, "waiting for the blast wave to pass");
            remove(odo);
        });
    }

    /**
     * A searchlight shining down on water lights up its surface, where the beam is seen to land (the light sources go in
     * the air over it), not the bottom of the pool, which it can't reach for the water in the way.
     */
    @GameTest(template = FLOOR, batch = "gmo_searchlight_water", timeoutTicks = 80)
    public static void aSearchlightOnWaterLightsItsSurface(GameTestHelper helper) {
        int floor = floorY(helper);
        Runnable release = room(helper, floor, 0, 0, 8, 16, 8);
        for (int x = 0; x <= 8; x++) {
            for (int z = 2; z <= 16; z++) {
                for (int y = floor + 1; y <= floor + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
                }
            }
        }
        for (int y = floor + 1; y <= floor + 4; y++) {
            helper.setBlock(new BlockPos(4, y, 1), Blocks.STONE.defaultBlockState());
        }
        SearchlightEntity light = helper.spawn(ModEntities.SEARCHLIGHT.get(), new Vec3(4.5, floor + 5, 1.5));
        Pig operator = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 5, 0.5));
        operator.setNoAi(true);
        helper.assertTrue(operator.startRiding(light), "the searchlight should be mountable");
        operator.setYRot(0.0F); // south, down at the water
        operator.setXRot(30.0F);
        int surface = helper.absolutePos(new BlockPos(0, floor + 5, 0)).getY();
        helper.succeedWhen(() -> {
            helper.assertTrue(light.isLit(), "manned, the searchlight should be lit");
            helper.assertTrue(light.getLights().size() >= 2, "the beam should light up the water where it lands, lights " + light.getLights());
            for (BlockPos pos : light.getLights()) {
                helper.assertTrue(pos.getY() == surface && helper.getLevel().getFluidState(pos.below()).is(FluidTags.WATER),
                        "its lights should be just over the water, one at " + pos + " (surface " + surface + ")");
            }
            remove(light, operator);
            release.run();
        });
    }

    @GameTest(template = FLOOR, batch = "gmo_searchlight", timeoutTicks = 60)
    public static void aSearchlightOnTheOdoMonsterEnragesIt(GameTestHelper helper) {
        int floor = floorY(helper);
        // a clear view down to him (the test's pocket may end before he does)
        for (int x = 0; x <= 8; x++) {
            for (int y = floor + 1; y <= floor + 4; y++) {
                for (int z = 0; z <= 18; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        OdoIslandMonsterEntity odo = spawnOdo(helper, floor, 14);
        odo.setNoAi(true);
        SearchlightEntity light = helper.spawn(ModEntities.SEARCHLIGHT.get(), new Vec3(4.5, floor + 1, 2.5));
        Pig operator = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 1, 1.5));
        operator.setNoAi(true);
        helper.assertTrue(operator.startRiding(light), "the searchlight should be mountable");
        operator.setYRot(0.0F); // looking south, down the corridor at him
        operator.setXRot(0.0F);
        helper.assertFalse(odo.getTarget() == operator, "he shouldn't be after anyone yet");
        helper.succeedWhen(() -> {
            helper.assertTrue(light.isLit(), "manned, the searchlight should be lit");
            helper.assertTrue(odo.getHuntedLight() == light, "caught in the beam he should go for the searchlight first");
            helper.assertTrue(odo.getState() == OdoIslandMonsterEntity.STATE_ANGRY, "he should be enraged");
            helper.assertTrue(odo.isFrenzied(), "a searchlight should send him into a frenzy");
            helper.assertTrue(light.getLights().size() >= 2, "the beam should light up a pool around where it lands, lights "
                    + light.getLights());
            // his jaws come down on it: it's smashed, and he turns on whoever was working it
            odo.smashLight();
            helper.assertTrue(light.isRemoved(), "he should smash the searchlight");
            helper.assertTrue(odo.getTarget() == operator, "then he should go for its operator, target " + odo.getTarget());
            remove(odo, operator);
        });
    }

    /**
     * A searchlight up on a brick tower inside a brick shed, behind a brick wall, over 20 blocks off: having been
     * caught in its beam, he tears through the wall, the shed and the tower, and the light comes down and is smashed
     * (he doesn't get stuck at the wall).
     */
    @GameTest(template = FLOOR, batch = "gmo_odo_tower", timeoutTicks = 900)
    public static void theOdoMonsterTearsDownTheBuildingHoldingASearchlight(GameTestHelper helper) {
        odoTower(helper, "straight on", 4.5);
    }

    /**
     * The same, coming at the wall at an angle from either side: he used to climb onto the stubs of wall he'd left
     * (his breaking stopped a row above his feet) until he was wedged against one too tall to climb, and gave up.
     */
    @GameTest(template = FLOOR, batch = "gmo_odo_tower_right", timeoutTicks = 900)
    public static void theOdoMonsterTearsDownTheBuildingHoldingASearchlightFromTheRight(GameTestHelper helper) {
        odoTower(helper, "from the right", 9.5);
    }

    @GameTest(template = FLOOR, batch = "gmo_odo_tower_left", timeoutTicks = 900)
    public static void theOdoMonsterTearsDownTheBuildingHoldingASearchlightFromTheLeft(GameTestHelper helper) {
        odoTower(helper, "from the left", 0.5);
    }

    @GameTest(template = FLOOR, batch = "gmo_odo_tower_far_right", timeoutTicks = 900)
    public static void theOdoMonsterTearsDownTheBuildingHoldingASearchlightFromFarRight(GameTestHelper helper) {
        odoTower(helper, "from far right", 11.0);
    }

    private static void odoTower(GameTestHelper helper, String run, double startX) {
        int floor = floorY(helper);
        // sealed in first, roof and walls of stone: the test's pocket is deep in a normal world, and with the rock over
        // the room cleared from under it, the gravel in it fell in, in his way (ground, which he doesn't break: twice he
        // stood stuck behind a pile of it till he gave up on the light); lava or water in a cave beside it could flow in
        for (int x = -8; x <= 16; x++) {
            for (int z = -5; z <= 35; z++) {
                helper.setBlock(new BlockPos(x, floor + 17, z), Blocks.STONE.defaultBlockState());
                boolean side = x == -8 || x == 16 || z == -5 || z == 35;
                for (int y = floor; side && y <= floor + 16; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                }
            }
        }
        // room to move: he's 9 wide and 14 tall, and the rock round the test's pocket is ground he doesn't break
        for (int x = -7; x <= 15; x++) {
            for (int y = floor + 1; y <= floor + 16; y++) {
                for (int z = -4; z <= 34; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
            for (int z = -4; z <= 34; z++) {
                helper.setBlock(new BlockPos(x, floor, z), Blocks.STONE.defaultBlockState());
            }
        }
        List<Runnable> release = new ArrayList<>();
        for (int z : new int[]{0, 16, 34}) {
            BlockPos abs = helper.absolutePos(new BlockPos(4, floor, z));
            // (only let go of what this forced: the test framework keeps its tests' chunks forced too)
            if (helper.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, true)) {
                release.add(() -> helper.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, false));
            }
        }
        for (int x = -7; x <= 15; x++) {
            for (int y = floor + 1; y <= floor + 12; y++) {
                helper.setBlock(new BlockPos(x, y, 14), Blocks.STONE_BRICKS.defaultBlockState()); // the wall
            }
        }
        for (int x = 1; x <= 7; x++) {
            for (int z = 24; z <= 30; z++) {
                for (int y = floor + 1; y <= floor + 12; y++) {
                    boolean shell = x == 1 || x == 7 || z == 24 || z == 30 || y == floor + 12;
                    boolean tower = x == 4 && z == 27 && y <= floor + 8;
                    if (shell || tower) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.BRICKS.defaultBlockState());
                    }
                }
            }
        }
        OdoIslandMonsterEntity odo = spawnOdo(helper, floor, startX, 4);
        SearchlightEntity light = helper.spawn(ModEntities.SEARCHLIGHT.get(), new Vec3(4.5, floor + 9, 27.5));
        // (behind him, clear of him: one inside his box shoved him aside, a different way every time)
        Pig operator = helper.spawn(EntityType.PIG, new Vec3(startX, floor + 1, -3.0));
        operator.setNoAi(true);
        odo.spottedBy(light, operator);
        helper.assertTrue(odo.getHuntedLight() == light, "caught in its beam he should go for the searchlight");
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.onEachTick(() -> {
            // (every 20 ticks at first, naming what's in his box: that's how the gravel falling into the room showed up)
            if (helper.getTick() % 100 == 0 || helper.getTick() < 200 && helper.getTick() % 20 == 0 || light.isRemoved() && !odoTowerDone(run)) {
                BlockPos at = BlockPos.containing(odo.position()).subtract(origin);
                GodzillaMinusOne.LOGGER.info("odo tower {} tick {}: odo at {} (yaw {}), hunting the light {}, stuck {}, target {}, light {} at y {}, tower {} blocks",
                        run, helper.getTick(), at.toShortString(), Math.round(odo.getYRot()), odo.getHuntedLight() != null,
                        odo.getLightStuckTicks(), odo.getTarget() == null ? "none" : odo.getTarget().getType().toShortString(),
                        light.isRemoved() ? "smashed" : "standing", String.format("%.1f", light.getY() - origin.getY()),
                        countBlocks(helper, Blocks.BRICKS, 4, floor + 1, 27, 4, floor + 8, 27));
                GodzillaMinusOne.LOGGER.info("odo tower {} tick {}: {}", run, helper.getTick(), odo.debugState());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(light.isRemoved(), "he should bring the building down and smash the searchlight");
            remove(odo, operator);
            release.forEach(Runnable::run);
        });
    }

    /**
     * Going for someone with a building in the way (a wall of deepslate bricks and polished deepslate, which he used to
     * take for rock and stand stuck behind), the Odo Island Monster breaks and bites his way through it to them.
     */
    @GameTest(template = FLOOR, batch = "gmo_odo_wall", timeoutTicks = 600)
    public static void theOdoMonsterBreaksThroughAWallToWhoeverHesAfter(GameTestHelper helper) {
        int floor = floorY(helper);
        Runnable release = room(helper, floor, -7, -4, 15, 34, 16);
        for (int x = -7; x <= 15; x++) {
            for (int y = floor + 1; y <= floor + 12; y++) {
                helper.setBlock(new BlockPos(x, y, 14), (y % 2 == 0 ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE).defaultBlockState());
            }
        }
        OdoIslandMonsterEntity odo = spawnOdo(helper, floor, 4);
        Pig prey = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 1, 24.5));
        prey.setNoAi(true);
        odo.setTarget(prey);
        float full = prey.getHealth();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.onEachTick(() -> {
            if (helper.getTick() % 40 == 0) {
                GodzillaMinusOne.LOGGER.info("odo wall tick {}: odo at {}, target {}, wall {} blocks; {}", helper.getTick(),
                        BlockPos.containing(odo.position()).subtract(origin).toShortString(), odo.getTarget() == null ? "none" : "the pig",
                        countBlocks(helper, Blocks.DEEPSLATE_BRICKS, -7, floor + 1, 14, 15, floor + 12, 14)
                                + countBlocks(helper, Blocks.POLISHED_DEEPSLATE, -7, floor + 1, 14, 15, floor + 12, 14), odo.debugState());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!prey.isAlive() || prey.getHealth() < full, "he should break through the wall and get his jaws on the pig");
            remove(odo, prey);
            release.run();
        });
    }

    /** His claw swipe tears through a wall where his claws rake, and his tail whip knocks away what's built round him. */
    @GameTest(template = FLOOR, batch = "gmo_melee_breaks", timeoutTicks = 40)
    public static void godzillasClawsAndTailTearDownBuildings(GameTestHelper helper) {
        int floor = floorY(helper);
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        // (the rock round the tests' pocket is something his tail breaks now too: cleared, so the pillars are what it reaches)
        clearAround(helper, godzilla.position(), 30, 11);
        Vec3 claws = godzilla.clawPosition(1.0);
        BlockPos rake = BlockPos.containing(claws);
        // a wall of stone bricks where his claws rake...
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                helper.getLevel().setBlockAndUpdate(rake.offset(dx, dy, 0), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        // ...and pillars of polished andesite and bricks round him, 12 to 25 blocks out, within his tail's reach
        Vec3 feet = godzilla.position();
        List<BlockPos> pillars = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            double r = 12.0 + i * 1.8;
            BlockPos base = BlockPos.containing(feet.x + Math.cos(a) * r, feet.y, feet.z + Math.sin(a) * r);
            for (int y = 0; y < 4; y++) {
                helper.getLevel().setBlockAndUpdate(base.above(y), (i % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.BRICKS).defaultBlockState());
            }
            pillars.add(base);
        }
        godzilla.strikeNow("swipe_right");
        int wall = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (helper.getLevel().getBlockState(rake.offset(dx, dy, 0)).is(Blocks.STONE_BRICKS)) {
                    wall++;
                }
            }
        }
        helper.assertTrue(wall == 0, "his claws should tear through the wall, " + wall + " of 25 blocks left");
        godzilla.strikeNow("spin_whip");
        int standing = 0;
        for (BlockPos base : pillars) {
            for (int y = 0; y < 4; y++) {
                BlockState state = helper.getLevel().getBlockState(base.above(y));
                if (state.is(Blocks.POLISHED_ANDESITE) || state.is(Blocks.BRICKS)) {
                    standing++;
                }
            }
        }
        helper.assertTrue(standing == 0, "his tail should knock away what's built round him, " + standing + " of 32 blocks left");
        helper.assertTrue(helper.getBlockState(new BlockPos(4, floor, 4)).is(Blocks.STONE), "the ground should stay");
        remove(godzilla);
        helper.succeed();
    }

    /**
     * Walking, he destroys everything in his path at walkBreaksFromY (60) and above, ground and all (here, with that level
     * set on him a few blocks above his feet: the tests are at y -60), and nothing below it.
     */
    @GameTest(template = FLOOR, batch = "gmo_walk_floor", timeoutTicks = 40)
    public static void walkingHeDestroysOnlyFromY60Up(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        int floor = floorY(helper);
        godzilla.setYRot(0.0F);
        godzilla.setYBodyRot(0.0F);
        Vec3 feet = godzilla.position();
        clearAround(helper, feet, 16, 12);
        int feetY = BlockPos.containing(feet).getY();
        // a wall in his path, 6 high: rock, earth, logs, terracotta, mud...
        BlockState[] kinds = {Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.GRANITE.defaultBlockState(),
                Blocks.TERRACOTTA.defaultBlockState(), Blocks.MUD.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState()};
        BlockPos wall = BlockPos.containing(feet.x, feetY, feet.z + 7.0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 1; dy <= 6; dy++) {
                helper.getLevel().setBlockAndUpdate(wall.offset(dx, dy, 0), kinds[(dx + 2 + dy) % kinds.length]);
            }
        }
        godzilla.setWalkFloorY(feetY + 4);
        godzilla.smashPathNow();
        int below = 0;
        int above = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 1; dy <= 6; dy++) {
                boolean standing = !helper.getLevel().getBlockState(wall.offset(dx, dy, 0)).isAir();
                if (dy < 4 && standing) {
                    below++;
                } else if (dy >= 4 && standing) {
                    above++;
                }
            }
        }
        helper.assertTrue(above == 0, "walking, he should destroy everything in his path from that level up, " + above + " of 15 left");
        helper.assertTrue(below == 15, "and nothing below it, " + (15 - below) + " of 15 gone");
        remove(godzilla);
        helper.succeed();
    }

    /**
     * His blows break the Building Blocks as well as what's built: terracotta (glazed too), mud, granite, andesite, stone,
     * deepslate, logs and stripped wood. Not the ground proper (dirt).
     */
    @GameTest(template = FLOOR, batch = "gmo_building_blocks", timeoutTicks = 40)
    public static void hisClawsBreakTheBuildingBlocks(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        BlockPos rake = BlockPos.containing(godzilla.clawPosition(1.0));
        Block[] kinds = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.CYAN_GLAZED_TERRACOTTA, Blocks.MUD, Blocks.PACKED_MUD,
                Blocks.MUD_BRICKS, Blocks.GRANITE, Blocks.ANDESITE, Blocks.STONE, Blocks.DEEPSLATE, Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG,
                Blocks.STRIPPED_SPRUCE_WOOD, Blocks.SANDSTONE, Blocks.DIORITE};
        List<BlockPos> wall = new ArrayList<>();
        int i = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = rake.offset(dx, dy, 0);
                helper.getLevel().setBlockAndUpdate(pos, kinds[i++ % kinds.length].defaultBlockState());
                wall.add(pos);
            }
        }
        BlockPos dirt = rake.offset(0, 2, 0);
        helper.getLevel().setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
        for (Block kind : kinds) {
            helper.assertTrue(Destruction.godzillaBreaks(kind.defaultBlockState()), kind + " should be among what his blows break");
        }
        helper.assertFalse(Destruction.godzillaBreaks(Blocks.DIRT.defaultBlockState()), "the ground proper (dirt) shouldn't be");
        godzilla.strikeNow("swipe_right");
        List<String> left = new ArrayList<>();
        for (BlockPos pos : wall) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (!state.isAir()) {
                left.add(state.getBlock().toString());
            }
        }
        helper.assertTrue(left.isEmpty(), "his claws should tear through all of it, left: " + left);
        helper.assertTrue(helper.getLevel().getBlockState(dirt).is(Blocks.DIRT), "and leave the ground proper");
        remove(godzilla);
        helper.succeed();
    }

    /**
     * Minus Zero's tail strike hits twice as hard as his spin, and destroys whatever it hits: an armor stand and a boat
     * (which no mob's blow breaks), and every block in its sweep, rock, earth and trees as well as what's built, but not
     * the ground he stands on.
     */
    @GameTest(template = FLOOR, batch = "gmo_minus_zero_tail_strike", timeoutTicks = 120)
    public static void minusZerosTailStrikeDestroysWhatItHits(GameTestHelper helper) {
        removeLeftoverGodzillas(helper);
        int floor = floorY(helper);
        ServerLevel level = helper.getLevel();
        GodzillaMinusZeroEntity zero = helper.spawn(ModEntities.GODZILLA_MINUS_ZERO.get(), 4, floor + 1, 4);
        zero.skipIntro();
        GodzillaEntity one = helper.spawn(ModEntities.GODZILLA.get(), 4, floor + 1, 4);
        helper.assertTrue(zero.tailStrikes() && !one.tailStrikes(), "only Minus Zero should have the tail strike");
        one.discard();
        Vec3 feet = zero.position();
        // his spin, on a pig as tough as a pig can be made (1024 health): it takes his spin's blow (0.4 of his attack) and lives...
        float blow = (float) zero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.4F;
        Pig spun = toughPig(level, feet.add(0.0, 0.0, 20.0));
        helper.assertTrue(blow < 1024.0F && blow * GodzillaEntity.TAIL_STRIKE_POWER >= 1024.0F && GodzillaEntity.TAIL_STRIKE_POWER == 2.0F,
                "(the test's pig: his spin's blow should leave it standing, twice that not)");
        zero.strikeNow("spin_whip");
        helper.assertTrue(spun.isAlive() && Math.abs(spun.getHealth() - (1024.0F - blow)) < 1.0F,
                "his spin should take " + blow + " of the pig's 1024 health, left " + spun.getHealth());
        spun.discard();
        // ...his tail strike, twice as hard: the same pig doesn't
        Pig struck = toughPig(level, feet.add(0.0, 0.0, 20.0));
        net.minecraft.world.entity.decoration.ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        stand.moveTo(feet.x + 18.0, feet.y, feet.z + 6.0, 0.0F, 0.0F);
        level.addFreshEntity(stand);
        net.minecraft.world.entity.vehicle.Boat boat = EntityType.BOAT.create(level);
        boat.moveTo(feet.x - 22.0, feet.y, feet.z + 4.0, 0.0F, 0.0F);
        level.addFreshEntity(boat);
        // pillars of rock, earth and tree round him, 11 to 16 blocks out, on ground of stone (what it takes at once,
        // however much else is round him), and a block further out, 45 blocks off, on its own high up (what it goes on to
        // clear over the next ticks, here, where the tests round this one have left it close to 200,000 blocks to sweep)
        List<BlockPos> pillars = new ArrayList<>();
        BlockState[] layers = {Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true)};
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0 + 0.3;
            double r = 11.5 + i * 0.6;
            BlockPos base = BlockPos.containing(feet.x + Math.cos(a) * r, feet.y, feet.z + Math.sin(a) * r);
            level.setBlockAndUpdate(base.below(), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < layers.length; y++) {
                level.setBlockAndUpdate(base.above(y), layers[y]);
            }
            pillars.add(base);
        }
        BlockPos far = BlockPos.containing(feet.x - 45.0, feet.y + 10.0, feet.z + 3.0);
        level.setBlockAndUpdate(far, Blocks.OAK_LOG.defaultBlockState());
        // (what else is in his sweep: the tests round this one, built and left there)
        int around = 0;
        BlockPos middle = BlockPos.containing(feet);
        for (BlockPos p : BlockPos.betweenClosed(middle.offset(-60, 0, -60), middle.offset(60, 20, 60))) {
            int dx = p.getX() - middle.getX();
            int dz = p.getZ() - middle.getZ();
            if (dx * dx + dz * dz <= 3600 && dx * dx + dz * dz >= 100 && !level.getBlockState(p).isAir()) {
                around++;
            }
        }
        zero.strikeNow("tail_strike");
        helper.assertTrue(!struck.isAlive(), "his tail strike should hit twice as hard: the pig should be dead, has " + struck.getHealth());
        helper.assertTrue(stand.isRemoved(), "his tail strike should destroy an armor stand");
        helper.assertTrue(boat.isRemoved(), "his tail strike should destroy a boat");
        int standing = 0;
        int ground = 0;
        for (BlockPos base : pillars) {
            for (int y = 0; y < layers.length; y++) {
                if (!level.getBlockState(base.above(y)).isAir()) {
                    standing++;
                }
            }
            if (level.getBlockState(base.below()).is(Blocks.STONE)) {
                ground++;
            }
        }
        helper.assertTrue(standing == 0, "his tail strike should destroy every block it sweeps through, " + standing + " of 32 left ("
                + around + " blocks in his sweep before it)");
        helper.assertTrue(ground == pillars.size() && helper.getBlockState(new BlockPos(4, floor, 4)).is(Blocks.STONE),
                "the ground he stands on should stay, " + ground + " of " + pillars.size());
        int[] waited = {0};
        int inSweep = around;
        helper.onEachTick(() -> waited[0]++);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(far).isAir(), "his tail strike should go on to clear its whole sweep, 45 blocks out too ("
                    + inSweep + " blocks in it)");
            GodzillaMinusOne.LOGGER.info("tail strike test: its sweep cleared {} blocks out {} ticks after, {} blocks in it", 45, waited[0], inSweep);
            remove(zero, struck);
        });
    }

    private static Pig toughPig(ServerLevel level, Vec3 at) {
        Pig pig = EntityType.PIG.create(level);
        pig.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024.0);
        pig.setHealth(1024.0F);
        level.addFreshEntity(pig);
        return pig;
    }

    private static final java.util.Set<String> ODO_TOWERS_DONE = new java.util.HashSet<>();

    /** (logs the tick the light went, once per run) */
    private static boolean odoTowerDone(String run) {
        return !ODO_TOWERS_DONE.add(run);
    }

    private static int countBlocks(GameTestHelper helper, net.minecraft.world.level.block.Block block, int x0, int y0, int z0, int x1, int y1, int z1) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (helper.getBlockState(pos).is(block)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Left alone, a searchlight sweeps round on its own, lit, without bothering the Odo Island Monster in its path; its
     * switch turns it off.
     */
    @GameTest(template = FLOOR, batch = "gmo_searchlight_sweep", timeoutTicks = 200)
    public static void anUnmannedSearchlightSweepsOnItsOwn(GameTestHelper helper) {
        int floor = floorY(helper);
        for (int x = 0; x <= 8; x++) {
            for (int y = floor + 1; y <= floor + 4; y++) {
                for (int z = 0; z <= 18; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        OdoIslandMonsterEntity odo = spawnOdo(helper, floor, 14);
        odo.setNoAi(true);
        SearchlightEntity light = helper.spawn(ModEntities.SEARCHLIGHT.get(), new Vec3(4.5, floor + 1, 2.5));
        light.setAim(0.0F, 0.0F); // starting down the corridor, at him
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(light.isLit(), "unmanned, it should still be lit");
            helper.assertTrue(Math.abs(light.getAimYaw()) > 20.0F, "it should have swept round, yaw " + light.getAimYaw());
            helper.assertTrue(odo.getHuntedLight() == null && !odo.isFrenzied(), "sweeping on its own, it shouldn't bother him");
            light.setSwitchedOn(false);
        });
        helper.runAtTickTime(110, () -> {
            helper.assertFalse(light.isLit(), "switched off, it should go dark");
            float yaw = light.getAimYaw();
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(light.getAimYaw() == yaw, "switched off, it should stop sweeping");
                helper.assertTrue(light.getLights().isEmpty(), "and its pool of light should go out");
                remove(odo, light);
                helper.succeed();
            });
        });
    }

    /** He waits two to three minutes after he appears before he can use his atomic breath, target or not. */
    @GameTest(template = FLOOR, batch = "gmo_first_breath", timeoutTicks = 120)
    public static void heWaitsBeforeHisFirstAtomicBreath(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        int delay = godzilla.getFirstBreathDelay();
        helper.assertTrue(delay >= Config.FIRST_BREATH_MIN_DELAY.get() * 20 - 1 && delay <= Config.FIRST_BREATH_MAX_DELAY.get() * 20,
                "the first breath should be 2 to 3 minutes off, " + delay + " ticks");
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(4.5, floorY(helper) + 1, 4.5));
        pig.setInvulnerable(true);
        godzilla.setTarget(pig);
        helper.runAtTickTime(100, () -> {
            helper.assertFalse(godzilla.isCharging(), "he shouldn't charge his atomic breath so soon after appearing");
            remove(godzilla, pig);
            helper.succeed();
        });
    }

    /** Every roarIntervalSeconds he stops and roars. */
    @GameTest(template = FLOOR, batch = "gmo_roar", timeoutTicks = 60)
    public static void heRoarsNowAndThen(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        helper.assertTrue(Config.ROAR_INTERVAL.get() > 0, "roaring should be on by default");
        helper.assertFalse(godzilla.isRoaring(), "he shouldn't roar straight away");
        godzilla.setRoarTimer(5);
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.isRoaring(), "when the time comes he should roar");
            remove(godzilla);
        });
    }

    /** A player coming into his sight sends the Odo Island Monster into a frenzy after them. */
    @GameTest(template = FLOOR, batch = "gmo_odo_frenzy", timeoutTicks = 40)
    public static void theOdoMonsterGoesIntoAFrenzyWhenSeen(GameTestHelper helper) {
        int floor = floorY(helper);
        // room for all 14 blocks of him, so he and the player can see each other
        Runnable release = room(helper, floor, 0, 0, 8, 12, 16);
        OdoIslandMonsterEntity odo = spawnOdo(helper, floor, 2);
        odo.setNoAi(true);
        // (helper.makeMockPlayer() is in creative mode, and creative players don't set him off)
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "odo-spotter")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Vec3 at = helper.absoluteVec(new Vec3(4.5, floor + 1, 8.5));
        player.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);   // looking north, at him
        helper.assertFalse(odo.isFrenzied(), "he shouldn't be in a frenzy yet");
        helper.assertTrue(odo.noticePlayer(player), "he should notice a player looking at him from close by");
        helper.assertTrue(odo.isFrenzied() && odo.getTarget() == player, "seen, he should go into a frenzy after the player");
        helper.assertTrue(odo.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0.3,
                "in a frenzy he should be faster");
        remove(odo);
        release.run();
        helper.succeed();
    }

    /** Put in the water, the Shinsei Maru floats with her keel DRAFT below the surface. */
    @GameTest(template = FLOOR, batch = "gmo_boat_float", timeoutTicks = 100)
    public static void theShinseiMaruFloats(GameTestHelper helper) {
        int floor = floorY(helper);
        Runnable release = room(helper, floor, 0, 0, 8, 14, 6);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 14; z++) {
                for (int y = floor + 1; y <= floor + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
                }
            }
        }
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floor + 2, 7.5));
        helper.runAtTickTime(60, () -> {
            double surface = boat.waterSurface();
            helper.assertFalse(Double.isNaN(surface), "she should be in the water");
            helper.assertTrue(Math.abs(boat.getY() - (surface - ShinseiMaruEntity.DRAFT)) < 0.3,
                    "she should ride with her keel " + ShinseiMaruEntity.DRAFT + " below the surface, y " + boat.getY() + ", surface " + surface);
            remove(boat);
            release.run();
            helper.succeed();
        });
    }

    /**
     * Afloat she keeps still: no bobbing of her own (Physics Mod, where its ocean runs, moves her on its waves itself).
     */
    @GameTest(template = FLOOR, batch = "gmo_boat_steady", timeoutTicks = 160)
    public static void theShinseiMaruRidesSteady(GameTestHelper helper) {
        int floor = floorY(helper);
        Runnable release = room(helper, floor, 0, 0, 8, 14, 6);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 14; z++) {
                for (int y = floor + 1; y <= floor + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
                }
            }
        }
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floor + 2, 7.5));
        double[] low = {Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (helper.getTick() >= 60) {
                low[0] = Math.min(low[0], boat.getY());
                high[0] = Math.max(high[0], boat.getY());
            }
        });
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(high[0] - low[0] < 0.02, "settled, she shouldn't bob: y from " + low[0] + " to " + high[0]);
            remove(boat);
            release.run();
            helper.succeed();
        });
    }

    /**
     * Whichever way she heads, there's deck under anyone standing anywhere on her decks (the main deck and side decks
     * at deck height, the wheelhouse roof up at the helm), and none well out past her side.
     */
    @GameTest(template = FLOOR, batch = "gmo_boat_decks", timeoutTicks = 20)
    public static void theShinseiMarusDecksHoldAtAnyHeading(GameTestHelper helper) {
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floorY(helper) + 1, 4.5));
        for (float heading = 0.0F; heading < 360.0F; heading += 22.5F) {
            boat.setYRot(heading);
            boat.placeParts();
            for (double f = -7.0; f <= 7.0; f += 0.5) {
                for (double s = -2.3; s <= 2.3; s += 0.46) {
                    boolean wheelhouse = f > -2.7 && f < 1.2 && Math.abs(s) < 1.8;
                    double top = wheelhouse ? 4.45 : f < -4.4 ? 2.35 : 2.2;
                    helper.assertTrue(standsOn(boat, f, s, top), "no deck under (" + f + ", " + s + ") heading " + heading);
                }
            }
            helper.assertFalse(standsOn(boat, 0.0, ShinseiMaruEntity.BEAM + 0.9, 2.2), "deck reaching out past her side, heading " + heading);
        }
        remove(boat);
        helper.succeed();
    }

    /**
     * Turning, she takes whoever stands on her round with her: a pig on her foredeck is still on her foredeck after she
     * has swung through 50 degrees or so (the wheel hard over, no way on). (She used to swing them round the other way,
     * which only those near her middle got away with.)
     */
    @GameTest(template = FLOOR, batch = "gmo_boat_carry", timeoutTicks = 200)
    public static void theShinseiMaruTakesHerCrewRoundAsSheTurns(GameTestHelper helper) {
        int floor = floorY(helper);
        Runnable release = room(helper, floor, -6, -6, 14, 14, 7);
        for (int x = -6; x <= 14; x++) {
            for (int z = -6; z <= 14; z++) {
                for (int y = floor + 1; y <= floor + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
                }
            }
        }
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floor + 2, 4.5));
        Pig[] pig = {null};
        float[] heading = {0.0F};
        helper.runAtTickTime(40, () -> {
            Vec3 at = boat.at(new Vec3(0.0, ShinseiMaruEntity.DECK + 0.05, 4.5));
            pig[0] = EntityType.PIG.create(helper.getLevel());
            pig[0].moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            pig[0].setNoAi(true);
            helper.getLevel().addFreshEntity(pig[0]);
            heading[0] = boat.getYRot();
            boat.autopilot(0.0F, 1.0F);
        });
        helper.runAtTickTime(130, () -> {
            float turned = Math.abs(Mth.wrapDegrees(boat.getYRot() - heading[0]));
            Vec3 rel = pig[0].position().subtract(boat.position());
            Vec3 forward = Vec3.directionFromRotation(0.0F, boat.getYRot());
            double side = -rel.x * forward.z + rel.z * forward.x;
            double ahead = rel.x * forward.x + rel.z * forward.z;
            helper.assertTrue(turned > 30.0F, "she should have turned, turned " + turned);
            helper.assertTrue(Math.abs(side) < 1.0 && Math.abs(ahead - 4.5) < 1.0 && rel.y > ShinseiMaruEntity.DECK - 0.3,
                    String.format("the pig should still be on her foredeck after she turned %.0f degrees: %.2f to starboard, %.2f forward, %.2f up",
                            turned, side, ahead, rel.y));
            remove(pig[0], boat);
            release.run();
            helper.succeed();
        });
    }

    /** Whether a player's feet (0.6 wide) at (forward, to starboard) on her, at {@code top} above her keel, rest on a deck tile. */
    private static boolean standsOn(ShinseiMaruEntity boat, double forward, double side, double top) {
        Vec3 at = boat.at(new Vec3(side, top, forward));
        AABB feet = new AABB(at.x - 0.3, at.y - 0.05, at.z - 0.3, at.x + 0.3, at.y + 0.05, at.z + 0.3);
        for (ShinseiMaruPart part : boat.getParts()) {
            AABB deck = part.getBoundingBox();
            if (deck.intersects(feet) && Math.abs(deck.maxY - at.y) < 0.2) {
                return true;
            }
        }
        return false;
    }

    /** The helmsman's key drops a naval mine from her hold over the stern. */
    @GameTest(template = FLOOR, batch = "gmo_boat_mine")
    public static void theShinseiMaruDropsAMineAstern(GameTestHelper helper) {
        int floor = floorY(helper);
        Runnable release = room(helper, floor, 0, 0, 8, 16, 5);
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floor + 1, 4.5));
        boat.setYRot(180.0F); // bow to the north: her stern is towards the far end of the room
        boat.getMines().setItem(0, new ItemStack(ModItems.NAVAL_MINE_SPAWN_EGG.get(), 2));
        helper.assertTrue(boat.getMinesAboard() == 2, "two mines should be aboard, " + boat.getMinesAboard());
        helper.assertTrue(boat.dropMine(null), "with mines aboard she should drop one");
        List<NavalMineEntity> mines = helper.getLevel().getEntitiesOfClass(NavalMineEntity.class, boat.getBoundingBox().inflate(12.0));
        helper.assertTrue(mines.size() == 1, "one mine should be over the side, " + mines.size());
        helper.assertTrue(mines.get(0).getZ() > boat.getZ() + 5.0, "it should go over the stern");
        helper.assertTrue(boat.getMinesAboard() == 1, "it should come out of her hold, " + boat.getMinesAboard() + " left");
        mines.forEach(Entity::discard);
        remove(boat);
        release.run();
        helper.succeed();
    }

    /** A round from her stern gun sets off a naval mine floating astern. */
    @GameTest(template = FLOOR, batch = "gmo_boat_gun")
    public static void theShinseiMarusGunSetsOffAMine(GameTestHelper helper) {
        int floor = floorY(helper);
        Runnable release = room(helper, floor, 0, 0, 8, 20, 7);
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floor + 1, 3.5));
        boat.setYRot(180.0F); // her stern (and the gun, dead astern) towards the mine
        NavalMineEntity mine = helper.spawn(ModEntities.NAVAL_MINE.get(), new Vec3(4.5, floor + 3, 16.5));
        mine.setNoAi(true);
        mine.setNoGravity(true);
        helper.assertTrue(boat.fireGun(null, true), "the gun should fire");
        helper.assertTrue(mine.isRemoved(), "the round should set the mine off");
        remove(boat);
        release.run();
        helper.succeed();
    }

    /** Her hold takes naval mines and nothing else. */
    @GameTest(template = FLOOR, batch = "gmo_boat_hold")
    public static void theShinseiMarusHoldOnlyTakesMines(GameTestHelper helper) {
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floorY(helper) + 1, 4.5));
        Player player = helper.makeMockPlayer();
        ShinseiMaruMenu menu = new ShinseiMaruMenu(0, player.getInventory(), boat.getMines(), boat);
        helper.assertFalse(menu.holdSlot(0).mayPlace(new ItemStack(Items.DIRT)), "the hold shouldn't take dirt");
        helper.assertTrue(menu.holdSlot(0).mayPlace(new ItemStack(ModItems.NAVAL_MINE_SPAWN_EGG.get())), "the hold should take a naval mine");
        helper.assertTrue(ShinseiMaruEntity.MAX_SPEED == GodzillaEntity.SWIM_TOP_SPEED, "she should be as fast as Godzilla swims");
        remove(boat);
        helper.succeed();
    }

    @GameTest(template = FLOOR, batch = "gmo_crash")
    public static void aCrashExplosionSparesThePilot(GameTestHelper helper) {
        int floor = floorY(helper);
        ShindenEntity plane = helper.spawn(ModEntities.SHINDEN.get(), new Vec3(4.5, floor + 1, 4.5));
        Pig pilot = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 1, 4.5));
        Pig bystander = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 1, 8.5));
        // (a crashing plane is already done for when its explosion goes off; this one, left whole, would be set off
        // by the blast like a naval mine and reach the tests next door)
        plane.discard();
        // (items already lying about, dropped by the tests next door, aren't the blast's)
        List<ItemEntity> before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, plane.getBoundingBox().inflate(16.0));
        Blasts.impact(helper.getLevel(), plane.position(), ShindenEntity.CRASH_EXPLOSION_POWER, plane, pilot);
        helper.assertTrue(pilot.isAlive() && pilot.getHealth() == pilot.getMaxHealth(), "the pilot should be spared");
        helper.assertFalse(bystander.isAlive(), "a 3x TNT blast should kill a pig 4 blocks away");
        helper.assertTrue(helper.getBlockState(new BlockPos(4, floor, 4)).isAir(), "the blast should blow a hole in the floor");
        // (the game test framework's own start buttons, on neighbouring tests' structure blocks, pop off when the blast
        // shakes their support and drop as items: they aren't blocks the blast broke)
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, plane.getBoundingBox().inflate(16.0),
                item -> item.getItem().getItem() instanceof BlockItem && !item.getItem().is(Items.STONE_BUTTON) && !before.contains(item));
        helper.assertTrue(drops.isEmpty(), "the blocks it breaks should vanish, not drop: " + drops);
        remove(pilot);
        helper.succeed();
    }

    /**
     * A tower 26 blocks tall (more than half his height) right in front of him: he swipes at it until it stands a
     * quarter of his height (demolishDownToHeight, 11), and no lower. (Kept on task: other tests' pigs might catch his
     * eye.)
     */
    @GameTest(template = FLOOR, batch = "gmo_demolish", timeoutTicks = 400)
    public static void heKnocksTallBuildingsDownToSize(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        int floor = floorY(helper);
        int kept = GodzillaEntity.keptHeight();
        helper.assertTrue(GodzillaEntity.tallBuildingHeight() == 23 && kept == 11,
                "by default the thresholds should be half and a quarter of his real height (23, 11): " + GodzillaEntity.tallBuildingHeight() + ", " + kept);
        for (int y = floor + 1; y <= floor + 26; y++) {
            helper.setBlock(new BlockPos(4, y, 10), Blocks.STONE_BRICKS.defaultBlockState());
        }
        BlockPos base = helper.absolutePos(new BlockPos(4, floor + 1, 10));
        BlockPos tooHigh = new BlockPos(4, floor + kept + 1, 10);
        godzilla.knockDown(base);
        helper.onEachTick(() -> {
            godzilla.setTarget(null);
            if (!godzilla.isDemolishing() && !helper.getBlockState(tooHigh).isAir()) {
                godzilla.knockDown(base);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(tooHigh).isAir(), "the tower should be knocked down to " + kept + " blocks");
            helper.assertTrue(helper.getBlockState(tooHigh.below()).is(Blocks.STONE_BRICKS), "he should only knock it down to size");
            helper.assertFalse(godzilla.isDemolishing(), "then he should move on");
            helper.assertTrue(godzilla.getDemolitionRest() > (Config.DEMOLITION_INTERVAL.get() - 5) * 20,
                    "and leave buildings alone for demolitionIntervalSeconds, rest " + godzilla.getDemolitionRest());
            remove(godzilla);
        });
    }

    /**
     * The atomic breath's flattening (here with a radius of 3 blocks around the middle of the floor, down to the
     * floor): a tower with a chest of diamonds on top is erased without a single item dropping.
     */
    @GameTest(template = FLOOR, batch = "gmo_flatten", timeoutTicks = 200)
    public static void flatteningErasesEverythingAboveTheLevelWithoutDrops(GameTestHelper helper) {
        int floor = floorY(helper);
        for (int y = floor + 1; y <= floor + 6; y++) {
            helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG.defaultBlockState());
        }
        BlockPos top = new BlockPos(3, floor + 7, 3);
        helper.setBlock(top, Blocks.CHEST.defaultBlockState());
        if (helper.getBlockEntity(top) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        }
        BlockPos middle = helper.absolutePos(new BlockPos(4, floor, 4));
        Flattening.start(helper.getLevel(), Vec3.atBottomCenterOf(middle), 3, middle.getY());
        helper.succeedWhen(() -> {
            for (int y = floor + 1; y <= floor + 7; y++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(3, y, 3)).isAir(), "everything above the level should be gone, y " + y);
            }
            helper.assertTrue(helper.getBlockState(new BlockPos(3, floor, 3)).is(Blocks.STONE), "the ground at the level should stay");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, floor, 4)).is(Blocks.STONE), "the ground at the level should stay");
            // (only round the flattened ground: neighbouring tests' frames can shed their buttons as they're cleared)
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(middle).inflate(4.0, 10.0, 4.0));
            helper.assertTrue(drops.isEmpty(), "nothing should drop: " + drops);
        });
    }

    /**
     * The atomic blast's look, as its timeline sets it out (client.render.BlastTimeline, at the default settings: a
     * cloud 240 blocks tall; flash 1.5 s, fireball 4, dust wave 8, rising 30, cooled by 30, towering 60, breaking up
     * 90): the flash blinding at once and over first; the dust wave rolling out, fast then slowing, to its full reach;
     * the fireball rising into a cap far wider than the stem, as tall as the cloud should be; its fire out before it
     * breaks up, broadening as it goes; the black rain falling from it after the fireball, until it breaks up.
     */
    @GameTest(template = FLOOR, batch = "gmo_blast_timeline")
    public static void atomicBlastFollowsItsTimeline(GameTestHelper helper) {
        float height = 240.0F;
        BlastTimeline blast = new BlastTimeline(height, 1.5F, 4.0F, 8.0F, 30.0F, 30.0F, 60.0F, 90.0F);
        helper.assertTrue(blast.flash(0.1F) > 0.8F, "the flash should be blinding at once, was " + blast.flash(0.1F));
        helper.assertTrue(blast.flash(1.0F) < blast.flash(0.2F), "the flash should die away");
        helper.assertTrue(blast.flash(1.5F) == 0.0F && blast.flash(2.0F) == 0.0F, "the flash should be over by 1.5 s");

        float w1 = blast.waveRadius(1.0F);
        float w4 = blast.waveRadius(4.0F);
        float w8 = blast.waveRadius(8.0F);
        helper.assertTrue(blast.waveRadius(0.0F) == 0.0F && w1 > 0.0F && w1 < w4 && w4 < w8, "the dust wave should roll outwards");
        helper.assertTrue(w1 > w8 - w4, "the dust wave should slow down: " + w1 + " in its first second, " + (w8 - w4) + " in its last four");
        helper.assertTrue(Math.abs(w8 - BlastTimeline.WAVE_REACH * height) < 1.0F, "the dust wave should reach its full reach in 8 s, was " + w8);
        float half = blast.wavePasses(0.5F);
        helper.assertTrue(half > 0.0F && half < 8.0F && Math.abs(blast.waveRadius(half) - 0.5F * w8) < 1.0F,
                "the haze should appear where the dust wave has got to, at " + half + " s");

        helper.assertTrue(blast.capCentre(2.0F) < blast.capCentre(10.0F) && blast.capCentre(10.0F) < blast.capCentre(30.0F),
                "the fireball should rise");
        float top = blast.capCentre(30.0F) + blast.capHalfHeight(30.0F);
        helper.assertTrue(top > 0.85F * height && top <= height, "the risen cap's top should be near the cloud's height, was " + top);
        helper.assertTrue(blast.capRadius(30.0F) > 1.5F * blast.capHalfHeight(30.0F) * 2.0F, "the cap should be broad and flat");
        helper.assertTrue(blast.capRadius(30.0F) > 4.0F * BlastTimeline.STEM_RADIUS * height, "the cap should be far wider than the stem");

        helper.assertTrue(blast.heat(0.2F) == 1.0F && blast.heat(10.0F) > 0.0F && blast.heat(30.0F) == 0.0F,
                "the cloud's fire should cool by 30 s");
        helper.assertTrue(blast.dissipated(90.0F) == 0.0F && blast.dissipated(135.0F) > 0.0F, "it should break up after 90 s");
        helper.assertTrue(blast.capRadius(170.0F) > blast.capRadius(90.0F), "the cap should broaden as it breaks up");
        helper.assertTrue(blast.lifetime() == 180.0F, "it should be gone after 3 minutes, was " + blast.lifetime());
        helper.assertTrue(blast.fallout(3.0F) == 0.0F && blast.fallout(60.0F) == 1.0F && blast.fallout(179.0F) == 0.0F,
                "black rain should fall from the cloud after the fireball, until it breaks up");
        helper.succeed();
    }

    /** In the water the Shinsei Maru is his main focus: he hunts her, and whoever is aboard her is his target. */
    @GameTest(template = FLOOR, batch = "gmo_boat_focus", timeoutTicks = 200)
    public static void inTheWaterHeGoesForTheShinseiMaru(GameTestHelper helper) {
        int depth = 20;
        int floor = floorY(helper);
        fillPool(helper, floor, depth, false);
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setBreathCooldown(100000);
        godzilla.moveTo(helper.absoluteVec(new Vec3(4.5, floor + 1, 4.5)));
        Pig bystander = helper.spawn(EntityType.PIG, new Vec3(1.5, floor + depth + 1, 1.5));
        bystander.setNoAi(true);
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floor + depth, 9.5));
        Pig crew = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + depth + 2, 9.5));
        crew.setNoAi(true);
        helper.assertTrue(crew.startRiding(boat, true), "a pig should be able to board her");
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.getHuntedBoat() == boat, "in the water he should be after the Shinsei Maru");
            helper.assertTrue(godzilla.getTarget() == crew, "whoever is aboard her should be his target, was " + godzilla.getTarget());
            remove(godzilla, boat, crew, bystander);
            fillPool(helper, floor, depth, true);
        });
    }

    /**
     * The Shinsei Maru has two stations: a click on her stern takes the machine gun, anywhere else the wheel (and the
     * mines); her decks are solid sections along her whole length, not just her middle.
     */
    @GameTest(template = FLOOR, batch = "gmo_boat_seats")
    public static void theShinseiMaruHasAHelmAndAGun(GameTestHelper helper) {
        ShinseiMaruEntity boat = helper.spawn(ModEntities.SHINSEI_MARU.get(), new Vec3(4.5, floorY(helper) + 1, 4.5));
        ShinseiMaruPart[] parts = boat.getParts();
        ShinseiMaruPart stern = parts[parts.length - 1];
        Player gunner = helper.makeMockPlayer();
        Player helmsman = helper.makeMockPlayer();
        helper.assertTrue(stern.interact(gunner, InteractionHand.MAIN_HAND).consumesAction(), "a click on her stern should board her");
        helper.assertTrue(boat.getGunner() == gunner && boat.getControllingPassenger() == null,
                "a click on her stern should take the gun, not the wheel");
        helper.assertTrue(boat.interact(helmsman, InteractionHand.MAIN_HAND).consumesAction(), "the second aboard should board her");
        helper.assertTrue(boat.getControllingPassenger() == helmsman && boat.getGunner() == gunner,
                "the second aboard should take the wheel, the gunner keep the gun");
        helper.assertTrue(!boat.canBeCollidedWith() && stern.canBeCollidedWith(), "her decks (her parts) should be what's solid");
        helper.succeedWhen(() -> {
            double length = parts[0].position().distanceTo(stern.position());
            helper.assertTrue(length > 10.0, "her decks should run her whole length, bow to stern " + length);
            helper.assertTrue(stern.getBoundingBox().maxY - boat.getY() > 2.0, "her stern platform should stand above the water");
            gunner.stopRiding();
            helmsman.stopRiding();
            remove(boat);
        });
    }
}
