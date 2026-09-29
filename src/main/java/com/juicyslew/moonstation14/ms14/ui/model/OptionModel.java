package com.juicyslew.moonstation14.ms14.ui.model;

import java.util.List;

/** Tabs and option selectors share stable-ID selection; selection itself is local, not a machine command. */
public final class OptionModel {
    public record Option(String id, String label) implements ModelBounds.Identified {
        public Option { ModelBounds.label(label); }
    }

    private final int cap;
    private List<Option> options = List.of();
    private String selectedId;
    private long revision = -1;

    public OptionModel(int cap) {
        ModelBounds.size(0, cap);
        this.cap = cap;
    }

    public void snapshot(long revision, List<Option> options) {
        if (revision <= this.revision) return;
        ModelBounds.ids(options, cap);
        this.options = List.copyOf(options);
        this.revision = revision;
        if (indexOf(selectedId) < 0) selectedId = options.isEmpty() ? null : options.getFirst().id();
    }

    public boolean select(String id) {
        if (indexOf(id) < 0) return false;
        selectedId = id;
        return true;
    }

    public void step(int delta) {
        if (options.isEmpty()) return;
        int next = (int) Math.max(0L, Math.min(options.size() - 1L, (long) indexOf(selectedId) + Integer.signum(delta)));
        selectedId = options.get(next).id();
    }

    private int indexOf(String id) {
        for (int i = 0; i < options.size(); i++) if (options.get(i).id().equals(id)) return i;
        return -1;
    }

    public String selectedId() { return selectedId; }
    public int size() { return options.size(); }
    public Option at(int index) { return options.get(index); }
    public long revision() { return revision; }
}
