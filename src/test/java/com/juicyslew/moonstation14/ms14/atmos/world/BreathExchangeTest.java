package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreathExchangeTest {
    @Test
    void exchangeConservesEachGasAndThermalEnergyAcrossRoomAndInhaledPortion() {
        GasMixture room = new GasMixture(Map.of(
                GasType.OXYGEN, 8.0, GasType.NITROGEN, 22.0, GasType.TRITIUM, 2.0), 280.0);
        GasMixture exhaled = new GasMixture(Map.of(
                GasType.OXYGEN, 1.0, GasType.CARBON_DIOXIDE, 3.0), 310.0);

        BreathExchange result = BreathExchange.calculate(room, 5.0, exhaled).orElseThrow();

        assertEquals(5.0, result.inhaled().totalMoles(), 1e-12);
        for (GasType type : GasType.values()) {
            assertEquals(room.moles(type) + exhaled.moles(type),
                    result.inhaled().moles(type) + result.roomAfter().moles(type), 1e-12);
        }
        assertEquals(room.thermalEnergy() + exhaled.thermalEnergy(),
                result.inhaled().thermalEnergy() + result.roomAfter().thermalEnergy(), 1e-9);
    }

    @Test
    void inhaleIsLimitedByAvailableRoomGasAndInvalidRequestsAreRejected() {
        GasMixture room = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 293.15);
        BreathExchange result = BreathExchange.calculate(room, 9.0, GasMixture.vacuum()).orElseThrow();

        assertEquals(2.0, result.inhaled().totalMoles(), 0.0);
        assertEquals(0.0, result.roomAfter().totalMoles(), 0.0);
        assertEquals(room.moles(GasType.OXYGEN), BreathExchange.calculate(room, 0.0, GasMixture.vacuum()).orElseThrow().roomAfter().moles(GasType.OXYGEN), 1e-12);
        var returned = BreathExchange.calculate(room, 0.0,
                new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 0.3), 310)).orElseThrow();
        assertEquals(0, returned.inhaled().totalMoles());
        assertEquals(0.3, returned.roomAfter().moles(GasType.CARBON_DIOXIDE));
        assertFalse(BreathExchange.calculate(room, Double.NaN, GasMixture.vacuum()).isPresent());
        assertTrue(BreathExchange.calculate(GasMixture.vacuum(), 1.0, GasMixture.vacuum())
                .orElseThrow().inhaled().totalMoles() == 0.0);
    }
}
