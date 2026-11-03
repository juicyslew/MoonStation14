package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Bounded loaded-world visual ray query shared by client selection and server fixtures. */
public final class ExamineWorldSelector {
    public static final int MAX_CANDIDATES = 32;
    public static final double MAX_VISIBLE_RANGE = 128;
    public record Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box,
                            boolean transparent, boolean looseItem) {
        public Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box, boolean transparent) {
            this(target, hitPoint, distance, publicName, box, transparent, false);
        }
        public Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box) {
            this(target, hitPoint, distance, publicName, box, false, false);
        }
    }
    private ExamineWorldSelector() { }

    /** Resolves live outline/eligibility geometry without depending on the current camera ray. */
    public static AABB currentBounds(Level level, ExamineTarget target) {
        if (level == null || target == null) return null;
        if (!target.kind().hasWorldGeometry()) return null;
        if (target.kind() == ExamineTarget.Kind.ENTITY) {
            Entity entity = level.getEntity(target.entityId());
            return entity == null || entity.level() != level ? null
                    : liveEntityBounds(entity.getBoundingBox(), entity.getUUID(), target.entityUuid(), entity.isRemoved());
        }
        if (target.kind() != ExamineTarget.Kind.BLOCK) return null;
        BlockPos pos = target.blockPos();
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlock) {
            BlockPos root=canonicalFirelockRoot(level,state,pos);
            return firelockVisibleBounds(level,root,level.getBlockState(root));
        }
        if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
            BlockPos root = canonicalRoot(level, state, pos);
            var rootState = level.getBlockState(root);
            var facing = rootState.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING);
            return airlockVisibleBounds(level,root,rootState,facing);
        }
        var shape = visualShape(state, level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }
    /** Identity-checked adapter used with the entity's freshly-read bounding box each frame. */
    public static AABB liveEntityBounds(AABB currentBox, UUID currentUuid, UUID expectedUuid, boolean removed) {
        return removed || currentBox == null || currentUuid == null || !currentUuid.equals(expectedUuid) ? null : currentBox;
    }

    public static List<Candidate> select(Level level, Vec3 rayOrigin, Vec3 rayDirection, double range,
                                         Set<Integer> ignoredEntityIds) {
        if (level == null || rayOrigin == null || rayDirection == null || !Double.isFinite(range) || range <= 0)
            return List.of();
        Vec3 direction = normalized(rayDirection);
        if (direction.lengthSqr() < 1.0e-12) return List.of();
        double limit = Math.min(range, MAX_VISIBLE_RANGE);
        Vec3 end = rayOrigin.add(direction.scale(limit));
        ArrayList<Candidate> candidates = new ArrayList<>();
        double opaqueStop = limit;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = floor(rayOrigin.x), y = floor(rayOrigin.y), z = floor(rayOrigin.z);
        int sx = sign(direction.x), sy = sign(direction.y), sz = sign(direction.z);
        double tx = firstBoundary(rayOrigin.x, direction.x, x, sx), ty = firstBoundary(rayOrigin.y, direction.y, y, sy),
                tz = firstBoundary(rayOrigin.z, direction.z, z, sz);
        double dx = delta(direction.x), dy = delta(direction.y), dz = delta(direction.z), entered = 0;
        for (int cells = 0; cells < 384; cells++) {
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) { opaqueStop = entered; break; }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
                    BlockPos root=canonicalRoot(level,state,pos);
                    var facing=state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING);
                    double openness=airlockOpenness(level,root,state);
                    List<AABB> pieces=airlockVisiblePieces(level,root,facing,openness);
                    var hit=com.juicyslew.moonstation14.ms14.doors.DoorExamineGeometry.ray(pieces,rayOrigin,end);
                    if(hit!=null&&hit.distance()<=limit) { candidates.add(new Candidate(ExamineTarget.block(root),hit.point(),hit.distance(),state.getBlock().getName().getString(),hit.bounds())); opaqueStop=hit.distance(); break; }
                    // Do not nominate an invisible plane through an open aperture; continue DDA behind it.
                } else if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlock) {
                    BlockPos root=canonicalFirelockRoot(level,state,pos);
                    BlockState rootState=level.getBlockState(root);
                    var facing=rootState.getValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.FACING);
                    var pieces=com.juicyslew.moonstation14.ms14.doors.FirelockGeometry.worldBounds(root,facing,firelockOpenness(level,root));
                    var hit=com.juicyslew.moonstation14.ms14.doors.DoorExamineGeometry.ray(pieces,rayOrigin,end);
                    if(hit!=null&&hit.distance()<=limit) { candidates.add(new Candidate(ExamineTarget.block(root),hit.point(),hit.distance(),state.getBlock().getName().getString(),hit.bounds())); opaqueStop=hit.distance(); break; }
                } else {
                var shape = visualShape(state, level, pos);
                var hit = shape.clip(rayOrigin, end, pos);
                if (hit != null) {
                    Vec3 point = hit.getLocation(); double distance = rayOrigin.distanceTo(point);
                    if (distance <= limit) {
                        BlockPos root = canonicalRoot(level, state, pos);
                        candidates.add(new Candidate(ExamineTarget.block(root), point, distance,
                                state.getBlock().getName().getString(), shape.bounds().move(pos), ExamineVision.isTransparent(state)));
                        if (!ExamineVision.isTransparent(state)) { opaqueStop = distance; break; }
                    }
                }
                }
            }
            double next = Math.min(tx, Math.min(ty, tz));
            if (next > limit || !Double.isFinite(next)) break;
            entered = next;
            if (tx <= next + 1.0e-10) { x += sx; tx += dx; }
            if (ty <= next + 1.0e-10) { y += sy; ty += dy; }
            if (tz <= next + 1.0e-10) { z += sz; tz += dz; }
        }
        AABB search = new AABB(rayOrigin, end).inflate(1.0);
        for (Entity entity : level.getEntities((Entity) null, search, e -> !e.isRemoved()
                && !e.isInvisible() && (ignoredEntityIds == null || !ignoredEntityIds.contains(e.getId())))) {
            AABB bounds = entity.getBoundingBox();
            Hit hit = intersect(rayOrigin, direction, Math.min(limit, opaqueStop), bounds);
            if (hit != null) candidates.add(new Candidate(ExamineTarget.entity(entity), hit.point(), hit.distance(),
                    com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(entity).getString(), bounds,
                    false, entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity));
        }
        LinkedHashMap<ExamineTarget, Candidate> unique = new LinkedHashMap<>();
        candidates.stream().sorted(Comparator.comparingDouble(Candidate::distance))
                .forEach(candidate -> unique.putIfAbsent(candidate.target(), candidate));
        ArrayList<Candidate> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingDouble(Candidate::distance).thenComparing(c -> c.target().kind().ordinal())
                .thenComparing(c -> c.target().kind() == ExamineTarget.Kind.BLOCK
                        ? c.target().blockPos().toShortString() : c.target().entityUuid().toString()));
        return boundedCandidates(sorted);
    }

    /** Keeps the nearest admitted visible block reachable even in a dense entity pile. */
    static List<Candidate> boundedCandidates(List<Candidate> sorted) {
        if (sorted.size() <= MAX_CANDIDATES) return List.copyOf(sorted);
        Candidate nearestBlock = sorted.stream().filter(c -> c.target().kind() == ExamineTarget.Kind.BLOCK)
                .findFirst().orElse(null);
        ArrayList<Candidate> bounded = new ArrayList<>(sorted.subList(0, MAX_CANDIDATES));
        if (nearestBlock != null && !bounded.contains(nearestBlock)) {
            for (int i = bounded.size() - 1; i >= 0; i--) {
                if (bounded.get(i).target().kind() == ExamineTarget.Kind.ENTITY) {
                    bounded.set(i, nearestBlock);
                    bounded.sort(Comparator.comparingDouble(Candidate::distance)
                            .thenComparing(c -> c.target().kind().ordinal())
                            .thenComparing(c -> c.target().kind() == ExamineTarget.Kind.BLOCK
                                    ? c.target().blockPos().toShortString() : c.target().entityUuid().toString()));
                    break;
                }
            }
        }
        return List.copyOf(bounded);
    }
    private static Vec3 normalized(Vec3 d) {
        if (d == null || !Double.isFinite(d.x) || !Double.isFinite(d.y) || !Double.isFinite(d.z) || d.lengthSqr() < 1e-12) return Vec3.ZERO;
        return d.normalize();
    }
    private record Hit(double distance, Vec3 point) { }
    private static Hit intersect(Vec3 origin, Vec3 direction, double range, AABB box) {
        double near=0, far=range;
        double[] o={origin.x,origin.y,origin.z}, d={direction.x,direction.y,direction.z};
        double[] lo={box.minX,box.minY,box.minZ}, hi={box.maxX,box.maxY,box.maxZ};
        for(int a=0;a<3;a++) { if(Math.abs(d[a])<1e-12) { if(o[a]<lo[a]||o[a]>hi[a])return null; continue; }
            double x=(lo[a]-o[a])/d[a], y=(hi[a]-o[a])/d[a]; if(x>y){double t=x;x=y;y=t;} near=Math.max(near,x);far=Math.min(far,y);if(near>far)return null; }
        return near<=range?new Hit(near,origin.add(direction.scale(near))):null;
    }
    private static int floor(double v) { return (int)Math.floor(v); }
    private static double airlockOpenness(Level level,BlockPos root,BlockState fallback) {
        if(level.getBlockEntity(root) instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity door) {
            var view=door.clientView(); var data=door.prototype();
            double elapsed=Math.max(0,level.getGameTime()-view.transitionStartedAt())/20.0;
            return com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.openness(view.phase(),elapsed,
                    data==null?.4:data.door().openTimeOneSeconds(),data==null?.2:data.door().openTimeTwoSeconds(),
                    data==null?.4:data.door().closeTimeOneSeconds(),data==null?.2:data.door().closeTimeTwoSeconds());
        }
        return fallback.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN)?1:0;
    }
    private static boolean airlockPanelOpen(Level level,BlockPos root) {
        if(level.getBlockEntity(root) instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity door) {
            var view=door.clientView();
            return view.panelVisible()&&view.panelOpen()&&view.phase()!=com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN;
        }
        return false;
    }
    private static boolean hasFirelock(Level level,BlockPos root) {
        return level.getBlockEntity(root) instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity a && a.firelockPresentation()!=null;
    }
    private static List<AABB> airlockVisiblePieces(Level level,BlockPos root,net.minecraft.core.Direction facing,double q) {
        var attached=level.getBlockEntity(root) instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity a && a.firelockPresentation()!=null;
        boolean latched=attached && ((com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity)level.getBlockEntity(root)).firelockPresentation().authoritativeLatch();
        double coverQ=attached?firelockOpenness(level,root):0;
        var base=com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.worldBounds(root,facing,q,airlockPanelOpen(level,root));
        var cover=hasFirelock(level,root)?com.juicyslew.moonstation14.ms14.doors.FirelockGeometry.worldBounds(root,facing,firelockOpenness(level,root)):List.<AABB>of();
        return com.juicyslew.moonstation14.ms14.doors.DoorExamineGeometry.visiblePieces(base,cover,attached,latched,coverQ);
    }
    private static AABB airlockVisibleBounds(Level level,BlockPos root,BlockState state,net.minecraft.core.Direction facing) {
        return com.juicyslew.moonstation14.ms14.doors.DoorExamineGeometry.bounds(airlockVisiblePieces(level,root,facing,airlockOpenness(level,root,state)));
    }
    private static AABB firelockVisibleBounds(Level level,BlockPos root,BlockState state) {
        var pieces=com.juicyslew.moonstation14.ms14.doors.FirelockGeometry.worldBounds(root,
                state.getValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.FACING),firelockOpenness(level,root));
        return com.juicyslew.moonstation14.ms14.doors.DoorExamineGeometry.bounds(pieces);
    }
    private static double firelockOpenness(Level level,BlockPos root) {
        com.juicyslew.moonstation14.ms14.doors.FirelockController.Presentation view=null;
        com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData data=null;
        var be=level.getBlockEntity(root);
        if(be instanceof com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity f){view=f.firelockPresentation();data=f.prototype();}
        else if(be instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity a){view=a.firelockPresentation();data=a.firelockPrototypeForPresentation();}
        return com.juicyslew.moonstation14.ms14.doors.FirelockMotion.openness(view,data,level.getGameTime());
    }
    private static BlockPos canonicalFirelockRoot(Level level,BlockState state,BlockPos pos) {
        boolean upper=state.getValue(com.juicyslew.moonstation14.ms14.doors.FirelockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        BlockPos below=pos.below();boolean paired=upper&&level.hasChunkAt(below)&&level.getBlockState(below).getBlock()==state.getBlock();
        return com.juicyslew.moonstation14.ms14.doors.DoorExamineGeometry.canonicalLowerRoot(pos,upper,paired);
    }
    private static net.minecraft.world.phys.shapes.VoxelShape visualShape(BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
            var facing=state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING);
            return facing.getAxis()==net.minecraft.core.Direction.Axis.Z
                    ? net.minecraft.world.phys.shapes.Shapes.box(0,0,7.9/16,1,1,8.1/16)
                    : net.minecraft.world.phys.shapes.Shapes.box(7.9/16,0,0,8.1/16,1,1);
        }
        return state.getShape(level,pos);
    }
    private static BlockPos canonicalRoot(Level level, BlockState state, BlockPos pos) {
        boolean upper=state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock
                && state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        BlockPos lower=pos.below();boolean paired=upper&&level.hasChunkAt(lower)&&level.getBlockState(lower).getBlock()==state.getBlock()
                &&level.getBlockState(lower).getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
        return com.juicyslew.moonstation14.ms14.doors.DoorExamineGeometry.canonicalLowerRoot(pos,upper,paired);
    }
    private static int sign(double v){return v>0?1:v<0?-1:0;}
    private static double delta(double v){return v==0?Double.POSITIVE_INFINITY:Math.abs(1/v);}
    private static double firstBoundary(double origin,double direction,int cell,int step){return step==0?Double.POSITIVE_INFINITY:((step>0?cell+1.0:cell)-origin)/direction;}
}
