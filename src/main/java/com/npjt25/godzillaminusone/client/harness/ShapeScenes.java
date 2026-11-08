package com.npjt25.godzillaminusone.client.harness;

import com.mojang.blaze3d.platform.InputConstants;
import com.npjt25.godzillaminusone.ClientConfig;
import com.npjt25.godzillaminusone.client.ModKeyContexts;
import com.npjt25.godzillaminusone.client.PlayerGodzillaControls;
import com.npjt25.godzillaminusone.compat.Shapes;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import com.npjt25.godzillaminusone.entity.GodzillaStride;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
 * takes Godzilla's shape through Walkers' own API (as its commands and menus do), and the Odo's through ReMorphed's
 * (the request its morph menu sends), and plays them with the keys a player would press: as Godzilla each of his five
 * keys makes its own move (J bites, B breathes: rooted from the charge to the end of the roar after the beam, then free
 * again), a key rebound as in Controls is the one that works (and is saved with the game's options), and walking breaks
 * through what's built; climbing he walks up onto a rock ledge, and with M walks through a bedrock wall; as the Odo,
 * he's in third person on his own and moves at 12 blocks a second with no key held (measured by the distance he
 * covers), and a left click bites; changing back leaves the player's speed as it was.
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

    /**
     * Through ReMorphed, as its morph menu does (NetworkHandler.sendSwap2ndShapeRequest(ShapeType), from the client): the
     * player asks to become {@code type}. False if ReMorphed isn't there (or that didn't work).
     */
    private static boolean morphThroughReMorphed(EntityType<?> type) {
        if (!ModList.get().isLoaded("remorphed")) {
            return false;
        }
        try {
            Class<?> shapeType = Class.forName("tocraft.walkers.api.variant.ShapeType");
            Object shape = shapeType.getMethod("from", EntityType.class).invoke(null, type);
            if (shape == null) {
                log("shapes: Walkers has no shape for " + type);
                return false;
            }
            Class.forName("tocraft.remorphed.network.NetworkHandler").getMethod("sendSwap2ndShapeRequest", shapeType).invoke(null, shape);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            log("shapes: ReMorphed's morph request: " + e);
            return false;
        }
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

        // each of his other keys makes its own move (each waited out before the next)
        check("shapes: as Godzilla, his keys count", () -> ModKeyContexts.GODZILLA_SHAPE.isActive() ? null : "their context isn't active");
        godzillaKey("shapes: Z makes his tail whip", GLFW.GLFW_KEY_Z, PlayerGodzilla.Move.TAIL_WHIP, true);
        waitTicks(PlayerGodzilla.Move.TAIL_WHIP.length);
        godzillaKey("shapes: H makes his scratch", GLFW.GLFW_KEY_H, PlayerGodzilla.Move.SCRATCH, true);
        waitTicks(PlayerGodzilla.Move.SCRATCH.length);
        godzillaKey("shapes: K makes his roar", GLFW.GLFW_KEY_K, PlayerGodzilla.Move.ROAR, true);
        waitTicks(PlayerGodzilla.Move.ROAR.length);

        // his roar rebound from K to N, as Controls does it: N roars, K doesn't any more; saved with the game's options
        once("shapes: Roar rebound to N", () -> ShotsHarness.rebind(PlayerGodzillaControls.ROAR, InputConstants.getKey(GLFW.GLFW_KEY_N, 0)));
        check("shapes: the new key is saved with the game's options", () -> ShotsHarness.saved(PlayerGodzillaControls.ROAR, "key.keyboard.n"));
        check("shapes: the list on screen names the new key", () -> {
            String shown = PlayerGodzillaControls.ROAR.getTranslatedKeyMessage().getString();
            log("shapes: roar's key shown as " + shown);
            return shown.equalsIgnoreCase(InputConstants.getKey(GLFW.GLFW_KEY_N, 0).getDisplayName().getString())
                    && !shown.equalsIgnoreCase("K") ? null : "shows " + shown;
        });
        once("shapes: the HUD on (roar on N)", () -> mc().options.hideGui = false);
        waitTicks(5);
        shot("shape_godzilla_hud_roar_on_n");
        once("shapes: the HUD off (roar on N)", () -> mc().options.hideGui = true);
        godzillaKey("shapes: K, no longer his roar's key, does nothing", GLFW.GLFW_KEY_K, PlayerGodzilla.Move.ROAR, false);
        godzillaKey("shapes: N, his roar's key now, roars", GLFW.GLFW_KEY_N, PlayerGodzilla.Move.ROAR, true);
        waitTicks(PlayerGodzilla.Move.ROAR.length);
        once("shapes: Roar back on K", () -> ShotsHarness.rebind(PlayerGodzillaControls.ROAR, PlayerGodzillaControls.ROAR.getDefaultKey()));
        check("shapes: K saved again", () -> ShotsHarness.saved(PlayerGodzillaControls.ROAR, "key.keyboard.k"));

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
        // (walking, he destroys nothing below walkBreaksFromY, 60: this flat world's ground is at -60, so for this walk it's
        // set below it; back to 60 after)
        server("shapes: walkBreaksFromY below this world's ground", level -> com.npjt25.godzillaminusone.Config.WALK_BREAKS_FROM_Y.set(-64));
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
        server("shapes: walkBreaksFromY back to 60", level -> com.npjt25.godzillaminusone.Config.WALK_BREAKS_FROM_Y.set(60));
        stride();
        minusZero();
        server("shapes: back to himself", level -> log("shapes: back: " + setShape(serverPlayer(level), null)));
        waitTicks(20);
        checkServer("shapes: changing back frees him", level -> !PlayerGodzilla.isGodzilla(serverPlayer(level))
                && !PlayerGodzilla.isRooted(serverPlayer(level)) ? null : "still Godzilla or rooted");
        check("shapes: himself again, Godzilla's keys don't count", () -> ModKeyContexts.GODZILLA_SHAPE.isActive() ? "their context is still active" : null);
    }

    /**
     * Never stuck as Godzilla: climbing (as he starts out), he walks straight up onto a ledge of rock 6 blocks high (rock:
     * nothing he breaks); M switches him to walking through things, and he walks through a wall of bedrock 21 blocks
     * high (nothing anyone breaks), on the ground, the server agreeing where he is; M again, back to climbing.
     */
    private static void stride() {
        Vec3 climbAt = new Vec3(300.5, -60.0, 400.5);
        Vec3 throughAt = new Vec3(300.5, -60.0, 480.5);
        commands("fill 285 -60 410 315 -55 440 minecraft:stone", "fill 285 -60 490 315 -40 492 minecraft:bedrock");
        server("shapes: to the ledge", level -> serverPlayer(level).teleportTo(level, climbAt.x, climbAt.y, climbAt.z, 0.0F, 0.0F));
        waitTicks(20);
        check("shapes: as Godzilla he starts out climbing", () -> ClientConfig.PLAYER_GODZILLA_WALK_THROUGH.get() ? "walking through" : null);
        once("shapes: walk at the ledge", () -> {
            if (mc().player != null) {
                mc().player.setYRot(0.0F);
                mc().player.setXRot(0.0F);
            }
            mc().options.keyUp.setDown(true);
        });
        waitTicks(100);
        once("shapes: stop at the ledge", () -> mc().options.keyUp.setDown(false));
        waitTicks(10);
        checkServer("shapes: climbing, he walks up onto a ledge 6 blocks high", level -> {
            ServerPlayer player = serverPlayer(level);
            log(String.format("shapes: after the ledge at %.1f %.1f %.1f", player.getX(), player.getY(), player.getZ()));
            return player.getY() > -54.5 && player.getZ() > 412.0 ? null
                    : String.format("at y %.1f, z %.1f", player.getY(), player.getZ());
        });
        shot("shape_godzilla_climbed");

        server("shapes: to the bedrock wall", level -> serverPlayer(level).teleportTo(level, throughAt.x, throughAt.y, throughAt.z, 0.0F, 0.0F));
        waitTicks(20);
        tap("shapes: M", GLFW.GLFW_KEY_M);
        waitTicks(10);
        check("shapes: M switches him to walking through things", () -> ClientConfig.PLAYER_GODZILLA_WALK_THROUGH.get() ? null : "still climbing");
        checkServer("shapes: the server knows", level -> GodzillaStride.walksThrough(serverPlayer(level)) ? null : "not on the server");
        once("shapes: walk at the wall", () -> {
            if (mc().player != null) {
                mc().player.setYRot(0.0F);
                mc().player.setXRot(0.0F);
            }
            mc().options.keyUp.setDown(true);
        });
        waitTicks(50);
        shot("shape_godzilla_walking_through");
        waitTicks(50);
        once("shapes: stop past the wall", () -> mc().options.keyUp.setDown(false));
        waitTicks(10);
        check("shapes: walking through, he's through the bedrock wall, on the ground", () -> {
            LocalPlayer player = mc().player;
            if (player == null) {
                return "no player";
            }
            log(String.format("shapes: after the wall at %.1f %.1f %.1f", player.getX(), player.getY(), player.getZ()));
            return player.getZ() > 494.0 && player.getY() < -59.0 ? null : String.format("at y %.1f, z %.1f", player.getY(), player.getZ());
        });
        checkServer("shapes: the server has him there too (not snapped back)", level -> {
            ServerPlayer player = serverPlayer(level);
            return player.getZ() > 494.0 ? null : String.format("server has him at z %.1f", player.getZ());
        });
        tap("shapes: M again", GLFW.GLFW_KEY_M);
        waitTicks(10);
        check("shapes: M again, back to climbing", () -> ClientConfig.PLAYER_GODZILLA_WALK_THROUGH.get() ? "still walking through" : null);
        checkServer("shapes: the server knows that too", level -> GodzillaStride.walksThrough(serverPlayer(level)) ? "still walking through" : null);
    }

    /**
     * Taps a key as Godzilla: {@code made}, the server should see {@code move} made then (and not, nothing new made).
     */
    /**
     * As Godzilla Minus Zero: X makes his tail strike (the tip of his tail glowing), as does /godzilla tailstrike (run as
     * the player); /godzilla breath his breath, rooted until the roar after his second beam is over (his charge's length,
     * not Minus One's).
     */
    private static void minusZero() {
        server("shapes: as Minus Zero", level -> {
            ServerPlayer player = serverPlayer(level);
            GodzillaMinusZeroEntity shape = ModEntities.GODZILLA_MINUS_ZERO.get().create(level);
            log("shapes: Minus Zero taken: " + setShape(player, shape));
        });
        waitTicks(60);
        check("shapes: playing as Minus Zero (client)", () -> mc().player != null && Shapes.godzillaOf(mc().player) instanceof GodzillaMinusZeroEntity
                ? null : "the client doesn't have Minus Zero");
        checkServer("shapes: Minus Zero on the server too", level -> PlayerGodzilla.canTailStrike(serverPlayer(level)) ? null : "no tail strike");
        once("shapes: Minus Zero's HUD on", () -> mc().options.hideGui = false);
        waitTicks(5);
        shot("shape_minus_zero_hud");
        once("shapes: Minus Zero's HUD off", () -> mc().options.hideGui = true);
        once("shapes: Minus Zero looks ahead", () -> {
            if (mc().player != null) {
                mc().player.setXRot(10.0F);
            }
        });
        godzillaKey("shapes: X makes Minus Zero's tail strike", GLFW.GLFW_KEY_X, PlayerGodzilla.Move.TAIL_STRIKE, true);
        waitTicks(6);
        check("shapes: the tip of his tail glowing", () -> {
            GodzillaEntity shape = mc().player == null ? null : Shapes.godzillaOf(mc().player);
            return shape != null && shape.isTailGlowing() ? null : "not glowing";
        });
        // (seen from the side: wound up, the tip of his tail is ~108 blocks behind him, ~21 up, where his own camera is)
        once("shapes: a look at the tip of his tail", () -> {
            LocalPlayer player = mc().player;
            if (player != null) {
                Vec3 ahead = Vec3.directionFromRotation(0.0F, player.yBodyRot);
                Vec3 tip = player.position().add(ahead.scale(-108.0)).add(0.0, 21.0, 0.0);
                Vec3 side = new Vec3(-ahead.z, 0.0, ahead.x).scale(60.0);
                ShotsHarness.view(tip.add(side).add(0.0, 18.0, 0.0), tip);
            }
        });
        waitTicks(3);
        shot("shape_minus_zero_tail_strike");
        once("shapes: his own camera again", () -> ShotsHarness.view(null, null));
        waitTicks(PlayerGodzilla.Move.TAIL_STRIKE.length);
        int[] before = {-1};
        server("shapes: /godzilla tailstrike", level -> {
            ServerPlayer player = serverPlayer(level);
            before[0] = PlayerGodzilla.lastMoveAt(player);
            level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "godzilla tailstrike");
        });
        waitTicks(4);
        checkServer("shapes: /godzilla tailstrike makes it too", level -> PlayerGodzilla.lastMove(serverPlayer(level)) == PlayerGodzilla.Move.TAIL_STRIKE
                && PlayerGodzilla.lastMoveAt(serverPlayer(level)) != before[0] ? null : "last move " + PlayerGodzilla.lastMove(serverPlayer(level)));
        waitTicks(PlayerGodzilla.Move.TAIL_STRIKE.length);

        once("shapes: Minus Zero looks up", () -> {
            if (mc().player != null) {
                mc().player.setXRot(-70.0F);
            }
        });
        waitTicks(5);
        int[] pressed = {0};
        once("shapes: mark Minus Zero's breath", () -> pressed[0] = now());
        server("shapes: /godzilla breath", level -> {
            ServerPlayer player = serverPlayer(level);
            PlayerGodzilla.rechargeBreath(player); // (he breathed as Minus One a minute ago: no waiting out its recharge)
            level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "godzilla breath");
        });
        waitTicks(20);
        checkServer("shapes: /godzilla breath starts Minus Zero's charge: rooted", level -> PlayerGodzilla.isRooted(serverPlayer(level))
                && PlayerGodzilla.lastMove(serverPlayer(level)) == PlayerGodzilla.Move.ATOMIC_BREATH ? null : "not rooted / not his breath");
        until("shapes: Minus Zero's second beam", () -> now() - pressed[0] > GodzillaEntity.SURGE_TICK + 8, 700);
        shot("shape_minus_zero_second_beam");
        until("shapes: Minus Zero's first beam would be over (Minus One's whole charge)", () -> now() - pressed[0] > GodzillaEntity.CHARGE_LENGTH + 10, 400);
        checkServer("shapes: still rooted (his breath lasts longer than Minus One's)", level -> PlayerGodzilla.isRooted(serverPlayer(level)) ? null : "free already");
        until("shapes: Minus Zero's charge and the roar after it are over",
                () -> now() - pressed[0] > GodzillaEntity.SECOND_BEAM_END_TICK + GodzillaEntity.ROAR_TICKS + 20, 400);
        checkServer("shapes: Minus Zero free again after the roar", level -> PlayerGodzilla.isRooted(serverPlayer(level)) ? "still rooted" : null);
        server("shapes: /godzilla tailstrike as Minus One", level -> {
            ServerPlayer player = serverPlayer(level);
            log("shapes: back to Minus One: " + setShape(player, ModEntities.GODZILLA.get().create(level)));
        });
        waitTicks(30);
        checkServer("shapes: as Minus One, no tail strike", level -> {
            ServerPlayer player = serverPlayer(level);
            int at = PlayerGodzilla.lastMoveAt(player);
            int result = level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "godzilla tailstrike");
            return !PlayerGodzilla.canTailStrike(player) && PlayerGodzilla.lastMoveAt(player) == at ? null : "made it anyway (" + result + ")";
        });
    }

    private static void godzillaKey(String name, int key, PlayerGodzilla.Move move, boolean made) {
        int[] before = {-1};
        server(name + ": before", level -> before[0] = PlayerGodzilla.lastMoveAt(serverPlayer(level)));
        tap(name, key);
        waitTicks(6);
        checkServer(name, level -> {
            ServerPlayer player = serverPlayer(level);
            PlayerGodzilla.Move last = PlayerGodzilla.lastMove(player);
            boolean fresh = PlayerGodzilla.lastMoveAt(player) != before[0];
            String detail = "last move " + last + (fresh ? ", just now" : ", from before");
            return made ? (fresh && last == move ? null : detail) : (fresh ? detail : null);
        });
    }



    // ------------------------------------------------------------------ the Odo Island Monster

    private static void odo() {
        server("shapes: to the Odo's ground", level -> serverPlayer(level).teleportTo(level, ODO_AT.x, ODO_AT.y, ODO_AT.z, 0.0F, 0.0F));
        waitTicks(10);
        boolean[] remorphed = {false};
        once("shapes: as the Odo Island Monster, through ReMorphed", () -> {
            remorphed[0] = morphThroughReMorphed(ModEntities.ODO_ISLAND_MONSTER.get());
            log("shapes: ReMorphed's morph request sent: " + remorphed[0]);
        });
        server("shapes: as the Odo Island Monster (Walkers, without ReMorphed)", level -> {
            if (!remorphed[0]) {
                log("shapes: Odo taken through Walkers: " + setShape(serverPlayer(level), ModEntities.ODO_ISLAND_MONSTER.get().create(level)));
            }
        });
        waitTicks(60);
        check("shapes: playing as the Odo (both sides)", () -> mc().player != null && Shapes.shapeOf(mc().player)
                instanceof com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity ? null : "the client doesn't have him");
        checkServer("shapes: the server has him too", level -> PlayerOdo.isOdo(serverPlayer(level)) ? null : "not on the server");

        // no key held: he moves at 12 blocks a second (three seconds of it), and sprinting (Left Control) adds nothing
        check("shapes: as the Odo, third person without asking", () -> mc().options.getCameraType().isFirstPerson()
                ? "still in first person" : null);
        once("shapes: first person asked for", () -> mc().options.setCameraType(CameraType.FIRST_PERSON));
        waitTicks(3);
        check("shapes: as the Odo, back in third person at once", () -> mc().options.getCameraType().isFirstPerson()
                ? "first person stuck" : null);
        double[] walk = new double[3];
        double[] sprint = new double[2];
        speed("shapes: moving", walk);
        checkServer("shapes: as the Odo, one speed-up on him, no more", level -> {
            long ups = runModifiers(serverPlayer(level));
            double value = serverPlayer(level).getAttributeValue(Attributes.MOVEMENT_SPEED);
            log(String.format("shapes: moving: %d speed-up(s), speed attribute %.4f (server)", ups, value));
            return ups == 1 ? null : ups + " speed-ups";
        });
        check("shapes: as the Odo, third person stands back to show all of him", () -> {
            LocalPlayer player = mc().player;
            if (player == null) {
                return "no player";
            }
            double d = mc().gameRenderer.getMainCamera().getPosition().distanceTo(player.getEyePosition());
            return d > 15.0 ? null : String.format("camera %.1f blocks from his eyes", d);
        });
        once("shapes: Left Control down (sprint)", () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),
                GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_CONTROL));
        speed("shapes: sprinting", sprint);
        shot("shape_odo_running");
        once("shapes: Left Control up", () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),
                GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_RELEASE, 0));
        check("shapes: the Odo moves at 12 blocks a second, no key held (each second of it), sprinting too", () -> {
            String detail = String.format("moving %.2f, %.2f, %.2f; sprinting %.2f, %.2f blocks a second", walk[0], walk[1], walk[2],
                    sprint[0], sprint[1]);
            for (double v : walk) {
                if (Math.abs(v - 12.0) > 0.4) {
                    return detail;
                }
            }
            for (double v : sprint) {
                if (Math.abs(v - 12.0) > 0.6) {
                    return detail;
                }
            }
            return null;
        });

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
        // himself again: his speed as it was (no speed-up left on him, either side), and he walks at a player's pace
        checkServer("shapes: himself again, the server has his own speed", level -> {
            ServerPlayer player = serverPlayer(level);
            double value = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            return runModifiers(player) == 0 && Math.abs(value - 0.1) < 1.0E-6 ? null
                    : String.format("%d speed-up(s), speed attribute %.4f", runModifiers(player), value);
        });
        check("shapes: himself again, so does his client", () -> {
            LocalPlayer player = mc().player;
            if (player == null) {
                return "no player";
            }
            double value = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            return runModifiers(player) == 0 && Math.abs(value - 0.1) < 1.0E-6 ? null
                    : String.format("%d speed-up(s), speed attribute %.4f", runModifiers(player), value);
        });
        double[] own = new double[3];
        speed("shapes: walking as himself", own);
        check("shapes: himself again, he walks at a player's pace (4.3 blocks a second)", () -> Math.abs(own[2] - 4.317) < 0.3 ? null
                : String.format("walking %.2f blocks a second", own[2]));
    }

    /** The Odo's run speed-ups on a player's speed (PlayerOdo: one while the Run key is held, none otherwise). */
    private static long runModifiers(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed == null ? -1 : speed.getModifiers().stream().map(AttributeModifier::getName)
                .filter("Odo Island Monster's pace"::equals).count();
    }

    /**
     * Walks forward: after 30 ticks to get going, the speed (blocks a second, level ground) over each of the next
     * {@code out.length} stretches of 20 ticks or so, by the distance covered and the ticks that went by.
     */
    private static void speed(String label, double[] out) {
        Vec3[] at = {null};
        int[] from = {0};
        once(label + ": go", () -> mc().options.keyUp.setDown(true));
        waitTicks(30);
        for (int i = 0; i < out.length; i++) {
            int n = i;
            once(label + ": from " + n, () -> {
                at[0] = mc().player != null ? mc().player.position() : Vec3.ZERO;
                from[0] = now();
            });
            waitTicks(20);
            once(label + ": to " + n, () -> {
                if (mc().player != null) {
                    int ticks = Math.max(1, now() - from[0]);
                    out[n] = Math.sqrt(mc().player.position().subtract(at[0]).horizontalDistanceSqr()) * 20.0 / ticks;
                }
                log(String.format("%s (%d): %.2f blocks a second", label, n + 1, out[n]));
            });
        }
        once(label + ": stop", () -> mc().options.keyUp.setDown(false));
        waitTicks(10);
    }

    private ShapeScenes() {
    }
}
