package com.pingtai.platformbuilder.network;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BuildPlatformPacket {

    private final BlockPos blockEntityPos;

    public BuildPlatformPacket(BlockPos blockEntityPos) {
        this.blockEntityPos = blockEntityPos;
    }

    public BuildPlatformPacket(FriendlyByteBuf buf) {
        this.blockEntityPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockEntityPos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(blockEntityPos);
            if (be instanceof PlatformBuilderBlockEntity builderBe) {
                if (builderBe.canStartBuilding()) {
                    builderBe.startBuilding();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
