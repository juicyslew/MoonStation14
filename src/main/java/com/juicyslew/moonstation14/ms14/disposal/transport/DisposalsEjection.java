package com.juicyslew.moonstation14.ms14.disposal.transport;

/** Verified SS14 disposal throw inputs; Minecraft velocity is expressed per tick. */
public final class DisposalsEjection {
    public static final double DISTANCE_BLOCKS = 3.0;
    public static final double SPEED_BLOCKS_PER_SECOND = 10.0;

    private DisposalsEjection() { }

    public static double speedBlocksPerTick() { return blocksPerSecondToBlocksPerTick(SPEED_BLOCKS_PER_SECOND); }
    public static double blocksPerSecondToBlocksPerTick(double speed) { return speed / 20.0; }
}
