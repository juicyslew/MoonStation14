package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Emits staged reaction effects only after their owning state has committed. */
public final class ReactionAudio {
    private ReactionAudio() { }

    public static void playCommitted(ServerLevel level, Vec3 position, ReagentAttachment reacted) {
        if (reacted == null) return;
        var sounds = reacted.drainReactionSounds();
        if (level == null || position == null || level.isClientSide) return;
        for (var sound : sounds)
            ControlledSoundDispatch.emit(level, sound, position.x, position.y, position.z, 1f);
    }

    public static void playCommitted(ServerLevel level, BlockPos position, ReagentAttachment reacted) {
        if (position != null) playCommitted(level, Vec3.atCenterOf(position), reacted);
        else if (reacted != null) reacted.drainReactionSounds();
    }
}

