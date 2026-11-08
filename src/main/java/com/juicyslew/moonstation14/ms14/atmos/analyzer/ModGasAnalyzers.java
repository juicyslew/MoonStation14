package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModGasAnalyzers {
    public static final ResourceLocation BASE_ID = id("gas_analyzer_base");
    public static final ResourceLocation HANDHELD_ID = id("atmosphere_analyzer");
    public static final PrototypeType<GasAnalyzerProfile> TYPE = new PrototypeType<>(id("gas_analyzer"),
            "moonstation14/gas_analyzer", GasAnalyzerProfile.CODEC,
            catalog -> catalog.values().forEach(profile -> {
                if (profile == null) throw new IllegalArgumentException("missing gas analyzer profile");
            }));

    private ModGasAnalyzers() { }
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name); }
    public static PrototypeCatalog<GasAnalyzerProfile> catalog(Level level) {
        return (level.isClientSide() ? com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager()
                : com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
    public static GasAnalyzerProfile resolve(Level level) {
        return resolve(catalog(level));
    }
    /** Same resolved-catalog lookup used by the live handheld item consumer. */
    public static GasAnalyzerProfile resolve(PrototypeCatalog<GasAnalyzerProfile> catalog) {
        GasAnalyzerProfile profile = catalog.get(HANDHELD_ID);
        if (profile == null) throw new IllegalStateException("Missing resolved atmosphere analyzer profile " + HANDHELD_ID);
        return profile;
    }
}
