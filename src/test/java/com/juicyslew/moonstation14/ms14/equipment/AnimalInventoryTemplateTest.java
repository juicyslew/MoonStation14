package com.juicyslew.moonstation14.ms14.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class AnimalInventoryTemplateTest {
    @Test void templatesResolveAnimalSlotLayoutsDependenciesAndTypedGasTankFilter() throws Exception {
        var manager = new PrototypeManager();
        manager.register(ModInventoryTemplates.TYPE);
        var resources = new LinkedHashMap<ResourceLocation, JsonObject>();
        Path directory = projectPath("src/main/resources/data/moonstation14/moonstation14/inventory_template");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                resources.put(ResourceLocation.parse("moonstation14:" + file.getFileName().toString().replace(".json", "")), json);
            }
        }
        manager.reload(ModInventoryTemplates.TYPE, resources);
        var catalog = manager.snapshot(ModInventoryTemplates.TYPE);
        assertEquals(java.util.List.of("head", "ears", "mask", "jumpsuit", "id", "suitstorage", "outerClothing"),
                catalog.get(id("monkey")).slots().stream().map(InventoryTemplateData.Slot::id).toList());
        assertEquals(java.util.List.of("mask", "suitstorage"),
                catalog.get(id("pet")).slots().stream().map(InventoryTemplateData.Slot::id).toList());
        assertEquals(java.util.List.of("mask", "neck", "eyes", "suitstorage", "head"),
                catalog.get(id("hamster")).slots().stream().map(InventoryTemplateData.Slot::id).toList());
        var hamsterStorage = catalog.get(id("hamster")).slot("suitstorage").orElseThrow();
        assertEquals(java.util.List.of(InventoryTemplateData.SlotComponent.GAS_TANK), hamsterStorage.whitelistComponents());
        assertTrue(hamsterStorage.stripTime() == 3.0);
        assertEquals(java.util.List.of(EquipmentTag.PET_WEARABLE), catalog.get(id("pet")).slot("mask").orElseThrow().whitelist());
        assertTrue(catalog.get(id("hamster")).slot("mask").orElseThrow().whitelist().contains(EquipmentTag.HAMSTER_WEARABLE));
        assertTrue(catalog.get(id("kangaroo")).slot("pocket1").orElseThrow().dependsOn().isEmpty());
        assertTrue(catalog.get(id("kangaroo")).slot("pocket1").orElseThrow().stripHidden());
        var capability = EquipmentSlotsPrototypeComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"EquipmentSlots\",\"template\":\"moonstation14:monkey\"}")).getOrThrow();
        assertEquals(id("monkey"), capability.template().orElseThrow());

        var hamster = catalog.get(id("hamster"));
        var pet = catalog.get(id("pet"));
        var humanJson = JsonParser.parseString(Files.readString(projectPath(
                "src/main/resources/data/moonstation14/moonstation14/inventory_template/human.json")));
        var human = InventoryTemplateData.CODEC.parse(JsonOps.INSTANCE, humanJson).getOrThrow();
        var glassesJson = JsonParser.parseString(Files.readString(projectPath(
                "src/main/resources/data/moonstation14/moonstation14/equipment_item/glasses.json")));
        var glasses = EquipmentItemData.CODEC.parse(JsonOps.INSTANCE, glassesJson).getOrThrow();
        assertTrue(EquipmentFitPolicy.fits(hamster, "eyes", glasses, java.util.Set.of(), false),
                "source-verified HamsterWearable glasses fit hamster eyes");
        assertFalse(EquipmentFitPolicy.fits(hamster, "head", glasses, java.util.Set.of(), false),
                "wrong slot is rejected");
        var untaggedGlasses = new EquipmentItemData(glasses.item(), glasses.clothing(), glasses.allowSuitStorage(), java.util.List.of());
        assertFalse(EquipmentFitPolicy.fits(hamster, "eyes", untaggedGlasses, java.util.Set.of(), false),
                "hamster whitelist rejects otherwise slot-compatible untagged equipment");
        assertTrue(EquipmentFitPolicy.fits(human, "eyes", untaggedGlasses, java.util.Set.of(), false),
                "human non-whitelist equipment behavior remains unchanged");
        assertFalse(EquipmentFitPolicy.fits(pet, "mask", glasses, java.util.Set.of(), false),
                "eye clothing cannot fit pet mask");
        var redTieJson = JsonParser.parseString(Files.readString(projectPath(
                "src/main/resources/data/moonstation14/moonstation14/equipment_item/red_tie.json")));
        var redTie = EquipmentItemData.CODEC.parse(JsonOps.INSTANCE, redTieJson).getOrThrow();
        assertEquals(java.util.List.of(EquipmentTag.HAMSTER_WEARABLE), redTie.tags());
        assertTrue(EquipmentFitPolicy.fits(hamster, "neck", redTie, java.util.Set.of(), false));
        for (String name : java.util.List.of("monkey", "kobold", "scurret", "fox", "possum", "ordinary_pig", "hamster", "mothroach", "parrot", "kangaroo")) {
            var animal = readCharacter(name);
            var components = animal.getAsJsonArray("components");
            assertTrue(components.asList().stream().map(element -> element.getAsJsonObject())
                    .anyMatch(component -> "EquipmentSlots".equals(component.get("type").getAsString())), name);
        }
        for (String name : java.util.List.of("monkey", "kobold", "scurret")) {
            var components = readCharacter(name).getAsJsonArray("components");
            assertTrue(components.asList().stream().map(element -> element.getAsJsonObject())
                    .anyMatch(component -> "Hands".equals(component.get("type").getAsString())
                            && component.getAsJsonArray("hands").size() == 1), name);
        }
        for (String name : java.util.List.of("fox", "possum", "ordinary_pig", "hamster", "mothroach", "parrot")) {
            var components = readCharacter(name).getAsJsonArray("components");
            assertFalse(components.asList().stream().map(element -> element.getAsJsonObject())
                    .anyMatch(component -> "Hands".equals(component.get("type").getAsString())), name);
        }

        var characterManager = new PrototypeManager();
        characterManager.register(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE);
        var characterDefinitions = new LinkedHashMap<ResourceLocation, JsonObject>();
        Path characterDirectory = projectPath("src/main/resources/data/moonstation14/moonstation14/character");
        try (var files = Files.list(characterDirectory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                characterDefinitions.put(id(file.getFileName().toString().replace(".json", "")),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
        }
        characterManager.reload(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE,
                characterDefinitions);
        var resolvedCharacters = characterManager.snapshot(
                com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE);
        var nativePigId = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(resolvedCharacters,
                ResourceLocation.parse("minecraft:pig")).orElseThrow();
        var customPigId = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(resolvedCharacters,
                id("pig")).orElseThrow();
        assertEquals(id("pig"), nativePigId);
        assertEquals(id("ordinary_pig"), customPigId);
        assertTrue(resolvedCharacters.get(nativePigId).component(
                com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent.class).isEmpty(),
                "preserved native Minecraft pig has no animal inventory composition");
        assertEquals(id("pet"), resolvedCharacters.get(customPigId).component(
                com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent.class)
                .orElseThrow().template().orElseThrow());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("moonstation14:" + path); }
    private static JsonObject readCharacter(String name) throws Exception {
        Path path = projectPath("src/main/resources/data/moonstation14/moonstation14/character/" + name + ".json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    private static Path projectPath(String path) throws java.io.IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new java.io.IOException("Could not locate " + path);
    }
}
