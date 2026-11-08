package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Damage-container enrollment used to decide whether healing is allowed. */
public record DamageableComponent(Optional<String> damageContainer) implements CharacterComponent {
    public static final String TYPE = "Damageable";
    public static final Codec<DamageableComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE),
            Codec.STRING.optionalFieldOf("damage_container").forGetter(DamageableComponent::damageContainer)
    ).apply(instance, (type, container) -> new DamageableComponent(container)));

    public DamageableComponent { damageContainer = damageContainer == null ? Optional.empty() : damageContainer; }
    @Override public String type() { return TYPE; }
}

