package com.juicyslew.moonstation14.ms14.power.topology;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PowerTopologyTest {
    @Test void allSixFacesHaveSamePlaneNeighborsOnlyAlongTheirSurface() {
        for (Direction face : Direction.values()) {
            CableFaceNode origin = node(0, 0, 0, face, CableTier.HV);
            for (Direction step : Direction.values()) {
                CableFaceNode adjacent = new CableFaceNode(new BlockPos(step.getStepX(), step.getStepY(), step.getStepZ()),
                        face, CableTier.HV);
                boolean tangent = step.getAxis() != face.getAxis();
                assertEquals(tangent, PowerTopology.cableEdge(origin, adjacent).isPresent(), face + " / " + step);
                assertEquals(PowerTopology.cableEdge(origin, adjacent).isPresent(),
                        PowerTopology.cableEdge(adjacent, origin).isPresent());
            }
        }
    }

    @Test void perpendicularFacesJoinOnlyAtTheirGeometricSharedEdge() {
        int turns = 0;
        for (Direction firstFace : Direction.values()) {
            for (Direction secondFace : Direction.values()) {
                if (firstFace.getAxis() == secondFace.getAxis()) continue;
                BlockPos delta = new BlockPos(firstFace.getStepX() - secondFace.getStepX(),
                        firstFace.getStepY() - secondFace.getStepY(),
                        firstFace.getStepZ() - secondFace.getStepZ());
                CableFaceNode first = new CableFaceNode(BlockPos.ZERO, firstFace, CableTier.MV);
                CableFaceNode second = new CableFaceNode(delta, secondFace, CableTier.MV);
                var edge = PowerTopology.cableEdge(first, second);
                assertTrue(edge.isPresent());
                assertEquals(PowerTopology.EdgeKind.EDGE_TURN, edge.orElseThrow().kind());
                assertTrue(PowerTopology.cableEdge(second, first).isPresent(), "turn must be symmetric");
                turns++;

                CableFaceNode wrongDiagonal = new CableFaceNode(delta.offset(1, 0, 0), secondFace, CableTier.MV);
                assertTrue(PowerTopology.cableEdge(first, wrongDiagonal).isEmpty());
            }
        }
        assertEquals(24, turns);
    }

    @Test void rejectsSameHostVolumeShortcutsDiagonalsAndTierMixing() {
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                if (a != b) assertTrue(PowerTopology.cableEdge(node(0, 0, 0, a, CableTier.HV),
                        node(0, 0, 0, b, CableTier.HV)).isEmpty());
            }
        }
        CableFaceNode east = node(0, 0, 0, Direction.EAST, CableTier.HV);
        assertTrue(PowerTopology.cableEdge(east, node(1, 1, 0, Direction.EAST, CableTier.HV)).isEmpty());
        assertTrue(PowerTopology.cableEdge(east, node(0, 1, 1, Direction.EAST, CableTier.HV)).isEmpty());
        assertTrue(PowerTopology.cableEdge(east, node(0, 1, 0, Direction.EAST, CableTier.MV)).isEmpty());
        assertTrue(PowerTopology.cableEdge(east, null).isEmpty());
    }

    @Test void wallCableHasExplicitVerticalSegmentsForFloorRises() {
        CableFaceNode lower = node(3, 10, 4, Direction.EAST, CableTier.APC);
        CableFaceNode upper = node(3, 11, 4, Direction.EAST, CableTier.APC);
        CableFaceNode twoFloorsUp = node(3, 12, 4, Direction.EAST, CableTier.APC);
        assertEquals(PowerTopology.EdgeKind.COPLANAR,
                PowerTopology.cableEdge(lower, upper).orElseThrow().kind());
        assertTrue(PowerTopology.cableEdge(lower, twoFloorsUp).isEmpty());
    }

    @Test void neighborSetIsDeterministicUniqueAndBounded() {
        CableFaceNode center = node(0, 0, 0, Direction.EAST, CableTier.HV);
        List<CableFaceNode> fixtures = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            fixtures.add(node(direction.getStepX(), direction.getStepY(), direction.getStepZ(), Direction.EAST, CableTier.HV));
        }
        for (Direction face : Direction.values()) {
            for (Direction other : Direction.values()) {
                if (face.getAxis() != other.getAxis()) {
                    fixtures.add(new CableFaceNode(new BlockPos(face.getStepX() - other.getStepX(),
                            face.getStepY() - other.getStepY(), face.getStepZ() - other.getStepZ()), face, CableTier.HV));
                }
            }
        }
        var once = PowerTopology.cableNeighbors(center, fixtures);
        var twice = PowerTopology.cableNeighbors(center, fixtures);
        assertEquals(once, twice);
        assertEquals(once.stream().distinct().count(), once.size());
        assertTrue(once.size() <= PowerTopology.MAX_CABLE_NEIGHBORS);
        assertEquals(List.of(), PowerTopology.cableNeighbors(center, List.of()));
    }

    @Test void devicePortAdjacencyIsTypedAndExactButDoesNotCreateAPlug() {
        DevicePort port = new DevicePort(new BlockPos(5, 7, 9), Direction.NORTH, CableTier.MV);
        CableFaceNode touching = node(5, 7, 8, Direction.SOUTH, CableTier.MV);
        assertTrue(PowerTopology.deviceAdjacent(port, touching));
        assertFalse(PowerTopology.deviceAdjacent(port, node(5, 7, 8, Direction.SOUTH, CableTier.HV)));
        assertFalse(PowerTopology.deviceAdjacent(port, node(5, 7, 8, Direction.NORTH, CableTier.MV)));
        assertFalse(PowerTopology.deviceAdjacent(port, node(5, 7, 7, Direction.SOUTH, CableTier.MV)));
        assertFalse(PowerTopology.deviceAdjacent(null, touching));
    }

    private static CableFaceNode node(int x, int y, int z, Direction face, CableTier tier) {
        return new CableFaceNode(new BlockPos(x, y, z), face, tier);
    }
}
