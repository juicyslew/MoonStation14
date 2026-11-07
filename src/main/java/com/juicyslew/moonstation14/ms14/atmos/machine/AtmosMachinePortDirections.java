package com.juicyslew.moonstation14.ms14.atmos.machine;

import net.minecraft.core.Direction;

/** Canonical transform from resolved machine-local port axes to world directions. */
public final class AtmosMachinePortDirections {
    private AtmosMachinePortDirections() { }

    public static Direction worldDirection(AtmosMachineProfile.PortDirection local, Direction facing) {
        Direction direction = Direction.valueOf(local.name());
        int turns = switch (facing) { case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> throw new IllegalArgumentException("horizontal machine facing required"); };
        for (int i = 0; i < turns; i++) direction = direction.getClockWise();
        return direction;
    }
}
