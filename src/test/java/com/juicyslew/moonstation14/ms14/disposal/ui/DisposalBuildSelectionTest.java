package com.juicyslew.moonstation14.ms14.disposal.ui;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisposalBuildSelectionTest {
    @Test void selectionHasNoClientFacingOrDirectionField() {
        var request = new DisposalBuildSelection(3, UUID.randomUUID(), 7, "moonstation14:disposal_pipe");
        assertEquals("moonstation14:disposal_pipe", request.prototypeId());
        assertFalse(java.util.Arrays.stream(DisposalBuildSelection.class.getRecordComponents())
                .anyMatch(component -> component.getName().equalsIgnoreCase("direction")
                        || component.getName().toLowerCase().contains("facing")));
    }
}
