package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.ClientConfig;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.client.particle.Fx;
import com.npjt25.godzillaminusone.client.render.NuclearCloud;
import com.npjt25.godzillaminusone.network.CameraPacket;
import com.npjt25.godzillaminusone.network.FxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Client-side state for the add-on's camera shakes, the blurred vision after a blast, black rain and the Godzilla boss bar. */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFx {
    private static final String BOSS_KEY = "boss." + GodzillaMinusOne.MODID + ".godzilla";
    private static final ResourceLocation BAR_EMPTY = GodzillaMinusOne.id("textures/gui/godzilla_bar/health_empty.png");
    private static final ResourceLocation BAR_FULL = GodzillaMinusOne.id("textures/gui/godzilla_bar/health_full.png");
    private static final ResourceLocation BAR_SPINES = GodzillaMinusOne.id("textures/gui/godzilla_bar/center_mark.png");
    private static final ResourceLocation BAR_TEXT = GodzillaMinusOne.id("textures/gui/godzilla_bar/gojira_text.png");
    private static final ResourceLocation BAR_FACE = GodzillaMinusOne.id("textures/gui/godzilla_bar/shadow.png");

    @FunctionalInterface
    public interface EmitterTick {
        void tick(int age);
    }

    private static final class Emitter {
        final int life;
        final EmitterTick tick;
        int age;

        Emitter(int life, EmitterTick tick) {
            this.life = life;
            this.tick = tick;
        }
    }

    private static final List<Emitter> EMITTERS = new ArrayList<>();

    private static float shakeIntensity;
    private static int shakeTicks;
    private static int shakeTotal;

    private static float blurStrength;
    private static float blurIn;
    private static float blurHold;
    private static float blurOut;
    private static int blurAge = -1;

    private static Vec3 rainCenter = Vec3.ZERO;
    private static float rainRadius;
    private static int rainTicks;

    private static int clientTicks;

    /** Runs {@code tick} once per client tick for {@code life} ticks (a Bedrock "steady rate" emitter). */
    public static void addEmitter(int life, EmitterTick tick) {
        EMITTERS.add(new Emitter(life, tick));
    }

    public static void handle(FxPacket msg) {
        switch (msg.type()) {
            case MINE_BLAST -> Fx.mineBlast(msg.pos());
            case ATOMIC_BLAST -> {
                Fx.atomicBlast(msg.pos(), msg.b() > 0.5F);
                NuclearCloud.spawn(msg.pos(), msg.a(), msg.b() < 0.5F);
            }
            case STOMP -> Fx.stomp(msg.pos(), Math.max(0.5F, msg.a()));
            case BOAT_GUN -> Fx.boatGun(msg.pos(), msg.a(), msg.b());
            case BLACK_RAIN -> {
                rainCenter = msg.pos();
                rainRadius = msg.a();
                rainTicks = Math.round(msg.b() * 20.0F);
            }
        }
    }

    public static void handle(CameraPacket msg) {
        if (msg.shakeSeconds() > 0.0F && msg.shakeIntensity() > 0.0F) {
            float current = shakeTotal > 0 ? shakeIntensity * shakeTicks / shakeTotal : 0.0F;
            if (msg.shakeIntensity() >= current) {
                shakeIntensity = msg.shakeIntensity();
                shakeTotal = shakeTicks = Math.max(1, Math.round(msg.shakeSeconds() * 20.0F));
            }
        }
        if (msg.blurStrength() > 0.0F && msg.blurIn() + msg.blurHold() + msg.blurOut() > 0.0F && msg.blurStrength() >= blur(0.0F)) {
            blurStrength = Math.min(msg.blurStrength(), 1.0F);
            blurIn = msg.blurIn() * 20.0F;
            blurHold = msg.blurHold() * 20.0F;
            blurOut = msg.blurOut() * 20.0F;
            blurAge = 0;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        clientTicks++;
        NuclearCloud.tick();
        if (!EMITTERS.isEmpty()) {
            List<Emitter> running = new ArrayList<>(EMITTERS);
            for (Emitter emitter : running) {
                emitter.tick.tick(emitter.age++);
            }
            EMITTERS.removeIf(e -> e.age >= e.life);
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        if (blurAge >= 0 && ++blurAge > blurIn + blurHold + blurOut) {
            blurAge = -1;
        }
        if (rainTicks > 0) {
            rainTicks--;
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            if (cam.distanceToSqr(rainCenter.x, cam.y, rainCenter.z) < rainRadius * rainRadius) {
                Fx.blackRainTick(cam);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EMITTERS.clear();
        shakeTicks = 0;
        blurAge = -1;
        BlurEffect.close();
        NuclearCloud.clear();
        rainTicks = 0;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || shakeTotal <= 0) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float strength = Math.min(12.0F, shakeIntensity * 2.5F) * Mth.clamp((shakeTicks - partial) / shakeTotal, 0.0F, 1.0F);
        float t = clientTicks + partial;
        event.setYaw(event.getYaw() + strength * noise(t * 1.7F));
        event.setPitch(event.getPitch() + strength * noise(t * 2.1F + 37.0F));
        event.setRoll(event.getRoll() + strength * 0.6F * noise(t * 1.3F + 91.0F));
    }

    private static float noise(float t) {
        return (Mth.sin(t) * 0.6F + Mth.sin(t * 2.3F + 1.7F) * 0.3F + Mth.sin(t * 5.1F + 4.2F) * 0.1F);
    }

    /** Current blur strength: fades in, holds, and fades out. */
    private static float blur(float partialTick) {
        if (blurAge < 0) {
            return 0.0F;
        }
        float t = blurAge + partialTick;
        float envelope;
        if (t < blurIn) {
            envelope = t / Math.max(blurIn, 1.0F);
        } else if (t < blurIn + blurHold) {
            envelope = 1.0F;
        } else {
            float x = 1.0F - (t - blurIn - blurHold) / Math.max(blurOut, 1.0F);
            envelope = x * x;
        }
        return blurStrength * Mth.clamp(envelope, 0.0F, 1.0F);
    }

    /** The 3D nuclear explosions; and after a blast the view goes blurry (instead of the add-on's fade to black), then slowly clears. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            NuclearCloud.renderAll(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && blurAge >= 0) {
            BlurEffect.render(blur(event.getPartialTick()));
        }
    }

    /** The add-on's custom HUD boss bar ("GOJIRA", dorsal plates, the glowing blue bar). */
    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents contents) || !BOSS_KEY.equals(contents.getKey())) {
            return;
        }
        event.setCanceled(true);
        if (!ClientConfig.SHOW_BOSS_BAR.get()) {
            event.setIncrement(0); // switched off in the settings: no bar, and no gap where it would be
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        // The add-on's panel, at 60% of its original size.
        int centerX = event.getX() + 91;
        int top = event.getY() - 8;
        int width = 114;
        int height = 64;
        int left = centerX - width / 2;

        graphics.blit(BAR_EMPTY, centerX - 63, top + 1, 126, 60, 0.0F, 0.0F, 64, 64, 64, 64);
        float progress = Mth.clamp(boss.getProgress(), 0.0F, 1.0F);
        int filled = Math.round(width * progress);
        if (filled > 0) {
            graphics.blit(BAR_FULL, left, top, filled, height, 0.0F, 0.0F, Math.max(1, Math.round(64 * progress)), 64, 64, 64);
        }
        graphics.blit(BAR_SPINES, left, top, width, height, 0.0F, 0.0F, 64, 64, 64, 64);
        graphics.blit(BAR_TEXT, left, top, width, height, 0.0F, 0.0F, 64, 64, 64, 64);
        int bob = Math.round(Mth.sin(clientTicks * 0.12F));
        graphics.blit(BAR_FACE, left, top + bob, width, height, 0.0F, 0.0F, 64, 64, 64, 64);

        event.setIncrement(34);
    }

    private ClientFx() {
    }
}
