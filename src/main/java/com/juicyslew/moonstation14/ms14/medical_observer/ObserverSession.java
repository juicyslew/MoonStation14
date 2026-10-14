package com.juicyslew.moonstation14.ms14.medical_observer;

import java.util.UUID;

/** Server-owned observer association. The corpse is never replaced by the camera harness. */
public record ObserverSession(UUID ownerId, UUID corpseBodyId, UUID ghostId, long epoch,
                              Mode mode, boolean returnAvailable,
                              BoundObserverAuthority boundAuthority, long authorityGeneration) {
    public enum Mode { CORPSE, GHOST }

    public ObserverSession {
        if (ownerId == null || corpseBodyId == null || epoch < 0 || mode == null)
            throw new IllegalArgumentException("invalid observer session");
        if (mode == Mode.GHOST && ghostId == null)
            throw new IllegalArgumentException("ghost mode requires a harness id");
        if (mode == Mode.CORPSE && ghostId != null)
            throw new IllegalArgumentException("corpse mode cannot retain a ghost id");
        if (boundAuthority == null && authorityGeneration != 0)
            throw new IllegalArgumentException("native session cannot have an authority generation");
    }

    public ObserverSession(UUID ownerId, UUID corpseBodyId, UUID ghostId, long epoch,
                           Mode mode, boolean returnAvailable) {
        this(ownerId, corpseBodyId, ghostId, epoch, mode, returnAvailable, null, 0);
    }
}

