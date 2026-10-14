package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterLabelRefreshPolicyTest {
    @Test void updateLabelMatrixMatchesTransferAndBufferDiscardCallSites() {
        var transfer = ChemMasterControlRequest.Mode.TRANSFER;
        var discard = ChemMasterControlRequest.Mode.DISCARD;
        assertTrue(ChemMasterSessionService.shouldRegenerateLabel(ChemMasterControlRequest.Operation.INPUT_REAGENT, transfer, true));
        assertTrue(ChemMasterSessionService.shouldRegenerateLabel(ChemMasterControlRequest.Operation.BUFFER_REAGENT, transfer, true));
        assertTrue(ChemMasterSessionService.shouldRegenerateLabel(ChemMasterControlRequest.Operation.BUFFER_REAGENT, discard, true));
        assertFalse(ChemMasterSessionService.shouldRegenerateLabel(ChemMasterControlRequest.Operation.INPUT_REAGENT, discard, true));
        for (var operation : new ChemMasterControlRequest.Operation[]{ChemMasterControlRequest.Operation.SET_DRAW_SOURCE,
                ChemMasterControlRequest.Operation.SET_PILL_TYPE, ChemMasterControlRequest.Operation.CREATE_PILLS,
                ChemMasterControlRequest.Operation.OUTPUT_TO_BOTTLE, ChemMasterControlRequest.Operation.QUERY})
            assertFalse(ChemMasterSessionService.shouldRegenerateLabel(operation, transfer, true));
        assertFalse(ChemMasterSessionService.shouldRegenerateLabel(ChemMasterControlRequest.Operation.INPUT_REAGENT, transfer, false));
    }
}
