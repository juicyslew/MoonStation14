package com.juicyslew.moonstation14.gametest.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTestFailureMessageTest {
    @Test
    void keepsConciseTextAndBoundsOversizedFailureArtifacts() {
        String shortMessage = "proof failed";
        assertEquals(shortMessage, GameTestFailureMessage.concise(shortMessage));

        String longMessage = "x".repeat(1_500);
        String concise = GameTestFailureMessage.concise(longMessage);
        assertEquals(240, concise.length());
        assertTrue(concise.endsWith("..."));

        String nativeFailure = "Test " + "continuousResourceAdmission" + " failed: " + concise;
        assertTrue(nativeFailure.length() < 1_024);
    }
}
