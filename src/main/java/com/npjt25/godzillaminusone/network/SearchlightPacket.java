package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Whoever is working a searchlight, client to server: switch its lamp off (or back on) (the "Vehicle Action" key, R). */
public record SearchlightPacket(int entityId) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static SearchlightPacket decode(FriendlyByteBuf buf) {
        return new SearchlightPacket(buf.readVarInt());
    }

    public static void handle(SearchlightPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.getVehicle() instanceof SearchlightEntity light && light.getId() == msg.entityId()
                && light.getOperator() == player) {
            light.toggle();
        }
    }
}
