package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A Shinsei Maru crewman's client to the server: fire one round from the stern gun, or drop a mine over the stern
 * (client.ShinseiMaruControls). */
public record ShinseiMaruPacket(int entityId, boolean dropMine) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(dropMine);
    }

    public static ShinseiMaruPacket decode(FriendlyByteBuf buf) {
        return new ShinseiMaruPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ShinseiMaruPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !(player.getVehicle() instanceof ShinseiMaruEntity boat) || boat.getId() != msg.entityId()) {
            return;
        }
        if (msg.dropMine()) {
            if (boat.getControllingPassenger() == player) {
                boat.dropMine(player);
            }
        } else {
            boat.fireGun(player);
        }
    }
}
