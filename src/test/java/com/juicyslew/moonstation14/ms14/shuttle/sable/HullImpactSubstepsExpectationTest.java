package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HullImpactSubstepsExpectationTest {
    @Test void absentExpectationAllowsAndReportsStockSdkValueWithoutAssumingDefault() {
        assertNull(HullImpactSubstepsExpectation.parseExpected(null));
        assertDoesNotThrow(()->HullImpactSubstepsExpectation.requireExpected(2,null));
    }

    @Test void explicitTenIsAcceptedOnlyWhenSdkActuallyReportsTen() {
        assertEquals(10,HullImpactSubstepsExpectation.parseExpected("10"));
        assertDoesNotThrow(()->HullImpactSubstepsExpectation.requireExpected(10,"10"));
        var mismatch=assertThrows(IllegalStateException.class,
                ()->HullImpactSubstepsExpectation.requireExpected(2,"10"));
        assertTrue(mismatch.getMessage().contains("did not match expected 10"));
    }

    @Test void malformedAndOutOfRangeExpectationsFailClosed() {
        assertThrows(IllegalArgumentException.class,()->HullImpactSubstepsExpectation.parseExpected("ten"));
        assertThrows(IllegalArgumentException.class,()->HullImpactSubstepsExpectation.parseExpected("0"));
        assertThrows(IllegalArgumentException.class,()->HullImpactSubstepsExpectation.parseExpected("11"));
        assertThrows(IllegalStateException.class,()->HullImpactSubstepsExpectation.requireExpected(0,null));
        assertThrows(IllegalStateException.class,()->HullImpactSubstepsExpectation.requireExpected(11,null));
    }

    @Test void explicitSixIsAcceptedOnlyWhenSdkActuallyReportsSix() {
        assertEquals(6,HullImpactSubstepsExpectation.parseExpected("6"));
        assertDoesNotThrow(()->HullImpactSubstepsExpectation.requireExpected(6,"6"));
        var mismatch2=assertThrows(IllegalStateException.class,
                ()->HullImpactSubstepsExpectation.requireExpected(2,"6"));
        assertTrue(mismatch2.getMessage().contains("did not match expected 6"));
        var mismatch10=assertThrows(IllegalStateException.class,
                ()->HullImpactSubstepsExpectation.requireExpected(10,"6"));
        assertTrue(mismatch10.getMessage().contains("did not match expected 6"));
    }
}
