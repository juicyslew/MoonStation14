package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobThresholdsComponent;
import java.util.Optional;

/** Resolved component pair consumed by threshold-driven mob-state behavior. */
public record MobStatePolicy(MobStateComponent states, MobThresholdsComponent thresholds) {
    public static Optional<MobStatePolicy> from(CharacterData resolvedCharacter) {
        var states = resolvedCharacter.component(MobStateComponent.class);
        var thresholds = resolvedCharacter.component(MobThresholdsComponent.class);
        return states.isPresent() && thresholds.isPresent()
                ? Optional.of(new MobStatePolicy(states.get(), thresholds.get())) : Optional.empty();
    }
}

