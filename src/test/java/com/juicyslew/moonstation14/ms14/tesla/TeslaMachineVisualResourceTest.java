package com.juicyslew.moonstation14.ms14.tesla;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class TeslaMachineVisualResourceTest {
    @Test void machineStateBindsNativeCompleteHitModelsAndTheirTextures() throws Exception {
        check("tesla_coil", "moonstation14:block/power_collection/generation_tesla_coil/default",
                "moonstation14:block/power_collection/generation_tesla_coil/coilhit",
                "assets/moonstation14/textures/block/power_collection/generation_tesla_coil/coilhit.png");
        check("tesla_grounding_rod", "moonstation14:block/power_collection/generation_tesla_coil__teslagroundingrod/default",
                "moonstation14:block/power_collection/generation_tesla_coil__teslagroundingrod/grounding_rodhit",
                "assets/moonstation14/textures/block/power_collection/generation_tesla_coil__teslagroundingrod/grounding_rodhit.png");
    }

    private static void check(String block, String idleModel, String hitModel, String hitTexture) throws Exception {
        var json=JsonParser.parseReader(new java.io.InputStreamReader(resource("assets/moonstation14/blockstates/"+block+".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var variants=json.getAsJsonObject("variants");
        assertEquals(idleModel,variants.getAsJsonObject("hit=false").get("model").getAsString());
        assertEquals(hitModel,variants.getAsJsonObject("hit=true").get("model").getAsString());
        String modelPath="assets/"+hitModel.substring(0,hitModel.indexOf(":")).replace(':','/')+"/models/"+hitModel.substring(hitModel.indexOf(':')+1)+".json";
        var model=JsonParser.parseReader(new java.io.InputStreamReader(resource(modelPath), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(hitTexture.substring("assets/moonstation14/textures/block/".length(),hitTexture.length()-4),
                model.getAsJsonObject("textures").get("atlas").getAsString().substring("moonstation14:block/".length()));
        assertNotNull(resource(hitTexture));
    }
    private static java.io.InputStream resource(String name) {
        var stream=TeslaMachineVisualResourceTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream,name);
        return stream;
    }
}
