package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class OwnedFramePresentationContextTest {
    @Test void productionPublicationAndActorReadersKeepWorldEndpointsIsolatedFromSceneFrames() throws Exception {
        UUID frame=UUID.randomUUID(), body=UUID.randomUUID();
        var timeline=new AuthoritativeFrameTimeline();
        UUID level=UUID.randomUUID();
        assertTrue(timeline.receive(level,frame,100,pose(0),new Vec3(.15625,0,0),Vec3.ZERO));
        assertTrue(timeline.receive(level,frame,101,pose(.15625),new Vec3(.15625,0,0),Vec3.ZERO));
        var history=new ClientFrameTimelineStore.LevelHistory();
        history.frames.put(frame,timeline);
        var actors=new HashMap<UUID,ClientFrameTimelineStore.Presentation>();
        var angular=ClientFrameTimelineStore.AngularPair.identity();
        ClientFrameTimelineStore.publishViews(history,actors,1,body,100,101,history.frames,Map.of(),angular);
        assertTrue(actors.get(body).pairs.isEmpty(),"world body publication retains the exact empty primary keyset");
        assertNull(ClientFrameTimelineStore.actorEndpoint(actors,body,frame,true));
        assertNull(ClientFrameTimelineStore.actorEndpoint(actors,body,frame,false));
        assertEquals(.078125d,history.renderPublication.pose(frame,.5f).position().x(),1e-9);
        assertEquals(100,actors.get(body).from()); assertEquals(101,actors.get(body).to());

        var owned=new OwnedFrameSimulationScope.PresentationPair(pose(4),pose(5));
        ClientFrameTimelineStore.publishViews(history,actors,2,body,100,101,history.frames,Map.of(frame,owned),angular);
        assertEquals(4d,ClientFrameTimelineStore.actorEndpoint(actors,body,frame,true).position().x(),1e-9);
        assertEquals(5d,ClientFrameTimelineStore.actorEndpoint(actors,body,frame,false).position().x(),1e-9);
        assertEquals(4.5d,history.renderPublication.pose(frame,.5f).position().x(),1e-9,"primary owner pair wins in scene view");
        ClientFrameTimelineStore.publishViews(history,actors,3,body,101,102,history.frames,Map.of(),angular);
        assertNull(ClientFrameTimelineStore.actorEndpoint(actors,body,frame,true));
        assertNotNull(history.renderPublication.pose(frame,.5f),"scene continues on the new clock");
        double committedScene=history.renderPublication.pose(frame,1).position().x();
        assertTrue(timeline.receive(level,frame,102,pose(50),Vec3.ZERO,Vec3.ZERO));
        assertEquals(committedScene,history.renderPublication.pose(frame,1).position().x(),1e-9,"published scene view is immutable");

        Path root=Path.of("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable").toAbsolutePath();
        if(!Files.isDirectory(root)) {
            Path directory=Path.of("").toAbsolutePath().normalize();
            while(directory!=null&&!Files.isDirectory(directory.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable")))directory=directory.getParent();
            assertNotNull(directory,"production store source root");
            root=directory.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable");
        }
        Path store=root.resolve("ClientFrameTimelineStore.java");
        Path context=store.resolveSibling("OwnedFramePresentationContext.java");
        Path getter=root.getParent().getParent().getParent().resolve("mixin/client/SableOwnedSubLevelPoseGetterMixin.java");
        String storeSource=Files.readString(store),contextSource=Files.readString(context),getterSource=Files.readString(getter);
        assertTrue(storeSource.contains("publishViews(level(level),PRESENTATION.computeIfAbsent"));
        assertTrue(storeSource.contains("return actorEndpoint(PRESENTATION.get(level),body,frame,last)"));
        assertTrue(contextSource.contains("presentationEndpoint(owner.level(),owner.getUUID(),frame.getUniqueId(),true)"));
        assertTrue(contextSource.contains("presentationEndpoint(owner.level(),owner.getUUID(),frame.getUniqueId(),false)"));
        assertTrue(getterSource.contains("if(pose!=null)cir.setReturnValue((Pose3d)pose)"));
        assertTrue(getterSource.contains("if(pose!=null)cir.setReturnValue(pose)"));
    }

    @Test void publicationFreezesUnownedFramesAtOwnedClockAndPrefersExactOwnerPair() {
        UUID level=UUID.randomUUID(), frame=UUID.randomUUID(), body=UUID.randomUUID();
        var timeline=new AuthoritativeFrameTimeline();
        assertTrue(timeline.receive(level,frame,10,pose(0),new Vec3(20,0,0),Vec3.ZERO));
        assertTrue(timeline.receive(level,frame,11,pose(1),new Vec3(20,0,0),Vec3.ZERO));
        var primary=new OwnedFrameSimulationScope.PresentationPair(pose(7),pose(8));
        var frozen=ClientFrameTimelineStore.freezePairs(Map.of(frame,timeline),10,11,Map.of());
        assertEquals(.5d,new ClientFrameTimelineStore.Presentation(1,body,10,11,frozen)
                .pose(frame,.5f).position().x(),1e-9);
        var ownerWins=ClientFrameTimelineStore.freezePairs(Map.of(frame,timeline),10,11,Map.of(frame,primary));
        assertEquals(7.5d,new ClientFrameTimelineStore.Presentation(1,body,10,11,ownerWins)
                .pose(frame,.5f).position().x(),1e-9);
        assertTrue(timeline.receive(level,frame,12,pose(50),Vec3.ZERO,Vec3.ZERO));
        assertEquals(.5d,new ClientFrameTimelineStore.Presentation(1,body,10,11,frozen)
                .pose(frame,.5f).position().x(),1e-9,"render publication does not resample revised history");
        assertFalse(ClientFrameTimelineStore.freezePairs(Map.of(),10,11,Map.of()).containsKey(frame));
    }

    @Test void currentLevelPublicationDrivesFrameAirWorldAndAdvancingPoseIntervals() {
        UUID frame=UUID.randomUUID(),owner=UUID.randomUUID();
        var history=new ClientFrameTimelineStore.LevelHistory();
        var first=new ClientFrameTimelineStore.Presentation(1,owner,100,101,
                Map.of(frame,new OwnedFrameSimulationScope.PresentationPair(yawPose(0,0),yawPose(1,Math.PI/2))));
        history.publish(first);
        // Renderer selection is independent of whether the observer is frame-owned, airborne, or world-owned.
        for(String mode:new String[]{"FRAME","AIR","WORLD"}) {
            assertEquals(1d,history.renderPublication.pose(frame,1).position().x(),1e-9,mode);
            assertEquals(Math.PI/4,Math.abs(new Pose3d(history.renderPublication.pose(frame,.5f)).orientation().getEulerAnglesXYZ(new org.joml.Vector3d()).y),1e-6,mode);
        }
        var second=new ClientFrameTimelineStore.Presentation(1,owner,101,102,
                Map.of(frame,new OwnedFrameSimulationScope.PresentationPair(yawPose(1,Math.PI/2),yawPose(2,Math.PI))));
        history.publish(second);
        assertEquals(1d,history.renderPublication.pose(frame,0).position().x(),1e-9);
        assertEquals(1.5d,history.renderPublication.pose(frame,.5f).position().x(),1e-9);
        assertEquals(2d,history.renderPublication.pose(frame,1).position().x(),1e-9);
    }

    @Test void publicationResetRemovalAndReplacementCannotSelectStaleOrOtherBodyClock() {
        UUID firstFrame=UUID.randomUUID(),secondFrame=UUID.randomUUID(),oldBody=UUID.randomUUID(),newBody=UUID.randomUUID();
        var history=new ClientFrameTimelineStore.LevelHistory();
        history.publish(new ClientFrameTimelineStore.Presentation(1,oldBody,1,2,
                Map.of(firstFrame,new OwnedFrameSimulationScope.PresentationPair(pose(0),pose(1)))));
        var old=history.renderPublication;
        history.publish(new ClientFrameTimelineStore.Presentation(2,newBody,8,9,
                Map.of(secondFrame,new OwnedFrameSimulationScope.PresentationPair(pose(20),pose(21)))));
        assertEquals(20.5d,history.renderPublication.pose(secondFrame,.5f).position().x(),1e-9);
        assertNull(old.pose(secondFrame,.5f));
        history.reset(oldBody);
        assertEquals(20.5d,history.renderPublication.pose(secondFrame,.5f).position().x(),1e-9,
                "resetting another body's retained reader does not clear the current render publication");
        history.remove(secondFrame);
        assertNull(history.renderPublication.pose(secondFrame,.5f));
        history.reset(newBody);
        assertNull(history.renderPublication);
    }

    @Test void missingUnknownMovingSeedIsSkippedButStationaryAndStoppedFramesHold() {
        UUID level=UUID.randomUUID(),unknown=UUID.randomUUID(),stationary=UUID.randomUUID();
        var movingSeed=new AuthoritativeFrameTimeline();
        assertTrue(movingSeed.seed(level,unknown,10,pose(0),pose(1)));
        var held=new AuthoritativeFrameTimeline();
        assertTrue(held.seed(level,stationary,10,pose(2),pose(2)));
        held.stop(level,stationary,10);
        var pairs=ClientFrameTimelineStore.freezePairs(Map.of(unknown,movingSeed,stationary,held),10,11,Map.of());
        assertFalse(pairs.containsKey(unknown));
        assertEquals(2d,new ClientFrameTimelineStore.Presentation(1,UUID.randomUUID(),10,11,pairs)
                .pose(stationary,1).position().x(),1e-9);
    }

    @Test void packetRevisionCannotRePairCommittedBodyEndpointWithResampledFrameEndpoint() {
        UUID level = UUID.randomUUID(), frame = UUID.randomUUID();
        var timeline = new AuthoritativeFrameTimeline();
        Pose3d anchor = pose(0);
        assertTrue(timeline.receive(level, frame, 99, anchor, new Vec3(20, 0, 0), Vec3.ZERO));
        Pose3d predicted = timeline.sample(frame, 100).pose();
        assertEquals(1d, predicted.position().x, 1e-9);
        // Production publication payload freezes the actual selected interval once at commit.
        UUID body = UUID.randomUUID();
        var committed = new ClientFrameTimelineStore.Presentation(1, body, 99, 100,
                Map.of(frame, new OwnedFrameSimulationScope.PresentationPair(anchor, predicted)));
        Vec3 bodyFrom = new Vec3(1, 0, 0), bodyTo = new Vec3(2, 0, 0);
        var firstRender = committed.pose(frame, .5f);
        assertTrue(timeline.receive(level, frame, 100, pose(.1), Vec3.ZERO, Vec3.ZERO));
        Pose3d revisedRender = pose(.1);
        assertEquals(firstRender.position().x(), committed.pose(frame, .5f).position().x(), 1e-9,
                "repeated render query consumes the frozen publication, not timeline.sample");
        Vec3 currentRenderSequence = OwnedFramePresentationContext.projectOwnedInterval(
                new Pose3d(committed.pose(frame, 0)), new Pose3d(committed.pose(frame, 1)), revisedRender, bodyFrom, bodyTo, .5f);
        assertEquals(1d, currentRenderSequence.x - .1, 1e-9,
                "resampling a replaced predicted endpoint must not alter committed standing local offset");
        var nextCommit = new ClientFrameTimelineStore.Presentation(1, body, 100, 101,
                Map.of(frame, new OwnedFrameSimulationScope.PresentationPair(revisedRender, pose(.2))));
        assertEquals(.15, nextCommit.pose(frame, .5f).position().x(), 1e-9,
                "a subsequent commit publishes its own revised endpoints");
    }

    @Test void committedFramePairPreservesStandingOffsetUnderRevisedRenderPose() {
        Pose3d anchor = pose(0);
        Pose3d predicted = pose(1);
        Pose3d revisedRender = pose(.1);
        Vec3 bodyFrom = new Vec3(1, 0, 0), bodyTo = new Vec3(2, 0, 0);
        Vec3 committedPair = OwnedFramePresentationContext.projectOwnedInterval(
                anchor, predicted, revisedRender, bodyFrom, bodyTo, .5f);
        assertEquals(1d, committedPair.x - .1, 1e-9,
                "the original committed body/frame pair remains coherent under revised render pose");
    }

    @Test void presentationPairCopiesCanonicalPoseInputsAndAccessors() {
        Pose3d sourceFrom=pose(1), sourceTo=pose(2);
        var pair=new OwnedFrameSimulationScope.PresentationPair(sourceFrom,sourceTo);
        sourceFrom.position().x=100; sourceTo.position().x=200;
        assertEquals(1d,pair.from().position().x,1e-9);
        assertEquals(2d,pair.to().position().x,1e-9);
        var exposed=pair.from(); exposed.position().x=300;
        assertEquals(1d,pair.from().position().x,1e-9);
    }

    @Test void matchingTranslationEndpointsCancelToVanillaWorldLerpButDelayedSdkEndpointsDoNot() {
        var from=pose(0);var to=pose(10);var render=pose(5);
        Vec3 bodyFrom=new Vec3(2,0,0),bodyTo=new Vec3(12,0,0);
        Vec3 owned=OwnedFramePresentationContext.projectOwnedInterval(from,to,render,bodyFrom,bodyTo,.5f);
        Vec3 vanilla=bodyFrom.lerp(bodyTo,.5);
        assertEquals(vanilla.x,owned.x,1e-9);assertEquals(vanilla.y,owned.y,1e-9);assertEquals(vanilla.z,owned.z,1e-9);
        Vec3 sdkDelayed=OwnedFramePresentationContext.projectOwnedInterval(pose(-2),pose(-1),render,bodyFrom,bodyTo,.5f);
        assertTrue(sdkDelayed.distanceTo(vanilla)>1d,"SDK-delayed endpoint inversions reproduce the camera/body offset");
    }

    @Test void cameraAndOwnedEntityRendererBracketExactOwnerContextAndBothRenderPoseOverloadsAreHooked() throws Exception {
        Path root=Path.of("src/main/java/com/juicyslew/moonstation14/mixin/client").toAbsolutePath();
        if (!Files.isDirectory(root)) {
            Path directory=Path.of("").toAbsolutePath().normalize();
            while(directory!=null&&!Files.isDirectory(directory.resolve("src/main/java/com/juicyslew/moonstation14/mixin/client")))directory=directory.getParent();
            assertNotNull(directory,"project source root");
            root=directory.resolve("src/main/java/com/juicyslew/moonstation14/mixin/client");
        }
        String camera=Files.readString(root.resolve("SableOwnedCameraPresentationContextMixin.java"));
        String renderer=Files.readString(root.resolve("SableOwnedFrameEntityRenderContextMixin.java"));
        String frame=Files.readString(root.resolve("SableOwnedFramePresentationMixin.java"));
        String getters=Files.readString(root.resolve("SableOwnedSubLevelPoseGetterMixin.java"));
        Path sourceRoot=root;
        while(sourceRoot!=null&&!Files.isDirectory(sourceRoot.resolveSibling("resources")))sourceRoot=sourceRoot.getParent();
        assertNotNull(sourceRoot,"production java source root");
        String config=Files.readString(sourceRoot.resolveSibling("resources/moonstation14.mixins.json"));
        for(String source:new String[]{camera,renderer}) {
            assertTrue(source.contains("OwnedFramePresentationContext.begin"));
            assertTrue(source.contains("OwnedFramePresentationContext.end"));
            assertTrue(source.contains("@At(\"HEAD\")")&&source.contains("@At(\"RETURN\")"));
        }
        assertTrue(camera.contains("@Local(argsOnly=true) Entity cameraEntity"));
        assertTrue(renderer.contains("@Local(argsOnly=true) Entity entity"));
        assertTrue(getters.contains("OwnedFramePresentationContext.active"));
        assertTrue(frame.contains("renderPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;"));
        assertTrue(frame.contains("getGameTimeDeltaPartialTick(false)"));
        assertTrue(config.contains("client.SableOwnedCameraPresentationContextMixin"));
        assertTrue(config.contains("client.SableOwnedFrameEntityRenderContextMixin"));
    }

    @Test void acceptedIntervalsAdvanceAllVanillaBodyEndpointsAndUseThemWithPublishedFrameEndpoints() throws Exception {
        // Three accepted replay intervals after a snapshot restore: the final render interval starts
        // at the second replay endpoint, not at the original authority anchor.
        Vec3 anchor = new Vec3(10, 2, 3);
        Vec3 bodyTo = anchor;
        Vec3 renderFrom = anchor;
        Vec3 frameFrom = Vec3.ZERO;
        Vec3 frameTo = Vec3.ZERO;
        for (int step = 0; step < 3; step++) {
            renderFrom = bodyTo;
            frameFrom = new Vec3(step, step * .5, step * -.25);
            bodyTo = bodyTo.add(1, .5, -.25);
            frameTo = new Vec3(step + 1, (step + 1) * .5, (step + 1) * -.25);
        }
        Vec3 renderFrame = frameFrom.lerp(frameTo, .5);
        Vec3 projected = OwnedFramePresentationContext.projectOwnedInterval(
                pose(frameFrom), pose(frameTo), pose(renderFrame), renderFrom, bodyTo, .5f);
        Vec3 vanilla = renderFrom.lerp(bodyTo, .5);
        assertEquals(vanilla.x, projected.x, 1e-9);
        assertEquals(vanilla.y, projected.y, 1e-9);
        assertEquals(vanilla.z, projected.z, 1e-9);
        Vec3 staleAnchorProjection = OwnedFramePresentationContext.projectOwnedInterval(
                pose(frameFrom), pose(frameTo), pose(renderFrame), anchor, bodyTo, .5f);
        assertTrue(staleAnchorProjection.distanceTo(vanilla) > .5,
                "leaving body render-from at snapshot S while publishing the final replay interval creates frame-motion drift");

        ClassNode node = readClass(com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.class);
        MethodNode writer = method(node, "setOwnedBodyRenderFrom");
        Set<String> fields = new HashSet<>();
        for (var instruction : writer.instructions)
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.owner.equals("net/minecraft/world/entity/Entity")) fields.add(field.name);
        assertEquals(Set.of("xo", "yo", "zo", "xOld", "yOld", "zOld"), fields,
                "the shared from-point writer must anchor SDK camera and vanilla body-render interpolation endpoints");

        MethodNode prediction = method(node, "predictCharacter");
        int commit = callIndex(prediction, "commit", "ClientOwnedSimulationClock");
        int predictionWriter = callIndex(prediction, "setOwnedBodyRenderFrom", "GhostControlClient");
        int predictionPublish = callIndex(prediction, "publish", "ClientFrameTimelineStore");
        assertTrue(commit >= 0 && commit < predictionWriter && predictionWriter < predictionPublish,
                "only an accepted prediction writes body-from fields, before publishing its matching SDK interval");

        MethodNode restore = method(node, "reconcileSnapshot");
        int restoreWriter = callIndex(restore, "setOwnedBodyRenderFrom", "GhostControlClient");
        int restorePublish = callIndex(restore, "publish", "ClientFrameTimelineStore");
        assertTrue(restoreWriter >= 0 && restoreWriter < restorePublish,
                "valid restored clock anchor initializes all body-from fields before publishing the anchor");

        int noHistoryReset = callIndex(restore, "resetOwnedSimulationTimeline", "GhostControlClient");
        int snapshotVelocity = callIndex(restore, "setDeltaMovement", "Entity");
        assertTrue(noHistoryReset >= 0 && noHistoryReset < snapshotVelocity,
                "the no-history snapshot path clears this body's presentation clock before returning");

        MethodNode cleanup = method(node, "resetOwnedSimulationTimeline");
        int exactIdentity = callIndex(cleanup, "matches", "OwnedSimulationTimelineIdentity");
        int clockReset = callIndex(cleanup, "reset", "ClientOwnedSimulationClock");
        int publicationReset = callIndex(cleanup, "reset", "ClientFrameTimelineStore");
        assertTrue(exactIdentity >= 0 && exactIdentity < clockReset && clockReset < publicationReset,
                "raw committed-body identity is checked before resetting its clock and published interval");
        assertNoEligibilityRecursion(node, cleanup);

        MethodNode medical = method(node, "medicalDecision");
        int medicalBarrier = callIndex(medical, "authorityBarrierRequired", "MedicalPredictionPolicy$Decision");
        int medicalReset = callIndex(medical, "resetOwnedSimulationTimeline", "GhostControlClient");
        int historyBarrier = callIndex(medical, "onAuthorityBarrier", "GhostPredictionHistory");
        assertTrue(medicalBarrier >= 0 && medicalBarrier < medicalReset && medicalReset < historyBarrier,
                "medical suspension clears the stale presentation interval while retaining history barrier behavior");
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        String name = type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
    }

    private static int callIndex(MethodNode method, String name, String ownerSuffix) {
        int index = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)
                    && call.owner.endsWith(ownerSuffix)) return index;
            index++;
        }
        return -1;
    }

    private static void assertNoEligibilityRecursion(ClassNode owner, MethodNode root) {
        Set<String> forbidden = Set.of("medicalDecision", "ownedHarnessForTracker", "ownedGhostForTracker",
                "ownsSableRemoteFrameInterpolation", "currentBody", "characterPolicy");
        Map<String, MethodNode> byKey = new HashMap<>();
        for (MethodNode method : owner.methods) byKey.put(method.name + method.desc, method);
        Set<String> visited = new HashSet<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            MethodNode current = pending.removeFirst();
            String key = current.name + current.desc;
            if (!visited.add(key)) continue;
            for (var instruction : current.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (call.owner.equals(owner.name)) {
                    assertFalse(forbidden.contains(call.name),
                            "timeline cleanup cannot recurse through eligibility method " + call.name);
                    MethodNode target = byKey.get(call.name + call.desc);
                    if (target != null) pending.addLast(target);
                }
            }
        }
        assertTrue(visited.contains(root.name + root.desc));
    }

    private static Pose3d pose(double x){var pose=new Pose3d();pose.position().set(x,0,0);return pose;}
    private static Pose3d yawPose(double x,double yaw){var pose=pose(x);pose.orientation().rotationY(yaw);return pose;}
    private static Pose3d pose(Vec3 translation){var pose=new Pose3d();pose.position().set(translation.x,translation.y,translation.z);return pose;}
}
