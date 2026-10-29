package com.juicyslew.moonstation14.ms14.storage.grid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemFootprintResourceTest {
    private static final String RESOURCE_ROOT = "data/moonstation14/moonstation14/item_footprint";

    @Test
    void everySupportedSourceSpeciesLinksToMainProductAndPacketFootprints() throws IOException {
        var unsupported = java.util.Set.of("killer_tomato", "anomaly_berry", "gatfruit", "fake_capfruit",
                "real_capfruit", "spacemans_trumpet");
        var expectedSpecies = BotanyCatalogCandidateTest.resources().keySet().stream()
                .map(ResourceLocation::getPath).filter(name -> !name.equals("defaults") && !unsupported.contains(name))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(65, expectedSpecies.size());

        var mainSeeds = BotanyCatalogCandidateTest.mainResources("seed_profile", expectedSpecies.toArray(String[]::new));
        var footprints = PrototypeResolver.resolve(loadResources());
        for (String species : expectedSpecies) {
            var source = mainSeeds.get(id(species));
            assertTrue(source != null, "missing MAIN seed profile " + species);
            var packet = ResourceLocation.parse(source.get("packet").getAsString());
            var product = ResourceLocation.parse(source.get("product").getAsString());
            assertTrue(footprints.get(packet) != null, species + " packet footprint " + packet);
            assertTrue(footprints.get(product) != null, species + " product footprint " + product);
            var packetProfile = ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    footprints.get(packet)).getOrThrow();
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), packetProfile.itemSize(),
                    species + " packet size from source packet expectation");
        }
        assertEquals(65, mainSeeds.size());
        assertEquals(id("pea_seeds"), ResourceLocation.parse(mainSeeds.get(id("pea")).get("packet").getAsString()));
        assertEquals(ResourceLocation.parse(mainSeeds.get(id("pea")).get("packet").getAsString()),
                ResourceLocation.parse(mainSeeds.get(id("world_pea")).get("packet").getAsString()));
        assertNotEquals(ResourceLocation.parse(mainSeeds.get(id("pea")).get("product").getAsString()),
                ResourceLocation.parse(mainSeeds.get(id("world_pea")).get("product").getAsString()));
    }

    @Test
    void productionFootprintProfilesLoadFromResourcesWithExpectedInheritanceAndSizes() throws IOException {
        Map<ResourceLocation, JsonObject> raw = loadResources();
        // 202 concrete profiles plus five abstract geometry templates in the production inventory.
        assertEquals(207, raw.size());

        PrototypeCatalog<JsonObject> resolved = PrototypeResolver.resolve(raw);
        assertEquals(202, resolved.size());
        String[] coreItems = {"steel", "glass", "plastic", "cloth", "wood", "jug", "station_floor_tile",
                "station_floor_tile_white", "station_floor_tile_pry", "paper", "bottle", "waffle", "crowbar",
                "wirecutter", "atmosphere_analyzer", "grenade", "hv_cable_spool", "mv_cable_spool",
                "apc_cable_spool", "bag", "belt", "pickaxe", "syringe", "metal_rod", "screwdriver", "multitool",
                "network_configurator", "remote_signaller", "remote_signaller_advanced", "signal_button", "signal_switch",
                "beaker", "beaker_large", "chem_master", "black_bandana", "black_gloves", "black_shoes",
                "blank_id_card", "earpieces", "glasses", "grey_cap", "grey_jumpsuit", "red_tie", "simple_coat",
                "utility_case", "pill", "pill_canister", "mothroach_normal", "hamster_tiny", "plasma_tank", "suit_rad", "hood_rad", "meatwheat_seeds", "meatwheat_bushel"};
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
        assertEquals(202, catalog.size());
        var pocket = new com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData(java.util.List.of(
                new com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.Slot("pocket1", "Pocket",
                        ResourceLocation.withDefaultNamespace("test"), "Default", 0, 0, java.util.List.of(
                        com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET),
                        java.util.List.of(), java.util.List.of(), false)));
        for (String item : new String[]{"wheat_seeds", "tomato_seeds", "potato_seeds", "poppy_seeds", "aloe_seeds"}) {
            assertEquals(1, catalog.get(id(item)).cells().size(), item + " resolves Tiny footprint");
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id(item)).itemSize(), item);
            assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(pocket, "pocket1",
                    catalog.get(id(item)), 1, 64, java.util.Set.of()), item + " fits pocket");
        }
        for (String item : new String[]{"wheat_bushel", "tomato", "potato", "poppy", "aloe"}) {
            assertEquals(2, catalog.get(id(item)).cells().size(), item + " resolves Small footprint");
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id(item)).itemSize(), item);
            assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(pocket, "pocket1",
                    catalog.get(id(item)), 1, 64, java.util.Set.of()), item + " fits pocket");
        }
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id("poppy")).itemSize());
        assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(pocket, "pocket1",
                catalog.get(id("poppy")), 1, 64, java.util.Set.of()));
        for (String item : new String[]{"meatwheat_seeds"}) {
            assertEquals(1, catalog.get(id(item)).cells().size(), item + " resolves Tiny footprint");
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id(item)).itemSize(), item);
            assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(pocket, "pocket1",
                    catalog.get(id(item)), 1, 64, java.util.Set.of()), item + " fits pocket");
        }
        for (String item : new String[]{"meatwheat_bushel"}) {
            assertEquals(2, catalog.get(id(item)).cells().size(), item + " resolves Small footprint");
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id(item)).itemSize(), item);
            assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(pocket, "pocket1",
                    catalog.get(id(item)), 1, 64, java.util.Set.of()), item + " fits pocket");
        }
        for (String item : new String[]{"pill", "pill_canister"}) {
            assertEquals(java.util.List.of(new GridGeometry.Cell(0, 0)), catalog.get(id(item)).cells(),
                    item + " resolves inherited tiny footprint");
            assertFalse(catalog.get(id(item)).cells().isEmpty(), item + " must have a usable footprint");
        }
        assertEquals(4, catalog.get(id("mothroach_normal")).cells().size());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Normal), catalog.get(id("mothroach_normal")).itemSize());
        assertEquals(1, catalog.get(id("hamster_tiny")).cells().size());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("hamster_tiny")).itemSize());
        assertEquals(4, catalog.get(id("plasma_tank")).cells().size());
        assertEquals(9, catalog.get(id("suit_rad")).cells().size());
        assertEquals(1, catalog.get(id("hood_rad")).cells().size());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Normal), catalog.get(id("plasma_tank")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Huge), catalog.get(id("suit_rad")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id("hood_rad")).itemSize());
        assertEquals(catalog.get(id("wirecutter")).cells(), catalog.get(id("screwdriver")).cells());
        for (String item : new String[]{"screwdriver", "crowbar", "wirecutter", "multitool", "atmosphere_analyzer"})
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id(item)).itemSize(), item);
        assertTrue(raw.get(id("small")).get("item_size") == null,
                "abstract geometry templates do not invent/inherit a physical size");
        assertEquals(16, catalog.get(id("bag")).cells().size());
        assertEquals(36, catalog.get(id("belt")).cells().size());
        assertEquals(5, catalog.get(id("pickaxe")).cells().size());
        assertEquals(4, catalog.get(id("metal_rod")).cells().size());
        assertEquals(2, catalog.get(id("beaker")).cells().size());
        assertEquals(4, catalog.get(id("beaker_large")).cells().size());
        assertEquals(4, catalog.get(id("chem_master")).cells().size());
        assertEquals(2, catalog.get(id("network_configurator")).cells().size());
        assertEquals(catalog.get(id("remote_signaller")).cells(), catalog.get(id("remote_signaller_advanced")).cells());
        assertEquals(2, catalog.get(id("remote_signaller_advanced")).cells().size());
        assertEquals(1, catalog.get(id("signal_button")).cells().size());
        assertEquals(1, catalog.get(id("signal_switch")).cells().size());
        assertTrue(catalog.get(id("bag")).cells().contains(new GridGeometry.Cell(3, 3)));

        // Representative profiles from the expanded production crop/output catalog.
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("carrots_seeds")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id("carrot")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("pea_seeds")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id("food_world_peas")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id("food_pea_pod")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id("watermelon")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("trash_cherry_pit")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("blood_tomato_seeds")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("blue_tomato_seeds")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("papercane_seeds")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("steelcap_seeds")).itemSize());
        assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Tiny), catalog.get(id("towercap_seeds")).itemSize());
        for (String product : new String[]{"blood_tomato", "blue_tomato", "papercane", "steel_log", "log"})
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.Small), catalog.get(id(product)).itemSize(), product);
    }

    @Test
    void portableBodyFootprintsKeepCarrierGeometryBodySpecificForGridAdmission() throws IOException {
        var catalog = PrototypeResolver.resolve(loadResources());
        assertCounts(catalog, 4, "mothroach_normal");
        assertCounts(catalog, 1, "hamster_tiny");
        assertCounts(catalog, 4, "plasma_tank");
        assertCounts(catalog, 9, "suit_rad");
        assertCounts(catalog, 1, "hood_rad");
    }

    @Test void pocketAdmissionUsesExplicitPhysicalSizeAndNeverInfersItFromFootprintGeometry() {
        var icon = ResourceLocation.withDefaultNamespace("test");
        var slot = new com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.Slot("pocket1", "Pocket",
                icon, "Default", 0, 0, java.util.List.of(
                com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET),
                java.util.List.of(), java.util.List.of(), false);
        var template = new com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData(java.util.List.of(slot));
        var standardSizeGeometry = new ItemFootprintProfile(java.util.List.of(new GridGeometry.Cell(0, 0),
                new GridGeometry.Cell(1, 0)), java.util.Optional.of(ItemFootprintProfile.ItemSize.Small));
        var unitGeometryWithoutSize = new ItemFootprintProfile(java.util.List.of(new GridGeometry.Cell(0, 0)));
        var normal = new ItemFootprintProfile(java.util.List.of(new GridGeometry.Cell(0, 0)),
                java.util.Optional.of(ItemFootprintProfile.ItemSize.Normal));
        assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(template, "pocket1",
                standardSizeGeometry, 1, 64, java.util.Set.of()));
        assertFalse(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(template, "pocket1",
                unitGeometryWithoutSize, 1, 64, java.util.Set.of()));
        assertFalse(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fitsPocket(template, "pocket1",
                normal, 1, 64, java.util.Set.of()));
        assertEquals(java.util.Optional.empty(), unitGeometryWithoutSize.itemSize());
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
