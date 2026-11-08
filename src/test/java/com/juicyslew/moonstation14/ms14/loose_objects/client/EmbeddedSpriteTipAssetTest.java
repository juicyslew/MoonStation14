package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddedSpriteTipAssetTest {
    @Test void authoredTipUvsMatchOpaqueLeadingTexelsInImportedIcons() throws Exception {
        verify("throwing_knife", .875f, .125f, 27, 4, true);
        verify("dart", .625f, .65625f, 19, 20, false);
    }

    private static void verify(String item, float u, float v, int x, int y, boolean knife) throws Exception {
        var json = read("/data/moonstation14/moonstation14/thrown_item_profile/" + item + ".json");
        var tip = json.getAsJsonObject("embedding").getAsJsonObject("sprite_tip_uv");
        assertEquals(u, tip.get("u").getAsFloat()); assertEquals(v, tip.get("v").getAsFloat());
        String path = "/assets/moonstation14/textures/ss14/" + (knife ? "throwing_knife" : "dart") + "/icon.png";
        var image = ImageIO.read(EmbeddedSpriteTipAssetTest.class.getResourceAsStream(path));
        assertNotNull(image); assertEquals(32, image.getWidth()); assertEquals(32, image.getHeight());
        assertTrue(((image.getRGB(x,y) >>> 24) & 255) > 0, "leading texel must be opaque");
        // The authored boundary must be within one texel of the leading opaque extent.
        int tipX = Math.min(31, (int)(u * 32)); int tipY = Math.min(31, (int)(v * 32));
        if (knife) { tipX--; } else { tipX--; tipY--; }
        assertTrue(((image.getRGB(tipX,tipY) >>> 24) & 255) > 0, "tip-side texel near authored boundary");
        float angle = json.getAsJsonObject("embedding").get("sprite_forward_degrees").getAsFloat();
        double radians = Math.toRadians(angle);
        double cos = Math.cos(radians), sin = Math.sin(radians);
        double configured = (u - .5) * cos + (.5 - v) * sin;
        double leading = Double.NEGATIVE_INFINITY;
        for (int py=0; py<32; py++) for (int px=0; px<32; px++)
            if (((image.getRGB(px,py) >>> 24)&255)>127) {
                double leadingU = (cos >= 0 ? px + 1 : px) / 32.0;
                double leadingV = (sin >= 0 ? py : py + 1) / 32.0;
                double projection = (leadingU - .5) * cos + (.5 - leadingV) * sin;
                leading = Math.max(leading, projection);
            }
        assertEquals(leading, configured, 1e-6, "authored tip must match opaque leading projection");
    }

    private static com.google.gson.JsonObject read(String path) throws Exception {
        try (var stream = EmbeddedSpriteTipAssetTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
