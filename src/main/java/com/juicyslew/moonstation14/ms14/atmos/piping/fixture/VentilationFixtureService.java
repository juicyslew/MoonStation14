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
        var saved = VentilationFixtureSavedData.existing(level).orElse(null);
        if (saved != null && (saved.entry(origin).isPresent() || saved.count() >= VentilationFixtureSavedData.MAX_FIXTURES))
            return "Fixture already has a saved ownership marker; refusing overwrite.";
        if (!preflight(level, origin)) return "Build refused: footprint must be loaded, clear of entities/fluids/block entities and within world bounds.";
        if (PrototypeRuntime.serverVentilationDevices().get(ModVentilationDevices.SCRUBBER) == null
                || PrototypeRuntime.serverVentilationDevices().get(ModVentilationDevices.VENT) == null
                || ModAirAlarms.catalog(level).get(ModAirAlarms.AIR_ALARM) == null)
            return "Build refused: required resolved ventilation/alarm prototypes are unavailable.";
        // All write targets have been checked before the first mutation.
        for (VentilationFixturePlan.Cell cell : PLAN.blocks()) {
            BlockPos pos = origin.offset(cell.pos());
            switch (cell.role()) {
                case AIR -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                case STEEL -> level.setBlock(pos, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
                case GLASS -> level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
            }
        }
        BlockPos scrub = origin.offset(PLAN.scrubber()), vent = origin.offset(PLAN.vent()), alarm = origin.offset(PLAN.alarm());
        level.setBlock(scrub, ModBlocks.GAS_SCRUBBER.get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, PLAN.scrubberFacing()), 3);
        level.setBlock(vent, ModBlocks.GAS_VENT.get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, PLAN.ventFacing()), 3);
        level.setBlock(alarm, ModBlocks.AIR_ALARM.get().defaultBlockState().setValue(com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock.FACING, PLAN.alarmFacing()), 3);
        if (!(level.getBlockEntity(scrub) instanceof VentilationDeviceBlockEntity scrubber)
                || !(level.getBlockEntity(vent) instanceof VentilationDeviceBlockEntity ventilator)
                || !configureFixtureDevice(scrubber, false) || !configureFixtureDevice(ventilator, false))
            return "Partial placement: ventilation configuration failed; manual recovery required.";
        for (VentilationFixturePlan.PipeNode node : PLAN.pipes()) {
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
        for (var node : PLAN.pipes()) if (!node.concealed()) VisualPipeBlockEntity.refreshLoadedAdjacent(level, origin.offset(node.position()));
        for (var device : PLAN.powerDevices()) {
            var block = switch (device.kind()) {
                case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
                case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
                case APC -> ModBlocks.APC.get();
                default -> throw new IllegalArgumentException("Unsupported fixture power device kind: " + device.kind());
            };
            BlockPos pos = origin.offset(device.position());
            level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, device.facing()), 3);
            if (level.getBlockState(pos).getBlock() != block) return "Partial placement: power device failed; manual recovery required.";
        }
        for (var cable : PLAN.cables()) if (!CableStorage.place(level, origin.offset(cable.host()), cable.face(), cable.tier()))
            return "Partial placement: cable installation failed; manual recovery required.";
        UUID sid = ((VentilationDeviceBlockEntity) level.getBlockEntity(scrub)).deviceId();
        UUID vid = ((VentilationDeviceBlockEntity) level.getBlockEntity(vent)).deviceId();
        UUID aid = UUID.randomUUID(); // Fixture token; alarms have links, not an independent device identity.
        if (sid == null || vid == null || !(level.getBlockEntity(alarm) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarmEntity)
                || !alarmEntity.addDeviceLink((VentilationDeviceBlockEntity) level.getBlockEntity(scrub))
                || !alarmEntity.addDeviceLink((VentilationDeviceBlockEntity) level.getBlockEntity(vent))
                || !VentilationFixtureSavedData.forMutation(level).recordBuilt(origin, sid, vid, aid))
            return "Structure placed but fixture ownership admission failed; manual recovery required.";
        for (BlockPos p : PLAN.sourceCells()) AtmosphereService.INSTANCE.sample(level, origin.offset(p));
        for (BlockPos p : PLAN.recipientCells()) AtmosphereService.INSTANCE.sample(level, origin.offset(p));
        return "Built finite ventilation fixture at absolute origin " + origin.getX()+" "+origin.getY()+" "+origin.getZ() + "; wait for normal atmosphere ownership, then run initialize.";
    }

    public static String initialize(ServerLevel level, BlockPos origin) {
        var data = VentilationFixtureSavedData.existing(level).orElse(null);
        var entry = data == null ? Optional.<VentilationFixtureSavedData.Entry>empty() : data.entry(origin);
        if (entry.isEmpty()) return "No saved fixture at that origin.";
        var e = entry.get(); if (e.initialized()) return "Fixture was already initialized; refusing reseed.";
        if (!footprintLoaded(level, origin)) return "Initialization refused: fixture footprint chunks must all be loaded.";
        BlockPos source = origin.offset(PLAN.scrubber()), receiver = origin.offset(PLAN.vent());
        if (!(level.getBlockEntity(source) instanceof VentilationDeviceBlockEntity s) || !e.scrubberUUID().equals(s.deviceId())
                || !(level.getBlockEntity(receiver) instanceof VentilationDeviceBlockEntity v) || !e.ventUUID().equals(v.deviceId()))
            return "Fixture devices do not match saved identities.";
        if (!(level.getBlockEntity(origin.offset(PLAN.alarm())) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity alarm)
                || alarm.prototype() == null || !alarm.deviceLinksValid() || alarm.deviceLinks().size() != 2 || alarm.deviceLinks().stream().noneMatch(link -> link.deviceId().equals(e.scrubberUUID()))
                || alarm.deviceLinks().stream().noneMatch(link -> link.deviceId().equals(e.ventUUID())))
            return "Fixture air alarm links do not match saved device identities.";
        if (!validatePipeNetwork(level, origin)) return "Fixture pipe network does not match its planned nodes.";
         for (BlockPos p : PLAN.sourceCells()) AtmosphereService.INSTANCE.sample(level, origin.offset(p));
         for (BlockPos p : PLAN.recipientCells()) AtmosphereService.INSTANCE.sample(level, origin.offset(p));
         for (BlockPos p : PLAN.sourceCells()) if (AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(p)).isEmpty()) return "Source room is not fully finite/owned; no gas written.";
        for (BlockPos p : PLAN.recipientCells()) if (AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level, origin.offset(p)).isEmpty()) return "Recipient room is not fully finite/owned; no gas written.";
          if (!VentilationFixtureSavedData.forMutation(level).markInitialized(origin, e.scrubberUUID(), e.ventUUID(), e.fixtureUUID())) return "Initialization guard changed; refusing gas write.";
          Map<GasType, Double> mix = new EnumMap<>(GasType.class); mix.put(GasType.OXYGEN, 8.0); mix.put(GasType.NITROGEN, 30.0); mix.put(PLAN.testGas(), PLAN.testGasMolesPerCell());
         GasMixture fill = new GasMixture(mix, 300.0);
         for (BlockPos p : PLAN.sourceCells()) if (!AtmosphereService.INSTANCE.initializeFiniteMixtureForTrustedSetup(level, origin.offset(p), fill)) return "Partial initialization failure; guard retained, manual rebuild required.";
         for (BlockPos p : PLAN.recipientCells()) if (!AtmosphereService.INSTANCE.initializeFiniteMixtureForTrustedSetup(level, origin.offset(p), GasMixture.vacuum())) return "Partial initialization failure; guard retained, manual rebuild required.";
        if (!configureFixtureDevice(s, true) || !configureFixtureDevice(v, false))
            return "Gas initialized but device configuration failed; manual recovery required.";
         return "Initialized 18 finite room cells per side at absolute origin " + origin.getX()+" "+origin.getY()+" "+origin.getZ()+"; source contains 8 mol O2, 30 mol N2 and 2 mol Plasma per cell.";
    }

    public static String status(ServerLevel level, BlockPos origin) {
        return VentilationFixtureTelemetry.status(level, origin);
    }

    private static boolean preflight(ServerLevel level, BlockPos origin) {
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
            if (!state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(p) != null) return false;
        }
        for (var node : PLAN.pipes()) {
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
