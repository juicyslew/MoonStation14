package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeIdleSupportProbeCapturedTraceTest {
    private static final MovementVector REQUEST = new MovementVector(0,-.001,0);
    private static final MovementVector NATIVE = new MovementVector(-.0036279771586692246,
            .0026618433468801107,-1.6328211113529987e-6);

    private List<CurrentFrameCollisionScope.NativeCollisionTrace> trace() {
        return R34NativeTraceFixture.rows().stream().map(R34NativeTraceFixture.Row::trace).toList();
    }

    private java.util.Optional<NativeSideContactProof.Result> prove(MovementVector request, MovementVector nativeMotion,
            UUID frame, List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,
            java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> known) {
        return NativeSideContactProof.validateIdleSupportProbe(request,nativeMotion,nativeMotion,frame,rows,known);
    }

    @Test void capturedR34Tick57IsAdmittedAsIdleLocalFloorProbe() {
        var fixtures=R34NativeTraceFixture.rows();
        double[] firstPose=fixtures.get(0).geometry().subLevelPose().matrix();
        double[] lastPose=fixtures.get(fixtures.size()-1).geometry().subLevelPose().matrix();
        assertTrue(maxDifference(firstPose,lastPose)>1e-6,"native pose legitimately evolves across substeps");
        var sameStepIterations=fixtures.stream().filter(row->row.substep()==4).toList();
        assertEquals(2,sameStepIterations.size());
        assertTrue(maxDifference(sameStepIterations.get(0).geometry().subLevelPose().matrix(),
                sameStepIterations.get(1).geometry().subLevelPose().matrix())<=1e-6,
                "iterations within one substep share the same captured pose");
        var result = prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,trace(),ignored->true).orElseThrow();
        assertEquals(MovementVector.ZERO,result.boundedOwn());
        assertEquals(NATIVE,result.geometryCorrection());
        assertNull(result.horizontalContact());
    }

    @Test void tinyResidualRowStillMustShareTheLocalFloorTopPlane() {
        var rows=trace();
        int tinyIndex=4; // ordinal 5: substep 4 iteration 1, near-zero response
        var source=rows.get(tinyIndex);
        assertEquals(4,source.substep());
        assertTrue(Math.hypot(Math.hypot(source.selectedX(),source.selectedY()),source.selectedZ())<1e-12);
        var g=source.geometry(); var b=g.selectedStorageBounds();
        var lowerTop=new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY-.05,b.maxZ);
        var changed=new NativeCollisionGeometry(g.entityBoundsCenter(),g.entityBoundsDimensions(),g.entityBoundsOrientation(),
                g.subLevelPose(),lowerTop,g.maxMtv(),g.collisionMotion(),g.iteration(),g.substeps());
        MovementVector replay=new NativeSideCollisionWitness().separation(changed);
        assertNotNull(replay);
        assertTrue(length(replay)<1e-12,"changed bounds preserve a SAT-consistent zero MTV for the residual row");
        var mutated=new ArrayList<>(rows);
        mutated.set(tinyIndex,withGeometry(source,changed));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,mutated,ignored->true).isEmpty(),
                "even near-zero rows must match the common selected floor top plane");
    }

    @Test void differentPosesWithinOneCollisionSubstepAreRejected() {
        var rows=trace();
        int repeatedStepRow=4; // second record of substep 4
        var source=rows.get(repeatedStepRow); var g=source.geometry();
        double[] matrix=g.subLevelPose().matrix();
        double shift=1e-4; matrix[12]+=shift;
        var center=g.entityBoundsCenter();
        var shiftedCenter=new MovementVector(center.x()+shift,center.y(),center.z());
        var changed=new NativeCollisionGeometry(shiftedCenter,g.entityBoundsDimensions(),g.entityBoundsOrientation(),
                new NativeFrameInterval.PoseValue(matrix),g.selectedStorageBounds(),g.maxMtv(),g.collisionMotion(),g.iteration(),g.substeps());
        assertEquals(source.selectedX(),new NativeSideCollisionWitness().separation(changed).x(),1e-6,
                "coherence mutation retains the captured MTV; both bodies move together");
        var mutated=new ArrayList<>(rows); mutated.set(repeatedStepRow,withGeometry(source,changed));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,mutated,ignored->true).isEmpty(),
                "pose must be coherent within a substep");
    }

    @Test void capturedProbeProofRejectsIncompleteMismatchedOrUnexplainedEvidence() {
        var rows=trace();
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,rows,ignored->false).isEmpty(),"unknown state");
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,List.of(),ignored->true).isEmpty(),"missing trace");
        assertTrue(prove(REQUEST,NATIVE,UUID.randomUUID(),rows,ignored->true).isEmpty(),"wrong frame");
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,rows.subList(0,rows.size()-1),ignored->true).isEmpty(),"missing tail");
        assertTrue(prove(REQUEST,new MovementVector(NATIVE.x()+.00001,NATIVE.y(),NATIVE.z()),
                R34NativeTraceFixture.FRAME,rows,ignored->true).isEmpty(),"measured/native mismatch");
        assertTrue(prove(new MovementVector(.001,-.001,0),NATIVE,R34NativeTraceFixture.FRAME,rows,ignored->true).isEmpty(),"horizontal request");
        assertTrue(prove(new MovementVector(0,.001,0),NATIVE,R34NativeTraceFixture.FRAME,rows,ignored->true).isEmpty(),"upward request");
        var wrongFrame=new ArrayList<>(rows);
        wrongFrame.set(0,copy(rows.get(0),UUID.randomUUID(),null,null,null,null,false));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,wrongFrame,ignored->true).isEmpty());
        var missingGeometry=new ArrayList<>(rows);
        missingGeometry.set(0,copy(rows.get(0),null,null,null,null,null,true));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,missingGeometry,ignored->true).isEmpty(),"missing geometry");
        var alteredSelected=new ArrayList<>(rows);
        alteredSelected.set(0,copy(rows.get(0),null,rows.get(0).selectedX()+.001,null,null,null,false));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,alteredSelected,ignored->true).isEmpty(),"altered MTV");
        var alteredApplied=new ArrayList<>(rows);
        alteredApplied.set(0,copy(rows.get(0),null,null,rows.get(0).appliedX()+.001,null,null,false));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,alteredApplied,ignored->true).isEmpty(),"altered applied");
        var alteredBefore=new ArrayList<>(rows);
        alteredBefore.set(0,copy(rows.get(0),null,null,null,null,new MovementVector(0,0,0),false));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,alteredBefore,ignored->true).isEmpty(),"altered motion-before");
        var altered=new ArrayList<>(rows);
        altered.set(0,copy(rows.get(0),null,null,null,new MovementVector(.01,0,0),null,false));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,altered,ignored->true).isEmpty(),"altered response accounting");
        var reordered=new ArrayList<>(rows); java.util.Collections.swap(reordered,0,1);
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,reordered,ignored->true).isEmpty(),"out-of-order rows");
        var duplicate=new ArrayList<>(rows); duplicate.set(1,rows.get(0));
        assertTrue(prove(REQUEST,NATIVE,R34NativeTraceFixture.FRAME,duplicate,ignored->true).isEmpty(),"duplicate metadata");
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace copy(
            CurrentFrameCollisionScope.NativeCollisionTrace row, UUID frame, Double selectedX, Double appliedX,
            MovementVector response, MovementVector before, boolean dropGeometry) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame==null?row.frameId():frame,
                row.substep(),row.substeps(),row.iteration(),row.selectedPos(),row.selectedState(),row.block(),row.blockState(),
                selectedX==null?row.selectedX():selectedX,row.selectedY(),row.selectedZ(),
                before==null?row.collisionMotionBeforeX():before.x(),before==null?row.collisionMotionBeforeY():before.y(),
                before==null?row.collisionMotionBeforeZ():before.z(), appliedX==null?row.appliedX():appliedX,row.appliedY(),row.appliedZ(),
                response==null?row.responseX():response.x(),response==null?row.responseY():response.y(),response==null?row.responseZ():response.z(),
                dropGeometry?null:row.geometry(),row.verticalCollision());
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace withGeometry(
            CurrentFrameCollisionScope.NativeCollisionTrace row, NativeCollisionGeometry geometry) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(row.frameId(),row.substep(),row.substeps(),row.iteration(),
                row.selectedPos(),row.selectedState(),row.block(),row.blockState(),row.selectedX(),row.selectedY(),row.selectedZ(),
                row.collisionMotionBeforeX(),row.collisionMotionBeforeY(),row.collisionMotionBeforeZ(),row.appliedX(),row.appliedY(),row.appliedZ(),
                row.responseX(),row.responseY(),row.responseZ(),geometry,row.verticalCollision());
    }

    private static double maxDifference(double[] a,double[] b) {
        double result=0; for(int i=0;i<a.length;i++)result=Math.max(result,Math.abs(a[i]-b[i])); return result;
    }

    private static double length(MovementVector v) { return Math.hypot(Math.hypot(v.x(),v.y()),v.z()); }
}
