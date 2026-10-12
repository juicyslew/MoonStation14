package com.juicyslew.moonstation14.ms14.hands.transfer;

import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared per-level item ownership ledger, deliberately limited to systems that opt into it. */
public final class LevelItemLocationLedgers {
    private static final Map<ServerLevel, ItemLocationLedger> LEVELS = new WeakHashMap<>();
    private LevelItemLocationLedgers() { }
    public static synchronized ItemLocationLedger forLevel(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new ItemLocationLedger());
    }
    public static synchronized void close(ServerLevel level) { LEVELS.remove(level); }
}
