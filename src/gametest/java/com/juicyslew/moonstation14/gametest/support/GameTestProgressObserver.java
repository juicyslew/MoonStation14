package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Bounded, test-only visibility into native GameTest progress; never affects test outcomes. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class GameTestProgressObserver {
    private static final int CAP = 256;
    private static final GameTestProgressState<GameTestInfo, Entry> STATE = new GameTestProgressState<>(CAP);
    private static GameTestServer owner;
    private static GameTestServer captureOwner;
    private static CountDownLatch captureStopped;
    private static boolean captureStarted;

    private GameTestProgressObserver() { }

    /** Arms bounded shutdown-only stack diagnostics for this exact observed GameTest server. */
    public static synchronized void armShutdownStackCapture(GameTestHelper helper) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer server) || owner != server
                || (captureOwner != null && captureOwner != server) || captureOwner == server) return;
        captureOwner = server;
        captureStopped = new CountDownLatch(1);
        captureStarted = false;
        MoonStation14.LOGGER.info("[GameTest shutdown stack] ARM server={} threadId={}", bounded(server.toString(), 256), Thread.currentThread().getId());
    }

    @SubscribeEvent
    public static synchronized void serverStopping(ServerStoppingEvent event) {
        if (captureOwner == null || event.getServer() != captureOwner || captureStarted) return;
        captureStarted = true;
        Thread serverThread = Thread.currentThread();
        CountDownLatch stopped = captureStopped;
        MoonStation14.LOGGER.info("[GameTest shutdown stack] STOPPING serverThreadId={} isServerThread={}", serverThread.getId(), event.getServer().isSameThread());
        Thread diagnostic = new Thread(() -> GameTestShutdownStackCapture.capture(serverThread, stopped, MoonStation14.LOGGER), "GameTest-shutdown-stack-diagnostic");
        diagnostic.setDaemon(true);
        diagnostic.start();
    }

    /** Called only by the test-scope GameTestTicker.add tail injection. */
    public static synchronized void admitted(GameTestInfo info) {
        if (!(info.getLevel().getServer() instanceof GameTestServer server)) return;
        if (owner != null && owner != server) return;
        GameTestHelper helper = new GameTestHelper(info);
        Entry entry = new Entry(info, helper, server);
        long now = System.nanoTime();
        GameTestProgressState.Admission result = STATE.admit(info, entry, server.getTickCount(), now);
        if (result == GameTestProgressState.Admission.DUPLICATE) return;
        owner = server;
        if (result == GameTestProgressState.Admission.OVERFLOW) {
            if (STATE.markOverflowReported()) MoonStation14.LOGGER.warn("GameTest progress tracking overflow: cap={} coverage is incomplete", CAP);
            return;
        }
        log(entry, "START", "", server.getTickCount(), now);
        info.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) { }
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { terminal(entry, GameTestProgressState.Outcome.PASS, "PASS", ""); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) {
                terminal(entry, GameTestProgressState.Outcome.FAIL, "FAIL", bounded(test.getError() == null ? "" : test.getError().getMessage(), 160));
            }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                terminal(entry, GameTestProgressState.Outcome.OTHER, "RERUN", "");
            }
        });
    }

    private static synchronized void terminal(Entry entry, GameTestProgressState.Outcome outcome, String label, String error) {
        Entry removed = STATE.complete(entry.info, outcome);
        if (removed == null) return;
        long tick = entry.server.getTickCount(), now = System.nanoTime();
        log(entry, label, error, tick, now);
        if (STATE.activeSize() == 0) {
            GameTestProgressState.Counts c = STATE.counts();
            MoonStation14.LOGGER.info("GameTest progress IDLE batch={} started={} completedAttempts={} failedAttempts={} overflow={}",
                    bounded(entry.info.getTestFunction().batchName(), 256), c.started(), c.completed(), c.failed(), c.overflow());
        }
    }

    @SubscribeEvent
    public static synchronized void postTick(ServerTickEvent.Post event) {
        if (!(event.getServer() instanceof GameTestServer server) || server != owner) return;
        for (Map.Entry<GameTestInfo, Entry> tracked : STATE.snapshot()) {
            GameTestInfo info = tracked.getKey();
            if (!info.isDone()) continue;
            GameTestProgressState.Outcome outcome = info.hasFailed() ? GameTestProgressState.Outcome.FAIL
                    : info.hasSucceeded() ? GameTestProgressState.Outcome.PASS : GameTestProgressState.Outcome.OTHER;
            terminal(tracked.getValue(), outcome, "DONE_FALLBACK_" + outcome, "");
        }
        long tick = server.getTickCount(), now = System.nanoTime();
        if (!STATE.heartbeatDue(tick, now)) return;
        for (Map.Entry<GameTestInfo, Entry> tracked : STATE.snapshot())
            log(tracked.getValue(), "INCOMPLETE", "", tick, now);
    }

    private static void log(Entry e, String state, String error, long tick, long now) {
        GameTestProgressState.Counts c = STATE.counts();
        MoonStation14.LOGGER.info("GameTest progress {} name={} batch={} serverTick={} gameTime={} testTick={}/{} started={} counts={}/{}/{}/{} overflow={} monotonicNanos={} error={}",
                state, bounded(e.info.getTestName(), 256), bounded(e.info.getTestFunction().batchName(), 256), tick,
                e.info.getLevel().getGameTime(), e.helper.getTick(), e.info.getTimeoutTicks(), e.info.hasStarted(),
                c.started(), c.completed(), c.failed(), STATE.activeSize(), c.overflow(), now, bounded(error, 160));
    }

    @SubscribeEvent
    public static synchronized void serverStopped(ServerStoppedEvent event) {
        if (captureOwner != null && event.getServer() == captureOwner) captureStopped.countDown();
        if (owner == null || event.getServer() != owner) return;
        long tick = owner.getTickCount(), now = System.nanoTime();
        for (Map.Entry<GameTestInfo, Entry> tracked : STATE.snapshot())
            log(tracked.getValue(), "SHUTDOWN_UNFINISHED", "", tick, now);
        STATE.clear();
        owner = null;
        if (captureOwner == event.getServer()) { captureOwner = null; captureStopped = null; captureStarted = false; }
    }

    public static synchronized boolean isObserved(GameTestInfo info) { return STATE.contains(info); }
    static String bounded(String value, int limit) {
        return GameTestShutdownStackCapture.bounded(value, limit);
    }
    private record Entry(GameTestInfo info, GameTestHelper helper, GameTestServer server) { }
}
