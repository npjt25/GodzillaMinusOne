package com.npjt25.godzillaminusone.gametest;

import com.mojang.authlib.GameProfile;
import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.Blasts;
import com.npjt25.godzillaminusone.entity.Destruction;
import com.npjt25.godzillaminusone.entity.Flattening;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaStatueEntity;
import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import com.npjt25.godzillaminusone.entity.NuclearBombEntity;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.item.RemoteDetonatorItem;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModItems;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
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
     * The pig waits in a sealed cell 40 blocks ahead: out of his sight and beyond his footsteps and the heat around him
     * while he charges, but well inside the blast.
     */
    @GameTest(template = FLOOR, batch = "gmo_atomic_breath", timeoutTicks = 560)
    public static void atomicBreathKillsHisTargetThenRecharges(GameTestHelper helper) {
        GodzillaEntity godzilla = spawnActiveGodzilla(helper);
        godzilla.setFirstBreathDelay(0);
        int floor = floorY(helper);
        BlockPos cell = new BlockPos(4, floor + 1, 44);
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
            helper.assertTrue(godzilla.getBurnTicks() > 0, "once the beam is over his mouth should be burnt");
            remove(godzilla);
            helper.getLevel().setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, false);
        });
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
        GodzillaStatueEntity statue = helper.spawn(ModEntities.GODZILLA_POSE_SKY_ROAR.get(), new Vec3(4.5, floorY(helper) + 4, 4.5));
        Vec3 at = statue.position();
        helper.assertFalse(statue.hurt(helper.getLevel().damageSources().generic(), 1000.0F), "a statue can't be hurt");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(statue.isAlive() && statue.position().equals(at), "it should stay just where it was put, even in mid-air");
            statue.discard();
            helper.succeed();
        });
    }

    private static OdoIslandMonsterEntity spawnOdo(GameTestHelper helper, int floor, int z) {
        for (GodzillaEntity leftover : helper.getLevel().getEntities(ModEntities.GODZILLA.get(), e -> true)) {
            leftover.discard();
        }
        for (OdoIslandMonsterEntity leftover : helper.getLevel().getEntities(ModEntities.ODO_ISLAND_MONSTER.get(), e -> true)) {
            leftover.discard();
        }
        return helper.spawn(ModEntities.ODO_ISLAND_MONSTER.get(), new Vec3(4.5, floor + 1, z + 0.5));
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
            helper.assertTrue(odo.getTarget() == operator, "caught in the beam he should go for the operator, target " + odo.getTarget());
            helper.assertTrue(odo.getState() == OdoIslandMonsterEntity.STATE_ANGRY, "he should be enraged");
            helper.assertTrue(odo.isFrenzied(), "a searchlight should send him into a frenzy");
            helper.assertTrue(light.getLights().size() >= 2, "the beam should light up a pool around where it lands, lights "
                    + light.getLights());
            remove(odo, operator, light);
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

    @GameTest(template = FLOOR, batch = "gmo_crash")
    public static void aCrashExplosionSparesThePilot(GameTestHelper helper) {
        int floor = floorY(helper);
        ShindenEntity plane = helper.spawn(ModEntities.SHINDEN.get(), new Vec3(4.5, floor + 1, 4.5));
        Pig pilot = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 1, 4.5));
        Pig bystander = helper.spawn(EntityType.PIG, new Vec3(4.5, floor + 1, 8.5));
        // (a crashing plane is already done for when its explosion goes off; this one, left whole, would be set off
        // by the blast like a naval mine and reach the tests next door)
        plane.discard();
        Blasts.impact(helper.getLevel(), plane.position(), ShindenEntity.CRASH_EXPLOSION_POWER, plane, pilot);
        helper.assertTrue(pilot.isAlive() && pilot.getHealth() == pilot.getMaxHealth(), "the pilot should be spared");
        helper.assertFalse(bystander.isAlive(), "a 3x TNT blast should kill a pig 4 blocks away");
        helper.assertTrue(helper.getBlockState(new BlockPos(4, floor, 4)).isAir(), "the blast should blow a hole in the floor");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, plane.getBoundingBox().inflate(16.0),
                item -> item.getItem().getItem() instanceof BlockItem);
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
        int kept = Config.KNOCK_DOWN_TO_HEIGHT.get();
        helper.assertTrue(26 >= Config.TALL_BUILDING_HEIGHT.get(), "the tower should count as tall");
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
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(middle).inflate(16.0));
            helper.assertTrue(drops.isEmpty(), "nothing should drop: " + drops);
        });
    }
}
