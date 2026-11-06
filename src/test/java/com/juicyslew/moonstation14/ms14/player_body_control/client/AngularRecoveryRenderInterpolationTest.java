package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AngularRecoveryRenderInterpolationTest {
    @Test void interpolatesConsecutiveTicksAndResetsAfterGap() {
        var h = new AngularRecoveryRenderInterpolation();
        var a = new Quaternionf();
        var b = new Quaternionf().rotationY(1.0f);
        h.sample(10, a, 1);
        var mid = h.sample(11, b, .5f);
        assertEquals(.5f, mid.angle(), .02f);
        var gap = h.sample(14, new Quaternionf().rotationY(2), 0);
        assertEquals(2f, gap.angle(), .02f);
    }

    @Test void repeatedTickSamplingIsStableAcrossPartialTicksAndQuaternionSign() {
        var h = new AngularRecoveryRenderInterpolation();
        var target = new Quaternionf().rotationY(1.0f);
        h.sample(20, new Quaternionf(), 1);
        var atStart = h.sample(21, target, 0);
        var halfway = h.sample(21, target, .5f);
        var atEnd = h.sample(21, new Quaternionf(-target.x, -target.y, -target.z, -target.w), 1);
        assertEquals(0f, atStart.angle(), .02f);
        assertEquals(.5f, halfway.angle(), .02f);
        assertEquals(1f, Math.abs(new Quaternionf(atEnd).normalize().dot(new Quaternionf(target).normalize())), .0001f);
        assertEquals(.5f, h.sample(21, target, .5f).angle(), .02f);
    }

    @Test void oldTickCurrentInputCannotInferTheReplayCommittedStartEndpoint() {
        var h = new AngularRecoveryRenderInterpolation();
        h.sample(10, new Quaternionf().rotationY(.10f), 1);

        // Replay committed the interval .12 -> .13, but render history only knows
        // the previously displayed .10 and the new target .13.
        var sampled = h.sample(11, new Quaternionf().rotationY(.13f), .5f);

        assertEquals(.115f, worldYAngle(sampled), 1e-6f,
                "display-history API remains the explicit counterexample; it lacks committed interval start .12");
    }

    @Test void ownedConsumerUsesFrozenCommittedPairAcrossReplayAndPacketRevision() {
        var frame=java.util.UUID.randomUUID();var body=java.util.UUID.randomUUID();
        var qFrom=new Quaternionf().rotationY(.12f);var qTo=new Quaternionf().rotationY(.13f);
        var angular=com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair.capture(qFrom,qTo);
        var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
        var publication=new com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.Presentation(
                7,body,40,41,java.util.Map.of(frame,new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.PresentationPair(pose,pose)),angular);
        assertNotNull(publication.angularFor(7,body));
        assertNull(publication.angularFor(8,body));
        assertNull(publication.angularFor(7,java.util.UUID.randomUUID()));
        qFrom.rotationY(2);qTo.rotationY(3);
        var sampled=AngularRecoveryPresentation.committedOrientation(publication.angular(),.5f);
        assertEquals(.125f,worldYAngle(sampled),1e-6f);
        var cameraPose=AngularRecoveryPresentation.committedOrientation(publication.angular(),.5f);
        var modelPose=AngularRecoveryPresentation.committedOrientation(publication.angular(),.5f);
        assertEquals(1f,Math.abs(cameraPose.normalize().dot(modelPose.normalize())),1e-6f,
                "camera and model consume the same immutable angular pair at the same partial tick");

        // Mutable frame packet history is independent of the committed body angular pair.
        var timeline=new com.juicyslew.moonstation14.ms14.shuttle.sable.AuthoritativeFrameTimeline();
        var level=java.util.UUID.randomUUID();
        assertTrue(timeline.receive(level,frame,41,pose,new net.minecraft.world.phys.Vec3(20,0,0),net.minecraft.world.phys.Vec3.ZERO));
        assertTrue(timeline.receive(level,frame,42,new dev.ryanhcode.sable.companion.math.Pose3d(),net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO));
        assertEquals(.125f,worldYAngle(AngularRecoveryPresentation.committedOrientation(publication.angular(),.5f)),1e-6f);
        var next=com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair.capture(
                new Quaternionf().rotationY(.12f),new Quaternionf().rotationY(.14f));
        var nextPublication=new com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.Presentation(
                7,body,41,42,java.util.Map.of(frame,new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.PresentationPair(pose,pose)),next);
        assertEquals(.13f,worldYAngle(AngularRecoveryPresentation.committedOrientation(nextPublication.angular(),.5f)),1e-6f);
        assertEquals(0f,worldYAngle(AngularRecoveryPresentation.committedOrientation(
                com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair.identity(),1f)),1e-6f,
                "S,S and absent normal-posture state explicitly interpolate identity");
        var s=.40f;var restored=com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair.capture(
                new Quaternionf().rotationY(s),new Quaternionf().rotationY(s));
        assertEquals(s,worldYAngle(AngularRecoveryPresentation.committedOrientation(restored,.5f)),1e-6f,
                "zero-pending authority restore publishes the same qS endpoint at both ends");
    }

    @Test void cameraAndModelUseTheSameOwnedPresentationConsumer() throws Exception {
        var root=projectRoot();
        var camera=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/SupportedBodyCameraClient.java"));
        var renderer=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/character/client/PlayerCharacterHarnessRenderer.java"));
        var owned=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/AngularRecoveryPresentation.java"));
        var control=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"));
        assertTrue(camera.contains("AngularRecoveryPresentation.orientation(body, partialTick)"));
        assertTrue(renderer.contains("AngularRecoveryPresentation\n                .orientation(entity, partialTick)"));
        assertTrue(owned.contains("publishedAngular(level,epoch,body.getUUID())"));
        assertTrue(owned.contains("committedOrientation(committed,partialTick)"));
        assertTrue(control.contains("angularBefore=angularPose(body);\n            predictCharacterInTemporalScope(body, intent, animate, interval);\n            angularAfter=angularPose(body);"),
                "publication endpoints bracket the exact prediction step");
        assertTrue(control.contains("AngularPair.capture(angular,angular)"),"authority restore publishes S,S");
        assertTrue(control.contains("AngularPair.capture(\n                            angularBefore,angularAfter)"),
                "successful commit publishes the last replay/live interval pair");
    }

    private static float worldYAngle(Quaternionf quaternion) {
        return 2f * (float) Math.atan2(quaternion.y, quaternion.w);
    }
    private static java.nio.file.Path projectRoot(){
        var directory=java.nio.file.Path.of("").toAbsolutePath().normalize();
        while(directory!=null&&!java.nio.file.Files.isRegularFile(directory.resolve(
                "src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java")))directory=directory.getParent();
        assertNotNull(directory,"project root");return directory;
    }
}
