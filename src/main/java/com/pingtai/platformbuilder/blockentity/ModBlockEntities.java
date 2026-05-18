package com.pingtai.platformbuilder.blockentity;

import com.pingtai.platformbuilder.PlatformBuilder;
import com.pingtai.platformbuilder.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PlatformBuilder.MODID);

    public static final RegistryObject<BlockEntityType<PlatformBuilderBlockEntity>> PLATFORM_BUILDER_BE =
            BLOCK_ENTITIES.register("platform_builder_be",
                    () -> BlockEntityType.Builder.of(
                            PlatformBuilderBlockEntity::new,
                            ModBlocks.PLATFORM_BUILDER.get()
                    ).build(null));
}
