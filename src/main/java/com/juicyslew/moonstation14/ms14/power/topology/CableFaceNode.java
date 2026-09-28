package com.juicyslew.moonstation14.ms14.power.topology;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/** A cable mounted on one face of a full-cube host. This is geometry only. */
public record CableFaceNode(BlockPos host, Direction face, CableTier tier) {
    public CableFaceNode {
        host = new BlockPos(Objects.requireNonNull(host, "host"));
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(tier, "tier");
    }

    @Override
    public BlockPos host() {
        return host;
    }
}
