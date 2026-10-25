package com.juicyslew.moonstation14.ms14.chat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParrotMemoryPolicyTest {
    @Test void onlyClearInRangeAuthorizedLocalSpeechCanBeLearned() {
        assertTrue(canLearn(9.99, SpeechRecipientPolicy.Delivery.CLEAR, false, true, true, 0));
        assertFalse(canLearn(10.01, SpeechRecipientPolicy.Delivery.CLEAR, false, true, true, 0));
        assertFalse(canLearn(1, SpeechRecipientPolicy.Delivery.MUFFLED, false, true, true, 0));
        assertFalse(canLearn(1, SpeechRecipientPolicy.Delivery.NONE, false, true, true, 0));
        assertFalse(canLearn(1, SpeechRecipientPolicy.Delivery.CLEAR, true, true, true, 0));
        assertFalse(canLearn(1, SpeechRecipientPolicy.Delivery.CLEAR, false, false, true, 0));
        assertFalse(canLearn(1, SpeechRecipientPolicy.Delivery.CLEAR, false, true, false, 0));
    }

    @Test void messageBoundsAndLearningCooldownAreEnforced() {
        assertTrue(ParrotMemoryPolicy.canLearn(true, true, true, true, false, false,
                SpeechRecipientPolicy.Delivery.CLEAR, 0, 10, "four", 4, 50, 100, 0));
        assertTrue(ParrotMemoryPolicy.canLearn(true, true, true, true, false, false,
                SpeechRecipientPolicy.Delivery.CLEAR, 0, 10, "x".repeat(50), 4, 50, 100, 0));
        assertFalse(ParrotMemoryPolicy.canLearn(true, true, true, true, false, false,
                SpeechRecipientPolicy.Delivery.CLEAR, 0, 10, " abc ", 4, 50, 100, 0));
        assertFalse(ParrotMemoryPolicy.canLearn(true, true, true, true, false, false,
                SpeechRecipientPolicy.Delivery.CLEAR, 0, 10, "hello", 4, 50, 99, 100));
        assertFalse(ParrotMemoryPolicy.canLearn(true, true, true, true, false, false,
                SpeechRecipientPolicy.Delivery.CLEAR, 0, 10, "~faraway~", 4, 50, 100, 0));
    }

    @Test void boundedMemoryUsesRandomReplacementAndIntervalsUseAuthoritativeTicks() {
        assertEquals(-1, ParrotMemoryPolicy.replacementIndex(2, 3, 0));
        assertEquals(2, ParrotMemoryPolicy.replacementIndex(3, 3, 2));
        assertFalse(ParrotMemoryPolicy.shouldReplay(true, true, true, false, 1, 2399, 2400));
        assertTrue(ParrotMemoryPolicy.shouldReplay(true, true, true, false, 1, 2400, 2400));
        assertFalse(ParrotMemoryPolicy.shouldReplay(true, true, false, false, 1, 2400, 2400));
        assertEquals(2400, ParrotMemoryPolicy.nextInterval(100, 2300, 2400, 0));
        assertEquals(2499, ParrotMemoryPolicy.nextInterval(100, 2300, 2400, 99));
    }

    private static boolean canLearn(double distance, SpeechRecipientPolicy.Delivery delivery,
                                    boolean self, boolean autonomous, boolean alive, long now) {
        return ParrotMemoryPolicy.canLearn(true, true, alive, autonomous, false, self, delivery,
                distance * distance, 10, "heard phrase", 4, 50, now, 0);
    }
}
