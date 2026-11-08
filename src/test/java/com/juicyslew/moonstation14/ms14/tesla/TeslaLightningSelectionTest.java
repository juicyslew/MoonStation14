package com.juicyslew.moonstation14.ms14.tesla;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class TeslaLightningSelectionTest {
    @Test void priorityAndProbabilityMissesDoNotConsumeStrike() {
        var candidates = List.of(new TeslaLightningSelection.Candidate<>("miss", 4, 0, 10),
                new TeslaLightningSelection.Candidate<>("coil", 4, 1, 10),
                new TeslaLightningSelection.Candidate<>("rod", 3, 1, 10));
        assertEquals("coil", TeslaLightningSelection.choose(candidates, new Random(4)).value());
    }
    @Test void resistanceStopsFiniteChain() {
        assertTrue(TeslaLightningSelection.chains(3, 1));
        assertFalse(TeslaLightningSelection.chains(1, 1));
        assertFalse(TeslaLightningSelection.chains(0, 0));
    }
    @Test void missesDoNotSpendConfiguredBoltsAndCandidateConsideredOnlyOnce() {
        var candidates = List.of(new TeslaLightningSelection.Candidate<>("miss", 9, 0, 1),
                new TeslaLightningSelection.Candidate<>("hit", 8, 1, 1),
                new TeslaLightningSelection.Candidate<>("next", 7, 1, 1));
        var admitted = TeslaLightningSelection.chooseMany(candidates, 2, new Random(7));
        assertEquals(2, admitted.size());
        assertTrue(admitted.stream().anyMatch(c -> c.value().equals("hit")));
        assertTrue(admitted.stream().anyMatch(c -> c.value().equals("next")));
    }
    @Test void explosionProposalUsesTargetAuthoredBlastFieldsAndHonorsDisabledTargets() {
        var target = new com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent(
                0, 1, 1, 1, true, 25, 2, 5);
        var proposal = TeslaLightningSystem.explosionProposal(target);
        assertNotNull(proposal);
        assertEquals(25, proposal.totalIntensity());
        assertEquals(2, proposal.slope());
        assertEquals(5, proposal.maxCellIntensity());
        var safe = new com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent(
                4, .5, 10, 1, false, 25, 2, 5);
        assertNull(TeslaLightningSystem.explosionProposal(safe));
    }
    @Test void initialBoltCountUsesExclusiveAuthoredMaximum() {
        var random=new Random(9182); boolean one=false,two=false,three=false;
        for(int i=0;i<500;i++) {
            int value=TeslaLightningSelection.initialBoltCount(4,random);
            assertTrue(value>=1 && value<4);
            one|=value==1; two|=value==2; three|=value==3;
            assertEquals(1,TeslaLightningSelection.initialBoltCount(1,random));
        }
        assertTrue(one&&two&&three);
    }
}
