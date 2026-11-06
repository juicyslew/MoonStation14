package com.juicyslew.moonstation14.ms14.player_body_control.character;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.SupportedMovementReference;

import java.util.UUID;

/** Captures and resolves a movement-only heading in the verified support frame's world-up yaw basis. */
public final class SupportedMovementYaw {
    private SupportedMovementYaw() { }

    public static SupportedMovementReference capture(UUID frameId, float worldFrameYaw, float worldMovementYaw) {
        if (!Float.isFinite(worldFrameYaw) || !Float.isFinite(worldMovementYaw))
            throw new IllegalArgumentException("movement headings must be finite");
        return new SupportedMovementReference(frameId, wrap(worldMovementYaw - worldFrameYaw));
    }

    public static float resolve(SupportedMovementReference reference, float fallbackWorldMovementYaw,
                                boolean actuallyGrounded, UUID actualSupportFrameId, float currentWorldFrameYaw) {
        if (reference == null || !actuallyGrounded || actualSupportFrameId == null
                || !reference.frameId().equals(actualSupportFrameId) || !Float.isFinite(currentWorldFrameYaw))
            return fallbackWorldMovementYaw;
        return wrap(reference.localMovementYaw() + currentWorldFrameYaw);
    }

    public static float wrap(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped < -180f) wrapped += 360f;
        if (wrapped > 180f) wrapped -= 360f;
        return wrapped;
    }
}
