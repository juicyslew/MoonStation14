package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle;

import com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.TargetEligibility;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleStoreEnvelope;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pure lifecycle policy layered on the shared BodyControlRegistry ownership authority. */
public final class PlayerLifecycleRegistry {
    public static final int SCHEMA_VERSION = 1;

    private final BodyControlRegistry ownership;
    private final BodyControlRegistry.LifecycleCapability capability;
    private final Map<UUID, ProfileRecord> byAccount = new HashMap<>();
    private final Map<ProfileKey, ProfileRecord> byProfile = new HashMap<>();
    private final Map<MindId, ProfileRecord> byMind = new HashMap<>();
    private final Map<MobHarnessId, ProfileRecord> byBody = new HashMap<>();

    public PlayerLifecycleRegistry() { this(new BodyControlRegistry()); }

    public PlayerLifecycleRegistry(BodyControlRegistry ownership) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.capability = ownership.bindLifecycleFacade(this);
    }

    /** Used by the shared registry to ensure only its actual owning facade can bind. */
    public boolean ownsRegistry(BodyControlRegistry candidate) { return ownership == candidate; }

    public boolean registerHarness(MobHarness harness) { return ownership.registerHarness(harness); }

    /** Creates the account claim and stable Mind only on a registered eligible character. */
    public synchronized Optional<Snapshot> create(UUID accountId, String profileId, MindId mindId,
                                                     MobHarnessId bodyId, TargetEligibility eligibility) {
        synchronized (ownership) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(mindId, "mindId");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(eligibility, "eligibility");
        ProfileKey profileKey = new ProfileKey(accountId, profileId);
        if (profileId.isBlank() || byAccount.containsKey(accountId) || byProfile.containsKey(profileKey)
                || byMind.containsKey(mindId) || byBody.containsKey(bodyId))
            return Optional.empty();
        Optional<BodyControlRegistry.MindSnapshot> claim =
                ownership.createCharacterMind(accountId, mindId, bodyId, eligibility, capability);
        if (claim.isEmpty()) return Optional.empty();
        var mind = claim.orElseThrow();
        ProfileRecord record = new ProfileRecord(accountId, profileId, mindId, bodyId,
                LifecycleState.ACTIVE, mind.epoch(), true, false);
        byAccount.put(accountId, record);
        byProfile.put(profileKey, record);
        byMind.put(mindId, record);
        byBody.put(bodyId, record);
        return Optional.of(record.snapshot());
        }
    }

    /**
     * Hydrates one already-validated OFFLINE durable record into memory. The caller must have
     * completed loaded/alive custom-body and authoritative ownership proof before supplying eligibility.
     * This method performs no persistence or world access.
     */
    public synchronized Optional<Snapshot> restoreOffline(LifecycleProfileStore.CurrentPrimary currentPrimary,
            SavedLifecycleProfile saved, TargetEligibility eligibleLoadedCharacter) {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(saved, "saved");
            Objects.requireNonNull(eligibleLoadedCharacter, "eligibleLoadedCharacter");
            if (!currentPrimary.isActive()) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || saved.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || saved.state() != SavedLifecycleProfile.State.OFFLINE
                    || !saved.connectionEpoch().equals(envelope.epochToken())
                    || saved.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().noneMatch(saved::equals))
                return Optional.empty();
            MindId mindId = new MindId(saved.mindId());
            MobHarnessId bodyId = new MobHarnessId(saved.bodyId());
            ProfileKey key = new ProfileKey(saved.accountId(), saved.profileKey());
            if (byAccount.containsKey(saved.accountId()) || byProfile.containsKey(key)
                    || byMind.containsKey(mindId) || byBody.containsKey(bodyId))
                return Optional.empty();
            Optional<BodyControlRegistry.MindSnapshot> restored = ownership.restoreOfflineCharacterMind(
                    saved.accountId(), mindId, bodyId, envelope.generationCounter(),
                    eligibleLoadedCharacter, capability);
            if (restored.isEmpty()) return Optional.empty();
            long generation = restored.orElseThrow().epoch();
        ProfileRecord record = new ProfileRecord(saved.accountId(), saved.profileKey(), mindId, bodyId,
                    LifecycleState.OFFLINE, generation, false, false, true);
            byAccount.put(record.accountId, record);
            byProfile.put(key, record);
            byMind.put(mindId, record);
            byBody.put(bodyId, record);
            return Optional.of(record.snapshot());
        }
    }

    /** Stages the durable corpse claim on a fresh registered ghost without granting session authority. */
    public synchronized Optional<Snapshot> stageDeadClaimGhost(LifecycleProfileStore.CurrentPrimary currentPrimary,
            SavedLifecycleProfile saved, MobHarnessId ghostId, TargetEligibility eligibleGhost) {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(saved, "saved");
            Objects.requireNonNull(ghostId, "ghostId");
            Objects.requireNonNull(eligibleGhost, "eligibleGhost");
            if (!currentPrimary.isActive() || saved.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || saved.state() != SavedLifecycleProfile.State.DEAD_CLAIM || saved.offlineSinceMillis() == null
                    || ghostId.value().equals(saved.bodyId())) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || !saved.connectionEpoch().equals(envelope.epochToken())
                    || saved.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().filter(saved::equals).count() != 1) return Optional.empty();
            MindId mindId = new MindId(saved.mindId());
            ProfileKey key = new ProfileKey(saved.accountId(), saved.profileKey());
            ProfileRecord existing = byAccount.get(saved.accountId());
            boolean sameServerClaim = existing != null && !existing.active && existing.deadClaim
                    && existing.state == LifecycleState.DEAD_CLAIM && existing.mindId.equals(mindId)
                    && existing.profileId.equals(saved.profileKey())
                    && existing.bodyId.equals(new MobHarnessId(saved.bodyId()))
                    && existing.generation == saved.connectionGeneration();
            if ((existing != null && !sameServerClaim) || byProfile.containsKey(key) && !sameServerClaim
                    || byMind.containsKey(mindId) && !sameServerClaim || byBody.containsKey(ghostId))
                return Optional.empty();
            Optional<BodyControlRegistry.MindSnapshot> staged = ownership.stageDeadClaimGhost(saved.accountId(),
                    mindId, new MobHarnessId(saved.bodyId()), ghostId, envelope.generationCounter(),
                    eligibleGhost, capability);
            if (staged.isEmpty()) return Optional.empty();
            ProfileRecord record = existing;
            if (record == null) {
                record = new ProfileRecord(saved.accountId(), saved.profileKey(), mindId, ghostId,
                        LifecycleState.DEAD_CLAIM, staged.orElseThrow().epoch(), false, true, true);
                byAccount.put(record.accountId, record);
                byProfile.put(key, record);
                byMind.put(mindId, record);
            } else {
                byBody.remove(record.bodyId);
                record.bodyId = ghostId;
                record.generation = staged.orElseThrow().epoch();
            }
            record.corpseBodyId = new MobHarnessId(saved.bodyId());
            byBody.put(ghostId, record);
            return Optional.of(record.snapshot());
        }
    }

    /** Advances the exact DEAD_CLAIM row before enabling the already-staged ghost Mind. */
    public synchronized Optional<Snapshot> activateDeadClaimGhostDurably(
            LifecycleProfileStore.CurrentPrimary currentPrimary, SavedLifecycleProfile saved,
            MobHarnessId ghostId, boolean authenticated, TargetEligibility eligibleGhost) throws IOException {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(saved, "saved");
            Objects.requireNonNull(ghostId, "ghostId");
            Objects.requireNonNull(eligibleGhost, "eligibleGhost");
            if (!authenticated || !currentPrimary.isActive() || saved.state() != SavedLifecycleProfile.State.DEAD_CLAIM
                    || saved.offlineSinceMillis() == null || ghostId.value().equals(saved.bodyId())) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || saved.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || !saved.connectionEpoch().equals(envelope.epochToken())
                    || saved.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().filter(saved::equals).count() != 1) return Optional.empty();
            ProfileRecord record = byAccount.get(saved.accountId());
            MindId mindId = new MindId(saved.mindId());
            if (record == null || !record.restoredFromStore || record.active || !record.deadClaim
                    || record.state != LifecycleState.DEAD_CLAIM || record.generation <= saved.connectionGeneration()
                    || !record.mindId.equals(mindId) || !record.profileId.equals(saved.profileKey())
                    || !record.bodyId.equals(ghostId) || !record.corpseBodyId.value().equals(saved.bodyId())) return Optional.empty();
            var mind = ownership.mind(saved.accountId()).orElse(null);
            if (mind == null || !mind.id().equals(mindId) || !ghostId.equals(mind.harnessId())
                    || mind.epoch() != record.generation) return Optional.empty();
            var preview = ownership.previewDeadClaimGhostActivation(saved.accountId(), mindId, ghostId,
                    record.generation, envelope.generationCounter(), eligibleGhost, capability);
            if (preview.isEmpty()) return Optional.empty();
            final long revision;
            try { revision = Math.incrementExact(saved.revision()); }
            catch (ArithmeticException overflow) { return Optional.empty(); }
            long next = preview.orElseThrow().nextEpoch();
            SavedLifecycleProfile continuedClaim = new SavedLifecycleProfile(saved.schemaVersion(),
                    saved.accountId(), saved.profileKey(), saved.mindId(), saved.bodyId(), saved.dimension(),
                    saved.location(), saved.appearance(), saved.connectionEpoch(), next,
                    SavedLifecycleProfile.State.DEAD_CLAIM, revision, saved.offlineSinceMillis());
            ArrayList<SavedLifecycleProfile> profiles = new ArrayList<>(envelope.profiles().size());
            boolean replaced = false;
            for (SavedLifecycleProfile item : envelope.profiles()) {
                if (item.equals(saved)) {
                    if (replaced) return Optional.empty();
                    profiles.add(continuedClaim); replaced = true;
                } else profiles.add(item);
            }
            if (!replaced) return Optional.empty();
            currentPrimary.compareAndSwap(Math.max(envelope.generationCounter(), next), profiles);
            var committed = ownership.commitDeadClaimGhostActivation(preview.orElseThrow(), eligibleGhost, capability);
            if (committed.isEmpty()) {
                ownership.suspendLifecycleForRecovery(saved.accountId(), mindId, capability);
                record.generation = next;
                record.active = false;
                record.deadClaim = false;
                record.state = LifecycleState.RECOVERY_REQUIRED;
                return Optional.empty();
            }
            record.generation = committed.orElseThrow().epoch();
            record.active = true;
            record.deadClaim = true;
            record.state = LifecycleState.GHOST;
            return Optional.of(record.snapshot());
        }
    }

    /** Revokes an active durable ghost back to its exact retained corpse claim; no store write or epoch is allocated. */
    public synchronized Optional<Snapshot> returnGhostToDeadClaim(UUID accountId, MindId mindId,
            MobHarnessId ghostId, MobHarnessId corpseId, long expectedEpoch) {
        synchronized (ownership) {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(mindId, "mindId");
            Objects.requireNonNull(ghostId, "ghostId");
            Objects.requireNonNull(corpseId, "corpseId");
            ProfileRecord record = byAccount.get(accountId);
            if (record == null || !record.restoredFromStore || !record.active || !record.deadClaim
                    || record.state != LifecycleState.GHOST || record.generation != expectedEpoch
                    || !record.mindId.equals(mindId) || !record.bodyId.equals(ghostId)
                    || !corpseId.equals(record.corpseBodyId)) return Optional.empty();

            var mind = ownership.mind(accountId).orElse(null);
            if (mind == null || !mindId.equals(mind.id()) || mind.epoch() != expectedEpoch) {
                ownership.suspendLifecycleForRecovery(accountId, mindId, capability);
                record.active = false;
                record.deadClaim = false;
                record.state = LifecycleState.RECOVERY_REQUIRED;
                return Optional.empty();
            }
            if (!ghostId.equals(mind.harnessId())) {
                ownership.suspendLifecycleForRecovery(accountId, mindId, capability);
                record.active = false;
                record.deadClaim = false;
                record.state = LifecycleState.RECOVERY_REQUIRED;
                return Optional.empty();
            }
            var returned = ownership.returnGhostToDeadClaim(accountId, mindId, ghostId, corpseId,
                    expectedEpoch, capability);
            if (returned.isEmpty()) {
                ownership.suspendLifecycleForRecovery(accountId, mindId, capability);
                record.active = false;
                record.deadClaim = false;
                record.state = LifecycleState.RECOVERY_REQUIRED;
                return Optional.empty();
            }
            byBody.remove(ghostId);
            record.bodyId = corpseId;
            record.active = false;
            record.deadClaim = true;
            record.state = LifecycleState.DEAD_CLAIM;
            byBody.put(corpseId, record);
            return Optional.of(record.snapshot());
        }
    }

    /** Removes only a transient ghost after its Mind has safely returned to the retained corpse. */
    public synchronized boolean unregisterTransientGhost(UUID accountId, MobHarnessId ghostId) {
        synchronized (ownership) {
            ProfileRecord record = byAccount.get(Objects.requireNonNull(accountId, "accountId"));
            if (record == null || record.active
                    || record.state != LifecycleState.DEAD_CLAIM && record.state != LifecycleState.RECOVERY_REQUIRED
                    || record.state == LifecycleState.DEAD_CLAIM && record.bodyId.equals(ghostId)) return false;
            var mind = ownership.mind(accountId).orElse(null);
            if (mind == null || !record.mindId.equals(mind.id())
                    || record.state == LifecycleState.DEAD_CLAIM && !record.bodyId.equals(mind.harnessId())
                    || record.state == LifecycleState.RECOVERY_REQUIRED && !ghostId.equals(mind.harnessId())) return false;
            MobHarness harness = ownership.registeredHarness(ghostId).orElse(null);
            if (harness == null || harness.kind() != MobHarnessKind.GHOST) return false;
            return ownership.unregisterHarness(ghostId) == BodyControlRegistry.OperationResult.CHANGED;
        }
    }

    /**
     * Stages a Mind only for an exact PREPARING reservation read from the locked current primary.
     * The staged Mind/body remain disconnected and cannot authorize; promotion requires a separate
     * proof-scoped durable saga that is intentionally not provided by this staging-only increment.
     */
    public synchronized Optional<Snapshot> stageFirstCharacter(LifecycleProfileStore.CurrentPrimary currentPrimary,
            SavedLifecycleProfile preparing, TargetEligibility eligibleRegisteredCharacter) {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(preparing, "preparing");
            Objects.requireNonNull(eligibleRegisteredCharacter, "eligibleRegisteredCharacter");
            if (!currentPrimary.isActive() || preparing.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || preparing.state() != SavedLifecycleProfile.State.PREPARING
                    || preparing.offlineSinceMillis() != null) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || !preparing.connectionEpoch().equals(envelope.epochToken())
                    || preparing.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().filter(preparing::equals).count() != 1)
                return Optional.empty();
            MindId mindId = new MindId(preparing.mindId());
            MobHarnessId bodyId = new MobHarnessId(preparing.bodyId());
            ProfileKey key = new ProfileKey(preparing.accountId(), preparing.profileKey());
            if (byAccount.containsKey(preparing.accountId()) || byProfile.containsKey(key)
                    || byMind.containsKey(mindId) || byBody.containsKey(bodyId)) return Optional.empty();
            var staged = ownership.stageFirstCharacterMind(preparing.accountId(), mindId, bodyId,
                    envelope.generationCounter(), eligibleRegisteredCharacter, capability);
            if (staged.isEmpty()) return Optional.empty();
            ProfileRecord record = new ProfileRecord(preparing.accountId(), preparing.profileKey(), mindId,
                    bodyId, LifecycleState.PREPARING, staged.orElseThrow().epoch(), false, false, true);
            byAccount.put(record.accountId, record);
            byProfile.put(key, record);
            byMind.put(mindId, record);
            byBody.put(bodyId, record);
            return Optional.of(record.snapshot());
        }
    }

    /**
     * Durably promotes the exact staged PREPARING claim. This is a pure state transition: the caller
     * must already have authenticated the player and proved its loaded/alive world entity binding.
     * Caller eligibility callbacks are deliberately not accepted or run here: durable promotion runs
     * under facade, ownership, and store locks. A future runtime CALLER must prove a live/alive,
     * custom-character, owner-bound entity and main-server-thread execution before entry, then
     * recheck that proof immediately before Begin/Commit when those hooks are wired.
     */
    public synchronized Optional<Snapshot> promoteFirstCharacterDurably(
            LifecycleProfileStore.CurrentPrimary currentPrimary, SavedLifecycleProfile preparing) throws IOException {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(preparing, "preparing");
            if (!currentPrimary.isActive() || preparing.state() != SavedLifecycleProfile.State.PREPARING
                    || preparing.offlineSinceMillis() != null
                    || preparing.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || !preparing.connectionEpoch().equals(envelope.epochToken())
                    || preparing.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().filter(preparing::equals).count() != 1) return Optional.empty();
            ProfileRecord record = byAccount.get(preparing.accountId());
            MindId mindId = new MindId(preparing.mindId());
            MobHarnessId bodyId = new MobHarnessId(preparing.bodyId());
            if (record == null || record.state != LifecycleState.PREPARING || record.active
                    || !record.profileId.equals(preparing.profileKey()) || !record.mindId.equals(mindId)
                    || !record.bodyId.equals(bodyId)) return Optional.empty();
            var mind = ownership.mind(preparing.accountId()).orElse(null);
            if (mind == null || !mind.id().equals(mindId) || !bodyId.equals(mind.harnessId())
                    || mind.epoch() != record.generation) return Optional.empty();
            // This internal check is intentionally pure and cannot call world/application code while
            // the lifecycle, ownership, and store locks are held. The registry separately checks the
            // exact Mind/body ownership and epoch both at preview and commit.
            TargetEligibility exactRegisteredCharacter = target -> target.id().equals(bodyId)
                    && target.kind() == MobHarnessKind.CHARACTER;
            var preview = ownership.previewFirstEnrollmentPromotion(preparing.accountId(), mindId, bodyId,
                    record.generation, envelope.generationCounter(), exactRegisteredCharacter, capability);
            if (preview.isEmpty()) return Optional.empty();
            final long revision;
            try { revision = Math.incrementExact(preparing.revision()); }
            catch (ArithmeticException overflow) { return Optional.empty(); }
            long nextEpoch = preview.orElseThrow().nextEpoch();
            SavedLifecycleProfile active = new SavedLifecycleProfile(preparing.schemaVersion(),
                    preparing.accountId(), preparing.profileKey(), preparing.mindId(), preparing.bodyId(),
                    preparing.dimension(), preparing.location(), preparing.appearance(), preparing.connectionEpoch(),
                    nextEpoch, SavedLifecycleProfile.State.ACTIVE, revision, null);
            ArrayList<SavedLifecycleProfile> profiles = new ArrayList<>(envelope.profiles().size());
            boolean replaced = false;
            for (SavedLifecycleProfile item : envelope.profiles()) {
                if (item.equals(preparing)) {
                    if (replaced) return Optional.empty();
                    profiles.add(active);
                    replaced = true;
                } else profiles.add(item);
            }
            if (!replaced) return Optional.empty();
            var proof = new EnrollmentPromotionProof(envelope.epochToken(), envelope.storeRevision(), preparing,
                    active, envelope.generationCounter());
            currentPrimary.promotePreparingToActive(proof, Math.max(envelope.generationCounter(), nextEpoch), profiles);
            Optional<BodyControlRegistry.MindSnapshot> committed;
            try {
                committed = ownership.commitFirstEnrollmentPromotion(preview.orElseThrow(),
                        exactRegisteredCharacter, capability);
            } catch (RuntimeException postCasFailure) {
                markPromotionRecoveryRequired(record, active);
                return Optional.empty();
            }
            if (committed.isEmpty()) {
                markPromotionRecoveryRequired(record, active);
                return Optional.empty();
            }
            record.generation = committed.orElseThrow().epoch();
            record.active = true;
            record.state = LifecycleState.ACTIVE;
            record.durableActive = true;
            return Optional.of(record.snapshot());
        }
    }

    private void markPromotionRecoveryRequired(ProfileRecord record, SavedLifecycleProfile active) {
        ownership.suspendLifecycleForRecovery(record.accountId, record.mindId, capability);
        record.generation = active.connectionGeneration();
        record.active = false;
        record.state = LifecycleState.RECOVERY_REQUIRED;
        record.durableActive = false;
    }

    /** Opaque facade-minted, single-use authorization for one exact durable reservation transition. */
    public static final class EnrollmentPromotionProof {
        private final UUID epoch;
        private final long storeRevision;
        private final SavedLifecycleProfile preparing;
        private final SavedLifecycleProfile active;
        private final long generationFloor;
        private final AtomicBoolean consumed = new AtomicBoolean();
        private EnrollmentPromotionProof(UUID epoch, long storeRevision, SavedLifecycleProfile preparing,
                SavedLifecycleProfile active, long generationFloor) {
            this.epoch = epoch; this.storeRevision = storeRevision; this.preparing = preparing;
            this.active = active; this.generationFloor = generationFloor;
        }
        public boolean consume(LifecycleStoreEnvelope envelope, long generationCounter,
                java.util.List<SavedLifecycleProfile> profiles) {
            if (envelope.storeRevision() != storeRevision || !envelope.epochToken().equals(epoch)
                    || envelope.generationCounter() != generationFloor || generationCounter < active.connectionGeneration()
                    || envelope.profiles().stream().filter(preparing::equals).count() != 1
                    || profiles.stream().filter(active::equals).count() != 1) return false;
            return consumed.compareAndSet(false, true);
        }
        public boolean matchesProfiles(SavedLifecycleProfile prior, SavedLifecycleProfile next) {
            return preparing.equals(prior) && active.equals(next);
        }
    }

    public synchronized Transition disconnect(UUID accountId, long expectedGeneration) {
        synchronized (ownership) {
        ProfileRecord record = byAccount.get(Objects.requireNonNull(accountId, "accountId"));
        if (record == null) return Transition.NOT_FOUND;
        if (record.restoredFromStore || !record.active || record.generation != expectedGeneration)
            return Transition.STALE_GENERATION;
        Optional<BodyControlRegistry.MindSnapshot> detached = ownership.disconnectLifecycle(accountId, expectedGeneration, capability);
        if (detached.isEmpty()) return Transition.STALE_GENERATION;
        record.generation = detached.orElseThrow().epoch();
        record.active = false;
        record.state = record.state == LifecycleState.GHOST
                ? LifecycleState.GHOST_OFFLINE : LifecycleState.OFFLINE;
        return Transition.CHANGED;
        }
    }

    /**
     * Persists an OFFLINE claim before revoking the currently authorized Mind. Only a profile made
     * ACTIVE by this facade's successful durable reconnect may use this transition.
     */
    public synchronized Optional<Snapshot> disconnectDurably(LifecycleProfileStore.CurrentPrimary currentPrimary,
            SavedLifecycleProfile savedActive, long expectedGeneration, long serverTimestampMillis)
            throws IOException {
        return disconnectDurably(currentPrimary, savedActive, expectedGeneration, serverTimestampMillis,
                target -> true);
    }

    public synchronized Optional<Snapshot> disconnectDurably(LifecycleProfileStore.CurrentPrimary currentPrimary,
            SavedLifecycleProfile savedActive, long expectedGeneration, long serverTimestampMillis,
            TargetEligibility eligibility) throws IOException {
        return disconnectDurably(currentPrimary, savedActive, savedActive, expectedGeneration,
                serverTimestampMillis, eligibility);
    }

    /** Clean logout persists authoritative body state while matching the old ACTIVE row exactly under the store lock. */
    public synchronized Optional<Snapshot> disconnectDurably(LifecycleProfileStore.CurrentPrimary currentPrimary,
            SavedLifecycleProfile savedActive, SavedLifecycleProfile updatedBodyState, long expectedGeneration,
            long serverTimestampMillis, TargetEligibility eligibility) throws IOException {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(savedActive, "savedActive");
            Objects.requireNonNull(updatedBodyState, "updatedBodyState");
            Objects.requireNonNull(eligibility, "eligibility");
            if (serverTimestampMillis < 0 || !currentPrimary.isActive()) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || savedActive.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || savedActive.state() != SavedLifecycleProfile.State.ACTIVE
                    || !savedActive.connectionEpoch().equals(envelope.epochToken())
                    || savedActive.connectionGeneration() > envelope.generationCounter()
                    || updatedBodyState.state() != SavedLifecycleProfile.State.ACTIVE
                    || !updatedBodyState.accountId().equals(savedActive.accountId())
                    || !updatedBodyState.profileKey().equals(savedActive.profileKey())
                    || !updatedBodyState.mindId().equals(savedActive.mindId())
                    || !updatedBodyState.bodyId().equals(savedActive.bodyId())
                    || !updatedBodyState.connectionEpoch().equals(savedActive.connectionEpoch())
                    || updatedBodyState.connectionGeneration() != savedActive.connectionGeneration()
                    || envelope.profiles().stream().filter(savedActive::equals).count() != 1)
                return Optional.empty();

            ProfileRecord record = byAccount.get(savedActive.accountId());
            if (record == null || !record.durableActive || !record.active
                    || record.generation != expectedGeneration || record.generation != savedActive.connectionGeneration()
                    || record.state != LifecycleState.ACTIVE || record.deadClaim
                    || !record.profileId.equals(savedActive.profileKey())
                    || !record.mindId.value().equals(savedActive.mindId())
                    || !record.bodyId.value().equals(savedActive.bodyId()))
                return Optional.empty();
            var currentMind = ownership.mind(savedActive.accountId()).orElse(null);
            if (currentMind == null || currentMind.harnessId() == null || !currentMind.id().equals(record.mindId)
                    || !savedActive.bodyId().equals(currentMind.harnessId().value())
                    || currentMind.epoch() != expectedGeneration)
                return Optional.empty();
            var preview = ownership.previewDisconnect(savedActive.accountId(), expectedGeneration,
                    envelope.generationCounter(), eligibility, capability);
            if (preview.isEmpty()) return Optional.empty();
            long nextRevision;
            try {
                nextRevision = Math.incrementExact(savedActive.revision());
            } catch (ArithmeticException overflow) {
                return Optional.empty();
            }
            long nextEpoch = preview.orElseThrow().nextEpoch();
            SavedLifecycleProfile offline = new SavedLifecycleProfile(updatedBodyState.schemaVersion(),
                    updatedBodyState.accountId(), updatedBodyState.profileKey(), updatedBodyState.mindId(), updatedBodyState.bodyId(),
                    updatedBodyState.dimension(), updatedBodyState.location(), updatedBodyState.appearance(),
                    savedActive.connectionEpoch(), nextEpoch, SavedLifecycleProfile.State.OFFLINE,
                    nextRevision, serverTimestampMillis);
            ArrayList<SavedLifecycleProfile> completeProfiles = new ArrayList<>(envelope.profiles().size());
            boolean replaced = false;
            for (SavedLifecycleProfile profile : envelope.profiles()) {
                if (profile.equals(savedActive)) {
                    if (replaced) return Optional.empty();
                    completeProfiles.add(offline);
                    replaced = true;
                } else completeProfiles.add(profile);
            }
            if (!replaced) return Optional.empty();

            // Persistence is the barrier: no ownership or profile state changes before this succeeds.
            currentPrimary.compareAndSwap(Math.max(envelope.generationCounter(), nextEpoch), completeProfiles);
            Optional<BodyControlRegistry.MindSnapshot> committed;
            try {
                committed = ownership.commitDisconnectAtExpectedGeneration(
                        preview.orElseThrow(), eligibility, capability);
            } catch (RuntimeException postCasFailure) {
                // The durable record is already OFFLINE. Never roll it back or re-run fallible validation.
                markDisconnectRecoveryRequired(record, offline);
                return Optional.empty();
            }
            if (committed.isEmpty()) {
                markDisconnectRecoveryRequired(record, offline);
                return Optional.empty();
            }
            record.generation = committed.orElseThrow().epoch();
            record.active = false;
            record.state = LifecycleState.OFFLINE;
            record.durableActive = false;
            return Optional.of(record.snapshot());
        }
    }

    private void markDisconnectRecoveryRequired(ProfileRecord record, SavedLifecycleProfile offline) {
        ownership.suspendLifecycleForRecovery(record.accountId, record.mindId, capability);
        record.generation = offline.connectionGeneration();
        record.active = false;
        record.state = LifecycleState.RECOVERY_REQUIRED;
        record.durableActive = false;
    }

    /**
     * Durably records actual death of a restored player's disconnected character. Evidence must be
     * supplied by a trusted world adapter and is checked before any store write. The corpse remains
     * registered and owned by the disconnected Mind; this transition deliberately creates no ghost.
     */
    public synchronized Optional<Snapshot> markOfflineDeathDurably(
            LifecycleProfileStore.CurrentPrimary currentPrimary, SavedLifecycleProfile savedOffline,
            long expectedGeneration, Predicate<MobHarness> actualWorldDeathEvidence) throws IOException {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(savedOffline, "savedOffline");
            Objects.requireNonNull(actualWorldDeathEvidence, "actualWorldDeathEvidence");
            if (!currentPrimary.isActive() || savedOffline.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || savedOffline.state() != SavedLifecycleProfile.State.OFFLINE
                    || savedOffline.offlineSinceMillis() == null) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || !savedOffline.connectionEpoch().equals(envelope.epochToken())
                    || savedOffline.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().filter(savedOffline::equals).count() != 1)
                return Optional.empty();

            ProfileRecord record = byAccount.get(savedOffline.accountId());
            if (record == null || !record.restoredFromStore || record.active
                    || record.deadClaim || record.state != LifecycleState.OFFLINE
                    || record.generation != expectedGeneration || !record.profileId.equals(savedOffline.profileKey())
                    || !record.mindId.value().equals(savedOffline.mindId())
                    || !record.bodyId.value().equals(savedOffline.bodyId())) return Optional.empty();
            var mind = ownership.mind(savedOffline.accountId()).orElse(null);
            if (mind == null || mind.harnessId() == null || !mind.id().equals(record.mindId)
                    || !savedOffline.bodyId().equals(mind.harnessId().value())
                    || mind.epoch() != expectedGeneration) return Optional.empty();
            MobHarness corpse = ownership.registeredHarness(record.bodyId).orElse(null);
            if (corpse == null || corpse.kind() != MobHarnessKind.CHARACTER
                    || !actualWorldDeathEvidence.test(corpse)) return Optional.empty();

            var preview = ownership.previewOfflineDeathClaim(record.accountId, record.mindId, record.bodyId,
                    expectedGeneration, envelope.generationCounter(), capability);
            if (preview.isEmpty()) return Optional.empty();
            final long revision;
            try { revision = Math.incrementExact(savedOffline.revision()); }
            catch (ArithmeticException overflow) { return Optional.empty(); }
            long nextGeneration = preview.orElseThrow().nextEpoch();
            SavedLifecycleProfile deadClaim = new SavedLifecycleProfile(savedOffline.schemaVersion(),
                    savedOffline.accountId(), savedOffline.profileKey(), savedOffline.mindId(), savedOffline.bodyId(),
                    savedOffline.dimension(), savedOffline.location(), savedOffline.appearance(),
                    savedOffline.connectionEpoch(), nextGeneration, SavedLifecycleProfile.State.DEAD_CLAIM,
                    revision, savedOffline.offlineSinceMillis());
            ArrayList<SavedLifecycleProfile> completeProfiles = new ArrayList<>(envelope.profiles().size());
            boolean replaced = false;
            for (SavedLifecycleProfile item : envelope.profiles()) {
                if (item.equals(savedOffline)) {
                    if (replaced) return Optional.empty();
                    completeProfiles.add(deadClaim);
                    replaced = true;
                } else completeProfiles.add(item);
            }
            if (!replaced) return Optional.empty();

            currentPrimary.compareAndSwap(Math.max(envelope.generationCounter(), nextGeneration), completeProfiles);
            Optional<BodyControlRegistry.MindSnapshot> committed;
            try {
                committed = ownership.commitOfflineDeathClaim(preview.orElseThrow(), capability);
            } catch (RuntimeException postCasFailure) {
                markDeathClaimRecoveryRequired(record, deadClaim);
                return Optional.empty();
            }
            if (committed.isEmpty()) {
                markDeathClaimRecoveryRequired(record, deadClaim);
                return Optional.empty();
            }
            record.generation = committed.orElseThrow().epoch();
            record.active = false;
            record.deadClaim = true;
            record.state = LifecycleState.DEAD_CLAIM;
            record.durableActive = false;
            return Optional.of(record.snapshot());
        }
    }

    private void markDeathClaimRecoveryRequired(ProfileRecord record, SavedLifecycleProfile deadClaim) {
        ownership.suspendLifecycleForRecovery(record.accountId, record.mindId, capability);
        record.generation = deadClaim.connectionGeneration();
        record.active = false;
        record.deadClaim = false;
        record.state = LifecycleState.RECOVERY_REQUIRED;
        record.durableActive = false;
    }

    /**
     * Persists actual death of a connected durable character before revoking its Mind. The caller's
     * world evidence is only trusted input to this pure transition; this method does not establish
     * that the caller itself is trustworthy.
     */
    public synchronized Optional<Snapshot> markActiveDeathDurably(
            LifecycleProfileStore.CurrentPrimary currentPrimary, SavedLifecycleProfile savedActive,
            SavedLifecycleProfile updatedBodyState, long expectedGeneration, long serverTimestampMillis,
            Predicate<MobHarness> actualWorldDeathEvidence) throws IOException {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(savedActive, "savedActive");
            Objects.requireNonNull(updatedBodyState, "updatedBodyState");
            Objects.requireNonNull(actualWorldDeathEvidence, "actualWorldDeathEvidence");
            if (serverTimestampMillis < 0 || !currentPrimary.isActive()
                    || savedActive.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || savedActive.state() != SavedLifecycleProfile.State.ACTIVE
                    || savedActive.offlineSinceMillis() != null
                    || updatedBodyState.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || updatedBodyState.state() != SavedLifecycleProfile.State.ACTIVE
                    || updatedBodyState.offlineSinceMillis() != null
                    || !updatedBodyState.accountId().equals(savedActive.accountId())
                    || !updatedBodyState.profileKey().equals(savedActive.profileKey())
                    || !updatedBodyState.mindId().equals(savedActive.mindId())
                    || !updatedBodyState.bodyId().equals(savedActive.bodyId())
                    || !updatedBodyState.connectionEpoch().equals(savedActive.connectionEpoch())
                    || updatedBodyState.connectionGeneration() != savedActive.connectionGeneration())
                return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || !savedActive.connectionEpoch().equals(envelope.epochToken())
                    || savedActive.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().filter(savedActive::equals).count() != 1)
                return Optional.empty();

            ProfileRecord record = byAccount.get(savedActive.accountId());
            if (record == null || !record.durableActive || !record.active || record.deadClaim
                    || record.state != LifecycleState.ACTIVE || record.generation != expectedGeneration
                    || record.generation != savedActive.connectionGeneration()
                    || !record.profileId.equals(savedActive.profileKey())
                    || !record.mindId.value().equals(savedActive.mindId())
                    || !record.bodyId.value().equals(savedActive.bodyId())) return Optional.empty();
            var mind = ownership.mind(savedActive.accountId()).orElse(null);
            if (mind == null || mind.harnessId() == null || !mind.id().equals(record.mindId)
                    || !savedActive.bodyId().equals(mind.harnessId().value())
                    || mind.epoch() != expectedGeneration) return Optional.empty();
            MobHarness corpse = ownership.registeredHarness(record.bodyId).orElse(null);
            if (corpse == null || corpse.kind() != MobHarnessKind.CHARACTER) return Optional.empty();
            final boolean provenDead;
            try { provenDead = actualWorldDeathEvidence.test(corpse); }
            catch (RuntimeException invalidEvidence) { return Optional.empty(); }
            if (!provenDead) return Optional.empty();

            var preview = ownership.previewActiveDeathClaim(record.accountId, record.mindId, record.bodyId,
                    expectedGeneration, envelope.generationCounter(), capability);
            if (preview.isEmpty()) return Optional.empty();
            final long revision;
            try { revision = Math.incrementExact(savedActive.revision()); }
            catch (ArithmeticException overflow) { return Optional.empty(); }
            long nextGeneration = preview.orElseThrow().nextEpoch();
            SavedLifecycleProfile deadClaim = new SavedLifecycleProfile(updatedBodyState.schemaVersion(),
                    updatedBodyState.accountId(), updatedBodyState.profileKey(), updatedBodyState.mindId(),
                    updatedBodyState.bodyId(), updatedBodyState.dimension(), updatedBodyState.location(),
                    updatedBodyState.appearance(), savedActive.connectionEpoch(), nextGeneration,
                    SavedLifecycleProfile.State.DEAD_CLAIM, revision, serverTimestampMillis);
            ArrayList<SavedLifecycleProfile> completeProfiles = new ArrayList<>(envelope.profiles().size());
            boolean replaced = false;
            for (SavedLifecycleProfile item : envelope.profiles()) {
                if (item.equals(savedActive)) {
                    if (replaced) return Optional.empty();
                    completeProfiles.add(deadClaim);
                    replaced = true;
                } else completeProfiles.add(item);
            }
            if (!replaced) return Optional.empty();

            currentPrimary.compareAndSwap(Math.max(envelope.generationCounter(), nextGeneration), completeProfiles);
            Optional<BodyControlRegistry.MindSnapshot> committed;
            try {
                committed = ownership.commitActiveDeathClaim(preview.orElseThrow(), capability);
            } catch (RuntimeException postCasFailure) {
                markDeathClaimRecoveryRequired(record, deadClaim);
                return Optional.empty();
            }
            if (committed.isEmpty()) {
                markDeathClaimRecoveryRequired(record, deadClaim);
                return Optional.empty();
            }
            record.generation = committed.orElseThrow().epoch();
            record.active = false;
            record.deadClaim = true;
            record.state = LifecycleState.DEAD_CLAIM;
            record.durableActive = false;
            return Optional.of(record.snapshot());
        }
    }

    /** Reconnect to the same living body, or transfer a claimed offline death to a fresh ghost. */
    public synchronized Optional<Snapshot> reconnect(UUID accountId, long expectedGeneration,
            boolean authenticated, MobHarnessId freshGhost, TargetEligibility eligibility) {
        synchronized (ownership) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(eligibility, "eligibility");
        ProfileRecord record = byAccount.get(accountId);
        if (!authenticated || record == null || record.restoredFromStore
                || record.active || record.generation != expectedGeneration
                || record.state == LifecycleState.GHOST_OFFLINE
                || record.state == LifecycleState.RECOVERY_REQUIRED)
            return Optional.empty();
        Optional<BodyControlRegistry.MindSnapshot> rebound;
        if (record.deadClaim) {
            if (freshGhost == null) return Optional.empty();
            rebound = ownership.reconnectDeadClaim(accountId, expectedGeneration, freshGhost, eligibility, capability);
            if (rebound.isPresent()) {
                byBody.remove(record.bodyId);
                record.bodyId = freshGhost;
                byBody.put(freshGhost, record);
            }
        } else {
            if (freshGhost != null) return Optional.empty();
            rebound = ownership.reconnectLifecycle(accountId, expectedGeneration, eligibility, capability);
        }
        if (rebound.isEmpty()) return Optional.empty();
        var mind = rebound.orElseThrow();
        record.generation = mind.epoch();
        record.active = true;
        record.state = record.deadClaim ? LifecycleState.GHOST : LifecycleState.ACTIVE;
        return Optional.of(record.snapshot());
        }
    }

    /**
     * Reconnects a restored living character only after the complete durable snapshot has advanced.
     * The lease callback holds the store OS lock; this method additionally serializes facade and
     * ownership state in the same order as restore/create. No ghost fallback is available here.
     */
    public synchronized Optional<Snapshot> reconnectLivingDurably(LifecycleProfileStore.CurrentPrimary currentPrimary,
            SavedLifecycleProfile saved, boolean authenticated, TargetEligibility eligibility) throws IOException {
        synchronized (ownership) {
            Objects.requireNonNull(currentPrimary, "currentPrimary");
            Objects.requireNonNull(saved, "saved");
            Objects.requireNonNull(eligibility, "eligibility");
            if (!authenticated || !currentPrimary.isActive()) return Optional.empty();
            LifecycleStoreEnvelope envelope = currentPrimary.envelope();
            if (envelope.schemaVersion() != LifecycleStoreEnvelope.CURRENT_SCHEMA
                    || saved.schemaVersion() != SavedLifecycleProfile.CURRENT_SCHEMA
                    || saved.state() != SavedLifecycleProfile.State.OFFLINE
                    || !saved.connectionEpoch().equals(envelope.epochToken())
                    || saved.connectionGeneration() > envelope.generationCounter()
                    || envelope.profiles().stream().filter(saved::equals).count() != 1)
                return Optional.empty();

            ProfileRecord record = byAccount.get(saved.accountId());
            if (record == null || !record.restoredFromStore || record.active || record.deadClaim
                    || record.state != LifecycleState.OFFLINE || !record.profileId.equals(saved.profileKey())
                    || !record.mindId.value().equals(saved.mindId()) || !record.bodyId.value().equals(saved.bodyId()))
                return Optional.empty();
            var currentMind = ownership.mind(saved.accountId()).orElse(null);
            if (currentMind == null || currentMind.harnessId() == null || !currentMind.id().equals(record.mindId)
                    || !saved.bodyId().equals(currentMind.harnessId().value()) || currentMind.epoch() != record.generation)
                return Optional.empty();

            var preview = ownership.previewReconnect(saved.accountId(), record.generation,
                    envelope.generationCounter(), eligibility, capability);
            if (preview.isEmpty()) return Optional.empty();
            long nextEpoch = preview.orElseThrow().nextEpoch();
            final long nextProfileRevision;
            try {
                nextProfileRevision = Math.incrementExact(saved.revision());
            } catch (ArithmeticException overflow) {
                return Optional.empty();
            }
            SavedLifecycleProfile activeProfile = new SavedLifecycleProfile(saved.schemaVersion(), saved.accountId(),
                    saved.profileKey(), saved.mindId(), saved.bodyId(), saved.dimension(), saved.location(),
                    saved.appearance(), saved.connectionEpoch(), nextEpoch, SavedLifecycleProfile.State.ACTIVE,
                    nextProfileRevision, null);
            ArrayList<SavedLifecycleProfile> completeProfiles = new ArrayList<>(envelope.profiles().size());
            boolean replaced = false;
            for (SavedLifecycleProfile profile : envelope.profiles()) {
                if (profile.equals(saved)) {
                    if (replaced) return Optional.empty();
                    completeProfiles.add(activeProfile);
                    replaced = true;
                } else completeProfiles.add(profile);
            }
            if (!replaced) return Optional.empty();

            // Store CAS is the authorization barrier: memory remains offline on any write/CAS failure.
            currentPrimary.compareAndSwap(Math.max(envelope.generationCounter(), nextEpoch), completeProfiles);
            Optional<BodyControlRegistry.MindSnapshot> committed;
            try {
                committed = ownership.commitReconnectAtExpectedGeneration(
                        preview.orElseThrow(), eligibility, capability);
            } catch (RuntimeException postCasFailure) {
                markReconnectRecoveryRequired(record, activeProfile);
                return Optional.empty();
            }
            if (committed.isEmpty()) {
                markReconnectRecoveryRequired(record, activeProfile);
                return Optional.empty();
            }
            record.generation = committed.orElseThrow().epoch();
            record.active = true;
            record.state = LifecycleState.ACTIVE;
            record.durableActive = true;
            return Optional.of(record.snapshot());
        }
    }

    private void markReconnectRecoveryRequired(ProfileRecord record, SavedLifecycleProfile activeProfile) {
        ownership.suspendLifecycleForRecovery(record.accountId, record.mindId, capability);
        record.generation = activeProfile.connectionGeneration();
        record.active = false;
        record.state = LifecycleState.RECOVERY_REQUIRED;
        record.durableActive = false;
    }

    /**
     * Revokes an exact restored ACTIVE session in memory after controller/session integration fails.
     * The durable ACTIVE record is deliberately left unchanged so a restart remains fail-closed and
     * requires operator recovery. This method performs no filesystem, eligibility callback, or body work.
     */
    public synchronized boolean suspendActiveSessionForRecovery(UUID account, MindId expectedMind,
            MobHarnessId expectedBody, long expectedEpoch) {
        synchronized (ownership) {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(expectedMind, "expectedMind");
            Objects.requireNonNull(expectedBody, "expectedBody");
            ProfileRecord record = byAccount.get(account);
            if (record == null || !record.durableActive || !record.active
                    || record.state != LifecycleState.ACTIVE || record.generation != expectedEpoch
                    || !record.mindId.equals(expectedMind) || !record.bodyId.equals(expectedBody)) return false;
            if (!ownership.suspendLifecycleForRecovery(account, expectedMind, expectedBody, expectedEpoch,
                    capability)) return false;
            record.active = false;
            record.state = LifecycleState.RECOVERY_REQUIRED;
            return true;
        }
    }

    /** Actual death proof is supplied by the world caller; alive voluntary ghosting must return false. */
    public synchronized Optional<Snapshot> markActualDeath(UUID accountId, long expectedGeneration,
            MobHarnessId freshGhost, TargetEligibility ghostEligibility,
            Predicate<MobHarness> actualWorldDeathEvidence) {
        synchronized (ownership) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(ghostEligibility, "ghostEligibility");
        Objects.requireNonNull(actualWorldDeathEvidence, "actualWorldDeathEvidence");
        ProfileRecord record = byAccount.get(accountId);
        if (record == null || record.generation != expectedGeneration || record.restoredFromStore
                || record.durableActive)
            return Optional.empty();
        if (freshGhost == null) {
            if (record.active || record.state != LifecycleState.OFFLINE || record.deadClaim) return Optional.empty();
            MobHarness corpse = ownership.registeredHarness(record.bodyId).orElse(null);
            if (corpse == null || !actualWorldDeathEvidence.test(corpse)) return Optional.empty();
            if (!ownership.claimOfflineDeath(accountId, expectedGeneration, capability)) return Optional.empty();
            record.deadClaim = true;
            record.state = LifecycleState.DEAD_CLAIM;
            return Optional.of(record.snapshot());
        }
        if (!record.active || record.state != LifecycleState.ACTIVE) return Optional.empty();
        MobHarness corpse = ownership.registeredHarness(record.bodyId).orElse(null);
        if (corpse == null) return Optional.empty();
        Optional<BodyControlRegistry.MindSnapshot> moved = ownership.transferActualDeath(
                accountId, expectedGeneration, freshGhost, ghostEligibility, actualWorldDeathEvidence, capability);
        if (moved.isEmpty()) return Optional.empty();
        byBody.remove(record.bodyId);
        record.bodyId = freshGhost;
        record.generation = moved.orElseThrow().epoch();
        record.deadClaim = true;
        record.state = LifecycleState.GHOST;
        byBody.put(freshGhost, record);
        return Optional.of(record.snapshot());
        }
    }

    public synchronized boolean authorizes(UUID accountId, long expectedGeneration, MobHarnessId bodyId) {
        return authorizes(accountId, expectedGeneration, bodyId, target -> true);
    }

    /** Checks both lifecycle profile state and the current shared Mind/harness authority. */
    public synchronized boolean authorizes(UUID accountId, long expectedGeneration, MobHarnessId bodyId,
                                           TargetEligibility eligibility) {
        synchronized (ownership) {
        Objects.requireNonNull(eligibility, "eligibility");
        ProfileRecord record = byAccount.get(accountId);
        if (record == null || !record.active || record.generation != expectedGeneration
                || !record.bodyId.equals(bodyId)) return false;
        MobHarness target = ownership.registeredHarness(bodyId).orElse(null);
        MobHarnessKind expectedKind = record.state == LifecycleState.GHOST
                ? MobHarnessKind.GHOST : record.state == LifecycleState.ACTIVE
                ? MobHarnessKind.CHARACTER : null;
        if (expectedKind == null || target == null || target.kind() != expectedKind) return false;
        return ownership.authorizes(accountId, bodyId, expectedGeneration, eligibility);
        }
    }

    public synchronized Optional<Snapshot> profile(UUID accountId) {
        ProfileRecord record = byAccount.get(Objects.requireNonNull(accountId, "accountId"));
        return record == null ? Optional.empty() : Optional.of(record.snapshot());
    }

    public enum LifecycleState { PREPARING, ACTIVE, OFFLINE, DEAD_CLAIM, GHOST, GHOST_OFFLINE, RECOVERY_REQUIRED }
    public enum Transition { CHANGED, NOT_FOUND, STALE_GENERATION }
    public record Snapshot(int schemaVersion, UUID accountId, String profileId, MindId mindId,
                           MobHarnessId bodyId, LifecycleState state, long connectionGeneration,
                           boolean active, boolean deadClaim) { }

    private static final class ProfileRecord {
        private final UUID accountId;
        private final String profileId;
        private final MindId mindId;
        private MobHarnessId bodyId;
        private LifecycleState state;
        private long generation;
        private boolean active;
        private boolean deadClaim;
        private boolean durableActive;
        private MobHarnessId corpseBodyId;
        private final boolean restoredFromStore;
        private ProfileRecord(UUID accountId, String profileId, MindId mindId, MobHarnessId bodyId,
                LifecycleState state, long generation, boolean active, boolean deadClaim) {
            this(accountId, profileId, mindId, bodyId, state, generation, active, deadClaim, false);
        }
        private ProfileRecord(UUID accountId, String profileId, MindId mindId, MobHarnessId bodyId,
                LifecycleState state, long generation, boolean active, boolean deadClaim, boolean restoredFromStore) {
            this.accountId = accountId; this.profileId = profileId; this.mindId = mindId;
            this.bodyId = bodyId; this.state = state; this.generation = generation;
            this.corpseBodyId = bodyId;
            this.active = active; this.deadClaim = deadClaim; this.restoredFromStore = restoredFromStore;
        }
        private Snapshot snapshot() { return new Snapshot(SCHEMA_VERSION, accountId, profileId, mindId,
                bodyId, state, generation, active, deadClaim); }
    }

    private record ProfileKey(UUID accountId, String profileId) { }
}
