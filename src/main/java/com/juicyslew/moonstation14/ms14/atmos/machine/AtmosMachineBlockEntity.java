package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedDevicePortGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransaction;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent identity/control shell; gas inventories are owned by LoadedDevicePortGasOwner, not this entity. */
public final class AtmosMachineBlockEntity extends BlockEntity implements PipeGasPortHost, PowerRuntimeNode {
    private static final String INCARNATION = "PortIncarnations";
    private final java.util.Map<String, UUID> incarnations = new java.util.LinkedHashMap<>();
    private AtmosMachineSettings.Values settingsOverride;
    private transient CompoundTag pendingSettingsTag;
    private transient boolean settingsInvalid;
    private UUID placementIdentity;
    private static final String SELECTED_PIPE_LAYER = "SelectedPipeLayer";
    /** Null means use the resolved profile's initial layer; never a client-authored value. */
    private PipeTopology.Layer selectedPipeLayer;
    private String pendingPipeLayerName;
    private transient String registeredPowerShape;
    private final transient AtmosMachinePowerState.PaidIntervalTracker paidPowerTracker = new AtmosMachinePowerState.PaidIntervalTracker();
    private final transient AtmosMachineCadence.PaidWorkBudget paidWorkBudget = new AtmosMachineCadence.PaidWorkBudget();
    private final transient AtmosMachineCadence.Clock machineCadence = new AtmosMachineCadence.Clock();
    private transient double apcAllocatedWatts;
    private transient double apcRequestedWatts;
    private transient boolean apcAllocationKnown;
    private transient long apcSampleTick = -1;
    private transient PowerRuntimeNode.SupplierOffer tegOffer;
    private transient long tegOfferPublishedSampleTick = -1;
    private transient double tegLastOfferedWatts;
    private transient long tegLastOfferSampleTick = -1;
    private transient boolean tegOutputPublicationCredit;
    private final transient AtmosMachinePowerState.TegEnergyBudget tegEnergyBudget = new AtmosMachinePowerState.TegEnergyBudget();
    private transient long tegOutputCreditSampleTick = -1;
    private transient double hvDeliveredWatts;
    private transient double hvDeliveredJoules;
    private transient boolean hvAllocationKnown;
    private transient long hvSampleTick = -1;
    private transient TegRuntime.State tegRuntimeState = TegRuntime.initial();
    private transient AtmosDeviceRuntime.State deviceRuntimeState = AtmosDeviceRuntime.State.initial();
    private transient GasWorkDecision lastFlowDecision;
    private transient long flowDecisionSequence;
    private transient boolean ambientEligible;
    private transient double ambientHeatReceiptJoules;
    private transient double spaceHeatReceiptJoules;
    // Durable, per-placement totals: unlike processor state these survive idle ticks and reloads.
    private double committedPaidJoules;
    private double committedMovedMoles;
    private double committedThermalJoules;
    private long committedWorkEvents;
    private long lastCommittedEventTick = Long.MIN_VALUE;
    /** Prevent presentation writes from synchronously reloading this chunk during its unload callback. */
    private transient boolean chunkUnloading;
    private int manifoldAppearanceMask;

    /** One bounded, consumable interval of fully paid APC input. sampleTick is its completion callback. */
    public record PaidPowerInterval(UUID placementIdentity, long actualElapsedTicks, double actualJoules,
                                    double allocationWatts, double requestedWatts, long sampleTick) { }

