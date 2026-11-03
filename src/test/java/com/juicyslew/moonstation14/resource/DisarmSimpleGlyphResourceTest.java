package com.juicyslew.moonstation14.resource;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class DisarmSimpleGlyphResourceTest {
    @Test
    void disarmHandIsSingleSimpleWhiteGlyphOnTransparentCanvas() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/moonstation14/textures/effects/melee/disarm_hand.png")) {
            assertNotNull(stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(32, image.getWidth());
            assertEquals(32, image.getHeight());
            int opaque = 0;
            int minX = 32, minY = 32, maxX = -1, maxY = -1;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                opaque++;
                assertEquals(0xffffffff, argb, "glyph must be flat white");
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
            assertTrue(opaque >= 35 && opaque <= 512);
            boolean[][] visited = new boolean[32][32];
            int components = 0;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                if ((image.getRGB(x, y) >>> 24) == 0 || visited[y][x]) continue;
                components++;
                var pending = new java.util.ArrayDeque<int[]>();
                pending.add(new int[]{x, y}); visited[y][x] = true;
                while (!pending.isEmpty()) {
                    int[] pixel = pending.removeFirst();
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        int nx = pixel[0] + dx, ny = pixel[1] + dy;
                        if (nx < 0 || nx >= 32 || ny < 0 || ny >= 32 || visited[ny][nx]
                                || (image.getRGB(nx, ny) >>> 24) == 0) continue;
                        visited[ny][nx] = true; pending.addLast(new int[]{nx, ny});
                    }
                }
            }
            assertEquals(1, components, "glyph silhouette must be one connected component");
            assertTrue(minX >= 2 && maxX <= 29 && minY >= 2 && maxY <= 29);
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertEquals(0, image.getRGB(31, 31) >>> 24);
        }
    }
}
