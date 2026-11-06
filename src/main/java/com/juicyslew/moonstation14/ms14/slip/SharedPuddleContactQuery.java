package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded, read-only puddle contact lookup shared by slip and friction adapters. */
public final class SharedPuddleContactQuery {
    private static final int FULL_BODY_MAX_CELLS = 16;
    private static final double FULL_BODY_MAX_WIDTH = 1.5d;
    private static final double FULL_BODY_MAX_HEIGHT = 3d;
    private static final int FEET_WORLD_MAX_CELLS = 578; // 17 x 2 x 17 for the legacy 16-block footprint.

    private SharedPuddleContactQuery() { }

    public enum State { AVAILABLE, UNAVAILABLE, UNSUPPORTED }

    /** Geometry and source are captured in one coordinate basis; frameId is stable source identity. */
    public record Contact(PuddleBlockEntity source, BlockPos sourcePosition, UUID frameId,
                          AABB sourceShape, AABB actorContactGeometry, double triggerRatio) {
        public Contact {
            java.util.Objects.requireNonNull(source);
            sourcePosition = sourcePosition.immutable();
            sourceShape = sourceShape == null ? null : sourceShape;
            actorContactGeometry = actorContactGeometry == null ? null : actorContactGeometry;
        }
    }

    public record Result(State state, UUID frameId, List<Contact> contacts) {
        public Result { contacts = List.copyOf(contacts); }
        public boolean available() { return state == State.AVAILABLE; }
    }

    /** Current world contact query. Existing callers retain their exact unrotated-world bounds semantics. */
    public static Result query(LivingEntity actor, AABB actorGeometry) {
        return query(actor, actorGeometry, false);
    }

    /** Feet-band variant for friction projection, using frame-local up when aboard. */
    public static Result queryFeetBand(LivingEntity actor) {
        return query(actor, actor == null ? null : actor.getBoundingBox(), true);
    }

