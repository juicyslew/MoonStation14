package com.juicyslew.moonstation14.ms14.disposal;

import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/** Server-authoritative positional audio for disposal gameplay hooks. */
public final class DisposalAudio {
    private static final float PIPE_CLANG_GAIN = 0.5623F; // SS14 -5 dB converted to amplitude.

    private DisposalAudio() {}

    public static void playFlush(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.DISPOSAL_FLUSH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public static void playInsert(ServerLevel level, BlockPos pos,
                                  com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Unit config) {
        if (level == null || config == null) return;
        var id = net.minecraft.resources.ResourceLocation.tryParse(config.insertionSound());
        var sound = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) return;
        try {
            // There is no client-side prediction for this authoritative commit hook. Broadcast
            // with a null player source so the inserting player (as well as observers) hears it.
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        } catch (RuntimeException audioFailure) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn("Unable to play disposal insertion sound {} at {}", id, pos, audioFailure);
        }
    }

    public static void playTurnClang(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.DISPOSAL_PIPE_CLANG.get(), SoundSource.BLOCKS, PIPE_CLANG_GAIN, 1.0F);
    }
}

