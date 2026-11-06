package com.juicyslew.moonstation14.gametest.support;

import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Pure bounded thread-stack sampler shared by test-only shutdown diagnostics. */
public final class GameTestShutdownStackCapture {
    private GameTestShutdownStackCapture() { }

    public static void capture(Thread serverThread, CountDownLatch stopped, Logger logger) {
        try {
            if (stopped.await(15, TimeUnit.SECONDS)) {
                logger.info("[GameTest shutdown stack] CANCEL normal server stop observed");
                return;
            }
            captureStackSample(1, serverThread, logger);
            if (stopped.await(15, TimeUnit.SECONDS)) {
                logger.info("[GameTest shutdown stack] CANCEL server stop observed after sample=1");
                return;
            }
            captureStackSample(2, serverThread, logger);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("[GameTest shutdown stack] CANCEL diagnostic interrupted");
        } catch (Throwable e) {
            logger.warn("[GameTest shutdown stack] UNKNOWN diagnostic failed: {}", bounded(e.toString(), 512));
        }
    }

    private static void captureStackSample(int sample, Thread serverThread, Logger logger) {
        ThreadInfo[] all = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        Arrays.sort(all, (a, b) -> {
            if (a.getThreadId() == serverThread.getId()) return b.getThreadId() == serverThread.getId() ? 0 : -1;
            if (b.getThreadId() == serverThread.getId()) return 1;
            return Long.compare(a.getThreadId(), b.getThreadId());
        });
        logger.warn("[GameTest shutdown stack] SAMPLE_BEGIN sample={} serverThreadId={} threadCount={} truncatedThreads={}",
                sample, serverThread.getId(), Math.min(all.length, 128), Math.max(0, all.length - 128));
        for (int i = 0; i < Math.min(all.length, 128); i++) {
            ThreadInfo info = all[i];
            logger.warn("[GameTest shutdown stack] THREAD sample={} id={} name={} state={} lock={} lockOwner={}",
                    sample, info.getThreadId(), bounded(info.getThreadName(), 160), info.getThreadState(),
                    bounded(info.getLockName(), 256), bounded(info.getLockOwnerName(), 160));
            StackTraceElement[] frames = info.getStackTrace();
            logger.warn("[GameTest shutdown stack] THREAD_FRAME_COUNT sample={} threadId={} frames={} truncatedFrames={}",
                    sample, info.getThreadId(), Math.min(frames.length, 64), Math.max(0, frames.length - 64));
            for (int f = 0; f < Math.min(frames.length, 64); f++)
                logger.warn("[GameTest shutdown stack] FRAME sample={} threadId={} index={} frame={}",
                        sample, info.getThreadId(), f, bounded(frames[f].toString(), 512));
        }
        logger.warn("[GameTest shutdown stack] SAMPLE_END sample={}", sample);
    }

    static String bounded(String value, int limit) {
        if (value == null) return "";
        String safe = value.replace('\n', ' ').replace('\r', ' ');
        return safe.length() <= limit ? safe : safe.substring(0, limit - 1) + "…";
    }
}
