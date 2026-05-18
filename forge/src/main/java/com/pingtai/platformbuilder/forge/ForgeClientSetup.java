package com.pingtai.platformbuilder.forge;

import com.pingtai.platformbuilder.screen.ModMenuTypesForge;
import com.pingtai.platformbuilder.screen.PlatformBuilderScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = PlatformBuilderForge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ForgeClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypesForge.PLATFORM_BUILDER_MENU.get(), PlatformBuilderScreen::new);
        });
    }
}
