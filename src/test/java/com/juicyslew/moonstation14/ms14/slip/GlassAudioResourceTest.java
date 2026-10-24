package com.juicyslew.moonstation14.ms14.slip;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class GlassAudioResourceTest {
    @Test void sourceClipsArePackagedByteFaithfullyAndRegistered() throws Exception {
        String[] names = {"glass_break1.ogg", "glass_break2.ogg", "glass_break3.ogg", "glass_break4.ogg", "glass_step.ogg"};
        String[] hashes = {"46ed09dfe7039a0d1f9cd77fdbad090b2636bd673e0ff3421ec593dd75cd7dac", "c73d09d35fc3abb992c2cf6142b032aaf5c2c22085e6b47c2ff1ad04be3e335d", "f58a6896431fedc535bda8530ae8975d73bffac883777bba6b637dcf6fd027a6", "c9da55a5dd74664b86047bb253a880da915a909bae927a1d923c5e5722864f32", "7010e89b4b519e206cbb0bf4918a743d7e06af41a034e569aa5654331c8927ed"};
        for (int i=0;i<names.length;i++) try(var in=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds/ss14/effects/"+names[i])) {
            assertNotNull(in, names[i]); assertEquals(hashes[i], java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
            var json=JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var broken=json.getAsJsonObject("ss14/effects/glass_break").getAsJsonArray("sounds");
            assertEquals(4, broken.size());
            for (int i=0;i<4;i++) {
                var sample=broken.get(i).getAsJsonObject();
                assertEquals("moonstation14:ss14/effects/glass_break"+(i+1),sample.get("name").getAsString());
                assertEquals(1,sample.get("weight").getAsInt());
            }
            assertEquals("moonstation14:ss14/effects/glass_step",json.getAsJsonObject("ss14/effects/glass_step").getAsJsonArray("sounds").get(0).getAsString());
        }
    }
}
