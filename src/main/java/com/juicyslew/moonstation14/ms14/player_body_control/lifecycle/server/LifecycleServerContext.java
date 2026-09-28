package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleStoreEnvelope;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.stream.Collectors;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearance;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;

/** Exact-server, startup-only ownership boundary; it never imports debug-controller state. */
final class LifecycleServerContext {
    private final BodyControlRegistry ownership;
    private final PlayerLifecycleRegistry lifecycle;
    private final LifecycleProfileStore primaryStore;
    private final boolean initialized;
    private final Set<UUID> reservedAccounts;
    private final Set<UUID> serverCreatedAccounts = new HashSet<>();

    LifecycleServerContext(LifecycleProfileStore primaryStore, LifecycleStoreEnvelope envelope) {
        this.primaryStore = Objects.requireNonNull(primaryStore, "primaryStore");
        this.ownership = new BodyControlRegistry();
        this.lifecycle = new PlayerLifecycleRegistry(ownership);
        this.initialized = envelope != null;
        this.reservedAccounts = envelope == null ? new HashSet<>() : envelope.profiles().stream()
                .map(profile -> profile.accountId()).collect(Collectors.toCollection(HashSet::new));
    }

    synchronized boolean hasReservedClaim(UUID accountUUID) {
        return accountUUID != null && reservedAccounts.contains(accountUUID);
    }

    /** True only for an account whose PREPARING reservation this live server successfully published. */
    synchronized boolean wasCreatedOnThisServer(UUID accountUUID) {
        return accountUUID != null && serverCreatedAccounts.contains(accountUUID);
    }

    /** Confirm the reservation token is still the unique PREPARING member of the current primary. */
    synchronized boolean hasCurrentPreparing(UUID accountId, String profileKey, UUID mindId, UUID bodyId)
            throws IOException {
        if (accountId == null || profileKey == null || mindId == null || bodyId == null
                || !reservedAccounts.contains(accountId)) return false;
        return primaryStore.withCurrentPrimary(lease -> lease.envelope().profiles().stream()
                .filter(saved -> saved.accountId().equals(accountId) && saved.profileKey().equals(profileKey)
                        && saved.mindId().equals(mindId) && saved.bodyId().equals(bodyId)
                        && saved.state() == SavedLifecycleProfile.State.PREPARING)
                .count() == 1).orElse(false);
    }

    /** Publish only a complete PREPARING member verified in this store's locked post-CAS primary. */
    synchronized boolean rememberPreparingClaim(LifecycleProfileStore.CurrentPrimary lease,
                                                SavedLifecycleProfile claim) throws java.io.IOException {
        if (lease == null || claim == null || !lease.isFrom(primaryStore)
                || claim.state() != SavedLifecycleProfile.State.PREPARING) return false;
        if (lease.envelope().profiles().stream().anyMatch(saved -> saved.accountId().equals(claim.accountId())))
            return false;
        var current = lease.readCurrentPrimary();
        if (current.storeRevision() <= lease.envelope().storeRevision()
                || !current.epochToken().equals(lease.envelope().epochToken())
                || current.profiles().stream().noneMatch(saved -> saved.equals(claim))) return false;
        reservedAccounts.add(claim.accountId());
        return true;
    }

    /** Persist a single first-account reservation. Caller has already checked server gates and session ownership. */
    synchronized boolean reserveFirstProfile(UUID accountId, String profileKey, UUID mindId, UUID bodyId,
            String dimension, SavedLifecycleProfile.Location location, Map<String, String> appearance) throws IOException {
        if (accountId == null || profileKey == null || mindId == null || bodyId == null || dimension == null
                || location == null || appearance == null) return false;

        // An absent store is initialized as an immutable empty epoch. Do not retry a failed CAS.
        if (primaryStore.read().isEmpty()) {
            UUID epoch = UUID.randomUUID();
            primaryStore.compareAndSwap(-1, epoch, 0, List.of());
        }

        boolean reserved = primaryStore.withCurrentPrimary(lease -> {
            LifecycleStoreEnvelope current = lease.envelope();
            if (current.profiles().stream().anyMatch(profile -> profile.accountId().equals(accountId))) return false;
            SavedLifecycleProfile preparing = new SavedLifecycleProfile(SavedLifecycleProfile.CURRENT_SCHEMA,
                    accountId, profileKey, mindId, bodyId, dimension, location, appearance,
                    current.epochToken(), current.generationCounter(), SavedLifecycleProfile.State.PREPARING,
                    0, null);
            var profiles = new java.util.ArrayList<>(current.profiles());
            profiles.add(preparing);
            lease.compareAndSwap(current.generationCounter(), profiles);
            return rememberPreparingClaim(lease, preparing);
        }).orElse(false);
        if (reserved) serverCreatedAccounts.add(accountId);
        return reserved;
    }

