package com.pingtai.platformbuilder;

import com.pingtai.platformbuilder.block.ModBlocks;
import com.pingtai.platformbuilder.block.ModItems;
import com.pingtai.platformbuilder.blockentity.ModBlockEntities;
import com.pingtai.platformbuilder.network.ModMessages;
import com.pingtai.platformbuilder.screen.ModMenuTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(PlatformBuilder.MODID)
public class PlatformBuilder {

    public static final String MODID = "platformbuilder";

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> PLATFORM_TAB = TABS.register("platform_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocks.PLATFORM_BUILDER.get()))
                    .title(Component.translatable("itemGroup.platformbuilder"))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.PLATFORM_BUILDER.get());
                        output.accept(ModItems.PIG_CERTIFICATE.get());
                    })
                    .build());

    public PlatformBuilder() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        TABS.register(modEventBus);

        ModMessages.init();
    }
}
