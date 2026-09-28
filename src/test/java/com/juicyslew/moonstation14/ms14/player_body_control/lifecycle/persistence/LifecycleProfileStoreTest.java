package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.TargetEligibility;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleProfileStoreTest {
    @TempDir Path directory;
    private static final UUID EPOCH = id(90);

    @Test void initializesReadsAndPreservesMultipleUniqueAccountProfiles() throws Exception {
        var store = new LifecycleProfileStore(directory.resolve("profiles.json"));
        assertTrue(store.read().isEmpty());
        var first = profile(1, 11, 21, 0);
        var second = profile(2, 12, 22, 0);
        var saved = store.compareAndSwap(-1, EPOCH, 0, List.of(first, second));
        assertEquals(0, saved.storeRevision());
        assertEquals(List.of(first, second), new LifecycleProfileStore(directory.resolve("profiles.json")).read().orElseThrow().profiles());
    }

    @Test void casConflictAndRestartKeepEpochAndGenerationMetadata() throws Exception {
        Path path = directory.resolve("profiles.json");
        var store = new LifecycleProfileStore(path);
        var original = profile(1, 11, 21, 3);
        store.compareAndSwap(-1, EPOCH, 3, List.of(original));
        var reloaded = new LifecycleProfileStore(path);
        var snapshot = reloaded.read().orElseThrow();
        assertEquals(EPOCH, snapshot.epochToken());
        assertEquals(3, snapshot.generationCounter());
        assertEquals(3, snapshot.profiles().get(0).connectionGeneration());
        assertThrows(LifecycleProfileStore.RevisionConflictException.class,
                () -> reloaded.compareAndSwap(-1, EPOCH, 4, List.of(original)));
        var nextProfile = profile(1, 11, 21, 4);
        var next = reloaded.compareAndSwap(0, EPOCH, 4, List.of(nextProfile));
        assertEquals(1, next.storeRevision());
        assertEquals(4, new LifecycleProfileStore(path).read().orElseThrow().profiles().get(0).connectionGeneration());
        assertThrows(IllegalArgumentException.class, () -> reloaded.compareAndSwap(1, id(91), 5, List.of(nextProfile)));
    }

    @Test void separatelyConstructedStoresRejectStaleRevisionWithoutOverwritingWinner() throws Exception {
        Path path = directory.resolve("shared.json");
        var first = new LifecycleProfileStore(path);
        var second = new LifecycleProfileStore(path);
        first.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
        var winner = first.compareAndSwap(0, EPOCH, 1, List.of(profile(1, 11, 21, 1)));

        assertThrows(LifecycleProfileStore.RevisionConflictException.class,
                () -> second.compareAndSwap(0, EPOCH, 2, List.of(profile(1, 11, 21, 2))));
        assertEquals(winner, new LifecycleProfileStore(path).read().orElseThrow());
    }

    @Test void currentPrimaryLeaseIsScopedToLockedCallbackAndInvalidatedOnException() throws Exception {
        Path path = directory.resolve("current-primary.json");
        var store = new LifecycleProfileStore(path);
        var primary = store.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
        LifecycleProfileStore.CurrentPrimary[] captured = new LifecycleProfileStore.CurrentPrimary[1];
        assertEquals(primary, store.withCurrentPrimary(proof -> {
            assertTrue(proof.isActive());
            captured[0] = proof;
            return proof.envelope();
        }).orElseThrow());
        assertFalse(captured[0].isActive());

        assertThrows(IllegalStateException.class, () -> store.withCurrentPrimary(proof -> {
            captured[0] = proof;
            throw new IllegalStateException("callback failure");
        }));
        assertFalse(captured[0].isActive());

        Path lock = path.resolveSibling(path.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = channel.lock()) {
            assertThrows(IOException.class, () -> store.withCurrentPrimary(proof -> proof.envelope()));
        }
    }

    @Test void currentPrimaryCasUpdatesUnderExistingLockAndLeaseExpiresAfterCallback() throws Exception {
        Path path = directory.resolve("leased-cas.json");
        var store = new LifecycleProfileStore(path);
        var original = profile(1, 11, 21, 0);
        var primary = store.compareAndSwap(-1, EPOCH, 0, List.of(original));
        LifecycleProfileStore.CurrentPrimary[] captured = new LifecycleProfileStore.CurrentPrimary[1];
        var nextProfile = profile(1, 11, 21, 1);

        var updated = store.withCurrentPrimary(proof -> {
            captured[0] = proof;
            return proof.compareAndSwap(1, List.of(nextProfile));
        }).orElseThrow();

        assertEquals(primary.storeRevision() + 1, updated.storeRevision());
        assertEquals(1, updated.generationCounter());
        assertEquals(List.of(nextProfile), updated.profiles());
        assertEquals(updated, new LifecycleProfileStore(path).read().orElseThrow());
        assertThrows(IllegalStateException.class, () -> captured[0].compareAndSwap(2, List.of(profile(1, 11, 21, 2))));
    }

    @Test void injectedPrimaryWriteFailureLeavesRestoredMindAndBodyOffline() throws Exception {
        Path path = directory.resolve("durable-reconnect-failure.json");
        var saved = new SavedLifecycleProfile(1, id(301), "main", id(302), id(303), "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 0, 0), Map.of(), EPOCH, 1,
                SavedLifecycleProfile.State.OFFLINE, 0, 1L);
        new LifecycleProfileStore(path).compareAndSwap(-1, EPOCH, 3, List.of(saved));
        var failingStore = new LifecycleProfileStore(path, new InjectedFault(UpdateFault.PRIMARY_MOVE));
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        var body = new MobHarness(new MobHarnessId(id(303)), MobHarnessKind.CHARACTER);
        assertTrue(lifecycle.registerHarness(body));
        TargetEligibility eligible = mob -> true;
        var restored = failingStore.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, saved, eligible))
                .orElseThrow().orElseThrow();

        assertThrows(IOException.class, () -> failingStore.withCurrentPrimary(proof ->
                lifecycle.reconnectLivingDurably(proof, saved, true, eligible)));
        assertEquals(restored, lifecycle.profile(id(301)).orElseThrow());
        assertFalse(lifecycle.authorizes(id(301), restored.connectionGeneration(), body.id()));
        assertEquals(SavedLifecycleProfile.State.OFFLINE,
                LifecycleProfileCodec.decodeStore(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
                        .profiles().get(0).state());
    }

    @Test void injectedDisconnectWriteFailureLeavesDurableActiveMindAuthorized() throws Exception {
        Path path = directory.resolve("durable-disconnect-failure.json");
        UUID account = id(311);
        var saved = new SavedLifecycleProfile(1, account, "main", id(312), id(313), "minecraft:overworld",
                new SavedLifecycleProfile.Location(1, 2, 3), Map.of("hair", "blue"), EPOCH, 1,
                SavedLifecycleProfile.State.OFFLINE, 0, 1L);
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, EPOCH, 4, List.of(saved));
        BodyControlRegistry ownership = new BodyControlRegistry();
        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry(ownership);
        var body = new MobHarness(new MobHarnessId(id(313)), MobHarnessKind.CHARACTER);
        assertTrue(lifecycle.registerHarness(body));
        TargetEligibility eligible = mob -> true;
        store.withCurrentPrimary(proof -> lifecycle.restoreOffline(proof, saved, eligible)).orElseThrow().orElseThrow();
        var connected = store.withCurrentPrimary(proof -> lifecycle.reconnectLivingDurably(proof, saved, true, eligible))
                .orElseThrow().orElseThrow();
        var active = store.read().orElseThrow().profiles().get(0);
        var failingStore = new LifecycleProfileStore(path, new InjectedFault(UpdateFault.PRIMARY_MOVE));

        assertThrows(IOException.class, () -> failingStore.withCurrentPrimary(proof ->
                lifecycle.disconnectDurably(proof, active, connected.connectionGeneration(), 1234)));
        assertEquals(connected, lifecycle.profile(account).orElseThrow());
        assertTrue(lifecycle.authorizes(account, connected.connectionGeneration(), body.id()));
        assertEquals(active, LifecycleProfileCodec.decodeStore(Files.readString(path)).profiles().get(0));
    }

    @Test void leasedCasRejectsStalePrimaryAndInvalidSnapshotWithoutOverwritingWinner() throws Exception {
        Path path = directory.resolve("leased-conflict.json");
        var store = new LifecycleProfileStore(path);
        var original = profile(1, 11, 21, 0);
        var primary = store.compareAndSwap(-1, EPOCH, 0, List.of(original));

        assertThrows(LifecycleProfileStore.RevisionConflictException.class, () -> store.withCurrentPrimary(proof -> {
            // Simulate a non-cooperating replacement while the lease is live. The CAS must still
            // compare the revision observed by the lease, rather than trust stale provenance.
            var winner = new LifecycleStoreEnvelope(LifecycleStoreEnvelope.CURRENT_SCHEMA,
                    primary.storeRevision() + 1, EPOCH, 1, List.of(profile(1, 11, 21, 1)));
            Files.writeString(path, LifecycleProfileCodec.encodeStore(winner));
            return proof.compareAndSwap(2, List.of(profile(1, 11, 21, 2)));
        }));
        var winner = new LifecycleProfileStore(path).read().orElseThrow();
        assertEquals(1, winner.generationCounter());

        Path invalidPath = directory.resolve("leased-invalid.json");
        var invalidStore = new LifecycleProfileStore(invalidPath);
        invalidStore.compareAndSwap(-1, EPOCH, 0, List.of(original));
        assertThrows(IllegalArgumentException.class, () -> invalidStore.withCurrentPrimary(proof ->
                proof.compareAndSwap(1, List.of())));
        assertEquals(0, new LifecycleProfileStore(invalidPath).read().orElseThrow().storeRevision());
        assertEquals(List.of(original), new LifecycleProfileStore(invalidPath).read().orElseThrow().profiles());
    }

    @Test void currentPrimaryRejectsCorruptBackupBeforeCallback() throws Exception {
        Path path = directory.resolve("current-primary-corrupt-backup.json");
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
        var primary = store.compareAndSwap(0, EPOCH, 1, List.of(profile(1, 11, 21, 1)));
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        Files.writeString(backup, "{truncated");
        boolean[] called = {false};

        assertThrows(IOException.class, () -> store.withCurrentPrimary(proof -> {
            called[0] = true;
            return proof.envelope();
        }));
        assertFalse(called[0]);
        assertEquals(primary, LifecycleProfileCodec.decodeStore(Files.readString(path)));
    }

    @Test void currentPrimaryRejectsHigherRevisionBackupBeforeCallback() throws Exception {
        Path path = directory.resolve("current-primary-ahead-backup.json");
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
        var primary = store.compareAndSwap(0, EPOCH, 1, List.of(profile(1, 11, 21, 1)));
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        Files.writeString(backup, LifecycleProfileCodec.encodeStore(new LifecycleStoreEnvelope(
                LifecycleStoreEnvelope.CURRENT_SCHEMA, primary.storeRevision() + 1, EPOCH,
                primary.generationCounter(), primary.profiles())));
        boolean[] called = {false};

        assertThrows(IOException.class, () -> store.withCurrentPrimary(proof -> {
            called[0] = true;
            return proof.envelope();
        }));
        assertFalse(called[0]);
        assertEquals(primary, LifecycleProfileCodec.decodeStore(Files.readString(path)));
    }

    @Test void currentPrimaryAllowsOlderValidBackupWithoutSelectingIt() throws Exception {
        Path path = directory.resolve("current-primary-valid-backup.json");
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
        var primary = store.compareAndSwap(0, EPOCH, 1, List.of(profile(1, 11, 21, 1)));
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        var backupEnvelope = LifecycleProfileCodec.decodeStore(Files.readString(backup));

        var leased = store.withCurrentPrimary(proof -> proof.envelope()).orElseThrow();

        assertEquals(primary, leased);
        assertNotEquals(backupEnvelope, leased);
        assertEquals(0, backupEnvelope.storeRevision());
        assertEquals(1, leased.storeRevision());
    }

    @Test void profileGenerationCannotRegressAndWriterLockIsFailClosed() throws Exception {
        Path path = directory.resolve("generation.json");
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, EPOCH, 5, List.of(profile(1, 11, 21, 5)));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 5,
                List.of(profile(1, 11, 21, 4))));
        assertEquals(0, store.read().orElseThrow().storeRevision());

        Path lock = path.resolveSibling(path.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = channel.lock()) {
            assertThrows(IOException.class, () -> store.compareAndSwap(0, EPOCH, 5,
                    List.of(profile(1, 11, 21, 5))));
        }
        assertEquals(0, store.read().orElseThrow().storeRevision());
    }

    @Test void existingAccountProfileCannotBeDeletedOrReplaced() throws Exception {
        Path path = directory.resolve("persistent-identity.json");
        var store = new LifecycleProfileStore(path);
        var original = profile(1, 11, 21, 2);
        store.compareAndSwap(-1, EPOCH, 2, List.of(original));

        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 3, List.of()));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 3,
                List.of(profile(1, 11, 21, 3, "replacement"))));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 3,
                List.of(profile(1, 12, 21, 3))));
        assertEquals(0, store.read().orElseThrow().storeRevision());
        assertEquals(List.of(original), store.read().orElseThrow().profiles());
    }

    @Test void rejectedRemovalCannotBeUsedToReaddAnAccountAtAnOldGeneration() throws Exception {
        Path path = directory.resolve("remove-readd.json");
        var store = new LifecycleProfileStore(path);
        var original = profile(1, 11, 21, 7);
        store.compareAndSwap(-1, EPOCH, 7, List.of(original));

        // The deletion CAS is rejected, so there is no later revision in which stale re-addition can
        // appear to be a first insertion.
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 7, List.of()));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 7,
                List.of(profile(1, 11, 21, 6))));
        var unchanged = store.read().orElseThrow();
        assertEquals(0, unchanged.storeRevision());
        assertEquals(List.of(original), unchanged.profiles());
    }

    @Test void initialEnrollmentReservationCannotBePromotedDeletedOrChangedAfterFailure() throws Exception {
        Path path = directory.resolve("initial-enrollment.json");
        var store = new LifecycleProfileStore(path);
        var preparing = enrollment(SavedLifecycleProfile.State.PREPARING, 0, 0);
        store.compareAndSwap(-1, EPOCH, 0, List.of(preparing));

        // No saga receipt exists in this persistence foundation, so neither direct ACTIVE nor
        // OFFLINE promotion can authorize a partially-created body/Mind.
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 1,
                List.of(enrollment(SavedLifecycleProfile.State.ACTIVE, 1, 1))));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, 0, List.of()));

        var recovery = enrollment(SavedLifecycleProfile.State.RECOVERY_REQUIRED, 0, 1);
        var failed = store.compareAndSwap(0, EPOCH, 0, List.of(recovery));
        assertEquals(List.of(recovery), failed.profiles());
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(1, EPOCH, 1,
                List.of(enrollment(SavedLifecycleProfile.State.PREPARING, 1, 2))));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(1, EPOCH, 1, List.of()));
        assertEquals(List.of(recovery), new LifecycleProfileStore(path).read().orElseThrow().profiles());
    }

    @Test void firstEnrollmentPromotionPrimaryWriteFailureLeavesPreparingMindDisconnected() throws Exception {
        Path path = directory.resolve("promotion-primary-failure.json");
        var preparing = enrollment(SavedLifecycleProfile.State.PREPARING, 0, 0);
        new LifecycleProfileStore(path).compareAndSwap(-1, EPOCH, 0, List.of(preparing));
        var store = new LifecycleProfileStore(path, new InjectedFault(UpdateFault.PRIMARY_MOVE));
        var ownership = new BodyControlRegistry();
        var lifecycle = new PlayerLifecycleRegistry(ownership);
        var bodyId = new MobHarnessId(preparing.bodyId());
        assertTrue(ownership.registerHarness(new MobHarness(bodyId, MobHarnessKind.CHARACTER)));
        var staged = store.withCurrentPrimary(primary -> lifecycle.stageFirstCharacter(primary, preparing, target -> true))
                .orElseThrow().orElseThrow();

        assertThrows(IOException.class, () -> store.withCurrentPrimary(primary ->
                lifecycle.promoteFirstCharacterDurably(primary, preparing)));
        var durable = LifecycleProfileCodec.decodeStore(Files.readString(path)).profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.PREPARING, durable.state());
        assertFalse(lifecycle.authorizes(preparing.accountId(), staged.connectionGeneration(), bodyId));
        assertFalse(ownership.authorizes(preparing.accountId(), bodyId, staged.connectionGeneration(), target -> true));
        assertEquals(PlayerLifecycleRegistry.LifecycleState.PREPARING,
                lifecycle.profile(preparing.accountId()).orElseThrow().state());
    }

    @Test void initialEnrollmentPartialWriteFailureKeepsReservedAccountAndMaxGenerationIsRepresentable() throws Exception {
        Path path = directory.resolve("initial-enrollment-failure.json");
        var store = new LifecycleProfileStore(path);
        var preparing = enrollment(SavedLifecycleProfile.State.PREPARING, Long.MAX_VALUE, 0);
        store.compareAndSwap(-1, EPOCH, Long.MAX_VALUE, List.of(preparing));
        assertThrows(IllegalArgumentException.class, () -> store.compareAndSwap(0, EPOCH, Long.MIN_VALUE,
                List.of(enrollment(SavedLifecycleProfile.State.PREPARING, Long.MIN_VALUE, 1))));
        var failing = new LifecycleProfileStore(path, new InjectedFault(UpdateFault.PRIMARY_MOVE));

        assertThrows(IOException.class, () -> failing.compareAndSwap(0, EPOCH, Long.MAX_VALUE,
                List.of(enrollment(SavedLifecycleProfile.State.RECOVERY_REQUIRED, Long.MAX_VALUE, 1))));
        assertEquals(List.of(preparing), LifecycleProfileCodec.decodeStore(Files.readString(path)).profiles());
        assertThrows(IOException.class, store::read); // retained temp/backup evidence prevents a silent retry
    }

    @Test void firstAccountAdditionAndGhostBodyTransitionAreAllowed() throws Exception {
        Path path = directory.resolve("legal-transitions.json");
        var store = new LifecycleProfileStore(path);
        var initial = profile(1, 11, 21, 0);
        store.compareAndSwap(-1, EPOCH, 0, List.of(initial));

        var added = profile(2, 12, 22, 1);
        var ghost = new SavedLifecycleProfile(1, id(1), "main", id(11), id(23), "minecraft:overworld",
                new SavedLifecycleProfile.Location(4, 5, 6), Map.of(), EPOCH, 1,
                SavedLifecycleProfile.State.GHOST, 1, null);
        var saved = store.compareAndSwap(0, EPOCH, 1, List.of(ghost, added));
        assertEquals(1, saved.storeRevision());
        assertEquals(List.of(ghost, added), saved.profiles());
    }

    @Test void corruptUnsupportedBackupAndOrphanTemporaryFailClosed() throws Exception {
        Path path = directory.resolve("profiles.json");
        var store = new LifecycleProfileStore(path);
        store.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
        Files.writeString(path, "{truncated");
        assertThrows(IOException.class, store::read);

        Path unsupported = directory.resolve("unsupported.json");
        Files.writeString(unsupported, "{\"schemaVersion\":2}");
        assertThrows(IOException.class, () -> new LifecycleProfileStore(unsupported).read());

        Path backupOnly = directory.resolve("backup-only.json");
        Files.writeString(backupOnly.resolveSibling("backup-only.json.bak"), "{}");
        assertThrows(IOException.class, () -> new LifecycleProfileStore(backupOnly).read());

        Path orphan = directory.resolve("orphan.json");
        Files.writeString(orphan.resolveSibling("orphan.json.tmp"), "partial");
        assertThrows(IOException.class, () -> new LifecycleProfileStore(orphan).read());
        assertThrows(IOException.class, () -> new LifecycleProfileStore(orphan)
                .compareAndSwap(-1, EPOCH, 0, List.of()));
    }

    @Test void stagedUpdateFaultsPreservePrimaryAndLeaveFailClosedEvidence() throws Exception {
        for (UpdateFault fault : UpdateFault.values()) {
            Path path = directory.resolve("failure-" + fault.name().toLowerCase() + ".json");
            var good = new LifecycleProfileStore(path);
            var prior = good.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
            var failing = new LifecycleProfileStore(path, new InjectedFault(fault));

            assertThrows(IOException.class, () -> failing.compareAndSwap(0, EPOCH, 1,
                    List.of(profile(1, 11, 21, 1))), fault.name());
            assertEquals(prior, LifecycleProfileCodec.decodeStore(Files.readString(path)), fault.name());
            assertTrue(Files.exists(path.resolveSibling(path.getFileName() + ".tmp")), fault.name());
            if (fault == UpdateFault.BACKUP_STAGING || fault == UpdateFault.BACKUP_MOVE)
                assertTrue(Files.exists(path.resolveSibling(path.getFileName() + ".bak.tmp")), fault.name());
            if (fault == UpdateFault.PRIMARY_MOVE)
                assertEquals(prior, LifecycleProfileCodec.decodeStore(Files.readString(
                        path.resolveSibling(path.getFileName() + ".bak"))));
            assertThrows(IOException.class, good::read, fault.name());
        }
    }

    @Test void casRejectionDoesNotLoseNewestRecordAndCanContinueFromIt() throws Exception {
        Path path = directory.resolve("cas-rejected.json");
        var stale = new LifecycleProfileStore(path);
        var winner = new LifecycleProfileStore(path);
        stale.compareAndSwap(-1, EPOCH, 0, List.of(profile(1, 11, 21, 0)));
        winner.compareAndSwap(0, EPOCH, 1, List.of(profile(1, 11, 21, 1)));

        assertThrows(LifecycleProfileStore.RevisionConflictException.class,
                () -> stale.compareAndSwap(0, EPOCH, 2, List.of(profile(1, 11, 21, 2))));
        var newest = stale.read().orElseThrow();
        assertEquals(1, newest.storeRevision());
        assertEquals(1, newest.profiles().get(0).connectionGeneration());
        var continued = stale.compareAndSwap(newest.storeRevision(), EPOCH, 2,
                List.of(profile(1, 11, 21, 2)));
        assertEquals(2, continued.storeRevision());
        assertEquals(continued, new LifecycleProfileStore(path).read().orElseThrow());
    }

    private static SavedLifecycleProfile profile(long account, long mind, long body, long generation) {
        return profile(account, mind, body, generation, "main");
    }
    private static SavedLifecycleProfile profile(long account, long mind, long body, long generation, String profileKey) {
        return new SavedLifecycleProfile(1, id(account), profileKey, id(mind), id(body), "minecraft:overworld",
                new SavedLifecycleProfile.Location(1, 2, 3), Map.of(), EPOCH, generation,
                SavedLifecycleProfile.State.ACTIVE, 0, null);
    }
    private static SavedLifecycleProfile enrollment(SavedLifecycleProfile.State state, long generation, long revision) {
        return new SavedLifecycleProfile(1, id(501), "main", id(502), id(503), "minecraft:overworld",
                new SavedLifecycleProfile.Location(1, 2, 3), Map.of(), EPOCH, generation, state, revision, null);
    }
    private static UUID id(long value) { return new UUID(0, value); }

    private enum UpdateFault { TEMP_PARTIAL_WRITE, BACKUP_STAGING, BACKUP_MOVE, PRIMARY_MOVE }

    private static final class InjectedFault implements LifecycleProfileStore.FileOperations {
        private final UpdateFault fault;
        private final LifecycleProfileStore.FileOperations delegate = new LifecycleProfileStore.FileOperations() {
            @Override public boolean exists(Path path) { return Files.exists(path); }
            @Override public byte[] readAll(Path path) throws IOException { return Files.readAllBytes(path); }
            @Override public void writeNew(Path path, byte[] bytes) throws IOException {
                Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            @Override public void atomicMove(Path source, Path target, boolean replace) throws IOException {
                Files.move(source, target, replace ? new java.nio.file.CopyOption[]{java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING} : new java.nio.file.CopyOption[]{java.nio.file.StandardCopyOption.ATOMIC_MOVE});
            }
        };
        private InjectedFault(UpdateFault fault) { this.fault = fault; }
        @Override public boolean exists(Path path) { return delegate.exists(path); }
        @Override public byte[] readAll(Path path) throws IOException { return delegate.readAll(path); }
        @Override public void writeNew(Path path, byte[] bytes) throws IOException {
            String name = path.getFileName().toString();
            if (fault == UpdateFault.TEMP_PARTIAL_WRITE && name.endsWith(".tmp") && !name.endsWith(".bak.tmp"))
                writePartialThenFail(path, bytes);
            if (fault == UpdateFault.BACKUP_STAGING && name.endsWith(".bak.tmp"))
                writePartialThenFail(path, bytes);
            delegate.writeNew(path, bytes);
        }
        @Override public void atomicMove(Path source, Path target, boolean replace) throws IOException {
            String name = target.getFileName().toString();
            if (fault == UpdateFault.BACKUP_MOVE && name.toString().endsWith(".bak"))
                throw new java.nio.file.AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected backup move failure");
            if (fault == UpdateFault.PRIMARY_MOVE && !name.toString().endsWith(".bak"))
                throw new java.nio.file.AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected");
            delegate.atomicMove(source, target, replace);
        }

        private static void writePartialThenFail(Path path, byte[] bytes) throws IOException {
            try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                channel.write(java.nio.ByteBuffer.wrap(bytes, 0, Math.max(1, bytes.length / 2)));
            }
            throw new IOException("injected partial staged write failure");
        }
    }
}
