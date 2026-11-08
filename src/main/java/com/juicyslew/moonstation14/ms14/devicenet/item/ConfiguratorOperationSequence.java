package com.juicyslew.moonstation14.ms14.devicenet.item;

/** Shared session operation ordering: publications and client commands use one monotonic sequence. */
public final class ConfiguratorOperationSequence {
    private ConfiguratorOperationSequence() { }

    /** Advance a server-side selection/publication from the previous high-water mark (-1 initially). */
    public static long nextPublication(long previous) { return previous + 1; }

    /** Make a command newer than both the client counter and latest accepted server publication. */
    public static long nextCommand(long clientCurrent, long published) {
        return Math.max(clientCurrent, published) + 1;
    }
}

