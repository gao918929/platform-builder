package com.pingtai.platformbuilder.neoforge;

import com.pingtai.platformbuilder.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

public class NeoForgePlatformHelper implements PlatformHelper {

    @Override
    public void openScreen(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        player.openMenu(provider, buf -> buf.writeBlockPos(pos));
    }

    @Override
    public boolean extractFromAdjacent(Level level, BlockPos machinePos, Block block) {
        // TODO: implement using NeoForge capability system
        return false;
    }

    @Override
    public void sendBuildPacket(BlockPos pos) {
        NeoForgeNetworking.sendToServer(new NeoForgeNetworking.BuildPayload(pos));
    }

    @Override
    public void sendSyncDesignPacket(BlockPos pos, Map<BlockPos, String> design) {
        NeoForgeNetworking.sendToServer(new NeoForgeNetworking.SyncDesignPayload(pos, design));
    }

    @Override
    public void sendQuickLoadPacket(BlockPos pos) {
        NeoForgeNetworking.sendToServer(new NeoForgeNetworking.QuickLoadPayload(pos));
    }

    @Override
    public void sendExtractAllPacket(BlockPos pos) {
        NeoForgeNetworking.sendToServer(new NeoForgeNetworking.ExtractAllPayload(pos));
    }

    @Override
    public void sendSetSpeedPacket(BlockPos pos, int speed) {
        NeoForgeNetworking.sendToServer(new NeoForgeNetworking.SetSpeedPayload(pos, speed));
    }

    @Override
    public void sendSetOffsetPacket(BlockPos pos, int offset) {
        NeoForgeNetworking.sendToServer(new NeoForgeNetworking.SetOffsetPayload(pos, offset));
    }
}
