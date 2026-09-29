package com.npjt25.godzillaminusone.gametest;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.Destruction;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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

    private static GodzillaEntity spawnGodzilla(GameTestHelper helper) {
        for (GodzillaEntity leftover : helper.getLevel().getEntities(ModEntities.GODZILLA.get(), e -> true)) {
            GodzillaMinusOne.LOGGER.info("test at {}: removing a leftover Godzilla at {}", helper.absolutePos(BlockPos.ZERO), leftover.position());
            leftover.discard();
        }
        return helper.spawn(ModEntities.GODZILLA.get(), 4, 1, 4);
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

    private static void remove(Entity... entities) {
        for (Entity entity : entities) {
            entity.discard();
        }
    }

    private static void detonateMine(GameTestHelper helper, int x, int z) {
        NavalMineEntity mine = helper.spawn(ModEntities.NAVAL_MINE.get(), x, 1, z);
        mine.hurt(helper.getLevel().damageSources().generic(), 5.0F);
        helper.assertTrue(mine.isRemoved(), "a hurt naval mine should detonate");
    }

    @GameTest(template = FLOOR, batch = "gmo_emerge", timeoutTicks = 400)
    public static void godzillaEmergesThenBecomesActive(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnGodzilla(helper);
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.EMERGING, "Godzilla should start by emerging");
        helper.runAtTickTime(200, () -> helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.RISING,
                "after 9 s he should be roaring, was " + godzilla.getPhase()));
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.isActive(), "Godzilla should become active, was " + godzilla.getPhase());
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

    @GameTest(template = FLOOR, batch = "gmo_regenerate", timeoutTicks = 800)
    public static void godzillaCollapsesAndRegenerates(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setHealth(10.0F);
        detonateMine(helper, 4, 14);
        helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.COLLAPSING, "at zero health he should collapse, was " + godzilla.getPhase());
        helper.assertTrue(godzilla.isAlive(), "he should not really die");
        helper.runAtTickTime(200, () -> helper.assertTrue(godzilla.getPhase() == GodzillaEntity.Phase.DEAD, "he should be lying dead"));
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.isActive() && godzilla.getHealth() >= godzilla.getMaxHealth() - 0.5F,
                    "he should rise again at full health, was " + godzilla.getPhase() + " / " + godzilla.getHealth());
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

    @GameTest(template = FLOOR, batch = "gmo_kill")
    public static void killCommandStillWorks(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.kill();
        helper.assertFalse(godzilla.isAlive(), "/kill should kill him for good");
        helper.succeed();
    }

    /**
     * Game tests run in a pocket dug out at y -60 of a normal world, so the beam hits rock right at his mouth.
     * The pig waits in a sealed cell 30 blocks ahead: out of his sight and out of reach of the heat around him
     * while he charges, but well inside the blast.
     */
    @GameTest(template = FLOOR, batch = "gmo_atomic_breath", timeoutTicks = 560)
    public static void atomicBreathKillsHisTargetThenRecharges(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        int floor = floorY(helper);
        BlockPos cell = new BlockPos(4, floor + 1, 34);
        for (BlockPos wall : new BlockPos[]{cell.below(), cell.above(), cell.north(), cell.south(), cell.east(), cell.west()}) {
            helper.setBlock(wall, Blocks.STONE.defaultBlockState());
        }
        helper.setBlock(cell, Blocks.AIR.defaultBlockState());
        BlockPos abs = helper.absolutePos(cell);
        helper.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, true);
        Pig pig = helper.spawn(EntityType.PIG, cell);
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
            remove(godzilla);
            helper.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, false);
        });
    }

    @GameTest(template = FLOOR, batch = "gmo_plane_first", timeoutTicks = 60)
    public static void aPilotedPlaneBecomesHisTarget(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        Pig pig = helper.spawn(EntityType.PIG, 4, 1, 20);
        godzilla.setTarget(pig);
        ShindenEntity plane = helper.spawn(ModEntities.SHINDEN.get(), 4, 1, 44);
        Pig pilot = helper.spawn(EntityType.PIG, 4, 1, 44);
        pilot.startRiding(plane, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(godzilla.getTarget() == plane, "a plane with someone in it should come first, target was " + godzilla.getTarget());
            remove(godzilla, pig, pilot, plane);
        });
    }

    @GameTest(template = FLOOR, batch = "gmo_swim", timeoutTicks = 300)
    public static void godzillaSwimsAtTheSurfaceOfDeepWater(GameTestHelper helper) {
        // A 16 deep pool with unbreakable walls and bottom.
        int depth = 16;
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
            remove(godzilla);
            fillPool(helper, floor, depth, true);
        });
    }

    private static void fillPool(GameTestHelper helper, int floor, int depth, boolean drain) {
        for (int y = floor + depth + 1; y >= floor; y--) {
            for (int x = -3; x <= 11; x++) {
                for (int z = -3; z <= 11; z++) {
                    boolean wall = x == -3 || x == 11 || z == -3 || z == 11 || y == floor;
                    BlockState state;
                    if (drain) {
                        state = y == floor && x >= 0 && x <= 8 && z >= 0 && z <= 8 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    } else if (y > floor + depth) {
                        state = Blocks.AIR.defaultBlockState();
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
}
