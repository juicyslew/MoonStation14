package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.LinkedHashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SeedProfileTest {
    @Test void resolvedCatalogInheritsDefaultsAndChemistryAndRuntimeExposesSnapshot() {
        var runtime = PrototypeRuntime.serverManager();
        Map<ResourceLocation, com.google.gson.JsonObject> profiles = loadProfiles();
        runtime.reload(ModSeedProfiles.TYPE, profiles);
        PrototypeRuntime.clientManager().reload(ModSeedProfiles.TYPE, profiles);
        var wheat = ModSeedProfiles.serverCatalog().get(id("wheat"));
        assertEquals(100, wheat.biology().endurance());
        assertEquals(0.4f, wheat.biology().nutrientConsumption());
        assertEquals(6, wheat.lifecycle().growthStages());
        assertEquals(1.25f, wheat.chemicals().get(id("nutriment")).amount(5));
        assertEquals(20, wheat.chemicals().get(id("nutriment")).amount(1000));
        assertTrue(ModSeedProfiles.serverReady());
        assertSame(wheat, ModSeedProfiles.serverLookup(id("wheat")));
        assertTrue(ModSeedProfiles.clientReady());
        assertEquals(6, ModSeedProfiles.clientLookup(id("wheat")).lifecycle().growthStages());
        Map<String, Integer> stages = Map.of("wheat", 6, "tomato", 6, "potato", 4, "poppy", 3, "aloe", 5);
        for (var entry : stages.entrySet()) {
            var profile = ModSeedProfiles.serverLookup(id(entry.getKey()));
            assertEquals(100, profile.biology().endurance());
            assertEquals(entry.getValue(), profile.lifecycle().growthStages());
            assertFalse(profile.chemicals().isEmpty());
        }
        assertEquals("wheat_bushel", wheat.product().getPath());
        assertEquals(id("wheat_seeds"), wheat.packet());
        assertEquals("tomato", ModSeedProfiles.serverLookup(id("tomato")).product().getPath());
        assertEquals("potato", ModSeedProfiles.serverLookup(id("potato")).product().getPath());
        assertEquals("poppy", ModSeedProfiles.serverLookup(id("poppy")).product().getPath());
        assertEquals("aloe", ModSeedProfiles.serverLookup(id("aloe")).product().getPath());
        for (String crop : new String[]{"wheat", "tomato", "potato", "poppy", "aloe"})
            assertEquals(id(crop + "_seeds"), ModSeedProfiles.serverLookup(id(crop)).packet(), crop + " packet prototype ID");
    }
    @Test void abstractZeroMaturationIsAllowedButConcreteZeroAndNegativeAreRejected() {
        var profiles = loadProfiles();
        PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE, profiles);
        assertEquals(0, PrototypeRuntime.serverManager().snapshot(ModSeedProfiles.TYPE).get(id("defaults")).lifecycle().maturation());
        profiles.get(id("wheat")).getAsJsonObject("lifecycle").addProperty("maturation", 0);
        assertThrows(PrototypeLoadException.class, () -> PrototypeRuntime.serverManager().reload(ModSeedProfiles.TYPE, profiles));
        assertThrows(IllegalArgumentException.class, () -> new com.juicyslew.moonstation14.ms14.botany.SeedProfile.Lifecycle(
                6, 1, -1, 1, 1, 1, com.juicyslew.moonstation14.ms14.botany.SeedProfile.Harvest.NO_REPEAT));
    }
    private static Map<ResourceLocation, com.google.gson.JsonObject> loadProfiles() {
        Map<ResourceLocation, com.google.gson.JsonObject> profiles = new LinkedHashMap<>();
        for (String name : new String[]{"defaults", "wheat", "tomato", "potato", "poppy", "aloe"}) {
            String path = "data/moonstation14/moonstation14/seed_profile/" + name + ".json";
            try (var stream = SeedProfileTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                profiles.put(id(name), com.google.gson.JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        return profiles;
    }
    private static ResourceLocation id(String id) { return ResourceLocation.fromNamespaceAndPath("moonstation14", id); }
}
