package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoorPassabilityCompositionTest {
    @Test void ordinaryAirlockKeepsItsOwnPassabilityWhenNoFirelockIsInstalled() {
        assertTrue(DoorPassabilityComposition.passable(true, false, false));
        assertFalse(DoorPassabilityComposition.passable(false, false, true));
    }

    @Test void eitherClosedLeafBlocksTheComposedOpening() {
        assertFalse(DoorPassabilityComposition.passable(false, true, true));
        assertFalse(DoorPassabilityComposition.passable(true, true, false));
        assertTrue(DoorPassabilityComposition.passable(true, true, true));
    }
}
