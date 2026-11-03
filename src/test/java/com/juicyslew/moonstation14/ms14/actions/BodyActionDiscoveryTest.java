package com.juicyslew.moonstation14.ms14.actions;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.actions.prototype.ModActionPrototypes;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyActionDiscoveryTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    static Map<ResourceLocation, JsonObject> resources(String directory) throws Exception {
        Path root = Path.of(System.getProperty("moonstation14.projectDir"), "src/main/resources/data/moonstation14/moonstation14", directory);
        Map<ResourceLocation, JsonObject> data = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                data.put(id(name.substring(0, name.length() - 5)), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
        }
        return data;
    }

    @Test void discoversActionsFromResolvedProductionCharacterComposition() throws Exception {
        var characters = resources("character");
        var actions = resources("character_action");
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.register(ModActionPrototypes.TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, characters);
        manager.reload(ModActionPrototypes.TYPE, actions);
        var profiles = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        var actionCatalog = manager.snapshot(ModActionPrototypes.TYPE);

        assertActions(profiles.get(id("human")), actionCatalog, "combat_mode_toggle", "scream");
        for (String species : java.util.List.of("bat", "bee", "monkey"))
            assertActions(profiles.get(id(species)), actionCatalog, "combat_mode_toggle");
        for (String species : java.util.List.of("kobold", "scurret"))
            assertActions(profiles.get(id(species)), actionCatalog, "combat_mode_toggle", "scream");
        ResourceLocation inheritedChild = id("action_discovery_simple_mob_child_fixture");
        characters.put(inheritedChild, JsonParser.parseString("""
                {"parent":"moonstation14:supported_simple_mob","host_entity_types":[]}
                """).getAsJsonObject());
        manager.reload(ModCharacters.CHARACTER_TYPE, characters);
        assertTrue(manager.snapshot(ModCharacters.CHARACTER_TYPE).get(inheritedChild).component(
                com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent.class).isPresent(),
                "concrete child resolves inherited combat composition without species-name policy");

        ResourceLocation child = id("action_discovery_child_fixture");
        characters.put(child, JsonParser.parseString("""
                {"parent":"moonstation14:human","host_entity_types":[],"components":[{"type":"CombatMode","combatToggleAction":"moonstation14:scream"}]}
                """).getAsJsonObject());
        manager.reload(ModCharacters.CHARACTER_TYPE, characters);
        assertActions(manager.snapshot(ModCharacters.CHARACTER_TYPE).get(child), actionCatalog, "scream", "scream");

        var missing = JsonParser.parseString("""
                {"host_entity_types":[],"components":[{"type":"CombatMode","combatToggleAction":"moonstation14:missing_action"}]}
                """).getAsJsonObject();
        ResourceLocation missingId = id("action_discovery_missing_fixture");
        characters.put(missingId, missing);
        manager.reload(ModCharacters.CHARACTER_TYPE, characters);
        assertTrue(BodyActionDiscovery.discover(UUID.randomUUID(), manager.snapshot(ModCharacters.CHARACTER_TYPE).get(missingId), actionCatalog).isEmpty(),
                "unresolved action references fail closed");
    }

    @Test void carriedProvidersKeepTokensAcrossWornPocketHeldAndMaskCases() throws Exception {
        var equipmentJson = resources("equipment_item");
        var actionJson = resources("character_action");
        var manager = new PrototypeManager();
        manager.register(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE);
        manager.register(ModActionPrototypes.TYPE);
        manager.reload(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, equipmentJson);
        manager.reload(ModActionPrototypes.TYPE, actionJson);
        var tanks = manager.snapshot(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE);
        var actions = manager.snapshot(ModActionPrototypes.TYPE);
        var found = new java.util.ArrayList<BodyActionDiscovery.Entry>();
        BodyActionDiscovery.addItemGrant(found, "equipment:back:tank-a", tanks.get(id("oxygen_tank")), true, true, actions);
        BodyActionDiscovery.addItemGrant(found, "equipment:pocket1:tank-b", tanks.get(id("emergency_oxygen_tank")), false, true, actions);
        BodyActionDiscovery.addItemGrant(found, "hand:left:tank-c", tanks.get(id("oxygen_tank")), false, true, actions);
        assertEquals(java.util.List.of("equipment:back:tank-a", "equipment:pocket1:tank-b", "hand:left:tank-c"),
                found.stream().map(BodyActionDiscovery.Entry::providerKey).toList());
        assertEquals(java.util.List.of(id("toggle_internals"), id("toggle_internals"), id("toggle_internals")),
                found.stream().map(BodyActionDiscovery.Entry::actionId).toList());
        found.clear();
        BodyActionDiscovery.addItemGrant(found, "equipment:head:cap", tanks.get(id("debug_capability_cap")), true, true, actions);
        assertEquals("debug_pulse", found.getFirst().actionId().getPath());
        found.clear();
        BodyActionDiscovery.addItemGrant(found, "equipment:pocket1:mask", tanks.get(id("breathing_mask")), false, true, actions);
        assertTrue(found.isEmpty(), "held/pocket masks with active_if_carried=false do not grant mask toggle");
    }

    private static void assertActions(com.juicyslew.moonstation14.component.codec.json.CharacterData character,
                                      com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype> catalog,
                                      String... expected) {
        assertEquals(java.util.List.of(expected).stream().map(BodyActionDiscoveryTest::id).toList(),
                BodyActionDiscovery.discover(UUID.randomUUID(), character, catalog).stream().map(BodyActionDiscovery.Entry::actionId).toList());
    }
}
