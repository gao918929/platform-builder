package com.pingtai.platformbuilder.network;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetOffsetPacket {

    private final BlockPos pos;
    private final int offsetY;

    public SetOffsetPacket(BlockPos pos, int offsetY) { this.pos = pos; this.offsetY = offsetY; }

    public SetOffsetPacket(FriendlyByteBuf buf) { this.pos = buf.readBlockPos(); this.offsetY = buf.readInt(); }

    public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeInt(offsetY); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PlatformBuilderBlockEntity builderBe) {
                builderBe.setBuildOffsetY(offsetY);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
