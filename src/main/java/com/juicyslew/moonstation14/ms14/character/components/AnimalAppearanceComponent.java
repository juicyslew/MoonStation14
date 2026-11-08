package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Authored visual models an animal host may select between; selection is persisted on the entity. */
public record AnimalAppearanceComponent(List<String> modelIds, Float eyeHeight, Float speechHeight) implements CharacterComponent {
    public AnimalAppearanceComponent(List<String> modelIds) { this(modelIds, null, null); }
    public static final String TYPE = "AnimalAppearance";
    public static final Codec<AnimalAppearanceComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(AnimalAppearanceComponent::type),
            Codec.STRING.listOf().fieldOf("model_ids").forGetter(AnimalAppearanceComponent::modelIds),
            positiveHeightCodec().optionalFieldOf("eye_height").forGetter(c -> java.util.Optional.ofNullable(c.eyeHeight())),
            positiveHeightCodec().optionalFieldOf("speech_height").forGetter(c -> java.util.Optional.ofNullable(c.speechHeight()))
    ).apply(i, (type, ids, eye, speech) -> { if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type " + type); return new AnimalAppearanceComponent(ids, eye.orElse(null), speech.orElse(null)); }));
    public AnimalAppearanceComponent {
        modelIds = List.copyOf(modelIds);
        if (eyeHeight != null && (!Float.isFinite(eyeHeight) || eyeHeight <= 0) || speechHeight != null && (!Float.isFinite(speechHeight) || speechHeight <= 0)) throw new IllegalArgumentException("presentation heights must be finite and positive");
        if (modelIds.isEmpty() || modelIds.stream().anyMatch(id -> id == null || id.isBlank()) || modelIds.stream().distinct().count() != modelIds.size())
            throw new IllegalArgumentException("animal appearance model_ids must be nonempty, nonblank and distinct");
    }
    @Override public String type() { return TYPE; }
    private static Codec<Float> positiveHeightCodec() {
        return Codec.FLOAT.validate(value -> Float.isFinite(value) && value > 0f
                ? com.mojang.serialization.DataResult.success(value)
                : com.mojang.serialization.DataResult.error(() -> "presentation height must be finite and positive"));
    }
}
