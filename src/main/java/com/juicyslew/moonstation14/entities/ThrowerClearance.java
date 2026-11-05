package com.juicyslew.moonstation14.entities;

/** Per-flight guard against the launch overlap; deliberately has no time-based expiry. */
final class ThrowerClearance {
    private boolean cleared;

    void reset() { cleared = false; }

    /** Returns true when this swept thrower contact must be suppressed. */
    boolean suppress(boolean currentlyOverlapping) {
        if (cleared) return false;
        if (!currentlyOverlapping) cleared = true;
        // Also suppress the sweep which proves physical clearance; later re-entry is ordinary.
        return true;
    }

    boolean cleared() { return cleared; }
}
