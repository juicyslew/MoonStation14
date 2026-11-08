package com.juicyslew.moonstation14.ms14.singularity.pa;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

import java.util.Map;

/** Compatibility wrapper retained for existing PA GameTests. */
public final class PAAssemblyFixture {
    private PAAssemblyFixture() { }

    public record Assembly(ParticleAcceleratorControlProfile profile,
                           com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity control,
                           Map<String, com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity> parts) { }

    public static Assembly place(ServerLevel level, BlockPos controlPos, Rotation orientation) {
        var placed = ParticleAcceleratorFixturePlacement.place(level, controlPos, orientation);
        return new Assembly(placed.profile(), placed.control(), placed.parts());
    }
}
