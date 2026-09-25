package com.juicyslew.moonstation14.ms14.player_body_control;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronized, in-memory ownership policy. This pure model does not authenticate packets or
 * inspect world entities; callers must authenticate sessions and supply live target validation.
 */
public final class BodyControlRegistry {
    private final Map<UUID, MindId> mindsBySession = new HashMap<>();
    private final Map<MindId, MindState> minds = new HashMap<>();
    private final Map<MobHarnessId, MobHarness> harnesses = new HashMap<>();
    private final Map<MobHarnessId, MindId> ownersByHarness = new HashMap<>();
    private long generation;

    /** Creates an already ghost-attached mind; rejected requests have no registry side effects. */
    public synchronized Optional<MindSnapshot> createMind(UUID authenticatedSessionId,
                                                          MobHarnessId initialGhostId,
                                                          TargetEligibility eligibility) {
        Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId");
        Objects.requireNonNull(initialGhostId, "initialGhostId");
        Objects.requireNonNull(eligibility, "eligibility");
        MobHarness initial = eligibleGhost(initialGhostId, eligibility);
        if (mindsBySession.containsKey(authenticatedSessionId) || initial == null
                || ownersByHarness.containsKey(initialGhostId))
            return Optional.empty();

        MindId id;
        do {
            id = new MindId(UUID.randomUUID());
        } while (id.value().equals(authenticatedSessionId) || minds.containsKey(id));
        long epoch = nextGeneration();
        MindState state = new MindState(id);
        state.epoch = epoch;
        state.harnessId = initial.id();
        mindsBySession.put(authenticatedSessionId, id);
        minds.put(id, state);
        ownersByHarness.put(initial.id(), id);
        return Optional.of(state.snapshot());
    }

    public synchronized boolean registerHarness(MobHarness harness) {
        Objects.requireNonNull(harness, "harness");
        return harnesses.putIfAbsent(harness.id(), harness) == null;
    }

    /** Removes a harness and atomically detaches its owner, if any. Repeated removal is harmless. */
    public synchronized OperationResult unregisterHarness(MobHarnessId harnessId) {
        Objects.requireNonNull(harnessId, "harnessId");
        MobHarness removed = harnesses.get(harnessId);
        if (removed == null)
            return OperationResult.UNKNOWN_HARNESS;
        MindId owner = ownersByHarness.get(harnessId);
        MindState mind = owner == null ? null : minds.get(owner);
        long nextEpoch = nextGeneration();
        harnesses.remove(harnessId);
        ownersByHarness.remove(harnessId);
        if (mind != null) {
            mind.harnessId = null;
            mind.epoch = nextEpoch;
        }
        return OperationResult.CHANGED;
    }

