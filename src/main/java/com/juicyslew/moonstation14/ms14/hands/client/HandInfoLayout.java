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
        if (bar == null || width < 90 || height < 120) return null;
        var first = bar.leftHand();
        var second = bar.rightHand();
        int panelWidth = Math.min(96, Math.min(first.x() + first.width(), width - second.x()));
        if (panelWidth < 24) return null;
        int panelHeight = 12;
        int y = first.y() - panelHeight - 2;
        if (y < 0) return null;
        // Keep each panel on its existing inner-edge anchor; the hand gap
        // separates the panels even at the expanded width.
        int leftX = first.x() + first.width() - panelWidth;
        int rightX = second.x();
        return new Layout(new HandBarLayout.Rect(leftX, y, panelWidth, panelHeight),
                new HandBarLayout.Rect(rightX, y, panelWidth, panelHeight), Math.max(0, panelWidth - 6));
    }
}
