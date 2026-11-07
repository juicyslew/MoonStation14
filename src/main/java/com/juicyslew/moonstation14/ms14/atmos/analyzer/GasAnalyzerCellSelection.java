package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Analyzer adapter to the shared examine loaded-world cell ray and transparency policy. */
public final class GasAnalyzerCellSelection {
    public enum Status { SELECTED, BLOCKED, UNLOADED }
    public record Result(Status status, BlockPos cell) { }
    private GasAnalyzerCellSelection() { }
    public static Result select(Level level, Vec3 origin, Vec3 direction, int distance) {
        var result = com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.selectLoadedCell(
                level, origin, direction, distance);
        return new Result(Status.valueOf(result.status().name()), result.cell());
    }
}
