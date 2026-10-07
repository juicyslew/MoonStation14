package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;

/** Maps an exact live containment owner to the movement authority advertised to the client. */
public final class CharacterMovementModeResolver {
    private CharacterMovementModeResolver() { }

    public static GhostControlPayloads.Snapshot.MovementMode fromExactOwner(ContainedBodyOwner owner) {
        if (owner == null) return GhostControlPayloads.Snapshot.MovementMode.GROUNDED;
        return switch (owner.stage()) {
            case UNIT -> GhostControlPayloads.Snapshot.MovementMode.UNIT;
            case STATIONARY -> GhostControlPayloads.Snapshot.MovementMode.UNIT;
            case TRANSIT -> GhostControlPayloads.Snapshot.MovementMode.TRANSIT;
        };
    }
}
