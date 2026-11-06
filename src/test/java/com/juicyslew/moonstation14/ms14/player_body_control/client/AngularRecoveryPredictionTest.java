package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import static org.junit.jupiter.api.Assertions.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class AngularRecoveryPredictionTest {
    @Test void delayedAckClosesHistoryGapWithoutDestroyingRetainedAngularFacts() {
        var frame = UUID.randomUUID();
        var history = new GhostPredictionHistory(1, 7, MobHarnessKind.CHARACTER);
        for (long seq = 1; seq <= 9; seq++) history.record(intent(seq));
        history.onAuthorityBarrier(); // one legitimate cut; subsequent inputs are new live captures
        var expectedIntents = new java.util.ArrayList<GhostControlPayloads.Intent>();
        var expectedFacts = new java.util.HashMap<Long, GhostPredictionHistory.AngularSupportEvidence>();
        int secondaryBarriers = 0;
        long tick = 0;
        for (long seq = 10; seq <= 17; seq++) {
            var live = intent(seq);
            expectedIntents.add(live);
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(live));
            boolean supported = seq >= 13;
            var fact = new GhostPredictionHistory.AngularSupportEvidence(supported ? frame : null, supported,
                    supported ? new AngularRecoveryModel.Vec3(0, .5, 0) : zero());
            expectedFacts.put(seq, fact);
            assertTrue(history.recordAngularEvidence(seq, fact));
            var result = history.reconcile(characterSnapshot(1, 7, ++tick, seq - 8));
            if (seq < 17) {
                assertEquals(GhostPredictionHistory.SnapshotStatus.HISTORY_LOST, result.status());
                assertTrue(result.replayFrames().isEmpty());
            } else {
                assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
                assertEquals(expectedIntents, result.replayFrames());
                for (var replayIntent : result.replayFrames()) {
                    var evidence = history.angularEvidence(replayIntent.sequence());
                    assertEquals(expectedFacts.get(replayIntent.sequence()), evidence.orElseThrow());
                    var selected = AngularRecoveryPrediction.supportForReplay(evidence,
                            fact.supported() ? frame : null);
                    if (!selected.usable()) {
                        secondaryBarriers++;
                        history.onAuthorityBarrier();
                    }
                    assertTrue(selected.usable(), "retained replay evidence must remain usable");
                }
            }
        }
        assertEquals(0, secondaryBarriers);

        // Continue live key changes while the ACK trails by eight; every stable window replays once in order.
        for (long seq = 18; seq <= 25; seq++) {
            var live = intent(seq);
            assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(live));
            var fact = new GhostPredictionHistory.AngularSupportEvidence(frame, true, new AngularRecoveryModel.Vec3(0, .5, 0));
            assertTrue(history.recordAngularEvidence(seq, fact));
            var result = history.reconcile(characterSnapshot(1, 7, ++tick, seq - 8));
            assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, result.status());
            assertEquals(8, result.replayFrames().size());
            assertEquals(seq, result.replayFrames().get(7).sequence());
            assertEquals(live, result.replayFrames().get(7));
            assertTrue(AngularRecoveryPrediction.supportForReplay(history.angularEvidence(seq), frame).usable());
        }
        assertFalse(AngularRecoveryPrediction.supportForReplay(Optional.of(
                new GhostPredictionHistory.AngularSupportEvidence(UUID.randomUUID(), true,
                        new AngularRecoveryModel.Vec3(0, .5, 0))), frame).usable());
        history.onAuthorityBarrier();
        assertTrue(history.angularEvidence(25).isEmpty());
    }

    private static GhostControlPayloads.Intent intent(long sequence) {
        short forward = (short) (sequence % 2 == 0 ? 125 : -125);
        short turn = (short) (sequence % 3 == 0 ? 40 : -20);
        int buttons = sequence % 3 == 0 ? GhostControlPayloads.BUTTON_JUMP : sequence % 3 == 1 ? 0 : GhostControlPayloads.BUTTON_JUMP;
        return new GhostControlPayloads.Intent(1, sequence, forward, (short) 25, (byte) 1, buttons,
                turn, (float) (sequence * 3));
    }

    private static GhostControlPayloads.Snapshot characterSnapshot(long epoch, int body, long tick, long ack) {
        return new GhostControlPayloads.Snapshot(epoch, body, MobHarnessKind.CHARACTER, tick, ack,
                0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, false);
    }

    @Test void captureJumpReplayPreservesOriginalOmegaAcrossPreJumpSnapshots() {
        var frame=UUID.randomUUID(); var history=new GhostPredictionHistory(1,7,MobHarnessKind.CHARACTER);
        for(long seq=8512;seq<=8519;seq++) history.record(new GhostControlPayloads.Intent(1,seq,(short)0,(short)0,(byte)0,0,0,0));
        var omega=new AngularRecoveryModel.Vec3(0,-1.917993,0);
        assertTrue(history.recordAngularEvidence(8519,new GhostPredictionHistory.AngularSupportEvidence(frame,true,omega)));
        var replay=AngularRecoveryPrediction.supportForReplay(history.angularEvidence(8519),frame);
        assertTrue(replay.usable()); assertEquals(omega,replay.omega());
        var prediction=new AngularRecoveryPrediction(); var body=UUID.randomUUID();
        var q=AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0,1,0),0);
        var state=AngularRecoveryModel.departure(q,omega);
        for(int i=0;i<8;i++) state=prediction.afterMove(1,body,false,false,state,replay.omega(),new AngularRecoveryModel.Vec3(0,1,0),.05,.65,1);
        var expected=new Quaterniond().rotationY(-1.917993*.05*8).transform(new org.joml.Vector3d(0,0,1));
        var actual=state.orientation().rotate(new AngularRecoveryModel.Vec3(0,0,1));
        assertEquals(expected.x,actual.x(),1e-9); assertEquals(expected.z,actual.z(),1e-9);
    }

    @Test void authoritativeDepartureAckReplacesCapturedPrediction() {
        var frame=UUID.randomUUID(); var history=new GhostPredictionHistory(1,7,MobHarnessKind.CHARACTER);
        var intent=new GhostControlPayloads.Intent(1,8519,(short)0,(short)0,(byte)0,0,0,0); history.record(intent);
        history.recordAngularEvidence(8519,new GhostPredictionHistory.AngularSupportEvidence(frame,true,new AngularRecoveryModel.Vec3(0,-1.917993,0)));
        assertTrue(history.angularEvidence(8519).isPresent());
        var snapshot=new GhostControlPayloads.Snapshot(1,7,MobHarnessKind.CHARACTER,1,8519,0,0,0,0,0,0,0,0,false,1,1,false,0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED,Optional.empty(),Optional.empty(),0,Optional.empty());
        assertTrue(history.reconcile(snapshot).replayFrames().isEmpty());
        assertTrue(history.angularEvidence(8519).isEmpty());
        var p=new AngularRecoveryPrediction(); var body=UUID.randomUUID();
        var authoritative=AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0,1,0),0),new AngularRecoveryModel.Vec3(0,-1.915322,0));
        var advanced=p.afterMove(1,body,false,false,authoritative,zero(),new AngularRecoveryModel.Vec3(0,1,0),.05,.65,1);
        var expected=new Quaterniond().rotationY(-1.915322*.05).transform(new org.joml.Vector3d(0,0,1));
        var actual=advanced.orientation().rotate(new AngularRecoveryModel.Vec3(0,0,1));
        assertEquals(expected.x,actual.x(),1e-9); assertEquals(expected.z,actual.z(),1e-9);
    }

    @Test void replaySelectorDistinguishesMissingMismatchWorldZeroAndDoesNotResetLiveCache() {
        var frame=UUID.randomUUID(); var other=UUID.randomUUID();
        var zeroFact=new GhostPredictionHistory.AngularSupportEvidence(null,false,zero());
        assertTrue(AngularRecoveryPrediction.supportForReplay(Optional.of(zeroFact),null).usable());
        assertFalse(AngularRecoveryPrediction.supportForReplay(Optional.empty(),frame).usable());
        assertFalse(AngularRecoveryPrediction.supportForReplay(Optional.of(new GhostPredictionHistory.AngularSupportEvidence(other,true,new AngularRecoveryModel.Vec3(0,2,0))),frame).usable());
        var p=new AngularRecoveryPrediction(); var body=UUID.randomUUID(); var poses=pair(1);
        var omega=p.sample(1,body,frame,true,poses,yaw(0),yaw(.1),.05);
        assertTrue(AngularRecoveryPrediction.supportForReplay(Optional.of(new GhostPredictionHistory.AngularSupportEvidence(frame,true,omega)),frame).usable());
        assertEquals(omega,p.sample(1,body,frame,true,poses,yaw(0),yaw(.9),.05));
    }

    @Test void replayedGroundedDepartureUsesExactPendingAngularFactInsteadOfStaleNativeCache() {
        var frame = UUID.randomUUID(); var other = UUID.randomUUID(); var body = UUID.randomUUID();
        var history = new GhostPredictionHistory(1, 7, MobHarnessKind.CHARACTER);
        var jump = new GhostControlPayloads.Intent(1, 7891, (short) 0, (short) 0, (byte) 0, 0, 0, 0);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(jump));
        var omega = new AngularRecoveryModel.Vec3(0, 2, 0);
        assertTrue(history.recordAngularEvidence(7891,
                new GhostPredictionHistory.AngularSupportEvidence(frame, true, omega)));
        // A rewind restores grounded frame A, while the mutable native collision cache reflects AIR.
        var selected = AngularRecoveryPrediction.omegaForReplayDeparture(history.angularEvidence(7891), frame,
                true, zero());
        assertEquals(omega, selected);
        var prediction = new AngularRecoveryPrediction();
        var departure = prediction.afterMove(1, body, true, false, null, selected,
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        var firstAir = prediction.afterMove(1, body, false, false, departure, zero(),
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        assertEquals(0, departure.airborneSeconds(), 0);
        assertEquals(.05, firstAir.airborneSeconds(), 1e-12);
        var expected = new Quaterniond().rotationY(.1);
        var actual = new Quaterniond(firstAir.orientation().x(), firstAir.orientation().y(),
                firstAir.orientation().z(), firstAir.orientation().w());
        assertEquals(1, Math.abs(expected.dot(actual)), 1e-12);

        assertEquals(zero(), AngularRecoveryPrediction.omegaForReplayDeparture(history.angularEvidence(7891), other,
                true, zero()), "frame-A evidence must not seed authority frame B");
        assertEquals(zero(), AngularRecoveryPrediction.omegaForReplayDeparture(Optional.empty(), frame,
                true, zero()), "missing/retired evidence fails closed");
        var airContinuation = AngularRecoveryPrediction.omegaForReplayDeparture(Optional.empty(), other,
                false, omega);
        assertEquals(omega, airContinuation, "an already-airborne state retains its own angular velocity");
        var noAdvance = prediction.afterMove(1, body, true, true, null, zero(),
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        assertNull(noAdvance, "grounded state has no airborne angular recovery state");
    }

    @Test void ackReplayKeepsActualPublishedAngularPresentationContinuous() {
        var frame = UUID.fromString("c7ae448e-f64a-4962-88ec-4f315b7d6a20"); var body = UUID.randomUUID();
        var history = new GhostPredictionHistory(1, 7, MobHarnessKind.CHARACTER);
        var launch = new GhostControlPayloads.Intent(1, 1, (short) 0, (short) 0, (byte) 0, 0, 0, 0);
        var air = new GhostControlPayloads.Intent(1, 2, (short) 0, (short) 0, (byte) 0, 0, 0, 0);
        history.record(launch); history.record(air);
        var omega = new AngularRecoveryModel.Vec3(0, 2, 0);
        history.recordAngularEvidence(1, new GhostPredictionHistory.AngularSupportEvidence(frame, true, omega));
        history.recordAngularEvidence(2, new GhostPredictionHistory.AngularSupportEvidence(null, false, zero()));
        var ground = angularSnapshot(1, 0, true);
        var firstReplay = history.reconcile(ground);
        assertEquals(List.of(launch, air), firstReplay.replayFrames());
        assertTrue(CharacterMovementSnapshotPolicy.allowsReplay(ground.movementMode(), firstReplay.status(), false));

        var model = new AngularRecoveryPrediction();
        var q0 = AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0, 1, 0), .35);
        var departureOmega = AngularRecoveryPrediction.omegaForReplayDeparture(history.angularEvidence(1), frame,
                true, zero());
        assertTrue(CharacterMovementSnapshotPolicy.allowsReplay(ground.movementMode(), firstReplay.status(), false));
        var prior = AngularRecoveryModel.departure(q0, zero());
        var departure = model.afterMove(1, body, true, false, prior, departureOmega,
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        assertEquals(0, departure.airborneSeconds(), 0, "departure seeds at time zero");
        var firstAir = model.afterMove(1, body, false, false, departure,
                AngularRecoveryPrediction.omegaForReplayDeparture(history.angularEvidence(2), frame, false, zero()),
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        assertEquals(.05, firstAir.airborneSeconds(), 1e-12);
        var qPrior = new org.joml.Quaternionf((float) departure.orientation().x(), (float) departure.orientation().y(),
                (float) departure.orientation().z(), (float) departure.orientation().w());
        var qAfter = new org.joml.Quaternionf((float) firstAir.orientation().x(), (float) firstAir.orientation().y(),
                (float) firstAir.orientation().z(), (float) firstAir.orientation().w());
        var beforeAck = angularPresentation(body, qPrior, qAfter);
        var ack = history.reconcile(angularSnapshot(2, 1, false));
        assertEquals(List.of(air), ack.replayFrames());
        assertTrue(CharacterMovementSnapshotPolicy.allowsReplay(angularSnapshot(2, 1, false).movementMode(), ack.status(), false));
        assertTrue(history.angularEvidence(1).isEmpty(), "ACK retires launch fact");
        assertTrue(history.angularEvidence(2).isPresent(), "pending suffix fact survives ACK");
        // G6 authority is the accepted launch endpoint: restore qPrior, then replay the still-pending AIR input.
        var restored = AngularRecoveryModel.departure(departure.orientation(), departure.worldAngularVelocity());
        var replayAir = model.afterMove(1, body, false, false, restored, zero(),
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        var after = new org.joml.Quaternionf((float) replayAir.orientation().x(), (float) replayAir.orientation().y(),
                (float) replayAir.orientation().z(), (float) replayAir.orientation().w());
        var afterAck = angularPresentation(body, qPrior, after);
        for (float partial : new float[]{.73995f, .91995f}) {
            var actualBefore = AngularRecoveryPresentation.committedOrientation(beforeAck, partial);
            var actualAfter = AngularRecoveryPresentation.committedOrientation(afterAck, partial);
            assertEquals(1, Math.abs(actualBefore.dot(actualAfter)), 1e-6f,
                    "same SDK pair presents continuously on either side of ACK");
            var cameraBefore = PureAngularRecoveryCameraMath.compose(actualBefore, -40.275f, 18.15f, 0);
            var cameraAfter = PureAngularRecoveryCameraMath.compose(actualAfter, -40.275f, 18.15f, 0);
            assertEquals(cameraBefore.yaw(), cameraAfter.yaw(), 1e-5f);
            assertEquals(cameraBefore.pitch(), cameraAfter.pitch(), 1e-5f);
        }
        var transit = angularSnapshot(3, 1, false, GhostControlPayloads.Snapshot.MovementMode.TRANSIT);
        var transitResult = history.reconcile(transit);
        assertFalse(CharacterMovementSnapshotPolicy.allowsReplay(transit.movementMode(), transitResult.status(), false),
                "TRANSIT is an authority barrier and must not enter angular replay");
        var legacyDeparture = model.afterMove(1, body, true, false, prior, zero(),
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        var legacyAir = model.afterMove(1, body, false, false, legacyDeparture, zero(),
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        var legacy = new org.joml.Quaternionf((float) legacyAir.orientation().x(), (float) legacyAir.orientation().y(),
                (float) legacyAir.orientation().z(), (float) legacyAir.orientation().w());
        assertTrue(Math.abs(qPrior.dot(legacy)) > .999999f, "old zero-witness replay freezes first AIR interval");
        assertTrue(Math.abs(qPrior.dot(qAfter)) < .999f, "recorded omega advances first AIR interval");
        var oldPair = angularPresentation(body, qPrior, qPrior);
        var correctedPair = angularPresentation(body, qPrior, qAfter);
        for (float partial : new float[]{.73995f, .91995f}) {
            var oldPresentation = AngularRecoveryPresentation.committedOrientation(oldPair, partial);
            var caughtUp = AngularRecoveryPresentation.committedOrientation(correctedPair, partial);
            double pulseRadians = 2 * Math.acos(Math.min(1, Math.abs(oldPresentation.dot(caughtUp))));
            assertEquals(partial * .1, pulseRadians, 2e-5,
                    "old frozen pair followed by corrected pair produces the observed partial-tick catch-up pulse");
        }
        // Opposite signed spin near the yaw wrap retains prior heading and advances rather than snapping north.
        var wrapPrior = AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(
                new AngularRecoveryModel.Vec3(0, 1, 0), -Math.PI + .03), zero());
        var reverseHistory = new GhostPredictionHistory(1, 8, MobHarnessKind.CHARACTER);
        reverseHistory.record(new GhostControlPayloads.Intent(1, 1, (short) 0, (short) 0, (byte) 0, 0, 0, 0));
        reverseHistory.recordAngularEvidence(1, new GhostPredictionHistory.AngularSupportEvidence(frame, true,
                new AngularRecoveryModel.Vec3(0, -2, 0)));
        var reverseSnapshot = new GhostControlPayloads.Snapshot(1, 8, MobHarnessKind.CHARACTER, 1, 0,
                0, 0, 0, 0, 0, 0, 0, 0, true, 1, 1, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, Optional.empty(),
                Optional.of(new GhostControlPayloads.Snapshot.FrameState(frame, 0, 0, 0)), 0);
        var reverseReplay = reverseHistory.reconcile(reverseSnapshot);
        assertTrue(CharacterMovementSnapshotPolicy.allowsReplay(reverseSnapshot.movementMode(), reverseReplay.status(), false));
        var reverseOmega = AngularRecoveryPrediction.omegaForReplayDeparture(reverseHistory.angularEvidence(1), frame, true, zero());
        var wrapDeparture = model.afterMove(1, body, true, false, wrapPrior, reverseOmega,
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        var wrapAir = model.afterMove(1, body, false, false, wrapDeparture, zero(),
                new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        assertEquals(.05, wrapAir.airborneSeconds(), 1e-12);
        assertEquals(1, Math.abs(new org.joml.Quaterniond().rotationY(-Math.PI + .03 - .1)
                .dot(new org.joml.Quaterniond(wrapAir.orientation().x(), wrapAir.orientation().y(),
                        wrapAir.orientation().z(), wrapAir.orientation().w()))), 1e-9);
    }

    @Test void compiledGhostConsumerActuallySelectsReplayEvidenceBeforeSingleAngularAdvance() throws Exception {
        var node = new ClassNode();
        try (var input = GhostControlClient.class.getResourceAsStream("/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.class")) {
            assertNotNull(input); new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        boolean wired = false;
        var wiringRows = new java.util.ArrayList<String>();
        for (var method : node.methods) {
            int evidence = -1, selector = -1, advance = -1, move = -1, advanceCount = 0;
            boolean exactSequenceEvidence = false;
            var instructions = method.instructions.toArray();
            for (int i = 0; i < instructions.length; i++) if (instructions[i] instanceof MethodInsnNode call) {
                if (call.name.equals("angularEvidence") && call.owner.equals("com/juicyslew/moonstation14/ms14/player_body_control/prediction/GhostPredictionHistory")) {
                    evidence = i;
                    for (int before = i - 1; before >= 0; before--) if (instructions[before] instanceof MethodInsnNode priorCall) {
                        exactSequenceEvidence = priorCall.name.equals("sequence")
                                && priorCall.owner.equals("com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlPayloads$Intent");
                        break;
                    }
                }
                if (call.name.equals("omegaForReplayDeparture") && call.owner.equals("com/juicyslew/moonstation14/ms14/player_body_control/client/AngularRecoveryPrediction")) selector = i;
                if (call.name.equals("afterMove") && call.owner.equals("com/juicyslew/moonstation14/ms14/player_body_control/client/AngularRecoveryPrediction")) { advance = i; advanceCount++; }
                if (call.name.equals("move") && call.owner.equals("com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyMotion")) move = i;
            }
            if (method.name.contains("lambda$predictCharacterInTemporalScope")) wiringRows.add(method.name + ":" + exactSequenceEvidence + "," + evidence + "," + selector + "," + move + "," + advance + "," + advanceCount);
            if (move >= 0 && exactSequenceEvidence && evidence >= 0 && selector > evidence
                    && advance > selector && advanceCount == 1) wired = true;
        }
        assertTrue(wired, "actual GhostControlClient callback must read exact-sequence evidence, select replay omega, then advance model; methods="
                + wiringRows);
    }

    private static GhostControlPayloads.Snapshot angularSnapshot(long tick, long ack, boolean grounded) {
        return angularSnapshot(tick, ack, grounded, GhostControlPayloads.Snapshot.MovementMode.GROUNDED);
    }
    private static GhostControlPayloads.Snapshot angularSnapshot(long tick, long ack, boolean grounded,
            GhostControlPayloads.Snapshot.MovementMode mode) {
        return new GhostControlPayloads.Snapshot(1, 7, MobHarnessKind.CHARACTER, tick, ack,
                0, 0, 0, 0, 0, 0, 0, 0, grounded, 1, 1, false, 0,
                mode,
                Optional.empty(), grounded ? Optional.of(new GhostControlPayloads.Snapshot.FrameState(UUID.fromString("c7ae448e-f64a-4962-88ec-4f315b7d6a20"), 0, 0, 0)) : Optional.empty(), 0);
    }
    private static com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair angularPresentation(
            UUID body, org.joml.Quaternionf from, org.joml.Quaternionf to) {
        var pose = new dev.ryanhcode.sable.companion.math.Pose3d();
        var pair = com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.AngularPair.capture(from, to);
        var publication = new com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore.Presentation(
                1, body, 10, 11, java.util.Map.of(), pair);
        return publication.angularFor(1, body);
    }
    private static NativeFrameInterval.PosePair pair(double v) {
        double[] m = {1,0,0,0,0,1,0,0,0,0,1,0,v,0,0,1};
        return new NativeFrameInterval.PosePair(new NativeFrameInterval.PoseValue(m),
                new NativeFrameInterval.PoseValue(m));
    }
    private static Quaterniond yaw(double a) { return new Quaterniond().rotationY(a); }
    private static AngularRecoveryModel.Vec3 zero() { return new AngularRecoveryModel.Vec3(0,0,0); }

    @Test void supportRotationIsWorldOmegaAndRepeatedNativePairDoesNotDoubleSample() {
        var p = new AngularRecoveryPrediction(); var body=UUID.randomUUID(); var frame=UUID.randomUUID();
        var pose=pair(1);
        assertEquals(2, p.sample(4,body,frame,true,pose,yaw(0),yaw(.1),.05).y(), 1e-9); // first witnessed interval is measurable immediately
        var changed=pair(2);
        assertEquals(2, p.sample(4,body,frame,true,changed,yaw(0),yaw(.1),.05).y(), 1e-9);
        assertEquals(2, p.sample(4,body,frame,true,changed,yaw(0),yaw(.7),.05).y(), 1e-9);
    }

    @Test void unsupportedOrMerelyNearbyFrameCannotSupplyInheritedSpinAndResetRebases() {
        var p = new AngularRecoveryPrediction(); var body=UUID.randomUUID(); var frame=UUID.randomUUID();
        p.sample(1,body,frame,true,pair(1),yaw(0),yaw(.2),.05);
        assertEquals(zero(),p.sample(1,body,frame,false,pair(2),yaw(0),yaw(1),.05));
        assertEquals(10,p.sample(2,body,frame,true,pair(3),yaw(0),yaw(.5),.05).y(),1e-9);
        p.reset();
        assertEquals(zero(),p.sample(2,UUID.randomUUID(),frame,true,pair(4),yaw(1),yaw(1),.05));
        var freshBody = UUID.randomUUID();
        assertEquals(10, p.sample(3,freshBody,frame,true,pair(5),yaw(0),yaw(.5),.05).y(), 1e-9);
        assertEquals(zero(), p.sample(4,freshBody,frame,true,pair(6),yaw(0),yaw(0),.05));
    }

    @Test void actualDepartureSeedsOnceAndEachReplayedPhysicalMoveAdvancesTheSameModel() {
        var p = new AngularRecoveryPrediction(); var body=UUID.randomUUID();
        var q=AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0,1,0),.4);
        var authoritative=AngularRecoveryModel.departure(q,new AngularRecoveryModel.Vec3(0,2,0));
        var first=p.afterMove(8,body,true,false,authoritative,new AngularRecoveryModel.Vec3(0,3,0),
                new AngularRecoveryModel.Vec3(0,1,0),.05,.65,1);
        assertEquals(0,first.airborneSeconds(),1e-12);
        var second=p.afterMove(8,body,false,false,first,zero(),new AngularRecoveryModel.Vec3(0,1,0),.05,.65,1);
        assertEquals(.05,second.airborneSeconds(),1e-12);
        assertEquals(3,second.worldAngularVelocity().y(),1e-12);
        assertNotEquals(first.orientation(),second.orientation());
    }

    @Test void nullPriorStateStillInheritsMeasuredSupportSpinAndEpochBodyChangesResetCache() {
        var p = new AngularRecoveryPrediction(); var body=UUID.randomUUID();
        var start=p.afterMove(3,body,true,false,null,new AngularRecoveryModel.Vec3(0,9,0),
                new AngularRecoveryModel.Vec3(0,1,0),.05,.65,1);
        assertEquals(0,start.airborneSeconds(),1e-12);
        assertEquals(9,start.worldAngularVelocity().y(),0);
        var replacement=p.afterMove(4,UUID.randomUUID(),false,false,start,zero(),
                new AngularRecoveryModel.Vec3(0,1,0),.05,.65,1);
        assertEquals(.05,replacement.airborneSeconds(),1e-12);
    }

    @Test void landingSettlesWithoutDestroyingHeading() {
        var p=new AngularRecoveryPrediction(); var body=UUID.randomUUID();
        var initial = new Quaterniond().rotationY(.7);
        var omega = new org.joml.Vector3d(1, 2, 0);
        var airborne=AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(
                new AngularRecoveryModel.Vec3(0,1,0),.7),new AngularRecoveryModel.Vec3(1,2,0));
        var landed=p.afterMove(1,body,false,true,airborne,zero(),new AngularRecoveryModel.Vec3(0,1,0),.05,.65,1);
        var integrated = new Quaterniond().rotationAxis(omega.length() * .05,
                omega.x / omega.length(), omega.y / omega.length(), omega.z / omega.length()).mul(initial);
        var expected = new Quaterniond().rotationTo(integrated.transform(new org.joml.Vector3d(0, 1, 0)),
                new org.joml.Vector3d(0, 1, 0)).mul(integrated);
        var expectedForward = expected.transform(new org.joml.Vector3d(0, 0, 1));
        var actualForward = landed.orientation().rotate(new AngularRecoveryModel.Vec3(0, 0, 1));
        assertEquals(0,landed.worldAngularVelocity().length(),0);
        assertEquals(expectedForward.x, actualForward.x(), 1e-9);
        assertEquals(expectedForward.y, actualForward.y(), 1e-9);
        assertEquals(expectedForward.z, actualForward.z(), 1e-9);
    }

    @Test void landingMoveIntegratesItsFlightIntervalBeforeSettling() {
        var p = new AngularRecoveryPrediction(); var body = UUID.randomUUID();
        double dt = .05, omega = 2.78, initialYaw = .31;
        var prior = AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(
                new AngularRecoveryModel.Vec3(0, 1, 0), initialYaw), new AngularRecoveryModel.Vec3(0, omega, 0));
        var landed = p.afterMove(1, body, false, true, prior, zero(),
                new AngularRecoveryModel.Vec3(0, 1, 0), dt, .65, 1);
        var actual = landed.orientation().rotate(new AngularRecoveryModel.Vec3(0, 0, 1));
        var expected = new Quaterniond().rotationY(initialYaw + omega * dt)
                .transform(new org.joml.Vector3d(0, 0, 1));
        assertEquals(expected.x, actual.x(), 1e-9);
        assertEquals(expected.z, actual.z(), 1e-9);
        assertEquals(0, landed.worldAngularVelocity().length(), 0);
    }

    @Test void completedLandingPoseSeedsNextNativeCarryWithoutBoundaryFreeze() {
        var prediction = new AngularRecoveryPrediction();
        var heading = new ClientSupportedHeading();
        var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        double dt = .05, omega = 2.78;
        var up = new AngularRecoveryModel.Vec3(0, 1, 0);
        var q0 = new Quaterniond();
        assertEquals(0, heading.carry(1, body, frame, q0, true, true));
        // This supported native interval precedes the physical departure move.
        assertEquals((float) Math.toDegrees(-omega * dt), heading.carry(1, body, frame,
                new Quaterniond().rotationY(omega * dt), true, true), 1e-3);
        var departure = prediction.afterMove(1, body, true, false, null,
                new AngularRecoveryModel.Vec3(0, omega, 0), up, dt, .65, 1);
        heading.carry(1, body, frame, null, false, true); // live unsupported input rebases heading basis
        var landed = prediction.afterMove(1, body, false, true, departure, zero(), up, dt, .65, 1);
        double landingYaw = 2 * Math.atan2(landed.orientation().y(), landed.orientation().w());
        var landingPose = new Quaterniond().rotationY(landingYaw);
        heading.seed(1, body, frame, landingPose, true);
        assertEquals((float) Math.toDegrees(-omega * dt), heading.carry(1, body, frame,
                new Quaterniond().rotationY(landingYaw + omega * dt), true, true), 1e-3);
    }

    @Test void replayedAngularMovesDoNotConsumeLiveSupportedHeadingBaseline() {
        var prediction = new AngularRecoveryPrediction();
        var liveHeading = new ClientSupportedHeading();
        var body = UUID.randomUUID(); var frame = UUID.randomUUID();
        var livePose = new Quaterniond();
        assertEquals(0, liveHeading.carry(1, body, frame, livePose, true, true));
        var state = AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(
                new AngularRecoveryModel.Vec3(0, 1, 0), 0), new AngularRecoveryModel.Vec3(0, 2.78, 0));
        for (int i = 0; i < 7; i++)
            state = prediction.afterMove(1, body, false, false, state, zero(),
                    new AngularRecoveryModel.Vec3(0, 1, 0), .05, .65, 1);
        var replayedPose = new Quaterniond(state.orientation().x(), state.orientation().y(),
                state.orientation().z(), state.orientation().w());
        assertEquals(1, Math.abs(replayedPose.dot(new Quaterniond().rotationY(2.78 * .05 * 7))), 1e-9);
        // The client invokes carry only for live supported input; replay advances the separate angular model.
        assertEquals((float) Math.toDegrees(-.139), liveHeading.carry(1, body, frame,
                new Quaterniond().rotationY(.139), true, true), 1e-3);
    }
}
