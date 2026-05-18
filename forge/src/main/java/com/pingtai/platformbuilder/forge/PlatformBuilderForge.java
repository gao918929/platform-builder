package com.pingtai.platformbuilder.forge;

import com.pingtai.platformbuilder.PlatformServices;
import com.pingtai.platformbuilder.block.ModBlocksForge;
import com.pingtai.platformbuilder.blockentity.ModBlockEntitiesForge;
import com.pingtai.platformbuilder.blockentity.PlatformBuilderBlockEntity;
import com.pingtai.platformbuilder.item.ModItemsForge;
import com.pingtai.platformbuilder.screen.ModMenuTypesForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(PlatformBuilderForge.MODID)
public class PlatformBuilderForge {

    public static final String MODID = "platformbuilder";

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> PLATFORM_TAB = TABS.register("platform_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocksForge.PLATFORM_BUILDER.get()))
                    .title(Component.translatable("itemGroup.platformbuilder"))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocksForge.PLATFORM_BUILDER.get());
                        output.accept(ModItemsForge.PIG_CERTIFICATE.get());
                    })
                    .build());

    public PlatformBuilderForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        PlatformBuilderBlockEntity.TYPE = ModBlockEntitiesForge.PLATFORM_BUILDER_BE;

        ModBlocksForge.BLOCKS.register(modEventBus);
        ModItemsForge.ITEMS.register(modEventBus);
        ModBlockEntitiesForge.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypesForge.MENU_TYPES.register(modEventBus);
        TABS.register(modEventBus);

        PlatformServices.init(new ForgePlatformHelper());
        ForgeNetworking.init();
    }
}
