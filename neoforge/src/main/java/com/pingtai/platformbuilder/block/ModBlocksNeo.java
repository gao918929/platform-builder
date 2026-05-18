package com.pingtai.platformbuilder.block;

import com.pingtai.platformbuilder.blockentity.ModBlockEntitiesNeo;
import com.pingtai.platformbuilder.item.ModItemsNeo;
import com.pingtai.platformbuilder.neoforge.PlatformBuilderNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocksNeo {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, PlatformBuilderNeoForge.MODID);

    public static final DeferredHolder<Block, Block> PLATFORM_BUILDER = registerBlock("platform_builder",
            () -> new PlatformBuilderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .noOcclusion()
                    .requiresCorrectToolForDrops(),
                    ModBlockEntitiesNeo.PLATFORM_BUILDER_BE));

    private static <T extends Block> DeferredHolder<Block, T> registerBlock(String name, Supplier<T> block) {
        DeferredHolder<Block, T> registered = BLOCKS.register(name, block);
        registerBlockItem(name, registered);
        return registered;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredHolder<Block, T> block) {
        ModItemsNeo.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
