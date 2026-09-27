package com.juicyslew.moonstation14.ms14.atmos.exposure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Explicit future respiration boundary. No lung, saturation, oxygen, or poison model is implemented. */
public final class RespirationExposure {
    private RespirationExposure() { }

    /** Intentionally deferred until actual upstream respiration owners and semantics are identified. */
    public static void exposeTwoSeconds(LivingEntity entity, ServerLevel level) {
        // No-op by design: EffectHandlers.Oxygenate and AdjustTemperature remain unsupported.
    }
}
