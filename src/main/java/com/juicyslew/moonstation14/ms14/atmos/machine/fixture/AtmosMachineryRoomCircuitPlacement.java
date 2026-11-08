package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Places the ordinary, primary-lane room-facing devices for a machinery circuit. */
public final class AtmosMachineryRoomCircuitPlacement {
    public record PlacedDevice(UUID deviceId, BlockPos position) {
        public PlacedDevice { position = position.immutable(); }
    }
    public record Result(List<PlacedDevice> devices, String failure) {
        public Result { devices = List.copyOf(devices); }
        public boolean successful() { return failure == null; }
    }
    private AtmosMachineryRoomCircuitPlacement() { }

    public static Result place(ServerLevel level, AtmosMachineryRoomCircuitPlan plan) {
        var devices = new ArrayList<PlacedDevice>();
        for (var endpoint : plan.endpoints()) {
            // The exchanger's central observation chamber is a separately seeded
            // finite room, not a pipe endpoint and therefore has no room device.
            if (endpoint.ventHosts().isEmpty()) continue;
            record LayerRoute(BlockPos stub,BlockPos crossing,PipeTopology.Layer layer,Direction outward) {}
            var routes=new ArrayList<LayerRoute>();
            if(endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM) {
                for(int i=0;i<endpoint.additionalPipeStubs().size();i++) {
                    var stub=endpoint.additionalPipeStubs().get(i);var crossing=endpoint.additionalWallCrossings().get(i);
                    routes.add(new LayerRoute(stub,crossing,endpoint.hostLayers().get(i),Direction.getNearest(crossing.getX()-stub.getX(),0,crossing.getZ()-stub.getZ())));
                }
            } else for(var seed:endpoint.pipeSeeds()) {
                Direction outward=Direction.getNearest(seed.position().getX()-endpoint.machine().getX(),0,
                        seed.position().getZ()-endpoint.machine().getZ());
                routes.add(new LayerRoute(seed.position(),AtmosMachineryLaboratoryRooms.sealedCrossing(endpoint.machine(),outward),seed.layer(),outward));
            }
            for(var route:routes) {
                Direction outward=route.outward();BlockPos stub=route.stub(),crossing=route.crossing();
                PipeTopology.Layer layer=route.layer();
                var existing=LoadedSealedWallSegment.matchingLive(level,crossing,layer);
                if(existing.isEmpty()) {
                    if(!level.getBlockState(crossing).is(ModBlocks.STEEL_WALL_BLOCK.get()))
                        level.setBlock(crossing,ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
                    if(LoadedSealedWallSegment.prepareOne(level,crossing,outward,layer,UUID.randomUUID()).isEmpty()
                            ||!LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level,crossing,layer))
                        return new Result(devices,"sealed wall pipe admission failed at "+crossing+" layer "+layer);
                } else if(LoadedSealedWallGasOwner.activeSnapshot(level,crossing,layer).isEmpty()
                        &&!LoadedSealedWallGasOwner.reconcileExisting(level,crossing,layer))
                    return new Result(devices,"sealed wall gas owner unavailable at "+crossing+" layer "+layer);
                if(!level.getBlockState(stub).is(ModBlocks.GAS_PIPE_VISUAL.get()))
                    level.setBlock(stub,ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(),3);
                if(!(level.getBlockEntity(stub) instanceof VisualPipeBlockEntity pipe))return new Result(devices,"exposed pipe setup failed at "+stub);
                int bit=layerBit(layer);
                if(!pipe.hasLayer(bit)&&!(pipe.hasLayer(VisualPipeBlockEntity.PRIMARY)
                        ?pipe.addFunctionalPlacementLayer(bit):pipe.setLayersForPlacement(bit)))
                    return new Result(devices,"exposed pipe layer setup failed at "+stub+" layer "+layer);
                if(LoadedPipeGasOwner.activeSnapshot(level,stub,bit).isEmpty()
                        &&LoadedPipeGasOwner.createVacuumLane(level,stub,bit).isEmpty()
                        &&!LoadedPipeGasOwner.reconcileExisting(level,stub,bit))
                    return new Result(devices,"exposed pipe inventory missing at "+stub+" layer "+layer);
                VisualPipeBlockEntity.refreshLoadedAdjacent(level,stub);
            }
            for (int hostIndex=0;hostIndex<endpoint.ventHosts().size();hostIndex++) {
                BlockPos host=endpoint.ventHosts().get(hostIndex);
                Direction hostFacing=Direction.getNearest(host.getX()-endpoint.pipeStub().getX(),0,
                        host.getZ()-endpoint.pipeStub().getZ());
                boolean scrubber = endpoint.hostKind() == AtmosMachineryRoomCircuitPlan.HostKind.SCRUBBER_SIPHON;
                if (endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM) scrubber=hostIndex==0;
                var block = scrubber ? ModBlocks.GAS_SCRUBBER.get() : ModBlocks.GAS_VENT.get();
                level.setBlock(host, block.defaultBlockState().setValue(VentilationDeviceBlock.FACING, hostFacing), 3);
                if (!(level.getBlockEntity(host) instanceof VentilationDeviceBlockEntity device))
                    return new Result(devices, "ventilation device missing at " + host);
                var layer=endpoint.hostLayers().get(hostIndex);
                var old = device.config().settings();
                var settings = new VentilationDeviceConfig.Settings(old.revision()+1, false, old.externalPressureBoundKPa(),
                        old.targetPressureChangeKPaPerSecond(), old.targetPressureKPa(), old.volumeRateM3PerSecond(),
                        scrubber ? VentilationDeviceConfig.ScrubberMode.SIPHON : old.scrubberMode(), old.filteredGases(),
                        VentilationDeviceConfig.Layer.valueOf(layer.name()));
                if (!device.compareAndSetSettings(old.revision(), settings) || device.deviceId() == null)
                    return new Result(devices, "ventilation device configuration failed at " + host);
                devices.add(new PlacedDevice(device.deviceId(), host));
            }
        }
        return new Result(devices, null);
    }

    private static int layerBit(PipeTopology.Layer layer){return switch(layer){case PRIMARY->VisualPipeBlockEntity.PRIMARY;case SECONDARY->VisualPipeBlockEntity.SECONDARY;case TERTIARY->VisualPipeBlockEntity.TERTIARY;};}
}
