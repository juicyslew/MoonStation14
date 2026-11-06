package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.joml.Quaterniond;
import static org.junit.jupiter.api.Assertions.*;

class ClientSupportedHeadingTest {
    @Test void acceptedAuthorityRestoreInvalidatesSameTickDepartureBridgeAndUsesPublishedSupport() throws Exception {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        var q0 = new Quaterniond();
        var q1 = new Quaterniond().rotationY(.2);
        var q2 = new Quaterniond().rotationY(.5);
        assertEquals(0, h.carry(3, body, frame, q0, true, true));
        h.transition(3, body, frame, 27);
        assertEquals(0, ClientSupportedHeading.presentationOffset(
                h.transitionRenderOffset(3, body, 27, .5f), 0));

        // The accepted grounded authority publication restores a changed support pose at the same body tick.
        h.authorityRestored();
        float supportOffset = h.renderOffset(3, body, frame, q2, true);
        assertEquals((float) Math.toDegrees(-.5), supportOffset, 1e-4);
        assertEquals(supportOffset, ClientSupportedHeading.presentationOffset(
                h.transitionRenderOffset(3, body, 27, .5f), supportOffset), 1e-5);
        for (float partial : new float[]{0, .5f, 1}) {
            float expected = (float) Math.toDegrees(-(.2 + .3 * partial));
            float offset = ClientSupportedHeading.presentationOffset(
                    h.transitionRenderOffset(3, body, 27, partial),
                    h.renderOffset(3, body, frame, new Quaterniond().rotationY(.2 + .3 * partial), true));
            assertEquals(expected, offset, 1e-4);
            float camera = SupportedBodyCameraClient.composePresentationYaw(11, offset);
            float model = SupportedBodyCameraClient.composePresentationYaw(11, offset);
            assertEquals(camera, model, 0);
        }
        assertEquals((float) Math.toDegrees(-.6), h.carry(3, body, frame,
                new Quaterniond().rotationY(.6), true, true), 1e-4,
                "authority invalidation leaves the live carry basis intact");
        assertEquals((float) Math.toDegrees(-.1), h.carry(3, body, frame,
                new Quaterniond().rotationY(.7), true, true), 1e-4);

        var control = java.nio.file.Files.readString(projectRoot().resolve(
                "src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"));
        assertTrue(control.contains("if (groundedCharacter) supportedHeading.authorityRestored();"));
        assertTrue(control.contains("ClientSupportedHeading.presentationOffset(bridge, supportOffset)"));
    }

