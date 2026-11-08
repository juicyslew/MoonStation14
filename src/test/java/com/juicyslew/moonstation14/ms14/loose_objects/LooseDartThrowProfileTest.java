package com.juicyslew.moonstation14.ms14.loose_objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LooseDartThrowProfileTest {
    @Test void resolvedDartsGetLongerLightThrowWithoutChangingOtherWeapons() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (String name : new String[]{"generic", "dart", "hypo_dart", "throwing_knife", "bola"})
            raw.put(id(name), read(name));
        var resolved = PrototypeResolver.resolve(raw, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());

        for (String name : new String[]{"dart", "hypo_dart"}) {
            var profile = profile(resolved, name);
            assertEquals(1.5f, profile.throwSpeed());
            assertEquals(16f, profile.throwRange());
        }
        var dart = profile(resolved, "dart");
        var hypo = profile(resolved, "hypo_dart");
        assertEquals(.4f, dart.visual().spriteScaleX());
        assertEquals(.4f, dart.visual().spriteScaleY());
        assertEquals(.8f, hypo.visual().spriteScaleX());
        assertEquals(.8f, hypo.visual().spriteScaleY());
        assertEquals(.4f, hypo.physicalWidth());
        assertEquals(.08f, hypo.physicalHeight());
        assertEquals(1.5f, hypo.throwSpeed());
        assertEquals(16f, hypo.throwRange());
        var generic = profile(resolved, "generic");
        assertEquals(.75f, generic.throwSpeed());
        assertEquals(8f, generic.throwRange());
        var knife = profile(resolved, "throwing_knife");
        assertEquals(1.25f, knife.throwSpeed());
        assertEquals(16f, knife.throwRange());
        var bola = profile(resolved, "bola");
        assertEquals(.75f, bola.throwSpeed());
        assertEquals(8f, bola.throwRange());
        assertEquals(1f, bola.throwSpin());
    }

    private static LooseObjectProfile profile(com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<JsonObject> values, String name) {
        return LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, values.get(id(name))).getOrThrow();
    }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("moonstation14", name); }
    private static JsonObject read(String name) {
        try (var stream = LooseDartThrowProfileTest.class.getResourceAsStream("/data/moonstation14/moonstation14/loose_object_profile/" + name + ".json")) {
            assertNotNull(stream, name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
