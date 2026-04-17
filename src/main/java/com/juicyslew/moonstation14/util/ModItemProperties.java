package com.juicyslew.moonstation14.util;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.ReagentContainerData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.container.Container;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public class ModItemProperties {
    public static void addCustomItemProperties(){
        makeContainerObject((Container) ModItems.BOTTLE.get());
    }

    private static void makeContainerObject(Container container) {
        ItemProperties.register(
                container,
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "fill"),
                (stack, level, entity, seed) -> (float) container.getTotalVolume(stack) / container.capacity);
    }
}
