package com.npjt25.godzillaminusone.client.harness;

import com.npjt25.godzillaminusone.compat.Shapes;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.PlayerGodzilla;
import com.npjt25.godzillaminusone.entity.PlayerOdo;
import com.npjt25.godzillaminusone.registry.ModEntities;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.check;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.checkServer;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.commands;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.during;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.log;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.now;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.once;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.server;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.serverPlayer;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.shot;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.until;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.waitTicks;

/**
 * The in-game checks of playing as Godzilla and the Odo Island Monster, with the real Walkers (and ReMorphed) in the
 * game (CI's "client-shapes" job: the released game with this mod's built jar and theirs; Walkers can't run in the
 * development game). Skipped when Walkers isn't there. The player
 * takes a shape through Walkers' own API (as its commands and menus do) and plays it with the keys a player would
 * press: J bites, B breathes (rooted from the charge to the end of the roar after the beam, then free again), walking
 * breaks through what's built; as the Odo, Left Control runs (6 blocks a second) and a left click bites.
 */
final class ShapeScenes {
    private static final Vec3 GODZILLA_AT = new Vec3(300.5, -60.0, 300.5);
    private static final Vec3 ODO_AT = new Vec3(380.5, -60.0, 300.5);

    static void script() {
        if (!ModList.get().isLoaded("walkers")) {
            log("shapes: Walkers isn't in the game: playing as Godzilla and the Odo not checked here");
            return;
        }
        log("shapes: Walkers " + version("walkers") + ", ReMorphed " + version("remorphed") + ", CraftedCore " + version("craftedcore"));
        godzilla();
        odo();
    }

