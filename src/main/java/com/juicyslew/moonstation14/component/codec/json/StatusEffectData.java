package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record StatusEffectData(
        String id,
        String translationKey,
        boolean isBeneficial,
        int color
) {
    public static final Codec<StatusEffectData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(StatusEffectData::id),
            Codec.STRING.fieldOf("translation_key").forGetter(StatusEffectData::translationKey),
            Codec.BOOL.optionalFieldOf("beneficial", false).forGetter(StatusEffectData::isBeneficial),
            Codec.STRING.xmap(Integer::decode, i -> "0x" + Integer.toHexString(i)).fieldOf("color").forGetter(StatusEffectData::color)
    ).apply(instance, StatusEffectData::new));
}
