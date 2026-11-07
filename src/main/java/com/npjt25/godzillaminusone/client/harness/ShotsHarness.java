package com.npjt25.godzillaminusone.client.harness;

import com.mojang.blaze3d.platform.NativeImage;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.client.CameraPlacement;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * In-game checks and screenshots, for CI only: the "client" job of .github/workflows/build.yml starts the real game
 * ({@code ./gradlew runClient -Pshots}) on a virtual display. Nothing here runs unless the game is started with
 * {@code -Dgodzillaminusone.shots=true}: nobody playing the mod ever runs any of it.
 *
 * <p>It makes a flat creative world and goes through the scenes of {@link Scenes} in it: building them with commands
 * and server-side calls, then driving the real client (its keys, its player's own movement and collisions, its sound
 * engine and renderer) and checking what happens. Each check is logged as {@code GMO-CHECK PASS|FAIL name: detail};
 * each screenshot is saved under run/screenshots and also logged as base64 JPEG lines
 * {@code GMO-IMG name i/n data} (the only way CI can hand them back here). Then the game quits.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShotsHarness {
    public static final boolean ENABLED = Boolean.getBoolean("godzillaminusone.shots");
    /**
     * Which script: "all" (the default: every scene, in a flat world), "ocean" (-Dgodzillaminusone.shots.scene=ocean:
     * only the Shinsei Maru on a real ocean, in a normal world, for Physics Mod's waves), or "searchlight" (only the
     * searchlight's scenes, in a flat world: with a shader pack on, CI's "shaders" job).
     */
    static final String SCENE = System.getProperty("godzillaminusone.shots.scene", "all");
    /** Everything has to be done within this many ticks of the world loading (30 minutes). */
    private static final int TIME_LIMIT = 20 * 60 * 30;
    private static final int IMAGE_WIDTH = 800;
    private static final int CHUNK = 3000;

    private enum State { START, LOADING, RUNNING, DONE }

    /** One step of the script: called every tick until it says it's done. */
    @FunctionalInterface
    interface Step {
        boolean tick();
    }

    private record Labelled(String label, Step step) {
    }

    /** A sound the client started playing: when (ticks since the world loaded), what and where. */
    record Heard(int tick, ResourceLocation sound, double x, double y, double z) {
    }

    private static State state = State.START;
    private static final Deque<Labelled> STEPS = new ArrayDeque<>();
    @Nullable
    private static Labelled current;
    private static int ticks;
    private static int loadingTicks;
    private static int startWait;
    private static int passed;
    private static int failed;
    private static final List<String> RESULTS = new ArrayList<>();
    @Nullable
    private static String wantShot;
    private static boolean shotTaken;
    @Nullable
    private static Consumer<NativeImage> wantProbe;
    private static boolean probed;
    static final List<Heard> HEARD = new ArrayList<>();

    // ------------------------------------------------------------------ driving the game

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (state) {
            case START -> {
                // (from the title screen; or, after 10 s on some other screen, from that: a mod's notice, say)
                boolean waiting = mc.level == null && mc.getOverlay() == null && mc.screen != null;
                startWait = waiting ? startWait + 1 : 0;
                if (waiting && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen || startWait > 200)) {
                    if (!(mc.screen instanceof TitleScreen)) {
                        log("starting from " + mc.screen.getClass().getName());
                    }
                    setUpOptions(mc);
                    createWorld(mc);
                    state = State.LOADING;
                }
            }
            case LOADING -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && ++loadingTicks > 60) {
                    log("world loaded; sound engine: " + mc.getSoundManager().getDebugString());
                    if (SCENE.equals("ocean")) {
                        Scenes.oceanScript();
                    } else if (SCENE.equals("searchlight")) {
                        Scenes.searchlightScript();
                    } else {
                        Scenes.script();
                    }
                    state = State.RUNNING;
                }
            }
            case RUNNING -> run(mc);
            default -> {
            }
        }
    }

    private static void run(Minecraft mc) {
        if (++ticks > TIME_LIMIT) {
            fail("harness", "out of time, at step " + (current != null ? current.label() : "?"));
            finish(mc);
            return;
        }
        while (true) {
            if (current == null) {
                current = STEPS.poll();
                if (current == null) {
                    finish(mc);
                    return;
                }
            }
            boolean done;
            try {
                done = current.step().tick();
            } catch (Throwable t) {
                fail(current.label(), "threw " + t);
                GodzillaMinusOne.LOGGER.error("GMO harness step {} threw", current.label(), t);
                done = true;
            }
            if (!done) {
                return;
            }
            current = null;
        }
    }

    /** The screenshots (and probes): taken once a frame has been drawn, with everything in it. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (wantProbe != null) {
            Consumer<NativeImage> probe = wantProbe;
            wantProbe = null;
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                probe.accept(image);
            } catch (Throwable t) {
                fail("probe", t.toString());
            }
            probed = true;
        }
        if (wantShot == null) {
            return;
        }
        String name = wantShot;
        wantShot = null;
        try {
            capture(Minecraft.getInstance(), name);
        } catch (Throwable t) {
            fail("screenshot " + name, t.toString());
        }
        shotTaken = true;
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (!ENABLED || state != State.RUNNING) {
            return;
        }
        SoundInstance sound = event.getOriginalSound();
        if (sound != null) {
            HEARD.add(new Heard(ticks, sound.getLocation(), sound.getX(), sound.getY(), sound.getZ()));
        }
    }

    private static void setUpOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false; // (the virtual display's window never has the focus)
        mc.options.onboardAccessibility = false;
        mc.options.tutorialStep = TutorialSteps.NONE; // (no "move with W, A, S and D" toast in the pictures)
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.renderDistance().set(8);
        mc.options.simulationDistance().set(8);
        mc.options.fovEffectScale().set(0.0);
        mc.options.screenEffectScale().set(0.0);
        mc.options.graphicsMode().set(GraphicsStatus.FANCY);
        mc.options.hideGui = true;
    }

    private static void createWorld(Minecraft mc) {
        String name = "gmo-checks-" + System.currentTimeMillis();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1954L, false, false);
        log("creating world " + name);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(SCENE.equals("ocean") ? WorldPresets.NORMAL : WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    private static void finish(Minecraft mc) {
        state = State.DONE;
        String summary = "GMO-CHECKS passed=" + passed + " failed=" + failed;
        log(summary);
        RESULTS.add(summary);
        try {
            Files.write(new File(mc.gameDirectory, "gmo-checks.txt").toPath(), RESULTS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            GodzillaMinusOne.LOGGER.error("Couldn't write gmo-checks.txt", e);
        }
        mc.stop();
    }

    // ------------------------------------------------------------------ results

    /**
     * A line of the results, through the mod's logger (a launcher shows the game's log, not what it prints: the
     * released game's run in CI goes through one).
     */
    private static void out(String line) {
        GodzillaMinusOne.LOGGER.info(line);
    }

    static void log(String line) {
        out("GMO-LOG " + line);
    }

    static void pass(String name, String detail) {
        passed++;
        String line = "GMO-CHECK PASS " + name + (detail.isEmpty() ? "" : ": " + detail);
        RESULTS.add(line);
        out(line);
    }

    static void fail(String name, String detail) {
        failed++;
        String line = "GMO-CHECK FAIL " + name + ": " + detail;
        RESULTS.add(line);
        out(line);
    }

    /** A check's result: no problem (null) passes, anything else fails with it. */
    static void report(String name, @Nullable String problem, String detail) {
        if (problem == null) {
            pass(name, detail);
        } else {
            fail(name, problem);
        }
    }

    // ------------------------------------------------------------------ steps

    static void add(String label, Step step) {
        STEPS.add(new Labelled(label, step));
    }

    static void once(String label, Runnable action) {
        add(label, () -> {
            action.run();
            return true;
        });
    }

    static void waitTicks(int n) {
        int[] left = {n};
        add("wait " + n, () -> --left[0] <= 0);
    }

    /** Runs on the server's thread (and waits for it). */
    static void server(String label, Consumer<ServerLevel> action) {
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] future = new CompletableFuture[1];
        add(label, () -> {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (future[0] == null) {
                future[0] = server.submit(() -> action.accept(server.overworld()));
                return false;
            }
            if (!future[0].isDone()) {
                return false;
            }
            try {
                future[0].join();
            } catch (CompletionException e) {
                fail(label, "threw " + e.getCause());
                GodzillaMinusOne.LOGGER.error("GMO harness server step {} threw", label, e.getCause());
            }
            return true;
        });
    }

    /** Commands, as the server's console (with its permissions); their output goes to the log. */
    static void commands(String... commands) {
        server("commands " + commands[0], level -> {
            MinecraftServer server = level.getServer();
            CommandSourceStack source = server.createCommandSourceStack();
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(source, command);
            }
        });
    }

    /** A check made on the client. */
    static void check(String name, Supplier<String> problem) {
        add(name, () -> {
            String p;
            try {
                p = problem.get();
            } catch (Throwable t) {
                p = "threw " + t;
            }
            report(name, p, "");
            return true;
        });
    }

    /** A check made on the server's thread. */
    static void checkServer(String name, Function<ServerLevel, String> problem) {
        @SuppressWarnings("unchecked")
        CompletableFuture<String>[] future = new CompletableFuture[1];
        add(name, () -> {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (future[0] == null) {
                future[0] = server.submit(() -> problem.apply(server.overworld()));
                return false;
            }
            if (!future[0].isDone()) {
                return false;
            }
            String p;
            try {
                p = future[0].join();
            } catch (CompletionException e) {
                p = "threw " + e.getCause();
            }
            report(name, p, "");
            return true;
        });
    }

    /** Waits until {@code condition} holds (a check: it fails if it doesn't within {@code timeout} ticks). */
    static void until(String name, BooleanSupplier condition, int timeout) {
        int[] waited = {0};
        add(name, () -> {
            if (condition.getAsBoolean()) {
                pass(name, "after " + waited[0] + " ticks");
                return true;
            }
            if (++waited[0] >= timeout) {
                fail(name, "not within " + timeout + " ticks");
                return true;
            }
            return false;
        });
    }

    /**
     * Watches something for {@code n} ticks: {@code eachTick} returns a problem with this tick, or null; the check
     * fails if any tick had one (the first few are reported), and {@code summary} adds what was measured.
     */
    static void during(String name, int n, Supplier<String> eachTick, Supplier<String> summary) {
        int[] t = {0};
        int[] bad = {0};
        List<String> problems = new ArrayList<>();
        add(name, () -> {
            String p;
            try {
                p = eachTick.get();
            } catch (Throwable e) {
                p = "threw " + e;
            }
            if (p != null) {
                bad[0]++;
                if (problems.size() < 4) {
                    problems.add("tick " + t[0] + ": " + p);
                }
            }
            if (++t[0] >= n) {
                String measured = summary.get();
                report(name, bad[0] == 0 ? null : bad[0] + "/" + n + " ticks wrong (" + String.join("; ", problems) + "); " + measured,
                        measured);
                return true;
            }
            return false;
        });
    }

    /** A screenshot of the next frame drawn. */
    static void shot(String name) {
        boolean[] asked = {false};
        add("screenshot " + name, () -> {
            if (!asked[0]) {
                asked[0] = true;
                shotTaken = false;
                wantShot = name;
                return false;
            }
            return shotTaken;
        });
    }

    /** Binds {@code mapping} to {@code key} as the Controls screen does, and saves the options. */
    static void rebind(KeyMapping mapping, InputConstants.Key key) {
        mapping.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    /**
     * Whether options.txt has {@code mapping} bound to {@code key} (null), or what's wrong: its line is
     * {@code key_<name>:<key>}, with Forge's {@code :<modifier>} after it when there is one.
     */
    @Nullable
    static String saved(KeyMapping mapping, String key) {
        File file = new File(Minecraft.getInstance().gameDirectory, "options.txt");
        String prefix = "key_" + mapping.getName() + ":";
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith(prefix)) {
                    log("options.txt: " + line);
                    String value = line.substring(prefix.length());
                    return value.equals(key) || value.equals(key + ":NONE") ? null : "saved as " + value;
                }
            }
            long keys = lines.stream().filter(l -> l.startsWith("key_")).count();
            List<String> ours = lines.stream().filter(l -> l.contains(GodzillaMinusOne.MODID)).toList();
            return String.format("no %s line in %s (%d lines, %d keys, modified %d ms ago; ours: %s)", prefix, file.getAbsolutePath(),
                    lines.size(), keys, System.currentTimeMillis() - file.lastModified(), ours);
        } catch (IOException e) {
            return file.getAbsolutePath() + ": " + e;
        }
    }

    /** Looks at the next frame drawn (nothing saved). */
    static void probe(String label, Consumer<NativeImage> probe) {
        boolean[] asked = {false};
        add(label, () -> {
            if (!asked[0]) {
                asked[0] = true;
                probed = false;
                wantProbe = probe;
                return false;
            }
            return probed;
        });
    }

    /** How bright (0 to 255) a frame is on average in its middle: a square a tenth as high as the frame. */
    static double middleBrightness(NativeImage image) {
        int side = Math.max(2, image.getHeight() / 10);
        int x0 = (image.getWidth() - side) / 2;
        int y0 = (image.getHeight() - side) / 2;
        double sum = 0.0;
        for (int y = y0; y < y0 + side; y++) {
            for (int x = x0; x < x0 + side; x++) {
                int p = image.getPixelRGBA(x, y); // (ABGR)
                sum += 0.299 * (p & 0xFF) + 0.587 * (p >> 8 & 0xFF) + 0.114 * (p >> 16 & 0xFF);
            }
        }
        return sum / (side * side);
    }

    /** Each pixel's brightness (0 to 255), row by row. */
    static int[] brightness(NativeImage image) {
        int[] out = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getPixelRGBA(x, y); // (ABGR)
                out[y * image.getWidth() + x] = (int) (0.299 * (p & 0xFF) + 0.587 * (p >> 8 & 0xFF) + 0.114 * (p >> 16 & 0xFF));
            }
        }
        return out;
    }

    /** How many pixels are brighter in {@code on} than in {@code off} by more than {@code by}. */
    static int brighter(int[] on, int[] off, int by) {
        int n = 0;
        for (int i = 0; i < Math.min(on.length, off.length); i++) {
            if (on[i] - off[i] > by) {
                n++;
            }
        }
        return n;
    }

    static int now() {
        return ticks;
    }

    // ------------------------------------------------------------------ helpers for the scenes

    /** The first entity of a type the client knows of. */
    @Nullable
    static <T extends Entity> T find(Class<T> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (type.isInstance(entity) && entity.isAlive()) {
                return type.cast(entity);
            }
        }
        return null;
    }

    static ServerPlayer serverPlayer(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayers().get(0);
    }

    /** Yaw and pitch of a look from {@code from} to {@code to}. */
    static float[] look(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        return new float[] {yaw, pitch};
    }

    /** Where {@link #view} puts the camera, and which way it looks (yaw, pitch); null: where the game puts it. */
    @Nullable
    private static Vec3 viewEye;
    private static float[] viewAngles = new float[2];

    /**
     * From a step: puts the camera at {@code eye} looking at {@code target} (third person, so the player is drawn), the
     * player staying where they are (at a gun, at a wheel). {@code view(null, null)} gives the camera back to the game.
     */
    static void view(@Nullable Vec3 eye, @Nullable Vec3 target) {
        viewEye = eye;
        if (eye != null && target != null) {
            viewAngles = look(eye, target);
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ENABLED && viewEye != null) {
            event.setYaw(viewAngles[0]);
            event.setPitch(viewAngles[1]);
            event.setRoll(0.0F);
            CameraPlacement.place(event.getCamera(), viewEye);
        }
    }

    /** A camera: the player as a spectator, eyes at {@code eye}, looking at {@code target}, with this vertical field of view. */
    static void camera(String label, Vec3 eye, Vec3 target, int fov) {
        float[] angles = look(eye, target);
        once("camera " + label + ": the game's", () -> view(null, null));
        server("camera " + label, level -> {
            ServerPlayer player = serverPlayer(level);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, eye.x, eye.y - 1.62, eye.z, angles[0], angles[1]);
        });
        once("fov " + fov, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.fov().set(fov);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.player != null) {
                mc.player.setYRot(angles[0]);
                mc.player.setXRot(angles[1]);
            }
        });
    }

    /** The sounds heard since {@code since} (ticks), whose path (e.g. "godzilla.roar") is {@code path}. */
    static List<Heard> heard(int since, String path) {
        List<Heard> out = new ArrayList<>();
        for (Heard h : HEARD) {
            if (h.tick() >= since && h.sound().getNamespace().equals(GodzillaMinusOne.MODID) && h.sound().getPath().equals(path)) {
                out.add(h);
            }
        }
        return out;
    }

    /** The mod's sounds heard since {@code since}, as text for the log. */
    static String heardSince(int since) {
        StringBuilder sb = new StringBuilder();
        for (Heard h : HEARD) {
            if (h.tick() >= since) {
                sb.append(sb.length() == 0 ? "" : ", ").append(h.sound()).append("@").append(h.tick() - since);
            }
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    // ------------------------------------------------------------------ screenshots

    private static void capture(Minecraft mc, String name) throws IOException {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            File dir = new File(mc.gameDirectory, "screenshots");
            dir.mkdirs();
            image.writeToFile(new File(dir, name + ".png"));
            int w = image.getWidth();
            int h = image.getHeight();
            BufferedImage full = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = image.getPixelRGBA(x, y); // (ABGR)
                    full.setRGB(x, y, (p & 0xFF) << 16 | (p >> 8 & 0xFF) << 8 | (p >> 16 & 0xFF));
                }
            }
            int sw = Math.min(IMAGE_WIDTH, w);
            int sh = Math.max(1, h * sw / w);
            BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(full, 0, 0, sw, sh, null);
            g.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.8F);
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(small, null, null), param);
            }
            writer.dispose();
            String b64 = Base64.getEncoder().encodeToString(bytes.toByteArray());
            int n = (b64.length() + CHUNK - 1) / CHUNK;
            for (int i = 0; i < n; i++) {
                out("GMO-IMG " + name + " " + (i + 1) + "/" + n + " "
                        + b64.substring(i * CHUNK, Math.min(b64.length(), (i + 1) * CHUNK)));
            }
            log("screenshot " + name + " " + w + "x" + h);
        }
    }

    private ShotsHarness() {
    }
}
