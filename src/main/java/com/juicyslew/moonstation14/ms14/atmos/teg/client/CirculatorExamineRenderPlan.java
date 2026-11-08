package com.juicyslew.moonstation14.ms14.atmos.teg.client;

import net.minecraft.core.Direction;

/** Pure local port geometry for examine overlays. Local forward is north and inlet/outlet are opposite. */
public final class CirculatorExamineRenderPlan {
    public record Arrow(float centerX, float centerZ, Direction forward) { }
    public record Plan(Arrow inlet, Arrow outlet) { }
    private CirculatorExamineRenderPlan() { }

    public static Plan forFacing(Direction facing) {
        Direction in = CirculatorExamineIndicator.rotate(Direction.NORTH, facing);
        Direction out = CirculatorExamineIndicator.rotate(Direction.SOUTH, facing);
        return new Plan(new Arrow(in.getStepX() * .30f, in.getStepZ() * .30f, in),
                new Arrow(out.getStepX() * .30f, out.getStepZ() * .30f, out));
    }
}
