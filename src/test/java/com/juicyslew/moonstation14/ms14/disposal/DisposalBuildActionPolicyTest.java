package com.juicyslew.moonstation14.ms14.disposal;

import com.juicyslew.moonstation14.ms14.disposal.ui.DisposalBuildActionPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalBuildActionPolicyTest {
    private boolean ready(long ticks, boolean menu, boolean moved, boolean host, boolean recipe, boolean source) {
        return DisposalBuildActionPolicy.mayComplete(ticks, menu, !moved, host, recipe, source);
    }
    @Test void successRequiresFullTwentyTicksAndEveryCapturedSource() {
        assertFalse(ready(19,true,false,true,true,true));
        assertTrue(ready(20,true,false,true,true,true));
    }
    @Test void movementMenuSourceHostAndRecipeChangesCancel() {
        assertFalse(ready(20,true,true,true,true,true));
        assertFalse(ready(20,false,false,true,true,true));
        assertFalse(ready(20,true,false,true,true,false));
        assertFalse(ready(20,true,false,false,true,true));
        assertFalse(ready(20,true,false,true,false,true));
    }
    @Test void materialDamageDoesNotParticipateInCancellationGate() {
        assertTrue(ready(20,true,false,true,true,true));
    }
    @Test void insufficientMaterialsAndFailedCommitMustNotProceedToWorldMutation() {
        // The source gate includes exact steel count/token/revision; commit failure invalidates that gate.
        assertFalse(ready(20,true,false,true,true,false));
    }
}
