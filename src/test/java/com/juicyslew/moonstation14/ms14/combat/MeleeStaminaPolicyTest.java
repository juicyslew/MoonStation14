package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MeleeStaminaPolicyTest {
    @Test void doseUsesOnlyCommittedBluntDeltaAndAuthoredFactor() {
        assertEquals(2.5f, MeleeStaminaPolicy.amount(Map.of(DamageKeys.BLUNT, 5f).get(DamageKeys.BLUNT), .5f));
        var mixedCommittedDamage = Map.of(DamageKeys.BLUNT, 5f, DamageKeys.HEAT, 20f);
        assertEquals(2.5f, MeleeStaminaPolicy.amount(mixedCommittedDamage.get(DamageKeys.BLUNT), .5f));
        assertEquals(0f, MeleeStaminaPolicy.amount(Map.<String, Float>of().getOrDefault(DamageKeys.BLUNT, 0f), .5f));
        assertEquals(0f, MeleeStaminaPolicy.amount(5f, 0f));
        assertEquals(0f, MeleeStaminaPolicy.amount(Float.NaN, .5f));
        assertEquals(0f, MeleeStaminaPolicy.amount(5f, Float.POSITIVE_INFINITY));
        assertEquals(0f, MeleeStaminaPolicy.amount(Float.MAX_VALUE, 2f));
    }
}
