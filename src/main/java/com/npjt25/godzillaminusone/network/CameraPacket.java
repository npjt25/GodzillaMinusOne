package com.npjt25.godzillaminusone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Java stand-in for Bedrock's /camerashake, plus the blurred vision after a blast (which replaces the add-on's
 * /camera ... fade to black). Blur strength is 0-1; it fades in, holds and fades out over the given seconds.
 */
public record CameraPacket(float shakeIntensity, float shakeSeconds, float blurStrength, float blurIn, float blurHold, float blurOut) {
    public static CameraPacket shake(float intensity, float seconds) {
        return new CameraPacket(intensity, seconds, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public static CameraPacket blur(float strength, float in, float hold, float out) {
        return new CameraPacket(0.0F, 0.0F, strength, in, hold, out);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(shakeIntensity);
        buf.writeFloat(shakeSeconds);
        buf.writeFloat(blurStrength);
        buf.writeFloat(blurIn);
        buf.writeFloat(blurHold);
        buf.writeFloat(blurOut);
    }

    public static CameraPacket decode(FriendlyByteBuf buf) {
        return new CameraPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(CameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.npjt25.godzillaminusone.client.ClientFx.handle(msg));
    }
}
