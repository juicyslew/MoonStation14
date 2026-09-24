package com.juicyslew.moonstation14.ms14.prototype;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A small read-only catalog of prototypes. The catalog owns its map and keeps
 * insertion order so diagnostics and tests are deterministic.
 *
 * @param <T> the catalog value type
 */
public final class PrototypeCatalog<T> {
    private final Map<ResourceLocation, T> entries;

    public PrototypeCatalog(Map<ResourceLocation, T> entries) {
        Objects.requireNonNull(entries, "entries");
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public T get(ResourceLocation id) {
        return entries.get(id);
    }

    public boolean contains(ResourceLocation id) {
        return entries.containsKey(id);
    }

    public Set<ResourceLocation> keys() {
        return entries.keySet();
    }

    public Collection<T> values() {
        return entries.values();
    }

    /** Returns the immutable map owned by this catalog. */
    public Map<ResourceLocation, T> asMap() {
        return entries;
    }

    public int size() {
        return entries.size();
    }
}
