package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwoBoneArmSolveTest {
    private static double dist(GunArmAimMath.Vec a, GunArmAimMath.Vec b) { return a.sub(b).length(); }
    @Test void preservesBothLinkLengthsAndBends() {
        var s=new GunArmAimMath.Vec(0,2,0); var h=new GunArmAimMath.Vec(2,1,0);
        var r=TwoBoneArmSolve.solve(s,h,2,2,new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(0,1,0),0);
        assertTrue(r.reached()); assertEquals(2,dist(s,r.elbow()),1e-8); assertEquals(2,dist(r.elbow(),r.hand()),1e-8);
        assertTrue(dist(r.elbow(),s.add(h).scale(.5))>0.5);
    }
    @Test void selectsExactNonTangentFloorIntersectionAndPreferredMirroredSide() {
        var r=TwoBoneArmSolve.solve(new GunArmAimMath.Vec(0,2,0),new GunArmAimMath.Vec(2,2,0),2,2,
                new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(0,1,0),1.5);
        var mirror=TwoBoneArmSolve.solve(new GunArmAimMath.Vec(0,2,0),new GunArmAimMath.Vec(2,2,0),2,2,
                new GunArmAimMath.Vec(0,0,-1),new GunArmAimMath.Vec(0,1,0),1.5);
        assertTrue(r.floorContact()); assertEquals(1.5,r.elbow().y(),1e-7);
        assertEquals(2,dist(new GunArmAimMath.Vec(0,2,0),r.elbow()),1e-7);
        assertEquals(2,dist(r.elbow(),r.hand()),1e-7);
        assertTrue(r.elbow().z()>0); assertTrue(mirror.elbow().z()<0);
    }
    @Test void mirroredShouldersProduceMirroredSolutions() {
        var a=TwoBoneArmSolve.solve(new GunArmAimMath.Vec(1,2,0),new GunArmAimMath.Vec(0,2,0),2,2,
                new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(0,1,0),0);
        var b=TwoBoneArmSolve.solve(new GunArmAimMath.Vec(-1,2,0),new GunArmAimMath.Vec(0,2,0),2,2,
                new GunArmAimMath.Vec(0,0,-1),new GunArmAimMath.Vec(0,1,0),0);
        assertEquals(a.elbow().x(),-b.elbow().x(),1e-8); assertEquals(a.elbow().y(),b.elbow().y(),1e-8);
        assertEquals(a.elbow().z(),-b.elbow().z(),1e-8);
    }
    @Test void unreachableAndDegenerateInputsRemainFiniteAndClamp() {
        var r=TwoBoneArmSolve.solve(new GunArmAimMath.Vec(0,0,0),new GunArmAimMath.Vec(100,0,0),2,1,
                new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(0,1,0),-100);
        assertFalse(r.reached()); assertEquals(3,r.hand().length(),1e-7);
        assertTrue(Double.isFinite(r.elbow().x()+r.elbow().y()+r.elbow().z()));
        var z=TwoBoneArmSolve.solve(new GunArmAimMath.Vec(0,0,0),new GunArmAimMath.Vec(0,0,0),2,2,null,null,0);
        assertTrue(Double.isFinite(z.elbow().x()+z.elbow().y()+z.elbow().z()));
    }
}
