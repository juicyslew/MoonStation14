package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleOfflineRelocationTest {
    @TempDir Path directory;

    @Test
    void offlineRoleOwnedMobLocatorMovesOnlyOnChangedLocatorAndPreservesClaim() throws Exception {
        Fixture f = fixture("offline");
        var body = f.context.ownership().registeredHarness(new MobHarnessId(f.body)).orElseThrow();
        var old = f.store.read().orElseThrow().profiles().getFirst();
        long storeRevision = f.store.read().orElseThrow().storeRevision();
        var moved = new SavedLifecycleProfile.Location(80.25, 65, -32.75);
        assertTrue(f.context.relocateOfflineBody(f.account, f.mind, f.body, old.connectionGeneration(), body,
                "moonstation14:station", moved, row -> row.equals(old)));
        var updated = f.store.read().orElseThrow().profiles().getFirst();
        assertEquals(storeRevision + 1, f.store.read().orElseThrow().storeRevision());
        assertEquals("moonstation14:station", updated.dimension());
        assertEquals(moved, updated.location());
        assertEquals(old.accountId(), updated.accountId());
        assertEquals(old.profileKey(), updated.profileKey());
        assertEquals(old.mindId(), updated.mindId());
        assertEquals(old.bodyId(), updated.bodyId());
        assertEquals(old.connectionEpoch(), updated.connectionEpoch());
        assertEquals(old.connectionGeneration(), updated.connectionGeneration());
        assertEquals(old.state(), updated.state());
        assertEquals(old.offlineSinceMillis(), updated.offlineSinceMillis());
        assertEquals(old.appearance(), updated.appearance());

        long afterMove = f.store.read().orElseThrow().storeRevision();
        assertTrue(f.context.relocateOfflineBody(f.account, f.mind, f.body, updated.connectionGeneration(), body,
                updated.dimension(), new SavedLifecycleProfile.Location(95, 65, -33), row -> row.equals(updated)));
        assertEquals(afterMove, f.store.read().orElseThrow().storeRevision());

        var beforeDenied = f.store.read().orElseThrow();
        assertFalse(f.context.relocateOfflineBody(f.account, f.mind, UUID.randomUUID(),
                updated.connectionGeneration(), body, "moonstation14:elsewhere", moved, row -> true));
        assertFalse(f.context.relocateOfflineBody(f.account, f.mind, f.body,
                updated.connectionGeneration() + 1, body, "moonstation14:elsewhere", moved, row -> true));
        assertFalse(f.context.relocateOfflineBody(f.account, f.mind, f.body,
                updated.connectionGeneration(), body, "moonstation14:elsewhere", moved, row -> false));
        assertEquals(beforeDenied, f.store.read().orElseThrow());
    }

    @Test
    void coldOfflineBodyRestoresExactClaimOnlyAfterProofThenMovesLocator() throws Exception {
        Fixture f = fixture("cold-offline");
        var durableBefore = f.store.read().orElseThrow();
        SavedLifecycleProfile saved = durableBefore.profiles().getFirst();
        var cold = new LifecycleServerContext(f.store, durableBefore);
        var body = new MobHarness(new MobHarnessId(f.body), MobHarnessKind.CHARACTER);

        assertTrue(cold.ownership().registeredHarness(body.id()).isEmpty());
        assertTrue(cold.lifecycle().profile(f.account).isEmpty());
        assertFalse(cold.relocateOfflineBody(f.account, f.mind, f.body, saved.connectionGeneration(), body,
                "moonstation14:station", new SavedLifecycleProfile.Location(80, 65, 32), row -> false));
        assertTrue(cold.ownership().registeredHarness(body.id()).isEmpty(), "false physical proof registers nothing");
        assertTrue(cold.lifecycle().profile(f.account).isEmpty(), "false physical proof restores no lifecycle authority");
        assertEquals(durableBefore, f.store.read().orElseThrow(), "false proof leaves durable row untouched");

        assertFalse(cold.relocateOfflineBody(f.account, f.mind, f.body, saved.connectionGeneration() + 1, body,
                "moonstation14:station", new SavedLifecycleProfile.Location(80, 65, 32), row -> row.equals(saved)));
        assertTrue(cold.ownership().registeredHarness(body.id()).isEmpty(), "stale generation registers nothing");
        assertTrue(cold.lifecycle().profile(f.account).isEmpty(), "stale generation restores nothing");
        assertFalse(cold.relocateOfflineBody(f.account, UUID.randomUUID(), f.body, saved.connectionGeneration(), body,
                "moonstation14:station", new SavedLifecycleProfile.Location(80, 65, 32), row -> row.equals(saved)));
        assertFalse(cold.relocateOfflineBody(f.account, f.mind, UUID.randomUUID(), saved.connectionGeneration(), body,
                "moonstation14:station", new SavedLifecycleProfile.Location(80, 65, 32), row -> row.equals(saved)));
        assertTrue(cold.ownership().registeredHarness(body.id()).isEmpty(), "mismatched durable identity registers nothing");
        assertTrue(cold.lifecycle().profile(f.account).isEmpty(), "mismatched durable identity restores nothing");

        var conflicted = new LifecycleServerContext(f.store, durableBefore);
        MobHarness conflictingHarness = new MobHarness(body.id(), MobHarnessKind.GHOST);
        assertTrue(conflicted.ownership().registerHarness(conflictingHarness));
        assertFalse(conflicted.relocateOfflineBody(f.account, f.mind, f.body, saved.connectionGeneration(), body,
                "moonstation14:station", new SavedLifecycleProfile.Location(80, 65, 32), row -> row.equals(saved)));
        assertSame(conflictingHarness, conflicted.ownership().registeredHarness(body.id()).orElseThrow());
        assertTrue(conflicted.lifecycle().profile(f.account).isEmpty(), "conflicting registered role restores nothing");
        assertEquals(durableBefore, f.store.read().orElseThrow(), "identity and harness conflicts leave durable row untouched");

        var moved = new SavedLifecycleProfile.Location(80, 65, 32);
        assertTrue(cold.relocateOfflineBody(f.account, f.mind, f.body, saved.connectionGeneration(), body,
                "moonstation14:station", moved, row -> row.equals(saved)));
        assertSame(body, cold.ownership().registeredHarness(body.id()).orElseThrow());
        var restored = cold.lifecycle().profile(f.account).orElseThrow();
        assertEquals(PlayerLifecycleRegistry.LifecycleState.OFFLINE, restored.state());
        assertFalse(restored.active());
        assertEquals(f.mind, restored.mindId().value());
        assertEquals(f.body, restored.bodyId().value());
        assertTrue(restored.connectionGeneration() > saved.connectionGeneration(),
                "restoration creates only an in-memory fencing epoch");

        var updated = f.store.read().orElseThrow().profiles().getFirst();
        assertEquals(durableBefore.storeRevision() + 1, f.store.read().orElseThrow().storeRevision());
        assertEquals(saved.accountId(), updated.accountId());
        assertEquals(saved.profileKey(), updated.profileKey());
        assertEquals(saved.mindId(), updated.mindId());
        assertEquals(saved.bodyId(), updated.bodyId());
        assertEquals(saved.connectionEpoch(), updated.connectionEpoch());
        assertEquals(saved.connectionGeneration(), updated.connectionGeneration());
        assertEquals(saved.offlineSinceMillis(), updated.offlineSinceMillis());
        assertEquals(SavedLifecycleProfile.State.OFFLINE, updated.state());
        assertEquals("moonstation14:station", updated.dimension());
        assertEquals(moved, updated.location());
    }

    @Test
    void failedSecondProofRollsBackOnlyTheColdAuthorityCreatedByThatCall() throws Exception {
        Fixture f = fixture("rollback-second-proof");
        var envelope = f.store.read().orElseThrow();
        SavedLifecycleProfile saved = envelope.profiles().getFirst();
        var body = new MobHarness(new MobHarnessId(f.body), MobHarnessKind.CHARACTER);
        var moved = new SavedLifecycleProfile.Location(80, 65, 32);

        for (boolean throwOnSecond : List.of(false, true)) {
            var cold = new LifecycleServerContext(f.store, envelope);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            assertFalse(cold.relocateOfflineBody(f.account, f.mind, f.body, saved.connectionGeneration(), body,
                    "moonstation14:station", moved, row -> {
                        if (calls.incrementAndGet() == 1) return true;
                        if (throwOnSecond) throw new IllegalStateException("physical custody expired");
                        return false;
                    }));
            assertEquals(2, calls.get());
            assertTrue(cold.lifecycle().profile(f.account).isEmpty());
            assertTrue(cold.ownership().mind(f.account).isEmpty());
            assertTrue(cold.ownership().registeredHarness(body.id()).isEmpty());
            assertEquals(envelope, f.store.read().orElseThrow());
        }
    }

    @Test
    void failedSecondProofPreservesPreexistingOfflineAuthorityAndEmptyDurableRowDeniesBeforeRegistration()
            throws Exception {
        Fixture f = fixture("rollback-existing-authority");
        var envelope = f.store.read().orElseThrow();
        SavedLifecycleProfile saved = envelope.profiles().getFirst();
        var body = new MobHarness(new MobHarnessId(f.body), MobHarnessKind.CHARACTER);
        var moved = new SavedLifecycleProfile.Location(80, 65, 32);

        var existingHarness = new LifecycleServerContext(f.store, envelope);
        assertTrue(existingHarness.ownership().registerHarness(body));
        var harnessCalls = new java.util.concurrent.atomic.AtomicInteger();
        assertFalse(existingHarness.relocateOfflineBody(f.account, f.mind, f.body, saved.connectionGeneration(), body,
                "moonstation14:station", moved, row -> harnessCalls.incrementAndGet() == 1));
        assertSame(body, existingHarness.ownership().registeredHarness(body.id()).orElseThrow());
        assertTrue(existingHarness.lifecycle().profile(f.account).isEmpty());
        assertTrue(existingHarness.ownership().mind(f.account).isEmpty());

        var existingClaim = new LifecycleServerContext(f.store, envelope);
        assertTrue(existingClaim.ownership().registerHarness(body));
        var prior = f.store.withCurrentPrimary(lease -> existingClaim.lifecycle()
                .restoreOffline(lease, saved, target -> target == body)).orElseThrow().orElseThrow();
        var priorMind = existingClaim.ownership().mind(f.account).orElseThrow();
        var claimCalls = new java.util.concurrent.atomic.AtomicInteger();
        assertFalse(existingClaim.relocateOfflineBody(f.account, f.mind, f.body, saved.connectionGeneration(), body,
                "moonstation14:station", moved, row -> claimCalls.incrementAndGet() == 1));
        assertEquals(prior, existingClaim.lifecycle().profile(f.account).orElseThrow());
        assertEquals(priorMind, existingClaim.ownership().mind(f.account).orElseThrow());
        assertSame(body, existingClaim.ownership().registeredHarness(body.id()).orElseThrow());
        assertEquals(envelope, f.store.read().orElseThrow());

        var emptyStore = new LifecycleProfileStore(directory.resolve("empty-durable-row.json"));
        emptyStore.compareAndSwap(-1, UUID.randomUUID(), 0, List.of());
        var emptyEnvelope = emptyStore.read().orElseThrow();
        var emptyContext = new LifecycleServerContext(emptyStore, emptyEnvelope);
        assertFalse(emptyContext.relocateOfflineBody(UUID.randomUUID(), f.mind, f.body, 0, body,
                "moonstation14:station", moved, row -> { fail("proof is not evaluated without an exact durable row"); return true; }));
        assertTrue(emptyContext.lifecycle().profile(f.account).isEmpty());
        assertTrue(emptyContext.ownership().mind(f.account).isEmpty());
        assertTrue(emptyContext.ownership().registeredHarness(body.id()).isEmpty());
        assertEquals(emptyEnvelope, emptyStore.read().orElseThrow());
    }

    @Test
    void activeAndUnclaimedBodiesAreNotRelocatedOrEnrolled() throws Exception {
        var active = new LifecycleProfileStore(directory.resolve("active.json"));
        active.compareAndSwap(-1, UUID.randomUUID(), 0, List.of());
        UUID activeAccount = UUID.randomUUID(), activeMind = UUID.randomUUID(), activeBody = UUID.randomUUID();
        var activeContext = new LifecycleServerContext(active, active.read().orElseThrow());
        assertTrue(activeContext.reserveFirstProfile(activeAccount, "main", activeMind, activeBody,
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), Map.of()));
        var activeHarness = new MobHarness(new MobHarnessId(activeBody), MobHarnessKind.CHARACTER);
        assertTrue(activeContext.ownership().registerHarness(activeHarness));
        assertTrue(activeContext.stageFirstProfile(activeAccount, activeMind, activeBody, activeHarness.id()));
        var activeSnapshot = activeContext.promoteFirstCharacter(activeAccount, activeMind, activeBody).orElseThrow();
        var activeRow = active.read().orElseThrow().profiles().getFirst();
        assertFalse(activeContext.relocateOfflineBody(activeAccount, activeMind, activeBody,
                activeSnapshot.connectionGeneration(), activeHarness, "minecraft:the_nether",
                new SavedLifecycleProfile.Location(0, 70, 0), ignored -> true));
        assertEquals(activeRow, active.read().orElseThrow().profiles().getFirst());

        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), bodyId = UUID.randomUUID();
        var unclaimedStore = new LifecycleProfileStore(directory.resolve("unclaimed.json"));
        var unclaimed = new LifecycleServerContext(unclaimedStore, null);
        var mob = new MobHarness(new MobHarnessId(bodyId), MobHarnessKind.CHARACTER);
        assertFalse(unclaimed.relocateOfflineBody(account, mind, bodyId, 0, mob, "minecraft:overworld",
                new SavedLifecycleProfile.Location(1, 64, 1), ignored -> true));
        assertTrue(unclaimedStore.read().isEmpty());
        assertTrue(unclaimed.ownership().registeredHarness(mob.id()).isEmpty());
    }

    private Fixture fixture(String name) throws Exception {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), body = UUID.randomUUID();
        var store = new LifecycleProfileStore(directory.resolve(name + ".json"));
        store.compareAndSwap(-1, UUID.randomUUID(), 0, List.of());
        var context = new LifecycleServerContext(store, store.read().orElseThrow());
        assertTrue(context.reserveFirstProfile(account, "main", mind, body, "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 64, 0), Map.of("appearance", "mouse")));
        var harness = new MobHarness(new MobHarnessId(body), MobHarnessKind.CHARACTER);
        assertTrue(context.ownership().registerHarness(harness));
        assertTrue(context.stageFirstProfile(account, mind, body, harness.id()));
        var active = context.promoteFirstCharacter(account, mind, body).orElseThrow();
        assertTrue(context.disconnectCleanly(account, active.connectionGeneration(), mind, body,
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), Map.of(), 123456));
        return new Fixture(store, context, account, mind, body);
    }

    private record Fixture(LifecycleProfileStore store, LifecycleServerContext context,
                           UUID account, UUID mind, UUID body) { }
}
