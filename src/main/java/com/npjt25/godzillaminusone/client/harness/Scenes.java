package com.npjt25.godzillaminusone.client.harness;

import com.mojang.blaze3d.platform.InputConstants;
import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.client.ModKeyMappings;
import com.npjt25.godzillaminusone.client.PlayerGodzillaControls;
import com.npjt25.godzillaminusone.client.SearchlightGlow;
import com.npjt25.godzillaminusone.client.ShinseiMaruSounds;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaMinusZeroEntity;
import com.npjt25.godzillaminusone.entity.GodzillaStatueEntity;
import com.npjt25.godzillaminusone.entity.KingGhidorahEntity;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import com.npjt25.godzillaminusone.entity.Radiation;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruPart;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModEffects;
import com.npjt25.godzillaminusone.registry.ModItems;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.camera;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.check;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.checkServer;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.commands;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.during;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.find;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.heard;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.heardSince;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.log;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.look;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.now;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.once;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.probe;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.server;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.serverPlayer;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.shot;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.until;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.untilServer;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.view;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.waitTicks;

/**
 * The scenes {@link ShotsHarness} goes through, in a flat world (its ground is at y -60), each in its own place: the
 * config screen; the hazmat suit worn and the Radiation effect; the hunched snarl statue; the Odo Island Monster spawning (and his spawn egg in the hotbar); Godzilla's roar heard from
 * either side of 200 blocks; a searchlight's beam over water and ice at night; the Shinsei Maru (walking her decks as
 * she sails and turns, climbing her ladders, her crew standing at their stations, her motor); a Shinden (third person,
 * its engine, Free Look); Godzilla getting over his atomic breath.
 */
final class Scenes {
    private static final double MODEL_SCALE = 7.83 / 16.0; // GodzillaRenderer.SCALE: blocks per model pixel

    static void script() {
        commands("gamerule doMobSpawning false", "gamerule mobGriefing true", "time set 6000", "weather clear");
        waitTicks(20);
        controls();
        configScreen();
        hazmat();
        statue();
        odo();
        roars();
        searchlight();
        searchlightOperator();
        boat();
        shinden();
        godzilla();
        minusZero();
        kingGhidorah();
        kingGhidorahGround();
        ShapeScenes.script(); // (with Walkers installed: CI's client-shapes job)
    }

