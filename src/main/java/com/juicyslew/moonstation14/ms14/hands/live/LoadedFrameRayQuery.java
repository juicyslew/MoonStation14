package com.juicyslew.moonstation14.ms14.hands.live;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;

import java.util.UUID;

/** Loaded-only terrain and Sable-frame ray admission; unknown storage never degrades to a miss. */
public final class LoadedFrameRayQuery {
    private LoadedFrameRayQuery() { }

    public enum State { HIT, MISS, UNKNOWN }

    public record Hit(BlockPos storagePos, Direction localFace, Vec3 worldPoint, UUID frameId, Vec3 storagePoint) {
        public Hit {
            storagePos = storagePos.immutable();
            storagePoint = storagePoint == null ? null : new Vec3(storagePoint.x, storagePoint.y, storagePoint.z);
        }
        public Hit(BlockPos storagePos, Direction localFace, Vec3 worldPoint, UUID frameId) {
            this(storagePos, localFace, worldPoint, frameId, null);
        }
    }

    public record Result(State state, BlockHitResult worldHit, Hit frameHit) { }

    /** Narrow examine/selection frame adapter: SDK discovery, one explicit pose, projected endpoints and loaded cells. */
    public record FrameRayContext(SubLevel frame, dev.ryanhcode.sable.companion.math.Pose3dc pose,
                                  Vec3 worldFrom, Vec3 worldTo, Vec3 localFrom, Vec3 localTo,
                                  Vec3 traversalWorldFrom, Vec3 traversalWorldTo,
                                  Vec3 traversalFrom, Vec3 traversalTo) {
        public UUID frameId() { return frame.getUniqueId(); }
        public boolean owns(BlockPos pos) { return frame.getPlot() != null
                && frame.getPlot().contains(new net.minecraft.world.level.ChunkPos(pos)); }
        public CellRead read(BlockPos pos) {
            if (!owns(pos)) return new CellRead(false, true, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var state = frame.getLevel().isClientSide
                    ? knownClientBlock(frame.getLevel(), frame, pos)
                    : knownServerBlock((ServerLevel) frame.getLevel(), frame, pos);
            return state == null ? new CellRead(true, false, null) : new CellRead(true, true, state);
        }
        public net.minecraft.world.level.block.entity.BlockEntity blockEntity(BlockPos pos) {
            return knownBlockEntity(frame.getLevel(), frame, pos);
        }
    }
    public record CellRead(boolean owned, boolean known, net.minecraft.world.level.block.state.BlockState state) { }

    /** Returns only live SDK-broadphase candidates; pose and inverse endpoint projection are captured once. */
    public static java.util.List<FrameRayContext> frameRayContexts(net.minecraft.world.level.Level level,
            Vec3 from, Vec3 to, float renderPartialTick) {
        if (level == null || from == null || to == null) return java.util.List.of();
        java.util.ArrayList<FrameRayContext> result = new java.util.ArrayList<>();
        for (SubLevel frame : candidates(level, new BoundingBox3d(from, to).expand(1.0))) {
            if (frame.isRemoved() || frame.getLevel() != level || frame.getPlot() == null
                    || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame
                    || level.isClientSide && !(frame instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                    || !level.isClientSide && !(frame instanceof ServerSubLevel)) continue;
            var pose = level.isClientSide ? renderPose(frame, renderPartialTick)
                    : level instanceof LevelPoseProviderExtension provider ? provider.sable$getPose(frame) : frame.logicalPose();
            if (pose == null) continue;
            AABB frameBounds = frameWorldBounds(frame, pose, 1.0);
            Vec3 clippedFrom = rayEntry(frameBounds, from, to);
            Vec3 clippedTo = rayExit(frameBounds, from, to);
            if (clippedFrom == null || clippedTo == null) continue;
            result.add(new FrameRayContext(frame, pose, from, to,
                    pose.transformPositionInverse(from), pose.transformPositionInverse(to), clippedFrom, clippedTo,
                    pose.transformPositionInverse(clippedFrom), pose.transformPositionInverse(clippedTo)));
        }
        return java.util.List.copyOf(result);
    }

    /** Full-volume frame candidates for bounded body-box queries; ray-context semantics remain unchanged. */
    public static java.util.List<FrameRayContext> frameBoxContexts(net.minecraft.world.level.Level level,
            AABB worldBox, float renderPartialTick) {
        return frameBoxQuery(level, worldBox, renderPartialTick).contexts();
    }

    /** Full-volume candidates plus whether every SDK-returned candidate was validly classified. */
    public static FrameBoxQuery frameBoxQuery(net.minecraft.world.level.Level level,
            AABB worldBox, float renderPartialTick) {
        return frameBoxQuery(level, worldBox, renderPartialTick, FrameBoxPose.RENDER_OR_CURRENT);
    }

    /** Explicit physical endpoint pose policy; unlike rendering this always captures logical pose. */
    public static FrameBoxQuery frameBoxEndpointQuery(net.minecraft.world.level.Level level, AABB worldBox) {
        return frameBoxQuery(level, worldBox, 0f, FrameBoxPose.LOGICAL_ENDPOINT);
    }

    private enum FrameBoxPose { RENDER_OR_CURRENT, LOGICAL_ENDPOINT }

    private static FrameBoxQuery frameBoxQuery(net.minecraft.world.level.Level level,
            AABB worldBox, float renderPartialTick, FrameBoxPose posePolicy) {
        if (level == null || !validBox(worldBox)) return new FrameBoxQuery(java.util.List.of(), false);
        Vec3 from = new Vec3(worldBox.minX, worldBox.minY, worldBox.minZ);
        Vec3 to = new Vec3(worldBox.maxX, worldBox.maxY, worldBox.maxZ);
        var result = new java.util.ArrayList<FrameRayContext>();
        boolean complete = true;
        for (SubLevel frame : candidates(level, new BoundingBox3d(from, to).expand(1.0))) {
            if (frame == null) { complete = false; continue; }
            if (frame.isRemoved() || frame.getLevel() != level || frame.getPlot() == null
                    || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame
                    || level.isClientSide && !(frame instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                    || !level.isClientSide && !(frame instanceof ServerSubLevel)) { complete = false; continue; }
            try {
                var pose = captureFrameBoxPose(frame, posePolicy == FrameBoxPose.LOGICAL_ENDPOINT, level, renderPartialTick);
                if (pose == null) { complete = false; continue; }
                AABB frameBounds = frameWorldBounds(frame, pose, 0);
                if (!validBox(frameBounds)) { complete = false; continue; }
                if (!frameBounds.intersects(worldBox)) continue;
                Vec3 localFrom = pose.transformPositionInverse(from);
                Vec3 localTo = pose.transformPositionInverse(to);
                if (!finite(localFrom) || !finite(localTo)) { complete = false; continue; }
                result.add(new FrameRayContext(frame, pose, from, to,
                    localFrom, localTo, from, to, localFrom, localTo));
            } catch (RuntimeException invalidCandidate) {
                complete = false;
            }
        }
        return new FrameBoxQuery(java.util.List.copyOf(result), complete);
    }

    /** Candidate completeness concerns enumeration/pose validity, not whether downstream SAT accepts scale. */
    static dev.ryanhcode.sable.companion.math.Pose3dc captureFrameBoxPose(SubLevel frame, boolean endpoint,
            net.minecraft.world.level.Level level, float partialTick) {
        var temporal=com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.activeExact(level,frame);
        if(temporal!=null)return endpoint?temporal.to(frame):temporal.poseAt(frame,partialTick);
        return captureFrameBoxPose(endpoint, frame::logicalPose, () -> level.isClientSide ? renderPose(frame, partialTick)
                : level instanceof LevelPoseProviderExtension provider ? provider.sable$getPose(frame) : frame.logicalPose());
    }

    static dev.ryanhcode.sable.companion.math.Pose3dc captureFrameBoxPose(boolean endpoint,
            java.util.function.Supplier<dev.ryanhcode.sable.companion.math.Pose3dc> logical,
            java.util.function.Supplier<dev.ryanhcode.sable.companion.math.Pose3dc> ordinary) {
        return captureFrameBoxPose((endpoint ? logical : ordinary).get());
    }

    static dev.ryanhcode.sable.companion.math.Pose3dc captureFrameBoxPose(
            dev.ryanhcode.sable.companion.math.Pose3dc selected) {
        return selected == null ? null : new dev.ryanhcode.sable.companion.math.Pose3d().set(selected);
    }

    public record FrameBoxQuery(java.util.List<FrameRayContext> contexts, boolean complete) {
        public FrameBoxQuery { contexts = java.util.List.copyOf(contexts); }
    }

    private static boolean validBox(AABB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && box.minX <= box.maxX && box.minY <= box.maxY && box.minZ <= box.maxZ;
    }

    /** Resolve an already-loaded storage block through Sable's owning plot; ordinary world positions stay unchanged. */
    public static TargetPoint currentTargetPoint(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.isOutsideBuildHeight(pos)) return null;
        var container = SubLevelContainer.getContainer(level);
        var plot = container.getPlot(new net.minecraft.world.level.ChunkPos(pos));
        if (plot == null || !plot.contains(new net.minecraft.world.level.ChunkPos(pos)))
            return loaded(level, pos) ? new TargetPoint(Vec3.atCenterOf(pos), null) : null;
        var sub = plot.getSubLevel();
        if (!(sub instanceof ServerSubLevel server) || server.isRemoved() || server.getLevel() != level
                || !server.getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))
                || container.getSubLevel(server.getUniqueId()) != server || !loaded(level, pos)) return null;
        var pose = level instanceof LevelPoseProviderExtension provider ? provider.sable$getPose(server) : server.logicalPose();
        Vec3 point = pose.transformPosition(Vec3.atCenterOf(pos));
        return finite(point) && level.getWorldBorder().isWithinBounds(point)
                ? new TargetPoint(point, server.getUniqueId()) : null;
    }

