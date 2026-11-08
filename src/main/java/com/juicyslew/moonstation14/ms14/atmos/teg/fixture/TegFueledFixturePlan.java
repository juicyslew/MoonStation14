package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import java.util.*;

/** Concrete hot/cold finite-loop reservation; inventories are declarations, not seeded state. */
public final class TegFueledFixturePlan {
    public static final int WIDTH=43, HEIGHT=6, DEPTH=37;
    private TegFueledFixturePlan() { }
    /** Proportional finite inventory allocation over one independently-owned loop's real component volumes. */
    public static List<Double> apportionFiniteMoles(double totalMoles,List<Double> componentVolumes) {
        if(!Double.isFinite(totalMoles)||totalMoles<=0||componentVolumes==null||componentVolumes.isEmpty())throw new IllegalArgumentException("positive finite loop inventory and component volumes required");
        double totalVolume=0;for(double volume:componentVolumes){if(!Double.isFinite(volume)||volume<=0)throw new IllegalArgumentException("component volume must be positive and finite");totalVolume+=volume;}
        if(!Double.isFinite(totalVolume)||totalVolume<=0)throw new IllegalArgumentException("invalid total loop volume");
        List<Double> result=new ArrayList<>(componentVolumes.size());double allocated=0;
        for(int i=0;i<componentVolumes.size();i++){double moles=i==componentVolumes.size()-1?totalMoles-allocated:totalMoles*(componentVolumes.get(i)/totalVolume);if(!Double.isFinite(moles)||moles<=0)throw new IllegalArgumentException("component allocation is not positive");result.add(moles);allocated+=moles;}
        return List.copyOf(result);
    }
    /** Conservative bound for independent GasMixture float storage of apportioned component seeds. */
    public static double floatSeedRoundingBound(List<Double> componentMoles) {
        if(componentMoles==null||componentMoles.isEmpty())throw new IllegalArgumentException("component seed moles required");
        double bound=0;for(double moles:componentMoles){if(!Double.isFinite(moles)||moles<=0)throw new IllegalArgumentException("component seed moles must be positive and finite");float stored=(float)moles;if(!Float.isFinite(stored))throw new IllegalArgumentException("component seed exceeds float range");bound+=Math.ulp(stored)*.5d;}return bound;
    }
    public static Layout layout(BlockPos core) {
        return layoutWithHeater(core, core.offset(-11,0,-2), Direction.NORTH, core.offset(-11,0,-3), true);
    }
    static Layout layoutWithHeater(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition) {
        return layoutWithHeater(core, actualHeaterPosition, actualFacing, heaterLabelPosition, false, 0);
    }
    static Layout layoutWithExternalCooling(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition) {
        return layoutWithHeater(core, actualHeaterPosition, actualFacing, heaterLabelPosition, false, 0, true);
    }
    static Layout continuousLayoutWithExternalCooling(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition) {
        return layoutWithHeater(core, actualHeaterPosition, actualFacing, heaterLabelPosition, false, 0, true, true);
    }
    static Layout layoutWithHeaterAtHotPickup(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition) {
        // Continuous-only adaptation: prior matched diagnostic measured the old lower-center
        // exchanger cell at 433 K while the same burner averaged 655 K. Put the ordinary single
        // exchanger in the upper burner layer; no heat source, exchanger coefficient, or TEG
        // conversion is changed. The finite demonstration keeps its original lower-layer layout.
        return layoutWithHeater(core, actualHeaterPosition, actualFacing, heaterLabelPosition, false, 2);
    }
    private static Layout layoutWithHeater(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition, boolean startupMvFeedback) {
        return layoutWithHeater(core, actualHeaterPosition, actualFacing, heaterLabelPosition, startupMvFeedback, 0);
    }
    private static Layout layoutWithHeater(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition, boolean startupMvFeedback, int hotExchangerYOffset) {
        return layoutWithHeater(core,actualHeaterPosition,actualFacing,heaterLabelPosition,startupMvFeedback,hotExchangerYOffset,false);
    }
    private static Layout layoutWithHeater(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition, boolean startupMvFeedback, int hotExchangerYOffset, boolean externalCooling) {
        return layoutWithHeater(core,actualHeaterPosition,actualFacing,heaterLabelPosition,startupMvFeedback,hotExchangerYOffset,externalCooling,false);
    }
    private static Layout layoutWithHeater(BlockPos core, BlockPos actualHeaterPosition, Direction actualFacing, BlockPos heaterLabelPosition, boolean startupMvFeedback, int hotExchangerYOffset, boolean externalCooling, boolean compactContinuousReturn) {
        Objects.requireNonNull(core);
        Objects.requireNonNull(actualHeaterPosition); Objects.requireNonNull(actualFacing); Objects.requireNonNull(heaterLabelPosition);
        if (!actualFacing.getAxis().isHorizontal()) throw new IllegalArgumentException("heater facing must be horizontal");
        TegFixturePlan.Layout electrical=TegFixturePlan.layout(core);
        if(hotExchangerYOffset!=0&&hotExchangerYOffset!=2)throw new IllegalArgumentException("unsupported hot pickup layer");
        BlockPos circ=electrical.northCirculator(), pump=core.offset(-8,0,-1), exchanger=core.offset(-16,hotExchangerYOffset,-1), heater=actualHeaterPosition;
        BlockPos coldCirc=electrical.southCirculator(),coldPump=core.offset(8,0,1),coldExchanger=core.offset(16,0,1);
          List<Machine> machines=new ArrayList<>(List.of(new Machine(circ,Direction.EAST,MachineKind.CIRCULATOR),new Machine(pump,Direction.EAST,compactContinuousReturn?MachineKind.PRESSURE_PUMP:MachineKind.PUMP),new Machine(exchanger,Direction.EAST,MachineKind.EXCHANGER),new Machine(heater,actualFacing,MachineKind.HEATER),new Machine(coldCirc,Direction.WEST,MachineKind.CIRCULATOR),new Machine(coldPump,Direction.WEST,MachineKind.PUMP))); if(compactContinuousReturn)machines.add(new Machine(core.offset(-9,0,-6),Direction.SOUTH,MachineKind.HEATER)); if(!externalCooling)machines.add(new Machine(coldExchanger,Direction.WEST,MachineKind.EXCHANGER));
        Set<BlockPos> room=new LinkedHashSet<>(); for(int x=-17;x<=-15;x++)for(int y=0;y<=2;y++)for(int z=-2;z<=0;z++)room.add(core.offset(x,y,z));
        Set<BlockPos> shell=new LinkedHashSet<>(); for(int x=-18;x<=-14;x++)for(int y=-1;y<=3;y++)for(int z=-3;z<=1;z++) if(x==-18||x==-14||y==-1||y==3||z==-3||z==1)shell.add(core.offset(x,y,z));
         Set<BlockPos> coldRoom=new LinkedHashSet<>();if(!externalCooling)for(int x=15;x<=17;x++)for(int y=0;y<=2;y++)for(int z=0;z<=2;z++)coldRoom.add(core.offset(x,y,z));
         Set<BlockPos> coldShell=new LinkedHashSet<>();if(!externalCooling)for(int x=14;x<=18;x++)for(int y=-1;y<=3;y++)for(int z=-1;z<=3;z++)if(x==14||x==18||y==-1||y==3||z==-1||z==3)coldShell.add(core.offset(x,y,z));
        Set<BlockPos> crossings=Set.of(core.offset(-14,0,-1),core.offset(-18,0,-1));
        List<BlockPos> hotPumpBranch=concat(line(core,-13,0,-1,-9,0,-1),List.of(core.offset(-15,0,-1)));
          List<BlockPos> hotExchangerBranch=compactContinuousReturn
                  ?polyline(core,new int[][]{{-17,hotExchangerYOffset,-1},{-17,0,-1},{-18,0,-1},{-19,0,-1},{-20,0,-1},{-20,0,-7},{3,0,-7},{3,0,-1},{1,0,-1}})
                  :polyline(core,new int[][]{{-17,hotExchangerYOffset,-1},{-18,hotExchangerYOffset,-1},{-19,hotExchangerYOffset,-1},{-19,hotExchangerYOffset,-4},{1,hotExchangerYOffset,-4},{1,hotExchangerYOffset,-1}});
        if(hotExchangerYOffset>0)hotPumpBranch=concat(hotPumpBranch,line(core,-15,0,-1,-15,hotExchangerYOffset,-1));
        List<List<BlockPos>> routes=List.of(line(core,-7,0,-1,-1,0,-1),hotPumpBranch,hotExchangerBranch);
        Set<BlockPos> exposed=new LinkedHashSet<>(); routes.forEach(exposed::addAll); exposed.removeAll(crossings);
        List<List<BlockPos>> componentRoutes=routes.stream().map(r->{List<BlockPos> all=new ArrayList<>(r);for(BlockPos x:crossings)if(x.equals(core.offset(-14,0,-1))&&r==routes.get(1)||x.equals(core.offset(-18,0,-1))&&r==routes.get(2))all.add(x);return List.copyOf(new LinkedHashSet<>(all));}).toList();
         Set<BlockPos> coldCrossings=externalCooling?Set.of(core.offset(1,0,1)):Set.of(core.offset(1,0,1),core.offset(14,0,1),core.offset(18,0,1));
        List<List<BlockPos>> coldRoutes=List.of(concat(List.of(core.offset(1,0,1)),line(core,2,0,1,7,0,1)),concat(concat(line(core,9,0,1,13,0,1),List.of(core.offset(15,0,1))),List.of()),polyline(core,new int[][]{{17,0,1},{18,0,1},{19,0,1},{20,0,1},{20,5,1},{-3,5,1},{-3,0,1},{-1,0,1}}));
         List<List<BlockPos>> coldComponents=externalCooling?TegContinuousCoolingPlan.layout(core).pipeComponents():coldRoutes.stream().map(r->{List<BlockPos> all=new ArrayList<>(r);for(BlockPos x:coldCrossings)if(x.equals(core.offset(1,0,1))&&r==coldRoutes.get(0)||x.equals(core.offset(14,0,1))&&r==coldRoutes.get(1)||x.equals(core.offset(18,0,1))&&r==coldRoutes.get(2))all.add(x);return List.copyOf(new LinkedHashSet<>(all));}).toList();
        Set<BlockPos> owned=new LinkedHashSet<>(exposed); owned.addAll(crossings); owned.addAll(room); owned.addAll(shell); machines.forEach(m->owned.add(m.position()));
         Set<BlockPos> coldExposed=new LinkedHashSet<>();if(externalCooling)coldComponents.forEach(coldExposed::addAll);else coldRoutes.forEach(coldExposed::addAll);coldExposed.removeAll(coldCrossings);
          Map<BlockPos,PresentationLabel> presentationSigns=new LinkedHashMap<>(signs(core));presentationSigns.remove(core.offset(-11,0,-3));if(externalCooling)presentationSigns.remove(core.offset(16,0,-2));presentationSigns.put(heaterLabelPosition,new PresentationLabel("STARTUP HEATER",actualFacing));
          Set<BlockPos> otherMachines=new HashSet<>();machines.stream().filter(m->m.kind()!=MachineKind.HEATER).forEach(m->otherMachines.add(m.position()));
         Set<BlockPos> carrierCells=new HashSet<>(owned); carrierCells.removeAll(room);carrierCells.removeAll(shell);carrierCells.removeAll(coldRoom);carrierCells.removeAll(coldShell);carrierCells.removeAll(machines.stream().map(Machine::position).toList());
         if(room.contains(heater)||shell.contains(heater)||coldRoom.contains(heater)||coldShell.contains(heater)||otherMachines.contains(heater)||carrierCells.contains(heater)||heaterLabelPosition.equals(heater)||otherMachines.contains(heaterLabelPosition)||carrierCells.contains(heaterLabelPosition)||room.contains(heaterLabelPosition)||shell.contains(heaterLabelPosition)||coldRoom.contains(heaterLabelPosition)||coldShell.contains(heaterLabelPosition)) throw new IllegalArgumentException("heater or heater label overlaps fixture geometry");
         owned.remove(core.offset(-11,0,-2)); owned.add(heater);
         Set<BlockPos> cableShared=Set.of(core.offset(1,0,1));Set<BlockPos> allOwned=new LinkedHashSet<>(owned);allOwned.addAll(coldExposed);allOwned.addAll(coldCrossings);allOwned.addAll(coldRoom);allOwned.addAll(coldShell);allOwned.addAll(presentationSigns.keySet());
        Set<BlockPos> sharedHostCollision=new HashSet<>(allOwned);sharedHostCollision.retainAll(electrical.cableHosts());
        if(!sharedHostCollision.equals(cableShared)) throw new IllegalStateException("unexpected electrical-host overlap: "+sharedHostCollision);
         Set<BlockPos> coldCrossingShell=new HashSet<>(coldCrossings);coldCrossingShell.removeAll(cableShared);
         if(!Collections.disjoint(exposed,shell)||!Collections.disjoint(coldExposed,coldShell)||!Collections.disjoint(machines.stream().map(Machine::position).toList(),shell)||!Collections.disjoint(machines.stream().map(Machine::position).toList(),coldShell)||!shell.containsAll(crossings)||!coldShell.containsAll(coldCrossingShell))throw new IllegalStateException("invalid room-shell geometry");
         List<PortPairing> pairings=new ArrayList<>(externalCooling
                 ?List.of(new PortPairing("hot-circulator",circ,"outlet",pump,"inlet"),new PortPairing("hot-pump",pump,"outlet",exchanger,"inlet"),new PortPairing("hot-exchanger",exchanger,"outlet",circ,"inlet"),new PortPairing("heater-branch",heater,"pipe",heater.relative(actualFacing.getOpposite()),"pipe"),new PortPairing("cold-circulator",coldCirc,"outlet",coldPump,"inlet"))
                 :List.of(new PortPairing("hot-circulator",circ,"outlet",pump,"inlet"),new PortPairing("hot-pump",pump,"outlet",exchanger,"inlet"),new PortPairing("hot-exchanger",exchanger,"outlet",circ,"inlet"),new PortPairing("heater-branch",heater,"pipe",heater.relative(actualFacing.getOpposite()),"pipe"),new PortPairing("cold-circulator",coldCirc,"outlet",coldPump,"inlet"),new PortPairing("cold-pump",coldPump,"outlet",coldExchanger,"inlet"),new PortPairing("cold-exchanger",coldExchanger,"outlet",coldCirc,"inlet")));
         if(compactContinuousReturn) { Machine extra=machines.stream().filter(m->m.kind()==MachineKind.HEATER&&!m.position().equals(heater)).findFirst().orElseThrow();pairings.add(new PortPairing("heater-branch",extra.position(),"pipe",extra.position().relative(extra.facing().getOpposite()),"pipe")); }
         return new Layout(core.immutable(),electrical,machines,room,shell,componentRoutes,exposed,crossings,owned,coldRoom,coldShell,coldComponents,coldExposed,coldCrossings,allOwned,cableShared,startupMvFeedback,
                    pairings,new Inventory(.1,1,293.15,12,293.15),presentationSigns);
    }
    /** Front-facing observation panels; sealed pipe-crossing faces remain steel. */
    public static Set<BlockPos> observationWindows(BlockPos core) {
        Set<BlockPos> windows=new LinkedHashSet<>();
        for(int x=-17;x<=-15;x++)for(int y=0;y<=2;y++)windows.add(core.offset(x,y,-3));
        for(int x=15;x<=17;x++)for(int y=0;y<=2;y++)windows.add(core.offset(x,y,-1));
        return Set.copyOf(windows);
    }
    /** Readable labels stand outside the chamber fronts and clear the heater interaction side. */
     public static Map<BlockPos,PresentationLabel> signs(BlockPos core) {
         return Map.of(core.offset(-16,0,-4),new PresentationLabel("HOT BURN",Direction.NORTH),core.offset(16,0,-2),new PresentationLabel("COOLANT CHAMBER",Direction.NORTH),core.offset(-11,0,-3),new PresentationLabel("STARTUP HEATER",Direction.NORTH),core.offset(0,0,-2),new PresentationLabel("TEG",Direction.NORTH));
    }
    public record PresentationLabel(String text,Direction readerFacing) { public PresentationLabel { if(text==null||text.isBlank()||text.split("\\n",-1).length>4||Arrays.stream(text.split("\\n",-1)).anyMatch(line->line.length()>15)||readerFacing==null||!readerFacing.getAxis().isHorizontal())throw new IllegalArgumentException("horizontal-facing sign text requires one to four lines of at most 15 characters"); } }
    /** Planned structural footprint whose complete ground support is required before admission. */
    public static Set<BlockPos> foundation(BlockPos origin) {
        Set<BlockPos> result=new LinkedHashSet<>();
        for(int x=0;x<48;x++)for(int z=0;z<48;z++)result.add(origin.offset(x,1,z));
        return Set.copyOf(result);
    }
    private static List<BlockPos> concat(List<BlockPos> a,List<BlockPos> b){List<BlockPos> r=new ArrayList<>(a);r.addAll(b);return r;}
    private static Set<BlockPos> intersection(Collection<BlockPos>a,Collection<BlockPos>b){Set<BlockPos>r=new HashSet<>(a);r.retainAll(b);return r;}
    private static List<BlockPos> line(BlockPos c,int x1,int y1,int z1,int x2,int y2,int z2){List<BlockPos> r=new ArrayList<>();int dx=Integer.compare(x2,x1),dy=Integer.compare(y2,y1),dz=Integer.compare(z2,z1);for(int x=x1,y=y1,z=z1;;x+=dx,y+=dy,z+=dz){r.add(c.offset(x,y,z));if(x==x2&&y==y2&&z==z2)break;}return r;}
    private static List<BlockPos> polyline(BlockPos c,int[][] points){LinkedHashSet<BlockPos> r=new LinkedHashSet<>();for(int i=1;i<points.length;i++)r.addAll(line(c,points[i-1][0],points[i-1][1],points[i-1][2],points[i][0],points[i][1],points[i][2]));return List.copyOf(r);}
    public enum MachineKind { CIRCULATOR,PUMP,PRESSURE_PUMP,EXCHANGER,HEATER }
    public record Machine(BlockPos position,Direction facing,MachineKind kind) { }
    public record PortPairing(String component,BlockPos first,String firstPort,BlockPos second,String secondPort) { }
    public record Inventory(double plasmaMoles,double oxygenMoles,double temperatureKelvin,double nitrogenMoles,double nitrogenKelvin) { }
    public record Layout(BlockPos core,TegFixturePlan.Layout electrical,List<Machine> machines,Set<BlockPos> roomInterior,Set<BlockPos> wallShell,List<List<BlockPos>> hotComponents,Set<BlockPos> exposedPipePositions,Set<BlockPos> sealedCrossings,Set<BlockPos> ownedCells,Set<BlockPos> coldRoomInterior,Set<BlockPos> coldWallShell,List<List<BlockPos>> coldComponents,Set<BlockPos> coldExposedPipePositions,Set<BlockPos> coldSealedCrossings,Set<BlockPos> allOwnedCells,Set<BlockPos> sharedCableHosts,boolean startupMvFeedback,List<PortPairing> pairings,Inventory inventory,Map<BlockPos,PresentationLabel> presentationSigns) {
         public Layout { core=core.immutable();machines=List.copyOf(machines);roomInterior=Set.copyOf(roomInterior);wallShell=Set.copyOf(wallShell);hotComponents=hotComponents.stream().map(List::copyOf).toList();exposedPipePositions=Set.copyOf(exposedPipePositions);sealedCrossings=Set.copyOf(sealedCrossings);ownedCells=Set.copyOf(ownedCells);coldRoomInterior=Set.copyOf(coldRoomInterior);coldWallShell=Set.copyOf(coldWallShell);coldComponents=coldComponents.stream().map(List::copyOf).toList();coldExposedPipePositions=Set.copyOf(coldExposedPipePositions);coldSealedCrossings=Set.copyOf(coldSealedCrossings);allOwnedCells=Set.copyOf(allOwnedCells);sharedCableHosts=Set.copyOf(sharedCableHosts);pairings=List.copyOf(pairings);presentationSigns=Map.copyOf(presentationSigns); }
         public boolean coldLoopGeometryComplete(){return true;}
         /** Full startup APC cable tree, retaining the canonical finite-charge TEG output island unchanged. */
         public List<CableFaceNode> combinedCables() {
             List<CableFaceNode> result=rawCombinedCables(this);
              if(!TegFueledFixturePlan.startupPowerValid(this,result)) throw new IllegalStateException("invalid fueled-fixture startup power topology: "+result.size());
             return result;
         }
          public boolean startupPowerValid(){return TegFueledFixturePlan.startupPowerValid(this,rawCombinedCables(this));}
         private static List<CableFaceNode> rawCombinedCables(Layout layout) {
              LinkedHashSet<CableFaceNode> out=new LinkedHashSet<>(layout.electrical.cables());
             BlockPos c=layout.core;
              for(int z=0;z>=-4;z--) add(out,c.offset(-5,-1,z),Direction.UP);
              if(layout.startupMvFeedback) {
                  for(int x=0;x>=-5;x--) out.add(new CableFaceNode(c.offset(x,-1,10),Direction.UP,CableTier.MV));
                  for(int z=9;z>=0;z--) out.add(new CableFaceNode(c.offset(-5,-1,z),Direction.UP,CableTier.MV));
              }
             for(int x=-5;x<=8;x++) add(out,c.offset(x,-1,-4),Direction.UP);
             for(int z=-3;z<=1;z++) add(out,c.offset(8,-1,z),Direction.UP);
              for(int x=-6;x>=-11;x--) add(out,c.offset(x,-1,-4),Direction.UP);
              for(int z=-3;z<=-2;z++) add(out,c.offset(-11,-1,z),Direction.UP);
             for(int z=-3;z<=-1;z++) add(out,c.offset(-8,-1,z),Direction.UP);
              add(out,c.offset(-8,0,-2),Direction.SOUTH);
               add(out,c.offset(-12,0,-2),Direction.EAST);
             add(out,c.offset(8,0,2),Direction.NORTH);
             return List.copyOf(out);
         }
         private static void add(Set<CableFaceNode> out,BlockPos pos,Direction face){out.add(new CableFaceNode(pos,face,CableTier.APC));}
    }
    public static boolean startupPowerValid(Layout p,List<CableFaceNode> cables){
        Machine heater=p.machines().stream().filter(m->m.kind()==MachineKind.HEATER).findFirst().orElseThrow();
        if(!heater.position().equals(p.core().offset(-11,0,-2))||heater.facing()!=Direction.NORTH)return false;
        var d=p.electrical().devices();
        List<DevicePort> startup=List.of(new DevicePort(d.startupApc(),Direction.WEST,CableTier.APC),new DevicePort(d.core(),Direction.WEST,CableTier.APC));
         List<DevicePort> receivers=new ArrayList<>(List.of(new DevicePort(p.core().offset(-8,0,-1),Direction.NORTH,CableTier.APC),new DevicePort(p.core().offset(8,0,1),Direction.SOUTH,CableTier.APC)));
         receivers.add(new DevicePort(heater.position(),Direction.WEST,CableTier.APC));
        if(receivers.stream().anyMatch(port->cables.stream().noneMatch(n->PowerTopology.deviceAdjacent(port,n))))return false;
        for(DevicePort port:receivers)if(!TegFixturePlan.connected(cables,startup.get(0),port))return false;
         if(!TegFixturePlan.connected(cables,startup.get(0),startup.get(1)))return false;
         if(p.startupMvFeedback()&&!TegFixturePlan.connected(cables,new DevicePort(d.substation(),Direction.WEST,CableTier.MV),new DevicePort(d.startupApc(),Direction.WEST,CableTier.MV)))return false;
        List<DevicePort> out=List.of(new DevicePort(d.core(),Direction.EAST,CableTier.HV),new DevicePort(d.substation(),Direction.EAST,CableTier.HV),new DevicePort(d.substation(),Direction.WEST,CableTier.MV),new DevicePort(d.outputApc(),Direction.WEST,CableTier.MV),new DevicePort(d.outputApc(),Direction.EAST,CableTier.APC),new DevicePort(d.load(),Direction.NORTH,CableTier.APC));
        for(int i:List.of(0,2,4))if(!TegFixturePlan.connected(cables,out.get(i),out.get(i+1)))return false;
        if(TegFixturePlan.connected(cables,startup.get(0),out.get(4))||TegFixturePlan.connected(cables,startup.get(0),out.get(5)))return false;
        Set<BlockPos> added=new HashSet<>();cables.stream().filter(n->!p.electrical().cables().contains(n)).forEach(n->added.add(n.host()));
        Set<BlockPos> conflicts=new HashSet<>(p.allOwnedCells());conflicts.retainAll(added);
         conflicts.remove(p.core().offset(1,0,1)); if(!conflicts.isEmpty())return false;
        int minX=p.core().getX()-21,maxX=p.core().getX()+21,minZ=p.core().getZ()-18,maxZ=p.core().getZ()+18;
          Set<CableFaceNode> walls=Set.of(new CableFaceNode(p.core().offset(-8,0,-2),Direction.SOUTH,CableTier.APC),new CableFaceNode(p.core().offset(-12,0,-2),Direction.EAST,CableTier.APC),new CableFaceNode(p.core().offset(8,0,2),Direction.NORTH,CableTier.APC));
         return cables.stream().filter(n->!p.electrical().cables().contains(n)).allMatch(n->(n.tier()==CableTier.APC&&(n.host().getY()==p.core().getY()-1&&n.face()==Direction.UP||walls.contains(n)))||(p.startupMvFeedback()&&n.tier()==CableTier.MV&&n.host().getY()==p.core().getY()-1&&n.face()==Direction.UP))
                &&added.stream().allMatch(h->h.getX()>=minX&&h.getX()<=maxX&&h.getY()>=p.core().getY()-1&&h.getY()<=p.core().getY()&&h.getZ()>=minZ&&h.getZ()<=maxZ)
                 &&cables.stream().filter(n->!p.electrical().cables().contains(n)).allMatch(n->TegFixturePlan.connected(cables,n.tier()==CableTier.MV?new DevicePort(d.substation(),Direction.WEST,CableTier.MV):startup.get(0),new DevicePort(n.host().relative(n.face()),n.face().getOpposite(),n.tier())));
    }
}
