package com.juicyslew.moonstation14.ms14.ui.model;

import java.util.List;
import java.util.Objects;

/** Queue/preset command contract; order and results remain snapshot-only until authority responds. */
public final class QueueIntentModel {
    public record Entry(String id, String label) implements ModelBounds.Identified {
        public Entry { ModelBounds.label(label); }
    }
    public enum Action { ENQUEUE, REMOVE, MOVE_BEFORE, APPLY_PRESET }
    public record Intent(Action action, String id, String beforeId, long baseRevision) {}

    private final int cap;
    private List<Entry> queue = List.of();
    private List<Entry> presets = List.of();
    private long revision = -1;

    public QueueIntentModel(int cap) {
        ModelBounds.size(0, cap);
        this.cap = cap;
    }

    public void snapshot(long revision, List<Entry> queue, List<Entry> presets) {
        if (revision <= this.revision) return;
        ModelBounds.ids(queue, cap);
        ModelBounds.ids(presets, cap);
        this.queue = List.copyOf(queue);
        this.presets = List.copyOf(presets);
        this.revision = revision;
    }

    public Intent enqueue(String presetId) { return contains(presets, presetId) ? intent(Action.ENQUEUE, presetId, null) : null; }
    public Intent applyPreset(String presetId) { return contains(presets, presetId) ? intent(Action.APPLY_PRESET, presetId, null) : null; }
    public Intent remove(String id) { return contains(queue, id) ? intent(Action.REMOVE, id, null) : null; }
    /** Null beforeId means move to end. */
    public Intent moveBefore(String id, String beforeId) {
        if (!contains(queue, id) || (beforeId != null && !contains(queue, beforeId)) || Objects.equals(id, beforeId)) return null;
        return intent(Action.MOVE_BEFORE, id, beforeId);
    }

    private Intent intent(Action action, String id, String beforeId) {
        return revision < 0 ? null : new Intent(action, id, beforeId, revision);
    }

    private static boolean contains(List<Entry> entries, String id) {
        for (Entry entry : entries) if (entry.id().equals(id)) return true;
        return false;
    }

    public int queueSize() { return queue.size(); }
    public Entry queuedAt(int index) { return queue.get(index); }
    public int presetSize() { return presets.size(); }
    public Entry presetAt(int index) { return presets.get(index); }
    public long revision() { return revision; }
}
