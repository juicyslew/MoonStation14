package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.ExposedPipeVisualBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallSegment;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import net.minecraft.server.level.FullChunkStatus;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.UUID;

/** Shared registered-block placement primitives for disposable TEG fixture builders. */
final class TegFixturePlacement {
    private TegFixturePlacement() { }
    /** Re-evaluates live exterior boundaries; callers must invoke after any relevant block writes. */
    static String continuousExteriorFailure(ServerLevel level, TegContinuousFuelPlan.Layout layout) {
        for (BlockPos host : layout.radiatorPositions()) {
            var boundary=AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level, host).orElse(null);
            if (boundary==null)
                return "Site refused: radiator host " + host + " is not a loaded, passable real exterior boundary.";
            if(boundary.kind()!=AtmosphereService.ExteriorBoundary.Kind.SPACE
                    &&boundary.kind()!=AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT)
                return "Site refused: radiator host " + host + " is not SPACE or planetary ambient.";
            if (AtmosphereService.INSTANCE.finiteCellEndpointForImmediateTransfer(level, host).isPresent())
                return "Site refused: radiator host " + host + " resolves to a finite atmosphere cell instead of the exterior sink.";
        }
        BlockPos wasteFront = layout.wasteVentBody().relative(layout.wasteVentFacing());
        if (AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level, wasteFront).isEmpty())
            return "Site refused: waste vent front " + wasteFront + " is not a loaded, passable real exterior boundary.";
        return null;
    }
    static void placeAtmos(ServerLevel level, BlockPos pos, AtmosMachineBlock block, Direction facing) {
        level.setBlock(pos, block.defaultBlockState().setValue(AtmosMachineBlock.FACING, facing), 3);
    }
    static void placePower(ServerLevel level, BlockPos pos, Block block, Direction facing) {
        if (block == ModBlocks.APC.get()) {
            if (!PowerDeviceFixturePlacement.ensureApcSupport(level, pos, facing) || !PowerDeviceFixturePlacement.placeApc(level, pos, facing))
                throw new IllegalStateException("APC support/placement failed at " + pos);
            return;
        }
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }
    /** Shared shell/observation/sign placement for both continuous and finite fixture layouts. */
    static void placePresentation(ServerLevel level,Set<BlockPos> shells,Set<BlockPos> windows,Map<BlockPos,TegFueledFixturePlan.PresentationLabel> labels) {
        for(BlockPos wall:shells)level.setBlock(wall,ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
        for(BlockPos window:windows)level.setBlock(window,Blocks.GLASS.defaultBlockState(),3);
        for(var label:labels.entrySet()){
            var data=label.getValue(); int rotation=switch(data.readerFacing()){case SOUTH->0;case WEST->4;case NORTH->8;case EAST->12;default->throw new IllegalArgumentException("horizontal sign facing required");};
            level.setBlock(label.getKey(),Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION,rotation),3);
            if(level.getBlockEntity(label.getKey()) instanceof SignBlockEntity sign){var text=sign.getFrontText();String[] lines=data.text().split("\\n",-1);for(int i=0;i<lines.length;i++)text=text.setMessage(i,Component.literal(lines[i]));sign.setText(text,true);sign.setChanged();}
        }
    }
    static Set<BlockPos> continuousObservationWindows(TegContinuousFuelPlan.Layout layout){
        Set<BlockPos> shells=new HashSet<>(layout.fueled().wallShell());shells.addAll(layout.fueled().coldWallShell());
        shells.addAll(layout.shellA());shells.addAll(layout.shellB());shells.addAll(layout.fueled().sharedCableHosts());shells.addAll(layout.wasteMountWall());
        Set<BlockPos> windows=new HashSet<>(TegFueledFixturePlan.observationWindows(layout.fueled().core()));
        windows.addAll(TegContinuousFuelPlan.sourceObservationWindows(layout.origin()));windows.retainAll(shells);windows.removeAll(layout.sealedCrossings());
        return Set.copyOf(windows);
    }

    /** Shared 48x8 footprint admission. Returns the historical refusal text, or null. */
    static String preflight(ServerLevel level, BlockPos origin, Set<BlockPos> planned,
                            Iterable<BlockPos> foundation) {
        return preflight(level,origin,planned,foundation,8);
    }
    static boolean insideFootprint(BlockPos origin,BlockPos pos,int height){return height>0&&pos.getX()>=origin.getX()&&pos.getX()<origin.getX()+48&&pos.getY()>=origin.getY()&&pos.getY()<origin.getY()+height&&pos.getZ()>=origin.getZ()&&pos.getZ()<origin.getZ()+48;}
    static String preflight(ServerLevel level, BlockPos origin, Set<BlockPos> planned,
                            Iterable<BlockPos> foundation,int height) {
        if(height<=0)throw new IllegalArgumentException("positive footprint height required");
        AABB bounds=new AABB(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+48,origin.getY()+height,origin.getZ()+48);
        var entities=level.getEntities((Entity)null,bounds,Entity::isAlive);
        if(!entities.isEmpty())return "Build refused: fixture footprint contains "+entities.size()+" entity/entities: "+entities.stream().limit(3).map(e->e.getType()+"@"+e.blockPosition()).toList()+".";
        for(BlockPos floor:foundation){if(!level.getWorldBorder().isWithinBounds(floor)||level.getChunkSource().getChunk(floor.getX()>>4,floor.getZ()>>4,ChunkStatus.FULL,false)==null)return "Build refused: foundation surface must already be FULL-loaded and within bounds.";var state=level.getBlockState(floor);if(state.isAir()||!state.isFaceSturdy(level,floor,Direction.UP)||!level.getFluidState(floor).isEmpty()||level.getBlockEntity(floor)!=null||state.getDestroySpeed(level,floor)<0)return "Build refused: fixture needs an intact, solid, breakable flat supporting floor at "+floor+".";}
        for(BlockPos pos:planned){if(!insideFootprint(origin,pos,height))return "Build refused: planned host outside 48x"+height+"x48 footprint at "+pos+".";if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight())return "Build refused: planned host outside build height at "+pos+".";if(!level.getWorldBorder().isWithinBounds(pos))return "Build refused: planned host outside world border at "+pos+".";if(level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false)==null)return "Build refused: planned host chunk is not FULL-loaded at "+pos+".";boolean floor=pos.getY()==origin.getY()+1;var state=level.getBlockState(pos);if(!floor&&!state.isAir())return "Build refused: occupied block "+state+" at "+pos+".";if(floor&&state.isAir())return "Build refused: fixture needs a solid, flat supporting floor at "+pos+".";if(state.getDestroySpeed(level,pos)<0)return "Build refused: unbreakable planned host "+state+" at "+pos+".";if(!level.getFluidState(pos).isEmpty())return "Build refused: fluid "+level.getFluidState(pos)+" at "+pos+".";if(level.getBlockEntity(pos)!=null)return "Build refused: block entity "+level.getBlockEntity(pos).getType()+" at "+pos+".";}
        return null;
    }

    static void placeBaseAtmosMachines(ServerLevel level, TegFueledFixturePlan.Layout p) {
        for(var m:p.machines()) placeAtmos(level,m.position(),baseBlock(m.kind()),m.facing());
        placeAtmos(level,p.core(),ModBlocks.TEG_CENTER.get(),Direction.NORTH);
    }
    static AtmosMachineBlock baseBlock(TegFueledFixturePlan.MachineKind kind) { return switch(kind) {
            case CIRCULATOR -> ModBlocks.TEG_CIRCULATOR.get();
            case PUMP -> ModBlocks.GAS_VOLUME_PUMP.get();
            case PRESSURE_PUMP -> ModBlocks.GAS_PRESSURE_PUMP.get();
            case EXCHANGER -> ModBlocks.PASSIVE_HEAT_EXCHANGER.get();
            case HEATER -> ModBlocks.GAS_THERMO_HEATER.get();
        }; }
    static net.minecraft.world.level.block.Block continuousBlock(TegContinuousFuelPlan.DeviceRole role) { return switch(role) {
        case MIXER -> ModBlocks.GAS_MIXER.get(); case PASSIVE_HEAT_EXCHANGER, HOT_HEAT_EXCHANGER -> ModBlocks.PASSIVE_HEAT_EXCHANGER.get();
        case COLD_PRESSURE_PUMP -> ModBlocks.GAS_PRESSURE_PUMP.get();
        case GAS_MINER_OXYGEN -> ModBlocks.GAS_MINER_OXYGEN.get(); case GAS_MINER_PLASMA -> ModBlocks.GAS_MINER_PLASMA.get();
        case SCRUBBER, WASTE_PUMP -> ModBlocks.GAS_SCRUBBER.get(); case INJECTOR, VENT -> ModBlocks.GAS_VENT.get();
    }; }

    /** Places only the eleven ordinary devices added by the continuous plan. Gas admission and port
     * reconciliation belong to the caller after its pipe topology has been admitted. */
    static ContinuousDevices placeContinuousDevices(ServerLevel level, TegContinuousFuelPlan.Layout layout) {
        List<AtmosMachineBlockEntity> atmos=new java.util.ArrayList<>();
        List<GasMinerBlockEntity> miners=new java.util.ArrayList<>();
        List<VentilationDeviceBlockEntity> vents=new java.util.ArrayList<>();
        for (var device:layout.devices()) {
            BlockPos pos=device.position();
            switch (device.role()) {
                case MIXER, PASSIVE_HEAT_EXCHANGER, HOT_HEAT_EXCHANGER, COLD_PRESSURE_PUMP -> {
                    AtmosMachineBlock block=(AtmosMachineBlock)continuousBlock(device.role());
                    placeAtmos(level,pos,block,device.facing());
                    if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine))
                        throw new IllegalStateException("Continuous atmos machine block entity missing at "+pos);
                    if (!machine.settingsValid() || machine.resolvedProfile()==null)
                        throw new IllegalStateException("Continuous atmos machine profile/settings invalid at "+pos);
                    if (device.role()==TegContinuousFuelPlan.DeviceRole.MIXER) {
                        machine.setControl("enabled",false);
                        if (!machine.settingsValid() || machine.settingsSnapshot()==null
                                || machine.settingsSnapshot().enabled().orElse(true))
                            throw new IllegalStateException("Continuous mixer disable failed at "+pos);
                    }
                    atmos.add(machine);
                }
                case GAS_MINER_OXYGEN, GAS_MINER_PLASMA -> {
                    var block=continuousBlock(device.role());
                    level.setBlock(pos,block.defaultBlockState(),3);
                    if (!(level.getBlockEntity(pos) instanceof GasMinerBlockEntity miner))
                        throw new IllegalStateException("Continuous gas miner block entity missing at "+pos);
                    var profile=miner.resolvedProfile();
                    if (profile==null || profile.gas()!=(device.role()==TegContinuousFuelPlan.DeviceRole.GAS_MINER_OXYGEN
                            ? com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN
                            : com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA))
                        throw new IllegalStateException("Continuous gas miner resolved profile invalid at "+pos);
                    miners.add(miner);
                }
                case SCRUBBER, INJECTOR, WASTE_PUMP, VENT -> {
                    boolean vent=device.role()==TegContinuousFuelPlan.DeviceRole.INJECTOR || device.role()==TegContinuousFuelPlan.DeviceRole.VENT;
                    var block=continuousBlock(device.role());
                    level.setBlock(pos,block.defaultBlockState().setValue(VentilationDeviceBlock.FACING,device.facing()),3);
                    if (!(level.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity ventilation))
                        throw new IllegalStateException("Continuous ventilation block entity missing at "+pos);
                    var prototype=ventilation.prototype(); var settings=ventilation.config().settings();
                    if (prototype==null || !ventilation.settingsValid() || settings==null)
                        throw new IllegalStateException("Continuous ventilation prototype/settings invalid at "+pos);
                    if ((settings.configuredEnabled() || vent && settings.selectedLayer()!=VentilationDeviceConfig.Layer.PRIMARY)
                            && !ventilation.compareAndSetSettings(settings.revision(),
                            new VentilationDeviceConfig.Settings(settings.revision(),false,settings.externalPressureBoundKPa(),
                                    settings.targetPressureChangeKPaPerSecond(),settings.targetPressureKPa(),settings.volumeRateM3PerSecond(),
                                    settings.scrubberMode(),settings.filteredGases(),vent?VentilationDeviceConfig.Layer.PRIMARY:settings.selectedLayer())))
                        throw new IllegalStateException("Continuous ventilation placement settings failed at "+pos);
                    if (!ventilation.settingsValid() || ventilation.prototype()==null)
                        throw new IllegalStateException("Continuous ventilation settings invalid after placement at "+pos);
                    vents.add(ventilation);
                }
            }
        }
        int expectedAtmos=(int)layout.devices().stream().filter(device->device.role().isAtmosMachine()).count();
        if(atmos.size()!=expectedAtmos||miners.size()!=2||vents.size()!=5)
            throw new IllegalStateException("Continuous device plan mismatch: atmos="+atmos.size()+" miners="+miners.size()+" vents="+vents.size());
        var resolvedHotProfile=atmos.stream().filter(machine->layout.hotExchangers().subList(1,3).contains(machine.getBlockPos()))
                .map(AtmosMachineBlockEntity::resolvedProfile).filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(()->new IllegalStateException("Continuous resolved hot exchanger profile missing"));
        layout.validateHotExchanger(resolvedHotProfile);
        return new ContinuousDevices(List.copyOf(atmos),List.copyOf(miners),List.copyOf(vents));
    }
    record ContinuousDevices(List<AtmosMachineBlockEntity> atmosMachines,List<GasMinerBlockEntity> miners,
                             List<VentilationDeviceBlockEntity> ventilation) { }

    /** Internal post-preflight hardware placement. It admits only empty finite vacuum rooms; source
     * gas, pipe inventory, ownership tokens and saved fixture state remain caller-owned. */
    static ContinuousHardware placeContinuousHardware(ServerLevel level,TegContinuousFuelPlan.Layout layout) {
        String phase="continuous placement";
        try {
            var base=layout.fueled();
            Set<BlockPos> shells=new HashSet<>(base.wallShell()); shells.addAll(base.coldWallShell());
            shells.addAll(layout.shellA()); shells.addAll(layout.shellB()); shells.addAll(base.sharedCableHosts());
            shells.addAll(layout.wasteMountWall());
            Set<BlockPos> windows=continuousObservationWindows(layout);
            placePresentation(level,shells,windows,layout.presentationSigns());
            level.setBlock(layout.wasteMountHost(),ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
            Set<BlockPos> protectedCells=new HashSet<>(base.allOwnedCells());
            protectedCells.addAll(shells); protectedCells.addAll(layout.machines());
            layout.routes().values().forEach(protectedCells::addAll);
            protectedCells.addAll(layout.outsideVent()); protectedCells.addAll(layout.roomA()); protectedCells.addAll(layout.roomB());
            var cables=TegContinuousPowerPlan.cables(layout.origin());
            placeCableHosts(level,cables,protectedCells);
            placeBaseAtmosMachines(level,base);
            placePowerDevices(level,base.electrical().devices(),false);
            var auxiliaryEquipment=TegContinuousPowerPlan.equipment(layout);
            BlockPos secondaryApc=auxiliaryEquipment.secondaryStartupApc();
            placePower(level,secondaryApc,ModBlocks.APC.get(),auxiliaryEquipment.secondaryStartupApcFacing());
            if(level.getBlockEntity(secondaryApc) instanceof PowerDeviceBlockEntity device)device.setEnergyJoules(0);
            ContinuousDevices additional=placeContinuousDevices(level,layout);

            phase="continuous cable admission";
            String failure=admitCables(level,cables);
            if(failure!=null)throw new IllegalStateException(failure);

            phase="primary pipe admission";
            Set<BlockPos> primary=new LinkedHashSet<>(base.exposedPipePositions());
            primary.addAll(layout.hotPipeHosts());
            primary.addAll(layout.coldExposedPipes());
            layout.routes().values().forEach(primary::addAll);
            primary.removeAll(base.sealedCrossings()); primary.removeAll(base.coldSealedCrossings());
            primary.removeAll(layout.sealedCrossings());
            layout.sealedRouteSegments().forEach(segment->primary.remove(segment.position()));
            failure=admitExposedLanes(level,primary,VisualPipeBlockEntity.PRIMARY);
            if(failure!=null)throw new IllegalStateException(failure);
            refreshPipeConnections(level,layout.hotPipeConnections());

            phase="sealed crossings";
            Map<BlockPos,Direction> crossings=new LinkedHashMap<>();
            base.sealedCrossings().forEach(pos->crossings.put(pos,Direction.EAST));
            base.coldSealedCrossings().forEach(pos->crossings.put(pos,Direction.EAST));
            crossings.put(layout.origin().offset(2,2,2),Direction.NORTH);
            crossings.put(layout.origin().offset(10,2,2),Direction.NORTH);
             var injector=layout.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.INJECTOR).findFirst().orElseThrow();
             crossings.put(injector.position().relative(injector.facing().getOpposite()),injector.facing().getOpposite());
            for(var segment:layout.sealedRouteSegments())crossings.put(segment.position(),Direction.NORTH);
            failure=admitSealedCrossings(level,crossings,PipeTopology.Layer.PRIMARY);
            if(failure!=null)throw new IllegalStateException(failure);
            for(var segment:layout.sealedRouteSegments())
                VisualPipeBlockEntity.refreshLoadedAdjacent(level,segment.position());

            phase="room admission";
            var atmosphere=AtmosphereService.INSTANCE;
            var vacuum=new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(Map.of(),293.15);
            for(Set<BlockPos> room:List.of(base.roomInterior(),base.coldRoomInterior(),layout.roomA(),layout.roomB()))
                if(!room.isEmpty()&&!atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,List.copyOf(room),vacuum))
                    throw new IllegalStateException("enclosed vacuum room admission failed");

            phase="machine reconciliation";
            List<AtmosMachineBlockEntity> machines=new java.util.ArrayList<>();
            for(var planned:base.machines()) {
                if(!(level.getBlockEntity(planned.position()) instanceof AtmosMachineBlockEntity machine))
                    throw new IllegalStateException("base machine missing at "+planned.position());
                machine.reconcileLoadedPorts(level);
                if(!machine.settingsValid()||machine.resolvedProfile()==null)throw new IllegalStateException("base machine profile/settings invalid at "+planned.position());
                machine.setControl("enabled",false); machines.add(machine);
            }
            for(var machine:additional.atmosMachines()) machine.reconcileLoadedPorts(level);
            var corePos=base.core();
            if(!(level.getBlockEntity(corePos) instanceof AtmosMachineBlockEntity core))
                throw new IllegalStateException("TEG core missing at "+corePos);
            core.reconcileLoadedPorts(level);
            if(!core.settingsValid()||core.resolvedProfile()==null)throw new IllegalStateException("TEG core profile/settings invalid at "+corePos);
            return new ContinuousHardware(List.copyOf(machines),core,additional);
        } catch(RuntimeException failure) {
            throw new IllegalStateException("Continuous hardware placement failed during "+phase+"; partial placement may require manual recovery: "+failure.getMessage(),failure);
        }
    }
    record ContinuousHardware(List<AtmosMachineBlockEntity> baseMachines,AtmosMachineBlockEntity core,
                              ContinuousDevices additional) { }

    static void placePowerDevices(ServerLevel level, TegFixturePlan.Devices d, boolean includeStartupApc) {
        if(includeStartupApc) placePower(level,d.startupApc(),ModBlocks.APC.get(),Direction.EAST);
        placePower(level,d.substation(),ModBlocks.HV_MV_SUBSTATION.get(),Direction.EAST);
        placePower(level,d.outputApc(),ModBlocks.APC.get(),Direction.WEST);
        placePower(level,d.load(),ModBlocks.POWER_LAMP.get(),Direction.NORTH);
        if(includeStartupApc && level.getBlockEntity(d.startupApc()) instanceof PowerDeviceBlockEntity device)device.setEnergyJoules(0);
        if(level.getBlockEntity(d.outputApc()) instanceof PowerDeviceBlockEntity device)device.setEnergyJoules(0);
    }

    static Map<PipeTopology.NodeKey,UUID> gasOwners(LoadedPipeGasWork.Snapshot captured) {
        Map<PipeTopology.NodeKey,UUID> owners=new HashMap<>();
        for(var c:captured.components().values())for(var n:c.nodes())owners.put(n.key(),n.incarnation());
        return owners;
    }
    static Map<BlockPos,UUID> machineOwners(java.util.List<AtmosMachineBlockEntity> machines, AtmosMachineBlockEntity core) {
        Map<BlockPos,UUID> owners=new HashMap<>();
        for(var machine:machines) putOwner(owners,machine.getBlockPos(),machine.placementIdentity());
        putOwner(owners,core.getBlockPos(),core.placementIdentity());
        return owners;
    }
    static Map<BlockPos,UUID> machineOwners(java.util.List<AtmosMachineBlockEntity> machines, AtmosMachineBlockEntity core,
                                             java.util.Collection<GasMinerBlockEntity> miners,
                                            java.util.Collection<VentilationDeviceBlockEntity> vents) {
        Map<BlockPos,UUID> owners=machineOwners(machines,core);
        for(var miner:miners) {
            UUID identity=miner.placementIdentity();
            if(identity==null) throw new IllegalStateException("Gas miner placement identity is unavailable at "+miner.getBlockPos());
            if(!miner.hasLivePlacementIdentity()) throw new IllegalStateException("Gas miner is not the exact FULL-loaded host at "+miner.getBlockPos());
            putOwner(owners,miner.getBlockPos(),identity);
        }
        for(var vent:vents) {
            UUID identity=vent.deviceId();
            if(identity==null || !(vent.getLevel() instanceof ServerLevel server) || server.getServer()==null
                    || !server.getServer().isSameThread() || vent.isRemoved())
                throw new IllegalStateException("Ventilation device identity is unavailable at "+vent.getBlockPos());
            var chunk=server.getChunkSource().getChunkNow(vent.getBlockPos().getX()>>4,vent.getBlockPos().getZ()>>4);
            if(chunk==null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                    || chunk.getBlockEntities().get(vent.getBlockPos())!=vent)
                throw new IllegalStateException("Ventilation device is not the exact FULL-loaded host at "+vent.getBlockPos());
            putOwner(owners,vent.getBlockPos(),identity);
        }
        return owners;
    }
    /** Continuous-only owner extension: adds each fixture APC through its persisted PowerDevice identity. */
    static Map<BlockPos,UUID> continuousMachineOwners(java.util.List<AtmosMachineBlockEntity> machines,AtmosMachineBlockEntity core,
            java.util.Collection<GasMinerBlockEntity> miners,java.util.Collection<VentilationDeviceBlockEntity> vents,
            java.util.Collection<PowerDeviceBlockEntity> powerDevices) {
        Map<BlockPos,UUID> owners=machineOwners(machines,core,miners,vents);
        for(var powerDevice:powerDevices) {
            if(powerDevice==null||!(powerDevice.getLevel() instanceof ServerLevel level)||level.getServer()==null
                    ||!level.getServer().isSameThread()||powerDevice.isRemoved()
                    ||!level.getBlockState(powerDevice.getBlockPos()).is(ModBlocks.APC.get()))
                throw new IllegalStateException("Power-device owner identity is unavailable or not an exact APC host");
            var chunk=level.getChunkSource().getChunkNow(powerDevice.getBlockPos().getX()>>4,powerDevice.getBlockPos().getZ()>>4);
            if(chunk==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                    ||chunk.getBlockEntities().get(powerDevice.getBlockPos())!=powerDevice)
                throw new IllegalStateException("Power device is not the exact FULL-loaded owner at "+powerDevice.getBlockPos());
            putOwner(owners,powerDevice.getBlockPos(),powerDevice.placementIdentity());
        }
        return owners;
    }
    private static void putOwner(Map<BlockPos,UUID> owners, BlockPos pos, UUID identity) {
        if(identity==null || owners.putIfAbsent(pos,identity)!=null)
            throw new IllegalStateException("Missing or duplicate fixture owner at "+pos);
    }
    static Map<BlockPos,UUID> machineOwners(java.util.List<AtmosMachineBlockEntity> machines, AtmosMachineBlockEntity core,
                                            java.util.Collection<GasMinerBlockEntity> miners) {
        return machineOwners(machines,core,miners,java.util.List.of());
    }

    static void placeCableHosts(ServerLevel level, Collection<CableFaceNode> cables, Set<BlockPos> protectedCells) {
        Set<BlockPos> hosts=new java.util.LinkedHashSet<>();for(var cable:cables)hosts.add(cable.host());
        for(BlockPos host:hosts)if(!protectedCells.contains(host))level.setBlock(host,Blocks.STONE.defaultBlockState(),3);
    }

    static String admitExposedLanes(ServerLevel level, Collection<BlockPos> positions, int layer) {
        for(BlockPos pos:positions){
            boolean fresh=!level.getBlockState(pos).is(ModBlocks.GAS_PIPE_VISUAL.get());
            if(fresh)level.setBlock(pos,ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(),3);
            if(!(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity visual))return "Partial build: primary vacuum lane admission failed at "+pos+"; manual recovery required.";
            boolean configured=fresh?visual.setLayersForPlacement(layer):visual.addFunctionalPlacementLayer(layer);
            if(!configured||LoadedPipeGasOwner.createVacuumLane(level,pos,layer).isEmpty())return "Partial build: primary vacuum lane admission failed at "+pos+"; manual recovery required.";
        }
        return null;
    }

    /** Rebuilds every exposed visual arm for a declared connected primary-lane host set. */
    static void refreshPipeConnections(ServerLevel level,Map<BlockPos,Set<Direction>> connections) {
        for(var entry:connections.entrySet()) {
            BlockPos pos=entry.getKey();Set<Direction> directions=entry.getValue();
            if(!(level.getBlockEntity(pos) instanceof VisualPipeBlockEntity pipe)||!pipe.hasLayer(VisualPipeBlockEntity.PRIMARY))
                throw new IllegalStateException("declared hot pipe host is not an admitted primary lane at "+pos);
            var state=level.getBlockState(pos);var updated=state;
            for(Direction direction:Direction.values()) {
                BlockPos neighbor=pos.relative(direction);
                boolean connected=directions.contains(direction)&&level.getBlockEntity(neighbor) instanceof VisualPipeBlockEntity adjacent
                        &&adjacent.hasLayer(VisualPipeBlockEntity.PRIMARY);
                updated=updated.setValue(switch(direction) {
                    case NORTH -> ExposedPipeVisualBlock.NORTH;case SOUTH -> ExposedPipeVisualBlock.SOUTH;
                    case EAST -> ExposedPipeVisualBlock.EAST;case WEST -> ExposedPipeVisualBlock.WEST;
                    case UP -> ExposedPipeVisualBlock.UP;case DOWN -> ExposedPipeVisualBlock.DOWN;
                },connected);
            }
            if(!updated.equals(state))level.setBlock(pos,updated,3);
        }
    }

    static String admitCables(ServerLevel level, Collection<CableFaceNode> cables) {
        for(var cable:cables)if(!CableStorage.place(level,cable.host(),cable.face(),cable.tier()))return "Partial build: cable face admission failed at "+cable+"; manual recovery required.";
        return null;
    }

    static String admitSealedCrossings(ServerLevel level, Map<BlockPos,Direction> crossings, PipeTopology.Layer layer) {
        for(var crossing:crossings.entrySet())if(LoadedSealedWallSegment.prepareOne(level,crossing.getKey(),crossing.getValue(),layer,UUID.randomUUID()).isEmpty()||!LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level,crossing.getKey(),layer))return "Partial build: sealed crossing ownership failed at "+crossing.getKey()+"; manual recovery required.";
        return null;
    }
}
