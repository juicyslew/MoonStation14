package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Ordered equipment capability only; occupants and wearable behavior are not part of this policy. */
public record EquipmentSlotsPrototypeComponent(List<String> slots) implements CharacterComponent {
    public static final String TYPE = "EquipmentSlots";
    // Adding a slot requires a reviewed policy change, not merely a prototype edit.
    public static final Set<String> ALLOWED_SLOTS = Set.of("belt", "back");
    public static final int MAX_SLOTS = 2;
    public static final Codec<EquipmentSlotsPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(EquipmentSlotsPrototypeComponent::type),
            Codec.STRING.listOf().fieldOf("slots").forGetter(EquipmentSlotsPrototypeComponent::slots)
    ).apply(instance, (type, slots) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new EquipmentSlotsPrototypeComponent(slots);
    }));

    public EquipmentSlotsPrototypeComponent {
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        if (slots.size() > MAX_SLOTS) throw new IllegalArgumentException("at most " + MAX_SLOTS + " equipment slots are allowed");
        Set<String> seen = new HashSet<>();
        for (String slot : slots) {
            if (!ALLOWED_SLOTS.contains(slot)) throw new IllegalArgumentException("unknown equipment slot: " + slot);
            if (!seen.add(slot)) throw new IllegalArgumentException("duplicate equipment slot: " + slot);
        }
    }

    @Override public String type() { return TYPE; }
}
