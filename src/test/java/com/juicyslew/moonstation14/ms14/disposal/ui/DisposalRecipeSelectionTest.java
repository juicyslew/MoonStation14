package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisposalRecipeSelectionTest {
    @Test void selectionPersistsOnlyForExactBodyMindEpoch() {
        var selection = new DisposalRecipeSelection();
        var body = UUID.randomUUID();
        var mind = new MindId(UUID.randomUUID());
        selection.select(body, 4, mind, "moonstation14:disposal_pipe");
        assertEquals("moonstation14:disposal_pipe", selection.selected(body, 4, mind).orElseThrow());
        assertTrue(selection.selected(body, 5, mind).isEmpty());
        assertTrue(selection.selected(body, 4, new MindId(UUID.randomUUID())).isEmpty());
        assertTrue(selection.selected(UUID.randomUUID(), 4, mind).isEmpty());
    }
}
