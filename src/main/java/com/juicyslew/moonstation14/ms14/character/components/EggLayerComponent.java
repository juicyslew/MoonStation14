package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved egg production policy; interval values are SS14 seconds. */
public record EggLayerComponent(String eggItem, int cooldownMin, int cooldownMax, float hungerUsage)
        implements CharacterComponent {
    public static final String TYPE = "EggLayer";
    public static final int DEFAULT_COOLDOWN_MIN = 60;
    public static final int DEFAULT_COOLDOWN_MAX = 120;
    public static final float DEFAULT_HUNGER_USAGE = 60f;
    public static final Codec<EggLayerComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(EggLayerComponent::type),
            Codec.STRING.optionalFieldOf("egg", "minecraft:egg").forGetter(EggLayerComponent::eggItem),
            Codec.INT.optionalFieldOf("cooldown_min", DEFAULT_COOLDOWN_MIN).forGetter(EggLayerComponent::cooldownMin),
            Codec.INT.optionalFieldOf("cooldown_max", DEFAULT_COOLDOWN_MAX).forGetter(EggLayerComponent::cooldownMax),
            Codec.FLOAT.optionalFieldOf("hunger_usage", DEFAULT_HUNGER_USAGE).forGetter(EggLayerComponent::hungerUsage)
    ).apply(i, (type, egg, min, max, cost) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type " + type);
        return new EggLayerComponent(egg, min, max, cost);
    }));

    public EggLayerComponent {
        if (eggItem == null || eggItem.isBlank() || cooldownMin < 1 || cooldownMax < cooldownMin
                || !Float.isFinite(hungerUsage) || hungerUsage < 0)
            throw new IllegalArgumentException("invalid EggLayer policy");
    }
    @Override public String type() { return TYPE; }
}
