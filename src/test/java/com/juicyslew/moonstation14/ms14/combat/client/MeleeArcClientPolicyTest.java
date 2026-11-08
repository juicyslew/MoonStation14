package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeArcClientPolicyTest {
    @Test void disarmAtlasSelectsOneFrameAndWidthDoesNotCompoundProfileScale() {
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(0, 1f/3, 0, 1), MeleeDisarmPresentationPolicy.atlasWindow(.1, 3));
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(1f/3, 2f/3, 0, 1), MeleeDisarmPresentationPolicy.atlasWindow(.5, 3));
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(2f/3, 1, 0, 1), MeleeDisarmPresentationPolicy.atlasWindow(.9, 3));
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(0, 1, 0, 1), MeleeDisarmPresentationPolicy.atlasWindow(.5, 1));
        assertEquals(.2f, MeleeDisarmPresentationPolicy.glyphHalfExtent(.1), 1e-6);
        assertEquals(.4f, MeleeDisarmPresentationPolicy.glyphHalfExtent(.2), 1e-6);
        assertEquals(.8f, MeleeDisarmPresentationPolicy.glyphHalfExtent(.4), 1e-6);
    }
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");
    private static final com.juicyslew.moonstation14.ms14.combat.MeleeStrikeProfile STRIKE =
            new com.juicyslew.moonstation14.ms14.combat.MeleeStrikeProfile(.45, .5, .8, .1, 1);
    private static final com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile STRIKE_ARC =
            new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile(ResourceLocation.withDefaultNamespace("test"),
                    com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.Animation.NONE, true, .15, STRIKE);
    private static com.juicyslew.moonstation14.ms14.combat.MeleeArcGeometry.StrikeCapture geometry() {
        return new com.juicyslew.moonstation14.ms14.combat.MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 0, 1), 0, 1, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT,
                60, new Vec3(0, 0, 1), java.util.List.of(Vec3.ZERO));
    }
    private static MeleeArcClient.Arc strikeArc(java.util.UUID body, int entity, long epoch, long seq, long start,
            ResourceLocation dimension, com.juicyslew.moonstation14.ms14.combat.MeleeArcGeometry.StrikeCapture geometry) {
        return new MeleeArcClient.Arc(body, entity, epoch, seq, dimension, Vec3.ZERO, Vec3.ZERO, 0, start,
                STRIKE_ARC, 1, 60, 0, STRIKE_ARC.texture(), null, false, geometry, "test");
    }
    private static MeleeArcClient.Arc fixture(java.util.UUID body, long epoch, long sequence) {
        return new MeleeArcClient.Arc(body, 1, epoch, sequence, ResourceLocation.withDefaultNamespace("overworld"),
                Vec3.ZERO, Vec3.ZERO, 0, 1, null, 0, 0, 0, null, null, false, null, "test");
    }
    @Test void rejectedPredictionCancellationMatchesOnlyBodyEpochAndSequence() {
        var body = java.util.UUID.randomUUID();
        var other = java.util.UUID.randomUUID();
        try {
            MeleeArcClient.appendForTest(fixture(body, 7, 12));
            MeleeArcClient.appendForTest(fixture(body, 7, 13));
            MeleeArcClient.appendForTest(fixture(other, 7, 12));
            MeleeArcClient.appendForTest(fixture(body, 8, 12));
            MeleeArcClient.appendForTest(fixture(body, 0, 12));
            assertEquals(1, MeleeArcClient.cancelRejected(body, 7, 12));
            assertEquals(4, MeleeArcClient.queuedForTest());
            assertEquals(0, MeleeArcClient.cancelRejected(body, 7, 12));
            var geometry = geometry();
            MeleeArcClient.clear();
            MeleeArcClient.appendForTest(strikeArc(body, 1, 7, 12, 1, DIMENSION, geometry));
            assertTrue(MeleeArcClient.strikeFor(body, 1, DIMENSION, 2, 0).isPresent());
            assertEquals(1, MeleeArcClient.cancelRejected(body, 7, 12));
            assertTrue(MeleeArcClient.strikeFor(body, 1, DIMENSION, 2, 0).isEmpty());
        } finally { MeleeArcClient.clear(); }
    }
    @Test void strikeQueryUsesLiveNewestValidEntryAndRenderGuardUsesGeometryIdentity() {
        var body = java.util.UUID.randomUUID();
        var olderGeometry = geometry();
        var newestGeometry = geometry();
        try {
            MeleeArcClient.appendForTest(new MeleeArcClient.Arc(body, 9, 0, 0, DIMENSION, Vec3.ZERO, Vec3.ZERO, 0, 17,
                    STRIKE_ARC, 1, 60, 0, STRIKE_ARC.texture(), null, false, olderGeometry, "observer"));
            MeleeArcClient.appendForTest(strikeArc(body, 9, 4, 12, 18, DIMENSION, newestGeometry));
            MeleeArcClient.appendForTest(strikeArc(java.util.UUID.randomUUID(), 9, 5, 13, 19, DIMENSION, geometry()));
            MeleeArcClient.appendForTest(strikeArc(body, 10, 5, 14, 20, DIMENSION, geometry()));
            MeleeArcClient.appendForTest(strikeArc(body, 9, 5, 15, 21, ResourceLocation.withDefaultNamespace("nether"), geometry()));
            var newest = MeleeArcClient.strikeFor(body, 9, DIMENSION, 23, .0f).orElseThrow();
            assertSame(newestGeometry, newest.geometry());
            assertTrue(com.juicyslew.moonstation14.ms14.combat.MeleeArcTimeline.sample(
                    new com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile(STRIKE_ARC.texture(),
                            com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile.Animation.NONE, true, .15),
                    1, 0, 0.30).expired());
            assertFalse(newest.sample().expired(), "strike remains live after its legacy glyph lifetime");
            assertTrue(MeleeArcClient.matchesStrike(strikeArc(body, 9, 0, 0, 17, DIMENSION, olderGeometry),
                    new MeleeArcClient.StrikeView(body, 0, false, olderGeometry, STRIKE, null, false)));
            assertFalse(MeleeArcClient.matchesStrike(strikeArc(body, 9, 0, 0, 17, DIMENSION, olderGeometry), newest));
            assertTrue(MeleeArcClient.strikeFor(body, 9, DIMENSION, 27, 0).isEmpty());
        } finally { MeleeArcClient.clear(); }
    }
    @Test void rotationUsesHorizontalDirectionAndWeaponRotation() {
        assertEquals((float)Math.PI / 2, MeleeArcClient.rotationRadians(new Vec3(1,0,0), 0, 0), 1e-6);
        assertEquals((float)(3 * Math.PI / 2), MeleeArcClient.rotationRadians(new Vec3(0,0,-1), 90, 0), 1e-6);
    }
    @Test void predictedAndObserverArcsShareWeaponRotationAndFractionalTimeline() {
        var payload = new com.juicyslew.moonstation14.ms14.combat.MeleeArcPayload(2, java.util.UUID.randomUUID(), ResourceLocation.withDefaultNamespace("overworld"), java.util.UUID.randomUUID(),
                ResourceLocation.withDefaultNamespace("test"), 1, 60, 0, 17, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null, false);
        assertEquals(payload.wideAnimationRotation(), 17f); // Local enqueue resolves this same weapon property.
        assertEquals(0.025, MeleeArcClient.timelineElapsed(12, 12, .5f), 1e-9);
        assertTrue(MeleeArcClient.timelineElapsed(12, 12, .75f) > MeleeArcClient.timelineElapsed(12, 12, .25f));
    }
    @Test void onlyDefinitiveCancellationStatusesRejectPredictedPresentation() {
        for (var status : new com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status[]{
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.NO_AUTHORITY,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.WRONG_EPOCH,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.REPLAY,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.RATE_LIMITED,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.STALE_WEAPON,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.REJECTED,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.PRE_ATTEMPT_CANCELLED,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.CONSUMED_CANCELLED})
            assertTrue(MeleeArcClient.rejectedReply(status), status.name());
        for (var status : new com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status[]{
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.MISS,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HANDLED,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_FAILED,
                com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_HANDLED})
            assertFalse(MeleeArcClient.rejectedReply(status), status.name());
    }
    @Test void predictionTogglesFrozenSourceBit() {
        assertTrue(MeleeArcClient.predictedSwingLeft(false));
        assertFalse(MeleeArcClient.predictedSwingLeft(true));
    }
    @org.junit.jupiter.api.Test void observerEligibilityFiltersOwnPacketsAndWrongDimension() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var payload = new com.juicyslew.moonstation14.ms14.combat.MeleeArcPayload(2, java.util.UUID.randomUUID(), dimension, java.util.UUID.randomUUID(),
                ResourceLocation.withDefaultNamespace("test"), 1, 60, 0, 0, net.minecraft.world.phys.Vec3.ZERO,
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, null, false);
        assertTrue(MeleeArcClient.observerPayloadEligible(payload, java.util.UUID.randomUUID(), dimension));
        assertFalse(MeleeArcClient.observerPayloadEligible(payload, payload.initiatingPlayerUUID(), dimension));
        assertFalse(MeleeArcClient.observerPayloadEligible(payload, java.util.UUID.randomUUID(), ResourceLocation.withDefaultNamespace("nether")));
    }
    @Test void arcContextRequiresSameBodyEpochAndDimension() {
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        assertTrue(MeleeArcClient.contextMatches(java.util.UUID.randomUUID(), 2, dimension,
                java.util.UUID.randomUUID(), 2, dimension) == false);
        var body = java.util.UUID.randomUUID();
        assertTrue(MeleeArcClient.contextMatches(body, 2, dimension, body, 2, dimension));
        assertFalse(MeleeArcClient.contextMatches(body, 2, dimension, body, 3, dimension));
        assertFalse(MeleeArcClient.contextMatches(body, 2, dimension, body, 2, ResourceLocation.withDefaultNamespace("nether")));
    }
    @Test void firstPersonFrameTracksCameraDirectionAndKeepsAForwardNearPlaneMargin() {
        var camera = new Vec3(4, 8, -2);
        var look = new Vec3(.4, -.3, .8).normalize();
        var right = new Vec3(1, 0, 0);
        var up = new Vec3(0, 1, 0);
        var rotation = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 0, -1),
                new org.joml.Vector3f((float) look.x, (float) look.y, (float) look.z));
        var frame = MeleeArcClient.firstPersonFrame(camera, look, right, up, rotation);
        assertEquals(1.05, frame.origin().subtract(camera).dot(frame.look()), 1e-6);
        assertEquals(0, frame.right().dot(frame.look()), 1e-6);
        assertEquals(0, frame.up().dot(frame.look()), 1e-6);
        assertEquals(1, frame.right().length(), 1e-6);
        assertEquals(1, frame.up().length(), 1e-6);
        assertEquals(look.x, frame.look().x, 1e-6);
        assertEquals(look.y, frame.look().y, 1e-6);
        assertEquals(look.z, frame.look().z, 1e-6);
        assertTrue(frame.origin().subtract(camera).length() > 1,
                "forward offset keeps the source quad/item out of the camera near-plane region");
        var normal = new org.joml.Vector3f(0, 0, 1).rotate(frame.rotation());
        assertEquals(1, Math.abs(normal.x * (float) look.x + normal.y * (float) look.y + normal.z * (float) look.z), 1e-5,
                "camera-rotated arc quad normal follows view direction at pitched aim");
    }
    @Test void firstPersonProjectionUniformlyScalesCameraRelativePoseAndLeavesObserversUnchanged() {
        var camera = new Vec3(4, 8, -2);
        var canonical = camera.add(.24, -.22, -1.05);
        var projected = MeleeArcClient.renderProjection(canonical, camera, true);
        assertEquals(.1, projected.scale(), 1e-6);
        var canonicalRelative = canonical.subtract(camera);
        var projectedRelative = projected.position().subtract(camera);
        assertEquals(.105, -projectedRelative.z, 1e-6);
        assertEquals(canonicalRelative.length() * projected.scale(), projectedRelative.length(), 1e-6);
        for (double x : new double[]{.2, .7}) for (double z : new double[]{.8, 1.05}) {
            var point = camera.add(x, .3, -z);
            var scaled = MeleeArcClient.renderProjection(point, camera, true).position().subtract(camera);
            assertEquals(x / z, scaled.x / -scaled.z, 1e-6);
            assertEquals(.3 / z, scaled.y / -scaled.z, 1e-6);
        }
        var observer = MeleeArcClient.renderProjection(canonical, camera, false);
        assertEquals(1, observer.scale(), 1e-6);
        assertEquals(canonical, observer.position());
    }
    @Test void controlledBodyIdentitySelectsLocalProjectionInsteadOfDetachedCarrierIdentity() {
        var carrier = java.util.UUID.randomUUID();
        var controlledBody = java.util.UUID.randomUUID();
        assertNotEquals(carrier, controlledBody);
        assertTrue(MeleeArcClient.firstPersonProjection(controlledBody, controlledBody, true, true));
        assertFalse(MeleeArcClient.firstPersonProjection(carrier, controlledBody, false, true));
        assertFalse(MeleeArcClient.firstPersonProjection(controlledBody, controlledBody, true, false));
        assertFalse(MeleeArcClient.firstPersonProjection(java.util.UUID.randomUUID(), controlledBody, true, true),
                "other first-person viewers keep observer world-space presentation");
    }
    @Test void cameraBasisAndArcNormalRemainDefinedAtLevelAndVerticalPitch() {
        for (Vec3 look : new Vec3[]{new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(0, -1, 0)}) {
            var right = new Vec3(1, 0, 0);
            var up = new Vec3(0, 0, -1);
            if (look.y < 0) up = new Vec3(0, 0, 1);
            var rotation = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 0, -1),
                    new org.joml.Vector3f((float) look.x, (float) look.y, (float) look.z));
            var frame = MeleeArcClient.firstPersonFrame(Vec3.ZERO, look, right, up, rotation);
            assertTrue(Double.isFinite(frame.origin().x) && Double.isFinite(frame.origin().y)
                    && Double.isFinite(frame.origin().z));
            var normal = new org.joml.Vector3f(0, 0, 1).rotate(frame.rotation());
            assertEquals(1, Math.abs(normal.x * (float) look.x + normal.y * (float) look.y + normal.z * (float) look.z), 1e-5);
        }
    }
    @Test void copiedWeaponUsesCameraFacingOnlyWhenFirstPersonFrameExists() {
        assertFalse(MeleeArcClient.cameraFacingWeapon(null));
        var frame = MeleeArcClient.firstPersonFrame(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(1, 0, 0),
                new Vec3(0, 0, -1), new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 0, -1), new org.joml.Vector3f(0, 1, 0)));
        assertTrue(MeleeArcClient.cameraFacingWeapon(frame));
    }
    @Test void worldBillboardPlaneFacesCameraAtLevelAndVerticalPitch() {
        for (Vec3 look : new Vec3[]{new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(0, -1, 0)}) {
            var cameraRotation = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 0, -1),
                    new org.joml.Vector3f((float) look.x, (float) look.y, (float) look.z));
            var pose = MeleeArcClient.billboardRotation(cameraRotation, new Vec3(1, 0, 0), 0, 0);
            var normal = new org.joml.Vector3f(0, 0, 1).rotate(pose);
            assertEquals(1, Math.abs(normal.dot(new org.joml.Vector3f((float) look.x, (float) look.y, (float) look.z))), 1e-5);
            var rotated = MeleeArcClient.billboardRotation(cameraRotation, new Vec3(0, 0, 1), 25, 15);
            var rotatedNormal = new org.joml.Vector3f(0, 0, 1).rotate(rotated);
            assertEquals(1, Math.abs(rotatedNormal.dot(new org.joml.Vector3f((float) look.x, (float) look.y, (float) look.z))), 1e-5,
                    "timeline spin remains in the camera plane");
        }
        assertEquals(1, MeleeArcClient.renderProjection(new Vec3(2, 3, 4), Vec3.ZERO, false).scale());
        assertEquals(.1, MeleeArcClient.renderProjection(new Vec3(2, 3, 4), Vec3.ZERO, true).scale(), 1e-7);
    }
}
