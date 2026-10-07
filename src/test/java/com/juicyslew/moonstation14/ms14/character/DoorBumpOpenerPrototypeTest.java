package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.DoorBumpOpenerComponent;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DoorBumpOpenerPrototypeTest {
    @Test void humanResolvedProfileOptsInAndRemovingMarkerOptsOut() throws Exception {
        var resource = getClass().getResourceAsStream("/data/moonstation14/moonstation14/character/human.json");
        var json = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
        var human = CharacterData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(human.component(DoorBumpOpenerComponent.class).isPresent());

        var noMarker = json.deepCopy();
        var components = noMarker.getAsJsonArray("components");
        for (int i=components.size()-1;i>=0;i--) {
            if ("DoorBumpOpener".equals(components.get(i).getAsJsonObject().get("type").getAsString())) components.remove(i);
        }
        var inert = CharacterData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, noMarker).getOrThrow();
        assertTrue(inert.component(DoorBumpOpenerComponent.class).isEmpty());
    }
}
