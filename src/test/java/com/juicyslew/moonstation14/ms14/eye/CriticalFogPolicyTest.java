package com.juicyslew.moonstation14.ms14.eye;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CriticalFogPolicyTest {
    @Test void interpolatesResolvedThresholdsAndClearsOutsideCritical() {
        assertEquals(Float.POSITIVE_INFINITY, CriticalFogPolicy.radius(99, 100, 200));
        assertEquals(16, CriticalFogPolicy.radius(100, 100, 200), .001);
        assertEquals(8.75, CriticalFogPolicy.radius(150, 100, 200), .001);
        assertEquals(1.5, CriticalFogPolicy.radius(200, 100, 200), .001);
        assertEquals(8.75, CriticalFogPolicy.radius(100, 90, 110), .001);
        assertTrue(CriticalFogPolicy.radius(125, 100, 200) > CriticalFogPolicy.radius(175, 100, 200));
        assertEquals(1.5, CriticalFogPolicy.radius(200, 100, 200), .001);
        assertEquals(16, CriticalFogPolicy.radius(10, 10, 20), .001);
        assertEquals(1.5, CriticalFogPolicy.radius(20, 10, 20), .001);
    }

    @Test void boundsOffCenterCameraAndPreservesStricterEnvironmentFog() {
        assertEquals(20, CriticalFogPolicy.cameraFarPlane(8, 12), .001);
        assertEquals(24, CriticalFogPolicy.cameraFarPlane(20, 100), .001);
        assertEquals(new CriticalFogPolicy.FogPlanes(5.25f, 7), CriticalFogPolicy.combine(120, 160, 7));
        assertEquals(new CriticalFogPolicy.FogPlanes(-8, 2), CriticalFogPolicy.combine(-8, 2, 7));
        assertEquals(new CriticalFogPolicy.FogPlanes(.75f, 1), CriticalFogPolicy.combine(1, 2, 1));
        assertEquals(new CriticalFogPolicy.FogPlanes(1, 2), CriticalFogPolicy.combine(1, 2, 7));
        assertNull(CriticalFogPolicy.combine(0, 0, 7));
    }
}
