package com.pingtai.platformbuilder.network;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExtractAllPacket {

    private final BlockPos pos;

    public ExtractAllPacket(BlockPos pos) { this.pos = pos; }

    public ExtractAllPacket(FriendlyByteBuf buf) { this.pos = buf.readBlockPos(); }

    public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof PlatformBuilderBlockEntity builderBe)) return;

            IItemHandler machineInv = builderBe.getItemHandler();
            for (int slot = 0; slot < machineInv.getSlots(); slot++) {
                ItemStack stack = machineInv.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                int count = stack.getCount();
                ItemStack extracted = machineInv.extractItem(slot, count, false);
                if (extracted.isEmpty()) continue;
                player.getInventory().add(extracted);
                if (!extracted.isEmpty()) {
                    machineInv.insertItem(slot, extracted, false);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
