package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.entities.client.SingularityAmbientPolicy;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SingularityAudioResourceTest {
    @Test void newlyRegisteredAudioEventsResolveEverySampleAndAmbientGainIsNotDuplicated() throws Exception {
        JsonObject sounds;
        try (var input = getClass().getResourceAsStream("/assets/moonstation14/sounds.json")) {
            assertNotNull(input);
            sounds = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (String event : new String[]{"singularity/ambient", "singularity/component_ambient", "singularity/form",
                "singularity/dissipate", "emitter/fire", "item_slot_insert", "item_slot_eject", "gas_valve_toggle"}) {
            assertTrue(sounds.has(event), "missing event " + event);
            for (JsonElement sample : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
                String sampleName = sample.isJsonObject() ? sample.getAsJsonObject().get("name").getAsString() : sample.getAsString();
                String path = sampleName.substring(sampleName.indexOf(':') + 1);
                assertNotNull(getClass().getResource("/assets/moonstation14/sounds/" + path + ".ogg"), "missing sample " + sampleName);
            }
        }
        assertFalse(sounds.getAsJsonObject("singularity/ambient").getAsJsonArray("sounds").get(0).getAsJsonObject().has("volume"));
        assertFalse(sounds.getAsJsonObject("singularity/component_ambient").getAsJsonArray("sounds").get(0).getAsJsonObject().has("volume"));
        assertNotNull(getClass().getResource("/assets/moonstation14/sounds/chemistry/chemmaster/revolver_magin.ogg"));
        assertNotNull(getClass().getResource("/assets/moonstation14/sounds/chemistry/chemmaster/revolver_magout.ogg"));
    }

    @Test void ambientSourcesStartAndStopIndependentlyByResolvedRangeAndLifecycle() {
        assertTrue(SingularityAmbientPolicy.shouldPlay(true, true, true, 14 * 14, 14));
        assertFalse(SingularityAmbientPolicy.shouldPlay(true, true, true, 14.1 * 14.1, 14));
        assertTrue(SingularityAmbientPolicy.shouldPlay(true, true, true, 19 * 19, 20));
        assertFalse(SingularityAmbientPolicy.shouldPlay(false, true, true, 1, 20));
        assertFalse(SingularityAmbientPolicy.shouldPlay(true, false, true, 1, 20));
        assertFalse(SingularityAmbientPolicy.shouldPlay(true, true, false, 1, 20));
    }

    @Test void teslaLoopLifecycleReplacesStoppedOrChangedLoopsWithoutDuplicatingLiveLoops() {
        var action = SingularityAmbientPolicy.loopAction(true, false, false, false);
        assertEquals(SingularityAmbientPolicy.LoopAction.CREATE, action);
        // Leaving range stops the active instance; re-entry must allocate a fresh one.
        action = SingularityAmbientPolicy.loopAction(true, true, true, true);
        assertEquals(SingularityAmbientPolicy.LoopAction.REPLACE, action);
        // A live in-range loop is retained, not replayed as a second voice.
        assertEquals(SingularityAmbientPolicy.LoopAction.REUSE,
                SingularityAmbientPolicy.loopAction(true, true, false, true));
        assertEquals(SingularityAmbientPolicy.LoopAction.REPLACE,
                SingularityAmbientPolicy.loopAction(true, true, false, false));
        assertEquals(SingularityAmbientPolicy.LoopAction.DISCARD,
                SingularityAmbientPolicy.loopAction(false, true, false, true));
    }
}
