package com.npjt25.godzillaminusone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to clients: a player playing as Godzilla made a move (entity.PlayerGodzilla): the animation to play on their
 * shape ({@code move} -1: none, just the breath's recharge time for the player themselves), and how long until their
 * atomic breath is ready again, in ticks.
 */
public record GodzillaMovePacket(int playerId, int move, int breathCooldown, String animation) {
    public GodzillaMovePacket(int playerId, int move, int breathCooldown) {
        this(playerId, move, breathCooldown, "");
    }

    public GodzillaMovePacket {
        animation = animation == null ? "" : animation;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeVarInt(move);
        buf.writeVarInt(breathCooldown);
        buf.writeUtf(animation, 64);
    }

    public static GodzillaMovePacket decode(FriendlyByteBuf buf) {
        return new GodzillaMovePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(GodzillaMovePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.npjt25.godzillaminusone.client.PlayerGodzillaControls.handle(msg));
    }
}
