package com.juicyslew.moonstation14.ms14.explosion.grenade.client;

import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPolicy;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class GrenadeSoundClient {
    private GrenadeSoundClient() { }

    /** Called on the client main thread; discard stale world/camera state instead of queuing sound. */
    public static void receive(GrenadeSoundPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload == null || minecraft.level == null || minecraft.player == null
                || minecraft.player.level() != minecraft.level
                || !minecraft.level.dimension().location().equals(payload.dimension())) return;
        var cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null || cameraEntity.isRemoved() || cameraEntity.level() != minecraft.level) return;
        // Revalidate even though the record constructor validates decoded packets.
        if (!Float.isFinite(payload.gain()) || payload.gain() <= 0 || payload.gain() > 0.5f) return;
        if (payload.mode() == GrenadeSoundPayload.Mode.NEAR) {
            if (!Double.isFinite(payload.x()) || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z())
                    || payload.gain() != 0.5f) return;
            minecraft.level.playLocalSound(payload.x(), payload.y(), payload.z(), ModSounds.GRENADE_EXPLODE.get(),
                    SoundSource.BLOCKS, 0.5f, 1.0f, false);
        } else if (payload.mode() == GrenadeSoundPayload.Mode.FAR) {
            if (payload.gain() > GrenadeSoundPolicy.FAR_GAIN) return;
            // Packet delivery can precede renderer camera synchronization after a camera switch.
            // The active camera entity is authoritative; its eye position keeps this nonpositional
            // far sound local to the current listener without dropping on a renderer mismatch.
            Vec3 pos = cameraEntity.getEyePosition();
            if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return;
            minecraft.level.playLocalSound(pos.x, pos.y, pos.z, ModSounds.GRENADE_EXPLODE_FAR.get(),
                    SoundSource.BLOCKS, payload.gain(), 1.0f, false);
        }
    }
}
