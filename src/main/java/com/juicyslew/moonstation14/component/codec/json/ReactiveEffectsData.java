package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public record ReactiveEffectsData(List<String> methods, List<EffectData> effects) {
    public static final Codec<ReactiveEffectsData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.list(Codec.STRING).fieldOf("methods").forGetter(ReactiveEffectsData::methods),
            Codec.list(EffectData.CODEC).fieldOf("effects").forGetter(ReactiveEffectsData::effects) // If the reactive effect exists, these fields MUST be defined.
    ).apply(inst, ReactiveEffectsData::new));
}
