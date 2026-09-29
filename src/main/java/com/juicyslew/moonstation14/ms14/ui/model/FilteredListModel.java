package com.juicyslew.moonstation14.ms14.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bounded, stable-ID navigation. Filtering rebuilds only on input/snapshot changes, never on draw. */
public final class FilteredListModel {
    public record Entry(String id, String label) implements ModelBounds.Identified {
        public Entry {
            ModelBounds.label(label);
        }
    }

    private final int cap;
    private final int pageSize;
    private List<Entry> entries = List.of();
    private final ArrayList<Entry> matches = new ArrayList<>();
    private String query = "";
    private String selectedId;
    private int first;
    private long revision = -1;

    public FilteredListModel(int cap, int pageSize) {
        ModelBounds.size(pageSize, cap);
        this.cap = cap;
        this.pageSize = pageSize;
    }

    public void snapshot(long revision, List<Entry> entries) {
        if (revision <= this.revision) return;
        ModelBounds.ids(entries, cap);
        this.entries = List.copyOf(entries);
        this.revision = revision;
        rebuild();
    }

    public void filter(String query) {
        Objects.requireNonNull(query);
        if (query.length() > 128) throw new IllegalArgumentException("query too long");
        if (this.query.equals(query)) return;
        this.query = query;
        rebuild();
    }

    private void rebuild() {
        matches.clear();
        String needle = query.toLowerCase(Locale.ROOT);
        boolean selectedVisible = false;
        for (Entry entry : entries) {
            if (entry.label().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entry);
                selectedVisible |= entry.id().equals(selectedId);
            }
        }
        if (!selectedVisible) selectedId = null;
        first = Math.min(first, Math.max(0, matches.size() - pageSize));
    }

    public boolean select(String id) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).id().equals(id)) {
                selectedId = id;
                if (i < first) first = i;
                else if (i >= first + pageSize) first = i - pageSize + 1;
                return true;
            }
        }
        return false;
    }

    public void move(int direction) {
        if (matches.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < matches.size(); i++) if (matches.get(i).id().equals(selectedId)) index = i;
        int next = index < 0 ? (direction < 0 ? matches.size() - 1 : 0)
                : Math.max(0, Math.min(matches.size() - 1, index + Integer.signum(direction)));
        select(matches.get(next).id());
    }

    public void scroll(int delta) {
        first = (int) Math.max(0L, Math.min((long) matches.size() - Math.min(pageSize, matches.size()), (long) first + delta));
    }

    public int visibleCount() { return Math.min(pageSize, matches.size() - first); }
    public Entry visibleAt(int row) {
        if (row < 0 || row >= visibleCount()) throw new IndexOutOfBoundsException(row);
        return matches.get(first + row);
    }
    public int matchCount() { return matches.size(); }
    public int pageSize() { return pageSize; }
    public int first() { return first; }
    public String selectedId() { return selectedId; }
    public long revision() { return revision; }
}
