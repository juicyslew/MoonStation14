package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.disposal.domain.*;
import com.juicyslew.moonstation14.ms14.disposal.prototype.*;
import com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting;
import com.juicyslew.moonstation14.ms14.disposal.unit.*;
import com.juicyslew.moonstation14.ms14.disposal.world.*;
import com.juicyslew.moonstation14.ms14.power.cable.*;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/** Explicit, bounded admin demo of the resolved unit, power graph, finite room and real pneumatic route. */
public final class DisposalFixtureCommands {
     private static final int W=12,H=7,D=12;
     private static final class TourHolder {
         private static final DisposalFixtureTourPlan.Plan TOUR = DisposalFixtureTourPlan.resolve(PrototypeRuntime.serverDisposals());
     }
     private static DisposalFixtureTourPlan.Plan tour(){return TourHolder.TOUR;}
     static final int ROOM_MIN_X=2,ROOM_MAX_X=7,ROOM_MIN_Y=1,ROOM_MAX_Y=4,ROOM_MIN_Z=7,ROOM_MAX_Z=10;
    private static final BlockPos UNIT=new BlockPos(3,2,8);
    static final BlockPos LAMP=new BlockPos(3,3,7);
    static final BlockPos ENTRY_CELL=new BlockPos(6,1,8);
     private static List<DisposalFixtureTourPlan.Node> route(){return tour().nodes();}
    private static final Map<net.minecraft.server.MinecraftServer,DisposalFixtureSiteContext> SITES=Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean stopListenerRegistered;
    private DisposalFixtureCommands() {}
    public static void registerCommands(RegisterCommandsEvent event){
        if(!stopListenerRegistered){NeoForge.EVENT_BUS.addListener(DisposalFixtureCommands::serverStopped);stopListenerRegistered=true;}
        var fixture = Commands.literal("disposals_fixture");
        fixture.then(action("build", 0));
        fixture.then(action("initialize", 1));
        fixture.then(action("status", 2));
        fixture.then(action("enter", 3));
        event.getDispatcher().register(Commands.literal("ms14").requires(s->s.hasPermission(2)).then(fixture));
    }
    private static void serverStopped(ServerStoppedEvent event){SITES.remove(event.getServer());}
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> action(String name, int action) {
        var command = Commands.literal(name);
        command.executes(context -> run(context.getSource(), null, action));
        var origin = Commands.argument("origin", BlockPosArgument.blockPos());
        origin.executes(context -> run(context.getSource(), BlockPosArgument.getBlockPos(context, "origin"), action));
        command.then(origin);
        return command;
    }
    private static int run(net.minecraft.commands.CommandSourceStack s,BlockPos requested,int action){
        UUID operator=s.getEntity()==null?new UUID(0,0):s.getEntity().getUUID();
        var sites=SITES.computeIfAbsent(s.getServer(),ignored->new DisposalFixtureSiteContext());
        BlockPos entry=requested;
         if(entry==null&&action==0){if(s.getEntity()==null){s.sendFailure(Component.literal("Console must supply floor reference X Y Z for build."));return 0;}var auth=s.getPlayer()==null?null:HandActorAuthority.resolve(s.getPlayer()).orElse(null);entry=auth==null?s.getEntity().blockPosition():auth.body().blockPosition();}
        if(entry==null)entry=sites.find(operator,s.getLevel().dimension());
         if(entry==null){s.sendFailure(Component.literal("No fixture site remembered. Supply floor reference X Y Z, or build at your feet first."));return 0;}
         var before=s.getPlayer()==null?null:HandActorAuthority.resolve(s.getPlayer()).orElse(null);
         boolean bodyAtReference=before!=null&&before.body().level()==s.getLevel()&&before.body().blockPosition().equals(entry);
         String r=action==0?build(s.getLevel(),entry):action==1?initialize(s.getLevel(),entry):action==2?status(s.getLevel(),entry):enter(s.getPlayer(),s.getLevel(),entry);
         if(action==0&&r.startsWith("Built from floor reference")&&bodyAtReference)r+="; "+enter(s.getPlayer(),s.getLevel(),entry);
         if(action==0)sites.buildResult(operator,s.getLevel().dimension(),entry,r.startsWith("Built from floor reference "));
         String result=r;s.sendSuccess(()->Component.literal(result),false);return 1;
    }
     private static BlockPos cornerFromFloorReference(BlockPos reference){return reference.offset(-6,0,-8);}
     private static net.minecraft.world.phys.Vec3 entryFromFloorReference(BlockPos reference){return new net.minecraft.world.phys.Vec3(reference.getX()+.5,reference.getY()+1,reference.getZ()+.5);}
     static BlockPos cornerForEntry(BlockPos reference){return cornerFromFloorReference(reference);}
    public static String build(ServerLevel l,BlockPos entry){
        BlockPos o=cornerFromFloorReference(entry);
         if(!prototypesValid())return "Refused without changes: resolved fixture prototypes unavailable.";
         Set<BlockPos> required=new HashSet<>(tour().obstacleFootprint()); for(var n:route())required.add(n.position());
         for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++)required.add(new BlockPos(x,y,z));
         for(BlockPos local:required){BlockPos p=o.offset(local);if(p.getY()<l.getMinBuildHeight()||p.getY()>=l.getMaxBuildHeight()||!l.getWorldBorder().isWithinBounds(p)||l.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return "Refused, no changes made: complete fixture must be in bounds and FULL-loaded; "+bounds(o);}
          for(BlockPos local:required)if(!l.getBlockState(o.offset(local)).isAir()||l.getBlockEntity(o.offset(local))!=null)return "Refused, no changes made: room and sparse route footprint must be clear; blocked "+o.offset(local);
         for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++)l.setBlock(o.offset(x,y,z),ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
         for(BlockPos p:tour().obstacleFootprint())l.setBlock(o.offset(p),ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
        for(int x=ROOM_MIN_X;x<=ROOM_MAX_X;x++)for(int y=ROOM_MIN_Y;y<=ROOM_MAX_Y;y++)for(int z=ROOM_MIN_Z;z<=ROOM_MAX_Z;z++)
            l.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
         putPower(l,o.offset(3,1,2),PowerDeviceKind.HV_SOURCE,Direction.EAST);putPower(l,o.offset(3,1,4),PowerDeviceKind.HV_MV_SUBSTATION,Direction.EAST);
        // Cables are face-mounted records on solid hosts, matching DisposalPowerGameTests.
        for(int z=2;z<=8;z++)l.setBlock(o.offset(4,1,z),Blocks.STONE.defaultBlockState(),3);
        // APC-tier drop on the existing post row feeds a real 100 W lamp above the unit.
        for(int y=2;y<=3;y++)l.setBlock(o.offset(4,y,7),Blocks.STONE.defaultBlockState(),3);
         for(int z=4;z<=6;z++)l.setBlock(o.offset(2,1,z),Blocks.STONE.defaultBlockState(),3);
         if(!PowerDeviceFixturePlacement.placeApc(l,o.offset(3,1,6),Direction.WEST))return partial(o,"APC sturdy support/placement");
        for(int z=2;z<=4;z++)if(!CableStorage.place(l,o.offset(4,1,z),Direction.WEST,CableTier.HV))return partial(o,"HV cable");
        for(int z=4;z<=6;z++)if(!CableStorage.place(l,o.offset(2,1,z),Direction.EAST,CableTier.MV))return partial(o,"MV cable");
        for(int z=6;z<=8;z++)if(!CableStorage.place(l,o.offset(4,1,z),Direction.WEST,CableTier.APC))return partial(o,"APC cable");
        for(int y=2;y<=3;y++)if(!CableStorage.place(l,o.offset(4,y,7),Direction.WEST,CableTier.APC))return partial(o,"lamp APC cable");
        putPower(l,o.offset(LAMP),PowerDeviceKind.LAMP,Direction.EAST);
        BlockPos up=o.offset(UNIT);l.setBlock(up,ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(),3);
        if(!(l.getBlockEntity(up) instanceof DisposalUnitBlockEntity))return partial(o,"unit entity");
        BlockPos apc=o.offset(3,1,6);if(!(l.getBlockEntity(apc) instanceof PowerDeviceBlockEntity power))return partial(o,"APC entity");
        power.setEnergyJoules(500_000); // finite, explicit fixture battery charge (not a meter grant)
          for(var node:route()){
             BlockPos pos=o.offset(node.position());
             boolean installed=node.hostKind()==DisposalTubeRecord.HostKind.SEALED_WALL
                     ?DisposalTubeWorldData.forMutation(l).installSealed(l,pos,node.orientation(),node.prototypeId().toString())
                     :DisposalTubeWorldData.forMutation(l).installExposed(l,new DisposalTubeRecord(UUID.randomUUID(),pos,node.prototypeId().toString(),node.orientation(),true,node.tag(),String.join(",",node.routerTags()),0,node.hostKind(),"",null));
             if(!installed)return partial(o,"owned tube at "+node.position());
        }
         PowerRuntime.disposalUnitChanged(l,up);
         String seeded=initialize(l,entry);
         if(!seeded.startsWith("Initialized "))return "PARTIAL build: fixture structure exists but finite breathable-room initialization failed: "+seeded+"; bounds "+bounds(o);
           return "Built from floor reference "+entry+"; "+seeded+"; bounds "+bounds(o)+"; standing entry="+entryFromFloorReference(entry)+"; unit="+up+"; tour nodes="+route().size()+"; safe outlet="+o.offset(tour().outlet())+"; 500,000 J finite APC battery.";
    }
    public static String initialize(ServerLevel l,BlockPos entry){
        BlockPos o=cornerFromFloorReference(entry);
        String invalid=fixtureRoomFailure(l,o);if(invalid!=null)return "Not initialized: "+invalid+"; bounds "+bounds(o);
         // Claim every gas-passable cell in the bounded enclosure, not just AIR: partial
         // collision blocks (unit, tubes and powered devices) are part of the same gas component.
         List<BlockPos> room=new ArrayList<>();for(int x=1;x<W-1;x++)for(int y=1;y<H-1;y++)for(int z=1;z<D-1;z++){BlockPos p=o.offset(x,y,z);if(AtmosphereTopology.isPassable(l,p))room.add(p);}
         GasMixture air=new GasMixture(Map.of(GasType.OXYGEN,8.0,GasType.NITROGEN,30.0),293.15);
          if(!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(l,room,air))return "Not initialized: bounded room could not be synchronously proven finite and enclosed; no gas written.";
         double pressure=room.isEmpty()?0:AtmosphereService.INSTANCE.readAtmosphere(l,room.get(0)).map(r->r.mixture().pressureKpa(1.0)).orElse(0.0);
            return "Initialized "+room.size()+" finite owned gas-passable enclosure cells (96 play-area cells plus connected passable machine/pipe cells) with 8 mol O2 + 30 mol N2 per cell; breathable pressure="+String.format(java.util.Locale.ROOT,"%.1f",pressure)+" kPa. Unit captures room gas at its actual flush boundary; APC has finite 500,000 J charge.";
    }
     public static String status(ServerLevel l,BlockPos entry){BlockPos o=cornerFromFloorReference(entry);if(!boundsLoaded(l,o))return "NOT READY: fixture bounds and complete sparse route footprint must already be FULL-loaded; "+bounds(o);BlockPos p=o.offset(UNIT);if(!(l.getBlockEntity(p) instanceof DisposalUnitBlockEntity u))return "NOT READY: missing unit; bounds "+bounds(o);
        var meter=PowerRuntime.disposalUnitMeter(l,u);var sample=u.reservoirAir()==null?Optional.<com.juicyslew.moonstation14.ms14.atmos.exposure.FiniteExposureAir.Sample>empty():u.reservoirAir().sample();
        var room=AtmosphereService.INSTANCE.readAtmosphere(l,p.above());
          BlockPos outlet=o.offset(tour().outlet());
          return "route="+fixtureRoute(l,o)+", routeNodes="+route().size()+", coveredPrototypes="+tour().coveredTypes()+", estimatedRideSeconds="+String.format(Locale.ROOT,"%.1f",tour().estimatedRideSeconds())+", obstacleCells="+tour().obstacleFootprint().size()+", resolvedUnit="+u.prototype()+", powered="+l.getBlockState(p).getValue(DisposalUnitBlock.POWERED)+", phase="+l.getBlockState(p).getValue(DisposalUnitBlock.VISUAL_STATE)+", meter="+meter+", finiteRoom="+room.map(r->r.status()+" "+r.mixture().pressureKpa(1.0)+" kPa").orElse("unknown")+", reservoir(informational)="+sample.map(s->s.mixture().gasMoles()+" / "+s.mixture().totalMoles()+" mol").orElse("unavailable")+", entry="+entryFromFloorReference(entry)+", unit="+p+", outlet="+outlet+", outletClear="+l.getBlockState(outlet).getCollisionShape(l,outlet).isEmpty()+", bounds="+bounds(o);}
    private static String enter(net.minecraft.server.level.ServerPlayer actor,ServerLevel level,BlockPos entry){
        BlockPos origin=cornerFromFloorReference(entry);
        if(actor==null)return "Enter requires an online player with an owned character body; console cannot enter.";
        var authority=HandActorAuthority.resolve(actor).orElse(null);
        if(authority==null)return "Enter refused: no currently-authorized character body for this operator.";
        var body=authority.body();
        if(body.level()!=level||body.isRemoved()||!body.isAlive()||!body.isAddedToLevel()||level.getEntity(body.getUUID())!=body
                ||com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(body).isPresent())
            return "Enter refused: controlled body is not a live, uncontained body in this dimension.";
        String invalid=fixtureRoomFailure(level,origin);if(invalid!=null)return "Enter refused: "+invalid+"; bounds "+bounds(origin)+".";
        var target=entryFromFloorReference(entry);
        var movedBox=body.getBoundingBox().move(target.subtract(body.position()));
        if(!level.hasChunkAt(BlockPos.containing(target))||!level.getBlockState(origin.offset(ENTRY_CELL.getX(),0,ENTRY_CELL.getZ())).is(ModBlocks.STEEL_WALL_BLOCK.get())
                ||!level.getBlockState(origin.offset(ENTRY_CELL)).isAir()||!level.getBlockState(origin.offset(ENTRY_CELL.above())).isAir()||!level.noCollision(body,movedBox))
            return "Enter refused: safe interior body-sized destination is blocked or unloaded.";
        // This is only an explicit same-world position move of the authority-resolved body; no body, mind,
        // harness, inventory or camera binding is created or changed by the fixture helper.
        body.teleportTo(target.x,target.y,target.z);
        body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return "Moved your existing controlled body (authority epoch "+authority.epoch()+") to safe fixture interior "+target+". No body state was created.";
    }
    private static boolean fixtureRoomValid(ServerLevel level,BlockPos o){return fixtureRoomFailure(level,o)==null;}
    private static String fixtureRoomFailure(ServerLevel level,BlockPos o){
        if(!boundsLoaded(level,o))return "required fixture bounds or sparse route cells are not fully loaded";
        String routeFailure=fixtureRouteFailure(level,o);if(routeFailure!=null)return routeFailure;
        if(!(level.getBlockEntity(o.offset(UNIT)) instanceof DisposalUnitBlockEntity))return mismatch("unit",o,UNIT,level.getBlockState(o.offset(UNIT)),ModBlocks.DISPOSAL_UNIT.get());
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
            BlockPos local=new BlockPos(x,y,z),world=o.offset(local);
            if(x==0||x==W-1||y==0||y==H-1||z==0||z==D-1){if(!level.getBlockState(world).is(ModBlocks.STEEL_WALL_BLOCK.get()))return mismatch("perimeter",o,local,level.getBlockState(world),ModBlocks.STEEL_WALL_BLOCK.get());continue;}
            if(isPlayAreaCell(local)){
                if(local.equals(UNIT)){if(!level.getBlockState(world).is(ModBlocks.DISPOSAL_UNIT.get()))return mismatch("unit",o,local,level.getBlockState(world),ModBlocks.DISPOSAL_UNIT.get());continue;}
                if(isPowerDevice(local)){if(level.getBlockState(world).getBlock()!=powerBlock(local))return mismatch("power device",o,local,level.getBlockState(world),powerBlock(local));continue;}
                var routeNode=route().stream().filter(n->n.position().equals(local)).findFirst().orElse(null);
                if(routeNode!=null){Block expected=routeNode.hostKind()==DisposalTubeRecord.HostKind.EXPOSED?ModBlocks.DISPOSAL_TUBE.get():ModBlocks.STEEL_WALL_BLOCK.get();if(!level.getBlockState(world).is(expected))return mismatch("route host",o,local,level.getBlockState(world),expected);continue;}
                if(isCablePost(local)){if(!level.getBlockState(world).is(Blocks.STONE))return mismatch("cable post",o,local,level.getBlockState(world),Blocks.STONE);continue;}
                if(!level.getBlockState(world).isAir())return mismatch("play area",o,local,level.getBlockState(world),Blocks.AIR);
            }else if(isPowerDevice(local)){
                if(level.getBlockState(world).getBlock()!=powerBlock(local))return mismatch("power device",o,local,level.getBlockState(world),powerBlock(local));
            }else if(isCablePost(local)){
                if(!level.getBlockState(world).is(Blocks.STONE))return mismatch("cable post",o,local,level.getBlockState(world),Blocks.STONE);
            }else if(!level.getBlockState(world).is(ModBlocks.STEEL_WALL_BLOCK.get()))return mismatch("sealed fixture",o,local,level.getBlockState(world),ModBlocks.STEEL_WALL_BLOCK.get());
        }
         for(BlockPos obstacle:tour().obstacleFootprint())if(!level.getBlockState(o.offset(obstacle)).is(ModBlocks.STEEL_WALL_BLOCK.get()))return mismatch("tour obstacle",o,obstacle,level.getBlockState(o.offset(obstacle)),ModBlocks.STEEL_WALL_BLOCK.get());
         for(var node:route()){BlockPos world=o.offset(node.position());var state=level.getBlockState(world);
            Block expected=node.hostKind()==DisposalTubeRecord.HostKind.EXPOSED?ModBlocks.DISPOSAL_TUBE.get():ModBlocks.STEEL_WALL_BLOCK.get();if(!state.is(expected))return mismatch("route host",o,node.position(),state,expected);
        }
        return null;
    }
    private static String mismatch(String what,BlockPos origin,BlockPos local,BlockState actual,Block expected){return what+" mismatch relative "+local+" world "+origin.offset(local)+": actual="+actual+", expected="+expected;}
    static boolean isPlayAreaCell(BlockPos p){return p.getX()>=ROOM_MIN_X&&p.getX()<=ROOM_MAX_X&&p.getY()>=ROOM_MIN_Y&&p.getY()<=ROOM_MAX_Y&&p.getZ()>=ROOM_MIN_Z&&p.getZ()<=ROOM_MAX_Z;}
    private static boolean isCablePost(BlockPos p){return p.getX()==4&&p.getZ()==7&&p.getY()>=1&&p.getY()<=3||p.getY()==1&&(p.getX()==4&&p.getZ()>=2&&p.getZ()<=8||p.getX()==2&&p.getZ()>=4&&p.getZ()<=6);}
    private static boolean isPowerDevice(BlockPos p){return p.equals(LAMP)||p.getX()==3&&p.getY()==1&&(p.getZ()==2||p.getZ()==4||p.getZ()==6);}
    private static net.minecraft.world.level.block.Block powerBlock(BlockPos p){if(p.equals(LAMP))return ModBlocks.POWER_LAMP.get();return switch(p.getZ()){case 2->ModBlocks.HV_SOURCE.get();case 4->ModBlocks.HV_MV_SUBSTATION.get();case 6->ModBlocks.APC.get();default->Blocks.AIR;};}
     private static boolean prototypesValid(){try{var cat=PrototypeRuntime.serverDisposals();var plan=DisposalFixtureTourPlan.resolve(cat);var unit=cat.get(ModDisposals.UNIT);return plan.nodes().size()==259&&unit!=null&&unit.unit()!=null&&unit.unit().powerWatts()==1000;}catch(RuntimeException invalid){return false;}}
    private static DisposalOrientation findFrame(ServerLevel l,BlockPos pos,String id,Set<Direction> wanted){var proto=PrototypeRuntime.serverDisposals().get(ModDisposals.id(id));for(var frame:DisposalOrientationCandidates.candidates(proto.tube(),DisposalOrientation.identity(),wanted))if(DisposalTubeRouting.connections(proto.tube(),frame).stream().map(DisposalDirection::toMC).collect(java.util.stream.Collectors.toSet()).equals(wanted))return frame;return null;}
       private static boolean fixtureRoute(ServerLevel l,BlockPos o){return fixtureRouteFailure(l,o)==null;}
       private static String fixtureRouteFailure(ServerLevel l,BlockPos o){if(!boundsLoaded(l,o))return "route bounds are not loaded";if(!(l.getBlockEntity(o.offset(UNIT)) instanceof DisposalUnitBlockEntity))return "route unit missing";var route=route();var routing=new LoadedWorldDisposalRouting(l);var data=DisposalTubeWorldData.forMutation(l);Set<String> tags=new HashSet<>();for(int i=0;i<route.size();i++){var n=route.get(i);BlockPos pos=o.offset(n.position());var a=routing.record(pos);if(a==null)return "route node "+i+" at "+n.position()+" world "+pos+" has no effective routing record";if(!a.tube().kind().equals(n.effectiveTube().kind()))return "route node "+i+" at "+n.position()+" effective kind actual="+a.tube().kind()+" expected="+n.effectiveTube().kind();if(!a.frame().equals(n.orientation()))return "route node "+i+" at "+n.position()+" frame actual="+a.frame()+" expected="+n.orientation();if(!DisposalTubeRouting.connections(a.tube(),a.frame()).equals(n.ports()))return "route node "+i+" at "+n.position()+" ports actual="+DisposalTubeRouting.connections(a.tube(),a.frame())+" expected="+n.ports();var record=data.lookup(l,pos).record();if(record==null)return "route node "+i+" at "+n.position()+" persisted record missing";if(!record.prototypeId().equals(n.prototypeId().toString())||!record.hostKind().equals(n.hostKind())||!record.routingTag().equals(n.tag())||!parseRouterTags(record.routingFilter()).equals(n.routerTags()))return "route node "+i+" persisted metadata mismatch at "+n.position()+": "+record;if(i>0){var prev=route.get(i-1);var b=routing.record(o.offset(prev.position()));Direction d=direction(prev.position(),n.position());if(!routing.reciprocal(b,a,d))return "route edge "+(i-1)+"->"+i+" is not reciprocal";Direction expected=i+1<route.size()?direction(n.position(),route.get(i+1).position()):direction(n.position(),tour().outlet());for(int choice=0;choice<2;choice++){final int c=choice;var selected=DisposalTubeRouting.selectNextDirection(a.tube(),a.frame(),DisposalDirection.fromMC(d.getOpposite()),tags,bound->c==0?0:bound-1);if(selected!=DisposalDirection.fromMC(expected))return "route node "+i+" at "+n.position()+" selects "+selected+" expected outgoing "+expected+" for random extreme "+choice;}}if(!n.tag().isBlank())tags.add(n.tag());}BlockPos outlet=o.offset(tour().outlet());if(!l.getBlockState(outlet).isAir()||l.getBlockEntity(outlet)!=null)return "safe outlet "+tour().outlet()+" world "+outlet+" is occupied by "+l.getBlockState(outlet);return null;}
      private static Set<String> parseRouterTags(String filter){if(filter==null||filter.isBlank())return Set.of();return Arrays.stream(filter.split(",")).map(String::trim).filter(s->!s.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());}
     private static boolean boundsLoaded(ServerLevel level,BlockPos origin){
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
            long wx=(long)origin.getX()+x,wy=(long)origin.getY()+y,wz=(long)origin.getZ()+z;
            if(wx<Integer.MIN_VALUE||wx>Integer.MAX_VALUE||wy<level.getMinBuildHeight()||wy>=level.getMaxBuildHeight()||wz<Integer.MIN_VALUE||wz>Integer.MAX_VALUE)return false;
            BlockPos p=new BlockPos((int)wx,(int)wy,(int)wz);
            if(!level.getWorldBorder().isWithinBounds(p)||level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return false;
        }
         Set<BlockPos> extended=new HashSet<>(tour().obstacleFootprint());for(var n:route())extended.add(n.position());extended.add(tour().outlet());
        for(BlockPos local:extended){BlockPos p=origin.offset(local);if(p.getY()<level.getMinBuildHeight()||p.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(p)||level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return false;}
        return true;
    }
    private static Direction direction(BlockPos a,BlockPos b){return Direction.fromDelta(b.getX()-a.getX(),b.getY()-a.getY(),b.getZ()-a.getZ());}
     private static void putPower(ServerLevel l,BlockPos p,PowerDeviceKind kind,Direction face){if(kind==PowerDeviceKind.APC){if(!PowerDeviceFixturePlacement.placeApc(l,p,face))throw new IllegalStateException("APC support/placement failed at "+p);return;}var b=switch(kind){case HV_SOURCE->ModBlocks.HV_SOURCE.get();case HV_MV_SUBSTATION->ModBlocks.HV_MV_SUBSTATION.get();case LAMP->ModBlocks.POWER_LAMP.get();default->throw new IllegalArgumentException();};l.setBlock(p,b.defaultBlockState().setValue(PowerDeviceBlock.FACING,face),3);}
    private static String partial(BlockPos o,String what){return "Partial build failed at "+what+"; inspect/repair bounded area "+bounds(o)+".";}
    private static String bounds(BlockPos o){return "["+o.getX()+","+o.getY()+","+o.getZ()+"]..["+(o.getX()+W-1)+","+(o.getY()+H-1)+","+(o.getZ()+D-1)+"]";}
}

