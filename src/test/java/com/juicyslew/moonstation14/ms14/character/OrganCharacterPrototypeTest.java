package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.BodyComponent;
import com.juicyslew.moonstation14.ms14.character.components.InitialBodyComponent;
import com.juicyslew.moonstation14.ms14.organ.ModOrgans;
import com.juicyslew.moonstation14.ms14.organ.OrganCategory;
import com.juicyslew.moonstation14.ms14.organ.OrganData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OrganCharacterPrototypeTest {
    private static final ResourceLocation HUMAN = ResourceLocation.parse("moonstation14:human");
    private static final ResourceLocation PIG = ResourceLocation.parse("moonstation14:pig");
    private static final ResourceLocation HUMAN_LUNG = ResourceLocation.parse("moonstation14:organ_lungs_human");
    private static final ResourceLocation PIG_LUNG = ResourceLocation.parse("moonstation14:organ_lungs_pig");

    private JsonObject resource(String type, String id) {
        String path = "data/moonstation14/moonstation14/" + type + "/" + id + ".json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) { throw new AssertionError(ex); }
    }

    private Map<ResourceLocation, JsonObject> organs() {
        return Map.of(HUMAN_LUNG, resource("organ", "organ_lungs_human"),
                PIG_LUNG, resource("organ", "organ_lungs_pig"));
    }

    @Test void typedOrganMarkerAndCharacterEntries() {
        assertTrue(PrototypeRuntime.serverManager().registeredTypes().contains(ModOrgans.ORGAN_TYPE));
        assertTrue(PrototypeRuntime.clientManager().registeredTypes().contains(ModOrgans.ORGAN_TYPE));
        for (var entry : organs().entrySet()) {
            OrganData organ = OrganData.CODEC.parse(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
            assertEquals(OrganCategory.LUNGS, organ.category());
            assertTrue(organ.lung().isPresent());
            assertEquals(entry.getKey().equals(HUMAN_LUNG) ? 6.0 : 4.2,
                    organ.lung().orElseThrow().maxLungMoles());
            assertEquals(organ, OrganData.CODEC.parse(JsonOps.INSTANCE,
                    OrganData.CODEC.encodeStart(JsonOps.INSTANCE, organ).getOrThrow()).getOrThrow());
        }
        for (var entry : Map.of(HUMAN, HUMAN_LUNG, PIG, PIG_LUNG).entrySet()) {
            CharacterData mob = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    resource("character", entry.getKey().getPath())).getOrThrow();
            assertTrue(mob.component(BodyComponent.class).isPresent());
            assertEquals(entry.getValue(), mob.component(InitialBodyComponent.class).orElseThrow()
                    .organs().get(OrganCategory.LUNGS));
            assertTrue(mob.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class).isPresent());
        }
        for (String invalid : new String[]{"{\"category\":\"Unknown\",\"Lung\":{}}",
                "{\"category\":\"Lungs\",\"Lung\":{\"interval\":1}}",
                "{\"category\":\"Lungs\"}", "{\"category\":\"Lungs\",\"Lung\":{},\"unknown\":0}"}) {
            assertTrue(OrganData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent(), invalid);
        }
    }

    @Test void inheritanceOptionalityAndStrictFragments() {
        JsonObject human = resource("character", "human");
        JsonObject parent = new JsonObject();
        parent.addProperty("abstract", true);
        parent.add("components", JsonParser.parseString("[{\"type\":\"Body\"},{\"type\":\"InitialBody\",\"organs\":{\"Lungs\":\"moonstation14:organ_lungs_human\"}}]"));
        JsonObject child = human.deepCopy();
        child.getAsJsonArray("components").remove(3);
        child.getAsJsonArray("components").remove(2);
        child.addProperty("parent", "parent");
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.register(ModOrgans.ORGAN_TYPE);
        manager.reload(Map.of(ModOrgans.ORGAN_TYPE, organs(), ModCharacters.CHARACTER_TYPE,
                Map.of(ResourceLocation.parse("moonstation14:parent"), parent, HUMAN, child)));
        assertEquals(HUMAN_LUNG, manager.snapshot(ModCharacters.CHARACTER_TYPE).get(HUMAN)
                .component(InitialBodyComponent.class).orElseThrow().organs().get(OrganCategory.LUNGS));
        JsonObject noBody = human.deepCopy();
        noBody.remove("components");
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, noBody).getOrThrow().component(BodyComponent.class).isEmpty());
        assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE, noBody).getOrThrow().component(InitialBodyComponent.class).isEmpty());
        for (String bad : new String[]{"[{\"type\":\"InitialBody\",\"organs\":{\"Heart\":\"moonstation14:x\"}}]",
                "[{\"type\":\"InitialBody\",\"organs\":{\"Lungs\":\"lungs\"}}]",
                "[{\"type\":\"Body\",\"unknown\":1}]"}) {
            JsonObject invalid = child.deepCopy();
            invalid.add("components", JsonParser.parseString(bad));
            assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE, Map.of(HUMAN, invalid,
                    ResourceLocation.parse("moonstation14:parent"), parent))));
        }
    }

    @Test void missingAndWrongCategoryRollbackAcrossReloadAndSync() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.register(ModOrgans.ORGAN_TYPE);
        manager.reload(Map.of(ModOrgans.ORGAN_TYPE, organs(), ModCharacters.CHARACTER_TYPE,
                Map.of(HUMAN, resource("character", "human"))));
        var published = manager.snapshot();
        var valid = manager.encodePublishedCatalogs();
        JsonObject missing = resource("character", "human");
        missing.getAsJsonArray("components").get(3).getAsJsonObject().getAsJsonObject("organs")
                .addProperty("Lungs", "moonstation14:not_found");
        var badReload = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModCharacters.CHARACTER_TYPE,
                Map.of(HUMAN, missing))));
        assertTrue(badReload.getMessage().contains("missing organ prototype"), badReload.getMessage());
        assertEquals(published, manager.snapshot());
        var badSync = new LinkedHashMap<>(valid);
        badSync.put(ModCharacters.CHARACTER_TYPE.typeId(), Map.of(HUMAN, missing));
        assertThrows(RuntimeException.class, () -> manager.publishEncodedCatalogs(badSync));
        assertEquals(published, manager.snapshot());
        assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModOrgans.ORGAN_TYPE, Map.of())));
        assertEquals(published, manager.snapshot());
        // Another organ catalog candidate can invalidate a previously valid character.
        JsonObject changed = resource("organ", "organ_lungs_human");
        changed.addProperty("category", "Heart");
        changed.remove("Lung");
        var wrongCategory = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModOrgans.ORGAN_TYPE,
                Map.of(HUMAN_LUNG, changed))));
        assertTrue(wrongCategory.getMessage().contains("wrong-category"), wrongCategory.getMessage());
        var wrongSync = new LinkedHashMap<>(valid);
        wrongSync.put(ModOrgans.ORGAN_TYPE.typeId(), Map.of(HUMAN_LUNG, changed));
        assertThrows(RuntimeException.class, () -> manager.publishEncodedCatalogs(wrongSync));
        assertEquals(published, manager.snapshot());
    }

    @Test void lungTuningReloadIsTypedAndInvalidCandidateRollsBack() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.register(ModOrgans.ORGAN_TYPE);
        manager.reload(Map.of(ModOrgans.ORGAN_TYPE, organs(), ModCharacters.CHARACTER_TYPE,
                Map.of(HUMAN, resource("character", "human"))));
        var old = manager.snapshot();
        JsonObject changed = resource("organ", "organ_lungs_human");
        changed.getAsJsonObject("Lung").addProperty("max_lung_moles", 3.25);
        manager.reload(Map.of(ModOrgans.ORGAN_TYPE, Map.of(HUMAN_LUNG, changed)));
        assertEquals(3.25, manager.snapshot(ModOrgans.ORGAN_TYPE).get(HUMAN_LUNG).lung().orElseThrow().maxLungMoles());
        assertNotEquals(old, manager.snapshot());
        var published = manager.snapshot();
        for (String badValue : new String[]{"null", "\"6\"", "-1", "1000001"}) {
            JsonObject invalid = changed.deepCopy();
            invalid.getAsJsonObject("Lung").add("max_lung_moles", JsonParser.parseString(badValue));
            assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModOrgans.ORGAN_TYPE, Map.of(HUMAN_LUNG, invalid))));
            assertEquals(published, manager.snapshot());
        }
        JsonObject invalidGas = changed.deepCopy();
        invalidGas.getAsJsonObject("Lung").add("toxic_gas_damage_per_mole",
                JsonParser.parseString("{\"plasma\":{\"poison\":\"1\"}}"));
        assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModOrgans.ORGAN_TYPE, Map.of(HUMAN_LUNG, invalidGas))));
        assertEquals(published, manager.snapshot());
    }

    @Test void respiratorMinimumBelowPersistedFloorRejectsStageIncludingAbstractParent() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.register(ModOrgans.ORGAN_TYPE);
        JsonObject human = resource("character", "human");
        manager.reload(Map.of(ModOrgans.ORGAN_TYPE, organs(), ModCharacters.CHARACTER_TYPE, Map.of(HUMAN, human)));
        var published = manager.snapshot();
        for (double minimum : new double[]{-2.01, -3}) {
            JsonObject invalid = human.deepCopy();
            invalid.getAsJsonArray("components").get(4).getAsJsonObject().addProperty("min_saturation", minimum);
            var rejected = assertThrows(RuntimeException.class, () -> manager.stage(
                    Map.of(ModCharacters.CHARACTER_TYPE, Map.of(HUMAN, invalid))));
            assertTrue(rejected.getMessage().contains("min_saturation"), rejected.getMessage());
            assertEquals(published, manager.snapshot());
        }
        JsonObject parent = new JsonObject();
        parent.addProperty("abstract", true);
        parent.add("components", JsonParser.parseString("[{\"type\":\"Respirator\",\"min_saturation\":-3}]"));
        JsonObject child = human.deepCopy();
        child.addProperty("parent", "bad_parent");
        var rejected = assertThrows(RuntimeException.class, () -> manager.stage(Map.of(
                ModCharacters.CHARACTER_TYPE, Map.of(HUMAN, child,
                        ResourceLocation.parse("moonstation14:bad_parent"), parent))));
        assertTrue(rejected.getMessage().contains("min_saturation"), rejected.getMessage());
        assertEquals(published, manager.snapshot());
    }
}
