package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.*;

/** Pure continuous-generation cable reservation; supplies no energy and performs no bootstrap. */
public final class TegContinuousPowerPlan {
    public static final double TOTAL_BOOTSTRAP_JOULES=PowerDeviceRules.APC_CAPACITY_JOULES;
    public static final Direction SECONDARY_STARTUP_APC_FACING=Direction.WEST;
    private TegContinuousPowerPlan() { }

    /** Continuous-operation electrical equipment; the finite startup APC is never part of this plan. */
    public static Equipment equipment(TegContinuousFuelPlan.Layout layout) {
        var devices=layout.fueled().electrical().devices();
        return new Equipment(devices.substation(),devices.outputApc(),devices.load(),secondaryStartupApc(layout.core()),SECONDARY_STARTUP_APC_FACING);
    }
    public static BlockPos secondaryStartupApc(BlockPos core){return core.offset(-9,1,-6);}
    public record Equipment(BlockPos substation,BlockPos operatingApc,BlockPos lamp,BlockPos secondaryStartupApc,Direction secondaryStartupApcFacing) { }
    public enum StartupReceiverKind { TEG_CORE, VOLUME_PUMP, PRESSURE_PUMP, HEATER, VENTILATION, POWER_LAMP }
    public record StartupReceiverPositions(Map<BlockPos,StartupReceiverKind> mainApc,
                                           Map<BlockPos,StartupReceiverKind> secondaryApc) {
        public StartupReceiverPositions { mainApc=Map.copyOf(mainApc);secondaryApc=Map.copyOf(secondaryApc); }
    }
    public record ReceiverIdentity(BlockPos position,StartupReceiverKind kind) {
        public ReceiverIdentity { if(position==null||kind==null)throw new IllegalArgumentException("receiver identity requires position and kind");position=position.immutable(); }
    }
    /** Exact typed-roster comparison used by construction/runtime admission and its regressions. */
    public static boolean exactReceiverRoster(Map<BlockPos,StartupReceiverKind> expected,Collection<ReceiverIdentity> actual) {
        if(expected==null||actual==null)return false;
        Map<BlockPos,StartupReceiverKind> observed=new HashMap<>();
        for(var receiver:actual)if(receiver==null||observed.putIfAbsent(receiver.position(),receiver.kind())!=null)return false;
        return expected.equals(observed);
    }
    public static List<ReceiverIdentity> receiverIdentities(Map<BlockPos,StartupReceiverKind> roster) {
        if(roster==null)throw new IllegalArgumentException("typed receiver roster required");
        return roster.entrySet().stream().map(entry->new ReceiverIdentity(entry.getKey(),entry.getValue())).toList();
    }
    /** Exact typed receiver membership for the two real bootstrap circuits. */
    public static StartupReceiverPositions startupReceiverPositions(TegContinuousFuelPlan.Layout layout) {
        if(layout==null)throw new IllegalArgumentException("continuous fixture layout required");
        var heaters=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList();
        if(heaters.size()!=2||heaters.get(0).position().equals(heaters.get(1).position()))
            throw new IllegalArgumentException("two unique continuous startup heaters required");
        Map<BlockPos,StartupReceiverKind> main=new LinkedHashMap<>();putStartupReceiver(main,layout.core(),StartupReceiverKind.TEG_CORE);
        for(var machine:layout.fueled().machines())switch(machine.kind()) {
            case PUMP -> putStartupReceiver(main,machine.position(),StartupReceiverKind.VOLUME_PUMP);
            case PRESSURE_PUMP -> putStartupReceiver(main,machine.position(),StartupReceiverKind.PRESSURE_PUMP);
            case HEATER -> { if(machine.position().equals(heaters.get(0).position()))putStartupReceiver(main,machine.position(),StartupReceiverKind.HEATER); }
            case CIRCULATOR,EXCHANGER -> { }
        }
        for(var device:layout.devices())switch(device.role()) {
            case SCRUBBER,INJECTOR,WASTE_PUMP,VENT -> putStartupReceiver(main,device.position(),StartupReceiverKind.VENTILATION);
            case COLD_PRESSURE_PUMP -> putStartupReceiver(main,device.position(),StartupReceiverKind.PRESSURE_PUMP);
            case GAS_MINER_OXYGEN,GAS_MINER_PLASMA,MIXER,PASSIVE_HEAT_EXCHANGER,HOT_HEAT_EXCHANGER -> { }
        }
        putStartupReceiver(main,equipment(layout).lamp(),StartupReceiverKind.POWER_LAMP);
        Map<BlockPos,StartupReceiverKind> auxiliary=Map.of(heaters.get(1).position(),StartupReceiverKind.HEATER);
        if(!Collections.disjoint(main.keySet(),auxiliary.keySet()))
            throw new IllegalStateException("startup APC receiver rosters overlap");
        return new StartupReceiverPositions(main,auxiliary);
    }
    private static void putStartupReceiver(Map<BlockPos,StartupReceiverKind> receivers,BlockPos position,StartupReceiverKind kind) {
        if(receivers.putIfAbsent(position,kind)!=null)throw new IllegalStateException("duplicate planned startup receiver position: "+position);
    }
    public enum StartupCircuit { OPERATING, SECONDARY_HEATER }
    public record StartupReceiver(BlockPos position,StartupCircuit circuit,double resolvedDemandWatts) {
        public StartupReceiver {
            if(position==null||circuit==null||!Double.isFinite(resolvedDemandWatts)||resolvedDemandWatts<=0)
                throw new IllegalArgumentException("resolved positive startup receiver demand required");
            position=position.immutable();
        }
    }
    public record BootstrapSplit(double totalJoules,double operatingJoules,double secondaryJoules,
                                 double operatingDemandWatts,double secondaryDemandWatts) { }
    /** Allocate the original finite store in proportion to the resolved startup circuit loads. */
    public static BootstrapSplit splitBootstrapByResolvedDemand(double totalJoules,List<StartupReceiver> roster) {
        if(!Double.isFinite(totalJoules)||totalJoules<=0||roster==null||roster.isEmpty())
            throw new IllegalArgumentException("positive bootstrap and resolved startup receiver roster required");
        double operating=0,secondary=0;Set<BlockPos> positions=new HashSet<>();
        for(StartupReceiver receiver:roster) {
            if(!positions.add(receiver.position()))throw new IllegalArgumentException("duplicate startup receiver position");
            if(receiver.circuit()==StartupCircuit.OPERATING)operating+=receiver.resolvedDemandWatts();
            else secondary+=receiver.resolvedDemandWatts();
        }
        double totalDemand=operating+secondary;
        if(!Double.isFinite(operating)||!Double.isFinite(secondary)||operating<=0||secondary<=0||!Double.isFinite(totalDemand))
            throw new IllegalArgumentException("both startup circuits require positive finite resolved demand");
        double operatingJoules=totalJoules*(operating/totalDemand);
        double secondaryJoules=totalJoules-operatingJoules;
        if(!Double.isFinite(operatingJoules)||!Double.isFinite(secondaryJoules)||operatingJoules<=0||secondaryJoules<=0
                ||operatingJoules>PowerDeviceRules.APC_CAPACITY_JOULES||secondaryJoules>PowerDeviceRules.APC_CAPACITY_JOULES)
            throw new IllegalArgumentException("proportional startup stores exceed per-APC capacity");
        return new BootstrapSplit(totalJoules,operatingJoules,secondaryJoules,operating,secondary);
    }

