package com.pingtai.platformbuilder.blockentity;

import com.pingtai.platformbuilder.forge.PlatformBuilderForge;
import com.pingtai.platformbuilder.block.ModBlocksForge;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntitiesForge {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PlatformBuilderForge.MODID);

    public static final RegistryObject<BlockEntityType<PlatformBuilderBlockEntity>> PLATFORM_BUILDER_BE =
            BLOCK_ENTITIES.register("platform_builder_be",
                    () -> BlockEntityType.Builder.of(
                            PlatformBuilderBlockEntity::new,
                            ModBlocksForge.PLATFORM_BUILDER.get()
                    ).build(null));
}
