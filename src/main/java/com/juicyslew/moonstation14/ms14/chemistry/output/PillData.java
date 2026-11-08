package com.juicyslew.moonstation14.ms14.chemistry.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistent, synchronized presentation identity for the SS14 pill sprite state range. */
public record PillData(int type) {
    public static final Codec<PillData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 19).optionalFieldOf("type", 0).forGetter(PillData::type)
    ).apply(i, PillData::new));
    public PillData { if (type < 0 || type > 19) throw new IllegalArgumentException("pill type must be in 0..19"); }
}
