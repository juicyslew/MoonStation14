package com.juicyslew.moonstation14.ms14.chat.client;

/** Shared scaled-GUI placement for the read-only chat viewer and status stack. */
public final class TopRightHudLayout {
    public static final int BOTTOM_RESERVE = 34;
    public static final int ROW_HEIGHT = 10;
    public static final float STATUS_FONT_SCALE = 0.78f;
    private static final int MARGIN = 6;
    private static final int STATUS_GAP = 5;
    public static final int EXAMINE_RESERVED_STATUS_ROWS = 7;
    private static final int EXAMINE_GAP = 5;

    private TopRightHudLayout() { }

    public static Rect chat(int width, int height) {
        int right = Math.max(0, width - MARGIN);
        int left = Math.max(0, right - Math.min(155, Math.max(0, width - 2 * MARGIN)));
        int top = Math.min(MARGIN, Math.max(0, height));
        // On cramped screens preserve room for several statuses rather than obscuring the handbar.
        int available = Math.max(0, height - BOTTOM_RESERVE - top - STATUS_GAP - 3 * ROW_HEIGHT);
        int bottom = top + Math.min(78, available);
        return new Rect(left, top, right, bottom);
    }

    public static int statusTop(int width, int height) {
        return chat(width, height).bottom() + STATUS_GAP;
    }

    public static int statusLimit(int height) {
        return Math.max(0, height - BOTTOM_RESERVE);
    }

    /** Only complete, non-overlapping rows fit; no clamping multiple rows onto one baseline. */
    public static int statusRows(int width, int height) {
        return Math.max(0, (statusLimit(height) - statusTop(width, height)) / ROW_HEIGHT);
    }

    /** A full status row, including its tinted background, within the shared right column. */
    public static Rect statusRow(int width, int height, int index) {
        Rect panel = chat(width, height);
        int top = statusTop(width, height) + index * ROW_HEIGHT;
        return new Rect(panel.left(), top, panel.right(), top + ROW_HEIGHT);
    }

    /** Top edge after the fixed status reservation, regardless of active row count. */
    public static int examineTop(int width, int height) {
        return statusRow(width, height, EXAMINE_RESERVED_STATUS_ROWS - 1).bottom() + EXAMINE_GAP;
    }

    public static int examineAvailableHeight(int width, int height) {
        return Math.max(0, height - BOTTOM_RESERVE - examineTop(width, height));
    }

    public static int examineLeft(int width, int panelWidth) {
        return Math.max(0, chat(width, 0).right() - panelWidth);
    }

    public record Rect(int left, int top, int right, int bottom) {
        public int width() { return right - left; }
        public int height() { return bottom - top; }
    }
}