    /** Stage the exact reservation from a locked current-primary snapshot; the Mind remains disconnected. */
    synchronized boolean stageFirstProfile(UUID accountId, UUID mindId, UUID bodyId,
                                           com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId harnessId)
            throws IOException {
        if (accountId == null || mindId == null || bodyId == null || harnessId == null
                || !bodyId.equals(harnessId.value())) return false;
        return primaryStore.withCurrentPrimary(lease -> {
            var matches = lease.envelope().profiles().stream()
                    .filter(profile -> profile.accountId().equals(accountId)
                            && profile.mindId().equals(mindId) && profile.bodyId().equals(bodyId)
                            && profile.profileKey().equals("main")
                            && profile.state() == SavedLifecycleProfile.State.PREPARING).toList();
            if (matches.size() != 1) return false;
            return lifecycle.stageFirstCharacter(lease, matches.get(0),
                    target -> target.id().equals(harnessId)
                            && target.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER)
                    .isPresent();
        }).orElse(false);
    }

    /** Promote only the exact reserved PREPARING claim from the current-primary lease. */
    synchronized java.util.Optional<PlayerLifecycleRegistry.Snapshot> promoteFirstCharacter(
            UUID accountId, UUID mindId, UUID bodyId) throws IOException {
        if (accountId == null || mindId == null || bodyId == null) return java.util.Optional.empty();
        return primaryStore.withCurrentPrimary(lease -> {
            var matches = lease.envelope().profiles().stream()
                    .filter(saved -> saved.accountId().equals(accountId) && saved.mindId().equals(mindId)
                            && saved.bodyId().equals(bodyId) && saved.profileKey().equals("main")
                            && saved.state() == SavedLifecycleProfile.State.PREPARING).toList();
            if (matches.size() != 1) return null;
            return lifecycle.promoteFirstCharacterDurably(lease, matches.get(0)).orElse(null);
        }).map(java.util.Optional::ofNullable).orElse(java.util.Optional.empty());
    }

    synchronized boolean disconnectCleanly(UUID accountId, long epoch, UUID mindId, UUID bodyId,
            String dimension, SavedLifecycleProfile.Location location, Map<String, String> appearance,
            long timestamp) throws IOException {
        if (accountId == null || mindId == null || bodyId == null || dimension == null
                || location == null || appearance == null || timestamp < 0) return false;
        return primaryStore.withCurrentPrimary(lease -> {
            var matches = lease.envelope().profiles().stream().filter(saved -> saved.accountId().equals(accountId)
                    && saved.state() == SavedLifecycleProfile.State.ACTIVE && saved.connectionGeneration() == epoch
                    && saved.mindId().equals(mindId) && saved.bodyId().equals(bodyId)).toList();
            var memory = lifecycle.profile(accountId).orElse(null);
            if (matches.size() != 1 || memory == null || !memory.active()
                    || memory.state() != PlayerLifecycleRegistry.LifecycleState.ACTIVE
                    || memory.connectionGeneration() != epoch || !memory.mindId().value().equals(mindId)
                    || !memory.bodyId().value().equals(bodyId)) return false;
            SavedLifecycleProfile old = matches.get(0);
            SavedLifecycleProfile updated = new SavedLifecycleProfile(old.schemaVersion(), old.accountId(),
                    old.profileKey(), old.mindId(), old.bodyId(), dimension, location, appearance,
                    old.connectionEpoch(), old.connectionGeneration(), old.state(), old.revision(), null);
            return lifecycle.disconnectDurably(lease, old, updated, epoch, timestamp, target ->
                    target.id().value().equals(bodyId)
                            && target.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER)
                    .isPresent();
        }).orElse(false);
    }

