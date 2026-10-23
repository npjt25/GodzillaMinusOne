package com.npjt25.godzillaminusone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.compat.Shapes;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.PlayerGodzilla;
import com.npjt25.godzillaminusone.network.GodzillaMovePacket;
import com.npjt25.godzillaminusone.network.GodzillaMoveRequestPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Playing as Godzilla (Walkers / ReMorphed: entity.PlayerGodzilla), client side: his moves' keys, a small list of them
 * in the top left corner of the screen (with the breath's recharge time), and his animations on every player playing
 * as him: Walkers draws him in their place but never runs him, so here he's told what they're doing.
 *
 * <p>His moves are ordinary key bindings, the only say in which key does what: Options, Controls, Key Binds, Godzilla
 * Minus One ("Godzilla: Tail Whip" Z, "Scratch" H, "Bite" J, "Roar" K, "Atomic Breath" B by default; Walkers' own keys
 * are G, U, R and V, ReMorphed's `), saved with the rest of the game's (options.txt), and the list on screen shows
 * whatever they're bound to. They only count while you're playing as him ({@link ModKeyContexts#GODZILLA_SHAPE}), as
 * whichever of Walkers or ReMorphed made you him: otherwise their keys are free for whatever else is bound to them.
 * As him you see yourself from behind (third person, forced: back to first person when you change back), the camera
 * pulled far enough back for all of him to be in the picture.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlayerGodzillaControls {
    private static final String KEY = "key." + GodzillaMinusOne.MODID + ".godzilla_";
    private static final String CATEGORY = "key.categories.godzillaminusone";
    public static final KeyMapping TAIL_WHIP = key("tail_whip", GLFW.GLFW_KEY_Z);
    public static final KeyMapping SCRATCH = key("scratch", GLFW.GLFW_KEY_H);
    public static final KeyMapping BITE = key("bite", GLFW.GLFW_KEY_J);
    public static final KeyMapping ROAR = key("roar", GLFW.GLFW_KEY_K);
    public static final KeyMapping ATOMIC_BREATH = key("atomic_breath", GLFW.GLFW_KEY_B);
    private static final KeyMapping[] KEYS = {TAIL_WHIP, SCRATCH, BITE, ROAR, ATOMIC_BREATH};
    private static final String HUD = "hud." + GodzillaMinusOne.MODID + ".godzilla.";
    private static final float HUD_SCALE = 0.7F;

    private static int clientTicks;
    /** When this player's atomic breath is ready (client ticks), as the server last told us. */
    private static int breathReadyAt;
    /** Whether we put the camera into third person (to put it back in first person when the player changes back). */
    private static boolean forcedThirdPerson;
    /** Players playing as Godzilla who are charging their breath: when they started (client ticks). */
    private static final Map<Integer, Integer> CHARGING = new HashMap<>();

    private static KeyMapping key(String name, int key) {
        return new KeyMapping(KEY + name, ModKeyContexts.GODZILLA_SHAPE, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    @Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            for (KeyMapping key : KEYS) {
                event.register(key);
            }
        }

        private Keys() {
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        boolean godzilla = mc.player != null && Shapes.godzillaOf(mc.player) != null;
        if (godzilla && mc.options.getCameraType().isFirstPerson()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            forcedThirdPerson = true;
        } else if (!godzilla && forcedThirdPerson) {
            forcedThirdPerson = false;
            if (mc.player != null) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }
        for (int i = 0; i < KEYS.length; i++) {
            while (KEYS[i].consumeClick()) {
                if (godzilla) {
                    ModNetwork.CHANNEL.sendToServer(new GodzillaMoveRequestPacket(i));
                }
            }
        }
        if (mc.level == null) {
            CHARGING.clear();
            return;
        }
        // the charge of a played Godzilla runs as the mob's does: glowing, the beam, cooling down
        for (Iterator<Map.Entry<Integer, Integer>> it = CHARGING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Integer> charge = it.next();
            Entity entity = mc.level.getEntity(charge.getKey());
            GodzillaEntity shape = entity instanceof Player player ? Shapes.godzillaOf(player) : null;
            int t = clientTicks - charge.getValue();
            if (shape == null) {
                it.remove();
            } else if (t >= GodzillaEntity.CHARGE_LENGTH) {
                shape.setShapeCharge(GodzillaEntity.CHARGE_NONE);
                it.remove();
            } else {
                shape.setShapeCharge(t >= GodzillaEntity.BEAM_END_TICK ? GodzillaEntity.CHARGE_COOLING : GodzillaEntity.CHARGE_GLOWING);
            }
        }
    }

    /**
     * As Godzilla, in third person: the camera looks at the middle of him from {@link #cameraDistance} away (instead of
     * Minecraft's 4 blocks from the eyes, which is inside him), closer if the ground or a wall is in the way.
     */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        if (!camera.isDetached() || !(camera.getEntity() instanceof Player player) || Shapes.godzillaOf(player) == null) {
            return;
        }
        Vec3 middle = player.getPosition((float) event.getPartialTick()).add(0.0, player.getBbHeight() * 0.55, 0.0);
        CameraPlacement.pullBack(camera, player, middle, cameraDistance(player));
    }

    /** How far back the camera has to be for all of him, tail and all, to fit in the picture. */
    static double cameraDistance(Player player) {
        return Math.max(4.0, player.getBbHeight() * 1.3 + 6.0);
    }

    /** Before a player is drawn: if they're Godzilla, he's got ready to be drawn as them. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        GodzillaEntity shape = Shapes.godzillaOf(event.getEntity());
        if (shape != null) {
            shape.prepareAsShape(event.getEntity());
        }
    }

    /** A move by a player playing as Godzilla (or the recharge time of this player's breath). */
    public static void handle(GodzillaMovePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (mc.player != null && msg.playerId() == mc.player.getId()) {
            breathReadyAt = clientTicks + msg.breathCooldown();
        }
        if (msg.move() < 0 || !(mc.level.getEntity(msg.playerId()) instanceof Player player)) {
            return;
        }
        GodzillaEntity shape = Shapes.godzillaOf(player);
        if (shape == null) {
            return;
        }
        shape.prepareAsShape(player);
        if (!msg.animation().isEmpty()) {
            shape.playShapeMove(msg.animation());
        }
        if (msg.move() == PlayerGodzilla.Move.ATOMIC_BREATH.ordinal()) {
            CHARGING.put(player.getId(), clientTicks);
            shape.setShapeCharge(GodzillaEntity.CHARGE_GLOWING);
            if (player == mc.player) {
                // (the recharge starts once the breath is over)
                breathReadyAt = clientTicks + msg.breathCooldown();
            }
        }
    }

    /** His moves and their keys, small, in the top left corner, while you're playing as him. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || Shapes.godzillaOf(mc.player) == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(4.0F, 4.0F, 0.0F);
        graphics.pose().scale(HUD_SCALE, HUD_SCALE, 1.0F);
        int y = 0;
        graphics.drawString(mc.font, Component.translatable(HUD + "title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), 0, y, 0xFFFFFF);
        y += 11;
        String[] names = {"tail_whip", "scratch", "bite", "roar"};
        for (int i = 0; i < names.length; i++) {
            graphics.drawString(mc.font, line(KEYS[i], Component.translatable(HUD + names[i])), 0, y, 0xFFFFFF);
            y += 10;
        }
        Component breath;
        Integer charging = CHARGING.get(mc.player.getId());
        int left = breathReadyAt - clientTicks;
        if (charging != null && clientTicks - charging < GodzillaEntity.BEAM_END_TICK) {
            breath = Component.translatable(HUD + "charging").withStyle(ChatFormatting.AQUA);
        } else if (left > 0) {
            int seconds = (left + 19) / 20;
            breath = Component.translatable(HUD + "recharging", seconds / 60, String.format("%02d", seconds % 60)).withStyle(ChatFormatting.GRAY);
        } else {
            breath = Component.translatable(HUD + "ready").withStyle(ChatFormatting.GREEN);
        }
        graphics.drawString(mc.font, line(ATOMIC_BREATH, Component.translatable(HUD + "atomic_breath").append(": ").append(breath)), 0, y, 0xFFFFFF);
        graphics.pose().popPose();
    }

    private static Component line(KeyMapping key, Component name) {
        return Component.literal("[").append(key.getTranslatedKeyMessage()).append("] ").withStyle(ChatFormatting.YELLOW).append(name.copy()
                .withStyle(style -> style.getColor() == null ? style.withColor(ChatFormatting.WHITE) : style));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CHARGING.clear();
        forcedThirdPerson = false;
        breathReadyAt = 0;
    }

    private PlayerGodzillaControls() {
    }
}
