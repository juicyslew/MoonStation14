package com.juicyslew.moonstation14.ms14.mobs.client;

/** Camera override bookkeeping, independent of Minecraft camera APIs for deterministic tests. */
public final class MedicalPerspectivePolicy<T> {
    public record Decision<T>(boolean pushRear, T restore) { }
    private T prior;
    private boolean active;
    private boolean manuallyChanged;

    public Decision<T> state(boolean criticalOrDead, T current) {
        if (criticalOrDead) {
            if (!active) { prior = current; manuallyChanged = false; }
            active = true;
            return new Decision<>(true, null);
        }
        if (!active) return new Decision<>(false, null);
        T restore = manuallyChanged ? null : prior;
        active = false;
        prior = null;
        manuallyChanged = false;
        return new Decision<>(false, restore);
    }

    /** Starts the Dead phase; a Critical-phase manual choice becomes Dead's restorable baseline. */
    public Decision<T> dead(T current) {
        if (!active) return state(true, current);
        if (manuallyChanged) {
            prior = current;
            manuallyChanged = false;
        }
        return new Decision<>(true, null);
    }

    public void manualChange() { if (active) manuallyChanged = true; }
    public Decision<T> observationChanged() {
        if (!active) return new Decision<>(false, null);
        T restore = manuallyChanged ? null : prior;
        active = false; prior = null; manuallyChanged = false;
        return new Decision<>(false, restore);
    }
}
