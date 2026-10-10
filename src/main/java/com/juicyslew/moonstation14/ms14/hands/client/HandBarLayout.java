package com.juicyslew.moonstation14.ms14.hands.client;

/** Pure pixel layout for the body hand bar. Rectangles are GUI-scaled coordinates. */
public final class HandBarLayout {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public record Layout(Rect leftHand, Rect rightHand, Rect back, Rect belt, Rect id, Rect suitstorage, Rect pocket1, Rect pocket2) {
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
        /** Historical side-of-hotbar positions for the remaining equipment tiles. */
        public Rect equipmentSlot(String slotId) {
            return switch (slotId) {
                case "id" -> id; case "suitstorage" -> suitstorage;
                case "pocket1" -> pocket1; case "pocket2" -> pocket2;
                default -> null;
            };
        }
    }
    private HandBarLayout() { }

    public static Layout calculate(int width, int height) {
        if (width < 90 || height < 120) return null;
        int gap = 3;
        int hand = 18;
        int handX = (width - (hand * 2 + gap)) / 2;
        int equipmentGap = 3;
        // Reserve the left clothing block before placing hotbar equipment. One
        // canonical wearable size keeps panel hitboxes aligned with this bar.
        int equipment = Math.min(15, Math.max(1, (handX - 17) / 6));
        int y = Math.max(0, height - hand - 2);
        int equipmentY = y + (hand - equipment) / 2;
        int rightX = handX + hand * 2 + gap;
        int side = handX - equipment * 3 - equipmentGap * 3;
        int rightStart = rightX + equipmentGap;
        return new Layout(new Rect(handX, y, hand, hand), new Rect(handX + hand + gap, y, hand, hand),
                new Rect(handX - equipment * 2 - equipmentGap * 2, equipmentY, equipment, equipment),
                new Rect(handX - equipment - equipmentGap, equipmentY, equipment, equipment),
                new Rect(side, equipmentY, equipment, equipment),
                new Rect(rightStart + equipment * 2 + equipmentGap * 2, equipmentY, equipment, equipment),
                new Rect(rightStart, equipmentY, equipment, equipment),
                new Rect(rightStart + equipment + equipmentGap, equipmentY, equipment, equipment));
    }
}
