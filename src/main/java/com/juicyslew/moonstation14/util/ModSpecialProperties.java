package com.juicyslew.moonstation14.util;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class ModSpecialProperties {
    public static void addCustomItemProperties(FMLClientSetupEvent event) {
        // Now you can pass any item that implements IReagentItem
        registerFillProperty(ModItems.BOTTLE.get());
        registerFillProperty(ModItems.JUG.get());
    }

    private static <T extends Item & IReagentTrait> void registerFillProperty(T item) {
        ItemProperties.register(
                item,
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "fill"),
                (stack, level, entity, seed) -> {
                    float currentVolume = MapOperations.getTotal(
                            stack.getOrDefault(ModDataComponents.REAGENT.get(), new ReagentComponent()).contents()
                    );
                    // Use the capacity defined in your interface!
                    return currentVolume / item.getCapacity();
                }
        );
    }
}
