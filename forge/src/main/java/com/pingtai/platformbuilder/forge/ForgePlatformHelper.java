package com.pingtai.platformbuilder.forge;

import com.pingtai.platformbuilder.PlatformHelper;
import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkHooks;

import java.util.Map;

public class ForgePlatformHelper implements PlatformHelper {

    @Override
    public void openScreen(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        NetworkHooks.openScreen(player, provider, pos);
    }

    @Override
    public boolean extractFromAdjacent(Level level, BlockPos machinePos, Block block) {
        for (Direction dir : Direction.values()) {
            BlockEntity adjBe = level.getBlockEntity(machinePos.relative(dir));
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

    @Override
    public void sendBuildPacket(BlockPos pos) {
        ForgeNetworking.sendToServer(new ForgeNetworking.BuildPacket(pos));
    }

    @Override
    public void sendSyncDesignPacket(BlockPos pos, Map<BlockPos, String> design) {
        ForgeNetworking.sendToServer(new ForgeNetworking.SyncDesignPacket(pos, design));
    }

    @Override
    public void sendQuickLoadPacket(BlockPos pos) {
        ForgeNetworking.sendToServer(new ForgeNetworking.QuickLoadPacket(pos));
    }

    @Override
    public void sendExtractAllPacket(BlockPos pos) {
        ForgeNetworking.sendToServer(new ForgeNetworking.ExtractAllPacket(pos));
    }

    @Override
    public void sendSetSpeedPacket(BlockPos pos, int speed) {
        ForgeNetworking.sendToServer(new ForgeNetworking.SetSpeedPacket(pos, speed));
    }

    @Override
    public void sendSetOffsetPacket(BlockPos pos, int offset) {
        ForgeNetworking.sendToServer(new ForgeNetworking.SetOffsetPacket(pos, offset));
    }
}
