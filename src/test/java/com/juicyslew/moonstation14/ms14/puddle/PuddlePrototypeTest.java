package com.juicyslew.moonstation14.ms14.puddle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class PuddlePrototypeTest {
    private static JsonObject resource() throws Exception {
        try (var in = new InputStreamReader(PuddlePrototypeTest.class.getResourceAsStream(
                "/data/moonstation14/moonstation14/puddle/puddle.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }

    @Test void rootCompositionResolvesSourceDefaultsAndChildInheritance() throws Exception {
        JsonObject root = resource();
        assertFalse(root.has("parent"), "source ordinary puddle has no entity-prototype parent");
        var child = new JsonObject();
        child.addProperty("id", "small_puddle");
        child.addProperty("parent", "puddle");
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ModPuddles.PUDDLE, root);
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "small_puddle"), child);
        var manager = new PrototypeManager();
        manager.register(ModPuddles.TYPE);
        manager.reload(ModPuddles.TYPE, raw);
        var catalog = manager.snapshot(ModPuddles.TYPE);
        var resolved = catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "small_puddle"));
        assertEquals(1000f, resolved.solutionContainer().maxVolume());
        assertEquals("puddle", resolved.examinableSolution().solutionName());
        assertFalse(resolved.examinableSolution().exactVolume());
        assertEquals("Puddle", resolved.metadata().name());
        assertEquals("A puddle of liquid.", resolved.metadata().description());
        var changed = root.deepCopy();
        changed.getAsJsonObject("solutionContainer").addProperty("maxVolume", 500f);
        raw.put(ModPuddles.PUDDLE, changed);
        manager.reload(ModPuddles.TYPE, raw);
        assertEquals(500f, manager.snapshot(ModPuddles.TYPE).get(
                ResourceLocation.fromNamespaceAndPath("moonstation14", "small_puddle")).solutionContainer().maxVolume());
    }
}
