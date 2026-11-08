package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.custom.EmitterBlock;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.singularity.emitter.EmitterProfile;
import com.juicyslew.moonstation14.ms14.singularity.emitter.ModEmitters;
import com.juicyslew.moonstation14.ms14.singularity.emitter.EmitterBurstSchedule;
import com.juicyslew.moonstation14.entities.EmitterBoltEntity;
import com.juicyslew.moonstation14.ms14.disposal.transport.DisposalsEjection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/** Live MV power and requested-state adapter. Projectile scheduling is intentionally separate. */
public final class EmitterBlockEntity extends BlockEntity implements PowerRuntimeNode {
    private static final String PROFILE="Profile", ON="RequestedOn", ID="Identity";
    private ResourceLocation profileId = ModEmitters.ID;
    private UUID identity = UUID.randomUUID();
    private boolean requestedOn;
    private boolean anchored = true;
    private double receivedWatts;
    private boolean graphKnown;
    private EmitterBurstSchedule burstSchedule = EmitterBurstSchedule.idle();
    private long poweredEpochStartedAt = -1;
    private int poweredEpochResets;

    public EmitterBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.EMITTER.get(), pos, state); }
    public EmitterProfile resolvedProfile() { return level == null ? null : ModEmitters.catalog(level).get(profileId); }
    public ResourceLocation profileId() { return profileId; }
    public UUID placementIdentity() { return identity; }
    public boolean requestedOn() { return requestedOn; }
    public boolean anchored() { return anchored; }
    public double receivedWatts() { return receivedWatts; }
    /** Diagnostic snapshot for server-side cadence validation; not persisted or synchronized. */
    public long poweredEpochStartedAt() { return poweredEpochStartedAt; }
    public int poweredEpochResets() { return poweredEpochResets; }
    public double currentDemandWatts() {
        EmitterProfile profile = resolvedProfile();
        return profile == null ? 0 : (requestedOn ? profile.onWatts() : profile.offWatts());
    }
    public boolean firingPowered() {
        if (!(level instanceof ServerLevel server) || !anchored || !requestedOn) return false;
        var meter = PowerRuntime.mvPowerMeter(server, this);
        EmitterProfile profile = resolvedProfile();
        return profile != null && meter.known() && meter.deliveredWatts() == profile.onWatts();
    }
    public void toggleRequestedOn() { setRequestedOn(!requestedOn); }
    public void setRequestedOn(boolean on) {
        if (!(level instanceof ServerLevel) || requestedOn == on || resolvedProfile() == null) return;
        requestedOn = on; setChanged();
    }
    @Override public List<DevicePort> powerRuntimePorts() {
        Direction facing = getBlockState().getValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.FACING);
        return List.of(new DevicePort(worldPosition, facing.getOpposite(), CableTier.MV));
    }
    @Override public Double mvPowerDemandWatts() { return currentDemandWatts(); }
    @Override public void receiveMvPowerAllocation(double watts, boolean known, long tick) {
        receivedWatts = watts; graphKnown = known;
        EmitterProfile profile = resolvedProfile();
        if (!known || requestedOn && (profile == null || watts != profile.onWatts())) burstSchedule = EmitterBurstSchedule.idle();
    }
    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, EmitterBlockEntity entity) {
        if (entity.isRemoved() || entity.level != level) return;
        EmitterProfile profile = entity.resolvedProfile();
        if (profile == null) {
            entity.burstSchedule = EmitterBurstSchedule.idle();
            if (state.getValue(EmitterBlock.VISUAL) != EmitterBlock.Visual.OFF)
                level.setBlock(pos, state.setValue(EmitterBlock.VISUAL, EmitterBlock.Visual.OFF), 2);
            return;
        }
        boolean powered = entity.firingPowered();
        EmitterBlock.Visual visual = powered ? EmitterBlock.Visual.ON : entity.requestedOn ? EmitterBlock.Visual.UNDERPOWERED : EmitterBlock.Visual.OFF;
        if (state.getValue(EmitterBlock.VISUAL) != visual)
            level.setBlock(pos, state.setValue(EmitterBlock.VISUAL, visual), 2);
        int delay = level.getRandom().nextInt(profile.firstDelayMinTicks(), profile.firstDelayMaxTicks() + 1);
        var result = entity.burstSchedule.tick(profile, entity.requestedOn && entity.anchored, powered, delay);
        if (!entity.burstSchedule.wasPowered() && result.schedule().wasPowered()) {
            if (entity.poweredEpochStartedAt >= 0) entity.poweredEpochResets++;
            entity.poweredEpochStartedAt = level.getGameTime();
        }
        entity.burstSchedule = result.schedule();
        if (result.fire()) {
            var direction = state.getValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.FACING);
            var start = net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, .25, 0)
                    .add(direction.getStepX() * .8, direction.getStepY() * .8, direction.getStepZ() * .8);
            var bolt = new EmitterBoltEntity(level, entity, profile);
            bolt.setPos(start.x, start.y, start.z);
            double speedPerTick = DisposalsEjection.blocksPerSecondToBlocksPerTick(profile.boltSpeed());
            bolt.setDeltaMovement(direction.getStepX() * speedPerTick, direction.getStepY() * speedPerTick,
                    direction.getStepZ() * speedPerTick);
            level.addFreshEntity(bolt);
            com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(level,
                    profile.fireSound(),
                    start.x, start.y, start.z);
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
        super.saveAdditional(tag, registries); tag.putString(PROFILE, profileId.toString()); tag.putBoolean(ON, requestedOn); tag.putUUID(ID, identity);
    }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation candidate = ResourceLocation.tryParse(tag.getString(PROFILE)); if (candidate != null) profileId = candidate;
        requestedOn = tag.getBoolean(ON); if (tag.hasUUID(ID)) identity = tag.getUUID(ID);
        receivedWatts = 0; graphKnown = false; anchored = true;
    }
}

