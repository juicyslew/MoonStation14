package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChemMasterDumpCorrelationTest {
    @Test void releaseBeforeBeginAcknowledgementIsOrderedByOwnedRequestSequence() {
        UUID gesture = UUID.randomUUID(), source = UUID.randomUUID(), body = UUID.randomUUID();
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        var begin = new ChemMasterDumpCorrelation.Owner(gesture, source, body, 8, dim, 20);
        assertTrue(ChemMasterDumpCorrelation.owns(begin, gesture, source, body, 8, dim, 21));
        assertFalse(ChemMasterDumpCorrelation.owns(begin, gesture, source, body, 8, dim, 20));
        // The response to BEGIN is deliberately not part of ownership; transport response order cannot authorize release.
    }

    @Test void oldCancelAndMismatchedContextCannotRemoveReplacementCapture() {
        UUID source = UUID.randomUUID(), body = UUID.randomUUID();
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        var replacement = new ChemMasterDumpCorrelation.Owner(UUID.randomUUID(), source, body, 9, dim, 32);
        assertFalse(ChemMasterDumpCorrelation.owns(replacement, UUID.randomUUID(), source, body, 9, dim, 33));
        assertFalse(ChemMasterDumpCorrelation.owns(replacement, replacement.gesture(), source, body, 8, dim, 33));
        assertFalse(ChemMasterDumpCorrelation.owns(replacement, replacement.gesture(), source, body, 9,
                ResourceLocation.withDefaultNamespace("the_nether"), 33));
        assertTrue(ChemMasterDumpCorrelation.owns(replacement, replacement.gesture(), source, body, 9, dim, 33));
    }

    @Test void replacementSourceAndNonmonotonicSequencesAreDenied() {
        UUID body = UUID.randomUUID(), gesture = UUID.randomUUID(), source = UUID.randomUUID();
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        var current = new ChemMasterDumpCorrelation.Owner(gesture, source, body, 1, dim, 10);
        assertFalse(ChemMasterDumpCorrelation.owns(current, gesture, UUID.randomUUID(), body, 1, dim, 11));
        assertFalse(ChemMasterDumpCorrelation.owns(current, gesture, source, body, 1, dim, 9));
        assertTrue(ChemMasterDumpCorrelation.newer(11, 10));
        assertFalse(ChemMasterDumpCorrelation.newer(10, 10));
    }
}
