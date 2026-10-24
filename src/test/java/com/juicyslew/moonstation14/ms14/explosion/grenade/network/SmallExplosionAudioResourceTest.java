package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class SmallExplosionAudioResourceTest {
    @Test void sourceClipsArePackagedByteFaithfullyAndRegistered() throws Exception {
        String[] names = {"explosion_small1.ogg", "explosion_small2.ogg", "explosion_small3.ogg", "explosionsmallfar.ogg"};
        String[] hashes = {"c353217a4dee62a786f0e0c2f955037fed0a206700917fe77ad3980bafeb7ffa", "c981e49efd4a9eaae9116220f3e3f4440ae7951f059edfb3235dfd0eead6d458", "a859dfd298f61f99a31c55c835c92b9b7dd63214e4be920faeae2faa821ebc2f", "bb21a6d4b2de72737457ad59318d70d87494afc0667d9d4ec7b107b285a1b59e"};
        for (int i=0;i<names.length;i++) try(var in=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds/ss14/effects/"+names[i])) {
            assertNotNull(in, names[i]); assertEquals(hashes[i], java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
            var json=JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(3,json.getAsJsonObject("ss14/effects/explosion_small").getAsJsonArray("sounds").size());
            assertEquals("moonstation14:ss14/effects/explosionsmallfar",json.getAsJsonObject("ss14/effects/explosion_small_far").getAsJsonArray("sounds").get(0).getAsString());
        }
    }
}
