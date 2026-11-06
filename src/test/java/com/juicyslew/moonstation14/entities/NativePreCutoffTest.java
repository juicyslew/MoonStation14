package com.juicyslew.moonstation14.entities;

import dev.ryanhcode.sable.companion.math.Pose3d;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativePreCutoffTest {
    @Test void nativeGeometryObservationIsOptInAndReconnectChecksReadyIntervalBaseline() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java")))
            root = root.getParent();
        assertNotNull(root, "project source tree is available");
        String entity = Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java"));
        String mixin = Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/LooseNativePreCutoffMixin.java"));
        String reconnect = Files.readString(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleMovingShuttleReconnectGameTests.java"));
        assertTrue(entity.contains("private boolean observeNativePreCutoff;"), "diagnostic observation defaults false");
        assertTrue(entity.contains("if (!observeNativePreCutoff || !nativeMoveInFlight"), "observer itself respects opt-in");
        assertTrue(entity.contains("observeNativePreCutoff && NativePreCutoff.matches"), "published diagnostic respects opt-in");
        assertTrue(mixin.contains("loose.observesNativePreCutoff()"), "mixin gates snapshot allocations by exact entity flag");
        assertTrue(mixin.contains("return original.call(inherited);"), "native cutoff operation remains exactly once");
        assertTrue(reconnect.contains("readyBodyPosition") && reconnect.contains("readyFramePose"),
                "post-Ready motion baseline is captured at Ready");
        assertTrue(reconnect.contains("readyFramePose.equals(afterFramePose)"),
                "immutable shared pose-pair baseline is compared after Ready");
        assertTrue(reconnect.contains("driveReconnectTicks(helper, joined, body, 8")
                        && reconnect.contains("safeDelay(helper, 1")
                        && reconnect.contains("joined.neutralIntent(body)") && reconnect.contains("joined.drainSnapshots()"),
                "eight bounded scheduled reconnect ticks each send normal intent and drain snapshots");
    }

    @Test void poseAndBoxCopiesDoNotLeakMutationsAndIdentityIsExact() {
        Object info = new Object();
        Pose3d source = new Pose3d();
        source.rotationPoint().set(1, 2, 3);
        var box = new LooseItemEntity.NativeBoxSnapshot(4, 5, 6, 2, 3, 4, .1, .2, .3, .9);
        var observed = new LooseItemEntity.NativePreCutoff(info, UUID.randomUUID(), source, box,
                new net.minecraft.world.phys.Vec3(7, 8, 9));
        source.rotationPoint().set(10, 11, 12);
        assertEquals(1, observed.logicalPose().rotationPoint().x);
        Pose3d exposed = observed.logicalPose();
        exposed.rotationPoint().set(20, 21, 22);
        assertEquals(1, observed.logicalPose().rotationPoint().x);
        assertSame(box, observed.solvedBox());
        assertTrue(LooseItemEntity.NativePreCutoff.matches(observed, info));
        assertFalse(LooseItemEntity.NativePreCutoff.matches(observed, new Object()));
        assertFalse(LooseItemEntity.NativePreCutoff.matches(null, info));
        var trace = java.util.List.of(new com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope.NativeCollisionTrace(
                UUID.randomUUID(), 1, 1, 1, net.minecraft.core.BlockPos.ZERO,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), "stone", "stone",
                0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, null, true));
        var traced = LooseItemEntity.NativePreCutoff.withTraceIfMatches(observed, info, trace, false);
        assertFalse(traced.nativeCorrectionTraceComplete(), "incomplete status is retained");
        assertEquals(trace, traced.nativeCorrectionTrace());
        assertSame(observed, LooseItemEntity.NativePreCutoff.withTraceIfMatches(observed, new Object(), trace, true),
                "mismatched returned info cannot bind");
        assertThrows(UnsupportedOperationException.class, () -> traced.nativeCorrectionTrace().clear());
    }
}
