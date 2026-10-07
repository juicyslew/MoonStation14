package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotspotBypassParityTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"), new GasReactionData(
                    Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01), 373.149, 2000,
                    0, -1, List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)))));

    private static GasMixture mix(double temperature) {
        return new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), temperature);
    }

    @Test void bypassIsSelectedByExplicitHotVolumeAboveNinetyFivePercentOfCell() {
        var gas = mix(400);
        double cellLiters = gas.volumeCubicMeters() * 1000;
        var exact = HotspotKernel.process(CATALOG, gas,
                new HotspotState(cellLiters * .95, 400, true, false), 0);
        var above = HotspotKernel.process(CATALOG, gas,
                new HotspotState(cellLiters * .951, 400, true, false), .01);
        assertFalse(exact.nextState().orElseThrow().bypass());
        assertTrue(above.nextState().orElseThrow().bypass());
        assertEquals(.01 * 40000, above.nextState().orElseThrow().volumeLiters(), 1e-9);
    }

    @Test void firstProcessOnlyClearsSkipFlagWithoutChangingHotMetadataOrGas() {
        var gas = mix(300);
        var state = new HotspotState(200, 900, false, false);
        var result = HotspotKernel.process(CATALOG, gas, state, 0);
        assertSame(gas, result.mixture());
        assertTrue(result.events().isEmpty());
        var next = result.nextState().orElseThrow();
        assertEquals(200, next.volumeLiters());
        assertEquals(900, next.temperatureKelvin());
        assertTrue(next.skippedFirstProcess());
    }
}
