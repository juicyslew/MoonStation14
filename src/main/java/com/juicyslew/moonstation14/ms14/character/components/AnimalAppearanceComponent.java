package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Authored visual models an animal host may select between; selection is persisted on the entity. */
public record AnimalAppearanceComponent(List<String> modelIds) implements CharacterComponent {
    public static final String TYPE = "AnimalAppearance";
    public static final Codec<AnimalAppearanceComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(AnimalAppearanceComponent::type),
            Codec.STRING.listOf().fieldOf("model_ids").forGetter(AnimalAppearanceComponent::modelIds)
    ).apply(i, (type, ids) -> { if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type " + type); return new AnimalAppearanceComponent(ids); }));
    public AnimalAppearanceComponent {
        modelIds = List.copyOf(modelIds);
        if (modelIds.isEmpty() || modelIds.stream().anyMatch(id -> id == null || id.isBlank()) || modelIds.stream().distinct().count() != modelIds.size())
            throw new IllegalArgumentException("animal appearance model_ids must be nonempty, nonblank and distinct");
    }
    @Override public String type() { return TYPE; }
}
