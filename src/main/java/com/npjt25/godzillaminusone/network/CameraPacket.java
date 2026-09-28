package com.npjt25.godzillaminusone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Java stand-in for Bedrock's /camerashake and /camera ... fade commands. */
public record CameraPacket(float shakeIntensity, float shakeSeconds, int fadeColor, float fadeIn, float fadeHold, float fadeOut) {
    public static CameraPacket shake(float intensity, float seconds) {
        return new CameraPacket(intensity, seconds, 0, 0.0F, 0.0F, 0.0F);
    }

    public static CameraPacket fade(int rgb, float in, float hold, float out) {
        return new CameraPacket(0.0F, 0.0F, rgb, in, hold, out);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(shakeIntensity);
        buf.writeFloat(shakeSeconds);
        buf.writeInt(fadeColor);
        buf.writeFloat(fadeIn);
        buf.writeFloat(fadeHold);
        buf.writeFloat(fadeOut);
    }

    public static CameraPacket decode(FriendlyByteBuf buf) {
        return new CameraPacket(buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(CameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.npjt25.godzillaminusone.client.ClientFx.handle(msg));
    }
}
