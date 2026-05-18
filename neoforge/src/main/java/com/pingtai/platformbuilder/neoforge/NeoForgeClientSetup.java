package com.pingtai.platformbuilder.neoforge;

import com.pingtai.platformbuilder.screen.ModMenuTypesNeo;
import com.pingtai.platformbuilder.screen.PlatformBuilderScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = PlatformBuilderNeoForge.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NeoForgeClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypesNeo.PLATFORM_BUILDER_MENU.get(), PlatformBuilderScreen::new);
    }
}
