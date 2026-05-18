package com.pingtai.platformbuilder.neoforge;

import com.pingtai.platformbuilder.PlatformServices;
import com.pingtai.platformbuilder.block.ModBlocksNeo;
import com.pingtai.platformbuilder.blockentity.ModBlockEntitiesNeo;
import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import com.pingtai.platformbuilder.item.ModItemsNeo;
import com.pingtai.platformbuilder.screen.ModMenuTypesNeo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(PlatformBuilderNeoForge.MODID)
public class PlatformBuilderNeoForge {

    public static final String MODID = "platformbuilder";

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final Supplier<CreativeModeTab> PLATFORM_TAB = TABS.register("platform_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocksNeo.PLATFORM_BUILDER.get()))
                    .title(Component.translatable("itemGroup.platformbuilder"))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocksNeo.PLATFORM_BUILDER.get());
                        output.accept(ModItemsNeo.PIG_CERTIFICATE.get());
                    })
                    .build());

    public PlatformBuilderNeoForge(IEventBus modEventBus) {
        PlatformBuilderBlockEntity.TYPE = ModBlockEntitiesNeo.PLATFORM_BUILDER_BE;

        ModBlocksNeo.BLOCKS.register(modEventBus);
        ModItemsNeo.ITEMS.register(modEventBus);
        ModBlockEntitiesNeo.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypesNeo.MENU_TYPES.register(modEventBus);
        TABS.register(modEventBus);

        PlatformServices.init(new NeoForgePlatformHelper());
        modEventBus.addListener(NeoForgeNetworking::register);
    }
}
