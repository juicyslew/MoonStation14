package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Configures the action reference used to toggle combat mode. */
public record CombatModeComponent(String combatToggleAction) implements CharacterComponent {
    public static final String TYPE = "CombatMode";
    public static final String DEFAULT_ACTION = "moonstation14:combat_mode_toggle";
    public static final Codec<CombatModeComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ignored -> TYPE),
            Codec.STRING.optionalFieldOf("combatToggleAction", DEFAULT_ACTION).forGetter(CombatModeComponent::combatToggleAction)
    ).apply(instance, (type, action) -> new CombatModeComponent(action)));

    @Override public String type() { return TYPE; }
}
