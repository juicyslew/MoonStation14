package com.juicyslew.moonstation14.ms14.audio;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GunItemModelClosureTest {
    private static final String MODELS = "assets/moonstation14/models/";
    private static final Set<String> SPENT = Set.of("cartridge_pistol", "cartridge_rifle", "cartridge_light_rifle", "cartridge_magnum", "shell_shotgun");
    private static final Set<String> ROOTS = Set.of("weapon_pistol_mk58", "weapon_rifle_lecter", "weapon_revolver_inspector", "weapon_shotgun_kammerer", "weapon_sniper_mosin", "weapon_laser_gun", "weapon_energy_shotgun", "weapon_laser_svalinn", "magazine_pistol", "magazine_rifle", "speedloader_magnum", "power_cell_small", "cartridge_pistol", "cartridge_rifle", "cartridge_light_rifle", "cartridge_magnum", "shell_shotgun");
    private static final Set<String> PROPERTIES = Set.of("moonstation14:cartridge_spent", "moonstation14:magazine_absent", "moonstation14:bolt_open", "moonstation14:ammo_fill", "moonstation14:charge", "moonstation14:cell_absent", "moonstation14:battery_mode", "moonstation14:solution_present");

    @Test void spentCartridgeModelsHaveNativeDistinctBodyAndNoTip() throws Exception {
        for (String item : SPENT) {
            var spent = model("item/" + item + "_spent");
            var live = model("item/" + item);
            var spentTextures = spent.getAsJsonObject("textures");
            assertNotNull(spentTextures, item);
            assertFalse(spentTextures.has("layer1"), item + " spent tip");
            String body = spentTextures.get("layer0").getAsString();
            assertNotEquals(live.getAsJsonObject("textures").get("layer0").getAsString(), body, item);
            assertNativeTexture(body);
        }
    }

    @Test void selectedCartridgeLiveAndSpentModelTextureReferencesResolve() throws Exception {
        for (String item : SPENT) {
            for (String suffix : new String[]{"", "_spent"}) {
                var json = model("item/" + item + suffix);
                for (var entry : json.getAsJsonObject("textures").entrySet()) {
                    String value=entry.getValue().getAsString();
                    if (!value.startsWith("#")) assertNativeTexture(value);
                }
            }
        }
        var ap=model("item/cartridge_magnum_ap");
        assertEquals("moonstation14:item/cartridge_magnum_spent",ap.getAsJsonArray("overrides").get(0).getAsJsonObject().get("model").getAsString());
    }

    @Test void selectedRootModelReferencesResolveRecursively() throws Exception {
        Set<String> visited = new HashSet<>();
        Set<String> textures = new HashSet<>();
        for (String root : ROOTS) walk("moonstation14:item/" + root, new HashSet<>(), visited, textures);
        assertEquals(17, ROOTS.size());
    }

    @Test void lecterSelectsSourceStepsAtActualMagazineCounts() throws Exception {
        var root = model("item/weapon_rifle_lecter");
        int[] counts = {0,1,8,9,16,17,24,25};
        int[] states = {0,1,1,2,2,3,3,4};
        for (int i=0;i<counts.length;i++) {
            float fill=counts[i]/25f;
            String expected=states[i] == 4 ? "moonstation14:item/guns/weapon_rifle_lecter"
                    : "moonstation14:item/guns/weapon_rifle_lecter__mag_" + states[i] + "_bolt_closed";
            assertEquals(expected, selected(root, Map.of("moonstation14:ammo_fill",fill)), "rounds="+counts[i]);
            String openExpected = states[i] == 4 ? "moonstation14:item/guns/weapon_rifle_lecter__mag_4_bolt_open"
                    : expected.replace("bolt_closed", "bolt_open");
            assertEquals(openExpected, selected(root, Map.of("moonstation14:ammo_fill",fill,"moonstation14:bolt_open",1f)), "open rounds="+counts[i]);
        }
        assertEquals("moonstation14:item/guns/weapon_rifle_lecter__no_mag_bolt_closed", selected(root, Map.of("moonstation14:magazine_absent",1f)));
        assertEquals("moonstation14:item/guns/weapon_rifle_lecter__no_mag_bolt_open", selected(root, Map.of("moonstation14:magazine_absent",1f,"moonstation14:bolt_open",1f)));
    }

    @Test void rifleMagazineOnlyShowsSourceIndicatorWhenFull() throws Exception {
        var root=model("item/magazine_rifle");
        assertEquals("moonstation14:item/guns/magazine_rifle__not_full", selected(root, Map.of("moonstation14:ammo_fill",0f)));
        assertEquals("moonstation14:item/guns/magazine_rifle__not_full", selected(root, Map.of("moonstation14:ammo_fill",.5f)));
        assertEquals("moonstation14:item/guns/magazine_rifle__not_full", selected(root, Map.of("moonstation14:ammo_fill",.96f)));
        assertEquals("moonstation14:item/guns/magazine_rifle", selected(root, Map.of("moonstation14:ammo_fill",1f)));
    }

    @Test void mk58OverridesSelectSourceMagazineAndBoltVariants() throws Exception {
        var root = model("item/weapon_pistol_mk58");
        assertEquals("moonstation14:item/weapon_pistol_mk58", selected(root, Map.of()));
        assertEquals("moonstation14:item/guns/weapon_pistol_mk58__loaded_bolt_open", selected(root, Map.of("moonstation14:bolt_open", 1f)));
        assertEquals("moonstation14:item/guns/weapon_pistol_mk58__no_mag_bolt_closed", selected(root, Map.of("moonstation14:magazine_absent", 1f)));
        assertEquals("moonstation14:item/guns/weapon_pistol_mk58__no_mag_bolt_open", selected(root, Map.of("moonstation14:bolt_open", 1f, "moonstation14:magazine_absent", 1f)));
        for (String name : Set.of("weapon_pistol_mk58", "weapon_pistol_mk58__loaded_bolt_open",
                "weapon_pistol_mk58__no_mag_bolt_closed", "weapon_pistol_mk58__no_mag_bolt_open")) {
            var variant = model("item/guns/" + name);
            assertEquals("neoforge:obj", variant.get("loader").getAsString(), name);
            assertTrue(variant.has("model"), name);
            assertTrue(variant.getAsJsonObject("textures").has("atlas"), name);
        }
    }

    @Test void pistolMagazineOverridesMatchRoundedSourceLevels() throws Exception {
        var root = model("item/magazine_pistol");
        assertEquals("moonstation14:item/guns/magazine_pistol", selected(root, Map.of("moonstation14:ammo_fill", 1f)));
        assertEquals("moonstation14:item/guns/magazine_pistol__empty", selected(root, Map.of("moonstation14:ammo_fill", 0f)));
        assertEquals("moonstation14:item/guns/magazine_pistol__level_1", selected(root, Map.of("moonstation14:ammo_fill", .2f)));
        assertEquals("moonstation14:item/guns/magazine_pistol__level_2", selected(root, Map.of("moonstation14:ammo_fill", .3f)));
        assertEquals("moonstation14:item/guns/magazine_pistol__level_3", selected(root, Map.of("moonstation14:ammo_fill", .6f)));
        assertEquals("moonstation14:item/guns/magazine_pistol__level_4", selected(root, Map.of("moonstation14:ammo_fill", .8f)));
    }

    @Test void retroLaserUsesAmmoFillForFiveSourceChargeSteps() throws Exception {
        var root = model("item/weapon_laser_gun");
        int[] counts = {0, 1, 5, 6, 10, 11, 15, 16};
        int[] states = {0, 1, 1, 2, 2, 3, 3, 4};
        for (int i = 0; i < counts.length; i++) {
            float fill = counts[i] / 16f;
            String selected = selected(root, Map.of("moonstation14:ammo_fill", fill));
            String expected = "moonstation14:item/guns/weapon_laser_gun_charge" + states[i];
            assertEquals(expected, selected, "shots=" + counts[i]);
        }
    }

    @Test void energyWeaponsSelectArtistChargeStepsAndSvalinnAbsenceWins() throws Exception {
        for (String item : Set.of("weapon_energy_shotgun", "weapon_laser_svalinn")) {
            var root = model("item/" + item);
            float[] fills = {0, .1f, .4f, .7f, 1};
            for (int level = 0; level <= 4; level++) {
                assertEquals("moonstation14:item/guns/" + item + "_charge" + level,
                        selected(root, Map.of("moonstation14:ammo_fill", fills[level])), item + " level=" + level);
            }
            if (item.equals("weapon_laser_svalinn"))
                assertEquals("moonstation14:item/guns/weapon_laser_svalinn_cell_absent",
                        selected(root, Map.of("moonstation14:ammo_fill", 0f, "moonstation14:cell_absent", 1f)));
        }
    }

    @Test void smallCellUsesRawBatteryThresholdsAndSpeedloaderUsesSevenFillLevels() throws Exception {
        var cell = model("item/power_cell_small");
        assertEquals("moonstation14:item/guns/power_cell_small_empty", selected(cell, Map.of("moonstation14:charge", 0f)));
        assertEquals("moonstation14:item/guns/power_cell_small_partial", selected(cell, Map.of("moonstation14:charge", .5f)));
        assertEquals("moonstation14:item/guns/power_cell_small_full", selected(cell, Map.of("moonstation14:charge", 1f)));
        var speedloader = model("item/speedloader_magnum");
        for (int rounds = 0; rounds <= 6; rounds++) {
            String expected = rounds == 6 ? "moonstation14:item/guns/speedloader_magnum"
                    : "moonstation14:item/guns/speedloader_magnum_fill_" + rounds;
            assertEquals(expected, selected(speedloader, Map.of("moonstation14:ammo_fill", rounds / 6f)), "rounds=" + rounds);
        }
    }

    private String selected(com.google.gson.JsonObject model, Map<String, Float> properties) {
        String baseTexture = model.has("model") ? model.get("model").getAsString()
                : model.has("parent") ? model.get("parent").getAsString()
                : model.getAsJsonObject("textures").has("layer0") ? model.getAsJsonObject("textures").get("layer0").getAsString()
                : model.getAsJsonObject("textures").get("atlas").getAsString();
        String selected = baseTexture.contains("weapon_pistol_mk58")
                ? "moonstation14:item/weapon_pistol_mk58" : baseTexture.contains("weapon_rifle_lecter")
                ? "moonstation14:item/weapon_rifle_lecter" : baseTexture.contains("magazine_rifle")
                ? "moonstation14:item/magazine_rifle" : baseTexture.contains("magazine_pistol")
                ? "moonstation14:item/magazine_pistol" : baseTexture.contains("weapon_laser_gun")
                ? "moonstation14:item/weapon_laser_gun" : baseTexture.contains("weapon_energy_shotgun")
                ? "moonstation14:item/weapon_energy_shotgun" : baseTexture.contains("weapon_laser_svalinn")
                ? "moonstation14:item/weapon_laser_svalinn" : baseTexture.contains("power_cell_small")
                ? "moonstation14:item/power_cell_small" : baseTexture.contains("speedloader_magnum")
                ? "moonstation14:item/speedloader_magnum" : baseTexture.startsWith("moonstation14:ss14/guns/weapon_pistol_mk58")
                ? "moonstation14:item/weapon_pistol_mk58" : baseTexture.startsWith("moonstation14:ss14/guns/weapon_rifle_lecter")
                ? "moonstation14:item/weapon_rifle_lecter" : baseTexture.endsWith("weapon_laser_gun")
                ? "moonstation14:item/weapon_laser_gun" : baseTexture.endsWith("magazine_rifle_base")
                ? "moonstation14:item/magazine_rifle" : "moonstation14:item/magazine_pistol";
        // Vanilla applies matching overrides in array order; the last match wins.
        if (model.has("overrides")) for (var entry : model.getAsJsonArray("overrides")) {
            var o = entry.getAsJsonObject(); boolean matches = true;
            for (var p : o.getAsJsonObject("predicate").entrySet())
                matches &= properties.getOrDefault(p.getKey(), 0f) >= p.getValue().getAsFloat();
            if (matches) selected = o.getAsJsonObject().get("model").getAsString();
        }
        return selected;
    }

    private void walk(String id, Set<String> stack, Set<String> visited, Set<String> textures) throws Exception {
        if (!id.startsWith("moonstation14:")) return;
        assertFalse(stack.contains(id), "model cycle: " + stack + " -> " + id);
        if (visited.contains(id)) return;
        stack.add(id);
        String name = ResourceLocation.parse(id).getPath();
        var json = model(name);
        if (json.has("parent")) walk(json.get("parent").getAsString(), stack, visited, textures);
        if (json.has("textures")) for (var entry : json.getAsJsonObject("textures").entrySet()) {
            String value = entry.getValue().getAsString();
            if (value.startsWith("#")) continue;
            if (value.startsWith("moonstation14:")) { assertNativeTexture(value); textures.add(value); }
        }
        if (json.has("overrides")) for (var override : json.getAsJsonArray("overrides")) {
            var predicate = override.getAsJsonObject().getAsJsonObject("predicate");
            for (var prop : predicate.entrySet()) assertTrue(PROPERTIES.contains(prop.getKey()), "unknown predicate property " + prop.getKey());
            walk(override.getAsJsonObject().get("model").getAsString(), stack, visited, textures);
        }
        stack.remove(id);
        visited.add(id);
    }

    private com.google.gson.JsonObject model(String name) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(MODELS + name + ".json")) {
            assertNotNull(in, name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private void assertNativeTexture(String value) {
        var id = ResourceLocation.parse(value);
        assertEquals(value, id.toString());
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png")) {
            assertNotNull(in, value);
        } catch (Exception e) { fail(e); }
    }
}
