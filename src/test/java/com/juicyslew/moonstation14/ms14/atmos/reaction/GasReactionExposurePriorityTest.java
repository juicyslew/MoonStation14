package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GasReactionExposurePriorityTest {
    private static final PrototypeCatalog<GasReactionData> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"), new GasReactionData(
                    Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01), 373.149, 2000,
                    0, -1, List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)))));

    @Test void freshExposureBurnsDespiteInvalidStoredPartition() {
        var before = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), 300);
        var stale = new HotspotState(250, 1600, true, false);
        var exposure = new ExplosionHotspotExposure.Decision(true, 800, .25, 10);
        var result = GasReactionStep.evaluate(CATALOG, before, stale, exposure);
        assertFalse(result.fireOccurred(), "exposure selects metadata, not a reaction or heat source");
        assertTrue(result.events().isEmpty());
        assertSame(before, result.mixture());
        assertEquals(before.thermalEnergy(), result.mixture().thermalEnergy(), 0);
        assertEquals(250, result.nextState().orElseThrow().volumeLiters());
        assertEquals(1600, result.nextState().orElseThrow().temperatureKelvin(),
                "lower-priority exposure must not regress an existing hotspot temperature");
    }
}
