package com.juicyslew.moonstation14.resource;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposalPipeMaterialResourceTest {
    @Test
    void atlasIsOpaqueNativePaletteAndHasDarkUnderside() throws IOException {
        BufferedImage atlas = read("/assets/moonstation14/textures/block/disposal/pipe_material.png");
        BufferedImage source = read("/assets/moonstation14/textures/block/disposal/pipe-s_frame0.png");
        assertEquals(16, atlas.getWidth());
        assertEquals(8, atlas.getHeight());

        Set<Integer> opaqueSourceColors = new HashSet<>();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                if ((pixel >>> 24) == 255) opaqueSourceColors.add(pixel & 0x00ffffff);
            }
        }

        long metalLuminance = 0;
        long darkLuminance = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = atlas.getRGB(x, y);
                assertEquals(255, pixel >>> 24, "atlas pixels must be opaque");
                assertTrue(opaqueSourceColors.contains(pixel & 0x00ffffff), "color must come from native opaque palette");
                int luminance = (((pixel >>> 16) & 255) * 299 + ((pixel >>> 8) & 255) * 587 + (pixel & 255) * 114) / 1000;
                if (x < 8) metalLuminance += luminance;
                else darkLuminance += luminance;
            }
        }
        assertTrue(darkLuminance / 64.0 < metalLuminance / 64.0,
                "right-hand underside patch must be darker than left metal patch");
    }

    private static BufferedImage read(String resource) throws IOException {
        try (var stream = DisposalPipeMaterialResourceTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new AssertionError("Missing resource " + resource);
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new AssertionError("Invalid PNG " + resource);
            return image;
        }
    }
}
