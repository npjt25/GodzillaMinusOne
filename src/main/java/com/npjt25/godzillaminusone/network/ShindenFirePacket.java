package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.entity.ShindenEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Pilot's client to server: "fire the guns" (sent while right-click is held: client.ShindenControls; the plane keeps the rate of fire). */
public record ShindenFirePacket(int entityId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static ShindenFirePacket decode(FriendlyByteBuf buf) {
        return new ShindenFirePacket(buf.readVarInt());
    }

    public static void handle(ShindenFirePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.getVehicle() instanceof ShindenEntity plane && plane.getId() == msg.entityId()
                && plane.getControllingPassenger() == player) {
            plane.tryFireGuns(player);
        }
    }
}
