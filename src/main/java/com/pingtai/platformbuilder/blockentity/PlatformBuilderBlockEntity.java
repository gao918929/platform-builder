package com.pingtai.platformbuilder.blockentity;

import com.pingtai.platformbuilder.block.ModItems;
import com.pingtai.platformbuilder.screen.PlatformBuilderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlatformBuilderBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INVENTORY_SIZE = 27;
    private int buildSpeed = 50;
    private int buildOffsetY = 0; // vertical offset from machine position

    private final ItemStackHandler itemHandler = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    };

    private LazyOptional<ItemStackHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    // Design data: relative position -> block registry name (e.g. "minecraft:stone")
    private final Map<BlockPos, String> design = new LinkedHashMap<>();

    // Build state
    private boolean isBuilding = false;
    private final Queue<Map.Entry<BlockPos, String>> buildQueue = new ArrayDeque<>();
    private int buildCooldown = 0;

    public PlatformBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLATFORM_BUILDER_BE.get(), pos, state);
    }

    // === Design management ===

    public void setDesign(Map<BlockPos, String> newDesign) {
        design.clear();
        design.putAll(newDesign);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public Map<BlockPos, String> getDesign() {
        return Collections.unmodifiableMap(design);
    }

    public void toggleBlock(BlockPos relativePos, String blockId) {
        if (design.containsKey(relativePos) && design.get(relativePos).equals(blockId)) {
            design.remove(relativePos);
        } else {
            design.put(relativePos, blockId);
        }
        setChanged();
    }

    public void clearDesign() {
        design.clear();
        buildQueue.clear();
        isBuilding = false;
        setChanged();
    }

    // === Build logic ===

    public boolean canStartBuilding() {
        return !design.isEmpty() && !isBuilding;
    }

    public boolean isBuilding() {
        return isBuilding;
    }

    public void startBuilding() {
        if (!canStartBuilding()) return;

        buildQueue.clear();
        // Sort by distance for logical build order
        design.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().distManhattan(BlockPos.ZERO)))
                .forEach(buildQueue::add);

        isBuilding = true;
        buildCooldown = 0;
        setChanged();
    }

    public void stopBuilding() {
        isBuilding = false;
        buildQueue.clear();
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PlatformBuilderBlockEntity be) {
        if (!be.isBuilding || be.buildQueue.isEmpty()) {
            if (be.isBuilding && be.buildQueue.isEmpty()) {
                be.isBuilding = false;
                be.setChanged();
            }
            return;
        }

        if (be.buildCooldown > 0) {
            be.buildCooldown--;
            return;
        }

        int placed = 0;
        int skipped = 0;
        int queueSize = be.buildQueue.size();

        while (placed < be.buildSpeed && !be.buildQueue.isEmpty() && skipped < queueSize) {
            Map.Entry<BlockPos, String> entry = be.buildQueue.poll();
            BlockPos worldPos = pos.offset(entry.getKey()).offset(0, be.buildOffsetY, 0);

            if (be.tryPlaceBlock(level, worldPos, entry.getValue())) {
                placed++;
            } else {
                be.buildQueue.offer(entry); // move to end, retry later
                skipped++;
            }
        }

        be.buildCooldown = 0;
        be.setChanged();
    }

    private boolean tryPlaceBlock(Level level, BlockPos targetPos, String blockId) {
        if (!level.isLoaded(targetPos)) return false;

        BlockState existingState = level.getBlockState(targetPos);
        if (!existingState.isAir() && !existingState.canBeReplaced()) return false;

        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (block == null) return false;

        // Pig Certificate: infinite concrete without consuming materials
        if (hasPigCertificate() && isConcrete(blockId)) {
            return level.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL);
        }

        // Try internal inventory first
        int slot = findMaterialSlot(block);
        if (slot >= 0) {
            ItemStack simulated = itemHandler.extractItem(slot, 1, true);
            if (simulated.getCount() >= 1) {
                boolean placed = level.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL);
                if (placed) itemHandler.extractItem(slot, 1, false);
                return placed;
            }
        }

        // Fallback: try adjacent inventories (chests, barrels, hoppers, etc.)
        if (extractFromAdjacent(block)) {
            return level.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL);
        }

        return false;
    }

    public boolean hasPigCertificate() {
        Item certItem = ModItems.PIG_CERTIFICATE.get();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (itemHandler.getStackInSlot(i).is(certItem)) {
                return true;
            }
        }
        return false;
    }

    public int getBuildSpeed() { return buildSpeed; }

    public void setBuildSpeed(int speed) {
        this.buildSpeed = Math.max(1, Math.min(speed, 500));
        setChanged();
    }

    public int getBuildOffsetY() { return buildOffsetY; }

    public void setBuildOffsetY(int offset) {
        this.buildOffsetY = Math.max(-64, Math.min(offset, 64));
        setChanged();
    }

    private static boolean isConcrete(String blockId) {
        return blockId.endsWith("_concrete") || blockId.endsWith("_concrete_powder");
    }

    private int findMaterialSlot(Block block) {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == block) return i;
            }
        }
        return -1;
    }

    /** Extract one of the given block from any adjacent inventory */
    private boolean extractFromAdjacent(Block block) {
        if (level == null) return false;
        for (Direction dir : Direction.values()) {
            BlockEntity adjBe = level.getBlockEntity(worldPosition.relative(dir));
            if (adjBe == null) continue;
            boolean[] found = {false};
            adjBe.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).ifPresent(adjInv -> {
                for (int i = 0; i < adjInv.getSlots() && !found[0]; i++) {
                    ItemStack stack = adjInv.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi
                            && bi.getBlock() == block) {
                        ItemStack extracted = adjInv.extractItem(i, 1, false);
                        if (!extracted.isEmpty()) found[0] = true;
                    }
                }
            });
            if (found[0]) return true;
        }
        return false;
    }

    // === Inventory ===

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    // === NBT ===

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());
        tag.putBoolean("isBuilding", isBuilding);
        tag.putInt("buildSpeed", buildSpeed);
        tag.putInt("buildOffsetY", buildOffsetY);
        saveDesign(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("inventory"));
        isBuilding = tag.getBoolean("isBuilding");
        buildSpeed = tag.contains("buildSpeed") ? tag.getInt("buildSpeed") : 50;
        buildOffsetY = tag.contains("buildOffsetY") ? tag.getInt("buildOffsetY") : 0;
        loadDesign(tag);
    }

    private void saveDesign(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, String> entry : design.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("x", entry.getKey().getX());
            entryTag.putInt("y", entry.getKey().getY());
            entryTag.putInt("z", entry.getKey().getZ());
            entryTag.putString("block", entry.getValue());
            list.add(entryTag);
        }
        tag.put("design", list);
    }

    private void loadDesign(CompoundTag tag) {
        design.clear();
        ListTag list = tag.getList("design", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = new BlockPos(
                    entryTag.getInt("x"),
                    entryTag.getInt("y"),
                    entryTag.getInt("z")
            );
            String blockId = entryTag.getString("block");
            design.put(pos, blockId);
        }
    }

    // === Sync ===

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveDesign(tag);
        tag.putInt("buildSpeed", buildSpeed);
        tag.putInt("buildOffsetY", buildOffsetY);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        loadDesign(tag);
        buildSpeed = tag.contains("buildSpeed") ? tag.getInt("buildSpeed") : 50;
        buildOffsetY = tag.contains("buildOffsetY") ? tag.getInt("buildOffsetY") : 0;
    }

    // === Capability ===

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    // === MenuProvider ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.platformbuilder.platform_builder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PlatformBuilderMenu(containerId, playerInventory, this);
    }
}
