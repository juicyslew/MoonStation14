package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleCharacterDeathHandlerTest {
    @TempDir Path directory;

    @Test
    void confirmedCallbackRequiresEveryGateAndExactServerBody() {
        assertTrue(eligible(true, false, true, true, true, true));
        assertFalse(eligible(false, false, true, true, true, true));
        assertFalse(eligible(true, true, true, true, true, true));
        assertFalse(eligible(true, false, false, true, true, true)); // canceled vanilla death
        assertFalse(eligible(true, false, true, false, true, true));
        assertFalse(eligible(true, false, true, true, false, true));
        assertFalse(eligible(true, false, true, true, true, false));
    }

    @Test
    void offlineDeathGateRequiresReservedOrSameServerCreatedAccountAndSafeStartupState() throws Exception {
        UUID account = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        var store = new LifecycleProfileStore(directory.resolve("gate.json"));
        var fresh = new LifecycleServerContext(store, null);
        assertFalse(offlineEligible(LifecycleStartupRuntime.State.UNINITIALIZED, fresh, account));
        assertTrue(fresh.reserveFirstProfile(account, "main", UUID.randomUUID(), UUID.randomUUID(),
                "minecraft:overworld", new SavedLifecycleProfile.Location(0, 64, 0), Map.of()));
        for (var state : List.of(LifecycleStartupRuntime.State.UNINITIALIZED, LifecycleStartupRuntime.State.EMPTY)) {
            assertTrue(offlineEligible(state, fresh, account), state.name());
            assertFalse(offlineEligible(state, fresh, foreign), state.name());
            assertFalse(offlineEligible(state, null, account), state.name());
        }
        for (var state : List.of(LifecycleStartupRuntime.State.RECOVERY_REQUIRED,
                LifecycleStartupRuntime.State.DISABLED, LifecycleStartupRuntime.State.CONFLICT,
                LifecycleStartupRuntime.State.STOPPED)) {
            assertFalse(offlineEligible(state, fresh, account), state.name());
        }
        assertFalse(offlineEligible(null, fresh, account));

        // A restart reserves persisted accounts but must not treat them as newly created on this server.
        var restarted = new LifecycleServerContext(store, store.read().orElseThrow());
        assertFalse(offlineEligible(LifecycleStartupRuntime.State.EMPTY, restarted, account));
        assertFalse(offlineEligible(LifecycleStartupRuntime.State.UNINITIALIZED, restarted, account));
        assertTrue(offlineEligible(LifecycleStartupRuntime.State.DEFERRED, restarted, account));
        assertFalse(offlineEligible(LifecycleStartupRuntime.State.DEFERRED, restarted, foreign));
    }

    @Test
    void freshEnrollmentOfflineDeathClaimsOnlyAfterExactCurrentRowAndEvidence() throws Exception {
        for (var startup : List.of(LifecycleStartupRuntime.State.UNINITIALIZED, LifecycleStartupRuntime.State.EMPTY)) {
            UUID account = UUID.randomUUID();
            UUID mind = UUID.randomUUID();
            UUID bodyId = UUID.randomUUID();
            var store = new LifecycleProfileStore(directory.resolve(startup.name() + ".json"));
            if (startup == LifecycleStartupRuntime.State.EMPTY)
                store.compareAndSwap(-1, UUID.randomUUID(), 0, List.of());
            var context = new LifecycleServerContext(store, store.read().orElse(null));
            var location = new SavedLifecycleProfile.Location(0, 64, 0);
            var appearance = Map.<String, String>of();
            assertTrue(context.reserveFirstProfile(account, "main", mind, bodyId,
                    "minecraft:overworld", location, appearance));
            var corpse = new MobHarness(new MobHarnessId(bodyId), MobHarnessKind.CHARACTER);
            assertTrue(context.ownership().registerHarness(corpse));
            assertTrue(context.stageFirstProfile(account, mind, bodyId, corpse.id()));
            var active = context.promoteFirstCharacter(account, mind, bodyId).orElseThrow();
            assertTrue(context.disconnectCleanly(account, active.connectionGeneration(), mind, bodyId,
                    "minecraft:overworld", location, appearance, 1234));
            var offline = store.read().orElseThrow().profiles().get(0);
            assertEquals(SavedLifecycleProfile.State.OFFLINE, offline.state());
            assertTrue(offlineEligible(startup, context, account));
            long revision = store.read().orElseThrow().storeRevision();
            assertFalse(context.claimOfflineDeath(offline, corpse, ignored -> false));
            assertFalse(context.claimOfflineDeath(offline, new MobHarness(new MobHarnessId(UUID.randomUUID()),
                    MobHarnessKind.CHARACTER), ignored -> true));
            assertEquals(revision, store.read().orElseThrow().storeRevision());
            assertEquals(SavedLifecycleProfile.State.OFFLINE, store.read().orElseThrow().profiles().get(0).state());

            assertTrue(context.claimOfflineDeath(offline, corpse, candidate -> candidate.equals(corpse)));
            assertEquals(revision + 1, store.read().orElseThrow().storeRevision());
            assertEquals(SavedLifecycleProfile.State.DEAD_CLAIM, store.read().orElseThrow().profiles().get(0).state());
            assertFalse(context.claimOfflineDeath(offline, corpse, candidate -> candidate.equals(corpse)));
            assertEquals(revision + 1, store.read().orElseThrow().storeRevision());
        }
    }

    private static boolean offlineEligible(LifecycleStartupRuntime.State state, LifecycleServerContext context,
                                           UUID account) {
        return LifecycleCharacterDeathHandler.offlineDeathStateEligible(state, context, account);
    }

    private static boolean eligible(boolean enabled, boolean conflict, boolean confirmed,
                                    boolean serverThread, boolean exact, boolean dead) {
        return LifecycleCharacterDeathHandler.callbackEligible(enabled, conflict, confirmed,
                serverThread, exact, dead);
    }
}
