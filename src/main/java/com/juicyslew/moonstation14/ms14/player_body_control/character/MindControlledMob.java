package com.juicyslew.moonstation14.ms14.player_body_control.character;

/** Synchronized marker indicating that the harness owns this mob's movement tick. */
public interface MindControlledMob {
    boolean moonstation14$isMovementOwned();

    void moonstation14$setMovementOwned(boolean owned);

    /** Native movement already consumed for this body during the current server tick. */
    boolean moonstation14$physicalStepConsumed(net.minecraft.world.level.Level level, long serverTick);

    void moonstation14$markPhysicalStep(net.minecraft.world.level.Level level, long serverTick);
}

