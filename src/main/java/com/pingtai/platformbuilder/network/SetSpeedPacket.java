package com.pingtai.platformbuilder.network;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetSpeedPacket {

    private final BlockPos pos;
    private final int speed;

    public SetSpeedPacket(BlockPos pos, int speed) { this.pos = pos; this.speed = speed; }

    public SetSpeedPacket(FriendlyByteBuf buf) { this.pos = buf.readBlockPos(); this.speed = buf.readInt(); }

    public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeInt(speed); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PlatformBuilderBlockEntity builderBe) {
                builderBe.setBuildSpeed(speed);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
