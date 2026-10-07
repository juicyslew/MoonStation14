package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;

/** Bounded, allocation-light queue of seeds requiring fresh ownership checks. */
final class OwnershipRetryCandidates {
    private final ArrayDeque<Batch> batches = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> deferred = new ArrayDeque<>();

    /** Retains a view of the immutable search snapshot; does not copy a potentially large tail. */
    void add(List<BlockPos> candidates) {
        if (!candidates.isEmpty()) batches.addLast(new Batch(candidates));
    }

    /**
     * Inspects at most {@code budget} candidates. Return true to consume the candidate (including
     * rejected/blocked seeds); false retains it when downstream seed capacity is temporarily full.
     */
    int drain(int budget, Predicate<BlockPos> inspect) {
        return drain(budget, () -> false, inspect);
    }

    /** Deadline-aware bounded drain; admits one candidate operation before checking the clock. */
    int drain(int budget, BooleanSupplier deadlineReached, Predicate<BlockPos> inspect) {
        if (budget < 0) throw new IllegalArgumentException("Retry budget cannot be negative");
        if (inspect == null) throw new IllegalArgumentException("Inspection action cannot be null");
        if (deadlineReached == null) throw new IllegalArgumentException("Deadline predicate cannot be null");
        int inspected = 0;
        int deferredChecks = Math.min(deferred.size(), Math.min(32, budget / 4));
        for (int i = 0; i < deferredChecks && (inspected == 0 || !deadlineReached.getAsBoolean()); i++) {
            BlockPos candidate = deferred.removeFirst();
            boolean consumed = inspect.test(candidate);
            inspected++;
            if (!consumed) deferred.addLast(candidate);
        }
        while (inspected < budget && !batches.isEmpty()
                && (inspected == 0 || !deadlineReached.getAsBoolean())) {
            Batch batch = batches.peekFirst();
            BlockPos candidate = batch.positions.get(batch.cursor);
            boolean consumed = inspect.test(candidate);
            inspected++;
            if (!consumed) deferred.addLast(candidate);
            batch.cursor++;
            if (batch.cursor == batch.positions.size()) batches.removeFirst();
        }
        return inspected;
    }

    boolean isEmpty() { return batches.isEmpty() && deferred.isEmpty(); }
    /** O(1) pending-work diagnostic; batches stand for arbitrarily large immutable suffixes. */
    int pendingWorkItems() { return batches.size() + deferred.size(); }

    private static final class Batch {
        final List<BlockPos> positions;
        int cursor;

        Batch(List<BlockPos> positions) { this.positions = positions; }
    }
}
