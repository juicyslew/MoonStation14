package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import java.util.OptionalDouble;
import java.util.UUID;

/** Validates handless melee's server-time authority against the active controlled session. */
public final class CharacterClockMeleeAdmission {
    private CharacterClockMeleeAdmission() { }

    public static OptionalDouble estimate(UUID body, long epoch, String dimension,
            GhostControlClient.CharacterSessionClockContext context) {
        if (body == null || epoch <= 0 || dimension == null || context == null
                || !body.equals(context.body()) || epoch != context.activeEpoch()
                || !dimension.equals(context.dimension())) return OptionalDouble.empty();
        return OptionalDouble.of(context.estimatedServerTick());
    }
}
