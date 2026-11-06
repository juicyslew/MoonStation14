package com.juicyslew.moonstation14.ms14.shuttle.sable;

import java.util.IdentityHashMap;
import java.util.List;

/** Narrow witness checks for native contacts captured during one travel call. */
public final class NativeHullContactEligibility {
    private NativeHullContactEligibility() { }

    public static boolean worldPoint(Object point, Object frameId) {
        return point != null && frameId == null;
    }

    public static boolean fresh(Object info, Object previous, Object consumed, long startTick, long currentTick,
                                List<NativeSelectedContactAccess.Entry> entries, boolean overflowed) {
        if (info == null || info == previous || info == consumed || startTick != currentTick
                || overflowed || entries == null || entries.isEmpty()) return false;
        IdentityHashMap<Object, Boolean> identities = new IdentityHashMap<>();
        for (var entry : entries) {
            if (entry == null || entry.serverTick() != startTick || identities.put(entry, Boolean.TRUE) != null)
                return false;
        }
        return true;
    }
}
