package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class FiniteVentilationTransferTest {
    @Test void wideNetSelectionIsCenterAndFourHorizontalCardinalsOnly() {
        BlockPos center = new BlockPos(10, 20, 30);
        assertArrayEquals(new BlockPos[]{center, center.west(), center.east(), center.north(), center.south()},
                FiniteVentilationTransfer.trustedScrubberTargets(center, true, ignored -> true, ignored -> true).toArray(BlockPos[]::new));
        assertArrayEquals(new BlockPos[]{center}, FiniteVentilationTransfer.trustedScrubberTargets(
                center, false, ignored -> true, ignored -> true).toArray(BlockPos[]::new));
    }

    @Test void wideNetRequiresLoadedExchangeableFiniteNeighbor() {
        BlockPos center = BlockPos.ZERO;
        var loadedPassable = new java.util.HashSet<>(java.util.List.of(center.west(), center.east(), center.north(), center.south()));
        var finite = new java.util.HashSet<>(loadedPassable);
        loadedPassable.remove(center.east()); // unloaded/blocked neighbor
        finite.remove(center.north()); // loaded, passable, but unknown/exterior ownership
        var selected = FiniteVentilationTransfer.trustedScrubberTargets(center, true,
                loadedPassable::contains, pos -> pos.equals(center) || finite.contains(pos));
        assertEquals(java.util.List.of(center, center.west(), center.south()), selected);
        assertTrue(FiniteVentilationTransfer.trustedScrubberTargets(center, true,
                loadedPassable::contains, ignored -> false).isEmpty(), "unknown center must not bridge into finite neighbors");
        assertFalse(FiniteVentilationTransfer.trustedScrubberTargets(center, true,
                ignored -> false, ignored -> true).contains(center.east()));
    }

    @Test void sharedCellTransferUsesFullRateAndConservesEverySpeciesAndEnergy() {
        var defaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER).settings();
        var settings = new VentilationDeviceConfig.Settings(0, true, defaults.externalPressureBoundKPa(),
                defaults.targetPressureChangeKPaPerSecond(), defaults.targetPressureKPa(), 0.2,
                VentilationDeviceConfig.ScrubberMode.SIPHON, defaults.filteredGases(), defaults.selectedLayer(), false, true);
        MemoryEndpoint pipe = new MemoryEndpoint(GasMixture.vacuum());
        GasMixture[] rooms = new GasMixture[5];
        GasMixture[] afterRooms = new GasMixture[5];
        double startingEnergy = pipe.gas.thermalEnergy();
        double startingMoles = 0;
        for (int i = 0; i < rooms.length; i++) {
            rooms[i] = new GasMixture(Map.of(GasType.OXYGEN, 10.0, GasType.NITROGEN, 20.0,
                    GasType.CARBON_DIOXIDE, 5.0), 300.0);
            GasMixture roomBefore = rooms[i];
            GasMixture pipeBefore = pipe.gas;
            startingMoles += rooms[i].totalMoles();
            MemoryEndpoint endpoint = new MemoryEndpoint(rooms[i]);
            assertTrue(FiniteVentilationTransfer.transferCell(VentilationDeviceConfig.Kind.SCRUBBER,
                    settings, endpoint, pipe, 1.0).moved());
            double captured = roomBefore.totalMoles() - endpoint.gas.totalMoles();
            // room volume 1m3 and 0.2m3/s for 1s: full 20% of this cell, not 1/5 of the rate.
            assertEquals(roomBefore.totalMoles() * 0.2, captured, 1e-10);
            for (GasType gas : GasType.values())
                assertEquals(roomBefore.moles(gas) + pipeBefore.moles(gas),
                        endpoint.gas.moles(gas) + pipe.gas.moles(gas), 1e-9);
            assertEquals(roomBefore.thermalEnergy() + pipeBefore.thermalEnergy(),
                    endpoint.gas.thermalEnergy() + pipe.gas.thermalEnergy(), 1e-7);
            afterRooms[i] = endpoint.gas;
            startingEnergy += rooms[i].thermalEnergy();
        }
        assertEquals(startingMoles, pipe.gas.totalMoles() + roomsSumAfter(afterRooms), 1e-8);
        assertEquals(startingEnergy, pipe.gas.thermalEnergy() + energyAfter(afterRooms), 1e-7);
        assertEquals(5, pipe.writes, "one transaction per admitted cell in the same callback");
    }

    private static double roomsSumAfter(GasMixture[] rooms) { return java.util.Arrays.stream(rooms).mapToDouble(GasMixture::totalMoles).sum(); }
    private static double energyAfter(GasMixture[] rooms) { return java.util.Arrays.stream(rooms).mapToDouble(GasMixture::thermalEnergy).sum(); }

    private static final class MemoryEndpoint implements PipeGasTransaction.Endpoint {
        final Object id = new Object();
        GasMixture gas;
        int writes;
        MemoryEndpoint(GasMixture gas) { this.gas = gas; }
        @Override public Object identity() { return id; }
        @Override public long version() { return writes; }
        @Override public GasMixture snapshot() { return gas; }
        @Override public boolean eligible() { return true; }
        @Override public boolean writeRaw(GasMixture expected, GasMixture next) {
            if (expected != null && !expected.equals(gas)) return false;
            gas = next;
            writes++;
            return true;
        }
        @Override public void publish() { }
    }
}
