package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.server.MinecraftServer;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.management.ThreadInfo;

/** Test-only, bounded thread snapshots after arming from the native fueled fixture. */
public final class ShutdownDiagnostics {
    private static final ScheduledExecutorService WATCHER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "moonstation14-gametest-shutdown-diagnostics");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile MinecraftServer target;
    private static volatile Thread serverThread;

    private ShutdownDiagnostics() { }

    /** Arms exactly once for the fixture's server/thread; the watcher never reads server-owned state. */
    public static synchronized void arm(MinecraftServer server) {
        if (target != null) return;
        target = server;
        serverThread = Thread.currentThread();
        MoonStation14.LOGGER.info("GAMETEST_SHUTDOWN_DIAGNOSTICS armed serverThread={}; bounded post-arm snapshots at 30/60/120s (shutdown not assumed)", serverThread.getName());
        for (int seconds : new int[]{30, 60, 120}) WATCHER.schedule(() -> {
            Thread captured = serverThread;
            if (captured == null || !captured.isAlive()) {
                WATCHER.shutdownNow();
                return;
            }
            dump(seconds, captured);
            if (seconds == 120) WATCHER.shutdownNow();
        }, seconds, TimeUnit.SECONDS);
    }

    private static void dump(int seconds, Thread server) {
        var bean = ManagementFactory.getThreadMXBean();
        long serverId = server.getId();
        var serverInfo = bean.getThreadInfo(serverId, 64);
        long[] ids = bean.getAllThreadIds();
        long[] otherIds = java.util.Arrays.stream(ids).filter(id -> id != serverId).limit(199).toArray();
        var infos = bean.getThreadInfo(otherIds, 64);
        MoonStation14.LOGGER.warn("GAMETEST_SHUTDOWN_DIAGNOSTICS post-arm snapshot={}s; shutdown not assumed; serverThread={} state={} threadCount={} captured={} (server included, max 199 others, 64 frames)",
                seconds, server.getName(), server.getState(), ids.length, infos.length + (serverInfo == null ? 0 : 1));
        if (serverInfo != null) MoonStation14.LOGGER.warn("GAMETEST_SERVER_THREAD {}", formatThreadInfo(serverInfo));
        for (var info : infos) {
            if (info != null) MoonStation14.LOGGER.warn("GAMETEST_THREAD {}", formatThreadInfo(info));
        }
    }

    static String formatThreadInfo(ThreadInfo info) {
        var result = new StringBuilder(info.getThreadName())
                .append(" id=").append(info.getThreadId())
                .append(" state=").append(info.getThreadState());
        if (info.getLockName() != null) result.append(" lock=").append(info.getLockName());
        if (info.getLockOwnerName() != null) result.append(" lockOwner=").append(info.getLockOwnerName())
                .append("#").append(info.getLockOwnerId());
        StackTraceElement[] frames = info.getStackTrace();
        result.append(" frames[").append(frames.length).append("]");
        for (int i = 0; i < frames.length; i++) result.append("\n  at ").append(i).append(' ').append(frames[i]);
        return result.toString();
    }
}
