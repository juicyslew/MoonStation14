package com.juicyslew.moonstation14.ms14.power.device;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/** Immutable device port definitions. Facing is the device's front-facing port. */
public enum PowerDeviceKind {
    HV_SOURCE("hv_source", CableTier.HV, null),
    HV_MV_SUBSTATION("hv_mv_substation", CableTier.HV, CableTier.MV),
    APC("apc", CableTier.MV, CableTier.APC),
    LAMP("power_lamp", CableTier.APC, null);

    private final String id;
    private final CableTier frontTier;
    private final CableTier backTier;

    PowerDeviceKind(String id, CableTier frontTier, CableTier backTier) {
        this.id = id;
        this.frontTier = frontTier;
        this.backTier = backTier;
    }

    public String id() { return id; }

    public List<DevicePort> ports(BlockPos pos, Direction facing) {
        if (facing == null || facing.getAxis().isVertical()) throw new IllegalArgumentException("horizontal facing required");
        var ports = new java.util.ArrayList<DevicePort>(2);
        ports.add(new DevicePort(pos, facing, frontTier));
        if (backTier != null) ports.add(new DevicePort(pos, facing.getOpposite(), backTier));
        return List.copyOf(ports);
    }
}
