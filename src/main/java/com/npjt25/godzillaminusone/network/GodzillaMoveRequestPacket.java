package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.entity.PlayerGodzilla;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A player playing as Godzilla, client to server: one of his moves (client.PlayerGodzillaControls). */
public record GodzillaMoveRequestPacket(int move) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(move);
    }

    public static GodzillaMoveRequestPacket decode(FriendlyByteBuf buf) {
        return new GodzillaMoveRequestPacket(buf.readVarInt());
    }

    public static void handle(GodzillaMoveRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        PlayerGodzilla.Move[] moves = PlayerGodzilla.Move.values();
        if (player != null && msg.move() >= 0 && msg.move() < moves.length) {
            PlayerGodzilla.use(player, moves[msg.move()]);
        }
    }
}
