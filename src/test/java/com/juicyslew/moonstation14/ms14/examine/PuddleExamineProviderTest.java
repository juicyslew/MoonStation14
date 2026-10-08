package com.juicyslew.moonstation14.ms14.examine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PuddleExamineProviderTest {
    @Test void volumeUsesResolvedCapacityThresholdsAndDeterministicMiddleAdaptation() {
        assertEquals("empty", BuiltinExamineContributors.examinedVolume(0, 1000, false));
        assertEquals("mostly-empty", BuiltinExamineContributors.examinedVolume(3300, 1000, false));
        assertEquals("half-full", BuiltinExamineContributors.examinedVolume(50000, 1000, false));
        assertEquals("mostly-full", BuiltinExamineContributors.examinedVolume(67000, 1000, false));
        assertEquals("full", BuiltinExamineContributors.examinedVolume(100000, 1000, false));
        assertEquals("mostly-full", BuiltinExamineContributors.examinedVolume(35000, 500, false),
                "inherited prototype capacity feeds examination categorization");
        assertEquals("50.00 / 500.00", BuiltinExamineContributors.examinedVolume(5000, 500, true));
    }
}
