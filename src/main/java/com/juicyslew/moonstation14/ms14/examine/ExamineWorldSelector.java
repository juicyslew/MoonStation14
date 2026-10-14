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
                            boolean transparent) {
        public Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box) {
            this(target, hitPoint, distance, publicName, box, false);
        }
    }
    private ExamineWorldSelector() { }

    /** Resolves live outline/eligibility geometry without depending on the current camera ray. */
    public static AABB currentBounds(Level level, ExamineTarget target) {
        if (level == null || target == null) return null;
        if (target.kind() == ExamineTarget.Kind.SCENE) return null;
        if (target.kind() == ExamineTarget.Kind.ENTITY) {
            Entity entity = level.getEntity(target.entityId());
            return entity == null || entity.level() != level ? null
                    : liveEntityBounds(entity.getBoundingBox(), entity.getUUID(), target.entityUuid(), entity.isRemoved());
        }
        BlockPos pos = target.blockPos();
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
            BlockPos root = canonicalRoot(level, state, pos);
            var rootState = level.getBlockState(root);
            var facing = rootState.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING);
            AABB result = null;
            for (AABB part : com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.worldBounds(root, facing,
                    airlockOpenness(level, root, rootState), airlockPanelOpen(level,root))) result = result == null ? part : result.minmax(part);
            return result;
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
                    Candidate nearest=null;
                    List<AABB> pieces=com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.worldBounds(root,facing,openness,airlockPanelOpen(level,root));
                    AABB bounds=null;
                    for(AABB piece:pieces) bounds=bounds==null?piece:bounds.minmax(piece);
                    for(AABB box:pieces) {
                        var point=box.clip(rayOrigin,end);
                        if(point.isPresent()) {
                            Vec3 hit=point.get(); double distance=rayOrigin.distanceTo(hit);
                            if(distance<=limit&&(nearest==null||distance<nearest.distance()))
                                nearest=new Candidate(ExamineTarget.block(root),hit,distance,state.getBlock().getName().getString(),bounds);
                        }
                    }
                    if(nearest!=null) { candidates.add(nearest); opaqueStop=nearest.distance(); break; }
                    // Do not nominate an invisible plane through an open aperture; continue DDA behind it.
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
            Hit hit = intersect(rayOrigin, direction, Math.min(limit, opaqueStop), entity.getBoundingBox());
            if (hit != null) candidates.add(new Candidate(ExamineTarget.entity(entity), hit.point(), hit.distance(),
                    entity.getDisplayName().getString(), entity.getBoundingBox(), false));
        }
        LinkedHashMap<ExamineTarget, Candidate> unique = new LinkedHashMap<>();
        candidates.stream().sorted(Comparator.comparingDouble(Candidate::distance))
                .forEach(candidate -> unique.putIfAbsent(candidate.target(), candidate));
        ArrayList<Candidate> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingDouble(Candidate::distance).thenComparing(c -> c.target().kind().ordinal())
                .thenComparing(c -> c.target().kind() == ExamineTarget.Kind.BLOCK
                        ? c.target().blockPos().toShortString() : c.target().entityUuid().toString()));
        return List.copyOf(sorted.subList(0, Math.min(MAX_CANDIDATES, sorted.size())));
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
        if(state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock
                && state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            BlockPos lowerPos=pos.below(); if(level.hasChunkAt(lowerPos)){BlockState lower=level.getBlockState(lowerPos);
                if(lower.getBlock()==state.getBlock()&&lower.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)
                        ==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)return lowerPos.immutable();}}
        return pos.immutable();
    }
    private static int sign(double v){return v>0?1:v<0?-1:0;}
    private static double delta(double v){return v==0?Double.POSITIVE_INFINITY:Math.abs(1/v);}
    private static double firstBoundary(double origin,double direction,int cell,int step){return step==0?Double.POSITIVE_INFINITY:((step>0?cell+1.0:cell)-origin)/direction;}
}

