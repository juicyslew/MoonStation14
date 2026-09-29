package com.juicyslew.moonstation14.ms14.ui.client.window;

/**
 * GUI-scaled, client-only window placement. Forward mouse presses/drags/releases from a screen;
 * check widgets first so buttons in the title bar retain their normal click behavior.
 * Resizing is opt-in: fixed-size windows (including an APC) should pass {@code false}.
 */
public final class WindowGeometryController {
    public static final int TITLE_HEIGHT = 27;
    public static final int TITLE_BUTTON_SPACE = 46;
    public static final int RESIZE_GRIP = 8;

    private final int minWidth;
    private final int minHeight;
    private final boolean resizable;
    private int screenWidth;
    private int screenHeight;
    private int x;
    private int y;
    private int width;
    private int height;
    private int startX;
    private int startY;
    private int startWidth;
    private int startHeight;
    private double startMouseX;
    private double startMouseY;
    private int capturedButton = -1;
    private boolean resizing;

    public WindowGeometryController(int screenWidth, int screenHeight, int x, int y,
                                    int width, int height, int minWidth, int minHeight,
                                    boolean resizable) {
        if (screenWidth <= 0 || screenHeight <= 0 || minWidth <= 0 || minHeight <= 0)
            throw new IllegalArgumentException("Screen and minimum sizes must be positive");
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.resizable = resizable;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        setBounds(x, y, width, height);
    }

    /** Re-clamp on GUI scale changes; a viewport smaller than the minimum fits the viewport. */
    public void setScreenSize(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0)
            throw new IllegalArgumentException("Screen size must be positive");
        cancelPointer();
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        setBounds(x, y, width, height);
    }

    public void setBounds(int x, int y, int width, int height) {
        cancelPointer();
        this.width = clamp(width, Math.min(minWidth, screenWidth), screenWidth);
        this.height = clamp(height, Math.min(minHeight, screenHeight), screenHeight);
        this.x = clamp(x, 0, screenWidth - this.width);
        this.y = clamp(y, 0, screenHeight - this.height);
    }

    /** Returns true only when the primary button captured the title or resize grip. */
    public boolean beginPointer(double mouseX, double mouseY, int button) {
        if (button != 0 || capturedButton != -1 || !Double.isFinite(mouseX) || !Double.isFinite(mouseY))
            return false;
        if (mouseX < x || mouseY < y || mouseX >= (long) x + width || mouseY >= (long) y + height)
            return false;
        boolean onGrip = resizable && mouseX >= (long) x + width - RESIZE_GRIP
                && mouseY >= (long) y + height - RESIZE_GRIP;
        boolean onTitle = mouseY < (long) y + Math.min(TITLE_HEIGHT, height)
                && mouseX < (long) x + Math.max(0, width - TITLE_BUTTON_SPACE);
        if (!onGrip && !onTitle) return false;
        capturedButton = button;
        resizing = onGrip;
        startMouseX = mouseX;
        startMouseY = mouseY;
        startX = x;
        startY = y;
        startWidth = width;
        startHeight = height;
        return true;
    }

    /** Return true while captured; updates are relative to press, not accumulated frame-to-frame. */
    public boolean dragPointer(double mouseX, double mouseY) {
        if (capturedButton == -1) return false;
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)) return true;
        if (resizing) {
            width = clamp(startWidth + (mouseX - startMouseX), Math.min(minWidth, screenWidth - x), screenWidth - x);
            height = clamp(startHeight + (mouseY - startMouseY), Math.min(minHeight, screenHeight - y), screenHeight - y);
        } else {
            x = clamp(startX + (mouseX - startMouseX), 0, screenWidth - width);
            y = clamp(startY + (mouseY - startMouseY), 0, screenHeight - height);
        }
        return true;
    }

    public boolean releasePointer(int button) {
        if (button != capturedButton || capturedButton == -1) return false;
        cancelPointer();
        return true;
    }

    public void cancelPointer() {
        capturedButton = -1;
        resizing = false;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean resizable() { return resizable; }
    public boolean isPointerCaptured() { return capturedButton != -1; }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(double value, int min, int max) {
        return (int) Math.max(min, Math.min(max, Math.round(value)));
    }
}
