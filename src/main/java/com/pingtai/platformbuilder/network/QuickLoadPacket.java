package com.pingtai.platformbuilder.network;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class QuickLoadPacket {

    private final BlockPos pos;

    public QuickLoadPacket(BlockPos pos) { this.pos = pos; }

    public QuickLoadPacket(FriendlyByteBuf buf) { this.pos = buf.readBlockPos(); }

    public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Level level = player.level();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof PlatformBuilderBlockEntity builderBe)) return;

            Inventory playerInv = player.getInventory();
            IItemHandler machineInv = builderBe.getItemHandler();
            int moved = 0;

            // Iterate all player inventory slots (main 36 + armor + offhand)
            for (int i = 0; i < playerInv.getContainerSize(); i++) {
                ItemStack stack = playerInv.getItem(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;

                // Try to insert into machine slots
                ItemStack remaining = stack.copy();
                for (int slot = 0; slot < machineInv.getSlots() && !remaining.isEmpty(); slot++) {
                    remaining = machineInv.insertItem(slot, remaining, false);
                }

                int inserted = stack.getCount() - remaining.getCount();
                if (inserted > 0) {
                    stack.shrink(inserted);
                    if (stack.isEmpty()) playerInv.setItem(i, ItemStack.EMPTY);
                    moved += inserted;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
