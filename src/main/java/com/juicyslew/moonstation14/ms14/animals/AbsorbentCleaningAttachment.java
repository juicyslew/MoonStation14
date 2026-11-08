package com.juicyslew.moonstation14.ms14.animals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistent absorbed mixture and absolute game-tick deadlines. */
public record AbsorbentCleaningAttachment(long nextUseTick, long nextCycleTick) {
    public static final AbsorbentCleaningAttachment EMPTY = new AbsorbentCleaningAttachment(0, 0);
    public static final Codec<AbsorbentCleaningAttachment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("nextUseTick", 0L).forGetter(AbsorbentCleaningAttachment::nextUseTick),
            Codec.LONG.optionalFieldOf("nextCycleTick", 0L).forGetter(AbsorbentCleaningAttachment::nextCycleTick)
    ).apply(i, AbsorbentCleaningAttachment::new));
    public AbsorbentCleaningAttachment { if (nextUseTick < 0 || nextCycleTick < 0) throw new IllegalArgumentException("negative cleaning deadline"); }
}
