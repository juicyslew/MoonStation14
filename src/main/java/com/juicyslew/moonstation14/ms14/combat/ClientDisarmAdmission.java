package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent;

/** Client-only known-prototype gate for disarm presentation; it grants no authority. */
public final class ClientDisarmAdmission {
    private ClientDisarmAdmission() { }

    public static boolean allows(CharacterData target, boolean living, boolean self,
                                 boolean incapacitated, boolean spectator) {
        if (!living || self || incapacitated || spectator || target == null) return false;
        return target.component(HandsPrototypeComponent.class).isPresent()
                || target.component(StatusEffectsPrototypeComponent.class)
                .map(status -> status.allowed().contains(StatusEffectsPrototypeComponent.KNOCKDOWN)).orElse(false);
    }
}
