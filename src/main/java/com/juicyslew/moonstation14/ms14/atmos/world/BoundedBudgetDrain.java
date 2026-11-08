package com.juicyslew.moonstation14.ms14.atmos.world;

import java.util.function.BooleanSupplier;

/** Runs bounded same-stage work slices while they make progress and the shared deadline permits. */
final class BoundedBudgetDrain {
    private BoundedBudgetDrain() { }

    static int drain(BooleanSupplier deadlineReached, BooleanSupplier sliceMadeProgress) {
        int slices = 0;
        boolean progress;
        do {
            progress = sliceMadeProgress.getAsBoolean();
            slices++;
        } while (progress && !deadlineReached.getAsBoolean());
        return slices;
    }
}

