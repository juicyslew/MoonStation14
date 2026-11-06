package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/** Stable UUID-derived diagnostic IDs, scoped to currently loaded ships. */
final class ShuttleFixtureShipIds {
    private ShuttleFixtureShipIds() { }

    static String display(Collection<UUID> loadedIds, UUID id) {
        String hex = hex(id);
        int length = 8;
        outer: for (; length < hex.length(); length++) {
            String candidate = hex.substring(0, length);
            for (UUID other : loadedIds)
                if (!other.equals(id) && hex(other).startsWith(candidate)) continue outer;
            break;
        }
        return "ship-" + hex.substring(0, length);
    }

    static UUID resolve(Collection<UUID> loadedIds, String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return loadedIds.contains(uuid) ? uuid : null;
        } catch (IllegalArgumentException ignored) { }

        String key = value.startsWith("ship-") ? value.substring(5) : value;
        if (key.length() < 1 || key.length() > 32 || !key.matches("[0-9a-fA-F]+")) return null;
        key = key.toLowerCase(Locale.ROOT);
        UUID match = null;
        for (UUID candidate : loadedIds) if (hex(candidate).startsWith(key)) {
            if (match != null) return null;
            match = candidate;
        }
        return match;
    }

    private static String hex(UUID id) { return id.toString().replace("-", ""); }
}
