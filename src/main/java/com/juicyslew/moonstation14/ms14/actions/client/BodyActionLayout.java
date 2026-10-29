package com.juicyslew.moonstation14.ms14.actions.client;

/** Shared pure geometry and timing calculations for the body-action menu and HUD. */
public final class BodyActionLayout {
    public static final float ACTION_UI_SCALE = 0.5f;
    public static final int ACTION_BAR_BUTTON = 28, ACTION_BAR_ICON = 24, ACTION_BAR_ICON_INSET = 2;
    public static final int ROW_HEIGHT = 34, HEADER_HEIGHT = 29, WIDTH = 190;
    public record Layout(int left, int top, int width, int height, int visibleRows, int scrollOffset) {
        public int rowAt(double x, double y, int actionCount) {
            if (width <= 0 || visibleRows <= 0 || x < left || x >= left + width || y < top + HEADER_HEIGHT) return -1;
            int row = (int)((y - top - HEADER_HEIGHT) / ROW_HEIGHT);
            return row >= 0 && row < Math.min(visibleRows, Math.max(0, actionCount - scrollOffset)) ? row + scrollOffset : -1;
        }
    }
    private BodyActionLayout() { }
    public static double toLogical(double screenCoordinate, double origin) { return (screenCoordinate - origin) / ACTION_UI_SCALE; }
    public static double screenToLogical(double screenCoordinate) { return screenCoordinate / ACTION_UI_SCALE; }
    public static int logicalToScreen(double logicalCoordinate) { return (int)Math.round(logicalCoordinate * ACTION_UI_SCALE); }
    public static net.minecraft.client.gui.navigation.ScreenRectangle screenBounds(int x, int y, int width, int height) {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(logicalToScreen(x), logicalToScreen(y),
                toScreenSize(width), toScreenSize(height));
    }
    public static int toScreenSize(int logicalSize) { return (int)Math.ceil(logicalSize * ACTION_UI_SCALE); }
    public static net.minecraft.client.gui.navigation.ScreenRectangle closeBounds(int width, int height) {
        var raw = com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(
                width - com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.EDGE
                        - com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.SIZE,
                com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.EDGE,
                com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.SIZE,
                com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.SIZE);
        int left = Math.min(Math.max(0, width), Math.max(0, raw.left()));
        int top = Math.min(Math.max(0, height), Math.max(0, raw.top()));
        int right = Math.min(Math.max(0, width), raw.left() + raw.width());
        int bottom = Math.min(Math.max(0, height), raw.top() + raw.height());
        return com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(
                left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }
    public static Layout calculate(int screenWidth, int screenHeight, int actionCount, int scroll) {
        screenWidth = Math.max(0, screenWidth);
        screenHeight = Math.max(0, screenHeight);
        // Keep the floating menu compact on large viewports; excess entries scroll in-window.
        int desiredRows = Math.min(7, Math.max(1, (screenHeight - 8 - HEADER_HEIGHT - 6) / ROW_HEIGHT));
        int rows = Math.max(0, Math.min(desiredRows,
                Math.max(0, (screenHeight - HEADER_HEIGHT - 6) / ROW_HEIGHT)));
        int offset = clampScroll(scroll, actionCount, rows);
        int width = Math.min(WIDTH, screenWidth > 8 ? screenWidth - 8 : screenWidth);
        int desiredHeight = HEADER_HEIGHT + rows * ROW_HEIGHT + 6;
        int height = Math.min(screenHeight, desiredHeight);
        return new Layout(Math.max(0, (screenWidth-width)/2), Math.max(0,(screenHeight-height)/2), width,height,rows,offset);
    }
    public static int clampScroll(int offset, int count, int visibleRows) { return Math.max(0, Math.min(Math.max(0,count-visibleRows),offset)); }
    public static float cooldownFraction(long remaining, int duration) { return Math.max(0f, Math.min(1f, remaining / (float)Math.max(1,duration))); }
    /** Top-down left-edge vertical bar; action identity is the stable numbered binding. */
    public static int actionBarSlot(int x, int y, int left, int top) {
        if (x < left || x >= left + 28 || y < top) return -1;
        int offset = y - top, slot = offset / 30;
        return slot < 3 && offset % 30 < 28 ? slot : -1;
    }
    public static float cooldownWipeProgress(long remaining, int duration) { return cooldownFraction(remaining, duration); }
    public static float cooldownBlinkProgress(long elapsedTicks) { return elapsedTicks < 0 || elapsedTicks >= 2 ? 0f : 1f - elapsedTicks / 2f; }
    public static String cooldownSeconds(long remainingTicks) {
        return String.format(java.util.Locale.ROOT, "%.1f s", Math.max(0, remainingTicks) / 20.0);
    }
    public static int actionBarTop() { return 8; }
    /** Shared host admission for visible, ungrabbed HUD/pointer-workspace input. */
    public static boolean pointerHostAllowed(boolean visible, boolean allowedHost, boolean mouseGrabbed) {
        return visible && allowedHost && !mouseGrabbed;
    }
}
