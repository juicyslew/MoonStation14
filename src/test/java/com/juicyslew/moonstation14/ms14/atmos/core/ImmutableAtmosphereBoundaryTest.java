package com.juicyslew.moonstation14.ms14.atmos.core;

import com.juicyslew.moonstation14.ms14.atmos.device.AtmosphereDeviceRules;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmutableAtmosphereBoundaryTest {
    @Test
    void heatedFiniteCellIsReplenishedByNonVacuumAmbientWithoutLosingLedgeredMatterOrEnergy() {
        GasMixture cell = new GasMixture(Map.of(GasType.OXYGEN, 1.0e-100), 300.0);
        GasMixture ambient = GasMixture.breathableAir();
        double initialEnergy = cell.thermalEnergy();
        double heaterEnergy = 0.0;
        double boundaryEnergyExport = 0.0;
        double oxygenExport = 0.0;
        double nitrogenExport = 0.0;

        for (int step = 0; step < 12; step++) {
            // A sub-threshold residue cannot be heated; first let the open face contact ambient.
            if (step == 0) {
                ImmutableAtmosphereBoundary.Result initialExchange = ImmutableAtmosphereBoundary.exchange(
                        cell, ambient, 0.5, false);
                assertLedger(cell, initialExchange);
                oxygenExport += initialExchange.perGasExported().getOrDefault(GasType.OXYGEN, 0.0);
                nitrogenExport += initialExchange.perGasExported().getOrDefault(GasType.NITROGEN, 0.0);
                boundaryEnergyExport += initialExchange.energyExportedJoules();
                cell = initialExchange.finiteAfter();
            }
            double offer = AtmosphereDeviceRules.heaterOffer(cell, 40000.0);
            assertTrue(offer > 0.0);
            cell = cell.withEnergyDelta(offer);
            heaterEnergy += offer;
            assertTrue(cell.temperatureKelvin() <= AtmosphereDeviceRules.HEATER_MAX_TEMPERATURE_KELVIN);

            ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                    cell, ambient, 0.5, false);
            assertLedger(cell, result);
            oxygenExport += result.perGasExported().getOrDefault(GasType.OXYGEN, 0.0);
            nitrogenExport += result.perGasExported().getOrDefault(GasType.NITROGEN, 0.0);
            boundaryEnergyExport += result.energyExportedJoules();
            cell = result.finiteAfter();
            assertTrue(cell.temperatureKelvin() <= AtmosphereDeviceRules.HEATER_MAX_TEMPERATURE_KELVIN);
        }

        assertTrue(cell.moles(GasType.OXYGEN) > 1e-100);
        assertTrue(cell.moles(GasType.NITROGEN) > 1e-100);
        assertTrue(cell.moles(GasType.OXYGEN) > ambient.moles(GasType.OXYGEN) * 0.1);
        assertTrue(cell.moles(GasType.NITROGEN) > ambient.moles(GasType.NITROGEN) * 0.1);
        assertEquals(initialEnergy + heaterEnergy - boundaryEnergyExport, cell.thermalEnergy(), 1e-8);
        assertEquals(-cell.moles(GasType.OXYGEN) + 1e-100, oxygenExport, 1e-10);
        assertEquals(-cell.moles(GasType.NITROGEN), nitrogenExport, 1e-10);
    }

    @Test
    void heaterDoesNotCreateVacuumAmbientAndBoundaryLedgerAccountsVacuumRemoval() {
        GasMixture residue = new GasMixture(Map.of(GasType.OXYGEN, 1e-100), 300.0);
        assertEquals(0.0, AtmosphereDeviceRules.heaterOffer(residue, 40000.0));
        GasMixture sealed = residue.withEnergyDelta(40000.0);
        assertEquals(residue.moles(GasType.OXYGEN), sealed.moles(GasType.OXYGEN), 0.0,
                "thermal mutation of a sealed mixture does not delete its species");

        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                sealed, GasMixture.vacuum(), 0.5, true);
        assertLedger(sealed, result);
        for (GasType type : GasType.values())
            assertEquals(sealed.moles(type) - result.finiteAfter().moles(type),
                    result.perGasExported().getOrDefault(type, 0.0), 0.0);
        assertEquals(sealed.thermalEnergy() - result.finiteAfter().thermalEnergy(),
                result.energyExportedJoules(), 0.0);
    }

    @Test
    void finiteAirExportsIntoImmutableVacuum() {
        GasMixture air = GasMixture.breathableAir();
        GasMixture vacuum = GasMixture.vacuum();
        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(air, vacuum, 0.5, true);

        assertTrue(result.perGasExported().get(GasType.OXYGEN) > 0.0);
        assertTrue(result.perGasExported().get(GasType.NITROGEN) > 0.0);
        assertTrue(result.finiteAfter().moles(GasType.OXYGEN) > 0.0);
        assertTrue(result.finiteAfter().moles(GasType.OXYGEN) < air.moles(GasType.OXYGEN));
        assertTrue(result.finiteAfter().moles(GasType.NITROGEN) > 0.0);
        assertTrue(result.finiteAfter().moles(GasType.NITROGEN) < air.moles(GasType.NITROGEN));
        assertEquals(0.0, vacuum.totalMoles(), 0.0);
        assertEquals(2.7, vacuum.temperatureKelvin(), 0.0);
        assertNotSame(air, result.finiteAfter());
        assertLedger(air, result);
    }

    @Test
    void spaceDepressurizationRemovesHalfPressureAndCarriesSourceEnergy() {
        GasMixture air = GasMixture.breathableAir();
        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                air, GasMixture.vacuum(), 0.125, true);

        assertEquals(air.totalMoles() * 0.5, result.finiteAfter().totalMoles(), 1e-10);
        assertTrue(result.finiteAfter().temperatureKelvin() < air.temperatureKelvin(),
                "the separate space heat sink cools the remaining gas");
        assertEquals(air.moles(GasType.OXYGEN) / air.totalMoles(),
                result.finiteAfter().moles(GasType.OXYGEN) / result.finiteAfter().totalMoles(), 1e-12);
        assertLedger(air, result);
    }

    @Test
    void lowInventorySpaceExchangeRemovesOnlyHalfUntilEmptyEpsilon() {
        GasMixture afterOnePass = new GasMixture(Map.of(GasType.OXYGEN, 1.5), 300.0);
        ImmutableAtmosphereBoundary.Result cleanup = ImmutableAtmosphereBoundary.exchange(
                afterOnePass, GasMixture.vacuum(), 0.125, true);

        assertEquals(afterOnePass.totalMoles() * 0.5, cleanup.finiteAfter().totalMoles(), 1e-12);
        assertEquals(afterOnePass.totalMoles() * 0.5,
                cleanup.perGasExported().get(GasType.OXYGEN), 1e-12);
        assertLedger(afterOnePass, cleanup);
    }

    @Test
    void coldLowPressureGasDrainsGraduallyIntoSpace() {
        GasMixture coldGas = new GasMixture(Map.of(GasType.OXYGEN, 5.0), 50.0);
        assertTrue(coldGas.pressureKpa(1.0) < 10.0);

        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                coldGas, GasMixture.vacuum(), 0.125, true);

        assertEquals(2.5, result.finiteAfter().totalMoles(), 1e-12);
        assertEquals(2.5, result.perGasExported().get(GasType.OXYGEN), 1e-12);
        assertLedger(coldGas, result);
    }

    @Test
    void oneMoleTritiumDrainsSmoothlyThenSnapsOnlyBelowEmptyEpsilon() {
        GasMixture finite = new GasMixture(Map.of(GasType.TRITIUM, 1.0), 293.15);
        GasMixture vacuum = GasMixture.vacuum();

        ImmutableAtmosphereBoundary.Result first = ImmutableAtmosphereBoundary.exchange(
                finite, vacuum, 0.125, true);
        assertEquals(0.5, first.finiteAfter().moles(GasType.TRITIUM), 1e-12);
        assertLedger(finite, first);

        finite = first.finiteAfter();
        int passes = 1;
        while (finite.totalMoles() > 0.0 && passes++ < 64) {
            GasMixture before = finite;
            ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                    before, vacuum, 0.125, true);
            assertTrue(result.finiteAfter().totalMoles() < before.totalMoles());
            assertLedger(before, result);
            finite = result.finiteAfter();
        }
        assertTrue(passes < 64, "repeated fallback exchanges should reach the empty epsilon");
        assertEquals(0.0, finite.totalMoles(), 0.0);
        assertEquals(0.0, ImmutableAtmosphereBoundary.exchange(finite, vacuum, 0.125, true)
                .finiteAfter().totalMoles(), 0.0, "empty vacuum cell must not refill without a source");
    }

    @Test
    void spaceOutflowPreservesOxygenNitrogenTritiumRatiosAndLedger() {
        GasMixture mixture = new GasMixture(Map.of(
                GasType.OXYGEN, 10.0,
                GasType.NITROGEN, 30.0,
                GasType.TRITIUM, 1.0), 300.0);

        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                mixture, GasMixture.vacuum(), 0.125, true);

        assertEquals(mixture.totalMoles() * 0.5, result.finiteAfter().totalMoles(), 1e-12);
        for (GasType type : GasType.values()) {
            assertEquals(mixture.moles(type) / mixture.totalMoles(),
                    result.finiteAfter().moles(type) / result.finiteAfter().totalMoles(), 1e-12);
            assertTrue(result.finiteAfter().moles(type) <= mixture.moles(type));
        }
        assertTrue(result.finiteAfter().temperatureKelvin() < mixture.temperatureKelvin());
        assertLedger(mixture, result);
    }

    @Test
    void twelveOpeningsExchangeMoreRoomGasThanOneOpening() {
        GasMixture roomCell = GasMixture.breathableAir();
        double initial = roomCell.totalMoles();
        double oneOpening = ImmutableAtmosphereBoundary.exchange(
                roomCell, GasMixture.vacuum(), 0.125, true).finiteAfter().totalMoles();
        double twelveOpenings = roomCell.totalMoles();
        for (int edge = 0; edge < 12; edge++) {
            twelveOpenings = ImmutableAtmosphereBoundary.exchange(
                    new GasMixture(Map.of(GasType.OXYGEN, twelveOpenings * 0.21,
                            GasType.NITROGEN, twelveOpenings * 0.79), roomCell.temperatureKelvin()),
                    GasMixture.vacuum(), 0.125, true).finiteAfter().totalMoles();
        }

        assertTrue(initial - twelveOpenings > initial - oneOpening);
    }

    @Test
    void repeatedExchangeApproachesSpaceWithoutAccumulatingExteriorGas() {
        GasMixture finite = GasMixture.breathableAir();
        GasMixture vacuum = GasMixture.vacuum();
        double initialOxygen = finite.moles(GasType.OXYGEN);
        for (int i = 0; i < 20; i++) {
            finite = ImmutableAtmosphereBoundary.exchange(finite, vacuum, 0.25, true).finiteAfter();
        }
        assertTrue(finite.moles(GasType.OXYGEN) < initialOxygen * 0.01);
        assertEquals(0.0, vacuum.totalMoles(), 0.0);
        assertEquals(0.0, vacuum.thermalEnergy(), 0.0);
    }

    @Test
    void spaceHeatExchangeCoolsTowardSpaceTemperature() {
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 500.0);
        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                hot, GasMixture.vacuum(), 0.0, true);

        assertEquals(hot.totalMoles() * 0.5, result.finiteAfter().totalMoles(), 1e-12,
                "high-pressure outflow relaxes half the pressure difference before heat transfer");
        assertTrue(result.finiteAfter().temperatureKelvin() < hot.temperatureKelvin());
        assertTrue(result.finiteAfter().temperatureKelvin() > 2.7);
        assertTrue(result.energyExportedJoules() > 0.0);
        for (GasType type : GasType.values()) {
            assertTrue(result.finiteAfter().moles(type) <= hot.moles(type));
        }
        assertLedger(hot, result);
    }

    @Test
    void nonSpaceAmbientCanReplenishFiniteCellAndLedgerIsSigned() {
        GasMixture empty = GasMixture.vacuum();
        GasMixture ambient = GasMixture.breathableAir();
        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(empty, ambient, 0.5, false);

        assertTrue(result.finiteAfter().moles(GasType.OXYGEN) > 0.0);
        assertTrue(result.perGasExported().get(GasType.OXYGEN) < 0.0);
        assertTrue(result.energyExportedJoules() < 0.0);
        assertEquals(ambient.totalMoles(), GasMixture.breathableAir().totalMoles(), 1e-12);
        assertLedger(empty, result);
    }

    @Test
    void ledgerMatchesFiniteStateAndIsImmutable() {
        GasMixture finite = new GasMixture(Map.of(GasType.OXYGEN, 2.0, GasType.NITROGEN, 1.0), 350.0);
        GasMixture exterior = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.CARBON_DIOXIDE, 2.0), 250.0);
        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(finite, exterior, 0.3, false);

        assertLedger(finite, result);
        assertThrows(UnsupportedOperationException.class,
                () -> result.perGasExported().put(GasType.PLASMA, 1.0));
        assertEquals(1.0, exterior.moles(GasType.OXYGEN), 0.0);
        assertEquals(2.0, exterior.moles(GasType.CARBON_DIOXIDE), 0.0);
    }

    @Test
    void closedFiniteFinitePairConservesSpecies() {
        GasMixture first = new GasMixture(Map.of(GasType.OXYGEN, 3.0, GasType.NITROGEN, 2.0), 300.0);
        GasMixture second = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.NITROGEN, 4.0), 280.0);
        AtmosphereTransfer.Result result = AtmosphereTransfer.step(first, second, 0.4);
        for (GasType type : GasType.values()) {
            assertEquals(first.moles(type) + second.moles(type),
                    result.first().moles(type) + result.second().moles(type), 1e-12);
        }
    }

    @Test
    void rejectsInvalidInputsAndNeverProducesInvalidCellEnergy() {
        GasMixture finite = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300.0);
        GasMixture vacuum = GasMixture.vacuum();
        assertThrows(NullPointerException.class, () -> ImmutableAtmosphereBoundary.exchange(null, vacuum, 0.1, true));
        assertThrows(NullPointerException.class, () -> ImmutableAtmosphereBoundary.exchange(finite, null, 0.1, true));
        assertThrows(IllegalArgumentException.class,
                () -> ImmutableAtmosphereBoundary.exchange(finite, vacuum, Double.NaN, true));
        assertThrows(IllegalArgumentException.class,
                () -> ImmutableAtmosphereBoundary.exchange(finite, vacuum, 0.5001, true));
        assertThrows(IllegalArgumentException.class,
                () -> ImmutableAtmosphereBoundary.exchange(finite, vacuum, -0.1, true));

        ImmutableAtmosphereBoundary.Result gasFree = ImmutableAtmosphereBoundary.exchange(
                GasMixture.vacuum(), vacuum, 0.5, true);
        assertEquals(0.0, gasFree.finiteAfter().heatCapacity(), 0.0);
        assertEquals(0.0, gasFree.finiteAfter().thermalEnergy(), 0.0);
        assertFalse(Double.isNaN(gasFree.energyExportedJoules()));
    }

    private static void assertLedger(GasMixture before, ImmutableAtmosphereBoundary.Result result) {
        for (GasType type : GasType.values()) {
            assertEquals(before.moles(type) - result.finiteAfter().moles(type),
                    result.perGasExported().getOrDefault(type, 0.0), 1e-12);
        }
        assertEquals(before.thermalEnergy() - result.finiteAfter().thermalEnergy(),
                result.energyExportedJoules(), 1e-9);
        assertTrue(Double.isFinite(result.finiteAfter().thermalEnergy()));
        assertTrue(result.finiteAfter().thermalEnergy() >= 0.0);
    }
}
