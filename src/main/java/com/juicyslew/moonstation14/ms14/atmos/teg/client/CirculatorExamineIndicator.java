package com.juicyslew.moonstation14.ms14.atmos.teg.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Client-only, short-lived presentation caused by an accepted circulator examine. */
public final class CirculatorExamineIndicator {
    public static final long LIFETIME_TICKS = 40;
    public record State(BlockPos position, Direction facing, ResourceLocation dimension, long expiresAt) { }
    private static State state;
    private CirculatorExamineIndicator() { }

    public static void show(BlockPos pos, Direction facing, ResourceLocation dimension, long now) {
        state = new State(pos.immutable(), facing, dimension, now + LIFETIME_TICKS);
    }
    public static State current(ResourceLocation dimension, long now, boolean exactCirculator, Direction facing) {
        if (state == null) return null;
        if (now >= state.expiresAt() || dimension == null || !state.dimension().equals(dimension)
                || !exactCirculator || facing != state.facing()) { clear(); return null; }
        return state;
    }
    public static void clear() { state = null; }
    public static State peek() { return state; }
    /** Profile north/south port directions rotated by the same clockwise-facing transform as gasPorts(). */
    public static Direction rotate(Direction local, Direction facing) {
        int turns = switch (facing) { case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        Direction result = local;
        for (int i=0;i<turns;i++) result=result.getClockWise();
        return result;
    }
}
