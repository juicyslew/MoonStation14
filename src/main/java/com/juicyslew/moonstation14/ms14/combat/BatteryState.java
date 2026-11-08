package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BatteryState(float charge) {
    public static final Codec<BatteryState> CODEC=RecordCodecBuilder.create(i->i.group(Codec.FLOAT.fieldOf("charge").forGetter(BatteryState::charge)).apply(i,BatteryState::new));
    public BatteryState { if(!Float.isFinite(charge)||charge<0) throw new IllegalArgumentException("invalid charge"); }
}
