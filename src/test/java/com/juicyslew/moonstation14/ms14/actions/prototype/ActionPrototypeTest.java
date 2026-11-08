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
        for (String name : java.util.List.of("base_action", "base_mental_action", "combat_mode_toggle", "scream", "debug_pulse", "toggle_internals", "toggle_mask")) {
            try (var stream = getClass().getResourceAsStream("/data/moonstation14/moonstation14/character_action/" + name + ".json")) {
                assertNotNull(stream, name);
                raw.put(id(name), JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new AssertionError(e); }
        }
        manager.reload(ModActionPrototypes.TYPE, raw);
        var catalog = manager.snapshot(ModActionPrototypes.TYPE);
        assertEquals(5, catalog.size());
        var mental = catalog.get(id("combat_mode_toggle"));
        assertFalse(mental.checkCanInteract()); assertFalse(mental.checkConsciousness());
        assertEquals(ActionPrototype.Kind.TOGGLE, mental.kind()); assertEquals(-100, mental.priority());
        var scream = catalog.get(id("scream")); assertFalse(scream.checkCanInteract()); assertTrue(scream.checkConsciousness()); assertEquals(200, scream.useDelayTicks());
        var debug = catalog.get(id("debug_pulse")); assertTrue(debug.checkCanInteract()); assertTrue(debug.checkConsciousness()); assertEquals(20, debug.useDelayTicks());
        var internals = catalog.get(id("toggle_internals"));
        assertEquals(ActionHandler.INTERNALS_TOGGLE, internals.handler()); assertEquals(20, internals.useDelayTicks());
        assertEquals("textures/interface/actions/internals_off.png", internals.icon().getPath());
        assertEquals("textures/interface/actions/internals_on.png", internals.iconOn().orElseThrow().getPath());
        var mask = catalog.get(id("toggle_mask")); assertEquals(ActionHandler.MASK_TOGGLE, mask.handler());
        assertEquals("textures/interface/actions/mask_on.png", mask.icon().getPath());
    }
    @Test void rejectsInvalidActionFields() {
        assertTrue(ActionPrototype.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("{\"display_name\":\"x\",\"icon\":\"moonstation14:x\",\"handler\":\"NOPE\"}")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> ActionPrototype.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("{\"display_name\":\"x\",\"icon\":\"moonstation14:x\",\"handler\":\"SCREAM\",\"use_delay_ticks\":-1}")));
        assertTrue(ActionPrototype.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("{\"display_name\":\"x\",\"icon\":\"moonstation14:x\",\"handler\":\"SCREAM\",\"use_delay_ticks\":1.5}")).error().isPresent());
    }
    @Test void dedicatedActionAssetsHaveAdjacentProvenanceAndSoundMapping() throws Exception {
        for (String icon : java.util.List.of("internals_off.png", "internals_on.png", "internals_low.png", "mask_on.png", "mask_off.png")) {
            String base = "/assets/moonstation14/textures/interface/actions/" + icon;
            try (var png = getClass().getResourceAsStream(base);
                 var attribution = getClass().getResourceAsStream(base + ".attribution.txt")) {
                assertNotNull(png, icon); assertNotNull(attribution, icon);
                assertTrue(new String(attribution.readAllBytes(), StandardCharsets.UTF_8).contains("c9df5ef5d675b0d1d226828bddf6b78c28502d91"));
            }
        }
        try (var sound = getClass().getResourceAsStream("/assets/moonstation14/sounds/internals.ogg");
             var attribution = getClass().getResourceAsStream("/assets/moonstation14/sounds/internals.ogg.attribution.txt")) {
            assertNotNull(sound); assertNotNull(attribution);
            assertTrue(new String(attribution.readAllBytes(), StandardCharsets.UTF_8).contains("CC-BY-SA-3.0"));
        }
    }
}
