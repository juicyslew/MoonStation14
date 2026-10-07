package com.juicyslew.moonstation14.ms14.disposal.domain;

import net.minecraft.core.Direction;
import java.util.Objects;

/** Orthonormal frame mapping local prototype ports into the six-axis world. */
public record DisposalOrientation(Direction forward, Direction up) {
    public DisposalOrientation {
        Objects.requireNonNull(forward); Objects.requireNonNull(up);
        if (forward.getAxis() == up.getAxis()) throw new IllegalArgumentException("orientation axes must be perpendicular");
    }
    public Direction world(DisposalDirection local) {
        Direction right = cross(forward, up);
        return switch (local) {
            case NORTH -> forward; case SOUTH -> forward.getOpposite(); case UP -> up; case DOWN -> up.getOpposite();
            case EAST -> right; case WEST -> right.getOpposite();
        };
    }
    public DisposalDirection local(Direction world) {
        for (DisposalDirection direction : DisposalDirection.values()) if (world(direction) == world) return direction;
        throw new IllegalArgumentException("direction is not in orientation frame: " + world);
    }
    private static Direction cross(Direction a, Direction b) {
        int x=a.getStepY()*b.getStepZ()-a.getStepZ()*b.getStepY(), y=a.getStepZ()*b.getStepX()-a.getStepX()*b.getStepZ(), z=a.getStepX()*b.getStepY()-a.getStepY()*b.getStepX();
        return Direction.fromDelta(x,y,z);
    }
    public static DisposalOrientation identity() { return new DisposalOrientation(Direction.NORTH, Direction.UP); }
    public static DisposalOrientation placement(Direction clickedFace, Direction look) {
        Direction forward = clickedFace;
        Direction chosen = null; int best = Integer.MIN_VALUE;
        for (Direction candidate : Direction.values()) if (candidate.getAxis() != forward.getAxis()) {
            int score = candidate.getStepX()*look.getStepX()+candidate.getStepY()*look.getStepY()+candidate.getStepZ()*look.getStepZ();
            if (score > best || score == best && tie(candidate, forward) < tie(chosen, forward)) { chosen=candidate; best=score; }
        }
        if (best <= 0) chosen = forward.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        return new DisposalOrientation(forward, chosen);
    }
    private static int tie(Direction d, Direction f) { return d == null ? 99 : d.getAxis()==Direction.Axis.Y ? 0 : d.getAxis()==Direction.Axis.Z ? 1 : 2; }
}