    public AtmosMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATMOS_MACHINE.get(), pos, state);
    }

    public String profileIdentity() {
        return getBlockState().getBlock() instanceof AtmosMachineBlock machine ? machine.profileIdentity() : "";
    }

    /** Resolves the machine catalog entry for this block's declared identity. */
    public AtmosMachineProfile resolvedProfile() {
        if (level == null || profileIdentity().isBlank()) return null;
        var id = ModAtmosMachines.id(profileIdentity());
        return ModAtmosMachines.catalog(level).get(id);
    }

    public boolean control(String key, boolean defaultValue) {
        AtmosMachineSettings.Values current = settingsSnapshot();
        if (current == null) return defaultValue;
        return switch (key) {
            case "enabled" -> current.enabled().orElse(defaultValue);
            case "open" -> current.valveOpen().orElse(defaultValue);
            default -> defaultValue;
        };
    }
    public void setControl(String key, boolean value) {
        AtmosMachineSettings.Operation operation = switch (key) {
            case "enabled" -> AtmosMachineSettings.Operation.SET_ENABLED;
            case "open" -> AtmosMachineSettings.Operation.SET_VALVE_OPEN;
            default -> null;
        };
        if (operation != null) applySettings(operation, new AtmosMachineSettings.BooleanValue(value));
    }

    public AtmosMachineSettings.Values settingsSnapshot() {
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null) return null;
        loadPendingSettings(profile);
        return settingsOverride != null ? settingsOverride : AtmosMachineSettings.defaults(profile);
    }

    public boolean settingsValid() { return !settingsInvalid; }
    public UUID placementIdentity() { return placementIdentity; }

    /** Resolved selected lane, validated against this exact profile's supported composition. */
    public PipeTopology.Layer selectedPipeLayer() {
        AtmosMachineProfile profile = resolvedProfile();
        AtmosMachineProfile.PipeLayers layers = profile == null ? null : profile.pipeLayers().orElse(null);
        if (layers == null) return PipeTopology.Layer.PRIMARY;
        if (selectedPipeLayer == null && pendingPipeLayerName != null) {
            try {
                PipeTopology.Layer stored = PipeTopology.Layer.valueOf(pendingPipeLayerName);
                if (stored.ordinal() < layers.count() && (!layers.locked() || stored == layers.initialLayer()))
                    selectedPipeLayer = stored;
            } catch (IllegalArgumentException ignored) { /* malformed save resolves to initial layer */ }
            pendingPipeLayerName = null;
        }
        if (selectedPipeLayer == null || selectedPipeLayer.ordinal() >= layers.count()
                || (layers.locked() && selectedPipeLayer != layers.initialLayer())) return layers.initialLayer();
        return selectedPipeLayer;
    }

    /**
     * Atomically relocates this exact machine's finite device-port inventory and selected lane.
     * Callers own interaction admission; this method owns only server-thread/world identity and
     * transaction validation. No inventory is created, retired, or reseeded here.
     */
    public boolean changePipeLayer(PipeTopology.Layer destination) {
        if (!(level instanceof ServerLevel server) || destination == null || !server.getServer().isSameThread()
                || !isExactLoadedHost(server)) return false;
        AtmosMachineProfile profile = resolvedProfile();
        AtmosMachineProfile.PipeLayers layers = profile == null ? null : profile.pipeLayers().orElse(null);
        PipeTopology.Layer oldLayer = selectedPipeLayer();
        if (!supportsPipeLayerChange(profile, destination, oldLayer)) return false;
        var inventoryMutation = LoadedDevicePortGasOwner.prepareLayerRelocation(server, worldPosition, destination).orElse(null);
        if (inventoryMutation == null) return false;
        String expectedProfile = profileIdentity();
        PipeGasTransaction.PreparedMutation hostMutation = new PipeGasTransaction.PreparedMutation() {
            private boolean attempted;
            @Override public Object identity() { return AtmosMachineBlockEntity.this; }
            @Override public boolean validate() {
                return isExactLoadedHost(server) && expectedProfile.equals(profileIdentity())
                        && resolvedProfile() == profile && selectedPipeLayer() == oldLayer;
            }
            @Override public boolean writeRaw() {
                if (!validate()) return false;
                attempted = true;
                selectedPipeLayer = destination;
                return true;
            }
            @Override public boolean rollbackRaw() {
                if (!attempted) return true;
                selectedPipeLayer = oldLayer;
                attempted = false;
                return true;
            }
            @Override public void publish() {
                setChanged();
                com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeNetworks.invalidateTopology(server, worldPosition);
                clearProcessorRuntimeState();
                clearPaidMachineCadence();
                if (level instanceof ServerLevel live) {
                    live.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    live.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                    refreshAppearance();
                    com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.refreshLoadedAdjacent(live, worldPosition);
                }
            }
        };
        try {
            PipeGasTransaction.commit(inventoryMutation, hostMutation);
            return true;
        } catch (PipeGasTransaction.RecoveryRequiredException recoveryRequired) { throw recoveryRequired; }
        catch (RuntimeException rejected) { return false; }
    }

    static boolean supportsPipeLayerChange(AtmosMachineProfile profile, PipeTopology.Layer destination,
                                          PipeTopology.Layer current) {
        AtmosMachineProfile.PipeLayers layers = profile == null ? null : profile.pipeLayers().orElse(null);
        return layers != null && destination != null && current != null && !layers.locked()
                && destination.ordinal() < layers.count() && destination != current;
    }

    /** Server-thread and exact-loaded-host admission; menu/network authorization remains caller-owned. */
    public boolean applySettings(AtmosMachineSettings.Operation operation, AtmosMachineSettings.Value value) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !isExactLoadedHost(server)
                || settingsInvalid) return false;
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null) return false;
        try {
            return applySettings(settingsRevision(), operation, value);
        } catch (RuntimeException rejected) { return false; }
    }

    public long settingsRevision() {
        AtmosMachineSettings.Values values = settingsSnapshot();
        return values == null ? -1 : values.revision();
    }

    public boolean applySettings(long expectedRevision, AtmosMachineSettings.Operation operation, AtmosMachineSettings.Value value) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !isExactLoadedHost(server)
                || settingsInvalid) return false;
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null) return false;
        try {
            AtmosMachineSettings.Values current = settingsSnapshot();
            if (current == null || current.revision() != expectedRevision) return false;
            AtmosMachineSettings.Values next = AtmosMachineSettings.apply(profile, current, operation, value);
            boolean topologyChanged = operation == AtmosMachineSettings.Operation.SET_VALVE_OPEN
                    && current.valveOpen().orElse(profile.valve() != null && profile.valve().open())
                    != next.valveOpen().orElse(profile.valve() != null && profile.valve().open());
            settingsOverride = next;
            pendingSettingsTag = null;
            setChanged();
            if (topologyChanged) com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeNetworks.invalidateTopology(server, worldPosition);
            if (operation == AtmosMachineSettings.Operation.SET_ENABLED && !next.enabled().orElse(true))
                deviceRuntimeState = AtmosDeviceRuntime.clearAcceptedTransfer(deviceRuntimeState);
            if (operation == AtmosMachineSettings.Operation.SET_ENABLED
                    && (profile.thermostat() != null || profile.teg() != null)) {
                if (!next.enabled().orElse(false)) clearPowerRuntimeState();
                registeredPowerShape = null;
                refreshPowerRegistration(server);
            }
            refreshAppearance();
            return true;
        } catch (RuntimeException rejected) { return false; }
    }

    private void loadPendingSettings(AtmosMachineProfile profile) {
        if (pendingSettingsTag == null || settingsOverride != null || settingsInvalid) return;
        try {
            settingsOverride = AtmosMachineSettings.load(profile, pendingSettingsTag);
            pendingSettingsTag = null;
        } catch (RuntimeException malformed) { settingsInvalid = true; }
    }

    /** Invoked on load and by the bounded BE ticker when its profile catalog is published later. */
    public void reconcileLoadedPorts(ServerLevel server) {
        if (!isExactLoadedHost(server)) return;
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null) {
            if (registeredPowerShape != null) {
                clearPowerRuntimeState();
                registeredPowerShape = null;
                PowerRuntime.deviceChanged(server, worldPosition);
            }
            return;
        }
        boolean newIncarnation = false;
        for (AtmosMachineProfile.Port definition : profile.ports()) {
            String id = portId(definition);
            if (!incarnations.containsKey(id)) {
                incarnations.put(id, UUID.randomUUID());
                newIncarnation = true;
            }
        }
        if (newIncarnation) setChanged();

        // Publish the full host port descriptor before asking the owner to admit any one port.
        // gasPorts() intentionally fails closed while even a single port incarnation is missing.
        for (AtmosMachineProfile.Port definition : profile.ports()) {
            String id = portId(definition);
            // Do not churn active records on every tick. Retry only inactive/missing ownership;
            // both owner operations validate this exact host in its already-full chunk.
            if (LoadedDevicePortGasOwner.activeSnapshot(server, worldPosition, id).isEmpty()
                    && !LoadedDevicePortGasOwner.reconcileExisting(server, worldPosition, id))
                LoadedDevicePortGasOwner.admit(server, worldPosition, id);
        }
        ensurePlacementIdentity(server);
        refreshPowerRegistration(server);
        // Descriptors/owners are now fully reconciled; earlier lifecycle callbacks can
        // observe gasPorts() fail-closed before all incarnations exist.
        com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.refreshLoadedAdjacent(server, worldPosition);
    }

    @Override public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        if (level instanceof ServerLevel server) {
            com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeNetworks.invalidateTopology(server, worldPosition);
            reconcileLoadedPorts(server);
            AtmosMachineProfile profile = resolvedProfile();
            // Circulator runtime observations are transient; restored art starts still until its core republishes.
            if (profile != null && profile.kind() == AtmosMachineProfile.Kind.TEG_CIRCULATOR)
                projectCirculatorAppearance(false, 0, 0);
            // APC allocation and processor observations are transient. Re-project the restored host
            // immediately so stale persisted DISPLAY cannot survive until its first machine tick.
            if (profile != null) projectAppearance(server, profile);
        }
    }

    @Override public void onChunkUnloaded() {
        chunkUnloading = true;
        if (level instanceof ServerLevel server) {
            if (resolvedProfile() != null && resolvedProfile().kind() == AtmosMachineProfile.Kind.TEG_CENTER) {
                var face = getBlockState().getValue(AtmosMachineBlock.FACING);
                clearTegCirculatorAt(server, worldPosition.relative(face));
                clearTegCirculatorAt(server, worldPosition.relative(face.getOpposite()));
            }
            deactivateLoadedPorts(server);
            PowerRuntime.deviceRemoved(server, worldPosition);
            clearPowerRuntimeState();
            registeredPowerShape = null;
        }
        super.onChunkUnloaded();
    }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            boolean replaced = chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                    && chunk.getBlockState(worldPosition).getBlock() != getBlockState().getBlock();
            if (replaced) PowerRuntime.deviceRemoved(server, worldPosition);
            if (replaced) com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeNetworks.invalidateTopology(server, worldPosition);
            clearPowerRuntimeState();
        }
        super.setRemoved();
    }

    public static void tick(ServerLevel server, AtmosMachineBlockEntity entity) {
        if (entity == null || entity.isRemoved()) return;
        entity.reconcileLoadedPorts(server);
        if (!entity.isExactLoadedHost(server)) return;
        AtmosMachineProfile profile = entity.resolvedProfile();
        if (profile != null && profile.kind() == AtmosMachineProfile.Kind.GAS_PIPE_MANIFOLD)
            entity.refreshManifoldAppearance(server);
        if (profile == null || !entity.settingsValid()) {
            entity.clearProcessorRuntimeState();
            entity.clearPaidMachineCadence();
            entity.setAmbientEligible(false, server);
            return;
        }
        if (profile.teg() != null) {
            entity.tegRuntimeState = TegRuntime.tick(server, entity, entity.tegRuntimeState);
            entity.projectTegCirculators(server, profile);
        } else {
            entity.tegRuntimeState = TegRuntime.initial();
            entity.deviceRuntimeState = AtmosDeviceRuntime.tick(server, entity, entity.deviceRuntimeState,
                    server.getGameTime());
        }
        entity.projectAppearance(server, profile);
    }

    private void refreshManifoldAppearance(ServerLevel server) {
        int mask = 0;
        for (Port port : gasPorts()) {
            Direction direction = port.directions().stream().findFirst().orElse(null);
            int bit = AtmosManifoldAppearance.portBit(port.id());
            if (direction != null && bit >= 0 && LoadedPipeComponent.hasExternalConnection(server,
                    port.key(worldPosition), direction)) mask |= 1 << bit;
        }
        if (mask == manifoldAppearanceMask) return;
        manifoldAppearanceMask = mask;
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** Six external reciprocal connections: north lanes 0-2, then south lanes 0-2. */
    public int manifoldAppearanceMask() { return manifoldAppearanceMask; }

    private void ensurePlacementIdentity(ServerLevel server) {
        if (placementIdentity == null && isExactLoadedHost(server)) {
            placementIdentity = UUID.randomUUID();
            setChanged();
        }
    }

    private void refreshPowerRegistration(ServerLevel server) {
        if (!isExactLoadedHost(server)) return;
        ensurePlacementIdentity(server);
        List<DevicePort> ports = powerRuntimePorts();
        Double demand = apcPowerDemandWatts();
        if (ports.isEmpty() && demand == null) {
            if (registeredPowerShape != null) {
                registeredPowerShape = null;
                PowerRuntime.deviceChanged(server, worldPosition);
            }
            return;
        }
        String shape = profileIdentity() + ":" + ports + ":" + demand;
        if (!shape.equals(registeredPowerShape)) {
            registeredPowerShape = shape;
            PowerRuntime.deviceChanged(server, worldPosition);
        }
    }

    @Override public List<DevicePort> powerRuntimePorts() {
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null || level == null || !(getBlockState().getBlock() instanceof AtmosMachineBlock)) return List.of();
        Direction facing = getBlockState().getValue(AtmosMachineBlock.FACING);
        List<DevicePort> ports = new ArrayList<>(2);
        boolean hasApcReceiver = profile.power() != null || profile.thermostat() != null;
        if (hasApcReceiver) ports.add(new DevicePort(worldPosition, facing.getCounterClockWise(), CableTier.APC));
        if (profile.kind() == AtmosMachineProfile.Kind.TEG_CENTER) {
            // SS14 front/back host the circulators; Minecraft electrical ports use free left/right faces.
            ports.add(new DevicePort(worldPosition, facing.getClockWise(), CableTier.HV));
        }
        return List.copyOf(ports);
    }

    @Override public Double apcPowerDemandWatts() {
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null || (profile.power() == null && profile.thermostat() == null)) return null;
        if (profile.teg() != null && (!(level instanceof ServerLevel server)
                || TegAssembly.resolve(server, this).isEmpty())) return 0d;
        AtmosMachineSettings.Values settings = settingsSnapshot();
        boolean configuredPowerEnabled = settingsValid() && settings != null
                && settings.enabled().orElse(profile.thermostat() == null || profile.thermostat().enabled());
        Double demand = AtmosMachinePowerState.apcDemand(profile, configuredPowerEnabled);
        return demand != null && Double.isFinite(demand) && demand >= 0 ? demand : null;
    }

    @Override public UUID apcPowerIdentity() {
        return level instanceof ServerLevel server && isExactLoadedHost(server) ? placementIdentity : null;
    }

    @Override public void receiveMvPowerAllocation(double actualWatts, boolean graphKnown, long sampleTick) { }

    @Override public void receiveApcPowerAllocation(double actualWatts, boolean graphKnown, long sampleTick) {
        Double requested = apcPowerDemandWatts();
        UUID identity = apcPowerIdentity();
        boolean valid = identity != null && requested != null && requested > 0 && graphKnown
                && Double.isFinite(actualWatts) && actualWatts >= requested
                && Double.isFinite(requested) && sampleTick >= 0;
        paidPowerTracker.accept(identity, actualWatts, requested == null ? 0 : requested, valid, sampleTick);
        apcAllocatedWatts = valid ? actualWatts : 0;
        apcRequestedWatts = requested == null ? 0 : requested;
        apcAllocationKnown = valid;
        apcSampleTick = sampleTick;
        if (!valid) {
            clearProcessorRuntimeState();
            tegOutputPublicationCredit = false;
            tegOutputCreditSampleTick = -1;
            tegOffer = null;
            tegOfferPublishedSampleTick = -1;
        }
        refreshAppearance();
        if (level instanceof ServerLevel server && resolvedProfile() != null
                && resolvedProfile().kind() == AtmosMachineProfile.Kind.TEG_CENTER)
            projectTegCirculators(server, resolvedProfile());
    }

    /** Called by the power chunk-unload listener before its invalid allocation callback. */
    public void beginPowerChunkUnload() { chunkUnloading = true; }

    /** Consumes one interval against the same private solver clock used by the allocation callback. */
    public PaidPowerInterval consumePaidPowerInterval() {
        if (!(level instanceof ServerLevel server) || !isExactLoadedHost(server)) return null;
        AtmosMachinePowerState.PaidInterval result = paidPowerTracker.consume(PowerRuntime.currentSampleTick(server));
        if (result == null) return null;
        AtmosMachineProfile profile = resolvedProfile();
        if (profile != null && profile.kind() == AtmosMachineProfile.Kind.TEG_CENTER
                && result.placementIdentity().equals(placementIdentity)) {
            tegOutputPublicationCredit = true;
            tegOutputCreditSampleTick = result.sampleTick();
        }
        return new PaidPowerInterval(result.placementIdentity(), result.actualElapsedTicks(),
                result.actualJoules(), result.allocationWatts(), result.requestedWatts(), result.sampleTick());
    }

    /** Receives only completed PaidIntervalTracker output; fixed events are funded by its actual joules. */
    public boolean paidMachineEventDue(AtmosMachineProfile profile, long serverTick, double eventCostJoules) {
        if (!(level instanceof ServerLevel server) || !isExactLoadedHost(server) || profile == null) return false;
        long sampleTick = PowerRuntime.currentSampleTick(server);
        PaidPowerInterval grant = consumePaidPowerInterval();
        UUID identity = apcPowerIdentity();
        if (grant != null) acceptPaidInterval(paidWorkBudget, identity, grant, eventCostJoules);
        if (!Double.isFinite(apcAllocatedWatts) || apcAllocatedWatts < apcRequestedWatts
                || apcRequestedWatts <= 0 || sampleTick < 0) paidWorkBudget.unavailable();
        boolean due = machineCadence.due(serverTick, profile.simulation());
        return due && paidWorkBudget.canWork(identity, serverTick, sampleTick, eventCostJoules);
    }

    public boolean completePaidMachineEvent(long serverTick, double eventCostJoules) {
        return completePaidMachineEvent(serverTick, eventCostJoules, 0, 0);
    }
    /** Completes a funded event attempt without charging its work budget or recording a commit. */
    public void completePaidMachineAttempt() {
        machineCadence.completed();
    }
    /** Records receipt only after the caller has successfully committed the associated gas/thermal mutation. */
    public boolean completePaidMachineEvent(long serverTick, double eventCostJoules, double movedMoles, double thermalJoules) {
        if (!(level instanceof ServerLevel server)) return false;
        long sampleTick = PowerRuntime.currentSampleTick(server);
        boolean committed = paidWorkBudget.commitWork(apcPowerIdentity(), serverTick, sampleTick, eventCostJoules);
        if (committed) {
            machineCadence.completed();
            if (lastCommittedEventTick != serverTick) {
                committedPaidJoules = finiteTotal(committedPaidJoules, eventCostJoules);
                committedMovedMoles = finiteTotal(committedMovedMoles, movedMoles);
                committedThermalJoules = signedTotal(committedThermalJoules, thermalJoules);
                committedWorkEvents = committedWorkEvents == Long.MAX_VALUE ? Long.MAX_VALUE : committedWorkEvents + 1;
                lastCommittedEventTick = serverTick;
                setChanged();
            }
        }
        return committed;
    }
    private static double finiteTotal(double total, double addition) {
        if (!Double.isFinite(addition) || addition <= 0) return total;
        double next = total + addition;
        return Double.isFinite(next) ? next : total;
    }
    private static double signedTotal(double total, double addition) {
        if (!Double.isFinite(addition)) return total;
        double next = total + addition;
        return Double.isFinite(next) ? next : total;
    }
    public double committedPaidJoules() { return committedPaidJoules; }
    public double committedMovedMoles() { return committedMovedMoles; }
    public double committedThermalJoules() { return committedThermalJoules; }
    public long committedWorkEvents() { return committedWorkEvents; }
    /** Unpowered processor receipt; thermal work is signed and never manufactures paid joules. */
    public void recordCommittedUnpoweredWork(long eventTick, double movedMoles) {
        recordCommittedUnpoweredWork(eventTick, movedMoles, 0);
    }
    public void recordCommittedUnpoweredWork(long eventTick, double movedMoles, double thermalJoules) {
        if ((!Double.isFinite(movedMoles) || movedMoles <= 0)
                && (!Double.isFinite(thermalJoules) || thermalJoules == 0)
                || !Double.isFinite(thermalJoules) || lastCommittedEventTick == eventTick) return;
        committedMovedMoles = finiteTotal(committedMovedMoles, movedMoles);
        committedThermalJoules = signedTotal(committedThermalJoules, thermalJoules);
        committedWorkEvents = committedWorkEvents == Long.MAX_VALUE ? Long.MAX_VALUE : committedWorkEvents + 1;
        lastCommittedEventTick = eventTick;
        setChanged();
    }
    public void clearPaidMachineCadence() { paidWorkBudget.unavailable(); machineCadence.reset(); }

    static void acceptPaidInterval(AtmosMachineCadence.PaidWorkBudget budget, UUID currentIdentity,
                                   PaidPowerInterval grant, double eventCostJoules) {
        if (grant == null || !grant.placementIdentity().equals(currentIdentity)) {
            budget.unavailable();
            return;
        }
        budget.accept(currentIdentity, grant.actualJoules(), grant.sampleTick(), eventCostJoules);
    }

    /** Future TEG processor publishes measured generation; this host never invents an offer. */
    public boolean publishTegSupplyOffer(double currentWatts, double ratedMaximumWatts, double generatedJoules) {
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null || profile.kind() != AtmosMachineProfile.Kind.TEG_CENTER
                || !(level instanceof ServerLevel server) || !isExactLoadedHost(server)
                || TegAssembly.resolve(server, this).isEmpty()
                || !apcAllocationKnown || apcRequestedWatts <= 0 || apcAllocatedWatts < apcRequestedWatts
                || !settingsValid() || !control("enabled", true)
                || !tegOutputPublicationCredit || tegOutputCreditSampleTick < 0
                || PowerRuntime.currentSampleTick(server) < tegOutputCreditSampleTick
                || PowerRuntime.currentSampleTick(server) - tegOutputCreditSampleTick > PowerRuntime.TICKS_PER_SOLVE
                || apcSampleTick < 0 || PowerRuntime.currentSampleTick(server) < apcSampleTick
                || PowerRuntime.currentSampleTick(server) - apcSampleTick > PowerRuntime.TICKS_PER_SOLVE
                || !Double.isFinite(currentWatts) || currentWatts < 0 || !Double.isFinite(ratedMaximumWatts)
                || ratedMaximumWatts < currentWatts) return false;
        tegOffer = new PowerRuntimeNode.SupplierOffer(currentWatts, ratedMaximumWatts);
        tegEnergyBudget.credit(generatedJoules, true);
        tegOfferPublishedSampleTick = PowerRuntime.currentSampleTick(server);
        return true;
    }

    public void clearTegSupplyOffer() {
        tegOffer = null; tegOfferPublishedSampleTick = -1; tegEnergyBudget.clear();
        tegLastOfferedWatts = 0; tegLastOfferSampleTick = -1;
    }

    @Override public PowerRuntimeNode.SupplierOffer powerSupplierOffer() {
        return powerSupplierOffer(PowerRuntime.TICKS_PER_SOLVE);
    }

    private PowerRuntimeNode.SupplierOffer canonicalTegOffer() {
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null || profile.kind() != AtmosMachineProfile.Kind.TEG_CENTER) return null;
        boolean exactLoaded = level instanceof ServerLevel server && isExactLoadedHost(server);
        if (exactLoaded && level instanceof ServerLevel server && TegAssembly.resolve(server, this).isEmpty())
            return AtmosMachinePowerState.ZERO_OFFER;
        boolean enabled = settingsValid() && control("enabled", true);
        long now = level instanceof ServerLevel server && server.getServer().isSameThread()
                ? PowerRuntime.currentSampleTick(server) : -1;
        PowerRuntimeNode.SupplierOffer canonical = AtmosMachinePowerState.offer(true, exactLoaded, enabled, apcAllocationKnown,
                apcAllocatedWatts, apcRequestedWatts, apcSampleTick, now,
                tegOffer, tegOfferPublishedSampleTick);
        return canonical;
    }

    @Override public PowerRuntimeNode.SupplierOffer powerSupplierOffer(long elapsedTicks) {
        PowerRuntimeNode.SupplierOffer offer = canonicalTegOffer();
        if (offer == null) return null;
        double watts = tegEnergyBudget.capWatts(offer.currentWatts(), offer.ratedMaximumWatts(), elapsedTicks);
        tegLastOfferedWatts = watts;
        tegLastOfferSampleTick = level instanceof ServerLevel server && server.getServer().isSameThread()
                ? PowerRuntime.currentSampleTick(server) : -1;
        return new PowerRuntimeNode.SupplierOffer(watts, watts);
    }
    public double tegLastOfferedWatts() { return tegLastOfferedWatts; }
    public long tegLastOfferSampleTick() { return tegLastOfferSampleTick; }

    @Override public void receivePowerSupplyAllocation(double actualWatts, boolean graphKnown,
                                                        long elapsedTicks, long sampleTick) {
        boolean isTeg = resolvedProfile() != null && resolvedProfile().kind() == AtmosMachineProfile.Kind.TEG_CENTER;
        boolean valid = isTeg && level instanceof ServerLevel server && isExactLoadedHost(server)
                && graphKnown && Double.isFinite(actualWatts) && actualWatts >= 0
                && elapsedTicks >= 0 && sampleTick >= 0;
        boolean settled = tegEnergyBudget.settle(actualWatts, elapsedTicks, valid);
        hvDeliveredWatts = valid ? actualWatts : 0;
        hvDeliveredJoules = valid ? actualWatts * elapsedTicks / 20d : 0;
        hvAllocationKnown = valid && settled;
        hvSampleTick = sampleTick;
        // Offers are one-solve snapshots; the processor must publish fresh generation again.
        tegOffer = null;
        tegOfferPublishedSampleTick = -1;
    }

    public double hvDeliveredWatts() { return hvDeliveredWatts; }
    /** Signed fixed-reservoir thermal receipt for the current loaded placement session. */
    public double ambientHeatReceiptJoules() { return ambientHeatReceiptJoules; }
    /** Radiated boundary energy for the current loaded placement session; never ambient energy. */
    public double spaceHeatReceiptJoules() { return spaceHeatReceiptJoules; }
    public void recordThermalReceipt(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind kind,
                                    double environmentJoules, double radiatedJoules) {
        if (kind == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT)
            ambientHeatReceiptJoules = com.juicyslew.moonstation14.ms14.atmos.core.FiniteObservationTotal.add(ambientHeatReceiptJoules, environmentJoules);
        else if (kind == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.SPACE)
            spaceHeatReceiptJoules = com.juicyslew.moonstation14.ms14.atmos.core.FiniteObservationTotal.add(spaceHeatReceiptJoules, radiatedJoules);
        setChanged();
    }
    public double hvDeliveredJoules() { return hvDeliveredJoules; }
    public boolean hvAllocationKnown() { return hvAllocationKnown; }
    public long hvSampleTick() { return hvSampleTick; }

    public double apcAllocatedWatts() { return apcAllocatedWatts; }
    public boolean apcAllocationKnown() { return apcAllocationKnown; }
    public long apcSampleTick() { return apcSampleTick; }

    /** Transient per-loaded-host processor state; never persisted. */
    public TegRuntime.State tegRuntimeState() { return tegRuntimeState; }
    public AtmosDeviceRuntime.State deviceRuntimeState() { return deviceRuntimeState; }
    /** Last actual pump/mixer policy attempt; transient and diagnostic-only. */
    public GasWorkDecision lastFlowDecision() { return lastFlowDecision; }
    void recordFlowDecision(GasWorkDecision decision) {
        if (decision == null) return;
        flowDecisionSequence = decision.sequence();
        lastFlowDecision = decision;
    }
    long nextFlowDecisionSequence() {
        flowDecisionSequence = nextFlowDecisionSequence(flowDecisionSequence);
        return flowDecisionSequence;
    }
    static long nextFlowDecisionSequence(long current) { return current == Long.MAX_VALUE ? 1 : current + 1; }

    /** Projects authoritative owner snapshots into derived native blockstate only. */
    private void projectAppearance(ServerLevel server, AtmosMachineProfile profile) {
        if (chunkUnloading) return;
        if (!(getBlockState().getBlock() instanceof AtmosMachineBlock)) return;
        AtmosMachineSettings.Values settings = settingsSnapshot();
        boolean powered = apcAllocationKnown && apcRequestedWatts > 0 && apcAllocatedWatts >= apcRequestedWatts;
        boolean enabled = switch (profile.kind()) {
            case MANUAL_VALVE -> settings != null && settings.valveOpen().orElse(profile.valve().open());
            case MIXER -> settings != null && settings.enabled().orElse(profile.mixer().enabled());
            case FILTER -> settings != null && settings.enabled().orElse(profile.filter().enabled());
            case PRESSURE_PUMP, VOLUME_PUMP -> settings != null && settings.enabled().orElse(profile.pump().enabled());
            case HEATER, FREEZER -> settings != null && settings.enabled().orElse(profile.thermostat().enabled());
            default -> true;
        };
        var supply = tegRuntimeState == null ? null : tegRuntimeState.supply();
        if (supply == null) supply = com.juicyslew.moonstation14.ms14.atmos.teg.TegSupplyPolicy.State.zero();
        BlockState old = getBlockState();
        int layer = profile.pipeLayers().isPresent() ? selectedPipeLayer().ordinal() : 0;
        if (profile.kind() == AtmosMachineProfile.Kind.TEG_CIRCULATOR) {
            if (old.getValue(AtmosMachineBlock.PIPE_LAYER) != layer)
                server.setBlock(worldPosition, old.setValue(AtmosMachineBlock.PIPE_LAYER, layer), 2);
            return; // Its exact assembly's core alone owns circulator DISPLAY.
        }
        String visual = AtmosMachineAppearance.project(profile, powered, enabled,
                deviceRuntimeState != null && deviceRuntimeState.lastPumpBlocked(), supply);
        int display = AtmosMachineAppearance.nativeDisplay(profile, visual, powered);
        if (old.getValue(AtmosMachineBlock.DISPLAY) != display || old.getValue(AtmosMachineBlock.PIPE_LAYER) != layer) {
            server.setBlock(worldPosition, old.setValue(AtmosMachineBlock.DISPLAY, display).setValue(AtmosMachineBlock.PIPE_LAYER, layer), 2);
        }
        updateAmbientEligibility(server, profile);
    }

    private void updateAmbientEligibility(ServerLevel server, AtmosMachineProfile profile) {
        var cue = profile.ambientAudio().orElse(null);
        boolean eligible = false;
        if (cue != null && settingsValid()) {
            var settings = settingsSnapshot();
            boolean enabled = settings != null && switch (profile.kind()) {
                case PRESSURE_PUMP, VOLUME_PUMP -> settings.enabled().orElse(profile.pump().enabled());
                case MIXER -> settings.enabled().orElse(profile.mixer().enabled());
                case FILTER -> settings.enabled().orElse(profile.filter().enabled());
                case MANUAL_VALVE -> settings.valveOpen().orElse(profile.valve().open());
                default -> true;
            };
            boolean powered = apcAllocationKnown && apcRequestedWatts > 0 && apcAllocatedWatts >= apcRequestedWatts;
            eligible = AtmosAmbientEligibility.eligible(profile, enabled,
                    deviceRuntimeState == null ? 0 : deviceRuntimeState.lastMoles(), powered,
                    getBlockState().getValue(AtmosMachineBlock.DISPLAY));
        }
        setAmbientEligible(eligible, server);
    }

    private void setAmbientEligible(boolean eligible, ServerLevel server) {
        if (eligible == ambientEligible) return;
        ambientEligible = eligible;
        setChanged(); // saveAdditional omits this update-payload-only derived flag.
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public boolean ambientEligible() { return ambientEligible; }
    public com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio resolvedAmbientAudio() {
        AtmosMachineProfile profile = resolvedProfile();
        return profile == null ? null : profile.ambientAudio().orElse(null);
    }
    public float resolvedAmbientVolume() {
        var cue = resolvedAmbientAudio();
        return cue == null ? 0f : (float) Math.pow(10d, cue.volumeDb() / 20d);
    }

    /** Called only by the core's existing accepted-transfer owner for members of its exact assembly. */
    public void projectCirculatorAppearance(boolean powered, double previousPressureDeltaKpa,
                                            double previousMolesTransferred) {
        if (chunkUnloading || !(level instanceof ServerLevel server) || !(getBlockState().getBlock() instanceof AtmosMachineBlock)) return;
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null || profile.kind() != AtmosMachineProfile.Kind.TEG_CIRCULATOR || profile.visual() == null) return;
        int display = AtmosMachineAppearance.circulatorDisplay(powered, previousPressureDeltaKpa,
                previousMolesTransferred, profile.visual().circulatorFastDeltaKpa());
        BlockState old = getBlockState();
        if (old.getValue(AtmosMachineBlock.DISPLAY) != display || old.getValue(AtmosMachineBlock.PIPE_LAYER) != 0)
            server.setBlock(worldPosition, old.setValue(AtmosMachineBlock.DISPLAY, display).setValue(AtmosMachineBlock.PIPE_LAYER, 0), 2);
    }

    private void projectTegCirculators(ServerLevel server, AtmosMachineProfile profile) {
        var assembly = com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly.resolve(server, this).orElse(null);
        if (assembly == null) {
            clearTegCirculatorAt(server, getBlockPos().relative(getBlockState().getValue(AtmosMachineBlock.FACING)));
            clearTegCirculatorAt(server, getBlockPos().relative(getBlockState().getValue(AtmosMachineBlock.FACING).getOpposite()));
            return;
        }
        boolean powered = apcAllocationKnown && apcRequestedWatts > 0 && apcAllocatedWatts >= apcRequestedWatts;
        var state = tegRuntimeState;
        assembly.circulatorA().projectCirculatorAppearance(powered, state.circulatorA().pressureDeltaKpa(), state.circulatorA().transferredMoles());
        assembly.circulatorB().projectCirculatorAppearance(powered, state.circulatorB().pressureDeltaKpa(), state.circulatorB().transferredMoles());
    }

    private static void clearTegCirculatorAt(ServerLevel server, BlockPos position) {
        var chunk = server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)) return;
        if (chunk.getBlockEntities().get(position) instanceof AtmosMachineBlockEntity candidate
                && candidate.resolvedProfile() != null
                && candidate.resolvedProfile().kind() == AtmosMachineProfile.Kind.TEG_CIRCULATOR)
            candidate.projectCirculatorAppearance(false, 0, 0);
    }
    public void clearTegCirculatorPresentation(ServerLevel server, BlockPos position) {
        clearTegCirculatorAt(server, position);
    }

    /** Explicitly republish derived presentation after settings or power callbacks. */
    private void refreshAppearance() {
        if (level instanceof ServerLevel server) {
            AtmosMachineProfile profile = resolvedProfile();
            if (profile != null) projectAppearance(server, profile);
        }
    }

    private void clearPowerRuntimeState() {
        apcAllocatedWatts = apcRequestedWatts = 0; apcAllocationKnown = false; apcSampleTick = -1;
        paidPowerTracker.clear(); clearPaidMachineCadence(); tegOffer = null; tegOfferPublishedSampleTick = -1;
        tegLastOfferedWatts = 0; tegLastOfferSampleTick = -1;
        tegEnergyBudget.clear();
        tegOutputPublicationCredit = false; tegOutputCreditSampleTick = -1;
        hvDeliveredWatts = hvDeliveredJoules = 0; hvAllocationKnown = false; hvSampleTick = -1;
        clearProcessorRuntimeState();
    }

    private void clearProcessorRuntimeState() {
        tegRuntimeState = TegRuntime.initial();
        deviceRuntimeState = AtmosDeviceRuntime.State.initial();
    }

    private boolean isExactLoadedHost(ServerLevel server) {
        if (server == null || !server.getServer().isSameThread() || level != server || isRemoved()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                && chunk.getBlockEntities().get(worldPosition) == this
                && chunk.getBlockState(worldPosition).getBlock() instanceof AtmosMachineBlock block
                && block.profileIdentity().equals(profileIdentity());
    }

    public void deactivateLoadedPorts(ServerLevel server) {
        if (!isExactLoadedHost(server)) return;
        for (Port port : gasPorts()) LoadedDevicePortGasOwner.deactivate(server, worldPosition, port.id());
    }

    /** Called before the exact block entity is detached on a real block replacement/removal. */
    public void retireRemovedPorts(ServerLevel server, BlockState removedState) {
        if (server == null || !server.getServer().isSameThread() || level != server || isRemoved()
                || !(removedState.getBlock() instanceof AtmosMachineBlock block)
                || !block.profileIdentity().equals(profileIdentity())) return;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || chunk.getBlockEntities().get(worldPosition) != this) return;
        for (Port port : gasPorts()) LoadedDevicePortGasOwner.retireRemoved(server, worldPosition,
                port.layer(), port.id(), port.incarnation());
    }

    @Override public List<Port> gasPorts() {
        AtmosMachineProfile profile = resolvedProfile();
        if (profile == null || level == null) return List.of();
        Direction facing = getBlockState().getValue(AtmosMachineBlock.FACING);
        List<Port> result = new ArrayList<>();
        for (AtmosMachineProfile.Port definition : profile.ports()) {
            String id = portId(definition);
            UUID incarnation = incarnations.get(id);
            // Client descriptors are read-only and may not invent server-owned identity.
            if (incarnation == null) return List.of();
            Direction direction = AtmosMachinePortDirections.worldDirection(definition.direction(), facing);
            result.add(new Port(id, definition.fixedLayer().orElseGet(this::selectedPipeLayer), Set.of(direction), definition.volumeM3(), incarnation));
        }
        return List.copyOf(result);
    }

    private static String portId(AtmosMachineProfile.Port definition) {
        return definition.name().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override public Set<InternalEdge> openInternalGasEdges() {
        AtmosMachineProfile profile = resolvedProfile();
        AtmosMachineSettings.Values settings = settingsSnapshot();
        if (profile == null) return Set.of();
        Set<InternalEdge> edges = new java.util.HashSet<>();
        for (AtmosMachineProfile.Port port : profile.ports()) for (AtmosMachineProfile.PortName peer : port.alwaysReachablePeers())
            edges.add(new InternalEdge(port.name().name().toLowerCase(java.util.Locale.ROOT), peer.name().toLowerCase(java.util.Locale.ROOT)));
        if (settingsValid() && profile.kind() == AtmosMachineProfile.Kind.MANUAL_VALVE && profile.valve() != null
                && settings != null && settings.valveOpen().orElse(profile.valve().open())) {
            List<String> ids = profile.ports().stream().map(p -> p.name().name().toLowerCase(java.util.Locale.ROOT)).toList();
            if (ids.size() == 2) edges.add(new InternalEdge(ids.get(0), ids.get(1)));
        }
        return Set.copyOf(edges);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag ports = new ListTag();
        incarnations.forEach((id, uuid) -> { CompoundTag row = new CompoundTag(); row.putString("Id", id); row.putUUID("Uuid", uuid); ports.add(row); });
        tag.put(INCARNATION, ports);
        AtmosMachineProfile profile = resolvedProfile();
        if (settingsOverride != null && profile != null) tag.put("MachineSettings", AtmosMachineSettings.save(profile, settingsOverride));
        else if (pendingSettingsTag != null) tag.put("MachineSettings", pendingSettingsTag.copy());
        if (placementIdentity != null) tag.putUUID("PlacementIdentity", placementIdentity);
        if (profile != null && profile.pipeLayers().isPresent())
            tag.putString(SELECTED_PIPE_LAYER, selectedPipeLayer().name());
        tag.putDouble("CommittedPaidJoules", committedPaidJoules);
        tag.putDouble("CommittedMovedMoles", committedMovedMoles);
        tag.putDouble("CommittedThermalJoules", committedThermalJoules);
        tag.putLong("CommittedWorkEvents", committedWorkEvents);
        tag.putLong("LastCommittedEventTick", lastCommittedEventTick);
        tag.putDouble("AmbientHeatReceiptJoules", ambientHeatReceiptJoules);
        tag.putDouble("SpaceHeatReceiptJoules", spaceHeatReceiptJoules);
    }

    /** Read-only client descriptor payload: persistent host identity/settings/layer, never gas or paid runtime state. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.putBoolean("AmbientEligible", ambientEligible);
        tag.putInt("ManifoldAppearanceMask", manifoldAppearanceMask);
        return tag;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ambientEligible = tag.contains("AmbientEligible") && tag.getBoolean("AmbientEligible");
        manifoldAppearanceMask = tag.contains("ManifoldAppearanceMask") ? tag.getInt("ManifoldAppearanceMask") & 0x3f : 0;
        incarnations.clear();
        ListTag ports = tag.getList(INCARNATION, Tag.TAG_COMPOUND);
        for (int i = 0; i < ports.size(); i++) {
            CompoundTag row = ports.getCompound(i);
            if (row.hasUUID("Uuid")) incarnations.put(row.getString("Id"), row.getUUID("Uuid"));
        }
        settingsOverride = null;
        pendingSettingsTag = tag.contains("MachineSettings", CompoundTag.TAG_COMPOUND)
                ? tag.getCompound("MachineSettings").copy() : null;
        settingsInvalid = false;
        placementIdentity = tag.hasUUID("PlacementIdentity") ? tag.getUUID("PlacementIdentity") : null;
        committedPaidJoules = readNonnegative(tag, "CommittedPaidJoules");
        committedMovedMoles = readNonnegative(tag, "CommittedMovedMoles");
        committedThermalJoules = tag.contains("CommittedThermalJoules") && Double.isFinite(tag.getDouble("CommittedThermalJoules")) ? tag.getDouble("CommittedThermalJoules") : 0;
        committedWorkEvents = Math.max(0, tag.getLong("CommittedWorkEvents"));
        lastCommittedEventTick = tag.contains("LastCommittedEventTick") ? tag.getLong("LastCommittedEventTick") : Long.MIN_VALUE;
        ambientHeatReceiptJoules = readFiniteSigned(tag, "AmbientHeatReceiptJoules");
        spaceHeatReceiptJoules = readNonnegative(tag, "SpaceHeatReceiptJoules");
        selectedPipeLayer = null;
        pendingPipeLayerName = null;
        if (tag.contains(SELECTED_PIPE_LAYER, Tag.TAG_STRING)) {
            pendingPipeLayerName = tag.getString(SELECTED_PIPE_LAYER);
        }
        clearPowerRuntimeState();
        registeredPowerShape = null;
    }
    private static double readNonnegative(CompoundTag tag, String key) {
        double value = tag.contains(key) ? tag.getDouble(key) : 0;
        return Double.isFinite(value) && value >= 0 ? value : 0;
    }
    private static double readFiniteSigned(CompoundTag tag, String key) {
        double value = tag.contains(key) ? tag.getDouble(key) : 0;
        return Double.isFinite(value) ? value : 0;
    }
}
