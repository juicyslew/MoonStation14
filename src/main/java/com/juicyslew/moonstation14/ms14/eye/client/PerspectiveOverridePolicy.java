package com.juicyslew.moonstation14.ms14.eye.client;

/** Pure bookkeeping for a forced perspective and its restorable baseline. */
public final class PerspectiveOverridePolicy<T> {
    private T baseline;
    private boolean active;
    private boolean manuallyChanged;

    public boolean active() { return active; }
    public T baseline() { return baseline; }
    public boolean manuallyChanged() { return manuallyChanged; }

    /** Starts an override, capturing the current perspective only on first entry. */
    public void enter(T current) {
        if (active) return;
        baseline = current;
        active = true;
        manuallyChanged = false;
    }

    /** Replaces the baseline, used when a medical phase makes a manual choice restorable. */
    public void rebase(T current) {
        if (!active) enter(current);
        else {
            baseline = current;
            manuallyChanged = false;
        }
    }

    public void manualChange() { if (active) manuallyChanged = true; }

    /** Ends the override and returns the baseline unless the user changed perspective. */
    public T restore() {
        if (!active) return null;
        T result = manuallyChanged ? null : baseline;
        clear();
        return result;
    }

    public void clear() { baseline = null; active = false; manuallyChanged = false; }
}
