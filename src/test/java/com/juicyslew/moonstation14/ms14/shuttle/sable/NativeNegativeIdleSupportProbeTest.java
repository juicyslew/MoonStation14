package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeNegativeIdleSupportProbeTest {
    private static final UUID FRAME = UUID.randomUUID();
    private static final MovementVector REQUEST = new MovementVector(0, -.001, 0);

    @Test void tiltedNativeSatProbeAcceptsNegativeGeometryOnlyDomainAndRejectsInvalidEvidence() {
        double angle = Math.toRadians(42.5), c = Math.cos(angle), s = Math.sin(angle);
        var pose = new NativeFrameInterval.PoseValue(new double[]{c, s, 0, 0, -s, c, 0, 0,
                0, 0, 1, 0, 0, 0, 0, 1});
        var normal = new MovementVector(-s, c, 0);
        double h = .1 * (Math.abs(normal.x()) + Math.abs(normal.y()));
        var localFloorCenter = new MovementVector(.5, 1, .5);
        var worldFloorCenter = new MovementVector(c * .5 - s, s * .5 + c, .5);
        var before = REQUEST;
        var bodyCenter = new MovementVector(worldFloorCenter.x() + normal.x() * h,
                worldFloorCenter.y() + normal.y() * h + REQUEST.y(), worldFloorCenter.z());
        var template = new NativeCollisionGeometry(bodyCenter, new MovementVector(.2, .2, .2),
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1), pose,
                new AABB(0, 0, 0, 1, 1, 1), MovementVector.ZERO, before, 0, 1);
        var selected = new NativeSideCollisionWitness().separation(template);
        assertNotNull(selected, "synthetic floor geometry must have an actual SAT witness");
        assertTrue(selected.y() > 0);
        assertEquals(c / Math.sqrt(c*c+s*s),
                selected.y() / Math.sqrt(selected.x()*selected.x()+selected.y()*selected.y()+selected.z()*selected.z()), 1e-6);
        var geometry = new NativeCollisionGeometry(bodyCenter, new MovementVector(.2, .2, .2),
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1), pose,
                new AABB(0, 0, 0, 1, 1, 1), selected, before, 0, 1);
        var nativeMotion = REQUEST.add(selected);
        assertTrue(nativeMotion.y() < 0, "the oblique response leaves a negative native Y tangent");
        var row = new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, 1, 1, 0, BlockPos.ZERO,
                Blocks.STONE.defaultBlockState(), "stone", "stone", selected.x(), selected.y(), selected.z(),
                before.x(), before.y(), before.z(), selected.x(), selected.y(), selected.z(),
                selected.x(), selected.y(), selected.z(), geometry, false);
        var known = (java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace>)
                candidate -> candidate.selectedState() == Blocks.STONE.defaultBlockState();

        var result = NativeSideContactProof.validateNegativeIdleSupportProbe(REQUEST, nativeMotion,
                nativeMotion, FRAME, List.of(row), known).orElseThrow();
        assertEquals(MovementVector.ZERO, result.boundedOwn());
        assertEquals(nativeMotion, result.geometryCorrection());
        assertNull(result.horizontalContact());
        assertTrue(NativeSideContactProof.validateIdleSupportProbe(REQUEST, nativeMotion,
                nativeMotion, FRAME, List.of(row), known).isEmpty(), "positive-domain validator rejects negative Y");

        var alteredResponse = new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, 1, 1, 0,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone", selected.x(), selected.y(), selected.z(),
                before.x(), before.y(), before.z(), selected.x(), selected.y(), selected.z(),
                selected.x(), selected.y() + .001, selected.z(), geometry, false);
        assertTrue(NativeSideContactProof.validateNegativeIdleSupportProbe(REQUEST, nativeMotion,
                nativeMotion, FRAME, List.of(alteredResponse), known).isEmpty(), "altered response rejected");
        assertTrue(NativeSideContactProof.validateNegativeIdleSupportProbe(REQUEST, nativeMotion,
                nativeMotion, UUID.randomUUID(), List.of(row), known).isEmpty(), "wrong frame rejected");
        assertTrue(NativeSideContactProof.validateNegativeIdleSupportProbe(REQUEST, nativeMotion,
                nativeMotion, FRAME, List.of(row), ignored -> false).isEmpty(), "unknown state rejected");
        var ceiling = new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, 1, 1, 0, BlockPos.ZERO,
                Blocks.STONE.defaultBlockState(), "stone", "stone", 0, -1, 0, before.x(), before.y(), before.z(),
                0, -1, 0, 0, -1, 0, geometry, true);
        assertTrue(NativeSideContactProof.validateNegativeIdleSupportProbe(REQUEST,
                new MovementVector(0, -1.001, 0), new MovementVector(0, -1.001, 0), FRAME,
                List.of(ceiling), known).isEmpty(), "ceiling/incorrect geometry rejected");
    }
}
