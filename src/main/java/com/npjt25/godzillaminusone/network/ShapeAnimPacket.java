package com.npjt25.godzillaminusone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to clients: a player playing as one of the mod's creatures makes a move: play {@code animation} on their shape. */
public record ShapeAnimPacket(int playerId, String animation) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeUtf(animation, 64);
    }

    public static ShapeAnimPacket decode(FriendlyByteBuf buf) {
        return new ShapeAnimPacket(buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(ShapeAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.npjt25.godzillaminusone.client.PlayerOdoControls.handle(msg));
    }
}
