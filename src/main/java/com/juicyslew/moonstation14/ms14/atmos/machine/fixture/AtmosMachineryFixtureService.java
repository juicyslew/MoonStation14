package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.*;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureService;
import com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureTelemetry;
import com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.*;

/** Explicit bounded build and read-only inspection of the nine atmosphere machine examples. */
public final class AtmosMachineryFixtureService {
    private AtmosMachineryFixtureService() { }

    public static String build(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null || !level.getServer().isSameThread()) return "Build refused: server thread required.";
        var saved = AtmosMachineryFixtureSavedData.existing(level).orElse(null);
        if (saved != null && saved.entry(origin).isPresent()) return "Build refused: saved fixture already owns this origin.";
        var catalog = ModAtmosMachines.catalog(level);
        var plan = AtmosMachineryFixturePlan.standard(catalog::get);
        var circuits = AtmosMachineryRoomCircuitPlan.standard(plan, origin);
        if (!preflight(level, origin, plan)) return "Build refused: bounded footprint must be empty, in bounds and already loaded.";
        for (var p : plan.placements()) if (catalog.get(ModAtmosMachines.id(p.blockId())) == null)
            return "Build refused: required resolved machine profile is unavailable.";
        for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)
            level.setBlock(origin.offset(x,0,z),Blocks.IRON_BLOCK.defaultBlockState(),3);
        BlockPos ventilationOrigin = origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        String ventilationBuild = VentilationFixtureService.buildOnLaboratoryFloor(level, ventilationOrigin);
        if (!ventilationBuild.startsWith("Built")) return "Partial build: " + ventilationBuild;
        BlockPos crossingRelative = crossingCenter();
        BlockPos crossing = origin.offset(crossingRelative);
        level.setBlock(crossing.below(), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        BlockPos primarySouth = crossingCenter().offset(0, 0, -2);
        BlockPos secondaryEast = crossingCenter().offset(-2, 0, 0);
        for (BlockPos valvePos : crossingValvePositions()) {
            level.setBlock(origin.offset(valvePos), ModBlocks.GAS_VALVE.get().defaultBlockState().setValue(AtmosMachineBlock.FACING, valvePos.equals(primarySouth) ? Direction.SOUTH : Direction.EAST), 3);
            if (!(level.getBlockEntity(origin.offset(valvePos)) instanceof AtmosMachineBlockEntity valve)) return "Partial build: crossing valve entity missing; manual recovery required.";
            valve.reconcileLoadedPorts(level);
            if (valvePos.equals(secondaryEast) && !valve.changePipeLayer(PipeTopology.Layer.SECONDARY)) return "Partial build: secondary crossing valve layer unavailable; manual recovery required.";
            valve.setControl("open", false);
        }
        for (var p : plan.placements()) {
            Block block = blockFor(p.kind());
            BlockPos pos = origin.offset(p.position());
            level.setBlock(pos, block.defaultBlockState().setValue(AtmosMachineBlock.FACING, p.facing()), 3);
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)) return "Partial build: machine entity missing; manual recovery required.";
            machine.reconcileLoadedPorts(level);
            if (p.kind() == AtmosMachineryFixturePlan.Kind.VALVE) machine.setControl("open", false);
            else machine.setControl("enabled", false);
            if (p.kind() == AtmosMachineryFixturePlan.Kind.HEATER || p.kind() == AtmosMachineryFixturePlan.Kind.FREEZER)
                machine.applySettings(AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K,
                        new AtmosMachineSettings.NumericValue(p.kind() == AtmosMachineryFixturePlan.Kind.HEATER ? 350.0 : 250.0));
            if (p.kind() == AtmosMachineryFixturePlan.Kind.FILTER
                    && !machine.applySettings(AtmosMachineSettings.Operation.SET_FILTER_GAS,
                    new AtmosMachineSettings.GasValue(GasType.PLASMA)))
                return "Partial build: filter refused plasma selection; manual recovery required.";
        }
        // Build the planned circuit endpoint chambers, plus the legacy exchanger/miner rooms.
        AtmosMachineryLaboratoryRooms.buildCircuitRooms(level, circuits);
        var exchangerRoom = AtmosMachineryLaboratoryRooms.exchanger(origin);
        AtmosMachineryLaboratoryRooms.build(level, origin, exchangerRoom);
        AtmosMachineryLaboratoryRooms.requestDiscovery(level, exchangerRoom);
        for (var room : AtmosMachineryLaboratoryRooms.miners(origin)) {
            AtmosMachineryLaboratoryRooms.build(level, origin, room);
            AtmosMachineryLaboratoryRooms.requestDiscovery(level, room);
            Block minerBlock;
            if (room.machine().getX() - origin.getX() == AtmosMachineryFixturePlan.MINER_X.get(0)) minerBlock=ModBlocks.GAS_MINER_OXYGEN.get();
            else if (room.machine().getX() - origin.getX() == AtmosMachineryFixturePlan.MINER_X.get(1)) minerBlock=ModBlocks.GAS_MINER_NITROGEN.get();
            else minerBlock=ModBlocks.GAS_MINER_PLASMA.get();
            level.setBlock(room.machine(), minerBlock.defaultBlockState(), 3);
        }
        for (var label : plan.labels()) {
            List<Component> lines=stationLabelLines(label).stream().map(line -> (Component) Component.literal(line)).toList();
            if (!writeLabelLines(level, origin.offset(label.position()), lines)) return "Partial build: fixture sign setup failed; manual recovery required.";
        }
        for (var endpoint : circuits.endpoints()) {
            String gas=AtmosMachineryFixtureProofSignFormatter.operatorGasLabel(roomTracer(endpoint));
            String title=switch(endpoint.role()) { case SOURCE,MANIFOLD_IN -> "Pump SRC"; case RECEIVER,MANIFOLD_OUT -> "Pump ROOM"; case THERMAL_ROOM -> endpoint.station()==AtmosMachineryFixturePlan.Kind.HEATER?"Heater ROOM":"Freezer ROOM"; case FILTERED -> "Filter SELECT"; case EXHAUST -> "Filter OUT"; case EXCHANGER_ROOM -> "HX ROOM"; };
            String second=endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM?"Measured gas temperature":"PLANNED "+gas;
            String third=endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM?"T0 after selected start":"Start:"+String.format(Locale.ROOT,"%.0f",endpoint.initialInventory().totalMoles())+"mol";
            if (!writeLabel(level, endpoint.room().labelPosition(), title+"\n"+second+"\n"+third+"\nNo reaction"))
                return "Partial build: room label setup failed; manual recovery required.";
            if(endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                    ||endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM) {
                BlockPos indicator=thermalIndicatorPosition(endpoint);
                if(!level.getBlockState(indicator).isAir()||level.getBlockEntity(indicator)!=null)
                    return "Partial build: thermal gauge position is occupied at "+indicator+".";
                level.setBlock(indicator,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,false),3);
            }
        }
        if (!writeLabel(level, origin.offset(crossingCenter()).offset(3, 0, 3), "Crossing\nLayers stay\nseparate")) return "Partial build: crossing sign setup failed; manual recovery required.";
        for (var room : AtmosMachineryLaboratoryRooms.miners(origin)) {
            Component minerName = level.getBlockState(room.machine()).getBlock().getName();
            if (!writeLabel(level, room.labelPosition(), minerName))
                return "Partial build: miner sign setup failed; manual recovery required.";
        }
        BlockPos ventilationLabelOrigin = origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        if (!writeLabel(level, ventilationLabelOrigin.offset(4, 1, -1), "Scrubber\nSource room")
                || !writeLabel(level, ventilationLabelOrigin.offset(12, 1, -1), "Vent\nRecipient room"))
            return "Partial build: ventilation sign setup failed; manual recovery required.";
        Set<BlockPos> lanes = new LinkedHashSet<>();
        for (var p : plan.placements()) {
            for (var seed : concat(p.inletSeeds(), p.outletSeeds())) lanes.add(seed.position());
        }
        var freezer = plan.placements().stream().filter(p -> p.kind() == AtmosMachineryFixturePlan.Kind.FREEZER).findFirst().orElseThrow();
        Map<BlockPos, Set<PipeTopology.Layer>> laneLayers = new LinkedHashMap<>();
        for (var p : plan.placements()) for (var seed : concat(p.inletSeeds(), p.outletSeeds()))
            laneLayers.computeIfAbsent(seed.position(), ignored -> new LinkedHashSet<>()).add(seed.layer());
        for (var entry : laneLayers.entrySet()) {
            BlockPos pos = origin.offset(entry.getKey());
            level.setBlock(pos, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
            if (!(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual)) return "Partial build: pipe lane setup failed; manual recovery required.";
            boolean first = true;
            for (PipeTopology.Layer layer : entry.getValue()) {
                int bit = layerBit(layer);
                boolean placed = first ? visual.setLayersForPlacement(bit) : visual.addFunctionalPlacementLayer(bit);
                first = false;
                if (!placed || LoadedPipeGasOwner.createVacuumLane(level, pos, bit).isEmpty())
                    return "Partial build: pipe lane setup failed; manual recovery required.";
            }
        }
        var crossingPorts = new ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
        for (var point : crossingPipePositions()) {
            level.setBlock(origin.offset(point), ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
            int layer = crossingSecondaryOnly().contains(point) ? VisualPipeBlockEntity.SECONDARY : VisualPipeBlockEntity.PRIMARY;
            if (!(level.getBlockEntity(origin.offset(point)) instanceof VisualPipeBlockEntity visual)
                    || !visual.setLayersForPlacement(layer)
                    || (point.equals(crossingRelative) && !visual.addFunctionalPlacementLayer(VisualPipeBlockEntity.SECONDARY))
                    || LoadedPipeGasOwner.createVacuumLane(level, origin.offset(point), layer).isEmpty()
                    || (point.equals(crossingRelative) && LoadedPipeGasOwner.createVacuumLane(level, origin.offset(point), VisualPipeBlockEntity.SECONDARY).isEmpty()))
                return "Partial build: crossing pipe setup failed; manual recovery required.";
        }
        for (BlockPos p : crossingValvePositions()) VisualPipeBlockEntity.refreshLoadedAdjacent(level, origin.offset(p));
        List<AtmosMachineryFixtureSavedData.PortOwner> owners = new ArrayList<>();
        List<TrustedPipeFixtureInitialization.PortSeed> seeds = new ArrayList<>();
        for (var valvePosAndDirection : List.of(Map.entry(primarySouth, Direction.SOUTH), Map.entry(secondaryEast, Direction.EAST))) {
            var machine = (AtmosMachineBlockEntity) level.getBlockEntity(origin.offset(valvePosAndDirection.getKey()));
            for (var port : machine.gasPorts()) {
                if (port.directions().contains(valvePosAndDirection.getValue())) {
                    var gas = new GasMixture(Map.of(port.layer() == PipeTopology.Layer.PRIMARY ? GasType.OXYGEN : GasType.NITROGEN, 3.0), 293.15);
                    crossingPorts.add(new TrustedPipeFixtureInitialization.PortSeed(origin.offset(valvePosAndDirection.getKey()), port.id(), port.incarnation(), new GasMixture(gas.gasMoles(), gas.temperatureKelvin(), port.volume())));
                }
            }
        }
        owners.addAll(crossingOwners(level, origin));
        for (BlockPos relative : lanes) VisualPipeBlockEntity.refreshLoadedAdjacent(level, origin.offset(relative));

        // Room devices are placed only after the appliance ports and ordinary pipe lanes exist.
        // Thermal endpoints use their planned vent hosts and ordinary cable/network placement.
        var roomDevices = AtmosMachineryRoomCircuitPlacement.place(level, circuits);
        if (!roomDevices.successful()) return "Partial build: room circuit placement failed (" + roomDevices.failure() + "); manual recovery required.";

        if (!AtmosMachineryFixturePower.installLaboratorySupplies(level, origin))
            return "Partial build: laboratory aisle power supply setup failed; manual recovery required.";
        List<AtmosMachineBlockEntity> poweredMachines = new ArrayList<>();
        for (var placement : plan.placements()) {
            BlockPos machinePos = origin.offset(placement.position());
            if (level.getBlockEntity(machinePos) instanceof AtmosMachineBlockEntity machine
                    && machine.powerRuntimePorts().stream().anyMatch(port -> port.tier() == com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC))
                poweredMachines.add(machine);
        }
        if (!AtmosMachineryFixturePower.connectLaboratoryAppliances(level, origin, poweredMachines))
            return "Partial build: laboratory appliance power cable setup failed; manual recovery required.";
        List<BlockPos> roomVentHosts = AtmosMachineryFixturePower.laboratoryRoomVentHosts(circuits);
        BlockPos failedReceiverCableHost = AtmosMachineryFixturePower.connectLaboratoryRoomVentReceiversFailure(level, origin, roomVentHosts);
        if (failedReceiverCableHost != null)
            return "Partial build: room ventilation receiver cable setup failed at host " + failedReceiverCableHost + "; manual recovery required.";
        seeds.addAll(crossingPorts);
        for (var p : plan.placements()) {
            BlockPos pos = origin.offset(p.position());
            var machine = (AtmosMachineBlockEntity) level.getBlockEntity(pos);
            for (var port : machine.gasPorts()) {
                owners.add(new AtmosMachineryFixtureSavedData.PortOwner(pos, port.id(), port.incarnation()));
                // Appliance ports are inventory endpoints, not authored seed stock. Their
                // connected finite room circuits are initialized at laboratory start instead.
            }
        }
        var guard = AtmosMachineryFixtureSavedData.forMutation(level);
        if (!guard.recordBuilt(origin, owners)) return "Fixture ownership admission failed; manual recovery required.";
        var roomOwners = roomDevices.devices().stream().map(d -> new AtmosMachineryFixtureSavedData.RoomDeviceOwner(d.position(), d.deviceId())).toList();
        if (!guard.recordRoomDevices(origin, roomOwners)) return "Fixture built; room device identity admission failed; manual recovery required.";
        List<AtmosMachineryFixtureSavedData.MinerOwner> minerOwners = new ArrayList<>();
        for (var room : AtmosMachineryLaboratoryRooms.miners(origin)) {
            if (!(level.getBlockEntity(room.machine()) instanceof GasMinerBlockEntity miner)
                    || miner.resolvedProfile() == null || miner.placementIdentity() == null)
                return "Fixture built; miner identity/profile unavailable; manual recovery required.";
            minerOwners.add(new AtmosMachineryFixtureSavedData.MinerOwner(room.machine(), miner.placementIdentity()));
        }
        if (!guard.recordMiners(origin, minerOwners)) return "Fixture built; miner ownership admission failed; manual recovery required.";
        var ready = TrustedPipeFixtureInitialization.validateVacuumPorts(level, seeds);
        if (!ready.successful()) return "Fixture built; inlet initialization refused: " + ready.failure() + ".";
        if (!guard.markInitialized(origin, owners)) return "Fixture built; initialization guard changed, refusing gas write.";
        var result = TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, seeds);
        return result.successful() ? "Built nine-machine glass-room lab at " + origin.toShortString() + ". Start initializes finite room stock."
                : "Fixture built; partial inlet initialization (" + result.failure() + ").";
    }

    private static boolean writeLabel(ServerLevel level, BlockPos signPos, String text) {
        List<Component> lines = Arrays.stream(text.split("\\n", 4)).map(line -> (Component) Component.literal(line)).toList();
        return writeLabelLines(level, signPos, lines);
    }

    /** Canonical, four-line station sign including the exact Brigadier literal. */
    public static List<String> stationLabelLines(AtmosMachineryFixturePlan.Label label) {
        String[] description=label.text().split("\\n",-1);
        if(description.length!=2)throw new IllegalArgumentException("station label must provide a short title and detail");
        return List.of(description[0],description[1],"start",AtmosMachineryFixtureCommands.demoLiteral(label.kind()));
    }

    private static boolean writeLabel(ServerLevel level, BlockPos signPos, Component text) {
        return writeLabelLines(level, signPos, List.of(text));
    }

    private static boolean writeLabelLines(ServerLevel level, BlockPos signPos, List<Component> lines) {
        var existing = level.getBlockEntity(signPos);
        if (existing != null && !(existing instanceof SignBlockEntity)) return false;
        level.setBlock(signPos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), 3);
        if (!(level.getBlockEntity(signPos) instanceof SignBlockEntity sign)) return false;
        var front = sign.getFrontText();
        for (int i = 0; i < 4; i++) front = front.setMessage(i, i < lines.size() ? lines.get(i) : Component.empty());
        sign.setText(front, true);
        sign.setChanged();
        return true;
    }

    public static String status(ServerLevel level, BlockPos origin) {
        return statusComponent(level, origin).getString();
    }

    public static Component statusComponent(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) return Component.literal("Invalid fixture location.");
        var plan = AtmosMachineryFixturePlan.standard(ModAtmosMachines.catalog(level)::get);
        if (!footprintLoaded(level, origin, plan)) return Component.literal("Status unavailable: fixture footprint is not fully loaded.");
        var saved = AtmosMachineryFixtureSavedData.existing(level).flatMap(d -> d.entry(origin)).orElse(null);
        if (saved == null) return Component.literal("Status unavailable: no saved fixture ownership entry exists for this origin.");
        List<AtmosMachineryFixtureSavedData.PortOwner> currentOwners = new ArrayList<>();
        for (var placement : plan.placements()) {
            BlockPos ownerPos = origin.offset(placement.position());
            if (level.getBlockEntity(ownerPos) instanceof AtmosMachineBlockEntity owner)
                for (var port : owner.gasPorts()) currentOwners.add(new AtmosMachineryFixtureSavedData.PortOwner(ownerPos, port.id(), port.incarnation()));
        }
        currentOwners.addAll(crossingOwners(level, origin));
        for (BlockPos valvePos : crossingValvePositions()) {
            BlockPos pos = origin.offset(valvePos);
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)) return Component.literal("Status unavailable: crossing valve missing.");
        }
        boolean ownershipMatches = saved.origin().equals(origin)
                && new HashSet<>(saved.expectedPortOwners()).equals(new HashSet<>(currentOwners));
        var out = Component.literal("Atmos machinery at " + origin.toShortString() + ":");
        for (var p : plan.placements()) {
            BlockPos pos = origin.offset(p.position());
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)) {
                out.append(Component.literal("\n")).append(machineName(level, pos, p.kind())).append(Component.literal(": missing")); continue;
            }
            var settings = machine.settingsSnapshot();
                out.append(Component.literal("\n")).append(machineName(level, pos, p.kind())).append(Component.literal(": "));
            if (!ownershipMatches) out.append("Fixture ownership: incarnation mismatch; saved fixture is no longer current. ");
            if (settings != null) {
                settings.enabled().ifPresent(v -> out.append("enabled=").append(String.valueOf(v)).append(" "));
                settings.valveOpen().ifPresent(v -> out.append("open=").append(String.valueOf(v)).append(" "));
                settings.targetPressureKpa().ifPresent(v -> out.append("target=" + String.format(Locale.ROOT, "%.2f kPa ", v)));
                settings.transferRateM3PerSecond().ifPresent(v -> out.append("rate=" + String.format(Locale.ROOT, "%.2f m³/s ", v)));
                settings.mixerFractionA().ifPresent(v -> out.append("fractionA=" + String.format(Locale.ROOT, "%.2f ", v)));
                settings.filterGas().ifPresent(v -> out.append("filterGas=").append(String.valueOf(v)).append(" "));
                settings.thermostatSetpointK().ifPresent(v -> out.append("thermostat=" + String.format(Locale.ROOT, "%.2f K ", v)));
            }
            if (p.kind() == AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP || p.kind() == AtmosMachineryFixturePlan.Kind.VOLUME_PUMP || p.kind() == AtmosMachineryFixturePlan.Kind.FREEZER || p.kind() == AtmosMachineryFixturePlan.Kind.HEATER)
                out.append("power=" + (machine.apcAllocationKnown() ? String.format(Locale.ROOT, "%.2f W", machine.apcAllocatedWatts()) : "unknown"));
            for (var port : machine.gasPorts()) {
                var gas = PipeGasInventorySavedData.existing(level).map(d -> d.snapshot().get(port.key(pos)).orElse(null)).orElse(null);
                var mixture=gas==null?null:PipeGasInventorySavedData.existing(level).map(d->d.snapshot().nodeShare(port.key(pos))).orElse(null);
                out.append("\n  ").append(port.id()).append(" [").append(port.layer().toString()).append("]: ").append(mixture == null ? "unavailable" : formatMoles(mixture.gasMoles())
                        + " mol, " + String.format(Locale.ROOT, "%.2f kPa, %.2f K", mixture.pressureKpa(mixture.volumeCubicMeters()), mixture.temperatureKelvin()));
            }
        }
        // Compact operator-facing receipts: configuration alone is not evidence that a
        // device moved or heated gas. Compare committed port readings to the saved start
        // baseline, and keep an already observed result distinct from its current idle state.
        out.append("\n\nDevice work (committed readings; baseline → now):");
        var inventory = PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        var roomValues=roomMeasurements(level,origin);
        for (var p : plan.placements()) {
            BlockPos pos = origin.offset(p.position());
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)) continue;
            out.append("\n  ").append(machineName(level, pos, p.kind())).append(": ");
            if (inventory == null || machine.gasPorts().isEmpty()) { out.append("reading unavailable"); continue; }
            var ports = machine.gasPorts();
            Map<String, GasMixture> readings = new LinkedHashMap<>();
            for (var port : ports) {
                var entry = inventory.get(port.key(pos)).orElse(null);
                if (entry == null) continue;
                var mixture = inventory.nodeShare(entry.node());
                readings.put(port.id().toUpperCase(Locale.ROOT), mixture);
                String key = "port." + p.kind().name().toLowerCase(Locale.ROOT) + "." + port.id();
                Double before = saved.baseline().get(key + ".moles");
                double now = mixture.totalMoles();
                if (before != null && Double.isFinite(now)) {
                    out.append(port.id()).append(" ").append(before < now ? "↑" : before > now ? "↓" : "=")
                            .append(String.format(Locale.ROOT, " %.2f→%.2f mol", before, now));
                } else out.append(port.id()).append(" ").append(String.format(Locale.ROOT, "%.2f mol", now));
                out.append(" ").append(formatMoles(mixture.gasMoles())).append("; ");
            }
             out.append("; ").append(deviceOutcome(p.kind(), machine, readings, saved, pos, roomValues));
        }
        appendMinerReceipts(out, level, origin, saved);
        appendCrossingAndManifold(out, level, origin, plan, inventory);
        BlockPos ventilationOrigin = origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        out.append("\n\n").append(VentilationFixtureTelemetry.status(level, ventilationOrigin));
        appendScrubberReceipt(out,level,ventilationOrigin,saved);
        Map<String, Double> currentMeasurements = laboratoryMeasurements(level, origin);
        out.append("\n\nLaboratory measurements (change from pre-start baseline):");
        for (var measurement : currentMeasurements.entrySet()) {
            Double baseline = saved.baseline().get(measurement.getKey());
            out.append("\n  ").append(measurement.getKey()).append("=")
                    .append(String.format(Locale.ROOT, "%.2f", measurement.getValue()));
            if (baseline == null) out.append(" (baseline unavailable)");
            else out.append(" (Δ ").append(String.format(Locale.ROOT, "%+.2f", measurement.getValue() - baseline)).append(")");
        }
        out.append("\nFinite room stock (baseline → now):");
        for(var e:roomValues.entrySet()) {
            if(!e.getKey().endsWith(".moles"))continue;
            Double before=saved.baseline().get(e.getKey());
            out.append("\n  ").append(e.getKey()).append(" ").append(before==null?"?":String.format(Locale.ROOT,"%.2f",before))
                    .append("→").append(String.format(Locale.ROOT,"%.2f",e.getValue())).append(" mol");
            String prefix=e.getKey().substring(0,e.getKey().length()-".moles".length());
            for(String species:List.of("plasmaMoles","nitrogenMoles","water_vaporMoles")) {
                Double n=roomValues.get(prefix+"."+species);
                if(n!=null)out.append(" ").append(species).append("=").append(String.format(Locale.ROOT,"%.2f",n));
            }
            Double temp=roomValues.get(prefix+".temperatureK");
            if(temp!=null)out.append(" ").append(String.format(Locale.ROOT,"%.2f K",temp));
        }
        refreshProofSigns(level,origin,plan,saved,roomValues);
        return out;
    }

    private static String deviceOutcome(AtmosMachineryFixturePlan.Kind kind, AtmosMachineBlockEntity machine,
                                        Map<String, GasMixture> gas, AtmosMachineryFixtureSavedData.Entry saved, BlockPos pos, Map<String,Double> rooms) {
        boolean started=saved.reservedDemos().contains(kind.name().toLowerCase(Locale.ROOT));
        return switch(kind) {
            case PRESSURE_PUMP, VOLUME_PUMP -> {
                var in=gas.get("INLET"); var out=gas.get("OUTLET");
                double source=in==null?Double.NaN:in.totalMoles(), target=out==null?Double.NaN:out.totalMoles();
                var inlet=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("inlet")).findFirst().orElse(null);
                var outlet=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("outlet")).findFirst().orElse(null);
                var proof=pumpRoomReadings(kind,saved,rooms);
                double movedMoles=machine.committedMovedMoles()-saved.baseline().getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".movedMoles",0d);
                double paid=machine.committedPaidJoules()-saved.baseline().getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".paidJ",0d);
                boolean moved=pumpTransferObserved(proof,movedMoles,paid,machine.apcAllocationKnown());
                var settings=machine.settingsSnapshot();
                boolean targetOrRate=kind==AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP
                        ?settings.targetPressureKpa().isPresent()&&out!=null&&out.pressureKpa(out.volumeCubicMeters())<=settings.targetPressureKpa().get()+0.01
                        :settings.transferRateM3PerSecond().isPresent()&&settings.transferRateM3PerSecond().get()>0;
                yield String.format(Locale.ROOT,"room source %.2f→%.2f mol; room receiver %.2f→%.2f mol; outlet %.2f kPa; %s",proof.sourceBefore(),proof.sourceNow(),proof.receiverBefore(),proof.receiverNow(),out==null?Double.NaN:out.pressureKpa(out.volumeCubicMeters()),moved&&targetOrRate?"paid transfer observed":started?"no paid transfer observed yet":"pending start");
            }
            case MIXER -> { var m=gas.get("OUTLET"); double total=m==null?0:m.totalMoles(); double fraction=total<=0?Double.NaN:m.moles(GasType.PLASMA)/total;
                yield String.format(Locale.ROOT,"outlet Plasma %.2f%% / water vapor %.2f%%",fraction*100,total<=0?Double.NaN:100*m.moles(GasType.WATER_VAPOR)/total); }
            case FILTER -> { var selected=gas.get("FILTER"); var ordinary=gas.get("OUTLET"); var species=machine.settingsSnapshot().filterGas().orElse(null);
                if(species==null)yield "no filter gas selected; all gas is expected at ordinary outlet";
                String label=AtmosMachineryFixtureProofSignFormatter.operatorGasLabel(species);
                 yield "selected outlet "+label+" "+fmt(selected==null?Double.NaN:selected.moles(species))+" mol; water vapor pass "+fmt(ordinary==null?Double.NaN:ordinary.moles(GasType.WATER_VAPOR))+" mol"; }
            case VALVE -> {
                boolean open=machine.settingsSnapshot().valveOpen().orElse(false);
                var proof=valveRoomProof(saved,rooms);
                boolean observed=valveRoomFlowObserved(open,proof.sourceBefore(),proof.sourceNow(),proof.receiverBefore(),proof.receiverNow());
                yield String.format(Locale.ROOT,"source room ΔPl %.2f mol; destination room ΔPl %.2f mol; %s",proof.sourceNow()-proof.sourceBefore(),proof.receiverNow()-proof.receiverBefore(),open?(observed?"open room flow observed":started?"open flow pending":"pending start"):(Math.abs(proof.receiverNow()-proof.receiverBefore())<=1e-6?"closed / destination unchanged":"closed / destination changed"));
            }
            case HEATER, FREEZER -> {
                String prefix="APC "+(machine.apcAllocationKnown()?fmt(machine.apcAllocatedWatts())+" W":"unknown")+"; ";
                var proof=thermalRoomProof(kind,machine,saved,rooms);
                var baseline=demoBaseline(saved,kind);
                double paid=machine.committedPaidJoules()-baseline.getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".paidJ",0d);
                double thermal=machine.committedThermalJoules()-baseline.getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".thermalJ",0d);
                boolean observed=thermalRoomObserved(proof,kind,paid,thermal);
                yield prefix+"room "+fmt(proof.beforeK())+"→"+fmt(proof.nowK())+" K; paid "+fmt(paid)+" J, committed thermal "+fmt(thermal)+" J; "+(observed?"thermal work observed":"thermal work not observed");
            }
            case PASSIVE_EXCHANGER -> { var port=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("inlet")).findFirst().orElseGet(()->machine.gasPorts().stream().findFirst().orElse(null)); var m=port==null?null:gas.get(port.id().toUpperCase(Locale.ROOT));
                var baseline=demoBaseline(saved,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
                Double beforeT=port==null?null:baseline.get("port.passive_exchanger."+port.id()+".temperatureK");
                Double beforeE=port==null?null:baseline.get("port.passive_exchanger."+port.id()+".thermalJ");
                 var proof=exchangerRoomProof(saved,rooms,machine);
                 double roomT=proof.nowK(),roomE=proof.nowJ(),roomBeforeT=proof.beforeK(),roomBeforeE=proof.beforeJ();
                 double thermalReceipt=machine.committedThermalJoules()-baseline.getOrDefault("work.passive_exchanger.thermalJ",0d);
                 double pipeBefore=beforeE==null?Double.NaN:beforeE,pipeNow=m==null?Double.NaN:m.thermalEnergy();
                 boolean opposite=exchangerObserved(proof,pipeBefore,pipeNow,thermalReceipt);
                yield "pipe "+(m==null?"unknown":fmt(beforeT==null?m.temperatureKelvin():beforeT)+"→"+fmt(m.temperatureKelvin())+" K; "+fmt(beforeE==null?m.thermalEnergy():beforeE)+"→"+fmt(m.thermalEnergy())+" J")
                         +"; room "+fmt(roomBeforeT)+"→"+fmt(roomT)+" K, "+fmt(roomBeforeE)+"→"+fmt(roomE)+" J; committed heat-to-pipe "+fmt(thermalReceipt)+" J; "+(opposite?"opposite heat transfer observed":started?"heat transfer not observed":"pending start"); }
            case MANIFOLD -> "six ports report shared mixture; see layer composition below";
        };
    }

    private static String fmt(double n) { return Double.isFinite(n)?String.format(Locale.ROOT,"%.2f",n):"unknown"; }

    /** Shared status/native acceptance projection; a pre-seeded but unchanged receiver is not a transfer. */
    public static boolean pumpTransferObserved(double sourceBefore,double sourceNow,double receiverBefore,double receiverNow,
                                               double sourcePressureBefore,double sourcePressureNow,
                                               double receiverPressureBefore,double receiverPressureNow,
                                               double committedMoles,double paidInputJoules,boolean allocationKnown) {
        return allocationKnown&&Double.isFinite(committedMoles)&&committedMoles>0&&Double.isFinite(paidInputJoules)&&paidInputJoules>0
                &&Double.isFinite(sourceBefore)&&Double.isFinite(sourceNow)&&sourceNow<sourceBefore-1e-6
                &&Double.isFinite(receiverBefore)&&Double.isFinite(receiverNow)&&receiverNow>receiverBefore+1e-6
                &&Double.isFinite(sourcePressureBefore)&&Double.isFinite(sourcePressureNow)&&sourcePressureNow<sourcePressureBefore-1e-6
                &&Double.isFinite(receiverPressureBefore)&&Double.isFinite(receiverPressureNow)&&receiverPressureNow>receiverPressureBefore+1e-6;
    }
    private static void appendMinerReceipts(MutableComponent out,ServerLevel level,BlockPos origin,AtmosMachineryFixtureSavedData.Entry saved) {
        out.append("\nMiner production receipts:");
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            if(level.getBlockEntity(room.machine()) instanceof GasMinerBlockEntity miner) {
                String key="miner."+miner.resolvedProfile().gas().name().toLowerCase(Locale.ROOT)+"."+room.machine().getX()+"."+room.machine().getZ()+".producedMoles";
                Double before=saved.baseline().get(key); double now=miner.producedMoles();
                out.append("\n  ").append(String.valueOf(miner.resolvedProfile().gas())).append(" ").append(before==null?"baseline unavailable":fmt(before)+"→"+fmt(now)+" mol; "+(now>before?"production observed":"no production observed yet"));
            }
        }
    }
    private static void appendCrossingAndManifold(MutableComponent out,ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,PipeGasInventory inventory) {
        out.append("\nManifold (one common 300 L mixture; 3 fixed lanes join):");
        BlockPos host=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MANIFOLD));
        if(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity manifold) {
            if(inventory!=null)for(var port:manifold.gasPorts()) { var e=inventory.get(port.key(host)).orElse(null); if(e!=null) {var share=inventory.nodeShare(e.node()); out.append("\n  ").append(String.valueOf(port.layer())).append(" lane ").append(port.id()).append(" shared mix: Plasma ").append(fmt(share.moles(GasType.PLASMA))).append(" / N₂ ").append(fmt(share.moles(GasType.NITROGEN))).append(" mol");} }
            var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
            var receipt=ManifoldFixtureObservation.observe(manifold,host,inventory,saved);
            out.append("\n  Topology: one common mixture across all six ports; measured transfer: ").append(receipt.reason());
            for(var port:receipt.ports())out.append("\n  ").append(port.lane()).append(" ").append(port.id()).append(" n ").append(fmt(port.beforeMoles())).append("→").append(fmt(port.nowMoles())).append("; PL ").append(fmt(port.beforePlasma())).append("→").append(fmt(port.nowPlasma())).append("; N₂ ").append(fmt(port.beforeNitrogen())).append("→").append(fmt(port.nowNitrogen())).append(" mol; kPa ").append(fmt(port.beforePressure())).append("→").append(fmt(port.nowPressure()));
        }
        var crossing=LoadedPipeGasOwner.activeSnapshot(level,origin.offset(crossingCenter()),VisualPipeBlockEntity.PRIMARY).orElse(null);
        var second=LoadedPipeGasOwner.activeSnapshot(level,origin.offset(crossingCenter()),VisualPipeBlockEntity.SECONDARY).orElse(null);
        var crossingGas=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        var crossingMix=crossing==null||crossingGas==null?null:crossingGas.nodeShare(crossing.node());
        var secondMix=second==null||crossingGas==null?null:crossingGas.nodeShare(second.node());
        out.append("\n  ordinary crossing ").append(crossingMix==null||secondMix==null?"unavailable":crossingMix.moles(GasType.OXYGEN)>0&&secondMix.moles(GasType.NITROGEN)>0&&crossingMix.moles(GasType.NITROGEN)<=1e-9&&secondMix.moles(GasType.OXYGEN)<=1e-9?"isolated O₂/N₂ lanes":"check lane contents");
    }

    static void refreshProofSigns(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,AtmosMachineryFixtureSavedData.Entry saved,Map<String,Double> measurements) {
        record Proof(Component title,String status,String first,String second) {}
        var proofs=new ArrayList<Proof>();
        var inv=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        for(var p:plan.placements()) {
            BlockPos host=origin.offset(p.position()); if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine))continue;
            var readings=new LinkedHashMap<String,GasMixture>();
            if(inv!=null)for(var port:machine.gasPorts()){var e=inv.get(port.key(host)).orElse(null);if(e!=null)readings.put(port.id().toUpperCase(Locale.ROOT),inv.nodeShare(e.node()));}
            var view=deviceProofView(p.kind(),machine,readings,saved,host,measurements);
            proofs.add(new Proof(machineName(level,host,p.kind()),view.status(),view.first(),view.second()));
        }
        BlockPos ventOrigin=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var rooms=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureTelemetry.roomTotals(level,ventOrigin,true).orElse(null);
        var vp=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        var scrub=level.getBlockEntity(ventOrigin.offset(vp.scrubber()));
        var scrubberTitle=level.getBlockState(ventOrigin.offset(vp.scrubber())).getBlock().getName();
        double captured=Double.NaN,sourceDelta=Double.NaN,sourceNow=Double.NaN;
        Double sourceBefore=saved.baseline().get("vent.source."+vp.testGas().name().toLowerCase(Locale.ROOT)+".moles");
        if(scrub instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity s) {
            captured=s.transferObservation().speciesMoles().getOrDefault(vp.testGas(),0.0);
        }
        sourceNow=rooms==null?Double.NaN:rooms.moles(vp.testGas());sourceDelta=sourceBefore==null?Double.NaN:sourceNow-sourceBefore;
        String scrubStatus=sourceBefore==null||!Double.isFinite(sourceNow)?"UNKNOWN":!saved.laboratoryStarted()?"PENDING":captured>1e-6&&sourceDelta< -1e-6?"CAPTURED":"NO CAPTURE";
        setProofSignIfPresent(level,ventOrigin.offset(4,1,-1),AtmosMachineryFixtureProofSignFormatter.scrubber(scrubberTitle,scrubStatus,vp.testGasLabel(),captured,sourceDelta));
        var receiver=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureTelemetry.roomTotals(level,ventOrigin,false).orElse(null);
        Double receiverBefore=saved.baseline().get("vent.receiver."+vp.testGas().name().toLowerCase(Locale.ROOT)+".moles");
        var ventBe=level.getBlockEntity(ventOrigin.offset(vp.vent()));
        String ventPower=ventBe instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v
                ?ventPowerText(com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ventilationMeter(level,v)):"power unknown";
        double receiverNow=receiver==null?Double.NaN:receiver.moles(vp.testGas());double receiverDelta=receiverBefore==null?Double.NaN:receiverNow-receiverBefore;
        double ventMoved=ventBe instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v
                ?v.transferObservation().speciesMoles().getOrDefault(vp.testGas(),0.0):0.0;
        boolean transferred=ventMoved>1e-6&&receiverBefore!=null&&Double.isFinite(receiverNow)&&receiverDelta>1e-6&&Double.isFinite(sourceDelta)&&sourceDelta< -1e-6;
        String ventStatus=receiverBefore==null||!Double.isFinite(receiverNow)?"UNKNOWN":!saved.laboratoryStarted()?"PENDING":transferred?"TRANSFERRED":"NO TRANSFER";
        var ventEntity=level.getBlockEntity(ventOrigin.offset(vp.vent()));Component ventTitle=ventEntity==null?Component.literal("Vent receiver"):ventEntity.getBlockState().getBlock().getName();
        setProofSignIfPresent(level,ventOrigin.offset(12,1,-1),AtmosMachineryFixtureProofSignFormatter.vent(ventTitle,ventStatus,ventMoved,receiverDelta,receiverNow));
        for(int i=0;i<Math.min(proofs.size(),plan.labels().size()+2);i++) {
            BlockPos signPos=i<plan.labels().size()?origin.offset(plan.labels().get(i).position()):i==plan.labels().size()?ventOrigin.offset(4,1,-1):ventOrigin.offset(12,1,-1);
            if(!(level.getBlockEntity(signPos) instanceof SignBlockEntity sign))continue;
            Proof proof=proofs.get(i);
            Component[] lines={proof.title(),Component.literal(proof.status()),Component.literal(proof.first()),Component.literal(proof.second())};
            setSignLinesIfChanged(sign,lines);
        }
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            if(!(level.getBlockEntity(room.machine()) instanceof GasMinerBlockEntity miner)||!(level.getBlockEntity(room.labelPosition()) instanceof SignBlockEntity sign))continue;
            var profile=miner.resolvedProfile();String gas=profile==null?"Gas":String.valueOf(profile.gas());
            String key="miner."+(profile==null?"unknown":profile.gas().name().toLowerCase(Locale.ROOT))+"."+room.machine().getX()+"."+room.machine().getZ()+".producedMoles";
            Double before=saved.baseline().get(key);double made=before==null?Double.NaN:miner.producedMoles()-before;
            var lines=AtmosMachineryFixtureProofSignFormatter.miner(machineName(level,room.machine(),AtmosMachineryFixturePlan.Kind.MANIFOLD),Double.isFinite(made)&&made>0,made,profile==null?Double.NaN:profile.maxExternalPressureKpa());
            setProofSign(sign,lines);
        }
        var circuits=AtmosMachineryRoomCircuitPlan.standard(plan,origin);
        for(var endpoint:circuits.endpoints()) {
            String key=roomMeasurementKey(endpoint);
            Double total=measurements.get(key+".moles"),plasma=measurements.get(key+".plasmaMoles"),nitrogen=measurements.get(key+".nitrogenMoles"),temp=measurements.get(key+".temperatureK");
            if(total==null||temp==null)continue;
            var tracer=roomTracer(endpoint);
            double amount=measurements.getOrDefault(roomSpeciesMeasurementKey(key,tracer),0d);
            String gas=AtmosMachineryFixtureProofSignFormatter.operatorGasLabel(tracer);
            double delta=amount-saved.baseline().getOrDefault(roomSpeciesMeasurementKey(key,tracer),amount);
            double dt=temp-saved.baseline().getOrDefault(key+".temperatureK",temp);
            String stationTitle=switch(endpoint.role()) {
                case SOURCE -> switch(endpoint.station()) { case FILTER -> "Filter SRC"; case MIXER -> "Mixer SRC"; case PASSIVE_EXCHANGER -> "HX SRC"; default -> "Press pump SRC"; };
                case RECEIVER -> switch(endpoint.station()) { case FILTER -> "Filter OUT"; case PASSIVE_EXCHANGER -> "HX OUT"; default -> "Press pump ROOM"; };
                case FILTERED -> "Filter SELECT"; case EXHAUST -> "Filter OUT";
                case THERMAL_ROOM -> endpoint.station()==AtmosMachineryFixturePlan.Kind.HEATER?"Heater ROOM":"Freezer ROOM";
                case MANIFOLD_IN -> "Mixer IN"; case MANIFOLD_OUT -> "Mixer OUT"; case EXCHANGER_ROOM -> "HX ROOM";
            };
            if(endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM) {
                var baseline=demoBaseline(saved,endpoint.station());
                double before=baseline.getOrDefault(key+".temperatureK",Double.NaN),temperatureDelta=temp-before;
                BlockPos machinePos=endpoint.machine();
                var machine=level.getBlockEntity(machinePos) instanceof AtmosMachineBlockEntity m?m:null;
                String workKey="work."+endpoint.station().name().toLowerCase(Locale.ROOT)+".thermalJ";
                double thermal=machine==null?Double.NaN:machine.committedThermalJoules()-baseline.getOrDefault(workKey,0d);
                boolean started=saved.reservedDemos().contains(endpoint.station().name().toLowerCase(Locale.ROOT));
                String paidKey="work."+endpoint.station().name().toLowerCase(Locale.ROOT)+".paidJ";
                double paid=machine==null?Double.NaN:machine.committedPaidJoules()-baseline.getOrDefault(paidKey,0d);
                boolean observed=endpoint.station()==AtmosMachineryFixturePlan.Kind.HEATER
                        ?AtmosMachineryThermalGauge.warming(before,temp,paid,thermal)
                        :AtmosMachineryThermalGauge.cooling(before,temp,paid,thermal);
                String status=!started?"WAITING · START DEMO":baseline.isEmpty()?"START RESERVED · NO BASELINE":observed
                        ?(endpoint.station()==AtmosMachineryFixturePlan.Kind.HEATER?"MEASURED WARMING":"MEASURED COOLING"):"NO MEASURED TRANSFER";
                setThermalIndicator(level,thermalIndicatorPosition(endpoint),observed);
                setProofSignIfPresent(level,endpoint.room().labelPosition(),AtmosMachineryFixtureProofSignFormatter.thermalGauge(
                        Component.literal(stationTitle),status,before,temp,temperatureDelta));
                continue;
            }
            if(endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM) {
                var baseline=demoBaseline(saved,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
                BlockPos machinePos=endpoint.machine();
                var machine=level.getBlockEntity(machinePos) instanceof AtmosMachineBlockEntity m?m:null;
                var hxPort=machine==null?null:machine.gasPorts().stream().findFirst().orElse(null);
                String pipeKey=hxPort==null?"":"port.passive_exchanger."+hxPort.id()+".thermalJ";
                double pipeBefore=baseline.getOrDefault(pipeKey,Double.NaN);
                var pipeEntry=inv==null||hxPort==null?null:inv.get(hxPort.key(machinePos)).orElse(null);
                double pipeNow=pipeEntry==null?Double.NaN:inv.nodeShare(pipeEntry.node()).thermalEnergy();
                double roomBefore=baseline.getOrDefault(key+".thermalJ",Double.NaN),roomNow=measurements.getOrDefault(key+".thermalJ",Double.NaN);
                double work=machine==null?Double.NaN:machine.committedThermalJoules()-baseline.getOrDefault("work.passive_exchanger.thermalJ",0d);
                boolean started=saved.reservedDemos().contains(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER.name().toLowerCase(Locale.ROOT));
                boolean observed=AtmosMachineryThermalGauge.oppositeExchange(roomBefore,roomNow,pipeBefore,pipeNow,work);
                String status=!started?"WAITING · START HX":baseline.isEmpty()?"START RESERVED · NO BASELINE":observed
                        ?(pipeNow>pipeBefore?"MEASURED ROOM → PIPE":"MEASURED PIPE → ROOM"):"NO MEASURED EXCHANGE";
                setThermalIndicator(level,thermalIndicatorPosition(endpoint),observed);
                setProofSignIfPresent(level,endpoint.room().labelPosition(),AtmosMachineryFixtureProofSignFormatter.exchangerGauge(
                        Component.literal(stationTitle),status,roomNow-roomBefore,pipeNow-pipeBefore));
                continue;
            }
            if(tracer==GasType.WATER_VAPOR) {
                setRoomProofSignIfPresent(level,endpoint.room().labelPosition(),AtmosMachineryFixtureProofSignFormatter.localizedRoom(
                        Component.literal(stationTitle),endpoint.role().name(),tracer,amount,delta,temp,dt));
            } else {
                var lines=AtmosMachineryFixtureProofSignFormatter.room(Component.literal(stationTitle),endpoint.role().name(),gas,amount,delta,temp,dt);
                setProofSignIfPresent(level,endpoint.room().labelPosition(),lines);
            }
        }
    }

    private static BlockPos thermalIndicatorPosition(AtmosMachineryRoomCircuitPlan.Endpoint endpoint){return endpoint.room().labelPosition().above();}
    private static void setThermalIndicator(ServerLevel level,BlockPos position,boolean lit) {
        if(level.getBlockState(position).is(Blocks.REDSTONE_LAMP)) {
            var state=level.getBlockState(position).setValue(RedstoneLampBlock.LIT,lit);
            if(!state.equals(level.getBlockState(position)))level.setBlock(position,state,3);
        }
    }

    /** Canonical key shared by baseline capture, status, and physical fixture acceptance. */
    public static String roomMeasurementKey(AtmosMachineryRoomCircuitPlan.Endpoint endpoint) {
        String port=endpoint.ports().stream().map(Enum::name).sorted().reduce((a,b)->a+"-"+b).orElse("room").toLowerCase(Locale.ROOT);
        return "room."+endpoint.station().name().toLowerCase(Locale.ROOT)+"."+endpoint.role().name().toLowerCase(Locale.ROOT)+"."+port;
    }

    public static String roomSpeciesMeasurementKey(String roomKey,GasType gas){return roomKey+"."+gas.id()+"Moles";}

    public static String portSpeciesMeasurementKey(AtmosMachineryFixturePlan.Kind station,String portId,GasType gas) {
        return "port."+station.name().toLowerCase(Locale.ROOT)+"."+portId+".gas."+gas.id()+"Moles";
    }

    /** Stable visible tracer for room receipts, independent of current/initial chamber contents. */
    public static GasType roomTracer(AtmosMachineryRoomCircuitPlan.Endpoint endpoint) {
        if (endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.EXHAUST
                && endpoint.station()==AtmosMachineryFixturePlan.Kind.FILTER) return GasType.WATER_VAPOR;
        if (endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.SOURCE
                && endpoint.station()==AtmosMachineryFixturePlan.Kind.FILTER) return GasType.WATER_VAPOR;
        if (endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.SOURCE
                && endpoint.station()==AtmosMachineryFixturePlan.Kind.MIXER
                && endpoint.ports().contains(AtmosMachineProfile.PortName.FILTER)) return GasType.WATER_VAPOR;
        return GasType.PLASMA;
    }

    private static void setProofSignIfPresent(ServerLevel level,BlockPos pos,AtmosMachineryFixtureProofSignFormatter.Lines lines) {
        if(level.getBlockEntity(pos) instanceof SignBlockEntity sign)setProofSign(sign,lines);
    }

    private static void setRoomProofSignIfPresent(ServerLevel level,BlockPos pos,AtmosMachineryFixtureProofSignFormatter.RoomComponents lines) {
        if(!(level.getBlockEntity(pos) instanceof SignBlockEntity sign))return;
        Component[] values={lines.title(),lines.status(),lines.first(),lines.second()};
        setSignLinesIfChanged(sign,values);
    }

    private static void setProofSign(SignBlockEntity sign,AtmosMachineryFixtureProofSignFormatter.Lines lines) {
        Component[] values={lines.title(),Component.literal(lines.status()),Component.literal(lines.first()),Component.literal(lines.second())};
        setSignLinesIfChanged(sign,values);
    }

    private static void setSignLinesIfChanged(SignBlockEntity sign,Component[] values) {
        var current=sign.getFrontText();var next=current;boolean changed=false;
        for(int i=0;i<4;i++)if(!current.getMessage(i,false).equals(values[i])) {
            next=next.setMessage(i,values[i]);changed=true;
        }
        if(changed){sign.setText(next,true);sign.setChanged();}
    }

    private record DeviceProofView(String status,String first,String second) {}
    public record ThermalRoomProof(double beforeK,double nowK) {}
    public record ExchangerRoomProof(double beforeK,double nowK,double beforeJ,double nowJ) {}
    private static Map<String,Double> demoBaseline(AtmosMachineryFixtureSavedData.Entry saved,AtmosMachineryFixturePlan.Kind kind) {
        return saved.demoBaselines().getOrDefault(kind.name().toLowerCase(Locale.ROOT),Map.of());
    }
    private static ExchangerRoomProof exchangerRoomProof(AtmosMachineryFixtureSavedData.Entry saved,Map<String,Double> now,AtmosMachineBlockEntity machine) {
        String key=roomMeasurementKey(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER,
                AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM,List.of());
        var baseline=demoBaseline(saved,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
        return new ExchangerRoomProof(baseline.getOrDefault(key+".temperatureK",Double.NaN),now.getOrDefault(key+".temperatureK",Double.NaN),
                baseline.getOrDefault(key+".thermalJ",Double.NaN),now.getOrDefault(key+".thermalJ",Double.NaN));
    }
    public static boolean exchangerObserved(ExchangerRoomProof proof,double pipeBeforeJ,double pipeNowJ,double committedPipeHeat) {
        return proof!=null&&AtmosMachineryThermalGauge.oppositeExchange(proof.beforeJ(),proof.nowJ(),pipeBeforeJ,pipeNowJ,committedPipeHeat);
    }
    public record ValveRoomProof(double sourceBefore,double sourceNow,double receiverBefore,double receiverNow) {}
    private static ValveRoomProof valveRoomProof(AtmosMachineryFixtureSavedData.Entry saved,Map<String,Double> now) {
        String sk=roomMeasurementKey(AtmosMachineryFixturePlan.Kind.VALVE,AtmosMachineryRoomCircuitPlan.Role.SOURCE,List.of(AtmosMachineProfile.PortName.INLET));
        String rk=roomMeasurementKey(AtmosMachineryFixturePlan.Kind.VALVE,AtmosMachineryRoomCircuitPlan.Role.RECEIVER,List.of(AtmosMachineProfile.PortName.OUTLET));
        return new ValveRoomProof(saved.baseline().getOrDefault(sk+".plasmaMoles",Double.NaN),now.getOrDefault(sk+".plasmaMoles",Double.NaN),saved.baseline().getOrDefault(rk+".plasmaMoles",Double.NaN),now.getOrDefault(rk+".plasmaMoles",Double.NaN));
    }
    public static boolean valveRoomFlowObserved(boolean open,double sourceBefore,double sourceNow,double destinationBefore,double destinationNow) {
        return open&&Double.isFinite(sourceBefore)&&Double.isFinite(sourceNow)&&sourceNow<sourceBefore-1e-6
                &&Double.isFinite(destinationBefore)&&Double.isFinite(destinationNow)&&destinationNow>destinationBefore+1e-6;
    }
    private static ThermalRoomProof thermalRoomProof(AtmosMachineryFixturePlan.Kind kind,AtmosMachineBlockEntity machine,AtmosMachineryFixtureSavedData.Entry saved,Map<String,Double> now) {
        var port=machine.gasPorts().stream().findFirst().orElse(null);
        if(port==null)return new ThermalRoomProof(Double.NaN,Double.NaN);
        String key=roomMeasurementKey(kind,AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM,List.of(AtmosMachineProfile.PortName.valueOf(port.id().toUpperCase(Locale.ROOT))));
        var baseline=demoBaseline(saved,kind);
        return new ThermalRoomProof(baseline.getOrDefault(key+".temperatureK",Double.NaN),now.getOrDefault(key+".temperatureK",Double.NaN));
    }
    public static boolean thermalRoomObserved(ThermalRoomProof proof,AtmosMachineryFixturePlan.Kind kind,double paidJ,double thermalJ) {
        if(proof==null||!Double.isFinite(proof.beforeK())||!Double.isFinite(proof.nowK())||!Double.isFinite(paidJ)||paidJ<=0)return false;
        return kind==AtmosMachineryFixturePlan.Kind.HEATER?AtmosMachineryThermalGauge.warming(proof.beforeK(),proof.nowK(),paidJ,thermalJ)
                :kind==AtmosMachineryFixturePlan.Kind.FREEZER&&AtmosMachineryThermalGauge.cooling(proof.beforeK(),proof.nowK(),paidJ,thermalJ);
    }
    public record PumpRoomReadings(double sourceBefore,double sourceNow,double receiverBefore,double receiverNow,
                                    double sourcePressureBefore,double sourcePressureNow,double receiverPressureBefore,double receiverPressureNow) {}
    private static PumpRoomReadings pumpRoomReadings(AtmosMachineryFixturePlan.Kind kind,AtmosMachineryFixtureSavedData.Entry saved,Map<String,Double> now) {
        String sk=roomMeasurementKey(kind,AtmosMachineryRoomCircuitPlan.Role.SOURCE,List.of(AtmosMachineProfile.PortName.INLET));
        String rk=roomMeasurementKey(kind,AtmosMachineryRoomCircuitPlan.Role.RECEIVER,List.of(AtmosMachineProfile.PortName.OUTLET));
        return new PumpRoomReadings(saved.baseline().getOrDefault(sk+".moles",Double.NaN),now.getOrDefault(sk+".moles",Double.NaN),
                saved.baseline().getOrDefault(rk+".moles",Double.NaN),now.getOrDefault(rk+".moles",Double.NaN),
                saved.baseline().getOrDefault(sk+".averagePressureKpa",Double.NaN),now.getOrDefault(sk+".averagePressureKpa",Double.NaN),
                saved.baseline().getOrDefault(rk+".averagePressureKpa",Double.NaN),now.getOrDefault(rk+".averagePressureKpa",Double.NaN));
    }
    public static boolean pumpTransferObserved(PumpRoomReadings r,double moved,double paid,boolean allocationKnown) {
        return r!=null&&pumpTransferObserved(r.sourceBefore(),r.sourceNow(),r.receiverBefore(),r.receiverNow(),r.sourcePressureBefore(),r.sourcePressureNow(),
                r.receiverPressureBefore(),r.receiverPressureNow(),moved,paid,allocationKnown);
    }
    private static boolean pumpTargetOrRate(AtmosMachineryFixturePlan.Kind kind,AtmosMachineBlockEntity machine,GasMixture out) {
        var settings=machine.settingsSnapshot();
        return kind==AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP?settings.targetPressureKpa().isPresent()&&out!=null&&out.pressureKpa(out.volumeCubicMeters())<=settings.targetPressureKpa().get()+0.01
                :settings.transferRateM3PerSecond().isPresent()&&settings.transferRateM3PerSecond().get()>0;
    }
    public static String roomMeasurementKey(AtmosMachineryFixturePlan.Kind station,AtmosMachineryRoomCircuitPlan.Role role,List<AtmosMachineProfile.PortName> ports) {
        String port=ports.stream().map(Enum::name).sorted().reduce((a,b)->a+"-"+b).orElse("room").toLowerCase(Locale.ROOT);
        return "room."+station.name().toLowerCase(Locale.ROOT)+"."+role.name().toLowerCase(Locale.ROOT)+"."+port;
    }
    /** Compact physical-sign projection; full deviceOutcome remains the detailed chat receipt. */
    private static DeviceProofView deviceProofView(AtmosMachineryFixturePlan.Kind kind,AtmosMachineBlockEntity machine,Map<String,GasMixture> gas,AtmosMachineryFixtureSavedData.Entry saved,BlockPos pos,Map<String,Double> rooms) {
        String demo=kind.name().toLowerCase(Locale.ROOT);
        if(!saved.reservedDemos().contains(demo))return new DeviceProofView("WAITING","Run start "+(kind==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER?"heat_exchanger":demo),"no finite stock authored");
        if(!saved.demoBaselines().containsKey(demo))return new DeviceProofView("START RESERVED","setup incomplete; no retry/refill","manual rebuild required");
        return switch(kind) {
            case PRESSURE_PUMP,VOLUME_PUMP -> {
                var in=gas.get("INLET"); var out=gas.get("OUTLET");
                var inlet=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("inlet")).findFirst().orElse(null);
                var outlet=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("outlet")).findFirst().orElse(null);
                double nIn=machine.committedMovedMoles()-saved.baseline().getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".movedMoles",0d);
                double paid=machine.committedPaidJoules()-saved.baseline().getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".paidJ",0d);
                var readings=pumpRoomReadings(kind,saved,rooms);
                boolean observed=pumpTransferObserved(readings,nIn,paid,machine.apcAllocationKnown())&&pumpTargetOrRate(kind,machine,out);
                var lines=AtmosMachineryFixtureProofSignFormatter.pump(machineName((ServerLevel)machine.getLevel(),pos,kind),observed,readings.sourceNow(),readings.receiverNow());
                yield new DeviceProofView(lines.status(),lines.first(),lines.second());
            }
            case HEATER,FREEZER -> {
                var proof=thermalRoomProof(kind,machine,saved,rooms);
                var baseline=demoBaseline(saved,kind);
                double paid=machine.committedPaidJoules()-baseline.getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".paidJ",0d);
                double thermal=machine.committedThermalJoules()-baseline.getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".thermalJ",0d);
                boolean heating=kind==AtmosMachineryFixturePlan.Kind.HEATER;
                boolean observed=thermalRoomObserved(proof,kind,paid,thermal);
                var lines=AtmosMachineryFixtureProofSignFormatter.thermal(machineName((ServerLevel)machine.getLevel(),pos,kind),observed,heating,proof.beforeK(),proof.nowK(),paid);
                yield new DeviceProofView(lines.status(),lines.first(),lines.second());
            }
            case MIXER -> {
                var m=gas.get("OUTLET"); double o=m==null?Double.NaN:m.moles(GasType.PLASMA),n=m==null?Double.NaN:m.moles(GasType.WATER_VAPOR),total=o+n;
                double work=machine.committedMovedMoles()-saved.baseline().getOrDefault("work.mixer.movedMoles",0d);
                var ratio=machine.settingsSnapshot().mixerFractionA(); boolean observed=Double.isFinite(work)&&work>0&&total>0&&ratio.isPresent()&&Math.abs(o/total-ratio.get())<=0.05;
                var l=AtmosMachineryFixtureProofSignFormatter.mixer(machineName((ServerLevel)machine.getLevel(),pos,kind),observed,GasType.PLASMA,total>0?100*o/total:Double.NaN,GasType.WATER_VAPOR,total>0?100*n/total:Double.NaN);
                yield new DeviceProofView(l.status(),l.first(),l.second());
            }
            case FILTER -> {
                var selected=gas.get("FILTER"); var ordinary=gas.get("OUTLET"); var species=machine.settingsSnapshot().filterGas().orElse(null);
                if(species==null) { var l=AtmosMachineryFixtureProofSignFormatter.unconfiguredFilter(machineName((ServerLevel)machine.getLevel(),pos,kind)); yield new DeviceProofView(l.status(),l.first(),l.second()); }
                double inSelected=selected==null?Double.NaN:selected.moles(species), contaminant=0, passSelected=ordinary==null?Double.NaN:ordinary.moles(species),passOther=0,passContaminant=0;
                if(selected!=null)for(GasType t:GasType.values())if(t!=species)contaminant+=selected.moles(t);
                if(ordinary!=null)passOther=ordinary.moles(GasType.WATER_VAPOR);
                if(ordinary!=null)for(GasType t:GasType.values())if(t!=species&&t!=GasType.WATER_VAPOR)passContaminant+=ordinary.moles(t);
                double work=machine.committedMovedMoles()-saved.baseline().getOrDefault("work.filter.movedMoles",0d);
                boolean observed=Double.isFinite(work)&&work>0&&inSelected>1e-6&&contaminant<=1e-6&&passSelected<=1e-6&&passOther>1e-6&&passContaminant<=1e-6;
                var l=AtmosMachineryFixtureProofSignFormatter.filter(machineName((ServerLevel)machine.getLevel(),pos,kind),observed,
                        species,inSelected,GasType.WATER_VAPOR,passOther);
                yield new DeviceProofView(l.status(),l.first(),l.second());
            }
            case VALVE -> {
                boolean open=machine.settingsSnapshot().valveOpen().orElse(false);var proof=valveRoomProof(saved,rooms);
                double inDelta=proof.sourceNow()-proof.sourceBefore(),outDelta=proof.receiverNow()-proof.receiverBefore();
                boolean observed=valveRoomFlowObserved(open,proof.sourceBefore(),proof.sourceNow(),proof.receiverBefore(),proof.receiverNow());
                var l=AtmosMachineryFixtureProofSignFormatter.valve(machineName((ServerLevel)machine.getLevel(),pos,kind),open,observed,inDelta,outDelta);
                yield new DeviceProofView(l.status(),l.first(),l.second());
            }
            case PASSIVE_EXCHANGER -> {
                var port=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("inlet")).findFirst().orElseGet(()->machine.gasPorts().stream().findFirst().orElse(null));
                var m=port==null?null:gas.get(port.id().toUpperCase(Locale.ROOT)); String key=port==null?"":"port.passive_exchanger."+port.id();
                   var baseline=demoBaseline(saved,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
                   double pipeBefore=baseline.getOrDefault(key+".thermalJ",Double.NaN);
                   double pipeNow=m==null?Double.NaN:m.thermalEnergy(),pipeDelta=pipeNow-pipeBefore;
                 var proof=exchangerRoomProof(saved,rooms,machine); double roomNow=proof.nowJ(),roomBefore=proof.beforeJ(),roomDelta=roomNow-roomBefore;
                   double thermalReceipt=machine.committedThermalJoules()-baseline.getOrDefault("work.passive_exchanger.thermalJ",0d);
                  boolean roomToPipe=thermalReceipt>0;
                   boolean observed=exchangerObserved(proof,pipeBefore,pipeNow,thermalReceipt);
                 var l=AtmosMachineryFixtureProofSignFormatter.exchanger(machineName((ServerLevel)machine.getLevel(),pos,kind),observed,roomToPipe,pipeDelta,roomDelta);
                yield new DeviceProofView(l.status(),l.first(),l.second());
            }
            case MANIFOLD -> {
                var snapshot=PipeGasInventorySavedData.existing((ServerLevel)machine.getLevel()).map(PipeGasInventorySavedData::snapshot).orElse(null);
                var receipt=ManifoldFixtureObservation.observe(machine,pos,snapshot,saved);
                double plasma=0,n=0;for(var port:machine.gasPorts()){var m=gas.get(port.id().toUpperCase(Locale.ROOT));if(m!=null){plasma+=m.moles(GasType.PLASMA);n+=m.moles(GasType.NITROGEN);}}
                boolean observed=receipt.valid()&&receipt.observed();
                var l=AtmosMachineryFixtureProofSignFormatter.manifold(machineName((ServerLevel)machine.getLevel(),pos,kind),observed,GasType.PLASMA,plasma+n>0?100*plasma/(plasma+n):Double.NaN,GasType.NITROGEN,plasma+n>0?100*n/(plasma+n):Double.NaN);
                yield new DeviceProofView(receipt.valid()?l.status():"UNAVAILABLE",l.first(),l.second());
            }
            default -> new DeviceProofView("READING", "See atmos status", "Unavailable");
        };
    }

    private static void appendScrubberReceipt(MutableComponent out,ServerLevel level,BlockPos origin,AtmosMachineryFixtureSavedData.Entry saved) {
        var plan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        var source=VentilationFixtureTelemetry.roomTotals(level,origin,true).orElse(null);
        var sink=VentilationFixtureTelemetry.roomTotals(level,origin,false).orElse(null);
        var be=level.getBlockEntity(origin.offset(plan.scrubber()));
        out.append("\nScrubber committed capture:");
        if(!(be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity scrubber)||source==null||sink==null) { out.append(" UNKNOWN (device/finite room unavailable)"); return; }
        double captured=scrubber.transferObservation().speciesMoles().getOrDefault(plan.testGas(),0.0);
        Double before=saved.baseline().get("vent.source."+plan.testGas().name().toLowerCase(Locale.ROOT)+".moles");
        Double receiverBefore=saved.baseline().get("vent.receiver."+plan.testGas().name().toLowerCase(Locale.ROOT)+".moles");
        out.append(" ").append(plan.testGasLabel()).append(" captured ").append(fmt(captured)).append(" mol; source room ")
                .append(before==null?"baseline unknown":fmt(before)+"→"+fmt(source.moles(plan.testGas()))+" mol")
                .append("; receiver room ").append(receiverBefore==null?"baseline unknown":fmt(receiverBefore)+"→")
                .append(fmt(sink.moles(plan.testGas()))).append(" mol; ")
                .append(captured>1e-6&&before!=null&&source.moles(plan.testGas())<before-1e-6?"capture and room decrease observed":"no capture result observed yet");
    }
    private static String ventPowerText(com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation m) {
        return m.known()?fmt(m.deliveredWatts())+" W":"power unknown";
    }

    private static Block blockFor(AtmosMachineryFixturePlan.Kind kind) {
        return switch (kind) {
            case PRESSURE_PUMP -> ModBlocks.GAS_PRESSURE_PUMP.get();
            case VOLUME_PUMP -> ModBlocks.GAS_VOLUME_PUMP.get();
            case MIXER -> ModBlocks.GAS_MIXER.get();
            case FILTER -> ModBlocks.GAS_FILTER.get();
            case VALVE -> ModBlocks.GAS_VALVE.get();
            case HEATER -> ModBlocks.GAS_THERMO_HEATER.get();
            case FREEZER -> ModBlocks.GAS_THERMO_FREEZER.get();
            case PASSIVE_EXCHANGER -> ModBlocks.PASSIVE_HEAT_EXCHANGER.get();
            case MANIFOLD -> ModBlocks.GAS_PIPE_MANIFOLD.get();
        };
    }

    private static Component machineName(ServerLevel level, BlockPos pos, AtmosMachineryFixturePlan.Kind kind) {
        return blockFor(kind).getName();
    }

    public static String start(ServerLevel level, BlockPos origin) {
        return "Start refused: choose an explicit demonstration; use startAll for the full lab. No changes made.";
    }

    public static String startAll(ServerLevel level, BlockPos origin) {
        return startSelected(level,origin,EnumSet.allOf(AtmosMachineryFixturePlan.Kind.class),true);
    }

    public static String start(ServerLevel level, BlockPos origin, AtmosMachineryFixturePlan.Kind kind) {
        return startSelected(level,origin,EnumSet.of(kind),false);
    }

    /** Read-only trusted-authoring physical readiness; simulation endpoints remain ownership-strict. */
    static String finiteRoomReadinessFailure(ServerLevel level,BlockPos origin,
            Collection<AtmosMachineryRoomCircuitPlan.Endpoint> circuitEndpoints,boolean includeAuxiliary,boolean includeMiners) {
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        var circuitRooms=new LinkedHashSet<AtmosMachineryLaboratoryRooms.Geometry>();
        circuitEndpoints.forEach(endpoint->circuitRooms.add(endpoint.room()));
        for(var room:circuitRooms) {
            var cells=AtmosMachineryLaboratoryRooms.passableCells(level,room);
            if(!AtmosMachineryLaboratoryRooms.finiteRoomShapeReady(room,cells,true))return "circuit chamber is not fully passable.";
            if(!atmosphere.trustedSetupRoomReady(level,cells,null)) return "circuit chamber is not physically enclosed.";
        }
        if(includeAuxiliary) {
            var room=AtmosMachineryLaboratoryRooms.exchanger(origin);var cells=AtmosMachineryLaboratoryRooms.passableCells(level,room);
            if(!AtmosMachineryLaboratoryRooms.finiteRoomShapeReady(room,cells,false))return "exchanger chamber has no passable finite endpoint.";
            if(!atmosphere.trustedSetupRoomReady(level,cells,null))return "exchanger chamber is not physically enclosed.";
        }
        if(includeMiners)for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            var cells=AtmosMachineryLaboratoryRooms.passableCells(level,room);
            if(!AtmosMachineryLaboratoryRooms.finiteRoomShapeReady(room,cells,false))return "miner chamber has no passable finite endpoint.";
            if(!atmosphere.trustedSetupRoomReady(level,cells,null))return "miner chamber is not physically enclosed.";
        }
        return null;
    }

    private static String startSelected(ServerLevel level, BlockPos origin, Set<AtmosMachineryFixturePlan.Kind> selected, boolean all) {
        var plan = AtmosMachineryFixturePlan.standard(ModAtmosMachines.catalog(level)::get);
        if (!footprintLoaded(level, origin, plan)) return "Start refused: fixture footprint is not fully loaded.";
        var saved = AtmosMachineryFixtureSavedData.existing(level).flatMap(d -> d.entry(origin)).orElse(null);
        if (saved == null) return "Start refused: no saved fixture ownership entry exists for this origin.";
        if(saved.legacyGlobalStart())
            return "Start refused: legacy fixture is already globally started; use a fresh lab to avoid reseeding finite rooms.";
        if(!saved.auxiliaryStartupAvailable())
            return "Start refused: auxiliary laboratory startup is already reserved or complete; no changes made.";
        String demo=all?"all":selected.iterator().next().name().toLowerCase(Locale.ROOT);
        if (!all && saved.reservedDemos().contains(demo)) return "Start refused: "+demo+" was already reserved; no changes made.";
        Set<AtmosMachineryFixturePlan.Kind> pending=all?EnumSet.allOf(AtmosMachineryFixturePlan.Kind.class):EnumSet.copyOf(selected);
        if(all) for(var kind:AtmosMachineryFixturePlan.Kind.values())if(saved.reservedDemos().contains(kind.name().toLowerCase(Locale.ROOT)))pending.remove(kind);
        boolean auxiliaryOnly=all&&pending.isEmpty()&&saved.auxiliaryStartupAvailable();
        if(pending.isEmpty()&&!auxiliaryOnly)return "Start refused: no unstarted demonstrations remain; no changes made.";
        List<AtmosMachineryFixtureSavedData.PortOwner> current = new ArrayList<>();
        for (var placement : plan.placements()) {
            BlockPos pos = origin.offset(placement.position());
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)) return "Start refused: machine missing.";
            for (var port : machine.gasPorts()) current.add(new AtmosMachineryFixtureSavedData.PortOwner(pos, port.id(), port.incarnation()));
        }
        for (BlockPos valvePos : crossingValvePositions()) {
            BlockPos pos = origin.offset(valvePos);
            if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)) return "Start refused: crossing valve missing.";
            for (var port : machine.gasPorts()) current.add(new AtmosMachineryFixtureSavedData.PortOwner(pos, port.id(), port.incarnation()));
        }
        if (!new HashSet<>(saved.expectedPortOwners()).equals(new HashSet<>(current)))
            return "Start refused: saved fixture ownership is no longer current.";
        List<AtmosMachineryFixtureSavedData.MinerOwner> miners = new ArrayList<>();
        for (var room : AtmosMachineryLaboratoryRooms.miners(origin)) {
            if (!(level.getBlockEntity(room.machine()) instanceof GasMinerBlockEntity miner)
                    || miner.resolvedProfile() == null || miner.placementIdentity() == null)
                return "Start refused: miner identity/profile unavailable.";
            miners.add(new AtmosMachineryFixtureSavedData.MinerOwner(room.machine(), miner.placementIdentity()));
        }
        if (!saved.minerOwners().equals(miners)) return "Start refused: saved miner ownership is no longer current.";
        BlockPos ventilationOrigin = origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        if (all) {
            String ventilationFailure = VentilationFixtureService.laboratoryReadinessFailure(level, ventilationOrigin);
            if (ventilationFailure != null) return "Start refused: " + ventilationFailure;
        }
        var circuits = AtmosMachineryRoomCircuitPlan.standard(plan, origin);
        var chosenEndpoints=circuits.endpoints().stream().filter(e->pending.contains(e.station())&&e.enabledAtStart()).toList();
        var expectedRoomPositions = chosenEndpoints.stream().flatMap(e -> e.ventHosts().stream()).toList();
        var allExpectedRoomPositions=circuits.endpoints().stream().flatMap(e->e.ventHosts().stream()).toList();
        if (saved.roomDeviceOwners().size() != allExpectedRoomPositions.size()
                || !new HashSet<>(saved.roomDeviceOwners().stream().map(AtmosMachineryFixtureSavedData.RoomDeviceOwner::position).toList()).equals(new HashSet<>(allExpectedRoomPositions)))
            return "Start refused: saved room ventilation device identities are incomplete or no longer match planned positions.";
        for (var owner : saved.roomDeviceOwners().stream().filter(o->expectedRoomPositions.contains(o.position())).toList()) {
            if (!(level.getBlockEntity(owner.position()) instanceof VentilationDeviceBlockEntity device)
                    || !owner.deviceId().equals(device.deviceId()) || !device.config().settingsValid()
                    || device.config().settings().selectedLayer() != VentilationDeviceConfig.Layer.PRIMARY
                    || (device.config().kind() == VentilationDeviceConfig.Kind.SCRUBBER
                    && device.config().settings().scrubberMode() != VentilationDeviceConfig.ScrubberMode.SIPHON))
                return "Start refused: room ventilation device ownership or settings are no longer current.";
        }
        String roomReadiness=finiteRoomReadinessFailure(level,origin,chosenEndpoints,all,all);
        if(roomReadiness!=null)return "Start refused: "+roomReadiness;
        var circuitRooms = new LinkedHashMap<AtmosMachineryLaboratoryRooms.Geometry, AtmosMachineryRoomCircuitPlan.Inventory>();
        for (var endpoint : chosenEndpoints) circuitRooms.putIfAbsent(endpoint.room(), endpoint.initialInventory());
        var atmosphere = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        var exchanger = (all?plan.placements().stream()
                .filter(p -> p.kind() == AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER).findFirst()
                : plan.placements().stream().filter(p->pending.contains(p.kind())).findFirst()).orElse(null);
        if (exchanger == null) return "Start refused: selected machine profile unavailable.";
        BlockPos exchangerPos = origin.offset(exchanger.position());
        if (!(level.getBlockEntity(exchangerPos) instanceof AtmosMachineBlockEntity exchangerMachine))
            return "Start refused: passive exchanger missing.";
        // Reserve the one-shot start before the first finite-gas mutation. Any later
        // partial failure is terminal and cannot be retried as a refill operation.
        var guard = AtmosMachineryFixtureSavedData.forMutation(level);
        if(all) {
            if (!guard.markLaboratoryStarted(origin, current, miners, Map.of()))
                return "Start refused: laboratory startup guard changed; no gas write performed.";
            for(var kind:pending) if(!guard.reserveDemo(origin,kind.name().toLowerCase(Locale.ROOT),current))
                return "Start refused: demonstration reservation changed; no gas write performed.";
        } else if(!guard.reserveDemo(origin,demo,current)) return "Start refused: demonstration reservation changed; no gas write performed.";
        for (var entry : circuitRooms.entrySet()) {
            var cells = AtmosMachineryLaboratoryRooms.passableCells(level, entry.getKey());
            var inventory = entry.getValue();
            Map<GasType, Double> perCell = new EnumMap<>(GasType.class);
            inventory.moles().forEach((gas, total) -> perCell.put(gas, total / cells.size()));
            if (!atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level, cells,
                    new GasMixture(perCell, inventory.temperatureK())))
                return "Partial start: circuit room seeding failed; startup guard retained; manual rebuild required.";
        }
        var vacuum = GasMixture.vacuum();
        // The global one-shot startup owns miner initialization in both cases:
        // ordinary `all` and the auxiliary-only completion after nine individual starts.
        // Individual demo starts never initialize miners, and the guard above makes
        // this the sole legitimate initialization opportunity.
        for (var room : all?AtmosMachineryLaboratoryRooms.miners(origin):List.<AtmosMachineryLaboratoryRooms.Geometry>of())
            if (!atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level, AtmosMachineryLaboratoryRooms.passableCells(level, room), vacuum))
                return "Partial start: miner chamber seeding failed; startup guard retained; manual rebuild required.";
        if(all) for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            for(BlockPos cell:AtmosMachineryLaboratoryRooms.passableCells(level,room))
                if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())
                    return "Partial start: miner chamber finite ownership/readback failed; startup guard retained; manual rebuild required.";
            if(!(level.getBlockEntity(room.machine()) instanceof GasMinerBlockEntity miner)
                    ||miner.resolvedProfile()==null||!Double.isFinite(miner.producedMoles()))
                return "Partial start: miner production baseline unavailable; startup guard retained; manual rebuild required.";
        }
        String result = "Selected demonstration initialized.";
        if(all) {
            result = VentilationFixtureService.prepareLaboratoryTransfer(level, ventilationOrigin);
            if (!result.startsWith("Initialized ")) return "Partial start: auxiliary ventilation preparation failed; startup guard retained; manual rebuild required. " + result;
        }
        if(auxiliaryOnly) {
            Map<String,Double> auxiliaryBaseline=laboratoryMeasurements(level,origin).entrySet().stream()
                    .filter(e->e.getKey().startsWith("vent.source.")||e.getKey().startsWith("vent.receiver.")||e.getKey().startsWith("miner."))
                    .collect(LinkedHashMap::new,(m,e)->m.put(e.getKey(),e.getValue()),LinkedHashMap::putAll);
            if(!guard.recordLaboratoryBaseline(origin,current,miners,auxiliaryBaseline))
                return "Partial start: auxiliary baseline capture failed; auxiliary guard retained; manual rebuild required.";
        }
        // Capture the immutable comparison point only after all finite room stock has
        // been authored, and before any powered device or room receiver is enabled.
        Map<String, Double> baseline = new LinkedHashMap<>(laboratoryMeasurements(level, origin));
        var roomBaseline=roomMeasurements(level,origin);
        for(var endpoint:chosenEndpoints) {
            String port=endpoint.ports().stream().map(Enum::name).sorted().reduce((a,b)->a+"-"+b).orElse("room").toLowerCase(Locale.ROOT);
            String key=roomMeasurementKey(endpoint);
            if(!finiteRoomBaselineReady(roomBaseline,key))
                return "Partial start: selected finite circuit room baseline is incomplete; reservation retained; no additional devices enabled; manual rebuild required.";
        }
        if(all&&!auxiliaryOnly) {
            if(!guard.recordLaboratoryBaseline(origin, current, miners, baseline))
                return "Partial start: laboratory baseline capture failed; startup guard retained; manual rebuild required.";
            for(var kind:pending) {
                String station=kind.name().toLowerCase(Locale.ROOT);
                Map<String,Double> scoped=baseline.entrySet().stream().filter(e->e.getKey().startsWith("room."+station+".")
                        ||e.getKey().startsWith("work."+station+".")||e.getKey().startsWith("port."+station+"."))
                        .collect(LinkedHashMap::new,(m,e)->m.put(e.getKey(),e.getValue()),LinkedHashMap::putAll);
                if(!guard.recordDemoBaseline(origin,station,scoped))return "Partial start: per-demo baseline capture failed; reservation retained; manual rebuild required.";
            }
        }
        if(!all) {
            String station=selected.iterator().next().name().toLowerCase(Locale.ROOT);
            Map<String,Double> scoped=baseline.entrySet().stream().filter(e->e.getKey().startsWith("room."+station+".")
                    ||e.getKey().startsWith("work."+station+".")||e.getKey().startsWith("port."+station+"."))
                    .collect(LinkedHashMap::new,(m,e)->m.put(e.getKey(),e.getValue()),LinkedHashMap::putAll);
            if(!guard.recordDemoBaseline(origin,demo,scoped)) return "Partial start: demo baseline capture failed; reservation retained; manual rebuild required.";
        }
        for (var owner : saved.roomDeviceOwners().stream().filter(o->expectedRoomPositions.contains(o.position())).toList()) {
            var device = (VentilationDeviceBlockEntity) level.getBlockEntity(owner.position());
            var old = device.config().settings();
            var enabled = new VentilationDeviceConfig.Settings(old.revision() + 1, true, old.externalPressureBoundKPa(),
                    old.targetPressureChangeKPaPerSecond(), old.targetPressureKPa(), old.volumeRateM3PerSecond(),
                    old.scrubberMode(), old.filteredGases(), old.selectedLayer());
            if (!device.compareAndSetSettings(old.revision(), enabled))
                return "Partial start: room ventilation activation failed; startup guard retained; manual rebuild required.";
        }
        if(all) {
            String activation = VentilationFixtureService.activateLaboratoryTransfer(level, ventilationOrigin);
            if (!activation.startsWith("Activated ")) return "Partial start: auxiliary ventilation activation failed; startup guard retained; manual rebuild required. " + activation;
        }
        for (var placement : plan.placements()) {
            if(!pending.contains(placement.kind()))continue;
            var machine = (AtmosMachineBlockEntity) level.getBlockEntity(origin.offset(placement.position()));
            machine.setControl(placement.kind() == AtmosMachineryFixturePlan.Kind.VALVE ? "open" : "enabled", true);
        }
        return auxiliaryOnly?"Started auxiliary laboratory ventilation and miners for the nine already-started demonstrations at "+origin.toShortString()+". "+result
                :all?"Started remaining atmosphere demonstrations and laboratory ventilation at "+origin.toShortString()+". "+result
                :"Started "+demo.replace('_',' ')+" at "+origin.toShortString()+". "+result;
    }
    private static String formatMoles(Map<GasType, Double> moles) {
        StringJoiner result = new StringJoiner(", ", "{", "}");
        moles.forEach((gas, amount) -> result.add(gas + "=" + String.format(Locale.ROOT, "%.2f", amount)));
        return result.toString();
    }

    /** Captures existing pipe, miner-receipt and finite-room state without creating or reconciling owners. */
    private static Map<String, Double> laboratoryMeasurements(ServerLevel level, BlockPos origin) {
        return roomMeasurements(level, origin, laboratoryMeasurementsWithoutRooms(level, origin));
    }

    /** Checks only the finite inventory fields required by a selected room; unrelated rooms may remain undiscovered. */
    static boolean finiteRoomBaselineReady(Map<String,Double> measurements,String key) {
        for(String field:List.of(".moles",".plasmaMoles",".nitrogenMoles",".temperatureK",".thermalJ")) {
            Double value=measurements.get(key+field);
            if(value==null||!Double.isFinite(value))return false;
        }
        Double water=measurements.get(roomSpeciesMeasurementKey(key,GasType.WATER_VAPOR));
        return water!=null&&Double.isFinite(water);
    }

    private static Map<String, Double> laboratoryMeasurementsWithoutRooms(ServerLevel level, BlockPos origin) {
        Map<String, Double> values = new LinkedHashMap<>();
        var pipes = PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        var plan = AtmosMachineryFixturePlan.standard(ModAtmosMachines.catalog(level)::get);
        for (var placement : plan.placements()) {
            BlockPos host = origin.offset(placement.position());
            if (level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine)
                for (var port : machine.gasPorts()) {
                    var gas = pipes == null ? java.util.Optional.<PipeGasInventory.Entry>empty() : pipes.get(port.key(host));
                    if (gas != null && gas.isPresent()) {
                        var mixture = pipes.nodeShare(gas.get().node());
                String key = "port." + placement.kind().name().toLowerCase(Locale.ROOT) + "." + port.id();
                        addFinite(values, key + ".moles", mixture.totalMoles());
                        addFinite(values, key + ".pressureKpa", mixture.pressureKpa(mixture.volumeCubicMeters()));
                        addFinite(values, key + ".temperatureK", mixture.temperatureKelvin());
                        addFinite(values, key + ".thermalJ", mixture.thermalEnergy());
                        for(GasType species:GasType.values())
                            addFinite(values,portSpeciesMeasurementKey(placement.kind(),port.id(),species),mixture.moles(species));
                    }
                }
            if (level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine) {
                String workKey = "work." + placement.kind().name().toLowerCase(Locale.ROOT);
                addFinite(values, workKey + ".paidJ", machine.committedPaidJoules());
                addFinite(values, workKey + ".movedMoles", machine.committedMovedMoles());
                addFinite(values, workKey + ".thermalJ", machine.committedThermalJoules());
                addFinite(values, workKey + ".events", machine.committedWorkEvents());
            }
        }
        for (var room : AtmosMachineryLaboratoryRooms.miners(origin)) {
            if (level.getBlockEntity(room.machine()) instanceof GasMinerBlockEntity miner && miner.resolvedProfile() != null) {
                String key = "miner." + miner.resolvedProfile().gas().name().toLowerCase(Locale.ROOT)
                        + "." + room.machine().getX() + "." + room.machine().getZ();
                addFinite(values, key + ".producedMoles", miner.producedMoles());
                addFinite(values, key + ".producedThermalJ", miner.producedThermalJoules());
            }
        }
        var atmosphere = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        List<AtmosMachineryLaboratoryRooms.Geometry> rooms = new ArrayList<>();
        rooms.add(AtmosMachineryLaboratoryRooms.exchanger(origin));
        rooms.addAll(AtmosMachineryLaboratoryRooms.miners(origin));
        for (int i = 0; i < rooms.size(); i++) {
            var cells = AtmosMachineryLaboratoryRooms.passableCells(level, rooms.get(i));
            if (cells.isEmpty()) continue;
            var endpoint = atmosphere.finiteRoomEndpointForTrustedSimulation(level, rooms.get(i).machine());
            if (endpoint.isEmpty()) continue;
            var gas = endpoint.get().snapshot();
            if (gas == null) continue;
            String key = "chamber." + (i == 0 ? "exchanger" : "miner" + i);
            addFinite(values, key + ".moles", gas.totalMoles());
            addFinite(values, key + ".temperatureK", gas.temperatureKelvin());
            addFinite(values, key + ".thermalJ", gas.thermalEnergy());
        }
        BlockPos ventilationOrigin=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var ventPlan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        var source=VentilationFixtureTelemetry.roomTotals(level,ventilationOrigin,true).orElse(null);
        var receiver=VentilationFixtureTelemetry.roomTotals(level,ventilationOrigin,false).orElse(null);
        if(source!=null)addFinite(values,"vent.source."+ventPlan.testGas().name().toLowerCase(Locale.ROOT)+".moles",source.moles(ventPlan.testGas()));
        if(receiver!=null)addFinite(values,"vent.receiver."+ventPlan.testGas().name().toLowerCase(Locale.ROOT)+".moles",receiver.moles(ventPlan.testGas()));
        return Map.copyOf(values);
    }

    /** Current physical finite-room readings keyed by stable station/role/port identity. */
    static Map<String, Double> roomMeasurements(ServerLevel level, BlockPos origin) {
        return roomMeasurements(level, origin, Map.of());
    }

    /** Read-only live fixture telemetry. Caller has already applied cadence and nearby-viewer admission. */
    static boolean refreshTelemetryIfLoaded(ServerLevel level,AtmosMachineryFixtureSavedData.Entry entry) {
        if(level==null||entry==null||!level.getServer().isSameThread()||entry.reservedDemos().isEmpty())return false;
        BlockPos origin=entry.origin();
        var plan=AtmosMachineryFixturePlan.standard(ModAtmosMachines.catalog(level)::get);
        if(!fixtureChunksAlreadyLoaded(level,origin))return false;
        refreshProofSigns(level,origin,plan,entry,roomMeasurements(level,origin));
        return true;
    }

    /** Checks only existing FULL chunks; this telemetry path must never request/loading-ticket a fixture chunk. */
    private static boolean fixtureChunksAlreadyLoaded(ServerLevel level,BlockPos origin) {
        int minX=origin.getX()>>4,maxX=(origin.getX()+AtmosMachineryFixturePlan.WIDTH-1)>>4;
        int minZ=origin.getZ()>>4,maxZ=(origin.getZ()+AtmosMachineryFixturePlan.DEPTH-1)>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            if(level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)==null)return false;
        return true;
    }

    private static Map<String, Double> roomMeasurements(ServerLevel level, BlockPos origin, Map<String, Double> existing) {
        var values=new LinkedHashMap<String,Double>(existing);
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        var fixture=AtmosMachineryFixturePlan.standard(ModAtmosMachines.catalog(level)::get);
        var circuits=AtmosMachineryRoomCircuitPlan.standard(fixture,origin);
        var seen=new HashSet<AtmosMachineryLaboratoryRooms.Geometry>();
        int index=0;
        for(var endpoint:circuits.endpoints()) {
            if(!seen.add(endpoint.room()))continue;
            String port=endpoint.ports().stream().map(Enum::name).sorted().reduce((a,b)->a+"-"+b).orElse("room").toLowerCase(Locale.ROOT);
            String key=roomMeasurementKey(endpoint);
            var cells=AtmosMachineryLaboratoryRooms.passableCells(level,endpoint.room());
            GasMixture total=GasMixture.vacuum();
            boolean complete=!cells.isEmpty();
            for(BlockPos cell:cells) {
                var reading=atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElse(null);
                if(reading==null){complete=false;break;}
                total=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureTelemetry.merge(total,reading.snapshot());
            }
            if(complete) {
                addFinite(values,key+".moles",total.totalMoles());
                addFinite(values,key+".plasmaMoles",total.moles(GasType.PLASMA));
                addFinite(values,key+".nitrogenMoles",total.moles(GasType.NITROGEN));
                for(GasType gas:GasType.values())addFinite(values,roomSpeciesMeasurementKey(key,gas),total.moles(gas));
                addFinite(values,key+".temperatureK",total.temperatureKelvin());
                addFinite(values,key+".thermalJ",total.thermalEnergy());
                addFinite(values,key+".averagePressureKpa",VentilationFixtureTelemetry.averagePressureKpa(total,cells.size()));
            }
            index++;
        }
        return Map.copyOf(values);
    }

    private static void addFinite(Map<String, Double> values, String key, double value) {
        if (Double.isFinite(value)) values.put(key, value);
    }

    private static List<AtmosMachineryFixturePlan.PipeSeed> concat(List<AtmosMachineryFixturePlan.PipeSeed> a, List<AtmosMachineryFixturePlan.PipeSeed> b) {
        var result = new ArrayList<>(a); result.addAll(b); return result;
    }
    private static GasMixture inletMixture(AtmosMachineryFixturePlan.Placement p, String id) {
        boolean inlet = p.inletSeeds().stream().anyMatch(s -> s.port().name().equalsIgnoreCase(id));
        if (!inlet) return null;
        Map<GasType, Double> gases = new EnumMap<>(GasType.class);
        if (p.kind() == AtmosMachineryFixturePlan.Kind.FREEZER || p.kind() == AtmosMachineryFixturePlan.Kind.HEATER) gases.put(GasType.NITROGEN, 100.0);
        else if (p.kind() == AtmosMachineryFixturePlan.Kind.MIXER) gases.put(id.equalsIgnoreCase(AtmosMachineProfile.PortName.INLET.name()) ? GasType.OXYGEN : GasType.NITROGEN, 40.0);
        else if (p.kind() == AtmosMachineryFixturePlan.Kind.FILTER) { gases.put(GasType.OXYGEN, 21.0); gases.put(GasType.NITROGEN, 79.0); }
        else { gases.put(GasType.OXYGEN, 21.0); gases.put(GasType.NITROGEN, 79.0); }
        return new GasMixture(gases, p.kind() == AtmosMachineryFixturePlan.Kind.HEATER || p.kind() == AtmosMachineryFixturePlan.Kind.FREEZER ? 300.0 : 293.15);
    }
    private static int layerBit(PipeTopology.Layer layer) {
        return switch (layer) { case PRIMARY -> VisualPipeBlockEntity.PRIMARY; case SECONDARY -> VisualPipeBlockEntity.SECONDARY; case TERTIARY -> VisualPipeBlockEntity.TERTIARY; };
    }
    static List<BlockPos> crossingPipePositions() {
        BlockPos center = crossingCenter();
        return List.of(center, center.offset(0,0,-1), center.offset(0,0,1), center.offset(-1,0,0), center.offset(1,0,0));
    }
    static BlockPos crossingCenter() { return AtmosMachineryFixturePlan.CROSSING_CENTER; }
    static Set<BlockPos> crossingSecondaryOnly() { BlockPos c=crossingCenter(); return Set.of(c.offset(-1,0,0), c.offset(1,0,0)); }
    static List<BlockPos> crossingValvePositions() { BlockPos c=crossingCenter(); return List.of(c.offset(0,0,-2), c.offset(-2,0,0)); }
    private static List<AtmosMachineryFixtureSavedData.PortOwner> crossingOwners(ServerLevel level, BlockPos origin) {
        var owners = new ArrayList<AtmosMachineryFixtureSavedData.PortOwner>();
        for (BlockPos valvePos : crossingValvePositions()) {
            BlockPos pos = origin.offset(valvePos);
            if (level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                for (var port : machine.gasPorts()) owners.add(new AtmosMachineryFixtureSavedData.PortOwner(pos, port.id(), port.incarnation()));
        }
        return owners;
    }
    private static boolean preflight(ServerLevel level, BlockPos origin, AtmosMachineryFixturePlan plan) {
        for (int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++) for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++) for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++) {
            BlockPos pos=origin.offset(x,y,z);
            if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(pos)
                    ||level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false)==null
                    ||!level.getFluidState(pos).isEmpty()||level.getBlockEntity(pos)!=null)return false;
            var state=level.getBlockState(pos);
            if(y==0) { if(!state.isAir() && !state.canBeReplaced() && state.getDestroySpeed(level,pos)<0)return false; }
            else if(!state.isAir()) return false;
        }
        return true;
    }
    private static boolean footprintLoaded(ServerLevel level, BlockPos origin, AtmosMachineryFixturePlan plan) {
        for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++) {
            BlockPos p=origin.offset(x,1,z);
            if(level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return false;
        }
        return true;
    }
}
