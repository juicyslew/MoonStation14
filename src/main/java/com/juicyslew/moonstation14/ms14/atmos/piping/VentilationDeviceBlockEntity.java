package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosVentilationReducer;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosMonitorSubscriptions;


/** Persisted configuration prototype only: no ticking, gas ownership, or gas transfer. */
public final class VentilationDeviceBlockEntity extends BlockEntity {
    private final VentilationDeviceConfig config;
    private boolean settingsValid = true;
    private UUID deviceId;
    private boolean ambientEligible;
    private final AtmosMonitorSubscriptions monitorSubscriptions = new AtmosMonitorSubscriptions();
    private final VentilationGasRuntime.ExportObservation exportObservation = new VentilationGasRuntime.ExportObservation();
    private final VentilationGasRuntime.TransferObservation transferObservation = new VentilationGasRuntime.TransferObservation();
    private final VentilationGasRuntime.DeviceCadence gasCadence = new VentilationGasRuntime.DeviceCadence();

    VentilationGasRuntime.DeviceCadence gasCadence() { return gasCadence; }
    void clearGasCadence() { gasCadence.clear(); }
    /** Observation-only counters; paid-work authority remains the private cadence budget. */
    public long lastPaidVentilationGrantTick() { return gasCadence.lastGrantTick(); }
    public double actualPaidVentilationGrantedJoules() { return gasCadence.actualGrantedJoules(); }
    public double actualPaidVentilationSpentJoules() { return gasCadence.actualSpentJoules(); }
    public VentilationGasRuntime.PaidEventReceipt lastPaidVentilationEvent() {
        return gasCadence.lastPaidEventReceipt();
    }

    void recordExport(FiniteVentilationTransfer.ExportReceipt receipt) { exportObservation.add(receipt); }
    void recordTransfer(com.juicyslew.moonstation14.ms14.atmos.core.GasMixture packet) { transferObservation.add(packet); setChanged(); }
    /** Read-only runtime receipt totals for this loaded placement session; resets on reload. */
    public VentilationGasRuntime.ExportObservation exportObservation() { return exportObservation; }
    public VentilationGasRuntime.TransferObservation transferObservation() { return transferObservation; }

