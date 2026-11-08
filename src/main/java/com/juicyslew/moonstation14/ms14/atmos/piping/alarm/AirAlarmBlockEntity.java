package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.AirAlarmData;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePayload;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmReceiverState;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmReportPolicy;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Resolved wall composition and indexed APC receiver; no device-network simulation. */
public final class AirAlarmBlockEntity extends BlockEntity {
    private static final String ID_TAG = "AirAlarmDeviceIdentity";
    private static final String MODE_TAG = "AirAlarmMode";
    private static final String AUTO_TAG = "AirAlarmAutoMode";
    private AirAlarmMode currentMode = AirAlarmMode.FILTERING;
    private boolean automaticMode = true;
    private long stateRevision;
    private boolean controllerInitialized;
    private java.util.UUID controllerId;
    private final AirAlarmDeviceLinks deviceLinks = new AirAlarmDeviceLinks();
    private final AirAlarmObservationCache observations = new AirAlarmObservationCache();
    private final AirAlarmPresetApplication presetApplication = new AirAlarmPresetApplication();
    private final AtmosAlarmReceiverState receiverState = new AtmosAlarmReceiverState();
    private boolean receiverWasPowered;
    private long commandSequence;
    private DeviceEndpoint networkEndpoint;
    public AirAlarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_ALARM.get(), pos, state);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            PowerRuntime.airAlarmChanged(server, worldPosition);
            if (prototype() != null) {
                var definition = prototype().controller();
                if (!controllerInitialized) {
                    currentMode = definition.initialMode(); automaticMode = definition.automaticMode();
                    controllerInitialized = true; setChanged();
                }
                ensureControllerId(); DeviceNetworkRuntime.forLevel(server).loaded(networkEndpoint(server)); refreshObserved(); applyCurrentPreset();
            }
        }
    }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) {
            var runtime = DeviceNetworkRuntime.forLevel(server);
            DeviceIdentity identity = controllerId == null ? null : new DeviceIdentity(controllerId);
            if (identity != null) {
                var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
                boolean replaced = chunk != null && !(chunk.getBlockState(worldPosition).getBlock() instanceof AirAlarmBlock);
                if (networkEndpoint != null && replaced) runtime.retired(identity);
                else runtime.unloaded(identity);
            }
            PowerRuntime.airAlarmRemoved(server, worldPosition);
        }
        super.setRemoved();
    }

    public java.util.List<AirAlarmDeviceLinks.Link> deviceLinks() { return deviceLinks.snapshot(); }
    public boolean deviceLinksValid() { return deviceLinks.valid(); }
    public java.util.Map<String, AtmosDevicePayload.State> observedStates() { return observations.snapshot(); }
    public java.util.Map<java.util.UUID, AtmosAlarmPayload.Measurement> observedReports() {
        return observations.reportSnapshot(deviceLinks.snapshot().stream().map(AirAlarmDeviceLinks.Link::deviceId)
                .collect(java.util.stream.Collectors.toSet()));
    }
    public AtmosAlarmReceiverState alarmReceiverState() { pruneStaleAlarmSources(); return receiverState; }
    public com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity alarmSeverity() {
        pruneStaleAlarmSources(); return receiverState.severity();
    }
    public AirAlarmMode currentMode() { return currentMode; }
    public boolean automaticMode() { return automaticMode; }
    public long stateRevision() { return stateRevision; }
    /** Authoritative server API. Manual selection intentionally leaves AutoMode unchanged, matching AirAlarmSystem. */
    public boolean setMode(AirAlarmMode mode) {
        if (mode == null || !authoritative()) return false;
        currentMode = mode; stateRevision++; setChanged(); applyCurrentPreset(); return true;
    }
    /** Authoritative server API; mode selection occurs only on severity transitions. */
    public boolean setAutomaticMode(boolean enabled) {
        if (!authoritative()) return false;
        if (automaticMode != enabled) { automaticMode = enabled; stateRevision++; setChanged(); }
        return true;
    }
    private boolean authoritative() {
        return level instanceof ServerLevel server && server.getServer().isSameThread() && !isRemoved()
                && server.getBlockEntity(worldPosition) == this && prototype() != null;
    }
    private void selectAutomaticMode(AtmosAlarmSeverity severity) {
        if (!automaticMode) return;
        AirAlarmMode next = severity == AtmosAlarmSeverity.DANGER ? AirAlarmMode.WIDE_FILTERING : AirAlarmMode.FILTERING;
        if (currentMode == next) return;
        currentMode = next; stateRevision++; setChanged(); applyCurrentPreset();
    }
    private void applyCurrentPreset() {
        if (!(level instanceof ServerLevel server) || !authoritative() || !deviceLinks.valid()) return;
        var data = prototype();
        if (data == null || data.controller().presets().get(currentMode) == null) return;
        var runtime = DeviceNetworkRuntime.forLevel(server);
        var sender = networkEndpoint;
        var source = sender == null ? null : sender.composition().network().orElse(null);
        if (source == null || !source.transmit()) return;
        var members = new java.util.ArrayList<AirAlarmPresetApplication.Member>();
        for (var link : deviceLinks.snapshot()) {
            if (link.role().ventilationKind().isEmpty()) continue;
            var device = AirAlarmDeviceLinks.resolve(server, link);
            if (device == null) continue;
            var target = runtime.transport().lookup(new DeviceIdentity(link.deviceId()));
            var destination = target == null ? null : target.composition().network().orElse(null);
            if (destination == null || destination.address().isBlank()) continue;
            members.add(new AirAlarmPresetApplication.Member(link.deviceId(), destination.address()));
        }
        presetApplication.begin(members);
        for (var link : deviceLinks.snapshot()) {
            if (link.role().ventilationKind().isEmpty()) continue;
            var target = runtime.transport().lookup(new DeviceIdentity(link.deviceId()));
            var destination = target == null ? null : target.composition().network().orElse(null);
            if (destination == null) continue;
            var member = new AirAlarmPresetApplication.Member(link.deviceId(), destination.address());
            var observed = observations.snapshot().get(destination.address());
            if (observed == null) queueMembership(runtime, sender, source, link, new AtmosDevicePayload.Sync());
            else sendPreset(server, sender, source, member, link.role().ventilationKind().orElseThrow(), data.controller().presets().get(currentMode), observed);
        }
    }

    private void sendPreset(ServerLevel server, DeviceEndpoint sender, DeviceComposition.NetworkInterface source,
            AirAlarmPresetApplication.Member member, com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind kind,
            AirAlarmData.Preset preset, AtmosDevicePayload.State observed) {
        var command = AirAlarmPresetPlanner.plan(kind, preset, observed);
        if (command == null) { presetApplication.cancel(member); return; }
        long request = presetApplication.claim(member, observed.revision());
        if (request == 0) return;
        var runtime = DeviceNetworkRuntime.forLevel(server);
        runtime.transport().enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(
                sender.identity(), source.networkId(), source.transmitFrequency(), source.address(), member.address(), false,
                new AtmosDevicePayload.SetState(command.expectedRevision(), command.enabled(), command.externalPressureBoundKPa(),
                        command.targetPressureChangeKPaPerSecond(), command.targetPressureKPa(), command.volumeRateM3PerSecond(),
                        command.mode(), command.filteredGases(), command.layer(), command.pressureLockoutOverride(), command.wideNet(), request),
                source.networkId(), source.transmitFrequency()));
    }
    private void pruneStaleAlarmSources() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        AtmosAlarmSeverity before = receiverState.severity();
        var members = deviceLinks.snapshot().stream().map(AirAlarmDeviceLinks.Link::deviceId).collect(java.util.stream.Collectors.toSet());
        for (var source : receiverState.reports().keySet()) {
            if (!source.equals(controllerId) && !members.contains(source))
                receiverState.remove(source);
        }
        if (before != receiverState.severity()) { emitSeverity(receiverState.severity()); selectAutomaticMode(receiverState.severity()); }
    }
    public void updateReceiverPower(boolean powered) {
        AtmosAlarmSeverity before = receiverState.severity();
        if (receiverWasPowered && !powered) { receiverState.actualPowerDown(); publishAlarmReset(); }
        boolean restoring=!receiverWasPowered&&powered;
        receiverWasPowered = powered;
        AirAlarmData data = prototype();
        receiverState.setIgnoreAlarms(data == null || data.atmosAlarmable() == null || data.atmosAlarmable().ignoreAlarms());
        if(restoring && synchronizeAlarmNetwork()) receiverState.markSyncAccepted();
        if (powered && before != receiverState.severity()) { emitSeverity(receiverState.severity()); selectAutomaticMode(receiverState.severity()); }
    }

    public void requestDeviceSync() { refreshObserved(); }

    /** Power freshness boundary for the runtime wiring slice. Staleness retains cached reports. */
    public void observeNetworkPowerFreshness(boolean freshFull) {
        if (!freshFull) { receiverState.markPowerUncertain(); return; }
        if (receiverState.synchronizationPending() && synchronizeAlarmNetwork()) receiverState.markSyncAccepted();
    }

    /** Source membership/register sync and cached aggregate replay form one accepted synchronization. */
    private boolean synchronizeAlarmNetwork() {
        boolean sourceSyncAccepted = refreshObserved();
        boolean aggregateAccepted = publishAlarmAggregate();
        return sourceSyncAccepted && aggregateAccepted;
    }

    private boolean refreshObserved() {
        if (!(level instanceof ServerLevel server) || isRemoved() || !server.getServer().isSameThread()
                || networkEndpoint == null || !networkEndpoint.available()) return false;
        var runtime = DeviceNetworkRuntime.forLevel(server);
        var sender = runtime.transport().lookup(networkEndpoint.identity());
        var source = sender == null ? null : sender.composition().network().orElse(null);
        if (source == null || !source.transmit()) return false;
        var links = deviceLinks.snapshot();
        boolean accepted = true;
        for (var link : links) accepted &= queueMembership(runtime, sender, source, link, new AtmosDevicePayload.Register());
        for (var link : links) accepted &= queueMembership(runtime, sender, source, link, new AtmosDevicePayload.Sync());
        return accepted;
    }

    private static boolean queueMembership(DeviceNetworkRuntime runtime, DeviceEndpoint sender,
            DeviceComposition.NetworkInterface source, AirAlarmDeviceLinks.Link link, DevicePayload payload) {
        var target = runtime.transport().lookup(new DeviceIdentity(link.deviceId()));
        var destination = target == null ? null : target.composition().network().orElse(null);
        // An unloaded/missing peer owns its own synchronization when it loads.
        if (destination == null || destination.address().isBlank() || !destination.receive()) return true;
        return runtime.transport().enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(
                sender.identity(), source.networkId(), source.transmitFrequency(), source.address(),
                destination.address(), false, payload, source.networkId(), source.transmitFrequency()));
    }

    private void membershipChanged(java.util.List<AirAlarmDeviceLinks.Link> previous) {
        observations.clear();
        var currentIds = deviceLinks.snapshot().stream().map(AirAlarmDeviceLinks.Link::deviceId).collect(java.util.stream.Collectors.toSet());
        boolean removedMember=previous.stream().anyMatch(old->!currentIds.contains(old.deviceId()));
        AtmosAlarmSeverity before = receiverState.severity();
        for (var old : previous) if (!currentIds.contains(old.deviceId())) receiverState.remove(old.deviceId());
        for (var old : previous) if (!currentIds.contains(old.deviceId())) presetApplication.cancel(old.deviceId());
        if(removedMember)for(var old:previous)if(!currentIds.contains(old.deviceId()))publishAlarmRemove(old.deviceId());
        if (before != receiverState.severity()) emitSeverity(receiverState.severity());
        setChanged();
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) return;
        var runtime = DeviceNetworkRuntime.forLevel(server);
        var sender = networkEndpoint;
        if (sender == null || !sender.available()) return;
        var source = sender.composition().network().orElse(null);
        if (source == null || !source.transmit()) return;
        var current = deviceLinks.snapshot();
        var ids = current.stream().map(AirAlarmDeviceLinks.Link::deviceId).collect(java.util.stream.Collectors.toSet());
        for (var link : previous) if (!ids.contains(link.deviceId())) {
            queueMembership(runtime, sender, source, link, new AtmosDevicePayload.Deregister());
        }
        refreshObserved();
        applyCurrentPreset();
        publishAlarmAggregate();
    }

    private DeviceEndpoint networkEndpoint(ServerLevel server) {
        if (networkEndpoint == null) networkEndpoint = new DeviceEndpoint() {
            private DeviceComposition resolved() {
                AirAlarmData data = prototype();
                if (data == null) return DeviceComposition.empty();
                DeviceComposition composition = data.deviceComponents().resolve(PrototypeRuntime.serverManager());
                return com.juicyslew.moonstation14.ms14.devicenet.DeviceAddressPolicy.retained(composition,
                        DeviceLinkSavedData.existing(server).flatMap(saved->saved.defaultAddress(identity())));
            }
            public DeviceIdentity identity() { ensureControllerId(); return new DeviceIdentity(controllerId); }
            public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() { return server.dimension(); }
            public BlockPos position() { return worldPosition; }
            public String displayName() { return getBlockState().getBlock().getName().getString(); }
            public DeviceComposition composition() { return resolved(); }
            public boolean available() { var chunk=server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4); return !isRemoved() && getLevel() == server && chunk != null && chunk.getBlockEntity(worldPosition) == AirAlarmBlockEntity.this && prototype() != null; }
            public boolean powered() { AirAlarmData data=prototype(); if(data==null)return false; var meter=PowerRuntime.airAlarmMeter(server, AirAlarmBlockEntity.this); return meter.known() && meter.deliveredWatts() == data.apcPowerReceiver().requestedWatts(); }
            public com.juicyslew.moonstation14.ms14.devicenet.DeviceList deviceList() { var composition=resolved(); return composition.deviceList().map(deviceLinks::deviceList).orElse(null); }
            public boolean mutateDeviceListMembers(com.juicyslew.moonstation14.ms14.devicenet.DeviceList replacement) {
                var current=deviceList();
                if (!available() || replacement == null || current == null || replacement.capacity() != current.capacity()
                        || replacement.allowList() != current.allowList() || replacement.filterIncoming() != current.filterIncoming()) return false;
                var ids=new java.util.LinkedHashSet<java.util.UUID>();
                try { for(String member:replacement.addresses()) if(!ids.add(java.util.UUID.fromString(member)))return false; }
                catch(IllegalArgumentException malformed){return false;}
                if(ids.size()>replacement.capacity())return false;
                if(ids.equals(deviceLinks.snapshot().stream().map(AirAlarmDeviceLinks.Link::deviceId).collect(java.util.stream.Collectors.toSet())))return true;
                return replaceDeviceLinks(ids);
            }
            public void receive(DevicePayload payload, String sourceAddress) {
                if (payload instanceof AtmosAlarmPayload.Relay || payload instanceof AtmosAlarmPayload.RelayRemove || payload instanceof AtmosAlarmPayload.RelayReset || payload instanceof AtmosAlarmPayload.RelaySync) {
                    if(!available()||sourceAddress==null||!deviceLinks.valid()||deviceList()==null)return;
                    {var config=prototype().atmosAlarmable();if(config==null||config.ignoreAlarms())return;}
                    java.util.UUID sourceId;
                    if(payload instanceof AtmosAlarmPayload.Relay relay)sourceId=relay.source();
                    else if(payload instanceof AtmosAlarmPayload.RelayRemove removal)sourceId=removal.source();
                    else if(payload instanceof AtmosAlarmPayload.RelayReset reset)sourceId=reset.source();
                    else sourceId=((AtmosAlarmPayload.RelaySync)payload).requester();
                    for(var link:deviceLinks.snapshot()) {
                        if(!link.deviceId().equals(sourceId)||!AirAlarmDeviceLinks.resolvesEndpoint(server,link))continue;
                        var endpoint=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(sourceId));
                        if(endpoint==null||!endpoint.composition().network().map(n->n.address().equals(sourceAddress)).orElse(false))return;
                        if(payload instanceof AtmosAlarmPayload.RelaySync){publishAlarmAggregate();return;}
                        if(link.role()!=AirAlarmDeviceLinks.EndpointRole.AIR_ALARM)return;
                        var sourceAlarm=chunkAlarm(server,link);
                        if(sourceAlarm==null||sourceAlarm.prototype()==null||sourceAlarm.prototype().atmosAlarmable()==null)return;
                        var sourceTags=sourceAlarm.prototype().atmosAlarmable().sourceTags();
                        AtmosAlarmSeverity before=receiverState.severity();
                        boolean resetAll=payload instanceof AtmosAlarmPayload.RelayReset;
                        if(!AtmosAlarmReportPolicy.applyRelay(receiverState,sourceId,prototype().atmosAlarmable().syncWith(),sourceTags,(AtmosAlarmPayload)payload))return;
                        if(payload instanceof AtmosAlarmPayload.RelayRemove removal)publishAlarmRemove(removal.removedSource());
                         if(before!=receiverState.severity()){com.juicyslew.moonstation14.ms14.atmos.monitor.AlarmTransitionAudio.play(server, worldPosition, before, receiverState.severity(), receiverWasPowered);if(resetAll)publishAlarmReset();emitSeverity(receiverState.severity());selectAutomaticMode(receiverState.severity());setChanged();} return;
                    }
                    return;
                }
                if (payload instanceof AtmosAlarmPayload.Measurement || payload instanceof AtmosAlarmPayload.Reset) {
                    if (!available() || sourceAddress == null || !deviceLinks.valid() || deviceList() == null) return;
                    var receiverConfig = prototype().atmosAlarmable();
                    if (receiverConfig == null || receiverConfig.ignoreAlarms()) return;
                    for (var link : deviceLinks.snapshot()) {
                        if (link.role().ventilationKind().isEmpty()) continue;
                        var source = AirAlarmDeviceLinks.resolve(server, link);
                        if (source == null) continue;
                        var sender = DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(link.deviceId()));
                        var sourceTags = source.prototype().atmosAlarmable();
                        if (sender == null || sourceTags == null || !sender.composition().network().map(net -> net.address().equals(sourceAddress)).orElse(false)) continue;
                        AtmosAlarmSeverity before = receiverState.severity();
                        if (!AtmosAlarmReportPolicy.apply(receiverState, link.deviceId(), receiverConfig.syncWith(),
                                sourceTags.sourceTags(), (AtmosAlarmPayload) payload)) return;
                        if (payload instanceof AtmosAlarmPayload.Measurement measurement)
                            observations.observeReport(link.deviceId(), measurement, deviceLinks.snapshot().stream()
                                    .map(AirAlarmDeviceLinks.Link::deviceId).collect(java.util.stream.Collectors.toSet()));
                        else observations.removeReport(link.deviceId());
                        if(payload instanceof AtmosAlarmPayload.Reset){
                            publishAlarmRemove(link.deviceId());
                            if(before==receiverState.severity())publishAlarmAggregate();
                            else { com.juicyslew.moonstation14.ms14.atmos.monitor.AlarmTransitionAudio.play(server, worldPosition, before, receiverState.severity(), receiverWasPowered); emitSeverity(receiverState.severity()); }
                        } else if (before != receiverState.severity()) {
                            { com.juicyslew.moonstation14.ms14.atmos.monitor.AlarmTransitionAudio.play(server, worldPosition, before, receiverState.severity(), receiverWasPowered); emitSeverity(receiverState.severity()); }
                        }
                        if(before!=receiverState.severity())selectAutomaticMode(receiverState.severity());
                        setChanged();
                        return;
                    }
                    return;
                }
                boolean resultReply = payload instanceof AtmosDevicePayload.CommandResult;
                AtmosDevicePayload.CommandResult result = resultReply ? (AtmosDevicePayload.CommandResult) payload : null;
                AtmosDevicePayload.State state = payload instanceof AtmosDevicePayload.State direct ? direct
                        : result == null ? null : result.current();
                if (state == null || !available() || sourceAddress == null) return;
                if (deviceList() == null) return;
                for (var link : deviceLinks.snapshot()) {
                    if (link.role().ventilationKind().isEmpty()) continue;
                    var vent=AirAlarmDeviceLinks.resolve(server,link);
                    if(vent==null||!link.deviceId().equals(vent.deviceId()))continue;
                    var sender=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(link.deviceId()));
                    if(sender==null||!sender.identity().value().equals(link.deviceId())
                            ||!sender.composition().network().map(DeviceComposition.NetworkInterface::address).orElse("").equals(sourceAddress))continue;
                    var member = new AirAlarmPresetApplication.Member(link.deviceId(), sourceAddress);
                    observations.observe(sourceAddress, state, java.util.Set.of(sourceAddress));
                    setChanged();
                    if (resultReply) {
                        var progress = presetApplication.result(member, result.requestId(), result.applied(), state.revision());
                        if (progress == AirAlarmPresetApplication.Result.RETRY_NEWER_STATE) {
                            var data = prototype(); var net = networkEndpoint.composition().network().orElse(null);
                            if (data != null && net != null) sendPreset(server, networkEndpoint, net, member, link.role().ventilationKind().orElseThrow(),
                                    data.controller().presets().get(currentMode), state);
                        }
                    } else if (presetApplication.pending(member)) {
                        var data = prototype(); var net = networkEndpoint.composition().network().orElse(null);
                        if (data != null && net != null) sendPreset(server, networkEndpoint, net, member, link.role().ventilationKind().orElseThrow(),
                                data.controller().presets().get(currentMode), state);
                    }
                    return;
                }
            }
        };
        return networkEndpoint;
    }

    private static AirAlarmBlockEntity chunkAlarm(ServerLevel server,AirAlarmDeviceLinks.Link link) {
        var chunk=server.getChunkSource().getChunkNow(link.pos().getX()>>4,link.pos().getZ()>>4);
        return chunk!=null&&chunk.getBlockEntity(link.pos()) instanceof AirAlarmBlockEntity alarm?alarm:null;
    }

    private void ensureControllerId() {
        if (controllerId == null && level instanceof ServerLevel server && server.getServer().isSameThread()) {
            controllerId=java.util.UUID.randomUUID(); setChanged();
        }
    }

    private void emitSeverity(AtmosAlarmSeverity severity) {
        if (!(level instanceof ServerLevel server) || !receiverWasPowered || networkEndpoint == null) return;
        var runtime = DeviceNetworkRuntime.forLevel(server);
        com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmPortSignals.emit(
                runtime.links(), runtime.graph(), networkEndpoint, severity);
        publishAlarmAggregate();
    }

    private boolean publishAlarmAggregate() {
        if (!(level instanceof ServerLevel server) || !authoritative() || !receiverWasPowered || networkEndpoint == null || !deviceLinks.valid()) return false;
        var data=prototype(); var sender=DeviceNetworkRuntime.forLevel(server).transport().lookup(networkEndpoint.identity());
        var source=sender==null?null:sender.composition().network().orElse(null);
        if(data==null||data.atmosAlarmable()==null||source==null||!source.transmit())return false;
        var runtime=DeviceNetworkRuntime.forLevel(server);
        boolean accepted=true;
        for(var link:deviceLinks.snapshot()) {
            if(link.role()==AirAlarmDeviceLinks.EndpointRole.VENT||link.role()==AirAlarmDeviceLinks.EndpointRole.SCRUBBER)continue;
            if(!AirAlarmDeviceLinks.resolvesEndpoint(server,link))continue;
            var target=runtime.transport().lookup(new DeviceIdentity(link.deviceId()));
            var destination=target==null?null:target.composition().network().orElse(null);
            if(target==null||destination==null||destination.address().isBlank())continue;
            accepted &= runtime.transport().enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(
                    sender.identity(),source.networkId(),source.transmitFrequency(),source.address(),destination.address(),false,
                    new AtmosAlarmPayload.Relay(controllerId,receiverState.reports(),data.atmosAlarmable().sourceTags()),
                    source.networkId(),source.transmitFrequency()));
        } return accepted;
    }
    private void publishAlarmReset() {
        if(!(level instanceof ServerLevel server)||!authoritative()||!receiverWasPowered||networkEndpoint==null)return;
        var data=prototype();var sender=DeviceNetworkRuntime.forLevel(server).transport().lookup(networkEndpoint.identity());
        var source=sender==null?null:sender.composition().network().orElse(null);
        if(data==null||source==null||!source.transmit())return;
        for(var link:deviceLinks.snapshot()) {
            if(link.role()==AirAlarmDeviceLinks.EndpointRole.VENT||link.role()==AirAlarmDeviceLinks.EndpointRole.SCRUBBER||!AirAlarmDeviceLinks.resolvesEndpoint(server,link))continue;
            var target=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(link.deviceId()));
            var destination=target==null?null:target.composition().network().orElse(null);
            if(destination==null)continue;
            DeviceNetworkRuntime.forLevel(server).transport().enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(sender.identity(),source.networkId(),source.transmitFrequency(),source.address(),destination.address(),false,new AtmosAlarmPayload.RelayReset(controllerId),source.networkId(),source.transmitFrequency()));
        }
    }
    private void publishAlarmRemove(java.util.UUID removedSource) {
        if(!(level instanceof ServerLevel server)||!authoritative()||!receiverWasPowered||networkEndpoint==null)return;
        var data=prototype();var sender=DeviceNetworkRuntime.forLevel(server).transport().lookup(networkEndpoint.identity());
        var source=sender==null?null:sender.composition().network().orElse(null);
        if(data==null||source==null||!source.transmit())return;
        for(var link:deviceLinks.snapshot()) {
            if(link.role()==AirAlarmDeviceLinks.EndpointRole.VENT||link.role()==AirAlarmDeviceLinks.EndpointRole.SCRUBBER||!AirAlarmDeviceLinks.resolvesEndpoint(server,link))continue;
            var target=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(link.deviceId()));
            var destination=target==null?null:target.composition().network().orElse(null);
            if(destination==null)continue;
            DeviceNetworkRuntime.forLevel(server).transport().enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(sender.identity(),source.networkId(),source.transmitFrequency(),source.address(),destination.address(),false,new AtmosAlarmPayload.RelayRemove(controllerId,removedSource,data.atmosAlarmable().sourceTags()),source.networkId(),source.transmitFrequency()));
        }
    }

    public java.util.UUID controllerId() { ensureControllerId(); return controllerId; }

    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel server && networkEndpoint != null)
            DeviceNetworkRuntime.forLevel(server).unloaded(networkEndpoint.identity());
    }

    /** Server-only explicit association; admission requires the resolved concrete DeviceList limit. */
    public boolean addDeviceLink(VentilationDeviceBlockEntity device) {
        AirAlarmData data = prototype();
        if (!(level instanceof ServerLevel server) || data == null || isRemoved()
                || !server.getServer().isSameThread()
                || server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) == null
                || server.getBlockEntity(worldPosition) != this) return false;
        var previous=deviceLinks.snapshot();
        if (!deviceLinks.add(server, device, data.deviceList().maxDevices())) return false;
        setChanged();
        membershipChanged(previous);
        return true;
    }

    public boolean removeDeviceLink(java.util.UUID deviceId) {
        if (!(level instanceof ServerLevel server) || prototype() == null || isRemoved()
                || !server.getServer().isSameThread() || !deviceLinks.valid()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                || chunk.getBlockEntity(worldPosition) != this) return false;
        var previous=deviceLinks.snapshot();
        if (!deviceLinks.remove(deviceId)) return false;
        setChanged();
        membershipChanged(previous);
        return true;
    }

    private boolean replaceDeviceLinks(java.util.Set<java.util.UUID> ids) {
        if (!(level instanceof ServerLevel server)) return false;
        var links=new java.util.ArrayList<AirAlarmDeviceLinks.Link>();
        for(var id:ids){
            var existing=deviceLinks.snapshot().stream().filter(link->link.deviceId().equals(id)).findFirst().orElse(null);
            if(existing!=null){links.add(existing);continue;}
            var endpoint=DeviceNetworkRuntime.forLevel(server).transport().lookup(new DeviceIdentity(id));
            if(endpoint==null||!server.dimension().equals(endpoint.dimension()))return false;
            var pos=endpoint.position(); var chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null||!chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL))return false;
            var be=chunk.getBlockEntity(pos); AirAlarmDeviceLinks.EndpointRole role;
            if(be instanceof VentilationDeviceBlockEntity vent && id.equals(vent.deviceId())) role=AirAlarmDeviceLinks.EndpointRole.ventilation(vent.kind());
            else if(be instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity firelock && id.equals(firelock.identity())) role=AirAlarmDeviceLinks.EndpointRole.FIRELOCK;
            else if(be instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity airlock && airlock.firelockController()!=null && id.equals(airlock.firelockController().identity())) role=AirAlarmDeviceLinks.EndpointRole.FIRELOCK;
            else if(be instanceof AirAlarmBlockEntity alarm && id.equals(alarm.controllerId())) role=AirAlarmDeviceLinks.EndpointRole.AIR_ALARM;
            else return false;
            var link=new AirAlarmDeviceLinks.Link(server.dimension(),pos,role,id);
            if(!AirAlarmDeviceLinks.resolvesEndpoint(server,link))return false;
            links.add(link);
        }
        var previous=deviceLinks.snapshot();
        if(!deviceLinks.replaceLinks(links,ids.size()))return false;
        membershipChanged(previous); return true;
    }
    public VentilationDeviceBlockEntity resolveDeviceLink(AirAlarmDeviceLinks.Link link) {
        if (!(level instanceof ServerLevel server) || prototype() == null || !deviceLinks.valid()
                || !deviceLinks.snapshot().contains(link)) return null;
        return AirAlarmDeviceLinks.resolve(server, link);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        deviceLinks.save(tag);
        if(controllerId!=null)tag.putUUID(ID_TAG, controllerId);
        tag.putString(MODE_TAG, currentMode.name()); tag.putBoolean(AUTO_TAG, automaticMode);
        tag.putLong("AirAlarmControllerStateRevision", stateRevision);
        tag.putBoolean("AirAlarmControllerInitialized", controllerInitialized);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if(tag.hasUUID(ID_TAG))controllerId=tag.getUUID(ID_TAG);
        if (tag.contains(MODE_TAG, CompoundTag.TAG_STRING)) try { currentMode = AirAlarmMode.valueOf(tag.getString(MODE_TAG)); }
        catch (IllegalArgumentException invalid) { currentMode = AirAlarmMode.FILTERING; }
        automaticMode = !tag.contains(AUTO_TAG, CompoundTag.TAG_BYTE) || tag.getBoolean(AUTO_TAG);
        stateRevision = tag.contains("AirAlarmControllerStateRevision", CompoundTag.TAG_LONG)
                ? Math.max(0, tag.getLong("AirAlarmControllerStateRevision")) : 0;
        controllerInitialized = tag.contains("AirAlarmControllerInitialized", CompoundTag.TAG_BYTE)
                ? tag.getBoolean("AirAlarmControllerInitialized") : tag.contains(MODE_TAG, CompoundTag.TAG_STRING);
        // The concrete prototype validates 32; refuse association if resolution later fails.
        deviceLinks.load(tag, 32);
    }

    /** Fail closed until the matching concrete alarm definition has been published. */
    public AirAlarmData prototype() {
        if (level == null || !(getBlockState().getBlock() instanceof AirAlarmBlock)) return null;
        AirAlarmData data = (level.isClientSide ? PrototypeRuntime.clientAirAlarms()
                : PrototypeRuntime.serverAirAlarms()).get(ModAirAlarms.AIR_ALARM);
        if (data == null) return null;
        try {
            data.validate(); // Includes inherited anchored/non-colliding wallmount and 100 W APC demand.
            return data;
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
