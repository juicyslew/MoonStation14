package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.ui.client.GameplayHudPolicy;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BodyActionChatWorkspaceTest {
    @Test
    void actionBarUsesSharedHudAdmissionForChatWithoutAdmittingHiddenGrabbedOrUnrelatedHosts() {
        var chat = new ChatScreen("");
        Screen unrelated = new Screen(Component.literal("unrelated")) { };

        assertFalse(PointerOverlayRegistry.allowsHost(chat), "chat is admitted by the HUD workspace, not window hosts");
        assertTrue(BodyActionHud.pointerHostAllowed(chat, true, false));
        assertFalse(BodyActionHud.pointerHostAllowed(chat, false, false));
        assertFalse(BodyActionHud.pointerHostAllowed(chat, true, true));
        assertFalse(BodyActionHud.pointerHostAllowed(unrelated, true, false));
    }

    @Test
    void chatUsesSharedHudWorkspacePaintAdmissionAndDoesNotAdmitSecondScreenWorkspacePass() {
        var chat = new ChatScreen("");
        assertTrue(GameplayHudPolicy.hudPaintsWorkspace(chat));
        assertFalse(PointerOverlayRegistry.shouldPaintWorkspace(false, true, true,
                PointerOverlayRegistry.allowsHost(chat)));

        AtomicInteger storagePaints = new AtomicInteger();
        AtomicInteger overlayPaints = new AtomicInteger();
        PointerOverlayRegistry.renderWorkspace(storagePaints::incrementAndGet, overlayPaints::incrementAndGet);
        assertEquals(1, storagePaints.get());
        assertEquals(1, overlayPaints.get());
    }

    @Test
    void chatPointerDispatchUsesSharedTopmostHitAndCapturedGestureOwner() {
        var window = new ProbeWindow();
        PointerOverlayRegistry.register(window);
        try {
            assertTrue(PointerOverlayRegistry.routeWorkspaceClick(12, 12, 0, false, false, () -> false));
            assertEquals(1, window.clicks);
            assertFalse(window.focused, "shared workspace click ends any prior overlay text target");
            assertFalse(PointerOverlayRegistry.routeWorkspaceClick(40, 40, 0, false, false, () -> false));
            assertEquals(1, window.clicks, "outside clicks pass through to chat rather than an overlay-specific path");

            assertTrue(PointerOverlayRegistry.drag(15, 15));
            assertEquals(1, window.drags);
            assertTrue(PointerOverlayRegistry.release(0));
            assertEquals(1, window.releases);
        } finally { PointerOverlayRegistry.resetOwnedContext(); }
    }

    private static final class ProbeWindow implements PointerOverlayWindow {
        private boolean capturing, focused = true;
        private int clicks, drags, releases;
        @Override public boolean visible() { return true; }
        @Override public boolean contains(double x, double y) { return x >= 10 && x < 20 && y >= 10 && y < 20; }
        @Override public void renderIntoGui(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                            int width, int height) { }
        @Override public boolean mouseClicked(double x, double y, int button) { clicks++; capturing = true; return true; }
        @Override public boolean mouseDragged(double x, double y) { drags++; return true; }
        @Override public boolean ownsPointerCapture() { return capturing; }
        @Override public boolean mouseReleased(int button) { releases++; capturing = false; return true; }
        @Override public boolean mouseScrolled(double x, double y, double delta) { return false; }
        @Override public boolean keyPressed(int key, int scan, int modifiers) { return false; }
        @Override public boolean charTyped(char value, int modifiers) { return false; }
        @Override public boolean isSearchFocused() { return focused; }
        @Override public void clearTextFocus() { focused = false; }
        @Override public void resetOwnedContext() { capturing = false; }
    }
}
