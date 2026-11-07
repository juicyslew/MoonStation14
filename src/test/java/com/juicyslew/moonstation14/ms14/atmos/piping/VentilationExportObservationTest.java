package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VentilationExportObservationTest {
    private static VentilationDeviceBlockEntity device() {
        return new VentilationDeviceBlockEntity(new net.minecraft.core.BlockPos(0, 1, 0),
                com.juicyslew.moonstation14.block.ModBlocks.GAS_VENT.get().defaultBlockState());
    }

    @Test void committedInternalTransferCountsTransferButNotExteriorExport() {
        var device = device();
        var packet = new GasMixture(Map.of(GasType.PLASMA, 2.5), 300.0);
        VentilationGasRuntime.recordCommittedTransfer(device, new FiniteVentilationTransfer.Result(packet));
        assertEquals(2.5, device.transferObservation().speciesMoles().get(GasType.PLASMA));
        assertTrue(device.exportObservation().speciesMoles().isEmpty());
    }

    @Test void exteriorTransferCountsOnceInEachDistinctReceipt() {
        var device = device();
        var packet = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 3.0), 300.0);
        var kind = AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT;
        VentilationGasRuntime.recordCommittedTransfer(device,
                new FiniteVentilationTransfer.Result(packet, new FiniteVentilationTransfer.ExportReceipt(kind, packet)));
        assertEquals(3.0, device.transferObservation().speciesMoles().get(GasType.CARBON_DIOXIDE));
        assertEquals(3.0, device.exportObservation().speciesMoles().get(GasType.CARBON_DIOXIDE));
        assertEquals(packet.thermalEnergy(), device.transferObservation().thermalJoules());
    }

    @Test void absentOrUnmovedResultDoesNotCreateReceiptAndDurableCounterSurvivesReloadOnly() {
        var device = device();
        VentilationGasRuntime.recordCommittedTransfer(device, FiniteVentilationTransfer.Result.none());
        assertTrue(device.transferObservation().speciesMoles().isEmpty());
        var packet = new GasMixture(Map.of(GasType.OXYGEN, 1.25), 295.0);
        VentilationGasRuntime.recordCommittedTransfer(device, new FiniteVentilationTransfer.Result(packet));
        var saved = device.transferObservation().save();
        var loaded = new VentilationGasRuntime.TransferObservation();
        loaded.load(saved);
        assertEquals(device.transferObservation().speciesMoles(), loaded.speciesMoles());
        assertEquals(device.transferObservation().thermalJoules(), loaded.thermalJoules());
        assertTrue(device().transferObservation().speciesMoles().isEmpty());
        assertTrue(device.exportObservation().speciesMoles().isEmpty());
    }

    @Test void observationsAggregateReceiptsAndExposeImmutableIndependentSnapshots() {
        var observation = new VentilationGasRuntime.ExportObservation();
        var kind = AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT;
        var firstPacket = new GasMixture(Map.of(GasType.OXYGEN, 2.0, GasType.NITROGEN, 3.0), 300.0);
        var secondPacket = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 300.0);
        observation.add(new FiniteVentilationTransfer.ExportReceipt(kind, firstPacket));
        var baselineMoles = observation.speciesMoles();
        var baselineEnergy = observation.thermalJoulesByBoundary();
        observation.add(new FiniteVentilationTransfer.ExportReceipt(kind, secondPacket));

        assertEquals(Map.of(GasType.OXYGEN, 6.0, GasType.NITROGEN, 3.0), observation.speciesMoles());
        assertEquals(firstPacket.thermalEnergy() + secondPacket.thermalEnergy(),
                observation.thermalJoulesByBoundary().get(kind));
        assertEquals(firstPacket.gasMoles(), baselineMoles);
        assertEquals(Map.of(kind, firstPacket.thermalEnergy()), baselineEnergy);
        assertThrows(UnsupportedOperationException.class, () -> observation.speciesMoles().put(GasType.OXYGEN, 0.0));
        assertThrows(UnsupportedOperationException.class, () -> observation.thermalJoulesByBoundary().clear());
    }

    @Test void decisionKeepsUnavailableRoomUnknownAndSeparatesLiveBoundFromPumpFactor() {
        var device = device();
        var observation = device.transferObservation();
        var settings = new VentilationDeviceConfig.Settings(0, true, 101.375,
                2.0, 4500, 0.2, VentilationDeviceConfig.ScrubberMode.FILTER,
                java.util.Set.of(GasType.PLASMA), VentilationDeviceConfig.Layer.PRIMARY);
        observation.recordDecision(10, 8, 0.6, device, new net.minecraft.core.BlockPos(1, 1, 0),
                "test-node#incarnation", 1, null, new net.minecraft.core.BlockPos(2, 1, 0), null,
                null, settings, 2.0, true,
                new com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump(
                        1.0, 101.325, 2.0,
                        com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.PressureChecks.EXTERNAL,
                        5000), true, false, null, null, null, null, null, null,
                "UNAVAILABLE_ENDPOINT_INPUT", null);
        var decision = observation.lastDecision();
        assertNotNull(decision);
        assertTrue(Double.isNaN(decision.roomVolumeM3()));
        assertTrue(Double.isNaN(decision.roomPressureKpa()));
        assertTrue(Double.isNaN(decision.acceptedMoles()));
        assertEquals(101.375, decision.liveExternalPressureBoundKpa());
        assertEquals(101.325, decision.profileExternalPressureBoundKpa());
        assertEquals(2.0, decision.pumpPowerFactor());
        assertTrue(decision.configurationAvailable());
        assertEquals("test-node#incarnation", decision.pipeNodeIdentity());
        var restored = new VentilationGasRuntime.TransferObservation();
        restored.load(observation.save());
        assertNull(restored.lastDecision());
        assertEquals(1, VentilationGasRuntime.TransferObservation.nextSequence(Long.MAX_VALUE));
        assertEquals(2, VentilationGasRuntime.TransferObservation.nextSequence(1));
    }
}
