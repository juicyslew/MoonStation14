package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeSelectionInsertionTest {
    private static boolean selected(String before, String after, String oldToken, String newToken,
                                    boolean sameItem, boolean occupied) {
        return MeleeSelectionCooldown.selectionChanged(before, after, oldToken, newToken, sameItem, occupied);
    }

    @Test void newlyInsertedSameItemWithNewTokenResets() {
        assertTrue(selected("right", "right", "old", "new", true, true));
    }

    @Test void componentOnlyUpdateWithSameIdentityDoesNotReset() {
        assertFalse(selected("right", "right", "same", "same", true, true));
    }

    @Test void differentItemResets() {
        assertTrue(selected("right", "right", "same", "same", false, true));
    }

    @Test void newlyActiveOccupiedHandResets() {
        assertTrue(selected("left", "right", "same", "same", true, true));
    }

    @Test void emptySelectionDoesNotReset() {
        assertFalse(selected("left", "right", null, null, false, false));
    }
}
