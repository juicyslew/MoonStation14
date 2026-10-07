package com.juicyslew.moonstation14.ms14.disposal.domain;

import net.minecraft.core.Direction;

/** Local disposal-port directions. NORTH is the prototype's forward port. */
public enum DisposalDirection {
    NORTH, EAST, SOUTH, WEST, UP, DOWN;
    public DisposalDirection opposite() { return switch (this) { case NORTH -> SOUTH; case SOUTH -> NORTH; case EAST -> WEST; case WEST -> EAST; case UP -> DOWN; case DOWN -> UP; }; }
    public DisposalDirection rotate(int quarterTurns) { return switch (this) { case NORTH, EAST, SOUTH, WEST -> values()[Math.floorMod(ordinal() + quarterTurns, 4)]; default -> this; }; }
    public Direction toMC() { return Direction.valueOf(name()); }
    public static DisposalDirection fromMC(Direction direction) { return valueOf(direction.name()); }
}
