package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExaminationPolicy;
import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;

/** Pure presentation decisions shared by the live client and focused unit tests. */
public final class ExamineClientPolicy {
    private ExamineClientPolicy() { }

    public static boolean eligible(ExaminationPolicy.Examiner examiner, ExaminationPolicy.State state,
                                   boolean blind, double blur, double distance) {
        return ExaminationPolicy.canExamine(examiner, state, blind, blur, distance, true, true, true);
    }

    public static boolean actualObserverGhost(boolean ghostMode, boolean identityMatches, boolean epochMatches) {
        return ghostMode && identityMatches && epochMatches;
    }

    /** Screens retain click priority except the non-interactive portion of the storage world host. */
    public static boolean worldClickAllowed(boolean noScreen, boolean storageHost, boolean overInteractiveStorage) {
        return noScreen || (storageHost && !overInteractiveStorage);
    }
    public static boolean popupClickAllowed(boolean noScreen, boolean storageHost) {
        return noScreen || storageHost;
    }
    public static boolean disagreementRefreshDue(boolean localAllowed, boolean serverAllowed,
                                                 boolean inFlight, long now, long retryAt) {
        return localAllowed != serverAllowed && !inFlight && now >= retryAt;
    }
    public static boolean periodicRefreshDue(boolean localAllowed, boolean serverAllowed,
                                             boolean inFlight, long now, long lastRequest) {
        return localAllowed == serverAllowed && !inFlight && now - lastRequest >= 20;
    }
    public static int clampScrollOffset(int offset, int totalLines, int visibleLines) {
        return Math.max(0, Math.min(Math.max(0, offset), Math.max(0, totalLines - Math.max(0, visibleLines))));
    }
    public static int scrollOffset(int offset, int direction, int totalLines, int visibleLines) {
        return clampScrollOffset(offset + Integer.signum(direction), totalLines, visibleLines);
    }
    public static int popupVisibleRows(int availableHeight, int lineHeight, int totalRows) {
        int capacity = Math.max(0, (availableHeight - 20) / Math.max(1, lineHeight));
        return Math.min(Math.max(0, totalRows), capacity);
    }
    public static int popupHeight(int availableHeight, int lineHeight, int visibleRows) {
        return Math.min(Math.max(0, availableHeight), 20 + Math.max(0, visibleRows) * Math.max(1, lineHeight));
    }
    /** Width left for the clipped scroll cue after the pin/close controls and text inset. */
    public static int popupCueMaxWidth(int popupWidth) { return Math.max(1, popupWidth - 38); }

    /** Input is natural font width; output includes padding after applying the rendered .75 scale. */
    public static int popupWidth(int naturalFontWidth, int guiWidth) {
        int renderedContentWidth = (int)Math.ceil(Math.max(0, naturalFontWidth) * .75f);
        return Math.max(1, Math.min(Math.min(112, guiWidth - 8), Math.max(56, renderedContentWidth + 8)));
    }
    /** Input is already-scaled rendered width; width includes only its padding. */
    public static int previewWidth(int renderedTextWidth, int guiWidth) {
        return Math.max(1, Math.min(guiWidth - 8, renderedTextWidth + 8));
    }
    public static float previewScale(int textWidth, int guiWidth) {
        int available = Math.max(1, guiWidth - 16);
        return Math.min(.75f, available / (float)Math.max(1, textWidth));
    }
    public static boolean tooltipTextPresent(String text) { return text != null && !text.isBlank(); }
    public static int previewTop(int pointerY, int previewHeight) {
        return Math.max(4, pointerY - previewHeight - 24);
    }
    public static int previewLeft(int pointerX, int previewWidth, int guiWidth) {
        return Math.max(4, Math.min(pointerX - previewWidth / 2, guiWidth - previewWidth - 4));
    }
    public static boolean closeHit(double x, int popupX, int width) { return x >= popupX + width - 14 && x < popupX + width; }
    public static boolean pinHit(double x, int popupX, int width) { return x >= popupX + width - 31 && x < popupX + width - 14; }
    public static String publicPreviewName(ExamineSelector.Candidate candidate) {
        return candidate == null ? "" : candidate.publicName();
    }
    public static boolean drawSeparatePreview(ExamineTarget active, ExamineTarget preview) {
        return preview != null && !preview.equals(active);
    }
    /** Scene candidates are a fallback only; any eligible world candidate, including transparent glass, wins. */
    public static boolean sceneFallbackAllowed(java.util.List<ExamineSelector.Candidate> eligibleWorldCandidates) {
        return eligibleWorldCandidates == null || eligibleWorldCandidates.isEmpty();
    }

    /** Detail filtering shared by the live renderer and permission-revocation tests. */
    public static java.util.List<net.minecraft.network.chat.Component> visibleEntryTexts(
            com.juicyslew.moonstation14.ms14.examine.ExamineResponse response, boolean showDetails) {
        if (response == null) return java.util.List.of();
        boolean detailsVisible = showDetails && response.detailsAllowed();
        return response.entries().stream()
                .filter(entry -> entry.visibility() == com.juicyslew.moonstation14.ms14.examine.ExamineEntry.Visibility.PUBLIC
                        || detailsVisible)
                .map(com.juicyslew.moonstation14.ms14.examine.ExamineEntry::text)
                .toList();
    }

    public static final class Popup {
        public static final long LIFETIME_MILLIS = 10_000;
        private final java.util.function.LongSupplier clock;
        private ExamineTarget target;
        private long epoch;
        private boolean pinned;
        private long openedAt;

        public Popup() { this(net.minecraft.Util::getMillis); }
        public Popup(java.util.function.LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }
        public void open(ExamineTarget target, long epoch) { open(target, epoch, clock.getAsLong()); }
        public void open(ExamineTarget target, long epoch, long now) {
            this.target = target; this.epoch = epoch; pinned = false; openedAt = now;
        }
        public void pin(boolean value) { pin(value, clock.getAsLong()); }
        public void pin(boolean value, long now) {
            if (target == null) return;
            if (pinned && !value) openedAt = now;
            pinned = value;
        }
        public boolean pinned() { return pinned; }
        public ExamineTarget target() { return target; }
        public boolean outsideClick() {
            if (target == null || pinned) return false;
            clear(); return true;
        }
        public boolean escape() {
            if (target == null) return false;
            clear(); return true;
        }
        public boolean valid(ExamineTarget current, long currentEpoch, boolean inRange) {
            return valid(current, currentEpoch, inRange, clock.getAsLong());
        }
        public boolean valid(ExamineTarget current, long currentEpoch, boolean inRange, long now) {
            if (target == null) return false;
            if (!target.equals(current) || epoch != currentEpoch || !inRange || (!pinned && now - openedAt >= LIFETIME_MILLIS)) clear();
            return target != null;
        }
        public void clear() { target = null; pinned = false; epoch = 0; openedAt = 0; }
    }
}

