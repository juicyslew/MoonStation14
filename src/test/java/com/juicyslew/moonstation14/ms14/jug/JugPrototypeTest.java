package com.juicyslew.moonstation14.ms14.jug;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class JugPrototypeTest {
    private static JsonObject load(String path) throws Exception {
        try (var in = new InputStreamReader(JugPrototypeTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(in).getAsJsonObject();
        }
    }

    @Test void resolvedInheritanceFeedsRuntimeCapacityProjection() throws Exception {
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ModJugs.BASE_ID, load("/data/moonstation14/moonstation14/jug/jug_base.json"));
        raw.put(ModJugs.JUG_ID, load("/data/moonstation14/moonstation14/jug/jug.json"));
        var childId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_jug");
        var child = new JsonObject();
        child.addProperty("id", "fixture_jug"); child.addProperty("parent", "jug");
        child.add("solutionContainer", JsonParser.parseString("{\"maxVolume\":500.0}"));
        raw.put(childId, child);

        var manager = new PrototypeManager(); manager.register(ModJugs.TYPE); manager.reload(ModJugs.TYPE, raw);
        var catalog = manager.snapshot(ModJugs.TYPE);
        var root = catalog.get(ModJugs.JUG_ID);
        assertEquals(200f, root.solutionContainer().maxVolume());
        assertEquals("drink", root.solutionContainer().solutionName());
        assertTrue(root.drainableSolution().enabled());
        assertEquals("drink", root.drainableSolution().solutionName());
        var policy = JugItem.drainableSolutionFromResolved(root);
        assertTrue(policy.enabled()); assertEquals("drink", policy.solutionName());
        assertEquals("drink", JugItem.primarySolutionNameFromResolved(root));
        assertTrue(root.examinableSolution().exactVolume());
        assertFalse(root.examinableSolution().heldOnly());
        assertTrue(root.examinableSolution().examinableWhileClosed());
        assertEquals(3, root.examinableSolution().maxDetails());
        assertEquals("jug", root.metadata().name());
        var resolvedChild = catalog.get(childId);
        assertEquals(500f, resolvedChild.solutionContainer().maxVolume());
        assertEquals(500f, JugItem.capacityFromResolved(resolvedChild),
                "resolved nondefault child capacity uses the same projection as JugItem getters");
        assertEquals(200f, JugItem.capacityFromResolved(root),
                "root prototype capacity uses the same runtime projection");
        var disabledId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_jug_drain_disabled");
        var disabled = new JsonObject(); disabled.addProperty("id", "fixture_jug_drain_disabled");
        disabled.addProperty("parent", "jug");
        disabled.add("drainableSolution", JsonParser.parseString("{\"enabled\":false}"));
        raw.put(disabledId, disabled);
        manager.reload(ModJugs.TYPE, raw);
        assertFalse(manager.snapshot(ModJugs.TYPE).get(disabledId).drainableSolution().enabled());
    }
}