    /**
     * Only the searchlight's scenes (CI's "shaders" job: with Oculus and a shader pack on, which draw the beam their own
     * way): its beam over water and ice, and worked from it, with nothing of it in the middle of the view.
     */
    static void searchlightScript() {
        commands("gamerule doMobSpawning false", "time set 6000", "weather clear");
        waitTicks(20);
        check("shaders: a shader pack in use", () -> {
            boolean on = com.npjt25.godzillaminusone.client.render.SearchlightRenderer.shaderPackInUse();
            log("shaders: shader pack in use: " + on);
            return on ? null : "no shader pack in use (Oculus missing, or the pack didn't load)";
        });
        searchlight();
        searchlightOperator();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ------------------------------------------------------------------ the controls

    /**
     * The mod's keys are the game's own key bindings, saved with the rest in options.txt: Godzilla's Roar rebound to N
     * (as the Controls screen does it) and saved, then back to K. (Whether the rebound key works is checked playing as
     * him, with Walkers: ShapeScenes.)
     */
    private static void controls() {
        check("controls: each of the mod's keys is in Controls, once", () -> {
            List<String> wrong = new ArrayList<>();
            for (KeyMapping key : ModKeyMappings.all()) {
                long times = java.util.Arrays.stream(mc().options.keyMappings).filter(k -> k == key).count();
                if (times != 1) {
                    wrong.add(key.getName() + " x" + times);
                }
            }
            log("controls: the mod's keys: " + java.util.Arrays.stream(ModKeyMappings.all())
                    .map(k -> k.getName() + "=" + k.getTranslatedKeyMessage().getString()).toList());
            return wrong.isEmpty() ? null : "in Controls other than once: " + wrong;
        });
        once("controls: Roar rebound to N", () -> ShotsHarness.rebind(PlayerGodzillaControls.ROAR, InputConstants.getKey(GLFW.GLFW_KEY_N, 0)));
        check("controls: the new key is saved in options.txt", () -> ShotsHarness.saved(PlayerGodzillaControls.ROAR, "key.keyboard.n"));
        once("controls: Roar back on K", () -> ShotsHarness.rebind(PlayerGodzillaControls.ROAR, PlayerGodzillaControls.ROAR.getDefaultKey()));
        check("controls: K saved again", () -> ShotsHarness.saved(PlayerGodzillaControls.ROAR, "key.keyboard.k"));
    }

    // ------------------------------------------------------------------ the config screen

    /** The config screen (Mods, Godzilla Minus One, Config) has Godzilla's downed time and the played Godzilla's movement. */
    private static void configScreen() {
        once("config: open", () -> mc().setScreen(new com.npjt25.godzillaminusone.client.ModConfigScreen(null)));
        waitTicks(10);
        check("config: downed time and played Godzilla's movement on it", () -> {
            if (mc().screen == null) {
                return "no screen";
            }
            List<String> labels = mc().screen.children().stream()
                    .filter(w -> w instanceof net.minecraft.client.gui.components.AbstractWidget)
                    .map(w -> ((net.minecraft.client.gui.components.AbstractWidget) w).getMessage().getString()).toList();
            log("config: " + labels);
            boolean downed = labels.stream().anyMatch(l -> l.startsWith("Lies Downed: "));
            boolean stride = labels.stream().anyMatch(l -> l.startsWith("Played Godzilla: "));
            return downed && stride ? null : "missing: " + (downed ? "" : "downed time ") + (stride ? "" : "movement");
        });
        shot("config_screen");
        once("config: close", () -> mc().setScreen(null));
        waitTicks(5);
    }

    // ------------------------------------------------------------------ the hazmat suit and Radiation

    private static final Vec3 SUIT_AT = new Vec3(500.5, -60.0, 0.5);

    /**
     * The hazmat suit worn (all four pieces), from in front and behind; the Radiation effect (its icon on screen, its
     * name), and the suit, put on, washing it off.
     */
    private static void hazmat() {
        once("hazmat: the game's camera", () -> view(null, null));
        server("hazmat: suited up", level -> {
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(level, SUIT_AT.x, SUIT_AT.y, SUIT_AT.z, 0.0F, 0.0F);
            suit(player, true);
        });
        waitTicks(30);
        check("hazmat: all four pieces on (client)", () -> mc().player != null && Radiation.shielded(mc().player) ? null : "not all on");
        once("hazmat: from in front", () -> view(SUIT_AT.add(0.6, 1.5, 3.0), SUIT_AT.add(0.0, 1.0, 0.0)));
        waitTicks(10);
        shot("hazmat_front");
        once("hazmat: from behind", () -> view(SUIT_AT.add(-0.6, 1.6, -3.0), SUIT_AT.add(0.0, 1.0, 0.0)));
        waitTicks(10);
        shot("hazmat_back");
        once("hazmat: from the side", () -> view(SUIT_AT.add(3.0, 1.3, 0.4), SUIT_AT.add(0.0, 1.0, 0.0)));
        waitTicks(10);
        shot("hazmat_side");

        // without the suit: Radiation, its icon on screen (the radiation trefoil)
        server("hazmat: off", level -> suit(serverPlayer(level), false));
        waitTicks(5);
        server("hazmat: irradiated", level -> {
            ServerPlayer player = serverPlayer(level);
            boolean added = player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.RADIATION.get(), 600, 0));
            log("hazmat: Radiation added: " + added + ", shielded " + Radiation.shielded(player) + ", armour "
                    + player.getInventory().armor + ", effects " + player.getActiveEffects() + ", game mode "
                    + player.gameMode.getGameModeForPlayer() + ", alive " + player.isAlive());
        });
        waitTicks(5);
        checkServer("hazmat: Radiation on the player (server)", level -> serverPlayer(level).hasEffect(ModEffects.RADIATION.get())
                ? null : "none; effects " + serverPlayer(level).getActiveEffects());
        once("hazmat: first person, the HUD on", () -> {
            view(null, null);
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            mc().options.hideGui = false;
        });
        waitTicks(20);
        check("hazmat: Radiation on the player, by that name", () -> {
            if (mc().player == null || !mc().player.hasEffect(ModEffects.RADIATION.get())) {
                return "no Radiation";
            }
            String name = net.minecraft.client.resources.language.I18n.get(ModEffects.RADIATION.get().getDescriptionId());
            log("hazmat: the effect is called " + name);
            return name.equals("Radiation") ? null : "called " + name;
        });
        shot("radiation_effect_hud");
        once("hazmat: inventory", () -> {
            if (mc().player != null) {
                mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player));
            }
        });
        waitTicks(10);
        shot("radiation_effect_inventory");
        once("hazmat: inventory closed, HUD off", () -> {
            mc().setScreen(null);
            mc().options.hideGui = true;
        });
        // the suit back on: what he'd caught washes off
        server("hazmat: suit back on", level -> suit(serverPlayer(level), true));
        waitTicks(30);
        checkServer("hazmat: the suit on, Radiation washes off", level -> serverPlayer(level).hasEffect(ModEffects.RADIATION.get())
                ? "still irradiated" : null);
        server("hazmat: done", level -> {
            ServerPlayer player = serverPlayer(level);
            suit(player, false);
            player.removeAllEffects();
            player.setGameMode(GameType.CREATIVE);
        });
        waitTicks(5);
    }

    private static void suit(ServerPlayer player, boolean on) {
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, on ? new ItemStack(ModItems.HAZMAT_HOOD.get()) : ItemStack.EMPTY);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, on ? new ItemStack(ModItems.HAZMAT_SUIT.get()) : ItemStack.EMPTY);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, on ? new ItemStack(ModItems.HAZMAT_TROUSERS.get()) : ItemStack.EMPTY);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, on ? new ItemStack(ModItems.HAZMAT_BOOTS.get()) : ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ the hunched snarl statue

    private static void statue() {
        Vec3 at = new Vec3(0.5, -60.0, 0.5);
        // the camera of docs/hunched_snarl_preview.png (tools: model pixels, eye (5, 30, -72), looking at (0, 61, 0),
        // 45 degrees), in the world: he faces south (+z), so his front (-z in the model) is +z and his right (+x) west
        Vec3 eye = at.add(-5 * MODEL_SCALE, 30 * MODEL_SCALE, 72 * MODEL_SCALE);
        Vec3 target = at.add(0.0, 61 * MODEL_SCALE, 0.0);
        camera("statue", eye, target, 45);
        waitTicks(60);
        commands("summon godzillaminusone:godzilla_pose_hunched_snarl 0.5 -60 0.5 {Rotation:[0f,0f]}");
        waitTicks(160);
        shot("statue_hunched_snarl");
        float[] was = new float[4];
        once("statue: where it stands", () -> {
            GodzillaStatueEntity statue = find(GodzillaStatueEntity.class);
            if (statue != null) {
                was[0] = statue.getYRot();
                was[1] = (float) statue.getX();
                was[2] = (float) statue.getY();
                was[3] = (float) statue.getZ();
            }
        });
        camera("statue, all of him", at.add(-14, 10, 62), at.add(0, 21, 0), 55);
        waitTicks(60);
        shot("statue_hunched_snarl_full");
        check("statue holds still (no turning, no moving)", () -> {
            GodzillaStatueEntity statue = find(GodzillaStatueEntity.class);
            if (statue == null) {
                return "no statue";
            }
            float turned = Math.abs(Mth.wrapDegrees(statue.getYRot() - was[0]));
            double moved = new Vec3(statue.getX() - was[1], statue.getY() - was[2], statue.getZ() - was[3]).length();
            return turned < 0.01F && moved < 0.001 && Math.abs(statue.getYRot()) < 0.01F ? null
                    : "turned " + turned + ", moved " + moved + ", facing " + statue.getYRot();
        });
        commands("kill @e[type=godzillaminusone:godzilla_pose_hunched_snarl]");
    }

    // ------------------------------------------------------------------ the Odo Island Monster spawning

    private static void odo() {
        server("odo: player with his egg", level -> {
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level, 0.5, -60.0, 100.5, 0.0F, 5.0F);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(ModItems.ODO_ISLAND_MONSTER_SPAWN_EGG.get()));
            player.getInventory().selected = 0;
        });
        once("odo: hud on", () -> {
            mc().options.hideGui = false;
            mc().options.fov().set(70);
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            if (mc().player != null) {
                mc().player.getInventory().selected = 0;
            }
        });
        waitTicks(60);
        shot("odo_spawn_egg_hotbar");
        once("odo: hud off", () -> mc().options.hideGui = true);
        int[] since = {0};
        once("odo: mark", () -> since[0] = now());
        commands("summon godzillaminusone:odo_island_monster 0.5 -60 125.5");
        waitTicks(100);
        check("odo: his spawn cry plays once", () -> {
            int n = heard(since[0], "odo.spawn").size();
            log("odo: sounds in the 5 s after he spawned: " + heardSince(since[0]));
            return n == 1 ? null : "odo.spawn heard " + n + " times";
        });
        check("odo: nothing else over his spawn cry (no growl, no roar)", () -> {
            int others = heard(since[0], "odo.ambient").size() + heard(since[0], "odo.roar").size()
                    + heard(since[0], "odo.roar_far").size() + heard(since[0], "odo.spotted").size();
            return others == 0 ? null : "also heard " + heardSince(since[0]);
        });
        shot("odo_spawned");
        commands("kill @e[type=godzillaminusone:odo_island_monster]");
    }

    // ------------------------------------------------------------------ Godzilla's roar, near and far

    private static void roars() {
        Vec3 from = new Vec3(0.5, 0.0, 3000.5);
        List<String> far = new ArrayList<>();
        for (int distance : new int[] {150, 199, 201, 250, 260, 400}) {
            server("roar: listener " + distance + " blocks away", level -> {
                ServerPlayer player = serverPlayer(level);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, from.x, from.y, from.z + distance, 0.0F, 0.0F);
            });
            waitTicks(10);
            int[] since = {0};
            once("roar: mark", () -> since[0] = now());
            server("roar from " + distance, level -> GodzillaEntity.broadcastRoar(level, from.x, from.y, from.z,
                    ModSounds.GODZILLA_ROAR.get(), 7L));
            waitTicks(10);
            check("roar: a listener " + distance + " blocks away hears " + (distance < 200 ? "the roar" : "a distant roar") + ", one sound",
                    () -> {
                        int near = heard(since[0], "godzilla.roar").size();
                        int far1 = heard(since[0], "godzilla.roar_far").size();
                        int far2 = heard(since[0], "godzilla.roar_far2").size();
                        String got = "roar " + near + ", roar_far " + far1 + ", roar_far2 " + far2;
                        if (near + far1 + far2 != 1) {
                            return "heard " + got;
                        }
                        if (distance < 200) {
                            return near == 1 ? null : "heard " + got;
                        }
                        far.add(far1 == 1 ? "roar_far" : "roar_far2");
                        return near == 0 ? null : "heard " + got;
                    });
        }
        check("roar: the distant roars take turns", () -> {
            for (int i = 1; i < far.size(); i++) {
                if (far.get(i).equals(far.get(i - 1))) {
                    return "in a row: " + far;
                }
            }
            return far.size() >= 3 ? null : "only " + far;
        });
    }

    // ------------------------------------------------------------------ a searchlight over water and ice

    private static void searchlight() {
        camera("searchlight: pool", new Vec3(56.5, -50.5, 19.5), new Vec3(52.0, -55.5, 0.5), 70);
        waitTicks(80);
        commands("time set 18000",
                "fill 40 -61 -15 70 -56 15 minecraft:stone_bricks",
                "fill 41 -60 -14 69 -56 14 minecraft:water",
                "fill 47 -60 -5 63 -60 5 minecraft:gold_block",
                "fill 41 -56 -14 54 -56 14 minecraft:ice",
                "fill 72 -60 0 72 -56 0 minecraft:stone_bricks");
        // the light on a pillar east of the pool, manned (an invisible armour stand at it, looking west and a little
        // down): its beam runs low over the water and lands on the ice
        server("searchlight: manned", level -> {
            SearchlightEntity light = ModEntities.SEARCHLIGHT.get().create(level);
            light.moveTo(72.5, -55.0, 0.5, 90.0F, 0.0F);
            level.addFreshEntity(light);
            ArmorStand stand = new ArmorStand(level, 72.5, -55.0, 0.5);
            stand.setInvisible(true);
            stand.setYRot(90.0F);
            stand.setXRot(3.0F);
            level.addFreshEntity(stand);
            stand.startRiding(light, true);
        });
        waitTicks(120);
        check("searchlight: lit", () -> {
            SearchlightEntity light = find(SearchlightEntity.class);
            return light != null && light.isLit() ? null : "not lit";
        });
        shot("searchlight_water_ice_fancy_a");
        camera("searchlight: looking down through the beam", new Vec3(61.5, -49.0, 7.5), new Vec3(52.0, -56.0, 0.5), 70);
        waitTicks(60);
        shot("searchlight_water_ice_fancy_b");
        once("graphics: fabulous", () -> {
            mc().options.graphicsMode().set(GraphicsStatus.FABULOUS);
            mc().levelRenderer.allChanged();
        });
        waitTicks(120);
        shot("searchlight_water_ice_fabulous_b");
        camera("searchlight: pool again", new Vec3(56.5, -50.5, 19.5), new Vec3(52.0, -55.5, 0.5), 70);
        waitTicks(60);
        shot("searchlight_water_ice_fabulous_a");
        once("graphics: fancy", () -> {
            mc().options.graphicsMode().set(GraphicsStatus.FANCY);
            mc().levelRenderer.allChanged();
        });
        // the same view with the lamp off: through the water and ice where the beam was, you should see just what you
        // see with it on (no deeper)
        camera("searchlight: looking down, lamp off", new Vec3(61.5, -49.0, 7.5), new Vec3(52.0, -56.0, 0.5), 70);
        server("searchlight: off", level -> level.getEntitiesOfClass(SearchlightEntity.class, new AABB(60, -70, -10, 80, -40, 10))
                .forEach(light -> light.setSwitchedOn(false)));
        waitTicks(80);
        shot("searchlight_water_ice_off_b");
        commands("time set 6000", "kill @e[type=minecraft:armor_stand]", "kill @e[type=godzillaminusone:searchlight]");
    }

    // ------------------------------------------------------------------ working a searchlight

    /** Where the operated searchlight stands, at night: its beam runs north (-z), a little down, into a wall. */
    private static final Vec3 OPERATED = new Vec3(200.5, -60.0, -200.5);

    /** The zombie (client side) standing at (x, z) about the operated searchlight, if there is one. */
    @Nullable
    private static Zombie zombieAt(double x, double z) {
        if (mc().level == null) {
            return null;
        }
        List<Zombie> found = mc().level.getEntitiesOfClass(Zombie.class,
                new AABB(OPERATED.x + x - 1.0, -62.0, OPERATED.z + z - 1.0, OPERATED.x + x + 1.0, -56.0, OPERATED.z + z + 1.0));
        return found.isEmpty() ? null : found.get(0);
    }

    /** The light a zombie is drawn in: as the world has it, and as it's drawn (with the beams). */
    private static int[] light(Zombie zombie) {
        int world = mc().getEntityRenderDispatcher().getRenderer(zombie).getPackedLightCoords(zombie, 1.0F);
        int drawn = mc().getEntityRenderDispatcher().getPackedLightCoords(zombie, 1.0F);
        return new int[] {world, drawn};
    }

    /** Checks a zombie is drawn lit by the beam (or not). */
    private static void litByTheBeam(String name, double x, double z, boolean lit) {
        check(name, () -> {
            Zombie zombie = zombieAt(x, z);
            if (zombie == null) {
                return "no zombie there";
            }
            int[] light = light(zombie);
            String detail = String.format("block light %d as drawn, %d in the world; from the beams %d", LightTexture.block(light[1]),
                    LightTexture.block(light[0]), SearchlightGlow.glow(zombie));
            log(name + ": " + detail);
            if (lit) {
                return LightTexture.block(light[1]) >= 13 && LightTexture.block(light[0]) <= 3 ? null : detail;
            }
            return light[1] == light[0] && SearchlightGlow.glow(zombie) == 0 ? null : detail;
        });
    }

    /** Where the player (the searchlight's operator) looks: yaw, pitch. */
    private static void operatorLooks(String label, float yaw, float pitch) {
        once(label, () -> {
            if (mc().player != null) {
                mc().player.setYRot(yaw);
                mc().player.setXRot(pitch);
            }
        });
    }

    private static void switchOperated(String label, boolean on) {
        server(label, level -> level.getEntitiesOfClass(SearchlightEntity.class, new AABB(OPERATED, OPERATED).inflate(4.0))
                .forEach(light -> light.setSwitchedOn(on)));
    }

    /**
     * Working a searchlight at night, as a player does (riding it, the beam going where they look): in first person, in
     * third person, and looking up into the empty sky (where nothing of the beam should pile up in the middle of the
     * view: it's measured, lamp on against lamp off); and the mobs it shines on: one in the beam lit (drawn brighter
     * than the world's light there), one beside it and one behind the wall the beam lands on left as they are; then
     * the beam turned away, and the lamp switched off, each putting the lit one back in the dark.
     */
    private static void searchlightOperator() {
        commands("time set 18000");
        once("searchlight operator: no clouds", () -> mc().options.cloudStatus().set(CloudStatus.OFF));
        once("searchlight operator: the game's camera", () -> view(null, null));
        server("searchlight operator: at it", level -> {
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            SearchlightEntity light = ModEntities.SEARCHLIGHT.get().create(level);
            light.moveTo(OPERATED.x, OPERATED.y, OPERATED.z, 180.0F, 0.0F);
            light.setAim(180.0F, 2.0F);
            level.addFreshEntity(light);
            player.teleportTo(level, OPERATED.x - 1.5, OPERATED.y, OPERATED.z, 180.0F, 2.0F);
            player.startRiding(light, true);
        });
        waitTicks(40);
        // (only now: a fill where no player is, in chunks that aren't loaded, does nothing)
        commands("fill 194 -60 -231 206 -53 -231 minecraft:stone",
                "summon minecraft:zombie 200.5 -60 -211.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[0f,0f]}",
                "summon minecraft:zombie 206.5 -60 -211.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[0f,0f]}",
                "summon minecraft:zombie 200.5 -60 -235.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
        waitTicks(5);
        check("searchlight operator: the wall is up", () -> mc().level != null
                && mc().level.getBlockState(BlockPos.containing(OPERATED.x, -57.0, -231.0)).is(net.minecraft.world.level.block.Blocks.STONE)
                ? null : "no wall");
        once("searchlight operator: first person", () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            mc().options.fov().set(70);
        });
        operatorLooks("searchlight operator: look down the beam", 180.0F, 2.0F);
        waitTicks(60);
        check("searchlight operator: riding it, lit", () -> {
            SearchlightEntity light = find(SearchlightEntity.class);
            return light != null && light.isLit() && mc().player != null && mc().player.getVehicle() == light ? null
                    : "not riding a lit searchlight";
        });
        litByTheBeam("searchlight: the zombie in the beam is lit", 0.0, -11.0, true);
        litByTheBeam("searchlight: the zombie beside the beam isn't", 6.0, -11.0, false);
        litByTheBeam("searchlight: the zombie behind the wall isn't", 0.0, -35.0, false);
        shot("searchlight_operator_first_person");

        // up into the empty sky: whatever is drawn in the middle of the view, lamp on, should be what's there lamp off
        double[] middle = new double[2];
        int[][] whole = new int[2][];
        operatorLooks("searchlight operator: look up", 180.0F, -20.0F);
        waitTicks(20);
        probe("searchlight operator: the middle of the view, lamp on", image -> {
            middle[0] = ShotsHarness.middleBrightness(image);
            whole[0] = ShotsHarness.brightness(image);
        });
        shot("searchlight_operator_sky_first_person");
        switchOperated("searchlight operator: lamp off", false);
        waitTicks(20);
        probe("searchlight operator: the middle of the view, lamp off", image -> {
            middle[1] = ShotsHarness.middleBrightness(image);
            whole[1] = ShotsHarness.brightness(image);
        });
        shot("searchlight_operator_sky_first_person_off");
        check("searchlight operator: nothing of the beam in the middle of the view", () -> {
            String detail = String.format("brightness in the middle %.1f lamp on, %.1f lamp off", middle[0], middle[1]);
            log("searchlight operator: " + detail);
            return Math.abs(middle[0] - middle[1]) < 3.0 ? null : detail;
        });
        check("searchlight operator: nothing of the beam anywhere in the view", () -> {
            if (whole[0] == null || whole[1] == null) {
                return "no pictures";
            }
            int n = ShotsHarness.brighter(whole[0], whole[1], 20);
            log("searchlight operator: pixels brighter by more than 20 with the lamp on: " + n + " of " + whole[0].length);
            return n < whole[0].length / 2000 ? null : n + " pixels brighter with the lamp on";
        });
        switchOperated("searchlight operator: lamp on", true);
        waitTicks(20);
        once("searchlight operator: third person", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        waitTicks(10);
        shot("searchlight_operator_sky_third_person");
        operatorLooks("searchlight operator: look down the beam again", 180.0F, 2.0F);
        waitTicks(20);
        shot("searchlight_operator_third_person");
        once("searchlight operator: first person again", () -> mc().options.setCameraType(CameraType.FIRST_PERSON));

        // the beam turned away from the zombie, and back; then the lamp off
        operatorLooks("searchlight operator: look west", 90.0F, 2.0F);
        waitTicks(15);
        litByTheBeam("searchlight: the beam turned away, the zombie goes dark", 0.0, -11.0, false);
        operatorLooks("searchlight operator: look down the beam once more", 180.0F, 2.0F);
        waitTicks(15);
        litByTheBeam("searchlight: the beam back on the zombie, it's lit again", 0.0, -11.0, true);
        once("searchlight operator: watch from the side", () -> view(OPERATED.add(14.0, 5.0, -12.0), OPERATED.add(0.0, -0.5, -16.0)));
        waitTicks(10);
        shot("searchlight_lights_mobs");
        switchOperated("searchlight operator: lamp off again", false);
        waitTicks(15);
        litByTheBeam("searchlight: the lamp off, the zombie goes dark", 0.0, -11.0, false);
        shot("searchlight_lights_mobs_off");

        once("searchlight operator: done", () -> {
            view(null, null);
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            mc().options.cloudStatus().set(CloudStatus.FANCY);
        });
        server("searchlight operator: off it", level -> serverPlayer(level).stopRiding());
        commands("kill @e[type=minecraft:zombie]", "kill @e[type=godzillaminusone:searchlight]", "time set 6000");
        waitTicks(5);
    }

    // ------------------------------------------------------------------ Godzilla Minus Zero

    private static final Vec3 ONE_AT = new Vec3(700.5, -60.0, 0.5);
    private static final Vec3 ZERO_AT = new Vec3(760.5, -60.0, 0.5);

    /**
     * Godzilla Minus Zero: standing beside Minus One, each with a gold pillar his own height beside him (47 and 94
     * blocks), twice his size on the client too; then Minus One brought down, and one Minus Zero (server and client)
     * getting up where he lay; his tail strike; his whole breath (both beams and both blasts, his plates dimmed and lit
     * again, the burns on his chest); his spawn egg.
     */
    private static void minusZero() {
        commands("gamerule mobGriefing false", "time set 6000", "kill @e[type=minecraft:pig]",
                "kill @e[type=godzillaminusone:godzilla]", "kill @e[type=godzillaminusone:nuclear_cloud]");
        // (within 90 blocks: further off, the game neither runs them nor sends them to the client)
        camera("minus zero", new Vec3(735.5, -12.0, 92.0), new Vec3(735.5, -14.0, 0.5), 70);
        waitTicks(60);
        commands("fill 690 -60 0 690 -14 0 minecraft:gold_block", "fill 780 -60 0 780 33 0 minecraft:gold_block");
        server("minus zero: beside Minus One", level -> {
            for (Vec3 at : new Vec3[] {ONE_AT, ZERO_AT}) {
                GodzillaEntity g = (at == ONE_AT ? ModEntities.GODZILLA.get() : ModEntities.GODZILLA_MINUS_ZERO.get()).create(level);
                g.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                g.setYBodyRot(0.0F);
                g.setYHeadRot(0.0F);
                g.skipIntro();
                g.setBreathCooldown(20 * 600);
                g.setNoAi(true);
                level.addFreshEntity(g);
            }
        });
        waitTicks(60);
        check("minus zero: twice Minus One's size on the client", () -> {
            GodzillaEntity one = null;
            GodzillaMinusZeroEntity zero = find(GodzillaMinusZeroEntity.class);
            for (Entity e : mc().level == null ? List.<Entity>of() : mc().level.entitiesForRendering()) {
                if (e instanceof GodzillaEntity g && !(e instanceof GodzillaMinusZeroEntity) && g.isAlive()) {
                    one = g;
                }
            }
            if (one == null || zero == null) {
                return "missing: " + (one == null ? "Minus One " : "") + (zero == null ? "Minus Zero" : "");
            }
            log(String.format("minus zero: hitboxes %.1f x %.1f and %.1f x %.1f", one.getBbWidth(), one.getBbHeight(), zero.getBbWidth(), zero.getBbHeight()));
            return Math.abs(zero.getBbHeight() - 2.0F * one.getBbHeight()) < 1.0E-3 && zero.bodyScale() == 2.0 ? null : "not twice";
        });
        shot("minus_zero_beside_minus_one");
        camera("minus zero: close", new Vec3(760.5, -12.0, 85.0), new Vec3(760.5, -14.0, 0.5), 70);
        waitTicks(40);
        shot("minus_zero_front");
        camera("minus zero: side", new Vec3(845.0, -12.0, 0.5), new Vec3(760.5, -14.0, 0.5), 70);
        waitTicks(40);
        shot("minus_zero_side");
        camera("minus zero: behind", new Vec3(760.5, -12.0, -85.0), new Vec3(760.5, -14.0, 0.5), 70);
        waitTicks(40);
        shot("minus_zero_back");

        // Minus One brought down: one Minus Zero gets up where he lay, and Minus One is gone
        commands("kill @e[type=godzillaminusone:godzilla_minus_zero]");
        server("minus zero: Minus One brought down", level -> {
            for (GodzillaEntity g : level.getEntitiesOfClass(GodzillaEntity.class, new AABB(ONE_AT, ONE_AT).inflate(20.0))) {
                g.setNoAi(false);
                g.setEvolves(true);
                g.setDownedTicks(20);
                g.die(level.damageSources().generic());
            }
        });
        camera("minus zero: the evolution", new Vec3(700.5, -12.0, 85.0), new Vec3(700.5, -14.0, 0.5), 70);
        waitTicks(60);
        shot("minus_one_collapsed");
        until("minus zero: Minus Zero gets up where Minus One lay", () -> find(GodzillaMinusZeroEntity.class) != null, 400);
        checkServer("minus zero: exactly one, and no Minus One", level -> {
            List<GodzillaEntity> all = level.getEntitiesOfClass(GodzillaEntity.class, new AABB(ONE_AT, ONE_AT).inflate(200.0), Entity::isAlive);
            long zeros = all.stream().filter(g -> g instanceof GodzillaMinusZeroEntity).count();
            return zeros == 1 && all.size() == 1 ? null : zeros + " Minus Zeros, " + (all.size() - zeros) + " Minus Ones";
        });
        waitTicks(80);
        shot("minus_zero_rising");
        until("minus zero: up and about", () -> {
            GodzillaMinusZeroEntity z = find(GodzillaMinusZeroEntity.class);
            return z != null && z.isActive();
        }, 400);
        check("minus zero: still only one (client)", () -> {
            long n = mc().level == null ? 0 : java.util.stream.StreamSupport.stream(mc().level.entitiesForRendering().spliterator(), false)
                    .filter(e -> e instanceof GodzillaMinusZeroEntity && e.isAlive()).count();
            return n == 1 ? null : n + " Minus Zeros";
        });
        // his tail strike (before his breath, while the air is clear): the plate at the tip of his tail punched up and
        // glowing, then the whip
        // (facing south: the tip of his tail, wound up, is then ~108 blocks behind him, ~21 up: tools/render_preview.py)
        server("minus zero: facing south", level -> {
            for (GodzillaMinusZeroEntity z : level.getEntitiesOfClass(GodzillaMinusZeroEntity.class, new AABB(ONE_AT, ONE_AT).inflate(50.0))) {
                z.setNoAi(true);
                z.setYRot(0.0F);
                z.setYBodyRot(0.0F);
                z.setYHeadRot(0.0F);
            }
        });
        camera("minus zero: tail strike", new Vec3(758.0, -22.0, -78.0), new Vec3(700.5, -40.0, -104.0), 60);
        waitTicks(30);
        int[] strikeAt = {0};
        once("minus zero: mark his tail strike", () -> strikeAt[0] = now());
        server("minus zero: tail strike", level -> {
            for (GodzillaMinusZeroEntity z : level.getEntitiesOfClass(GodzillaMinusZeroEntity.class, new AABB(ONE_AT, ONE_AT).inflate(50.0))) {
                z.startTailStrike();
            }
        });
        until("minus zero: his tail tip glowing (client)", () -> {
            GodzillaMinusZeroEntity z = find(GodzillaMinusZeroEntity.class);
            return z != null && z.isTailGlowing();
        }, 60);
        waitTicks(12);
        shot("minus_zero_tail_strike_windup");
        waitTicks(26);
        shot("minus_zero_tail_strike_whip");
        check("minus zero: his tail strike's sounds: the fin punching up, then the tail swinging round", () -> {
            int up = heard(strikeAt[0], "godzilla_minus_zero.dorsal_fin_punch_up").size();
            int swing = heard(strikeAt[0], "godzilla_minus_zero.tail_swing").size();
            log("minus zero: tail strike sounds: " + heardSince(strikeAt[0]));
            return up >= 1 && swing >= 1 ? null : "fin punch up " + up + ", tail swing " + swing;
        });
        until("minus zero: his tail tip dark again (client)", () -> {
            GodzillaMinusZeroEntity z = find(GodzillaMinusZeroEntity.class);
            return z != null && !z.isTailGlowing();
        }, 80);
        waitTicks(40);

        // his whole breath (each shot logged with how far into his charge he was: the client's and the server's): his
        // charge and his beam; his first blast (in the air, 420 blocks off, where his beam ends: twice Minus One's
        // cloud); his middle row of plates' glow dimmed, then the rows either side of it lit and punched up; his second laser, and the second
        // blast; then the burns it leaves on his mouth, throat and chest. (Godzillas aren't hurt by the blasts.)
        int[] chargeAt = {0};
        once("minus zero: mark his charge", () -> chargeAt[0] = now());
        server("minus zero: charges his breath", level -> {
            for (GodzillaMinusZeroEntity z : level.getEntitiesOfClass(GodzillaMinusZeroEntity.class, new AABB(ONE_AT, ONE_AT).inflate(50.0))) {
                z.setNoAi(true);
                z.setYRot(0.0F);
                z.setYBodyRot(0.0F);
                z.setYHeadRot(0.0F);
                z.startCharging();
            }
        });
        Object[] beamCam = {new Vec3(775.0, -5.0, 70.0), new Vec3(700.5, 10.0, 40.0), 70};
        Object[] cloudCam = {new Vec3(730.0, 20.0, 90.0), new Vec3(700.5, 110.0, 423.0), 90};
        Object[] platesCam = {new Vec3(760.0, 10.0, -30.0), new Vec3(700.5, -5.0, -5.0), 70};
        Object[] wallCam = {new Vec3(782.0, 12.0, 52.0), new Vec3(700.5, 0.0, 55.0), 80};
        int second = GodzillaEntity.SURGE_TICK;
        zeroChargeShot("charging", 300, beamCam, false);
        zeroChargeShot("beam", 320, beamCam, false);
        // a wall 100 blocks ahead of him, across his beam: it stops there (drawn no further, its impact on the wall), not
        // through it; taken down before his blast goes off (out where his beam reaches, in the air)
        commands("fill 684 -60 100 717 30 100 minecraft:stone_bricks");
        zeroChargeShot("beam_stops_at_wall", 326, wallCam, true); // (well before 344: the wall must be gone by his blast)
        commands("fill 684 -60 100 717 30 100 minecraft:air");
        zeroChargeShot("first_blast", GodzillaEntity.CHARGE_BLAST_TICK + 10, cloudCam, true);
        zeroChargeShot("first_blast_cloud", GodzillaEntity.CHARGE_BLAST_TICK + 34, cloudCam, true);
        zeroChargeShot("glow_dimmed", GodzillaEntity.CHARGE_BLAST_TICK + 40, platesCam, false);
        zeroChargeShot("plates_relit", second - 6, platesCam, false);
        zeroChargeShot("second_laser", second + 8, beamCam, false);
        check("minus zero: his sounds: charged again, then his second beam firing", () -> {
            List<ShotsHarness.Heard> again = heard(chargeAt[0], "godzilla_minus_zero.charged_2nd_time");
            List<ShotsHarness.Heard> fire = heard(chargeAt[0], "godzilla_minus_zero.2nd_fire_beam");
            log("minus zero: charge sounds: " + heardSince(chargeAt[0]));
            return !again.isEmpty() && !fire.isEmpty() && again.get(0).tick() <= fire.get(0).tick() ? null
                    : "charged again " + again.size() + ", second beam " + fire.size();
        });
        zeroChargeShot("second_blast", GodzillaEntity.SECOND_BLAST_TICK + 10, cloudCam, true);
        zeroChargeShot("second_blast_cloud", GodzillaEntity.SECOND_BLAST_TICK + 34, cloudCam, true);
        // his beam over (the client's charge clock starts again from there): his burns at their worst
        camera("minus zero: burnt", new Vec3(700.5, 8.0, 78.0), new Vec3(700.5, 4.0, 20.0), 70);
        until("minus zero: his beam over", () -> {
            GodzillaMinusZeroEntity z = find(GodzillaMinusZeroEntity.class);
            return z != null && z.getChargeState() != GodzillaEntity.CHARGE_GLOWING;
        }, 300);
        waitTicks(6);
        shot("minus_zero_burnt_chest");
        until("minus zero: his charge over", () -> {
            GodzillaMinusZeroEntity z = find(GodzillaMinusZeroEntity.class);
            return z != null && !z.isCharging();
        }, 300);
        commands("kill @e[type=godzillaminusone:godzilla_minus_zero]");
        waitTicks(5);

        // his spawn egg
        server("minus zero: player with his egg", level -> {
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level, 650.5, -60.0, 100.5, 0.0F, 5.0F);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(ModItems.GODZILLA_MINUS_ZERO_SPAWN_EGG.get()));
            player.getInventory().selected = 0;
        });
        once("minus zero: hud on", () -> {
            view(null, null);
            mc().options.hideGui = false;
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            if (mc().player != null) {
                mc().player.getInventory().selected = 0;
            }
        });
        waitTicks(40);
        shot("minus_zero_spawn_egg_hotbar");
        once("minus zero: hud off", () -> mc().options.hideGui = true);
        commands("fill 690 -60 0 690 -14 0 minecraft:air", "fill 780 -60 0 780 33 0 minecraft:air");
        server("minus zero: done", level -> serverPlayer(level).getInventory().clearContent());
    }

    // ------------------------------------------------------------------ King Ghidorah

    private static final Vec3 GHIDORAH_AT = new Vec3(850.5, -60.0, 0.5);

    @Nullable
    private static KingGhidorahEntity serverGhidorah(ServerLevel level) {
        List<KingGhidorahEntity> all = level.getEntitiesOfClass(KingGhidorahEntity.class, new AABB(GHIDORAH_AT, GHIDORAH_AT).inflate(300.0));
        return all.isEmpty() ? null : all.get(0);
    }

    /** Waits until his action is {@code action}, {@code ticks} into it by the server's clock (as the client sees it). */
    private static void ghidorahAt(String label, int action, int ticks) {
        until("ghidorah: " + label, () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            return g != null && g.getAction() == action && g.getActionTicks() >= ticks;
        }, 400);
    }

    /** Which of his footsteps (1, 2 or 3) a sound heard was. */
    private static int stepNumber(ShotsHarness.Heard h) {
        String path = h.sound().getPath();
        return path.charAt(path.length() - 1) - '0';
    }

    private static void ghidorahIdle(String label) {
        until("ghidorah: " + label, () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            return g != null && g.getAction() == KingGhidorahEntity.ACTION_NONE;
        }, 500);
    }

    private static void onGhidorah(String label, java.util.function.Consumer<KingGhidorahEntity> action) {
        server("ghidorah: " + label, level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            if (g != null) {
                action.accept(g);
            }
        });
    }

    /**
     * King Ghidorah: summoned (as his egg or /summon do: he rises out of the ground), his entrance shot as it goes,
     * the storm after it, beside Minus Zero, his stance, and each of his attacks as it happens: bite, stomp, tail whip,
     * gravity beams vaporizing a building, his lift, his grab and his retreat; checked where the game has him.
     */
    private static void kingGhidorah() {
        commands("gamerule mobGriefing false", "time set 6000", "weather clear", "kill @e[type=godzillaminusone:godzilla_minus_zero]",
                "kill @e[type=godzillaminusone:godzilla]", "kill @e[type=godzillaminusone:king_ghidorah]", "kill @e[type=minecraft:pig]");
        camera("ghidorah: the ground", new Vec3(902.0, -44.0, 92.0), new Vec3(850.5, -42.0, 18.0), 75);
        waitTicks(60);
        server("ghidorah: Minus Zero, for his size", level -> {
            GodzillaEntity zero = ModEntities.GODZILLA_MINUS_ZERO.get().create(level);
            zero.moveTo(ZERO_AT.x, ZERO_AT.y, ZERO_AT.z, 0.0F, 0.0F);
            zero.setYBodyRot(0.0F);
            zero.setYHeadRot(0.0F);
            zero.skipIntro();
            zero.setBreathCooldown(20 * 600);
            zero.setNoAi(true);
            level.addFreshEntity(zero);
        });
        int[] since = {0};
        once("ghidorah: mark the entrance", () -> since[0] = now());
        // (/summon without NBT, as his egg: a spawn, so he rises out of the ground)
        commands("summon godzillaminusone:king_ghidorah 850.5 -60 0.5");
        ghidorahAt("the ground cracking (1.5 s)", KingGhidorahEntity.ACTION_ENTRANCE, 30);
        check("ghidorah: rising, by the server's clock", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            return g != null && g.inEntrance() ? null : "not in his entrance";
        });
        shot("king_ghidorah_entrance_cracks");
        ghidorahAt("his first head (5 s)", KingGhidorahEntity.ACTION_ENTRANCE, 100);
        shot("king_ghidorah_entrance_first_head");
        ghidorahAt("his three heads (7.6 s)", KingGhidorahEntity.ACTION_ENTRANCE, 152);
        shot("king_ghidorah_entrance_heads");
        ghidorahAt("climbing out (9.4 s)", KingGhidorahEntity.ACTION_ENTRANCE, 188);
        shot("king_ghidorah_entrance_climbing");
        ghidorahAt("his wings spreading (11.6 s)", KingGhidorahEntity.ACTION_ENTRANCE, 232);
        shot("king_ghidorah_entrance_wings");
        ghidorahAt("his roar (13 s)", KingGhidorahEntity.ACTION_ENTRANCE, 260);
        shot("king_ghidorah_entrance_roar");
        checkServer("ghidorah: the server and the client agree where his entrance is", level -> {
            KingGhidorahEntity server = serverGhidorah(level);
            KingGhidorahEntity client = find(KingGhidorahEntity.class);
            if (server == null || client == null) {
                return "missing";
            }
            log(String.format("ghidorah: entrance at tick %d on the server, %d on the client (began at game time %d, %d on the client)",
                    server.getActionTicks(), client.getActionTicks(), server.getActionStart(), client.getActionStart()));
            // (his entrance is had by when it began, synced exactly; the client's game time only follows the server's, a
            // few ticks behind when it's drawing slowly, put right every second by the game itself)
            return server.getActionStart() != client.getActionStart() ? "his entrance began at different times"
                    : Math.abs(server.getActionTicks() - client.getActionTicks()) <= 10 ? null : "out of step";
        });
        long[] ended = {-1};
        untilServer("ghidorah: his entrance over", level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            if (g != null && !g.inEntrance() && ended[0] < 0) {
                // (the tick it ended, which this may notice a tick or two after: his storm is due a set time after it)
                ended[0] = g.stormAt() >= 0 ? g.stormAt() - Config.GHIDORAH_STORM_DELAY_TICKS.get() : level.getGameTime();
                g.setNoAi(true);          // (Minus Zero stands there for his size, not to be fought)
                g.setTarget(null);
            }
            return ended[0] >= 0;
        }, 200);
        check("ghidorah: the entrance's sounds heard", () -> {
            log("ghidorah: sounds of his entrance: " + heardSince(since[0]));
            return heard(since[0], "king_ghidorah.rumble").isEmpty() ? "no rumble"
                    : heard(since[0], "king_ghidorah.earth_crack").isEmpty() ? "no cracking"
                    : heard(since[0], "king_ghidorah.wing_spread").isEmpty() ? "no wings"
                    : heard(since[0], "king_ghidorah.entrance_roar").isEmpty() ? "no entrance roar" : null;
        });
        // his weather: no rain, the sky overcast (coming on over 5 s), lightning twice as often as a thunderstorm's
        // (him still meanwhile: left to himself through all this he turns about, and the shots after expect him as he rose)
        onGhidorah("no AI while his weather comes", g -> g.setNoAi(true));
        int[] strikesBefore = {0};
        untilServer("ghidorah: his weather", level -> {
            strikesBefore[0] = com.npjt25.godzillaminusone.entity.GhidorahWeather.strikes();
            return com.npjt25.godzillaminusone.entity.GhidorahWeather.active(level);
        }, 200);
        checkServer("ghidorah: his weather three seconds after his entrance", level -> {
            long after = level.getGameTime() - ended[0];
            log("ghidorah: his weather " + after + " ticks after his entrance ended");
            return after >= Config.GHIDORAH_STORM_DELAY_TICKS.get() - 1 && after <= Config.GHIDORAH_STORM_DELAY_TICKS.get() + 6
                    ? null : "his weather came " + after + " ticks after";
        });
        camera("ghidorah: his weather", new Vec3(920.0, 10.0, 120.0), new Vec3(850.5, 30.0, 0.5), 85);
        until("ghidorah: the sky overcast", () -> com.npjt25.godzillaminusone.client.GhidorahSky.factor(1.0F) >= 0.99F, 160);
        untilServer("ghidorah: lightning from it", level -> com.npjt25.godzillaminusone.entity.GhidorahWeather.strikes() > strikesBefore[0], 600);
        checkServer("ghidorah: overcast, no rain", level -> {
            net.minecraft.client.multiplayer.ClientLevel client = net.minecraft.client.Minecraft.getInstance().level;
            float rain = client == null ? -1.0F : client.getRainLevel(1.0F);
            float dark = client == null ? -1.0F : client.getSkyDarken(1.0F);
            log(String.format(java.util.Locale.ROOT, "ghidorah: his weather: raining %b (client rain %.2f), sky darkened to %.2f, %d strikes, until %d",
                    level.isRaining(), rain, dark, com.npjt25.godzillaminusone.entity.GhidorahWeather.strikes() - strikesBefore[0],
                    com.npjt25.godzillaminusone.entity.GhidorahWeather.until(level)));
            return !level.isRaining() && rain == 0.0F && com.npjt25.godzillaminusone.client.GhidorahSky.active() ? null
                    : "raining " + level.isRaining() + ", client rain " + rain;
        });
        shot("king_ghidorah_storm");
        server("ghidorah: his weather over (for the shots after)", com.npjt25.godzillaminusone.entity.GhidorahWeather::stop);
        until("ghidorah: the sky clear again", () -> com.npjt25.godzillaminusone.client.GhidorahSky.factor(1.0F) <= 0.0F, 160);
        commands("weather clear");
        onGhidorah("no AI for the shots, facing as he rose", g -> {
            g.setNoAi(true);
            g.setYRot(0.0F);
            g.setYBodyRot(0.0F);
            g.setYHeadRot(0.0F);
        });
        camera("ghidorah", new Vec3(805.5, -12.0, 88.0), new Vec3(805.5, -16.0, 0.5), 80);
        waitTicks(40);
        check("ghidorah: Minus Zero's hitbox, on the client", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            GodzillaMinusZeroEntity zero = find(GodzillaMinusZeroEntity.class);
            if (g == null || zero == null) {
                return "missing: " + (g == null ? "King Ghidorah " : "") + (zero == null ? "Minus Zero" : "");
            }
            log(String.format("ghidorah: hitbox %.1f x %.1f, Minus Zero's %.1f x %.1f; drawn %.3fx, %.2f blocks tall", g.getBbWidth(),
                    g.getBbHeight(), zero.getBbWidth(), zero.getBbHeight(), KingGhidorahEntity.RENDER_SCALE,
                    KingGhidorahEntity.MODEL_HEIGHT * KingGhidorahEntity.UNIT));
            return g.getBbHeight() == zero.getBbHeight() && g.getBbWidth() == zero.getBbWidth() ? null : "not Minus Zero's";
        });
        shot("king_ghidorah_beside_minus_zero");
        commands("kill @e[type=godzillaminusone:godzilla_minus_zero]");
        camera("ghidorah: front", new Vec3(885.0, 5.0, 68.0), new Vec3(850.5, -12.0, 0.5), 75);
        waitTicks(40);
        shot("king_ghidorah_front");
        camera("ghidorah: side", new Vec3(935.0, -10.0, 10.0), new Vec3(850.5, -12.0, 10.0), 75);
        waitTicks(40);
        shot("king_ghidorah_side");
        camera("ghidorah: heads", new Vec3(882.0, 26.0, 70.0), new Vec3(850.5, 12.0, 30.0), 60);
        waitTicks(40);
        shot("king_ghidorah_heads");

        // walking (south, with nothing to hunt)
        int[] walkSince = {0};
        once("ghidorah: mark the walk", () -> walkSince[0] = now());
        onGhidorah("walk south", g -> {
            g.setNoAi(false);
            g.walkTo(new Vec3(GHIDORAH_AT.x, GHIDORAH_AT.y, 60.5));
        });
        camera("ghidorah: walking", new Vec3(925.0, -5.0, 40.0), new Vec3(850.5, -18.0, 30.0), 80);
        waitTicks(40);
        Vec3[] paceFrom = {null};
        once("ghidorah: where he is (on the client)", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            paceFrom[0] = g == null ? null : g.position();
        });
        waitTicks(10);
        check("ghidorah: his walk keeps pace with him (his feet don't slide)", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            if (g == null || paceFrom[0] == null) {
                return "gone";
            }
            double moved = g.position().subtract(paceFrom[0]).horizontalDistance() / 10.0;
            log(String.format("ghidorah: walking %.2f blocks a tick on the client, his walk at %.2f its own pace", moved, g.walkPace()));
            return Math.abs(g.walkPace() - Math.max(0.25, moved / KingGhidorahEntity.WALK_BLOCKS_PER_TICK)) < 0.25 ? null
                    : "his walk at " + g.walkPace() + " for " + moved;
        });
        shot("king_ghidorah_walk_1");
        waitTicks(12);
        shot("king_ghidorah_walk_2");
        double[] walkedTo = {GHIDORAH_AT.z};
        checkServer("ghidorah: he walked", level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            if (g != null) {
                walkedTo[0] = g.getZ();
            }
            log(String.format("ghidorah: walked to z %.1f", g == null ? Double.NaN : g.getZ()));
            return g != null && g.getZ() > GHIDORAH_AT.z + 10.0 ? null : "he hasn't moved: " + (g == null ? "gone" : g.position());
        });
        // (on a little further, for a few more footfalls; then back where the shots after expect him)
        waitTicks(80);
        check("ghidorah: a footstep each time a foot came down, taking turns: 1, 2, 3, 1, ...", () -> {
            List<ShotsHarness.Heard> steps = new ArrayList<>();
            for (int k = 1; k <= 3; k++) {
                steps.addAll(heard(walkSince[0], "king_ghidorah.footstep_" + k));
            }
            steps.sort(java.util.Comparator.comparingInt(ShotsHarness.Heard::tick));
            StringBuilder order = new StringBuilder();
            for (ShotsHarness.Heard h : steps) {
                order.append(order.length() == 0 ? "" : ", ").append(stepNumber(h)).append(" @").append(h.tick() - walkSince[0]);
            }
            log("ghidorah: footsteps as he walked: " + order);
            if (steps.size() < 3) {
                return "only " + steps.size() + " footsteps: " + order;
            }
            for (int i = 1; i < steps.size(); i++) {
                if (stepNumber(steps.get(i)) != stepNumber(steps.get(i - 1)) % 3 + 1) {
                    return "out of turn: " + order;
                }
            }
            return null;
        });
        onGhidorah("stop", g -> {
            g.walkTo(null);
            g.setNoAi(true);
            g.setDeltaMovement(Vec3.ZERO);
            g.moveTo(g.getX(), g.getY(), walkedTo[0], g.getYRot(), g.getXRot());
        });
        waitTicks(30);

        // a bite (his right head's), a stomp, his tails' whip
        // (cameras within about 110 blocks of him across the ground: at render distance 8 the game stops showing a mob
        // 128 blocks off, and he's left wherever his walk ended)
        camera("ghidorah: close", new Vec3(908.0, 10.0, 98.0), new Vec3(850.5, -15.0, 40.0), 75);
        int[] attackSince = {0};
        once("ghidorah: mark the attacks", () -> attackSince[0] = now());
        onGhidorah("bite", g -> g.startBite(KingGhidorahEntity.Head.RIGHT));
        ghidorahAt("his jaws snapping shut", KingGhidorahEntity.ACTION_BITE + KingGhidorahEntity.Head.RIGHT.ordinal(), KingGhidorahEntity.BITE_SNAP_AT - 2);
        checkServer("ghidorah: where the bite is, and who's in earshot", level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            ServerPlayer player = serverPlayer(level);
            if (g == null || player == null) {
                return "missing";
            }
            Vec3 jaws = g.locator(KingGhidorahEntity.Head.RIGHT.biteMouth);
            log(String.format("ghidorah: on the server, biting (action %d, tick %d), him at %s, jaws at %s; the player at %s, %.1f from them",
                    g.getAction(), g.getActionTicks(), g.position(), jaws, player.position(), player.position().distanceTo(jaws)));
            return null;
        });
        shot("king_ghidorah_bite");
        ghidorahIdle("bite over");
        onGhidorah("stomp", g -> g.startStomp());
        ghidorahAt("his foot up", KingGhidorahEntity.ACTION_STOMP, 12);
        shot("king_ghidorah_stomp_raised");
        ghidorahAt("his foot down", KingGhidorahEntity.ACTION_STOMP, KingGhidorahEntity.STOMP_HIT_AT + 1);
        shot("king_ghidorah_stomp");
        ghidorahIdle("stomp over");
        camera("ghidorah: from above", new Vec3(890.0, 40.0, 110.0), new Vec3(850.5, -40.0, 50.0), 80);
        onGhidorah("tail whip", g -> g.startWhip());
        ghidorahAt("his tails swinging", KingGhidorahEntity.ACTION_WHIP, KingGhidorahEntity.WHIP_HIT_AT);
        shot("king_ghidorah_tail_whip");
        ghidorahIdle("whip over");
        check("ghidorah: his bite and his stomp heard (the bite, and Godzilla's stomp)", () -> {
            log("ghidorah: sounds of his attacks: " + heardSince(attackSince[0]));
            return heard(attackSince[0], "king_ghidorah.bite").isEmpty() ? "no bite"
                    : heard(attackSince[0], "godzilla.stomp").isEmpty() ? "no stomp (godzilla.stomp)" : null;
        });

        // his gravity beams, at a building ahead of him: they bore through it and vaporize everything 12 round
        Vec3[] target = {null};
        server("ghidorah: a building ahead of him", level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            if (g == null) {
                return;
            }
            Vec3 c = g.locator(new Vec3(0.0, 0.0, 70.0));
            target[0] = new Vec3(c.x, -55.0, c.z);
            int x = Mth.floor(c.x);
            int z = Mth.floor(c.z);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
                    String.format("fill %d -60 %d %d -44 %d minecraft:stone_bricks hollow", x - 8, z - 8, x + 8, z + 8));
        });
        commands("gamerule mobGriefing true");
        int[] beamSince = {0};
        once("ghidorah: mark the beams", () -> beamSince[0] = now());
        onGhidorah("beams", g -> g.startBeam(target[0]));
        camera("ghidorah: beams", new Vec3(930.0, 0.0, 80.0), new Vec3(850.5, -25.0, 70.0), 85);
        ghidorahAt("bracing", KingGhidorahEntity.ACTION_BEAM, 16);
        shot("king_ghidorah_beam_brace");
        ghidorahAt("all three firing", KingGhidorahEntity.ACTION_BEAM, 40);
        check("ghidorah: his beams come from his mouths (bones mouth_c / _r / _l, where the game draws them)", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            if (g == null) {
                return "gone";
            }
            StringBuilder problems = new StringBuilder();
            for (KingGhidorahEntity.Head head : KingGhidorahEntity.Head.values()) {
                Vec3 m = g.drawnMouths[head.ordinal()];
                if (m == null) {
                    problems.append(head).append(": not drawn; ");
                    continue;
                }
                Vec3 fwd = Vec3.directionFromRotation(0.0F, g.yBodyRot);
                double ahead = m.dot(fwd);
                log(String.format("ghidorah: %s mouth drawn %.1f up, %.1f ahead (the server's: %.1f up, %.1f ahead); firing %s", head, m.y,
                        ahead, g.beamMouth(head).y - g.getY(), g.beamMouth(head).subtract(g.position()).dot(fwd), g.isFiring(head)));
                if (m.y < 40.0 || m.y > 90.0 || ahead < 20.0 || ahead > 65.0 || !g.isFiring(head)) {
                    problems.append(head).append(" at ").append(m).append("; ");
                }
            }
            return problems.length() == 0 ? null : problems.toString();
        });
        shot("king_ghidorah_beam_fire");
        camera("ghidorah: beams, head on", new Vec3(895.0, 15.0, 130.0), new Vec3(850.5, -25.0, 60.0), 75);
        waitTicks(6);
        shot("king_ghidorah_beam_fire_front");
        ghidorahIdle("beams over");
        commands("gamerule mobGriefing false");
        checkServer("ghidorah: the building where his beams struck vaporized", level -> {
            if (target[0] == null) {
                return "no building";
            }
            BlockPos c = BlockPos.containing(target[0]);
            int left = 0;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-6, -5, -6), c.offset(6, 6, 6))) {
                if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS)) {
                    left++;
                }
            }
            log("ghidorah: stone bricks left within 6 of where his beams struck: " + left);
            return left == 0 ? null : left + " left";
        });
        check("ghidorah: his beams heard (once, for all three)", () -> {
            int n = heard(beamSince[0], "king_ghidorah.gravity_beam").size();
            return n == 1 ? null : "king_ghidorah.gravity_beam heard " + n + " times";
        });
        camera("ghidorah: the crater", new Vec3(900.0, 10.0, 120.0), new Vec3(850.5, -55.0, 70.0), 70);
        waitTicks(20);
        shot("king_ghidorah_beam_after");

        // his lift: a pig hauled into the air by his beams
        Pig[] lifted = {null};
        server("ghidorah: a pig to lift", level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            if (g == null) {
                return;
            }
            Vec3 at = g.locator(new Vec3(-12.0, 0.0, 50.0));
            Pig pig = EntityType.PIG.create(level);
            pig.moveTo(at.x, -60.0, at.z);
            pig.setInvulnerable(true);
            level.addFreshEntity(pig);
            lifted[0] = pig;
            g.startLift(pig);
        });
        camera("ghidorah: lift", new Vec3(925.0, 0.0, 70.0), new Vec3(840.5, -25.0, 50.0), 85);
        ghidorahAt("lifting", KingGhidorahEntity.ACTION_LIFT, 55);
        checkServer("ghidorah: the pig hauled up", level -> {
            Pig pig = lifted[0];
            log("ghidorah: the lifted pig is " + (pig == null ? -1 : pig.getY() + 60.0) + " blocks up");
            return pig != null && pig.getY() > -60.0 + 15.0 ? null : "not lifted";
        });
        shot("king_ghidorah_lift");
        ghidorahIdle("lift over");

        // his grab: off the ground, a pig seized in his talons, carried up, and slammed down
        Pig[] grabbed = {null};
        server("ghidorah: a pig to grab", level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            if (g == null) {
                return;
            }
            Vec3 at = g.locator(new Vec3(0.0, 0.0, 35.0));
            Pig pig = EntityType.PIG.create(level);
            pig.moveTo(at.x, -60.0, at.z);
            level.addFreshEntity(pig);
            grabbed[0] = pig;
            g.setNoAi(false);
            g.startGrab(pig);
        });
        camera("ghidorah: flying", new Vec3(925.0, 15.0, 95.0), new Vec3(850.5, -10.0, 40.0), 85);
        until("ghidorah: in the air", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            return g != null && g.isFlying() && g.getY() > -45.0;
        }, 200);
        shot("king_ghidorah_flying");
        until("ghidorah: carrying it up", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            return g != null && g.isCarrying() && g.getY() > -30.0;
        }, 300);
        shot("king_ghidorah_carrying");
        untilServer("ghidorah: slammed it down and landed", level -> {
            KingGhidorahEntity g = serverGhidorah(level);
            return g != null && g.hasSlammed() && !g.isFlying();
        }, 400);
        shot("king_ghidorah_after_slam");
        check("ghidorah: his wings heard in flight", () -> heard(attackSince[0], "king_ghidorah.flying").isEmpty() ? "no king_ghidorah.flying" : null);
        onGhidorah("no AI again", g -> g.setNoAi(true));

        // his retreat: his health low, he flies off
        onGhidorah("health low: retreat", g -> {
            g.setNoAi(false);
            g.setHealth(g.getMaxHealth() * 0.1F);
            g.startRetreat(new Vec3(g.getX(), g.getY(), g.getZ() - 40.0));
        });
        camera("ghidorah: retreating", new Vec3(930.0, 20.0, 60.0), new Vec3(850.5, 10.0, 80.0), 85);
        until("ghidorah: flying off", () -> {
            KingGhidorahEntity g = find(KingGhidorahEntity.class);
            return g != null && g.isFlying() && g.getY() > -30.0;
        }, 200);
        shot("king_ghidorah_retreat");
        commands("kill @e[type=godzillaminusone:king_ghidorah]", "kill @e[type=minecraft:pig]", "weather clear");
        waitTicks(5);
    }

    /** Where King Ghidorah digs up the ground (kingGhidorahGround): a patch of the flat world of its own. */
    private static final Vec3 DIG_AT = new Vec3(850.5, -60.0, 400.5);

    @Nullable
    private static KingGhidorahEntity digGhidorah(ServerLevel level) {
        List<KingGhidorahEntity> all = level.getEntitiesOfClass(KingGhidorahEntity.class, new AABB(DIG_AT, DIG_AT).inflate(250.0));
        return all.isEmpty() ? null : all.get(0);
    }

    /** How many of the flat world's grass blocks (y -61) are gone between (x0, z0) and (x1, z1). */
    private static int grassGone(ServerLevel level, double x0, double z0, double x1, double z1) {
        int gone = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(x0, -61, z0), BlockPos.containing(x1, -61, z1))) {
            if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
                gone++;
            }
        }
        return gone;
    }

    /**
     * King Ghidorah and the ground (mobGriefing on, in his own patch of it, his walk breaking the ground below Y 60): the
     * hole he hauls himself out of at his entrance, his footprints as he walks up out of it, and the ditch he drives into
     * the ground as he comes down out of the sky. (The flat world's ground is three blocks over bedrock: as deep as his
     * hole and ditch go here.)
     */
    private static void kingGhidorahGround() {
        commands("gamerule mobGriefing true", "weather clear", "kill @e[type=godzillaminusone:king_ghidorah]", "kill @e[type=minecraft:pig]");
        camera("ghidorah ground: his hole", new Vec3(DIG_AT.x + 72.0, -8.0, DIG_AT.z + 72.0), new Vec3(DIG_AT.x, -58.0, DIG_AT.z), 80);
        waitTicks(20);
        commands(String.format(java.util.Locale.ROOT, "summon godzillaminusone:king_ghidorah %.1f -60 %.1f", DIG_AT.x, DIG_AT.z));
        untilServer("ghidorah ground: rising", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            if (g != null && g.walkFloorY() != Integer.MIN_VALUE) {
                g.setWalkFloorY(Integer.MIN_VALUE);       // (his footprints in the flat world's ground, below Y 60)
            }
            return g != null && g.inEntrance();
        }, 60);
        untilServer("ghidorah ground: his entrance over", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            return g != null && !g.inEntrance();
        }, 400);
        waitTicks(10);
        checkServer("ghidorah ground: the hole he came out of", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            if (g == null) {
                return "gone";
            }
            double r = KingGhidorahEntity.ENTRANCE_HOLE_FLOOR - 2.0;
            int gone = grassGone(level, DIG_AT.x - r, DIG_AT.z - r, DIG_AT.x + r, DIG_AT.z + r);
            int all = (int) Math.pow(Math.floor(DIG_AT.x + r) - Math.floor(DIG_AT.x - r) + 1, 2);
            int beyond = grassGone(level, DIG_AT.x + 62.0, DIG_AT.z - 5.0, DIG_AT.x + 67.0, DIG_AT.z + 5.0);
            log(String.format(java.util.Locale.ROOT, "ghidorah ground: his hole: %d of %d of the ground in its middle dug out, %d beyond it; he stands at y %.1f, %d ditches",
                    gone, all, beyond, g.getY(), g.ditches()));
            return gone > all * 0.9 && beyond == 0 && g.ditches() == 1 && g.getY() < -61.5 ? null
                    : "no hole: " + gone + " of " + all + ", " + beyond + " beyond, y " + g.getY() + ", " + g.ditches() + " ditches";
        });
        shot("king_ghidorah_entrance_hole");
        untilServer("ghidorah ground: his weather", com.npjt25.godzillaminusone.entity.GhidorahWeather::active, 200);
        server("ghidorah ground: his weather over", com.npjt25.godzillaminusone.entity.GhidorahWeather::stop);
        commands("weather clear");

        // walking out of it: a footprint where each foot comes down
        int[] prints = {0};
        server("ghidorah ground: walk out of it", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            if (g != null) {
                prints[0] = g.walkPrints();
                g.setNoAi(false);
                g.walkTo(new Vec3(DIG_AT.x, -60.0, DIG_AT.z + 135.0));
            }
        });
        // (from behind him and above: his trail of footprints between his hole and him, him past it)
        camera("ghidorah ground: footprints", new Vec3(DIG_AT.x + 30.0, -22.0, DIG_AT.z + 35.0), new Vec3(DIG_AT.x, -61.0, DIG_AT.z + 87.0), 75);
        untilServer("ghidorah ground: out of his hole", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            return g == null || g.getZ() > DIG_AT.z + 130.0;
        }, 800);
        waitTicks(20);
        checkServer("ghidorah ground: his footprints", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            if (g == null) {
                return "gone";
            }
            int walked = g.walkPrints() - prints[0];
            // (beyond his hole and its rim, where he walked: a footprint's ground sunk where each foot came down)
            int gone = grassGone(level, DIG_AT.x - 16.0, DIG_AT.z + 62.0, DIG_AT.x + 16.0, DIG_AT.z + 120.0);
            log(String.format(java.util.Locale.ROOT, "ghidorah ground: %d footprints pressed walking %.1f blocks, %d of the ground beyond his hole sunk",
                    walked, g.getZ() - DIG_AT.z, gone));
            return walked >= 6 && gone > 80 ? null : walked + " footprints, " + gone + " blocks sunk";
        });
        shot("king_ghidorah_footprints");

        // his landing: up after a pig, and down again with it, into a ditch
        Pig[] grabbed = {null};
        Vec3 pigAt = new Vec3(DIG_AT.x, -60.0, DIG_AT.z + 170.0);
        server("ghidorah ground: a pig to grab", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            if (g == null) {
                return;
            }
            g.walkTo(null);
            Pig pig = EntityType.PIG.create(level);
            pig.moveTo(pigAt.x, pigAt.y, pigAt.z);
            level.addFreshEntity(pig);
            grabbed[0] = pig;
            g.startGrab(pig);
        });
        camera("ghidorah ground: his ditch", new Vec3(DIG_AT.x + 65.0, -15.0, DIG_AT.z + 220.0), new Vec3(DIG_AT.x, -60.0, DIG_AT.z + 170.0), 80);
        double[] landed = {0.0, 0.0};
        untilServer("ghidorah ground: landed", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            if (g != null && g.hasSlammed() && !g.isFlying()) {
                landed[0] = g.getX();
                landed[1] = g.getZ();
                return true;
            }
            return g == null;
        }, 500);
        waitTicks(15);
        checkServer("ghidorah ground: the ditch he landed in", level -> {
            KingGhidorahEntity g = digGhidorah(level);
            if (g == null) {
                return "gone";
            }
            double r = KingGhidorahEntity.LANDING_DITCH_FLOOR - 2.0;
            int gone = grassGone(level, landed[0] - r, landed[1] - r, landed[0] + r, landed[1] + r);
            int all = (int) Math.pow(Math.floor(landed[0] + r) - Math.floor(landed[0] - r) + 1, 2);
            log(String.format(java.util.Locale.ROOT, "ghidorah ground: his ditch at (%.1f, %.1f): %d of %d of the ground in its middle dug out; he stands at y %.1f, %d ditches",
                    landed[0], landed[1], gone, all, g.getY(), g.ditches()));
            return gone > all * 0.9 && g.ditches() == 2 && g.getY() < -61.5 ? null
                    : "no ditch: " + gone + " of " + all + ", y " + g.getY() + ", " + g.ditches() + " ditches";
        });
        shot("king_ghidorah_landing_ditch");
        commands("kill @e[type=godzillaminusone:king_ghidorah]", "kill @e[type=minecraft:pig]", "weather clear", "gamerule mobGriefing false");
        waitTicks(5);
    }

    /** The camera Minus Zero's charge is being shot from (zeroChargeShot moves it only when a shot wants another). */
    @Nullable
    private static Object[] zeroCam;

    /**
     * A shot of Minus Zero's charge, {@code at} ticks into it, from {@code cam} ({eye, target, fov}), logged with where his
     * charge is on the server and the client. {@code serverClock}: by the server's count (what the server sets going: a
     * blast, the wall coming down before one), else by the client's (his animation's: the client may be running behind),
     * or once his beam is over there.
     */
    private static void zeroChargeShot(String name, int at, Object[] cam, boolean serverClock) {
        if (cam != zeroCam) {
            zeroCam = cam;
            camera("minus zero: " + name, (Vec3) cam[0], (Vec3) cam[1], (Integer) cam[2]);
        }
        String label = "minus zero: " + name + " (charge tick " + at + (serverClock ? ", the server's)" : ")");
        if (serverClock) {
            untilServer(label, level -> {
                List<GodzillaMinusZeroEntity> zs = level.getEntitiesOfClass(GodzillaMinusZeroEntity.class, new AABB(ONE_AT, ONE_AT).inflate(50.0));
                return zs.isEmpty() || zs.get(0).getChargeTicks() >= at || zs.get(0).getChargeTicks() < 0;
            }, 700);
        } else {
            until(label, () -> {
                GodzillaMinusZeroEntity z = find(GodzillaMinusZeroEntity.class);
                return z != null && (z.getChargeState() != GodzillaEntity.CHARGE_GLOWING
                        || z.getChargeSeconds(0.0F) * 20.0F + GodzillaEntity.ANIM_TRANSITION >= at);
            }, 700);
        }
        checkServer("minus zero: " + name + ", where his charge is", level -> {
            List<GodzillaMinusZeroEntity> zs = level.getEntitiesOfClass(GodzillaMinusZeroEntity.class, new AABB(ONE_AT, ONE_AT).inflate(50.0));
            GodzillaMinusZeroEntity client = find(GodzillaMinusZeroEntity.class);
            log(String.format("minus zero: %s: server charge tick %d, client charge seconds %.2f, beam drawn %.2f of its length", name,
                    zs.isEmpty() ? -1 : zs.get(0).getChargeTicks(), client == null ? -1.0F : client.getChargeSeconds(0.0F),
                    client == null ? -1.0F : client.getBeamReach(1.0F)));
            return null;
        });
        shot("minus_zero_" + name);
    }

    // ------------------------------------------------------------------ the Shinsei Maru

    @Nullable
    private static ShinseiMaruEntity serverBoat(ServerLevel level) {
        List<ShinseiMaruEntity> boats = level.getEntitiesOfClass(ShinseiMaruEntity.class, new AABB(-140, -70, -60, 0, -40, 60));
        return boats.isEmpty() ? null : boats.get(0);
    }

    /** A point (blocks: to starboard, up from the keel, forward) in her frame. */
    private static Vec3 local(ShinseiMaruEntity boat, Vec3 p) {
        Vec3 rel = p.subtract(boat.position());
        Vec3 forward = Vec3.directionFromRotation(0.0F, boat.getYRot());
        Vec3 starboard = new Vec3(-forward.z, 0.0, forward.x);
        return new Vec3(rel.dot(starboard), rel.y, rel.dot(forward));
    }

    /** Looks from a point in her frame (to starboard, up from the keel, forward) at another, the player left where they are. */
    private static void viewOnBoat(String label, Vec3 eye, Vec3 target) {
        viewOnBoat(label, eye, target, 10);
    }

    /** The same, the picture taken {@code ticks} later (she may be moving: a tick, and she's still in the frame). */
    private static void viewOnBoat(String label, Vec3 eye, Vec3 target, int ticks) {
        once(label, () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            if (boat != null) {
                view(boat.at(eye), boat.at(target));
                mc().options.fov().set(60);
            }
        });
        waitTicks(ticks);
    }

    /** Puts the player (creative, walking) on her at a point in her frame, facing {@code yawOffset} from her heading. */
    private static void onBoat(String label, double side, double up, double forward, float yawOffset) {
        server(label, level -> {
            ShinseiMaruEntity boat = serverBoat(level);
            ServerPlayer player = serverPlayer(level);
            player.stopRiding();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            Vec3 at = boat.at(new Vec3(side, up, forward));
            player.teleportTo(level, at.x, at.y, at.z, boat.getYRot() + yawOffset, 15.0F);
        });
    }

    /** Watches the player stay on a deck {@code top} above her keel, within her sides, out of the water. */
    private static void stayOnDeck(String name, int ticks, double top) {
        double[] lowest = {Double.MAX_VALUE};
        double[] travelled = {0.0};
        Vec3[] last = {null};
        float[] turned = {0.0F};
        float[] lastYaw = {Float.NaN};
        during(name, ticks, () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            LocalPlayer player = mc().player;
            if (boat == null || player == null) {
                return "no boat";
            }
            if (last[0] != null) {
                travelled[0] += boat.position().distanceTo(last[0]);
            }
            last[0] = boat.position();
            if (!Float.isNaN(lastYaw[0])) {
                turned[0] += Math.abs(Mth.wrapDegrees(boat.getYRot() - lastYaw[0]));
            }
            lastYaw[0] = boat.getYRot();
            Vec3 l = local(boat, player.position());
            lowest[0] = Math.min(lowest[0], l.y - top);
            if (player.isInWater()) {
                return "in the water";
            }
            if (l.y < top - 0.35) {
                return String.format("fell to %.2f above her keel (deck at %.2f)", l.y, top);
            }
            if (Math.abs(l.x) > ShinseiMaruEntity.BEAM + 0.4) {
                return String.format("off her side (%.2f to starboard)", l.x);
            }
            return null;
        }, () -> String.format("she went %.1f blocks and turned %.0f degrees; lowest the player's feet got: %.2f off the deck",
                travelled[0], turned[0], lowest[0]));
    }

    /** Walks the player forward ({@code keys}) for a while: the highest their feet get above her keel. */
    private static void climb(String name, int ticks, double need) {
        double[] highest = {-Double.MAX_VALUE};
        StringBuilder trace = new StringBuilder();
        int[] tick = {0};
        once(name + ": walk forward", () -> mc().options.keyUp.setDown(true));
        during(name, ticks, () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            LocalPlayer player = mc().player;
            if (boat != null && player != null) {
                Vec3 l = local(boat, player.position());
                highest[0] = Math.max(highest[0], l.y);
                if (tick[0]++ % 5 == 0) { // (where they are in her frame: to starboard, up, forward; walking into something; on the ground)
                    trace.append(String.format(" [%d: %.2f %.2f %.2f%s%s]", tick[0] - 1, l.x, l.y, l.z,
                            player.horizontalCollision ? " hc" : "", player.onGround() ? " og" : ""));
                }
            }
            return null;
        }, () -> String.format("highest: %.2f above her keel (need %.2f); trace:%s", highest[0], need, trace));
        once(name + ": stop", () -> mc().options.keyUp.setDown(false));
        check(name + ": got up there", () -> highest[0] >= need - 0.05 ? null
                : String.format("only got %.2f above her keel, needed %.2f", highest[0], need));
    }

    private static void boat() {
        camera("boat: pool", new Vec3(-50.0, -48.0, -30.0), new Vec3(-70.0, -55.0, 0.0), 70);
        waitTicks(80);
        // a pool 118 by 98 (room for her to circle twice either way), filled in strips (a fill is at most 32768 blocks)
        commands("time set 6000",
                "fill -130 -61 -50 -101 -56 50 minecraft:stone_bricks",
                "fill -100 -61 -50 -71 -56 50 minecraft:stone_bricks",
                "fill -70 -61 -50 -41 -56 50 minecraft:stone_bricks",
                "fill -40 -61 -50 -11 -56 50 minecraft:stone_bricks",
                "fill -129 -60 -49 -90 -56 49 minecraft:water",
                "fill -89 -60 -49 -50 -56 49 minecraft:water",
                "fill -49 -60 -49 -12 -56 49 minecraft:water",
                "summon godzillaminusone:shinsei_maru -70 -57 0 {Rotation:[0f,0f]}");
        waitTicks(100);
        check("boat: afloat and steady", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            return boat != null && boat.isAfloat() ? null : "not afloat";
        });
        if (ModList.get().isLoaded("physicsmod")) {
            physicsModWaves();
        }
        camera("boat: from her starboard quarter", new Vec3(-82.0, -50.0, -14.0), new Vec3(-70.0, -55.0, 0.5), 60);
        waitTicks(60);
        shot("boat_starboard_quarter");
        camera("boat: from her port bow", new Vec3(-56.0, -49.0, 14.0), new Vec3(-70.0, -55.0, -0.5), 60);
        waitTicks(40);
        shot("boat_port_bow");
        camera("boat: the back of her wheelhouse", new Vec3(-70.0, -50.5, -9.5), new Vec3(-70.0, -53.5, -3.0), 60);
        waitTicks(40);
        shot("boat_wheelhouse_back");

        // walking her decks while she sails and turns
        onBoat("boat: onto her foredeck", 0.0, ShinseiMaruEntity.DECK + 0.1, 4.5, 0.0F);
        once("boat: third person", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        waitTicks(40);
        int[] motorSince = {0};
        once("boat: mark", () -> motorSince[0] = now());
        server("boat: ahead, turning to port", level -> serverBoat(level).autopilot(1.0F, 0.6F));
        stayOnDeck("boat: on her foredeck while she moves and turns", 120, ShinseiMaruEntity.DECK);
        check("boat: her motor runs while she's under way", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            return boat != null && ShinseiMaruSounds.isPlayingFor(boat) ? null : "no motor sound playing";
        });
        viewOnBoat("boat: the player on her foredeck, from abeam", new Vec3(9.0, 7.0, 2.0), new Vec3(0.0, 3.0, 4.0), 1);
        shot("boat_on_deck_under_way");
        once("boat: camera back to the player", () -> view(null, null));
        stayOnDeck("boat: still on her foredeck", 120, ShinseiMaruEntity.DECK);
        check("boat: one motor loop, not a new sound every tick", () -> {
            int n = heard(motorSince[0], "shinsei_maru.motor").size();
            return n == 1 ? null : "the motor sound started " + n + " times";
        });
        onBoat("boat: onto her wheelhouse roof", 0.4, ShinseiMaruEntity.HELM.y + 0.1, -0.8, 0.0F);
        waitTicks(30);
        server("boat: ahead, turning to starboard", level -> serverBoat(level).autopilot(1.0F, -0.6F));
        stayOnDeck("boat: on her wheelhouse roof while she moves and turns", 200, ShinseiMaruEntity.HELM.y);
        viewOnBoat("boat: the player on her roof, from abeam", new Vec3(-10.0, 9.0, -2.0), new Vec3(0.0, 5.0, -0.5), 1);
        shot("boat_on_roof_under_way");
        once("boat: camera back to the player, again", () -> view(null, null));
        server("boat: stop", level -> serverBoat(level).autopilot(0.0F, 0.0F));
        waitTicks(120);
        check("boat: her motor dies away when she stops", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            return boat != null && !ShinseiMaruSounds.isPlayingFor(boat) ? null : "still playing";
        });

        // her ladders: up the side from the water, and up the back of the wheelhouse to the roof (her back in the middle
        // of the pool, heading south, so nothing's in the way)
        server("boat: back to the middle of the pool", level -> {
            ShinseiMaruEntity boat = serverBoat(level);
            boat.autopilot(0.0F, 0.0F);
            boat.moveTo(-70.0, boat.getY(), 0.0, 0.0F, 0.0F);
        });
        waitTicks(40);
        for (int side : new int[] {1, -1}) {
            String which = side > 0 ? "starboard" : "port";
            onBoat("boat: in the water by her " + which + " ladder", side * (ShinseiMaruEntity.BEAM + 0.75), 0.5,
                    ShinseiMaruEntity.LADDER, side > 0 ? -90.0F : 90.0F); // (facing the hull: starboard is her heading + 90)
            waitTicks(20);
            climb("boat: up her " + which + " ladder", 100, ShinseiMaruEntity.DECK);
        }
        // the roof ladder is drawn to starboard of the door (seen from astern, on the right); the wall to port of the
        // door has no ladder, and can't be climbed
        onBoat("boat: on deck behind the wheelhouse, at the roof ladder", ShinseiMaruEntity.ROOF_LADDER, ShinseiMaruEntity.DECK + 0.1,
                ShinseiMaruEntity.WHEELHOUSE_AFT - 0.75, 0.0F);
        waitTicks(20);
        once("boat: third person, from behind", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        waitTicks(10);
        shot("boat_at_roof_ladder");
        climb("boat: up the roof ladder", 100, ShinseiMaruEntity.HELM.y);
        shot("boat_up_the_roof_ladder");
        onBoat("boat: on deck behind the wheelhouse, to port of the door", -ShinseiMaruEntity.ROOF_LADDER, ShinseiMaruEntity.DECK + 0.1,
                ShinseiMaruEntity.WHEELHOUSE_AFT - 0.75, 0.0F);
        waitTicks(20);
        double[] highestThere = {-Double.MAX_VALUE};
        once("boat: walk into the wall", () -> mc().options.keyUp.setDown(true));
        during("boat: no ladder to port of the door", 60, () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            if (boat != null && mc().player != null) {
                highestThere[0] = Math.max(highestThere[0], local(boat, mc().player.position()).y);
            }
            return null;
        }, () -> String.format("highest: %.2f above her keel", highestThere[0]));
        once("boat: stop walking", () -> mc().options.keyUp.setDown(false));
        check("boat: can't climb the wall to port of the door", () -> highestThere[0] < ShinseiMaruEntity.HELM.y - 0.5 ? null
                : String.format("got %.2f above her keel", highestThere[0]));

        // her crew stand at their stations
        server("boat: take the gun", level -> {
            ShinseiMaruEntity boat = serverBoat(level);
            ServerPlayer player = serverPlayer(level);
            player.stopRiding();
            ShinseiMaruPart[] parts = boat.getParts();
            parts[parts.length - 1].interact(player, InteractionHand.MAIN_HAND); // (her stern)
        });
        waitTicks(30);
        check("boat: at the gun, standing on the stern platform", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            LocalPlayer player = mc().player;
            if (boat == null || player == null || player.getVehicle() != boat || boat.getGunner() != player) {
                return "not at the gun";
            }
            double feet = player.getY() - boat.getY();
            return !boat.shouldRiderSit() && Math.abs(feet - ShinseiMaruEntity.GUNNER.y) < 0.2 ? null
                    : String.format("feet %.2f above her keel (platform at %.2f), sitting %b", feet, ShinseiMaruEntity.GUNNER.y,
                    boat.shouldRiderSit());
        });
        once("boat: the gunner looks astern", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            if (boat != null && mc().player != null) {
                mc().player.setYRot(boat.getYRot() + 180.0F);
                mc().player.setXRot(-5.0F);
            }
        });
        waitTicks(10);
        viewOnBoat("boat: the gunner from her starboard quarter", new Vec3(6.0, 6.0, -12.0), new Vec3(0.0, 3.4, -5.3));
        shot("boat_gunner_standing");
        viewOnBoat("boat: the gunner from abeam", new Vec3(-7.0, 4.0, -5.0), new Vec3(0.0, 3.2, -5.3));
        shot("boat_gunner_standing_abeam");
        once("boat: camera back", () -> view(null, null));
        server("boat: take the wheel", level -> {
            ShinseiMaruEntity boat = serverBoat(level);
            ServerPlayer player = serverPlayer(level);
            player.stopRiding();
            boat.interact(player, InteractionHand.MAIN_HAND);
        });
        waitTicks(30);
        check("boat: at the wheel, standing on the roof", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            LocalPlayer player = mc().player;
            if (boat == null || player == null || boat.getControllingPassenger() != player) {
                return "not at the wheel";
            }
            double feet = player.getY() - boat.getY();
            return Math.abs(feet - ShinseiMaruEntity.HELM.y) < 0.2 ? null
                    : String.format("feet %.2f above her keel (roof at %.2f)", feet, ShinseiMaruEntity.HELM.y);
        });
        viewOnBoat("boat: the helmsman from her starboard bow", new Vec3(7.0, 8.0, 8.0), new Vec3(0.0, 5.4, -0.1));
        shot("boat_helmsman_standing");
        once("boat: camera back", () -> view(null, null));
        Vec3[] start = {null};
        once("boat: throttle", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            start[0] = boat != null ? boat.position() : Vec3.ZERO;
            mc().options.keyUp.setDown(true);
        });
        waitTicks(80);
        check("boat: she answers the helm, motor running", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            if (boat == null) {
                return "no boat";
            }
            double moved = boat.position().distanceTo(start[0]);
            return moved > 3.0 && ShinseiMaruSounds.isPlayingFor(boat) ? null
                    : String.format("moved %.1f blocks, motor %b", moved, ShinseiMaruSounds.isPlayingFor(boat));
        });
        once("boat: throttle off", () -> mc().options.keyUp.setDown(false));
        server("boat: ashore", level -> {
            ServerPlayer player = serverPlayer(level);
            player.stopRiding();
            player.setGameMode(GameType.SPECTATOR);
        });
        once("boat: first person", () -> mc().options.setCameraType(CameraType.FIRST_PERSON));
    }

    /**
     * With Physics Mod in the game: how far its waves lift the Shinsei Maru as it draws her, beside a vanilla boat (its
     * EntityOcean, mixed into every entity: getPhysicsYOffset), and its ocean settings. Where they lift the vanilla boat,
     * they have to lift her too. In the rain: its waves are as high as the weather makes them (oceanWeatherClear,
     * oceanWeatherRain, oceanWeatherThunder), flat in clear weather by default.
     */
    private static void physicsModWaves() {
        once("physics mod: its ocean settings", () -> {
            try {
                StringBuilder settings = new StringBuilder();
                for (java.lang.reflect.Field field : Class.forName("net.diebuddies.config.ConfigClient").getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getName().toLowerCase(java.util.Locale.ROOT).contains("ocean")) {
                        settings.append(' ').append(field.getName()).append('=').append(field.get(null));
                    }
                }
                log("physics mod: ocean settings:" + settings);
            } catch (ReflectiveOperationException | LinkageError e) {
                log("physics mod: its settings: " + e);
            }
        });
        server("physics mod: a vanilla boat beside her", level -> {
            Boat boat = EntityType.BOAT.create(level);
            boat.moveTo(-70.5, -56.0, 16.5, 0.0F, 0.0F);
            level.addFreshEntity(boat);
        });
        commands("weather rain");
        waitTicks(200); // (the rain coming on)
        camera("physics mod: the boats in the rain", new Vec3(-82.0, -50.0, -14.0), new Vec3(-70.0, -55.0, 4.0), 60);
        waitTicks(20);
        shot("boat_physicsmod_rain");
        double[] ours = {Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] theirs = {Double.MAX_VALUE, -Double.MAX_VALUE};
        during("physics mod: its waves", 100, () -> {
            ShinseiMaruEntity maru = find(ShinseiMaruEntity.class);
            Boat boat = find(Boat.class);
            double a = waveOffset(maru);
            double b = waveOffset(boat);
            ours[0] = Math.min(ours[0], a);
            ours[1] = Math.max(ours[1], a);
            theirs[0] = Math.min(theirs[0], b);
            theirs[1] = Math.max(theirs[1], b);
            return Double.isNaN(a) || Double.isNaN(b) ? "can't read its wave offset" : null;
        }, () -> String.format("lifted by its waves: the Shinsei Maru %.3f to %.3f, a vanilla boat %.3f to %.3f blocks",
                ours[0], ours[1], theirs[0], theirs[1]));
        check("physics mod: where its waves lift a boat, they lift her", () -> theirs[1] - theirs[0] < 0.02 || ours[1] - ours[0] > 0.02 ? null
                : "they lift the vanilla boat, not her");
        commands("kill @e[type=minecraft:boat]", "weather clear");
        waitTicks(100);
    }

    /**
     * Physics Mod's waves on a real ocean (the "ocean" script, a normal world): the nearest ocean to the world's middle,
     * the Shinsei Maru and a vanilla boat on it in a thunderstorm, and how far its waves lift each as it draws them.
     */
    static void oceanScript() {
        commands("gamerule doMobSpawning false", "time set 6000");
        BlockPos[] sea = {null};
        server("ocean: the nearest ocean", level -> {
            com.mojang.datafixers.util.Pair<BlockPos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> found =
                    level.findClosestBiome3d(biome -> biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN),
                            new BlockPos(0, level.getSeaLevel(), 0), 3200, 32, 64);
            if (found == null) {
                log("ocean: none within 3200 blocks");
                return;
            }
            sea[0] = new BlockPos(found.getFirst().getX(), level.getSeaLevel(), found.getFirst().getZ());
            log("ocean: " + found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?") + " at " + sea[0].toShortString());
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.SPECTATOR);
            float[] angles = look(new Vec3(sea[0].getX() - 14.5, sea[0].getY() + 9.0, sea[0].getZ() - 14.5), Vec3.atCenterOf(sea[0]).add(0.0, 1.0, 6.0));
            player.teleportTo(level, sea[0].getX() - 14.5, sea[0].getY() + 9.0 - 1.62, sea[0].getZ() - 14.5, angles[0], angles[1]);
        });
        waitTicks(200); // (its chunks loading)
        server("ocean: the boats", level -> {
            if (sea[0] == null) {
                return;
            }
            ShinseiMaruEntity maru = ModEntities.SHINSEI_MARU.get().create(level);
            maru.moveTo(sea[0].getX() + 0.5, sea[0].getY() - 1.0, sea[0].getZ() + 0.5, 0.0F, 0.0F);
            level.addFreshEntity(maru);
            Boat boat = EntityType.BOAT.create(level);
            boat.moveTo(sea[0].getX() + 0.5, sea[0].getY(), sea[0].getZ() + 14.5, 0.0F, 0.0F);
            level.addFreshEntity(boat);
        });
        commands("weather thunder");
        waitTicks(300); // (the storm coming on)
        double[] ours = {Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] theirs = {Double.MAX_VALUE, -Double.MAX_VALUE};
        during("ocean: Physics Mod's waves, in a storm", 200, () -> {
            double a = waveOffset(find(ShinseiMaruEntity.class));
            double b = waveOffset(find(Boat.class));
            ours[0] = Math.min(ours[0], a);
            ours[1] = Math.max(ours[1], a);
            theirs[0] = Math.min(theirs[0], b);
            theirs[1] = Math.max(theirs[1], b);
            return Double.isNaN(a) || Double.isNaN(b) ? "can't read its wave offset (no Physics Mod, or no boats)" : null;
        }, () -> String.format("lifted by its waves: the Shinsei Maru %.3f to %.3f, a vanilla boat %.3f to %.3f blocks",
                ours[0], ours[1], theirs[0], theirs[1]));
        shot("ocean_physicsmod_storm");
        check("ocean: where its waves lift a boat, they lift her", () -> theirs[1] - theirs[0] < 0.02 || ours[1] - ours[0] > 0.02 ? null
                : "they lift the vanilla boat, not her");
    }

    /** Physics Mod's wave offset of {@code entity} as it's drawn (blocks), or NaN. */
    private static double waveOffset(@Nullable Entity entity) {
        if (entity == null) {
            return Double.NaN;
        }
        try {
            Object offset = Class.forName("net.diebuddies.physics.ocean.EntityOcean").getMethod("getPhysicsYOffset", float.class)
                    .invoke(entity, 1.0F);
            return offset instanceof Double d ? d : Double.NaN;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return Double.NaN;
        }
    }

    // ------------------------------------------------------------------ a Shinden

    private static void shinden() {
        server("shinden: player there", level -> {
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level, 0.5, -60.0, -70.5, 180.0F, 0.0F);
        });
        waitTicks(60);
        commands("summon godzillaminusone:shinden 0.5 -60 -80.5 {Rotation:[180f,0f]}");
        waitTicks(20);
        server("shinden: in the cockpit", level -> {
            ServerPlayer player = serverPlayer(level);
            List<ShindenEntity> planes = level.getEntitiesOfClass(ShindenEntity.class, new AABB(-20, -70, -100, 20, -40, -60));
            player.startRiding(planes.get(0), true);
        });
        int[] since = {0};
        once("shinden: mark", () -> since[0] = now());
        waitTicks(80);
        check("shinden: third person while piloting", () ->
                mc().options.getCameraType() != CameraType.FIRST_PERSON ? null : "first person");
        check("shinden: the camera stands back to frame the plane", () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            if (plane == null) {
                return "no plane";
            }
            double d = mc().gameRenderer.getMainCamera().getPosition().distanceTo(plane.position().add(0.0, 2.0, 0.0));
            return d > 8.0 ? null : String.format("camera only %.1f blocks from the plane", d);
        });
        check("shinden: no engine noise parked", () -> {
            int n = heard(since[0], "shinden.engine").size();
            return n == 0 ? null : "heard the engine " + n + " times";
        });
        shot("shinden_parked_third_person");
        once("shinden: take off", () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            LocalPlayer player = mc().player;
            if (plane != null && player != null) {
                player.setYRot(plane.getYRot());
                player.setXRot(-14.0F);
            }
            mc().options.keyUp.setDown(true);
        });
        until("shinden: airborne", () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            return plane != null && !plane.onGround() && plane.getY() > -56.0;
        }, 400);
        waitTicks(40);
        check("shinden: engine heard in the air", () -> heard(since[0], "shinden.engine").isEmpty() ? "no engine sound" : null);
        once("shinden: level off", () -> {
            if (mc().player != null) {
                mc().player.setXRot(-3.0F);
            }
        });
        waitTicks(20);
        shot("shinden_flying");
        float[] heading = {0.0F};
        // (as the keyboard sends it: Left Control down, with the Ctrl modifier)
        once("shinden: free look on", () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),
                GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_CONTROL));
        until("shinden: free look takes hold", () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            if (plane != null && plane.isFreeLooking()) {
                heading[0] = plane.getYRot();
                return true;
            }
            return false;
        }, 10);
        float[] lookedOff = {0.0F};
        during("shinden: free look: the plane holds its heading while the pilot looks round", 30, () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            LocalPlayer player = mc().player;
            if (plane == null || player == null) {
                return "no plane";
            }
            player.setYRot(player.getYRot() + 3.5F);
            lookedOff[0] = Mth.wrapDegrees(player.getYRot() - plane.getYRot());
            float drift = Mth.wrapDegrees(plane.getYRot() - heading[0]);
            return Math.abs(drift) < 2.0F ? null : String.format("the plane turned %.1f degrees", drift);
        }, () -> String.format("looking %.0f degrees off the nose", lookedOff[0]));
        shot("shinden_free_look");
        once("shinden: free look off", () -> mc().keyboardHandler.keyPress(mc().getWindow().getWindow(),
                GLFW.GLFW_KEY_LEFT_CONTROL, 0, GLFW.GLFW_RELEASE, 0));
        float[] previous = {Float.NaN};
        float[] biggest = {0.0F};
        during("shinden: letting go doesn't snap the plane round", 40, () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            if (plane == null) {
                return "no plane";
            }
            float step = Float.isNaN(previous[0]) ? 0.0F : Math.abs(Mth.wrapDegrees(plane.getYRot() - previous[0]));
            previous[0] = plane.getYRot();
            biggest[0] = Math.max(biggest[0], step);
            return step < 4.0F ? null : String.format("the plane swung %.1f degrees in a tick", step);
        }, () -> String.format("largest turn in a tick: %.1f degrees", biggest[0]));
        check("shinden: the view is back on the nose, steering again", () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            LocalPlayer player = mc().player;
            if (plane == null || player == null) {
                return "no plane";
            }
            float off = Mth.wrapDegrees(player.getYRot() - plane.getYRot());
            return Math.abs(off) < 2.0F && !plane.isFreeLooking() ? null
                    : String.format("view %.1f degrees off the nose, free look %b", off, plane.isFreeLooking());
        });
        once("shinden: throttle off", () -> mc().options.keyUp.setDown(false));
        server("shinden: out", level -> {
            ServerPlayer player = serverPlayer(level);
            player.stopRiding();
            player.setGameMode(GameType.SPECTATOR);
        });
        waitTicks(10);
        check("shinden: back to first person out of the plane", () ->
                mc().options.getCameraType() == CameraType.FIRST_PERSON ? null : "still " + mc().options.getCameraType());
        commands("kill @e[type=godzillaminusone:shinden]");
    }

    // ------------------------------------------------------------------ Godzilla after his atomic breath

    private static final UUID[] GODZILLA = new UUID[1];

    @Nullable
    private static GodzillaEntity serverGodzilla(ServerLevel level) {
        return GODZILLA[0] != null && level.getEntity(GODZILLA[0]) instanceof GodzillaEntity g ? g : null;
    }

    /** His jaw's angle (degrees, from shut) as last drawn. */
    private static float jaw(GodzillaEntity godzilla) {
        EntityRenderer<? super GodzillaEntity> renderer = mc().getEntityRenderDispatcher().getRenderer(godzilla);
        if (renderer instanceof GeoEntityRenderer<?> geo) {
            CoreGeoBone bone = geo.getGeoModel().getAnimationProcessor().getBone("Jew");
            if (bone != null) {
                return Math.abs(bone.getRotX()) * Mth.RAD_TO_DEG;
            }
        }
        return Float.NaN;
    }

    private static void godzilla() {
        camera("godzilla", new Vec3(452.0, -38.0, 46.0), new Vec3(400.5, -36.0, 0.5), 70);
        waitTicks(100);
        commands("gamerule mobGriefing false", "time set 6000");
        server("godzilla and a target 110 blocks off", level -> {
            GodzillaEntity godzilla = ModEntities.GODZILLA.get().create(level);
            godzilla.moveTo(400.5, -60.0, 0.5, 0.0F, 0.0F);
            godzilla.skipIntro();
            godzilla.setFirstBreathDelay(0);
            level.addFreshEntity(godzilla);
            Pig pig = EntityType.PIG.create(level);
            pig.moveTo(400.5, -60.0, 110.5, 0.0F, 0.0F);
            pig.setNoAi(true);
            level.addFreshEntity(pig);
            godzilla.setTarget(pig);
            GODZILLA[0] = godzilla.getUUID();
        });
        until("godzilla: charges his atomic breath", () -> {
            GodzillaEntity g = find(GodzillaEntity.class);
            return g != null && g.getChargeState() == GodzillaEntity.CHARGE_GLOWING;
        }, 400);
        int[] cooling = {0};
        until("godzilla: the beam ends and he roars", () -> {
            GodzillaEntity g = find(GodzillaEntity.class);
            if (g != null && g.getChargeState() == GodzillaEntity.CHARGE_COOLING) {
                cooling[0] = now();
                return true;
            }
            return false;
        }, 700);
        shot("godzilla_post_breath_roar");
        until("godzilla: the charge is over", () -> {
            GodzillaEntity g = find(GodzillaEntity.class);
            return g != null && g.getChargeState() == GodzillaEntity.CHARGE_NONE;
        }, 400);
        check("godzilla: his roar played out (not cut short)", () -> now() - cooling[0] >= GodzillaEntity.ROAR_TICKS - 5 ? null
                : "the charge ended " + (now() - cooling[0]) + " ticks after the beam");
        // His roar closes his jaw over its last half second, here a tick or two behind the server: from a second after
        // the charge ends his jaw has to be shut (idle at most 7.5 degrees) and stay shut, not stuck open.
        float[] jaws = new float[80];
        during("godzilla: jaw shuts as his roar ends and stays shut, standing normally", jaws.length, new Supplier<>() {
            private int tick;

            @Override
            public String get() {
                GodzillaEntity g = find(GodzillaEntity.class);
                if (g == null) {
                    return "gone";
                }
                float angle = jaw(g);
                jaws[this.tick] = angle;
                int t = this.tick++;
                if (g.getChargeState() != GodzillaEntity.CHARGE_NONE) {
                    return "still charging";
                }
                return t < 20 || Float.isNaN(angle) || angle <= 10.0F ? null : String.format("jaw open %.1f degrees", angle);
            }
        }, () -> {
            StringBuilder curve = new StringBuilder("jaw every 5 ticks:");
            for (int i = 0; i < jaws.length; i += 5) {
                curve.append(String.format(" %.0f", jaws[i]));
            }
            return curve.toString();
        });
        checkServer("godzilla: not roaring, not rooted", level -> {
            GodzillaEntity g = serverGodzilla(level);
            return g == null ? "gone" : g.isRoaring() || g.isCharging() ? "roaring " + g.isRoaring() + ", charging " + g.isCharging() : null;
        });
        shot("godzilla_after_breath");
        Vec3[] was = {null};
        server("godzilla: a new target 40 blocks off", level -> {
            GodzillaEntity g = serverGodzilla(level);
            Pig pig = EntityType.PIG.create(level);
            pig.moveTo(g.getX() + 40.0, -60.0, g.getZ(), 0.0F, 0.0F);
            pig.setNoAi(true);
            level.addFreshEntity(pig);
            g.setTarget(pig);
            was[0] = g.position();
        });
        waitTicks(100);
        checkServer("godzilla: walks again after his breath", level -> {
            GodzillaEntity g = serverGodzilla(level);
            if (g == null) {
                return "gone";
            }
            double moved = Math.sqrt(g.position().subtract(was[0]).horizontalDistanceSqr());
            return moved > 3.0 ? null : String.format("moved only %.1f blocks in 5 s", moved);
        });
        commands("kill @e[type=godzillaminusone:godzilla]", "gamerule mobGriefing true");
    }

    private Scenes() {
    }
}
