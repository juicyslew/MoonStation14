package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SupportedFloorSideAdmissionTest {
    private static final UUID FRAME = UUID.randomUUID();

    @Test void acceptsEitherAttestedFirstPlaneWhenSameFrameHasNativeUpwardFloorWithoutOptionalGeometry() {
        var floor = trace(true, 0, 1, 0, false);
        assertNull(floor.geometry(),"ordinary floor support must not depend on diagnostic SAT snapshots");
        var side = trace(false, 1, 0, 0, true);
        assertTrue(admit(side, true, List.of(side, floor)));
        assertTrue(admit(floor, false, List.of(floor, side)));

        Object collisionInfo = new Object();
        var candidate = new SableOwnedBodyMotion.TransientContactCandidate(
                new NativeSideContactProof.Result(com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO,
                        com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO, null), null, collisionInfo,
                net.minecraft.world.phys.Vec3.ZERO, new net.minecraft.world.phys.AABB(0,0,0,1,1,1), null,
                NativeFinalFootClearance.Result.CLEAR);
        assertTrue(SableOwnedBodyMotion.transientCandidateOwnsSupportedFloorSide(candidate, collisionInfo),
                "the fully attested floor+side shape routes a matching clear transient candidate first");
    }

    @Test void rejectsWithoutValidSameFrameGeometricFloorOrFirstContactAttestation() {
        var side = trace(false, 1, 0, 0, true);
        var floor = trace(true, 0, 1, 0, true);
        var ceiling = trace(true, 0, -1, 0, true);
        assertFalse(admit(side, true, List.of(side)));
        assertFalse(admit(side, true, List.of(side, ceiling)));
        assertFalse(admit(side, true, List.of(side, trace(true, 0, 1, 0, false)),0,0,0),
                "same-frame row with no upward frame normal is rejected");
        assertFalse(admit(side, true, List.of(side, traceFor(UUID.randomUUID(), true, 0, 1, 0, true))));
        assertFalse(admit(side, true, List.of(trace(false, 0, 0, 0, true), side, floor)));
        assertFalse(admit(side, true, List.of(side, floor), 0, 0, 0));
        assertFalse(admit(side, true, List.of(side, floor), Double.NaN, 1, 0));
        assertFalse(admit(side, true, List.of(side, floor), 0, 2, 0));
        assertFalse(admit(side, false, List.of(side, floor)));
        assertFalse(admit(side, true, List.of(side, floor), false));
    }

    private static boolean admit(CurrentFrameCollisionScope.NativeCollisionTrace first, boolean horizontal,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        return admit(first, horizontal, rows, 0, 1, 0);
    }
    private static boolean admit(CurrentFrameCollisionScope.NativeCollisionTrace first, boolean horizontal,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, double ux, double uy, double uz) {
        return admit(first, horizontal, rows, ux, uy, uz, true);
    }
    private static boolean admit(CurrentFrameCollisionScope.NativeCollisionTrace first, boolean horizontal,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, boolean validNormal) {
        return admit(first, horizontal, rows, 0, 1, 0, validNormal);
    }
    private static boolean admit(CurrentFrameCollisionScope.NativeCollisionTrace first, boolean horizontal,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, double ux, double uy, double uz,
            boolean validNormal) {
        var normal = horizontal ? new org.joml.Vector3d(first.selectedX(), first.selectedY(), first.selectedZ()).normalize()
                : new org.joml.Vector3d(0, 1, 0);
        if (!validNormal) normal.set(0, 0, 1);
        var contact = new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                new org.joml.Vector3d(), normal, horizontal, !horizontal,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        return SableOwnedBodyMotion.supportedFloorSideContactMatches(FRAME, contact, rows, ux, uy, uz);
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace trace(boolean vertical, double x, double y,
            double z, boolean geometry) { return traceFor(FRAME, vertical, x, y, z, geometry); }
    private static CurrentFrameCollisionScope.NativeCollisionTrace traceFor(UUID frame, boolean vertical,
            double x, double y, double z, boolean hasGeometry) {
        var geometric = hasGeometry ? OwnerTraceFixture.load("owner-seq15182.json")
                .getAsJsonArray("rows") : null;
        // Use a real captured geometry record, while keeping the row's tested MTV/frame explicit.
        var source = hasGeometry ? OwnerTraceFixture.rows(geometric, FRAME).getFirst() : null;
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 0, 1, 0,
                vertical ? net.minecraft.core.BlockPos.ZERO : null,
                vertical ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : null, null,
                source == null ? null : source.blockState(), x, y, z,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                source == null ? null : source.geometry(), vertical);
    }
}
