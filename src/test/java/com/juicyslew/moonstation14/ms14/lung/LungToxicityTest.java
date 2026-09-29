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
}
