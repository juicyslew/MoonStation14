package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpResult;
import java.util.UUID;

/** One-current-gesture ack correlation; no delayed begin ack can revive a completed gesture. */
final class AimReleaseWorldDumpFeedback {
    private UUID current;
    private boolean pending;
    private boolean beginAccepted;
    private boolean releaseQueued;
    private UUID source, body;
    private long epoch;
    void begin(UUID gesture, UUID source, UUID body, long epoch) {
        current = gesture; this.source = source; this.body = body; this.epoch = epoch;
        pending = true; beginAccepted = false; releaseQueued = false;
    }
    boolean queueRelease(UUID source, UUID body, long epoch) {
        if (current == null || !pending || releaseQueued) return false;
        if (!this.source.equals(source) || !this.body.equals(body) || this.epoch != epoch) return false;
        releaseQueued = true;
        return true;
    }
    boolean beginAccepted() { return beginAccepted; }
    boolean hasQueuedRelease() { return releaseQueued; }
    boolean takeQueuedRelease() {
        if (!releaseQueued || !beginAccepted || !pending) return false;
        releaseQueued = false;
        return true;
    }
    boolean ownsContext(UUID gesture, UUID source, UUID body, long epoch) {
        return pending && current != null && current.equals(gesture) && this.source.equals(source)
                && this.body.equals(body) && this.epoch == epoch;
    }
    void cancelQueued(UUID gesture) {
        if (current != null && current.equals(gesture)) { releaseQueued = false; pending = false; }
    }
    boolean accept(ChemMasterDumpResult result) {
        if (result == null || current == null || !current.equals(result.gestureId())
                || !pending) return false;
        if (result.phase() == ChemMasterDumpRequest.Phase.BEGIN) {
            if (!result.accepted()) { pending = false; releaseQueued = false; return true; }
            beginAccepted = true;
            return true;
        }
        pending = false;
        return true;
    }
    UUID current() { return current; }
    boolean pending() { return pending; }
    void clear() { current = source = body = null; epoch = 0; pending = false; beginAccepted = false; releaseQueued = false; }
}
