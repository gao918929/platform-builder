package com.pingtai.platformbuilder.network;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SyncDesignPacket {

    private final BlockPos blockEntityPos;
    private final Map<BlockPos, String> design;

    public SyncDesignPacket(BlockPos blockEntityPos, Map<BlockPos, String> design) {
        this.blockEntityPos = blockEntityPos;
        this.design = design;
    }

    public SyncDesignPacket(FriendlyByteBuf buf) {
        this.blockEntityPos = buf.readBlockPos();
        int size = buf.readVarInt();
        this.design = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            BlockPos pos = new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            String blockId = buf.readUtf();
            design.put(pos, blockId);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockEntityPos);
        buf.writeVarInt(design.size());
        for (Map.Entry<BlockPos, String> entry : design.entrySet()) {
            buf.writeVarInt(entry.getKey().getX());
            buf.writeVarInt(entry.getKey().getY());
            buf.writeVarInt(entry.getKey().getZ());
            buf.writeUtf(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(blockEntityPos);
            if (be instanceof PlatformBuilderBlockEntity builderBe) {
                Map<BlockPos, String> received = new LinkedHashMap<>(design);
                builderBe.setDesign(received);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
