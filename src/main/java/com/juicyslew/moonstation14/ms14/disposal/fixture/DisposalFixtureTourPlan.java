package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientationCandidates;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.Function;

/** Deterministic sparse ride geometry relative to the original room corner. */
final class DisposalFixtureTourPlan {
    private static final int FLOOR_Y = 0;
    private static final String CAMERA_TAG = "camera tour";
    private static final Set<String> EMPTY_TAGS = Set.of();
    private static final Map<BlockPos,String> SPECIAL_TYPES = Map.of(
            new BlockPos(3,1,8),"disposal_trunk",
            new BlockPos(3,1,9),"disposal_junction",
            new BlockPos(13,3,10),"disposal_junction_flipped",
            new BlockPos(14,3,10),"disposal_x_junction",
            new BlockPos(6,3,10),"disposal_tagger",
            new BlockPos(7,3,10),"disposal_router",
            new BlockPos(30,5,10),"disposal_y_junction",
            new BlockPos(14,5,1),"disposal_router_flipped");
    private DisposalFixtureTourPlan() { }

    /** 259 contiguous tube cells. Node zero is the unit trunk; the terminal exits west into room air. */
    static List<BlockPos> cells() {
        var path = new ArrayList<BlockPos>();
        path.add(new BlockPos(3,1,8));
        axis(path, Direction.SOUTH, 2);
        axis(path, Direction.UP, 2);
        axis(path, Direction.EAST, 2);
        // Outdoor outbound trunk at Y3, east to X30, then up to the serpentine tier.
        axis(path, Direction.EAST, 25);
        axis(path, Direction.UP, 2);
        axis(path, Direction.NORTH, 9);
        axis(path, Direction.WEST, 2);
        // Ten north/south lanes in a rectangle. Adjacent lanes are three cells apart.
        for (int lane=0; lane<10; lane++) {
            int targetX = (lane % 2 == 0) ? 14 : 28;
            axis(path, targetX > last(path).getX() ? Direction.EAST : Direction.WEST,
                    Math.abs(targetX-last(path).getX()));
            if (lane < 9) axis(path, Direction.SOUTH, 3);
        }
        // Return beyond the outbound line, then a low-level run through the room to a safe outlet.
        axis(path, Direction.DOWN, 4);
        axis(path, Direction.EAST, 1);
        axis(path, Direction.NORTH, 19);
        axis(path, Direction.WEST, 23);
        if (path.size()!=259) throw new IllegalStateException("tour route length changed: "+path.size());
        return List.copyOf(path);
    }

    static Set<BlockPos> cellsSet() { return Set.copyOf(new HashSet<>(cells())); }
    /** Small explicit camera-clip obstacle; no geometry is filled/enclosed around the whole ride. */
    static Set<BlockPos> obstacleFootprint() {
        var cells=new HashSet<BlockPos>();
        for(int x=20;x<=25;x++) for(int y=0;y<=3;y++) for(int z=8;z<=10;z++) cells.add(new BlockPos(x,y,z));
        return Set.copyOf(cells);
    }
    static BlockPos outlet() { return cells().getLast().relative(Direction.WEST); }
    static Direction outputAt(int index) {
        var path=cells();
        return index==path.size()-1 ? Direction.WEST : direction(path.get(index),path.get(index+1));
    }
    static Direction incomingAt(int index) {
        var path=cells();
        return index==0 ? null : direction(path.get(index),path.get(index-1));
    }
    static BlockPos minimum() { return new BlockPos(3,1,1); }
    static BlockPos maximum() { return new BlockPos(30,5,28); }
    static int floorY() { return FLOOR_Y; }
    static double estimatedRideSeconds(double resolvedHolderSegmentSeconds) {
        if(!Double.isFinite(resolvedHolderSegmentSeconds)||resolvedHolderSegmentSeconds<=0)
            throw new IllegalArgumentException("invalid resolved holder segment duration");
        return cells().size()*resolvedHolderSegmentSeconds;
    }

    /** Immutable resolved route, using the supplied resolved prototype lookup (including inheritance). */
    static Plan resolve(PrototypeCatalog<DisposalEntityData> catalog) {
        return resolve(id -> catalog.get(id), catalog.get(ModDisposals.HOLDER));
    }

