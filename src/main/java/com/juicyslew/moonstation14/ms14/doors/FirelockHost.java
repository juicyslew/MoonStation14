package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** World-facing services required by the common firelock mechanics. */
public interface FirelockHost {
    BlockEntity owner();
    BlockPos root();
    Direction facing();
    FirelockData prototype();
    PowerRuntime.VentMeterObservation powerObservation();
    void projectPassability(boolean passable);
    void changedAndSync();
    void scheduleAt(long gameTime);
    boolean pairCollides(LivingEntity actor);
    Level level();
    FirelockState state();
    void state(FirelockState state);
    boolean initialized();
    boolean setPaidPower(boolean powered);
    boolean obstructed(ServerLevel server);
    void play(net.minecraft.sounds.SoundEvent sound);
    boolean live(ServerLevel server);
    String displayName();
}
