package com.juicyslew.moonstation14.ms14.disposal.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class DisposalOrientationCandidatesTest {
    private static DisposalEntityData.Tube tube(DisposalEntityData.Kind kind, DisposalEntityData.Topology topology) {
        return new DisposalEntityData.Tube(kind, topology, 0, .1, 0, "", Set.of());
    }

    @Test void semanticCandidateCountsAndNeighborPriority() {
        var preferred = DisposalOrientation.identity();
        assertEquals(3, DisposalOrientationCandidates.candidates(tube(DisposalEntityData.Kind.STRAIGHT,
                DisposalEntityData.Topology.STRAIGHT), preferred, Set.of()).size());
        assertEquals(12, DisposalOrientationCandidates.candidates(tube(DisposalEntityData.Kind.BEND,
                DisposalEntityData.Topology.BEND), preferred, Set.of()).size());
        assertEquals(6, DisposalOrientationCandidates.candidates(tube(DisposalEntityData.Kind.TRUNK,
                DisposalEntityData.Topology.TRUNK), preferred, Set.of()).size());
        var straight = tube(DisposalEntityData.Kind.STRAIGHT, DisposalEntityData.Topology.STRAIGHT);
        assertEquals(Set.of(DisposalDirection.EAST, DisposalDirection.WEST),
                DisposalTubeRouting.connections(straight, DisposalOrientationCandidates.candidates(straight,
                        preferred, Set.of(Direction.EAST)).getFirst()));
        assertTrue(DisposalOrientationCandidates.candidates(straight, preferred, Set.of()).contains(preferred));
    }

    @Test void keepsForwardRolesDistinctAndOrderingIgnoresSetIterationOrder() {
        var router = tube(DisposalEntityData.Kind.ROUTER, DisposalEntityData.Topology.THREE_WAY);
        var a = DisposalOrientationCandidates.candidates(router, DisposalOrientation.identity(), Set.of(Direction.NORTH, Direction.WEST));
        var b = DisposalOrientationCandidates.candidates(router, DisposalOrientation.identity(), Set.of(Direction.WEST, Direction.NORTH));
        assertEquals(a, b);
        assertEquals(24, a.size());
    }

    @Test void routerCandidatesPreserveTheActualTaggedDestinationForRotatedTopologies() {
        for (var topology : new DisposalEntityData.Topology[]{DisposalEntityData.Topology.THREE_WAY,
                DisposalEntityData.Topology.THREE_WAY_FLIPPED}) {
            var router = new DisposalEntityData.Tube(DisposalEntityData.Kind.ROUTER, topology, 2, .1, 0, "", Set.of());
            var expected = new HashSet<List<Object>>();
            for (Direction forward : Direction.values()) for (Direction up : Direction.values()) {
                if (forward.getAxis() == up.getAxis()) continue;
                var frame = new DisposalOrientation(forward, up);
                expected.add(routerKey(router, frame));
            }
            var actual = new HashSet<List<Object>>();
            for (var frame : DisposalOrientationCandidates.candidates(router, DisposalOrientation.identity(), Set.of()))
                actual.add(routerKey(router, frame));
            assertEquals(expected, actual);
        }
    }

    private static List<Object> routerKey(DisposalEntityData.Tube tube, DisposalOrientation frame) {
        var ports = new java.util.HashSet<>(DisposalTubeRouting.connections(tube, frame));
        var dirs = tube.topology().directions();
        return List.of(ports,
                DisposalDirection.fromMC(frame.world(dirs.get(0).rotate(tube.rotationQuarters()))),
                DisposalDirection.fromMC(frame.world(dirs.get(1).rotate(tube.rotationQuarters()))));
    }
}
