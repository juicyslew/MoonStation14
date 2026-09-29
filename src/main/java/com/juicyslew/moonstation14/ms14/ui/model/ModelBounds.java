package com.juicyslew.moonstation14.ms14.ui.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

final class ModelBounds {
    static final int MAX_ENTRIES = 512;
    private ModelBounds() {}

    static void size(int size, int cap) {
        if (cap < 1 || cap > MAX_ENTRIES || size > cap) throw new IllegalArgumentException("collection exceeds cap");
    }

    static <T extends Identified> void ids(List<T> entries, int cap) {
        Objects.requireNonNull(entries);
        size(entries.size(), cap);
        var seen = new HashSet<String>();
        for (T entry : entries) {
            if (entry == null || entry.id() == null || entry.id().isBlank() || entry.id().length() > 128
                    || !seen.add(entry.id()))
                throw new IllegalArgumentException("missing or duplicate stable ID");
        }
    }

    static void label(String label) {
        if (Objects.requireNonNull(label).length() > 256) throw new IllegalArgumentException("label too long");
    }

    interface Identified {
        String id();
    }
}
