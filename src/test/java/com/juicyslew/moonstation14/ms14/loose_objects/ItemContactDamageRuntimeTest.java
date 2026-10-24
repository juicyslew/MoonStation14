package com.juicyslew.moonstation14.ms14.loose_objects;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemContactDamageRuntimeTest {
    @Test void positiveTypedDamageDecisionUsesCommittedMap() {
        assertTrue(ItemContactDamageSystem.hasCommittedPositive(Map.of("blunt", 2.1f)));
        assertFalse(ItemContactDamageSystem.hasCommittedPositive(Map.of()));
        assertFalse(ItemContactDamageSystem.hasCommittedPositive(Map.of("piercing", 0f)));
    }

    @Test void soundAndStunGateOnPositiveDamageButThrowDoesNot() {
        var contact = new LooseObjectProfile.DamageOnInteractData(Map.of("heat", 4f), true, 2f,
                false, LooseObjectProfile.DamageOnInteractData.DEFAULT_SOUND, "popup", .1f, 1.5f, true, 3f);
        boolean positive = ItemContactDamageSystem.hasCommittedPositive(Map.of("heat", 4f));
        assertTrue(positive, "positive decision is the same live gate used for sound/popup/stun");
        assertFalse(ItemContactDamageSystem.hasCommittedPositive(Map.of()));
        assertTrue(ItemContactDamageSystem.shouldThrow(contact, true, false));
        assertFalse(ItemContactDamageSystem.shouldThrow(contact, false, false));
        assertFalse(ItemContactDamageSystem.shouldThrow(contact, true, true));
        var inactive = new LooseObjectProfile.DamageOnInteractData(Map.of("heat", 4f), false, 2f,
                false, contact.soundEvent(), contact.popupText(), .1f, 1.5f, true, 3f);
        assertFalse(ItemContactDamageSystem.shouldThrow(inactive, true, false));
        var ignoresContactProtection = new LooseObjectProfile.DamageOnInteractData(Map.of("heat", 4f), true, 2f,
                true, contact.soundEvent(), contact.popupText(), .1f, 1.5f, true, 3f);
        assertFalse(ItemContactDamageSystem.ordinaryArmorBypass(ignoresContactProtection),
                "IgnoreResistances controls contact protection only; ordinary armor remains active");
    }
}
