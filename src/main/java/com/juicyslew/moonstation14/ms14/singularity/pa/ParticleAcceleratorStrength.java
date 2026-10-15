package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.mojang.serialization.Codec;

/** Named SS14 power-state mapping; Level2 is protocol value 4, never ordinal/rank 2. */
public enum ParticleAcceleratorStrength {
    STANDBY(1, 0), LEVEL_0(2, 1), LEVEL_1(3, 2), LEVEL_2(4, 3), LEVEL_3(5, 4);
    public static final Codec<ParticleAcceleratorStrength> CODEC = Codec.STRING.xmap(
            ParticleAcceleratorStrength::valueOf, ParticleAcceleratorStrength::name);
    private final int ss14Value;
    private final int level;
    ParticleAcceleratorStrength(int ss14Value, int level) { this.ss14Value = ss14Value; this.level = level; }
    public int ss14Value() { return ss14Value; }
    public int level() { return level; }
}

