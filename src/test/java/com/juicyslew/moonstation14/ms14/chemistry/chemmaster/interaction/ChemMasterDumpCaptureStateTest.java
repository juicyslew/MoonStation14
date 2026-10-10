package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterDumpCaptureStateTest {
    @Test void replacementAndOldCancelCannotClearCurrentCapture() {
        var state = new ChemMasterDumpCaptureState<ChemMasterDumpCorrelation.Owner>();
        UUID player = UUID.randomUUID(), body = UUID.randomUUID(), source = UUID.randomUUID();
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        var first = new ChemMasterDumpCorrelation.Owner(UUID.randomUUID(), source, body, 4, dim, 10);
        var replacement = new ChemMasterDumpCorrelation.Owner(UUID.randomUUID(), source, body, 5, dim, 12);
        state.replace(player, first);
        state.replace(player, replacement);
        assertFalse(state.removeIf(player, owner -> ChemMasterDumpCorrelation.owns(owner, first.gesture(), source, body, 4, dim, 11)));
        assertSame(replacement, state.get(player));
    }

    @Test void tickCleanupUsesPlayerAndCurrentBodyEpochSourceValidity() {
        var state = new ChemMasterDumpCaptureState<ChemMasterDumpCorrelation.Owner>();
        UUID player = UUID.randomUUID(), body = UUID.randomUUID(), source = UUID.randomUUID();
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        var owner = new ChemMasterDumpCorrelation.Owner(UUID.randomUUID(), source, body, 8, dim, 20);
        state.replace(player, owner);
        state.removeInvalid((id, capture) -> id.equals(player) && capture.epoch() == 8 && capture.source().equals(source) ? null : "invalid",
                (id, capture, reason) -> fail());
        assertSame(owner, state.get(player));
        var removed = new java.util.concurrent.atomic.AtomicReference<ChemMasterDumpCorrelation.Owner>();
        state.removeInvalid((id, capture) -> id.equals(player) && capture.epoch() == 9 ? null : "epoch",
                (id, capture, reason) -> { assertEquals("epoch", reason); removed.set(capture); });
        assertNull(state.get(player));
        assertSame(owner, removed.get());
    }
}
