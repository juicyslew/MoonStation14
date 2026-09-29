package com.juicyslew.moonstation14.ms14.hands.transfer;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, deterministic bounded view of the canonical item-location ledger. */
public final class ItemLocationSnapshot {
    public static final int MAX_ITEMS = 4096;
    private final long revision;
    private final Map<ItemToken, ItemLocation> locations;

    ItemLocationSnapshot(long revision, Map<ItemToken, ItemLocation> locations) {
        if (revision < 0) throw new IllegalArgumentException("revision must be nonnegative");
        if (locations.size() > MAX_ITEMS) throw new IllegalArgumentException("item count exceeds " + MAX_ITEMS);
        this.revision = revision;
        this.locations = Collections.unmodifiableMap(new LinkedHashMap<>(locations));
    }

    public long revision() { return revision; }
    public int size() { return locations.size(); }
    public Map<ItemToken, ItemLocation> locations() { return locations; }
    public Optional<ItemLocation> locationOf(ItemToken token) {
        return Optional.ofNullable(locations.get(Objects.requireNonNull(token, "token")));
    }
}
