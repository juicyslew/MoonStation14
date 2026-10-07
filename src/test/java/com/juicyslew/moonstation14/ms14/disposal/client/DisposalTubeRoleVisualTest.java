package com.juicyslew.moonstation14.ms14.disposal.client;

import com.juicyslew.moonstation14.ms14.disposal.domain.*;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DisposalTubeRoleVisualTest {
    private static DisposalEntityData.Tube tube(DisposalEntityData.Kind kind, DisposalEntityData.Topology topology) {
        return new DisposalEntityData.Tube(kind,topology,0,.1,0,"",Set.of("go"));
    }
    @Test void preferredMarkMatchesRoutingAcrossSixAxisFramesAndQuarterTurns() {
        var cases=List.of(
                tube(DisposalEntityData.Kind.THREE_WAY,DisposalEntityData.Topology.THREE_WAY),
                tube(DisposalEntityData.Kind.THREE_WAY_FLIPPED,DisposalEntityData.Topology.THREE_WAY_FLIPPED),
                tube(DisposalEntityData.Kind.Y_JUNCTION,DisposalEntityData.Topology.Y_JUNCTION),
                tube(DisposalEntityData.Kind.X_JUNCTION,DisposalEntityData.Topology.X_JUNCTION));
        for(var tube:cases) for(Direction forward:Direction.values()) for(Direction up:Direction.values()) if(forward.getAxis()!=up.getAxis())
            for(int turn=0;turn<4;turn++) {
                var t=new DisposalEntityData.Tube(tube.kind(),tube.topology(),turn,.1,0,"",Set.of());
                var frame=new DisposalOrientation(forward,up);
                var preferred=DisposalTubeRoleVisual.preferred(t,frame);
                var connections=DisposalTubeRouting.worldConnections(t,frame);
                for(var incoming:connections) if(incoming!=preferred)
                    assertEquals(preferred,DisposalTubeRouting.selectNextDirection(t,frame,incoming,Set.of(),bound->0));
                var randomOutput=DisposalTubeRouting.selectNextDirection(t,frame,null,Set.of(),bound->0);
                assertTrue(connections.contains(randomOutput));assertNotEquals(preferred,randomOutput);
                var enteredForward=DisposalTubeRouting.selectNextDirection(t,frame,preferred,Set.of(),bound->0);
                assertTrue(connections.contains(enteredForward));assertNotEquals(preferred,enteredForward);
                var output=DisposalTubeRoleVisual.preferred(t,frame).toMC();double radius=.875/2;
                for(var quad:DisposalTubeRoleVisual.markings(t,frame,.875).get(0).mesh()) for(var vertex:new DisposalMeshGeometry.Vertex[]{quad.a(),quad.b(),quad.c(),quad.d()}) {
                    double ax=vertex.x()-.5,ay=vertex.y()-.5,az=vertex.z()-.5;
                    double axial=ax*output.getStepX()+ay*output.getStepY()+az*output.getStepZ();
                    assertTrue(axial>=.29&&axial<=.47,"chevrons must sit along the selected output arm, not the opposite arm");
                    double rx=ax-axial*output.getStepX(),ry=ay-axial*output.getStepY(),rz=az-axial*output.getStepZ();
                    assertTrue(Math.sqrt(rx*rx+ry*ry+rz*rz)>radius,"role vertices must clear the barrel surface");
                    assertTrue(vertex.nx()*rx+vertex.ny()*ry+vertex.nz()*rz>0,"role normals must face outward");
                }
            }
    }
    @Test void routerHasSeparateGoldDefaultAndBlueTaggedBranchAndFlipUsesItsOwnOrder() {
        for(var topology:new DisposalEntityData.Topology[]{DisposalEntityData.Topology.THREE_WAY,DisposalEntityData.Topology.THREE_WAY_FLIPPED}) {
            var t=tube(DisposalEntityData.Kind.ROUTER,topology);var frame=DisposalOrientation.identity();var marks=DisposalTubeRoleVisual.markings(t,frame,.875);
            assertEquals(2,marks.size());assertEquals(255,marks.get(0).red());assertEquals(48,marks.get(1).red());
            var taggedBranch=DisposalDirection.fromMC(frame.world(topology.directions().get(1).rotate(t.rotationQuarters())));
            assertEquals(taggedBranch,DisposalTubeRouting.selectNextDirection(t,frame,DisposalTubeRoleVisual.preferred(t,frame),Set.of("go"),bound->0));
            assertEquals(DisposalTubeRoleVisual.preferred(t,frame),DisposalTubeRouting.selectNextDirection(t,frame,null,Set.of(),bound->0));
            assertEquals(DisposalTubeRoleVisual.preferred(t,frame),DisposalTubeRouting.selectNextDirection(t,frame,DisposalTubeRoleVisual.preferred(t,frame),Set.of("other"),bound->0));
            assertNotEquals(DisposalTubeRoleVisual.preferred(t,frame),taggedBranch);
        }
    }
    @Test void bidirectionalStraightAndBendHaveNoPreferredArrowAndTaggerOnlyHasTagMark() {
        for(var kind:new DisposalEntityData.Kind[]{DisposalEntityData.Kind.STRAIGHT,DisposalEntityData.Kind.BEND}) {
            var topology=kind==DisposalEntityData.Kind.STRAIGHT?DisposalEntityData.Topology.STRAIGHT:DisposalEntityData.Topology.BEND;
            assertTrue(DisposalTubeRoleVisual.markings(tube(kind,topology),DisposalOrientation.identity(),.875).isEmpty());
        }
        var marks=DisposalTubeRoleVisual.markings(tube(DisposalEntityData.Kind.TAGGER,DisposalEntityData.Topology.STRAIGHT),DisposalOrientation.identity(),.875);
        assertEquals(1,marks.size());assertEquals(48,marks.get(0).red());
    }
    @Test void taggerCollarsAndTrunkCouplingAreNondegenerateRaisedBandsForEveryAxis() {
        var tagger=tube(DisposalEntityData.Kind.TAGGER,DisposalEntityData.Topology.STRAIGHT);
        for(Direction axis:Direction.values()) {
            Direction up=axis.getAxis()==Direction.Axis.Y?Direction.NORTH:Direction.UP;
            var frame=new DisposalOrientation(axis,up);
            var mark=DisposalTubeRoleVisual.markings(tagger,frame,.875).get(0);
            assertRaisedBand(mark,.875,DisposalTubeRoleVisual.preferred(tagger,frame).toMC());
        }
        var trunk=tube(DisposalEntityData.Kind.TRUNK,DisposalEntityData.Topology.TRUNK);
        assertRaisedBand(DisposalTubeRoleVisual.markings(trunk,DisposalOrientation.identity(),.875).get(0),.875,Direction.UP);
    }
    private static void assertRaisedBand(DisposalTubeRoleVisual.Marking marking,double diameter,Direction axis) {
        assertFalse(marking.mesh().isEmpty());double minimumArea=1e-7;
        for(var q:marking.mesh()) {
            var a=q.a();var b=q.b();var c=q.c();
            double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z(),vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
            double cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;
            assertTrue(cx*cx+cy*cy+cz*cz>minimumArea,"collar quads must enclose visible area");
            assertTrue(cx*a.nx()+cy*a.ny()+cz*a.nz()>0,"collar quad winding must face outward");
            for(var vertex:new DisposalMeshGeometry.Vertex[]{q.a(),q.b(),q.c(),q.d()}) {
                double x=vertex.x()-.5,y=vertex.y()-.5,z=vertex.z()-.5;double along=x*axis.getStepX()+y*axis.getStepY()+z*axis.getStepZ();
                double rx=x-along*axis.getStepX(),ry=y-along*axis.getStepY(),rz=z-along*axis.getStepZ();
                assertTrue(Math.sqrt(rx*rx+ry*ry+rz*rz)>diameter*.5,"collar must clear barrel");
                assertTrue(vertex.nx()*rx+vertex.ny()*ry+vertex.nz()*rz>0,"collar normals must face out");
            }
        }
    }
}
