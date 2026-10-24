package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.singularity.SingularityFood;
import com.juicyslew.moonstation14.ms14.singularity.beacon.ModSingularityBeacons;
import com.juicyslew.moonstation14.ms14.singularity.beacon.SingularityBeaconProfile;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Server-authoritative static APC load and singularity attraction source. */
public final class SingularityBeaconBlockEntity extends BlockEntity implements PowerRuntimeNode, SingularityFood {
    private ResourceLocation profileId = ModSingularityBeacons.DEFAULT_ID;
    private UUID identity = UUID.randomUUID();
    private double receivedWatts;
    private boolean graphKnown;
    private long nextAttractorPulse = -1;
    public SingularityBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINGULARITY_BEACON.get(), pos, state);
    }
    public UUID placementIdentity() { return identity; }
    public SingularityBeaconProfile resolvedProfile() {
        return level == null ? null : ModSingularityBeacons.catalog(level).get(profileId);
    }
    public boolean anchored() { return true; }
    public boolean fullyPowered() {
        if (!(level instanceof ServerLevel server) || !anchored()) return false;
        SingularityBeaconProfile profile = resolvedProfile();
        var meter = PowerRuntime.apcPowerMeter(server, this);
        return profile != null && meter.known() && meter.deliveredWatts() >= profile.powerWatts();
    }
    @Override public double singularityFoodEnergy() {
        SingularityBeaconProfile p = resolvedProfile(); return p == null ? Double.NaN : p.foodEnergy();
    }
    @Override public double singularityFoodFactor() {
        SingularityBeaconProfile p = resolvedProfile(); return p == null ? Double.NaN : p.foodFactor();
    }
    @Override public List<DevicePort> powerRuntimePorts() { return List.of(new DevicePort(worldPosition, Direction.NORTH, CableTier.APC)); }
    @Override public Double apcPowerDemandWatts() {
        SingularityBeaconProfile p = resolvedProfile(); return p == null ? 0d : p.powerWatts();
    }
    @Override public UUID apcPowerIdentity() { return identity; }
    @Override public void receiveMvPowerAllocation(double watts, boolean known, long tick) { }
    @Override public void receiveApcPowerAllocation(double watts, boolean known, long tick) {
        receivedWatts = Double.isFinite(watts) ? Math.max(0, watts) : 0;
        graphKnown = known;
    }
    public double receivedWatts() { return receivedWatts; }
    public boolean allocationKnown() { return graphKnown; }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, SingularityBeaconBlockEntity beacon) {
        if (beacon.isRemoved() || beacon.level != level || !beacon.fullyPowered()) return;
        SingularityBeaconProfile profile = beacon.resolvedProfile();
        if (profile == null) return;
        long now = level.getGameTime();
        if (beacon.nextAttractorPulse < 0) {
            beacon.nextAttractorPulse = now + profile.attractorPeriodTicks();
            beacon.setChanged();
            return;
        }
        if (now < beacon.nextAttractorPulse) return;
        beacon.nextAttractorPulse = now + profile.attractorPeriodTicks();
        beacon.setChanged();
        Vec3 origin = Vec3.atCenterOf(pos);
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof SingularityEntity singularity) || singularity.isRemoved() || singularity.level() != level) continue;
            Vec3 bias = com.juicyslew.moonstation14.ms14.singularity.beacon.SingularityBeaconAttraction.bias(
                    origin, singularity.position(), profile.attractorRange());
            if (!bias.equals(Vec3.ZERO)) singularity.addWalkBias(bias);
        }
    }
    @Override public void onLoad() {
        super.onLoad(); if (level instanceof ServerLevel server) PowerRuntime.deviceChanged(server, worldPosition);
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) PowerRuntime.deviceRemoved(server, worldPosition);
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putString("Profile", profileId.toString()); tag.putUUID("Identity", identity);
        tag.putLong("NextAttractorPulse", nextAttractorPulse);
    }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("Profile")); if (parsed != null) profileId = parsed;
        if (tag.hasUUID("Identity")) identity = tag.getUUID("Identity");
        nextAttractorPulse = Math.max(-1, tag.getLong("NextAttractorPulse"));
        receivedWatts = 0; graphKnown = false;
    }
}
