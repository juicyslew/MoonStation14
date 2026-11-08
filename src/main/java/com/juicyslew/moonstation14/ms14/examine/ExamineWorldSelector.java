package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Function;

/** Bounded loaded-world visual ray query shared by client selection and server fixtures. */
public final class ExamineWorldSelector {
    public static final int MAX_CANDIDATES = 32;
    public static final double MAX_VISIBLE_RANGE = 128;
    public record Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box,
                            boolean transparent, boolean looseItem) {
        public Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box,
                         boolean transparent) {
            this(target, hitPoint, distance, publicName, box, transparent, false);
        }
        public Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box) {
            this(target, hitPoint, distance, publicName, box, false, false);
        }
    }
    private record ShapeHit(BlockPos root, Vec3 point, AABB bounds, boolean transparent) { }
    private ExamineWorldSelector() { }

    public enum CellRayStatus { SELECTED, BLOCKED, UNLOADED }
    public record CellRayResult(CellRayStatus status, BlockPos cell) { }

    /** Loaded-only finite-cell selection using the same visual shape and transparency policy as examine. */
    public static CellRayResult selectLoadedCell(Level level, Vec3 origin,
                                                  Vec3 rayDirection, int distance) {
        if (level == null || origin == null || rayDirection == null || distance < 0
                || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)
                || !Double.isFinite(rayDirection.x) || !Double.isFinite(rayDirection.y) || !Double.isFinite(rayDirection.z)
                || rayDirection.lengthSqr() < 1.0e-12) return new CellRayResult(CellRayStatus.UNLOADED, null);
        Vec3 direction = normalized(rayDirection), end = origin.add(direction.scale(distance));
        int x = floor(origin.x), y = floor(origin.y), z = floor(origin.z);
        int sx = sign(direction.x), sy = sign(direction.y), sz = sign(direction.z);
        double tx = firstBoundary(origin.x, direction.x, x, sx), ty = firstBoundary(origin.y, direction.y, y, sy),
                tz = firstBoundary(origin.z, direction.z, z, sz);
        double dx = delta(direction.x), dy = delta(direction.y), dz = delta(direction.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos previous = null;
        for (int cells = 0; cells < distance * 3 + 4; cells++) {
            pos.set(x, y, z);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos))
                return new CellRayResult(CellRayStatus.UNLOADED, null);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !ExamineVision.isTransparent(state)) {
                if(distance==0) {
                    boolean containsOrigin;
                    if(state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
                        BlockPos root=canonicalRoot(level,state,pos);
                        containsOrigin=com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.worldBounds(root,
                                state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING),
                                airlockOpenness(level,root,state),airlockPanelOpen(level,root)).stream().anyMatch(box->box.contains(origin));
                    } else {
                        containsOrigin=visualShape(state,level,pos).toAabbs().stream().map(box->box.move(pos)).anyMatch(box->box.contains(origin));
                    }
                    if(containsOrigin)return new CellRayResult(CellRayStatus.BLOCKED,pos.immutable());
                }
                boolean blocked;
                if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
                    BlockPos root = canonicalRoot(level, state, pos);
                    var facing = state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING);
                    blocked = com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.worldBounds(root, facing,
                                    airlockOpenness(level, root, state), airlockPanelOpen(level, root)).stream()
                            .anyMatch(box -> box.clip(origin, end).filter(point -> origin.distanceTo(point) <= distance + 1e-7).isPresent());
                } else {
                    var hit = visualShape(state, level, pos).clip(origin, end, pos);
                    blocked = hit != null && origin.distanceTo(hit.getLocation()) <= distance + 1e-7;
                }
                if (blocked) return previous == null
                        ? new CellRayResult(CellRayStatus.BLOCKED, null)
                        : new CellRayResult(CellRayStatus.SELECTED, previous);
            }
            previous = pos.immutable();
            double next = Math.min(tx, Math.min(ty, tz));
            if (!Double.isFinite(next) || next > distance) break;
            if (tx <= next + 1e-10) { x += sx; tx += dx; }
            if (ty <= next + 1e-10) { y += sy; ty += dy; }
            if (tz <= next + 1e-10) { z += sz; tz += dz; }
        }
        if(distance==0) return new CellRayResult(CellRayStatus.SELECTED, BlockPos.containing(origin));
        BlockPos target = BlockPos.containing(end);
        if (level.isOutsideBuildHeight(target) || !level.hasChunkAt(target))
            return new CellRayResult(CellRayStatus.UNLOADED, null);
        return new CellRayResult(CellRayStatus.SELECTED, target);
    }

    /** Resolves live outline/eligibility geometry without depending on the current camera ray. */
    public static AABB currentBounds(Level level, ExamineTarget target) {
        return currentBounds(level, target, 1.0f);
    }
    public static AABB currentBounds(Level level, ExamineTarget target, float partialTick) {
        if (level == null || target == null) return null;
        if (!target.kind().hasWorldGeometry()) return null;
        if (target.frameId() != null) return FrameExamineGeometry.worldBounds(level, target, partialTick);
        if (target.kind() == ExamineTarget.Kind.ENTITY) {
            Entity entity = level.getEntity(target.entityId());
            if (entity == null || entity.level() != level) return null;
            AABB identityChecked = liveEntityBounds(entity.getBoundingBox(), entity.getUUID(), target.entityUuid(), entity.isRemoved());
            if (identityChecked == null) return null;
            if (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose) {
                var geometry = com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry.forEntity(loose,
                        partialTick);
                return geometry == null ? null : geometry.worldEnvelope();
            }
            return identityChecked;
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
        return select(level, rayOrigin, rayDirection, range, ignoredEntityIds,
                1.0f);
    }
    public static List<Candidate> select(Level level, Vec3 rayOrigin, Vec3 rayDirection, double range,
                                         Set<Integer> ignoredEntityIds, float partialTick) {
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
                var hits = blockShapeHits(level, state, pos, null, rayOrigin, end,
                        root -> level.hasChunkAt(root) ? level.getBlockState(root) : null,
                        root -> com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.existingBlockEntity(level, root));
                if (!hits.isEmpty()) {
                    ShapeHit nearest = hits.stream().min(Comparator.comparingDouble(hit -> rayOrigin.distanceTo(hit.point()))).orElseThrow();
                    double distance = rayOrigin.distanceTo(nearest.point());
                    if (distance <= limit) {
                        candidates.add(new Candidate(ExamineTarget.block(nearest.root()), nearest.point(), distance,
                                state.getBlock().getName().getString(), nearest.bounds(), nearest.transparent()));
                        if (!nearest.transparent()) { opaqueStop = distance; break; }
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
        double profileRadius = com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry.maximumProfileRadius(level.isClientSide);
        AABB search = new AABB(rayOrigin, end).inflate(Math.max(1.0, profileRadius));
        ArrayList<Entity> foundEntities = new ArrayList<>(257);
        level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class), search,
                e -> !e.isRemoved() && !e.isInvisible()
                        && (ignoredEntityIds == null || !ignoredEntityIds.contains(e.getId())), foundEntities, 257);
        // The cap sentinel proves candidate overflow; never select an arbitrary prefix.
        if (foundEntities.size() < 257) for (Entity entity : foundEntities) {
            Vec3 hitPoint;
            double hitDistance;
            AABB candidateBox;
            boolean looseItem = entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity;
            if (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose) {
                var geometry = com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry.forEntity(loose, partialTick);
                if (geometry == null) continue;
                hitPoint = geometry.clip(rayOrigin, end);
                if (hitPoint == null) continue;
                hitDistance = rayOrigin.distanceTo(hitPoint);
                candidateBox = geometry.worldEnvelope();
            } else {
                Hit hit = intersect(rayOrigin, direction, Math.min(limit, opaqueStop), entity.getBoundingBox());
                if (hit == null) continue;
                hitPoint = hit.point(); hitDistance = hit.distance(); candidateBox = entity.getBoundingBox();
            }
            if (hitDistance <= Math.min(limit, opaqueStop)) candidates.add(new Candidate(ExamineTarget.entity(entity), hitPoint, hitDistance,
                    entity.getDisplayName().getString(), candidateBox, false, looseItem));
        }
        if (level.isClientSide || level instanceof net.minecraft.server.level.ServerLevel) {
            for (var frameRay : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .frameRayContexts(level, rayOrigin, end, partialTick)) {
                var frame = frameRay.frame(); var pose = frameRay.pose();
                Vec3 localFrom = frameRay.localFrom(), localTo = frameRay.localTo();
                Vec3 traversalFrom = frameRay.traversalFrom(), traversalTo = frameRay.traversalTo();
                for (BlockPos cell : ExamineTargetResolver.crossedCells(traversalFrom, traversalTo)) {
                    var cellRead = frameRay.read(cell);
                    if (!cellRead.owned()) continue;
                    if (!cellRead.known()) {
                        var entry = new AABB(cell).clip(traversalFrom, traversalTo).orElse(traversalFrom);
                        opaqueStop = Math.min(opaqueStop, rayOrigin.distanceTo(pose.transformPosition(entry)));
                        break;
                    }
                    BlockState state = cellRead.state();
                    if (state.isAir()) continue;
                    BlockPos root = canonicalRoot(state, cell, rootPos -> {
                        var rootRead = frameRay.read(rootPos);
                        return rootRead.known() ? rootRead.state() : null;
                    });
                    if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
                        if (state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)
                                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                                && root.equals(cell)) { opaqueStop = 0; break; }
                        var rootRead = frameRay.read(root);
                        if (!rootRead.known()) { opaqueStop = 0; break; }
                        if (!rootRead.owned()) continue;
                    }
                    var hits = blockShapeHits(level, state, cell, root, localFrom, localTo, rootPos -> {
                        var rootRead = frameRay.read(rootPos);
                        return rootRead.known() ? rootRead.state() : null;
                    }, frameRay::blockEntity);
                    boolean transparent = ExamineVision.isTransparent(state);
                    double nearestDistance = Double.POSITIVE_INFINITY;
                    for (ShapeHit shapeHit : hits) {
                        Vec3 worldHit = pose.transformPosition(shapeHit.point()); double distance = rayOrigin.distanceTo(worldHit);
                        nearestDistance = Math.min(nearestDistance, distance);
                        if (distance <= opaqueStop && distance <= limit) candidates.add(new Candidate(ExamineTarget.frameBlock(frame.getUniqueId(), shapeHit.root()), worldHit, distance,
                                state.getBlock().getName().getString(), FrameExamineGeometry.transformedBounds(shapeHit.bounds(), pose), transparent));
                    }
                    if (!transparent && Double.isFinite(nearestDistance)) { opaqueStop = Math.min(opaqueStop, nearestDistance); break; }
                }
            }
        }
        LinkedHashMap<ExamineTarget, Candidate> unique = new LinkedHashMap<>();
        candidates.stream().sorted(Comparator.comparingDouble(Candidate::distance)).forEach(candidate -> unique.putIfAbsent(candidate.target(), candidate));
        ArrayList<Candidate> sorted = new ArrayList<>(unique.values());
        sorted.sort(candidateOrdering());
        double finalOpaqueStop = opaqueStop; sorted.removeIf(candidate -> candidate.distance() > finalOpaqueStop + 1.0e-7);
        return boundedCandidates(sorted);
    }
    /** Applies the shared candidate budget while retaining the nearest admitted world block. */
    static List<Candidate> boundedCandidates(List<Candidate> sorted) {
        if (sorted.size() <= MAX_CANDIDATES) return List.copyOf(sorted);
        Candidate nearestBlock = sorted.stream().filter(c -> c.target().kind() == ExamineTarget.Kind.BLOCK)
                .findFirst().orElse(null);
        ArrayList<Candidate> bounded = new ArrayList<>(sorted.subList(0, MAX_CANDIDATES));
        if (nearestBlock != null && !bounded.contains(nearestBlock)) {
            for (int i = bounded.size() - 1; i >= 0; i--) {
                if (bounded.get(i).target().kind() == ExamineTarget.Kind.ENTITY) {
                    bounded.set(i, nearestBlock);
                    bounded.sort(candidateOrdering());
                    break;
                }
            }
        }
        return List.copyOf(bounded);
    }
    private static Comparator<Candidate> candidateOrdering() {
        return Comparator.comparingDouble(Candidate::distance).thenComparing(c -> c.target().kind().ordinal())
                .thenComparing(c -> c.target().kind() == ExamineTarget.Kind.BLOCK
                        ? (c.target().frameId() == null ? "world" : c.target().frameId().toString()) + ":" + c.target().blockPos().toShortString()
                        : c.target().entityUuid().toString());
    }
    private static Vec3 normalized(Vec3 d) {
        if (d == null || !Double.isFinite(d.x) || !Double.isFinite(d.y) || !Double.isFinite(d.z) || d.lengthSqr() < 1e-12) return Vec3.ZERO;
        return d.normalize();
    }
    private record Hit(double distance, Vec3 point) { }
    private static Hit intersect(Vec3 origin, Vec3 direction, double range, AABB box) {
        double near=0, far=range; double[] o={origin.x,origin.y,origin.z}, d={direction.x,direction.y,direction.z};
        double[] lo={box.minX,box.minY,box.minZ}, hi={box.maxX,box.maxY,box.maxZ};
        for(int a=0;a<3;a++) { if(Math.abs(d[a])<1e-12) { if(o[a]<lo[a]||o[a]>hi[a])return null; continue; }
            double x=(lo[a]-o[a])/d[a], y=(hi[a]-o[a])/d[a]; if(x>y){double t=x;x=y;y=t;} near=Math.max(near,x);far=Math.min(far,y);if(near>far)return null; }
        return near<=range?new Hit(near,origin.add(direction.scale(near))):null;
    }
    private static int floor(double v) { return (int)Math.floor(v); }
    public static double airlockOpenness(Level level, BlockPos root, BlockState fallback) {
        return airlockOpenness(level, root, fallback, com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.existingBlockEntity(level, root));
    }
    public static double airlockOpenness(Level level,BlockPos root,BlockState fallback, net.minecraft.world.level.block.entity.BlockEntity existingController) {
        if(existingController instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity door) {
            var view=door.clientView(); var data=door.prototype(); double elapsed=Math.max(0,level.getGameTime()-view.transitionStartedAt())/20.0;
            return com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.openness(view.phase(),elapsed,data==null?.4:data.door().openTimeOneSeconds(),data==null?.2:data.door().openTimeTwoSeconds(),data==null?.4:data.door().closeTimeOneSeconds(),data==null?.2:data.door().closeTimeTwoSeconds());
        }
        return fallback.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN)?1:0;
    }
    public static boolean airlockPanelOpen(Level level,BlockPos root) { return airlockPanelOpen(level, root, com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.existingBlockEntity(level, root)); }
    public static boolean airlockPanelOpen(Level level,BlockPos root, net.minecraft.world.level.block.entity.BlockEntity existingController) {
        if(existingController instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity door) { var view=door.clientView(); return view.panelVisible()&&view.panelOpen()&&view.phase()!=com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN; }
        return false;
    }
    public static AABB airlockLocalBounds(Level level, BlockPos root, BlockState state) { return airlockLocalBounds(level, root, state, com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.existingBlockEntity(level, root)); }
    public static AABB airlockLocalBounds(Level level, BlockPos root, BlockState state, net.minecraft.world.level.block.entity.BlockEntity existingController) {
        AABB result = null;
        for (AABB piece : com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.worldBounds(root, state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING), airlockOpenness(level, root, state, existingController), airlockPanelOpen(level, root, existingController))) result = result == null ? piece : result.minmax(piece);
        return result;
    }
    private static net.minecraft.world.phys.shapes.VoxelShape visualShape(BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
            var facing=state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING);
            return facing.getAxis()==net.minecraft.core.Direction.Axis.Z ? net.minecraft.world.phys.shapes.Shapes.box(0,0,7.9/16,1,1,8.1/16) : net.minecraft.world.phys.shapes.Shapes.box(7.9/16,0,0,8.1/16,1,1);
        }
        return state.getShape(level,pos);
    }
    private static List<ShapeHit> blockShapeHits(Level level, BlockState state, BlockPos pos, BlockPos resolvedRoot, Vec3 from, Vec3 to, Function<BlockPos, BlockState> rootLookup, Function<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entityLookup) {
        if (state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock) {
            BlockPos root = resolvedRoot == null ? canonicalRoot(level, state, pos) : resolvedRoot;
            BlockState rootState = root.equals(pos) ? state : rootLookup.apply(root);
            if (rootState == null || !(rootState.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock)) return List.of();
            var controller = entityLookup.apply(root);
            List<AABB> pieces = com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.worldBounds(root, rootState.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING), airlockOpenness(level, root, rootState, controller), airlockPanelOpen(level, root, controller));
            AABB assembly = null; for (AABB piece : pieces) assembly = assembly == null ? piece : assembly.minmax(piece);
            AABB assemblyBounds = assembly; ArrayList<ShapeHit> hits = new ArrayList<>();
            for (AABB piece : pieces) piece.clip(from, to).ifPresent(point -> hits.add(new ShapeHit(root, point, assemblyBounds, false)));
            return List.copyOf(hits);
        }
        var shape = visualShape(state, level, pos); var hit = shape.clip(from, to, pos);
        return hit == null ? List.of() : List.of(new ShapeHit(canonicalRoot(level, state, pos), hit.getLocation(), shape.bounds().move(pos), ExamineVision.isTransparent(state)));
    }
    private static BlockPos canonicalRoot(Level level, BlockState state, BlockPos pos) { return canonicalRoot(state, pos, root -> level.hasChunkAt(root) ? level.getBlockState(root) : null); }
    public static BlockPos canonicalRoot(BlockState state, BlockPos pos, Function<BlockPos, BlockState> knownState) {
        if(state.getBlock() instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlock && state.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            BlockPos lowerPos=pos.below(); BlockState lower=knownState.apply(lowerPos);
            if(lower!=null && lower.getBlock()==state.getBlock()&&lower.getValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)return lowerPos.immutable();}
        return pos.immutable();
    }
    private static int sign(double v){return v>0?1:v<0?-1:0;}
    private static double delta(double v){return v==0?Double.POSITIVE_INFINITY:Math.abs(1/v);}
    private static double firstBoundary(double origin,double direction,int cell,int step){return step==0?Double.POSITIVE_INFINITY:((step>0?cell+1.0:cell)-origin)/direction;}
}
