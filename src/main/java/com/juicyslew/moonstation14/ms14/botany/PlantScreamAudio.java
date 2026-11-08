package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;

/** Shared best-effort plant scream resolution/playback for committed botany actions. */
final class PlantScreamAudio {
    record Prepared(net.minecraft.resources.ResourceLocation sound, float gain) { }
    private PlantScreamAudio() { }
    static Prepared prepare(SeedProfile seed) {
        if (seed == null || !seed.scream().enabled()) return null;
        try {
            var event = BuiltInRegistries.SOUND_EVENT.getOptional(seed.scream().sound()).orElse(null);
            return event == null ? null : new Prepared(seed.scream().sound(), HydroponicsTrayBlockEntity.screamGain(seed.scream().volumeDb()));
        } catch (RuntimeException unavailable) { return null; }
    }
    static void play(ServerLevel level, BlockPos position, Prepared scream) {
        if (level == null || position == null || scream == null) return;
        try {
            PositionalSoundRouter.play(level, scream.sound(), SoundSource.BLOCKS,
                    position.getX() + .5, position.getY() + .5, position.getZ() + .5,
                    scream.gain(), 1.0f);
        } catch (RuntimeException ignored) { }
    }
}
