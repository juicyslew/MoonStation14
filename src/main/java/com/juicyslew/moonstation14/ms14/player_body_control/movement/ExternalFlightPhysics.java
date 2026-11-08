package com.juicyslew.moonstation14.ms14.player_body_control.movement;

/** Selects the vertical physics contract for an externally-launched flight lease. */
public enum ExternalFlightPhysics {
    /** Scripted impulses retain their vertical velocity for the duration of the lease. */
    SCRIPTED_IMPULSE,
    /** Portable throws continue ordinary gravity and vertical drag during their lease. */
    BALLISTIC_THROW
}
