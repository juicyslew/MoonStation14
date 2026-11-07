package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TegBurnerDiagnosticTest {
    @Test void absentFuelDoesNotClaimIgnitionFireOrVisiblePlasma() {
        var empty=new TegBurnerDiagnostic.Cell(293.15,0,0,0,0,0);
        var result=TegBurnerDiagnostic.aggregate(List.of(empty));
        assertEquals(1,result.cells());
        assertEquals(0,result.reactionEligibleCells());
        assertEquals(0,result.activeFireCells());
        assertEquals(0,result.maximumFireIntensity());
        assertEquals(0,result.plasmaVisibleCells());
        assertEquals(.25,TegBurnerDiagnostic.PLASMA_VISIBILITY_THRESHOLD_MOLES_PER_CELL,0);
    }
}
