package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedBlockGetter;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Quaterniond;

/** Read-only, conservative classification of the body's final grounded support band. */
public final class NativeFinalFootClearance {
    private NativeFinalFootClearance() { }

    public enum Result { CLEAR, OBSTRUCTED, UNKNOWN }
    /** At most one immutable witness, emitted by the exact query pass. */
    public record QueryObservation(String mode, AABB queryBox, AABB envelope, AABB frameBox,
            AABB nativeShapeContextBounds, double nativeYawRadians, long gameTime, Result result,
            String reason, String obstructionKind, String frameId, BlockPos storagePos,
            String blockState, AABB shapeBounds, NativeFrameInterval.PoseValue pose) { }

    /** Shared owner-query budget: one center chunk plus a one-chunk margin on each side. */
    public static Result classify(net.minecraft.world.level.Level level, Entity body) {
        return classify(level, body, null, 16, 1);
    }

    public static Result classify(net.minecraft.world.level.Level level, Entity body, NativeActorYaw nativeYaw) {
        return classify(level, body, nativeYaw, 16, 1);
    }

    /** Full candidate-body volume query; read-only and conservative for unloaded or unsupported geometry. */
    public static Result classifyCandidateBox(net.minecraft.world.level.Level level, Entity body, AABB candidate,
            NativeActorYaw nativeYaw) {
        return classifyCandidateBox(level, body, candidate, nativeYaw, 16, 1);
    }

    public static Result classifyCandidateBox(net.minecraft.world.level.Level level, Entity body, AABB candidate,
            NativeActorYaw nativeYaw, int maxChunks, int chunkMargin) {
        return classifyCandidateBox(level, body, candidate, nativeYaw, maxChunks, chunkMargin, null);
    }

    public static Result classifyCandidateBox(net.minecraft.world.level.Level level, Entity body, AABB candidate,
            NativeActorYaw nativeYaw, int maxChunks, int chunkMargin, java.util.function.Consumer<QueryObservation> observer) {
        if (level == null || body == null || body.level() != level || body.isRemoved() || !validBox(candidate)
                || nativeYaw == null || nativeYaw.orientation() == null) return observed(observer, level, "BODY_VOLUME", candidate,
                        null, nativeYaw, Result.UNKNOWN, "invalid-input", null, null, null, null, null, null);
        AABB envelope = FrameBoxIntersection.orientedWorldBounds(candidate, nativeYaw.orientation());
        if (!validBox(envelope)) return observed(observer, level, "BODY_VOLUME", candidate, envelope, nativeYaw,
                Result.UNKNOWN, "invalid-envelope", null, null, null, null, null, null);
        return classifyBox(level, body, envelope, envelope, candidate, candidate, nativeYaw, maxChunks, chunkMargin, true, observer);
    }

    private static boolean validBox(AABB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && box.getXsize() > 0 && box.getYsize() > 0 && box.getZsize() > 0;
    }

    public static AABB footBand(Entity body) {
        return footBand(body.getBoundingBox());
    }

    static AABB footBand(AABB bounds) {
        return new AABB(bounds.minX, bounds.minY - CharacterMovementMotor.GROUNDED_SUPPORT_PROBE_BLOCKS,
                bounds.minZ, bounds.maxX, bounds.minY, bounds.maxZ);
    }

    public static Result classify(net.minecraft.world.level.Level level, Entity body, int maxChunks, int chunkMargin) {
        return classify(level, body, null, maxChunks, chunkMargin);
    }

    public static Result classify(net.minecraft.world.level.Level level, Entity body, NativeActorYaw nativeYaw,
            int maxChunks, int chunkMargin) {
        return classify(level, body, nativeYaw, maxChunks, chunkMargin, null);
    }

