package com.juicyslew.moonstation14.ms14.chat.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechAccentPolicyTest {
    @Test void mouseAccentReplacesSpeechWithAllSevenCanonicalForms() {
        String[] expected = {"Squeak!", "Piep!", "Chuu!", "Eeee!", "Pip!", "Fwiep!", "Heep!"};
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], SpeechAccentPolicy.replace("mouse", "original sentence", i));
    }

    @Test void missingAccentKeepsTextAndNonSpeechIsNotReplaced() {
        assertEquals("hello", SpeechAccentPolicy.replace("", "hello"));
        assertEquals("", SpeechAccentPolicy.replace("mouse", ""));
        assertNull(SpeechAccentPolicy.replacement("other", 0));
    }
}
