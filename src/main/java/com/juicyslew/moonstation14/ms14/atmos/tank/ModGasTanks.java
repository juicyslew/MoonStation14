package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.LinkedHashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

public final class ModGasTanks {
    public static final ResourceLocation BASE_ID = id("gas_tank_base");
    public static final ResourceLocation PLASMA_ID = id("plasma_tank");
    public static final ResourceLocation PLASMA_FILLED_ID = id("plasma_tank_filled");
    public static final PrototypeType<GasTankData> TYPE = new PrototypeType<>(id("gas_tank"), "moonstation14/gas_tank", GasTankData.CODEC, new GasTankMergeStrategy());
    private ModGasTanks() { }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<GasTankData> catalog(Level level) {
        return (level.isClientSide() ? com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager()
                : com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()).snapshot(TYPE);
    }

    /** Seed client gas-tank prototypes from bundled definitions until authoritative reload sync arrives. */
    public static void bootstrapClientBundled() {
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : java.util.List.of("gas_tank_base", "plasma_tank", "plasma_tank_filled")) {
            String path = "/data/moonstation14/moonstation14/gas_tank/" + name + ".json";
            try (var stream = ModGasTanks.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing bundled gas tank prototype " + path);
                raw.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not read bundled gas tank prototype " + path, exception);
            }
        }
        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager().reload(TYPE, raw);
    }
}

