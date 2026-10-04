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

    public static void playTurnClang(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.DISPOSAL_PIPE_CLANG.get(), SoundSource.BLOCKS, PIPE_CLANG_GAIN, 1.0F);
    }
}
