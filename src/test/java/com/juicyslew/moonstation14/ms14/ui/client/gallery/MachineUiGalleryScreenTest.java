package com.juicyslew.moonstation14.ms14.ui.client.gallery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MachineUiGalleryScreenTest {
    @Test
    void galleryKeepsItsOverlayAndWidgetsWithoutScreenBackgroundBlur() throws IOException {
        String source = gallerySource();
        assertTrue(source.matches("(?s).*@Override\\s+public void renderBackground\\(GuiGraphics graphics, int mouseX, int mouseY, float partialTick\\)\\s*\\{\\s*//[^\\n]*\\s*}.*"),
                "The gallery must suppress Screen.renderBackground's blur pass");
        int overlay = source.indexOf("graphics.fill(0, 0, width, height, 0xbb000000);");
        int window = source.indexOf("MachineWindowRenderer.window(graphics");
        int widgets = source.indexOf("super.render(graphics, mouseX, mouseY, partialTick);");
        assertTrue(overlay >= 0 && overlay < window && window < widgets,
                "Keep the dim overlay and window before Screen renders widgets");
    }

    private static String gallerySource() throws IOException {
        // NeoForge runs tests from a game directory below the project root.
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path source = directory.resolve("src/main/java/com/juicyslew/moonstation14/ms14/ui/client/gallery/MachineUiGalleryScreen.java");
            if (Files.isRegularFile(source)) return Files.readString(source);
        }
        throw new IOException("Gallery source not found above test working directory");
    }
}
