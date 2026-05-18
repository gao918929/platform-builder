package com.pingtai.platformbuilder.forge;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ForgeNetworking {

    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void init() {
        INSTANCE = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(PlatformBuilderForge.MODID, "main"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE.messageBuilder(SyncDesignPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SyncDesignPacket::new)
                .encoder(SyncDesignPacket::encode)
                .consumerMainThread(SyncDesignPacket::handle)
                .add();

        INSTANCE.messageBuilder(BuildPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(BuildPacket::new)
                .encoder(BuildPacket::encode)
                .consumerMainThread(BuildPacket::handle)
                .add();

        INSTANCE.messageBuilder(QuickLoadPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(QuickLoadPacket::new)
                .encoder(QuickLoadPacket::encode)
                .consumerMainThread(QuickLoadPacket::handle)
                .add();

        INSTANCE.messageBuilder(ExtractAllPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ExtractAllPacket::new)
                .encoder(ExtractAllPacket::encode)
                .consumerMainThread(ExtractAllPacket::handle)
                .add();

        INSTANCE.messageBuilder(SetSpeedPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetSpeedPacket::new)
                .encoder(SetSpeedPacket::encode)
                .consumerMainThread(SetSpeedPacket::handle)
                .add();

        INSTANCE.messageBuilder(SetOffsetPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetOffsetPacket::new)
                .encoder(SetOffsetPacket::encode)
                .consumerMainThread(SetOffsetPacket::handle)
                .add();
    }

    public static <T> void sendToServer(T packet) {
        INSTANCE.sendToServer(packet);
    }

    // === Packet classes ===

    public record BuildPacket(BlockPos pos) {
        public BuildPacket(FriendlyByteBuf buf) { this(buf.readBlockPos()); }
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe) {
                    if (builderBe.canStartBuilding()) builderBe.startBuilding();
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record QuickLoadPacket(BlockPos pos) {
        public QuickLoadPacket(FriendlyByteBuf buf) { this(buf.readBlockPos()); }
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(pos);
                if (!(be instanceof PlatformBuilderBlockEntity builderBe)) return;

                Inventory playerInv = player.getInventory();
                var machineInv = builderBe.getInventory();
                int moved = 0;

                for (int i = 0; i < playerInv.getContainerSize(); i++) {
                    ItemStack stack = playerInv.getItem(i);
                    if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;

                    for (int slot = 0; slot < machineInv.getContainerSize() && !stack.isEmpty(); slot++) {
                        ItemStack machineStack = machineInv.getItem(slot);
                        if (machineStack.isEmpty()) {
                            machineInv.setItem(slot, stack.copy());
                            stack.setCount(0);
                            break;
                        } else if (ItemStack.isSameItemSameTags(machineStack, stack)
                                && machineStack.getCount() < machineStack.getMaxStackSize()) {
                            int transfer = Math.min(stack.getCount(),
                                    machineStack.getMaxStackSize() - machineStack.getCount());
                            machineStack.grow(transfer);
                            stack.shrink(transfer);
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ExtractAllPacket(BlockPos pos) {
        public ExtractAllPacket(FriendlyByteBuf buf) { this(buf.readBlockPos()); }
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(pos);
                if (!(be instanceof PlatformBuilderBlockEntity builderBe)) return;

                var machineInv = builderBe.getInventory();
                for (int slot = 0; slot < machineInv.getContainerSize(); slot++) {
                    ItemStack stack = machineInv.getItem(slot);
                    if (stack.isEmpty()) continue;
                    int count = stack.getCount();
                    ItemStack extracted = machineInv.removeItem(slot, count);
                    if (extracted.isEmpty()) continue;
                    player.getInventory().add(extracted);
                    if (!extracted.isEmpty()) {
                        // Return what we couldn't fit
                        int remaining = extracted.getCount();
                        machineInv.setItem(slot, extracted);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SetSpeedPacket(BlockPos pos, int speed) {
        public SetSpeedPacket(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readInt()); }
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeInt(speed); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe) builderBe.setBuildSpeed(speed);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SetOffsetPacket(BlockPos pos, int offset) {
        public SetOffsetPacket(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readInt()); }
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeInt(offset); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe) builderBe.setBuildOffsetY(offset);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncDesignPacket(BlockPos pos, Map<BlockPos, String> design) {
        public SyncDesignPacket(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), readDesign(buf));
        }
        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(design.size());
            for (var entry : design.entrySet()) {
                buf.writeVarInt(entry.getKey().getX());
                buf.writeVarInt(entry.getKey().getY());
                buf.writeVarInt(entry.getKey().getZ());
                buf.writeUtf(entry.getValue());
            }
        }
        private static Map<BlockPos, String> readDesign(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            Map<BlockPos, String> design = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                design.put(new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()), buf.readUtf());
            }
            return design;
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe) {
                    builderBe.setDesign(new LinkedHashMap<>(design));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
