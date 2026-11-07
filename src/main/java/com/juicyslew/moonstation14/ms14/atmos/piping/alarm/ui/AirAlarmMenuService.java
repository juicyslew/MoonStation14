package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmCandidates;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmDeviceLinks;
import com.juicyslew.moonstation14.ms14.atmos.piping.ui.VentilationControlIntentPolicy;
import com.juicyslew.moonstation14.ms14.atmos.piping.ui.VentilationControlRequest;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Main-thread menu capability. Admission is downstream of, never a second call to, the body-use replay gate. */
public final class AirAlarmMenuService {
    enum Validity { VALID, AUTHORITY, ALARM, POWER_UNKNOWN, POWER_INCOMPLETE, OUT_OF_REACH, PATH_UNLOADED, LOS_BLOCKED }
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AirAlarmMenuService.class);
    private record Session(ServerPlayer player, int containerId, long lastRequest, AirAlarmMenu menu) { }
    private static final Map<UUID, Session> SESSIONS = new java.util.HashMap<>();
    private AirAlarmMenuService() { }
    /** Read-only lookup of a currently valid workspace capability for its owning actor and alarm. */
    public static UUID workspaceSession(ServerPlayer actor, net.minecraft.core.BlockPos pos) {
        if (actor == null || pos == null) return null;
        for (var entry : List.copyOf(SESSIONS.entrySet())) {
            Session session = entry.getValue();
            if (session.player() == actor && session.menu().pos().equals(pos)
                    && validPersistent(actor, session.menu())) return entry.getKey();
        }
        return null;
    }
    static void closed(AirAlarmMenu menu) { if (menu.serverLevel() != null) invalidate(menu, "dismissed"); }

    /** Opens a server-owned capability without replacing the actor's current Minecraft container. */
    public static UUID openWorkspace(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admitted) {
        if (actor == null || request == null || admitted == null) return null;
        var resolved = HandActorAuthority.resolve(actor);
        if (resolved.isEmpty() || !resolved.get().equals(admitted.authority())) return null;
        var authority = resolved.get();
        if (!HandActorAuthority.revalidate(actor, authority)
                || !(authority.body().level() instanceof ServerLevel level)
                || admitted.hands() == null || admitted.hands() != authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())
                || !admitted.hands().compatible(authority.body()) || admitted.hands().revision() != request.expectedRevision()) return null;
        var hit = BodyBlockUseTargetProbe.probe(level, authority.body(), request.target(), request.face());
        if (hit == null || !hit.equals(admitted.hit()) || !level.getWorldBorder().isWithinBounds(hit.pos())) return null;
        var chunk = level.getChunkSource().getChunkNow(hit.pos().getX() >> 4, hit.pos().getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || chunk.getBlockState(hit.pos()).getBlock() != ModBlocks.AIR_ALARM.get()
                || !(chunk.getBlockEntity(hit.pos()) instanceof AirAlarmBlockEntity alarm)
                || alarm.getLevel() != level || alarm.isRemoved() || alarm.prototype() == null || !alarm.deviceLinksValid()) return null;
        Validity validity = persistentValidity(actor, level, alarm, authority);
        if (validity != Validity.VALID) { actor.sendSystemMessage(Component.literal("Air alarm closed: " + reason(validity))); return null; }
        alarm.requestDeviceSync();
        UUID session = UUID.randomUUID();
        AirAlarmMenu capability = new AirAlarmMenu(0, hit.pos(), session, level, alarm, authority, request);
        SESSIONS.put(session, new Session(actor, 0, -1, capability));
        AirAlarmNetworking.send(actor, new AirAlarmSessionState(session, true, ""));
        AirAlarmNetworking.send(actor, snapshotForWorkspace(session, capability));
        return session;
    }

    static AirAlarmControlSnapshot apply(ServerPlayer actor, AirAlarmControlRequest request) {
        Session previous = request == null ? null : SESSIONS.get(request.session());
        if (previous == null || previous.player() != actor) return denied(request);
        return applyToMenu(actor, request, previous, previous.menu());
    }

    private static AirAlarmControlSnapshot applyToMenu(ServerPlayer actor, AirAlarmControlRequest request, Session previous, AirAlarmMenu menu) {
        if (!menu.session().equals(request.session()) || !validPersistent(actor, menu)) { invalidate(menu, "invalid"); return denied(request); }
        if (!admit(previous.containerId(), request.session(), previous.lastRequest(), request))
            return denied(request);
        if (request.operation() == AirAlarmControlRequest.Operation.CLOSE) { invalidate(menu, "dismissed"); return denied(request); }
        // Consume each correlated action once, including rejected setup requests.
        SESSIONS.put(menu.session(), new Session(actor, menu.containerId, request.requestId(), menu));
        var level = menu.serverLevel(); var alarm = menu.alarm();
        if (request.operation() == AirAlarmControlRequest.Operation.QUERY) alarm.requestDeviceSync();
        boolean powered = powered(level, alarm);
        if (request.operation() == AirAlarmControlRequest.Operation.QUERY) {
            return snapshot(request, true, powered, level, alarm);
        }
        if (powered && validPersistent(actor, menu)) {
            switch (request.operation()) {
                case PAIR, UNPAIR -> { /* Membership is configured only by the Network Configurator. */ }
                case SET_ENABLED, SET_SCRUBBER_MODE, SET_FILTER_GAS, SET_EXTERNAL_PRESSURE_BOUND, SET_VOLUME_RATE -> {
                    if (alarm.deviceLinks().stream().filter(link -> link.deviceId().equals(request.deviceId())).count() != 1) break;
                    for (var link : alarm.deviceLinks()) {
                        if (!link.deviceId().equals(request.deviceId())) continue;
                        if (!localLink(level, alarm, link)) break;
                        var device = alarm.resolveDeviceLink(link);
                        if (device == null || !device.settingsValid()) break;
                        var current = device.config().settings();
                        var desired = propose(current, device.kind(), request);
                        if (desired.isPresent() && powered(level, alarm) && validPersistent(actor, menu)
                                && alarm.resolveDeviceLink(link) == device)
                            enqueueControl(level, alarm, device, desired.get());
                        break;
                    }
                }
                case QUERY -> { }
            }
        }
        return validPersistent(actor, menu) ? snapshot(request, true, powered(level, alarm), level, alarm) : denied(request);
    }

    private static boolean validPersistent(ServerPlayer actor, AirAlarmMenu menu) {
        return persistentValidity(actor, menu.serverLevel(), menu.alarm(), menu.authority()) == Validity.VALID;
    }

    public static void close(ServerPlayer actor, AirAlarmSessionClose request) {
        Session s = request == null ? null : SESSIONS.get(request.session());
        if (s != null && s.player() == actor) invalidate(s.menu(), "dismissed");
    }
    private static void invalidate(AirAlarmMenu menu, String reason) {
        Session s = SESSIONS.remove(menu.session());
        if (s != null) AirAlarmNetworking.send(s.player(), new AirAlarmSessionState(menu.session(), false, reason));
    }
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) for (Session s : List.copyOf(SESSIONS.values())) if (s.player() == p) invalidate(s.menu(), "logout");
    }
    public static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { SESSIONS.clear(); }
    public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (Session s : List.copyOf(SESSIONS.values())) if (s.menu().serverLevel().getServer() == event.getServer() && !validPersistent(s.player(), s.menu())) invalidate(s.menu(), "invalid");
    }
    private static AirAlarmControlSnapshot snapshotForWorkspace(UUID session, AirAlarmMenu menu) {
        return snapshot(new AirAlarmControlRequest(0, session, 0, new UUID(0, 0), 0,
                AirAlarmControlRequest.Operation.QUERY, false, 0, 0), true, powered(menu.serverLevel(), menu.alarm()), menu.serverLevel(), menu.alarm());
    }

    private static boolean enqueueControl(ServerLevel level, AirAlarmBlockEntity alarm,
            com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device,
            VentilationDeviceConfig.Settings proposed) {
        var runtime = DeviceNetworkRuntime.forLevel(level);
        var sender = runtime.transport().lookup(new DeviceIdentity(alarm.controllerId()));
        if (sender == null || !sender.available()) return false;
        var source = sender.composition().network().orElse(null);
        var target = runtime.transport().lookup(new DeviceIdentity(device.deviceId()));
        var destination = target == null ? null : target.composition().network().orElse(null);
        if (source == null || !source.transmit() || destination == null || destination.address().isBlank()) return false;
        var command = new AtmosDevicePayload.SetState(proposed.revision(), proposed.configuredEnabled(),
                proposed.externalPressureBoundKPa(), proposed.targetPressureChangeKPaPerSecond(),
                proposed.targetPressureKPa(), proposed.volumeRateM3PerSecond(), proposed.scrubberMode(),
                proposed.filteredGases(), proposed.selectedLayer());
        return runtime.transport().enqueue(new DeviceNetworkPacket(sender.identity(), source.networkId(),
                source.transmitFrequency(), source.address(), destination.address(), false, command,
                source.networkId(), source.transmitFrequency()))
                && runtime.transport().enqueue(new DeviceNetworkPacket(sender.identity(), source.networkId(),
                source.transmitFrequency(), source.address(), destination.address(), false, new AtmosDevicePayload.Sync(),
                source.networkId(), source.transmitFrequency()));
    }

    /** Pure capability/replay gate, separate from the pre-consumed block-use sequence. */
    static boolean admit(int containerId, UUID session, long lastRequest, AirAlarmControlRequest request) {
        return request != null && request.containerId() == containerId && session != null
                && session.equals(request.session()) && request.requestId() > lastRequest;
    }

    static Optional<VentilationDeviceConfig.Settings> propose(VentilationDeviceConfig.Settings current,
            VentilationDeviceConfig.Kind kind, AirAlarmControlRequest request) {
        if (current == null || request == null || current.revision() != request.expectedRevision()
                || current.revision() == Long.MAX_VALUE) return Optional.empty();
        VentilationControlRequest.Operation operation = switch (request.operation()) {
            case SET_ENABLED -> VentilationControlRequest.Operation.SET_ENABLED;
            case SET_SCRUBBER_MODE -> VentilationControlRequest.Operation.SET_SCRUBBER_MODE;
            case SET_FILTER_GAS -> VentilationControlRequest.Operation.SET_FILTER_GAS;
            case SET_EXTERNAL_PRESSURE_BOUND -> VentilationControlRequest.Operation.QUERY;
            case SET_VOLUME_RATE -> VentilationControlRequest.Operation.QUERY;
            default -> null;
        };
        if (request.operation() == AirAlarmControlRequest.Operation.SET_EXTERNAL_PRESSURE_BOUND
                || request.operation() == AirAlarmControlRequest.Operation.SET_VOLUME_RATE) {
            if (!AirAlarmNumericBounds.valid(kind, request.numericValue())) return Optional.empty();
            boolean vent = request.operation() == AirAlarmControlRequest.Operation.SET_EXTERNAL_PRESSURE_BOUND;
            if (kind != (vent ? VentilationDeviceConfig.Kind.VENT : VentilationDeviceConfig.Kind.SCRUBBER)) return Optional.empty();
            return Optional.of(new VentilationDeviceConfig.Settings(current.revision(), current.configuredEnabled(),
                    vent ? request.numericValue() : current.externalPressureBoundKPa(), current.targetPressureChangeKPaPerSecond(),
                    current.targetPressureKPa(), vent ? current.volumeRateM3PerSecond() : request.numericValue(),
                    current.scrubberMode(), current.filteredGases(), current.selectedLayer()));
        }
        return operation == null ? Optional.empty() : VentilationControlIntentPolicy.apply(current, kind,
                operation, request.desired(), request.enumIndex());
    }

    static boolean localLink(ServerLevel level, AirAlarmBlockEntity alarm, AirAlarmDeviceLinks.Link link) {
        return level.dimension().equals(link.dimension())
                && link.pos().distSqr(alarm.getBlockPos()) <= (long) AirAlarmCandidates.RANGE_BLOCKS
                    * AirAlarmCandidates.RANGE_BLOCKS;
    }

    static boolean powered(ServerLevel level, AirAlarmBlockEntity alarm) {
        var prototype = alarm.prototype();
        return prototype != null && powered((int) prototype.apcPowerReceiver().requestedWatts(),
                PowerRuntime.airAlarmMeter(level, alarm));
    }

    static Validity persistentValidity(ServerPlayer actor, ServerLevel level, AirAlarmBlockEntity alarm,
                                       HandActorAuthority.Snapshot authority) {
        return persistentValidity(actor, level, alarm, authority, null);
    }

    static Validity persistentValidity(ServerPlayer actor, ServerLevel level, AirAlarmBlockEntity alarm,
                                       HandActorAuthority.Snapshot authority, AirAlarmMenu expectedMenu) {
        if (!(expectedMenu == null ? HandActorAuthority.revalidate(actor, authority)
                : HandActorAuthority.revalidateInMenu(actor, authority, expectedMenu))) return Validity.AUTHORITY;
        if (alarm == null || alarm.getLevel() != level || alarm.isRemoved() || alarm.prototype() == null
                || !alarm.deviceLinksValid()) return Validity.ALARM;
        var meter = PowerRuntime.airAlarmMeter(level, alarm);
        if (meter == null || !meter.known()) return Validity.POWER_UNKNOWN;
        if (meter.deliveredWatts() != 100) return Validity.POWER_INCOMPLETE;
        var pos = alarm.getBlockPos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || chunk.getBlockEntity(pos) != alarm || chunk.getBlockState(pos).getBlock() != ModBlocks.AIR_ALARM.get()) return Validity.ALARM;
        var shape = chunk.getBlockState(pos).getShape(chunk, pos);
        if (shape.isEmpty()) return Validity.ALARM;
        var b = shape.bounds(); Vec3 point = new Vec3(pos.getX()+(b.minX+b.maxX)*.5,pos.getY()+(b.minY+b.maxY)*.5,pos.getZ()+(b.minZ+b.maxZ)*.5);
        return bodyReachReason(level, authority.body(), pos, point);
    }
    static String reason(Validity validity) { return switch (validity) {
        case VALID -> "valid"; case AUTHORITY -> "actor/body authority mismatch"; case ALARM -> "alarm unavailable";
        case POWER_UNKNOWN -> "power reading unknown"; case POWER_INCOMPLETE -> "power incomplete";
        case OUT_OF_REACH -> "body out of reach"; case PATH_UNLOADED -> "body path unloaded"; case LOS_BLOCKED -> "line of sight blocked";
    }; }

    /** Compatibility facade over shared bounded reach. */
    static boolean bodyCanReach(ServerLevel level, LivingEntity body, net.minecraft.core.BlockPos target, Vec3 point) { return bodyReachReason(level,body,target,point)==Validity.VALID; }
    static Validity bodyReachReason(ServerLevel level, LivingEntity body, net.minecraft.core.BlockPos target, Vec3 point) {
        return switch (com.juicyslew.moonstation14.ms14.hands.network.BodyMenuReach.check(level,body,target,point)) {
            case VALID -> Validity.VALID; case OUT_OF_REACH -> Validity.OUT_OF_REACH; case PATH_UNLOADED -> Validity.PATH_UNLOADED; case LOS_BLOCKED -> Validity.LOS_BLOCKED;
        };
    }

    static boolean powered(int requestedWatts, PowerRuntime.VentMeterObservation meter) {
        return requestedWatts == 100 && meter != null && meter.known() && meter.deliveredWatts() == 100;
    }

    private static AirAlarmControlSnapshot denied(AirAlarmControlRequest r) {
        return new AirAlarmControlSnapshot(r.containerId(), r.session(), r.requestId(), false, false, List.of());
    }

    private static AirAlarmControlSnapshot snapshot(AirAlarmControlRequest r, boolean accepted, boolean powered,
                                                    ServerLevel level, AirAlarmBlockEntity alarm) {
        var devices = new ArrayList<AirAlarmControlSnapshot.Device>();
        var seen = new HashSet<UUID>();
        // Persisted links take precedence so offline links remain removable within the 32-entry cap.
        var observations = alarm.observedStates();
        for (var link : alarm.deviceLinks()) {
            if (devices.size() == 32) break;
            var live = localLink(level, alarm, link) ? alarm.resolveDeviceLink(link) : null;
            devices.add(describe(link.deviceId(), link.kind(), link.pos(), true, live, observations));
            seen.add(link.deviceId());
        }
        return new AirAlarmControlSnapshot(r.containerId(), r.session(), r.requestId(), accepted, powered, devices);
    }

    private static AirAlarmControlSnapshot.Device describe(UUID id,
            com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind kind,
            net.minecraft.core.BlockPos pos, boolean linked,
            com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device,
            Map<String, AtmosDevicePayload.State> observations) {
        if (device == null || !device.settingsValid())
            return new AirAlarmControlSnapshot.Device(id, kind, pos, linked, false, false, 0, 0, 0, 0, 0);
        var endpoint = DeviceNetworkRuntime.forLevel((ServerLevel) device.getLevel()).transport()
                .lookup(new DeviceIdentity(device.deviceId()));
        String address = endpoint == null ? "" : endpoint.composition().network()
                .map(com.juicyslew.moonstation14.ms14.devicenet.DeviceComposition.NetworkInterface::address).orElse("");
        var observed = observations.get(address);
        if (address.isBlank() || observed == null)
            return new AirAlarmControlSnapshot.Device(id, kind, pos, linked, false, false, 0, 0, 0, 0, 0);
        int bits = 0, index = 0;
        for (GasType gas : GasType.values()) { if (observed.filteredGases().contains(gas)) bits |= 1 << index; index++; }
        return new AirAlarmControlSnapshot.Device(id, kind, pos, linked, true, observed.enabled(),
                observed.mode().ordinal(), bits, observed.revision(), observed.externalPressureBoundKPa(), observed.volumeRateM3PerSecond());
    }
}

