package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.item.custom.CrowbarItem;
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

    // CUSTOM ITEMS
    // TODO: If I start having dependency issues with what needs to be defined before what, consider a lazy-initialized static map pattern for these item's construction result maps.
    public static final DeferredItem<Item> STEEL = ITEMS.register("steel",
            () -> new SteelItem(new Item.Properties())
    );
    public static final DeferredItem<Item> CROWBAR = ITEMS.register("crowbar",
            () -> new CrowbarItem(new Item.Properties().durability(32))
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
