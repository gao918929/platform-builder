package com.pingtai.platformbuilder.network;

import com.pingtai.platformbuilder.PlatformBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {

    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void init() {
        INSTANCE = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(PlatformBuilder.MODID, "main"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE.messageBuilder(SyncDesignPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SyncDesignPacket::new)
                .encoder(SyncDesignPacket::encode)
                .consumerMainThread(SyncDesignPacket::handle)
                .add();

        INSTANCE.messageBuilder(BuildPlatformPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(BuildPlatformPacket::new)
                .encoder(BuildPlatformPacket::encode)
                .consumerMainThread(BuildPlatformPacket::handle)
                .add();

        INSTANCE.messageBuilder(QuickLoadPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(QuickLoadPacket::new)
                .encoder(QuickLoadPacket::encode)
                .consumerMainThread(QuickLoadPacket::handle)
                .add();

        INSTANCE.messageBuilder(ExtractAllPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ExtractAllPacket::new)
                .encoder(ExtractAllPacket::encode)
                .consumerMainThread(ExtractAllPacket::handle)
                .add();

        INSTANCE.messageBuilder(SetSpeedPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetSpeedPacket::new)
                .encoder(SetSpeedPacket::encode)
                .consumerMainThread(SetSpeedPacket::handle)
                .add();

        INSTANCE.messageBuilder(SetOffsetPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetOffsetPacket::new)
                .encoder(SetOffsetPacket::encode)
                .consumerMainThread(SetOffsetPacket::handle)
                .add();
    }

    public static <T> void sendToServer(T packet) {
        INSTANCE.sendToServer(packet);
    }
}
