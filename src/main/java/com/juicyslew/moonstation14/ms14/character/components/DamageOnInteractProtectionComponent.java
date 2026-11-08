package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.ms14.equipment.ContactDamageProtectionData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DamageOnInteractProtectionComponent(ContactDamageProtectionData profile) implements CharacterComponent {
    public static final String TYPE = "DamageOnInteractProtection";
    public static final Codec<DamageOnInteractProtectionComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(DamageOnInteractProtectionComponent::type),
            ContactDamageProtectionData.CODEC.optionalFieldOf("profile", ContactDamageProtectionData.DEFAULT).forGetter(DamageOnInteractProtectionComponent::profile)
    ).apply(i, (type, profile) -> { if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type); return new DamageOnInteractProtectionComponent(profile); }));
    @Override public String type() { return TYPE; }
}
