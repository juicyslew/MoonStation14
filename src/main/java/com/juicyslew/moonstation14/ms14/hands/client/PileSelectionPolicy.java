package com.juicyslew.moonstation14.ms14.hands.client;

import java.util.List;
import java.util.UUID;

/** Stable, presentation-only selection over the currently visible bounded pile. */
public final class PileSelectionPolicy {
    private UUID selected;

    public UUID update(List<UUID> visible) {
        if (visible == null || visible.isEmpty()) { selected = null; return null; }
        if (selected == null || !visible.contains(selected)) selected = visible.getFirst();
        return selected;
    }

    public UUID cycle(List<UUID> visible) {
        if (visible == null || visible.isEmpty()) { selected = null; return null; }
        int index = selected == null ? -1 : visible.indexOf(selected);
        selected = visible.get(index < 0 ? 0 : (index + 1) % visible.size());
        return selected;
    }

    public int index(List<UUID> visible) { return visible == null || selected == null ? -1 : visible.indexOf(selected); }
    public void clear() { selected = null; }
}
