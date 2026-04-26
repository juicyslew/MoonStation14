package com.juicyslew.moonstation14.component.codec.json.metamorphic;

import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record MetamorphicSpriteData(
        String sprite,
        String state
) {
    public static final Codec<MetamorphicSpriteData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("sprite").forGetter(MetamorphicSpriteData::sprite),
            Codec.STRING.fieldOf("state").forGetter(MetamorphicSpriteData::state)
    ).apply(instance, MetamorphicSpriteData::new));
}