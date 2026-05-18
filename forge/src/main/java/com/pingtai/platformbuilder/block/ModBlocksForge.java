package com.pingtai.platformbuilder.block;

import com.pingtai.platformbuilder.forge.PlatformBuilderForge;
import com.pingtai.platformbuilder.blockentity.ModBlockEntitiesForge;
import com.pingtai.platformbuilder.item.ModItemsForge;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocksForge {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PlatformBuilderForge.MODID);

    public static final RegistryObject<Block> PLATFORM_BUILDER = registerBlock("platform_builder",
            () -> new PlatformBuilderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .noOcclusion()
                    .requiresCorrectToolForDrops(),
                    ModBlockEntitiesForge.PLATFORM_BUILDER_BE));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> registered = BLOCKS.register(name, block);
        registerBlockItem(name, registered);
        return registered;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ModItemsForge.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
