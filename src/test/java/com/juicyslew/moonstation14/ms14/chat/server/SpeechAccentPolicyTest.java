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

    @Test void upstreamChickenAndDuckAccentsUseTheirCanonicalReplacementSets() {
        assertArrayEquals(new String[]{"Cluck!", "Cluck.", "Cluck?", "Cluck cluck!"}, forms("chicken"));
        assertArrayEquals(new String[]{"Quack!", "Quack.", "Quack?", "Quack quack!"}, forms("duck"));
        assertTrue(SpeechAccentPolicy.supports("chicken"));
        assertTrue(SpeechAccentPolicy.supports("duck"));
        assertTrue(SpeechAccentPolicy.supports("monkey"));
        assertTrue(SpeechAccentPolicy.supports("parrot"));
    }

    @Test void monkeyAccentProducesOnlyUpstreamWordForms() {
        String result = SpeechAccentPolicy.replace("monkey", "Hello there!");
        assertTrue(result.endsWith("!"));
        assertTrue(result.matches("[OAKH ]+!"));
        assertEquals(2, result.substring(0, result.length() - 1).split(" ").length);
        assertEquals("", SpeechAccentPolicy.replace("monkey", ""));
    }

    @Test void parrotAccentUsesOnlyPinnedSquawksAndPunctuation() {
        String result = SpeechAccentPolicy.replace("parrot", "Polly wants a cracker");
        assertTrue(result.contains("SQUAWK!") || result.contains("SQUAAAWK!") || result.contains("AWWK!")
                || result.contains("AAWK!") || result.contains("RAWWK!") || result.contains("RAAAWK!")
                || result.contains("BRAAWK!") || result.contains("BRAWWK!"));
    }

    @Test void additionalExplicitAnimalAccentProfilesUsePinnedText() {
        assertEquals("Grrar.", SpeechAccentPolicy.replace("kobold", "hello", 1));
        assertEquals("Click-clack!", SpeechAccentPolicy.replace("crab", "hello", 1));
        assertEquals("Hisssss!", SpeechAccentPolicy.replace("xeno", "hello", 1));
        assertEquals("Chirp!", SpeechAccentPolicy.replace("mothroach", "hello", 1));
        assertEquals("Wa?", SpeechAccentPolicy.replace("scurret", "hello", 1));
        assertEquals("thisss", SpeechAccentPolicy.replace("lizard", "this"));
        assertNull(SpeechAccentPolicy.replacement("lizard", 0));
    }

    private static String[] forms(String accent) {
        return java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> SpeechAccentPolicy.replace(accent, "original sentence", i)).toArray(String[]::new);
    }
}
