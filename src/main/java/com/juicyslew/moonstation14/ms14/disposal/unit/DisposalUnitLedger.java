package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.disposal.item.DisposalContents;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;

/** One disposal ownership ledger per server level, shared with transit/unit stores. */
public final class DisposalUnitLedger {
    private static final Map<ServerLevel, Map<java.util.UUID, DisposalContents>> STORES = new WeakHashMap<>();
    private DisposalUnitLedger() { }
    public static synchronized ItemLocationLedger forLevel(ServerLevel level) {
        return LevelItemLocationLedgers.forLevel(level);
    }
    public static synchronized void close(ServerLevel level) { STORES.remove(level); LevelItemLocationLedgers.close(level); }
    public static synchronized DisposalContents existing(ServerLevel level, java.util.UUID owner) {
        return STORES.getOrDefault(level, Map.of()).get(owner);
    }
    public static synchronized DisposalContents retain(ServerLevel level, DisposalContents contents) {
        return STORES.computeIfAbsent(level, ignored -> new java.util.HashMap<>()).computeIfAbsent(contents.ownerId(), ignored -> contents);
    }
}
