package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LungToxicityTest {
    @Test void actualMolesAccumulateByDamageKeyAndCapPreservesRatios() {
        var inhaled = new GasMixture(Map.of(GasType.PLASMA, 2d, GasType.TRITIUM, 1d,
                GasType.NITROGEN, 4d), 300);
        var rates = Map.of("plasma", Map.of("poison", 2d, "radiation", 1d),
                "tritium", Map.of("radiation", 4d));
        var uncapped = LungToxicity.perInhale(inhaled, rates, 10d).orElseThrow();
        assertEquals(4f, uncapped.get("poison"));
        assertEquals(6f, uncapped.get("radiation"));
        var capped = LungToxicity.perInhale(inhaled, rates, 5d).orElseThrow();
        assertEquals(2f, capped.get("poison"));
        assertEquals(3f, capped.get("radiation"));
        var fractionalCap = LungToxicity.perInhale(inhaled, rates, 0.3).orElseThrow();
        assertTrue((double) fractionalCap.get("poison") + fractionalCap.get("radiation") <= 0.3,
                "float conversion cannot round above the per-inhale cap");
        assertEquals(Map.of(), LungToxicity.perInhale(inhaled, Map.of(), 5d).orElseThrow());
        assertEquals(Map.of(), LungToxicity.perInhale(GasMixture.vacuum(), rates, 5d).orElseThrow());
        assertEquals(Map.of(), LungToxicity.perInhale(inhaled, rates, 0d).orElseThrow());
        assertEquals(2f, LungToxicity.perInhale(inhaled,
                Map.of("nitrogen", Map.of("caustic", 0.5)), 5d).orElseThrow().get("caustic"));
    }

    @Test void malformedPolicyAndOverflowFailClosed() {
        var inhaled = new GasMixture(Map.of(GasType.PLASMA, 2d), 300);
        assertTrue(LungToxicity.perInhale(inhaled, Map.of("plasma", Map.of("poison", 1d)), Double.NaN).isEmpty());
        assertTrue(LungToxicity.perInhale(inhaled, Map.of("unknown", Map.of("poison", 1d)), 4).isEmpty());
        assertTrue(LungToxicity.perInhale(inhaled, Map.of("plasma", Map.of("unknown", 1d)), 4).isEmpty());
        assertTrue(LungToxicity.perInhale(inhaled, Map.of("plasma", Map.of("poison", -1d)), 4).isEmpty());
        assertTrue(LungToxicity.perInhale(inhaled, Map.of("plasma", Map.of("poison", Double.MAX_VALUE)), 4).isEmpty());
    }

    @Test void toxicityUsesOnlyInhaledPortionRatherThanStoredOrganGas() {
        var stored = new GasMixture(Map.of(GasType.PLASMA, 2d), 300);
        var inhaled = new GasMixture(Map.of(GasType.NITROGEN, 0.02), 300);
        assertEquals(Map.of(), LungToxicity.perInhale(inhaled,
                Map.of("plasma", Map.of("poison", 1d)), 5).orElseThrow());
        assertEquals(2f, LungToxicity.perInhale(stored,
                Map.of("plasma", Map.of("poison", 1d)), 5).orElseThrow().get("poison"));
    }

    @Test void configuredGasMinimumIsInclusiveAndOnlyGatesThatGas() {
        double minimum = 0.5 / 1144d;
        var rates = Map.of("carbon_dioxide", Map.of("poison", 0.5d), "plasma", Map.of("poison", 1d));
        var thresholds = Map.of("carbon_dioxide", minimum);
        var nominal = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 5e-7), 293.6);
        assertEquals(Map.of(), LungToxicity.perInhale(nominal, rates, thresholds, 4).orElseThrow());
        var boundary = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, minimum), 293.6);
        assertEquals((float) (minimum * 0.5), LungToxicity.perInhale(boundary, rates, thresholds, 4)
                .orElseThrow().get("poison"));
        var substantial = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 0.01), 293.6);
        assertEquals(0.005f, LungToxicity.perInhale(substantial, rates, thresholds, 4)
                .orElseThrow().get("poison"));
        var plasma = new GasMixture(Map.of(GasType.PLASMA, 0.001), 293.6);
        assertEquals(0.001f, LungToxicity.perInhale(plasma, rates, thresholds, 4)
                .orElseThrow().get("poison"));
        assertTrue(LungToxicity.perInhale(nominal, rates, Map.of("unknown", 0d), 4).isEmpty());
        assertTrue(LungToxicity.perInhale(nominal, rates, Map.of("carbon_dioxide", Double.NaN), 4).isEmpty());
    }
}
