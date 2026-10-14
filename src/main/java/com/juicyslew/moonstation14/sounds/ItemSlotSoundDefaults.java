package com.juicyslew.moonstation14.sounds;

import net.minecraft.resources.ResourceLocation;

/** Pure inherited local projection of SS14 ItemSlot insert/eject sound defaults. */
public final class ItemSlotSoundDefaults {
    private ItemSlotSoundDefaults() { }
    public static ResourceLocation insert() { return ResourceLocation.fromNamespaceAndPath("moonstation14", "item_slot_insert"); }
    public static ResourceLocation eject() { return ResourceLocation.fromNamespaceAndPath("moonstation14", "item_slot_eject"); }
}