    private static Result query(LivingEntity actor, AABB actorGeometry, boolean feetBand) {
        if (actor == null || actorGeometry == null || actor.level() == null) return unavailable(null);
        Level level = actor.level();
        LoadedFrameRayQuery.ActorPoint point = LoadedFrameRayQuery.currentActorPoint(level, actor);
        if (point == null) return unavailable(null);
        AABB queryBounds = actorGeometry;
        PuddleContactGeometry frameGeometry = null;
        dev.ryanhcode.sable.sublevel.SubLevel capturedFrame = null;
        NativeFrameInterval.PoseValue capturedPose = null;
        if (point.frameId() != null) {
            var frame = SubLevelContainer.getContainer(level).getSubLevel(point.frameId());
            if (frame == null || frame.isRemoved() || frame.getLevel() != level
                    || frame.getPlot() == null || frame.logicalPose() == null
                    || level.isClientSide && !(frame instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                    || !level.isClientSide && !(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel)) return unavailable(point.frameId());
            capturedFrame = frame;
            var pose = frame.logicalPose();
            AABB contactBody = actorGeometry;
            if (feetBand) contactBody = new AABB(actorGeometry.minX, actorGeometry.minY, actorGeometry.minZ,
                    actorGeometry.maxX, actorGeometry.minY + Math.min(.25d, actorGeometry.getYsize()), actorGeometry.maxZ);
            try {
                capturedPose = NativeFrameInterval.PoseValue.capture(pose);
                frameGeometry = PuddleContactGeometry.capture(contactBody,
                        SubLevelEntityCollision.getHitBoxYaw(pose), pose);
            } catch (IllegalArgumentException invalidPose) {
                return new Result(State.UNSUPPORTED, point.frameId(), List.of());
            }
            queryBounds = frameGeometry.localCandidateBounds();
        } else if (feetBand) {
            queryBounds = new AABB(actorGeometry.minX, actorGeometry.minY, actorGeometry.minZ,
                    actorGeometry.maxX, actorGeometry.minY + Math.min(.25d, actorGeometry.getYsize()), actorGeometry.maxZ);
        }
        boolean worldFeetBand = feetBand && frameGeometry == null;
        if (!valid(queryBounds, feetBand, worldFeetBand)) return unavailable(point.frameId());
        int minX = floor(queryBounds.minX), maxX = floor(Math.nextDown(queryBounds.maxX));
        int minY = floor(queryBounds.minY), maxY = floor(Math.nextDown(queryBounds.maxY));
        int minZ = floor(queryBounds.minZ), maxZ = floor(Math.nextDown(queryBounds.maxZ));
        long count = (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
        if (!withinCellLimit(count, worldFeetBand)) return unavailable(point.frameId());
        ArrayList<Contact> contacts = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX,minY,minZ,maxX,maxY,maxZ)) {
            var frame = capturedFrame;
            var state = frame == null ? (level instanceof ServerLevel server
                    ? (server.hasChunkAt(pos) ? server.getBlockState(pos) : null)
                    : level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) instanceof net.minecraft.world.level.chunk.LevelChunk chunk
                    ? chunk.getBlockState(pos) : null)
                    : (level instanceof ServerLevel server ? LoadedFrameRayQuery.knownServerBlock(server, frame, pos)
                    : LoadedFrameRayQuery.knownClientBlock(level, frame, pos));
            if (state == null) return unavailable(point.frameId());
            if (!state.is(ModBlocks.PUDDLE.get())) continue;
            var blockEntity = LoadedFrameRayQuery.knownBlockEntity(level, frame, pos);
            if (!(blockEntity instanceof PuddleBlockEntity puddle)) continue;
            AABB shape = state.getShape(level, pos, CollisionContext.empty()).bounds().move(pos);
            if (frameGeometry != null) {
                PuddleContactGeometry.Contact exact = frameGeometry.contact(shape, pos);
                if (exact.intersects()) contacts.add(new Contact(puddle, pos, point.frameId(), shape, queryBounds, exact.triggerRatio()));
            } else if (queryBounds.intersects(shape)) {
                contacts.add(new Contact(puddle, pos, point.frameId(), shape, queryBounds,
                        SlipSystem.intersectionRatio(queryBounds, shape, pos)));
            }
        }
        if (!sameQueryContext(level, actor, point, capturedFrame, capturedPose)) return unavailable(point.frameId());
        return new Result(State.AVAILABLE, point.frameId(), contacts);
    }

    private static boolean sameQueryContext(Level level, LivingEntity actor, LoadedFrameRayQuery.ActorPoint initial,
            dev.ryanhcode.sable.sublevel.SubLevel frame, NativeFrameInterval.PoseValue pose) {
        LoadedFrameRayQuery.ActorPoint current = LoadedFrameRayQuery.currentActorPoint(level, actor);
        if (!initial.equals(current)) return false;
        if (initial.frameId() == null) return frame == null && pose == null;
        if (frame == null || frame.isRemoved() || frame.getLevel() != level || frame.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(initial.frameId()) != frame) return false;
        var currentPose = frame.logicalPose();
        if (currentPose == null) return false;
        try {
            return NativeFrameInterval.PoseValue.capture(currentPose).equals(pose);
        } catch (IllegalArgumentException invalidPose) {
            return false;
        }
    }

    static Result unavailable(UUID frameId) { return new Result(State.UNAVAILABLE, frameId, List.of()); }
    private static int floor(double v) { return (int)Math.floor(v); }
    static boolean valid(AABB b, boolean feetBand, boolean worldFeetBand) {
        if (b == null || !finite(b.minX)||!finite(b.minY)||!finite(b.minZ)||!finite(b.maxX)||!finite(b.maxY)||!finite(b.maxZ)) return false;
        if (feetBand && worldFeetBand)
            return b.getXsize() > 0 && b.getZsize() > 0 && b.getYsize() > 0
                    && b.getXsize() <= 16d && b.getZsize() <= 16d && b.getYsize() <= .25d;
        return b.getXsize() > 0 && b.getZsize() > 0 && b.getYsize() > 0
                && b.getXsize() <= FULL_BODY_MAX_WIDTH && b.getZsize() <= FULL_BODY_MAX_WIDTH
                && b.getYsize() <= FULL_BODY_MAX_HEIGHT;
    }
    static boolean withinCellLimit(long count, boolean worldFeetBand) {
        return count > 0 && count <= (worldFeetBand ? FEET_WORLD_MAX_CELLS : FULL_BODY_MAX_CELLS);
    }
    private static boolean finite(double value) { return Double.isFinite(value); }
}
