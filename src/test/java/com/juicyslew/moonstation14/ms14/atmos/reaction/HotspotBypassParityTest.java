package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotspotBypassParityTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"), new GasReactionData(
                    Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01), 373.149, 2000,
                    0, -1, List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)))));

    private static GasMixture mix(double temperature) {
        return new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), temperature);
    }

    @Test void exactThresholdPartitionsButStrictlyOverThresholdBypassesWholeCell() {
        var gas = mix(400);
        var partial = HotspotKernel.evaluate(gas, CATALOG, new HotspotKernel.HotspotState(.95, 400));
        var bypass = HotspotKernel.evaluate(gas, CATALOG, new HotspotKernel.HotspotState(.9500001, 400));
        assertEquals(.0095, partial.totalFireExtentMoles(), 1e-12);
        assertEquals(.01, bypass.totalFireExtentMoles(), 1e-12);
        assertTrue(partial.nextState().orElseThrow().fraction() > .95,
                "crossing the threshold grows state only; no same-pass spread");
        assertFalse(partial.spreadEligibleThisPass());
        assertEquals(1, bypass.nextState().orElseThrow().fraction());
        assertEquals(bypass.mixture().temperatureKelvin() > 423.15, bypass.spreadEligibleThisPass(),
                "strictly-over-threshold bypass uses the whole-cell post-fire temperature gate");

        var impossible = HotspotKernel.evaluate(gas, CATALOG,
                new HotspotKernel.HotspotState(.96, 1600));
        assertTrue(impossible.events().isEmpty(), "an invalid stored label cannot energize whole-cell bypass");
    }

    @Test void bypassSpreadGateUsesPostReactionBulkTemperature() {
        var hot = HotspotKernel.evaluate(mix(423.2), CATALOG,
                new HotspotKernel.HotspotState(.96, 423.2));
        assertTrue(hot.mixture().temperatureKelvin() > 423.15);
        assertTrue(hot.spreadEligibleThisPass());

        var below = HotspotKernel.evaluate(mix(423.15), CATALOG,
                new HotspotKernel.HotspotState(.96, 423.15));
        assertEquals(below.mixture().temperatureKelvin() > 423.15, below.spreadEligibleThisPass(),
                "spread eligibility follows the post-fire bulk temperature, not its pre-fire value");
    }
}
