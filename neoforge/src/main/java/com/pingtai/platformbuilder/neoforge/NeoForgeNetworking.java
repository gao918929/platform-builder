package com.pingtai.platformbuilder.neoforge;

import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

public class NeoForgeNetworking {

    private static final String PROTOCOL_VERSION = "1.0";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PlatformBuilderNeoForge.MODID).versioned(PROTOCOL_VERSION);

        registrar.playToServer(BuildPayload.TYPE, BuildPayload.STREAM_CODEC, NeoForgeNetworking::handleBuild);
        registrar.playToServer(SyncDesignPayload.TYPE, SyncDesignPayload.STREAM_CODEC, NeoForgeNetworking::handleSyncDesign);
        registrar.playToServer(QuickLoadPayload.TYPE, QuickLoadPayload.STREAM_CODEC, NeoForgeNetworking::handleQuickLoad);
        registrar.playToServer(ExtractAllPayload.TYPE, ExtractAllPayload.STREAM_CODEC, NeoForgeNetworking::handleExtractAll);
        registrar.playToServer(SetSpeedPayload.TYPE, SetSpeedPayload.STREAM_CODEC, NeoForgeNetworking::handleSetSpeed);
        registrar.playToServer(SetOffsetPayload.TYPE, SetOffsetPayload.STREAM_CODEC, NeoForgeNetworking::handleSetOffset);
    }

    public static <T extends CustomPacketPayload> void sendToServer(T payload) {
        PacketDistributor.sendToServer(payload);
    }

    // === Payloads ===

    public record BuildPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<BuildPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlatformBuilderNeoForge.MODID, "build"));
        public static final StreamCodec<FriendlyByteBuf, BuildPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBlockPos(p.pos),
                buf -> new BuildPayload(buf.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record QuickLoadPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<QuickLoadPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlatformBuilderNeoForge.MODID, "quick_load"));
        public static final StreamCodec<FriendlyByteBuf, QuickLoadPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBlockPos(p.pos),
                buf -> new QuickLoadPayload(buf.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ExtractAllPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ExtractAllPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlatformBuilderNeoForge.MODID, "extract_all"));
        public static final StreamCodec<FriendlyByteBuf, ExtractAllPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBlockPos(p.pos),
                buf -> new ExtractAllPayload(buf.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetSpeedPayload(BlockPos pos, int speed) implements CustomPacketPayload {
        public static final Type<SetSpeedPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlatformBuilderNeoForge.MODID, "set_speed"));
        public static final StreamCodec<FriendlyByteBuf, SetSpeedPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeInt(p.speed); },
                buf -> new SetSpeedPayload(buf.readBlockPos(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetOffsetPayload(BlockPos pos, int offset) implements CustomPacketPayload {
        public static final Type<SetOffsetPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlatformBuilderNeoForge.MODID, "set_offset"));
        public static final StreamCodec<FriendlyByteBuf, SetOffsetPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeInt(p.offset); },
                buf -> new SetOffsetPayload(buf.readBlockPos(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncDesignPayload(BlockPos pos, Map<BlockPos, String> design) implements CustomPacketPayload {
        public static final Type<SyncDesignPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(PlatformBuilderNeoForge.MODID, "sync_design"));
        public static final StreamCodec<FriendlyByteBuf, SyncDesignPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.design.size());
                    for (var e : p.design.entrySet()) {
                        buf.writeVarInt(e.getKey().getX());
                        buf.writeVarInt(e.getKey().getY());
                        buf.writeVarInt(e.getKey().getZ());
                        buf.writeUtf(e.getValue());
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int size = buf.readVarInt();
                    Map<BlockPos, String> design = new LinkedHashMap<>();
                    for (int i = 0; i < size; i++) {
                        design.put(new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()), buf.readUtf());
                    }
                    return new SyncDesignPayload(pos, design);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // === Handlers ===

    private static void handleBuild(BuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(payload.pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe && builderBe.canStartBuilding()) {
                    builderBe.startBuilding();
                }
            }
        });
    }

    private static void handleSyncDesign(SyncDesignPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(payload.pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe) {
                    builderBe.setDesign(new LinkedHashMap<>(payload.design));
                }
            }
        });
    }

    private static void handleQuickLoad(QuickLoadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = player.level().getBlockEntity(payload.pos);
            if (!(be instanceof PlatformBuilderBlockEntity builderBe)) return;

            Inventory playerInv = player.getInventory();
            var machineInv = builderBe.getInventory();

            for (int i = 0; i < playerInv.getContainerSize(); i++) {
                ItemStack stack = playerInv.getItem(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;

                for (int slot = 0; slot < machineInv.getContainerSize() && !stack.isEmpty(); slot++) {
                    ItemStack machineStack = machineInv.getItem(slot);
                    if (machineStack.isEmpty()) {
                        machineInv.setItem(slot, stack.copy());
                        stack.setCount(0);
                        break;
                    } else if (ItemStack.isSameItemSameComponents(machineStack, stack)
                            && machineStack.getCount() < machineStack.getMaxStackSize()) {
                        int transfer = Math.min(stack.getCount(),
                                machineStack.getMaxStackSize() - machineStack.getCount());
                        machineStack.grow(transfer);
                        stack.shrink(transfer);
                    }
                }
            }
        });
    }

    private static void handleExtractAll(ExtractAllPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            BlockEntity be = player.level().getBlockEntity(payload.pos);
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
                    machineInv.setItem(slot, extracted);
                }
            }
        });
    }

    private static void handleSetSpeed(SetSpeedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(payload.pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe) {
                    builderBe.setBuildSpeed(payload.speed);
                }
            }
        });
    }

    private static void handleSetOffset(SetOffsetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(payload.pos);
                if (be instanceof PlatformBuilderBlockEntity builderBe) {
                    builderBe.setBuildOffsetY(payload.offset);
                }
            }
        });
    }
}
