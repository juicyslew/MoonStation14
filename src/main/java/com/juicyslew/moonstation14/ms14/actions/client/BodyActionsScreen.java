package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Persistent, snapshot-owned action menu hosted by the shared pointer workspace. */
public final class BodyActionsScreen implements PointerOverlayWindow {
    private static final BodyActionsScreen INSTANCE = new BodyActionsScreen();
    private BodyActionSnapshot snapshot;
    private WindowGeometryController geometry;
    private int visibleRows, scrollOffset;
    private int viewportWidth, viewportHeight;
    private boolean open;
    private BodyActionsScreen() { }

    public static boolean isOpen() { return INSTANCE.open; }

    public static void toggle() {
        if (INSTANCE.open) INSTANCE.close();
        else INSTANCE.open();
    }

    private boolean ownsSnapshot() {
        var body = GhostControlClient.ownedCharacterForHud();
        var mc = Minecraft.getInstance();
        return snapshot != null && body != null && mc.level != null && body.level() == mc.level
                && snapshot.bodyId().equals(body.getUUID())
                && snapshot.bodyEpoch() == GhostControlClient.committedCharacterEpoch();
    }

    private boolean refresh() {
        if (!ownsSnapshot()) { close(); return false; }
        var latest = BodyActionClient.snapshot();
        if (latest == null || !snapshot.bodyId().equals(latest.bodyId()) || snapshot.bodyEpoch() != latest.bodyEpoch()) {
            close(); return false;
        }
        if (latest.revision() != snapshot.revision()) snapshot = latest;
        return true;
    }

    private void open() {
        var mc = Minecraft.getInstance();
        snapshot = BodyActionClient.snapshot();
        if (snapshot == null || !ownsSnapshot()) { snapshot = null; return; }
        int width = (int)Math.ceil(BodyActionLayout.screenToLogical(mc.getWindow().getGuiScaledWidth()));
        int height = (int)Math.ceil(BodyActionLayout.screenToLogical(mc.getWindow().getGuiScaledHeight()));
        viewportWidth = width; viewportHeight = height;
        var layout = BodyActionLayout.calculate(width, height, snapshot.actions().size(), 0);
        geometry = new WindowGeometryController(width, height, layout.left(), layout.top(), layout.width(), layout.height(),
                Math.min(80, width), Math.min(65, height), false);
        scrollOffset = 0;
        open = true;
        PointerOverlayRegistry.register(this);
        com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.openSharedPointerWorkspace();
    }

    private void close() {
        if (!open) return;
        open = false;
        snapshot = null;
        if (geometry != null) geometry.cancelPointer();
        PointerOverlayRegistry.unregister(this);
        com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.pointerOverlayClosed();
    }

    private void relayout(int width, int height) {
        if (geometry == null) return;
        width = Math.max(1, (int)Math.ceil(BodyActionLayout.screenToLogical(width)));
        height = Math.max(1, (int)Math.ceil(BodyActionLayout.screenToLogical(height)));
        if (width != viewportWidth || height != viewportHeight) {
            geometry.setScreenSize(width, height);
            viewportWidth = width; viewportHeight = height;
        }
    }

    @Override public boolean visible() { return open; }
    @Override public boolean pointerAvailable() {
        return open && !Minecraft.getInstance().mouseHandler.isMouseGrabbed();
    }
    @Override public boolean ownsPointerCapture() { return geometry != null && geometry.isPointerCaptured(); }

    @Override public boolean contains(double x, double y) {
        if (!open || geometry == null) return false;
        double lx = BodyActionLayout.screenToLogical(x), ly = BodyActionLayout.screenToLogical(y);
        return lx >= geometry.x() && lx < (long) geometry.x() + geometry.width()
                && ly >= geometry.y() && ly < (long) geometry.y() + geometry.height();
    }

