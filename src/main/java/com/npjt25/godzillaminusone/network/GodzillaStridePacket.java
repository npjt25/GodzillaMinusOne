package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.entity.GodzillaStride;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A player, client to server: as Godzilla, whether they walk through things or climb them (entity.GodzillaStride). */
public record GodzillaStridePacket(boolean walkThrough) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(walkThrough);
    }

    public static GodzillaStridePacket decode(FriendlyByteBuf buf) {
        return new GodzillaStridePacket(buf.readBoolean());
    }

    public static void handle(GodzillaStridePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            GodzillaStride.setWalkThrough(player, msg.walkThrough());
        }
    }
}
