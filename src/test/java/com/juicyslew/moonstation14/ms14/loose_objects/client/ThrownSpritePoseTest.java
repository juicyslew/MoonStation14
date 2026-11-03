package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrownSpritePoseTest {
    @Test void authoredAxesMapToThreeDimensionalTravel() {
        for (float axis : new float[]{45f, 315f}) {
            Vector3f target = new Vector3f(1, 2, 3).normalize();
            Vector3f source = new Vector3f((float)Math.cos(Math.toRadians(axis)),
                    (float)Math.sin(Math.toRadians(axis)), 0);
            var rotation = ThrownSpritePose.rotation(axis, target);
            assertNotNull(rotation);
            assertTrue(rotation.transform(source).distance(target) < 1e-5f);
        }
    }

    @Test void invalidVelocityHasNoOrientation() {
        assertNull(ThrownSpritePose.rotation(45, new Vector3f()));
        assertNull(ThrownSpritePose.rotation(45, new Vector3f(Float.NaN, 0, 1)));
    }
}
