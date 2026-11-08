package com.juicyslew.moonstation14.ms14.animals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistent private state for an animal's currently available milk and next production time. */
public record UdderAttachment(float available, long nextGrowthTick, String solutionName) {
    public static final UdderAttachment EMPTY = new UdderAttachment(0, 0, "");
    public static final Codec<UdderAttachment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("available").forGetter(UdderAttachment::available),
            Codec.LONG.fieldOf("next_growth_tick").forGetter(UdderAttachment::nextGrowthTick),
            Codec.STRING.optionalFieldOf("solution_name", "").forGetter(UdderAttachment::solutionName)
    ).apply(i, UdderAttachment::new));
    public UdderAttachment(float available, long nextGrowthTick) { this(available, nextGrowthTick, ""); }
    public UdderAttachment {
        if (!Float.isFinite(available) || available < 0 || nextGrowthTick < 0 || solutionName == null
                || solutionName.length() > 64)
            throw new IllegalArgumentException("invalid udder state");
    }
    public UdderAttachment withAvailable(float value) { return new UdderAttachment(value, nextGrowthTick, solutionName); }
}
