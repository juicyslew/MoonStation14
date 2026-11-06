package com.juicyslew.moonstation14;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation preferences. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ANGULAR_RECOVERY_COMFORT_CAMERA = BUILDER
            .comment("Enable the angular-recovery comfort camera (may cause motion sickness).")
            .define("angularRecoveryComfortCamera", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() { }
}