    public synchronized OperationResult attach(UUID sessionId, MobHarnessId targetId,
                                               long expectedEpoch, TargetEligibility eligibility) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(sessionId);
        if (mind == null)
            return OperationResult.UNKNOWN_SESSION;
        if (mind.epoch != expectedEpoch)
            return OperationResult.STALE_EPOCH;
        if (mind.harnessId != null) {
            if (mind.harnessId.equals(targetId)) {
                MobHarness current = harnesses.get(targetId);
                MindId owner = ownersByHarness.get(targetId);
                if (current != null && current.kind() != MobHarnessKind.GHOST)
                    return OperationResult.ATTACHED_HARNESS_REQUIRED;
                if (current != null && current.kind() == MobHarnessKind.GHOST
                        && eligibility.isEligible(current) && mind.id.equals(owner))
                    return OperationResult.UNCHANGED;

                long nextEpoch = nextGeneration();
                if (mind.id.equals(owner))
                    ownersByHarness.remove(targetId);
                mind.harnessId = null;
                mind.epoch = nextEpoch;
                if (current == null)
                    return OperationResult.UNKNOWN_HARNESS;
                return OperationResult.INELIGIBLE_TARGET;
            }
            return OperationResult.ATTACHED_HARNESS_REQUIRED;
        }
        MobHarness target = eligibleGhost(targetId, eligibility);
        if (target == null)
            return harnesses.containsKey(targetId) ? OperationResult.INELIGIBLE_TARGET
                    : OperationResult.UNKNOWN_HARNESS;
        if (ownersByHarness.containsKey(targetId))
            return OperationResult.HARNESS_OWNED;
        long nextEpoch = nextGeneration();
        ownersByHarness.put(targetId, mind.id);
        mind.harnessId = targetId;
        mind.epoch = nextEpoch;
        return OperationResult.CHANGED;
    }

    /** Transfer is the same atomic ownership transition as attach, including ghost/character changes. */
    public synchronized OperationResult transfer(UUID sessionId, MobHarnessId targetId,
                                                  long expectedEpoch, TargetEligibility eligibility) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        if (mind == null)
            return OperationResult.UNKNOWN_SESSION;
        if (mind.epoch != expectedEpoch)
            return OperationResult.STALE_EPOCH;
        if (mind.harnessId == null)
            return OperationResult.ATTACHED_HARNESS_REQUIRED;
        return transition(sessionId, targetId, expectedEpoch, eligibility);
    }

    private OperationResult transition(UUID sessionId, MobHarnessId targetId, long expectedEpoch,
                                       TargetEligibility eligibility) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(sessionId);
        if (mind == null)
            return OperationResult.UNKNOWN_SESSION;
        if (mind.epoch != expectedEpoch)
            return OperationResult.STALE_EPOCH;

        MobHarness target = harnesses.get(targetId);
        if (target == null)
            return OperationResult.UNKNOWN_HARNESS;
        if (!eligibility.isEligible(target))
            return OperationResult.INELIGIBLE_TARGET;
        MindId owner = ownersByHarness.get(targetId);
        if (owner != null && !owner.equals(mind.id))
            return OperationResult.HARNESS_OWNED;
        if (targetId.equals(mind.harnessId))
            return OperationResult.UNCHANGED;

        long nextEpoch = nextGeneration();
        if (mind.harnessId != null)
            ownersByHarness.remove(mind.harnessId);
        ownersByHarness.put(targetId, mind.id);
        mind.harnessId = targetId;
        mind.epoch = nextEpoch;
        return OperationResult.CHANGED;
    }

    public synchronized OperationResult release(UUID sessionId, long expectedEpoch, ReleaseReason reason) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(reason, "reason");
        if (reason == ReleaseReason.VOLUNTARY)
            return OperationResult.RELEASE_NOT_ALLOWED;
        MindState mind = mindFor(sessionId);
        if (mind == null)
            return OperationResult.UNKNOWN_SESSION;
        if (mind.epoch != expectedEpoch)
            return OperationResult.STALE_EPOCH;
        if (mind.harnessId == null)
            return OperationResult.UNCHANGED;

        long nextEpoch = nextGeneration();
        ownersByHarness.remove(mind.harnessId);
        mind.harnessId = null;
        mind.epoch = nextEpoch;
        return OperationResult.CHANGED;
    }

    /** Logout removes the mind and its ownership; the session must have been authenticated by caller. */
    public synchronized OperationResult logout(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        MindId id = mindsBySession.get(sessionId);
        if (id == null)
            return OperationResult.UNKNOWN_SESSION;
        MindState mind = minds.get(id);
        if (mind.harnessId != null)
            nextGeneration();
        mindsBySession.remove(sessionId);
        minds.remove(id);
        if (mind.harnessId != null)
            ownersByHarness.remove(mind.harnessId);
        return OperationResult.CHANGED;
    }

    public synchronized Optional<MindSnapshot> mind(UUID sessionId) {
        MindState state = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    /** Checks current target eligibility; an invalid owned target is revoked fail-closed. */
    public synchronized boolean authorizes(UUID sessionId, MobHarnessId harnessId, long epoch,
                                           TargetEligibility eligibility) {
        if (sessionId == null || harnessId == null || eligibility == null)
            return false;
        MindState mind = mindFor(sessionId);
        if (mind == null || mind.epoch != epoch || !harnessId.equals(mind.harnessId)
                || !mind.id.equals(ownersByHarness.get(harnessId)))
            return false;
        MobHarness target = harnesses.get(harnessId);
        if (target != null && eligibility.isEligible(target))
            return true;
        long nextEpoch = nextGeneration();
        ownersByHarness.remove(harnessId);
        mind.harnessId = null;
        mind.epoch = nextEpoch;
        return false;
    }

    private MobHarness eligibleGhost(MobHarnessId id, TargetEligibility eligibility) {
        MobHarness target = harnesses.get(id);
        return target != null && target.kind() == MobHarnessKind.GHOST && eligibility.isEligible(target)
                ? target : null;
    }

    private MindState mindFor(UUID sessionId) {
        MindId id = mindsBySession.get(sessionId);
        return id == null ? null : minds.get(id);
    }

    private long nextGeneration() {
        long next = Math.incrementExact(generation);
        generation = next;
        return next;
    }

    public enum OperationResult {
        CHANGED,
        UNCHANGED,
        UNKNOWN_SESSION,
        UNKNOWN_HARNESS,
        INELIGIBLE_TARGET,
        HARNESS_OWNED,
        STALE_EPOCH,
        RELEASE_NOT_ALLOWED,
        ATTACHED_HARNESS_REQUIRED
    }

    public enum ReleaseReason {
        FAILURE,
        LIFECYCLE,
        VOLUNTARY
    }

    public record MindSnapshot(MindId id, MobHarnessId harnessId, long epoch) { }

    private static final class MindState {
        private final MindId id;
        private MobHarnessId harnessId;
        private long epoch;

        private MindState(MindId id) {
            this.id = id;
        }

        private MindSnapshot snapshot() {
            return new MindSnapshot(id, harnessId, epoch);
        }
    }
}
