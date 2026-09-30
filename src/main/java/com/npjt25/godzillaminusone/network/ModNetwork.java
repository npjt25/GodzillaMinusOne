package com.npjt25.godzillaminusone.network;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            GodzillaMinusOne.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(FxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode).decoder(FxPacket::decode).consumerMainThread(FxPacket::handle).add();
        CHANNEL.messageBuilder(CameraPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CameraPacket::encode).decoder(CameraPacket::decode).consumerMainThread(CameraPacket::handle).add();
        CHANNEL.messageBuilder(ShindenCrashPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShindenCrashPacket::encode).decoder(ShindenCrashPacket::decode).consumerMainThread(ShindenCrashPacket::handle).add();
        CHANNEL.messageBuilder(ShindenFirePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShindenFirePacket::encode).decoder(ShindenFirePacket::decode).consumerMainThread(ShindenFirePacket::handle).add();
        CHANNEL.messageBuilder(ShinseiMaruPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShinseiMaruPacket::encode).decoder(ShinseiMaruPacket::decode).consumerMainThread(ShinseiMaruPacket::handle).add();
    }

    public static void send(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Sends to every player within {@code radius} blocks of {@code pos} in the same dimension. */
    public static void sendNear(ServerLevel level, Vec3 pos, double radius, Object message) {
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(pos.x, pos.y, pos.z, radius, level.dimension())), message);
    }

    /** Camera shake for every player within {@code radius}; strength fades out towards the edge. */
    public static void shake(ServerLevel level, Vec3 pos, double radius, float intensity, float seconds) {
        for (ServerPlayer player : level.players()) {
            double dist = player.position().distanceTo(pos);
            if (dist <= radius) {
                float falloff = (float) (1.0 - 0.6 * dist / radius);
                send(player, CameraPacket.shake(intensity * falloff, seconds));
            }
        }
    }

    /** Blurred vision for every player within {@code radius}; weaker towards the edge. */
    public static void blur(ServerLevel level, Vec3 pos, double radius, float strength, float in, float hold, float out) {
        for (ServerPlayer player : level.players()) {
            double dist = player.position().distanceTo(pos);
            if (dist <= radius) {
                float falloff = (float) (1.0 - 0.5 * dist / radius);
                send(player, CameraPacket.blur(strength * falloff, in, hold, out));
            }
        }
    }

    private ModNetwork() {
    }
}
