package com.npjt25.godzillaminusone.client.harness;

import com.npjt25.godzillaminusone.client.ShindenControls;
import com.npjt25.godzillaminusone.client.ShinseiMaruSounds;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.GodzillaStatueEntity;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruPart;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModItems;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.server;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.serverPlayer;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.shot;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.until;
import static com.npjt25.godzillaminusone.client.harness.ShotsHarness.waitTicks;

/**
 * The scenes {@link ShotsHarness} goes through, in a flat world (its ground is at y -60), each in its own place: the
 * hunched snarl statue; the Odo Island Monster spawning (and his spawn egg in the hotbar); Godzilla's roar heard from
 * either side of 200 blocks; a searchlight's beam over water and ice at night; the Shinsei Maru (walking her decks as
 * she sails and turns, climbing her ladders, her crew standing at their stations, her motor); a Shinden (third person,
 * its engine, Free Look); Godzilla getting over his atomic breath.
 */
final class Scenes {
    private static final double MODEL_SCALE = 7.83 / 16.0; // GodzillaRenderer.SCALE: blocks per model pixel

    static void script() {
        commands("gamerule doMobSpawning false", "gamerule mobGriefing true", "time set 6000", "weather clear");
        waitTicks(20);
        statue();
        odo();
        roars();
        searchlight();
        boat();
        shinden();
        godzilla();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
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
        waitTicks(60);
        commands("time set 6000", "kill @e[type=minecraft:armor_stand]", "kill @e[type=godzillaminusone:searchlight]");
    }

    // ------------------------------------------------------------------ the Shinsei Maru

    @Nullable
    private static ShinseiMaruEntity serverBoat(ServerLevel level) {
        List<ShinseiMaruEntity> boats = level.getEntitiesOfClass(ShinseiMaruEntity.class, new AABB(-110, -70, -40, -30, -40, 40));
        return boats.isEmpty() ? null : boats.get(0);
    }

    /** A point (blocks: to starboard, up from the keel, forward) in her frame. */
    private static Vec3 local(ShinseiMaruEntity boat, Vec3 p) {
        Vec3 rel = p.subtract(boat.position());
        Vec3 forward = Vec3.directionFromRotation(0.0F, boat.getYRot());
        Vec3 starboard = new Vec3(-forward.z, 0.0, forward.x);
        return new Vec3(rel.dot(starboard), rel.y, rel.dot(forward));
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
        once(name + ": walk forward", () -> mc().options.keyUp.setDown(true));
        during(name, ticks, () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            if (boat != null && mc().player != null) {
                highest[0] = Math.max(highest[0], local(boat, mc().player.position()).y);
            }
            return null;
        }, () -> String.format("highest: %.2f above her keel (need %.2f)", highest[0], need));
        once(name + ": stop", () -> mc().options.keyUp.setDown(false));
        check(name + ": got up there", () -> highest[0] >= need - 0.05 ? null
                : String.format("only got %.2f above her keel, needed %.2f", highest[0], need));
    }

    private static void boat() {
        camera("boat: pool", new Vec3(-50.0, -48.0, -30.0), new Vec3(-70.0, -55.0, 0.0), 70);
        waitTicks(80);
        commands("time set 6000",
                "fill -101 -61 -31 -39 -56 31 minecraft:stone_bricks",
                "fill -100 -60 -30 -40 -56 30 minecraft:water",
                "summon godzillaminusone:shinsei_maru -70 -57 0 {Rotation:[0f,0f]}");
        waitTicks(100);
        check("boat: afloat and steady", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            return boat != null && boat.isAfloat() ? null : "not afloat";
        });
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
        shot("boat_on_deck_under_way");
        stayOnDeck("boat: still on her foredeck", 120, ShinseiMaruEntity.DECK);
        check("boat: one motor loop, not a new sound every tick", () -> {
            int n = heard(motorSince[0], "shinsei_maru.motor").size();
            return n == 1 ? null : "the motor sound started " + n + " times";
        });
        onBoat("boat: onto her wheelhouse roof", 0.4, ShinseiMaruEntity.HELM.y + 0.1, -0.8, 0.0F);
        waitTicks(30);
        server("boat: ahead, turning to starboard", level -> serverBoat(level).autopilot(1.0F, -0.6F));
        stayOnDeck("boat: on her wheelhouse roof while she moves and turns", 200, ShinseiMaruEntity.HELM.y);
        shot("boat_on_roof_under_way");
        server("boat: stop", level -> serverBoat(level).autopilot(0.0F, 0.0F));
        waitTicks(120);
        check("boat: her motor dies away when she stops", () -> {
            ShinseiMaruEntity boat = find(ShinseiMaruEntity.class);
            return boat != null && !ShinseiMaruSounds.isPlayingFor(boat) ? null : "still playing";
        });

        // her ladders: up the side from the water, and up the back of the wheelhouse to the roof
        for (int side : new int[] {1, -1}) {
            String which = side > 0 ? "starboard" : "port";
            onBoat("boat: in the water by her " + which + " ladder", side * (ShinseiMaruEntity.BEAM + 0.75), 0.5,
                    ShinseiMaruEntity.LADDER, side > 0 ? -90.0F : 90.0F); // (facing the hull: starboard is her heading + 90)
            waitTicks(20);
            climb("boat: up her " + which + " ladder", 100, ShinseiMaruEntity.DECK);
        }
        for (double s : new double[] {ShinseiMaruEntity.ROOF_LADDER, -ShinseiMaruEntity.ROOF_LADDER}) {
            String which = s == ShinseiMaruEntity.ROOF_LADDER ? "at ROOF_LADDER" : "the other side";
            onBoat("boat: on deck behind the wheelhouse (" + which + ")", s, ShinseiMaruEntity.DECK + 0.1,
                    ShinseiMaruEntity.WHEELHOUSE_AFT - 0.75, 0.0F);
            waitTicks(20);
            if (s == ShinseiMaruEntity.ROOF_LADDER) {
                once("boat: third person, from behind", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
                waitTicks(10);
                shot("boat_at_roof_ladder");
            }
            climb("boat: up the roof ladder (" + which + ")", 100, ShinseiMaruEntity.HELM.y);
        }

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
        once("boat: look at the gunner", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        waitTicks(10);
        shot("boat_gunner_standing");
        once("boat: from behind", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
        waitTicks(10);
        shot("boat_gunner_standing_back");
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
        once("boat: look at the helmsman", () -> mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        waitTicks(10);
        shot("boat_helmsman_standing");
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
        once("shinden: free look on", () -> {
            ShindenEntity plane = find(ShindenEntity.class);
            heading[0] = plane != null ? plane.getYRot() : 0.0F;
            ShindenControls.FREE_LOOK.setDown(true);
        });
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
        once("shinden: free look off", () -> ShindenControls.FREE_LOOK.setDown(false));
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
        float[] widest = {0.0F};
        during("godzilla: jaw shut again (idle at most 7.5 degrees), standing normally", 60, () -> {
            GodzillaEntity g = find(GodzillaEntity.class);
            if (g == null) {
                return "gone";
            }
            float angle = jaw(g);
            if (!Float.isNaN(angle)) {
                widest[0] = Math.max(widest[0], angle);
            }
            if (g.getChargeState() != GodzillaEntity.CHARGE_NONE) {
                return "still charging";
            }
            return Float.isNaN(angle) || angle <= 10.0F ? null : String.format("jaw open %.1f degrees", angle);
        }, () -> String.format("widest jaw: %.1f degrees", widest[0]));
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
