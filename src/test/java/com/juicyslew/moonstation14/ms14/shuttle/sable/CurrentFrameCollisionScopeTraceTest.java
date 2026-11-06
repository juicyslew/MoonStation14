package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurrentFrameCollisionScopeTraceTest {
    private static final UUID FRAME = UUID.randomUUID();

    @Test void movementScopeCapturesExplicitWorldAndExactStartingFrameBasis() {
        var owner=blankPlayer();
        var point=new net.minecraft.world.phys.Vec3(1,2,3);
        var world=new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                point,point,null);
        var worldResult=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,false,world,()->null);
        assertTrue(worldResult.startingBasis().world());
        assertNull(worldResult.startingBasis().poses());

        UUID frameA=UUID.randomUUID();
        var startA=new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                point,point,frameA);
        // Even if the native solver subsequently selects frame B, the snapshot remains the
        // exact movement-entry A basis. Missing A history is explicitly invalid, never WORLD.
        var legacyFrameResult=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->null);
        assertFalse(legacyFrameResult.startingBasis().valid(),"legacy observation overload has no claimed WORLD basis");
        boolean[] moved={false};
        var invalidBasis=assertThrows(IllegalArgumentException.class,()->
                CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,false,startA,()->{moved[0]=true;return null;}));
        assertEquals("collision resolution must be finite and bounded by requested displacement",invalidBasis.getMessage());
        assertFalse(moved[0],"bound tracked movement rejects invalid history before invoking native move");
        assertFalse(CurrentFrameCollisionScope.isTrackedCharacter(owner),"invalid precondition does not leak scope state");
        var frameResult=legacyFrameResult;
        assertFalse(frameResult.startingBasis().world());
        assertFalse(frameResult.startingBasis().valid());
        assertNull(frameResult.startingBasis().poses());
        UUID frameB=UUID.randomUUID();
        assertNotEquals(frameA,frameB,"the selected B frame is distinct from the captured A basis");
    }

    @Test void startingBasisTransportChangesOnlyWorldOrCrossFrameBaseTransport() {
        var original=new Vector3d(10,2,3);
        var sdk=new Vector3d(12,4,6);
        var world=CurrentFrameCollisionScope.selectStartingBasisTransportPoint(null,true,FRAME,null,null,
                original,new Vector3d(sdk),2,2);
        assertEquals(original,world,"WORLD start removes only the selected-frame passenger transport");

        UUID frameA=UUID.randomUUID();
        var unchanged=new Vector3d(sdk);
        assertSame(unchanged,CurrentFrameCollisionScope.selectStartingBasisTransportPoint(frameA,true,frameA,
                null,null,original,unchanged,2,2));
        assertEquals(sdk,unchanged,"A->A preserves the exact SDK transform result");

        var from=new dev.ryanhcode.sable.companion.math.Pose3d();
        var to=new dev.ryanhcode.sable.companion.math.Pose3d();
        from.position().set(1,0,0);to.position().set(3,0,0);
        var cross=new Vector3d(sdk);
        CurrentFrameCollisionScope.selectStartingBasisTransportPoint(frameA,true,FRAME,from,to,original,cross,2,2);
        assertEquals(new Vector3d(11,2,3),cross,"A's matching 1->2 substep maps the same world point");
    }

    @Test void invalidCrossFrameBasisNeverFallsBackToSelectedFrameTransport() {
        UUID frameA=UUID.randomUUID();
        UUID frameB=UUID.randomUUID();
        var original=new Vector3d(10,2,3);
        var sdkCarrier=new Vector3d(12,2,3);
        assertThrows(IllegalArgumentException.class,()->CurrentFrameCollisionScope.selectStartingBasisTransportPoint(
                frameA,false,frameB,null,null,original,new Vector3d(sdkCarrier),1,2));
        assertThrows(IllegalArgumentException.class,()->CurrentFrameCollisionScope.selectStartingBasisTransportPoint(
                frameA,true,frameB,null,new dev.ryanhcode.sable.companion.math.Pose3d(),
                original,new Vector3d(sdkCarrier),1,2),"missing full A pose pair fails closed");

        var from=new dev.ryanhcode.sable.companion.math.Pose3d();
        var to=new dev.ryanhcode.sable.companion.math.Pose3d();
        from.position().set(-1e308,0,0);to.position().set(1e308,0,0);
        assertThrows(IllegalArgumentException.class,()->CurrentFrameCollisionScope.selectStartingBasisTransportPoint(
                frameA,true,frameB,from,to,new Vector3d(),new Vector3d(),1,2),
                "nonfinite transformed A point cannot publish B's raw carrier");
    }

    @Test void inheritedSdkVelocityIsSuppressedOnlyInExactTrackedCharacterScope() {
        var owner=blankPlayer(); var other=blankPlayer();
        var sdk=new net.minecraft.world.phys.Vec3(.2,.05,-.1);
        var selected=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            assertEquals(net.minecraft.world.phys.Vec3.ZERO,
                    CurrentFrameCollisionScope.effectiveVelocityMotion(owner,sdk));
            assertSame(sdk,CurrentFrameCollisionScope.effectiveVelocityMotion(other,sdk));
            return null;
        });
        assertSame(sdk,CurrentFrameCollisionScope.effectiveVelocityMotion(owner,sdk));
        assertEquals(new net.minecraft.world.phys.Vec3(.2,.05,-.1),sdk);
        CurrentFrameCollisionScope.withDiagnosticStockTrace(owner,()->{
            assertSame(sdk,CurrentFrameCollisionScope.effectiveVelocityMotion(owner,sdk));
            return null;
        });
        assertNotNull(selected);
    }

    @Test void capturesOnePairedVanillaWorldClipOnlyForExactTrackedEntity() {
        var owner=blankPlayer(); var other=blankPlayer();
        var input=new net.minecraft.world.phys.Vec3(.1,-.001,.2);
        var output=new net.minecraft.world.phys.Vec3(.1,0,.2);
        var position=new net.minecraft.world.phys.Vec3(4,5,6);
        var bounds=new net.minecraft.world.phys.AABB(3.7,5,5.7,4.3,6.8,6.3);
        var scoped=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(other,input,position,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(other,output);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,position,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(owner,output);
            return null;
        });
        var observation=scoped.worldClipObservation();
        assertNotNull(observation);
        assertTrue(observation.matchesCall(owner,input,output,position,bounds));
        assertFalse(observation.matchesCall(other,input,output,position,bounds));
        assertFalse(observation.matchesCall(owner,new net.minecraft.world.phys.Vec3(.1,-.002,.2),output,position,bounds));
        assertThrows(UnsupportedOperationException.class,()->scoped.nativeCollisionTrace().clear());
    }

    @Test void defaultTrackedClientContractRequiresNativeSideGeometryWithoutDiagnosticMode() {
        var owner=blankPlayer();
        var selected=new Vector3d(-.1,0,0);
        var motion=new Vector3d(.1,-.4,0);
        var result=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            assertFalse(CurrentFrameCollisionScope.observesNativeCollisionGeometry(owner),
                    "optional diagnostic geometry remains disabled by default");
            assertTrue(CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(owner,selected,false),
                    "side MTV geometry is required for tracked-character proof on client and server");
            CurrentFrameCollisionScope.recordNativeCollisionSelection(owner,FRAME,BlockPos.ZERO,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),selected,motion,1,1,0,
                    null,false);
            CurrentFrameCollisionScope.recordNativeCollisionApplied(owner,motion,selected);
            return null;
        });
        assertFalse(result.geometryTraceComplete(),"missing captured native OBB/MTV fails closed");
        assertEquals(1,result.nativeCollisionTrace().size());
    }

    @Test void defaultTrackedScopeRequiresNegativeVerticalCeilingGeometry() {
        var owner=blankPlayer(); var selected=new Vector3d(0,-.02202879558996,0);
        var result=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            assertFalse(CurrentFrameCollisionScope.observesNativeCollisionGeometry(owner));
            assertTrue(CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(owner,selected,true));
            CurrentFrameCollisionScope.recordNativeCollisionSelection(owner,FRAME,BlockPos.ZERO,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),selected,new Vector3d(0,.42,0),1,1,0,
                    null,true);
            CurrentFrameCollisionScope.recordNativeCollisionApplied(owner,new Vector3d(0,.42,0),selected);
            return null;
        });
        assertFalse(result.geometryTraceComplete());
        assertEquals(1,result.nativeCollisionTrace().size());
    }

    @Test void defaultTrackedScopeRequiresGeometryForPurePositiveFloorMtv() {
        var owner = blankPlayer();
        var selected = new Vector3d(0, .41339804586007034, 0);
        var result = CurrentFrameCollisionScope.withTrackedCharacterPose(owner, false, () -> {
            assertFalse(CurrentFrameCollisionScope.observesNativeCollisionGeometry(owner));
            assertTrue(CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(owner, selected, true));
            CurrentFrameCollisionScope.recordNativeCollisionSelection(owner, FRAME, BlockPos.ZERO,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), selected, new Vector3d(),
                    1, 1, 0, null, true);
            CurrentFrameCollisionScope.recordNativeCollisionApplied(owner, new Vector3d(), selected);
            return null;
        });
        assertFalse(result.geometryTraceComplete(), "missing required floor geometry must fail closed");
        assertEquals(1, result.nativeCollisionTrace().size(), "known selected row remains recorded");
        assertNull(result.nativeCollisionTrace().getFirst().geometry());
    }

    @Test void untrackedAndClosedScopesDoNotRequireGeometryButForcedObservationDoes() {
        var owner = blankPlayer();
        var selected = new Vector3d(0, .02, 0);
        assertFalse(CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(owner, selected, true));
        CurrentFrameCollisionScope.withDiagnosticStockTrace(owner, () -> {
            assertFalse(CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(owner, selected, true));
            return null;
        });
        CurrentFrameCollisionScope.withTrackedCharacterPose(owner, false, true, () -> {
            assertTrue(CurrentFrameCollisionScope.observesNativeCollisionGeometry(owner));
            assertTrue(CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(owner, selected, true));
            return null;
        });
        assertFalse(CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(owner, selected, true));
    }

    @Test void incompleteOrDuplicateVanillaWorldClipCannotCreateFloorWitness() {
        var owner=blankPlayer();
        var input=new net.minecraft.world.phys.Vec3(0,-.001,0);
        var output=net.minecraft.world.phys.Vec3.ZERO;
        var position=new net.minecraft.world.phys.Vec3(0,1,0);
        var bounds=new net.minecraft.world.phys.AABB(-.3,1,-.3,.3,2.8,.3);
        var missingReturn=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,position,bounds);return null;});
        assertNotNull(missingReturn.worldClipObservation());
        assertFalse(missingReturn.worldClipObservation().complete());
        var duplicate=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,position,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,position,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(owner,output);return null;});
        assertFalse(duplicate.worldClipObservation().complete());
        assertNull(CurrentFrameCollisionScope.withDiagnosticStockTrace(owner,()->null).worldClipObservation());
    }

    @Test void knownNoWorldFloorRequiresCompleteExactUnclippedOwnerObservation() {
        var owner=blankPlayer(); var other=blankPlayer();
        var before=new net.minecraft.world.phys.Vec3(4,5,6);
        var bounds=new net.minecraft.world.phys.AABB(3.7,5,5.7,4.3,6.8,6.3);
        var input=new net.minecraft.world.phys.Vec3(.1,-.001,.2);
        var nativeMotion=new com.juicyslew.moonstation14.ms14.movement.MovementVector(.1,-.001,.2);
        var measured=new com.juicyslew.moonstation14.ms14.movement.MovementVector(.1,-.001,.2);
        var after=before.add(measured.x(),measured.y(),measured.z());
        var unchanged=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,before,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(owner,input); return null;
        });
        assertTrue(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,bounds,after,
                unchanged.worldClipObservation()));
        var floor=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,before,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(owner,new net.minecraft.world.phys.Vec3(.1,0,.2));
            return null;
        });
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,bounds,after,
                floor.worldClipObservation()),"positive floor clip retains grounded proof");
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,bounds,after,null));
        var missing=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,before,bounds); return null;
        });
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,bounds,after,
                missing.worldClipObservation()));
        var duplicate=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,before,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,before,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(owner,input); return null;
        });
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,bounds,after,
                duplicate.worldClipObservation()));
        var wrongOwner=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(other,input,before,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(other,input); return null;
        });
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,bounds,after,
                wrongOwner.worldClipObservation()));
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,
                new net.minecraft.world.phys.AABB(3,5,5,4,7,6),after,unchanged.worldClipObservation()));
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,
                before.add(1,0,0),bounds,after,unchanged.worldClipObservation()));
        assertTrue(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,measured,before,bounds,
                after.add(com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL * .5,0,0),
                unchanged.worldClipObservation()),"sub-slop horizontal residue remains within the existing guard");
        assertFalse(SableOwnedBodyMotion.knownNoWorldFloor(owner,nativeMotion,
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(.2,-.001,.2),before,bounds,after,
                unchanged.worldClipObservation()));
        var supportedFloorProof=com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence.validateSupportedFloor(
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.2,0),
                com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO,
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,.2,0));
        assertTrue(supportedFloorProof.isPresent(),"existing native supported-floor geometry path remains accepted");
    }

    @Test void worldFloorContextUsesObservedFloorAndWorldFlagsNotSableAggregateSideFlag() {
        var owner=blankPlayer();
        var input=new net.minecraft.world.phys.Vec3(.1,-.426909648,.2);
        var output=new net.minecraft.world.phys.Vec3(.1,-.0443874444,.2);
        var before=new net.minecraft.world.phys.Vec3(4,300.0443874,6);
        var bounds=new net.minecraft.world.phys.AABB(3.7,300.0443874,5.7,4.3,301.8443874,6.3);
        var observation=CurrentFrameCollisionScope.withTrackedCharacterPose(owner,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(owner,input,before,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(owner,output);
            return null;
        }).worldClipObservation();
        var request=new com.juicyslew.moonstation14.ms14.movement.MovementVector(.1,-.426909648,.2);
        var measured=new com.juicyslew.moonstation14.ms14.movement.MovementVector(.1,-.0443874444,.2);
        assertTrue(SableOwnedBodyMotion.worldFloorClipContextAdmissible(owner,request,measured,input,
                true,true,true,false,observation,before,bounds,before.add(output)),
                "a certified world clip is admitted even when Sable's aggregate horizontal flag is false");
        assertFalse(SableOwnedBodyMotion.worldFloorClipContextAdmissible(owner,request,measured,input,
                false,true,true,false,observation,before,bounds,before.add(output)),"grounding must be genuine");
        assertFalse(SableOwnedBodyMotion.worldFloorClipContextAdmissible(owner,request,measured,input,
                true,true,true,true,observation,before,bounds,before.add(output)),"world-horizontal clipping remains rejected");
    }

    @Test void yawSamplesAreOrderedImmutableBoundedAndKeepNullTrackerTransitionsExplicit() {
        var owner = blankPlayer();
        var source = new dev.ryanhcode.sable.companion.math.Pose3d();
        var selected = new dev.ryanhcode.sable.companion.math.Pose3d();
        var result = CurrentFrameCollisionScope.withTrackedCharacterPose(owner, false, () -> {
            for (int i = 1; i <= 161; i++)
                CurrentFrameCollisionScope.recordYawSample(owner, null, i, 161, source, .25,
                        selected, .5);
            source.set(new dev.ryanhcode.sable.companion.math.Pose3d());
            return null;
        });
        assertEquals(160, result.yawSamples().size());
        assertFalse(result.yawSamplesComplete());
        assertEquals(1, result.yawSamples().getFirst().substep());
        assertEquals(160, result.yawSamples().getLast().substep());
        assertNull(result.yawSamples().getFirst().trackingFrameId());
        assertEquals(.25, result.yawSamples().getFirst().sdkSourceYaw());
        assertThrows(UnsupportedOperationException.class, () -> result.yawSamples().clear());
        double[] matrix = result.yawSamples().getFirst().sdkSourcePose().matrix();
        matrix[12] = 99;
        assertNotEquals(99, result.yawSamples().getFirst().sdkSourcePose().matrix()[12]);
        var swapped = CurrentFrameCollisionScope.withTrackedCharacterPose(owner, false, () -> {
            CurrentFrameCollisionScope.recordYawSample(owner, null, 1, 2, source, 0, selected, 1);
            CurrentFrameCollisionScope.recordYawSample(owner, null, 2, 2, source, 0, selected, 1);
            return null;
        });
        assertEquals(2, swapped.yawSamples().size());
        assertTrue(swapped.yawSamples().stream().allMatch(sample -> sample.trackingFrameId() == null));
    }

    @Test void diagnosticStockTraceDoesNotEnableCharacterGeometryOrYaw() {
        var owner = blankPlayer();
        var result = CurrentFrameCollisionScope.withDiagnosticStockTrace(owner, () -> {
            assertFalse(CurrentFrameCollisionScope.usesCurrentGeometry(owner));
            assertFalse(CurrentFrameCollisionScope.usesTrackedCharacterYaw(owner));
            select(owner, new Vector3d(0, 1, 0), new Vector3d(), 1);
            CurrentFrameCollisionScope.recordNativeCollisionApplied(owner, new Vector3d(0, .5, 0), new Vector3d(0, .5, 0));
            return null;
        });
        assertTrue(result.nativeCollisionTraceComplete());
        assertEquals(1, result.nativeCollisionTrace().size());
        assertFalse(CurrentFrameCollisionScope.usesCurrentGeometry(owner));
        assertFalse(CurrentFrameCollisionScope.usesTrackedCharacterYaw(owner));
        assertFalse(result.microCarryCutoffRetained());
    }

    @Test void capturesOnlyExactTrackedEntityAndSnapshotsNativeScratch() {
        var owner = blankPlayer();
        var other = blankPlayer();
        Vector3d selected = new Vector3d(1, 2, 3);
        Vector3d motion = new Vector3d(4, 5, 6);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(4, 5, 6);
        select(owner, selected, motion, -1);
        CurrentFrameCollisionScope.recordNativeCollisionApplied(owner, motion, selected);
        var result = CurrentFrameCollisionScope.withTrackedCharacterPose(owner, false, () -> {
            select(other, selected, motion, 1);
            CurrentFrameCollisionScope.recordNativeCollisionSelection(owner, FRAME, position,
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), selected, motion, 1, 2, 2);
            position.set(9, 9, 9);
            selected.set(20, 30, 40);
            motion.set(8, 9, 10);
            Vector3d applied = new Vector3d(0.5, 0.25, 0);
            motion.add(applied);
            CurrentFrameCollisionScope.recordNativeCollisionApplied(owner, motion, applied);
            applied.set(100);
            return null;
        });
        assertTrue(result.nativeCollisionTraceComplete());
        assertEquals(1, result.nativeCollisionTrace().size());
        var row = result.nativeCollisionTrace().getFirst();
        assertEquals(2, row.iteration());
        assertEquals(new BlockPos(4, 5, 6), row.selectedPos());
        assertEquals(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), row.selectedState());
        assertEquals(1, row.selectedX());
        assertEquals(4, row.collisionMotionBeforeX());
        assertEquals(.5, row.appliedX());
        assertEquals(4.5, row.responseX());
        assertThrows(UnsupportedOperationException.class,
                () -> result.nativeCollisionTrace().clear());
    }

    @Test void nestedScopesAreIsolatedAndTraceOverflowIsExplicit() {
        var owner = blankPlayer();
        var nested = blankPlayer();
        var result = CurrentFrameCollisionScope.withTrackedCharacterPose(owner, false, () -> {
            select(owner, new Vector3d(1, 0, 0), new Vector3d(), 0);
            var inner = CurrentFrameCollisionScope.withTrackedCharacterPose(nested, false, () -> {
                select(owner, new Vector3d(), new Vector3d(), 99);
                select(nested, new Vector3d(2, 0, 0), new Vector3d(), 1);
                CurrentFrameCollisionScope.recordNativeCollisionApplied(nested, new Vector3d(2, 0, 0), new Vector3d(2, 0, 0));
                return null;
            });
            assertEquals(1, inner.nativeCollisionTrace().size());
            CurrentFrameCollisionScope.recordNativeCollisionApplied(owner, new Vector3d(1, 0, 0), new Vector3d(1, 0, 0));
            for (int i = 0; i < 161; i++) {
                select(owner, new Vector3d(1, 0, 0), new Vector3d(), i);
                CurrentFrameCollisionScope.recordNativeCollisionApplied(owner, new Vector3d(1, 0, 0), new Vector3d(1, 0, 0));
            }
            return null;
        });
        assertEquals(160, result.nativeCollisionTrace().size());
        assertFalse(result.nativeCollisionTraceComplete());
    }

    @Test void floorTraceAdmissionRejectsIncompleteBadSumCeilingStateAndMixedFrameEvidence() {
        var pos = new BlockPos(2, 3, 4);
        var state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var good = trace(FRAME, pos, state, .001, .001, .001);
        var accepted = java.util.List.of(good);
        assertTrue(SableOwnedBodyMotion.nativeFloorTraceAdmissible(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.001,0),
                0, 0, 0, accepted, FRAME, row -> true));
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.001,0),
                0, .2, 0, accepted, FRAME, row -> true), "bad request-plus-response sum");
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.001,0),
                0, 0, 0, accepted, FRAME, row -> false), "unknown or replaced selected state");
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.001,0),
                0, 0, 0, java.util.List.of(), FRAME, row -> true), "incomplete trace has no usable rows");
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.001,0),
                0, 0, 0, java.util.List.of(trace(FRAME, pos, state, -1, .001, .001)), FRAME, row -> true), "ceiling MTV");
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.001,0),
                0, 0, 0, java.util.List.of(good, trace(UUID.randomUUID(), pos, state, 1, .001, .001)), FRAME, row -> true), "mixed frame");
    }

    @Test void floorContextAdmissionRejectsStaleUntrackedAndMixedNativeFrames() {
        assertTrue(SableOwnedBodyMotion.nativeFloorContextAdmissible(true, true, true, true,
                true, true, false, false, FRAME, FRAME, FRAME));
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(false, true, true, true,
                true, true, false, false, FRAME, FRAME, FRAME), "stale collision result");
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(true, true, true, false,
                true, true, false, false, FRAME, FRAME, FRAME), "incomplete trace");
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(true, true, true, true,
                true, true, false, false, FRAME, UUID.randomUUID(), FRAME), "actor crossed frames");
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(true, true, true, true,
                true, true, false, false, FRAME, FRAME, UUID.randomUUID()), "native collision frame mismatch");
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(true, true, true, true,
                true, true, true, false, FRAME, FRAME, FRAME), "horizontal collision");
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(true, true, true, true,
                true, true, false, true, FRAME, FRAME, FRAME), "sublevel horizontal collision");
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(true,true,true,true,
                true,true,false,false,null,null,FRAME),"WORLD start is not support without same-frame floor proof");
        assertTrue(SableOwnedBodyMotion.nativeFloorContextAdmissible(true,true,true,true,
                true,true,false,false,null,null,FRAME,true),"only separately verified first-boarding floor evidence admits WORLD->frame");
        assertFalse(SableOwnedBodyMotion.nativeFloorContextAdmissible(true,true,false,true,
                true,true,false,false,null,null,FRAME,true),"floor rows do not override final grounded state");
    }

    @Test void finalSupportAdmissionUsesLoadedSameFrameUpRowsWithoutOptionalGeometry() {
        var pos=new BlockPos(2,3,4);
        var stone=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var floor=trace(FRAME,pos,stone,.01,.01,.01); // diagnostic geometry intentionally absent
        assertNull(floor.geometry());
        assertTrue(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,FRAME,FRAME,
                java.util.List.of(floor),0,1,0,row->true),
                "grounded same-frame upward response and known loaded shape retain support without SAT snapshots");

        var sideOnly=new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME,1,1,0,pos,stone,"stone","stone",
                1,0,0,0,0,0,1,0,0,1,0,0,null,false);
        assertFalse(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,FRAME,FRAME,
                java.util.List.of(sideOnly),0,1,0,row->true),
                "world-grounded/below flags cannot turn a side row into frame support");
        assertFalse(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,FRAME,FRAME,
                java.util.List.of(floor),0,1,0,row->false),"unknown/unloaded selected shape fails closed");
        UUID frameA=UUID.randomUUID(),frameB=UUID.randomUUID();
        var floorA=trace(frameA,pos,stone,.01,.01,.01);
        var floorB=trace(frameB,pos,stone,.01,.01,.01);
        assertTrue(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,frameB,frameB,
                java.util.List.of(floorA,floorB),0,1,0,row->true),
                "A->B support selects final native B despite an earlier valid A floor row");
        assertFalse(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,frameB,frameB,
                java.util.List.of(floorA),0,1,0,row->true),
                "final native B cannot borrow A's earlier support row");
        assertFalse(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,frameA,null,
                java.util.List.of(floorA),0,1,0,row->true),
                "a prior A floor row cannot restore A after the final native reference became WORLD");
        assertTrue(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,frameB,frameB,
                java.util.List.of(floorB),0,1,0,row->true),
                "WORLD first boarding is admitted when final native B and its floor row agree");

        UUID tiltedFrame=UUID.randomUUID();
        double sx=-.02250249,sy=.02125611,sz=-1.0775e-5;
        var tiltedFloor=new CurrentFrameCollisionScope.NativeCollisionTrace(tiltedFrame,1,1,0,pos,stone,"stone","stone",
                sx,sy,sz,0,0,0,sx,sy,sz,sx,sy,sz,null,true);
        double upLength=Math.hypot(.74,.67),upX=-.74/upLength,upY=.67/upLength;
        assertTrue(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,tiltedFrame,tiltedFrame,
                java.util.List.of(tiltedFloor),upX,upY,0,row->true),
                "pinned SDK's .6867 world-up vertical floor row remains a raw-response support contact on tilted frame up");
        assertFalse(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,tiltedFrame,tiltedFrame,
                java.util.List.of(tiltedFloor),0,1,0,row->true),
                "the existing frame-up orientation filter still rejects a non-upward frame normal");
        assertFalse(SableOwnedBodyMotion.nativeSupportCandidateMatches(frameA,null),
                "world-floor-cleared live tracker cannot resurrect stale final-info A");
        assertFalse(SableOwnedBodyMotion.nativeSupportCandidateMatches(frameA,frameB),
                "final info A cannot override current live B");
        assertTrue(SableOwnedBodyMotion.nativeSupportCandidateMatches(frameB,frameB),
                "first boarding retains native B when both final references and its floor row agree");
        Object registeredA=new Object(),replacementAWithSameId=new Object();
        assertTrue(SableOwnedBodyMotion.nativeSupportCandidateInstancesMatch(registeredA,registeredA));
        assertFalse(SableOwnedBodyMotion.nativeSupportCandidateInstancesMatch(registeredA,replacementAWithSameId),
                "a removed/replaced frame object cannot be revived by UUID equality alone");

        double redirectY=.9,redirectX=Math.sqrt(1-redirectY*redirectY);
        var redirected=new CurrentFrameCollisionScope.NativeCollisionTrace(tiltedFrame,1,1,0,pos,stone,"stone","stone",
                redirectX,redirectY,0,0,0,0,0,1,0,0,1,0,null,true);
        assertTrue(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,tiltedFrame,tiltedFrame,
                java.util.List.of(redirected),redirectX,redirectY,0,row->true),
                "dot>.8 validates the pinned SDK's redirected-up response instead of raw MTV");
        var wrongResponse=new CurrentFrameCollisionScope.NativeCollisionTrace(tiltedFrame,1,1,0,pos,stone,"stone","stone",
                redirectX,redirectY,0,0,0,0,redirectX,redirectY,0,redirectX,redirectY,0,null,true);
        assertFalse(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(true,tiltedFrame,tiltedFrame,
                java.util.List.of(wrongResponse),redirectX,redirectY,0,row->true),
                "redirected native floor rows must preserve the exact SDK response channel");

        var earlierRedirectRaw=new CurrentFrameCollisionScope.NativeCollisionTrace(tiltedFrame,1,2,0,pos,stone,"stone","stone",
                .5,Math.sqrt(.75),0,0,0,0,.5,Math.sqrt(.75),0,.5,Math.sqrt(.75),0,null,true);
        var finalTiltedRaw=new CurrentFrameCollisionScope.NativeCollisionTrace(tiltedFrame,2,2,0,pos,stone,"stone","stone",
                sx,sy,sz,0,0,0,sx,sy,sz,sx,sy,sz,null,true);
        assertTrue(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(tiltedFrame,
                java.util.List.of(earlierRedirectRaw,finalTiltedRaw),row->true,
                row->row.substep()==1?new Vector3d(0,1,0):new Vector3d(upX,upY,0)),
                "each row uses its own exact substep pose; final tilted raw floor response is admitted");
        assertFalse(SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(tiltedFrame,
                java.util.List.of(earlierRedirectRaw,finalTiltedRaw),row->true,row->new Vector3d(0,1,0)),
                "reusing the first/world-up pose for the later tilted row rejects that later response");
        var unavailablePose=assertThrows(IllegalArgumentException.class,()->
                SableOwnedBodyMotion.nativeFloorRowsAdmitSupport(tiltedFrame,java.util.List.of(tiltedFloor),
                        row->true,row->null));
        assertEquals("collision resolution must be finite and bounded by requested displacement",unavailablePose.getMessage(),
                "a proved final-frame floor row with unavailable substep pose rejects instead of succeeding as WORLD");
        var world=new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,null);
        var bPoint=new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,frameB);
        assertThrows(IllegalArgumentException.class,()->SableOwnedBodyMotion.requireSupportedActorPoint(frameB,null),
                "known support with missing projection fails rather than reinterpreting as WORLD");
        assertThrows(IllegalArgumentException.class,()->SableOwnedBodyMotion.requireSupportedActorPoint(frameB,world),
                "known support with mismatched WORLD point fails closed");
        assertSame(bPoint,SableOwnedBodyMotion.requireSupportedActorPoint(frameB,bPoint));
        assertNull(SableOwnedBodyMotion.requireSupportedActorPoint(null,null),"explicit no-floor remains a null support result");
    }

    @Test void trackedSupportConsumerSynchronizesLiveTrackerBeforeFinalPointAndVelocityPublication() throws Exception {
        ClassNode mover;
        try(InputStream in=SableOwnedBodyMotion.class.getResourceAsStream("SableOwnedBodyMotion.class")) {
            assertNotNull(in,"compiled mover class bytes are available without initialization");
            mover=new ClassNode();new ClassReader(in).accept(mover,0);
        }
        MethodNode synchronize=mover.methods.stream().filter(m->m.name.equals("synchronizeFinalSupportReference"))
                .findFirst().orElseThrow();
        int trackerSet=findInvoke(synchronize,"sable$setTrackingSubLevel");
        int infoPublish=findFieldWrite(synchronize,"trackingSubLevel");
        int actorPoint=findInvoke(synchronize,"currentActorPoint");
        assertTrue(trackerSet>=0&&infoPublish>trackerSet&&actorPoint>infoPublish,
                "live tracker and CollisionInfo agree before the returned final ActorPoint is sampled");

        MethodNode move=mover.methods.stream().filter(m->m.name.equals("move")&&findInvoke(m,"synchronizeFinalSupportReference")>=0)
                .findFirst().orElseThrow();
        int selectSupport=findInvoke(move,"nativeFrameHasFinalFloorSupport");
        int synchronizeCall=findInvoke(move,"synchronizeFinalSupportReference");
        int frameDelta=findInvoke(move,"pointDeltaPerSecond");
        int anchorPublish=findInvoke(move,"observePhysicalMove");
        assertTrue(selectSupport>=0&&synchronizeCall>selectSupport&&frameDelta>synchronizeCall&&anchorPublish>synchronizeCall,
                "support selection precedes frame-delta and frame-anchor publication");
        java.nio.file.Path root=java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!java.nio.file.Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyMotion.java")))
            root=root.getParent();
        assertNotNull(root,"source tree is available for the scoped overload guard contract");
        String source=java.nio.file.Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyMotion.java"));
        assertTrue(source.contains("fresh && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER && movement != null"),
                "stock/legacy movers do not enter the tracker synchronization policy");
        assertTrue(source.contains("afterPoint=synchronizeFinalSupportReference(movement,body,level,info,supportFrame)"));
        assertTrue(source.contains("SableFrameVelocity.pointDeltaPerSecond(level, velocityBasis, afterPoint)"),
                "the frame delta uses the same final point returned in MoveResult");
    }

    private static int findInvoke(MethodNode method,String name) {
        int index=0;
        for(AbstractInsnNode insn:method.instructions) {
            if(insn instanceof MethodInsnNode call&&call.name.equals(name))return index;
            index++;
        }
        return -1;
    }
    private static int findFieldWrite(MethodNode method,String name) {
        int index=0;
        for(AbstractInsnNode insn:method.instructions) {
            if(insn instanceof org.objectweb.asm.tree.FieldInsnNode field&&field.name.equals(name)
                    &&field.getOpcode()==org.objectweb.asm.Opcodes.PUTFIELD)return index;
            index++;
        }
        return -1;
    }

    @Test void floorTraceUsesNormalizedMtvForRedirectAndChecksAccumulatedEvidence() {
        var pos = new BlockPos(2, 3, 4);
        var state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var request = new com.juicyslew.moonstation14.ms14.movement.MovementVector(0, -.001, 0);
        var upwardRedirect = rawTrace(pos, state, 0, .00098, 0, 0, .00098, 0, 0, .00098, 0);
        assertTrue(SableOwnedBodyMotion.nativeFloorTraceAdmissible(request, 0, -.00002, 0,
                java.util.List.of(upwardRedirect), FRAME, row -> true));

        var oblique = rawTrace(pos, state, .0024, .003196, 0, .0024, .003196, 0, .0024, .003196, 0);
        assertTrue(SableOwnedBodyMotion.nativeFloorTraceAdmissible(request, .0024, .002196, 0,
                java.util.List.of(oblique), FRAME, row -> true), "normal Y=.799 retains original MTV");
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(request, 0, 0, 0,
                java.util.List.of(rawTrace(pos, state, .0024, .003196, 0, 0, .004, 0, 0, .004, 0)), FRAME, row -> true),
                "normalized-up MTV cannot use the unredirected vector");

        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(request, 0, .0001, 0,
                java.util.List.of(upwardRedirect), FRAME, row -> true), "native sum mismatch");
        var repeated = new java.util.ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        for (int i = 0; i < 3; i++) repeated.add(rawTrace(pos, state, 0, .00098, 0, 0, .00098, 0, 0, .0009796, 0));
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(request, 0, -.0000612, 0,
                repeated, FRAME, row -> true), "many sub-epsilon discrepancies exceed global tolerance");
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace rawTrace(BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, double sx, double sy, double sz,
            double ax, double ay, double az, double rx, double ry, double rz) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, 1, 1, 1, pos, state,
                pos.toString(), state.toString(), sx, sy, sz, 0, -.001, 0,
                ax, ay, az, rx, ry, rz, null, true);
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace trace(UUID frame, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, double normalY, double appliedY, double responseY) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 1, 1, 1, pos, state,
                pos.toString(), state.toString(), 0, normalY, 0, 0, -.001, 0,
                0, appliedY, 0, 0, responseY, 0, null, true);
    }

    private static void select(ServerPlayer entity, Vector3d selected, Vector3d motion, int iteration) {
        CurrentFrameCollisionScope.recordNativeCollisionSelection(entity, FRAME, BlockPos.ZERO,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), selected, motion, 1, 2, iteration);
    }

    @SuppressWarnings("unchecked")
    private static ServerPlayer blankPlayer() {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field field = unsafeType.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);
            return (ServerPlayer) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, ServerPlayer.class);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not allocate inert identity-only entity test fixture", exception);
        }
    }
}
