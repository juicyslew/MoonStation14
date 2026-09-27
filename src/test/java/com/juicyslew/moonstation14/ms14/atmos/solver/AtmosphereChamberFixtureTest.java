package com.juicyslew.moonstation14.ms14.atmos.solver;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.juicyslew.moonstation14.ms14.atmos.solver.AtmosphereChamberFixture.NeighborKind.EXTERIOR;
import static com.juicyslew.moonstation14.ms14.atmos.solver.AtmosphereChamberFixture.NeighborKind.FINITE;
import static com.juicyslew.moonstation14.ms14.atmos.solver.AtmosphereChamberFixture.NeighborKind.UNKNOWN;
import static com.juicyslew.moonstation14.ms14.atmos.solver.AtmosphereChamberFixture.NeighborKind.WALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereChamberFixtureTest {
    private static final double R = 8.31446261815324;
    private static final double TEMPERATURE = AtmosphereChamberFixture.ROOM_TEMPERATURE_KELVIN;
    private static final double ONE_ATM_MOLES = 101325.0 / (R * TEMPERATURE);

    @Test
    void sealedRoomHasExpectedFiniteInventoryEnergyAndImmutableSnapshot() {
        AtmosphereChamberFixture chamber = AtmosphereChamberFixture.sealedRoom(4, 4, 3,
                Map.of(GasType.OXYGEN, 2.0, GasType.NITROGEN, 3.0), TEMPERATURE);
        assertEquals(48, chamber.finiteCells.size());
        assertEquals(240.0, chamber.finiteCells.values().stream().mapToDouble(GasMixture::totalMoles).sum());
        assertEquals((2.0 * GasType.OXYGEN.molarHeatCapacity()
                + 3.0 * GasType.NITROGEN.molarHeatCapacity()) * TEMPERATURE,
                chamber.finiteCells.values().iterator().next().thermalEnergy());
        assertEquals(101.325, new GasMixture(Map.of(GasType.OXYGEN, ONE_ATM_MOLES), TEMPERATURE).pressureKpa(1.0), 1e-10);

        AtmosphereChamberFixture.Snapshot snapshot = chamber.snapshot();
        chamber.finiteCells.put(new BlockPos(0, 0, 0), GasMixture.vacuum());
        assertEquals(2.0, snapshot.finiteCells().get(new BlockPos(0, 0, 0)).moles(GasType.OXYGEN));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.finiteCells().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.walls().clear());
    }

    @Test
    void twoRoomPressureAndSpeciesCasesShareOneAtmosphereAndWallBlocksUntilDoorOpens() {
        AtmosphereChamberFixture chamber = AtmosphereChamberFixture.twoRooms(4, 4, 3,
                AtmosphereChamberFixture.pureGasAtPressure(GasType.OXYGEN, 101.325, TEMPERATURE),
                AtmosphereChamberFixture.pureGasAtPressure(GasType.NITROGEN, 101.325, TEMPERATURE));
        assertEquals(96, chamber.finiteCells.size());
        assertEquals(101.325, chamber.finiteCells.get(new BlockPos(0, 0, 0)).pressureKpa(1.0), 1e-10);
        assertEquals(101.325, chamber.finiteCells.get(new BlockPos(5, 0, 0)).pressureKpa(1.0), 1e-10);
        assertEquals(ONE_ATM_MOLES, chamber.finiteCells.get(new BlockPos(0, 0, 0)).moles(GasType.OXYGEN), 1e-12);
        assertEquals(ONE_ATM_MOLES, chamber.finiteCells.get(new BlockPos(5, 0, 0)).moles(GasType.NITROGEN), 1e-12);

        BlockPos doorPosition = new BlockPos(4, 2, 1);
        assertEquals(WALL, chamber.neighborKind(doorPosition));
        chamber.openSingleDoor();
        assertEquals(FINITE, chamber.neighborKind(doorPosition));
        assertTrue(chamber.neighbors(new BlockPos(3, 2, 1)).contains(doorPosition));
        assertTrue(chamber.neighbors(new BlockPos(5, 2, 1)).contains(doorPosition));
        chamber.fullWall();
        assertEquals(WALL, chamber.neighborKind(doorPosition));
    }

    @Test
    void facesAreSixWayDeterministicAndExteriorIsOnlyAtExplicitOpenings() {
        AtmosphereChamberFixture chamber = AtmosphereChamberFixture.sealedRoom(4, 4, 3,
                Map.of(GasType.OXYGEN, 1.0), TEMPERATURE);
        BlockPos center = new BlockPos(1, 1, 1);
        assertEquals(List.of(new BlockPos(1, 0, 1), new BlockPos(1, 2, 1),
                new BlockPos(1, 1, 0), new BlockPos(1, 1, 2),
                new BlockPos(0, 1, 1), new BlockPos(2, 1, 1)), chamber.neighbors(center));
        assertEquals(6, chamber.faceOpenings(center));
        assertEquals(WALL, chamber.neighborKind(new BlockPos(1, 4, 1)));

        chamber.openExteriorFace(1);
        assertEquals(EXTERIOR, chamber.neighborKind(new BlockPos(0, 4, 0)));
        assertEquals(4, chamber.faceOpenings(new BlockPos(0, 3, 0)));
        assertEquals(UNKNOWN, chamber.neighborKind(new BlockPos(30, 30, 30)));
        chamber.unloaded.add(new BlockPos(30, 30, 30));
        assertEquals(UNKNOWN, chamber.neighborKind(new BlockPos(30, 30, 30)), "unloaded space is unknown, not vacuum");
        assertEquals(1, chamber.exterior.size(), "outside is not implicitly flood-filled");
        assertThrows(IllegalArgumentException.class, () -> chamber.openExteriorFace(13));
    }

    @Test
    void equalPressureMixesAndSourceInjectionAreReadyForSolverCases() {
        AtmosphereChamberFixture equalPressure = AtmosphereChamberFixture.twoRooms(4, 4, 3,
                AtmosphereChamberFixture.pureGasAtPressure(GasType.OXYGEN, 101.325, TEMPERATURE),
                AtmosphereChamberFixture.pureGasAtPressure(GasType.NITROGEN, 101.325, TEMPERATURE));
        assertEquals(equalPressure.finiteCells.get(new BlockPos(0, 0, 0)).pressureKpa(1.0),
                equalPressure.finiteCells.get(new BlockPos(5, 0, 0)).pressureKpa(1.0), 1e-10);

        AtmosphereChamberFixture higherPressure = AtmosphereChamberFixture.twoRooms(4, 4, 3,
                AtmosphereChamberFixture.pureGasAtPressure(GasType.OXYGEN, 202.65, TEMPERATURE),
                AtmosphereChamberFixture.pureGasAtPressure(GasType.NITROGEN, 101.325, TEMPERATURE));
        assertTrue(higherPressure.finiteCells.get(new BlockPos(0, 0, 0)).pressureKpa(1.0)
                > higherPressure.finiteCells.get(new BlockPos(5, 0, 0)).pressureKpa(1.0));

        AtmosphereChamberFixture source = AtmosphereChamberFixture.sealedRoom(4, 4, 3,
                Map.of(GasType.OXYGEN, 1.0), TEMPERATURE);
        GasMixture injected = source.inject20MolPerSecond(new BlockPos(0, 0, 0), GasType.NITROGEN, 2.0);
        assertEquals(40.0, injected.moles(GasType.NITROGEN));
        assertFalse(source.finiteCells.get(new BlockPos(1, 0, 0)).moles(GasType.NITROGEN) > 0.0);
    }
}