    /** Restores and durably reconnects the exact OFFLINE account while the current-primary lock is held. */
    synchronized java.util.Optional<PlayerLifecycleRegistry.Snapshot> reconnectOffline(
            SavedLifecycleProfile saved, com.juicyslew.moonstation14.ms14.player_body_control.MobHarness harness)
            throws IOException {
        if (saved == null || harness == null || saved.state() != SavedLifecycleProfile.State.OFFLINE
                || harness.kind() != com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER
                || !harness.id().value().equals(saved.bodyId())) return java.util.Optional.empty();
        return primaryStore.withCurrentPrimary(lease -> {
            var matches = lease.envelope().profiles().stream()
                    .filter(saved::equals).filter(row -> row.accountId().equals(saved.accountId())).toList();
            if (matches.size() != 1) return null;
            var registered = ownership.registeredHarness(harness.id()).orElse(null);
            if (registered == null) {
                if (!lifecycle.registerHarness(harness)) return null;
            } else if (registered != harness && (registered.kind() != harness.kind()
                    || !registered.id().equals(harness.id()))) return null;
            var existing = lifecycle.profile(saved.accountId()).orElse(null);
            if (existing == null) {
                if (lifecycle.restoreOffline(lease, saved, target -> target.id().equals(harness.id())
                        && target.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER).isEmpty())
                    return null;
            } else if (existing.state() != PlayerLifecycleRegistry.LifecycleState.OFFLINE || existing.active()
                    || !existing.bodyId().equals(harness.id())
                    || !existing.mindId().value().equals(saved.mindId())) return null;
            return lifecycle.reconnectLivingDurably(lease, saved, true,
                    target -> target.id().equals(harness.id())
                            && target.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER)
                    .orElse(null);
        }).map(java.util.Optional::ofNullable).orElse(java.util.Optional.empty());
    }

    /** Restore only the exact persisted offline owner for a world-proven dead corpse, then claim its death. */
    synchronized boolean claimOfflineDeath(SavedLifecycleProfile saved,
            com.juicyslew.moonstation14.ms14.player_body_control.MobHarness corpse,
            java.util.function.Predicate<com.juicyslew.moonstation14.ms14.player_body_control.MobHarness> evidence)
            throws IOException {
        if (saved == null || corpse == null || evidence == null
                || saved.state() != SavedLifecycleProfile.State.OFFLINE
                || corpse.kind() != com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER
                || !corpse.id().value().equals(saved.bodyId())) return false;
        return primaryStore.withCurrentPrimary(lease -> {
            var rows = lease.envelope().profiles().stream().filter(saved::equals).toList();
            if (rows.size() != 1 || !evidence.test(corpse)) return false;
            var registered = ownership.registeredHarness(corpse.id()).orElse(null);
            if (registered == null) {
                if (!lifecycle.registerHarness(corpse)) return false;
            } else if (!registered.equals(corpse)) return false;
            var memory = lifecycle.profile(saved.accountId()).orElse(null);
            if (memory == null) {
                memory = lifecycle.restoreOffline(lease, saved, target -> target.equals(corpse)
                        && target.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER)
                        .orElse(null);
                if (memory == null) return false;
            } else if (memory.active() || memory.state() != PlayerLifecycleRegistry.LifecycleState.OFFLINE
                    || !memory.bodyId().equals(corpse.id())
                    || !memory.mindId().value().equals(saved.mindId())) return false;
            return lifecycle.markOfflineDeathDurably(lease, saved, memory.connectionGeneration(), evidence).isPresent();
        }).orElse(false);
    }

