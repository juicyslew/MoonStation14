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

        assertEquals(5.0, result.inhaled().totalMoles(), 8 * Math.ulp(5.0f));
        for (GasType type : GasType.values()) {
            assertEquals(room.moles(type) + exhaled.moles(type),
                    result.inhaled().moles(type) + result.roomAfter().moles(type), 8 * Math.ulp((float) room.moles(type) + (float) exhaled.moles(type)));
        }
        double totalEnergy = room.thermalEnergy() + exhaled.thermalEnergy();
        assertEquals(totalEnergy, result.inhaled().thermalEnergy() + result.roomAfter().thermalEnergy(),
                8 * Math.ulp((float) totalEnergy));
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
        assertEquals((double) (float) .3, returned.roomAfter().moles(GasType.CARBON_DIOXIDE), 0);
        assertFalse(BreathExchange.calculate(room, Double.NaN, GasMixture.vacuum()).isPresent());
        assertTrue(BreathExchange.calculate(GasMixture.vacuum(), 1.0, GasMixture.vacuum())
                .orElseThrow().inhaled().totalMoles() == 0.0);
    }

    @Test
    void tinyInhalePacketIsCleanedWithoutDeletingRoomRemainder() {
        GasMixture room = new GasMixture(Map.of(GasType.OXYGEN, 2.3e-5), 293.15);

        BreathExchange result = BreathExchange.calculate(room, room.totalMoles() * 0.0005,
                GasMixture.vacuum()).orElseThrow();

        assertEquals(0.0, result.inhaled().totalMoles());
        assertTrue(result.roomAfter().totalMoles() > 2.29e-5);
    }

    @Test
    void tinyPacketAndRemainderAreIndependentlyCleaned() {
        GasMixture room = new GasMixture(Map.of(GasType.OXYGEN, 5.1e-8), 293.15);

        BreathExchange result = BreathExchange.calculate(room, room.totalMoles() * 0.9,
                GasMixture.vacuum()).orElseThrow();

        assertEquals(0.0, result.inhaled().totalMoles());
        assertEquals(0.0, result.roomAfter().totalMoles());
    }
}
