package com.juicyslew.moonstation14.gametest.support;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Bounded readiness polling through the native GameTest sequence iterator. */
public final class BoundedGameTestWait {
    private BoundedGameTestWait() { }

    public static void await(GameTestHelper helper, int maxWaitTicks, Supplier<String> timeoutMessage,
                             BooleanSupplier ready, Runnable onReady) {
        if (maxWaitTicks < 1) throw new IllegalArgumentException("maxWaitTicks must be positive");
        long start = helper.getTick();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (helper.getTick() - start >= maxWaitTicks) {
                        GameTestAssertException timeout = new GameTestAssertException(timeoutMessage.get());
                        helper.testInfo.fail(timeout);
                        throw timeout;
                    }
                    boolean readyNow;
                    try {
                        readyNow = ready.getAsBoolean();
                    } catch (RuntimeException failure) {
                        helper.testInfo.fail(failure);
                        throw new GameTestAssertException("unexpected exception evaluating bounded wait predicate: "
                                + failure);
                    }
                    if (!readyNow) throw new GameTestAssertException("condition not ready");
                })
                .thenExecute(() -> helper.runAfterDelay(1, onReady));
    }

    /** Schedules callback work after native sequence iteration, avoiding task-map iterator mutation. */
    public static void safeDelay(GameTestHelper helper, long ticks, Runnable callback) {
        if (ticks < 1) throw new IllegalArgumentException("ticks must be positive");
        int idleTicks = Math.toIntExact(ticks - 1);
        helper.startSequence().thenIdle(idleTicks).thenExecute(() -> helper.runAfterDelay(1, callback));
    }

    /** Safely reschedules at a test-relative target tick while retaining its original deadline. */
    public static void safeAtTickTime(GameTestHelper helper, long targetTick, Runnable callback) {
        long now = helper.getTick();
        long remaining = targetTick <= now ? 1L : Math.subtractExact(targetTick, now);
        safeDelay(helper, remaining, callback);
    }

    /** Registers a native tick callback from sequence phase, not a scheduled-task callback. */
    public static void safeOnEachTick(GameTestHelper helper, Runnable callback) {
        helper.startSequence().thenExecute(() -> helper.onEachTick(callback));
    }
}
