package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.entity.ShindenEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Pilot's client to server: "my Shinden just flew into something at full speed". The plane's flight is simulated
 * on the pilot's client (like any ridden vehicle), so that is where the crash is noticed.
 */
public record ShindenCrashPacket(int entityId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static ShindenCrashPacket decode(FriendlyByteBuf buf) {
        return new ShindenCrashPacket(buf.readVarInt());
    }

    public static void handle(ShindenCrashPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.getVehicle() instanceof ShindenEntity plane && plane.getId() == msg.entityId()) {
            plane.crash(player);
        }
    }
}
