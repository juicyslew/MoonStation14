package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereHotspotReconciliationTest {
    // Pinned SS14 AtmosphereSystem.Hotspot.cs processes stored volume/temperature metadata;
    // unrelated mixture energy writes do not project a replacement hot-partition temperature.
    @Test void externalEnergyWritesDoNotProjectHotspotTemperatureOrVolume() {
        GasMixture before = new GasMixture(Map.of(GasType.TRITIUM, 2.0, GasType.OXYGEN, 2.0), 1800);
        HotspotState metadata = new HotspotState(800, 1700, true, false);
        GasMixture afterCooling = before.withEnergyDelta(-before.thermalEnergy() * .35);
        GasMixture afterHeating = before.withEnergyDelta(before.thermalEnergy() * .35);

        assertNotEquals(before.thermalEnergy(), afterCooling.thermalEnergy());
        assertNotEquals(before.thermalEnergy(), afterHeating.thermalEnergy());
        assertEquals(800, metadata.volumeLiters());
        assertEquals(1700, metadata.temperatureKelvin());
        assertTrue(metadata.skippedFirstProcess());
        assertFalse(metadata.bypass());
    }

    @Test void coldBulkGasDoesNotEraseHotMetadataBeforeItsProcessingGuard() {
        GasMixture coldBulk = new GasMixture(Map.of(GasType.TRITIUM, 2.0, GasType.OXYGEN, 2.0), 300);
        HotspotState metadata = new HotspotState(800, 1700, true, false);
        assertEquals(300, coldBulk.temperatureKelvin());
        assertEquals(1700, metadata.temperatureKelvin());
        assertEquals(800, metadata.volumeLiters());
    }
}