    public VentilationDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENTILATION_DEVICE.get(), pos, state);
        config = new VentilationDeviceConfig(kind(), null);
    }

    public VentilationDeviceConfig.Kind kind() {
        if (!(getBlockState().getBlock() instanceof VentilationDeviceBlock block))
            throw new IllegalStateException("Ventilation entity has incompatible block");
        return block.kind();
    }

    /** Server-owned identity of this exact live block entity, retained across chunk save/reload. */
    public UUID deviceId() {
        if (deviceId == null && level instanceof ServerLevel server && !isRemoved()
                && server.getServer().isSameThread()) {
            // No chunk request, and do not generate for an unattached, pending-NBT or replaced entity.
            var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            if (chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                    && chunk.getBlockEntities().get(worldPosition) == this
                    && chunk.getBlockState(worldPosition).getBlock() instanceof VentilationDeviceBlock) {
                deviceId = UUID.randomUUID();
                setChanged();
            }
        }
        return deviceId;
    }

    @Override public void onLoad() {
        super.onLoad();
        deviceId();
        if (level instanceof ServerLevel server && deviceId != null) {
            try { DeviceNetworkRuntime.forLevel(server).loaded(networkEndpoint(server)); }
            catch (RuntimeException invalidComposition) { System.err.println("Ventilation network endpoint unavailable at " + worldPosition + ": " + invalidComposition.getMessage()); }
        }
    }

    @Override public void setRemoved() {
        clearGasCadence();
        if (level instanceof ServerLevel server && deviceId != null)
            DeviceNetworkRuntime.forLevel(server).unloaded(new DeviceIdentity(deviceId));
        super.setRemoved();
    }

    private DeviceEndpoint networkEndpoint(ServerLevel server) {
        return new DeviceEndpoint() {
            private DeviceComposition resolvedComposition() {
                var data = prototype();
                if (data == null) return DeviceComposition.empty();
                DeviceComposition resolved = data.deviceComponents().resolve(com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager());
                DeviceIdentity identity=new DeviceIdentity(deviceId);
                return com.juicyslew.moonstation14.ms14.devicenet.DeviceAddressPolicy.retained(resolved,
                        DeviceLinkSavedData.existing(server).flatMap(saved->saved.defaultAddress(identity)));
            }
            public DeviceIdentity identity() { return new DeviceIdentity(deviceId); }
            public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() { return server.dimension(); }
            public BlockPos position() { return worldPosition; }
            public String displayName() { return getBlockState().getBlock().getName().getString(); }
            public DeviceComposition composition() { return resolvedComposition(); }
            public boolean available() {
                var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
                return !isRemoved() && getLevel() == server && chunk != null
                        && chunk.getBlockEntity(worldPosition) == VentilationDeviceBlockEntity.this
                        && chunk.getBlockState(worldPosition).getBlock() instanceof VentilationDeviceBlock block
                        && block.kind() == config.kind() && prototype() != null;
            }
            public void receive(com.juicyslew.moonstation14.ms14.devicenet.DevicePayload payload, String sourceAddress) {
                if (!available() || !settingsValid()) return;
                if (payload instanceof AtmosDevicePayload.SetState command) {
                    if (AtmosVentilationReducer.apply(config(), command)) { setChanged(); refreshAppearance(server); }
                    return;
                }
                if (!monitorSubscriptions.receive(payload instanceof AtmosDevicePayload.Register
                        || payload instanceof AtmosDevicePayload.Deregister || payload instanceof AtmosDevicePayload.Sync
                        ? (AtmosDevicePayload) payload : null, sourceAddress)) return;
                DeviceComposition.NetworkInterface network = composition().network().orElse(null);
                if (network == null || !network.transmit() || network.transmitFrequency() < 1) return;
                var state = AtmosDevicePayload.State.from(config().settings());
                var packet = new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(
                        identity(), network.networkId(), network.transmitFrequency(), network.address(), sourceAddress,
                        false, state, network.networkId(), network.transmitFrequency());
                DeviceNetworkRuntime.forLevel(server).transport().enqueue(packet);
            }
        };
    }

    @Override public void onChunkUnloaded() {
        clearGasCadence();
        if (level instanceof ServerLevel server && deviceId != null)
            DeviceNetworkRuntime.forLevel(server).unloaded(new DeviceIdentity(deviceId));
        super.onChunkUnloaded();
    }

    /** Null until the matching concrete prototype is published and valid. */
    public VentilationDeviceData prototype() {
        if (level == null || !(getBlockState().getBlock() instanceof VentilationDeviceBlock block)
                || block.kind() != config.kind()) return null;
        VentilationDeviceData data = (level.isClientSide ? PrototypeRuntime.clientVentilationDevices()
                : PrototypeRuntime.serverVentilationDevices()).get(block.kind() == VentilationDeviceConfig.Kind.VENT
                ? ModVentilationDevices.VENT : ModVentilationDevices.SCRUBBER);
        if (data == null || data.kind() != VentilationDeviceData.Kind.valueOf(block.kind().name())) return null;
        try {
            data.validate();
            if (data.apcPowerReceiver().requestedWatts() != Ss14VentilationDefaults.APC_REQUESTED_POWER_WATTS)
                return null;
        } catch (RuntimeException invalid) { return null; }
        return data;
    }

    /** Recomputed only from the current power runtime meter; gas transfer is not an ambience gate. */
    public void refreshAmbientEligibility(ServerLevel server) {
        if (server == null || level != server || isRemoved()) { setAmbientEligible(false, server); return; }
        refreshAppearance(server);
        VentilationDeviceData data = prototype();
        var settings = settingsValid() ? config().settings() : null;
        var meter = data == null ? PowerRuntime.VentMeterObservation.UNKNOWN : PowerRuntime.ventilationMeter(server, this);
        var cue = data == null ? null : data.ambientAudio().orElse(null);
        boolean eligible = data != null && settings != null && com.juicyslew.moonstation14.ms14.atmos.machine.AtmosAmbientEligibility.eligible(cue,
                settings.configuredEnabled(), meter.known()
                        && meter.deliveredWatts() >= data.apcPowerReceiver().requestedWatts());
        setAmbientEligible(eligible, server);
    }

    /** Projects authoritative settings and the known delivered-power meter into the blockstate. */
    private void refreshAppearance(ServerLevel server) {
        if (server == null || level != server || isRemoved()) return;
        VentilationDeviceData data = prototype();
        var meter = data == null ? PowerRuntime.VentMeterObservation.UNKNOWN : PowerRuntime.ventilationMeter(server, this);
        boolean powered = data != null && meter.known()
                && meter.deliveredWatts() >= data.apcPowerReceiver().requestedWatts();
        String display = VentilationDeviceAppearance.project(config.kind(), settingsValid() ? config().settings() : null,
                powered, settingsValid());
        int displayIndex = VentilationDeviceAppearance.display(display);
        BlockState current = getBlockState();
        BlockState next = current.setValue(VentilationDeviceBlock.DISPLAY, displayIndex)
                .setValue(VentilationDeviceBlock.PIPE_LAYER, VentilationDeviceAppearance.layer(config().settings()));
        if (next != current) server.setBlock(worldPosition, next, 3);
    }

    private void setAmbientEligible(boolean eligible, ServerLevel server) {
        if (ambientEligible == eligible) return;
        ambientEligible = eligible;
        setChanged(); // Derived session state is update-payload-only, never persisted as saved truth.
        if (server != null) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public boolean ambientEligible() { return ambientEligible; }
    public com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio resolvedAmbientAudio() {
        VentilationDeviceData data = prototype();
        return data == null ? null : data.ambientAudio().orElse(null);
    }
    public float resolvedAmbientVolume() {
        var cue = resolvedAmbientAudio();
        return cue == null ? 0f : (float) Math.pow(10d, cue.volumeDb() / 20d);
    }

    public VentilationDeviceConfig config() {
        config.bindPrototype(prototype());
        return config;
    }
    public VentilationDeviceConfig.Layer selectedLayer() { return config().settings().selectedLayer(); }
    /** Applies a revisioned configuration update only on the authoritative server. */
    public boolean compareAndSetSettings(long expectedRevision, VentilationDeviceConfig.Settings proposed) {
        if (level != null && level.isClientSide) return false;
        if (prototype() == null || !settingsValid()) return false;
        boolean changed = config().compareAndSet(expectedRevision, proposed);
        if (changed) {
            setChanged();
            if (level instanceof ServerLevel server) refreshAmbientEligibility(server);
        }
        return changed;
    }
    public boolean settingsValid() { return settingsValid && prototype() != null && config().settingsValid(); }

    /** Sole gas-network attachment, one block behind the device front. */
    public BlockPos pipePortPos() { return worldPosition.relative(getBlockState().getValue(VentilationDeviceBlock.FACING).getOpposite()); }
    /** Room-side orientation; exposure/loaded checks belong to the eventual controlled runtime. */
    public Direction roomFacePos() { return getBlockState().getValue(VentilationDeviceBlock.FACING); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("VentilationSettings", config().save());
        if (deviceId != null) tag.putUUID("VentilationDeviceId", deviceId);
        tag.put("VentilationTransferReceipt", transferObservation.save());
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.putBoolean("AmbientEligible", ambientEligible);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        ambientEligible = tag.contains("AmbientEligible") && tag.getBoolean("AmbientEligible");
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ambientEligible = false;
        deviceId = tag.hasUUID("VentilationDeviceId") ? tag.getUUID("VentilationDeviceId") : null;
        transferObservation.load(tag.getCompound("VentilationTransferReceipt"));
        // A missing or malformed identity receives a fresh server identity on load; old links cannot match.
        if (!tag.contains("VentilationSettings", CompoundTag.TAG_COMPOUND)) {
            config.bindPrototype(prototype());
            settingsValid = true;
            config.restore(new CompoundTag());
            return;
        }
        config.bindPrototype(prototype());
        settingsValid = config.restore(tag.getCompound("VentilationSettings"));
        if (!settingsValid) System.err.println("Invalid persisted ventilation settings at " + worldPosition + "; device disabled");
    }
}

