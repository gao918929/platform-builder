package com.pingtai.platformbuilder.block;

import com.pingtai.platformbuilder.PlatformBuilder;
import com.pingtai.platformbuilder.item.PigCertificateItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PlatformBuilder.MODID);

    public static final RegistryObject<Item> PIG_CERTIFICATE = ITEMS.register("pig_certificate",
            () -> new PigCertificateItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .stacksTo(1)));
}
