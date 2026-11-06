package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShuttleFreightFlightProfileTest {
    @Test void profileExcursionEnvelopeIncludesEdgesAndRejectsNonfiniteNativePoses() {
        var origin=new org.joml.Vector3d(100,200,-40);
        assertTrue(ShuttleFreightFlightBounds.within(origin,new org.joml.Vector3d(164,208,-40)));
        assertTrue(ShuttleFreightFlightBounds.within(origin,new org.joml.Vector3d(100,192,-104)));
        assertFalse(ShuttleFreightFlightBounds.within(origin,new org.joml.Vector3d(164.001,200,-40)));
        assertFalse(ShuttleFreightFlightBounds.within(origin,new org.joml.Vector3d(100,208.001,-40)));
        assertFalse(ShuttleFreightFlightBounds.within(origin,new org.joml.Vector3d(Double.NaN,200,-40)));
        assertFalse(ShuttleFreightFlightBounds.within(origin,null));
    }

    @Test void deterministicFiniteBoundedContinuousSweep() {
        var prior=ShuttleFreightFlightProfile.target(0,2400);
        double maxSpeed=0, maxYaw=0;
        for(int i=1;i<=2400*6;i++) {
            double t=i/(20.0*6);
            var next=ShuttleFreightFlightProfile.target(t,2400);
            var repeat=ShuttleFreightFlightProfile.target(t,2400);
            assertEquals(next,repeat);
            double dt=1.0/120;
            double accel=Math.sqrt(sq(next.targetX()-prior.targetX())+sq(next.targetY()-prior.targetY())+sq(next.targetZ()-prior.targetZ()))/dt;
            assertTrue(accel < .4, "profile velocity discontinuity at "+t+": "+accel);
            assertTrue(Math.abs(next.targetYawRate()-prior.targetYawRate())/dt < .03);
            maxSpeed=Math.max(maxSpeed,Math.sqrt(sq(next.targetX())+sq(next.targetY())+sq(next.targetZ())));
            maxYaw=Math.max(maxYaw,Math.abs(next.targetYawRate())); prior=next;
        }
        assertTrue(maxSpeed<2.0); assertTrue(maxYaw<.09);
        var end=ShuttleFreightFlightProfile.target(120,2400);
        assertEquals(0,end.targetX(),1e-9); assertEquals(0,end.targetYawRate(),1e-9);
    }
    private static double sq(double x){return x*x;}
}
