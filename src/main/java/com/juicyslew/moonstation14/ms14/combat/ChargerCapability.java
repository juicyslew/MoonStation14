package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;

/** Supported source concepts, mapped to resolved local profile families. */
public enum ChargerCapability {
    BATTERY_AMMO_PROVIDER, POWER_CELL;
    public static final Codec<ChargerCapability> CODEC = Codec.STRING.xmap(
            value -> ChargerCapability.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
            value -> value.name().toLowerCase(java.util.Locale.ROOT));
}
