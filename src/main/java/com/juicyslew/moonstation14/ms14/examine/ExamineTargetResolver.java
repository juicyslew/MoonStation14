package com.juicyslew.moonstation14.ms14.examine;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Resolves stable request identity and performs bounded loaded-world visibility checks. */
public final class ExamineTargetResolver {
    public record Resolved(ExamineTarget target, Entity entity, BlockPos root, BlockState state,
                           BlockEntity blockEntity, Vec3 point, double distance,
                           UUID controllerIncarnation) { }
    /** Server-thread-only weak identity cache; it neither retains unloaded controllers nor writes saved data. */
    private static final ReferenceQueue<BlockEntity> STALE_CONTROLLERS = new ReferenceQueue<>();
    private static final Map<ControllerReference, UUID> CONTROLLER_INCARNATIONS = new HashMap<>();
    private static final class ControllerReference extends WeakReference<BlockEntity> {
        private final int identityHash;
        ControllerReference(BlockEntity controller, ReferenceQueue<BlockEntity> queue) {
            super(controller, queue);
            identityHash = System.identityHashCode(controller);
        }
        ControllerReference(BlockEntity controller) {
            super(controller);
            identityHash = System.identityHashCode(controller);
        }
        @Override public int hashCode() { return identityHash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ControllerReference reference)) return false;
            BlockEntity controller = get();
            return controller != null && controller == reference.get();
        }
    }
    private ExamineTargetResolver() { }

    public static Optional<Resolved> resolve(ServerLevel level, Entity actor, ExamineTarget target) {
        if (level == null || actor == null || target == null || actor.level() != level || actor.isRemoved())
            return Optional.empty();
        if (target.kind() == ExamineTarget.Kind.SCENE) return Optional.empty();
        if (target.kind() == ExamineTarget.Kind.ENTITY) {
            Entity entity = level.getEntity(target.entityId());
            if (entity == null || entity.isRemoved() || !entity.getUUID().equals(target.entityUuid())
                    || entity.level() != level) return Optional.empty();
            AABB box = entity.getBoundingBox();
            Vec3 eye = actor.getEyePosition();
            Vec3 point = closestPoint(box, eye);
            return Optional.of(new Resolved(target, entity, BlockPos.containing(point), null, null,
                    point, eye.distanceTo(point), null));
        }
        BlockPos pos = target.blockPos();
        if (!level.hasChunkAt(pos)) return Optional.empty();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return Optional.empty();
        BlockPos root = pos;
        if (state.getBlock() instanceof AirlockBlock) {
            root = state.getValue(AirlockBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            BlockPos upper = root.above();
            if (!level.hasChunkAt(root) || !level.hasChunkAt(upper)) return Optional.empty();
            BlockState lowerState = level.getBlockState(root), upperState = level.getBlockState(upper);
            if (!(lowerState.getBlock() instanceof AirlockBlock) || !(upperState.getBlock() instanceof AirlockBlock)
                    || lowerState.getValue(AirlockBlock.HALF) != DoubleBlockHalf.LOWER
                    || upperState.getValue(AirlockBlock.HALF) != DoubleBlockHalf.UPPER) return Optional.empty();
            state = lowerState;
        }
        Vec3 eye = actor.getEyePosition();
        var targetShape = state.getShape(level, pos);
        var bounds = targetShape.bounds().move(pos);
        Vec3 point = targetShape.isEmpty() ? Vec3.atCenterOf(pos)
                : closestPoint(bounds, eye);
        BlockEntity blockEntity = existingBlockEntity(level, root);
        UUID incarnation = controllerIncarnation(level, blockEntity);
        return Optional.of(new Resolved(target, null, root, state, blockEntity,
                point, eye.distanceTo(point), incarnation));
    }

    private static BlockEntity existingBlockEntity(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) return null;
        return chunk.getBlockEntities().get(pos);
    }

    private static UUID controllerIncarnation(ServerLevel level, BlockEntity blockEntity) {
        if (blockEntity == null) return null;
        if (level.getServer() == null || !level.getServer().isSameThread()) return null;
        synchronized (CONTROLLER_INCARNATIONS) {
            ControllerReference stale;
            while ((stale = (ControllerReference) STALE_CONTROLLERS.poll()) != null)
                CONTROLLER_INCARNATIONS.remove(stale);
            ControllerReference lookup = new ControllerReference(blockEntity);
            UUID existing = CONTROLLER_INCARNATIONS.get(lookup);
            if (existing != null) return existing;
            UUID assigned = UUID.randomUUID();
            CONTROLLER_INCARNATIONS.put(new ControllerReference(blockEntity, STALE_CONTROLLERS), assigned);
            return assigned;
        }
    }

    public record VisibilityResult(boolean visible, BlockPos blocker, String reason) { }

    /** LOS tests collision geometry only. Explicit transparent tags affect examination, not physical use. */
    public static boolean visible(ServerLevel level, Entity actor, Resolved target) {
        return visibility(level, actor, target).visible();
    }

    public static VisibilityResult visibility(ServerLevel level, Entity actor, Resolved target) {
        Vec3 from = actor.getEyePosition();
        Vec3 to = target.point();
        double distance = from.distanceTo(to);
        if (!Double.isFinite(distance) || distance > ExaminationPolicy.NORMAL_RANGE + 2)
            return new VisibilityResult(false, null, "out_of_range");
        return visibilityAlongRay(level, from, to);
    }

    /** Shared loaded-only shape LOS for virtual sky directions. Never forces chunks and has no examine-range cap. */
    public static VisibilityResult visibilityAlongRay(net.minecraft.world.level.Level level, Vec3 from, Vec3 to) {
        return visibilityAlongRay(level, from, to, true, true);
    }

    /** Shape LOS with separately controlled endpoint containment rules for world targets and virtual directions. */
    public static VisibilityResult visibilityAlongRay(net.minecraft.world.level.Level level, Vec3 from, Vec3 to,
                                                       boolean ignoreOriginContainingShape,
                                                       boolean ignoreTargetContainingShape) {
        if (level == null || from == null || to == null) return new VisibilityResult(false, null, "invalid");
        double distance = from.distanceTo(to);
        if (!Double.isFinite(distance) || distance <= 0 || distance > ExamineWorldSelector.MAX_VISIBLE_RANGE)
            return new VisibilityResult(false, null, "invalid_distance");
        for (BlockPos pos : crossedCells(from, to)) {
            if (!level.hasChunkAt(pos)) return new VisibilityResult(false, pos, "unloaded");
            BlockState state = level.getBlockState(pos);
            if (ExamineVision.isTransparent(state)) continue;
            // AirlockBlock's collision override probes getBlockEntity() for actor-specific crush
            // exemptions. Examination deliberately uses closed/open block geometry without
            // materializing a missing controller during this read-only query.
            var shape = state.getBlock() instanceof AirlockBlock
                    ? state.getValue(AirlockBlock.OPEN) ? net.minecraft.world.phys.shapes.Shapes.empty()
                            : net.minecraft.world.phys.shapes.Shapes.block()
                    : state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            if (shapeOccludes(shape, from, to, pos, distance,
                    ignoreOriginContainingShape, ignoreTargetContainingShape))
                return new VisibilityResult(false, pos, "collision");
        }
        return new VisibilityResult(true, null, "visible");
    }

    static boolean shapeOccludes(net.minecraft.world.phys.shapes.VoxelShape shape, Vec3 from, Vec3 to,
                                 BlockPos pos, double distance) {
        return shapeOccludes(shape, from, to, pos, distance, true, true);
    }
    static boolean shapeOccludes(net.minecraft.world.phys.shapes.VoxelShape shape, Vec3 from, Vec3 to,
                                 BlockPos pos, double distance, boolean ignoreOriginContainingShape,
                                 boolean ignoreTargetContainingShape) {
        if (shape == null || shape.isEmpty()) return false;
        // SS14 InRangeUnOccluded(ignoreInsideBlocker: true) ignores a hit when its
        // occluder bounds contain either endpoint. Match that endpoint rule using
        // this block's actual collision-shape bounds, not a neighboring-cell/range heuristic.
        var bounds = shape.bounds().move(pos);
        if (ignoreOriginContainingShape && containsInclusive(bounds, from)
                || ignoreTargetContainingShape && containsInclusive(bounds, to)) return false;
        var hit = shape.clip(from, to, pos);
        return hit != null && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(from) < distance * distance;
    }

    private static boolean containsInclusive(AABB box, Vec3 point) {
        return point.x >= box.minX && point.x <= box.maxX
                && point.y >= box.minY && point.y <= box.maxY
                && point.z >= box.minZ && point.z <= box.maxZ;
    }

    /** Supercover voxel traversal: at tied boundary crossings every touched cell is visited. */
    public static List<BlockPos> crossedCells(Vec3 from, Vec3 to) {
        List<BlockPos> cells = new ArrayList<>();
        int x = floor(from.x), y = floor(from.y), z = floor(from.z);
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        int endX = endpointCell(to.x, dx), endY = endpointCell(to.y, dy), endZ = endpointCell(to.z, dz);
        addCell(cells, x, y, z);
        int sx = Double.compare(dx, 0), sy = Double.compare(dy, 0), sz = Double.compare(dz, 0);
        double tx = firstBoundary(from.x, x, dx, sx), ty = firstBoundary(from.y, y, dy, sy), tz = firstBoundary(from.z, z, dz, sz);
        double dtx = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double dty = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double dtz = sz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        int guard = 0;
        while ((x != endX || y != endY || z != endZ) && guard++ < 10000) {
            double t = Math.min(tx, Math.min(ty, tz));
            // t is one of the exact computed boundary parameters; only equal parameters
            // cross together. A tolerance would visit cells the segment never enters.
            boolean cx = tx == t, cy = ty == t, cz = tz == t;
            int nx = x + (cx ? sx : 0), ny = y + (cy ? sy : 0), nz = z + (cz ? sz : 0);
            for (int mask = 1; mask < 8; mask++) {
                if ((mask & 1) != 0 && !cx || (mask & 2) != 0 && !cy || (mask & 4) != 0 && !cz
                        || mask == ((cx ? 1 : 0) | (cy ? 2 : 0) | (cz ? 4 : 0))) continue;
                addCell(cells, x + ((mask & 1) != 0 ? sx : 0),
                        y + ((mask & 2) != 0 ? sy : 0), z + ((mask & 4) != 0 ? sz : 0));
            }
            x = nx; y = ny; z = nz;
            addCell(cells, x, y, z);
            if (cx) tx += dtx; if (cy) ty += dty; if (cz) tz += dtz;
        }
        return List.copyOf(cells);
    }

    private static void addCell(List<BlockPos> cells, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!cells.contains(pos)) cells.add(pos);
    }
    private static int floor(double value) { return (int) Math.floor(value); }
    private static int endpointCell(double coordinate, double delta) {
        int cell = floor(coordinate);
        // At an exact voxel boundary, a negative-going segment terminates in
        // the cell immediately below that boundary. Without this direction-aware
        // endpoint, traversal can never meet floor(to) and runs beyond the ray.
        return delta < 0 && coordinate == cell ? cell - 1 : cell;
    }
    private static double firstBoundary(double origin, int cell, double delta, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - origin) / delta;
    }

    /** Shared client/server closest point primitive for physical-eye range checks. */
    public static Vec3 closestPoint(AABB bounds, Vec3 point) {
        return new Vec3(clamp(point.x, bounds.minX, bounds.maxX), clamp(point.y, bounds.minY, bounds.maxY),
                clamp(point.z, bounds.minZ, bounds.maxZ));
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
