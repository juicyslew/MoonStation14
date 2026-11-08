package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunArmAimMathTest {
    private static GunArmAimMath.Vec rotate(GunArmAimMath.Rotation a, GunArmAimMath.Vec v) {
        double cx=Math.cos(a.x()),sx=Math.sin(a.x()),cy=Math.cos(a.y()),sy=Math.sin(a.y()),cz=Math.cos(a.z()),sz=Math.sin(a.z());
        double x=v.x(),y=v.y(),z=v.z();
        double x1=x, y1=cx*y-sx*z,z1=sx*y+cx*z;
        double x2=cy*x1+sy*z1,y2=y1,z2=-sy*x1+cy*z1;
        return new GunArmAimMath.Vec(cz*x2-sz*y2,sz*x2+cz*y2,z2);
    }
    private static void aligned(GunArmAimMath.Vec bore,GunArmAimMath.Vec up,GunArmAimMath.Vec look) {
        var r=GunArmAimMath.aim(bore,up,look); var out=rotate(r,bore).normalized();
        var want=look.normalized(); assertEquals(want.x(),out.x(),1e-5); assertEquals(want.y(),out.y(),1e-5); assertEquals(want.z(),out.z(),1e-5);
        var actualUp=rotate(r,up).normalized();
        var desiredUp=new GunArmAimMath.Vec(0,-1,0);
        var projected=desiredUp.add(want.scale(-desiredUp.dot(want)));
        if(projected.length()>1e-5) { projected=projected.normalized(); assertEquals(projected.dot(actualUp),1,1e-5); }
    }
    @Test void alignsEitherHandingAndNoncanonicalModelAxes() {
        aligned(new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(.4,.2,.89));
        aligned(new GunArmAimMath.Vec(0,-1,0),new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(-.3,.5,.8));
        aligned(new GunArmAimMath.Vec(1,0,0),new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(0,1,0));
        aligned(new GunArmAimMath.Vec(0,0,1),new GunArmAimMath.Vec(0,1,0),new GunArmAimMath.Vec(0,-1,0));
    }
}
