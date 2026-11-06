package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Captured ordered projection sequence only; rows intentionally have no SAT geometry and are not a SAT proof. */
class ActiveZeroWallOrderedProjectionRegressionTest {
    private static final MovementVector REQUEST=new MovementVector(0,-.07840000104027735,.15625);
    private static final UUID FRAME=UUID.fromString("7aaee3a7-57a9-45eb-bd99-6a0c1d605a36");
    private static final double[][] SELECTED={
        {8.264123249885001e-8,0,-.01562870204566913},
        {2.4861501001250205e-12,3.390408160993939e-11,-4.7015919212633984e-7},
        {8.395256535851815e-8,0,-.015622946961004336},
        {5.673468009494449e-12,7.762709991053792e-11,-1.0557687317899953e-6},
        {8.53106463030178e-8,1.1710036334366927e-6,-.015625609603375116},
        {8.665284119309088e-8,1.193110795854365e-6,-.01562563177972372},
        {2.5218276239556723e-18,3.472269024301972e-17,-4.5474734955382874e-13},
        {8.799504076544696e-8,1.2152180210367763e-6,-.015625653956950425},
        {8.93372450099203e-8,1.237325308843682e-6,-.015625676133236243},
        {9.067945393419317e-8,1.2594326593811135e-6,-.01562569830994542},
        {5.278003005538913e-18,7.330535279040582e-17,-9.094946988034048e-13},
        {9.202166752767013e-8,1.2815400725017502e-6,-.015625720485258954},
        {9.33638858144534e-8,1.3036475485411884e-6,-.015625742663269583},
        {9.470610876492774e-8,1.325755087086533e-6,-.01562576483897508}
    };
    private static final int[] STEPS={1,1,2,2,3,4,4,5,6,7,7,8,9,10};
    private static final int[] ITERS={0,1,0,1,0,0,1,0,0,0,1,0,0,0};

