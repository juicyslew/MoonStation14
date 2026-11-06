package com.juicyslew.moonstation14.ms14.player_body_control;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConnectedJumpCaptureBudgetTest {
    @Test void windowRowsRenderAndEpisodeCapsAreBounded() {
        var budget = new ConnectedJumpCaptureBudget();
        UUID body = UUID.randomUUID();
        assertTrue(budget.begin(1, body, 7, 10, true));
        assertFalse(budget.begin(1, body, 7, 10, true));
        for (int tick = 10; tick < 26; tick++) {
            if (tick == 10) continue; // BEGIN consumes first tick and row
            assertTrue(budget.sample(1, body, 7, tick, false));
        }
        assertTrue(budget.active());
        assertFalse(budget.sample(1, body, 7, 26, false));
        assertFalse(budget.active());
        assertTrue(budget.begin(1, body, 7, 30, true));
        assertTrue(budget.begin(2, UUID.randomUUID(), 8, 30, true)); // identity reset
        assertEquals(1, budget.episodes());
    }

    @Test void duplicateTickAndRenderBudgetAreLimited() {
        var budget = new ConnectedJumpCaptureBudget();
        UUID body = UUID.randomUUID();
        assertTrue(budget.begin(1, body, 7, 1, false));
        assertTrue(budget.sample(1, body, 7, 1, true));
        assertFalse(budget.sample(1, body, 7, 1, true));
        for (int tick = 2; tick <= ConnectedJumpCaptureBudget.MAX_RENDER_ROWS; tick++)
            assertTrue(budget.sample(1, body, 7, tick, true));
        assertFalse(budget.sample(1, body, 7, 13, true));
        assertFalse(budget.sample(2, body, 7, 2, false));
        budget.stop();
        assertFalse(budget.sample(1, body, 7, 2, false));
    }

    @Test void gapsOutOfOrderIdentityPrelaunchAndServerStopCap() {
        var budget = new ConnectedJumpCaptureBudget(); UUID body = UUID.randomUUID();
        assertTrue(budget.begin(9, body, 2, 100, true));
        assertTrue(budget.prelaunchRow(9, body, 2, 98));
        assertTrue(budget.prelaunchRow(9, body, 2, 99));
        assertFalse(budget.prelaunchRow(9, body, 2, 99));
        assertFalse(budget.sample(9, body, 2, 99, false)); // backward tick closes window
        assertTrue(budget.begin(9, body, 2, 200, true));
        budget.stop();
        assertFalse(budget.begin(9, body, 2, 300, true)); // stop does not refund episode allowance
        assertTrue(budget.begin(10, UUID.randomUUID(), 3, 1, true)); // new identity resets allowance
        assertFalse(budget.sample(9, body, 2, 2, false)); // old identity rejected
    }

    @Test void elapsedGapExpiresEvenWithoutIntermediateSamples() {
        var budget = new ConnectedJumpCaptureBudget(); UUID body = UUID.randomUUID();
        assertTrue(budget.begin(1, body, 1, 50, false));
        assertFalse(budget.sample(1, body, 1, 66, false));
        assertFalse(budget.active());
    }

    @Test void totalRowsIncludeBeginAndNeverExceedSixtyFour() {
        var budget = new ConnectedJumpCaptureBudget(); UUID body = UUID.randomUUID();
        assertTrue(budget.begin(1, body, 1, 1, false));
        for (int i = 1; i < ConnectedJumpCaptureBudget.MAX_ROWS; i++)
            assertTrue(budget.sample(1, body, 1, 1, false));
        assertEquals(ConnectedJumpCaptureBudget.MAX_ROWS, budget.rows());
        assertFalse(budget.active());
        assertFalse(budget.finishRow(1,body,1));
        assertFalse(budget.sample(1, body, 1, 1, false));
    }
    @Test void endRowIsBudgetedAndAtMostOnce() {
        var budget=new ConnectedJumpCaptureBudget(); UUID body=UUID.randomUUID();
        assertTrue(budget.begin(1,body,1,1,false)); budget.stop();
        assertTrue(budget.finishRow(1,body,1));
        assertFalse(budget.finishRow(1,body,1));
        assertEquals(2,budget.rows());
    }
}
