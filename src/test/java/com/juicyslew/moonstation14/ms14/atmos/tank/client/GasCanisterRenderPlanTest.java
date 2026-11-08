package com.juicyslew.moonstation14.ms14.atmos.tank.client;

import com.juicyslew.moonstation14.entities.GasCanisterEntity;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasCanisterProfile;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class GasCanisterRenderPlanTest {
    private static final String ASSET_ROOT = "/assets/moonstation14/";

    @Test void plansResolvedStorageAndPlasmaAppearanceAsIndependentComponents() {
        var storage = profile("yellow", false);
        var plasma = profile("orange", true);
        var storagePlan = GasCanisterRenderPlan.from(new GasCanisterEntity.CanisterAppearance("storage", 0, false, storage));
        var plasmaPlan = GasCanisterRenderPlan.from(new GasCanisterEntity.CanisterAppearance("plasma", 3, true, plasma));
        assertEquals(4, storagePlan.components().size());
        assertEquals(5, plasmaPlan.components().size());
        assertEquals("moonstation14:block/canisters/yellow/components/pressure_0", storagePlan.components().get(2).model().toString());
        assertEquals("moonstation14:block/canisters/orange/components/pressure_3", plasmaPlan.components().get(2).model().toString());
        assertTrue(storagePlan.components().get(2).fullbright());
        assertEquals("lock_unlocked", name(storagePlan, 3));
        assertEquals("lock_locked", name(plasmaPlan, 3));
        assertEquals("tank_inserted", name(plasmaPlan, 4));
        for (var plan : new GasCanisterRenderPlan[]{storagePlan, plasmaPlan}) {
            assertEquals(1, plan.components().stream().filter(c -> c.model().getPath().contains("pressure_")).count());
            assertEquals(1, plan.components().stream().filter(c -> c.model().getPath().contains("lock_")).count());
        }
    }

    @Test void everySelectableComponentHasRegisteredModelAndClosedResourceDependencies() throws Exception {
        var registered = GasCanisterRenderer.additionalModels().stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
        for (String skin : new String[]{"yellow", "orange"}) {
            for (int band = 0; band < 4; band++) {
                for (boolean inserted : new boolean[]{false, true}) {
                    var plan = GasCanisterRenderPlan.from(new GasCanisterEntity.CanisterAppearance(
                            "test", band, inserted, profile(skin, false)));
                    for (var component : plan.components()) {
                        String modelPath = component.model().getPath();
                        assertTrue(registered.stream().anyMatch(id -> id.startsWith(component.model() + "#")),
                                "Unregistered model " + component.model());
                        String relative = "models/" + component.model().getPath() + ".json";
                        assertResource(relative);
                        JsonObject json = read(relative);
                        for (String texture : json.getAsJsonObject("textures").keySet()) {
                            String value = json.getAsJsonObject("textures").get(texture).getAsString();
                            if (value.contains("#")) continue;
                            String texturePath = value.substring(value.indexOf(':') + 1);
                            assertResource("textures/" + texturePath + ".png");
                            if (texturePath.endsWith("_pressure0")) assertResource("textures/" + texturePath + ".png.mcmeta");
                        }
                    }
                }
            }
        }
        for (String skin : new String[]{"yellow", "orange"}) {
            String base = "models/block/canisters/" + skin + "/components/";
            for (String obj : new String[]{"shell.obj", "shell_mask.obj"}) {
                String path = base + obj;
                String contents = readText(path);
                Matcher libraries = Pattern.compile("(?m)^\\s*mtllib\\s+(\\S+)").matcher(contents);
                while (libraries.find()) {
                    String mtl = base + libraries.group(1);
                    String material = readText(mtl);
                    Matcher textures = Pattern.compile("(?m)^\\s*map_Kd\\s+(\\S+)").matcher(material);
                    while (textures.find()) {
                        if (!textures.group(1).startsWith("#"))
                            assertResource("textures/block/canisters/" + textures.group(1));
                    }
                }
            }
        }
    }

    @Test void everyRegisteredNativeModelIdResolvesWithoutAnExtraModelsPrefix() throws Exception {
        for (var id : GasCanisterRenderer.additionalModels()) {
            String path = "models/" + id.id().getPath() + ".json";
            assertResource(path);
        }
        assertResource("models/item/gas_canister_proxy.json");
        var proxy = read("models/item/gas_canister_proxy.json");
        assertResource("models/" + proxy.get("parent").getAsString().substring("moonstation14:".length()) + ".json");
    }

    private static void assertResource(String path) {
        try (var stream = GasCanisterRenderPlanTest.class.getResourceAsStream(ASSET_ROOT + path)) {
            assertNotNull(stream, "Missing renderer dependency " + path);
        } catch (IOException exception) { throw new AssertionError(exception); }
    }

    private static String readText(String path) throws IOException {
        try (var stream = GasCanisterRenderPlanTest.class.getResourceAsStream(ASSET_ROOT + path)) {
            assertNotNull(stream, "Missing renderer dependency " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static com.google.gson.JsonObject read(String path) throws IOException {
        return com.google.gson.JsonParser.parseString(readText(path)).getAsJsonObject();
    }
    private static String name(GasCanisterRenderPlan plan, int index) {
        String path = plan.components().get(index).model().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
    private static GasCanisterProfile profile(String skin, boolean locked) {
        return new GasCanisterProfile(1, 293.15, Map.of(GasType.PLASMA, 1d), 101.325, 10.1325, 1013.25, skin, locked);
    }
}
