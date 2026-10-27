package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/** Server-authoritative live state shared by the coil and grounding rod. */
public final class TeslaMachineBlockEntity extends BlockEntity implements LightningConductor, PowerRuntimeNode {
    private ResourceLocation profileId;
    private UUID placementIdentity = UUID.randomUUID();
    private final TeslaBatteryState battery = new TeslaBatteryState();
    private long flashUntilTick;
    private double lastSuppliedWatts;
    private long lastSupplyElapsedTicks;

    public TeslaMachineBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.TESLA_MACHINE.get(), pos, state); }

    public TeslaMachineProfile profile() {
        if (level == null || profileId == null) return null;
        try { return ModTeslaProfiles.machines(level).get(profileId); }
        catch (RuntimeException unavailable) { return null; }
    }
    private boolean coil() { TeslaMachineProfile p = profile(); return p != null && "coil".equals(p.kind()); }
    public ResourceLocation profileId() { return profileId; }
    public void setProfileId(ResourceLocation value) { profileId = value; setChanged(); }
    public UUID placementIdentity() { return placementIdentity; }
    public double energyJoules() { return battery.energyJoules(); }
    public double offeredWatts() { TeslaMachineProfile p=profile(); return p == null || !coil() ? 0 : battery.offerWatts(p.supplyWatts(),20); }
    public double lastSuppliedWatts() { return lastSuppliedWatts; }
    /** Elapsed native solver interval for the most recent settled HV allocation. */
    public long lastSupplyElapsedTicks() { return lastSupplyElapsedTicks; }
    public boolean hitActive() { return level != null && level.getGameTime() < flashUntilTick; }
    public int structuralDamage() { return MachineryIntegrity.damage(this); }
    /** Full repair operation; the held-item handler owns tool/fuel and timed admission. */
    public boolean repairStructuralDamage() {
        TeslaMachineProfile p = profile();
        if (p == null || p.repair() == null || structuralDamage() <= 0 || !(level instanceof ServerLevel server)
                || server.isClientSide || isRemoved() || server.getBlockEntity(worldPosition) != this
                || server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) == null) return false;
        MachineryIntegrity.clear(this); setChanged(); sync(); return true;
    }

    @Override public LightningTargetComponent lightningTarget() { TeslaMachineProfile p=profile(); return p == null ? null : p.target(); }
    @Override public boolean receiveLightningStrike() {
        TeslaMachineProfile p=profile();
        if (p == null || level == null || level.isClientSide()) return false;
        if ("coil".equals(p.kind())) battery.charge(p.chargePerStrikeJoules(), p.capacityJoules());
        if (p.target() != null) MachineryStructuralDamage.apply(this, p, p.target().structuralDamage());
        long duration=Math.max(1, (long)Math.ceil(p.flashSeconds()*20));
        flashUntilTick=level.getGameTime()+duration;
        setHitState(true);
        setChanged(); sync();
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TeslaMachineBlockEntity be) {
        if (level.isClientSide) return;
        if (be.flashUntilTick != 0 && level.getGameTime() >= be.flashUntilTick) {
            be.flashUntilTick=0; be.setHitState(false); be.setChanged(); be.sync();
        }
    }
    private void setHitState(boolean hit) {
        if (level == null) return;
        BlockState state=level.getBlockState(worldPosition);
        if (state.getBlock() instanceof TeslaMachineBlock && state.hasProperty(TeslaMachineBlock.HIT)
                && state.getValue(TeslaMachineBlock.HIT) != hit)
            level.setBlock(worldPosition,state.setValue(TeslaMachineBlock.HIT,hit),2);
    }
    @Override public List<DevicePort> powerRuntimePorts() { return List.of(new DevicePort(worldPosition, Direction.NORTH, CableTier.HV)); }
    @Override public PowerRuntimeNode.SupplierOffer powerSupplierOffer() {
        return powerSupplierOffer(20);
    }
    @Override public PowerRuntimeNode.SupplierOffer powerSupplierOffer(long elapsedTicks) {
        TeslaMachineProfile p=profile();
        if (p == null || !"coil".equals(p.kind())) return null;
        double offer=battery.offerWatts(p.supplyWatts(),elapsedTicks);
        return new SupplierOffer(offer,p.supplyWatts());
    }
    @Override public void receiveMvPowerAllocation(double actualWatts, boolean graphKnown, long sampleTick) { }
    @Override public void receivePowerSupplyAllocation(double actualWatts, boolean graphKnown, long elapsedTicks, long sampleTick) {
        lastSupplyElapsedTicks=graphKnown?elapsedTicks:0;
        double used=battery.settle(actualWatts, graphKnown, elapsedTicks);
        lastSuppliedWatts=graphKnown ? Math.max(0, actualWatts) : 0;
        if (used > 0) { setChanged(); sync(); }
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel server) PowerRuntime.deviceChanged(server, worldPosition); }
    @Override public void setRemoved() { if (level instanceof ServerLevel server) PowerRuntime.deviceChanged(server, worldPosition); super.setRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (profileId != null) tag.putString("Profile", profileId.toString());
        tag.putUUID("Placement", placementIdentity); tag.putDouble("Energy", battery.energyJoules());
        tag.putLong("FlashUntil", flashUntilTick); tag.putDouble("LastSupplied", lastSuppliedWatts);
    }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Profile")) profileId=ResourceLocation.tryParse(tag.getString("Profile"));
        if (tag.hasUUID("Placement")) placementIdentity=tag.getUUID("Placement");
        battery.restore(tag.getDouble("Energy"),profile() == null ? 0 : profile().capacityJoules());
        flashUntilTick=tag.getLong("FlashUntil"); lastSuppliedWatts=Math.max(0,tag.getDouble("LastSupplied"));
    }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    private void sync() { if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }
}
