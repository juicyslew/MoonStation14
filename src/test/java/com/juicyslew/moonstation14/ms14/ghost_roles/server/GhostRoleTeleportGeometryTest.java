package com.juicyslew.moonstation14.ms14.ghost_roles.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostRoleTeleportGeometryTest {
    @Test
    void placesBehindTargetForCardinalFacingsAndLooksAtItsEyePosition() {
        double[][] facings = {{0, 0, -2}, {90, 2, 0}, {180, 0, 2}, {-90, -2, 0}};
        for (double[] facing : facings) {
            var result = GhostRoleTeleportGeometry.compute(4, 10, 7, (float) facing[0], 11.6, 1.5);
            assertEquals(4 + facing[1], result.position().x, 1.0e-6);
            assertEquals(11, result.position().y, 1.0e-6);
            assertEquals(7 + facing[2], result.position().z, 1.0e-6);
            double eyeY = result.position().y + 1.5;
            double dx = 4 - result.position().x;
            double dy = 11.6 - eyeY;
            double dz = 7 - result.position().z;
            assertEquals(Math.toDegrees(Math.atan2(-dx, dz)), result.yaw(), 1.0e-4);
            assertEquals(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), result.pitch(), 1.0e-4);
        }
    }
}
