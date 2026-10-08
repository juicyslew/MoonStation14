package com.juicyslew.moonstation14.ms14.mobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;

/** Persisted authoritative state and monotonically increasing transition revision. */
public record MobStateData(MobStateComponent.State state, long revision) {
    public static final MobStateData DEFAULT = new MobStateData(MobStateComponent.State.ALIVE, 0);
    public static final Codec<MobStateData> CODEC = RecordCodecBuilder.create(i -> i.group(
            MobStateComponent.STATE_CODEC.fieldOf("state").forGetter(MobStateData::state),
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(MobStateData::revision)
    ).apply(i, MobStateData::new));

    public MobStateData {
        if (state == null || revision < 0) throw new IllegalArgumentException("invalid mob state data");
    }
}
