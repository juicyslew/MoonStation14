package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Client-rendered appearance data; its local base is an extraction for shared defaults, not an upstream parent. */
public final class ModSingularityVisuals {
    public static final PrototypeType<SingularityVisualProfile> TYPE = new PrototypeType<>(
            id("singularity_visual"), "singularity_visual", SingularityVisualProfile.CODEC,
            catalog -> catalog.values().forEach(SingularityVisualProfile::validate));

    private ModSingularityVisuals() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    public static SingularityVisualProfile get(int level) {
        return PrototypeRuntime.clientManager().snapshot(TYPE).get(id("level_" + level));
    }
    public static void bootstrapClientBundled() {
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (int level = 1; level <= 6; level++) {
            String name = "level_" + level;
            try (var reader = new java.io.InputStreamReader(ModSingularityVisuals.class.getResourceAsStream(
                    "/data/moonstation14/singularity_visual/" + name + ".json"), java.nio.charset.StandardCharsets.UTF_8)) {
                raw.put(id(name), com.google.gson.JsonParser.parseReader(reader).getAsJsonObject());
            } catch (java.io.IOException | NullPointerException exception) {
                throw new IllegalStateException("Missing bundled singularity visual profile " + name, exception);
            }
        }
        PrototypeRuntime.clientManager().reload(TYPE, raw);
    }
}

