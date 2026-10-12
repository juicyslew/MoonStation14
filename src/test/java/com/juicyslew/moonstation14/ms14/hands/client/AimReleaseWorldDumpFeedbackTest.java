package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AimReleaseWorldDumpFeedbackTest {
    private ChemMasterDumpResult result(UUID id, ChemMasterDumpRequest.Phase phase, boolean accepted) {
        return new ChemMasterDumpResult(id, phase, accepted, accepted ? Optional.empty() : Optional.of(Component.literal("Rejected")));
    }
    @Test void lateBeginAckCannotReopenReleasedGestureAndOlderAckCannotClearNewer() {
        var feedback = new AimReleaseWorldDumpFeedback();
        UUID old = UUID.randomUUID(), newer = UUID.randomUUID();
        UUID source = UUID.randomUUID(), body = UUID.randomUUID();
        feedback.begin(old, source, body, 2);
        assertTrue(feedback.accept(result(old, ChemMasterDumpRequest.Phase.RELEASE, true)));
        assertFalse(feedback.accept(result(old, ChemMasterDumpRequest.Phase.BEGIN, true)));
        feedback.begin(newer, source, body, 2);
        assertFalse(feedback.accept(result(old, ChemMasterDumpRequest.Phase.RELEASE, false)));
        assertTrue(feedback.pending());
        assertTrue(feedback.accept(result(newer, ChemMasterDumpRequest.Phase.BEGIN, true)));
        assertTrue(feedback.pending());
        assertTrue(feedback.accept(result(newer, ChemMasterDumpRequest.Phase.RELEASE, false)));
        assertFalse(feedback.pending());
        assertFalse(feedback.accept(result(newer, ChemMasterDumpRequest.Phase.RELEASE, false)));
    }
    @Test void immediateReleaseWaitsForBeginAckAndFlushesOnlyOnce() {
        var feedback = new AimReleaseWorldDumpFeedback();
        UUID id = UUID.randomUUID();
        UUID source = UUID.randomUUID(), body = UUID.randomUUID();
        feedback.begin(id, source, body, 3);
        assertTrue(feedback.queueRelease(source, body, 3));
        assertFalse(feedback.queueRelease(source, body, 3));
        assertFalse(feedback.takeQueuedRelease());
        assertTrue(feedback.accept(result(id, ChemMasterDumpRequest.Phase.BEGIN, true)));
        assertTrue(feedback.takeQueuedRelease());
        assertFalse(feedback.takeQueuedRelease());
        assertTrue(feedback.accept(result(id, ChemMasterDumpRequest.Phase.BEGIN, true))); // duplicate ACK cannot terminate RELEASE correlation
        assertTrue(feedback.pending());
        assertTrue(feedback.accept(result(id, ChemMasterDumpRequest.Phase.RELEASE, true)));
    }
    @Test void deniedBeginPreservesSingleOriginalFailureAndDropsQueuedRelease() {
        var feedback = new AimReleaseWorldDumpFeedback();
        UUID id = UUID.randomUUID();
        UUID source = UUID.randomUUID(), body = UUID.randomUUID();
        feedback.begin(id, source, body, 3);
        assertTrue(feedback.queueRelease(source, body, 3));
        assertTrue(feedback.accept(result(id, ChemMasterDumpRequest.Phase.BEGIN, false)));
        assertFalse(feedback.pending());
        assertFalse(feedback.takeQueuedRelease());
        assertFalse(feedback.accept(result(id, ChemMasterDumpRequest.Phase.BEGIN, false)));
    }

    @Test void cancellationAndContextChangeDropPendingReleaseAndDuplicatesDoNotReflush() {
        var feedback = new AimReleaseWorldDumpFeedback();
        UUID id = UUID.randomUUID(), source = UUID.randomUUID(), body = UUID.randomUUID();
        feedback.begin(id, source, body, 7);
        assertFalse(feedback.queueRelease(source, body, 8));
        assertTrue(feedback.queueRelease(source, body, 7));
        assertFalse(feedback.ownsContext(id, source, body, 8)); // body epoch changed after physical up, before ACK
        feedback.cancelQueued(id);
        assertFalse(feedback.accept(result(id, ChemMasterDumpRequest.Phase.BEGIN, true)));
        assertFalse(feedback.takeQueuedRelease());

        UUID next = UUID.randomUUID();
        feedback.begin(next, source, body, 7);
        assertTrue(feedback.queueRelease(source, body, 7));
        assertTrue(feedback.accept(result(next, ChemMasterDumpRequest.Phase.BEGIN, true)));
        assertTrue(feedback.takeQueuedRelease());
        assertFalse(feedback.takeQueuedRelease());
        assertFalse(feedback.accept(result(id, ChemMasterDumpRequest.Phase.BEGIN, true)));
        assertFalse(feedback.takeQueuedRelease());
    }

    @Test void acceptedBeginThenPhysicalReleaseNeedsNoQueuedRoundTripAndExamineHasNoDumpIntent() {
        var feedback = new AimReleaseWorldDumpFeedback();
        UUID id = UUID.randomUUID(), source = UUID.randomUUID(), body = UUID.randomUUID();
        feedback.begin(id, source, body, 1);
        assertTrue(feedback.accept(result(id, ChemMasterDumpRequest.Phase.BEGIN, true)));
        assertTrue(feedback.beginAccepted()); // live route dispatches RELEASE immediately
        assertFalse(feedback.takeQueuedRelease());
        feedback.clear(); // safe source-click EXAMINE follows CANCEL, never queues RELEASE
        assertFalse(feedback.pending());
    }
}
