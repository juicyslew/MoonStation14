package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record MetabolismData(List<EffectData> effects, float rate) {
    public static final Codec<MetabolismData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.list(EffectData.CODEC).fieldOf("effects").forGetter(MetabolismData::effects),
            Codec.FLOAT.optionalFieldOf("metabolismRate", .5f).forGetter(MetabolismData::rate)
    ).apply(inst, MetabolismData::new));
}