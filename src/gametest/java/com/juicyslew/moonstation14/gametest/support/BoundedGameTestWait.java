package com.juicyslew.moonstation14.gametest.support;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;

/** Bounded readiness polling through the native GameTest sequence iterator. */
public final class BoundedGameTestWait {
    private BoundedGameTestWait() { }

    public static void await(GameTestHelper helper, int maxWaitTicks, Supplier<String> timeoutMessage,
                             BooleanSupplier ready, Runnable onReady) {
        if (maxWaitTicks < 1) throw new IllegalArgumentException("maxWaitTicks must be positive");
        long start = helper.getTick();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    boolean readyNow;
                    try {
                        readyNow = ready.getAsBoolean();
                    } catch (RuntimeException failure) {
                        helper.testInfo.fail(failure);
                        throw new GameTestAssertException("unexpected exception evaluating bounded wait predicate: "
                                + failure);
                    }
                    // A condition becoming true on the deadline tick is still ready within the
                    // advertised bound. Check it before declaring timeout.
                    if (readyNow) return;
                    if (helper.getTick() - start >= maxWaitTicks) {
                        GameTestAssertException timeout = new GameTestAssertException(timeoutMessage.get());
                        helper.testInfo.fail(timeout);
                        throw timeout;
                    }
                    throw new GameTestAssertException("condition not ready");
                })
                .thenExecute(() -> helper.runAfterDelay(1, onReady));
    }

    /** Schedules callback work after native sequence iteration, avoiding task-map iterator mutation. */
    public static void safeDelay(GameTestHelper helper, long ticks, Runnable callback) {
        if (ticks < 1) throw new IllegalArgumentException("ticks must be positive");
        int idleTicks = Math.toIntExact(ticks - 1);
        helper.startSequence().thenIdle(idleTicks).thenExecute(() -> helper.runAfterDelay(1, callback));
    }

    /** Finalize only from a native GameTest tick so listeners observe completion. */
    public static void succeedAfterObservation(GameTestHelper helper) {
        safeDelay(helper, 1, helper::succeed);
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

    /** Observe bounded samples after all normal server-tick handlers (including fallback physics). */
    public static void observeServerTicks(GameTestHelper helper, int count,
            Consumer<ServerTickEvent.Post> perTick, Runnable completion) {
        observeServerTicksUntil(helper,count,EventPriority.LOWEST,perTick,()->false,()->{},completion);
    }

    /** Bounded prioritized post-tick observation with an early one-shot completion condition. */
    public static void observeServerTicksUntil(GameTestHelper helper,int maxTicks,EventPriority priority,
            Consumer<ServerTickEvent.Post> perTick,java.util.function.BooleanSupplier stopCondition,
            Runnable stopped,Runnable timeout) {
        if (maxTicks < 1) throw new IllegalArgumentException("maxTicks must be positive");
        var server = helper.getLevel().getServer();
        final long registrationTick = server.getTickCount();
        final int[] observed = {0};
        final long[] lastTick = {-1};
        final boolean[] removed = {false};
        Consumer<ServerTickEvent.Post> listener = new Consumer<>() {
            private void remove() { if (!removed[0]) { removed[0] = true; NeoForge.EVENT_BUS.unregister(this); } }
            @Override public void accept(ServerTickEvent.Post event) {
                if (event.getServer() != server || removed[0]) return;
                long tick = server.getTickCount();
                // A listener installed during this tick's GameTest callback must observe only
                // completed future physics ticks, not a later phase of its registration tick.
                if (tick <= registrationTick) return;
                if (tick == lastTick[0]) return;
                lastTick[0] = tick;
                try {
                    perTick.accept(event);
                    if (stopCondition.getAsBoolean()) { remove(); stopped.run(); return; }
                    if (++observed[0] == maxTicks) { remove(); timeout.run(); }
                } catch (RuntimeException | AssertionError failure) {
                    remove();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                            "GameTest server-tick observer failed test={} serverTick={} message={}",
                            helper.testInfo, tick, failure.getMessage(), failure);
                    safeDelay(helper, 1, () -> helper.testInfo.fail(failure));
                }
            }
        };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup() { if (!removed[0]) { removed[0] = true; NeoForge.EVENT_BUS.unregister(listener); } }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
        });
        NeoForge.EVENT_BUS.addListener(priority, listener);
    }
}
