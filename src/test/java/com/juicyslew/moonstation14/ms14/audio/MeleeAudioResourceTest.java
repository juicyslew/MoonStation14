package com.juicyslew.moonstation14.ms14.audio;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAudioResourceTest {
    @Test void sourceClipsArePackagedByteFaithfullyAndRegistered() throws Exception {
        String[] paths = {"weapons/punchmiss.ogg", "weapons/punch1.ogg", "weapons/punch2.ogg", "weapons/punch3.ogg", "weapons/punch4.ogg", "weapons/bladeslice.ogg", "effects/weak_hit1.ogg", "effects/weak_hit2.ogg", "effects/lightburn.ogg", "effects/thudswoosh.ogg", "voice/diona/diona_salute.ogg"};
        String[] hashes = {"9110492d82854066de37453f677c66a65069f6e0cc30b96a0b50b5e02142f6b8", "00811f91c966be1c921529dcf3c716020013d1799aa06abfab2bb5c992a9dd19", "0a917e95c118b008df756138a560b4933e39040dc8d143496e1ab3f2bbdb7744", "fedcaccf3c4a6335d3875f3e13238093441f441f6af3e85f4489bbe1215f33ae", "ccb72b0740e455644f8adf4dc2138a8699545c2bb7de43575c3898346cf0d114", "db21312f6df87e93972accc4133f84414c25a352507265aab380677b53f16f93", "435ce810a89797020ef71bf53bba3d3e78a641dcc2f52c045617c0391b39a2e5", "f5bf3cd1b183608e277fcbca3d0c033371b9d7de6ba7b5c13fb6517b90a44915", "6d807fe1250dc4dfdf7c07d35f1bb410b0774321616486ba037609cdb28fb0ac", "519b7a4693681a2a62a061ac6f437897b0710a5dbf629cfe6ac50f87a2335c56", "42c4acc2986e450f95c81a82536c2caae4020915d406b61091284d02a09b4e14"};
        for (int i=0;i<paths.length;i++) try(var in=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds/ss14/"+paths[i])) {
            assertNotNull(in, paths[i]); assertEquals(hashes[i], java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
            var json=JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var punch=json.getAsJsonObject("ss14/weapons/punch").getAsJsonArray("sounds"); assertEquals(4,punch.size());
            for(int i=0;i<4;i++){var sample=punch.get(i).getAsJsonObject();assertEquals("moonstation14:ss14/weapons/punch"+(i+1),sample.get("name").getAsString());assertEquals(1,sample.get("weight").getAsInt());}
            var weak=json.getAsJsonObject("ss14/effects/weak_hit").getAsJsonArray("sounds");assertEquals(2,weak.size());
            for(int i=0;i<2;i++){var sample=weak.get(i).getAsJsonObject();assertEquals("moonstation14:ss14/effects/weak_hit"+(i+1),sample.get("name").getAsString());assertEquals(1,sample.get("weight").getAsInt());}
            assertEquals("moonstation14:ss14/weapons/punchmiss",json.getAsJsonObject("ss14/weapons/punch_miss").getAsJsonArray("sounds").get(0).getAsString());
            assertEquals("moonstation14:ss14/weapons/bladeslice",json.getAsJsonObject("ss14/weapons/blade_slice").getAsJsonArray("sounds").get(0).getAsString());
            assertEquals("moonstation14:ss14/effects/lightburn",json.getAsJsonObject("ss14/effects/light_burn").getAsJsonArray("sounds").get(0).getAsString());
            assertEquals("moonstation14:ss14/effects/thudswoosh",json.getAsJsonObject("ss14/effects/thudswoosh").getAsJsonArray("sounds").get(0).getAsString());
            assertEquals("moonstation14:ss14/voice/diona/diona_salute",json.getAsJsonObject("ss14/voice/diona/salute").getAsJsonArray("sounds").get(0).getAsString());
        }
    }
}
