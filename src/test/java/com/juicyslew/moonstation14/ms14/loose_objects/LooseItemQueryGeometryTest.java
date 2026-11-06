package com.juicyslew.moonstation14.ms14.loose_objects;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LooseItemQueryGeometryTest {
    @Test void identityAndInsideOriginSegmentClip() {
        var shape = new LooseItemQueryGeometry(2, 3, Vec3.ZERO, new Quaterniond());
        assertEquals(new Vec3(0, 0, 0), shape.clip(Vec3.ZERO, new Vec3(0, -2, 0)));
        assertEquals(-1, shape.worldEnvelope().minX);
        assertEquals(3, shape.worldEnvelope().maxY);
    }

    @Test void rotatedShapeRejectsOldBoxFalsePositiveAndFindsNearestPoint() {
        var shape = new LooseItemQueryGeometry(2, 1, Vec3.ZERO,
                new Quaterniond().rotationY(Math.PI / 4));
        assertNull(shape.clip(new Vec3(0, .5, 2), new Vec3(0, .5, 3)));
        assertNotNull(shape.clip(new Vec3(1.2, .5, 0), new Vec3(1.2, .5, 0)), "hit lies outside old axis-aligned box");
        Vec3 nearest = shape.nearestPoint(new Vec3(0, .5, 4));
        assertEquals(0, nearest.x, 1e-9);
        assertEquals(.5, nearest.y, 1e-9);
        assertEquals(Math.sqrt(2), nearest.z, 1e-9);
        var envelope = shape.worldEnvelope();
        Vec3 envelopeOnly = new Vec3(envelope.maxX - .01, .5, envelope.maxZ - .01);
        assertTrue(envelope.contains(envelopeOnly));
        assertNull(shape.clip(envelopeOnly, envelopeOnly));
    }

    @Test void pitchedRolledYawedLocalSpinUsesCombinedBasisForEnvelopeAndNearestPoint() {
        Quaterniond basis = new Quaterniond().rotationYXZ(.7, -.4, .6);
        Quaterniond composed = new Quaterniond(basis).mul(new Quaterniond().rotationY(.8)).normalize();
        var shape = new LooseItemQueryGeometry(2, 3, new Vec3(1, 2, 3), composed);
        var expectedCorner = new Vec3(1, 2, 3).add(composed.transform(new org.joml.Vector3d(1,3,1)).x,
                composed.transform(new org.joml.Vector3d(1,3,1)).y, composed.transform(new org.joml.Vector3d(1,3,1)).z);
        var envelope = shape.worldEnvelope();
        assertTrue(expectedCorner.x >= envelope.minX-1e-9 && expectedCorner.x <= envelope.maxX+1e-9
                && expectedCorner.y >= envelope.minY-1e-9 && expectedCorner.y <= envelope.maxY+1e-9
                && expectedCorner.z >= envelope.minZ-1e-9 && expectedCorner.z <= envelope.maxZ+1e-9);
        Vec3 outside = new Vec3(1,2,3).add(composed.transform(new org.joml.Vector3d(4, 1, 0)).x,
                composed.transform(new org.joml.Vector3d(4, 1, 0)).y, composed.transform(new org.joml.Vector3d(4, 1, 0)).z);
        Vec3 nearest = shape.nearestPoint(outside);
        assertEquals(0, nearest.distanceTo(new Vec3(1,2,3).add(composed.transform(new org.joml.Vector3d(1,1,0)).x,
                composed.transform(new org.joml.Vector3d(1,1,0)).y, composed.transform(new org.joml.Vector3d(1,1,0)).z)), 1e-9);
        assertEquals(3, outside.distanceTo(nearest), 1e-9);
    }

    @Test void translatedShapesAndInputQuaternionAreIsolated() {
        Quaterniond input = new Quaterniond().rotationYXZ(.4, .2, -.3);
        var shape = new LooseItemQueryGeometry(1, 2, Vec3.ZERO, input);
        Quaterniond expected = shape.orientation();
        Quaterniond captured = shape.orientation();
        input.identity(); captured.identity();
        var moved = shape.translated(new Vec3(4, 5, 6));
        assertEquals(new Vec3(4, 5, 6), moved.pivot());
        assertTrue(expected.equals(shape.orientation(), 1e-12));
        assertTrue(expected.equals(moved.orientation(), 1e-12));
        moved.orientation().identity();
        assertTrue(expected.equals(moved.orientation(), 1e-12));
        assertThrows(IllegalArgumentException.class,
                () -> new LooseItemQueryGeometry(Double.NaN, 1, Vec3.ZERO, new Quaterniond()));
        assertThrows(IllegalArgumentException.class,
                () -> new LooseItemQueryGeometry(1, 1, Vec3.ZERO, new Quaterniond(0,0,0,0)));
        assertThrows(IllegalArgumentException.class,
                () -> new LooseItemQueryGeometry(1, 1, Vec3.ZERO, new Quaterniond(Double.MAX_VALUE,0,0,1)));
        assertThrows(IllegalArgumentException.class, () -> moved.translated(new Vec3(Double.NaN,0,0)));
        assertNull(moved.nearestPoint(new Vec3(Double.NaN,0,0)));
    }

    @Test void scaledQuaternionIsNormalized() {
        var shape = new LooseItemQueryGeometry(1, 1, Vec3.ZERO,
                new Quaterniond().rotationY(.6).mul(3));
        Quaterniond normalized = shape.orientation();
        assertEquals(1, Math.sqrt(normalized.x*normalized.x + normalized.y*normalized.y
                + normalized.z*normalized.z + normalized.w*normalized.w), 1e-12);
    }

    @Test void orientedInflationAndProjectionPreservePhysicalShape() {
        double margin = .2;
        Vec3 pivot = new Vec3(3, 4, 5);
        var identity = new LooseItemQueryGeometry(.4, .8, pivot, new Quaterniond());
        var expanded = identity.inflated(margin);
        assertEquals(new net.minecraft.world.phys.AABB(2.6, 3.8, 4.6, 3.4, 5.0, 5.4), expanded.worldEnvelope());
        assertSame(identity, identity.inflated(0));
        assertThrows(IllegalArgumentException.class, () -> identity.inflated(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> identity.inflated(-.1));

        Quaterniond rotation = new Quaterniond().rotationX(.7).mul(new Quaterniond().rotationZ(.45)).normalize();
        var tilted = new LooseItemQueryGeometry(.4, .8, pivot, rotation);
        var tiltedInflated = tilted.inflated(margin);
        Vector3d localShift = rotation.transform(new Vector3d(0, -margin, 0));
        assertEquals(pivot.add(localShift.x, localShift.y, localShift.z), tiltedInflated.pivot());
        assertEquals(.8 + 2 * margin, tiltedInflated.localBounds().maxY, 1e-12);

        var rolled = new LooseItemQueryGeometry(2, 1, Vec3.ZERO, new Quaterniond().rotationZ(Math.PI / 2));
        assertEquals(-1, rolled.minimumProjection(Vec3.ZERO, new Vec3(1, 0, 0)), 1e-9);
        assertNotNull(rolled.clip(Vec3.ZERO, Vec3.ZERO));
        Vec3 marginOnly = new Vec3(0, -1.1, 0);
        assertNull(rolled.clip(marginOnly, marginOnly));
        assertNotNull(rolled.inflated(.2).clip(marginOnly, marginOnly));
    }

    @Test void rotatedProfileClipsAtPhysicalBoundaryAndNotBeyondIt() {
        var axis = new LooseItemQueryGeometry(.5, .75, Vec3.ZERO, new Quaterniond());
        assertNotNull(axis.clip(new Vec3(.25, .2, 0), new Vec3(.25, .2, 0)), "boundary contact remains included");
        assertNull(axis.clip(new Vec3(.251, .2, 0), new Vec3(.251, .2, 0)));
        var rotated = new LooseItemQueryGeometry(.5, .75, Vec3.ZERO,
                new Quaterniond().rotationY(Math.PI / 4));
        assertNotNull(rotated.clip(Vec3.ZERO, Vec3.ZERO));
        assertNull(rotated.clip(new Vec3(.4, .2, -.4), new Vec3(.4, .2, -.4)),
                "rotated world-envelope corner is outside the physical profile");
    }
}
