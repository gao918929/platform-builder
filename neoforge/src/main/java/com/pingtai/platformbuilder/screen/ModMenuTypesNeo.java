package com.pingtai.platformbuilder.screen;

import com.pingtai.platformbuilder.block.ModBlocksNeo;
import com.pingtai.platformbuilder.neoforge.PlatformBuilderNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypesNeo {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PlatformBuilderNeoForge.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<PlatformBuilderMenu>> PLATFORM_BUILDER_MENU =
            MENU_TYPES.register("platform_builder_menu",
                    () -> {
                        MenuType<PlatformBuilderMenu> type = IMenuTypeExtension.create(PlatformBuilderMenu::new);
                        PlatformBuilderMenu.setMenuType(type);
                        PlatformBuilderMenu.setPlatformBlock(ModBlocksNeo.PLATFORM_BUILDER.get());
                        return type;
                    });
}
