package com.juicyslew.moonstation14.ms14.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolProfileTest {
    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/tool_profile/" + name + ".json";
        try (var reader = new InputStreamReader(
                java.util.Objects.requireNonNull(ToolProfileTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<ResourceLocation, JsonObject> bundled() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        raw.put(ModToolProfiles.BASE_ITEM, resource("base_item"));
        raw.put(ModToolProfiles.BASE_CROWBAR, resource("base_crowbar"));
        raw.put(ModToolProfiles.CROWBAR, resource("crowbar"));
        raw.put(ModToolProfiles.WIRECUTTER, resource("wirecutter"));
        raw.put(ModToolProfiles.SCREWDRIVER, resource("screwdriver"));
        raw.put(ModToolProfiles.MULTITOOL, resource("multitool"));
        raw.put(ModToolProfiles.BOTANY_HOE, resource("botany_hoe"));
        raw.put(ModToolProfiles.BOTANY_SPADE, resource("botany_spade"));
        return raw;
    }

    private static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModToolProfiles.TYPE);
        return manager;
    }

    @Test void bundledProfilesResolveInheritanceAndConsumerCapabilities() {
        PrototypeManager manager = manager();
        manager.reload(ModToolProfiles.TYPE, bundled());
        var catalog = manager.snapshot(ModToolProfiles.TYPE);
        assertEquals(6, catalog.asMap().size());
        assertNull(catalog.get(ModToolProfiles.BASE_ITEM));
        assertNull(catalog.get(ModToolProfiles.BASE_CROWBAR));
        assertEquals(java.util.List.of(ToolProfile.Quality.PRYING), catalog.get(ModToolProfiles.CROWBAR).tool().qualities());
        assertEquals(1.0, catalog.get(ModToolProfiles.CROWBAR).prying().speedModifier());
        assertEquals(java.util.List.of(ToolProfile.Quality.CUTTING), catalog.get(ModToolProfiles.WIRECUTTER).tool().qualities());
        assertEquals("moonstation14:tools/wirecutter", catalog.get(ModToolProfiles.WIRECUTTER).useSound());
        assertEquals(java.util.List.of(ToolProfile.Quality.SCREWING), catalog.get(ModToolProfiles.SCREWDRIVER).tool().qualities());
        assertTrue(ToolQualities.has(ModToolProfiles.SCREWDRIVER, ToolProfile.Quality.SCREWING, catalog));
        assertNull(catalog.get(ModToolProfiles.SCREWDRIVER).useSound());
        assertEquals(java.util.List.of(ToolProfile.Quality.PULSING), catalog.get(ModToolProfiles.MULTITOOL).tool().qualities());

        assertTrue(ToolQualities.has(ModToolProfiles.CROWBAR, ToolProfile.Quality.PRYING, catalog));
        assertEquals(1.0, ToolQualities.prySpeed(ModToolProfiles.CROWBAR, catalog));
        assertFalse(ToolQualities.has(ModToolProfiles.CROWBAR, ToolProfile.Quality.CUTTING, catalog));
        assertFalse(ToolQualities.has(ResourceLocation.withDefaultNamespace("stone"), ToolProfile.Quality.PRYING, catalog));
        assertEquals(0, ToolQualities.prySpeed(ResourceLocation.withDefaultNamespace("stone"), catalog));
        assertEquals(0, ToolQualities.prySpeed(ModToolProfiles.WIRECUTTER, catalog));
    }

    @Test void onlyAcceptedCutAndMendSelectResolvedHeldToolSound() {
        var manager = manager();
        manager.reload(ModToolProfiles.TYPE, bundled());
        var catalog = manager.snapshot(ModToolProfiles.TYPE);
        assertEquals("moonstation14:tools/wirecutter", ToolQualities.acceptedWireActionSound(
                ModToolProfiles.WIRECUTTER, catalog, true, true));
        assertNull(ToolQualities.acceptedWireActionSound(ModToolProfiles.WIRECUTTER, catalog, false, true));
        assertNull(ToolQualities.acceptedWireActionSound(ModToolProfiles.WIRECUTTER, catalog, true, false));
        assertNull(ToolQualities.acceptedWireActionSound(ModToolProfiles.SCREWDRIVER, catalog, true, true));
        assertNull(ToolQualities.acceptedWireActionSound(ResourceLocation.withDefaultNamespace("stone"), catalog, true, true));
    }

    @Test void emptyOrInvalidCapabilitiesCannotPassCatalogValidation() {
        var raw = bundled();
        raw.get(ModToolProfiles.CROWBAR).getAsJsonObject("tool").getAsJsonArray("qualities").remove(0);
        var badQualities = raw;
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModToolProfiles.TYPE, badQualities));

        raw = bundled();
        raw.get(ModToolProfiles.BASE_CROWBAR).getAsJsonObject("prying").addProperty("speedModifier", -1);
        var badSpeed = raw;
        assertThrows(PrototypeLoadException.class, () -> manager().reload(ModToolProfiles.TYPE, badSpeed));
    }

    @Test void missingProfileCatalogFailsClosed() {
        var empty = manager().snapshot(ModToolProfiles.TYPE);
        assertFalse(ToolQualities.has(ModToolProfiles.CROWBAR, ToolProfile.Quality.PRYING, empty));
        assertEquals(0, ToolQualities.prySpeed(ModToolProfiles.CROWBAR, empty));
    }

    @Test void gardeningEligibilityUsesResolvedComponentsWithoutChangingLegacyToolCapabilities() {
        var manager = manager();
        manager.reload(ModToolProfiles.TYPE, bundled());
        var catalog = manager.snapshot(ModToolProfiles.TYPE);
        var hoe = catalog.get(ModToolProfiles.BOTANY_HOE);
        var spade = catalog.get(ModToolProfiles.BOTANY_SPADE);
        assertNotNull(hoe);
        assertTrue(hoe.tags().contains("Hoe"));
        assertTrue(hoe.tags().contains("BotanyHoe"));
        assertNull(hoe.shovel());
        assertNull(hoe.tool());
        assertTrue(ToolQualities.hasHoe(ModToolProfiles.BOTANY_HOE, catalog));
        assertFalse(ToolQualities.hasShovel(ModToolProfiles.BOTANY_HOE, catalog));
        assertFalse(ToolQualities.hasHoe(ModToolProfiles.BOTANY_SPADE, catalog));
        assertTrue(ToolQualities.hasShovel(ModToolProfiles.BOTANY_SPADE, catalog));
        assertEquals(.75, ToolQualities.shovelSpeed(ModToolProfiles.BOTANY_SPADE, catalog));
        assertEquals(java.util.Set.of(), catalog.get(ModToolProfiles.SCREWDRIVER).tags());
        assertNull(catalog.get(ModToolProfiles.SCREWDRIVER).shovel());
        assertTrue(ToolQualities.has(ModToolProfiles.SCREWDRIVER, ToolProfile.Quality.SCREWING, catalog));
        assertEquals(1.0, catalog.get(ModToolProfiles.CROWBAR).prying().speedModifier());
        assertFalse(ToolQualities.hasHoe(ResourceLocation.withDefaultNamespace("stone"), catalog));
        assertFalse(ToolQualities.hasShovel(ResourceLocation.withDefaultNamespace("stone"), catalog));
        assertEquals(0, ToolQualities.shovelSpeed(ResourceLocation.withDefaultNamespace("stone"), catalog));
    }
}
