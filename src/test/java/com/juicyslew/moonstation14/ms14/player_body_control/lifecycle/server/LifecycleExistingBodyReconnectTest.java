package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LifecycleExistingBodyReconnectTest {
    @Test void forcedSurvivalWithUnparkedItemsCannotTriggerCreativeParking() {
        AtomicInteger parks = new AtomicInteger();
        assertEquals(LifecycleExistingBodyReconnect.Admission.DENIED,
                LifecycleExistingBodyReconnect.admitCarrier(GameType.SURVIVAL, false, () -> {
                    parks.incrementAndGet();
                    return CreativeCarrierTransition.Result.PARKED;
                }, () -> false));
        assertEquals(0, parks.get());
        assertFalse(LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED, null, GameType.SURVIVAL, false));
    }

    @Test void onlyUnmarkedActualCreativeAttemptsToParkAndParkedSpectatorRemainsReady() {
        AtomicInteger parks = new AtomicInteger();
        assertEquals(LifecycleExistingBodyReconnect.Admission.NEWLY_PARKED,
                LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false, () -> {
                    parks.incrementAndGet();
                    return CreativeCarrierTransition.Result.PARKED;
                }, () -> true));
        assertEquals(LifecycleExistingBodyReconnect.Admission.READY,
                LifecycleExistingBodyReconnect.admitCarrier(GameType.SPECTATOR, true, () -> {
                    parks.incrementAndGet();
                    return CreativeCarrierTransition.Result.PARKED;
                }, () -> true));
        assertEquals(1, parks.get());
        assertEquals(LifecycleExistingBodyReconnect.Admission.DENIED,
                LifecycleExistingBodyReconnect.admitCarrier(GameType.SPECTATOR, true, () -> {
                    parks.incrementAndGet();
                    return CreativeCarrierTransition.Result.PARKED;
                }, () -> false));
        assertEquals(1, parks.get());
    }

    @Test void alreadyLoadedBodyCompletesWithoutWaiting() {
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.COMPLETE,
                LifecycleExistingBodyReconnect.pendingDecision(0,
                        LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE, true));
    }

    @Test void delayedBodyIsReobservedForEveryTickThroughTickTwenty() {
        AtomicInteger observations = new AtomicInteger();
        Supplier<LoadedBodyResolver.Outcome> observation = () -> observations.incrementAndGet() == 20
                ? LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE : LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY;
        LifecycleExistingBodyReconnect.PendingDecision result = null;
        for (int tick = 1; tick <= LifecycleExistingBodyReconnect.MAX_PENDING_TICKS; tick++) {
            result = LifecycleExistingBodyReconnect.pendingDecision(tick, observation.get(), true);
            if (result != LifecycleExistingBodyReconnect.PendingDecision.RETRY) break;
        }
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.COMPLETE, result);
        assertEquals(20, observations.get());
    }

    @Test void stillUnloadedTimesOutAfterTwentyTicksWithoutAnotherChunkLoad() {
        AtomicInteger forcedChunkLoads = new AtomicInteger(1);
        AtomicInteger observations = new AtomicInteger();
        Supplier<LoadedBodyResolver.Outcome> observation = () -> {
            observations.incrementAndGet();
            return LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY;
        };
        LifecycleExistingBodyReconnect.PendingDecision result = null;
        for (int tick = 1; tick <= LifecycleExistingBodyReconnect.MAX_PENDING_TICKS; tick++) {
            result = LifecycleExistingBodyReconnect.pendingDecision(tick, observation.get(), true);
            if (result != LifecycleExistingBodyReconnect.PendingDecision.RETRY) break;
        }
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.TIMEOUT, result);
        assertEquals(20, observations.get());
        assertEquals(1, forcedChunkLoads.get());
    }

    @Test void inboxAbsenceAfterOneForcedLoadCanBecomeAvailableOrTimeOut() {
        AtomicInteger forcedChunkLoads = new AtomicInteger(1);
        LoadedBodyResolver.Outcome[] outcomes = {
                LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY,
                LoadedBodyResolver.Outcome.RECOVERY_REQUIRED,
                LoadedBodyResolver.Outcome.RECOVERY_REQUIRED,
                LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
        };
        LifecycleExistingBodyReconnect.PendingDecision result = null;
        for (int tick = 1; tick <= outcomes.length; tick++) {
            LoadedBodyResolver.Outcome outcome = outcomes[tick - 1];
            result = LifecycleExistingBodyReconnect.pendingDecision(tick, outcome, true,
                    outcome == LoadedBodyResolver.Outcome.RECOVERY_REQUIRED);
            if (result != LifecycleExistingBodyReconnect.PendingDecision.RETRY) break;
        }
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.COMPLETE, result);
        assertEquals(1, forcedChunkLoads.get());

        result = null;
        for (int tick = 1; tick <= LifecycleExistingBodyReconnect.MAX_PENDING_TICKS; tick++) {
            result = LifecycleExistingBodyReconnect.pendingDecision(tick,
                    LoadedBodyResolver.Outcome.RECOVERY_REQUIRED, true, true);
            if (result != LifecycleExistingBodyReconnect.PendingDecision.RETRY) break;
        }
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.TIMEOUT, result);
        assertEquals(1, forcedChunkLoads.get());
    }

    @Test void visibleMismatchDuringInboxWaitFailsClosedImmediately() {
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.FAIL_CLOSED,
                LifecycleExistingBodyReconnect.pendingDecision(1,
                        LoadedBodyResolver.Outcome.RECOVERY_REQUIRED, true, false));
    }

    @Test void staleOwnerLogoutAndPositiveConflictFailClosedImmediately() {
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.FAIL_CLOSED,
                LifecycleExistingBodyReconnect.pendingDecision(1,
                        LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY, false));
        assertEquals(LifecycleExistingBodyReconnect.PendingDecision.FAIL_CLOSED,
                LifecycleExistingBodyReconnect.pendingDecision(1,
                        LoadedBodyResolver.Outcome.RECOVERY_REQUIRED, true));
    }
}
