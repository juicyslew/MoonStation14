package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Objects;

/**
 * Swaps in an empty TEG fixture registry only for an explicitly scoped native GameTest batch.
 * This is a SavedData isolation boundary, not a capacity test: GameTest tags do not isolate runs,
 * and this does not test more than eight simultaneous owners in one registry.
 */
final class TegFixtureRegistryScope implements AutoCloseable {
    private final ServerLevel level;
    private final TegFixtureSavedData original;
    private final List<TegFixtureSavedData.Entry> originalEntries;
    private final TegFixtureSavedData fresh;
    private boolean closed;

    private TegFixtureRegistryScope(ServerLevel level, TegFixtureSavedData original,
                                    List<TegFixtureSavedData.Entry> originalEntries,
                                    TegFixtureSavedData fresh) {
        this.level = level;
        this.original = original;
        this.originalEntries = originalEntries;
        this.fresh = fresh;
    }

    static TegFixtureRegistryScope acquire(ServerLevel level) {
        Objects.requireNonNull(level);
        TegFixtureSavedData original = TegFixtureSavedData.forMutation(level);
        List<TegFixtureSavedData.Entry> snapshot = original.entries();
        TegFixtureSavedData fresh = new TegFixtureSavedData();
        try {
            level.getDataStorage().set(TegFixtureSavedData.DATA_NAME, fresh);
            require(TegFixtureSavedData.existing(level).orElse(null) == fresh,
                    "fresh TEG fixture registry was not installed");
            require(original.entries().equals(snapshot), "original TEG fixture registry changed during install");
            require(fresh.count() == 0 && fresh.count() <= TegFixtureSavedData.MAX_FIXTURES,
                    "fresh TEG fixture registry is not empty/within capacity");
            return new TegFixtureRegistryScope(level, original, snapshot, fresh);
        } catch (RuntimeException | Error failure) {
            try {
                level.getDataStorage().set(TegFixtureSavedData.DATA_NAME, original);
                original.setDirty();
            } catch (RuntimeException | Error restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        level.getDataStorage().set(TegFixtureSavedData.DATA_NAME, original);
        original.setDirty(); // A normal disposable native test world may save this registry.
        closed = true;
        require(TegFixtureSavedData.existing(level).orElse(null) == original,
                "original TEG fixture registry was not restored by identity");
        require(original.entries().equals(originalEntries),
                "original TEG fixture entries (including tokens and ownership) changed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
