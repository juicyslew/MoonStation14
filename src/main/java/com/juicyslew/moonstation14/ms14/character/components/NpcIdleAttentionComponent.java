package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Optional Minecraft idle-interest adaptation for autonomous NPCs. */
public record NpcIdleAttentionComponent(float range, int minimumProbeTicks, int maximumProbeTicks,
                                         int minimumDurationTicks, int maximumDurationTicks) implements CharacterComponent {
    public static final String TYPE = "NpcIdleAttention";
    public static final Codec<NpcIdleAttentionComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(NpcIdleAttentionComponent::type),
            Codec.FLOAT.optionalFieldOf("range", 5f).forGetter(NpcIdleAttentionComponent::range),
            Codec.INT.optionalFieldOf("minimumProbeTicks", 40).forGetter(NpcIdleAttentionComponent::minimumProbeTicks),
            Codec.INT.optionalFieldOf("maximumProbeTicks", 100).forGetter(NpcIdleAttentionComponent::maximumProbeTicks),
            Codec.INT.optionalFieldOf("minimumDurationTicks", 20).forGetter(NpcIdleAttentionComponent::minimumDurationTicks),
            Codec.INT.optionalFieldOf("maximumDurationTicks", 40).forGetter(NpcIdleAttentionComponent::maximumDurationTicks)
    ).apply(i, (type, range, minProbe, maxProbe, minDuration, maxDuration) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new NpcIdleAttentionComponent(range, minProbe, maxProbe, minDuration, maxDuration);
    }));
    public NpcIdleAttentionComponent {
        if (!Float.isFinite(range) || range <= 0 || minimumProbeTicks < 1 || maximumProbeTicks < minimumProbeTicks
                || minimumDurationTicks < 1 || maximumDurationTicks < minimumDurationTicks)
            throw new IllegalArgumentException("invalid idle attention policy");
    }
    @Override public String type() { return TYPE; }
}
