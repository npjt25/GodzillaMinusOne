package com.npjt25.godzillaminusone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells clients to play one of the add-on's big particle effects (see client.particle.Fx). */
public record FxPacket(Type type, Vec3 pos, float a, float b) {
    public enum Type {
        /** goji:mind_explosion + goji:mine_shockwave. */
        MINE_BLAST,
        /** The atomic breath impact: flash, flames and shockwave (the fireball and cloud are an entity). a = yaw, b = 1 on the ground. */
        ATOMIC_BLAST,
        /** gojira:black_rain around pos. a = radius, b = seconds. */
        BLACK_RAIN,
        /** Stomp / tail impact ring and dust. a = size. */
        STOMP,
        /** One round from the Shinsei Maru's machine gun: pos = its muzzle, a = yaw, b = pitch. */
        BOAT_GUN
    }

    public FxPacket(Type type, Vec3 pos) {
        this(type, pos, 0.0F, 0.0F);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(a);
        buf.writeFloat(b);
    }

    public static FxPacket decode(FriendlyByteBuf buf) {
        return new FxPacket(buf.readEnum(Type.class), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat());
    }

    public static void handle(FxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.npjt25.godzillaminusone.client.ClientFx.handle(msg));
    }
}
