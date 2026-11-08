package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisarmFlightRendererTest {
    @Test void terrainClipContextUsesEntityIndependentCollisionContext() {
        var context = DisarmFlightRenderer.terrainClipContext(Vec3.ZERO, new Vec3(2.0, 0, 0));
        assertNotNull(context);
        assertEquals(Vec3.ZERO, context.getFrom());
        assertEquals(new Vec3(2.0, 0, 0), context.getTo());
    }
    @Test void geometryUsesVelocityNotCameraForTipAndKeepsWingsBehindTip() {
        Vec3 origin = new Vec3(2, 3, 4), velocity = new Vec3(0, 2, 0);
        var a = DisarmFlightRenderer.geometry(origin, velocity, new Vec3(10, 0, 4));
        var b = DisarmFlightRenderer.geometry(origin, velocity, new Vec3(-20, 5, 40));
        Vec3 expectedTip = origin.add(velocity.normalize().scale(2.0));
        assertEquals(expectedTip, a.get(5));
        assertEquals(expectedTip, b.get(5));
        Vec3 centerlineA = a.get(0).lerp(a.get(1), .5), centerlineB = b.get(0).lerp(b.get(1), .5);
        assertEquals(origin, centerlineA); assertEquals(origin, centerlineB);
        assertEquals(a.get(5).subtract(centerlineA), b.get(5).subtract(centerlineB));
        assertTrue(a.stream().allMatch(p -> Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)));
        assertTrue(a.get(4).subtract(origin).dot(velocity) < a.get(5).subtract(origin).dot(velocity));
        assertTrue(a.get(6).subtract(origin).dot(velocity) < a.get(5).subtract(origin).dot(velocity));
    }
    @Test void zeroVelocityIsEmptyAndAllAxisOrientationsAreFinite() {
        assertTrue(DisarmFlightRenderer.geometry(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO).isEmpty());
        for (Vec3 velocity : new Vec3[]{new Vec3(1,0,0), new Vec3(0,1,0), new Vec3(0,0,1), new Vec3(1,1,1)})
            assertTrue(DisarmFlightRenderer.geometry(Vec3.ZERO, velocity, velocity).stream()
                    .allMatch(p -> Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)));
    }
    @Test void clippedLengthsBoundEveryVertexAndKeepTipOnVelocityAxis() {
        Vec3 origin = new Vec3(1, -2, 3);
        for (double length : new double[]{.1, .01, 2.0}) {
            for (Vec3 velocity : new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(1, 1, -1)}) {
                var points = DisarmFlightRenderer.geometry(origin, velocity, new Vec3(8, 4, -3), length);
                Vec3 axis = velocity.normalize();
                assertEquals(origin.add(axis.scale(length)), points.get(5));
                for (Vec3 point : points) {
                    double station = point.subtract(origin).dot(axis);
                    assertTrue(station >= -1e-9 && station <= length + 1e-9, "station=" + station + " length=" + length);
                }
            }
        }
        Vec3 xAxis = new Vec3(1, 0, 0);
        assertTrue(DisarmFlightRenderer.geometry(origin, xAxis, Vec3.ZERO, 0).isEmpty());
        assertEquals(DisarmFlightRenderer.geometry(origin, xAxis, Vec3.ZERO),
                 DisarmFlightRenderer.geometry(origin, xAxis, Vec3.ZERO, 2.0));
    }
}
