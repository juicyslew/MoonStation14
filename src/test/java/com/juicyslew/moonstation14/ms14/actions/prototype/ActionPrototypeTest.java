package com.juicyslew.moonstation14.ms14.actions.prototype;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionPrototypeTest {
    private ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("moonstation14", name); }
    @Test void bundledFamilyResolvesThroughRealManagerAndPreservesDefaults() {
        PrototypeManager manager = new PrototypeManager(); manager.register(ModActionPrototypes.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : java.util.List.of("base_action", "base_mental_action", "combat_mode_toggle", "scream", "debug_pulse")) {
            try (var stream = getClass().getResourceAsStream("/data/moonstation14/moonstation14/character_action/" + name + ".json")) {
                assertNotNull(stream, name);
                raw.put(id(name), JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        manager.reload(ModActionPrototypes.TYPE, raw);
        var catalog = manager.snapshot(ModActionPrototypes.TYPE);
        assertEquals(3, catalog.size());
        var mental = catalog.get(id("combat_mode_toggle"));
        assertFalse(mental.checkCanInteract()); assertFalse(mental.checkConsciousness());
        assertEquals(ActionPrototype.Kind.TOGGLE, mental.kind()); assertEquals(-100, mental.priority());
        var scream = catalog.get(id("scream")); assertFalse(scream.checkCanInteract()); assertTrue(scream.checkConsciousness()); assertEquals(200, scream.useDelayTicks());
        var debug = catalog.get(id("debug_pulse")); assertTrue(debug.checkCanInteract()); assertTrue(debug.checkConsciousness()); assertEquals(20, debug.useDelayTicks());
    }
    @Test void rejectsInvalidActionFields() {
        assertTrue(ActionPrototype.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("{\"display_name\":\"x\",\"icon\":\"moonstation14:x\",\"handler\":\"NOPE\"}")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> ActionPrototype.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("{\"display_name\":\"x\",\"icon\":\"moonstation14:x\",\"handler\":\"SCREAM\",\"use_delay_ticks\":-1}")));
        assertTrue(ActionPrototype.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("{\"display_name\":\"x\",\"icon\":\"moonstation14:x\",\"handler\":\"SCREAM\",\"use_delay_ticks\":1.5}")).error().isPresent());
    }
}
