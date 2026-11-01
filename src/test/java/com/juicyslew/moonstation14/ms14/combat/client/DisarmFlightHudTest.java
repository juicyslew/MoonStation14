package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisarmFlightHudTest {
    @Test void bearingTracksActualCameraProjectionIncludingPitchAndDepthFallback() {
        assertBearing(new net.minecraft.world.phys.Vec3(0, 0, 1), 0, 0);
        assertBearing(new net.minecraft.world.phys.Vec3(0, 0, -1), 0, 0);
        assertBearing(new net.minecraft.world.phys.Vec3(-1, 0, 0), 0, 0);
        assertBearing(new net.minecraft.world.phys.Vec3(1, 0, 0), 0, 0);
        assertBearing(new net.minecraft.world.phys.Vec3(0, 1, 0), 0, 0);
        assertBearing(new net.minecraft.world.phys.Vec3(0, -1, 0), 0, 0);
        assertBearing(new net.minecraft.world.phys.Vec3(0, 0, 1), 0, 60);
        assertBearing(new net.minecraft.world.phys.Vec3(0, 0, 1), 0, -60);
        assertBearing(new net.minecraft.world.phys.Vec3(0, 0, 1), 0, 90);
        assertBearing(new net.minecraft.world.phys.Vec3(0, 0, -1), 0, -90);
        assertBearing(new net.minecraft.world.phys.Vec3(-1, 2, 1), 45, 60);
        assertBearing(new net.minecraft.world.phys.Vec3(0, 0, 0), 0, 0);
        var rotation = camera(25, 15);
        var fv = new org.joml.Vector3f(0, 0, -1).rotate(rotation);
        var forward = new net.minecraft.world.phys.Vec3(fv.x, fv.y, fv.z);
        assertEquals(0, DisarmFlightHud.bearing(forward, rotation), 1e-8);
        assertEquals(Math.PI, DisarmFlightHud.bearing(forward.scale(-1), rotation), 1e-8);
    }

    private static org.joml.Quaternionf camera(float yaw, float pitch) {
        return new org.joml.Quaternionf().rotationYXZ((float)Math.toRadians(180-yaw), (float)Math.toRadians(-pitch), 0);
    }

    private static void assertBearing(net.minecraft.world.phys.Vec3 velocity, float yaw, float pitch) {
        var rotation = camera(yaw, pitch);
        // Independent MC view-vector oracle; camera forward is Entity#getViewVector.
        var f = net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw);
        var worldUp = new net.minecraft.world.phys.Vec3(0, 1, 0);
        var right = f.cross(worldUp).normalize();
        if (right.lengthSqr() < 1e-8) {
            var fallback = new org.joml.Vector3f(1, 0, 0).rotate(rotation);
            right = new net.minecraft.world.phys.Vec3(fallback.x, fallback.y, fallback.z);
        }
        var up = right.cross(f).normalize();
        double x = velocity.x * right.x + velocity.y * right.y + velocity.z * right.z;
        double y = velocity.x * up.x + velocity.y * up.y + velocity.z * up.z;
        double depth = velocity.x * f.x + velocity.y * f.y + velocity.z * f.z;
        double expected = x*x + y*y < 1e-8 ? (depth >= 0 ? 0 : Math.PI) : Math.atan2(x, y);
        double actual = DisarmFlightHud.bearing(velocity, rotation);
        assertEquals(Math.cos(expected), Math.cos(actual), 1e-6);
        assertEquals(Math.sin(expected), Math.sin(actual), 1e-6);
    }

    @Test void renderAdmissionRequiresWorldParticipantVisibleHudAndOwnedBody() {
        assertTrue(DisarmFlightHud.admitted(true, true, false, true));
        assertFalse(DisarmFlightHud.admitted(false, true, false, true));
        assertFalse(DisarmFlightHud.admitted(true, false, false, true));
        assertFalse(DisarmFlightHud.admitted(true, true, true, true));
        assertFalse(DisarmFlightHud.admitted(true, true, false, false));
    }

    @Test void arrowArmsShareOutwardTipAndExtendBehindIt() {
        for (float angle : new float[]{0, (float)Math.PI / 2, (float)Math.PI, (float)-Math.PI / 2}) {
            float[][] arms = DisarmFlightHud.arrowArms(angle, 40);
            assertEquals(arms[0][0], arms[1][0], 1e-5);
            assertEquals(arms[0][1], arms[1][1], 1e-5);
            float ux = (float)Math.sin(angle), uy = (float)-Math.cos(angle);
            assertTrue((arms[0][2] - arms[0][0]) * ux + (arms[0][3] - arms[0][1]) * uy < 0);
            assertTrue((arms[1][2] - arms[1][0]) * ux + (arms[1][3] - arms[1][1]) * uy < 0);
        }
    }

    @Test void actorCueIsSmallerButGetsBoundedHudArrowAndVictimCueStaysFullSize() {
        var origin=new net.minecraft.world.phys.Vec3(0,0,0);
        var velocity=new net.minecraft.world.phys.Vec3(1,0,0);
        var actor=new DisarmFlightVisuals.Flight(origin,velocity,0,true,true);
        var victim=new DisarmFlightVisuals.Flight(origin,velocity,0,true,false);
        assertEquals(.68f,DisarmFlightHud.cueScale(actor));
        assertEquals(1f,DisarmFlightHud.cueScale(victim));
        var rotation = camera(0, 0);
        assertEquals(DisarmFlightHud.bearing(actor,rotation),DisarmFlightHud.bearing(victim,rotation),1e-9);
        assertEquals(-Math.PI/2,DisarmFlightHud.bearing(actor,rotation),1e-9);
        assertEquals(0,DisarmFlightHud.bearing(new DisarmFlightVisuals.Flight(origin,new net.minecraft.world.phys.Vec3(0,1,0),0,true,true),rotation));
        float[][] glyph=DisarmFlightHud.arrowArms((float)DisarmFlightHud.bearing(actor,rotation),22);
        assertTrue(Math.hypot(glyph[0][0],glyph[0][1])<=22.001);
        assertTrue(Math.hypot(glyph[0][2],glyph[0][3])<=27.001);
    }
}
