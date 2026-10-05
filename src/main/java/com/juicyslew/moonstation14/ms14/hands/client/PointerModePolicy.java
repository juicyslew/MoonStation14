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
    boolean closeWhenEmpty() {
        if (origin != Origin.POPUP_AUTOMATIC) return false;
        origin = Origin.CLOSED;
        return true;
    }
}
