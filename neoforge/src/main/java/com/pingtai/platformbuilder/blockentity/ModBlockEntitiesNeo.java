package com.pingtai.platformbuilder.blockentity;

import com.pingtai.platformbuilder.block.ModBlocksNeo;
import com.pingtai.platformbuilder.neoforge.PlatformBuilderNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntitiesNeo {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlatformBuilderNeoForge.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlatformBuilderBlockEntity>> PLATFORM_BUILDER_BE =
            BLOCK_ENTITIES.register("platform_builder_be",
                    () -> BlockEntityType.Builder.of(
                            PlatformBuilderBlockEntity::new,
                            ModBlocksNeo.PLATFORM_BUILDER.get()
                    ).build(null));
}