    @Override public void renderIntoGui(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int width, int height) {
        if (!refresh()) return;
        relayout(width, height);
        int logicalWidth = (int)Math.ceil(BodyActionLayout.screenToLogical(width));
        int logicalHeight = (int)Math.ceil(BodyActionLayout.screenToLogical(height));
        var layout = BodyActionLayout.calculate(logicalWidth, logicalHeight, snapshot.actions().size(), scrollOffset);
        visibleRows = layout.visibleRows();
        scrollOffset = layout.scrollOffset();
        int x = geometry.x(), y = geometry.y(), panelWidth = geometry.width(), panelHeight = geometry.height();
        var font = Minecraft.getInstance().font;
        graphics.enableScissor(0, 0, width, height);
        graphics.pose().pushPose();
        graphics.pose().translate(x * BodyActionLayout.ACTION_UI_SCALE, y * BodyActionLayout.ACTION_UI_SCALE, 0);
        graphics.pose().scale(BodyActionLayout.ACTION_UI_SCALE, BodyActionLayout.ACTION_UI_SCALE, 1);
        MachineWindowRenderer.window(graphics, font, 0, 0, panelWidth, panelHeight,
                Component.translatable("gui.moonstation14.actions.title"));
        var close = BodyActionLayout.closeBounds(panelWidth, panelHeight);
        int localMouseX = (int)Math.floor(BodyActionLayout.screenToLogical(mouseX) - x);
        int localMouseY = (int)Math.floor(BodyActionLayout.screenToLogical(mouseY) - y);
        WindowChromeButtons.paintClose(new GuiGraphicsPaint(graphics, font), close, localMouseX, localMouseY);
        if (snapshot.actions().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.moonstation14.actions.empty"), 8, 37,
                    MachineWindowRenderer.MUTED_TEXT, false);
            graphics.pose().popPose();
            graphics.disableScissor();
            return;
        }
        int count = Math.min(visibleRows, snapshot.actions().size() - scrollOffset);
        for (int i = 0; i < count; i++) {
            var entry = snapshot.actions().get(scrollOffset + i);
            int rowY = BodyActionLayout.HEADER_HEIGHT + i * BodyActionLayout.ROW_HEIGHT;
            boolean disabled = !entry.enabled() || BodyActionClient.pending();
            MachineWindowRenderer.insetPanel(graphics, 5, rowY, panelWidth - 10, BodyActionLayout.ROW_HEIGHT - 2);
            graphics.blit(entry.iconOn().filter(ignored -> entry.active()).orElse(entry.icon()), 9, rowY + 7,
                    0, 0, 16, 16, 16, 16);
            int textWidth = Math.max(0, panelWidth - 58);
            String name = Component.translatable(entry.displayName()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(name, textWidth), 29, rowY + 3,
                    disabled ? MachineWindowRenderer.MUTED_TEXT : MachineWindowRenderer.TEXT, false);
            Component state = Component.translatable(entry.kind() == com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype.Kind.TOGGLE
                    ? entry.active() ? "gui.moonstation14.actions.active" : "gui.moonstation14.actions.inactive"
                    : "gui.moonstation14.actions.use");
            long cooldown = BodyActionClient.remainingCooldown(entry);
            String cooldownText = cooldown > 0 ? " · " + BodyActionLayout.cooldownSeconds(cooldown) : "";
            String detail = !entry.enabled()
                    ? Component.translatable("gui.moonstation14.actions.disabled." + entry.disabledReason().toLowerCase(java.util.Locale.ROOT)).getString() + cooldownText
                    : state.getString() + cooldownText;
            graphics.drawString(font, font.plainSubstrByWidth(detail, textWidth), 29, rowY + 16,
                    disabled ? MachineWindowRenderer.MUTED_TEXT : MachineWindowRenderer.HIGHLIGHT, false);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!refresh() || !contains(mouseX, mouseY)) return false;
        double lx = BodyActionLayout.screenToLogical(mouseX), ly = BodyActionLayout.screenToLogical(mouseY);
        int x = geometry.x(), y = geometry.y(), width = geometry.width();
        var localClose = BodyActionLayout.closeBounds(width, geometry.height());
        var close = BodyActionLayout.screenBounds(x + localClose.left(), y + localClose.top(),
                localClose.width(), localClose.height());
        if (WindowChromeButtons.contains(close, mouseX, mouseY)) { if (button == 0) close(); return true; }
        if (geometry.beginPointer(lx, ly, button, WindowGeometryController.TITLE_HEIGHT,
                WindowGeometryController.TITLE_BUTTON_SPACE)) return true;
        if (button != 0) return true;
        var layout = new BodyActionLayout.Layout(geometry.x(), geometry.y(), geometry.width(), geometry.height(),
                visibleRows, scrollOffset);
        int index = layout.rowAt(lx, ly, snapshot.actions().size());
        if (index >= 0) {
            var entry = snapshot.actions().get(index);
            if (entry.enabled() && BodyActionClient.remainingCooldown(entry) == 0 && !BodyActionClient.pending())
                BodyActionClient.activate(entry);
        }
        return true;
    }

    @Override public boolean mouseDragged(double x, double y) { return geometry != null && geometry.dragPointer(BodyActionLayout.screenToLogical(x), BodyActionLayout.screenToLogical(y)); }
    @Override public boolean mouseReleased(int button) { return geometry != null && geometry.releasePointer(button); }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!contains(x, y)) return false;
        scrollOffset = BodyActionLayout.clampScroll(scrollOffset - (int) Math.signum(delta), snapshot.actions().size(), visibleRows);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || BodyActionKeys.matches(key, scan)) { close(); return true; }
        return false;
    }
    @Override public boolean charTyped(char value, int modifiers) { return false; }
    @Override public boolean isSearchFocused() { return false; }
    @Override public void clearTextFocus() { }
    @Override public void resetOwnedContext() { open = false; snapshot = null; if (geometry != null) geometry.cancelPointer(); }
}
