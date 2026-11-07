package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authored atlas UV regions for the deliberately flat-lit laser/shotgun status indicators. */
public final class GunIndicatorRegions {
    public record Region(double minU, double minV, double maxU, double maxV, Integer tintIndex, boolean emissive) {
        public Region(double minU, double minV, double maxU, double maxV) { this(minU, minV, maxU, maxV, null, true); }
        private static final double UV_EPSILON = 1.0e-5;
        public boolean matches(double u, double v) { return u >= minU - UV_EPSILON && u <= maxU + UV_EPSILON && v >= minV - UV_EPSILON && v <= maxV + UV_EPSILON; }
    }
    private static volatile Map<String, Region> definitions = Map.of();
    private GunIndicatorRegions() { }

    static void load(net.minecraft.server.packs.resources.ResourceManager manager) {
        var resource = manager.getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "gun_indicator_regions.json"));
        if (resource.isEmpty()) throw new IllegalStateException("Missing gun indicator region definitions");
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            var object = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Region> parsed = new LinkedHashMap<>();
            object.entrySet().forEach(entry -> {
                var definition = entry.getValue();
                var values = definition.isJsonArray() ? definition.getAsJsonArray() : definition.getAsJsonObject().getAsJsonArray("bounds");
                if (values.size() != 4) throw new IllegalArgumentException("Region must have four bounds: " + entry.getKey());
                Integer tint = definition.isJsonObject() && definition.getAsJsonObject().has("tint_index") ? definition.getAsJsonObject().get("tint_index").getAsInt() : null;
                boolean emissive = !definition.isJsonObject() || !definition.getAsJsonObject().has("emissive") || definition.getAsJsonObject().get("emissive").getAsBoolean();
                parsed.put(entry.getKey(), new Region(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble(), values.get(3).getAsDouble(), tint, emissive));
            });
            definitions = Map.copyOf(parsed);
        } catch (Exception ex) { throw new IllegalStateException("Unable to load gun indicator regions", ex); }
    }
    public static Region named(String name) {
        Region region = definitions.get(name);
        if (region == null) throw new IllegalStateException("Gun indicator regions have not been loaded: " + name);
        return region;
    }

    public static boolean matches(BakedQuad quad, Region region) {
        int[] data = quad.getVertices();
        // Decode UVs using the quad transformer's declared packed vertex layout.
        for (int i = 0; i < 4; i++) {
            int base = i * IQuadTransformer.STRIDE;
            double u = (Float.intBitsToFloat(data[base + IQuadTransformer.UV0]) - quad.getSprite().getU0()) / (quad.getSprite().getU1() - quad.getSprite().getU0());
            double v = (Float.intBitsToFloat(data[base + IQuadTransformer.UV0 + 1]) - quad.getSprite().getV0()) / (quad.getSprite().getV1() - quad.getSprite().getV0());
            // Imported item model resources specify flip_v=false; ObjModel.makeQuad therefore preserves OBJ V.
            if (!region.matches(u, v)) return false;
        }
        return true;
    }
}
