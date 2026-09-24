package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record FootstepSoundData(String collection, Optional<FootstepSoundParamsData> params) {
    public static final MapCodec<FootstepSoundData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("collection").forGetter(FootstepSoundData::collection),
            FootstepSoundParamsData.CODEC.codec().optionalFieldOf("params").forGetter(FootstepSoundData::params)
    ).apply(instance, FootstepSoundData::new));
}
