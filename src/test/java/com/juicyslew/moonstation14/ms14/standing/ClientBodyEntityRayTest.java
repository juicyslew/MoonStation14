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

    @Test void lightContactChoosesNearestAndHonorsAnyBlockOcclusion() {
        var hit = new net.minecraft.world.phys.BlockHitResult(new Vec3(4, 0, 0), net.minecraft.core.Direction.WEST,
                net.minecraft.core.BlockPos.ZERO, false);
        assertEquals(FIRST, ClientBodyEntityRay.chooseLightContact(FIRST, 2, hit, 4).entity());
        assertEquals(hit, ClientBodyEntityRay.chooseLightContact(SECOND, 5, hit, 4).block());
        assertNull(ClientBodyEntityRay.chooseLightContact(null, Double.POSITIVE_INFINITY, hit, 4).entity());
        assertEquals(SECOND, ClientBodyEntityRay.chooseLightContact(SECOND, 3, null, Double.POSITIVE_INFINITY).entity());
    }

    @Test void verticalRayUsesSameNearestContactOrdering() {
        var hit = new net.minecraft.world.phys.BlockHitResult(new Vec3(0, 3, 0), net.minecraft.core.Direction.DOWN,
                net.minecraft.core.BlockPos.ZERO, false);
        var start = Vec3.ZERO;
        var bounds = new AABB(-1, 1, -1, 1, 2, 1);
        double entityDistance = BodyInteractionRay.hit(bounds, start, new Vec3(0, 8, 0), 8, 3)
                .map(start::distanceTo).orElse(Double.POSITIVE_INFINITY);
        assertEquals(1, entityDistance, 1e-9);
        assertEquals(FIRST, ClientBodyEntityRay.chooseLightContact(FIRST, entityDistance, hit, 3).entity());
    }

    @Test void structuralNominationRejectsMissEvenWhenItsPositionIsStructural() {
        var pos = new net.minecraft.core.BlockPos(2, 0, 0);
        var state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var miss = net.minecraft.world.phys.BlockHitResult.miss(new Vec3(2.5, .5, .5),
                net.minecraft.core.Direction.WEST, pos);
        var states = (java.util.function.Function<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>) p -> state;
        var structural = (java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState>) s -> true;
        assertNull(ClientBodyEntityRay.nominateStructuralContact(miss, START, 4, states, structural));
        var hit = new net.minecraft.world.phys.BlockHitResult(new Vec3(2.5, .5, .5), net.minecraft.core.Direction.WEST, pos, false);
        assertNotNull(ClientBodyEntityRay.nominateStructuralContact(hit, START, 4, states, structural));
        assertNull(ClientBodyEntityRay.nominateStructuralContact(hit, START, 1, states, structural));
        assertNull(ClientBodyEntityRay.nominateStructuralContact(hit, START, 4, states, s -> false));
    }

    @Test void pitchedFanDirectionPreservesVerticalLookAndConservativeEyeReach() {
        var up = ClientBodyEntityRay.pitchedYawDirection(new Vec3(0, 1, 0), 0);
        var down = ClientBodyEntityRay.pitchedYawDirection(new Vec3(0, -1, 0), 1.2);
        assertEquals(1, up.y, 1e-9); assertEquals(0, up.x, 1e-9);
        assertEquals(-1, down.y, 1e-9); assertEquals(0, down.z, 1e-9);
        assertEquals(Vec3.ZERO, ClientBodyEntityRay.pitchedYawDirection(new Vec3(Double.NaN, 0, 1), 0));
        assertEquals(2 + Math.sqrt(3), ClientBodyEntityRay.eyeRayReach(2, new AABB(0, 0, 0, 1, 1, 1)), 1e-9);
    }
}
