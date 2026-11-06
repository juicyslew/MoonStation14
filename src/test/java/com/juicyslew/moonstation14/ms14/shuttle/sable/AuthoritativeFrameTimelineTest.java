package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthoritativeFrameTimelineTest {
    @Test void deduplicatesInterpolatesPredictsAndBoundsHistory() {
        var timeline = new AuthoritativeFrameTimeline(); UUID level = UUID.randomUUID(), frame = UUID.randomUUID();
        Pose3d p = new Pose3d();
        assertTrue(timeline.receive(level, frame, 10, p, new Vec3(2, 0, 0), Vec3.ZERO));
        assertFalse(timeline.receive(level, frame, 10, p, Vec3.ZERO, Vec3.ZERO));
        assertTrue(timeline.receive(level, frame, 12, p, new Vec3(2, 0, 0), Vec3.ZERO));
        assertEquals(AuthoritativeFrameTimeline.Kind.INTERPOLATED, timeline.sample(frame, 11).kind());
        var future = timeline.sample(frame, 14);
        assertEquals(AuthoritativeFrameTimeline.Kind.PREDICTED, future.kind());
        assertEquals(.2, future.pose().position().x, 1e-9);
        assertEquals(AuthoritativeFrameTimeline.Kind.MISSING, timeline.sample(frame, 53).kind());
        for (long tick = 13; tick < 60; tick++) timeline.receive(level, frame, tick, p, Vec3.ZERO, Vec3.ZERO);
        assertTrue(timeline.size() <= 42, "40-tick span plus one bracketing endpoint");
        assertEquals(AuthoritativeFrameTimeline.Kind.MISSING, timeline.sample(frame, 12).kind());
    }

    @Test void stopAndLifetimeResetPreventStaleMotion() {
        var timeline = new AuthoritativeFrameTimeline(); UUID level = UUID.randomUUID(), frame = UUID.randomUUID();
        timeline.receive(level, frame, 1, new Pose3d(), new Vec3(5, 0, 0), Vec3.ZERO);
        timeline.stop(level, frame, 1);
        assertEquals(AuthoritativeFrameTimeline.Kind.PREDICTED, timeline.sample(frame, 2).kind());
        assertEquals(0, timeline.sample(frame, 2).linearVelocity().length());
        assertFalse(timeline.receive(level, frame, 0, new Pose3d(), Vec3.ZERO, Vec3.ZERO), "late pre-stop packet cannot revive motion");
        assertTrue(timeline.receive(level, frame, 2, new Pose3d(), Vec3.ZERO, Vec3.ZERO), "later authoritative sample resumes moving timeline");
        assertTrue(timeline.receive(level, frame, 3, new Pose3d(), Vec3.ZERO, Vec3.ZERO));
        timeline.stop(level, frame, 2);
        assertEquals(AuthoritativeFrameTimeline.Kind.PREDICTED, timeline.sample(frame, 4).kind(), "late stop must not override newer authority");
        timeline.bind(UUID.randomUUID(), frame);
        assertEquals(0, timeline.size());
    }

    @Test void predictsWorldAxisRotationWithoutMutatingCapturedPose() {
        var timeline = new AuthoritativeFrameTimeline(); UUID level = UUID.randomUUID(), frame = UUID.randomUUID();
        Pose3d source = new Pose3d();
        timeline.receive(level, frame, 4, source, Vec3.ZERO, new Vec3(0, Math.PI / 2, 0));
        var predicted = timeline.sample(frame, 5).pose();
        var forward = predicted.orientation().transform(new org.joml.Vector3d(1, 0, 0));
        assertEquals(Math.cos(Math.PI / 40), forward.x, 1e-9);
        assertEquals(-Math.sin(Math.PI / 40), forward.z, 1e-9);
        assertEquals(0, timeline.sample(frame, 4).pose().orientation().transform(new org.joml.Vector3d(1, 0, 0)).z, 1e-9);
    }

    @Test void predictionRetainsFullPoseRotationPointForDiagnosticWorldComComparison() {
        var timeline = new AuthoritativeFrameTimeline(); UUID level = UUID.randomUUID(), frame = UUID.randomUUID();
        Pose3d source = new Pose3d();
        source.position().set(10, 20, 30);
        source.rotationPoint().set(4, 5, 6);
        source.orientation().rotationY(.25);
        assertTrue(timeline.receive(level, frame, 100, source, new Vec3(2, 0, 0), new Vec3(0, .5, 0)));
        var predicted = timeline.sample(frame, 114);
        assertEquals(AuthoritativeFrameTimeline.Kind.PREDICTED, predicted.kind());
        assertEquals(11.4, predicted.pose().position().x, 1e-9);
        assertEquals(20, predicted.pose().position().y, 1e-9);
        assertEquals(4, predicted.pose().rotationPoint().x, 1e-9);
        assertEquals(5, predicted.pose().rotationPoint().y, 1e-9);
        assertEquals(6, predicted.pose().rotationPoint().z, 1e-9);
        double dot = Math.abs(predicted.pose().orientation().dot(source.orientation()));
        double angleError = 2 * Math.acos(Math.min(1, dot));
        assertEquals(.35, angleError, 1e-7, "quaternion angular-distance comparison reports the expected horizon rotation against identity authority");
    }

    @Test void sdkStartSeedDistinguishesStationaryAndMovingWithoutInventingVelocity() {
        UUID level = UUID.randomUUID(), stationaryFrame = UUID.randomUUID(), movingFrame = UUID.randomUUID();
        var stationary = new AuthoritativeFrameTimeline();
        Pose3d atRest = new Pose3d();
        assertTrue(stationary.seed(level, stationaryFrame, 8, atRest, new Pose3d(atRest)));
        var held = stationary.sample(stationaryFrame, 1_008);
        assertEquals(AuthoritativeFrameTimeline.Kind.PREDICTED, held.kind());
        assertTrue(held.motionKnown());
        assertEquals(0, held.pose().position().length(), 1e-12);
        assertEquals(2, stationary.size(), "static hold does not synthesize unlimited stored samples");

        var moving = new AuthoritativeFrameTimeline();
        Pose3d before = new Pose3d(), after = new Pose3d(); after.position().x = 3;
        assertTrue(moving.seed(level, movingFrame, 8, before, after));
        assertFalse(moving.sample(movingFrame, 8).motionKnown());
        assertEquals(AuthoritativeFrameTimeline.Kind.MISSING, moving.sample(movingFrame, 9).kind(),
                "moving initial pair has no packet velocity and cannot be extrapolated");
        assertTrue(moving.receive(level, movingFrame, 9, after, new Vec3(4, 0, 0), Vec3.ZERO));
        assertEquals(AuthoritativeFrameTimeline.Kind.PREDICTED, moving.sample(movingFrame, 10).kind());
        assertEquals(3.2, moving.sample(movingFrame, 10).pose().position().x, 1e-9);
        moving.stop(level, movingFrame, 9);
        assertFalse(moving.receive(level, movingFrame, 8, before, Vec3.ZERO, Vec3.ZERO), "reordered old pose cannot revive stopped frame");
        assertEquals(AuthoritativeFrameTimeline.Kind.PREDICTED, moving.sample(movingFrame, 1_009).kind());
    }
}