    private static String version(String mod) {
        return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString()).orElse("not loaded");
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Through Walkers' API, PlayerShape.updateShapes(ServerPlayer, LivingEntity): {@code player} takes {@code shape}'s (null: their own). */
    private static boolean setShape(ServerPlayer player, @Nullable LivingEntity shape) {
        try {
            for (Method method : Class.forName("tocraft.walkers.api.PlayerShape").getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals("updateShapes") && Modifier.isStatic(method.getModifiers()) && params.length == 2
                        && params[0].isAssignableFrom(ServerPlayer.class) && params[1] == LivingEntity.class) {
                    Object done = method.invoke(null, player, shape);
                    return !(done instanceof Boolean b) || b;
                }
            }
            log("shapes: no PlayerShape.updateShapes(ServerPlayer, LivingEntity) in this Walkers");
        } catch (ReflectiveOperationException | LinkageError e) {
            log("shapes: Walkers' API: " + e);
        }
        return false;
    }

    /** Presses a key as the keyboard does (and lets it go a couple of ticks later). */
    private static void tap(String label, int key) {
        once(label, () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, 0));
        waitTicks(2);
        once(label + ": let go", () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(), key, 0, GLFW.GLFW_RELEASE, 0));
    }

    /** A pig hanging still where {@code at} says, to be bitten. */
    private static final UUID[] PIG = new UUID[1];

    private static void pigAt(String label, java.util.function.Function<ServerPlayer, Vec3> at) {
        server(label, level -> {
            ServerPlayer player = serverPlayer(level);
            Vec3 p = at.apply(player);
            Pig pig = EntityType.PIG.create(level);
            pig.moveTo(p.x, p.y - 0.45, p.z, 0.0F, 0.0F);
            pig.setNoAi(true);
            pig.setNoGravity(true);
            level.addFreshEntity(pig);
            PIG[0] = pig.getUUID();
        });
    }

    private static String pigBitten(ServerLevel level) {
        if (!(level.getEntity(PIG[0]) instanceof Pig pig)) {
            return null; // (bitten to death)
        }
        return pig.getHealth() < pig.getMaxHealth() ? null : "the pig wasn't hurt";
    }

    // ------------------------------------------------------------------ Godzilla

    private static void godzilla() {
        commands("gamerule mobGriefing true", "time set 6000");
        server("shapes: as Godzilla", level -> {
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, GODZILLA_AT.x, GODZILLA_AT.y, GODZILLA_AT.z, 0.0F, 0.0F);
            GodzillaEntity shape = ModEntities.GODZILLA.get().create(level);
            log("shapes: Godzilla taken: " + setShape(player, shape));
        });
        waitTicks(60);
        check("shapes: playing as Godzilla (both sides)", () -> mc().player != null && Shapes.godzillaOf(mc().player) != null ? null
                : "the client doesn't have him");
        checkServer("shapes: the server has him too", level -> PlayerGodzilla.isGodzilla(serverPlayer(level)) ? null : "not on the server");
        check("shapes: third person, pulled back to show all of him", () -> {
            LocalPlayer player = mc().player;
            if (player == null) {
                return "no player";
            }
            double d = mc().gameRenderer.getMainCamera().getPosition().distanceTo(player.getEyePosition());
            return mc().options.getCameraType() != CameraType.FIRST_PERSON && d > 20.0 ? null
                    : String.format("%s, camera %.1f blocks from his eyes", mc().options.getCameraType(), d);
        });
        once("shapes: the HUD on", () -> mc().options.hideGui = false);
        waitTicks(5);
        shot("shape_godzilla_hud");
        once("shapes: the HUD off", () -> mc().options.hideGui = true);

        // J: his bite, where he's looking
        once("shapes: look down ahead", () -> {
            if (mc().player != null) {
                mc().player.setYRot(0.0F);
                mc().player.setXRot(35.0F);
            }
        });
        waitTicks(5);
        pigAt("shapes: a pig in his jaws' way", player -> player.getEyePosition().add(player.getLookAngle().scale(14.0)));
        waitTicks(5);
        tap("shapes: J", GLFW.GLFW_KEY_J);
        waitTicks(30);
        checkServer("shapes: J bites (the pig in front of him is hurt)", ShapeScenes::pigBitten);
        waitTicks(20); // (the bite's 20 ticks)

        // B: his atomic breath, up into the sky; rooted from the start of the charge to the end of the roar after it
        once("shapes: look up", () -> {
            if (mc().player != null) {
                mc().player.setXRot(-70.0F);
            }
        });
        waitTicks(5);
        int[] pressed = {0};
        once("shapes: mark", () -> pressed[0] = now());
        tap("shapes: B", GLFW.GLFW_KEY_B);
        waitTicks(20);
        checkServer("shapes: B starts the charge: rooted", level -> PlayerGodzilla.isRooted(serverPlayer(level)) ? null : "not rooted");
        Vec3[] from = {null};
        once("shapes: try to walk away", () -> {
            from[0] = mc().player != null ? mc().player.position() : Vec3.ZERO;
            mc().options.keyUp.setDown(true);
        });
        during("shapes: can't walk while charging", 100, () -> {
            LocalPlayer player = mc().player;
            if (player == null) {
                return "no player";
            }
            double moved = Math.sqrt(player.position().subtract(from[0]).horizontalDistanceSqr());
            return moved < 0.5 ? null : String.format("moved %.2f blocks", moved);
        }, () -> "");
        once("shapes: stop trying", () -> mc().options.keyUp.setDown(false));
        until("shapes: the charge and the roar after it are over", () -> now() - pressed[0] > GodzillaEntity.CHARGE_LENGTH + 20, 700);
        checkServer("shapes: free again after the roar", level -> PlayerGodzilla.isRooted(serverPlayer(level)) ? "still rooted" : null);
        shot("shape_godzilla_after_breath");

        // walking: through a building in his way (put up only now, so his breath's blast can't have holed it), 17 blocks
        // ahead: 21 wide, 11 high, 2 thick
        commands("fill 290 -60 318 310 -50 319 minecraft:oak_planks");
        waitTicks(10);
        once("shapes: walk on", () -> {
            if (mc().player != null) {
                mc().player.setXRot(0.0F);
                from[0] = mc().player.position();
            }
            mc().options.keyUp.setDown(true);
        });
        waitTicks(200);
        once("shapes: stop", () -> mc().options.keyUp.setDown(false));
        check("shapes: he walks again after his breath", () -> {
            LocalPlayer player = mc().player;
            double moved = player == null ? 0.0 : Math.sqrt(player.position().subtract(from[0]).horizontalDistanceSqr());
            return moved > 5.0 ? null : String.format("moved only %.1f blocks", moved);
        });
        checkServer("shapes: walking, he breaks through the building", level -> {
            int left = 0;
            for (BlockPos pos : BlockPos.betweenClosed(290, -60, 318, 310, -50, 319)) {
                if (level.getBlockState(pos).is(Blocks.OAK_PLANKS)) {
                    left++;
                }
            }
            log("shapes: " + left + " of 462 planks left standing");
            return left < 400 ? null : left + " of 462 planks still standing";
        });
        shot("shape_godzilla_through_the_wall");
        server("shapes: back to himself", level -> log("shapes: back: " + setShape(serverPlayer(level), null)));
        waitTicks(20);
        checkServer("shapes: changing back frees him", level -> !PlayerGodzilla.isGodzilla(serverPlayer(level))
                && !PlayerGodzilla.isRooted(serverPlayer(level)) ? null : "still Godzilla or rooted");
    }

    // ------------------------------------------------------------------ the Odo Island Monster

    private static void odo() {
        server("shapes: as the Odo Island Monster", level -> {
            ServerPlayer player = serverPlayer(level);
            player.teleportTo(level, ODO_AT.x, ODO_AT.y, ODO_AT.z, 0.0F, 0.0F);
            log("shapes: Odo taken: " + setShape(player, ModEntities.ODO_ISLAND_MONSTER.get().create(level)));
        });
        waitTicks(60);
        check("shapes: playing as the Odo (both sides)", () -> mc().player != null && Shapes.shapeOf(mc().player)
                instanceof com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity ? null : "the client doesn't have him");
        checkServer("shapes: the server has him too", level -> PlayerOdo.isOdo(serverPlayer(level)) ? null : "not on the server");

        // walking, then holding Left Control (as the keyboard sends it) to run
        double[] walk = {0.0};
        double[] run = {0.0};
        speed("shapes: walking", walk);
        once("shapes: Left Control down", () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),
                GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_CONTROL));
        once("shapes: third person", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        speed("shapes: running", run);
        check("shapes: as the Odo, third person stands back to show all of him", () -> {
            LocalPlayer player = mc().player;
            if (player == null) {
                return "no player";
            }
            double d = mc().gameRenderer.getMainCamera().getPosition().distanceTo(player.getEyePosition());
            return d > 15.0 ? null : String.format("camera %.1f blocks from his eyes", d);
        });
        shot("shape_odo_running");
        once("shapes: first person", () -> mc().options.setCameraType(CameraType.FIRST_PERSON));
        once("shapes: Left Control up", () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),
                GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_RELEASE, 0));
        check("shapes: Left Control runs at 6 blocks a second", () -> Math.abs(run[0] - 6.0) < 0.3 && run[0] > walk[0] + 1.0 ? null
                : String.format("running %.2f, walking %.2f blocks a second", run[0], walk[0]));

        // a left click: his bite, not a punch
        once("shapes: look ahead", () -> {
            if (mc().player != null) {
                mc().player.setXRot(20.0F);
            }
        });
        waitTicks(5);
        pigAt("shapes: a pig in his jaws' way", player -> player.getEyePosition().add(player.getLookAngle().scale(6.5)));
        waitTicks(5);
        once("shapes: left click", () -> KeyMapping.click(mc().options.keyAttack.getKey()));
        waitTicks(30);
        checkServer("shapes: a left click bites (the pig in front of him is hurt)", ShapeScenes::pigBitten);
        server("shapes: back to himself", level -> log("shapes: back: " + setShape(serverPlayer(level), null)));
        waitTicks(20);
    }

    /** Walks forward about 60 ticks: the speed (blocks a second) over the last 20 or so, by the ticks that went by. */
    private static void speed(String label, double[] out) {
        Vec3[] at = {null};
        int[] from = {0};
        once(label + ": go", () -> mc().options.keyUp.setDown(true));
        waitTicks(40);
        once(label + ": from", () -> {
            at[0] = mc().player != null ? mc().player.position() : Vec3.ZERO;
            from[0] = now();
        });
        waitTicks(20);
        once(label + ": to", () -> {
            if (mc().player != null) {
                int ticks = Math.max(1, now() - from[0]);
                out[0] = Math.sqrt(mc().player.position().subtract(at[0]).horizontalDistanceSqr()) * 20.0 / ticks;
            }
            mc().options.keyUp.setDown(false);
            log(String.format("%s: %.2f blocks a second", label, out[0]));
        });
        waitTicks(10);
    }

    private ShapeScenes() {
    }
}
