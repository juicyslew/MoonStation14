package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.custom.BottleItem;
import com.juicyslew.moonstation14.item.custom.CrowbarItem;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.item.custom.SteelItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoonStation14.MOD_ID);

    // SIMPLE ITEMS
    public static final DeferredItem<Item> PLASTIC = ITEMS.register("plastic",
            () -> new Item(new Item.Properties().stacksTo(8))
    );
    public static final DeferredItem<Item> GLASS = ITEMS.register("glass",
            () -> new Item(new Item.Properties().stacksTo(8))
    );
    public static final DeferredItem<Item> PAPER = ITEMS.register("paper",
            () -> new Item(new Item.Properties().stacksTo(8))
    );
    public static final DeferredItem<Item> CLOTH = ITEMS.register("cloth",
            () -> new Item(new Item.Properties().stacksTo(8))
    );
    public static final DeferredItem<Item> WOOD = ITEMS.register("wood",
            () -> new Item(new Item.Properties().stacksTo(8))
    );

    // Food
    public static final DeferredItem<Item> WAFFLE = ITEMS.register("waffle",
            () -> new Item(new Item.Properties().food(ModFoodProperties.WAFFLE))
    );

    // CUSTOM ITEMS
    // TODO: If I start having dependency issues with what needs to be defined before what, consider a lazy-initialized static map pattern for these item's construction result maps.
    public static final DeferredItem<Item> STEEL = ITEMS.register("steel",
            () -> new SteelItem(new Item.Properties())
    );
    public static final DeferredItem<Item> CROWBAR = ITEMS.register("crowbar",
            () -> new CrowbarItem(new Item.Properties().durability(32))
    );
    public static final DeferredItem<BottleItem> BOTTLE = ITEMS.register("bottle",
            () -> new BottleItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<JugItem> JUG = ITEMS.register("jug",
            () -> new JugItem(ModBlocks.JUG.get(), new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
