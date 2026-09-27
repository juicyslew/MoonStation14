package com.juicyslew.moonstation14.ms14.player_body_control.character;

/** Synchronized marker indicating that the harness owns this mob's movement tick. */
public interface MindControlledMob {
    boolean moonstation14$isMovementOwned();

    void moonstation14$setMovementOwned(boolean owned);
}
