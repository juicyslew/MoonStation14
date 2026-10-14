package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import java.util.UUID;

/** Identity check for acknowledging a locally predicted timed interaction. */
public final class TimedInteractionPredictionIdentity {
    private TimedInteractionPredictionIdentity() { }

    public static boolean matches(UUID predictionBody, long predictionSequence, UUID snapshotBody, long snapshotSequence) {
        return predictionSequence == snapshotSequence && predictionBody.equals(snapshotBody);
    }
}

