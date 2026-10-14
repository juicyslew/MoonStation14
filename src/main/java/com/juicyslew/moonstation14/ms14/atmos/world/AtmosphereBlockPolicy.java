package com.juicyslew.moonstation14.ms14.atmos.world;

/** Explicit material composition for blocks whose gas sealing differs from collision geometry. */
public interface AtmosphereBlockPolicy {
    boolean atmospherePassable();
}

