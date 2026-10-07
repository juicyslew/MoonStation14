package com.juicyslew.moonstation14.ms14.disposal.transport;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposalRideDamageDiagnosticsTest {
    @Test
    void retainsOnlyFinitePositiveActualDamageEntries() {
        var result = DisposalRideDamageDiagnostics.positiveDamage(Map.of(
                "Asphyxiation", 1.25f,
                "Healing", -2f,
                "zero", 0f,
                "infinite", Float.POSITIVE_INFINITY,
                "nan", Float.NaN));

        assertEquals(Map.of("Asphyxiation", 1.25f), result);
    }

    @Test
    void emptyAndHealingOnlyDeltasDoNotBecomeDamage() {
        assertTrue(DisposalRideDamageDiagnostics.positiveDamage(Map.of()).isEmpty());
        assertTrue(DisposalRideDamageDiagnostics.positiveDamage(Map.of("healing", -1f)).isEmpty());
    }
}
