package com.juicyslew.moonstation14.ms14.hands.client;

/** Pure layout for the non-interactive hand name labels. */
public final class HandInfoLayout {
    public record Layout(HandBarLayout.Rect left, HandBarLayout.Rect right, int textBudget) { }

    private HandInfoLayout() { }

    /** Positions the text origin in GUI pixels, accounting for the pose scale applied by the renderer. */
    static int textX(int panelX, int panelWidth, int fontWidth, float scale, boolean rightAligned) {
        return rightAligned ? panelX + panelWidth - 3 - Math.round(fontWidth * scale) : panelX + 3;
    }

    public static Layout calculate(int width, int height, HandBarLayout.Layout bar) {
        return calculate(width, height, bar, false, false);
    }

    public static Layout calculate(int width, int height, HandBarLayout.Layout bar,
                                   boolean leftAmmoRowVisible, boolean rightAmmoRowVisible) {
        if (bar == null || width < 90 || height < 120) return null;
        var first = bar.leftHand();
        var second = bar.rightHand();
        if (second.width() == 0) {
            int panelWidth = Math.min(96, width - 6);
            int panelHeight = 12;
            int y = first.y() - panelHeight - 2;
            if (panelWidth < 24 || y < 0) return null;
            return new Layout(new HandBarLayout.Rect((width - panelWidth) / 2, y, panelWidth, panelHeight),
                    null, Math.max(0, panelWidth - 6));
        }
        int panelWidth = Math.min(96, Math.min(first.x() + first.width(), width - second.x()));
        if (panelWidth < 24) return null;
        int leftHeight = leftAmmoRowVisible ? 27 : 12;
        int rightHeight = rightAmmoRowVisible ? 27 : 12;
        int panelHeight = Math.max(leftHeight, rightHeight);
        int y = first.y() - panelHeight - 2;
        if (y < 0) return null;
        // Keep each panel on its existing inner-edge anchor; the hand gap
        // separates the panels even at the expanded width.
        int leftX = first.x() + first.width() - panelWidth;
        int rightX = second.x();
        return new Layout(new HandBarLayout.Rect(leftX, y + panelHeight - leftHeight, panelWidth, leftHeight),
                new HandBarLayout.Rect(rightX, y + panelHeight - rightHeight, panelWidth, rightHeight), Math.max(0, panelWidth - 6));
    }
}
