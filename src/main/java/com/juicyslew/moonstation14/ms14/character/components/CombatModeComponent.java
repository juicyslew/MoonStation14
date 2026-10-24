package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Immutable combat eligibility/defaults; current combat-mode state is runtime-owned. */
public record CombatModeComponent(boolean initiallyInCombat, java.util.Optional<Boolean> canDisarm,
                                  float baseDisarmFailChance, java.util.Optional<String> disarmSuccessSound) implements CharacterComponent {
    public static final String TYPE = "CombatMode";
    public static final CombatModeComponent DEFAULT = new CombatModeComponent(false, java.util.Optional.empty(), .75f, java.util.Optional.of("moonstation14:ss14/effects/thudswoosh"));
    public static final Codec<CombatModeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(CombatModeComponent::type),
            Codec.BOOL.optionalFieldOf("initially_in_combat", false).forGetter(CombatModeComponent::initiallyInCombat),
            Codec.BOOL.optionalFieldOf("can_disarm").forGetter(CombatModeComponent::canDisarm),
            Codec.FLOAT.optionalFieldOf("base_disarm_fail_chance", .75f).forGetter(CombatModeComponent::baseDisarmFailChance),
            Codec.STRING.optionalFieldOf("disarm_success_sound", "moonstation14:ss14/effects/thudswoosh").xmap(java.util.Optional::of, value -> value.orElse("moonstation14:ss14/effects/thudswoosh")).forGetter(CombatModeComponent::disarmSuccessSound)
    ).apply(i, (type, initial, disarm, chance, sound) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown character component type: " + type);
        return new CombatModeComponent(initial, disarm, chance, sound);
    }));
    public CombatModeComponent(boolean initiallyInCombat, java.util.Optional<Boolean> canDisarm) { this(initiallyInCombat, canDisarm, .75f, java.util.Optional.empty()); }
    public CombatModeComponent {
        if (canDisarm == null || disarmSuccessSound == null || !Float.isFinite(baseDisarmFailChance) || baseDisarmFailChance < 0 || baseDisarmFailChance > 1)
            throw new IllegalArgumentException("invalid combat mode component");
    }
    @Override public String type() { return TYPE; }
}
