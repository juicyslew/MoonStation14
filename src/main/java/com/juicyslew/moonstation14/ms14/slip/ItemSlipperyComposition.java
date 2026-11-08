package com.juicyslew.moonstation14.ms14.slip;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Supported item trigger and shared slip response composition. */
public record ItemSlipperyComposition(ItemStepTriggerData stepTrigger, SlipResponseData response) {
    public static final ItemSlipperyComposition DEFAULT = new ItemSlipperyComposition(
            new ItemStepTriggerData(true, .3d, 3.5d, false), SlipResponseData.DEFAULT);
    public static final MapCodec<ItemSlipperyComposition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStepTriggerData.CODEC.codec().optionalFieldOf("step_trigger", new ItemStepTriggerData(true, .3d, 3.5d, false))
                    .forGetter(ItemSlipperyComposition::stepTrigger),
            SlipResponseData.CODEC.codec().optionalFieldOf("response", SlipResponseData.DEFAULT)
                    .forGetter(ItemSlipperyComposition::response)
    ).apply(instance, ItemSlipperyComposition::new));

    public ItemSlipperyComposition {
        java.util.Objects.requireNonNull(stepTrigger, "stepTrigger");
        java.util.Objects.requireNonNull(response, "response");
    }
}
