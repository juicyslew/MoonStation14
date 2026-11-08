package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test void realReturnWaitTickPathResumesOnceOnlyWhenExactBodyBecomesAvailable() {
        AtomicInteger observations = new AtomicInteger();
        AtomicInteger handoffs = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger inventoryWrites = new AtomicInteger();
        int[] carrierInventory = { 0x51a7 };
        int inventoryFingerprint = java.util.Arrays.hashCode(carrierInventory);
        var sequence = new LoadedBodyResolver.Outcome[] {
                LoadedBodyResolver.Outcome.RECOVERY_REQUIRED,
                LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
        };
        for (int tick = 1; tick <= sequence.length; tick++) {
            int current = tick;
            LifecycleExistingBodyReconnect.advanceReturnWait(current, 200, () -> true,
                    () -> new LifecycleExistingBodyReconnect.ReturnTickObservation(
                            sequence[observations.getAndIncrement()], false), handoffs::incrementAndGet,
                    cancellations::incrementAndGet, inventoryWrites::incrementAndGet);
        }
        assertEquals(2, observations.get());
        assertEquals(1, handoffs.get());
        assertEquals(0, cancellations.get());
        assertEquals(0, inventoryWrites.get());
        assertEquals(inventoryFingerprint, java.util.Arrays.hashCode(carrierInventory));
    }

    @Test void returnWaitTickCancelsInvalidOwnerAndTimesOutWithoutHandoff() {
        AtomicInteger handoffs = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger timeouts = new AtomicInteger();
        AtomicInteger loads = new AtomicInteger(1);
        LifecycleExistingBodyReconnect.advanceReturnWait(4, 200, () -> false,
                () -> { throw new AssertionError("invalid owner must not observe body"); }, handoffs::incrementAndGet,
                cancellations::incrementAndGet, timeouts::incrementAndGet);
        LifecycleExistingBodyReconnect.advanceReturnWait(201, 200, () -> true,
                () -> new LifecycleExistingBodyReconnect.ReturnTickObservation(
                        LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY, false), handoffs::incrementAndGet,
                cancellations::incrementAndGet, timeouts::incrementAndGet);
        assertEquals(0, handoffs.get());
        assertEquals(2, cancellations.get());
        assertEquals(1, timeouts.get());
        assertEquals(1, loads.get(), "wait ticks never request another chunk load");
    }

    @Test void visibleWrongUuidOrConflictingBodyCancelsInsteadOfWaiting() {
        AtomicInteger handoffs = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        LifecycleExistingBodyReconnect.advanceReturnWait(3, 200, () -> true,
                () -> new LifecycleExistingBodyReconnect.ReturnTickObservation(
                        LoadedBodyResolver.Outcome.RECOVERY_REQUIRED, true), handoffs::incrementAndGet,
                cancellations::incrementAndGet, failures::incrementAndGet);
        assertEquals(0, handoffs.get());
        assertEquals(1, cancellations.get());
        assertEquals(1, failures.get());
    }
}
