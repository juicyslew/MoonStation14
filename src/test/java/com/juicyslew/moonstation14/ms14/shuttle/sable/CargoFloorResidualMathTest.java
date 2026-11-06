package com.juicyslew.moonstation14.ms14.shuttle.sable;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CargoFloorResidualMathTest {
    private static boolean matches(Vector3d selected, Vector3d applied, Vector3d deckUp,
                                   int iteration, double observed) {
        if (iteration != 3 || selected.lengthSquared() == 0) return false;
        if (new Vector3d(selected).normalize().dot(deckUp) < 1 - 1.0e-6) return false;
        double predicted = new Vector3d(selected).sub(applied).dot(deckUp);
        return Double.isFinite(predicted) && predicted >= 0 && Math.abs(observed - predicted) <= 1.0e-6;
    }

    @Test void smallTiltedLastMtvExplainsOnlyItsPlaneRemainder() {
        Vector3d selected = new Vector3d(5.47736106e-6, 1.24231842e-5, 1.8952644e-9);
        Vector3d up = new Vector3d(selected).normalize();
        Vector3d applied = new Vector3d(0, 1.357707606e-5, 0);
        double residual = new Vector3d(selected).sub(applied).dot(up);
        assertTrue(matches(new Vector3d(selected), applied, up, 3, residual));
        assertFalse(matches(new Vector3d(selected), applied, up, 2, residual));
        assertFalse(matches(new Vector3d(selected), applied, new Vector3d(1, 0, 0), 3, residual));
        assertFalse(matches(new Vector3d(selected), applied, up, 3, residual + .00001));
    }
}