    public static Result classify(net.minecraft.world.level.Level level, Entity body, NativeActorYaw nativeYaw,
            int maxChunks, int chunkMargin, java.util.function.Consumer<QueryObservation> observer) {
        if (level == null || body == null || body.level() != level || body.isRemoved()) return observed(observer, level,
                "SUPPORT_BAND", null, null, nativeYaw, Result.UNKNOWN, "invalid-body", null, null, null, null, null, null);
        AABB baseBand = footBand(body.getBoundingBox());
        AABB band = nativeYaw == null ? baseBand
                : FrameBoxIntersection.orientedWorldBounds(baseBand, nativeYaw.orientation());
        if (band == null) return observed(observer, level, "SUPPORT_BAND", baseBand, band, nativeYaw,
                Result.UNKNOWN, "invalid-envelope", null, null, null, null, null, null);
        return classifyBox(level, body, band, baseBand, baseBand, body.getBoundingBox(), nativeYaw,
                maxChunks, chunkMargin, false, observer);
    }

    private static Result classifyBox(net.minecraft.world.level.Level level, Entity body, AABB envelope,
            AABB worldQueryBox, AABB frameBox, AABB nativeShapeContextBounds, NativeActorYaw nativeYaw,
            int maxChunks, int chunkMargin, boolean candidateQuery, java.util.function.Consumer<QueryObservation> observer) {
        try {
            Vec3 queryCenter = candidateQuery ? envelope.getCenter() : body.position();
            var view = LoadedBlockGetter.collisionView(level, queryCenter, queryCenter,
                    candidateQuery ? Math.max(frameBox.getXsize(), Math.max(frameBox.getYsize(), frameBox.getZsize()))
                            : Math.max(body.getBbWidth(), body.getBbHeight()), maxChunks, chunkMargin);
            for (var shape : view.getBlockCollisions(body, worldQueryBox))
                if (Shapes.joinIsNotEmpty(shape, Shapes.create(worldQueryBox), net.minecraft.world.phys.shapes.BooleanOp.AND))
                    return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND",
                            worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw,
                            Result.OBSTRUCTED, "world-shape-overlap", "WORLD_SHAPE", null, null, null,
                            null, shape, null);
            var frames = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameBoxEndpointQuery(level, envelope);
            if (!frames.complete()) return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "incomplete-frame-query", null, null, null, null, null, null, null);
            if (!frames.contexts().isEmpty() && nativeYaw == null) return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "missing-native-yaw", null, null, null, null, null, null, null);
            for (var context : frames.contexts()) {
                if (!FrameBoxIntersection.supportsPose(context.pose())) return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "unsupported-frame-pose", null, context.frame(), null, null, null, null, context.pose());
                var local = inverseBounds(envelope, context.pose());
                var plot = context.frame().getPlot();
                if (local == null || plot == null) return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "frame-query-unavailable", null, context.frame(), null, null, null, null, context.pose());
                var storageBounds = new BoundingBox3d(plot.getBoundingBox()).toMojang().inflate(1.0);
                var getter = level instanceof net.minecraft.server.level.ServerLevel server
                        ? LoadedBlockGetter.forFullFrame(server, plot, storageBounds, maxChunks)
                        : LoadedBlockGetter.clientForFrame(level, plot, storageBounds, maxChunks);
                var shapeQuery = new NativeFrameShapeQuery(level, body, context.pose(), getter, nativeShapeContextBounds);
                BlockPos low = new BlockPos(floor(local.minX - 1.0e-7) - 1,
                        floor(local.minY - 1.0e-7) - 1, floor(local.minZ - 1.0e-7) - 1);
                BlockPos high = new BlockPos(floor(local.maxX + 1.0e-7) + 1,
                        floor(local.maxY + 1.0e-7) + 1, floor(local.maxZ + 1.0e-7) + 1);
                long cells = (long) (high.getX() - low.getX() + 1) * (high.getY() - low.getY() + 1)
                        * (high.getZ() - low.getZ() + 1);
                if (cells <= 0 || cells > (long) maxChunks * 16 * 16 * 16) return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "frame-cell-budget", null, context.frame(), null, null, null, null, context.pose());
                for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
                    if (!context.owns(pos)) continue;
                    var read = context.read(pos);
                    if (!read.known() || read.state() == null) return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "unknown-frame-cell", null, context.frame(), pos, null, null, null, context.pose());
                    // Enforce the same bounded, fully-loaded owner read contract for this cell.
                    var state = getter.getBlockState(pos);
                    for (var localShape : shapeQuery.shape(pos, state).toAabbs()) {
                        if (!validBox(localShape)) return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "unsupported-shape", null, context.frame(), pos, state, null, null, context.pose());
                        if (FrameBoxIntersection.intersects(frameBox, nativeYaw == null ? new Quaterniond()
                                : new Quaterniond(nativeYaw.orientation()),
                                localShape.move(pos), context.pose()))
                            return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND",
                                    worldQueryBox, envelope, frameBox, nativeShapeContextBounds, nativeYaw,
                                    Result.OBSTRUCTED, "frame-shape-overlap", "FRAME_SHAPE", context.frame(),
                                    pos, state, localShape, null, context.pose());
                    }
                }
            }
            return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox,
                    envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.CLEAR, "clear", null,
                    null, null, null, null, null, null);
        } catch (LoadedBlockGetter.UnknownChunk unknown) {
            return observedQuery(observer, level, candidateQuery ? "BODY_VOLUME" : "SUPPORT_BAND", worldQueryBox,
                    envelope, frameBox, nativeShapeContextBounds, nativeYaw, Result.UNKNOWN, "unknown-chunk", null,
                    null, null, null, null, null, null);
        }
    }

    private static Result observed(java.util.function.Consumer<QueryObservation> observer,
            net.minecraft.world.level.Level level, String mode, AABB query, AABB envelope, NativeActorYaw yaw,
            Result result, String reason, String kind, String frame, BlockPos pos, String state, AABB shape,
            dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        if (observer != null) {
            emitObservation(observer, () -> new QueryObservation(mode, query, envelope, query, query,
                    yaw == null ? Double.NaN : yaw.yaw(), level == null ? -1L : level.getGameTime(),
                    result, reason, kind, frame, pos, state, shape,
                    pose == null ? null : NativeFrameInterval.PoseValue.capture(pose)));
        }
        return result;
    }

    private static Result observedQuery(java.util.function.Consumer<QueryObservation> observer,
            net.minecraft.world.level.Level level, String mode, AABB query, AABB envelope, AABB frameBox,
            AABB nativeShapeContextBounds, NativeActorYaw yaw, Result result, String reason, String kind,
            dev.ryanhcode.sable.sublevel.SubLevel frame, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, AABB shapeBounds,
            net.minecraft.world.phys.shapes.VoxelShape worldShape,
            dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        if (observer != null) {
            emitObservation(observer, () -> new QueryObservation(mode, query, envelope, frameBox, nativeShapeContextBounds,
                        yaw == null ? Double.NaN : yaw.yaw(), level.getGameTime(), result, reason, kind,
                        frame == null ? null : frame.getUniqueId().toString(),
                        pos == null ? null : pos.immutable(), state == null ? null : state.toString(),
                        shapeBounds != null ? shapeBounds : worldShape == null ? null : worldShape.bounds(),
                        pose == null ? null : NativeFrameInterval.PoseValue.capture(pose)));
        }
        return result;
    }

    static void emitObservation(java.util.function.Consumer<QueryObservation> observer,
            java.util.function.Supplier<QueryObservation> extraction) {
        if (observer == null) return;
        try { observer.accept(extraction.get()); }
        catch (RuntimeException ignored) { /* Optional extraction and delivery cannot affect the classifier. */ }
    }

    private static int floor(double value) { return (int) Math.floor(value); }

    private static AABB inverseBounds(AABB box, dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        AABB result = null;
        for (int mask = 0; mask < 8; mask++) {
            Vec3 corner = new Vec3((mask & 1) == 0 ? box.minX : box.maxX,
                    (mask & 2) == 0 ? box.minY : box.maxY, (mask & 4) == 0 ? box.minZ : box.maxZ);
            Vec3 point = pose.transformPositionInverse(corner);
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return null;
            AABB singleton = new AABB(point.x, point.y, point.z, point.x, point.y, point.z);
            result = result == null ? singleton : result.minmax(singleton);
        }
        return result;
    }
}

