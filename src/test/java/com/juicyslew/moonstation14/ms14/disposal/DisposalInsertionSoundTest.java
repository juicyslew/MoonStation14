package com.juicyslew.moonstation14.ms14.disposal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DisposalInsertionSoundTest {
    private static JsonObject json(String id) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                DisposalInsertionSoundTest.class.getResourceAsStream("/data/moonstation14/moonstation14/disposal_entity/" + id + ".json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static Map<ResourceLocation, JsonObject> catalog() {
        var result = new LinkedHashMap<ResourceLocation, JsonObject>();
        result.put(ModDisposals.BASE, json("disposal_tube_base"));
        for (String id : List.of("base_structure", "base_machine", "base_machine_powered", "disposal_unit_base", "disposal_unit"))
            result.put(ModDisposals.id(id), json(id));
        result.put(ModDisposals.HOLDER, json("disposal_holder"));
        for (String id : List.of("disposal_pipe", "disposal_bend", "disposal_trunk", "disposal_junction", "disposal_junction_flipped", "disposal_y_junction", "disposal_x_junction", "disposal_tagger", "disposal_router", "disposal_router_flipped"))
            result.put(ModDisposals.id(id), json(id));
        return result;
    }

    private static PrototypeManager resolve(Map<ResourceLocation, JsonObject> raw) {
        var manager = new PrototypeManager();
        manager.register(ModDisposals.DISPOSAL_TYPE);
        manager.reload(ModDisposals.DISPOSAL_TYPE, raw);
        return manager;
    }

    @Test void unitInsertionAudioDefaultResolvesThroughParentAndChildMayOverride() {
        var resolved = resolve(catalog()).snapshot(ModDisposals.DISPOSAL_TYPE);
        assertEquals("moonstation14:disposal/insert", resolved.get(ModDisposals.UNIT).unit().insertionSound());
        assertEquals("moonstation14:disposal/insert",
                resolved.get(ModDisposals.UNIT).unit().insertionSound());

        var overridden = catalog();
        overridden.get(ModDisposals.UNIT).add("unit", json("disposal_unit_base").getAsJsonObject("unit").deepCopy());
        overridden.get(ModDisposals.UNIT).getAsJsonObject("unit").addProperty("insertionSound", "moonstation14:custom/insert");
        var childValue = resolve(overridden).snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit().insertionSound();
        assertEquals("moonstation14:custom/insert", childValue);
        assertNotEquals(childValue, resolved.get(ModDisposals.UNIT).unit().insertionSound());

        var malformed = catalog();
        malformed.get(ModDisposals.id("disposal_unit_base")).getAsJsonObject("unit").addProperty("insertionSound", "not a resource id");
        assertThrows(RuntimeException.class, () -> resolve(malformed), "malformed resource IDs must fail prototype resolution");
    }

    @Test void resolvedSoundSelectsRegisteredSampleAndImportedBytesMatchPinnedClip() throws Exception {
        var config = resolve(catalog()).snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit();
        var soundId = ResourceLocation.parse(config.insertionSound());
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "disposal/insert"), soundId);

        var root = Objects.requireNonNull(DisposalInsertionSoundTest.class.getResourceAsStream("/assets/moonstation14/sounds.json"));
        JsonObject sounds;
        try (var reader = new InputStreamReader(root, StandardCharsets.UTF_8)) { sounds = JsonParser.parseReader(reader).getAsJsonObject(); }
        assertTrue(sounds.has(soundId.getPath()));
        assertEquals(soundId.toString(), sounds.getAsJsonObject(soundId.getPath()).getAsJsonArray("sounds").get(0).getAsString());

        byte[] clip;
        try (var in = Objects.requireNonNull(DisposalInsertionSoundTest.class.getResourceAsStream("/assets/moonstation14/sounds/disposal/insert.ogg"))) { clip = in.readAllBytes(); }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(clip));
        assertEquals("77adcfb6ba63abf4e4646a26a922f26a9ce5509c5ab6d468df41996fa31fbcc2", hash);
    }
}
