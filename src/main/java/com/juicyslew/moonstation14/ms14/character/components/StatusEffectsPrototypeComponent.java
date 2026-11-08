package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import java.util.Set;

/** Prototype-side admission for status identifiers (distinct from currently active status state). */
public record StatusEffectsPrototypeComponent(Set<ResourceKey<StatusEffectData>> allowed) implements CharacterComponent {
    public static final String TYPE = "StatusEffects";
    public static final ResourceKey<StatusEffectData> KNOCKDOWN = ModStatusEffects.createKey("knockdown");
    public static final ResourceKey<StatusEffectData> PACIFIED = ModStatusEffects.createKey("pacified");
    public static final StatusEffectsPrototypeComponent DEFAULT = new StatusEffectsPrototypeComponent(Set.of());
    private static final Codec<ResourceKey<StatusEffectData>> ALLOWED_KEY = Codec.STRING.flatXmap(value -> {
        if (value.equals("knockdown")) return com.mojang.serialization.DataResult.success(KNOCKDOWN);
        if (value.equals("pacified")) return com.mojang.serialization.DataResult.success(PACIFIED);
        return com.mojang.serialization.DataResult.error(() -> "unsupported admitted status identifier: " + value);
    }, key -> key.equals(KNOCKDOWN) ? com.mojang.serialization.DataResult.success("knockdown")
            : key.equals(PACIFIED) ? com.mojang.serialization.DataResult.success("pacified")
            : com.mojang.serialization.DataResult.error(() -> "unsupported admitted status identifier: " + key));
    public static final Codec<StatusEffectsPrototypeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(StatusEffectsPrototypeComponent::type),
            ALLOWED_KEY.listOf().fieldOf("allowed_effects").forGetter(value -> value.allowed().stream().toList())
    ).apply(i, (type, allowed) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type);
        return new StatusEffectsPrototypeComponent(Set.copyOf(allowed));
    }));
    public StatusEffectsPrototypeComponent { allowed = Set.copyOf(allowed); }
    @Override public String type() { return TYPE; }
}
