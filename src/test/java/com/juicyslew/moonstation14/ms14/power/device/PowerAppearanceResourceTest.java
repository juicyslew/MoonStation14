package com.juicyslew.moonstation14.ms14.power.device;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PowerAppearanceResourceTest {
    @Test void nativeAppearanceModelsAndAnimatedTextureSchedulesHaveCompleteClosure() throws Exception {
        for(String block:new String[]{"hv_source","hv_mv_substation","apc"}) {
            var blockstate=resource("/assets/moonstation14/blockstates/"+block+".json");
            var variants=blockstate.getAsJsonObject("variants");
            assertTrue(variants.has("facing=north") || variants.has("facing=north,display=full"));
            for(String suffix:new String[]{"","_charging","_lack","_unpowered","_dead"}) {
                String model=block+suffix;
                if(!exists("/assets/moonstation14/models/block/"+model+".json")) continue;
                var json=resource("/assets/moonstation14/models/block/"+model+".json");
                for(var texture:json.getAsJsonObject("textures").entrySet())
                    if(texture.getValue().getAsString().startsWith("moonstation14:"))
                        assertTrue(exists("/assets/moonstation14/textures/"+texture.getValue().getAsString().substring("moonstation14:".length())+".png"));
            }
        }
        var apc=resource("/assets/moonstation14/blockstates/apc.json").getAsJsonObject("variants");
        for (String facing : new String[]{"north","east","south","west"}) {
            assertEquals("moonstation14:block/apc", apc.get("facing="+facing+",display=full").getAsJsonObject().get("model").getAsString());
            assertEquals("moonstation14:block/apc_charging", apc.get("facing="+facing+",display=charging").getAsJsonObject().get("model").getAsString());
            assertEquals("moonstation14:block/apc_lack", apc.get("facing="+facing+",display=lack").getAsJsonObject().get("model").getAsString());
            for (String fallback : new String[]{"off","unpowered","dead","on","fixed_on"})
                assertTrue(apc.has("facing="+facing+",display="+fallback));
        }
        for(String texture:new String[]{"apc_charging","apc_lack","hv_mv_substation","hv_mv_substation_charging"})
            assertTrue(exists("/assets/moonstation14/textures/block/"+texture+".png.mcmeta"),texture);
        var state=resource("/assets/moonstation14/blockstates/apc.json");
        var parts=state.getAsJsonArray("multipart");
        assertEquals(12,parts.size());
        for (var part : parts) {
            var when=part.getAsJsonObject().getAsJsonObject("when");
            if (when.has("display") && (when.get("display").getAsString().contains("full")
                    || when.get("display").getAsString().contains("charging")))
                assertFalse(when.has("glow"), "Full and Charging overlays are selected independently of GLOW");
            String model=part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString();
            assertTrue(exists("/assets/moonstation14/models/"+model.substring("moonstation14:".length())+".json"),model);
        }
        for(String model:new String[]{"apc_screen_glow","apc_charging_screen_glow","apc_lack_screen_glow"}) {
            var json=resource("/assets/moonstation14/models/block/"+model+".json");
            var element=json.getAsJsonArray("elements").get(0).getAsJsonObject();
            assertFalse(element.get("shade").getAsBoolean());
            var faces=element.getAsJsonObject("faces");
            assertEquals(1,faces.size(),"emissive overlay only covers screen front");
            var data=faces.getAsJsonObject("north").getAsJsonObject("neoforge_data");
            assertEquals(15,data.get("block_light").getAsInt());
            assertEquals(15,data.get("sky_light").getAsInt());
            assertFalse(data.get("ambient_occlusion").getAsBoolean());
        }
    }
    private static com.google.gson.JsonObject resource(String path) throws Exception {
        try(var in=PowerAppearanceResourceTest.class.getResourceAsStream(path)) { assertNotNull(in,path); return JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject(); }
    }
    private static boolean exists(String path) { return PowerAppearanceResourceTest.class.getResource(path)!=null; }
}
