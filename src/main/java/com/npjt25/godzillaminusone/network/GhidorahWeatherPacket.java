package com.npjt25.godzillaminusone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: King Ghidorah's weather (entity.GhidorahWeather) is over the player's level now, or not. */
public record GhidorahWeatherPacket(boolean active) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    public static GhidorahWeatherPacket decode(FriendlyByteBuf buf) {
        return new GhidorahWeatherPacket(buf.readBoolean());
    }

    public static void handle(GhidorahWeatherPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.npjt25.godzillaminusone.client.GhidorahSky.set(msg.active()));
    }
}
