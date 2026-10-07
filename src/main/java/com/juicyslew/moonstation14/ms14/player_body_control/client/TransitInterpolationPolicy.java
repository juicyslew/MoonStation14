package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;

import java.util.UUID;

/** Limits native client interpolation to consecutive transit samples from one unchanged holder. */
final class TransitInterpolationPolicy {
    private static final int INTERPOLATION_STEPS = 2;
    private Key previousTransit;

    int accept(long epoch, UUID body, GhostControlPayloads.Snapshot.MovementMode mode, UUID owner) {
        if (mode != GhostControlPayloads.Snapshot.MovementMode.TRANSIT || owner == null) {
            previousTransit = null;
            return 0;
        }

        Key current = new Key(epoch, body, owner);
        boolean consecutiveContext = current.equals(previousTransit);
        previousTransit = current;
        return consecutiveContext ? INTERPOLATION_STEPS : 0;
    }

    void clear() { previousTransit = null; }

    private record Key(long epoch, UUID body, UUID owner) { }
}
