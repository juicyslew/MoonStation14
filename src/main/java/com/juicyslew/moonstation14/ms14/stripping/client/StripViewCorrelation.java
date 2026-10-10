package com.juicyslew.moonstation14.ms14.stripping.client;

import com.juicyslew.moonstation14.ms14.stripping.StripViewSnapshot;
import java.util.UUID;

/** Bounded client-side reply gate. A closed/retargeted session can never be revived by an old reply. */
public final class StripViewCorrelation {
    private UUID target;
    private long epoch;
    private int entityId = -1;
    private long sequence;
    private long targetRevision;
    private boolean open;
    private boolean inFlight;

    public void begin(UUID target, long epoch, long sequence) {
        if (target == null || epoch <= 0 || sequence <= 0) throw new IllegalArgumentException("Invalid strip session");
        this.target = target;
        this.epoch = epoch;
        this.sequence = sequence;
        this.entityId = -1;
        this.targetRevision = -1;
        this.open = true;
        this.inFlight = true;
    }

    public long querySequence(long nextSequence) {
        if (!open || inFlight || nextSequence <= sequence) return 0;
        sequence = nextSequence;
        inFlight = true;
        return sequence;
    }

    public boolean accept(StripViewSnapshot snapshot) {
        if (snapshot == null || !open || snapshot.epoch() != epoch
                || !target.equals(snapshot.target()) || snapshot.sequence() != sequence) return false;
        if (snapshot.status() == StripViewSnapshot.Status.CLOSED) { close(); return true; }

        // A response to the current outstanding query may settle it. A broadcast using an
        // older accepted sequence is ignored while a newer query is pending, so it cannot
        // release that query or overwrite its eventual response.
        boolean queryReply = inFlight;
        if (targetRevision >= 0 && snapshot.targetRevision() < targetRevision
                && !(queryReply && snapshot.status() == StripViewSnapshot.Status.UNAVAILABLE && snapshot.entityId() == 0)) return false;
        boolean absentUnavailableTarget = snapshot.status() == StripViewSnapshot.Status.UNAVAILABLE
                && snapshot.entityId() == 0;
        if (snapshot.entityId() != 0 && entityId >= 0 && entityId != snapshot.entityId()) {
            close();
            return false;
        }
        if (!absentUnavailableTarget) entityId = snapshot.entityId();
        if (snapshot.targetRevision() >= targetRevision) targetRevision = snapshot.targetRevision();
        inFlight = false;
        return true;
    }

    public void close() { open = false; inFlight = false; target = null; epoch = 0; entityId = -1; }
    public boolean open() { return open; }
    public boolean inFlight() { return inFlight; }
    public UUID target() { return target; }
    public long epoch() { return epoch; }
    public long sequence() { return sequence; }
    public int entityId() { return entityId; }
    public long targetRevision() { return targetRevision; }
}
