package com.juicyslew.moonstation14.ms14.medical_observer.client;

/** Keeps cached medical observer snapshots advisory to, and scoped by, the canonical controlled actor. */
public final class ObserverMedicalScopePolicy {
    private ObserverMedicalScopePolicy() { }

    public static boolean expose(boolean canonicalGhost, boolean characterControl,
                                 boolean snapshotMatchesOwnedCharacter) {
        if (canonicalGhost) return false;
        return !characterControl || snapshotMatchesOwnedCharacter;
    }
}
