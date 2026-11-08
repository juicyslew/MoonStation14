package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative positional playback for accepted ChemMaster actions. */
public final class ChemMasterAudio {
    private static final float CLICK_GAIN = (float) Math.pow(10d, -2d / 20d);
    private ChemMasterAudio() { }
    public static void playClick(ServerLevel level, BlockPos pos, ChemMasterData data) {
        emit(level, pos, data == null ? null : data.clickSound(), CLICK_GAIN);
    }
    public static void playInsert(ServerLevel level, BlockPos pos, ChemMasterData data) {
        emit(level, pos, data == null ? null : data.slotInsertSound(), 1f);
    }
    public static void playEject(ServerLevel level, BlockPos pos, ChemMasterData data) {
        emit(level, pos, data == null ? null : data.slotEjectSound(), 1f);
    }
    private static void emit(ServerLevel level, BlockPos pos, ResourceLocation sound, float volume) {
        if (level == null || pos == null || sound == null) return;
        ControlledSoundDispatch.emit(level, sound, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, volume);
    }
}

