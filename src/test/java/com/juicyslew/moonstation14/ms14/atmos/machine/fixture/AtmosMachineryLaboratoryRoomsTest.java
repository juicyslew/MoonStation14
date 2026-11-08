package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineryLaboratoryRoomsTest {
    @Test void roomsAreFiniteNonOverlappingAndKeepLabelOutsideGlass() {
        BlockPos o=BlockPos.ZERO;
        var exchanger=AtmosMachineryLaboratoryRooms.exchanger(o);
        var host=AtmosMachineryFixturePlan.stationPosition(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
        assertEquals(host,exchanger.machine());
        assertTrue(exchanger.occupiedPositions().contains(host));
        assertTrue(exchanger.occupiedPositions().contains(host.north()));
        assertTrue(exchanger.occupiedPositions().contains(host.south()));
        assertEquals(18,exchanger.roomCells().size());
        // The exchanger host is intentionally included as a finite-room candidate;
        // runtime passability filtering excludes its machine/pipe occupied cells.
        assertTrue(exchanger.roomCells().contains(host));
        assertTrue(exchanger.roomCells().contains(host.relative(net.minecraft.core.Direction.EAST)));
        assertEquals(54, AtmosMachineryLaboratoryRooms.miners(o).stream().mapToInt(g -> g.roomCells().size()).sum());
        assertFalse(exchanger.glassPositions().contains(exchanger.labelPosition()));
        assertFalse(exchanger.roomCells().contains(exchanger.labelPosition()));
        assertFalse(exchanger.occupiedPositions().contains(exchanger.labelPosition()));
        var seen=new HashSet<BlockPos>(exchanger.glassPositions());
        var labels=new HashSet<BlockPos>();
        assertTrue(labels.add(exchanger.labelPosition()));
        assertEquals(3,AtmosMachineryLaboratoryRooms.miners(o).size());
        for(var g:AtmosMachineryLaboratoryRooms.miners(o)) {
            assertTrue(labels.add(g.labelPosition()), "duplicate room label position: " + g.labelPosition());
            assertEquals(18,g.roomCells().size());
            assertFalse(g.glassPositions().contains(g.labelPosition()));
            assertFalse(g.roomCells().contains(g.labelPosition()));
            assertFalse(g.occupiedPositions().contains(g.labelPosition()));
            for(var p:g.roomCells()) assertTrue(seen.add(p),"overlapping room cell "+p);
            for(var p:g.glassPositions()) assertTrue(seen.add(p),"overlapping boundary "+p);
        }
    }

    @Test void circuitReadinessRequiresEveryPlannedCellButMinerReadinessUsesPassableCellsOnly() {
        var miner=AtmosMachineryLaboratoryRooms.miners(BlockPos.ZERO).getFirst();
        assertTrue(miner.roomCells().contains(miner.machine()),"the miner's full-cube host is intentionally in planned room geometry");
        var actualPassable=miner.roomCells().stream().filter(pos->!pos.equals(miner.machine())).toList();
        assertTrue(actualPassable.size()<miner.roomCells().size());
        assertTrue(AtmosMachineryLaboratoryRooms.finiteRoomShapeReady(miner,actualPassable,false),
                "auxiliary miner readiness checks actual passable finite endpoints, not a solid host cell");
        assertFalse(AtmosMachineryLaboratoryRooms.finiteRoomShapeReady(miner,actualPassable,true),
                "circuit-room completeness remains strict");
        assertFalse(AtmosMachineryLaboratoryRooms.finiteRoomShapeReady(miner,List.of(),false),
                "a nonempty passable endpoint set is still required");
    }
}
