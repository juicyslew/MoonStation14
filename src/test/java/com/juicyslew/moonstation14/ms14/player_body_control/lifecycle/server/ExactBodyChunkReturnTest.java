package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExactBodyChunkReturnTest {
    @Test void unloadedChunkRequiresOneExactLoadBeforePending() {
        assertEquals(LifecycleExistingBodyReconnect.ReturnResult.BLOCKED,
                LifecycleExistingBodyReconnect.returnObservation(LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY,
                        false, false));
        assertEquals(LifecycleExistingBodyReconnect.ReturnResult.CHUNK_LOADING,
                LifecycleExistingBodyReconnect.returnObservation(LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY,
                        false, true));
    }

    @Test void loadedExactBodyCanReturn() {
        assertEquals(LifecycleExistingBodyReconnect.ReturnResult.RETURNED,
                LifecycleExistingBodyReconnect.returnObservation(LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE,
                        true, false));
    }

    @Test void wrongDimensionOrVisibleMismatchNeverCountsAsInboxDelay() {
        assertEquals(LifecycleExistingBodyReconnect.ReturnResult.BLOCKED,
                LifecycleExistingBodyReconnect.returnObservation(LoadedBodyResolver.Outcome.RECOVERY_REQUIRED,
                        true, true));
    }

    @Test void missingUuidImmediatelyAfterFullLoadIsPendingNotProvenAbsent() {
        assertEquals(LifecycleExistingBodyReconnect.ReturnResult.CHUNK_LOADING,
                LifecycleExistingBodyReconnect.returnObservation(LoadedBodyResolver.Outcome.RECOVERY_REQUIRED,
                        false, true));
        assertEquals(LifecycleExistingBodyReconnect.ReturnResult.BLOCKED,
                LifecycleExistingBodyReconnect.returnObservation(LoadedBodyResolver.Outcome.RECOVERY_REQUIRED,
                        false, false));
    }
}
