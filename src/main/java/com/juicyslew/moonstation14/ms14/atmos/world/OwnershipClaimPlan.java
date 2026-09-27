package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Predicate;

/** Incremental, deterministic commit cursor for cells from a proven finite ownership search. */
final class OwnershipClaimPlan {
    private final List<BlockPos> positions;
    private int cursor;
    private boolean cancelled;

    /** The search's terminal snapshot owns an immutable cached list; retain it rather than copying it. */
    OwnershipClaimPlan(List<BlockPos> positions) {
        this.positions = positions;
    }

    /** Commits at most {@code budget} positions. A failed validation cancels the uncommitted tail. */
    int advance(int budget, Predicate<BlockPos> commit) {
        if (budget < 0) throw new IllegalArgumentException("Claim budget cannot be negative");
        if (commit == null) throw new IllegalArgumentException("Commit action cannot be null");
        int written = 0;
        while (!cancelled && cursor < positions.size() && written < budget) {
            BlockPos position = positions.get(cursor);
            if (!commit.test(position)) {
                cancelled = true;
                break;
            }
            cursor++;
            written++;
        }
        return written;
    }

    void cancel() { cancelled = true; }
    boolean isCancelled() { return cancelled; }
    boolean isComplete() { return !cancelled && cursor == positions.size(); }
    int cursor() { return cursor; }
    int size() { return positions.size(); }
}
