package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import java.util.function.Supplier;

/** Limits the client prediction barrier to post-move collision failures, not arbitrary input errors. */
final class CharacterPredictionFailurePolicy {
    private CharacterPredictionFailurePolicy() { }

    /** Called only after body.move; distinguishes invalid actual displacement from pre-move vectors. */
    static MovementVector postMoveDisplacement(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new NonfinitePostMoveDisplacement();
        return new MovementVector(x, y, z);
    }

    /** Null means the step already moved and failed; callers must stop prediction until a new session. */
    static <T> T run(Supplier<T> step, GhostPredictionHistory history) {
        try {
            return step.get();
        } catch (IllegalArgumentException exception) {
            if (!(exception instanceof NonfinitePostMoveDisplacement)
                    && !"collision resolution must be finite and bounded by requested displacement".equals(exception.getMessage()))
                throw exception;
            history.onAuthorityBarrier();
            return null;
        }
    }

    private static final class NonfinitePostMoveDisplacement extends IllegalArgumentException { }
}