    public record TargetPoint(Vec3 worldPoint, UUID frameId) { }

    /** Capture only a determinate query result; frame coordinates retain the exact shape intersection. */
    public static com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget capture(Result result, Vec3 missEndpoint) {
        if (result == null || result.state() == State.UNKNOWN) return null;
        if (result.state() == State.MISS) return targetOrNull(missEndpoint, null, null);
        if (result.frameHit() != null) {
            Hit hit=result.frameHit();
            return hit.storagePoint() == null ? null : targetOrNull(hit.storagePoint(), hit.frameId(), hit.storagePos());
        }
        return result.worldHit() == null ? null : targetOrNull(result.worldHit().getLocation(), null, null);
    }

    private static com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget targetOrNull(
            Vec3 point, UUID frameId, BlockPos cell) {
        try { return frameId == null
                ? com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.world(point)
                : com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.frame(point, frameId, cell); }
        catch (IllegalArgumentException | NullPointerException invalid) { return null; }
    }

    /** Resolve against current frame pose and non-forcing known residency; never falls back across coordinates. */
    public static Vec3 resolveCapturedTarget(net.minecraft.world.level.Level level,
            com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        if (level == null || target == null) return null;
        Vec3 point=target.point();
        if (target.frameId() == null) {
            if (!finite(point) || !level.getWorldBorder().isWithinBounds(point)) return null;
            BlockPos pos=BlockPos.containing(point);
            if (level instanceof ServerLevel server) return loaded(server,pos) ? point : null;
            return level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false) == null ? null : point;
        }
        UUID id=target.frameId();
        SubLevel sub=SubLevelContainer.getContainer(level).getSubLevel(id);
        BlockPos cell=target.frameCell();
        if (sub == null || sub.isRemoved() || sub.getLevel()!=level || sub.getPlot()==null
                || SubLevelContainer.getContainer(level).getSubLevel(id)!=sub
                || !sub.getPlot().contains(new net.minecraft.world.level.ChunkPos(cell))) return null;
        boolean known=level instanceof ServerLevel server
                ? knownServerBlock(server,sub,cell)!=null : knownClientBlock(level,sub,cell)!=null;
        if (!known) return null;
        var pose=pose(level,sub);
        if(pose==null) return null;
        Vec3 world=pose.transformPosition(point);
        return finite(world) && level.getWorldBorder().isWithinBounds(world) ? world : null;
    }

    /** Current native Sable tracking frame for a live actor; never inferred from nearby geometry. */
    public record ActorPoint(Vec3 worldPoint, Vec3 storagePoint, UUID frameId) { }

    /** Side-neutral native-frame point lookup; each side uses its own non-forcing known-residency probe. */
    public static ActorPoint currentActorPoint(net.minecraft.world.level.Level level, Entity actor) {
        if (level == null || actor == null || actor.level() != level || actor.isRemoved() || !finite(actor.position())
                || !level.getWorldBorder().isWithinBounds(actor.position())) return null;
        if (actor instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending pending
                && pending.sable$frameAnchorInitializationPending()) return null;
        if (!(actor instanceof EntityMovementExtension movement)) return new ActorPoint(actor.position(), actor.position(), null);
        SubLevel tracked = movement.sable$getTrackingSubLevel();
        if (tracked == null) return new ActorPoint(actor.position(), actor.position(), null);
        if (tracked.isRemoved() || tracked.getLevel() != level || tracked.getPlot() == null) return null;
        var container = SubLevelContainer.getContainer(level);
        if (container.getSubLevel(tracked.getUniqueId()) != tracked) return null;
        var selectedPose = pose(level, tracked);
        if (selectedPose == null) return null;
        Vec3 local = selectedPose.transformPositionInverse(actor.position());
        if (!finite(local)) return null;
        var global = new net.minecraft.world.level.ChunkPos(BlockPos.containing(local));
        if (!resident(level, tracked, global)) return null;
        return new ActorPoint(actor.position(), local, tracked.getUniqueId());
    }

    /** Project a storage point only through its exact live frame and known storage chunk. */
    public static Vec3 projectActorPoint(net.minecraft.world.level.Level level, UUID frameId, Vec3 storagePoint) {
        return projectActorPoint(level, frameId, storagePoint, false, 1f);
    }

    /** Projects through the validated current render pose for presentation only. */
    public static Vec3 projectActorPoint(net.minecraft.world.level.Level level, UUID frameId, Vec3 storagePoint,
                                         boolean render, float partialTick) {
        if (level == null || frameId == null || storagePoint == null || !finite(storagePoint)
                || render && !Float.isFinite(partialTick)) return null;
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        SubLevel sub = container.getSubLevel(frameId);
        if (sub == null || sub.isRemoved() || sub.getLevel() != level
                || container.getSubLevel(frameId) != sub || sub.getPlot() == null) return null;
        var globalChunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(storagePoint));
        if (!resident(level, sub, globalChunk)) return null;
        var selectedPose = render && level.isClientSide && sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel
                ? renderPose(sub, net.minecraft.util.Mth.clamp(partialTick, 0f, 1f)) : pose(level, sub);
        if (selectedPose == null) return null;
        Vec3 point = selectedPose.transformPosition(storagePoint);
        return finite(point) && level.getWorldBorder().isWithinBounds(point) ? point : null;
    }

    /** Movement-entry restore requires physical full residency at both adjoining poses without forcing chunks. */
    public static Vec3 projectActorPointForOwnedMovement(ServerLevel level, UUID frameId, Vec3 storagePoint,
            dev.ryanhcode.sable.companion.math.Pose3dc entryPose) {
        if (level == null || frameId == null || storagePoint == null || entryPose == null || !finite(storagePoint)) return null;
        var currentWorld = projectActorPoint(level, frameId, storagePoint);
        if (!physicalWorldPointReady(level, currentWorld)) return null;
        var entryWorld = entryPose.transformPosition(storagePoint);
        return physicalWorldPointReady(level, entryWorld) ? entryWorld : null;
    }

    private static boolean physicalWorldPointReady(ServerLevel level, Vec3 point) {
        if (level == null || point == null || !finite(point) || !level.getWorldBorder().isWithinBounds(point)) return false;
        var pos = BlockPos.containing(point);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
    }

    /** Rebuilds a velocity-basis point at the body's actual present world position; never reuses old storage. */
    public static ActorPoint pointAtWorldPosition(net.minecraft.world.level.Level level, UUID frameId, Vec3 worldPoint) {
        return pointAtWorldPosition(level, frameId, worldPoint, false, 1f);
    }

    /** Render-pose inverse used only to preserve the first client presentation tick across ownership entry. */
    public static ActorPoint pointAtWorldPosition(net.minecraft.world.level.Level level, UUID frameId, Vec3 worldPoint,
                                                   boolean render, float partialTick) {
        if (level == null || worldPoint == null || !finite(worldPoint) || !level.getWorldBorder().isWithinBounds(worldPoint)) return null;
        if (render && (!level.isClientSide || !Float.isFinite(partialTick))) return null;
        if (frameId == null) return new ActorPoint(worldPoint, worldPoint, null);
        SubLevel sub = SubLevelContainer.getContainer(level).getSubLevel(frameId);
        if (sub == null || sub.isRemoved() || sub.getLevel() != level
                || SubLevelContainer.getContainer(level).getSubLevel(frameId) != sub || sub.getPlot() == null) return null;
        var selectedPose = render && sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel
                ? renderPose(sub, net.minecraft.util.Mth.clamp(partialTick, 0f, 1f)) : pose(level, sub);
        if (selectedPose == null) return null;
        Vec3 storage = selectedPose.transformPositionInverse(worldPoint);
        if (!finite(storage) || !resident(level, sub,
                new net.minecraft.world.level.ChunkPos(BlockPos.containing(storage)))) return null;
        return new ActorPoint(worldPoint, storage, frameId);
    }

    private static dev.ryanhcode.sable.companion.math.Pose3dc pose(net.minecraft.world.level.Level level, SubLevel sub) {
        var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.activeExact(level, sub);
        if (temporal != null) return temporal.to(sub);
        return level instanceof ServerLevel && level instanceof LevelPoseProviderExtension provider
                ? provider.sable$getPose(sub) : sub.logicalPose();
    }

    private static Iterable<SubLevel> candidates(net.minecraft.world.level.Level level, BoundingBox3d query) {
        var scope=com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.activeFor(level);
        if(scope==null)return Sable.HELPER.getAllIntersecting(level,query);
        var selected=scope.candidates(level,query,Sable.HELPER.getAllIntersecting(level,query));
        if(!scope.ready())throw new com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.TemporalUnavailableException(scope.failure());
        return selected;
    }

    /** Validated live frame orientation; does not discover/select a parent or force residency. */
    public static org.joml.Quaterniond frameOrientation(net.minecraft.world.level.Level level, UUID frameId,
                                                        Vec3 storagePoint, boolean render, float partialTick) {
        if (level == null || frameId == null || storagePoint == null || !finite(storagePoint)) return null;
        SubLevel sub = SubLevelContainer.getContainer(level).getSubLevel(frameId);
        if (sub == null || sub.isRemoved() || sub.getLevel() != level || sub.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(frameId) != sub) return null;
        var chunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(storagePoint));
        if (!resident(level, sub, chunk)) return null;
        var framePose = render && level.isClientSide && sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel
                ? renderPose(sub, partialTick) : pose(level, sub);
        if (framePose == null) return null;
        var q = framePose.orientation();
        return com.juicyslew.moonstation14.entities.LooseFrameOrientation.normalized(q.x(), q.y(), q.z(), q.w());
    }

    /** Render-time client pose; authority queries intentionally continue using logical poses. */
    public static dev.ryanhcode.sable.companion.math.Pose3dc renderPose(SubLevel sub, float partialTick) {
        return sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client
                ? client.renderPose(partialTick) : null;
    }

    /** Known client storage lookup: own plot and resident chunk only; never uses a forcing Level getter. */
    public static net.minecraft.world.level.block.state.BlockState knownClientBlock(
            net.minecraft.world.level.Level level, SubLevel sub, BlockPos globalStoragePos) {
        if (level == null || sub == null || !(sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                || sub.isRemoved() || sub.getLevel() != level || sub.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(sub.getUniqueId()) != sub
                || !sub.getPlot().contains(new net.minecraft.world.level.ChunkPos(globalStoragePos))) return null;
        var chunk = sub.getPlot().getChunk(sub.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(globalStoragePos)));
        return chunk == null ? null : chunk.getBlockState(globalStoragePos);
    }

    public static net.minecraft.world.level.block.entity.BlockEntity knownClientBlockEntity(
            net.minecraft.world.level.Level level, SubLevel sub, BlockPos globalStoragePos) {
        return sub == null ? null : knownBlockEntity(level, sub, globalStoragePos);
    }

    /** Existing block entity from the already-loaded owning chunk; never consults Level#getBlockEntity. */
    public static net.minecraft.world.level.block.entity.BlockEntity knownBlockEntity(
            net.minecraft.world.level.Level level, SubLevel frame, BlockPos pos) {
        if (frame == null) return existingBlockEntity(level, pos);
        if (level == null || frame.getLevel() != level || frame.isRemoved() || frame.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame
                || !frame.getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))) return null;
        if (level.isClientSide) {
            if (knownClientBlock(level, frame, pos) == null) return null;
            var chunk = frame.getPlot().getChunk(frame.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(pos)));
            return chunk == null ? null : chunk.getBlockEntities().get(pos);
        }
        if (!(level instanceof ServerLevel server) || knownServerBlock(server, frame, pos) == null) return null;
        var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                ? null : chunk.getBlockEntities().get(pos);
    }

    /** Existing entity only: a missing or non-FULL chunk remains absent and is never materialized. */
    public static net.minecraft.world.level.block.entity.BlockEntity existingBlockEntity(
            net.minecraft.world.level.Level level, BlockPos pos) {
        if (level == null || pos == null || level.isOutsideBuildHeight(pos)) return null;
        var access = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        return access instanceof net.minecraft.world.level.chunk.LevelChunk chunk
                ? chunk.getBlockEntities().get(pos) : null;
    }

    /** Server-side equivalent for examination traversal: exact owning plot and FULL chunk, never loads. */
    public static net.minecraft.world.level.block.state.BlockState knownServerBlock(
            ServerLevel level, SubLevel sub, BlockPos globalStoragePos) {
        if (level == null || sub == null || !(sub instanceof ServerSubLevel) || sub.isRemoved()
                || sub.getLevel() != level || sub.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(sub.getUniqueId()) != sub
                || !sub.getPlot().contains(new net.minecraft.world.level.ChunkPos(globalStoragePos))) return null;
        var chunk = level.getChunkSource().getChunkNow(globalStoragePos.getX() >> 4, globalStoragePos.getZ() >> 4);
        return chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                ? null : chunk.getBlockState(globalStoragePos);
    }

    private static boolean resident(net.minecraft.world.level.Level level, SubLevel sub,
                                    net.minecraft.world.level.ChunkPos globalChunk) {
        if (sub.getPlot() == null || !sub.getPlot().contains(globalChunk)) return false;
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(globalChunk.x, globalChunk.z);
            return chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
        }
        return sub.getPlot().getChunk(sub.getPlot().toLocal(globalChunk)) != null;
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
    }

    @FunctionalInterface
    public interface ContextFactory {
        ClipContext create(BlockGetter getter, Vec3 from, Vec3 to);
    }

    /** Factory preserves caller-specific block/fluid and dynamic-shape context semantics. */
    public static Result clip(ServerLevel level, Vec3 from, Vec3 to, int maxChunks,
                              double margin, int chunkMargin, ContextFactory contexts) {
        return clip(level, from, to, maxChunks, margin, chunkMargin, 0f, contexts);
    }

    /** Client capture uses the render pose captured once for both clipping and coordinate extraction. */
    public static Result clip(net.minecraft.world.level.Level level, Vec3 from, Vec3 to, int maxChunks,
                              double margin, int chunkMargin, float renderPartialTick, ContextFactory contexts) {
        if (level == null) return clipUnprofiled(level, from, to, maxChunks, margin, chunkMargin, renderPartialTick, contexts);
        var profiler = level.getProfiler();
        profiler.push("moonstation14_frame_ray");
        try {
            return clipUnprofiled(level, from, to, maxChunks, margin, chunkMargin, renderPartialTick, contexts);
        } finally {
            profiler.pop();
        }
    }

    private static Result clipUnprofiled(net.minecraft.world.level.Level level, Vec3 from, Vec3 to, int maxChunks,
                              double margin, int chunkMargin, float renderPartialTick, ContextFactory contexts) {
        BlockHitResult nearestTerrain;
        try {
            LoadedBlockGetter terrain = level instanceof ServerLevel server
                    ? LoadedBlockGetter.alongWithChunkMargin(server, from, to, margin, maxChunks, chunkMargin)
                    : LoadedBlockGetter.clientAlongWithChunkMargin(level, from, to, margin, maxChunks, chunkMargin);
            nearestTerrain = terrain.clip(contexts.create(terrain, from, to));
        } catch (LoadedBlockGetter.UnknownChunk unknown) {
            return new Result(State.UNKNOWN, null, null);
        }

        Hit nearestFrame = null;
        var candidates = new BoundingBox3d(from, to).expand(margin);
        for (SubLevel candidate : candidates(level, candidates)) {
            if (candidate.isRemoved() || candidate.getLevel() != level || candidate.getPlot() == null
                    || level.isClientSide && !(candidate instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                    || !level.isClientSide && !(candidate instanceof ServerSubLevel)) continue;

            SubLevel sub = candidate;
            var pose = level.isClientSide ? renderPose(sub, renderPartialTick)
                    : level instanceof LevelPoseProviderExtension provider
                    ? provider.sable$getPose(sub) : sub.logicalPose();
            if (pose == null) continue;
            double scale = Math.max(Math.abs(pose.scale().x()),
                    Math.max(Math.abs(pose.scale().y()), Math.abs(pose.scale().z())));
            // Sable's native bounds can be a tight physics/collision AABB and may omit blocks
            // protruding beyond that hull (notably lower door leaves during free fall). Keep
            // the SDK broadphase candidate filter, but clip within the current-pose projection
            // of that candidate's own plot as well as its native bounds.
            AABB worldBounds = frameWorldBounds(sub, pose, margin);
            Vec3 clippedFrom = rayEntry(worldBounds, from, to);
            Vec3 clippedTo = rayExit(worldBounds, from, to);
            if (clippedFrom == null || clippedTo == null) continue;

            Vec3 storageFrom = pose.transformPositionInverse(clippedFrom);
            Vec3 storageTo = pose.transformPositionInverse(clippedTo);
            AABB storageBounds = new BoundingBox3d(sub.getPlot().getBoundingBox())
                    .toMojang().inflate(1.0 + margin);
            LoadedBlockGetter storage = level instanceof ServerLevel server
                    ? LoadedBlockGetter.forFrame(server, sub.getPlot(), storageBounds, maxChunks)
                    : LoadedBlockGetter.clientForFrame(level, sub.getPlot(), storageBounds, maxChunks);
            try {
                BlockHitResult localHit = storage.clip(contexts.create(storage, storageFrom, storageTo));
                if (localHit.getType() != HitResult.Type.BLOCK) continue;

                Vec3 worldPoint = pose.transformPosition(localHit.getLocation());
                if (!finite(worldPoint)) return new Result(State.UNKNOWN, null, null);
                double candidateDistance = from.distanceToSqr(worldPoint);
                double terrainDistance = terrainDistance(from, nearestTerrain);
                if (candidateDistance < terrainDistance
                        && (nearestFrame == null
                        || candidateDistance < from.distanceToSqr(nearestFrame.worldPoint()))) {
                    nearestFrame = new Hit(localHit.getBlockPos(), localHit.getDirection(),
                            worldPoint, sub.getUniqueId(), localHit.getLocation());
                }
            } catch (LoadedBlockGetter.UnknownChunk unknown) {
                return new Result(State.UNKNOWN, null, null);
            }
        }

        boolean frameWins = nearestFrame != null
                && (nearestTerrain == null || nearestTerrain.getType() != HitResult.Type.BLOCK
                || from.distanceToSqr(nearestFrame.worldPoint()) < terrainDistance(from, nearestTerrain));
        boolean hit = nearestFrame != null
                || nearestTerrain != null && nearestTerrain.getType() == HitResult.Type.BLOCK;
        return new Result(hit ? State.HIT : State.MISS,
                frameWins ? null : nearestTerrain, nearestFrame);
    }

    /** Recheck exact engine identity, own plot admission, and current loaded storage before mutation. */
    public static boolean revalidate(ServerLevel level, Hit hit) {
        if (level == null || hit == null || hit.frameId() == null || hit.storagePos() == null) return false;
        SubLevel current = SubLevelContainer.getContainer(level).getSubLevel(hit.frameId());
        if (!(current instanceof ServerSubLevel sub) || sub.isRemoved() || sub.getLevel() != level
                || SubLevelContainer.getContainer(level).getSubLevel(hit.frameId()) != sub || sub.getPlot() == null) return false;
        return knownServerBlock(level, sub, hit.storagePos()) != null;
    }

    private static double terrainDistance(Vec3 from, BlockHitResult hit) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                ? from.distanceToSqr(hit.getLocation()) : Double.POSITIVE_INFINITY;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static Vec3 rayEntry(AABB bounds, Vec3 from, Vec3 to) {
        if (bounds.contains(from)) return from;
        return bounds.clip(from, to).orElse(null);
    }

    private static Vec3 rayExit(AABB bounds, Vec3 from, Vec3 to) {
        if (bounds.contains(to)) return to;
        return bounds.clip(to, from).orElse(null);
    }

    /** Same native-bounds/own-plot envelope used by physical clips, with their exact margin convention. */
    private static AABB frameWorldBounds(SubLevel sub, dev.ryanhcode.sable.companion.math.Pose3dc pose,
                                         double margin) {
        double scale = Math.max(Math.abs(pose.scale().x()),
                Math.max(Math.abs(pose.scale().y()), Math.abs(pose.scale().z())));
        return sub.boundingBox().toMojang().minmax(
                new BoundingBox3d(sub.getPlot().getBoundingBox()).transform(pose).toMojang())
                .inflate(margin * scale);
    }
}
