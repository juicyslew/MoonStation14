package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.ms14.eye.client.PerspectiveOverridePolicy;

/** Camera override bookkeeping, independent of Minecraft camera APIs for deterministic tests. */
public final class MedicalPerspectivePolicy<T> {
    public record Decision<T>(boolean pushRear, T restore) { }
    private final PerspectiveOverridePolicy<T> override = new PerspectiveOverridePolicy<>();

    public static boolean comfortActive(boolean enabled, boolean requested, boolean medicalActive,
                                        boolean contained, boolean manualOptOut) {
        return enabled && requested && !medicalActive && !contained && !manualOptOut;
    }

    public Decision<T> state(boolean criticalOrDead, T current) {
        if (criticalOrDead) {
            override.enter(current);
            return new Decision<>(true, null);
        }
        if (!override.active()) return new Decision<>(false, null);
        return new Decision<>(false, override.restore());
    }

    /** Starts the Dead phase; a Critical-phase manual choice becomes Dead's restorable baseline. */
    public Decision<T> dead(T current) {
        if (!override.active()) return state(true, current);
        if (override.manuallyChanged()) {
            override.rebase(current);
        }
        return new Decision<>(true, null);
    }

    public void manualChange() { override.manualChange(); }
    public Decision<T> observationChanged() {
        if (!override.active()) return new Decision<>(false, null);
        return new Decision<>(false, override.restore());
    }
}
