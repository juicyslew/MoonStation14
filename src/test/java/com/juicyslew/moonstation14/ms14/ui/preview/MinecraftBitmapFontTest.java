package com.juicyslew.moonstation14.ms14.ui.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftBitmapFontTest {
    private static final Path RESOURCES = locateResources();

    private static Path locateResources() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");
            if (java.nio.file.Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        return Path.of("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");
    }

    @Test void usesDevelopmentJarResourcesAndMinecraftBitmapAdvanceFormula() throws Exception {
        var font = MinecraftBitmapFont.load(RESOURCES);
        assertEquals(4, font.advance(' '));
        assertEquals(0, font.advance(0x200c));
        assertEquals(6, font.advance('A')); // trimmed 5-pixel ASCII glyph, plus Minecraft's 1px advance.
        assertTrue(font.manifest().contains("minecraft:include/default#bitmap:minecraft:font/nonlatin_european.png"));
        assertTrue(font.manifest().contains("minecraft:include/default#bitmap:minecraft:font/accented.png"));
        assertTrue(font.manifest().contains("minecraft:include/default#bitmap:minecraft:font/ascii.png"));
        for (String core : new String[]{"ChemMaster 4000", "Pending…", "No reagents in container",
                "Airlock — Wires", "Pill and bottle production is not implemented.", "12.34u"}) {
            assertTrue(font.width(core) > 0, "unhandled codepoint in core UI string: " + core);
            var glyphImage = new BufferedImage(Math.max(1, font.width(core)), 20, BufferedImage.TYPE_INT_ARGB);
            int cursor = 0;
            for (int cp : core.codePoints().toArray()) {
                font.draw(glyphImage, cursor, 12, cp, 0xffeeeeee);
                cursor += (int) font.advance(cp);
            }
            assertTrue(java.util.Arrays.stream(glyphImage.getRGB(0, 0, glyphImage.getWidth(), glyphImage.getHeight(), null, 0,
                    glyphImage.getWidth())).anyMatch(pixel -> pixel != 0), "real bitmap raster missing: " + core);
        }

        var first = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var second = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        font.draw(first, 2, 9, 'A', 0xffeeeeee);
        font.draw(second, 2, 9, 'A', 0xffeeeeee);
        assertArrayEquals(first.getRGB(0, 0, 16, 16, null, 0, 16), second.getRGB(0, 0, 16, 16, null, 0, 16));
        assertTrue(java.util.Arrays.stream(first.getRGB(0, 0, 16, 16, null, 0, 16)).anyMatch(pixel -> pixel != 0));
        assertThrows(UnsupportedOperationException.class, () -> font.draw(first, 0, 8, 0x10ffff, 0xffffffff));
    }
}
