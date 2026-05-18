package com.pingtai.platformbuilder.screen;

import com.pingtai.platformbuilder.PlatformBuilder;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PlatformBuilder.MODID);

    public static final RegistryObject<MenuType<PlatformBuilderMenu>> PLATFORM_BUILDER_MENU =
            MENU_TYPES.register("platform_builder_menu",
                    () -> IForgeMenuType.create(PlatformBuilderMenu::new));
}