    static Plan resolve(Function<ResourceLocation,DisposalEntityData> prototypeLookup,
                        DisposalEntityData resolvedHolder) {
        if(resolvedHolder==null||resolvedHolder.holder()==null)
            throw new IllegalArgumentException("resolved disposal holder prototype unavailable");
        double segmentSeconds=resolvedHolder.holder().segmentSeconds();
        var positions=cells();
        var nodes=new ArrayList<Node>(positions.size());
        var tags=new LinkedHashSet<String>();
        var covered=new LinkedHashSet<ResourceLocation>();
        for(int i=0;i<positions.size();i++) {
            BlockPos pos=positions.get(i);
            Direction incoming=incomingAt(i), outgoing=outputAt(i);
            String path= SPECIAL_TYPES.get(pos);
            if(path==null) path=incoming.getAxis()==outgoing.getAxis()?"disposal_pipe":"disposal_bend";
            ResourceLocation id=ModDisposals.id(path);
            DisposalEntityData prototype=prototypeLookup.apply(id);
            if(prototype==null||prototype.tube()==null) throw new IllegalArgumentException("missing resolved tube prototype "+id);
            String tag=path.equals("disposal_tagger")?CAMERA_TAG:"";
            Set<String> routerTags=path.equals("disposal_router_flipped")?Set.of(CAMERA_TAG):EMPTY_TAGS;
            var effective=new DisposalEntityData.Tube(prototype.tube().kind(),prototype.tube().topology(),
                    prototype.tube().rotationQuarters(),prototype.tube().segmentSeconds(),prototype.tube().turnDamage(),
                    tag.isBlank()?prototype.tube().tag():tag,routerTags);
            if(path.equals("disposal_tagger")) tags= new LinkedHashSet<>(DisposalTubeRouting.appendTag(tags,tag));
            DisposalOrientation frame=findFrame(effective,incoming,outgoing,tags);
            if(frame==null) throw new IllegalArgumentException("no deterministic resolved frame for "+id+" at "+pos+" "+incoming+" -> "+outgoing);
            DisposalTubeRecord.HostKind host=sealedHost(pos)?DisposalTubeRecord.HostKind.SEALED_WALL:DisposalTubeRecord.HostKind.EXPOSED;
            if(host==DisposalTubeRecord.HostKind.SEALED_WALL&&effective.kind()!=DisposalEntityData.Kind.STRAIGHT)
                throw new IllegalArgumentException("only ordinary straight tubes may cross sealed steel: "+id+" at "+pos);
            nodes.add(new Node(pos,id,frame,tag,routerTags,host,effective,DisposalTubeRouting.connections(effective,frame)));
            covered.add(id);
        }
        return new Plan(nodes,obstacleFootprint(),outlet(),covered,estimatedRideSeconds(segmentSeconds),segmentSeconds);
    }

    private static DisposalOrientation findFrame(DisposalEntityData.Tube tube,Direction incoming,Direction outgoing,Set<String> tags) {
        var in=incoming==null?null:DisposalDirection.fromMC(incoming);
        var out=DisposalDirection.fromMC(outgoing);
        for(var frame:DisposalOrientationCandidates.candidates(tube,DisposalOrientation.identity(),Set.of())) {
            var ports=DisposalTubeRouting.connections(tube,frame);
            if(!ports.contains(out)||(in!=null&&!ports.contains(in))) continue;
            var low=DisposalTubeRouting.selectNextDirection(tube,frame,in,tags,bound->0);
            var high=DisposalTubeRouting.selectNextDirection(tube,frame,in,tags,bound->bound-1);
            if(low==out&&high==out)return frame;
        }
        return null;
    }

    private static boolean sealedHost(BlockPos p) {
        boolean obstacle=p.getX()>=20&&p.getX()<=25&&p.getY()>=0&&p.getY()<=3&&p.getZ()>=8&&p.getZ()<=10;
        boolean bounded=p.getX()>=0&&p.getX()<12&&p.getY()>=0&&p.getY()<7&&p.getZ()>=0&&p.getZ()<12;
        boolean roomAir=p.getX()>=2&&p.getX()<=7&&p.getY()>=1&&p.getY()<=4&&p.getZ()>=7&&p.getZ()<=10;
        return obstacle||bounded&&!roomAir;
    }

    record Node(BlockPos position,ResourceLocation prototypeId,DisposalOrientation orientation,String tag,
                Set<String> routerTags,DisposalTubeRecord.HostKind hostKind,DisposalEntityData.Tube effectiveTube,
                Set<DisposalDirection> ports) {
        Node { position=position.immutable();routerTags=Set.copyOf(routerTags);ports=Set.copyOf(ports); }
    }
    record Plan(List<Node> nodes,Set<BlockPos> obstacleFootprint,BlockPos outlet,Set<ResourceLocation> coveredTypes,
                double estimatedRideSeconds,double resolvedSegmentSeconds) {
        Plan { nodes=List.copyOf(nodes);obstacleFootprint=Set.copyOf(obstacleFootprint);outlet=outlet.immutable();coveredTypes=Set.copyOf(coveredTypes); }
    }

    private static BlockPos last(List<BlockPos> path) { return path.getLast(); }
    private static void axis(List<BlockPos> path, Direction direction, int distance) {
        if (distance<0) throw new IllegalArgumentException("negative axis distance");
        for (int i=0;i<distance;i++) path.add(last(path).relative(direction));
    }
    private static Direction direction(BlockPos from,BlockPos to) {
        return Direction.fromDelta(to.getX()-from.getX(),to.getY()-from.getY(),to.getZ()-from.getZ());
    }
}
