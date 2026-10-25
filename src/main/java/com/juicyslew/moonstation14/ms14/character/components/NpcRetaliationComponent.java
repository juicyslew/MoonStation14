package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Opt-in defensive response to a living attacker; absent component means strictly non-retaliatory. */
public record NpcRetaliationComponent(int attackMemorySeconds) implements CharacterComponent {
    public static final String TYPE = "NpcRetaliation";
    /** Upstream leaves attackMemoryLength unset by default (no timer); zero means unbounded memory. */
    public static final NpcRetaliationComponent DEFAULT = new NpcRetaliationComponent(0);
    public static final Codec<NpcRetaliationComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(NpcRetaliationComponent::type),
            Codec.INT.optionalFieldOf("attackMemorySeconds", DEFAULT.attackMemorySeconds)
                    .forGetter(NpcRetaliationComponent::attackMemorySeconds)
    ).apply(instance, (type, seconds) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new NpcRetaliationComponent(seconds);
    }));

    public NpcRetaliationComponent {
        if (attackMemorySeconds < 0 || attackMemorySeconds > 3600)
            throw new IllegalArgumentException("attackMemorySeconds must be in [0, 3600]");
    }
    @Override public String type() { return TYPE; }
}
