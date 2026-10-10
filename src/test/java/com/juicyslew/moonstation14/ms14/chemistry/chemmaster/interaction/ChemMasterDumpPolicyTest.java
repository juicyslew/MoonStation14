package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.ms14.jug.JugData;
import com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.reagent.DumpableSolutionPolicy;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChemMasterDumpPolicyTest {
    @Test void resolvedJugPrimarySolutionMustMatchDrainAndCanonicalTargetNames() {
        var data = new JugData(ExamineMetadata.EMPTY, new JugData.SolutionContainer("custom", 200),
                new JugData.ExaminableSolution("custom", true, false, true, 3),
                new DrainableSolutionPolicy(true, "custom"));
        String actual = JugItem.primarySolutionNameFromResolved(data);
        var drainable = JugItem.drainableSolutionFromResolved(data);
        assertEquals("custom", actual);
        assertTrue(ChemMasterWorldDump.policiesMatch(drainable, actual,
                new DumpableSolutionPolicy(true, "buffer", true)));
        assertFalse(ChemMasterWorldDump.policiesMatch(drainable, actual,
                new DumpableSolutionPolicy(true, "other", true)));
        assertFalse(ChemMasterWorldDump.policiesMatch(new DrainableSolutionPolicy(true, "drink"), actual,
                new DumpableSolutionPolicy(true, "buffer", true)));
        assertFalse(ChemMasterWorldDump.policiesMatch(new DrainableSolutionPolicy(false, "custom"), actual,
                new DumpableSolutionPolicy(true, "buffer", true)));
        assertFalse(ChemMasterWorldDump.policiesMatch(drainable, actual,
                new DumpableSolutionPolicy(false, "buffer", true)),
                "disabled target is rejected even when unlimited is true");
    }
}
