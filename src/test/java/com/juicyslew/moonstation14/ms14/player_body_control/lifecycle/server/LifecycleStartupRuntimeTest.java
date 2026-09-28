package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleStoreEnvelope;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleStartupRuntimeTest {
    @TempDir Path directory;

    @Test void disabledAndConflictGatesNeverReadStore() {
        AtomicInteger reads = new AtomicInteger();
        LifecycleStartupRuntime.StoreReader reader = () -> {
            reads.incrementAndGet();
            return Optional.of(List.of(SavedLifecycleProfile.State.DEAD_CLAIM));
        };

        var disabled = LifecycleStartupRuntime.inspect(false, false, reader);
        var conflict = LifecycleStartupRuntime.inspect(true, true, reader);

        assertEquals(LifecycleStartupRuntime.State.DISABLED, disabled.state());
        assertEquals(LifecycleStartupRuntime.State.CONFLICT, conflict.state());
        assertEquals(0, reads.get());
    }

    @Test void absentStoreIsUninitializedWithoutCreatingAnything() throws Exception {
        Path root = Files.createDirectory(directory.resolve("world"));
        var snapshot = LifecycleStartupRuntime.inspect(true, false, () -> {
            Path path = LifecycleStartupRuntime.safeStorePath(root);
            return new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore(path)
                    .read().map(envelope -> envelope.profiles().stream().map(SavedLifecycleProfile::state).toList());
        });

        assertEquals(LifecycleStartupRuntime.State.UNINITIALIZED, snapshot.state());
        assertEquals(0, snapshot.profileCount());
        assertTrue(Files.isDirectory(root.resolve("moonstation14-lifecycle")));
        assertFalse(Files.exists(root.resolve("moonstation14-lifecycle/profiles.json")));
    }

    @Test void corruptStoreEvidenceIsRecoveryRequiredAndOfflineOrDeadClaimsAreDeferred() {
        var offlineState = SavedLifecycleProfile.State.OFFLINE;
        var corrupt = LifecycleStartupRuntime.inspect(true, false, () -> {
            throw new IOException("lifecycle store recovery required: invalid schema");
        });
        var existing = LifecycleStartupRuntime.inspect(true, false, () -> Optional.of(List.of(
                offlineState, SavedLifecycleProfile.State.DEAD_CLAIM, offlineState)));
        var empty = LifecycleStartupRuntime.inspect(true, false, () -> Optional.of(List.of()));
        var missing = LifecycleStartupRuntime.inspect(true, false, Optional::empty);

        assertEquals(LifecycleStartupRuntime.State.RECOVERY_REQUIRED, corrupt.state());
        assertTrue(corrupt.diagnostic().contains("recovery required"));
        assertEquals(LifecycleStartupRuntime.State.DEFERRED, existing.state());
        assertEquals(3, existing.profileCount());
        assertEquals(LifecycleStartupRuntime.State.EMPTY, empty.state());
        assertEquals(LifecycleStartupRuntime.State.UNINITIALIZED, missing.state());
    }

    @Test void unsupportedStatesAndMixedBatchesRequireRecoveryWithoutOverwritingEvidence() throws Exception {
        var states = SavedLifecycleProfile.State.values();
        var offline = SavedLifecycleProfile.State.OFFLINE;
        Path evidence = directory.resolve("existing-evidence");
        Files.writeString(evidence, "do not overwrite");

        for (var state : states) {
            var result = LifecycleStartupRuntime.inspect(true, false, () -> Optional.of(List.of(state)));
            if (state == offline || state == SavedLifecycleProfile.State.DEAD_CLAIM) {
                assertEquals(LifecycleStartupRuntime.State.DEFERRED, result.state(), state.name());
            } else {
                assertEquals(LifecycleStartupRuntime.State.RECOVERY_REQUIRED, result.state(), state.name());
                assertTrue(result.diagnostic().contains(state.name()));
                assertTrue(result.diagnostic().length() <= 240);
            }
        }

        var mixed = LifecycleStartupRuntime.inspect(true, false, () -> Optional.of(List.of(offline,
                SavedLifecycleProfile.State.PREPARING)));
        assertEquals(LifecycleStartupRuntime.State.RECOVERY_REQUIRED, mixed.state());
        var activeMixed = LifecycleStartupRuntime.inspect(true, false, () -> Optional.of(List.of(offline,
                SavedLifecycleProfile.State.ACTIVE, SavedLifecycleProfile.State.DEAD_CLAIM)));
        assertEquals(LifecycleStartupRuntime.State.RECOVERY_REQUIRED, activeMixed.state());
        var corrupt = LifecycleStartupRuntime.inspect(true, false, () -> {
            throw new IOException("corrupt primary");
        });
        assertEquals(LifecycleStartupRuntime.State.RECOVERY_REQUIRED, corrupt.state());
        assertEquals("do not overwrite", Files.readString(evidence));
    }

    @Test void mixedOfflineAndDeadClaimBatchIsDeferredAndReservesExactPersistedAccounts() throws Exception {
        UUID offlineAccount = UUID.randomUUID();
        UUID deadAccount = UUID.randomUUID();
        UUID epoch = UUID.randomUUID();
        var offline = new SavedLifecycleProfile(1, offlineAccount, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of(),
                epoch, 0, SavedLifecycleProfile.State.OFFLINE, 0, 100L);
        var dead = new SavedLifecycleProfile(1, deadAccount, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", new SavedLifecycleProfile.Location(1, 64, 1), java.util.Map.of(),
                epoch, 1, SavedLifecycleProfile.State.DEAD_CLAIM, 0, 200L);
        var store = new LifecycleProfileStore(directory.resolve("mixed-death-claims.json"));
        store.compareAndSwap(-1, epoch, 1, List.of(offline, dead));

        var result = LifecycleStartupRuntime.start(true, false, store, store::read);
        Object server = new Object();
        LifecycleStartupRuntime.rememberContext(server, result.context());
        LifecycleStartupRuntime.rememberSnapshot(server, result.snapshot());

        assertEquals(LifecycleStartupRuntime.State.DEFERRED, result.snapshot().state());
        assertEquals(2, result.snapshot().profileCount());
        assertTrue(result.context().initialized());
        assertTrue(result.context().ownership().mind(offlineAccount).isEmpty());
        assertTrue(result.context().ownership().mind(deadAccount).isEmpty());
        assertTrue(LifecycleStartupRuntime.blocksDebugMindFor(server, offlineAccount));
        assertTrue(LifecycleStartupRuntime.blocksDebugMindFor(server, deadAccount));
        assertFalse(LifecycleStartupRuntime.blocksDebugMindFor(server, UUID.randomUUID()));
        var persisted = store.read().orElseThrow().profiles();
        assertEquals(Set.of(offlineAccount, deadAccount), persisted.stream()
                .map(SavedLifecycleProfile::accountId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, persisted.stream()
                .filter(profile -> profile.accountId().equals(deadAccount)).findFirst().orElseThrow().state());
        assertTrue(result.snapshot().diagnostic().contains("loaded-body proof"));
        LifecycleStartupRuntime.forgetServer(server);
    }

    @Test void storePathRejectsSymlinkedDirectoryWhenSupported() throws Exception {
        Path root = Files.createDirectory(directory.resolve("world"));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        try {
            Files.createSymbolicLink(root.resolve("moonstation14-lifecycle"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return;
        }

        assertThrows(IOException.class, () -> LifecycleStartupRuntime.safeStorePath(root));
    }

    @Test void gatedContextOwnsDistinctRegistryPairAndImmutableSavedAccountClaims() {
        AtomicInteger reads = new AtomicInteger();
        UUID account = UUID.randomUUID();
        UUID epoch = UUID.randomUUID();
        var profile = new SavedLifecycleProfile(1, account, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of(),
                epoch, 0, SavedLifecycleProfile.State.PREPARING, 0, null);
        var envelope = new LifecycleStoreEnvelope(1, 0, epoch, 0, List.of(profile));
        var store = new LifecycleProfileStore(directory.resolve("profiles.json"));
        var first = LifecycleStartupRuntime.start(true, false, store, () -> {
            reads.incrementAndGet();
            return Optional.of(envelope);
        });
        var second = LifecycleStartupRuntime.start(true, false, store, Optional::empty);

        assertNotNull(first.context());
        assertNotNull(second.context());
        assertNotSame(first.context(), second.context());
        assertNotSame(first.context().ownership(), second.context().ownership());
        assertNotSame(first.context().lifecycle(), second.context().lifecycle());
        assertTrue(first.context().lifecycle().ownsRegistry(first.context().ownership()));
        assertTrue(first.context().hasReservedClaim(account));
        assertFalse(second.context().hasReservedClaim(account));
        assertTrue(first.context().initialized());
        assertFalse(second.context().initialized());
        assertEquals(1, reads.get());

        Object serverOne = new Object();
        Object serverTwo = new Object();
        LifecycleStartupRuntime.rememberContext(serverOne, first.context());
        LifecycleStartupRuntime.rememberContext(serverTwo, second.context());
        assertSame(first.context(), LifecycleStartupRuntime.contextFor(serverOne).orElseThrow());
        assertSame(second.context(), LifecycleStartupRuntime.contextFor(serverTwo).orElseThrow());
        LifecycleStartupRuntime.discardContext(serverOne);
        assertTrue(LifecycleStartupRuntime.contextFor(serverOne).isEmpty());
        assertSame(second.context(), LifecycleStartupRuntime.contextFor(serverTwo).orElseThrow());
    }

    @Test void disabledAndCorruptStartupNeverPublishesContext() {
        AtomicInteger reads = new AtomicInteger();
        var store = new LifecycleProfileStore(directory.resolve("profiles.json"));
        var disabled = LifecycleStartupRuntime.start(false, false, store, () -> {
            reads.incrementAndGet();
            return Optional.empty();
        });
        var corrupt = LifecycleStartupRuntime.start(true, false, store, () -> {
            throw new IOException("backup-only evidence");
        });

        assertNull(disabled.context());
        assertEquals(0, reads.get());
        assertNull(corrupt.context());
        assertEquals(LifecycleStartupRuntime.State.RECOVERY_REQUIRED, corrupt.snapshot().state());
    }

    @Test void backupOnlyStoreEvidenceDoesNotPublishLifecycleContext() throws Exception {
        Path primary = directory.resolve("backup-only.json");
        Files.writeString(primary.resolveSibling(primary.getFileName() + ".bak"), "evidence");
        var store = new LifecycleProfileStore(primary);
        var result = LifecycleStartupRuntime.start(true, false, store, store::read);

        assertEquals(LifecycleStartupRuntime.State.RECOVERY_REQUIRED, result.snapshot().state());
        assertNull(result.context());
    }

    @Test void debugMindExclusionUsesExactServerSnapshotAndFailsClosedOnRecovery() {
        Object reservedServer = new Object();
        Object otherServer = new Object();
        UUID account = UUID.randomUUID();
        var offline = new SavedLifecycleProfile(1, account, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of(),
                UUID.randomUUID(), 0, SavedLifecycleProfile.State.OFFLINE, 0, 1L);
        var store = new LifecycleProfileStore(directory.resolve("reserved.json"));
        var reserved = LifecycleStartupRuntime.start(true, false, store, () -> Optional.of(
                new LifecycleStoreEnvelope(1, 0, offline.connectionEpoch(), 0, List.of(offline))));
        LifecycleStartupRuntime.rememberContext(reservedServer, reserved.context());
        LifecycleStartupRuntime.rememberSnapshot(reservedServer, reserved.snapshot());

        assertEquals(LifecycleStartupRuntime.State.DEFERRED, reserved.snapshot().state());
        assertTrue(LifecycleStartupRuntime.blocksDebugMindFor(reservedServer, account));
        assertFalse(LifecycleStartupRuntime.blocksDebugMindFor(reservedServer, UUID.randomUUID()));
        assertFalse(LifecycleStartupRuntime.blocksDebugMindFor(otherServer, account));

        LifecycleStartupRuntime.rememberSnapshot(reservedServer,
                new LifecycleStartupRuntime.StartupSnapshot(LifecycleStartupRuntime.State.RECOVERY_REQUIRED,
                        "corrupt", 0));
        assertTrue(LifecycleStartupRuntime.blocksDebugMindFor(reservedServer, UUID.randomUUID()));

        LifecycleStartupRuntime.rememberSnapshot(reservedServer,
                new LifecycleStartupRuntime.StartupSnapshot(LifecycleStartupRuntime.State.DISABLED, "disabled", 0));
        LifecycleStartupRuntime.forgetServer(reservedServer);
        assertFalse(LifecycleStartupRuntime.blocksDebugMindFor(reservedServer, account));
    }

    @Test void dynamicallyReservesOnlyExactPostCasPreparingPrimaryClaim() throws Exception {
        UUID account = UUID.randomUUID();
        UUID epoch = UUID.randomUUID();
        var preparing = new SavedLifecycleProfile(1, account, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of(),
                epoch, 0, SavedLifecycleProfile.State.PREPARING, 0, null);
        var store = new LifecycleProfileStore(directory.resolve("dynamic.json"));
        store.compareAndSwap(-1, epoch, 0, List.of());
        var context = new LifecycleServerContext(store,
                new LifecycleStoreEnvelope(1, 0, epoch, 0, List.of()));
        Object server = new Object();
        LifecycleStartupRuntime.rememberContext(server, context);
        LifecycleStartupRuntime.rememberSnapshot(server,
                new LifecycleStartupRuntime.StartupSnapshot(LifecycleStartupRuntime.State.EMPTY, "empty", 0));

        store.withCurrentPrimary(primary -> {
            var completeUpdate = primary.envelope().profiles().stream().toList();
            var casProfiles = new java.util.ArrayList<>(completeUpdate);
            casProfiles.add(preparing);
            primary.compareAndSwap(0, casProfiles);
            assertTrue(context.rememberPreparingClaim(primary, preparing));
            assertTrue(LifecycleStartupRuntime.blocksDebugMindFor(server, account));
            assertTrue(context.rememberPreparingClaim(primary, preparing)); // idempotent
            return null;
        });

        UUID rejected = UUID.randomUUID();
        var rejectedClaim = new SavedLifecycleProfile(1, rejected, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of(),
                epoch, 0, SavedLifecycleProfile.State.PREPARING, 0, null);
        store.withCurrentPrimary(primary -> {
            assertThrows(IllegalArgumentException.class, () -> primary.compareAndSwap(-1, List.of(rejectedClaim)));
            assertFalse(context.rememberPreparingClaim(primary, rejectedClaim)); // failed CAS is not admission
            assertFalse(context.rememberPreparingClaim(primary, rejectedClaim)); // no CAS for this account
            assertFalse(LifecycleStartupRuntime.blocksDebugMindFor(server, rejected));
            assertFalse(context.rememberPreparingClaim(primary,
                    new SavedLifecycleProfile(1, UUID.randomUUID(), "main", UUID.randomUUID(), UUID.randomUUID(),
                            "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of(),
                            epoch, 0, SavedLifecycleProfile.State.PREPARING, 0, null)));
            assertFalse(context.rememberPreparingClaim(primary, preparing)); // exact member exists, but this lease did not CAS it
            assertFalse(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return context.rememberPreparingClaim(primary, preparing); }
                catch (IOException failure) { throw new RuntimeException(failure); }
            }).join());
            return null;
        });

        var expired = store.withCurrentPrimary(primary -> primary).orElseThrow();
        assertFalse(context.rememberPreparingClaim(expired, preparing));
        assertFalse(LifecycleStartupRuntime.blocksDebugMindFor(new Object(), account));
        LifecycleStartupRuntime.forgetServer(server);
    }

    @Test void firstProfileReservationInitializesOnceAndReservesDifferentAccountsUnderPrimaryLease() throws Exception {
        Path path = directory.resolve("first-account.json");
        var store = new LifecycleProfileStore(path);
        var context = new LifecycleServerContext(store, null);
        UUID firstAccount = UUID.randomUUID();
        UUID firstMind = UUID.randomUUID();
        UUID firstBody = UUID.randomUUID();
        var location = new SavedLifecycleProfile.Location(1, 64, 2);

        assertTrue(context.reserveFirstProfile(firstAccount, "main", firstMind, firstBody,
                "minecraft:overworld", location, java.util.Map.of("model", "wide")));
        assertFalse(context.reserveFirstProfile(firstAccount, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", location, java.util.Map.of("model", "wide")));
        UUID secondAccount = UUID.randomUUID();
        assertTrue(context.reserveFirstProfile(secondAccount, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", location, java.util.Map.of("model", "wide")));

        var persisted = store.read().orElseThrow();
        assertEquals(2, persisted.profiles().size());
        assertTrue(persisted.profiles().stream().allMatch(profile ->
                profile.state() == SavedLifecycleProfile.State.PREPARING && profile.profileKey().equals("main")));
        assertTrue(context.hasReservedClaim(firstAccount));
        assertTrue(context.hasReservedClaim(secondAccount));
        assertTrue(context.wasCreatedOnThisServer(firstAccount));
        assertTrue(context.wasCreatedOnThisServer(secondAccount));
    }

    @Test void reservationRejectsBackupOnlyEvidenceWithoutReplacingIt() throws Exception {
        Path primary = directory.resolve("reservation-backup-only.json");
        Path backup = primary.resolveSibling(primary.getFileName() + ".bak");
        Files.writeString(backup, "operator evidence");
        var store = new LifecycleProfileStore(primary);
        var context = new LifecycleServerContext(store, null);
        assertThrows(IOException.class, () -> context.reserveFirstProfile(UUID.randomUUID(), "main",
                UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of("model", "wide")));
        assertFalse(Files.exists(primary));
        assertEquals("operator evidence", Files.readString(backup));
    }

    @Test void reservationRejectsCorruptPrimaryAndOrphanTemporaryEvidence() throws Exception {
        Path corruptPrimary = directory.resolve("reservation-corrupt.json");
        Files.writeString(corruptPrimary, "corrupt evidence");
        var corruptStore = new LifecycleProfileStore(corruptPrimary);
        var corruptContext = new LifecycleServerContext(corruptStore, null);
        assertThrows(IOException.class, () -> corruptContext.reserveFirstProfile(UUID.randomUUID(), "main",
                UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of("model", "wide")));
        assertEquals("corrupt evidence", Files.readString(corruptPrimary));

        Path orphanPrimary = directory.resolve("reservation-orphan.json");
        Path orphanTemp = orphanPrimary.resolveSibling(orphanPrimary.getFileName() + ".tmp");
        Files.writeString(orphanTemp, "orphan write evidence");
        var orphanStore = new LifecycleProfileStore(orphanPrimary);
        var orphanContext = new LifecycleServerContext(orphanStore, null);
        assertThrows(IOException.class, () -> orphanContext.reserveFirstProfile(UUID.randomUUID(), "main",
                UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 64, 0), java.util.Map.of("model", "wide")));
        assertFalse(Files.exists(orphanPrimary));
        assertEquals("orphan write evidence", Files.readString(orphanTemp));
    }

    @Test void cleanDisconnectPersistsOfflineOnlyForExactActiveMindBodyAndGeneration() throws Exception {
        UUID account = UUID.randomUUID();
        UUID mind = UUID.randomUUID();
        UUID bodyId = UUID.randomUUID();
        var store = new LifecycleProfileStore(directory.resolve("clean-disconnect.json"));
        var context = new LifecycleServerContext(store, null);
        var location = new SavedLifecycleProfile.Location(12, 64, -8);
        var appearance = java.util.Map.of("model", "wide");

        assertTrue(context.reserveFirstProfile(account, "main", mind, bodyId,
                "minecraft:overworld", location, appearance));
        var body = new MobHarness(new MobHarnessId(bodyId), MobHarnessKind.CHARACTER);
        assertTrue(context.ownership().registerHarness(body));
        assertTrue(context.stageFirstProfile(account, mind, bodyId, body.id()));
        var active = context.promoteFirstCharacter(account, mind, bodyId).orElseThrow();
        var activeSaved = store.read().orElseThrow().profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.ACTIVE, activeSaved.state());
        assertTrue(context.lifecycle().authorizes(account, active.connectionGeneration(), body.id()));
        assertNotNull(context.ownership().mind(account).orElseThrow());

        long originalStoreRevision = store.read().orElseThrow().storeRevision();
        assertFalse(context.disconnectCleanly(account, active.connectionGeneration(), UUID.randomUUID(), bodyId,
                "minecraft:overworld", location, appearance, 1234));
        assertFalse(context.disconnectCleanly(account, active.connectionGeneration(), mind, UUID.randomUUID(),
                "minecraft:overworld", location, appearance, 1234));
        assertFalse(context.disconnectCleanly(account, active.connectionGeneration() + 1, mind, bodyId,
                "minecraft:overworld", location, appearance, 1234));
        assertEquals(originalStoreRevision, store.read().orElseThrow().storeRevision());
        assertEquals(SavedLifecycleProfile.State.ACTIVE, store.read().orElseThrow().profiles().get(0).state());
        assertTrue(context.lifecycle().authorizes(account, active.connectionGeneration(), body.id()));

        assertTrue(context.disconnectCleanly(account, active.connectionGeneration(), mind, bodyId,
                "minecraft:overworld", location, appearance, 1234));

        var persisted = store.read().orElseThrow();
        assertEquals(originalStoreRevision + 1, persisted.storeRevision());
        var offlineSaved = persisted.profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.OFFLINE, offlineSaved.state());
        assertEquals(1234L, offlineSaved.offlineSinceMillis());
        assertEquals(mind, offlineSaved.mindId());
        assertEquals(bodyId, offlineSaved.bodyId());
        var disconnected = context.lifecycle().profile(account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.OFFLINE, disconnected.state());
        assertFalse(disconnected.active());
        assertEquals(active.mindId(), disconnected.mindId());
        assertEquals(body.id(), disconnected.bodyId());
        assertEquals(active.mindId(), context.ownership().mind(account).orElseThrow().id());
        assertEquals(body.id(), context.ownership().mind(account).orElseThrow().harnessId());
        assertTrue(context.ownership().registeredHarness(body.id()).isPresent());
        assertFalse(context.lifecycle().authorizes(account, active.connectionGeneration(), body.id()));
    }
}
