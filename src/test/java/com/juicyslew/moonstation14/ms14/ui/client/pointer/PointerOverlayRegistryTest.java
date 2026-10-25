package com.juicyslew.moonstation14.ms14.ui.client.pointer;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class PointerOverlayRegistryTest {
    @AfterEach void resetRegistry() { PointerOverlayRegistry.resetOwnedContext(); }

    @Test void registrationIsUniqueAndReversePaintOrderOwnsOverlappingClicks() {
        var back = new Window(); var front = new Window();
        back.visible = front.visible = true;
        PointerOverlayRegistry.register(back);
        PointerOverlayRegistry.register(back);
        PointerOverlayRegistry.register(front);

        assertEquals(List.of(back, front), PointerOverlayRegistry.paintOrder());
        assertTrue(PointerOverlayRegistry.click(5, 5, 0));
        assertEquals(0, back.clicks);
        assertEquals(1, front.clicks);
        assertEquals(List.of(back, front), PointerOverlayRegistry.paintOrder());
        PointerOverlayRegistry.unregister(front);
        assertTrue(PointerOverlayRegistry.click(5, 5, 0));
        assertEquals(1, back.clicks);
    }

    @Test void focusAndPointerCaptureAreRoutedOnlyToEligibleVisibleWindows() {
        var window = new Window(); window.visible = true; window.focused = true;
        PointerOverlayRegistry.register(window);
        assertTrue(PointerOverlayRegistry.textFocused());
        assertTrue(PointerOverlayRegistry.key(1, 0, 0));
        assertTrue(PointerOverlayRegistry.character('a', 0));
        PointerOverlayRegistry.clearTextFocus();
        assertFalse(PointerOverlayRegistry.textFocused());
        assertFalse(PointerOverlayRegistry.key(1, 0, 0));

        window.available = false;
        assertFalse(PointerOverlayRegistry.click(5, 5, 0));
        window.available = true;
        assertTrue(PointerOverlayRegistry.click(5, 5, 0));
        assertTrue(PointerOverlayRegistry.hasPointerCapture());
        assertTrue(PointerOverlayRegistry.drag(50, 50));
        assertTrue(PointerOverlayRegistry.release(0));
        assertFalse(PointerOverlayRegistry.hasPointerCapture());
        assertEquals(1, window.drags);
        assertEquals(1, window.releases);
    }

    @Test void productionAdmissionAndWorkspacePhaseRejectForeignOrDuplicateHosts() {
        assertTrue(PointerOverlayRegistry.hostAllowed(true, false, false));
        assertTrue(PointerOverlayRegistry.hostAllowed(false, true, false));
        assertTrue(PointerOverlayRegistry.hostAllowed(false, false, true));
        assertFalse(PointerOverlayRegistry.hostAllowed(false, false, false));
        assertTrue(PointerOverlayRegistry.shouldPaintWorkspace(true, false, true, true));
        assertFalse(PointerOverlayRegistry.shouldPaintWorkspace(true, true, true, true));
        assertTrue(PointerOverlayRegistry.shouldPaintWorkspace(false, true, true, true));
        assertFalse(PointerOverlayRegistry.shouldPaintWorkspace(false, true, false, true));

        var window = new Window(); window.visible = true; window.focused = true;
        PointerOverlayRegistry.register(window);
        PointerOverlayRegistry.overlayFront(false);
        PointerOverlayRegistry.bringWireToFront();
        PointerOverlayRegistry.resetOwnedContext();
        assertFalse(window.visible);
        assertFalse(window.focused);
        assertTrue(PointerOverlayRegistry.overlayFront());
        assertTrue(PointerOverlayRegistry.wireFront());
        assertTrue(PointerOverlayRegistry.paintOrder().isEmpty());
    }

    private static final class Window implements PointerOverlayWindow {
        boolean visible, focused, available = true, captured;
        int clicks, drags, releases;
        public boolean visible() { return visible; }
        public boolean contains(double x, double y) { return visible && x >= 0 && x < 10 && y >= 0 && y < 10; }
        public boolean pointerAvailable() { return available; }
        public boolean ownsPointerCapture() { return captured; }
        public void renderIntoGui(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                                  float partialTick, int width, int height) { }
        public boolean mouseClicked(double x, double y, int button) { clicks++; captured = true; return true; }
        public boolean mouseDragged(double x, double y) { drags++; return captured; }
        public boolean mouseReleased(int button) { releases++; captured = false; return true; }
        public boolean mouseScrolled(double x, double y, double delta) { return false; }
        public boolean keyPressed(int key, int scan, int mods) { return focused; }
        public boolean charTyped(char character, int mods) { return focused; }
        public boolean isSearchFocused() { return focused; }
        public void clearTextFocus() { focused = false; }
        public void resetOwnedContext() { visible = false; focused = false; captured = false; }
    }
}
