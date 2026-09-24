package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Runtime boundary for data-bearing reagent holders; intentionally separate from persistence codecs. */
public final class ReagentCatalogValidation {
    private static final int WARNING_CACHE_LIMIT = 2048;
    private static final Set<String> WARNED = new LinkedHashSet<>();
    private static volatile Consumer<String> warningSink = message -> MoonStation14.LOGGER.error("{}", message);

    private ReagentCatalogValidation() { }

    /**
     * Returns false if a positive stored reagent is absent from a resolved catalog. An unavailable
     * catalog is not treated as invalid (notably during client prototype synchronization).
     */
    public static boolean hasOnlyKnownPositiveReagents(Map<ResourceKey<ReagentData>, Float> contents,
                                                        PrototypeCatalog<ReagentData> catalog,
                                                        String context) {
        if (catalog == null) return true;
        boolean valid = true;
        for (var entry : contents.entrySet()) {
            Float amount = entry.getValue();
            if (amount == null || !(amount > 0f) || !Float.isFinite(amount)) continue;
            if (catalog.get(entry.getKey().location()) != null) continue;
            valid = false;
            warnOnce(entry.getKey().location().toString(), context);
        }
        return valid;
    }

    public static boolean hasOnlyKnownPositiveReagents(Map<ResourceKey<ReagentData>, Float> contents,
                                                        Level level, String holder) {
        if (level == null || level.isClientSide()) return true;
        return hasOnlyKnownPositiveReagents(contents, ModReagents.catalog(level),
                "server " + holder);
    }

    private static void warnOnce(String id, String context) {
        String key = context + "|" + id;
        synchronized (WARNED) {
            if (WARNED.contains(key)) return;
            if (WARNED.size() >= WARNING_CACHE_LIMIT) WARNED.remove(WARNED.iterator().next());
            WARNED.add(key);
        }
        warningSink.accept("Rejected invalid reagent '" + id + "' in " + context + "; holder was left unchanged");
    }

    static void setWarningSinkForTests(Consumer<String> sink) { warningSink = sink; }
    static void resetWarningsForTests() {
        synchronized (WARNED) { WARNED.clear(); }
        warningSink = message -> MoonStation14.LOGGER.error("{}", message);
    }
}
