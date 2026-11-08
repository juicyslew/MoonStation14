package com.juicyslew.moonstation14.ms14.hands.client;

import java.util.List;
import java.util.UUID;

/** Stable, presentation-only selection over the currently visible bounded pile. */
public final class PileSelectionPolicy {
    private UUID selected;
    private List<UUID> previousOrderedIDs = List.of();

    public UUID update(List<UUID> visible) {
        if (visible == null || visible.isEmpty()) { clear(); return null; }
        if (!visible.equals(previousOrderedIDs)) selected = visible.getFirst();
        else if (selected == null || !visible.contains(selected)) selected = visible.getFirst();
        previousOrderedIDs = List.copyOf(visible);
        return selected;
    }

    public UUID cycle(List<UUID> visible) {
        if (visible == null || visible.isEmpty()) { clear(); return null; }
        if (!visible.equals(previousOrderedIDs)) selected = null;
        int index = selected == null ? -1 : visible.indexOf(selected);
        selected = visible.get(index < 0 ? 0 : (index + 1) % visible.size());
        previousOrderedIDs = List.copyOf(visible);
        return selected;
    }

    public int index(List<UUID> visible) { return visible == null || selected == null ? -1 : visible.indexOf(selected); }
    public void clear() { selected = null; previousOrderedIDs = List.of(); }
}

