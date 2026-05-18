package com.pingtai.platformbuilder.screen;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class PlatformBuilderMenu extends AbstractContainerMenu {

    @Nullable
    public final PlatformBuilderBlockEntity blockEntity;
    private final Level level;
    private static final int BE_SLOTS = PlatformBuilderBlockEntity.INVENTORY_SIZE;
    private static final int PLAYER_SLOTS = 36;

    static final int MACHINE_Y = 32;
    static final int PLAYER_MAIN_Y = 98;
    static final int PLAYER_HOTBAR_Y = 162;

    private static MenuType<PlatformBuilderMenu> menuType;
    private static Block platformBlock;

    public static void setMenuType(MenuType<PlatformBuilderMenu> type) {
        menuType = type;
    }

    public static void setPlatformBlock(Block block) {
        platformBlock = block;
    }

    /** Vanilla-compatible constructor for MenuType registration. */
    public PlatformBuilderMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, (PlatformBuilderBlockEntity) null);
    }

    public PlatformBuilderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntityFromBuf(playerInventory, extraData));
    }

    public PlatformBuilderMenu(int containerId, Inventory playerInventory, @Nullable PlatformBuilderBlockEntity be) {
        super(menuType, containerId);
        this.blockEntity = be;
        this.level = playerInventory.player.level();

        if (be != null) {
            var inv = be.getInventory();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = col + row * 9;
                    addSlot(new Slot(inv, idx, 8 + col * 18, MACHINE_Y + row * 18));
                }
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, PLAYER_MAIN_Y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    private static PlatformBuilderBlockEntity getBlockEntityFromBuf(Inventory playerInventory, FriendlyByteBuf extraData) {
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
        if (blockEntity == null) return true;
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                player, platformBlock);
    }

    public PlatformBuilderBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
