package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.ModAirAlarms;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Explicit, admin-invoked finite-gas demonstration; never runs as a world tick or generator. */
public final class VentilationFixtureService {
    private static final VentilationFixturePlan PLAN = VentilationFixturePlan.standard();
    private VentilationFixtureService() { }

    public static String build(ServerLevel level, BlockPos origin) {
        return build(level, origin, false);
    }
    /** Places the standard fixture on an already-owned laboratory iron platform (Y=0 only). */
    public static String buildOnLaboratoryFloor(ServerLevel level, BlockPos origin) {
        return build(level, origin, true, false);
    }

    public static String buildFirelock(ServerLevel level, BlockPos origin) { return build(level, origin, false, true); }
    private static String build(ServerLevel level, BlockPos origin, boolean laboratoryFloor) { return build(level, origin, laboratoryFloor, false); }
    private static String build(ServerLevel level, BlockPos origin, boolean laboratoryFloor, boolean firelockVariant) {
        VentilationFixturePlan plan = firelockVariant ? VentilationFixturePlan.firelockPlan() : PLAN;
        var saved = VentilationFixtureSavedData.existing(level).orElse(null);
        if (saved != null && (saved.entry(origin).isPresent() || saved.count() >= VentilationFixtureSavedData.MAX_FIXTURES))
            return "Fixture already has a saved ownership marker; refusing overwrite.";
        if (!preflight(level, origin, plan, laboratoryFloor)) return "Build refused: footprint must be loaded, clear of entities/fluids/block entities and within world bounds.";
        if (PrototypeRuntime.serverVentilationDevices().get(ModVentilationDevices.SCRUBBER) == null
                || PrototypeRuntime.serverVentilationDevices().get(ModVentilationDevices.VENT) == null
                || ModAirAlarms.catalog(level).get(ModAirAlarms.AIR_ALARM) == null)
            return "Build refused: required resolved ventilation/alarm prototypes are unavailable.";
        // All write targets have been checked before the first mutation.
        for (VentilationFixturePlan.Cell cell : plan.blocks()) {
            BlockPos pos = origin.offset(cell.pos());
            switch (cell.role()) {
                case AIR -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                case STEEL -> level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
                case GLASS -> level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
                case LIGHT -> level.setBlock(pos, Blocks.SEA_LANTERN.defaultBlockState(), 3);
            }
        }
        UUID firelockId = null;
        if(firelockVariant){
            BlockPos door=origin.offset(plan.firelock());
            var lower=com.juicyslew.moonstation14.block.ModBlocks.FIRELOCK.get().defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.FACING, firelockVariant ? Direction.EAST : Direction.NORTH);
            level.setBlock(door,lower,3);
            level.setBlock(door.above(),lower.setValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
            if(!(level.getBlockEntity(door) instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity firelock)) return "Partial placement: firelock setup failed; manual recovery required.";
            firelockId=firelock.identity();
        }
        BlockPos scrub = origin.offset(plan.scrubber()), vent = origin.offset(plan.vent()), alarm = origin.offset(plan.alarm());
        level.setBlock(scrub, ModBlocks.GAS_SCRUBBER.get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, plan.scrubberFacing()), 3);
        level.setBlock(vent, ModBlocks.GAS_VENT.get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, plan.ventFacing()), 3);
        level.setBlock(alarm, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock.FACING, plan.alarmFacing()), 3);
        if (!(level.getBlockEntity(scrub) instanceof VentilationDeviceBlockEntity scrubber)
                || !(level.getBlockEntity(vent) instanceof VentilationDeviceBlockEntity ventilator)
                || !configureFixtureDevice(scrubber, false) || !configureFixtureDevice(ventilator, false))
            return "Partial placement: ventilation configuration failed; manual recovery required.";
        for (VentilationFixturePlan.PipeNode node : plan.pipes()) {
            BlockPos pos = origin.offset(node.position());
            if (node.concealed()) {
                if (LoadedSealedWallSegment.prepareOne(level, pos, Direction.NORTH, PipeTopology.Layer.PRIMARY, UUID.randomUUID()).isEmpty())
                    return "Partial placement: concealed pipe admission failed; manual recovery required.";
                if (LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, pos, PipeTopology.Layer.PRIMARY) == false)
                    return "Partial placement: concealed pipe ownership failed; manual recovery required.";
            } else {
                level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
                if (!(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual) || !visual.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY))
                    return "Partial placement: exposed pipe setup failed; manual recovery required.";
                if (LoadedPipeGasOwner.createVacuumLane(level, pos, VisualPipeBlockEntity.PRIMARY).isEmpty())
                    return "Partial placement: exposed pipe ownership failed; manual recovery required.";
            }
        }
        for (var node : plan.pipes()) if (!node.concealed()) VisualPipeBlockEntity.refreshLoadedAdjacent(level, origin.offset(node.position()));
        for (var device : plan.powerDevices()) {
            var block = switch (device.kind()) {
                case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
                case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
                case APC -> ModBlocks.APC.get();
                default -> throw new IllegalArgumentException("Unsupported fixture power device kind: " + device.kind());
            };
            BlockPos pos = origin.offset(device.position());
            if (device.kind() == PowerDeviceKind.APC
                    && !PowerDeviceFixturePlacement.ensureApcSupport(level, pos, device.facing()))
                return "Partial placement: APC support unavailable; manual recovery required.";
            level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, device.facing()), 3);
            if (level.getBlockState(pos).getBlock() != block) return "Partial placement: power device failed; manual recovery required.";
        }
        for (var cable : plan.cables()) if (!CableStorage.place(level, origin.offset(cable.host()), cable.face(), cable.tier()))
            return "Partial placement: cable installation failed; manual recovery required.";
        UUID sid = ((VentilationDeviceBlockEntity) level.getBlockEntity(scrub)).deviceId();
        UUID vid = ((VentilationDeviceBlockEntity) level.getBlockEntity(vent)).deviceId();
        UUID apcId = plan.powerDevices().stream().filter(d -> d.kind() == PowerDeviceKind.APC)
                .map(d -> level.getBlockEntity(origin.offset(d.position())))
                .filter(com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity.class::isInstance)
                .map(com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity.class::cast)
                .map(com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity::apcPowerIdentity).findFirst().orElse(null);
        UUID aid = UUID.randomUUID(); // Fixture token; alarms have links, not an independent device identity.
        if (sid == null || vid == null || !(level.getBlockEntity(alarm) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarmEntity)
                || !alarmEntity.addDeviceLink((VentilationDeviceBlockEntity) level.getBlockEntity(scrub))
                || !alarmEntity.addDeviceLink((VentilationDeviceBlockEntity) level.getBlockEntity(vent))
                 || !VentilationFixtureSavedData.forMutation(level).recordBuilt(origin, sid, vid, aid, firelockVariant, firelockId,
                  ((com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)level.getBlockEntity(alarm)).controllerId(), apcId))
            return "Structure placed but fixture ownership admission failed; manual recovery required.";
        requestLaboratoryDiscovery(level, origin);
        return "Built finite ventilation fixture at absolute origin " + origin.getX()+" "+origin.getY()+" "+origin.getZ() + "; run initialize to prove and seed its enclosed rooms.";
    }

    private static String laboratoryReadiness(ServerLevel level, BlockPos origin, boolean requireOwned) {
        var data = VentilationFixtureSavedData.existing(level).orElse(null);
        var entry = data == null ? Optional.<VentilationFixtureSavedData.Entry>empty() : data.entry(origin);
        if (entry.isEmpty()) return "No saved fixture at that origin.";
        var e = entry.get();
        if(e.firelockVariant()) return "Refused: use firelock_fixture initialize for this owned variant.";
        if (e.initialized()) return "Fixture was already initialized; refusing reseed.";
        if (!footprintLoaded(level, origin)) return "Initialization refused: fixture footprint chunks must all be loaded.";
        BlockPos source = origin.offset(PLAN.scrubber()), receiver = origin.offset(PLAN.vent());
        if (!(level.getBlockEntity(source) instanceof VentilationDeviceBlockEntity s) || !e.scrubberUUID().equals(s.deviceId())
                || !(level.getBlockEntity(receiver) instanceof VentilationDeviceBlockEntity v) || !e.ventUUID().equals(v.deviceId()))
            return "Fixture devices do not match saved identities.";
        if (!(level.getBlockEntity(origin.offset(PLAN.alarm())) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarm)
                || alarm.prototype() == null || !alarm.deviceLinksValid() || alarm.deviceLinks().size() != 2
                || alarm.deviceLinks().stream().noneMatch(link -> link.deviceId().equals(e.scrubberUUID()))
                || alarm.deviceLinks().stream().noneMatch(link -> link.deviceId().equals(e.ventUUID())))
            return "Fixture air alarm links do not match saved device identities.";
        if (!validatePipeNetwork(level, origin)) return "Fixture pipe network does not match its planned nodes.";
        if (requireOwned) {
            for (BlockPos p : PLAN.sourceCells()) if (AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(p)).isEmpty()) return "Source room is not fully finite/owned.";
            for (BlockPos p : PLAN.recipientCells()) if (AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(p)).isEmpty()) return "Recipient room is not fully finite/owned.";
        } else {
            if (!AtmosphereService.INSTANCE.trustedSetupRoomReady(level, PLAN.sourceCells().stream().map(origin::offset).toList(), null)) return "Source room is not physically enclosed; no gas written.";
            if (!AtmosphereService.INSTANCE.trustedSetupRoomReady(level, PLAN.recipientCells().stream().map(origin::offset).toList(), null)) return "Recipient room is not physically enclosed; no gas written.";
        }
        return null;
    }

    public static boolean initializationReadyForLaboratory(ServerLevel level, BlockPos origin) {
        return laboratoryReadinessFailure(level, origin) == null;
    }

    /** Requests bounded discovery only at the fixture's already-loaded, planned finite-room cells. */
    public static void requestLaboratoryDiscovery(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null || !footprintLoaded(level, origin)) return;
        for (BlockPos p : PLAN.sourceCells()) AtmosphereService.INSTANCE.sample(level, origin.offset(p));
        for (BlockPos p : PLAN.recipientCells()) AtmosphereService.INSTANCE.sample(level, origin.offset(p));
    }

    /** Canonical read-only admission cause; requesting discovery is deliberately a separate operation. */
    public static String laboratoryReadinessFailure(ServerLevel level, BlockPos origin) {
        var data = VentilationFixtureSavedData.existing(level).orElse(null);
        var entry = data == null ? Optional.<VentilationFixtureSavedData.Entry>empty() : data.entry(origin);
        if (entry.isEmpty()) return "No saved fixture at that origin.";
        if (entry.get().initialized()) return "Fixture was already initialized; refusing reseed.";
        return laboratoryReadiness(level, origin, false);
    }

    /** Initializes the fixture once, then enables both the finite-gas source and scrubber. */
    public static String startLaboratoryTransfer(ServerLevel level, BlockPos origin) {
        var data = VentilationFixtureSavedData.existing(level).orElse(null);
        var entry = data == null ? Optional.<VentilationFixtureSavedData.Entry>empty() : data.entry(origin);
        if (entry.isEmpty()) return "No saved fixture at that origin.";
        if (entry.get().initialized()) return "Fixture was already initialized; refusing reseed.";
        String initialized = prepareLaboratoryTransfer(level, origin);
        if (initialized.startsWith("Initialized ")) {
            String activated = activateLaboratoryTransfer(level, origin);
            if (!activated.startsWith("Activated ")) return activated;
        }
        return initialized;
    }

    /** Seeds the actual laboratory source and receiver while leaving both devices disabled. */
    public static String prepareLaboratoryTransfer(ServerLevel level, BlockPos origin) {
        return initialize(level, origin, false, false);
    }

    /** Enables a previously prepared laboratory fixture without reseeding or consuming its guard. */
    public static String activateLaboratoryTransfer(ServerLevel level, BlockPos origin) {
        var data = VentilationFixtureSavedData.existing(level).orElse(null);
        var entry = data == null ? Optional.<VentilationFixtureSavedData.Entry>empty() : data.entry(origin);
        if (entry.isEmpty() || !entry.get().initialized()) return "Fixture has not been prepared; refusing activation.";
        String notReady = laboratoryReadiness(level, origin, true);
        if (notReady != null) return notReady;
        BlockPos scrub = origin.offset(PLAN.scrubber()), vent = origin.offset(PLAN.vent());
        if (!(level.getBlockEntity(scrub) instanceof VentilationDeviceBlockEntity s)
                || !(level.getBlockEntity(vent) instanceof VentilationDeviceBlockEntity v)
                || !configureFixtureDevice(s, true) || !configureFixtureDevice(v, true))
            return "Gas prepared but transfer device activation failed; manual recovery required.";
        return "Activated laboratory ventilation transfer.";
    }

    public static String initialize(ServerLevel level, BlockPos origin) {
        return initialize(level, origin, true, false);
    }

    private static String initialize(ServerLevel level, BlockPos origin, boolean sourceEnabled, boolean receiverEnabled) {
        var data = VentilationFixtureSavedData.existing(level).orElse(null);
        var entry = data == null ? Optional.<VentilationFixtureSavedData.Entry>empty() : data.entry(origin);
        if (entry.isEmpty()) return "No saved fixture at that origin.";
        var e = entry.get(); if (e.initialized()) return "Fixture was already initialized; refusing reseed.";
        String readiness = laboratoryReadiness(level, origin, false);
        if (readiness != null) return readiness;
        BlockPos source = origin.offset(PLAN.scrubber()), receiver = origin.offset(PLAN.vent());
        var s = (VentilationDeviceBlockEntity) level.getBlockEntity(source);
        var v = (VentilationDeviceBlockEntity) level.getBlockEntity(receiver);
        Map<GasType, Double> mix = new EnumMap<>(GasType.class); mix.put(GasType.OXYGEN, 8.0); mix.put(GasType.NITROGEN, 30.0); mix.put(PLAN.testGas(), PLAN.testGasMolesPerCell());
        GasMixture fill = new GasMixture(mix, 300.0);
        // Shared readiness already validated both complete domains before consuming the guard;
        // each initializer repeats its proof immediately before its own claim/write phase.
        if (!VentilationFixtureSavedData.forMutation(level).markInitialized(origin, e.scrubberUUID(), e.ventUUID(), e.fixtureUUID())) return "Initialization guard changed; refusing gas write.";
          if (!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level, PLAN.sourceCells().stream().map(origin::offset).toList(), fill)) return "Partial initialization failure; guard retained, manual rebuild required.";
          if (!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level, PLAN.recipientCells().stream().map(origin::offset).toList(), GasMixture.vacuum())) return "Partial initialization failure; guard retained, manual rebuild required.";
          if (!configureFixtureDevice(s, sourceEnabled) || !configureFixtureDevice(v, receiverEnabled))
            return "Gas initialized but device configuration failed; manual recovery required.";
          return "Initialized 18 finite room cells per side at absolute origin " + origin.getX()+" "+origin.getY()+" "+origin.getZ()+"; source contains 8 mol O2, 30 mol N2 and 2 mol Plasma per cell.";
    }

    public static String status(ServerLevel level, BlockPos origin) {
        return VentilationFixtureTelemetry.status(level, origin);
    }

    /** Firelock-specific lifecycle entry point; never admits the legacy Plasma demonstration. */
    public static String initializeFirelock(ServerLevel level, BlockPos origin) {
        var data = VentilationFixtureSavedData.existing(level).orElse(null);
        var entry = data == null ? Optional.<VentilationFixtureSavedData.Entry>empty() : data.entry(origin);
        if (entry.isEmpty() || !entry.get().firelockVariant()) return "Refused: no exact saved firelock_fixture ownership marker.";
        var e = entry.get();
        if (e.initialized()) return "Firelock fixture was already initialized; refusing reseed.";
        if (fixtureAdmission(level, origin, e)==null) return "Refused: exact fixture ownership admission failed.";
        if (!footprintLoaded(level, origin)) return "Initialization refused: fixture footprint chunks must all be loaded.";
        var plan = VentilationFixturePlan.firelockPlan();
        BlockPos scrubPos=origin.offset(plan.scrubber()), ventPos=origin.offset(plan.vent()), alarmPos=origin.offset(plan.alarm());
        if (!(level.getBlockEntity(scrubPos) instanceof VentilationDeviceBlockEntity scrubber) || !e.scrubberUUID().equals(scrubber.deviceId())
                || !(level.getBlockEntity(ventPos) instanceof VentilationDeviceBlockEntity vent) || !e.ventUUID().equals(vent.deviceId()))
            return "Refused: ventilation device identities do not match saved fixture ownership.";
        if (!(level.getBlockEntity(alarmPos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarm)
                || alarm.prototype() == null || !e.alarmUUID().equals(alarm.controllerId())) return "Refused: loaded air alarm identity does not match saved fixture ownership.";
        BlockPos doorPos = origin.offset(plan.firelock());
        if (!(level.getBlockEntity(doorPos) instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity door)
                || !e.firelockUUID().equals(door.identity())) return "Refused: firelock identity does not match fixture ownership.";
        if (!alarm.deviceLinksValid() || alarm.deviceLinks().size()!=2
                || alarm.deviceLinks().stream().noneMatch(link -> link.deviceId().equals(e.scrubberUUID()))
                || alarm.deviceLinks().stream().noneMatch(link -> link.deviceId().equals(e.ventUUID())))
            return "Refused: air alarm links do not match saved ventilation device identities.";
        if (!validatePipeNetwork(level,origin)) return "Refused: fixture pipe network does not match its planned nodes.";

        var cells=plan.firelockConnectedCells().stream().map(origin::offset).toList();
        var alarmOwner=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level).transport().lookupAt(alarmPos);
        var ventOwner=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level).transport().lookupAt(ventPos);
        var doorOwner=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level).transport().lookupAt(doorPos);
        if(alarmOwner.size()!=1||ventOwner.size()!=1||doorOwner.size()!=1
                || !alarmOwner.getFirst().identity().value().equals(e.alarmUUID())
                || !ventOwner.getFirst().identity().value().equals(e.ventUUID())
                || !doorOwner.getFirst().identity().value().equals(e.firelockUUID()))
            return "Initialization deferred: loaded device network endpoints are not ready; retry shortly; no gas written.";
        var settings=vent.config().settings();
        if (!VentilationFixtureSavedData.forMutation(level).markInitialized(origin,e.scrubberUUID(),e.ventUUID(),e.fixtureUUID()))
            return "Initialization guard changed; no fixture mutations were made.";
        StringBuilder atmosphereRejection = new StringBuilder();
        if (!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level,cells,GasMixture.breathableAir(),
                reason -> atmosphereRejection.append(reason)))
            return "Initialization guard admitted setup, but trusted finite-room ownership/gas setup rejected ("+atmosphereRejection+ "); manual rebuild required.";
        if (!new com.juicyslew.moonstation14.ms14.devicenet.DeviceListService().apply(alarmOwner.getFirst(),
                com.juicyslew.moonstation14.ms14.devicenet.DeviceListService.Mutation.set(java.util.List.of(e.ventUUID().toString(),e.firelockUUID().toString()))))
            return "Partial initialization: once-only guard admitted setup, but alarm DeviceList mutation failed; manual inspection/rebuild required.";
        if(!vent.config().compareAndSet(settings.revision(),new VentilationDeviceConfig.Settings(settings.revision(),false,
                settings.externalPressureBoundKPa(),settings.targetPressureChangeKPaPerSecond(),settings.targetPressureKPa(),
                settings.volumeRateM3PerSecond(),settings.scrubberMode(),settings.filteredGases(),settings.selectedLayer())))
            return "Partial initialization: once-only guard admitted setup, but ventilation settings changed; manual inspection/rebuild required.";
        if(!configureFixtureDevice(scrubber,false)) return "Gas initialized but scrubber configuration failed; manual recovery required.";
        return "Initialized connected firelock fixture room with breathable finite gas at absolute origin "+origin.getX()+" "+origin.getY()+" "+origin.getZ()+"; trusted setup is not proof of paid runtime power.";
    }

    public static String resetFirelock(ServerLevel level, BlockPos origin) {
        return mutateFirelockGas(level, origin, "normal", true);
    }

    public static String firelockScenario(ServerLevel level, BlockPos origin, String scenario) {
        return mutateFirelockGas(level, origin, scenario, false);
    }

    public static String setFirelockPower(ServerLevel level, BlockPos origin, boolean on) {
        var e=VentilationFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElse(null);
        var plan=VentilationFixturePlan.firelockPlan(); BlockPos pos=origin.offset(plan.apc());
        var apc=fixtureAdmission(level, origin, e);
        if(e==null||!e.initialized()||apc==null)
            return "Refused: exact initialized firelock fixture/APC ownership validation failed.";
        if(apc.breakerClosed()==on) return "Fixture APC already "+(on?"on":"off")+".";
        return apc.toggleBreakerAuthorized(true,0.0)?"Fixture APC turned "+(on?"on":"off")+"; normal allocation and paid-load telemetry remain authoritative.":"APC breaker transition rejected.";
    }

    private static String mutateFirelockGas(ServerLevel level, BlockPos origin, String scenario, boolean reset) {
        if (!java.util.Arrays.stream(VentilationFixturePlan.FirelockScenario.values()).map(x -> x.name().toLowerCase(java.util.Locale.ROOT)).toList().contains(scenario))
            return "Unknown scenario; choose " + String.join(", ", java.util.Arrays.stream(VentilationFixturePlan.FirelockScenario.values()).map(x -> x.name().toLowerCase(java.util.Locale.ROOT)).toList()) + ".";
        var e = VentilationFixtureSavedData.existing(level).flatMap(data -> data.entry(origin)).orElse(null);
        var plan = VentilationFixturePlan.firelockPlan();
        if (e == null || !e.firelockVariant() || !e.initialized()) return "Refused: initialized exact firelock_fixture ownership marker required.";
        if (!footprintLoaded(level, origin)) return "Refused: fixture footprint chunks must be loaded.";
        BlockPos scrubPos=origin.offset(plan.scrubber()), ventPos=origin.offset(plan.vent()), alarmPos=origin.offset(plan.alarm()), doorPos=origin.offset(plan.firelock());
        if (!(level.getBlockEntity(scrubPos) instanceof VentilationDeviceBlockEntity scrubber) || !e.scrubberUUID().equals(scrubber.deviceId())
                || !(level.getBlockEntity(ventPos) instanceof VentilationDeviceBlockEntity vent) || !e.ventUUID().equals(vent.deviceId())
                || !(level.getBlockEntity(alarmPos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarm) || !e.alarmUUID().equals(alarm.controllerId())
                || !(level.getBlockEntity(doorPos) instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity door) || !e.firelockUUID().equals(door.identity())
                || !alarm.deviceLinksValid() || alarm.deviceLinks().size()!=2 || !validatePipeNetwork(level,origin))
            return "Refused: exact owned devices, alarm links, door, and planned pipe network must validate.";
        var apcPos=origin.offset(plan.apc());
        if (fixtureAdmission(level, origin, e)==null)
            return "Refused: exact fixture APC is unavailable.";

        var all=plan.firelockConnectedCells().stream().map(origin::offset).toList();
        boolean differential=scenario.endsWith("_differential");
        var doorState=door.state();
        if(doorState==null) return "Refused: firelock phase state is unavailable; no gas written.";
        boolean closed=doorState.phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED
                && !level.getBlockState(doorPos).getValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.OPEN);
        if(differential&&(!closed
                || level.getBlockState(doorPos).getBlock()!=com.juicyslew.moonstation14.block.ModBlocks.FIRELOCK.get()
                || level.getBlockState(doorPos.above()).getBlock()!=com.juicyslew.moonstation14.block.ModBlocks.FIRELOCK.get()))
            return "Differential scenarios require a fully CLOSED firelock; run scenario pressure_danger, wait until CLOSED, then apply the differential scenario.";
        var firelockPrototype=door.prototype();
        if(differential&&firelockPrototype==null) return "Refused: resolved firelock prototype thresholds are unavailable; no gas written.";
        var writes=new java.util.LinkedHashMap<BlockPos,GasMixture>();
        var firelockScenario=VentilationFixturePlan.FirelockScenario.valueOf(scenario.toUpperCase(java.util.Locale.ROOT));
        var expectation=VentilationFixturePlan.firelockExpectation(firelockScenario);
        var differentialTargets=differential?VentilationFixturePlan.firelockDifferentialTargets(firelockScenario):null;
        var leftMixture=(GasMixture)null;
        var rightMixture=(GasMixture)null;
        for (BlockPos cell:all) {
            if (closed && (cell.getX()==doorPos.getX() && cell.getZ()==doorPos.getZ())) continue;
            if (AtmosphereService.INSTANCE.sample(level,cell).isEmpty()) return "Refused: finite fixture gas cells are not admitted; no gas written.";
            var endpoint=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,cell);
            if(endpoint.isEmpty()) return "Refused: finite fixture ownership/enclosure validation failed; no gas written.";
            GasMixture current=endpoint.get().snapshot(); if(current==null) return "Refused: fixture gas snapshot unavailable; no gas written.";
            boolean right=cell.getX()>doorPos.getX();
            double pressure=differential?(right?differentialTargets.rightPressureKPa():differentialTargets.leftPressureKPa()):expectation.pressureKPa();
            double temperature=differential?(right?differentialTargets.rightTemperatureKelvin():differentialTargets.leftTemperatureKelvin()):expectation.temperatureKelvin();
            double moles=pressure*1000.0*current.volumeCubicMeters()/(8.3144626*temperature);
            GasMixture generated = new GasMixture(Map.of(GasType.OXYGEN,moles*.21,GasType.NITROGEN,moles*.79),temperature,current.volumeCubicMeters());
            var resolvedVent = vent.prototype();
            if (resolvedVent == null || resolvedVent.atmosMonitor() == null)
                return "Refused: resolved fixture vent atmosphere monitor is unavailable; no gas written.";
            var actualSeverity = com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosMonitorClassification.classify(
                    PrototypeRuntime.serverAtmosAlarmThresholds(), resolvedVent.atmosMonitor(), generated);
            if(differential) { if(right) rightMixture=generated; else leftMixture=generated; }
            else {
                var expectedSeverity = switch (scenario) {
                    case "normal" -> com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity.NORMAL;
                    case "pressure_warning", "temperature_warning" -> com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity.WARNING;
                    case "pressure_danger", "temperature_danger" -> com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity.DANGER;
                    default -> throw new IllegalStateException("validated uniform firelock scenario changed");
                };
                if (actualSeverity != expectedSeverity)
                    return "Refused: generated gas classifies as " + actualSeverity.name().toLowerCase(java.util.Locale.ROOT)
                            + ", not requested " + expectedSeverity.name().toLowerCase(java.util.Locale.ROOT) + "; no gas written.";
            }
            writes.put(cell,generated);
        }
        if(differential) {
            if(leftMixture==null||rightMixture==null) return "Refused: differential pair is incomplete; no gas written.";
            boolean pressureAlarm=Math.abs(differentialTargets.rightPressureKPa()-differentialTargets.leftPressureKPa())>firelockPrototype.pressureDifferenceKpa();
            boolean temperatureAlarm=Math.abs(differentialTargets.rightTemperatureKelvin()-differentialTargets.leftTemperatureKelvin())>firelockPrototype.temperatureDifferenceK();
            boolean wantPressure=differentialTargets.leftPressureKPa()!=differentialTargets.rightPressureKPa();
            boolean wantTemperature=differentialTargets.leftTemperatureKelvin()!=differentialTargets.rightTemperatureKelvin();
            if(pressureAlarm!=wantPressure||temperatureAlarm!=wantTemperature)
                return "Refused: requested differential does not cross resolved firelock thresholds; no gas written.";
        }
        for(var write:writes.entrySet()) if(!AtmosphereService.INSTANCE.initializeFiniteMixtureForTrustedSetup(level,write.getKey(),write.getValue()))
            return "Partial trusted operator gas reset failed at "+write.getKey().toShortString()+"; inspect fixture before retry.";
        return (reset?"Reset":"Applied "+scenario)+" using trusted operator finite-gas setup; cells="+writes.size()+"; APC state unchanged. Normal alarm loop determines severity/door response.";
    }

    private static boolean preflight(ServerLevel level, BlockPos origin, VentilationFixturePlan plan, boolean laboratoryFloor) {
        var saved = VentilationFixtureSavedData.existing(level).orElse(null);
        if (saved != null && saved.count() >= VentilationFixtureSavedData.MAX_FIXTURES) return false;
        var gasSnapshot = PipeGasInventorySavedData.existing(level)
                .map(PipeGasInventorySavedData::snapshot).orElse(null);
        if (gasSnapshot != null) {
            for (var entry : gasSnapshot.snapshot().values()) {
                BlockPos pos = entry.node().position();
                if (pos.getX() >= origin.getX() && pos.getX() < origin.getX() + VentilationFixturePlan.WIDTH
                        && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + VentilationFixturePlan.HEIGHT
                        && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + VentilationFixturePlan.DEPTH)
                    return false;
            }
        }
        for (int x=0;x<VentilationFixturePlan.WIDTH;x++) for (int y=0;y<VentilationFixturePlan.HEIGHT;y++) for(int z=0;z<VentilationFixturePlan.DEPTH;z++) {
            BlockPos p = origin.offset(x,y,z);
            if (p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(p)) return false;
            if (level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, false) == null) return false;
            var state = level.getBlockState(p);
            boolean acceptable = state.isAir() || (laboratoryFloor && y == 0 && state.is(Blocks.IRON_BLOCK));
            if (!acceptable || !state.getFluidState().isEmpty() || level.getBlockEntity(p) != null) return false;
        }
        for (var node : plan.pipes()) {
            BlockPos p = origin.offset(node.position());
            var chunk = level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, false);
            if (chunk == null || level.getBlockEntity(p) != null) return false;
            var data = chunk.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.PIPE_CHUNK.get());
            if (data != null) for (PipeTopology.Layer layer : PipeTopology.Layer.values())
                if (data.get(p.getX() & 15, p.getY(), p.getZ() & 15, layer) != null) return false;
        }
        var box = new net.minecraft.world.phys.AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX()+20, origin.getY()+5, origin.getZ()+12);
        return level.getEntities((net.minecraft.world.entity.Entity)null, box, net.minecraft.world.entity.Entity::isAlive).isEmpty();
    }

    private static boolean footprintLoaded(ServerLevel level, BlockPos origin) {
        for (int x=0;x<VentilationFixturePlan.WIDTH;x++) for (int y=0;y<VentilationFixturePlan.HEIGHT;y++) for(int z=0;z<VentilationFixturePlan.DEPTH;z++) {
            BlockPos p=origin.offset(x,y,z);
            if (p.getY()<level.getMinBuildHeight() || p.getY()>=level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(p)
                    || level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null) return false;
        }
        return true;
    }

    private static boolean validatePipeNetwork(ServerLevel level, BlockPos origin) {
        BlockPos first=origin.offset(PLAN.pipes().stream().filter(node -> !node.concealed()).findFirst().orElseThrow().position());
        var snapshot=LoadedPipeComponent.discover(level, first, VisualPipeBlockEntity.PRIMARY);
        if (snapshot.isEmpty()) return false;
        var expected=PLAN.pipes().stream().map(node -> new PipeTopology.NodeKey(origin.offset(node.position()), PipeTopology.Layer.PRIMARY))
                .collect(java.util.stream.Collectors.toSet());
        return snapshot.get().nodes().stream().map(LoadedPipeComponent.Node::key).collect(java.util.stream.Collectors.toSet()).equals(expected);
    }

    /** Shared exact-membership gate for trusted firelock mutations; returns only the admitted APC. */
    private static com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity fixtureAdmission(
            ServerLevel level, BlockPos origin, VentilationFixtureSavedData.Entry e) {
        if (e == null || !e.firelockVariant() || !footprintLoaded(level, origin)) return null;
        var plan = VentilationFixturePlan.firelockPlan();
        if (!(level.getBlockEntity(origin.offset(plan.scrubber())) instanceof VentilationDeviceBlockEntity scrubber)
                || !e.scrubberUUID().equals(scrubber.deviceId())
                || !(level.getBlockEntity(origin.offset(plan.vent())) instanceof VentilationDeviceBlockEntity vent)
                || !e.ventUUID().equals(vent.deviceId())
                || !(level.getBlockEntity(origin.offset(plan.alarm())) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarm)
                || !e.alarmUUID().equals(alarm.controllerId()) || !hasExpectedFixtureLinks(alarm, e)
                || !(level.getBlockEntity(origin.offset(plan.firelock())) instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity door)
                || !e.firelockUUID().equals(door.identity()) || !validatePipeNetwork(level, origin)) return null;
        BlockPos apcPos=origin.offset(plan.apc());
        if (!(level.getBlockEntity(apcPos) instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity apc)
                || !java.util.Objects.equals(e.apcUUID(), apc.apcPowerIdentity())
                || !(level.getBlockState(apcPos).getBlock() instanceof PowerDeviceBlock block) || block.kind()!=PowerDeviceKind.APC) return null;
        return apc;
    }

    /** Link membership transitions exactly once with the saved initialization guard. */
    private static boolean hasExpectedFixtureLinks(
            com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarm,
            VentilationFixtureSavedData.Entry entry) {
        if (!alarm.deviceLinksValid() || alarm.deviceLinks().size() != 2) return false;
        var expected = entry.initialized()
                ? java.util.Set.of(entry.ventUUID(), entry.firelockUUID())
                : java.util.Set.of(entry.scrubberUUID(), entry.ventUUID());
        var actual = alarm.deviceLinks().stream().map(link -> link.deviceId())
                .collect(java.util.stream.Collectors.toSet());
        return actual.size() == 2 && actual.equals(expected);
    }

    private static boolean configureFixtureDevice(VentilationDeviceBlockEntity be, boolean enabled) {
        var current = be.config().settings();
        var settings = new VentilationDeviceConfig.Settings(current.revision(), enabled, current.externalPressureBoundKPa(),
                current.targetPressureChangeKPaPerSecond(), current.targetPressureKPa(), current.volumeRateM3PerSecond(),
                be.kind()==VentilationDeviceConfig.Kind.SCRUBBER ? VentilationDeviceConfig.ScrubberMode.FILTER : current.scrubberMode(),
                 be.kind()==VentilationDeviceConfig.Kind.SCRUBBER ? java.util.Set.of(PLAN.testGas()) : current.filteredGases(),
                VentilationDeviceConfig.Layer.PRIMARY);
        return be.compareAndSetSettings(current.revision(), settings);
    }
}
