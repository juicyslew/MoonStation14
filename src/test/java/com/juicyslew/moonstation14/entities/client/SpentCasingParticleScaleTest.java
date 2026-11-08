package com.juicyslew.moonstation14.entities.client;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SpentCasingParticleScaleTest {
    @Test void bundledGenericProfileSetsFullCanvasAndOpaqueSpentCaseRemainsVisible() throws Exception {
        var loader=getClass().getClassLoader();
        var profileJson=JsonParser.parseReader(new InputStreamReader(loader.getResourceAsStream(
                "data/moonstation14/moonstation14/loose_object_profile/generic.json"),StandardCharsets.UTF_8));
        var resolved=LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,profileJson).getOrThrow();
        float half=SpentCasingParticleScale.halfExtent(resolved);
        assertEquals(.325f,half,1e-6f);
        try(var in=loader.getResourceAsStream("assets/moonstation14/textures/ss14/guns/cartridge_spent_body.png")){
            assertNotNull(in);var image=ImageIO.read(in);assertNotNull(image);int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)!=0){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
            assertTrue(maxX>=minX&&maxY>=minY,"spent case atlas image has opaque pixels");
            float opaqueWorldWidth=(maxX-minX+1)/(float)image.getWidth()*(2*half);
            assertTrue(opaqueWorldWidth>.03f,"opaque casing width should remain visibly larger than sub-centimeter");
        }
    }
}
