package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Position basis correction for Sable's owned-character tryStepUp narrowphase probe. */
public final class SableOwnedStepProbe {
    private SableOwnedStepProbe() { }

    /** Copy immutable body base and apply the solver's accumulated collision displacement. */
    public static Vector3d offsetCandidate(Vector3d copiedCandidate, Vector3dc collisionMotion) {
        return copiedCandidate.add(collisionMotion);
    }
}
