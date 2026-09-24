package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record SlipData(float requiredSlipSpeed, Optional<Boolean> superSlippery) {
    public static final MapCodec<SlipData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("requiredslipspeed").forGetter(SlipData::requiredSlipSpeed),
            Codec.BOOL.optionalFieldOf("superslippery").forGetter(SlipData::superSlippery)
    ).apply(instance, SlipData::new));
}
