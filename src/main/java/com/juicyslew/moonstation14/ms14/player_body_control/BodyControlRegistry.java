package com.juicyslew.moonstation14.ms14.player_body_control;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;

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
    private LifecycleCapability lifecycleCapability;

    /** Binds the sole lifecycle facade for this shared ownership registry. */
    public synchronized LifecycleCapability bindLifecycleFacade(PlayerLifecycleRegistry facade) {
        Objects.requireNonNull(facade, "facade");
        if (lifecycleCapability != null || !facade.ownsRegistry(this))
            throw new IllegalStateException("Lifecycle facade already bound or does not own this registry");
        lifecycleCapability = new LifecycleCapability();
        return lifecycleCapability;
    }

    /** Opaque authority; instances can only be created by this registry's one-time binding. */
    public static final class LifecycleCapability {
        private LifecycleCapability() { }
    }

    private boolean isLifecycleCapability(LifecycleCapability capability) {
        return capability != null && capability == lifecycleCapability;
    }

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

    /** Creates a stable-id Mind atomically on a registered eligible character body. */
    public synchronized Optional<MindSnapshot> createCharacterMind(UUID authenticatedSessionId,
            MindId mindId, MobHarnessId bodyId, TargetEligibility eligibility,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId");
        Objects.requireNonNull(mindId, "mindId");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(eligibility, "eligibility");
        MobHarness body = harnesses.get(bodyId);
        if (mindsBySession.containsKey(authenticatedSessionId) || minds.containsKey(mindId)
                || body == null || body.kind() != MobHarnessKind.CHARACTER
                || !eligibility.isEligible(body) || ownersByHarness.containsKey(bodyId))
            return Optional.empty();
        long epoch = nextGeneration();
        MindState state = new MindState(mindId);
        state.lifecycleOwned = true;
        state.epoch = epoch;
        state.harnessId = bodyId;
        mindsBySession.put(authenticatedSessionId, mindId);
        minds.put(mindId, state);
        ownersByHarness.put(bodyId, mindId);
        return Optional.of(state.snapshot());
    }

    /** Restores an offline durable Mind only through its owning lifecycle facade. */
    public synchronized Optional<MindSnapshot> restoreOfflineCharacterMind(UUID accountId, MindId mindId,
            MobHarnessId bodyId, long durableGenerationFloor, TargetEligibility eligibility,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(mindId, "mindId");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(eligibility, "eligibility");
        if (durableGenerationFloor < 0 || mindsBySession.containsKey(accountId) || minds.containsKey(mindId))
            return Optional.empty();
        MobHarness body = harnesses.get(bodyId);
        if (body == null || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body)
                || ownersByHarness.containsKey(bodyId)) return Optional.empty();
        final long next;
        try {
            next = Math.incrementExact(Math.max(generation, durableGenerationFloor));
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        MindState state = new MindState(mindId);
        state.lifecycleOwned = true;
        state.connected = false;
        state.epoch = next;
        state.harnessId = bodyId;
        generation = next;
        mindsBySession.put(accountId, mindId);
        minds.put(mindId, state);
        ownersByHarness.put(bodyId, mindId);
        return Optional.of(state.snapshot());
    }

    /** Stages a first-enrollment Mind on its reserved body without granting network authority. */
    public synchronized Optional<MindSnapshot> stageFirstCharacterMind(UUID accountId, MindId mindId,
            MobHarnessId bodyId, long durableGenerationFloor, TargetEligibility eligibility,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(mindId, "mindId");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(eligibility, "eligibility");
        if (durableGenerationFloor < 0 || mindsBySession.containsKey(accountId) || minds.containsKey(mindId))
            return Optional.empty();
        MobHarness body = harnesses.get(bodyId);
        if (body == null || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body)
                || ownersByHarness.containsKey(bodyId)) return Optional.empty();
        final long next;
        try {
            next = Math.incrementExact(Math.max(generation, durableGenerationFloor));
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        MindState state = new MindState(mindId);
        state.lifecycleOwned = true;
        state.connected = false;
        state.epoch = next;
        state.harnessId = bodyId;
        generation = next;
        mindsBySession.put(accountId, mindId);
        minds.put(mindId, state);
        ownersByHarness.put(bodyId, mindId);
        return Optional.of(state.snapshot());
    }

    /** Read-only validation and generation allocation for durable first-enrollment promotion. */
    public synchronized Optional<ReconnectPreview> previewFirstEnrollmentPromotion(UUID accountId,
            MindId mindId, MobHarnessId bodyId, long expectedEpoch, long durableGenerationFloor,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || durableGenerationFloor < 0) return Optional.empty();
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(Objects.requireNonNull(accountId, "accountId"));
        MobHarness body = mind == null || mind.harnessId == null ? null : harnesses.get(mind.harnessId);
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.deadClaim
                || !mind.id.equals(mindId) || !bodyId.equals(mind.harnessId) || mind.epoch != expectedEpoch
                || !mind.id.equals(ownersByHarness.get(bodyId)) || body == null
                || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body))
            return Optional.empty();
        try {
            return Optional.of(new ReconnectPreview(accountId, mind.id, bodyId, expectedEpoch,
                    Math.incrementExact(Math.max(generation, durableGenerationFloor))));
        } catch (ArithmeticException overflow) { return Optional.empty(); }
    }

    /** Applies a staged-enrollment preview only after its complete durable snapshot was committed. */
    public synchronized Optional<MindSnapshot> commitFirstEnrollmentPromotion(ReconnectPreview preview,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || preview == null) return Optional.empty();
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(preview.sessionId);
        MobHarness body = mind == null ? null : harnesses.get(preview.bodyId);
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.deadClaim
                || !mind.id.equals(preview.mindId) || !preview.bodyId.equals(mind.harnessId)
                || mind.epoch != preview.expectedEpoch || !mind.id.equals(ownersByHarness.get(preview.bodyId))
                || body == null || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body)
                || preview.nextEpoch <= generation) return Optional.empty();
        generation = preview.nextEpoch;
        mind.epoch = preview.nextEpoch;
        mind.connected = true;
        return Optional.of(mind.snapshot());
    }

    /** Unauthenticated callers cannot create lifecycle-owned Minds. */
    public synchronized Optional<MindSnapshot> createCharacterMind(UUID session, MindId id,
            MobHarnessId body, TargetEligibility eligibility) {
        return Optional.empty();
    }

    /** Lifecycle disconnect is nondestructive: the Mind retains its body, but cannot authorize. */
    public synchronized Optional<MindSnapshot> disconnectLifecycle(UUID sessionId, long expectedEpoch,
                                                                     LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        if (mind == null || mind.epoch != expectedEpoch || !mind.connected)
            return Optional.empty();
        long epoch = nextGeneration();
        mind.epoch = epoch;
        mind.connected = false;
        return Optional.of(mind.snapshot());
    }

    public synchronized Optional<MindSnapshot> disconnectLifecycle(UUID sessionId, long expectedEpoch) {
        return Optional.empty();
    }

    /** Rebinds a disconnected stable Mind; active claims are never stolen. */
    public synchronized Optional<MindSnapshot> reconnectLifecycle(UUID sessionId, long expectedEpoch,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        MobHarness body = mind == null || mind.harnessId == null ? null : harnesses.get(mind.harnessId);
        if (mind == null || mind.epoch != expectedEpoch || mind.connected || mind.deadClaim || mind.harnessId == null
                || !mind.id.equals(ownersByHarness.get(mind.harnessId)) || body == null
                || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body))
            return Optional.empty();
        long epoch = nextGeneration();
        mind.epoch = epoch;
        mind.connected = true;
        return Optional.of(mind.snapshot());
    }

    /** Read-only reconnect validation and epoch allocation for the durable lifecycle transaction. */
    public synchronized Optional<ReconnectPreview> previewReconnect(UUID sessionId, long expectedEpoch,
            long durableGenerationFloor, TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || durableGenerationFloor < 0) return Optional.empty();
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        MobHarness body = mind == null || mind.harnessId == null ? null : harnesses.get(mind.harnessId);
        if (mind == null || !mind.lifecycleOwned || mind.epoch != expectedEpoch || mind.connected
                || mind.deadClaim || mind.harnessId == null
                || !mind.id.equals(ownersByHarness.get(mind.harnessId)) || body == null
                || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body))
            return Optional.empty();
        final long next;
        try {
            next = Math.incrementExact(Math.max(generation, durableGenerationFloor));
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        return Optional.of(new ReconnectPreview(sessionId, mind.id, mind.harnessId, expectedEpoch, next));
    }

    /** Read-only durable-disconnect validation and generation allocation. */
    public synchronized Optional<DisconnectPreview> previewDisconnect(UUID sessionId, long expectedEpoch,
            long durableGenerationFloor, TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || durableGenerationFloor < 0) return Optional.empty();
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        MobHarness body = mind == null || mind.harnessId == null ? null : harnesses.get(mind.harnessId);
        if (mind == null || !mind.lifecycleOwned || !mind.connected || mind.deadClaim
                || mind.epoch != expectedEpoch || mind.harnessId == null
                || !mind.id.equals(ownersByHarness.get(mind.harnessId)) || body == null
                || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body))
            return Optional.empty();
        final long next;
        try {
            next = Math.incrementExact(Math.max(generation, durableGenerationFloor));
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        return Optional.of(new DisconnectPreview(sessionId, mind.id, mind.harnessId, expectedEpoch, next));
    }

    /** Applies an unchanged durable-disconnect preview after its complete snapshot was persisted. */
    public synchronized Optional<MindSnapshot> commitDisconnectAtExpectedGeneration(DisconnectPreview preview,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || preview == null) return Optional.empty();
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(preview.sessionId);
        MobHarness body = mind == null ? null : harnesses.get(preview.bodyId);
        if (mind == null || !mind.lifecycleOwned || !mind.connected || mind.deadClaim
                || !mind.id.equals(preview.mindId) || !preview.bodyId.equals(mind.harnessId)
                || mind.epoch != preview.expectedEpoch || !mind.id.equals(ownersByHarness.get(preview.bodyId))
                || body == null || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body)
                || preview.nextEpoch <= generation)
            return Optional.empty();
        generation = preview.nextEpoch;
        mind.epoch = preview.nextEpoch;
        mind.connected = false;
        return Optional.of(mind.snapshot());
    }

    /**
     * Revokes lifecycle session authorization after a durable transition whose memory commit failed.
     * This deliberately does not call external eligibility, allocate a generation, or release the body.
     */
    public synchronized boolean suspendLifecycleForRecovery(UUID sessionId, MindId expectedMindId,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return false;
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        if (mind == null || !mind.lifecycleOwned || !mind.id.equals(Objects.requireNonNull(expectedMindId, "expectedMindId")))
            return false;
        mind.connected = false;
        return true;
    }

    /**
     * Callback-free exact-session revocation for fail-closed lifecycle recovery. The attached body
     * claim is intentionally retained; no generation is allocated and no world/body operation runs.
     */
    public synchronized boolean suspendLifecycleForRecovery(UUID sessionId, MindId expectedMindId,
            MobHarnessId expectedBodyId, long expectedEpoch, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return false;
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        if (mind == null || !mind.lifecycleOwned || !mind.connected || mind.epoch != expectedEpoch
                || !mind.id.equals(Objects.requireNonNull(expectedMindId, "expectedMindId"))
                || !Objects.equals(mind.harnessId, Objects.requireNonNull(expectedBodyId, "expectedBodyId"))
                || !mind.id.equals(ownersByHarness.get(expectedBodyId))) return false;
        mind.connected = false;
        return true;
    }

    public static final class DisconnectPreview {
        private final UUID sessionId;
        private final MindId mindId;
        private final MobHarnessId bodyId;
        private final long expectedEpoch;
        private final long nextEpoch;
        private DisconnectPreview(UUID sessionId, MindId mindId, MobHarnessId bodyId,
                long expectedEpoch, long nextEpoch) {
            this.sessionId = sessionId; this.mindId = mindId; this.bodyId = bodyId;
            this.expectedEpoch = expectedEpoch; this.nextEpoch = nextEpoch;
        }
        public long nextEpoch() { return nextEpoch; }
    }

    /** Applies an unchanged preview; callers persist its complete snapshot before invoking this. */
    public synchronized Optional<MindSnapshot> commitReconnectAtExpectedGeneration(ReconnectPreview preview,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || preview == null) return Optional.empty();
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(preview.sessionId);
        MobHarness body = mind == null ? null : harnesses.get(preview.bodyId);
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.deadClaim
                || !mind.id.equals(preview.mindId) || !preview.bodyId.equals(mind.harnessId)
                || mind.epoch != preview.expectedEpoch || !mind.id.equals(ownersByHarness.get(preview.bodyId))
                || body == null || body.kind() != MobHarnessKind.CHARACTER || !eligibility.isEligible(body)
                || preview.nextEpoch <= generation)
            return Optional.empty();
        generation = preview.nextEpoch;
        mind.epoch = preview.nextEpoch;
        mind.connected = true;
        return Optional.of(mind.snapshot());
    }

    public static final class ReconnectPreview {
        private final UUID sessionId;
        private final MindId mindId;
        private final MobHarnessId bodyId;
        private final long expectedEpoch;
        private final long nextEpoch;
        private ReconnectPreview(UUID sessionId, MindId mindId, MobHarnessId bodyId,
                long expectedEpoch, long nextEpoch) {
            this.sessionId = sessionId; this.mindId = mindId; this.bodyId = bodyId;
            this.expectedEpoch = expectedEpoch; this.nextEpoch = nextEpoch;
        }
        public long nextEpoch() { return nextEpoch; }
    }

    public synchronized Optional<MindSnapshot> reconnectLifecycle(UUID sessionId, long expectedEpoch,
            TargetEligibility eligibility) { return Optional.empty(); }

    /**
     * Transfers the same Mind to a fresh registered ghost after caller-supplied evidence confirms
     * actual world death. This registry cannot inspect world health; the caller owns that check.
     */
    public synchronized Optional<MindSnapshot> transferActualDeath(UUID sessionId, long expectedEpoch,
            MobHarnessId ghostId, TargetEligibility eligibility, Predicate<MobHarness> actualDeathEvidence,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(ghostId, "ghostId");
        Objects.requireNonNull(eligibility, "eligibility");
        Objects.requireNonNull(actualDeathEvidence, "actualDeathEvidence");
        MindState mind = mindFor(sessionId);
        MobHarness oldBody = mind == null || mind.harnessId == null ? null : harnesses.get(mind.harnessId);
        MobHarness ghost = harnesses.get(ghostId);
        if (mind == null || !mind.connected || mind.epoch != expectedEpoch || oldBody == null
                || !actualDeathEvidence.test(oldBody) || ghost == null
                || ghost.kind() != MobHarnessKind.GHOST || !eligibility.isEligible(ghost)
                || ownersByHarness.containsKey(ghostId))
            return Optional.empty();
        long epoch = nextGeneration();
        ownersByHarness.remove(mind.harnessId);
        mind.harnessId = ghostId;
        mind.epoch = epoch;
        ownersByHarness.put(ghostId, mind.id);
        return Optional.of(mind.snapshot());
    }

    public synchronized Optional<MindSnapshot> transferActualDeath(UUID sessionId, long expectedEpoch,
            MobHarnessId ghostId, TargetEligibility eligibility, Predicate<MobHarness> evidence) {
        return Optional.empty();
    }

    /** Reconnects an offline death claim onto a fresh eligible ghost, leaving the corpse owned nowhere. */
    public synchronized Optional<MindSnapshot> reconnectDeadClaim(UUID sessionId, long expectedEpoch,
            MobHarnessId ghostId, TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(ghostId, "ghostId");
        Objects.requireNonNull(eligibility, "eligibility");
        MindState mind = mindFor(sessionId);
        MobHarness ghost = harnesses.get(ghostId);
        if (mind == null || mind.connected || mind.epoch != expectedEpoch || mind.harnessId == null
                || !mind.deadClaim
                || ghost == null || ghost.kind() != MobHarnessKind.GHOST || !eligibility.isEligible(ghost)
                || ownersByHarness.containsKey(ghostId))
            return Optional.empty();
        long epoch = nextGeneration();
        ownersByHarness.remove(mind.harnessId);
        mind.harnessId = ghostId;
        mind.epoch = epoch;
        mind.connected = true;
        mind.deadClaim = false;
        ownersByHarness.put(ghostId, mind.id);
        return Optional.of(mind.snapshot());
    }

    /** Creates/restores a disconnected dead-claim Mind directly on its registered ghost. */
    public synchronized Optional<MindSnapshot> stageDeadClaimGhost(UUID accountId, MindId mindId,
            MobHarnessId corpseId, MobHarnessId ghostId, long durableGenerationFloor,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || durableGenerationFloor < 0 || ghostId.equals(corpseId))
            return Optional.empty();
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(mindId, "mindId");
        Objects.requireNonNull(eligibility, "eligibility");
        MobHarness ghost = harnesses.get(ghostId);
        if (ghost == null || ghost.kind() != MobHarnessKind.GHOST || !eligibility.isEligible(ghost)
                || ownersByHarness.containsKey(ghostId)) return Optional.empty();
        MindState existing = minds.get(mindId);
        if (existing == null) {
            if (mindsBySession.containsKey(accountId)) return Optional.empty();
            if (minds.values().stream().anyMatch(m -> m.id.equals(mindId))) return Optional.empty();
            try { generation = Math.incrementExact(Math.max(generation, durableGenerationFloor)); }
            catch (ArithmeticException overflow) { return Optional.empty(); }
            existing = new MindState(mindId);
            existing.lifecycleOwned = true;
            existing.connected = false;
            existing.deadClaim = false;
            existing.epoch = generation;
            existing.harnessId = ghostId;
            mindsBySession.put(accountId, mindId);
            minds.put(mindId, existing);
            ownersByHarness.put(ghostId, mindId);
            return Optional.of(existing.snapshot());
        }
        if (!existing.lifecycleOwned || existing.connected || !existing.deadClaim
                || !Objects.equals(existing.harnessId, corpseId)
                || !mindId.equals(ownersByHarness.get(corpseId))
                || !mindId.equals(mindsBySession.get(accountId))) return Optional.empty();
        final long next;
        try { next = Math.incrementExact(Math.max(generation, durableGenerationFloor)); }
        catch (ArithmeticException overflow) { return Optional.empty(); }
        ownersByHarness.remove(corpseId);
        existing.harnessId = ghostId;
        existing.deadClaim = false;
        existing.epoch = next;
        generation = next;
        ownersByHarness.put(ghostId, mindId);
        mindsBySession.put(accountId, mindId);
        return Optional.of(existing.snapshot());
    }

    /** Read-only generation allocation for the durable ghost authorization barrier. */
    public synchronized Optional<ReconnectPreview> previewDeadClaimGhostActivation(UUID accountId,
            MindId mindId, MobHarnessId ghostId, long expectedEpoch, long durableGenerationFloor,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || durableGenerationFloor < 0) return Optional.empty();
        MindState mind = mindFor(Objects.requireNonNull(accountId));
        MobHarness ghost = harnesses.get(ghostId);
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.deadClaim
                || !mind.id.equals(mindId) || !ghostId.equals(mind.harnessId) || mind.epoch != expectedEpoch
                || !mind.id.equals(ownersByHarness.get(ghostId)) || ghost == null
                || ghost.kind() != MobHarnessKind.GHOST || !eligibility.isEligible(ghost)) return Optional.empty();
        try { return Optional.of(new ReconnectPreview(accountId, mindId, ghostId, expectedEpoch,
                Math.incrementExact(Math.max(generation, durableGenerationFloor)))); }
        catch (ArithmeticException overflow) { return Optional.empty(); }
    }

    /** Connects only the exact staged ghost after its durable DEAD_CLAIM row was advanced. */
    public synchronized Optional<MindSnapshot> commitDeadClaimGhostActivation(ReconnectPreview preview,
            TargetEligibility eligibility, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || preview == null) return Optional.empty();
        MindState mind = mindFor(preview.sessionId);
        MobHarness ghost = harnesses.get(preview.bodyId);
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.deadClaim
                || !mind.id.equals(preview.mindId) || !preview.bodyId.equals(mind.harnessId)
                || mind.epoch != preview.expectedEpoch || !mind.id.equals(ownersByHarness.get(preview.bodyId))
                || ghost == null || ghost.kind() != MobHarnessKind.GHOST || !eligibility.isEligible(ghost)
                || preview.nextEpoch <= generation) return Optional.empty();
        generation = preview.nextEpoch;
        mind.epoch = preview.nextEpoch;
        mind.connected = true;
        return Optional.of(mind.snapshot());
    }

    /** Returns an exact active durable ghost session to its retained corpse claim without advancing epoch. */
    public synchronized Optional<MindSnapshot> returnGhostToDeadClaim(UUID accountId, MindId expectedMindId,
            MobHarnessId ghostId, MobHarnessId corpseId, long expectedEpoch, LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return Optional.empty();
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(expectedMindId, "expectedMindId");
        Objects.requireNonNull(ghostId, "ghostId");
        Objects.requireNonNull(corpseId, "corpseId");
        MindState mind = mindFor(accountId);
        if (mind == null || !mind.lifecycleOwned || !mind.connected || mind.epoch != expectedEpoch
                || !mind.id.equals(expectedMindId)) return Optional.empty();
        MobHarness ghost = harnesses.get(ghostId);
        MobHarness corpse = harnesses.get(corpseId);
        if (!ghostId.equals(mind.harnessId) || !expectedMindId.equals(ownersByHarness.get(ghostId))
                || ghost == null || ghost.kind() != MobHarnessKind.GHOST || corpse == null
                || corpse.kind() != MobHarnessKind.CHARACTER || ghostId.equals(corpseId)
                || ownersByHarness.containsKey(corpseId)) {
            mind.connected = false;
            return Optional.empty();
        }
        ownersByHarness.remove(ghostId);
        ownersByHarness.put(corpseId, expectedMindId);
        mind.harnessId = corpseId;
        mind.connected = false;
        mind.deadClaim = true;
        return Optional.of(mind.snapshot());
    }

    public synchronized Optional<MindSnapshot> reconnectDeadClaim(UUID sessionId, long expectedEpoch,
            MobHarnessId ghostId, TargetEligibility eligibility) { return Optional.empty(); }

    /** Marks an offline lifecycle-owned Mind as a death claim without binding it to a ghost. */
    public synchronized boolean claimOfflineDeath(UUID sessionId, long expectedEpoch,
                                                   LifecycleCapability capability) {
        if (!isLifecycleCapability(capability)) return false;
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.epoch != expectedEpoch
                || mind.harnessId == null || !mind.id.equals(ownersByHarness.get(mind.harnessId)))
            return false;
        mind.deadClaim = true;
        return true;
    }

    /** Read-only allocation for the durable death-claim transition. */
    public synchronized Optional<DeathClaimPreview> previewOfflineDeathClaim(UUID sessionId, MindId expectedMindId,
            MobHarnessId expectedBodyId, long expectedEpoch, long durableGenerationFloor,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || durableGenerationFloor < 0) return Optional.empty();
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        MobHarness body = harnesses.get(Objects.requireNonNull(expectedBodyId, "expectedBodyId"));
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.deadClaim
                || !mind.id.equals(Objects.requireNonNull(expectedMindId, "expectedMindId"))
                || !expectedBodyId.equals(mind.harnessId) || mind.epoch != expectedEpoch
                || !mind.id.equals(ownersByHarness.get(expectedBodyId)) || body == null
                || body.kind() != MobHarnessKind.CHARACTER) return Optional.empty();
        try {
            return Optional.of(new DeathClaimPreview(sessionId, mind.id, expectedBodyId, expectedEpoch,
                    Math.incrementExact(Math.max(generation, durableGenerationFloor))));
        } catch (ArithmeticException overflow) { return Optional.empty(); }
    }

    /** Applies the exact preview only after the full durable profile batch was committed. */
    public synchronized Optional<MindSnapshot> commitOfflineDeathClaim(DeathClaimPreview preview,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || preview == null) return Optional.empty();
        MindState mind = mindFor(preview.sessionId);
        MobHarness body = harnesses.get(preview.bodyId);
        if (mind == null || !mind.lifecycleOwned || mind.connected || mind.deadClaim
                || !mind.id.equals(preview.mindId) || !preview.bodyId.equals(mind.harnessId)
                || mind.epoch != preview.expectedEpoch || !mind.id.equals(ownersByHarness.get(preview.bodyId))
                || body == null || body.kind() != MobHarnessKind.CHARACTER || preview.nextEpoch <= generation)
            return Optional.empty();
        generation = preview.nextEpoch;
        mind.epoch = preview.nextEpoch;
        mind.deadClaim = true;
        return Optional.of(mind.snapshot());
    }

    public static final class DeathClaimPreview {
        private final UUID sessionId;
        private final MindId mindId;
        private final MobHarnessId bodyId;
        private final long expectedEpoch;
        private final long nextEpoch;
        private DeathClaimPreview(UUID sessionId, MindId mindId, MobHarnessId bodyId,
                long expectedEpoch, long nextEpoch) {
            this.sessionId = sessionId; this.mindId = mindId; this.bodyId = bodyId;
            this.expectedEpoch = expectedEpoch; this.nextEpoch = nextEpoch;
        }
        public long nextEpoch() { return nextEpoch; }
    }

    /** Read-only allocation for a connected durable death-claim transition. */
    public synchronized Optional<DeathClaimPreview> previewActiveDeathClaim(UUID sessionId, MindId expectedMindId,
            MobHarnessId expectedBodyId, long expectedEpoch, long durableGenerationFloor,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || durableGenerationFloor < 0) return Optional.empty();
        MindState mind = mindFor(Objects.requireNonNull(sessionId, "sessionId"));
        MobHarness body = harnesses.get(Objects.requireNonNull(expectedBodyId, "expectedBodyId"));
        if (mind == null || !mind.lifecycleOwned || !mind.connected || mind.deadClaim
                || !mind.id.equals(Objects.requireNonNull(expectedMindId, "expectedMindId"))
                || !expectedBodyId.equals(mind.harnessId) || mind.epoch != expectedEpoch
                || !mind.id.equals(ownersByHarness.get(expectedBodyId)) || body == null
                || body.kind() != MobHarnessKind.CHARACTER) return Optional.empty();
        try {
            return Optional.of(new DeathClaimPreview(sessionId, mind.id, expectedBodyId, expectedEpoch,
                    Math.incrementExact(Math.max(generation, durableGenerationFloor))));
        } catch (ArithmeticException overflow) { return Optional.empty(); }
    }

    /** Applies the exact durable connected-death preview, retaining the corpse claim but revoking session authority. */
    public synchronized Optional<MindSnapshot> commitActiveDeathClaim(DeathClaimPreview preview,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || preview == null) return Optional.empty();
        MindState mind = mindFor(preview.sessionId);
        MobHarness body = harnesses.get(preview.bodyId);
        if (mind == null || !mind.lifecycleOwned || !mind.connected || mind.deadClaim
                || !mind.id.equals(preview.mindId) || !preview.bodyId.equals(mind.harnessId)
                || mind.epoch != preview.expectedEpoch || !mind.id.equals(ownersByHarness.get(preview.bodyId))
                || body == null || body.kind() != MobHarnessKind.CHARACTER || preview.nextEpoch <= generation)
            return Optional.empty();
        generation = preview.nextEpoch;
        mind.epoch = preview.nextEpoch;
        mind.connected = false;
        mind.deadClaim = true;
        return Optional.of(mind.snapshot());
    }

    public synchronized boolean claimOfflineDeath(UUID sessionId, long expectedEpoch) { return false; }

    public synchronized boolean registerHarness(MobHarness harness) {
        Objects.requireNonNull(harness, "harness");
        return harnesses.putIfAbsent(harness.id(), harness) == null;
    }

    public synchronized Optional<MobHarness> registeredHarness(MobHarnessId id) {
        return Optional.ofNullable(harnesses.get(Objects.requireNonNull(id, "id")));
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
        if (mind.lifecycleOwned)
            return OperationResult.ATTACHED_HARNESS_REQUIRED;
        if (!mind.connected)
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
        if (mind.lifecycleOwned)
            return OperationResult.ATTACHED_HARNESS_REQUIRED;
        if (!mind.connected)
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
        if (mind.lifecycleOwned)
            return OperationResult.ATTACHED_HARNESS_REQUIRED;
        if (!mind.connected)
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
        if (mind.lifecycleOwned)
            return OperationResult.ATTACHED_HARNESS_REQUIRED;
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
        if (mind.lifecycleOwned)
            return OperationResult.ATTACHED_HARNESS_REQUIRED;
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
        if (mind == null || !mind.connected || mind.epoch != epoch || !harnessId.equals(mind.harnessId)
                || !mind.id.equals(ownersByHarness.get(harnessId)))
            return false;
        MobHarness target = harnesses.get(harnessId);
        if (target != null && eligibility.isEligible(target))
            return true;
        if (mind.lifecycleOwned)
            return false;
        long nextEpoch = nextGeneration();
        ownersByHarness.remove(harnessId);
        mind.harnessId = null;
        mind.epoch = nextEpoch;
        return false;
    }

    /** Checks current ownership and eligibility without revoking or otherwise mutating a binding. */
    public synchronized boolean authorizesReadOnly(UUID sessionId, MobHarnessId harnessId, long epoch,
                                                    TargetEligibility eligibility) {
        if (sessionId == null || harnessId == null || eligibility == null) return false;
        MindState mind = mindFor(sessionId);
        if (mind == null || !mind.connected || mind.epoch != epoch || !harnessId.equals(mind.harnessId)
                || !mind.id.equals(ownersByHarness.get(harnessId))) return false;
        MobHarness target = harnesses.get(harnessId);
        return target != null && eligibility.isEligible(target);
    }

    /** Exact lifecycle-owned authorization query; this method never performs recovery mutation. */
    public synchronized boolean authorizesLifecycleReadOnly(UUID sessionId, MindId expectedMindId,
            MobHarnessId harnessId, long epoch, TargetEligibility eligibility,
            LifecycleCapability capability) {
        if (!isLifecycleCapability(capability) || sessionId == null || expectedMindId == null
                || harnessId == null || eligibility == null) return false;
        MindState mind = mindFor(sessionId);
        if (mind == null || !mind.lifecycleOwned || !mind.id.equals(expectedMindId) || !mind.connected
                || mind.epoch != epoch || !harnessId.equals(mind.harnessId)
                || !mind.id.equals(ownersByHarness.get(harnessId))) return false;
        MobHarness target = harnesses.get(harnessId);
        return target != null && eligibility.isEligible(target);
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
        private boolean connected = true;
        private boolean lifecycleOwned;
        private boolean deadClaim;

        private MindState(MindId id) {
            this.id = id;
        }

        private MindSnapshot snapshot() {
            return new MindSnapshot(id, harnessId, epoch);
        }
    }
}
