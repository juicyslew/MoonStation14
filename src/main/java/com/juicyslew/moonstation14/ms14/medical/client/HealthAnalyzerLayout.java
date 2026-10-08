package com.juicyslew.moonstation14.ms14.medical.client;

import com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerSnapshot;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;

/** Pure geometry shared by analyzer sizing and its renderer. */
public final class HealthAnalyzerLayout {
    public record LedBounds(int x, int y, int size) { }
    public record ClipBounds(int left, int top, int right, int bottom) { }
    public static final int MIN_HEIGHT = 52, MAX_HEIGHT = 200, SCREEN_MARGIN = 16;
    public static final int WIDTH = 144, HEADER = 14, FOOTER = 0, ALERT_ROW = 7, CONTENT_TOP_PAD = 3;
    public static final int SUMMARY_HEIGHT = 33, FIELD_ROW = 6, GROUP_ROW = 8, DAMAGE_ROW = 6;
    public static final int GROUP_SEPARATOR = 3;
    public static final int PORTRAIT_WIDTH = 64;
    private HealthAnalyzerLayout() { }

    public static int contentExtent(HealthAnalyzerSnapshot reading) {
        int extent = 0, groups = 0;
        if (reading != null) for (var group : reading.groups())
            if (group.total() > 0 && !group.damage().isEmpty()) {
                if (groups++ > 0) extent += GROUP_SEPARATOR;
                extent += GROUP_ROW + group.damage().size() * DAMAGE_ROW;
            }
        return extent;
    }

    /** Last content pixel relative to content origin, including the portrait and summary padding. */
    public static int contentEnd(HealthAnalyzerSnapshot reading) { return contentExtent(reading); }

    /** Apply the analyzer's area budget at the current GUI viewport, without imposing a responsive layout system. */
    public static int desiredHeight(HealthAnalyzerSnapshot reading, int windowWidth, int screenWidth, int screenHeight) {
        int required = HEADER + CONTENT_TOP_PAD + SUMMARY_HEIGHT + contentExtent(reading) + footerHeight(reading) + 2;
        int cap = Math.min(MAX_HEIGHT, Math.max(1, screenHeight - SCREEN_MARGIN));
        return Math.min(Math.max(1, screenHeight), Math.min(cap, Math.max(Math.min(MIN_HEIGHT, cap), required)));
    }

    public static int footerHeight(HealthAnalyzerSnapshot reading) {
        if (reading == null) return 0;
        return ALERT_ROW * ((reading.bleeding() ? 1 : 0) + (reading.unrevivable() ? 1 : 0));
    }

    public static int maxScroll(HealthAnalyzerSnapshot reading, int windowHeight) {
        int viewport = damageViewportHeight(windowHeight, footerHeight(reading));
        return Math.max(0, contentExtent(reading) - viewport);
    }

    public static int summaryBottom() { return HEADER + CONTENT_TOP_PAD + SUMMARY_HEIGHT; }
    public static int damageViewportHeight(int height, int footer) { return Math.max(0, height - footer - summaryBottom()); }
    public static int measuredWidth(int summaryLabel, int summaryValue, int damageLabel, int damageValue, int titleWidth) {
        int summary = 8 + Math.max(0, summaryLabel) + 6 + Math.max(0, summaryValue) + 6 + PORTRAIT_WIDTH + 8;
        int damage = 42 + Math.max(0, damageLabel) + 3 + Math.max(0, damageValue) + 10;
        int title = Math.max(0, titleWidth) + 4 + 7 + 2 * (WindowChromeButtons.EDGE + WindowChromeButtons.SIZE);
        return Math.max(100, Math.max(summary, Math.max(damage, title)));
    }
    public static int scrollbarThumbHeight(int track, int viewport, int content) {
        if (track <= 0 || content <= 0) return 0;
        return Math.min(track, Math.max(Math.min(6, track), (int)((long)track * viewport / content)));
    }
    public static int scrollbarThumbY(int top, int track, int thumb, int scroll, int maxScroll) {
        return top + (maxScroll <= 0 ? 0 : (int)((long)Math.max(0, Math.min(maxScroll, scroll)) * Math.max(0, track-thumb) / maxScroll));
    }

    /** Four GUI-pixel indicator placed in the title-bar gap immediately before shared close chrome. */
    public static LedBounds ledBounds(int windowWidth, int windowHeight, int closeLeft) {
        int size = 4;
        int x = Math.max(2, closeLeft - size - 3);
        int y = Math.max(0, (HEADER - size) / 2);
        return new LedBounds(Math.min(x, Math.max(2, windowWidth - size - 2)), y, size);
    }

    /** Reduce a label/value pair only when localization or an unusually narrow viewport requires it. */
    public static int labelWidth(int available, int measuredLabel, int measuredValue) {
        return Math.max(0, Math.min(measuredLabel, available - Math.min(measuredValue, available)));
    }

    /** GuiGraphics scissor coordinates are GUI-screen pixels and are not transformed by the pose stack. */
    public static ClipBounds bodyClipBounds(int windowX, int windowY, int windowWidth, int windowHeight, int footer) {
        return new ClipBounds(windowX + 1, windowY + HEADER, windowX + windowWidth - 1, windowY + windowHeight - footer);
    }
}
