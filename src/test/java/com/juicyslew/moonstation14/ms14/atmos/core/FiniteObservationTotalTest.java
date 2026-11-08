package com.juicyslew.moonstation14.ms14.atmos.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FiniteObservationTotalTest {
    @Test void addsAndSaturatesSignedTotalsWithoutNonFiniteResults() {
        assertEquals(5, FiniteObservationTotal.add(2, 3));
        assertEquals(-5, FiniteObservationTotal.add(-2, -3));
        assertEquals(0, FiniteObservationTotal.add(0, 0));
        assertEquals(Double.MAX_VALUE, FiniteObservationTotal.add(Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(-Double.MAX_VALUE, FiniteObservationTotal.add(-Double.MAX_VALUE, -Double.MAX_VALUE));
    }
}
