package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record SlipData(float requiredSlipSpeed, Optional<Boolean> superSlippery,
                       Optional<Float> stunTime, Optional<Float> knockdownTime,
                       Optional<Boolean> autoStand, Optional<Float> launchVelocityMultiplier,
                       Optional<Float> slipFriction) {
    public static final boolean DEFAULT_AUTO_STAND = true;
    public static final float DEFAULT_SLIP_FRICTION = 0.5f;

    public SlipData(float requiredSlipSpeed, Optional<Boolean> superSlippery) {
        this(requiredSlipSpeed, superSlippery, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    public static final MapCodec<SlipData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("requiredSlipSpeed", 3.5f).forGetter(SlipData::requiredSlipSpeed),
            Codec.BOOL.optionalFieldOf("superSlippery").forGetter(SlipData::superSlippery),
            Codec.FLOAT.optionalFieldOf("stunTime").forGetter(SlipData::stunTime),
            Codec.FLOAT.optionalFieldOf("knockdownTime").forGetter(SlipData::knockdownTime),
            Codec.BOOL.optionalFieldOf("autoStand").forGetter(SlipData::autoStand),
            Codec.FLOAT.optionalFieldOf("launchForwardsMultiplier").forGetter(SlipData::launchVelocityMultiplier),
            Codec.FLOAT.optionalFieldOf("slipFriction").forGetter(SlipData::slipFriction)
    ).apply(instance, SlipData::new));

    public boolean autoStandOrDefault() {
        return autoStand.orElse(DEFAULT_AUTO_STAND);
    }

    public float slipFrictionOrDefault() {
        return slipFriction.orElse(DEFAULT_SLIP_FRICTION);
    }
}
