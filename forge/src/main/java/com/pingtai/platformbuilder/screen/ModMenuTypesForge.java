package com.pingtai.platformbuilder.screen;

import com.pingtai.platformbuilder.forge.PlatformBuilderForge;
import com.pingtai.platformbuilder.block.ModBlocksForge;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypesForge {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PlatformBuilderForge.MODID);

    public static final RegistryObject<MenuType<PlatformBuilderMenu>> PLATFORM_BUILDER_MENU =
            MENU_TYPES.register("platform_builder_menu",
                    () -> {
                        MenuType<PlatformBuilderMenu> type = IForgeMenuType.create(PlatformBuilderMenu::new);
                        PlatformBuilderMenu.setMenuType(type);
                        PlatformBuilderMenu.setPlatformBlock(ModBlocksForge.PLATFORM_BUILDER.get());
                        return type;
                    });
}
