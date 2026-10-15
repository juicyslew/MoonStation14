package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Server-only, loaded-world projection of generator links and complete six-face boxes. */
public final class FieldContainmentRuntime {
    private static final Map<ServerLevel, Set<BlockPos>> GENERATORS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<ClosedFieldVolume.Edge>> EDGES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<ClosedFieldVolume.Edge, LinkIdentity>> EDGE_IDENTITIES = new WeakHashMap<>();
    private static final Map<ServerLevel, List<ClosedFieldVolume.Bounds>> VOLUMES = new WeakHashMap<>();
    private static final Map<ServerLevel, List<Face>> FACES = new WeakHashMap<>();
    private record LinkIdentity(UUID first, UUID second) { }
    /** Axis-aligned rectangle proven by four live edges and four current generator placements. */
    public record Face(String key, Direction.Axis axis, int plane, int minU, int maxU, int minV, int maxV,
                       List<BlockPos> corners, List<UUID> endpointIdentities) {
        public Face { corners=List.copyOf(corners); endpointIdentities=List.copyOf(endpointIdentities); }
    }
    public record FaceHit(Face face, Direction normal, double segmentFraction) { }
    private FieldContainmentRuntime() { }
    public static void register(IEventBus bus) { bus.register(FieldContainmentRuntime.class); }
    public static void changed(ServerLevel level, BlockPos pos) { GENERATORS.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable()); }
    public static void removed(ServerLevel level, BlockPos pos) {
        Set<BlockPos> entries=GENERATORS.get(level); if(entries!=null) entries.remove(pos);
        EDGES.remove(level); EDGE_IDENTITIES.remove(level); VOLUMES.remove(level); FACES.remove(level);
    }
    public static boolean hasConnection(ServerLevel level, FieldGeneratorBlockEntity entity) {
        return EDGES.getOrDefault(level, Set.of()).stream().anyMatch(edge -> edge.first().equals(entity.getBlockPos()) || edge.second().equals(entity.getBlockPos()));
    }
    public static List<ClosedFieldVolume.Bounds> closedVolumes(ServerLevel level) { return List.copyOf(VOLUMES.getOrDefault(level,List.of())); }
    public static List<Face> currentFaces(ServerLevel level) { return List.copyOf(FACES.getOrDefault(level,List.of())); }
    public static Set<ClosedFieldVolume.Edge> currentEdges(ServerLevel level) { return Set.copyOf(EDGES.getOrDefault(level,Set.of())); }
    public static void refresh(ServerLevel level) { rebuild(level); }
    /** First conservative swept-sphere contact with any currently valid rectangular field face. */
    public static Optional<FaceHit> firstFaceHit(ServerLevel level, Vec3 from, Vec3 to, double radius) {
        if(radius<0) return Optional.empty();
        FaceHit best=null;
        for(Face face:currentFaces(level)) {
            int axis=face.axis().ordinal();
            double fraction=faceContact(from,to,axis,FieldGeometry.center(face.plane()),FieldGeometry.center(face.minU()),FieldGeometry.center(face.maxU()),FieldGeometry.center(face.minV()),FieldGeometry.center(face.maxV()),radius);
            if(Double.isFinite(fraction)&&(best==null||fraction<best.segmentFraction())) {
                double start=coordinate(from,axis),delta=coordinate(to,axis)-start;
                double plane=FieldGeometry.center(face.plane());
                Direction.AxisDirection side=start<plane?Direction.AxisDirection.NEGATIVE:start>plane?Direction.AxisDirection.POSITIVE:delta>=0?Direction.AxisDirection.NEGATIVE:Direction.AxisDirection.POSITIVE;
                best=new FaceHit(face,Direction.fromAxisAndDirection(face.axis(),side),fraction);
            }
        }
        return Optional.ofNullable(best);
    }
    public static boolean crossesFace(ServerLevel level, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to, double radius) {
        return firstFaceHit(level,from,to,radius).isPresent();
    }
    private static double faceContact(Vec3 a,Vec3 b,int axis,double plane,double u0,double u1,double v0,double v1,double radius) {
        double[] p={a.x,a.y,a.z},d={b.x-a.x,b.y-a.y,b.z-a.z}; int u=(axis+1)%3,v=(axis+2)%3;
        double du=d[axis];
        double signedDistance=p[axis]-plane;
        if(Math.abs(signedDistance)<=radius && withinFace(p,u,v,u0,u1,v0,v1,radius)) {
            if(Math.abs(du)<1e-12 || signedDistance*du>0)return Double.POSITIVE_INFINITY;
            return 0;
        }
        if(Math.abs(du)<1e-12)return Double.POSITIVE_INFINITY;
        double earliest=Double.POSITIVE_INFINITY;
        for(double offset:new double[]{-radius,radius}) {
            double t=(plane+offset-p[axis])/du;
            if(t<0||t>1)continue;
            double pu=p[u]+d[u]*t,pv=p[v]+d[v]*t;
            if(pu>=u0-radius&&pu<=u1+radius&&pv>=v0-radius&&pv<=v1+radius)earliest=Math.min(earliest,t);
        }
        return earliest;
    }
    private static boolean withinFace(double[] p,int u,int v,double u0,double u1,double v0,double v1,double r) { return p[u]>=u0-r&&p[u]<=u1+r&&p[v]>=v0-r&&p[v]<=v1+r; }
    @net.neoforged.bus.api.SubscribeEvent public static void chunkLoaded(ChunkEvent.Load e) {
        if(e.getLevel() instanceof ServerLevel l && e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk c)
            c.getBlockEntities().values().stream().filter(FieldGeneratorBlockEntity.class::isInstance).map(FieldGeneratorBlockEntity.class::cast).forEach(be->changed(l,be.getBlockPos()));
    }
    @net.neoforged.bus.api.SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload e) {
        if(e.getLevel() instanceof ServerLevel l) unloadChunk(l,e.getChunk().getPos());
    }
    public static void unloadChunk(ServerLevel level,ChunkPos chunkPos) {
        Set<BlockPos> s=GENERATORS.get(level); if(s!=null)s.removeIf(p->new ChunkPos(p).equals(chunkPos));
        EDGES.remove(level);EDGE_IDENTITIES.remove(level);VOLUMES.remove(level);FACES.remove(level);
    }
    @net.neoforged.bus.api.SubscribeEvent public static void levelUnloaded(LevelEvent.Unload e) { if(e.getLevel() instanceof ServerLevel l){GENERATORS.remove(l);EDGES.remove(l);EDGE_IDENTITIES.remove(l);VOLUMES.remove(l);FACES.remove(l);} }
    @net.neoforged.bus.api.SubscribeEvent public static void stopped(ServerStoppedEvent e){GENERATORS.clear();EDGES.clear();EDGE_IDENTITIES.clear();VOLUMES.clear();FACES.clear();}
    @net.neoforged.bus.api.SubscribeEvent public static void tick(ServerTickEvent.Post e) { for(ServerLevel l:e.getServer().getAllLevels()) rebuild(l); }

    private static void rebuild(ServerLevel level) {
        Set<BlockPos> indexed=GENERATORS.get(level); if(indexed==null){EDGES.remove(level);EDGE_IDENTITIES.remove(level);VOLUMES.remove(level);FACES.remove(level);return;}
        Map<BlockPos,FieldGeneratorBlockEntity> nodes=new HashMap<>();
        for(Iterator<BlockPos> it=indexed.iterator();it.hasNext();) { BlockPos p=it.next(); if(!level.hasChunkAt(p)){it.remove();continue;} if(level.getBlockEntity(p) instanceof FieldGeneratorBlockEntity be&&!be.isRemoved())nodes.put(p,be);else it.remove(); }
        Set<ClosedFieldVolume.Edge> previous=EDGES.getOrDefault(level,Set.of());
        Map<ClosedFieldVolume.Edge,LinkIdentity> oldIdentities=EDGE_IDENTITIES.getOrDefault(level,Map.of());
        Set<ClosedFieldVolume.Edge> edges=new HashSet<>();
        Map<ClosedFieldVolume.Edge,LinkIdentity> identities=new HashMap<>();
        for(ClosedFieldVolume.Edge edge:previous) {
            FieldGeneratorBlockEntity a=nodes.get(edge.first()),b=nodes.get(edge.second());
            if(a!=null&&b!=null&&a.snapshot().anchored()&&b.snapshot().anchored()
                    && a.profileId().equals(b.profileId()) && (a.connectionEligible()||b.connectionEligible())
                    && oldIdentities.get(edge)!=null && oldIdentities.get(edge).equals(identity(a,b)) && edgePathClear(level,a,b)) {
                edges.add(edge); identities.put(edge,identity(a,b));
            }
        }
        for(var entry:nodes.entrySet()) { var from=entry.getValue(); if(!from.snapshot().enabled()||!from.snapshot().anchored()||!from.connectionEligible())continue;
            FieldGeneratorProfile profile=ModFieldGenerators.catalog(level).get(from.profileId()); if(profile==null)continue;
            for(int[] axis:new int[][]{{1,0,0},{0,1,0},{0,0,1}}) for(int sign:new int[]{-1,1}) {
                BlockPos target=null;
                for(int d=1;d<=profile.maxConnectionLength();d++) { BlockPos p=from.getBlockPos().offset(axis[0]*d*sign,axis[1]*d*sign,axis[2]*d*sign);
                    if(!level.hasChunkAt(p))break;
                    FieldGeneratorBlockEntity other=nodes.get(p);
                    if(other!=null) { if(other.snapshot().anchored()&&other.profileId().equals(from.profileId())) target=p; break; }
                    BlockState obstruction=level.getBlockState(p); if(!obstruction.getCollisionShape(level,p).isEmpty())break;
                }
                if(target!=null) { FieldGeneratorBlockEntity other=nodes.get(target); if(from.profileId().equals(other.profileId())) { ClosedFieldVolume.Edge edge=new ClosedFieldVolume.Edge(entry.getKey(),target);edges.add(edge);identities.put(edge,identity(nodes.get(edge.first()),nodes.get(edge.second()))); } }
            }
        }
        EDGES.put(level,edges);
        EDGE_IDENTITIES.put(level,identities);
        Set<BlockPos> all=nodes.keySet(); List<ClosedFieldVolume.Bounds> boxes=new ArrayList<>();
        for(BlockPos a:all) for(BlockPos b:all) if(a.getX()<b.getX()&&a.getY()<b.getY()&&a.getZ()<b.getZ()) {
            Set<BlockPos> corners=new HashSet<>(); for(int x:new int[]{a.getX(),b.getX()})for(int y:new int[]{a.getY(),b.getY()})for(int z:new int[]{a.getZ(),b.getZ()})corners.add(new BlockPos(x,y,z));
            if(!all.containsAll(corners))continue;
            ClosedFieldVolume.derive(corners,edges,level::hasChunkAt).ifPresent(bounds->{if(!boxes.contains(bounds))boxes.add(bounds);});
        }
        VOLUMES.put(level,List.copyOf(boxes));
        FACES.put(level,deriveFaces(level,nodes,edges));
    }
    private static List<Face> deriveFaces(ServerLevel level,Map<BlockPos,FieldGeneratorBlockEntity> nodes,Set<ClosedFieldVolume.Edge> edges) {
        Set<String> seen=new HashSet<>(); List<Face> result=new ArrayList<>(); List<BlockPos> positions=List.copyOf(nodes.keySet());
        for(BlockPos a:positions)for(BlockPos b:positions) {
            int[] av={a.getX(),a.getY(),a.getZ()},bv={b.getX(),b.getY(),b.getZ()}; int axis=-1,diffs=0;
            for(int i=0;i<3;i++) { if(av[i]==bv[i])axis=i; else diffs++; }
            if(diffs!=2||axis<0)continue;
            int u=(axis+1)%3,v=(axis+2)%3,umin=Math.min(av[u],bv[u]),umax=Math.max(av[u],bv[u]),vmin=Math.min(av[v],bv[v]),vmax=Math.max(av[v],bv[v]);
            if(umin==umax||vmin==vmax)continue;
            List<BlockPos> corners=new ArrayList<>(4);
            for(int uc:new int[]{umin,umax})for(int vc:new int[]{vmin,vmax}) { int[] q=new int[3];q[axis]=av[axis];q[u]=uc;q[v]=vc;corners.add(new BlockPos(q[0],q[1],q[2])); }
            if(!nodes.keySet().containsAll(corners)||!faceEdges(corners).stream().allMatch(edges::contains)||!faceInteriorClear(level,axis,av[axis],u,umin,umax,v,vmin,vmax))continue;
            String key=axis+":"+av[axis]+":"+umin+":"+umax+":"+vmin+":"+vmax; if(!seen.add(key))continue;
            List<UUID> ids=corners.stream().map(nodes::get).map(FieldGeneratorBlockEntity::placementIdentity).toList();
            result.add(new Face(key,Direction.Axis.values()[axis],av[axis],umin,umax,vmin,vmax,corners,ids));
        }
        return List.copyOf(result);
    }
    private static Set<ClosedFieldVolume.Edge> faceEdges(List<BlockPos> corners) {
        Set<ClosedFieldVolume.Edge> result=new HashSet<>();
        for(int i=0;i<corners.size();i++)for(int j=i+1;j<corners.size();j++) {
            BlockPos a=corners.get(i),b=corners.get(j); int diff=(a.getX()!=b.getX()?1:0)+(a.getY()!=b.getY()?1:0)+(a.getZ()!=b.getZ()?1:0);
            if(diff==1)result.add(new ClosedFieldVolume.Edge(a,b));
        }
        return result;
    }
    private static boolean faceInteriorClear(ServerLevel level,int axis,int plane,int u,int umin,int umax,int v,int vmin,int vmax) {
        int[] q=new int[3];q[axis]=plane;
        for(int uc=umin;uc<=umax;uc++)for(int vc=vmin;vc<=vmax;vc++) {
            if((uc==umin||uc==umax)&&(vc==vmin||vc==vmax))continue;
            q[u]=uc;q[v]=vc;BlockPos pos=new BlockPos(q[0],q[1],q[2]);
            if(!level.hasChunkAt(pos)||!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())return false;
        }
        return true;
    }
    private static double coordinate(Vec3 p,int axis) { return axis==0?p.x:axis==1?p.y:p.z; }
    private static LinkIdentity identity(FieldGeneratorBlockEntity a,FieldGeneratorBlockEntity b) { return new LinkIdentity(a.placementIdentity(),b.placementIdentity()); }
    private static boolean edgePathClear(ServerLevel level,FieldGeneratorBlockEntity a,FieldGeneratorBlockEntity b) {
        BlockPos x=a.getBlockPos(), y=b.getBlockPos(); int dx=Integer.compare(y.getX(),x.getX()),dy=Integer.compare(y.getY(),x.getY()),dz=Integer.compare(y.getZ(),x.getZ());
        int distance=Math.abs(y.getX()-x.getX())+Math.abs(y.getY()-x.getY())+Math.abs(y.getZ()-x.getZ());
        FieldGeneratorProfile p=ModFieldGenerators.catalog(level).get(a.profileId());
        if(p==null||distance<1||distance>p.maxConnectionLength()||Integer.bitCount((dx!=0?1:0)|(dy!=0?2:0)|(dz!=0?4:0))!=1)return false;
        for(int i=1;i<distance;i++) { BlockPos at=x.offset(dx*i,dy*i,dz*i); if(!level.hasChunkAt(at)||!level.getBlockState(at).getCollisionShape(level,at).isEmpty())return false; }
        return true;
    }
}

