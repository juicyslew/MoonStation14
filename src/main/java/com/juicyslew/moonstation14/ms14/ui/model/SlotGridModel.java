package com.juicyslew.moonstation14.ms14.ui.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Display-only slot snapshots. Drag is opt-in and emits intent only; no inventory mutation occurs here. */
public final class SlotGridModel {
    public record Slot(String id, String display, int count) implements ModelBounds.Identified {
        public Slot {
            ModelBounds.label(display);
            if (count < 0) throw new IllegalArgumentException("negative count");
        }
    }
    public record DragIntent(String fromId, String toId, long baseRevision) {}

    private final int cap;
    private List<Slot> slots = List.of();
    private long revision = -1;
    private Consumer<DragIntent> dragOwner; // null means disabled

    public SlotGridModel(int cap) {
        ModelBounds.size(0, cap);
        this.cap = cap;
    }

    public void snapshot(long revision, List<Slot> slots) {
        if (revision <= this.revision) return;
        ModelBounds.ids(slots, cap);
        this.slots = List.copyOf(slots);
        this.revision = revision;
    }

    /** Owner must explicitly supply a callback; null disables drag. */
    public void dragOwner(Consumer<DragIntent> owner) { dragOwner = owner; }

    public boolean drag(String fromId, String toId) {
        if (dragOwner == null || revision < 0 || Objects.equals(fromId, toId) || !contains(fromId) || !contains(toId)) return false;
        dragOwner.accept(new DragIntent(fromId, toId, revision));
        return true;
    }

    private boolean contains(String id) {
        for (Slot slot : slots) if (slot.id().equals(id)) return true;
        return false;
    }

    public int size() { return slots.size(); }
    public Slot at(int index) { return slots.get(index); }
    public long revision() { return revision; }
}
