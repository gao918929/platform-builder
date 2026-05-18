package com.pingtai.platformbuilder.item;

import com.pingtai.platformbuilder.forge.PlatformBuilderForge;
import com.pingtai.platformbuilder.item.PigCertificateItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItemsForge {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PlatformBuilderForge.MODID);

    public static final RegistryObject<Item> PIG_CERTIFICATE = ITEMS.register("pig_certificate",
            () -> new PigCertificateItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .stacksTo(1)));
}
