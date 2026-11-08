package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePortDirections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/** Bounded geometry reservation for a continuously supplied TEG; it does not construct or seed a world. */
public final class TegContinuousFuelPlan {
    public static final int WIDTH=48, HEIGHT=9, DEPTH=48;
    /** Finite initial hot-loop seed is N2; operation adds burner exhaust, while the isolated cold array uses plasma. */
    public static final double CARRIER_MOLES=43d, CARRIER_TEMPERATURE_K=293.15d;
    public static final com.juicyslew.moonstation14.ms14.atmos.core.GasType HOT_CARRIER_GAS=com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN;
    public static final com.juicyslew.moonstation14.ms14.atmos.core.GasType COLD_CARRIER_GAS=com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA;
    private TegContinuousFuelPlan() { }

    public static Layout layout(BlockPos origin) {
        return layout(origin,null,null);
    }
    /** Layout overload for consumers that have resolved the mixer composition; supplied profiles are authoritative. */
    public static Layout layout(BlockPos origin,AtmosMachineProfile resolvedMixer) {
        return layout(origin,resolvedMixer,null);
    }
    /** Construction callers must supply both resolved machine profiles; the shorter overloads remain for pure geometry consumers. */
    public static Layout layout(BlockPos origin,AtmosMachineProfile resolvedMixer,AtmosMachineProfile resolvedRadiator) {
        Objects.requireNonNull(origin);
         BlockPos core=origin.offset(22,2,18);
         BlockPos heater=core.offset(-11,0,-2), heaterLabel=core.offset(-12,0,-3);
         BlockPos auxiliaryHeaterLabel=auxiliaryHeaterLabel(core);
         TegFueledFixturePlan.Layout base=TegFueledFixturePlan.continuousLayoutWithExternalCooling(core,heater,Direction.NORTH,heaterLabel);
         TegContinuousCoolingPlan.Layout cooling=TegContinuousCoolingPlan.layout(core);
        Set<BlockPos> roomA=room(origin,1,3), roomB=room(origin,9,3);
        Set<BlockPos> shellA=shell(origin,1,3), shellB=shell(origin,9,3);
         Map<BlockPos,TegFueledFixturePlan.PresentationLabel> signs=new LinkedHashMap<>(base.presentationSigns());
          // BURN CHAMBER already labels this room; the inherited duplicate would
          // collide with the compact continuous hot-return route.
          signs.remove(p(origin,6,2,14));
         signs.put(heaterLabel,new TegFueledFixturePlan.PresentationLabel("STARTUP HEATER\nPAID PREHEAT\nTARGET 590 K",Direction.NORTH));
         signs.put(auxiliaryHeaterLabel,new TegFueledFixturePlan.PresentationLabel("STARTUP HEATER\nPAID PREHEAT\nTARGET 590 K",Direction.SOUTH));
        signs.put(p(origin,1,2,1),new TegFueledFixturePlan.PresentationLabel("OXYGEN SOURCE\nO2 MINER\nNO REACTION",Direction.NORTH));
        signs.put(p(origin,11,2,1),new TegFueledFixturePlan.PresentationLabel("PLASMA SOURCE\nFUEL MINER\nNO REACTION",Direction.NORTH));
        signs.put(p(origin,5,2,12),new TegFueledFixturePlan.PresentationLabel("BURN CHAMBER\nO2+PLASMA\nCO2+HEAT",Direction.SOUTH));
        signs.put(p(origin,7,2,3),new TegFueledFixturePlan.PresentationLabel("MIXER\nO2+PLASMA\nN: O2 W: FUEL",Direction.NORTH));
         signs.remove(p(origin,12,2,12));
           signs.put(p(origin,12,2,13),new TegFueledFixturePlan.PresentationLabel("CLOSED PLASMA\nCOOLANT LOOP\nHEAT TO HX",Direction.WEST));
         signs.put(p(origin,38,2,25),new TegFueledFixturePlan.PresentationLabel("COOLANT ARRAY\n5 RADIATORS\nPRESSURE RETURN",Direction.NORTH));
        BlockPos scrubA=p(origin,2,2,3), scrubB=p(origin,10,2,3), minerA=p(origin,2,2,5), minerB=p(origin,10,2,5);
          BlockPos mixer=p(origin,6,2,4), injector=p(origin,6,2,16), waste=p(origin,7,2,18);
        Map<String,List<BlockPos>> routes=new LinkedHashMap<>();
        routes.put("oxygen",poly(origin,new int[][]{{2,2,2},{2,2,0},{6,2,0},{6,2,3}}));
        routes.put("plasma",poly(origin,new int[][]{{10,2,2},{10,2,1},{10,6,1},{5,6,1},{5,6,4},{5,2,4}}));
          routes.put("fuel",poly(origin,new int[][]{{6,2,5},{6,2,7},{6,4,7},{6,4,13},{6,2,13},{6,2,15}}));
         routes.put("waste",poly(origin,new int[][]{{7,2,19},{7,2,20},{5,2,20},{5,2,18}}));
         routes.put("vent",poly(origin,new int[][]{{18,3,17},{18,3,16},{18,4,16},{18,4,17}}));
        Set<BlockPos> pipePositions=new LinkedHashSet<>(); routes.values().forEach(pipePositions::addAll);
           Set<BlockPos> sealed=Set.of(p(origin,2,2,2),p(origin,10,2,2),injector.relative(Direction.NORTH),p(origin,7,2,19),p(origin,5,2,19));
         BlockPos radiator=core.offset(0,5,1);
        List<BlockPos> hotExchangers=List.of(core.offset(-16,0,-1),core.offset(-16,1,-1),core.offset(-16,2,-1));
        List<BlockPos> hotBranches=List.of(core.offset(-15,1,-1),core.offset(-15,2,-1),core.offset(-17,1,-1),core.offset(-17,2,-1));
         Set<BlockPos> machines=new LinkedHashSet<>(Set.of(scrubA,scrubB,minerA,minerB,mixer,injector,waste,hotExchangers.get(1),hotExchangers.get(2),TegContinuousPowerPlan.secondaryStartupApc(core))); machines.addAll(cooling.reservedDevicePositions());
         Set<BlockPos> outside=Set.of(p(origin,18,4,18));
        Set<BlockPos> wasteMountWall=new LinkedHashSet<>();
         for(int x=17;x<=19;x++)for(int y=4;y<=6;y++)if(!(x==18&&y==4))wasteMountWall.add(p(origin,x,y,17));
        if(pipePositions.size()!=routes.values().stream().mapToInt(List::size).sum()) throw new IllegalStateException("pipe routes overlap");
        Set<BlockPos> allMachines=new HashSet<>(machines); if(allMachines.size()!=machines.size()) throw new IllegalStateException("duplicate machine position");
        Set<BlockPos> bothRooms=new HashSet<>(roomA); if(!Collections.disjoint(bothRooms,roomB)) throw new IllegalStateException("source rooms overlap");
        Set<BlockPos> crossings=new HashSet<>(sealed); crossings.retainAll(pipePositions);
        if(!crossings.equals(sealed)) throw new IllegalStateException("sealed crossing is not a pipe segment");
        for(BlockPos pos:pipePositions) if(pos.getX()<origin.getX()||pos.getX()>=origin.getX()+WIDTH||pos.getY()<origin.getY()||pos.getY()>=origin.getY()+HEIGHT||pos.getZ()<origin.getZ()||pos.getZ()>=origin.getZ()+DEPTH) throw new IllegalStateException("pipe outside fixture bounds: "+pos);
        Set<BlockPos> baseMachinePositions=new HashSet<>();base.machines().forEach(m->baseMachinePositions.add(m.position()));
        Set<BlockPos> collision=new HashSet<>(machines);collision.retainAll(baseMachinePositions);
        if(!collision.isEmpty())throw new IllegalStateException("continuous machinery overlaps fueled machine: "+collision);
          Layout layout=new Layout(origin,core,base,roomA,roomB,shellA,shellB,machines,Map.copyOf(routes),sealed,outside,Set.copyOf(wasteMountWall),Map.copyOf(signs),
                     continuousDevices(scrubA,scrubB,minerA,minerB,mixer,injector,waste,hotExchangers,cooling,p(origin,18,4,18)),hotExchangers,hotBranches);
         layout.validateGeometry();
         validateHeaterSite(layout,heater,heaterLabel);
         if(resolvedMixer!=null) layout.validateMixer(resolvedMixer);
         if(resolvedRadiator!=null) { layout.validateRadiator(resolvedRadiator); layout.validateHotExchanger(resolvedRadiator); }
         return layout;
     }
     private static void validateHeaterSite(Layout layout,BlockPos heater,BlockPos label){
             BlockPos expected=layout.core().offset(-11,0,-2),front=heater.relative(Direction.NORTH),gas=heater.relative(Direction.SOUTH);
             if(!heater.equals(expected)||!label.equals(layout.core().offset(-12,0,-3)))throw new IllegalStateException("continuous heater site mismatch");
          var heaterMachines=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList();
          if(heaterMachines.size()!=2||heaterMachines.stream().noneMatch(m->m.position().equals(heater)&&m.facing()==Direction.NORTH)
                   ||heaterMachines.stream().noneMatch(m->m.position().equals(auxiliaryHeaterPosition(layout.core()))&&m.facing()==Direction.SOUTH))
               throw new IllegalStateException("continuous startup heater roles must occupy their distinct planned hot-loop components");
           if(!layout.fueled().hotComponents().get(1).contains(heater.relative(Direction.SOUTH))
                   ||!layout.fueled().hotComponents().get(2).contains(auxiliaryHeaterPosition(layout.core()).relative(Direction.NORTH)))
               throw new IllegalStateException("startup heater gas ports must attach to distinct existing hot components 1 and 2");
           Set<BlockPos> forbidden=new HashSet<>();forbidden.addAll(layout.roomA());forbidden.addAll(layout.roomB());forbidden.addAll(layout.shellA());forbidden.addAll(layout.shellB());forbidden.addAll(layout.fueled().wallShell());forbidden.addAll(layout.fueled().coldWallShell());forbidden.addAll(layout.machines());layout.routes().values().forEach(forbidden::addAll);forbidden.addAll(layout.fueled().sharedCableHosts());forbidden.addAll(layout.hotPipeHosts());forbidden.addAll(layout.fueled().coldExposedPipePositions());for(var component:layout.fueled().hotComponents())forbidden.addAll(component);for(var component:layout.fueled().coldComponents())forbidden.addAll(component);forbidden.add(layout.origin().offset(7,1,8));
          layout.fueled().machines().stream().filter(m->m.kind()!=TegFueledFixturePlan.MachineKind.HEATER).map(TegFueledFixturePlan.Machine::position).forEach(forbidden::add);
          forbidden.remove(gas);for(var machine:heaterMachines)forbidden.remove(machine.position().relative(machine.facing().getOpposite()));
          if(forbidden.contains(heater)||forbidden.contains(front)||forbidden.contains(label)||heater.equals(label)||front.equals(label))throw new IllegalStateException("continuous heater/control/sign intersects protected fixture geometry");
          BlockPos second=auxiliaryHeaterPosition(layout.core()),secondFront=second.relative(Direction.SOUTH),secondLabel=auxiliaryHeaterLabel(layout.core());
          if(forbidden.contains(second)||forbidden.contains(secondFront)||forbidden.contains(secondLabel)||second.equals(secondLabel)||secondFront.equals(secondLabel))throw new IllegalStateException("second startup heater/control/sign intersects protected fixture geometry");
           if(!layout.presentationSigns().containsKey(label)||!layout.presentationSigns().containsKey(secondLabel))throw new IllegalStateException("continuous heater presentation label mismatch");
          BlockPos auxiliaryApc=TegContinuousPowerPlan.secondaryStartupApc(layout.core());
          BlockPos auxiliaryOutput=auxiliaryApc.relative(Direction.EAST),auxiliaryMv=auxiliaryApc.relative(Direction.WEST);
          BlockPos auxiliaryHeaterContact=second.relative(Direction.SOUTH.getCounterClockWise());
          forbidden.remove(auxiliaryApc);
          Set<BlockPos> auxiliaryPowerGeometry=Set.of(auxiliaryApc,auxiliaryOutput,auxiliaryMv,auxiliaryHeaterContact);
          if(auxiliaryPowerGeometry.size()!=4||auxiliaryPowerGeometry.stream().anyMatch(forbidden::contains))
              throw new IllegalStateException("isolated auxiliary APC, contacts, or heater control geometry intersects protected fixture hosts");
      }
      static BlockPos auxiliaryHeaterPosition(BlockPos core){return core.offset(-9,0,-6);}
      static BlockPos auxiliaryHeaterLabel(BlockPos core){return core.offset(-9,0,-8);}
    private static Set<BlockPos> room(BlockPos o,int x,int z){Set<BlockPos>s=new LinkedHashSet<>();for(int dx=0;dx<3;dx++)for(int y=2;y<=3;y++)for(int dz=0;dz<3;dz++)s.add(p(o,x+dx,y,z+dz));return Set.copyOf(s);}
    public static Set<BlockPos> sourceObservationWindows(BlockPos origin){Set<BlockPos>windows=new LinkedHashSet<>();for(int y=2;y<=3;y++){for(int x:new int[]{1,3,9,11})windows.add(p(origin,x,y,2));for(int x:new int[]{0,4,8,12})windows.add(p(origin,x,y,4));}windows.removeAll(Set.of(p(origin,2,2,2),p(origin,10,2,2)));return Set.copyOf(windows);}
    private static Set<BlockPos> shell(BlockPos o,int x,int z){Set<BlockPos>s=new LinkedHashSet<>();for(int dx=-1;dx<=3;dx++)for(int y=1;y<=4;y++)for(int dz=-1;dz<=3;dz++)if(dx==-1||dx==3||y==1||y==4||dz==-1||dz==3)s.add(p(o,x+dx,y,z+dz));return Set.copyOf(s);}
    private static BlockPos p(BlockPos o,int x,int y,int z){return o.offset(x,y,z);}
    private static List<BlockPos> poly(BlockPos o,int[][] pts){LinkedHashSet<BlockPos> r=new LinkedHashSet<>();for(int i=1;i<pts.length;i++){int[]a=pts[i-1],b=pts[i];if((a[0]!=b[0]?1:0)+(a[1]!=b[1]?1:0)+(a[2]!=b[2]?1:0)!=1)throw new IllegalArgumentException("route waypoints must be axis-aligned");int dx=Integer.compare(b[0],a[0]),dy=Integer.compare(b[1],a[1]),dz=Integer.compare(b[2],a[2]);for(int x=a[0],y=a[1],z=a[2];;x+=dx,y+=dy,z+=dz){r.add(p(o,x,y,z));if(x==b[0]&&y==b[1]&&z==b[2])break;}}return List.copyOf(r);}
    public record Port(AtmosMachineProfile.PortName name,BlockPos body,Direction facing,Direction port) { }
     private static List<Device> continuousDevices(BlockPos a,BlockPos b,BlockPos oa,BlockPos ob,BlockPos mixer,BlockPos injector,BlockPos waste,List<BlockPos> hot,TegContinuousCoolingPlan.Layout cooling,BlockPos vent){List<Device> d=new ArrayList<>(List.of(new Device(DeviceRole.SCRUBBER,a,Direction.SOUTH),new Device(DeviceRole.SCRUBBER,b,Direction.SOUTH),new Device(DeviceRole.GAS_MINER_OXYGEN,oa,Direction.SOUTH),new Device(DeviceRole.GAS_MINER_PLASMA,ob,Direction.SOUTH),new Device(DeviceRole.MIXER,mixer,Direction.NORTH),new Device(DeviceRole.INJECTOR,injector,Direction.SOUTH),new Device(DeviceRole.WASTE_PUMP,waste,Direction.NORTH)));for(var r:cooling.radiators())d.add(new Device(DeviceRole.PASSIVE_HEAT_EXCHANGER,r.position(),r.facing()));d.add(new Device(DeviceRole.COLD_PRESSURE_PUMP,cooling.pressurePump().position(),cooling.pressurePump().facing()));d.add(new Device(DeviceRole.HOT_HEAT_EXCHANGER,hot.get(1),Direction.EAST));d.add(new Device(DeviceRole.HOT_HEAT_EXCHANGER,hot.get(2),Direction.EAST));d.add(new Device(DeviceRole.VENT,vent,Direction.SOUTH));return List.copyOf(d);}
      public enum DeviceRole { SCRUBBER,GAS_MINER_OXYGEN,GAS_MINER_PLASMA,MIXER,INJECTOR,WASTE_PUMP,PASSIVE_HEAT_EXCHANGER,COLD_PRESSURE_PUMP,HOT_HEAT_EXCHANGER,VENT;
          public boolean isAtmosMachine(){return switch(this){case MIXER,PASSIVE_HEAT_EXCHANGER,HOT_HEAT_EXCHANGER,COLD_PRESSURE_PUMP->true;default->false;};}
      }
    public record Device(DeviceRole role,BlockPos position,Direction facing) { }
     public record Layout(BlockPos origin,BlockPos core,TegFueledFixturePlan.Layout fueled,Set<BlockPos> roomA,Set<BlockPos> roomB,Set<BlockPos> shellA,Set<BlockPos> shellB,Set<BlockPos> machines,Map<String,List<BlockPos>> routes,Set<BlockPos> sealedCrossings,Set<BlockPos> outsideVent,Set<BlockPos> wasteMountWall,Map<BlockPos,TegFueledFixturePlan.PresentationLabel> presentationSigns,List<Device> devices,List<BlockPos> hotExchangers,List<BlockPos> hotBranches) {
          public Layout { roomA=Set.copyOf(roomA);roomB=Set.copyOf(roomB);shellA=Set.copyOf(shellA);shellB=Set.copyOf(shellB);machines=Set.copyOf(machines);routes=Map.copyOf(routes);sealedCrossings=Set.copyOf(sealedCrossings);outsideVent=Set.copyOf(outsideVent);wasteMountWall=Set.copyOf(wasteMountWall);presentationSigns=Map.copyOf(presentationSigns);devices=List.copyOf(devices);hotExchangers=List.copyOf(hotExchangers);hotBranches=List.copyOf(hotBranches); }
          public BlockPos devicePosition(DeviceRole role){return devices.stream().filter(device->device.role()==role).map(Device::position).reduce((a,b)->{throw new IllegalStateException("expected one device for role "+role);}).orElseThrow(()->new IllegalStateException("missing device role "+role));}
          public List<BlockPos> devicePositions(DeviceRole role){return devices.stream().filter(device->device.role()==role).map(Device::position).toList();}
        public List<Port> mixerPorts(AtmosMachineProfile profile){if(profile.kind()!=AtmosMachineProfile.Kind.MIXER)throw new IllegalArgumentException("resolved mixer profile required");BlockPos m=origin.offset(6,2,4);Direction facing=devices.stream().filter(d->d.role()==DeviceRole.MIXER).findFirst().orElseThrow().facing();return profile.ports().stream().map(p->new Port(p.name(),m,facing,AtmosMachinePortDirections.worldDirection(p.direction(),facing))).toList();}
          public List<Port> radiatorPorts(AtmosMachineProfile profile){if(profile.kind()!=AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER)throw new IllegalArgumentException("resolved passive heat exchanger profile required");return devices.stream().filter(d->d.role()==DeviceRole.PASSIVE_HEAT_EXCHANGER).flatMap(device->profile.ports().stream().map(p->new Port(p.name(),device.position(),device.facing(),AtmosMachinePortDirections.worldDirection(p.direction(),device.facing())))).toList();}
         public List<List<Port>> hotExchangerPorts(AtmosMachineProfile profile){if(profile.kind()!=AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER)throw new IllegalArgumentException("resolved passive heat exchanger profile required");return devices.stream().filter(d->d.role()==DeviceRole.HOT_HEAT_EXCHANGER).map(d->profile.ports().stream().map(p->new Port(p.name(),d.position(),d.facing(),AtmosMachinePortDirections.worldDirection(p.direction(),d.facing()))).toList()).toList();}
         /** Full physical hot carrier paths, extended from the finite plan's three separate loops. */
         public List<Set<BlockPos>> hotPipeComponents(){
             var source=fueled.hotComponents();if(source.size()!=3)throw new IllegalStateException("expected three base hot components");
             List<Set<BlockPos>> result=new ArrayList<>();for(var component:source)result.add(new LinkedHashSet<>(component));
              result.get(0).addAll(routes.get("vent"));
              result.get(1).add(hotBranches.get(0));result.get(1).add(hotBranches.get(1));
              result.get(2).add(hotBranches.get(2));result.get(2).add(hotBranches.get(3));result.get(2).addAll(routes.get("waste"));
             Set<BlockPos> all=new HashSet<>();for(var component:result){if(!connected(component)||!Collections.disjoint(all,component))throw new IllegalStateException("hot carrier component branch is disconnected or aliased");all.addAll(component);}
             return result.stream().map(Set::copyOf).toList();
         }
          public Set<BlockPos> hotPipeHosts(){Set<BlockPos> result=new LinkedHashSet<>();hotPipeComponents().forEach(result::addAll);result.removeAll(sealedCrossings);result.removeAll(fueled.sealedCrossings());result.remove(origin.offset(18,4,17));return Set.copyOf(result);}
         public Map<BlockPos,Set<Direction>> hotPipeConnections(){Set<BlockPos> hosts=hotPipeHosts();Map<BlockPos,Set<Direction>> result=new LinkedHashMap<>();for(BlockPos host:hosts){Set<Direction> directions=EnumSet.noneOf(Direction.class);for(Direction direction:Direction.values())if(hosts.contains(host.relative(direction)))directions.add(direction);result.put(host,Set.copyOf(directions));}return Map.copyOf(result);}
         public void validateHotExchanger(AtmosMachineProfile profile){
             var ports=hotExchangerPorts(profile);if(ports.size()!=2)throw new IllegalArgumentException("exactly two continuous hot exchanger profiles required");
             for(int i=0;i<ports.size();i++){
                 Map<AtmosMachineProfile.PortName,Port> byName=new EnumMap<>(AtmosMachineProfile.PortName.class);for(var port:ports.get(i))if(byName.put(port.name(),port)!=null)throw new IllegalArgumentException("duplicate hot exchanger port name");
                 if(byName.size()!=2||!byName.containsKey(AtmosMachineProfile.PortName.INLET)||!byName.containsKey(AtmosMachineProfile.PortName.OUTLET))throw new IllegalArgumentException("resolved hot exchanger inlet/outlet ports required");
                 var inlet=byName.get(AtmosMachineProfile.PortName.INLET);var outlet=byName.get(AtmosMachineProfile.PortName.OUTLET);BlockPos body=hotExchangers.get(i+1);
                 if(!body.equals(inlet.body())||!body.equals(outlet.body())||inlet.facing()!=Direction.EAST||inlet.port()!=Direction.EAST||outlet.port()!=Direction.WEST)throw new IllegalArgumentException("hot exchanger ports must face feed-east and return-west");
                 if(!hotPipeComponents().get(1).contains(body.relative(Direction.EAST))||!hotPipeComponents().get(2).contains(body.relative(Direction.WEST)))throw new IllegalStateException("hot exchanger must bridge only hot components 1 and 2");
             }
         }
        public void validateRadiator(AtmosMachineProfile profile){if(profile.kind()!=AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER||profile.ports().size()!=2||!profile.ports().stream().map(AtmosMachineProfile.Port::name).collect(java.util.stream.Collectors.toSet()).equals(Set.of(AtmosMachineProfile.PortName.INLET,AtmosMachineProfile.PortName.OUTLET)))throw new IllegalArgumentException("resolved exchanger inlet/outlet ports required");var ports=radiatorPorts(profile);var components=coldPipeComponents();for(BlockPos body:radiatorPositions()){var own=ports.stream().filter(p->p.body().equals(body)).toList();Map<AtmosMachineProfile.PortName,Direction> dirs=new EnumMap<>(AtmosMachineProfile.PortName.class);own.forEach(p->dirs.put(p.name(),p.port()));if(dirs.size()!=2||!Set.of(dirs.get(AtmosMachineProfile.PortName.INLET),dirs.get(AtmosMachineProfile.PortName.OUTLET)).equals(Set.of(Direction.WEST,Direction.EAST)))throw new IllegalArgumentException("resolved radiator ports must oppose along X");int west=-1,east=-1;for(int i=0;i<components.size();i++){if(components.get(i).contains(body.west()))west=i;if(components.get(i).contains(body.east()))east=i;}if(west<0||east<0||west==east||components.stream().anyMatch(c->c.contains(body)))throw new IllegalStateException("radiator endpoints must be pipe hosts in distinct cold components");}}
        /** Pipe-only cold-loop hosts for continuous construction. Never use fueled().coldExposedPipePositions() directly. */
         public Set<BlockPos> coldExposedPipes(){Set<BlockPos> result=new LinkedHashSet<>(fueled.coldExposedPipePositions());coldPipeComponents().forEach(result::addAll);result.removeAll(radiatorPositions());result.removeAll(sealedCrossings);return Set.copyOf(result);}
        /** Four physical pressure components: the finite plan's first two routes and the third route split around the inline radiator. */
        public List<List<BlockPos>> coldPipeComponents(){
            List<List<BlockPos>> added=TegContinuousCoolingPlan.layout(core).pipeComponents();if(added.size()!=4)throw new IllegalStateException("expected four external cold components");
             List<List<BlockPos>> result=new ArrayList<>(added);
             Set<BlockPos> covered=new HashSet<>();for(List<BlockPos> component:result){if(!connected(component))throw new IllegalStateException("disconnected continuous cold component");for(BlockPos pos:component)if(!covered.add(pos))throw new IllegalStateException("duplicate continuous cold pipe host: "+pos);}
              Set<BlockPos> expected=new HashSet<>();for(List<BlockPos> component:added)expected.addAll(component);if(!covered.equals(expected))throw new IllegalStateException("continuous cold plan lost or added pipe hosts");
             return result;
        }
        static List<List<BlockPos>> splitReturnComponent(Collection<BlockPos> members,BlockPos body){Set<BlockPos> allowed=new LinkedHashSet<>(members);if(!allowed.remove(body))throw new IllegalStateException("radiator body missing from cold return");Set<BlockPos> west=reachable(allowed,body.west()),east=reachable(allowed,body.east());if(west.isEmpty()||east.isEmpty()||!Collections.disjoint(west,east)){throw new IllegalStateException("radiator neighbors do not seed distinct return components");}Set<BlockPos> union=new HashSet<>(west);union.addAll(east);if(!union.equals(allowed))throw new IllegalStateException("cold return has disconnected or unassigned pipe hosts");return List.of(List.copyOf(west),List.copyOf(east));}
        private static boolean connected(Collection<BlockPos> nodes){if(nodes.isEmpty())return false;return reachable(new HashSet<>(nodes),nodes.iterator().next()).size()==new HashSet<>(nodes).size();}
        private static Set<BlockPos> reachable(Set<BlockPos> allowed,BlockPos seed){if(!allowed.contains(seed))return Set.of();Set<BlockPos> visited=new LinkedHashSet<>();ArrayDeque<BlockPos> pending=new ArrayDeque<>();pending.add(seed);visited.add(seed);while(!pending.isEmpty()){BlockPos current=pending.removeFirst();for(Direction direction:Direction.values()){BlockPos next=current.relative(direction);if(allowed.contains(next)&&visited.add(next))pending.addLast(next);}}return visited;}
        public void validateMixer(AtmosMachineProfile profile){var expected=Map.of(AtmosMachineProfile.PortName.INLET,Direction.NORTH,AtmosMachineProfile.PortName.FILTER,Direction.WEST,AtmosMachineProfile.PortName.OUTLET,Direction.SOUTH);var ports=mixerPorts(profile);Map<AtmosMachineProfile.PortName,Direction> actual=new EnumMap<>(AtmosMachineProfile.PortName.class);ports.forEach(p->actual.put(p.name(),p.port()));if(!actual.equals(expected))throw new IllegalArgumentException("resolved mixer ports do not match fixture connections");BlockPos mixer=origin.offset(6,2,4);if(!routes.get("oxygen").getLast().equals(mixer.relative(Direction.NORTH))||!routes.get("plasma").getLast().equals(mixer.relative(Direction.WEST))||!routes.get("fuel").getFirst().equals(mixer.relative(Direction.SOUTH)))throw new IllegalStateException("routes do not reach resolved mixer port endpoints");}
         public void validateGeometry(){
             if(radiatorPositions().size()!=5||!radiatorPositions().equals(TegContinuousCoolingPlan.layout(core).radiators().stream().map(TegContinuousCoolingPlan.Device::position).collect(java.util.stream.Collectors.toSet()))) throw new IllegalStateException("radiator site mismatch");
               Set<BlockPos> expectedMountWall=new LinkedHashSet<>();for(int x=17;x<=19;x++)for(int y=4;y<=6;y++)if(x!=18||y!=4)expectedMountWall.add(origin.offset(x,y,17));
               if(!wasteMountWall.equals(expectedMountWall)||wasteMountWall.contains(origin.offset(18,4,17))||outsideVent.size()!=1||!outsideVent.contains(origin.offset(18,4,18))||!origin.offset(18,4,19).equals(origin.offset(18,4,18).relative(Direction.SOUTH)))throw new IllegalStateException("exterior vent wall mount geometry mismatch");
            for(var route:routes.values())for(int i=1;i<route.size();i++)if(route.get(i-1).distManhattan(route.get(i))!=1)throw new IllegalStateException("nonadjacent route nodes");
             List<List<BlockPos>> primaryRoutes=routes.entrySet().stream().filter(e->!e.getKey().equals("waste")&&!e.getKey().equals("vent")).map(Map.Entry::getValue).toList();
            for(int i=0;i<primaryRoutes.size();i++)for(int j=i+1;j<primaryRoutes.size();j++)assertSeparated(primaryRoutes.get(i),primaryRoutes.get(j),"new primary route adjacency");
            List<BlockPos> basePrimary=new ArrayList<>();fueled.hotComponents().forEach(basePrimary::addAll);fueled.coldComponents().forEach(basePrimary::addAll);
            for(List<BlockPos> route:primaryRoutes)assertSeparated(route,basePrimary,"base carrier adjacency");
              BlockPos injector=devices.stream().filter(d->d.role()==DeviceRole.INJECTOR).findFirst().orElseThrow().position();
              Set<BlockPos> walls=new HashSet<>(shellA);walls.addAll(shellB);walls.addAll(fueled.wallShell());Set<BlockPos> wallCrossings=new HashSet<>(walls);wallCrossings.retainAll(sealedCrossings);Set<BlockPos> intendedCrossings=Set.of(origin.offset(2,2,2),origin.offset(10,2,2),injector.relative(Direction.NORTH),origin.offset(7,2,19),origin.offset(5,2,19));if(!wallCrossings.equals(sealedCrossings)||!sealedCrossings.equals(intendedCrossings))throw new IllegalStateException("declared crossing misses intended room shell");
             if(!TegFueledFixturePlan.observationWindows(core).contains(injector.relative(Direction.NORTH)))throw new IllegalStateException("fuel route must replace the intended observation window");
            if(!machines.containsAll(devices.stream().filter(d->d.role()!=DeviceRole.VENT).map(Device::position).toList())||devices.stream().map(Device::position).distinct().count()!=devices.size())throw new IllegalStateException("device position mismatch/duplicate");
              Set<BlockPos> allPositions=new HashSet<>();allPositions.addAll(roomA);allPositions.addAll(roomB);allPositions.addAll(shellA);allPositions.addAll(shellB);allPositions.addAll(machines);allPositions.addAll(hotPipeHosts());allPositions.addAll(fueled.coldExposedPipePositions());fueled.machines().forEach(m->allPositions.add(m.position()));allPositions.addAll(fueled.sharedCableHosts());routes.values().forEach(allPositions::addAll);allPositions.addAll(outsideVent);allPositions.addAll(wasteMountWall);
              Set<BlockPos> signCollisions=new HashSet<>(presentationSigns.keySet());signCollisions.retainAll(allPositions);if(!signCollisions.isEmpty())throw new IllegalStateException("presentation sign intersects owned fixture block: "+signCollisions);
            for(BlockPos pos:allPositions)if(pos.getX()<origin.getX()||pos.getX()>=origin.getX()+WIDTH||pos.getY()<origin.getY()||pos.getY()>=origin.getY()+HEIGHT||pos.getZ()<origin.getZ()||pos.getZ()>=origin.getZ()+DEPTH)throw new IllegalStateException("geometry outside fixture bounds: "+pos);
             for(BlockPos pos:machines)if(fueled.machines().stream().anyMatch(m->m.position().equals(pos)))throw new IllegalStateException("machine overlaps base machine");
             if(hotExchangers.size()!=3||!hotExchangers.get(0).equals(core.offset(-16,0,-1))||!hotExchangers.get(1).equals(core.offset(-16,1,-1))||!hotExchangers.get(2).equals(core.offset(-16,2,-1))||hotBranches.size()!=4||!hotBranches.equals(List.of(core.offset(-15,1,-1),core.offset(-15,2,-1),core.offset(-17,1,-1),core.offset(-17,2,-1))))throw new IllegalStateException("three-contact hot pickup geometry mismatch");
             if(!machines.containsAll(hotExchangers.subList(1,3))||!fueled.roomInterior().containsAll(hotExchangers.subList(1,3)))throw new IllegalStateException("stacked hot exchanger bodies must occupy burner chamber");
             Set<BlockPos> hotShells=new HashSet<>(shellA);hotShells.addAll(shellB);hotShells.addAll(fueled.wallShell());hotShells.addAll(fueled.coldWallShell());
              Set<BlockPos> hotShellCollision=new HashSet<>(hotPipeHosts());hotShellCollision.retainAll(hotShells);if(!hotShellCollision.isEmpty())throw new IllegalStateException("hot carrier branch intersects a protected shell: "+hotShellCollision);
             if(fueled.roomInterior().size()!=27||!fueled.roomInterior().containsAll(hotExchangers))throw new IllegalStateException("parallel contacts must preserve the 27-cell burner gas volume");
             Set<BlockPos> branchSet=new HashSet<>(hotBranches);branchSet.retainAll(machines);if(!branchSet.isEmpty())throw new IllegalStateException("hot pipe branch overlaps machinery");
             Set<BlockPos> hotReserved=new HashSet<>(hotExchangers.subList(1,3));hotReserved.addAll(hotBranches);
             Set<BlockPos> cableCollision=new HashSet<>(hotReserved);cableCollision.retainAll(fueled.sharedCableHosts());if(!cableCollision.isEmpty())throw new IllegalStateException("hot-contact geometry collides with APC host");
             Set<BlockPos> controls=new HashSet<>(fueled.presentationSigns().keySet());controls.add(core.offset(-11,0,-3));hotReserved.retainAll(controls);if(!hotReserved.isEmpty())throw new IllegalStateException("hot-contact geometry collides with control/sign site");
             Set<BlockPos> bodyShells=new HashSet<>(hotExchangers.subList(1,3));bodyShells.retainAll(hotShells);if(!bodyShells.isEmpty())throw new IllegalStateException("stacked hot exchanger intersects protected shell");
             if(!hotExchangers.get(1).below().equals(hotExchangers.get(0))||!hotExchangers.get(2).below().equals(hotExchangers.get(1)))throw new IllegalStateException("stacked hot exchangers require lower exchanger support");
             hotPipeComponents();
         }
        /** Candidate sites only; a builder must independently validate the live exterior boundary. */
          public Set<BlockPos> radiatorPositions(){return TegContinuousCoolingPlan.layout(core).radiators().stream().map(TegContinuousCoolingPlan.Device::position).collect(java.util.stream.Collectors.toUnmodifiableSet());}
         /** Steel host behind the sealed waste-pipe mount; surrounding wall panels remain unchanged. */
         public BlockPos wasteMountHost(){return origin.offset(18,4,17);}
        private static void assertSeparated(List<BlockPos>a,List<BlockPos>b,String description){for(BlockPos x:a)for(BlockPos y:b)if(x.distManhattan(y)<=1)throw new IllegalStateException(description+": "+x+" / "+y);}
         /** The three wall-penetrating hosts are sealed; routed open hosts are in routes(), not here. */
         public List<PipeTopology.Segment> sealedRouteSegments(){return List.of(segment(origin.offset(7,2,19),Direction.NORTH,Direction.SOUTH,PipeTopology.Kind.STRAIGHT_SEALED_WALL,PipeTopology.Layer.PRIMARY),segment(origin.offset(5,2,19),Direction.NORTH,Direction.SOUTH,PipeTopology.Kind.STRAIGHT_SEALED_WALL,PipeTopology.Layer.PRIMARY),segment(wasteMountHost(),Direction.NORTH,Direction.SOUTH,PipeTopology.Kind.STRAIGHT_SEALED_WALL,PipeTopology.Layer.PRIMARY));}
         public List<PipeTopology.Segment> wasteSegments(){return sealedRouteSegments();}
         public BlockPos wasteVentBody(){return origin.offset(18,4,18);}
        public Direction wasteVentFacing(){return Direction.SOUTH;}
        private static PipeTopology.Segment segment(BlockPos p,Direction a,Direction b,PipeTopology.Kind kind,PipeTopology.Layer layer){return new PipeTopology.Segment(p,1,EnumSet.of(a,b),kind,layer);}
    }
}
