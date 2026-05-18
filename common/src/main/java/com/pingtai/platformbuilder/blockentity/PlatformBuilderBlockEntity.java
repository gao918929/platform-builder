package com.pingtai.platformbuilder.blockentity;

import com.pingtai.platformbuilder.PlatformServices;
import com.pingtai.platformbuilder.screen.PlatformBuilderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class PlatformBuilderBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INVENTORY_SIZE = 27;

    /** Set by the platform module before block registration. */
    public static Supplier<BlockEntityType<PlatformBuilderBlockEntity>> TYPE;

    private int buildSpeed = 50;
    private int buildOffsetY = 0;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    private final Map<BlockPos, String> design = new LinkedHashMap<>();

    private boolean isBuilding = false;
    private final Queue<Map.Entry<BlockPos, String>> buildQueue = new ArrayDeque<>();
    private int buildCooldown = 0;

    /** Used by {@link BlockEntityType.Builder#of}. */
    public PlatformBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE.get(), pos, state);
    }

    /** Used by {@link com.pingtai.platformbuilder.block.PlatformBuilderBlock#newBlockEntity}. */
    public PlatformBuilderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
                be.buildQueue.offer(entry);
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

        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
        if (block == null) return false;

        // Pig Certificate: infinite concrete without consuming materials
        if (hasPigCertificate() && isConcrete(blockId)) {
            return level.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL);
        }

        // Try internal inventory first
        int slot = findMaterialSlot(block);
        if (slot >= 0) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getCount() >= 1) {
                boolean placed = level.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL);
                if (placed) {
                    stack.shrink(1);
                    if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
                }
                return placed;
            }
        }

        // Fallback: try adjacent inventories
        if (PlatformServices.PLATFORM.extractFromAdjacent(level, worldPosition, block)) {
            return level.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL);
        }

        return false;
    }

    public boolean hasPigCertificate() {
        Item certItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("platformbuilder", "pig_certificate"));
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).is(certItem)) {
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
        return blockId.endsWith("_concrete");
    }

    private int findMaterialSlot(Block block) {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == block) return i;
            }
        }
        return -1;
    }

    // === Inventory ===

    public SimpleContainer getInventory() {
        return inventory;
    }

    // === NBT ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag invList = new ListTag();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("Slot", i);
                slotTag.put("item", stack.save(registries));
                invList.add(slotTag);
            }
        }
        tag.put("inventory", invList);
        tag.putBoolean("isBuilding", isBuilding);
        tag.putInt("buildSpeed", buildSpeed);
        tag.putInt("buildOffsetY", buildOffsetY);
        saveDesign(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.clearContent();
        ListTag invList = tag.getList("inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < invList.size(); i++) {
            CompoundTag slotTag = invList.getCompound(i);
            int slot = slotTag.getInt("Slot");
            ItemStack.parse(registries, slotTag.getCompound("item"))
                    .ifPresent(stack -> inventory.setItem(slot, stack));
        }
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveDesign(tag);
        tag.putInt("buildSpeed", buildSpeed);
        tag.putInt("buildOffsetY", buildOffsetY);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadDesign(tag);
        buildSpeed = tag.contains("buildSpeed") ? tag.getInt("buildSpeed") : 50;
        buildOffsetY = tag.contains("buildOffsetY") ? tag.getInt("buildOffsetY") : 0;
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
