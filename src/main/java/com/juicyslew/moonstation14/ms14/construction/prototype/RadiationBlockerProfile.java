package com.juicyslew.moonstation14.ms14.construction.prototype;

/** Typed optional blocker data inherited into resolved construction-entity profiles. */
public record RadiationBlockerProfile(double resistance) {
    public RadiationBlockerProfile {
        if (!Double.isFinite(resistance) || resistance < 0) throw new IllegalArgumentException("invalid radiation blocker resistance");
    }
}

