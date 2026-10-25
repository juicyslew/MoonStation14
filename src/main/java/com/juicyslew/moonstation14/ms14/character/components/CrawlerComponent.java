package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Crawler tuning consumed by posture/movement systems; durations are seconds locally. */
public record CrawlerComponent(double speedModifier, double defaultKnockedDurationSeconds, double standTimeSeconds,
                               double damageThreshold, double frictionModifier) implements CharacterComponent {
    public static final String TYPE = "Crawler";
    public static final Codec<CrawlerComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(CrawlerComponent::type),
            Codec.DOUBLE.optionalFieldOf("speed_modifier", .4).forGetter(CrawlerComponent::speedModifier),
            Codec.DOUBLE.optionalFieldOf("default_knocked_duration_seconds", .5).forGetter(CrawlerComponent::defaultKnockedDurationSeconds),
            Codec.DOUBLE.optionalFieldOf("stand_time_seconds", 2d).forGetter(CrawlerComponent::standTimeSeconds),
            Codec.DOUBLE.optionalFieldOf("damage_threshold", 5d).forGetter(CrawlerComponent::damageThreshold),
            Codec.DOUBLE.optionalFieldOf("friction_modifier", 1d).forGetter(CrawlerComponent::frictionModifier)
    ).apply(i, (type, speed, knocked, stand, damage, friction) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new CrawlerComponent(speed, knocked, stand, damage, friction);
    }));
    public CrawlerComponent { if (!Double.isFinite(speedModifier) || speedModifier < 0 || !Double.isFinite(defaultKnockedDurationSeconds) || defaultKnockedDurationSeconds < 0 || !Double.isFinite(standTimeSeconds) || standTimeSeconds < 0 || !Double.isFinite(damageThreshold) || damageThreshold < 0 || !Double.isFinite(frictionModifier) || frictionModifier < 0) throw new IllegalArgumentException("crawler values must be finite and nonnegative"); }
    @Override public String type() { return TYPE; }
}

