package com.juicyslew.moonstation14.ms14.animals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persisted server production deadline prevents reloads from resetting egg timers. */
public record EggLayerAttachment(long nextGrowthTick, long nextManualLayTick) {
    public static final EggLayerAttachment EMPTY = new EggLayerAttachment(0, 0);
    public static final Codec<EggLayerAttachment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("next_growth_tick").forGetter(EggLayerAttachment::nextGrowthTick),
            Codec.LONG.fieldOf("next_manual_lay_tick").forGetter(EggLayerAttachment::nextManualLayTick)
    ).apply(i, EggLayerAttachment::new));
}
