package com.pingtai.platformbuilder.item;

import com.pingtai.platformbuilder.neoforge.PlatformBuilderNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItemsNeo {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, PlatformBuilderNeoForge.MODID);

    public static final DeferredHolder<Item, Item> PIG_CERTIFICATE = ITEMS.register("pig_certificate",
            () -> new PigCertificateItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .stacksTo(1)));
}
