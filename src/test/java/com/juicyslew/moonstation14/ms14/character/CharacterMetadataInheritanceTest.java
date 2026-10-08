package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CharacterMetadataInheritanceTest {
    @Test
    void rootMetadataInheritsPerFieldAndTypedDataKeepsAllOtherComposition() {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        raw.put(id("base"), obj("{\"abstract\":true,\"metadata\":{\"name\":\"observer\",\"description\":\"Boo!\"},\"components\":[{\"type\":\"Examiner\",\"skip_checks\":true}] }"));
        raw.put(id("child"), obj("{\"parent\":\"base\",\"metadata\":{\"description\":\"A child observer\"}}"));
        JsonObject result = PrototypeResolver.resolve(raw, new CharacterComponentMergeStrategy()).get(id("child"));
        CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE, result).getOrThrow();
        assertEquals(new ExamineMetadata("observer", "A child observer"), data.metadata());
        assertEquals(1, data.components().size());
        assertEquals(data.metadata(), data.withComponents(List.of()).metadata());
        assertEquals(data.metadata(), data.withMetadata(data.metadata()).metadata());
    }

    private static JsonObject obj(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
