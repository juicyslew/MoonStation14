package com.juicyslew.moonstation14.ms14.explosion.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Shared best-effort Minecraft-adapted cue for accepted explosions. */
public final class ExplosionPresentation {
    private ExplosionPresentation() { }

    public static void present(ServerLevel level, BlockPos origin) {
        ExplosionFeedbackService.emitLegacyGrenadeCues(level, origin);
    }

    static void sendBestEffort(Runnable emission) {
        try { emission.run(); } catch (RuntimeException ignored) { /* Cosmetic delivery must not abort accepted gameplay. */ }
    }
}
