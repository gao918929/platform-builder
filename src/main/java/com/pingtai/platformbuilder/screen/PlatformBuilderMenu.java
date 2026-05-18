package com.pingtai.platformbuilder.screen;

import com.pingtai.platformbuilder.block.ModBlocks;
import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.SlotItemHandler;

public class PlatformBuilderMenu extends AbstractContainerMenu {

    public final PlatformBuilderBlockEntity blockEntity;
    private final Level level;
    private static final int BE_SLOTS = PlatformBuilderBlockEntity.INVENTORY_SIZE;
    private static final int PLAYER_SLOTS = 36;

    // Slot Y positions — must be > DESIGN_H (220) to hide in design mode
    static final int MACHINE_Y = 230;
    static final int PLAYER_MAIN_Y = 300;
    static final int PLAYER_HOTBAR_Y = 362;

    public PlatformBuilderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData));
    }

    public PlatformBuilderMenu(int containerId, Inventory playerInventory, PlatformBuilderBlockEntity be) {
        super(ModMenuTypes.PLATFORM_BUILDER_MENU.get(), containerId);
        this.blockEntity = be;
        this.level = playerInventory.player.level();

        // Machine inventory — 3 rows × 9 cols
        be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new SlotItemHandler(handler, col + row * 9,
                            8 + col * 18, MACHINE_Y + row * 18));
                }
            }
        });

        // Player main inventory — 3 rows × 9 cols
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, PLAYER_MAIN_Y + row * 18));
            }
        }

        // Player hotbar — 1 row × 9
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    8 + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    private static PlatformBuilderBlockEntity getBlockEntity(Inventory playerInventory, FriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof PlatformBuilderBlockEntity builderBe) return builderBe;
        throw new IllegalStateException("Expected PlatformBuilderBlockEntity at " + pos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index < BE_SLOTS) {
            if (!this.moveItemStackTo(stack, BE_SLOTS, BE_SLOTS + PLAYER_SLOTS, true))
                return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, 0, BE_SLOTS, false))
                return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                player, ModBlocks.PLATFORM_BUILDER.get());
    }

    public PlatformBuilderBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
