package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClientDisarmAdmissionTest {
    private static CharacterData character(Object... components) {
        return new CharacterData(List.of(), java.util.Arrays.stream(components).map(c -> (com.juicyslew.moonstation14.ms14.character.components.CharacterComponent)c).toList());
    }
    @Test void knownHandsOrKnockdownProofAdmitsButUnknownAndStaminaOnlyDoNot() {
        assertTrue(ClientDisarmAdmission.allows(character(new HandsPrototypeComponent(List.of("left"))), true, false, false, false));
        var status = new StatusEffectsPrototypeComponent(java.util.Set.of(StatusEffectsPrototypeComponent.KNOCKDOWN));
        assertTrue(ClientDisarmAdmission.allows(character(status), true, false, false, false));
        assertFalse(ClientDisarmAdmission.allows(null, true, false, false, false));
        assertFalse(ClientDisarmAdmission.allows(character(), true, false, false, false));
    }
    @Test void disallowsNonlivingSelfIncapacitatedOrSpectator() {
        var proof = character(new HandsPrototypeComponent(List.of("left")));
        assertFalse(ClientDisarmAdmission.allows(proof, false, false, false, false));
        assertFalse(ClientDisarmAdmission.allows(proof, true, true, false, false));
        assertFalse(ClientDisarmAdmission.allows(proof, true, false, true, false));
        assertFalse(ClientDisarmAdmission.allows(proof, true, false, false, true));
    }
}
