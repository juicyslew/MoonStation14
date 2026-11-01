package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/** Authoritative one-shot policy for the shared AtmosAlarmable receiver composition. */
public final class AlarmTransitionAudio {
    private static final float VOLUME = (float) Math.pow(10.0, -10.0 / 20.0);

    private AlarmTransitionAudio() { }

    public static boolean shouldPlay(AtmosAlarmSeverity before, AtmosAlarmSeverity after, boolean powered, boolean serverSide) {
        return powered && serverSide && before != AtmosAlarmSeverity.DANGER && after == AtmosAlarmSeverity.DANGER;
    }

    public static void play(ServerLevel level, BlockPos pos, AtmosAlarmSeverity before, AtmosAlarmSeverity after, boolean powered) {
        if (shouldPlay(before, after, powered, true))
            PositionalSoundRouter.play(level, com.juicyslew.moonstation14.sounds.ModSounds.FIRE_ALARM.get().getLocation(), SoundSource.BLOCKS,
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, VOLUME, 1f);
    }
}
