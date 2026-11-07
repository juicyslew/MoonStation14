package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.*;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TegFueledLoopMeasurementTest {
    @Test void aggregatesThreeDistinctComponentsByActualInventoryHeatCapacityAndVolume(){
        var parts=List.of(new GasMixture(Map.of(GasType.NITROGEN,6d),280,6),new GasMixture(Map.of(GasType.NITROGEN,10d),400,10),new GasMixture(Map.of(GasType.NITROGEN,27d),320,27));
        var measured=TegFueledLoopMeasurement.measure(parts);var oracle=PipeGasLedger.merge(parts);var gas=measured.mixture();
        assertEquals(3,measured.componentCount());assertEquals(43,gas.moles(GasType.NITROGEN),1e-5);assertNotEquals(parts.get(0).totalMoles(),gas.totalMoles());
        assertEquals(oracle.temperatureKelvin(),gas.temperatureKelvin(),1e-4);assertEquals(43,gas.volumeCubicMeters(),1e-6);
        assertEquals(gas.pressureKpa(43),gas.pressureKpa(),1e-9);
    }
}
