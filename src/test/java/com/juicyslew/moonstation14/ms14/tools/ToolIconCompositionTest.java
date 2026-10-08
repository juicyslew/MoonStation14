package com.juicyslew.moonstation14.ms14.tools;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.io.InputStream;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ToolIconCompositionTest {
    private static java.awt.image.BufferedImage image(String name) throws Exception {
        try (InputStream input = Objects.requireNonNull(ToolIconCompositionTest.class.getResourceAsStream(
                "/assets/moonstation14/textures/item/" + name + ".png"))) {
            return ImageIO.read(input);
        }
    }

    private static void verifiesComposition(String baseName, String overlayName, String resultName) throws Exception {
        var base = image(baseName);
        var overlay = image(overlayName);
        var actual = image(resultName);
        assertEquals(32, actual.getWidth());
        assertEquals(32, actual.getHeight());
        var expected = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = expected.createGraphics();
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.drawImage(base, 0, 0, null);
        graphics.drawImage(overlay, 0, 0, null);
        graphics.dispose();
        boolean grayOverlayPixel = false;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel " + x + "," + y);
            int p = overlay.getRGB(x, y), alpha = p >>> 24;
            int r = p >>> 16 & 255, g = p >>> 8 & 255, b = p & 255;
            grayOverlayPixel |= alpha != 0 && Math.abs(r - g) < 8 && Math.abs(g - b) < 8;
        }
        assertTrue(grayOverlayPixel, "overlay must contain visible grey working parts");
    }

    @Test void screwdriverKeepsScrewBitsOverBase() throws Exception {
        verifiesComposition("source/screwdriver", "source/screwdriver-screwybits", "airlock_screwdriver");
    }

    @Test void wirecutterKeepsCuttingHeadsOverBaseAndBoundTexture() throws Exception {
        verifiesComposition("source/wirecutter", "source/wirecutter-overlay", "wirecutter");
    }
}
