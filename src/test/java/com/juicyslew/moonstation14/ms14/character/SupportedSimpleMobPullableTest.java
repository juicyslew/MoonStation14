package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.PullableComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SupportedSimpleMobPullableTest {
    @Test
    void resolvedMouseAndOtherSimpleMobsAreEligibleForTheSharedPullConsumer() throws Exception {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(
                id("supported_simple_mob"), read("supported_simple_mob"),
                id("supported_animal"), read("supported_animal"),
                id("supported_npc"), read("supported_npc"),
                id("pig"), read("pig"), id("mouse"), read("mouse")));

        var resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        // The shared parent chain (supported_simple_mob -> supported_animal/npc) is abstract;
        // pig and mouse are its current concrete resolved descendants.
        for (String supported : new String[] {"pig", "mouse"})
            assertTrue(resolved.get(id(supported)).component(PullableComponent.class).isPresent(),
                    supported + " resolves inherited Pullable consumed by BodyPullService.eligibleTarget");
        assertTrue(resolved.get(id("mouse")).component(
                        com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).isEmpty(),
                "pullable target composition does not grant the mouse hands or make it a puller");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("moonstation14:" + path); }

    private JsonObject read(String name) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/" + name + ".json");
        if (stream == null) throw new AssertionError("missing character prototype " + name);
        try (stream) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