    private static List<CurrentFrameCollisionScope.NativeCollisionTrace> projectionRows(){
        var rows=new ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        for(int i=0;i<SELECTED.length;i++){
            var v=SELECTED[i];
            rows.add(new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME,STEPS[i],10,ITERS[i],new BlockPos(0,0,0),
                    Blocks.IRON_BLOCK.defaultBlockState(),"captured:steel_wall","captured:steel_wall",
                    v[0],v[1],v[2],0,0,0,v[0],v[1],v[2],v[0],v[1],v[2],null,false));
        }
        return List.copyOf(rows);
    }

    @Test void capturedDescendingSequenceCertifiesTinyXZIntermediateOnlyInOrderedContext(){
        var rows=projectionRows();
        var selected=rows.stream().map(r->new MovementVector(r.selectedX(),r.selectedY(),r.selectedZ())).toList();
        var replay=NativeSideContactProof.replayOwnResponsesAt(REQUEST,10,rows,selected);
        assertEquals(14,replay.projections().size());
        var candidate=NativeCollisionSlop.accepted(REQUEST,REQUEST.add(replay.aggregate()));
        var second=replay.projections().get(1);
        assertEquals(REQUEST.y()/10d,second.input().y(),0d);
        assertTrue(Math.hypot(second.input().x(),second.input().z())<NativeCollisionSlop.MAX_RESIDUAL,
                "the intermediate plane input has sub-slop XZ despite real descending 3D approach");
        var first=replay.projections().getFirst();
        var expectedPrefix=new MovementVector(REQUEST.x()/10d+first.clipDelta().x(),REQUEST.y()/10d,
                REQUEST.z()/10d+first.clipDelta().z());
        assertEquals(expectedPrefix,second.input(),"ordered prefix equals prior replay output (4-ULP contract)");
        assertEquals(second.input().x()+second.clipDelta().x(),second.output().x(),0d);
        assertEquals(second.input().y(),second.output().y(),0d);
        assertEquals(second.input().z()+second.clipDelta().z(),second.output().z(),0d);
        double normalLength=Math.sqrt(second.normalX()*second.normalX()+second.normalY()*second.normalY()+second.normalZ()*second.normalZ());
        assertEquals(1d,normalLength,1e-12);
        double dot=second.input().x()*second.normalX()+second.input().y()*second.normalY()+second.input().z()*second.normalZ();
        assertTrue(dot<0,"the actual second plane is approached in full 3D");
        double horizontalNormalSquared=second.normalX()*second.normalX()+second.normalZ()*second.normalZ();
        assertTrue(horizontalNormalSquared>=second.normalY()*second.normalY());
        double lambda=(second.clipDelta().x()*second.normalX()+second.clipDelta().z()*second.normalZ())/horizontalNormalSquared;
        assertTrue(lambda>=0 && lambda<=-dot+NativeCollisionSlop.MAX_RESIDUAL);
        assertTrue(Math.abs(second.clipDelta().x()-lambda*second.normalX())<=NativeCollisionSlop.MAX_RESIDUAL);
        assertTrue(Math.abs(second.clipDelta().z()-lambda*second.normalZ())<=NativeCollisionSlop.MAX_RESIDUAL);
        assertEquals(new MovementVector(0,REQUEST.y(),-4.970660590131182e-6),candidate);
        assertTrue(HorizontalContactEvidence.validate(second.input(),second.output(),second.normalX(),second.normalY(),second.normalZ()).isEmpty(),
                "public single-request API keeps its tiny-horizontal-request rejection");
        var certificate=HorizontalContactEvidence.validateOrdered(REQUEST,candidate,10,replay.projections()).orElseThrow();
        assertTrue(certificate.matches(REQUEST,candidate));
        assertEquals(2d,certificate.projectVelocityTangent(new MovementVector(0,2,0)).y(),0d,
                "ordered planes preserve velocity Y");
        assertTrue(HorizontalContactEvidence.validateOrdered(REQUEST,new MovementVector(candidate.x(),candidate.y(),candidate.z()+1e-5),
                10,replay.projections()).isEmpty(),"changed final XZ is rejected");
        var changedY=new ArrayList<>(replay.projections());
        changedY.set(1,new HorizontalContactEvidence.Projection(second.substep(),second.input(),
                new MovementVector(second.output().x(),Math.nextUp(second.output().y()),second.output().z()),second.clipDelta(),
                second.normalX(),second.normalY(),second.normalZ()));
        assertTrue(HorizontalContactEvidence.validateOrdered(REQUEST,candidate,10,changedY).isEmpty(),"projection Y cannot be changed");
        var wrongOrder=new ArrayList<>(replay.projections());
        java.util.Collections.swap(wrongOrder,0,1);
        assertTrue(HorizontalContactEvidence.validateOrdered(REQUEST,candidate,10,wrongOrder).isEmpty(),"wrong projection order/prefix rejected");
        var badPrefix=new ArrayList<>(replay.projections());
        var fiveUp=second.input().x(); for(int i=0;i<5;i++) fiveUp=Math.nextUp(fiveUp);
        badPrefix.set(1,new HorizontalContactEvidence.Projection(second.substep(),new MovementVector(fiveUp,second.input().y(),second.input().z()),
                second.output(),second.clipDelta(),second.normalX(),second.normalY(),second.normalZ()));
        assertTrue(HorizontalContactEvidence.validateOrdered(REQUEST,candidate,10,badPrefix).isEmpty(),"prefix outside four ULP rejected");
        var nonApproaching=new ArrayList<>(replay.projections());
        nonApproaching.set(1,new HorizontalContactEvidence.Projection(second.substep(),second.input(),second.output(),second.clipDelta(),
                second.normalX(),-second.normalY(),second.normalZ()));
        assertTrue(HorizontalContactEvidence.validateOrdered(REQUEST,candidate,10,nonApproaching).isEmpty(),"wrong signed normal rejected");
        assertTrue(HorizontalContactEvidence.validateOrdered(new MovementVector(0,-.1,0),new MovementVector(1e-4,-.1,0),1,
                List.of(new HorizontalContactEvidence.Projection(1,new MovementVector(0,-.1,0),new MovementVector(1e-4,-.1,0),
                        new MovementVector(1e-4,0,0),.8,-.6,0))).isEmpty(),"positive normal dot is nonapproaching and rejected");
        assertTrue(HorizontalContactEvidence.validateOrdered(new MovementVector(0,-.1,0),new MovementVector(.04,-.1,0),1,
                List.of(new HorizontalContactEvidence.Projection(1,new MovementVector(0,-.1,0),new MovementVector(.04,-.1,0),
                        new MovementVector(.04,0,0),.8,.6,0))).isPresent(),"negative full-3D dot permits real tiny-XZ side approach");
        assertTrue(HorizontalContactEvidence.validateOrdered(new MovementVector(0,0,0),new MovementVector(1e-4,0,0),1,
                List.of(new HorizontalContactEvidence.Projection(1,MovementVector.ZERO,new MovementVector(1e-4,0,0),
                        new MovementVector(1e-4,0,0),1,0,0))).isEmpty(),"stationary full 3D input cannot authorize movement");
    }
}
