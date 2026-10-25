package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientBodyEntityRayTest {
    private static final Vec3 START = Vec3.ZERO;
    private static final Vec3 END = new Vec3(10, 0, 0);
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static ClientBodyEntityRay.Candidate candidate(UUID id, double near, double far) {
        return new ClientBodyEntityRay.Candidate(id, new AABB(near, -1, -1, far, 1, 1));
    }

    private static UUID select(List<ClientBodyEntityRay.Candidate> candidates, double reach, double blocker) {
        return ClientBodyEntityRay.selectCandidate(candidates, START, END, reach, blocker);
    }

    @Test void choosesNearestAndBreaksExactDistanceTiesByUuid() {
        assertEquals(SECOND, select(List.of(candidate(FIRST, 4, 5), candidate(SECOND, 2, 3)), 8, 9));
        assertEquals(FIRST, select(List.of(candidate(SECOND, 2, 3), candidate(FIRST, 2, 3)), 8, 9));
    }

    @Test void blockerOccludesCandidateAndCandidateBehindWallDoesNotWin() {
        assertNull(select(List.of(candidate(FIRST, 4, 5)), 8, 4));
        assertEquals(FIRST, select(List.of(candidate(FIRST, 2, 3), candidate(SECOND, 6, 7)), 8, 4));
    }

    @Test void originInsideBoundsUsesOriginAndExactReachBoundaryIsExcluded() {
        assertEquals(FIRST, select(List.of(candidate(FIRST, -1, 2)), 3, 4));
        assertNull(select(List.of(candidate(FIRST, 3, 4)), 3, 4));
    }

    @Test void noCandidatesMeansNoHit() {
        assertNull(select(List.of(), 8, 9));
    }
}
