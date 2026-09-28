package com.juicyslew.moonstation14.ms14.power.topology;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/** A typed, outward-facing device port used for geometric adjacency checks only. */
public record DevicePort(BlockPos device, Direction face, CableTier tier) {
    public DevicePort {
        device = new BlockPos(Objects.requireNonNull(device, "device"));
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(tier, "tier");
    }

    @Override
    public BlockPos device() {
        return device;
    }
}
