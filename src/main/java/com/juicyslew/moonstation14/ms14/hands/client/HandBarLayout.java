package com.juicyslew.moonstation14.ms14.hands.client;

/** Pure pixel layout for the body hand bar. Rectangles are GUI-scaled coordinates. */
public final class HandBarLayout {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public record Layout(Rect leftHand, Rect rightHand, Rect back, Rect belt) {
        public enum Tile { FIRST, SECOND, BACK, BELT }
        public Tile tileAt(int x, int y) {
            if (leftHand.contains(x, y)) return Tile.FIRST;
            if (rightHand.contains(x, y)) return Tile.SECOND;
            if (back.contains(x, y)) return Tile.BACK;
            if (belt.contains(x, y)) return Tile.BELT;
            return null;
        }
        public Rect hitTest(int x, int y) {
            for (Rect rect : new Rect[]{leftHand, rightHand, back, belt}) if (rect.contains(x, y)) return rect;
            return null;
        }
    }
    private HandBarLayout() { }

    public static Layout calculate(int width, int height) {
        if (width < 90 || height < 120) return null;
        int gap = 3;
        int equipmentGap = 2;
        int hand = Math.min(18, Math.max(1, (width - 20) / 5));
        int equipment = Math.min(15, Math.max(1, hand - 2));
        int handGroupWidth = hand * 2 + gap;
        int handX = (width - handGroupWidth) / 2;
        int equipmentInset = 8;
        int equipmentStart = handX - equipment * 2 - equipmentGap - equipmentInset;
        // Keep the centered hand group and fit both equipment tiles to its left.
        while (equipmentStart < 0
                && hand > 1) {
            hand--;
            equipment = Math.min(equipment, Math.max(1, hand - 2));
            handGroupWidth = hand * 2 + gap;
            handX = (width - handGroupWidth) / 2;
            equipmentStart = handX - equipment * 2 - equipmentGap - equipmentInset;
        }
        if (equipmentStart < 0) return null;
        int y = Math.max(0, height - hand - 2);
        int equipmentY = y + (hand - equipment) / 2;
        return new Layout(new Rect(handX, y, hand, hand), new Rect(handX + hand + gap, y, hand, hand),
                new Rect(equipmentStart, equipmentY, equipment, equipment),
                new Rect(equipmentStart + equipment + equipmentGap, equipmentY, equipment, equipment));
    }
}
