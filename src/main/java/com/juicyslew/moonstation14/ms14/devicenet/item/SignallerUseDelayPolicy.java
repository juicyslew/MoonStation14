package com.juicyslew.moonstation14.ms14.devicenet.item;

/** Tick arithmetic shared by authenticated held and loose activation adapters. */
final class SignallerUseDelayPolicy {
    private SignallerUseDelayPolicy() { }

    static boolean coolingDown(long now, Long nextUse) {
        return nextUse != null && now < nextUse;
    }

    static long nextUse(long now, float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid use delay");
        return now + (long) Math.ceil(seconds * 20);
    }
}
