package com.juicyslew.moonstation14.ms14.doors.ui.client;

/** Pixel geometry for the compact 210x160 airlock circuit window. */
public final class AirlockWireScreenLayout {
    public static final int WIDTH = 210, HEIGHT = 160;
    private AirlockWireScreenLayout() { }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public static Rect indicator(int x, int y, int index) {
        return new Rect(x + 10 + index % 4 * 49, y + 34 + index / 4 * 22, 45, 18);
    }

    public static Rect wire(int x, int y, int index) {
        // Eight physical wire slots fit at native 16px sprite size without fractional scaling.
        return new Rect(x + 16 + index * 22, y + 83, 16, 50);
    }

    public static Rect footer(int x, int y) { return new Rect(x + 7, y + 144, WIDTH - 14, 10); }

    public static int hitWire(int x, int y, double mouseX, double mouseY, int count) {
        for (int i = 0; i < count; i++) if (wire(x, y, i).contains(mouseX, mouseY)) return i;
        return -1;
    }
}
