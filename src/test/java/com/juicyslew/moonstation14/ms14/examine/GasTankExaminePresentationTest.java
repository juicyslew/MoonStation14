package com.juicyslew.moonstation14.ms14.examine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasTankExaminePresentationTest {
    @Test void upstreamRoundThenPressureUnitFormatter() {
        assertEquals("101.0 kPa", GasTankExaminePresentation.examinedPressure(101.325));
        assertEquals("1.0 MPa", GasTankExaminePresentation.examinedPressure(1_000));
        assertEquals("0.0 kPa", GasTankExaminePresentation.examinedPressure(0.4));
        assertThrows(IllegalArgumentException.class, () -> GasTankExaminePresentation.examinedPressure(Double.NaN));
    }
}
