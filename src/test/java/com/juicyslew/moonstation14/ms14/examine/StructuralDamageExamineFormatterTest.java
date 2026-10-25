package com.juicyslew.moonstation14.ms14.examine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuralDamageExamineFormatterTest {
    @Test void bandsMatchNearestFiveLevelsAgainstDestructionThreshold() {
        for (int band = 0; band <= 5; band++) {
            int damage = (int) Math.round((band * 0.2) * 225);
            assertEquals(band, StructuralDamageExamineFormatter.band(damage, 225));
        }
        assertEquals(1, StructuralDamageExamineFormatter.band(23, 225));
        assertEquals(1, StructuralDamageExamineFormatter.band(30, 300));
        assertEquals(5, StructuralDamageExamineFormatter.band(225, 225));
        assertEquals(5, StructuralDamageExamineFormatter.band(300, 300));
        assertEquals(0, StructuralDamageExamineFormatter.band(0, 300));
        assertEquals(0, StructuralDamageExamineFormatter.band(-1, 300));
        assertEquals(0, StructuralDamageExamineFormatter.band(1, 0));
    }
}
