package com.juicyslew.moonstation14.ms14.actions.client;

import java.util.UUID;

/** Pure guards and stable binding ownership for the numbered action-key path. */
final class BodyActionKeyPolicy {
    private BodyActionKeyPolicy() { }
    static boolean eligible(boolean hasWorld, boolean noScreen, boolean ownsBody, boolean bodyInWorld, long epoch) {
        return hasWorld && noScreen && ownsBody && bodyInWorld && epoch > 0;
    }
    static boolean snapshotMatches(UUID ownedBody, long epoch, UUID snapshotBody, long snapshotEpoch) {
        return ownedBody != null && ownedBody.equals(snapshotBody) && epoch > 0 && snapshotEpoch == epoch;
    }
    static int bindingWinner(String[] keys, int index) {
        for (int i = 0; i < index; i++) if (keys[i].equals(keys[index])) return i;
        return index;
    }
    static boolean consumeDebugChord(boolean f3Held, Runnable discardActionClicks) {
        if (!f3Held) return false;
        discardActionClicks.run();
        return true;
    }
}
