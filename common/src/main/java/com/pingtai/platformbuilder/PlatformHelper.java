package com.pingtai.platformbuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

public interface PlatformHelper {

    void openScreen(ServerPlayer player, MenuProvider provider, BlockPos pos);

    /** Try to extract one of the given block from any adjacent inventory. */
    boolean extractFromAdjacent(Level level, BlockPos machinePos, Block block);

    // --- Network ---

    void sendBuildPacket(BlockPos pos);

    void sendSyncDesignPacket(BlockPos pos, Map<BlockPos, String> design);

    void sendQuickLoadPacket(BlockPos pos);

    void sendExtractAllPacket(BlockPos pos);

    void sendSetSpeedPacket(BlockPos pos, int speed);

    void sendSetOffsetPacket(BlockPos pos, int offset);
}
