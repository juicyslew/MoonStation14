package com.juicyslew.moonstation14.ms14.hands.client;

/** Pure layout for the non-interactive hand name labels. */
public final class HandInfoLayout {
    public record Layout(HandBarLayout.Rect left, HandBarLayout.Rect right, int textBudget) { }

    private HandInfoLayout() { }

    public static Layout calculate(int width, int height, HandBarLayout.Layout bar) {
        if (bar == null || width < 90 || height < 120) return null;
        var first = bar.leftHand();
        var second = bar.rightHand();
        int panelWidth = Math.min(48, Math.min(width, Math.max(first.width() + 4, width / 6)));
        if (panelWidth < 24) return null;
        int panelHeight = 12;
        int y = first.y() - panelHeight - 2;
        if (y < 0) return null;
        // Keep each label attached to the inside edge of its hand. Centering
        // both wide labels on the hand tiles makes them overlap at normal GUI sizes.
        int leftX = first.x() + first.width() - panelWidth;
        int rightX = second.x();
        return new Layout(new HandBarLayout.Rect(leftX, y, panelWidth, panelHeight),
                new HandBarLayout.Rect(rightX, y, panelWidth, panelHeight), Math.max(0, panelWidth - 6));
    }
}
