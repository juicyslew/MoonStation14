package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleReadyTimeoutCleanupTest {
    @TempDir Path directory;

    @Test void timeoutParksDurablyThenReloadReconnectsSameCharacterAcrossRepeatedTimeouts() throws Exception {
        UUID account = UUID.randomUUID();
        UUID mind = UUID.randomUUID();
        UUID bodyUuid = UUID.randomUUID();
        var body = new MobHarness(new MobHarnessId(bodyUuid), MobHarnessKind.CHARACTER);
        var store = new LifecycleProfileStore(directory.resolve("profiles.json"));
        var first = new LifecycleServerContext(store, null);
        var location = new SavedLifecycleProfile.Location(12, 64, -8);
        var appearance = Map.of("appearance", "DEFAULT", "bodyShape", "WIDE");
        assertTrue(first.reserveFirstProfile(account, "main", mind, bodyUuid,
                "minecraft:overworld", location, appearance));
        assertTrue(first.ownership().registerHarness(body));
        assertTrue(first.stageFirstProfile(account, mind, bodyUuid, body.id()));
        var active = first.promoteFirstCharacter(account, mind, bodyUuid).orElseThrow();
        assertEquals(SavedLifecycleProfile.State.ACTIVE,
                store.read().orElseThrow().profiles().get(0).state());

        AtomicBoolean retired = new AtomicBoolean();
        AtomicBoolean disconnectSaidRetry = new AtomicBoolean();
        assertTrue(LifecycleCharacterSessionControl.timeoutCleanupDecision(
                first.lifecycle().authorizes(account, active.connectionGeneration(), body.id()),
                () -> {
                    boolean saved = clean(first, account, active.connectionGeneration(), mind, bodyUuid,
                            location, appearance, 100L);
                    if (saved) retired.set(true);
                    return saved;
                }, () -> fail("valid current timeout must use clean logout"),
                parked -> disconnectSaidRetry.set(parked)));
        assertTrue(retired.get(), "session retires only after the durable park succeeds");
        assertTrue(disconnectSaidRetry.get());
        var parked = store.read().orElseThrow().profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.OFFLINE, parked.state());
        assertEquals(mind, parked.mindId());
        assertEquals(bodyUuid, parked.bodyId());
        assertEquals(location, parked.location());
        assertEquals(appearance, parked.appearance());
        assertSame(body, first.ownership().registeredHarness(body.id()).orElseThrow(),
                "timeout cleanup leaves the existing live character harness registered");
        assertEquals(LifecycleFirstJoinHandler.AccountRoute.LIVING_RECONNECT,
                LifecycleFirstJoinHandler.accountRoute(parked.state()));

        // Simulate cold startup: only the durable OFFLINE row remains; reconnect the same canonical body id.
        var reloadedStore = new LifecycleProfileStore(directory.resolve("profiles.json"));
        var restarted = new LifecycleServerContext(reloadedStore, null);
        var reloaded = reloadedStore.read().orElseThrow().profiles().get(0);
        var restoredBody = new MobHarness(new MobHarnessId(bodyUuid), MobHarnessKind.CHARACTER);
        var reconnected = restarted.reconnectOffline(reloaded, restoredBody).orElseThrow();
        assertEquals(mind, reconnected.mindId().value());
        assertEquals(bodyUuid, reconnected.bodyId().value());
        assertTrue(reconnected.connectionGeneration() > parked.connectionGeneration());
        assertEquals(SavedLifecycleProfile.State.ACTIVE,
                reloadedStore.read().orElseThrow().profiles().get(0).state());

        // A subsequent technical timeout parks again without changing the persistent character identity.
        assertTrue(clean(restarted, account, reconnected.connectionGeneration(), mind, bodyUuid,
                location, appearance, 200L));
        var secondOffline = reloadedStore.read().orElseThrow().profiles().get(0);
        assertEquals(SavedLifecycleProfile.State.OFFLINE, secondOffline.state());
        assertEquals(mind, secondOffline.mindId());
        assertEquals(bodyUuid, secondOffline.bodyId());
    }

    @Test void staleEpochCannotParkOrRetireNewCurrentSession() throws Exception {
        UUID account = UUID.randomUUID();
        UUID mind = UUID.randomUUID();
        UUID bodyUuid = UUID.randomUUID();
        var store = new LifecycleProfileStore(directory.resolve("stale.json"));
        var context = new LifecycleServerContext(store, null);
        var location = new SavedLifecycleProfile.Location(0, 64, 0);
        var appearance = Map.of("appearance", "DEFAULT");
        assertTrue(context.reserveFirstProfile(account, "main", mind, bodyUuid,
                "minecraft:overworld", location, appearance));
        var body = new MobHarness(new MobHarnessId(bodyUuid), MobHarnessKind.CHARACTER);
        assertTrue(context.ownership().registerHarness(body));
        assertTrue(context.stageFirstProfile(account, mind, bodyUuid, body.id()));
        var active = context.promoteFirstCharacter(account, mind, bodyUuid).orElseThrow();
        long currentEpoch = active.connectionGeneration();
        AtomicInteger cleanupCalls = new AtomicInteger();
        AtomicInteger recoveryCalls = new AtomicInteger();
        boolean exactOldTimeoutOwner = context.lifecycle().authorizes(account, currentEpoch - 1, body.id());
        assertFalse(LifecycleCharacterSessionControl.timeoutCleanupDecision(exactOldTimeoutOwner,
                () -> { cleanupCalls.incrementAndGet(); return clean(context, account, currentEpoch - 1,
                        mind, bodyUuid, location, appearance, 300L); },
                recoveryCalls::incrementAndGet, ignored -> fail("stale owner cannot disconnect current session")));
        assertEquals(0, cleanupCalls.get());
        assertEquals(1, recoveryCalls.get());
        assertEquals(SavedLifecycleProfile.State.ACTIVE, store.read().orElseThrow().profiles().get(0).state());
        assertTrue(context.lifecycle().authorizes(account, currentEpoch, body.id()));
    }

    private static boolean clean(LifecycleServerContext context, UUID account, long epoch, UUID mind,
                                 UUID body, SavedLifecycleProfile.Location location,
                                 Map<String, String> appearance, long timestamp) {
        try {
            return context.disconnectCleanly(account, epoch, mind, body,
                    "minecraft:overworld", location, appearance, timestamp);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
