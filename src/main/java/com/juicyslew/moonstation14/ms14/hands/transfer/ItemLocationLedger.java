package com.juicyslew.moonstation14.ms14.hands.transfer;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Synchronized pure ledger. A successful operation replaces the immutable snapshot atomically. */
public final class ItemLocationLedger {
    private ItemLocationSnapshot snapshot = new ItemLocationSnapshot(0, Map.of());

    public ItemLocationLedger() { }

    ItemLocationLedger(ItemLocationSnapshot initialSnapshot) {
        snapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
    }

    public synchronized ItemLocationSnapshot snapshot() { return snapshot; }

    public synchronized Result register(ItemToken token, ItemLocation location) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(location, "location");
        if (snapshot.locations().containsKey(token)) return Result.rejected(snapshot, Rejection.DUPLICATE_ITEM);
        if (snapshot.size() >= ItemLocationSnapshot.MAX_ITEMS) return Result.rejected(snapshot, Rejection.ITEM_LIMIT);
        if (isOccupied(location, null, snapshot.locations())) {
            return Result.rejected(snapshot, Rejection.DESTINATION_OCCUPIED);
        }
        if (location instanceof ItemLocation.StorageCell cell && !validStorageEntry(token, cell, snapshot.locations())) {
            return Result.rejected(snapshot, Rejection.NESTED_STORAGE);
        }
        if (snapshot.revision() == Long.MAX_VALUE) return Result.rejected(snapshot, Rejection.REVISION_OVERFLOW);
        LinkedHashMap<ItemToken, ItemLocation> next = new LinkedHashMap<>(snapshot.locations());
        next.put(token, location);
        snapshot = new ItemLocationSnapshot(snapshot.revision() + 1, next);
        return Result.success(snapshot);
    }

    /** Compare-and-move: both revision and exact source must match; failures preserve state. */
    public synchronized Result compareAndMove(long expectedRevision, ItemToken token,
            ItemLocation expectedSource, ItemLocation destination) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(expectedSource, "expectedSource");
        Objects.requireNonNull(destination, "destination");
        if (expectedRevision != snapshot.revision()) return Result.rejected(snapshot, Rejection.STALE_REVISION);
        ItemLocation source = snapshot.locations().get(token);
        if (source == null) return Result.rejected(snapshot, Rejection.MISSING_ITEM);
        if (!source.equals(expectedSource)) return Result.rejected(snapshot, Rejection.SOURCE_MISMATCH);
        if (source.equals(destination)) return Result.rejected(snapshot, Rejection.SAME_LOCATION);
        if (isOccupied(destination, token, snapshot.locations())) {
            return Result.rejected(snapshot, Rejection.DESTINATION_OCCUPIED);
        }
        if (destination instanceof ItemLocation.StorageCell cell &&
                !validStorageEntry(token, cell, snapshot.locations())) {
            return Result.rejected(snapshot, Rejection.NESTED_STORAGE);
        }
        if (snapshot.revision() == Long.MAX_VALUE) return Result.rejected(snapshot, Rejection.REVISION_OVERFLOW);
        LinkedHashMap<ItemToken, ItemLocation> next = new LinkedHashMap<>(snapshot.locations());
        next.put(token, destination);
        snapshot = new ItemLocationSnapshot(snapshot.revision() + 1, next);
        return Result.success(snapshot);
    }

    /** Exact ledger identity only: arbitrary geometric storage overlap belongs to an adapter. */
    private static boolean isOccupied(ItemLocation destination, ItemToken movingToken,
            Map<ItemToken, ItemLocation> locations) {
        for (Map.Entry<ItemToken, ItemLocation> entry : locations.entrySet()) {
            if (!entry.getKey().equals(movingToken) && entry.getValue().equals(destination)) return true;
        }
        return false;
    }

    /* Nesting is unapproved: storage occupants may not themselves own storage, and an item
       already used as a container may not be placed in a storage cell. This is a bounded scan. */
    private static boolean validStorageEntry(ItemToken token, ItemLocation.StorageCell target,
            Map<ItemToken, ItemLocation> locations) {
        if (token.equals(target.container())) return false;
        ItemLocation parentLocation = locations.get(target.container());
        if (parentLocation == null || parentLocation instanceof ItemLocation.StorageCell) return false;
        for (Map.Entry<ItemToken, ItemLocation> entry : locations.entrySet()) {
            if (entry.getKey().equals(token) && entry.getValue() instanceof ItemLocation.StorageCell) return false;
            if (entry.getValue() instanceof ItemLocation.StorageCell cell &&
                    (cell.container().equals(token) || cell.container().equals(target.container()) &&
                            entry.getKey().equals(target.container()))) return false;
        }
        return true;
    }

    public enum Rejection { DUPLICATE_ITEM, ITEM_LIMIT, STALE_REVISION, MISSING_ITEM, SOURCE_MISMATCH,
        SAME_LOCATION, DESTINATION_OCCUPIED, NESTED_STORAGE, REVISION_OVERFLOW }

    public record Result(boolean accepted, ItemLocationSnapshot snapshot, Rejection rejection) {
        public Result {
            Objects.requireNonNull(snapshot, "snapshot");
            if (accepted == (rejection != null)) throw new IllegalArgumentException("result status/rejection mismatch");
        }
        static Result success(ItemLocationSnapshot snapshot) { return new Result(true, snapshot, null); }
        static Result rejected(ItemLocationSnapshot snapshot, Rejection reason) { return new Result(false, snapshot, reason); }
    }
}
