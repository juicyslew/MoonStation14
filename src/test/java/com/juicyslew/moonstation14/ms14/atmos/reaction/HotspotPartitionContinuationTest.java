package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotspotPartitionContinuationTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"), new GasReactionData(
                    Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01), 373.149, 2000,
                    0, -1, List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)))));

    @Test void firstDueVisitOnlyClearsSkipAndColdBulkDoesNotRewriteMetadata() {
        var gas = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), 300);
        var state = new HotspotState(250, 800, false, false);
        var result = HotspotKernel.process(CATALOG, gas, state, 0);
        assertSame(gas, result.mixture());
        assertTrue(result.events().isEmpty());
        assertEquals(new HotspotState(250, 800, true, false), result.nextState().orElseThrow());
    }

    @Test void canonicalHotVolumeGuardExtinguishesSmallMetadataWithoutThermalProjection() {
        var gas = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), 500);
        var state = new HotspotState(.3, 500, true, false);
        var result = HotspotKernel.process(CATALOG, gas, state, 0);
        assertTrue(result.events().isEmpty());
        assertTrue(result.nextState().isEmpty());
        assertSame(gas, result.mixture());
    }
}
