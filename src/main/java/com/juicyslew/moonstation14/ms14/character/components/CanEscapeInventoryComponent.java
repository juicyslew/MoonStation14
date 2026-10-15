package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved escape capability corresponding to SS14 CanEscapeInventory. Time is seconds. */
public record CanEscapeInventoryComponent(float baseResistTimeSeconds) implements CharacterComponent {
    public static final String TYPE = "CanEscapeInventory";
    public static final float DEFAULT_BASE_RESIST_TIME_SECONDS = 5f;
    private static final Codec<Float> DURATION = Codec.FLOAT.flatXmap(value ->
            Float.isFinite(value) && value > 0 && value <= 86_400
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "baseResistTime must be finite, positive, and at most 86400 seconds"),
            com.mojang.serialization.DataResult::success);
    public static final Codec<CanEscapeInventoryComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(CanEscapeInventoryComponent::type),
            DURATION.optionalFieldOf("baseResistTime", DEFAULT_BASE_RESIST_TIME_SECONDS)
                    .forGetter(CanEscapeInventoryComponent::baseResistTimeSeconds)
    ).apply(i, (type, seconds) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new CanEscapeInventoryComponent(seconds);
    }));

    public CanEscapeInventoryComponent {
        if (!Float.isFinite(baseResistTimeSeconds) || baseResistTimeSeconds <= 0)
            throw new IllegalArgumentException("baseResistTime must be finite and positive");
    }

    @Override public String type() { return TYPE; }
}
