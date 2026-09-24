package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FootstepSoundParamsData(float volume) {
    public static final MapCodec<FootstepSoundParamsData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("volume").forGetter(FootstepSoundParamsData::volume)
    ).apply(instance, FootstepSoundParamsData::new));
}
