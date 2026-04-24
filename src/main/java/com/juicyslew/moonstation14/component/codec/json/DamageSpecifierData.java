package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record DamageSpecifierData(Map<String, Float> types) {
    public static final Codec<DamageSpecifierData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("types").forGetter(DamageSpecifierData::types)
    ).apply(inst, DamageSpecifierData::new));
}