    /** Reuses the established fueled-layout cable tree, omitting only its startup APC branch. */
    public static List<CableFaceNode> cables(BlockPos origin) {
        var layout=TegContinuousFuelPlan.layout(origin);
        BlockPos c=layout.core();
        LinkedHashSet<CableFaceNode> out=new LinkedHashSet<>(layout.fueled().combinedCables());
        BlockPos heater=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).map(TegFueledFixturePlan.Machine::position).findFirst().orElseThrow();
        BlockPos secondHeater=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList().get(1).position();
        int heaterBusZ=heater.getZ()-origin.getZ();
        int bypassX=secondHeater.getX()-origin.getX()-1;
        // Bridge existing LV branches to the output APC tree. This face bus serves both vents and real equipment.
        int y=origin.getY()+1;
        for(int z=3;z<=31;z++) add(out,new BlockPos(origin.getX()+17,y,origin.getZ()+z),Direction.UP,CableTier.APC);
        for(int x=17;x<=27;x++) add(out,new BlockPos(origin.getX()+x,y,origin.getZ()+31),Direction.UP,CableTier.APC);
        for(int x=2;x<=17;x++) add(out,new BlockPos(origin.getX()+x,y,origin.getZ()+3),Direction.UP,CableTier.APC);
         for(int x=5;x<=17;x++) if(x!=bypassX&&x!=bypassX+1) add(out,new BlockPos(origin.getX()+x,y,origin.getZ()+16),Direction.UP,CableTier.APC);
        for(int x=7;x<=17;x++) add(out,new BlockPos(origin.getX()+x,y,origin.getZ()+18),Direction.UP,CableTier.APC);
        for(int x=7;x<=17;x++) add(out,new BlockPos(origin.getX()+x,y,origin.getZ()+21),Direction.UP,CableTier.APC);
        // Bring the added exhaust vent's DOWN-facing receiver within native cable reach.
        // Route the exhaust riser east; the north-facing alternative occupies the vent's exterior front.
        BlockPos exhaustCable=new BlockPos(origin.getX()+18,y,origin.getZ()+18);
        add(out,exhaustCable,Direction.UP,CableTier.APC);
        add(out,new BlockPos(exhaustCable.getX()+1,y+1,exhaustCable.getZ()),Direction.WEST,CableTier.APC);
        add(out,new BlockPos(exhaustCable.getX()+1,y+2,exhaustCable.getZ()),Direction.WEST,CableTier.APC);
        add(out,new BlockPos(exhaustCable.getX()+1,y+3,exhaustCable.getZ()),Direction.WEST,CableTier.APC);
        add(out,new BlockPos(exhaustCable.getX(),y+2,exhaustCable.getZ()),Direction.UP,CableTier.APC);
          add(out,heater.relative(Direction.WEST),Direction.EAST,CableTier.APC);
         for(int x=heater.getX()-origin.getX()-1;x<=17;x++) if(x!=bypassX&&x!=bypassX+1) add(out,new BlockPos(origin.getX()+x,y,origin.getZ()+heaterBusZ),Direction.UP,CableTier.APC);
         // The main APC bus routes around the second-heater cable's contact and edge-turn neighborhood.
         add(out,new BlockPos(origin.getX()+bypassX-1,y,origin.getZ()+heaterBusZ-1),Direction.UP,CableTier.APC);
         add(out,new BlockPos(origin.getX()+bypassX,y,origin.getZ()+heaterBusZ-1),Direction.UP,CableTier.APC);
         add(out,new BlockPos(origin.getX()+bypassX+1,y,origin.getZ()+heaterBusZ-1),Direction.UP,CableTier.APC);
         add(out,new BlockPos(origin.getX()+bypassX+2,y,origin.getZ()+heaterBusZ-1),Direction.UP,CableTier.APC);
        // Isolated MV input stub has no source; the APC output reaches only the second heater port.
         BlockPos auxApc=secondaryStartupApc(c);
         var auxiliaryPorts=PowerDeviceKind.APC.ports(auxApc,equipment(layout).secondaryStartupApcFacing());
         DevicePort auxOutput=auxiliaryPorts.stream().filter(p->p.tier()==CableTier.APC).findFirst().orElseThrow();
         DevicePort auxMvInput=auxiliaryPorts.stream().filter(p->p.tier()==CableTier.MV).findFirst().orElseThrow();
         var auxOutputContact=PowerTopology.deviceContacts(auxOutput).getFirst();
         add(out,auxOutputContact.host(),auxOutputContact.face(),CableTier.APC);
         Direction secondPowerPort=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList().get(1).facing().getCounterClockWise();
         BlockPos secondHeaterContact=secondHeater.relative(secondPowerPort);
         add(out,secondHeaterContact,secondPowerPort.getOpposite(),CableTier.APC);
        // A physically present, closed one-node MV network makes the disconnected input known;
        // it has no source and cannot charge the auxiliary battery.
          var auxiliaryMvContact=frontContact(auxMvInput);
          add(out,auxiliaryMvContact.host(),auxiliaryMvContact.face(),CableTier.MV);
         // The pressure pump's resolved APC port is on its local left (south while facing west).
         // Pick up that port with a side-mounted cable host, then rise to the existing floor bus.
         BlockPos pressurePump=layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP);
         BlockPos pumpSide=pressurePump.relative(Direction.SOUTH);
         add(out,pumpSide,Direction.NORTH,CableTier.APC);
          for(int py=pressurePump.getY();py>y;py--) add(out,new BlockPos(pumpSide.getX(),py,pumpSide.getZ()),Direction.NORTH,CableTier.APC);
          int pumpBusZ=pumpSide.getZ()-origin.getZ()-1;
          for(int x=pumpSide.getX()-origin.getX();x>=17;x--) add(out,new BlockPos(origin.getX()+x,y,origin.getZ()+pumpBusZ),Direction.UP,CableTier.APC);
          for(int z=pumpBusZ;z>=31;z--) add(out,new BlockPos(origin.getX()+17,y,origin.getZ()+z),Direction.UP,CableTier.APC);
        return List.copyOf(out);
    }
    private static void add(Set<CableFaceNode> out,BlockPos p,Direction f,CableTier tier){out.add(new CableFaceNode(p,f,tier));}
    static CableFaceNode frontContact(DevicePort port) {
        return PowerTopology.deviceContacts(port).stream().filter(contact->contact.face()==port.face().getOpposite())
                .findFirst().orElseThrow(()->new IllegalStateException("typed front cable contact missing for "+port));
    }

    /** Receivers as reported by the runtime profile resolver; no guessed ports are introduced here. */
    public static boolean receiversConnected(List<CableFaceNode> cables, DevicePort operatingApcOutput, Collection<DevicePort> actualPorts) {
        var d=actualPorts.stream().filter(p->p.tier()==CableTier.APC).toList();
        if(d.isEmpty()) return false;
        if(!operatingApcOutput.equals(new DevicePort(operatingApcOutput.device(),operatingApcOutput.face(),CableTier.APC))) return false;
        for(DevicePort port:d) if(cables.stream().noneMatch(n->com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceAdjacent(port,n))
                || !TegFixturePlan.connected(cables,operatingApcOutput,port)) return false;
        return true;
    }

    /** Dedicated battery-only island for the added startup heater; its MV port has only an isolated known stub. */
    public static boolean secondaryStartupCircuitValid(TegContinuousFuelPlan.Layout layout,List<CableFaceNode> cables) {
        if(layout==null||cables==null)return false;
        BlockPos core=layout.core(),aux=secondaryStartupApc(core);
        var heaters=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList();
        if(heaters.size()!=2||!heaters.get(1).position().equals(TegContinuousFuelPlan.auxiliaryHeaterPosition(core))
                ||heaters.get(1).facing()!=Direction.SOUTH)return false;
        var auxiliaryPorts=PowerDeviceKind.APC.ports(aux,equipment(layout).secondaryStartupApcFacing());
        DevicePort auxOutput=auxiliaryPorts.stream().filter(p->p.tier()==CableTier.APC).findFirst().orElse(null);
        DevicePort auxMvInput=auxiliaryPorts.stream().filter(p->p.tier()==CableTier.MV).findFirst().orElse(null);
        if(auxOutput==null||auxMvInput==null)return false;
        DevicePort secondHeater=new DevicePort(heaters.get(1).position(),heaters.get(1).facing().getCounterClockWise(),CableTier.APC);
        DevicePort operatingOutput=new DevicePort(equipment(layout).operatingApc(),Direction.EAST,CableTier.APC);
        return cables.stream().anyMatch(n->PowerTopology.deviceAdjacent(auxOutput,n))
                &&cables.stream().anyMatch(n->PowerTopology.deviceAdjacent(secondHeater,n))
                &&cables.stream().filter(n->n.tier()==CableTier.MV&&PowerTopology.deviceAdjacent(auxMvInput,n)).count()==1
                &&TegFixturePlan.connected(cables,auxOutput,secondHeater)
                &&!TegFixturePlan.connected(cables,operatingOutput,secondHeater)
                &&!TegFixturePlan.connected(cables,operatingOutput,auxOutput)
                &&!TegFixturePlan.connected(cables,auxMvInput,new DevicePort(equipment(layout).substation(),Direction.WEST,CableTier.MV));
    }
}
