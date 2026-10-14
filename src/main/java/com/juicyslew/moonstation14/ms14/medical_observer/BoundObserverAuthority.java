package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import com.juicyslew.moonstation14.ms14.character.components.ObserverMovementComponent;

/**
 * A transient, trusted bridge to the backend that owns a non-player corpse. Implementations
 * must revalidate their current owner/body/epoch claim on every callback; this object/session
 * reference is not itself proof of authority. Ghosts returned by startGhost are already staged
 * by that backend: implementations must not spawn a second ghost or start another controller.
 */
public interface BoundObserverAuthority {
    boolean valid(ServerPlayer owner, LivingEntity corpse, ObserverSession session);

    /** Return the backend's staged ghost and its new authority generation, or null on refusal. */
    GhostStartResult startGhost(ServerPlayer owner, LivingEntity corpse, long corpseEpoch,
                                ObserverMovementComponent observerMovement);

    /** Restore the backend's ACTIVE body state. Observer association is cleared only on true. */
    boolean returnToBody(ServerPlayer owner, LivingEntity body, ObserverSession session);

    /** An already-admitted ghost owned and staged by the authority backend. */
    record GhostStartResult(Entity ghost, long authorityGeneration) {
        public GhostStartResult {
            if (ghost == null || authorityGeneration <= 0)
                throw new IllegalArgumentException("invalid staged ghost result");
        }
    }
}

