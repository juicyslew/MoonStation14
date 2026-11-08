package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots;

/** Ordered equipment capability only; occupants and wearable behavior are not part of this policy. */
public record EquipmentSlotsPrototypeComponent(List<String> slots, Optional<ResourceLocation> template) implements CharacterComponent {
    public static final String TYPE = "EquipmentSlots";
    /** All supported humanoid slots; a body prototype may declare any ordered subset. */
    public static final List<String> HUMAN_SLOT_ORDER = EquipmentSlots.SLOT_ORDER;
    public static final Set<String> ALLOWED_SLOTS = Set.copyOf(HUMAN_SLOT_ORDER);
    public static final int MAX_SLOTS = HUMAN_SLOT_ORDER.size();
    public static final Codec<EquipmentSlotsPrototypeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(EquipmentSlotsPrototypeComponent::type),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(EquipmentSlotsPrototypeComponent::slots),
            ResourceLocation.CODEC.optionalFieldOf("template").forGetter(EquipmentSlotsPrototypeComponent::template)
    ).apply(instance, (type, slots, template) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new EquipmentSlotsPrototypeComponent(slots, template);
    }));

    public EquipmentSlotsPrototypeComponent {
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        template = template == null ? Optional.empty() : template;
        if (slots.isEmpty() && template.isEmpty()) throw new IllegalArgumentException("equipment capability requires slots or template");
        if (slots.size() > MAX_SLOTS) throw new IllegalArgumentException("at most " + MAX_SLOTS + " equipment slots are allowed");
        Set<String> seen = new HashSet<>();
        for (String slot : slots) {
            if (!ALLOWED_SLOTS.contains(slot)) throw new IllegalArgumentException("unknown equipment slot: " + slot);
            if (!seen.add(slot)) throw new IllegalArgumentException("duplicate equipment slot: " + slot);
        }
        if (template.isPresent() && !slots.isEmpty()) throw new IllegalArgumentException("template binding cannot duplicate explicit slots");
    }

    public EquipmentSlotsPrototypeComponent(List<String> slots) { this(slots, Optional.empty()); }

    @Override public String type() { return TYPE; }
}

