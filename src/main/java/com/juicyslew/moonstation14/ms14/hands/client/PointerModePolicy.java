package com.juicyslew.moonstation14.ms14.hands.client;

/** Tracks why the popup cursor is open so empty-window cleanup cannot undo explicit intent. */
final class PointerModePolicy {
    enum Origin { CLOSED, MANUAL, POPUP_AUTOMATIC }
    private Origin origin = Origin.CLOSED;
    Origin origin() { return origin; }
    boolean open() { return origin != Origin.CLOSED; }
    void manualOpen() { origin = Origin.MANUAL; }
    void popupOpen() { if (origin == Origin.CLOSED) origin = Origin.POPUP_AUTOMATIC; }
    void dismiss() { origin = Origin.CLOSED; }
    void authorityClear() { origin = Origin.CLOSED; }
    static boolean keepOpenForWindow(boolean activeWire, boolean visibleStorageWindow) {
        return activeWire || visibleStorageWindow;
    }
    static boolean shouldRestoreHost(boolean pointerOpen, boolean screenNull, boolean bodyAuthorized) {
        return pointerOpen && screenNull && bodyAuthorized;
    }
    boolean closeWhenEmpty() {
        if (origin != Origin.POPUP_AUTOMATIC) return false;
        origin = Origin.CLOSED;
        return true;
    }
    /** Last analyzer close exits the shared pointer UI, even after manual unlock. */
    boolean lastAnalyzerClosed(boolean managedWindowsRemain, boolean ownsPointerScreen) {
        if (origin == Origin.CLOSED || managedWindowsRemain || !ownsPointerScreen) return false;
        dismiss();
        return true;
    }
}
