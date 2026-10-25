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
        raw.put(ModToolProfiles.KITCHEN_KNIFE, resource("kitchen_knife"));
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
          assertEquals(7, catalog.asMap().size());
        assertNull(catalog.get(ModToolProfiles.BASE_ITEM));
        assertNull(catalog.get(ModToolProfiles.BASE_CROWBAR));
        assertEquals(java.util.List.of(ToolProfile.Quality.PRYING), catalog.get(ModToolProfiles.CROWBAR).tool().qualities());
        assertEquals(1.0, catalog.get(ModToolProfiles.CROWBAR).prying().speedModifier());
        assertEquals(java.util.List.of(ToolProfile.Quality.CUTTING), catalog.get(ModToolProfiles.WIRECUTTER).tool().qualities());
        assertEquals("moonstation14:tools/wirecutter", catalog.get(ModToolProfiles.WIRECUTTER).useSound());
        assertNotNull(catalog.get(ModToolProfiles.WIRECUTTER).botanySampleTaker());
        assertNull(catalog.get(ModToolProfiles.WIRECUTTER).sharp());
        assertFalse(ToolQualities.isSharp(ModToolProfiles.WIRECUTTER, catalog));
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

    @Test void optionalBotanySwabCapabilityDefaultsAndSurvivesResolvedProfileLookup() {
        var raw = bundled();
        raw.get(ModToolProfiles.BOTANY_HOE).add("botanySwab", new JsonObject());
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath("test", "swab_base");
        ResourceLocation child = ResourceLocation.fromNamespaceAndPath("test", "swab_child");
        JsonObject baseJson = new JsonObject();
        baseJson.addProperty("parent", ModToolProfiles.BOTANY_HOE.toString());
        baseJson.add("botanySwab", new JsonObject());
        raw.put(base, baseJson);
        JsonObject childJson = new JsonObject();
        childJson.addProperty("parent", base.toString());
        raw.put(child, childJson);
        var manager = manager();
        manager.reload(ModToolProfiles.TYPE, raw);
        var resolved = manager.snapshot(ModToolProfiles.TYPE).get(ModToolProfiles.BOTANY_HOE);
        assertNotNull(resolved.botanySwab());
        assertEquals(2.0, resolved.botanySwab().delaySeconds());
        assertEquals(2.0, ToolProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.Gson().toJsonTree(java.util.Map.of("botanySwab", java.util.Map.of())))
                .result().orElseThrow().botanySwab().delaySeconds());
        assertEquals(2.0, manager.snapshot(ModToolProfiles.TYPE).get(child).botanySwab().delaySeconds());
        assertNull(manager.snapshot(ModToolProfiles.TYPE).get(ModToolProfiles.BOTANY_SPADE).botanySwab());
        assertNull(manager.snapshot(ModToolProfiles.TYPE).get(ModToolProfiles.BOTANY_SPADE).botanySampleTaker());
    }

    @Test void explicitSharpMarkerIsInheritedWithoutEquatingCuttingQuality() {
        var raw = bundled();
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath("test", "sharp_base");
        ResourceLocation child = ResourceLocation.fromNamespaceAndPath("test", "sharp_child");
        JsonObject baseJson = new JsonObject(); baseJson.addProperty("parent", ModToolProfiles.BOTANY_HOE.toString());
        baseJson.add("sharp", new JsonObject()); raw.put(base, baseJson);
        JsonObject childJson = new JsonObject(); childJson.addProperty("parent", base.toString()); raw.put(child, childJson);
        var manager = manager(); manager.reload(ModToolProfiles.TYPE, raw);
        var catalog = manager.snapshot(ModToolProfiles.TYPE);
        assertTrue(ToolQualities.isSharp(child, catalog));
        assertFalse(ToolQualities.isSharp(ModToolProfiles.WIRECUTTER, catalog));
        assertFalse(ToolQualities.isSharp(ModToolProfiles.BOTANY_HOE, catalog));
        var productionKnife = catalog.get(ModToolProfiles.KITCHEN_KNIFE);
        assertNotNull(productionKnife);
        assertNotNull(productionKnife.sharp());
        assertNotNull(productionKnife.knife());
        assertNull(productionKnife.tool());
    }

    @Test void sharpDelayModifierDefaultsToOneInheritsAndRejectsInvalidValues() {
        var raw = bundled();
        ResourceLocation parent = ResourceLocation.fromNamespaceAndPath("test", "sharp_modifier_parent");
        ResourceLocation child = ResourceLocation.fromNamespaceAndPath("test", "sharp_modifier_child");
        JsonObject base = new JsonObject(); base.addProperty("parent", ModToolProfiles.BOTANY_HOE.toString());
        JsonObject sharp = new JsonObject(); sharp.addProperty("butcherDelayModifier", .5); base.add("sharp", sharp); raw.put(parent, base);
        JsonObject childJson = new JsonObject(); childJson.addProperty("parent", parent.toString()); raw.put(child, childJson);
        var manager = manager(); manager.reload(ModToolProfiles.TYPE, raw);
        assertEquals(.5, manager.snapshot(ModToolProfiles.TYPE).get(child).sharp().butcherDelayModifier());
        var defaults = new JsonObject(); defaults.add("sharp", new JsonObject());
        assertEquals(1.0, ToolProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, defaults).result().orElseThrow().sharp().butcherDelayModifier());
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            var invalid = bundled(); var marker = new JsonObject(); marker.addProperty("butcherDelayModifier", value);
            invalid.get(ModToolProfiles.KITCHEN_KNIFE).add("sharp", marker);
            assertThrows(PrototypeLoadException.class, () -> manager().reload(ModToolProfiles.TYPE, invalid));
        }
    }

    @Test void knifeCapabilityIsIndependentAndInheritedSeparatelyFromSharpAndCutting() {
        var raw = bundled();
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath("test", "knife_base");
        ResourceLocation child = ResourceLocation.fromNamespaceAndPath("test", "knife_child");
        JsonObject baseJson = new JsonObject(); baseJson.addProperty("parent", ModToolProfiles.BOTANY_HOE.toString());
        baseJson.add("knife", new JsonObject()); raw.put(base, baseJson);
        JsonObject childJson = new JsonObject(); childJson.addProperty("parent", base.toString()); raw.put(child, childJson);
        var manager = manager(); manager.reload(ModToolProfiles.TYPE, raw);
        var catalog = manager.snapshot(ModToolProfiles.TYPE);
        assertNotNull(catalog.get(child).knife());
        assertNull(catalog.get(child).sharp());
        assertNull(catalog.get(child).tool());
        assertNull(catalog.get(ModToolProfiles.WIRECUTTER).knife());
    }
}
