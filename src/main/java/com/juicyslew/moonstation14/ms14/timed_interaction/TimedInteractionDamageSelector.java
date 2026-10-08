package com.juicyslew.moonstation14.ms14.timed_interaction;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Pure server-side policy for selecting active operations interrupted by damage. */
public final class TimedInteractionDamageSelector {
    private TimedInteractionDamageSelector() { }

    public static List<Long> select(TimedOperationState state, double typedDamage,
                                    ToDoubleFunction<TimedOperation> thresholdFor) {
        return state.operations().stream()
                .filter(op -> op.phase() == TimedOperation.Phase.ACTIVE && op.breakOnDamage())
                .filter(op -> {
                    double threshold = thresholdFor.applyAsDouble(op);
                    return Double.isFinite(threshold) && threshold > 0 && typedDamage >= threshold;
                })
                .map(TimedOperation::id)
                .toList();
    }
}