    /** Claim a connected death only from the exact in-memory ACTIVE epoch and current primary row. */
    synchronized boolean claimActiveDeath(UUID accountId, String profileKey, UUID mindId, UUID bodyId,
            String dimension, SavedLifecycleProfile.Location location, Map<String, String> appearance, long epoch,
            java.util.function.Predicate<com.juicyslew.moonstation14.ms14.player_body_control.MobHarness> evidence)
            throws IOException {
        if (accountId == null || profileKey == null || mindId == null || bodyId == null
                || dimension == null || location == null || appearance == null || evidence == null)
            return false;
        return primaryStore.withCurrentPrimary(lease -> {
            var rows = lease.envelope().profiles().stream().filter(row -> row.accountId().equals(accountId)
                    && row.profileKey().equals(profileKey) && row.mindId().equals(mindId)
                    && row.bodyId().equals(bodyId) && row.state() == SavedLifecycleProfile.State.ACTIVE).toList();
            if (rows.size() != 1) return false;
            SavedLifecycleProfile saved = rows.get(0);
            SavedLifecycleProfile updatedBody = new SavedLifecycleProfile(saved.schemaVersion(), saved.accountId(),
                    saved.profileKey(), saved.mindId(), saved.bodyId(), dimension, location, appearance,
                    saved.connectionEpoch(), saved.connectionGeneration(), SavedLifecycleProfile.State.ACTIVE,
                    saved.revision(), null);
            var memory = lifecycle.profile(saved.accountId()).orElse(null);
            if (rows.size() != 1 || memory == null || !memory.active()
                    || memory.state() != PlayerLifecycleRegistry.LifecycleState.ACTIVE
                    || memory.connectionGeneration() != epoch || epoch != saved.connectionGeneration()
                    || !memory.profileId().equals(saved.profileKey())
                    || !memory.mindId().value().equals(saved.mindId())
                    || !memory.bodyId().value().equals(saved.bodyId())) return false;
            var transition = lifecycle.markActiveDeathDurably(lease, saved, updatedBody, epoch,
                    Math.max(0L, System.currentTimeMillis()), evidence);
            if (transition.isPresent()) return true;
            // The registry deliberately reports a post-CAS ownership failure as empty. Never send
            // that successful durable claim through the generic ACTIVE recovery path.
            return lease.readCurrentPrimary().profiles().stream().anyMatch(row ->
                    row.accountId().equals(saved.accountId()) && row.profileKey().equals(saved.profileKey())
                            && row.mindId().equals(saved.mindId()) && row.bodyId().equals(saved.bodyId())
                            && row.connectionEpoch().equals(saved.connectionEpoch())
                            && row.state() == SavedLifecycleProfile.State.DEAD_CLAIM
                            && row.revision() == saved.revision() + 1);
        }).orElse(false);
    }

    /** Stage and durably activate one caller-supplied transient ghost while the current-primary lock is held. */
    synchronized java.util.Optional<PlayerLifecycleRegistry.Snapshot> activateDeadClaimGhost(
            SavedLifecycleProfile saved, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId ghostId)
            throws IOException {
        if (saved == null || ghostId == null || saved.state() != SavedLifecycleProfile.State.DEAD_CLAIM
                || !reservedAccounts.contains(saved.accountId())) return java.util.Optional.empty();
        return primaryStore.withCurrentPrimary(lease -> {
            if (lease.envelope().profiles().stream().filter(saved::equals).count() != 1) return null;
            var profile = lifecycle.profile(saved.accountId()).orElse(null);
            if (profile != null && (profile.active()
                    || profile.state() != PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM
                    || !profile.mindId().value().equals(saved.mindId())
                    || !profile.bodyId().value().equals(saved.bodyId()))) return null;
            var exactGhost = ownership.registeredHarness(ghostId).orElse(null);
            if (exactGhost == null || exactGhost.kind()
                    != com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.GHOST) return null;
            var staged = lifecycle.stageDeadClaimGhost(lease, saved, ghostId,
                    target -> target == exactGhost && target.id().equals(ghostId)
                            && target.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.GHOST)
                    .orElse(null);
            if (staged == null) return null;
            try {
                return lifecycle.activateDeadClaimGhostDurably(lease, saved, ghostId, true,
                        target -> target == exactGhost && target.id().equals(ghostId)
                                && target.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.GHOST)
                        .orElse(null);
            } catch (IOException | RuntimeException failure) {
                // Staging has moved the stable Mind. Keep its transient harness registered and disconnected;
                // the retained DEAD_CLAIM is an explicit recovery reservation, never retry/authorize here.
                throw failure;
            }
        }).map(java.util.Optional::ofNullable).orElse(java.util.Optional.empty());
    }

