package com.juicyslew.moonstation14.ms14.storage.grid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemFootprintResourceTest {
    private static final String RESOURCE_ROOT = "data/moonstation14/moonstation14/item_footprint";

    @Test
    void productionFootprintProfilesLoadFromResourcesWithExpectedInheritanceAndSizes() throws IOException {
        Map<ResourceLocation, JsonObject> raw = loadResources();
        assertEquals(36, raw.size());

        PrototypeCatalog<JsonObject> resolved = PrototypeResolver.resolve(raw);
        assertEquals(31, resolved.size());
        String[] coreItems = {"steel", "glass", "plastic", "cloth", "wood", "jug", "station_floor_tile",
                "station_floor_tile_white", "station_floor_tile_pry", "paper", "bottle", "waffle", "crowbar",
                "wirecutter", "atmosphere_analyzer", "grenade", "hv_cable_spool", "mv_cable_spool",
                "apc_cable_spool", "bag", "belt", "pickaxe", "syringe", "metal_rod", "screwdriver", "multitool",
                "network_configurator", "remote_signaller", "remote_signaller_advanced", "signal_button", "signal_switch"};
        for (String name : coreItems) assertTrue(resolved.get(id(name)) != null, "missing profile " + name);

        assertCounts(resolved, 4, "steel", "glass", "plastic", "cloth", "wood", "jug",
                "station_floor_tile", "station_floor_tile_white");
        assertCounts(resolved, 1, "paper", "bottle", "waffle", "syringe");
        assertCounts(resolved, 2, "crowbar", "station_floor_tile_pry", "wirecutter", "grenade",
                "atmosphere_analyzer", "hv_cable_spool", "mv_cable_spool", "apc_cable_spool");

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModItemFootprints.TYPE);
        manager.reload(ModItemFootprints.TYPE, raw);
        var catalog = manager.snapshot(ModItemFootprints.TYPE);
        assertEquals(31, catalog.size());
        assertEquals(16, catalog.get(id("bag")).cells().size());
        assertEquals(36, catalog.get(id("belt")).cells().size());
        assertEquals(5, catalog.get(id("pickaxe")).cells().size());
        assertEquals(4, catalog.get(id("metal_rod")).cells().size());
        assertEquals(2, catalog.get(id("network_configurator")).cells().size());
        assertEquals(catalog.get(id("remote_signaller")).cells(), catalog.get(id("remote_signaller_advanced")).cells());
        assertEquals(2, catalog.get(id("remote_signaller_advanced")).cells().size());
        assertEquals(1, catalog.get(id("signal_button")).cells().size());
        assertEquals(1, catalog.get(id("signal_switch")).cells().size());
        assertTrue(catalog.get(id("bag")).cells().contains(new GridGeometry.Cell(3, 3)));
    }

    private static void assertCounts(PrototypeCatalog<JsonObject> catalog, int expected, String... ids) {
        for (String name : ids) assertEquals(expected, catalog.get(id(name)).getAsJsonArray("cells").size(), name);
    }

    private static Map<ResourceLocation, JsonObject> loadResources() throws IOException {
        var root = ItemFootprintResourceTest.class.getClassLoader().getResource(RESOURCE_ROOT);
        if (root == null) throw new IOException("Classpath resource directory not found: " + RESOURCE_ROOT);
        Path directory;
        if ("file".equals(root.getProtocol())) {
            directory = Path.of(URI.create(root.toString()));
        } else if ("union".equals(root.getProtocol())) {
            String url = root.toString();
            int buildMarker = url.indexOf("/build/");
            if (buildMarker < 0) throw new IOException("Could not locate project build directory in classpath URL: " + root);
            String projectPath = URLDecoder.decode(url.substring("union:".length(), buildMarker), StandardCharsets.UTF_8);
            if (projectPath.startsWith("/") && projectPath.length() > 2 && projectPath.charAt(2) == ':') {
                projectPath = projectPath.substring(1);
            }
            directory = Path.of(projectPath).resolve("src/main/resources").resolve(RESOURCE_ROOT);
        } else {
            throw new IOException("Unsupported classpath URL: " + root);
        }
        Map<ResourceLocation, JsonObject> resources = new LinkedHashMap<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try (var reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    String name = path.getFileName().toString();
                    resources.put(id(name.substring(0, name.length() - 5)), JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        }
        return resources;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
