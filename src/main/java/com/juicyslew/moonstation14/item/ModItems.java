package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoonStation14.MOD_ID);

    public static final DeferredItem<Item> STEEL = ITEMS.register("steel",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> PLASTIC = ITEMS.register("plastic",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> GLASS = ITEMS.register("glass",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> PAPER = ITEMS.register("paper",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> CLOTH = ITEMS.register("cloth",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> WOOD = ITEMS.register("wood",
            () -> new Item(new Item.Properties())
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
