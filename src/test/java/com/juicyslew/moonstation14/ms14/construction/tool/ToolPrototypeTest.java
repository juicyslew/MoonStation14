package com.juicyslew.moonstation14.ms14.construction.tool;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolPrototypeTest {
    @Test
    void resolvedToolsInheritSupportedCapabilityDefaultsAndExposeOnlyTheirCapabilities() {
        JsonObject toolBase = load("base_tool");
        assertFalse(toolBase.has("useSound"));
        assertFalse(toolBase.has("smallItem"));
        assertFalse(toolBase.has("dynamicPhysics"));
        assertFalse(toolBase.has("anchored"));
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModTools.TOOL_TYPE);
        Map<net.minecraft.resources.ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        raw.put(ModTools.BASE, load("base_tool"));
        raw.put(ModTools.WRENCH, load("wrench"));
        raw.put(ModTools.SCREWDRIVER, load("screwdriver"));
        raw.put(ModTools.WELDER, load("welder"));
        raw.put(ModTools.CROWBAR, load("crowbar"));
        manager.reload(ModTools.TOOL_TYPE, raw);

        var catalog = manager.snapshot(ModTools.TOOL_TYPE);
        ToolPrototypeData wrench = catalog.get(ModTools.WRENCH);
        ToolPrototypeData screwdriver = catalog.get(ModTools.SCREWDRIVER);
        ToolPrototypeData crowbar = catalog.get(ModTools.CROWBAR);
        assertEquals(ToolPrototypeData.UseSound.Wrench, wrench.useSound().orElseThrow());
        assertEquals(ToolPrototypeData.UseSound.Screwdriver, screwdriver.useSound().orElseThrow());
        assertEquals(ToolPrototypeData.UseSound.Crowbar, crowbar.useSound().orElseThrow());
        assertEquals(ToolPrototypeData.UseSound.Welder, catalog.get(ModTools.WELDER).useSound().orElseThrow());
        assertEquals(new ToolPrototypeData(Set.of(ToolPrototypeData.Quality.Prying), 1.0, java.util.Optional.empty(), java.util.Optional.of(ToolPrototypeData.UseSound.Crowbar)), crowbar);
        assertTrue(ModTools.canPryConstructionStep(catalog, ModTools.CROWBAR));
        assertFalse(ModTools.canPryConstructionStep(catalog, ModTools.WRENCH));
        assertFalse(ModTools.canPryConstructionStep(catalog, ModTools.id("unsupported_item")));
        assertFalse(ModTools.canPryConstructionStep(catalog, net.minecraft.world.item.ItemStack.EMPTY));
        assertFalse(ModTools.canPryConstructionStep(catalog,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)));
        assertTrue(ModTools.itemPrototype(java.util.Optional.empty()).isEmpty());
        assertTrue(ModTools.itemPrototype(null).isEmpty());
        assertTrue(ModTools.canPryConstructionStep(catalog,
                new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.CROWBAR.get())));
         assertEquals(new ToolPrototypeData(Set.of(ToolPrototypeData.Quality.Anchoring), 1.0, java.util.Optional.empty(), java.util.Optional.of(ToolPrototypeData.UseSound.Wrench)), wrench);
         assertEquals(new ToolPrototypeData(Set.of(ToolPrototypeData.Quality.Screwing), 1.0, java.util.Optional.empty(), java.util.Optional.of(ToolPrototypeData.UseSound.Screwdriver)), screwdriver);
        assertTrue(ModTools.hasQuality(catalog, ModTools.WRENCH, ToolPrototypeData.Quality.Anchoring));
        assertFalse(ModTools.hasQuality(catalog, ModTools.WRENCH, ToolPrototypeData.Quality.Screwing));
        assertTrue(ModTools.hasQuality(catalog, ModTools.SCREWDRIVER, ToolPrototypeData.Quality.Screwing));
        assertEquals(1.0, ModTools.speedModifier(catalog, ModTools.WRENCH, ToolPrototypeData.Quality.Anchoring).orElseThrow());
        assertTrue(ModTools.speedModifier(catalog, ModTools.WRENCH, ToolPrototypeData.Quality.Screwing).isEmpty());

        ToolPrototypeData welder = catalog.get(ModTools.WELDER);
        assertEquals(Set.of(ToolPrototypeData.Quality.Welding), welder.qualities());
        var composition = welder.welder().orElseThrow();
        assertEquals(new ToolPrototypeData.Welder(100, 0.5, 1), composition);
        var encoded = ToolPrototypeData.CODEC.encodeStart(JsonOps.INSTANCE, welder).result().orElseThrow();
        assertEquals(welder, ToolPrototypeData.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        WelderFuelData initial = WelderFuelData.initial(composition);
        WelderFuelData insufficient = new WelderFuelData(0.49, false, -1);
        assertTrue(insufficient.toggle(composition, 0).isEmpty());
        assertEquals(0.49, insufficient.fuel());
        WelderFuelData lit = initial.toggle(composition, 0).orElseThrow();
        assertEquals(99.5, lit.fuel());
        assertTrue(ModTools.hasQuality(catalog, ModTools.WELDER, ToolPrototypeData.Quality.Welding, lit));
        assertFalse(ModTools.hasQuality(catalog, ModTools.WELDER, ToolPrototypeData.Quality.Welding));
        WelderFuelData moved = lit.tick(composition, 40);
        assertEquals(97.5, moved.fuel());
        assertEquals(moved, moved.tick(composition, 40));
        WelderFuelData exhausted = new WelderFuelData(1, true, 40).tick(composition, 100);
        assertEquals(0, exhausted.fuel());
        assertFalse(exhausted.lit());
        assertTrue(exhausted.toggle(composition, 100).isEmpty());
    }

    private static JsonObject load(String name) {
        String path = "/data/moonstation14/moonstation14/construction_tool/" + name + ".json";
        try (var stream = ToolPrototypeTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("missing tool prototype resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("failed reading " + path, exception);
        }
    }
}
