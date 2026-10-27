package com.juicyslew.moonstation14.ms14.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Presence protects its owner; the mask applies when projected from worn equipment. */
public record ProtectedFromStepTriggers(List<InventoryTemplateData.SlotFlag> slotFlags) {
    public static final ProtectedFromStepTriggers DEFAULT = new ProtectedFromStepTriggers(List.of(InventoryTemplateData.SlotFlag.FEET));
    public static final Codec<ProtectedFromStepTriggers> CODEC = RecordCodecBuilder.create(i -> i.group(
            InventoryTemplateData.SLOT_FLAG_CODEC.listOf().optionalFieldOf("slot_flags", DEFAULT.slotFlags()).forGetter(ProtectedFromStepTriggers::slotFlags)
    ).apply(i, ProtectedFromStepTriggers::new));
    public ProtectedFromStepTriggers { slotFlags = List.copyOf(slotFlags); }
    public boolean intersects(List<InventoryTemplateData.SlotFlag> flags) { return slotFlags.stream().anyMatch(flags::contains); }
}
