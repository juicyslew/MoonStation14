package com.juicyslew.moonstation14.ms14.singularity;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class SingularitySoundProfileTest {
    @Test void resolvedCompositionCarriesBothAmbientSourcesAndComponentDissipationDefault() {
        var sounds = SingularitySoundProfile.DEFAULT;
        assertEquals("moonstation14:singularity/ambient", sounds.ambient().toString());
        assertEquals(-4f, sounds.ambientDb());
        assertEquals(14f, sounds.ambientRange());
        assertEquals("moonstation14:singularity/component_ambient", sounds.componentAmbient().toString());
        assertEquals(5f, sounds.componentAmbientDb());
        assertEquals(20f, sounds.componentAmbientRange());
        assertNull(sounds.formation());
        assertEquals("moonstation14:singularity/dissipate", sounds.dissipation().toString());
        assertEquals(Math.pow(10, -4d / 20), SingularitySoundProfile.gain(-4), 1e-6);
        assertEquals(Math.pow(10, 5d / 20), SingularitySoundProfile.gain(5), 1e-6);
    }

    @Test void inheritedResolvedProfileRetainsComposedSoundSection() throws Exception {
        JsonObject root;
        try (var input = getClass().getResourceAsStream("/data/moonstation14/singularity_gameplay/singularity.json")) {
            assertNotNull(input);
            root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "base"), root);
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "child"), JsonParser.parseString("{\"parent\":\"moonstation14:base\"}").getAsJsonObject());
        var resolved = PrototypeResolver.resolve(raw).get(ResourceLocation.fromNamespaceAndPath("moonstation14", "child"));
        var profile = SingularityGameplayProfile.CODEC.parse(JsonOps.INSTANCE, resolved).getOrThrow(message -> new AssertionError(message));
        assertEquals(SingularitySoundProfile.DEFAULT.ambient(), profile.sounds().ambient());
        assertEquals(SingularitySoundProfile.DEFAULT.componentAmbient(), profile.sounds().componentAmbient());
        assertEquals(14f, profile.sounds().ambientRange());
        assertEquals(20f, profile.sounds().componentAmbientRange());
    }
}
