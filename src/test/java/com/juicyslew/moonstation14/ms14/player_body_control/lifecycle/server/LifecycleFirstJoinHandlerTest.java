package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleFirstJoinHandlerTest {
    @TempDir Path directory;
    @Test void disabledOrConflictingGatePreservesVanillaRouteBeforePlayerValidation() {
        assertEquals(LifecycleFirstJoinHandler.JoinRoute.VANILLA,
                LifecycleFirstJoinHandler.routeDecision(false, false, false, false));
        assertEquals(LifecycleFirstJoinHandler.JoinRoute.VANILLA,
                LifecycleFirstJoinHandler.routeDecision(true, true, false, false));
    }

    @Test void enabledGateRejectsUnlistedPlayerAndSeparatesFreshFromKnownAccount() {
        assertEquals(LifecycleFirstJoinHandler.JoinRoute.INVALID_PLAYER,
                LifecycleFirstJoinHandler.routeDecision(true, false, false, false));
        assertEquals(LifecycleFirstJoinHandler.JoinRoute.FIRST_ACCOUNT,
                LifecycleFirstJoinHandler.routeDecision(true, false, true, false));
        assertEquals(LifecycleFirstJoinHandler.JoinRoute.EXISTING_ACCOUNT,
                LifecycleFirstJoinHandler.routeDecision(true, false, true, true));
    }

    @Test void onlyDeferredOfflineStartupCanEnterExistingAccountReconnect() {
        assertEquals(true, LifecycleFirstJoinHandler.existingAccountMayReconnect(
                LifecycleStartupRuntime.State.DEFERRED));
        for (LifecycleStartupRuntime.State state : LifecycleStartupRuntime.State.values()) {
            if (state != LifecycleStartupRuntime.State.DEFERRED)
                assertEquals(false, LifecycleFirstJoinHandler.existingAccountMayReconnect(state), state.name());
        }
    }

    @Test void sameServerReservationAllowsOnlyOfflineOrDeadClaimWhileStartupRemainsUninitialized() throws Exception {
        var store = new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore(
                directory.resolve("profiles.json"));
        var context = new LifecycleServerContext(store, null);
        var account = java.util.UUID.randomUUID();
        assertTrue(context.reserveFirstProfile(account, "main", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "minecraft:overworld", new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.Location(0, 64, 0),
                java.util.Map.of("model", "wide")));

        assertTrue(LifecycleFirstJoinHandler.existingAccountMayReconnect(LifecycleStartupRuntime.State.UNINITIALIZED,
                context, account, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE));
        assertTrue(LifecycleFirstJoinHandler.existingAccountMayReconnect(LifecycleStartupRuntime.State.UNINITIALIZED,
                context, account, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM));
        assertFalse(LifecycleFirstJoinHandler.existingAccountMayReconnect(LifecycleStartupRuntime.State.UNINITIALIZED,
                null, account, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE));
        for (var state : LifecycleStartupRuntime.State.values()) {
            if (state != LifecycleStartupRuntime.State.DEFERRED && state != LifecycleStartupRuntime.State.UNINITIALIZED)
                assertFalse(LifecycleFirstJoinHandler.existingAccountMayReconnect(state, context, account,
                        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE), state.name());
        }
        for (var profileState : com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.values()) {
            if (profileState != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE
                    && profileState != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM)
                assertFalse(LifecycleFirstJoinHandler.existingAccountMayReconnect(LifecycleStartupRuntime.State.UNINITIALIZED,
                        context, account, profileState), profileState.name());
        }
    }

    @Test void deferredRestartRemainsAllowedButCannotOverrideFailClosedSavedStates() {
        assertTrue(LifecycleFirstJoinHandler.existingAccountMayReconnect(LifecycleStartupRuntime.State.DEFERRED,
                null, java.util.UUID.randomUUID(),
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE));
        for (var state : com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.values()) {
            if (state != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE
                    && state != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM)
                assertFalse(LifecycleFirstJoinHandler.existingAccountMayReconnect(LifecycleStartupRuntime.State.DEFERRED,
                        null, java.util.UUID.randomUUID(), state), state.name());
        }
    }

    @Test void savedAccountStateSeparatesLivingReconnectFromDeadClaimGhostAndRejectsAllOtherStates() {
        assertEquals(LifecycleFirstJoinHandler.AccountRoute.LIVING_RECONNECT,
                LifecycleFirstJoinHandler.accountRoute(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE));
        assertEquals(LifecycleFirstJoinHandler.AccountRoute.GHOST_LOGIN,
                LifecycleFirstJoinHandler.accountRoute(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM));
        for (var state : com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.values()) {
            if (state != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE
                    && state != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM)
                assertEquals(LifecycleFirstJoinHandler.AccountRoute.FAIL_CLOSED,
                        LifecycleFirstJoinHandler.accountRoute(state), state.name());
        }
    }

    @Test void reservationAlwaysPrecedesModeChangeAndFailedReservationLeavesModeUntouched() {
        List<String> trace = new ArrayList<>();
        String token = LifecycleFirstJoinHandler.reserveBeforeMode(() -> {
            trace.add("reserve");
            return "PREPARING";
        }, ignored -> trace.add("spectator"));

        assertEquals("PREPARING", token);
        assertEquals(List.of("reserve", "spectator"), trace);

        trace.clear();
        token = LifecycleFirstJoinHandler.reserveBeforeMode(() -> {
            trace.add("reserve-failed");
            return null;
        }, ignored -> trace.add("spectator"));
        assertEquals(null, token);
        assertEquals(List.of("reserve-failed"), trace);
    }
}