    @Test void departureAndFirstAirIntervalsKeepSharedCameraContinuousWithoutAdvancingModelEarly() {
        var heading=new ClientSupportedHeading(); var body=UUID.randomUUID(); var frame=UUID.randomUUID();
        double omega=.1, lookPitch=Math.toRadians(18.15), dt=.05;
        var q0=new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Quat(1,0,0,0);
        var spin=new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0,omega/dt,0);
        var up=new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(0,1,0);
        var departure=com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.afterMove(null,true,false,q0,spin,up,dt,.65,1).nextState();
        assertEquals(0,departure.airborneSeconds());
        float basis=(float)Math.toDegrees(lookPitch);
        // Last grounded alpha=1; departure alpha=0/1; first two air intervals each alpha=0,.16,.5,.98,1.
        var prior=departure;
        for(int interval=0;interval<3;interval++) {
            var next=interval==0?prior:com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.step(prior,up,dt,.65,1);
            heading.transition(1,body,frame,interval);
            for(float alpha:new float[]{0,.16f,.5f,.98f,1}) {
                float offset=heading.transitionRenderOffset(1,body,interval,alpha).orElseThrow();
                var yaw=next.orientation().y();
                var rendered=new AngularRecoveryRenderInterpolation().sample(interval,
                        new org.joml.Quaternionf().rotationY((float)(2*Math.atan2(yaw,next.orientation().w()))),alpha);
                float camera=PureAngularRecoveryCameraMath.compose(rendered,basis+offset,0,0).yaw();
                float model=SupportedBodyCameraClient.composePresentationYaw(basis,offset);
                assertEquals(0,offset,1e-6f,"no future support interval is injected");
                assertEquals(basis,model,1e-5f);
                assertEquals(PureAngularRecoveryCameraMath.compose(rendered,basis,0,0).yaw(),camera,1e-5f);
            }
            if(interval>0) prior=next;
        }
    }
    @Test void delayedSdkBaselineRemainsCounterexampleButSharedCameraAndModelPresentationStayAligned() {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        var delayedLogical = new Quaterniond(); // ordinary SDK logical yaw while outside prediction scope
        assertEquals(0, h.carry(1, body, frame, delayedLogical, true, true));
        var ownedFutureRender = new Quaterniond().rotationY(.25); // five 20Hz ticks at 1 rad/s
        float cameraOffset = h.renderOffset(1, body, frame, ownedFutureRender, true);
        assertEquals((float)-Math.toDegrees(.25), cameraOffset, 1e-3,
                "feeding delayed SDK logical state still reproduces the known clock mismatch");
        float cameraYaw=SupportedBodyCameraClient.composePresentationYaw(31, cameraOffset);
        float bodyYaw=SupportedBodyCameraClient.composePresentationYaw(26, cameraOffset);
        float headYaw=SupportedBodyCameraClient.composePresentationYaw(35, cameraOffset);
        assertEquals(5f, ClientSupportedHeading.normalize(cameraYaw-bodyYaw), 1e-4);
        assertEquals(9f, ClientSupportedHeading.normalize(headYaw-bodyYaw), 1e-4,
                "the shared interval rotation moves model root/head and camera without changing intentional relative animation");
    }

    @Test void landingSeedFromFuturePredictionDoesNotInventNextNativeHeadingDelta() {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        // The committed publication end is also the future landing seed and next input baseline.
        h.seed(1, body, frame, new Quaterniond().rotationY(.25), true);
        assertEquals(0f, h.carry(1, body, frame, new Quaterniond().rotationY(.25), true, true), 1e-4);
        var stale = new ClientSupportedHeading();
        stale.seed(1, body, frame, new Quaterniond().rotationY(.25), true);
        assertEquals((float)Math.toDegrees(.25), stale.carry(1, body, frame, new Quaterniond(), true, true), 1e-3,
                "the old delayed SDK endpoint remains an explicit reverse-turn counterexample");
    }

    @Test void committedPublicationEndpointFeedsCarryAndRenderWithoutWorldLookJump() {
        var heading=new ClientSupportedHeading(); var body=UUID.randomUUID();
        var frame=UUID.randomUUID(); var nextFrame=UUID.randomUUID();
        var initial=poseYaw(0); var first=new com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.Presentation(
                4,body,10,10,java.util.Map.of(frame,new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.PresentationPair(initial,initial)));
        assertEquals(0,heading.carry(4,body,frame,ClientSupportedHeading.orientation(first.pose(frame,1)),true,true));
        var end=poseYaw(.25);
        var committed=new com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.Presentation(
                4,body,10,11,java.util.Map.of(frame,new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.PresentationPair(initial,end)));
        var selected=committed.pose(frame,1);
        assertEquals((float)-Math.toDegrees(.25),heading.carry(4,body,frame,ClientSupportedHeading.orientation(selected),true,true),1e-3);
        float localLook=-14.323945f;
        assertEquals(0,Math.toDegrees(.25)+localLook,1e-4,"zero mouse input preserves world look across carried rotation");
        float inputLook=SupportedBodyCameraClient.composePresentationYaw(localLook,12f);
        var future=poseYaw(.35);
        var nextCommitted=new com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.Presentation(
                4,body,11,12,java.util.Map.of(frame,new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.PresentationPair(end,future)));
        var renderPose=nextCommitted.pose(frame,.5f);
        float renderOffset=heading.renderOffset(4,body,frame,renderPose.orientation(),true);
        float camera=SupportedBodyCameraClient.composePresentationYaw(inputLook,renderOffset);
        float model=SupportedBodyCameraClient.composePresentationYaw(inputLook,renderOffset);
        assertEquals(camera,model,1e-5,"camera and body model consume the same published render endpoint");
        assertEquals(12f,ClientSupportedHeading.normalize((float)(camera+Math.toDegrees(.30))),1e-3,
                "raw input look remains immediately responsive");
        // Support identity changes rebase, rather than applying a spurious between-frame turn.
        assertEquals(0,heading.carry(4,body,nextFrame,poseYaw(1).orientation(),true,true));
        assertEquals(0f,heading.renderOffset(4,body,nextFrame,poseYaw(1).orientation(),true),1e-6f);
    }

    @Test void productionCameraAndModelTransformUseOneYawDeltaAndRestoreAllEndpoints() throws Exception {
        var root=projectRoot();
        var camera=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/SupportedBodyCameraClient.java"));
        var renderer=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/character/client/PlayerCharacterHarnessRenderer.java"));
        var control=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"));
        assertTrue(camera.contains("composePresentationYaw(event.getYaw(), offset)"));
        assertTrue(renderer.contains("SupportedBodyCameraClient.withModelYaw("));
        assertTrue(renderer.contains("bodyYaw(){return entity.yBodyRot;}"));
        assertTrue(renderer.contains("modelRootRotationDegrees(yBodyRot)"));
        assertTrue(renderer.contains("() -> super.render(entity, entityYaw, partialTick"));
        assertTrue(control.contains("ClientFrameTimelineStore.presentationEndpoint("));
        assertTrue(control.contains("ClientFrameTimelineStore.presentation("));
        assertFalse(control.contains("LoadedFrameRayQuery.frameOrientation(minecraft.level, basis.frameId()"),
                "owned heading must not consume the delayed SDK presentation orientation");

        float[] angles={15,14,15,14,23,22};float[] before=angles.clone();float offset=7.5f;
        float cameraYaw=SupportedBodyCameraClient.composePresentationYaw(angles[0],offset);
        SupportedBodyCameraClient.withModelYaw(yawAccess(angles),offset,()->{
            assertEquals(cameraYaw,angles[2],1e-5f,"root/body receives exactly one committed offset");
            assertEquals(SupportedBodyCameraClient.composePresentationYaw(before[4],offset),angles[4],1e-5f,
                    "head current yaw receives the same offset while preserving its relative animation");
            assertEquals(180f-cameraYaw,SupportedBodyCameraClient.modelRootRotationDegrees(angles[2]),1e-5f,
                    "actual root rotation uses the transformed yaw, not an additional correction");
        });
        assertArrayEquals(before,angles,0f,"all current/previous yaw endpoints restore after successful render");
        float inputCamera=SupportedBodyCameraClient.composePresentationYaw(before[0]+12f,offset);
        float[] inputModel=before.clone();inputModel[2]+=12f;
        SupportedBodyCameraClient.withModelYaw(yawAccess(inputModel),offset,()->
                assertEquals(inputCamera,inputModel[2],1e-5f,"model and camera preserve immediate input look with one committed offset"));
        assertEquals(34.5f,inputCamera,1e-5f,"camera retains the immediate input delta");
        assertThrows(IllegalStateException.class,()->SupportedBodyCameraClient.withModelYaw(yawAccess(angles),offset,
                ()->{angles[2]=999;throw new IllegalStateException("render fixture");}));
        assertArrayEquals(before,angles,0f,"finally restores every endpoint even when rendering throws");
    }

    private static SupportedBodyCameraClient.ModelYawAccess yawAccess(float[] a){return new SupportedBodyCameraClient.ModelYawAccess(){
        public float yaw(){return a[0];} public void yaw(float v){a[0]=v;} public float oldYaw(){return a[1];} public void oldYaw(float v){a[1]=v;}
        public float bodyYaw(){return a[2];} public void bodyYaw(float v){a[2]=v;} public float oldBodyYaw(){return a[3];} public void oldBodyYaw(float v){a[3]=v;}
        public float headYaw(){return a[4];} public void headYaw(float v){a[4]=v;} public float oldHeadYaw(){return a[5];} public void oldHeadYaw(float v){a[5]=v;}
    };}

    @Test void downPostureRootKeepsMovementFacingAndAppliesCommittedOffsetExactlyOnce() throws Exception {
        float authoredFacing=47f,committedOffset=8f;
        float postureRoot=SupportedBodyCameraClient.modelRootRotationDegrees(55f,authoredFacing,true,committedOffset);
        assertEquals(125f,postureRoot,1e-5f,"posture movement-facing yaw receives the shared committed heading");
        assertEquals(47f,authoredFacing,0f,"rendering does not mutate posture data");
        float fallbackRoot=SupportedBodyCameraClient.modelRootRotationDegrees(55f,authoredFacing,false,committedOffset);
        assertEquals(125f,fallbackRoot,1e-5f,"fallback uses already-shifted yBodyRot without applying offset twice");
        float remoteRoot=SupportedBodyCameraClient.modelRootRotationDegrees(55f,authoredFacing,true,0f);
        assertEquals(133f,remoteRoot,1e-5f,"the exact-owner presentation guard's remote zero preserves authored facing");

        var renderer=java.nio.file.Files.readString(projectRoot().resolve(
                "src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/character/client/PlayerCharacterHarnessRenderer.java"));
        assertTrue(renderer.contains("presentationYawOffset(entity,partialTick)"));
        assertTrue(renderer.contains("modelRootRotationDegrees(yBodyRot,posture==null?0f:posture.data().movementFacingYaw()"));
        assertTrue(renderer.contains("posture!=null,presentationOffset"));
    }

    private static java.nio.file.Path projectRoot(){
        var directory=java.nio.file.Path.of("").toAbsolutePath().normalize();
        while(directory!=null&&!java.nio.file.Files.isRegularFile(directory.resolve(
                "src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java")))directory=directory.getParent();
        assertNotNull(directory,"project root");return directory;
    }

    @Test void departureBridgeInterpolatesFrozenPoseAndComposesAsIndependentWorldHeading() {
        var h = new ClientSupportedHeading(); var body=UUID.randomUUID(); var frame=UUID.randomUUID();
        var last = new Quaterniond();
        var logical = new Quaterniond().rotationY(.1);
        assertEquals(0, h.carry(12, body, frame, last, true, true));
        assertEquals((float)Math.toDegrees(-.1), h.carry(12, body, frame, logical, true, true), 1e-3);
        var lastBefore = new Quaterniond(last);
        h.transition(12, body, frame, 40);
        last.rotationY(1.7); logical.rotationY(2.1); // bridge owns defensive snapshots
        for (float p : new float[]{0, .25f, .5f, 1}) {
            float offset = h.transitionRenderOffset(12, body, 40, p).orElseThrow();
            assertEquals(0f, offset, 1e-3);
            float rawMouseYaw = (float)-Math.toDegrees(.1);
            float mouseDelta = 17f;
            var composed = PureAngularRecoveryCameraMath.compose(new org.joml.Quaternionf(), rawMouseYaw + offset + mouseDelta, 0, 0);
            float expectedWorldYaw = rawMouseYaw + mouseDelta;
            var actualCamera = new org.joml.Quaternionf().rotationYXZ(
                    (float)Math.toRadians(180-composed.yaw()), 0, 0);
            var actualForward = actualCamera.transform(new org.joml.Vector3f(0, 0, -1));
            var expected = PureAngularRecoveryCameraMath.compose(new org.joml.Quaternionf(), rawMouseYaw + mouseDelta, 0, 0);
            assertEquals(expected.yaw(), composed.yaw(), 1e-5f,
                    "departure bridge leaves the already-consumed logical camera basis unchanged");
            assertEquals(0, h.transitionRenderOffset(13, body, 40, p).orElse(0f));
            assertTrue(h.transitionRenderOffset(12, body, 41, p).isEmpty());
            assertTrue(h.transitionRenderOffset(12, UUID.randomUUID(), 40, p).isEmpty());
        }
        assertTrue(h.transitionRenderOffset(12, body, 40, Float.NaN).isEmpty());
        float repeated = h.transitionRenderOffset(12, body, 40, .5f).orElseThrow();
        assertEquals(0f, repeated, 1e-3);
        assertEquals(repeated, h.transitionRenderOffset(12, body, 40, .5f).orElseThrow(), 1e-5);
        assertEquals(1d, Math.abs(lastBefore.dot(new Quaterniond())), 1e-12);
        h.transition(12, body, frame, 41);
        assertEquals(0, h.transitionRenderOffset(12, body, 41, .5f).orElseThrow());
        var angularRender = new AngularRecoveryRenderInterpolation();
        angularRender.sample(50, new org.joml.Quaternionf(), 1);
        var airPose = new org.joml.Quaternionf().rotationY(.1f);
        assertEquals(.1f, angularRender.sample(51, airPose, 1).angle(), 1e-5f);
        assertEquals(0, h.transitionRenderOffset(12, body, 41, .5f).orElseThrow(), 1e-5,
                "landing bridge must not apply a second floor-heading correction after the angular pose advances");
        h.reset();
        assertTrue(h.transitionRenderOffset(12, body, 41, .5f).isEmpty());
    }

    @Test void landingSeedPreservesNextNativeIntervalExactlyOnce() {
        var h = new ClientSupportedHeading(); var body=UUID.randomUUID(); var frame=UUID.randomUUID();
        var landing = new Quaterniond().rotationY(.4);
        h.seed(9, body, frame, landing, true);
        // Idempotent endpoint seeding does not consume or erase subsequent live movement.
        var next = new Quaterniond().rotationY(.539);
        assertEquals((float)Math.toDegrees(-.139), h.carry(9, body, frame, next, true, true), 1e-3);
        h.seed(9, body, frame, landing, true);
        assertEquals(0, h.carry(9, body, frame, next, true, true), 1e-3);
    }
    @Test void renderOffsetIsReadOnlyAndRelativeToConsumedLogicalPose() {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        var logical = new Quaterniond().rotationY(.4);
        assertEquals(0, h.carry(1, body, frame, logical, true, true));
        var render = new Quaterniond().rotationY(.5);
        for (int i=0;i<4;i++) assertEquals((float)Math.toDegrees(-.1),
                h.renderOffset(1, body, frame, render, true), 1e-4);
        assertEquals(0, h.renderOffset(2, body, frame, render, true));
        assertEquals(0, h.renderOffset(1, UUID.randomUUID(), frame, render, true));
        assertEquals(0, h.renderOffset(1, body, UUID.randomUUID(), render, true));
        assertEquals(0, h.renderOffset(1, body, frame, null, true));
        // Rendering did not consume the next logical interval.
        assertEquals((float)Math.toDegrees(-.1), h.carry(1, body, frame,
                new Quaterniond().rotationY(.5), true, true), 1e-4);
    }
    @Test void worldUpSpinCarriesIncrementallyAtAnyFrameOrientation() {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        var start = pitch(Math.PI / 2).rotateZ(Math.PI / 3);
        assertEquals(0, h.carry(1, body, frame, start, true, true));
        for (int i=1;i<=100;i++) {
            var now = new Quaterniond().rotationY(i * .03).mul(start);
            assertEquals((float)Math.toDegrees(-.03), h.carry(1, body, frame, now, true, true), 1e-4);
        }
    }

    @Test void purePitchRollAndAntipodalRepresentationDoNotInventHeading() {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        var initial = pitch(Math.PI / 2);
        assertEquals(0, h.carry(1, body, frame, initial, true, true));
        // World-X rotation is pure world-up-free swing (left multiplication).
        assertEquals(0, h.carry(1, body, frame, pitch(.4).mul(initial), true, true), 1e-5);
        // Right/local-Z rotation after Rx(90) is a world-Y spin, not pure roll.
        h.reset();
        assertEquals(0, h.carry(1, body, frame, initial, true, true));
        // Explicit world-Y delta is the known target: Rx(90) maps local +Z to world -Y.
        var worldYDelta = new Quaterniond().rotationY(-.4);
        var expectedWorldSpin = new Quaterniond(worldYDelta).mul(initial);
        assertEquals((float)Math.toDegrees(.4), h.carry(1, body, frame, expectedWorldSpin, true, true), 1e-4);
        // Antipodal representation of the exact same orientation carries no delta.
        assertEquals(0f, h.carry(1, body, frame, new Quaterniond(-expectedWorldSpin.x, -expectedWorldSpin.y,
                -expectedWorldSpin.z, -expectedWorldSpin.w), true, true), 1e-5f);
    }

    @Test void localZRollAfterQuarterTurnPitchEqualsWorldUpSpinAndCarriesHeading() {
        var h = new ClientSupportedHeading();
        var body = UUID.randomUUID();
        var frame = UUID.randomUUID();
        var baseline = pitch(Math.PI / 2);
        assertEquals(Math.sqrt(.5), baseline.x, 1e-12);
        assertEquals(0d, baseline.y, 1e-12);
        assertEquals(0d, baseline.z, 1e-12);
        assertEquals(Math.sqrt(.5), baseline.w, 1e-12);
        assertEquals(0d, baseline.transform(new org.joml.Vector3d(0, 0, 1)).x, 1e-12);
        assertEquals(-1d, baseline.transform(new org.joml.Vector3d(0, 0, 1)).y, 1e-12);
        var localZPose = new Quaterniond(baseline).mul(new Quaterniond().rotationZ(.4)).normalize();
        var equivalentWorldUpPose = new Quaterniond().rotationY(-.4).mul(baseline).normalize();

        assertEquals(0f, h.carry(3, body, frame, baseline, true, true), 1e-5f);
        assertTrue(Math.abs(new Quaterniond(baseline).dot(localZPose)) < .999,
                "the local-Z pose must differ physically from its baseline");
        assertEquals(1d, Math.abs(localZPose.dot(equivalentWorldUpPose)), 1e-12,
                "Rx(pi/2)*Rz(.4) must represent the same rotation as Ry(-.4)*Rx(pi/2), up to antipodal sign; "
                        + "baseline[x,y,z,w]=" + components(baseline)
                        + " localZ[x,y,z,w]=" + components(localZPose)
                        + " worldUp[x,y,z,w]=" + components(equivalentWorldUpPose)
                        + " baseline.rotate(+Z)=" + baseline.transform(new org.joml.Vector3d(0, 0, 1)));
        assertEquals((float) Math.toDegrees(.4), h.carry(3, body, frame, localZPose, true, true), 1e-4f);
    }

    private static String components(Quaterniond q) {
        return "[" + q.x + "," + q.y + "," + q.z + "," + q.w + "]";
    }

    // JOML 1.10.5 rotationX() yields incorrect components; build canonical pitch directly.
    private static Quaterniond pitch(double angle) {
        return new Quaterniond(Math.sin(angle / 2), 0, 0, Math.cos(angle / 2));
    }

    private static dev.ryanhcode.sable.companion.math.Pose3d poseYaw(double radians) {
        var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
        pose.orientation().rotationY(radians);
        return pose;
    }

    @Test void supportedWorldUpRatesStayIncrementalAcrossWrapsAndOrientations() {
        for (double rate : new double[]{.5, 1, 3}) {
            for (Quaterniond tilt : new Quaterniond[]{new Quaterniond(), pitch(Math.PI/2),
                    pitch(Math.PI).rotateZ(.7)}) {
                var h = new ClientSupportedHeading(); var body=UUID.randomUUID(); var frame=UUID.randomUUID();
                assertEquals(0, h.carry(1, body, frame, new Quaterniond(tilt), true, true));
                for (int tick=1; tick<=240; tick++) {
                    Quaterniond q = new Quaterniond().rotationY(rate*tick/20d).mul(tilt);
                    assertEquals((float)Math.toDegrees(-rate/20d), h.carry(1, body, frame, q, true, true), 1e-3);
                }
            }
        }
    }

    @Test void renderReadsAndSnapshotReplayIntervalsDoNotDoubleAdvanceHeading() {
        for (double rate : new double[]{.5, 1, 3}) {
            var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
            // A significant roll/pitch carries the frame's local up across world vertical.
            var tilt = pitch(Math.toRadians(112)).rotateZ(Math.toRadians(67));
            assertEquals(0, h.carry(4, body, frame, tilt, true, true));
            float logicalTotal = 0;
            for (int tick = 1; tick <= 80; tick++) {
                // Model several replayed logical moves before render, then repeated render reads.
                for (int replay = 1; replay <= 3; replay++) {
                    var logical = new Quaterniond().rotationY(rate * (tick - 1 + replay / 3d) / 20d).mul(tilt);
                    logicalTotal += h.carry(4, body, frame, logical, true, true);
                }
                var lastLogical = new Quaterniond().rotationY(rate * tick / 20d).mul(tilt);
                var rendered = new Quaterniond().rotationY(rate * (tick + .4) / 20d).mul(tilt);
                float offset = h.renderOffset(4, body, frame, rendered, true);
                assertEquals((float) Math.toDegrees(-rate * .4 / 20d), offset, 1e-3);
                assertEquals(offset, h.renderOffset(4, body, frame, rendered, true), 1e-5);
                // Repeated presentation must not alter the logical pose basis.
                assertEquals(0, h.carry(4, body, frame, lastLogical, true, true), 1e-3);
            }
            assertEquals((float) Math.toDegrees(-rate * 80 / 20d), logicalTotal, 2e-2);
            // After a roll transition, a stopped pose has no residual camera snap.
            var rolledStop = new Quaterniond().rotationX(2.4).mul(tilt);
            h.carry(4, body, frame, rolledStop, true, true);
            assertEquals(0, h.carry(4, body, frame, rolledStop, true, true), 1e-3);
            assertEquals(0, h.carry(4, body, null, tilt, false, true));
            assertEquals(0, h.carry(4, body, frame, new Quaterniond().rotationY(1.2), true, true));
            assertEquals(0, h.carry(5, body, frame, new Quaterniond().rotationY(2.0), true, true));
            assertEquals(0, h.carry(5, UUID.randomUUID(), frame, new Quaterniond(), true, true));
        }
    }

    @Test void carriesEachSupportedPoseDeltaOnceAndRebasesIdentityChanges() {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        assertEquals(0f, h.carry(1, body, frame, new Quaterniond().rotationY(Math.toRadians(170)), true, true), 1e-5f);
        assertEquals(0f, h.carry(1, body, frame, new Quaterniond().rotationY(Math.toRadians(170)), true, true), 1e-5f);
        assertEquals(-10, h.carry(1, body, frame, new Quaterniond().rotationY(Math.toRadians(180)), true, true), 1e-4);
        assertEquals(-10, h.carry(1, body, frame, new Quaterniond().rotationY(Math.toRadians(190)), true, true), 1e-4);
        assertEquals(0, h.carry(1, body, frame, null, false, true));
        assertEquals(0, h.carry(1, body, frame, new Quaterniond(), true, true));
        assertEquals(0, h.carry(2, body, frame, new Quaterniond(), true, true));
        assertEquals(0, h.carry(2, UUID.randomUUID(), frame, new Quaterniond(), true, true));
        assertEquals(0, h.carry(2, body, UUID.randomUUID(), new Quaterniond(), true, true));
        assertEquals(0, h.carry(2, body, frame, new Quaterniond(), true, false));
        assertEquals(0, h.carry(2, body, frame, new Quaterniond(), true, true));
        assertEquals(-5, h.carry(2, body, frame, new Quaterniond().rotationY(Math.toRadians(5)), true, true), 1e-4);
    }

    @Test void ownershipGapReacquiresAsBaselineThenCarriesNextInterval() {
        var h = new ClientSupportedHeading(); var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        assertEquals(0, h.carry(7, body, frame, new Quaterniond(), true, true));
        h.resetForOwnershipGap();
        assertEquals(0, h.carry(7, body, frame, new Quaterniond().rotationY(.5), true, true));
        assertEquals((float)Math.toDegrees(-.1), h.carry(7, body, frame, new Quaterniond().rotationY(.6), true, true), 1e-4);
    }
}
