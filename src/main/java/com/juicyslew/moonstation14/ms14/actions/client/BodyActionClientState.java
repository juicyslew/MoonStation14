package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Client-side ordering and presentation policy. Pending feedback never mutates authoritative entries. */
public final class BodyActionClientState {
    private UUID body;
    private long epoch, revision = -1, sequence, pendingSequence;
    private BodyActionSnapshot snapshot;
    private long localClock, snapshotClockAnchor;
    private String feedback;

    public void control(UUID bodyId, long bodyEpoch) {
        if (bodyId == null || bodyEpoch <= 0) { clear(); return; }
        if (!bodyId.equals(body) || epoch != bodyEpoch) { clear(); body = bodyId; epoch = bodyEpoch; }
    }
    public void worldChanged() { clear(); }
    private void clear() { body = null; epoch = 0; revision = -1; snapshot = null; localClock = 0; snapshotClockAnchor = 0; pendingSequence = 0; feedback = null; }
    private long advanceClock(long localTick) {
        if (localTick > localClock) localClock = localTick;
        return localClock;
    }
    public boolean accept(BodyActionSnapshot incoming) { return accept(incoming, 0); }
    public boolean accept(BodyActionSnapshot incoming, long localTick) {
        advanceClock(localTick);
        if (incoming == null || body == null || !body.equals(incoming.bodyId()) || epoch != incoming.bodyEpoch()
                || incoming.revision() <= revision) return false;
        snapshot = incoming; revision = incoming.revision(); snapshotClockAnchor = localClock; return true;
    }
    public long begin(ResourceLocation actionId, String providerId) {
        if (snapshot == null || pendingSequence != 0 || actionId == null || providerId == null || providerId.isBlank()
                || sequence == Long.MAX_VALUE) return 0;
        boolean available = snapshot.actions().stream().anyMatch(entry -> entry.actionId().equals(actionId)
                && entry.providerId().equals(providerId) && entry.enabled());
        if (!available) return 0;
        pendingSequence = ++sequence; feedback = null; return pendingSequence;
    }
    public boolean accept(BodyActionResult result) { return accept(result, 0); }
    public boolean accept(BodyActionResult result, long localTick) {
        advanceClock(localTick);
        if (result == null || pendingSequence == 0 || result.sequence() != pendingSequence) return false;
        if (result.snapshot().isPresent()) {
            BodyActionSnapshot incoming = result.snapshot().get();
            if (body == null || !body.equals(incoming.bodyId()) || epoch != incoming.bodyEpoch()) return false;
        }
        pendingSequence = 0; feedback = result.status() == BodyActionResult.Status.DENIED ? result.reason() : null;
        result.snapshot().ifPresent(incoming -> accept(incoming, localTick)); return true;
    }
    public BodyActionSnapshot snapshot() { return snapshot; }
    public String feedback() { return feedback; }
    public boolean pending() { return pendingSequence != 0; }
    public static long remainingCooldown(BodyActionSnapshot source, BodyActionSnapshot.Entry entry, long localElapsedTicks) {
        if (source == null || entry == null || localElapsedTicks < 0) return 0;
        long elapsedTime = source.serverGameTime() > Long.MAX_VALUE - localElapsedTicks
                ? Long.MAX_VALUE : source.serverGameTime() + localElapsedTicks;
        return Math.max(0, entry.cooldownEndTick() - elapsedTime);
    }
    public long remainingCooldown(BodyActionSnapshot.Entry entry, long localTick) {
        if (snapshot == null || entry == null) return 0;
        long now = advanceClock(localTick);
        long elapsed = now <= snapshotClockAnchor ? 0 : now - snapshotClockAnchor;
        return remainingCooldown(snapshot, entry, elapsed);
    }
}
