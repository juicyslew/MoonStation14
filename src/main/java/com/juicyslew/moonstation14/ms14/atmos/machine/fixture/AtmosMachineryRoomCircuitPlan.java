package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;

import java.util.*;

/** Pure lab-only plan for real finite rooms and ordinary vent/scrubber endpoints. */
public final class AtmosMachineryRoomCircuitPlan {
    public enum Role { SOURCE, RECEIVER, FILTERED, EXHAUST, THERMAL_ROOM, MANIFOLD_IN, MANIFOLD_OUT, EXCHANGER_ROOM }
    public enum HostKind { SCRUBBER_SIPHON, VENT }
    public record Inventory(Map<GasType, Double> moles, double temperatureK) {
        public Inventory { moles=Map.copyOf(moles); if(!Double.isFinite(temperatureK)||temperatureK<=0)throw new IllegalArgumentException("invalid room temperature"); }
        public double totalMoles(){return moles.values().stream().mapToDouble(Double::doubleValue).sum();}
    }
    public record Endpoint(AtmosMachineryFixturePlan.Kind station, Role role, List<AtmosMachineProfile.PortName> ports,
                           HostKind hostKind, BlockPos machine, BlockPos pipeStub, BlockPos wallCrossing,
                           List<BlockPos> ventHosts, List<BlockPos> additionalWallCrossings, List<BlockPos> additionalPipeStubs,
                            AtmosMachineryLaboratoryRooms.Geometry room, Inventory initialInventory, String label,
                             List<AtmosMachineryFixturePlan.PipeSeed> pipeSeeds, List<PipeTopology.Layer> hostLayers,
                             boolean enabledAtStart) {
        public Endpoint {
            ports=List.copyOf(ports); machine=machine.immutable(); pipeStub=pipeStub.immutable(); wallCrossing=wallCrossing.immutable();
            ventHosts=immutable(ventHosts); additionalWallCrossings=immutable(additionalWallCrossings); additionalPipeStubs=immutable(additionalPipeStubs);
            pipeSeeds=List.copyOf(pipeSeeds); hostLayers=List.copyOf(hostLayers);
            if(hostLayers.size()!=ventHosts.size())throw new IllegalArgumentException("each vent host needs a planned pipe layer");
        }
        private static List<BlockPos> immutable(List<BlockPos> positions){return positions.stream().map(BlockPos::immutable).toList();}
        public Direction outward(){return Direction.getNearest(pipeStub.getX()-machine.getX(),0,pipeStub.getZ()-machine.getZ());}
    }
    private final List<Endpoint> endpoints;
    private AtmosMachineryRoomCircuitPlan(AtmosMachineryFixturePlan fixture, BlockPos origin) {
        var out=new ArrayList<Endpoint>();
        for(var placement:fixture.placements()) {
            BlockPos machine=origin.offset(placement.position());
            var seeds=new ArrayList<AtmosMachineryFixturePlan.PipeSeed>();
            placement.inletSeeds().forEach(s -> seeds.add(worldSeed(s,origin)));
            placement.outletSeeds().forEach(s -> seeds.add(worldSeed(s,origin)));
            if(placement.kind()==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER) {
                for(var seed:seeds) {
                    Role role=role(placement.kind(),seed.port());
                    Inventory gas=role==Role.SOURCE?inventory(120,GasType.PLASMA,120,GasType.NITROGEN,293.15):inventory(0,GasType.PLASMA,0,GasType.NITROGEN,293.15);
                    regular(out,origin,placement.kind(),role,seed,machine,gas,"EXCHANGER "+role+" plasma; heat only");
                }
                var exchangerRoom=AtmosMachineryLaboratoryRooms.exchanger(origin);
                out.add(roomOnly(placement.kind(),Role.EXCHANGER_ROOM,machine,exchangerRoom,inventory(60,GasType.PLASMA,350.0),"EXCHANGER_ROOM plasma; heat only"));
                continue;
            }
            if(placement.kind()==AtmosMachineryFixturePlan.Kind.HEATER || placement.kind()==AtmosMachineryFixturePlan.Kind.FREEZER) {
                var seed=seeds.getFirst(); Direction d=outward(machine,seed.position());
                BlockPos center=machine.relative(d,4), hostA=machine.relative(d,3).relative(d.getClockWise()), hostB=machine.relative(d,3).relative(d.getCounterClockWise());
                var room=AtmosMachineryLaboratoryRooms.deviceRoom(origin,machine,List.of(hostA,hostB),machine.relative(d,7),center);
                out.add(new Endpoint(placement.kind(),Role.THERMAL_ROOM,List.of(seed.port()),HostKind.VENT,machine,seed.position(),machine.relative(d,2),
                        List.of(hostA,hostB),List.of(machine.relative(d,2).relative(d.getClockWise()),machine.relative(d,2).relative(d.getCounterClockWise())),
                        List.of(machine.relative(d).relative(d.getClockWise()),machine.relative(d).relative(d.getCounterClockWise())),room,
                        inventory(100,GasType.PLASMA,293.15),"THERMAL_ROOM · measured finite-gas temperature",
                         List.of(seed),List.of(seed.layer(),seed.layer()),true));
                continue;
            }
            if(placement.kind()==AtmosMachineryFixturePlan.Kind.MANIFOLD) {
                addManifoldEndpoint(out,origin,placement,seeds,machine,Role.MANIFOLD_IN);
                addManifoldEndpoint(out,origin,placement,seeds,machine,Role.MANIFOLD_OUT);
                continue;
            }
            for(var seed:seeds) {
                Role role=role(placement.kind(),seed.port());
                Inventory gas;
                if(role==Role.SOURCE && placement.kind()==AtmosMachineryFixturePlan.Kind.MIXER && seed.port()==AtmosMachineProfile.PortName.FILTER)
                    gas=inventory(240,GasType.WATER_VAPOR,293.15);
                else if(role==Role.SOURCE && placement.kind()==AtmosMachineryFixturePlan.Kind.MIXER && seed.port()==AtmosMachineProfile.PortName.INLET) gas=inventory(240,GasType.PLASMA,293.15);
                else if(role==Role.SOURCE && placement.kind()==AtmosMachineryFixturePlan.Kind.MIXER) gas=inventory(240,GasType.WATER_VAPOR,293.15);
                else if(role==Role.SOURCE && placement.kind()==AtmosMachineryFixturePlan.Kind.FILTER) gas=inventory(120,GasType.PLASMA,120,GasType.WATER_VAPOR,293.15);
                else if(role==Role.SOURCE) gas=inventory(120,GasType.PLASMA,120,GasType.NITROGEN,293.15);
                else if(role==Role.FILTERED) gas=inventory(0,GasType.PLASMA,293.15);
                else if(role==Role.EXHAUST) gas=inventory(0,GasType.WATER_VAPOR,293.15);
                else if(role==Role.RECEIVER && placement.kind()==AtmosMachineryFixturePlan.Kind.FILTER) gas=inventory(0,GasType.PLASMA,0,GasType.WATER_VAPOR,293.15);
                else if(role==Role.RECEIVER && placement.kind()==AtmosMachineryFixturePlan.Kind.MIXER) gas=inventory(0,GasType.PLASMA,0,GasType.WATER_VAPOR,293.15);
                else if(role==Role.RECEIVER) gas=inventory(0,GasType.PLASMA,0,GasType.NITROGEN,293.15);
                else gas=inventory(60,GasType.NITROGEN,293.15);
                regular(out,origin,placement.kind(),role,seed,machine,gas,placement.kind()+" · "+role+" · "+gas.moles()+" mol initial · 293.15 K");
            }
        }
        endpoints=List.copyOf(out);
    }
    private static AtmosMachineryFixturePlan.PipeSeed worldSeed(AtmosMachineryFixturePlan.PipeSeed seed,BlockPos origin){
        return new AtmosMachineryFixturePlan.PipeSeed(origin.offset(seed.position()),seed.axis(),seed.port(),seed.layer());
    }
    private static void regular(List<Endpoint> out,BlockPos origin,AtmosMachineryFixturePlan.Kind kind,Role role,
                                AtmosMachineryFixturePlan.PipeSeed seed,BlockPos machine,Inventory gas,String label) {
        Direction d=outward(machine,seed.position());
        BlockPos host=machine.relative(d,3), crossing=AtmosMachineryLaboratoryRooms.sealedCrossing(machine,d);
        var room=AtmosMachineryLaboratoryRooms.endpoint(origin,host,d,machine.relative(d,7));
        HostKind hostKind=role==Role.SOURCE||role==Role.MANIFOLD_IN?HostKind.SCRUBBER_SIPHON:HostKind.VENT;
        out.add(new Endpoint(kind,role,List.of(seed.port()),hostKind,machine,seed.position(),crossing,List.of(host),List.of(),List.of(),room,gas,label,
                List.of(seed),List.of(seed.layer()),!(kind==AtmosMachineryFixturePlan.Kind.MANIFOLD
                        && role==Role.MANIFOLD_IN && seed.layer()!=PipeTopology.Layer.PRIMARY)));
    }
    private static void addManifoldEndpoint(List<Endpoint> out,BlockPos origin,AtmosMachineryFixturePlan.Placement placement,
                                            List<AtmosMachineryFixturePlan.PipeSeed> seeds,BlockPos machine,Role role) {
        var group=seeds.stream().filter(s->role(placement.kind(),s.port())==role)
                .sorted(Comparator.comparing(AtmosMachineryFixturePlan.PipeSeed::layer)).toList();
        var primary=group.getFirst();
        Inventory gas=role==Role.MANIFOLD_IN?inventory(120,GasType.PLASMA,120,GasType.NITROGEN,293.15):inventory(0,GasType.NITROGEN,293.15);
        regular(out,origin,placement.kind(),role,primary,machine,gas,"Manifold shared 300 L · "+role+" · six ports mix");
        Endpoint endpoint=out.removeLast();
        out.add(new Endpoint(endpoint.station(),endpoint.role(),group.stream().map(AtmosMachineryFixturePlan.PipeSeed::port).toList(),endpoint.hostKind(),
                endpoint.machine(),endpoint.pipeStub(),endpoint.wallCrossing(),endpoint.ventHosts(),
                group.stream().skip(1).map(seed->AtmosMachineryLaboratoryRooms.sealedCrossing(machine,outward(machine,seed.position()))).toList(),
                group.stream().skip(1).map(AtmosMachineryFixturePlan.PipeSeed::position).toList(),endpoint.room(),endpoint.initialInventory(),endpoint.label(),
                group,List.of(primary.layer()),endpoint.enabledAtStart()));
    }
    private static Endpoint roomOnly(AtmosMachineryFixturePlan.Kind kind,Role role,BlockPos machine,AtmosMachineryLaboratoryRooms.Geometry room,Inventory gas,String label){
        return new Endpoint(kind,role,List.of(),HostKind.VENT,machine,machine,machine,List.of(),List.of(),List.of(),room,gas,label,List.of(),List.of(),true);
    }
    private static Direction outward(BlockPos machine,BlockPos stub){
        int dx=stub.getX()-machine.getX(), dz=stub.getZ()-machine.getZ();
        if(Math.abs(dx)+Math.abs(dz)!=1)throw new IllegalArgumentException("machine pipe stub must be an adjacent cardinal position");
        return Direction.getNearest(dx,0,dz);
    }
    private static Role role(AtmosMachineryFixturePlan.Kind kind,AtmosMachineProfile.PortName port){return switch(kind){
        case PRESSURE_PUMP,VOLUME_PUMP,VALVE -> port==AtmosMachineProfile.PortName.INLET?Role.SOURCE:Role.RECEIVER;
        case MIXER -> port==AtmosMachineProfile.PortName.OUTLET?Role.RECEIVER:Role.SOURCE;
        case FILTER -> port==AtmosMachineProfile.PortName.INLET?Role.SOURCE:port==AtmosMachineProfile.PortName.FILTER?Role.FILTERED:Role.EXHAUST;
        case PASSIVE_EXCHANGER -> port==AtmosMachineProfile.PortName.INLET?Role.SOURCE:Role.RECEIVER;
        case MANIFOLD -> port.name().startsWith("NORTH")?Role.MANIFOLD_IN:Role.MANIFOLD_OUT;
        default -> throw new IllegalArgumentException("not a room-circuit station: "+kind);
    };}
    private static Inventory inventory(double amount,GasType gas,double temperature){return new Inventory(Map.of(gas,amount),temperature);}
    private static Inventory inventory(double first,GasType a,double second,GasType b,double temperature){return new Inventory(Map.of(a,first,b,second),temperature);}
    public static AtmosMachineryRoomCircuitPlan standard(AtmosMachineryFixturePlan fixture,BlockPos origin){return new AtmosMachineryRoomCircuitPlan(Objects.requireNonNull(fixture),Objects.requireNonNull(origin));}
    public List<Endpoint> endpoints(){return endpoints;}
}
