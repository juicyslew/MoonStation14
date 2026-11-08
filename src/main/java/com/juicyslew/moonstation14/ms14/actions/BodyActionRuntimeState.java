package com.juicyslew.moonstation14.ms14.actions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Body-owned volatile state for action cooldowns, snapshots, and request deduplication. */
public final class BodyActionRuntimeState {
    private final Map<CooldownKey, Long> cooldownEnds = new HashMap<>();
    private long snapshotRevision;
    private Object snapshotFingerprint;
    private long controlEpoch;
    private java.util.UUID controller;
    private long acceptedSequence = -1;

    public long cooldownEnd(String actionId, String providerId) {
        return cooldownEnds.getOrDefault(new CooldownKey(actionId, providerId), 0L);
    }

    public boolean isOnCooldown(String actionId, String providerId, long worldTick) {
        return worldTick < cooldownEnd(actionId, providerId);
    }

    public void setCooldownEnd(String actionId, String providerId, long endTick) {
        CooldownKey key = new CooldownKey(actionId, providerId);
        if (endTick <= 0) cooldownEnds.remove(key);
        else cooldownEnds.put(key, endTick);
    }

    /** Updates the action-view fingerprint and advances the revision only when it changes. */
    public long updateSnapshotFingerprint(Object fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (!Objects.equals(snapshotFingerprint, fingerprint)) {
            snapshotFingerprint = fingerprint;
            snapshotRevision++;
        }
        return snapshotRevision;
    }

    public long snapshotRevision() { return snapshotRevision; }

    public boolean acceptRequestSequence(java.util.UUID controllerId, long epoch, long sequence) {
        Objects.requireNonNull(controllerId, "controllerId");
        if (!isRequestSequenceNew(controllerId, epoch, sequence)) return false;
        controller = controllerId;
        controlEpoch = epoch;
        acceptedSequence = sequence;
        return true;
    }

    public boolean isRequestSequenceNew(java.util.UUID controllerId, long epoch, long sequence) {
        Objects.requireNonNull(controllerId, "controllerId");
        return (!controllerId.equals(controller) || controlEpoch != epoch) || sequence > acceptedSequence;
    }

    /** Legacy controller-less sequence acceptance. */
    public boolean acceptRequestSequence(long epoch, long sequence) {
        if (controlEpoch != epoch) {
            controller = null;
            controlEpoch = epoch;
            acceptedSequence = -1;
        }
        if (sequence <= acceptedSequence) return false;
        acceptedSequence = sequence;
        return true;
    }

    public record CooldownKey(String actionId, String providerId) {
        public CooldownKey {
            Objects.requireNonNull(actionId, "actionId");
            Objects.requireNonNull(providerId, "providerId");
        }
    }
}
