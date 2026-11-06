package com.juicyslew.moonstation14.ms14.character;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AngularRecoveryModelTest {
    private static final AngularRecoveryModel.Vec3 UP = new AngularRecoveryModel.Vec3(0, 1, 0);
    private static final AngularRecoveryModel.Quat ID = new AngularRecoveryModel.Quat(1, 0, 0, 0);
    private static AngularRecoveryModel.State tick(AngularRecoveryModel.State s, double dt) { return AngularRecoveryModel.step(s, UP, dt, .65, 1); }

    @Test void validatesFiniteInputsAndUsesComponentDefaults() {
        var c = com.juicyslew.moonstation14.ms14.character.components.AngularRecoveryComponent.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"type\":\"AngularRecovery\"}")).getOrThrow();
        assertEquals(.65, c.graceSeconds());
        assertThrows(IllegalArgumentException.class, () -> new AngularRecoveryModel.Quat(Double.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> AngularRecoveryModel.step(AngularRecoveryModel.departure(ID, UP), new AngularRecoveryModel.Vec3(0,0,0), .1, .65, 1));
        assertThrows(IllegalArgumentException.class, () -> AngularRecoveryModel.step(AngularRecoveryModel.departure(ID, UP), UP, Double.NaN, .65, 1));
    }

    @Test void yawRemainsConstantThroughGraceAndUsesIntegratedEnvelope() {
        var s = AngularRecoveryModel.departure(ID, new AngularRecoveryModel.Vec3(0, 3, 0));
        for (int i=0; i<13; i++) s=tick(s,.05);
        assertEquals(3, s.worldAngularVelocity().y(), 1e-12);
        assertEquals(3*.65, yaw(s.orientation()), 1e-10);
        for(int i=0;i<20;i++) s=tick(s,.05);
        assertEquals(0, s.worldAngularVelocity().length(), 0);
        double expectedYaw = 3*(.65+.5);
        assertEquals(Math.cos(expectedYaw), s.orientation().rotate(new AngularRecoveryModel.Vec3(0,0,1)).z(), 1e-9);
        assertEquals(Math.sin(expectedYaw), s.orientation().rotate(new AngularRecoveryModel.Vec3(0,0,1)).x(), 1e-9);
        assertEquals(UP.y(), s.orientation().rotate(UP).y(), 1e-9);
    }

    private static double yaw(AngularRecoveryModel.Quat q) {
        var v=q.rotate(new AngularRecoveryModel.Vec3(0,0,1)); return Math.atan2(v.x(),v.z());
    }

    @Test void afterMoveAdvancesLandingIntervalOnceAndDoesNotAdvanceGroundedState() {
        var omega = new AngularRecoveryModel.Vec3(0, 3, 0);
        var departure = AngularRecoveryModel.afterMove(null, true, false, ID, omega, UP, .05, .65, 1).nextState();
        assertEquals(3, departure.worldAngularVelocity().y(), 0);
        var flight = AngularRecoveryModel.afterMove(departure, false, false, ID, omega, UP, .05, .65, 1).nextState();
        var landing = AngularRecoveryModel.afterMove(flight, false, true, ID, omega, UP, .05, .65, 1);
        assertEquals(.1, landing.impactState().airborneSeconds(), 1e-12);
        assertEquals(3, landing.impactState().worldAngularVelocity().y(), 0);
        assertEquals(0, landing.nextState().worldAngularVelocity().length(), 0);
        assertEquals(yaw(landing.impactState().orientation()), yaw(landing.nextState().orientation()), 1e-9);
        assertSame(landing.nextState(), AngularRecoveryModel.afterMove(landing.nextState(), true, true,
                ID, omega, UP, .05, .65, 1).nextState());
    }

    @Test void recoveryIsIndependentOfTimestepAndPreservesTiltDuringGrace() {
        var start=AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(1,0,0),.8),new AngularRecoveryModel.Vec3(0,2,1));
        var a=start; for(int i=0;i<33;i++) a=tick(a,.05);
        var b=start; for(int i=0;i<165;i++) b=tick(b,.01);
        assertEquals(0,a.worldAngularVelocity().length(),0);
        assertEquals(a.orientation().rotate(UP).x(),b.orientation().rotate(UP).x(),1e-8);
        var grace=AngularRecoveryModel.departure(start.orientation(),new AngularRecoveryModel.Vec3(0,0,0));
        for(int i=0;i<13;i++) grace=tick(grace,.05);
        assertEquals(start.orientation().rotate(UP).x(),grace.orientation().rotate(UP).x(),1e-10);
    }

    @Test void invertedBodyRecoversDeterministicallyAndLaterGravityChangesAreSmooth() {
        var s=AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(1,0,0),Math.PI),new AngularRecoveryModel.Vec3(0,0,0));
        for(int i=0;i<33;i++) s=tick(s,.05);
        assertEquals(0,s.worldAngularVelocity().length(),0);
        assertTrue(s.orientation().rotate(UP).y()>.999999);
        var changed=AngularRecoveryModel.step(s,new AngularRecoveryModel.Vec3(1,0,0),.01,.65,1);
        var before=s.orientation().rotate(UP); var after=changed.orientation().rotate(UP);
        assertTrue(Math.sqrt(Math.pow(after.x()-before.x(),2)+Math.pow(after.y()-before.y(),2)+Math.pow(after.z()-before.z(),2))<.04);
        assertEquals(0,changed.worldAngularVelocity().length(),0);
        assertTrue(changed.recoveryActive());
    }

    @Test void landingIgnoresYawAndUsesStrictOffAxisThreshold() {
        var yaw=AngularRecoveryModel.Quat.fromAxisAngle(UP,2);
        assertFalse(AngularRecoveryModel.landing(yaw,new AngularRecoveryModel.Vec3(0,3,0),UP,45,1).bad());
        assertFalse(AngularRecoveryModel.landing(ID,new AngularRecoveryModel.Vec3(1,0,0),UP,45,1).bad());
        assertTrue(AngularRecoveryModel.landing(ID,new AngularRecoveryModel.Vec3(1.01,0,0),UP,45,1).bad());
        assertTrue(AngularRecoveryModel.landing(AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(1,0,0),1),UP,UP,45,1).bad());
    }

    @Test void landedRemovesTiltAndSpinWithoutDiscardingHeading() {
        var heading = AngularRecoveryModel.Quat.fromAxisAngle(UP, 1.2);
        var tilted = AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(1,0,0), .7).multiply(heading);
        var landed = AngularRecoveryModel.landed(new AngularRecoveryModel.State(tilted,
                new AngularRecoveryModel.Vec3(2,3,4), 1.1, true), UP);
        assertEquals(0, landed.worldAngularVelocity().length(), 0);
        assertEquals(0, landed.airborneSeconds(), 0);
        assertFalse(landed.recoveryActive());
        assertEquals(UP.x(), landed.orientation().rotate(UP).x(), 1e-10);
        assertEquals(UP.y(), landed.orientation().rotate(UP).y(), 1e-10);
        assertEquals(UP.z(), landed.orientation().rotate(UP).z(), 1e-10);
        assertEquals(Math.atan2(heading.rotate(new AngularRecoveryModel.Vec3(0,0,1)).x(),
                heading.rotate(new AngularRecoveryModel.Vec3(0,0,1)).z()),
                Math.atan2(landed.orientation().rotate(new AngularRecoveryModel.Vec3(0,0,1)).x(),
                        landed.orientation().rotate(new AngularRecoveryModel.Vec3(0,0,1)).z()), 1e-9);
    }
}
