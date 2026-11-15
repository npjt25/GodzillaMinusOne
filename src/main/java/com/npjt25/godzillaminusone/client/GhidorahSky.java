package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * King Ghidorah's weather on this client (entity.GhidorahWeather): no rain, the sky overcast. Coming on and clearing
 * over FADE_TICKS, it turns the sky a dark grey and the clouds a heavy one (mixin.ClientLevelMixin), dims the daylight
 * about as much as a thunderstorm does, and greys the fog to match. (Its lightning is the game's own: flash and thunder.)
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GhidorahSky {
    private static final int FADE_TICKS = 100;
    private static boolean active;
    private static float level;
    private static float levelO;

    private GhidorahSky() {
    }

    public static void set(boolean on) {
        active = on;
    }

    public static boolean active() {
        return active;
    }

    /** How far it's come on (0 to 1), {@code partialTick} on from the last tick. */
    public static float factor(float partialTick) {
        return Mth.lerp(partialTick, levelO, level);
    }

    /** {@code color} under the overcast, {@code f} of the way in: greyed and darkened to {@code dark} of its brightness. */
    public static Vec3 overcast(Vec3 color, float f, double dark) {
        double grey = (color.x * 0.3 + color.y * 0.59 + color.z * 0.11) * dark;
        double k = 0.85 * f;
        return new Vec3(color.x * (1.0 - k) + grey * k, color.y * (1.0 - k) + grey * k, color.z * (1.0 - k) + grey * k);
    }

    /** The daylight's brightness ({@code darken}, the game's sky darkening, 0.2 to 1) under the overcast. */
    public static float daylight(float darken, float f) {
        float light = (darken - 0.2F) / 0.8F;
        return light * (1.0F - 0.5F * f) * 0.8F + 0.2F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        levelO = level;
        level = Mth.clamp(level + (active ? 1.0F : -1.0F) / FADE_TICKS, 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float f = factor((float) event.getPartialTick());
        if (f <= 0.0F) {
            return;
        }
        Vec3 fog = overcast(new Vec3(event.getRed(), event.getGreen(), event.getBlue()), f, 0.45);
        event.setRed((float) fog.x);
        event.setGreen((float) fog.y);
        event.setBlue((float) fog.z);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        level = 0.0F;
        levelO = 0.0F;
    }
}
