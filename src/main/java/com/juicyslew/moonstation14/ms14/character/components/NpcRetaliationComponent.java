package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Opt-in defensive response to a living attacker; absent component means strictly non-retaliatory. */
public record NpcRetaliationComponent(int attackMemorySeconds, double meleeMissChance) implements CharacterComponent {
    public NpcRetaliationComponent(int attackMemorySeconds) { this(attackMemorySeconds, .3); }
    public static final String TYPE = "NpcRetaliation";
    /** Upstream leaves attackMemoryLength unset by default (no timer); zero means unbounded memory. */
    public static final NpcRetaliationComponent DEFAULT = new NpcRetaliationComponent(0, .3);
    public static final Codec<NpcRetaliationComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(NpcRetaliationComponent::type),
             Codec.INT.optionalFieldOf("attackMemorySeconds", DEFAULT.attackMemorySeconds).forGetter(NpcRetaliationComponent::attackMemorySeconds),
             Codec.DOUBLE.optionalFieldOf("meleeMissChance", DEFAULT.meleeMissChance).forGetter(NpcRetaliationComponent::meleeMissChance)
     ).apply(instance, (type, seconds, chance) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
         return new NpcRetaliationComponent(seconds, chance);
    }));

    public NpcRetaliationComponent {
        if (attackMemorySeconds < 0 || attackMemorySeconds > 3600)
            throw new IllegalArgumentException("attackMemorySeconds must be in [0, 3600]");
        if (!Double.isFinite(meleeMissChance) || meleeMissChance < 0 || meleeMissChance > 1)
            throw new IllegalArgumentException("meleeMissChance must be in [0, 1]");
    }
    @Override public String type() { return TYPE; }
}
