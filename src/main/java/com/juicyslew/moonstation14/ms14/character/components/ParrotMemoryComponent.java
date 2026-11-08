package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved memory/replay capability; learned phrases themselves live on the saved entity. */
public record ParrotMemoryComponent(double learnChance, long learnCooldownTicks, int maxEntries,
                                    int minEntryLength, int maxEntryLength,
                                    long minimumReplayIntervalTicks, long maximumReplayIntervalTicks)
        implements CharacterComponent {
    public static final String TYPE = "ParrotMemory";
    public static final ParrotMemoryComponent DEFAULT = new ParrotMemoryComponent(.6, 1200, 50, 4, 50, 2400, 7200);
    public static final Codec<ParrotMemoryComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ParrotMemoryComponent::type),
            Codec.DOUBLE.optionalFieldOf("learnChance", DEFAULT.learnChance).forGetter(ParrotMemoryComponent::learnChance),
            Codec.LONG.optionalFieldOf("learnCooldownTicks", DEFAULT.learnCooldownTicks).forGetter(ParrotMemoryComponent::learnCooldownTicks),
            Codec.INT.optionalFieldOf("maxEntries", DEFAULT.maxEntries).forGetter(ParrotMemoryComponent::maxEntries),
            Codec.INT.optionalFieldOf("minEntryLength", DEFAULT.minEntryLength).forGetter(ParrotMemoryComponent::minEntryLength),
            Codec.INT.optionalFieldOf("maxEntryLength", DEFAULT.maxEntryLength).forGetter(ParrotMemoryComponent::maxEntryLength),
            Codec.LONG.optionalFieldOf("minimumReplayIntervalTicks", DEFAULT.minimumReplayIntervalTicks).forGetter(ParrotMemoryComponent::minimumReplayIntervalTicks),
            Codec.LONG.optionalFieldOf("maximumReplayIntervalTicks", DEFAULT.maximumReplayIntervalTicks).forGetter(ParrotMemoryComponent::maximumReplayIntervalTicks)
    ).apply(instance, (type, chance, cooldown, capacity, minLength, maxLength, minInterval, maxInterval) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new ParrotMemoryComponent(chance, cooldown, capacity, minLength, maxLength, minInterval, maxInterval);
    }));

    public ParrotMemoryComponent {
        if (!Double.isFinite(learnChance) || learnChance < 0 || learnChance > 1
                || learnCooldownTicks < 0 || maxEntries < 1 || maxEntries > 500
                || minEntryLength < 1 || maxEntryLength < minEntryLength || maxEntryLength > 256
                || minimumReplayIntervalTicks < 1 || maximumReplayIntervalTicks < minimumReplayIntervalTicks
                || maximumReplayIntervalTicks > Integer.MAX_VALUE)
            throw new IllegalArgumentException("invalid parrot memory policy");
    }

    @Override public String type() { return TYPE; }
}
