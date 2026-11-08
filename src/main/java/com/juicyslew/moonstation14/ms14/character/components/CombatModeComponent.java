package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Immutable combat capability/action configuration; mutable state is attachment-owned. */
public record CombatModeComponent(String combatToggleAction, boolean initiallyInCombat,
                                  Optional<Boolean> canDisarm, float baseDisarmFailChance,
                                  Optional<String> disarmSuccessSound) implements CharacterComponent {
    public static final String TYPE = "CombatMode";
    public static final String DEFAULT_ACTION = "moonstation14:combat_mode_toggle";
    public static final CombatModeComponent DEFAULT = new CombatModeComponent(DEFAULT_ACTION, false,
            Optional.empty(), .75f, Optional.of("moonstation14:ss14/effects/thudswoosh"));

    public static final Codec<CombatModeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(CombatModeComponent::type),
            Codec.STRING.optionalFieldOf("combatToggleAction", DEFAULT_ACTION).forGetter(CombatModeComponent::combatToggleAction),
            Codec.BOOL.optionalFieldOf("initially_in_combat", false).forGetter(CombatModeComponent::initiallyInCombat),
            Codec.BOOL.optionalFieldOf("can_disarm").forGetter(CombatModeComponent::canDisarm),
            Codec.FLOAT.optionalFieldOf("base_disarm_fail_chance", .75f).forGetter(CombatModeComponent::baseDisarmFailChance),
            Codec.STRING.optionalFieldOf("disarm_success_sound", "moonstation14:ss14/effects/thudswoosh")
                    .xmap(Optional::of, value -> value.orElse("moonstation14:ss14/effects/thudswoosh"))
                    .forGetter(CombatModeComponent::disarmSuccessSound)
    ).apply(i, (type, action, initial, disarm, chance, sound) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type);
        return new CombatModeComponent(action, initial, disarm, chance, sound);
    }));

    public CombatModeComponent(String combatToggleAction) {
        this(combatToggleAction, false, Optional.empty(), .75f,
                Optional.of("moonstation14:ss14/effects/thudswoosh"));
    }
    public CombatModeComponent(boolean initiallyInCombat, Optional<Boolean> canDisarm) {
        this(DEFAULT_ACTION, initiallyInCombat, canDisarm, .75f, Optional.empty());
    }
    public CombatModeComponent(boolean initiallyInCombat, Optional<Boolean> canDisarm,
                               float baseDisarmFailChance, Optional<String> disarmSuccessSound) {
        this(DEFAULT_ACTION, initiallyInCombat, canDisarm, baseDisarmFailChance, disarmSuccessSound);
    }
    public CombatModeComponent {
        if (combatToggleAction == null || combatToggleAction.isBlank() || canDisarm == null || disarmSuccessSound == null
                || !Float.isFinite(baseDisarmFailChance) || baseDisarmFailChance < 0 || baseDisarmFailChance > 1)
            throw new IllegalArgumentException("invalid combat mode component");
    }
    @Override public String type() { return TYPE; }
}
