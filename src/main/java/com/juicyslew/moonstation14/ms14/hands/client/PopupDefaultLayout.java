package com.juicyslew.moonstation14.ms14.hands.client;

import java.util.List;

/** Initial placement for newly opened storage windows; remembered window positions are untouched. */
public final class PopupDefaultLayout {
    private static final int MARGIN = 12;
    private static final int BAR_GAP = 4;

    private PopupDefaultLayout() { }

    public static HandBarLayout.Rect calculate(StoragePopupWorkspace.HostKind kind, int width, int height,
                                               int screenWidth, int screenHeight,
                                               List<HandBarLayout.Rect> otherDefaults) {
        int x = switch (kind) {
            case BAG -> MARGIN;
            case BELT -> screenWidth - width - MARGIN;
            case HELD, WORLD -> (screenWidth - width) / 2;
        };
        int barTop = screenHeight - 22;
        var bar = HandBarLayout.calculate(screenWidth, screenHeight);
        if (bar != null) barTop = Math.min(bar.leftHand().y(), bar.back().y());
        int bottom = Math.max(0, Math.min(screenHeight, barTop) - BAR_GAP);
        int y = bottom - height;
        x = Math.max(0, Math.min(Math.max(0, screenWidth - width), x));
        y = Math.max(0, Math.min(Math.max(0, screenHeight - height), y));
        // Try the same bottom anchor shifted horizontally before accepting overlap.
        if (otherDefaults != null && overlaps(x, y, width, height, otherDefaults)) {
            for (int shift : new int[]{width + 4, -(width + 4), 2 * (width + 4), -2 * (width + 4)}) {
                int candidate = Math.max(0, Math.min(Math.max(0, screenWidth - width), x + shift));
                if (!overlaps(candidate, y, width, height, otherDefaults)) { x = candidate; break; }
            }
        }
        return new HandBarLayout.Rect(x, y, width, height);
    }

    private static boolean overlaps(int x, int y, int width, int height, List<HandBarLayout.Rect> others) {
        for (var rect : others) if (x < rect.x() + rect.width() && rect.x() < x + width
                && y < rect.y() + rect.height() && rect.y() < y + height) return true;
        return false;
    }
}

