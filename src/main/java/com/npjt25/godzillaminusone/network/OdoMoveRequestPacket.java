package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.entity.PlayerOdo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A player playing as the Odo Island Monster, client to server: a bite (client.PlayerOdoControls). */
public record OdoMoveRequestPacket(int action, boolean on) {
    /** (Was the Run key, held or let go: he now moves at his pace without one.) */
    public static final int RUN = 0;
    public static final int BITE = 1;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeBoolean(on);
    }

    public static OdoMoveRequestPacket decode(FriendlyByteBuf buf) {
        return new OdoMoveRequestPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(OdoMoveRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        if (msg.action() == BITE) {
            PlayerOdo.bite(player);
        }
    }
}
