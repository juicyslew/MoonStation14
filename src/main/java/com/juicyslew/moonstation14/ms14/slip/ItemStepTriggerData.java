package com.juicyslew.moonstation14.ms14.slip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Supported item-contact subset of SS14 StepTrigger defaults; no StepOn instrument is modeled. */
public record ItemStepTriggerData(boolean active, double intersectionRatio, double requiredSpeed,
                                  boolean ignoreWeightless) {
    public static final boolean DEFAULT_ACTIVE = true;
    public static final double DEFAULT_INTERSECTION_RATIO = 0.3;
    public static final double DEFAULT_REQUIRED_SPEED = 3.5;
    public static final boolean DEFAULT_IGNORE_WEIGHTLESS = false;

    public static final MapCodec<ItemStepTriggerData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("active", DEFAULT_ACTIVE).forGetter(ItemStepTriggerData::active),
            Codec.DOUBLE.optionalFieldOf("intersection_ratio", DEFAULT_INTERSECTION_RATIO)
                    .forGetter(ItemStepTriggerData::intersectionRatio),
            Codec.DOUBLE.optionalFieldOf("required_speed", DEFAULT_REQUIRED_SPEED)
                    .forGetter(ItemStepTriggerData::requiredSpeed),
            Codec.BOOL.optionalFieldOf("ignore_weightless", DEFAULT_IGNORE_WEIGHTLESS)
                    .forGetter(ItemStepTriggerData::ignoreWeightless)
    ).apply(instance, ItemStepTriggerData::new));

    public ItemStepTriggerData {
        if (!Double.isFinite(intersectionRatio) || intersectionRatio < 0 || intersectionRatio > 1) {
            throw new IllegalArgumentException("intersectionRatio must be finite and within [0, 1]");
        }
        if (!Double.isFinite(requiredSpeed) || requiredSpeed < 0) {
            throw new IllegalArgumentException("requiredSpeed must be finite and non-negative");
        }
    }
}