    synchronized java.util.Optional<SavedLifecycleProfile> currentDeadClaim(UUID accountId) throws IOException {
        if (accountId == null || !reservedAccounts.contains(accountId)) return java.util.Optional.empty();
        return primaryStore.withCurrentPrimary(lease -> {
            var rows = lease.envelope().profiles().stream().filter(row -> row.accountId().equals(accountId)
                    && row.state() == SavedLifecycleProfile.State.DEAD_CLAIM).toList();
            return rows.size() == 1 ? rows.get(0) : null;
        }).map(java.util.Optional::ofNullable).orElse(java.util.Optional.empty());
    }

    /** Read one unique account row from the current primary, regardless of lifecycle state. */
    synchronized java.util.Optional<SavedLifecycleProfile> currentAccountProfile(UUID accountId) throws IOException {
        if (accountId == null || !reservedAccounts.contains(accountId)) return java.util.Optional.empty();
        return primaryStore.withCurrentPrimary(lease -> {
            var rows = lease.envelope().profiles().stream().filter(row -> row.accountId().equals(accountId)).toList();
            return rows.size() == 1 ? rows.get(0) : null;
        }).map(java.util.Optional::ofNullable).orElse(java.util.Optional.empty());
    }

    /** Persist one appearance field for the exact ACTIVE account/body/Mind/generation before entity mutation. */
    synchronized boolean writeActiveAppearance(UUID accountId, String profileKey, UUID mindId, UUID bodyId,
            long generation, String field, String value) throws IOException {
        if (accountId == null || profileKey == null || mindId == null || bodyId == null || generation <= 0
                || !("appearance".equals(field) || "bodyShape".equals(field)) || value == null) return false;
        return primaryStore.withCurrentPrimary(lease -> {
            var rows = lease.envelope().profiles().stream().filter(row -> row.accountId().equals(accountId)
                    && row.profileKey().equals(profileKey) && row.mindId().equals(mindId)
                    && row.bodyId().equals(bodyId) && row.state() == SavedLifecycleProfile.State.ACTIVE
                    && row.connectionGeneration() == generation).toList();
            var memory = lifecycle.profile(accountId).orElse(null);
            if (rows.size() != 1 || memory == null || !memory.active()
                    || memory.state() != PlayerLifecycleRegistry.LifecycleState.ACTIVE
                    || memory.connectionGeneration() != generation || !memory.profileId().equals(profileKey)
                    || !memory.mindId().value().equals(mindId) || !memory.bodyId().value().equals(bodyId)) return false;
            SavedLifecycleProfile old = rows.get(0);
            Map<String, String> appearance = new java.util.HashMap<>(old.appearance());
            appearance.put(field, value);
            final long nextRevision;
            try { nextRevision = Math.incrementExact(old.revision()); }
            catch (ArithmeticException exhausted) { return false; }
            SavedLifecycleProfile updated = new SavedLifecycleProfile(old.schemaVersion(), old.accountId(),
                    old.profileKey(), old.mindId(), old.bodyId(), old.dimension(), old.location(), appearance,
                    old.connectionEpoch(), old.connectionGeneration(), old.state(), nextRevision, null);
            var profiles = new java.util.ArrayList<>(lease.envelope().profiles());
            profiles.set(profiles.indexOf(old), updated);
            lease.compareAndSwap(lease.envelope().generationCounter(), profiles);
            return true;
        }).orElse(false);
    }

    boolean initialized() { return initialized; }

    BodyControlRegistry ownership() { return ownership; }

    PlayerLifecycleRegistry lifecycle() { return lifecycle; }

    LifecycleProfileStore primaryStore() { return primaryStore; }
}
