package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Typed data owner for SS14 weightedRandomFillSolution prototypes. */
public final class ModWeightedRandomFillSolutions {
    public static final PrototypeType<WeightedRandomFillSolution> TYPE = new PrototypeType<>(id("weighted_random_fill_solution"),
            "moonstation14/weighted_random_fill_solution", WeightedRandomFillSolution.CODEC,
            catalog -> {
                for (var entry : catalog.asMap().entrySet()) {
                    var table = entry.getValue();
                    if (table.fills().stream().noneMatch(g -> g.weight() > 0 && !g.reagents().isEmpty()))
                        throw new IllegalArgumentException("weighted fill '" + entry.getKey() + "' has no selectable group");
                }
            });
    private ModWeightedRandomFillSolutions() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static ResourceLocation solutionId(String path) { return id(path); }
    /** Upstream prototype IDs are commonly unqualified lowercase source identifiers. */
    public static ResourceLocation canonicalId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("blank weighted-fill ID");
        return sourceId.indexOf(':') < 0 ? id(sourceId.toLowerCase(java.util.Locale.ROOT))
                : ResourceLocation.parse(sourceId);
    }
    public static PrototypeCatalog<WeightedRandomFillSolution> serverCatalog() { return PrototypeRuntime.serverWeightedRandomFillSolutions(); }
    public static PrototypeCatalog<WeightedRandomFillSolution> clientCatalog() { return PrototypeRuntime.clientWeightedRandomFillSolutions(); }
}
