package com.juicyslew.moonstation14.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/** Native, inert placeholder items used by the equipment prototype family. */
public final class ModEquipmentItems {
    private ModEquipmentItems() {}

    public static final DeferredItem<Item> GREY_CAP = register("grey_cap");
    public static final DeferredItem<Item> GREY_JUMPSUIT = register("grey_jumpsuit");
    public static final DeferredItem<Item> SIMPLE_COAT = register("simple_coat");
    public static final DeferredItem<Item> BLACK_GLOVES = register("black_gloves");
    public static final DeferredItem<Item> BLACK_SHOES = register("black_shoes");
    public static final DeferredItem<Item> GLASSES = register("glasses");
    public static final DeferredItem<Item> RED_TIE = register("red_tie");
    public static final DeferredItem<Item> BLACK_BANDANA = register("black_bandana");
    public static final DeferredItem<Item> EARPIECES = register("earpieces");
    public static final DeferredItem<Item> BLANK_ID_CARD = register("blank_id_card");
    public static final DeferredItem<Item> UTILITY_CASE = register("utility_case");
    public static final DeferredItem<Item> SUIT_RAD = register("suit_rad");
    public static final DeferredItem<Item> HOOD_RAD = register("hood_rad");

    private static DeferredItem<Item> register(String id) {
        return ModItems.ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(1)));
    }
}

