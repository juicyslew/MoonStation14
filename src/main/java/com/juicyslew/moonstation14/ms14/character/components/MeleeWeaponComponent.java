package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Character melee configuration reuses the shared immutable weapon statistics model. */
public record MeleeWeaponComponent(MeleeWeaponData weapon) implements CharacterComponent {
    public static final String TYPE = "MeleeWeapon";
    public static final Codec<MeleeWeaponComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(MeleeWeaponComponent::type),
            MeleeWeaponData.CODEC.fieldOf("weapon").forGetter(MeleeWeaponComponent::weapon)
    ).apply(i, (type, weapon) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type);
        return new MeleeWeaponComponent(weapon);
    }));
    @Override public String type() { return TYPE; }
}
