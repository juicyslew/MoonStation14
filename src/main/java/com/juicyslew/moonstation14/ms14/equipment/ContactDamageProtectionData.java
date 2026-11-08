package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.damage.DamageModifierData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Contact-damage resistance, optionally projected from qualifying worn slots. */
public record ContactDamageProtectionData(DamageModifierData protection, List<InventoryTemplateData.SlotFlag> slots) {
    public static final ContactDamageProtectionData DEFAULT = new ContactDamageProtectionData(DamageModifierData.EMPTY, List.of(InventoryTemplateData.SlotFlag.GLOVES));
    public static final Codec<ContactDamageProtectionData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DamageModifierData.CODEC.optionalFieldOf("protection", DamageModifierData.EMPTY).forGetter(ContactDamageProtectionData::protection),
            InventoryTemplateData.SLOT_FLAG_CODEC.listOf().optionalFieldOf("slots", DEFAULT.slots()).forGetter(ContactDamageProtectionData::slots)
    ).apply(i, ContactDamageProtectionData::new));
    public ContactDamageProtectionData { if (protection == null || slots == null || slots.isEmpty()) throw new IllegalArgumentException("invalid contact protection"); slots = List.copyOf(slots); }
    public boolean intersects(List<InventoryTemplateData.SlotFlag> actualFlags) { return slots.stream().anyMatch(actualFlags::contains); }
}
