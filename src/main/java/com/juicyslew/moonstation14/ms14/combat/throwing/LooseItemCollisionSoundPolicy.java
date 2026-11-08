package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.ms14.combat.PositionalSoundCue;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Per-loose-item terrain/body collision audio admission and shared cooldown. */
public final class LooseItemCollisionSoundPolicy {
    private long nextAllowedTick;
    private int previousTerrainMask;

    public Optional<PositionalSoundCue> terrain(ThrownItemProfile profile, int mask, Vec3 velocity, long tick) {
        boolean entering = (mask & ~previousTerrainMask) != 0;
        previousTerrainMask = mask;
        return entering ? contact(profile, velocity, tick) : Optional.empty();
    }

    public Optional<PositionalSoundCue> body(ThrownItemProfile profile, Vec3 velocity, long tick) {
        return contact(profile, velocity, tick);
    }

    private Optional<PositionalSoundCue> contact(ThrownItemProfile profile, Vec3 velocity, long tick) {
        if (profile == null || velocity == null || !Double.isFinite(velocity.length())) return Optional.empty();
        var data = profile.collisionSound().orElse(null);
        double speed = velocity.length() * 20.0;
        if (data == null || speed < data.minimumVelocity() || tick < nextAllowedTick) return Optional.empty();
        long cooldown = (long)Math.ceil(data.cooldownSeconds() * 20f);
        nextAllowedTick = tick > Long.MAX_VALUE - cooldown ? Long.MAX_VALUE : tick + cooldown;
        float ramp = (float)Math.max(0, Math.min(1, (speed - data.minimumVelocity()) / 10.0));
        return Optional.of(data.cue().withVolume(data.cue().volumeDb() - 10f + 12f * ramp));
    }

    public void resetTerrain() { previousTerrainMask = 0; }
}
