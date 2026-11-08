package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Read-only body-eye first-hit probe, not pickup/ownership authority. Call again before any future transfer.
 * Getter reads never load chunks; arbitrary mod shape callbacks may independently access body.level() and
 * load chunks outside this getter (the same limitation as ApcBodyTargetProbe).
 */
public final class BodyItemTargetProbe {
    public static final double SYRINGE_CONTAINER_REACH = 2.0;
    /** Minecraft blocks, a defensive ray ceiling, not a conversion from SS14 interaction units. */
    public static final double MAX_REACH = 5.0;
    /** Defensive loose-throw ray ceiling in Minecraft blocks; profile range remains authoritative. */
    public static final double MAX_THROW_REACH = 16.0;
    /** Ground placement aim distance; deliberately independent of pickup/storage reach. */
    public static final double DROP_AIM_REACH = 3.5;
    private static final double DROP_RAY_ENDPOINT_EPSILON = 1.0e-5;
    private static final double DROP_SURFACE_MATCH_EPSILON = 1.0e-5;
    public static final int MAX_CHUNKS = 16;
    public static final double TARGET_MARGIN = 0.20;

    private BodyItemTargetProbe() { }

    public record Target(UUID itemId, Vec3 hitLocation) { }

    public enum ProbeFailure { NONE, INVALID_CONTEXT, INVALID_REACH, INVALID_LOOK, START_OUTSIDE_WORLD,
        RAY_OUTSIDE_BORDER, CHUNK_LIMIT, MISSING_CHUNK, BLOCKED_OR_MISSED_TARGET, NOMINATED_NULL }
    public record ProbeDiagnostic(ProbeFailure failure, Vec3 from, Vec3 requestedEnd, Vec3 clippedEnd,
                                  int minBuildHeight, int maxBuildHeight, int minChunkX, int maxChunkX,
                                  int minChunkZ, int maxChunkZ, String missingChunk,
                                  HitResult.Type blockHitType, BlockPos blockHitPos, Vec3 blockHitLocation) { }
    public record SelectedProbe(Target target, ProbeDiagnostic diagnostic) { }
    private record CandidateSet(java.util.List<Target> candidates, ProbeDiagnostic diagnostic) { }

    /** At most this many visible ray candidates are enumerated; overflow fails closed. */
    public static final int MAX_VISIBLE_CANDIDATES = 32;
    private static final int MAX_ENUMERATED_CANDIDATES = 256;

    /** Deterministic visible pile members along the body-eye ray. */
    public static java.util.List<Target> visibleCandidates(ServerLevel level, LivingEntity body, double reach) {
        return visibleCandidateSet(level, body, reach).candidates();
    }

    /** Same authoritative ray decision with read-only failure context for bounded diagnostics. */
    public static SelectedProbe probeSelectedDiagnostic(ServerLevel level, LivingEntity body, UUID nominated,
                                                         double reach) {
        if (nominated == null) {
            var d = diagnostic(ProbeFailure.NOMINATED_NULL, level, body, null, null, 0, 0, 0, 0, 0, 0,
                    null, null);
            return new SelectedProbe(null, d);
        }
        // A selected UID is an explicit client nomination (e.g. a click on a pile member),
        // not an assertion that the server's current look ray still intersects it. Revalidate
        // the exact live entity against current reach and block LOS instead.
        if (level == null || body == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !Double.isFinite(reach) || reach <= 0 || reach > MAX_REACH)
            return new SelectedProbe(null, diagnostic(ProbeFailure.INVALID_CONTEXT, level, body, null, null,
                    level == null ? 0 : level.getMinBuildHeight(), level == null ? 0 : level.getMaxBuildHeight(),
                    0, 0, 0, 0, null, null));
        Entity entity = level.getEntity(nominated);
        AABB bounds = entity == null ? null : selectionBounds(entity);
        if (entity == null || entity.level() != level || entity.isRemoved() || !entity.isAddedToLevel()
                || !(entity instanceof ItemEntity || entity instanceof LivingEntity living
                  && PortableBodyCarrying.eligibleLivingTarget(body, living))
                || entity instanceof ItemEntity item && !AttachedItemTargeting.eligible(body.getUUID(),
                        item instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose
                                ? loose.attachment() : java.util.Optional.empty(), false)
                || !probeSelectedGeometry(level, body, entity, reach))
            return new SelectedProbe(null, diagnostic(ProbeFailure.BLOCKED_OR_MISSED_TARGET, level, body,
                    body.getEyePosition(), null, level.getMinBuildHeight(), level.getMaxBuildHeight(), 0, 0, 0, 0, null, null));
        Vec3 eye = body.getEyePosition();
        Vec3 closest = closestPoint(eye, bounds);
        return new SelectedProbe(new Target(nominated, closest), diagnostic(ProbeFailure.NONE, level, body,
                eye, closest, level.getMinBuildHeight(), level.getMaxBuildHeight(), 0, 0, 0, 0, null, closest));
    }

    /** Shared nominated-entity physical admission: nearest-AABB reach and loaded block LOS, not aim ray. */
    public static boolean probeSelectedGeometry(ServerLevel level, LivingEntity body, Entity entity, double reach) {
        if (level == null || body == null || entity == null || level.getServer() == null
                || !level.getServer().isSameThread() || body.level() != level || body.isRemoved()
                || !body.isAlive() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || entity.level() != level || entity.isRemoved() || !entity.isAddedToLevel()
                || level.getEntity(entity.getUUID()) != entity || !Double.isFinite(reach) || reach <= 0
                || reach > MAX_REACH) return false;
        Vec3 eye = body.getEyePosition();
        AABB bounds = selectionBounds(entity);
        return inside(level, eye) && inside(level, entity.position()) && withinReach(body, bounds, reach)
                && clearLine(level, body, eye, bounds);
    }

    /** Attached sprites can expose a small region beyond their physical anchor box. */
    public static AABB selectionBounds(Entity entity) {
        AABB bounds = entity.getBoundingBox();
        return selectionBounds(bounds, entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose
                && loose.attachment().isPresent());
    }

    /** Upward-only tolerance accommodates attached sprites while preserving walls and floor barriers. */
    public static AABB selectionBounds(AABB bounds, boolean attached) {
        return attached ? new AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX,
                bounds.maxY + TARGET_MARGIN, bounds.maxZ) : bounds;
    }

    private static boolean withinReach(LivingEntity body, AABB bounds, double reach) {
        return body != null && withinPhysicalReach(body.getEyePosition(), bounds, reach);
    }
    /** Physical nearest-AABB-point reach shared by server admission and client nomination. */
    public static boolean withinPhysicalReach(Vec3 eye, AABB bounds, double reach) {
        if (eye == null || bounds == null || !finite(eye) || !Double.isFinite(reach) || reach <= 0
                || !Double.isFinite(bounds.minX) || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.maxY)
                || !Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxZ)) return false;
        Vec3 closest = closestPoint(eye, bounds);
        return finite(closest) && eye.distanceToSqr(closest) <= reach * reach;
    }
    private static Vec3 closestPoint(Vec3 from, AABB box) {
        return new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY),
                Mth.clamp(from.z, box.minZ, box.maxZ));
    }
    private static boolean clearLine(ServerLevel level, LivingEntity body, Vec3 from, AABB bounds) {
        Vec3 point = closestPoint(from, bounds);
        if (!inside(level, point)) return false;
        try {
            var query = LoadedFrameRayQuery.clip(level, from, point, MAX_CHUNKS, 0, 1,
                    (getter, rayFrom, rayTo) -> new ClipContext(rayFrom, rayTo, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, body));
            return query.state() == LoadedFrameRayQuery.State.MISS;
        } catch (LoadedBlockGetter.UnknownChunk ignored) { return false; }
    }

    private static CandidateSet visibleCandidateSet(ServerLevel level, LivingEntity body, double reach) {
        int minBuild = level == null ? 0 : level.getMinBuildHeight();
        int maxBuild = level == null ? 0 : level.getMaxBuildHeight();
        if (level == null || body == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body) return candidateFailure(ProbeFailure.INVALID_CONTEXT,
                level, body, null, null, minBuild, maxBuild, 0, 0, 0, 0, null, null);
        if (!Double.isFinite(reach) || reach <= 0 || reach > MAX_REACH)
            return candidateFailure(ProbeFailure.INVALID_REACH, level, body, null, null, minBuild, maxBuild,
                    0, 0, 0, 0, null, null);
        Vec3 from = body.getEyePosition(), look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01)
            return candidateFailure(ProbeFailure.INVALID_LOOK, level, body, from, null, minBuild, maxBuild,
                    0, 0, 0, 0, null, null);
        if (!inside(level, from)) return candidateFailure(ProbeFailure.START_OUTSIDE_WORLD, level, body, from,
                null, minBuild, maxBuild, 0, 0, 0, 0, null, null);
        Vec3 requestedEnd = from.add(look.scale(reach));
        if (!finite(requestedEnd) || !level.getWorldBorder().isWithinBounds(requestedEnd))
            return candidateFailure(ProbeFailure.RAY_OUTSIDE_BORDER, level, body, from, requestedEnd,
                    minBuild, maxBuild, 0, 0, 0, 0, null, null);
        // Keep floor/ceiling clipping on the ray itself for both targeting and throwing.
        Vec3 to = boundedRayEndpoint(from, requestedEnd, minBuild, maxBuild);
        int minX = (Math.min(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) + 1;
        if ((long)(maxX-minX+1)*(maxZ-minZ+1)>MAX_CHUNKS)
            return candidateFailure(ProbeFailure.CHUNK_LIMIT, level, body, from, requestedEnd, minBuild, maxBuild,
                    minX, maxX, minZ, maxZ, null, to);
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)
                return candidateFailure(ProbeFailure.MISSING_CHUNK, level, body, from, requestedEnd,
                        minBuild, maxBuild, minX, maxX, minZ, maxZ, x+","+z, to);
        try {

            var query=LoadedFrameRayQuery.clip(level,from,to,MAX_CHUNKS,0,1,
                    (getter,rayFrom,rayTo)->new ClipContext(rayFrom,rayTo,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            if(query.state()==LoadedFrameRayQuery.State.UNKNOWN)return candidateFailure(ProbeFailure.MISSING_CHUNK,
                    level, body, from, requestedEnd, minBuild, maxBuild, minX, maxX, minZ, maxZ, "ray-query", to);
            BlockHitResult block=query.worldHit();
            double blocker=query.frameHit()!=null?from.distanceTo(query.frameHit().worldPoint()):reach;
            if(block!=null&&block.getType()==HitResult.Type.BLOCK)blocker=Math.min(blocker,from.distanceTo(block.getLocation()));
            java.util.ArrayList<Target> result=new java.util.ArrayList<>();
            int enumerated = 0;
            java.util.ArrayList<ItemEntity> candidates=new java.util.ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(ItemEntity.class),new AABB(from,to).inflate(TARGET_MARGIN + LooseItemQueryGeometry.maximumProfileRadius(false)),e->true,candidates,257);
            if(candidates.size()>256)return candidateFailure(ProbeFailure.BLOCKED_OR_MISSED_TARGET,
                    level, body, from, requestedEnd, minBuild, maxBuild, minX, maxX, minZ, maxZ, "candidate-overflow", to);
            for(Entity entity:candidates) {
                if (++enumerated > MAX_ENUMERATED_CANDIDATES) return candidateFailure(
                        ProbeFailure.BLOCKED_OR_MISSED_TARGET, level, body, from, requestedEnd,
                        minBuild, maxBuild, minX, maxX, minZ, maxZ, "candidate-overflow", to);
                ItemEntity item=(ItemEntity)entity;
                if(item.level()!=level||item.isRemoved()||!item.isAddedToLevel()||level.getEntity(item.getUUID())!=item)continue;
                AABB box=item.getBoundingBox();
                LooseItemQueryGeometry shape=item instanceof LooseItemEntity loose?LooseItemQueryGeometry.forEntity(loose,0):null;
                if(item instanceof LooseItemEntity && shape==null)continue;
                Vec3 hit=shape!=null?shape.inflated(TARGET_MARGIN).clip(from,to):box.inflate(TARGET_MARGIN).contains(from)?from:box.inflate(TARGET_MARGIN).clip(from,to).orElse(null);
                if(hit==null)continue; double dist=from.distanceToSqr(hit);
                double near=shape!=null?shape.minimumProjection(from,look):(look.x>=0?box.minX:box.maxX)*look.x+(look.y>=0?box.minY:box.maxY)*look.y+(look.z>=0?box.minZ:box.maxZ)*look.z-from.dot(look);
                boolean overlap=shape!=null?shape.clip(from,from)!=null:box.contains(from);
                if(!Double.isFinite(dist)||(dist>=blocker*blocker&&!(dist==0&&overlap))||(near>=blocker&&!overlap)||dist>reach*reach)continue;
                result.add(new Target(entity.getUUID(),hit));
            }
            result.sort(java.util.Comparator.comparingDouble((Target t)->from.distanceToSqr(t.hitLocation())).thenComparing(Target::itemId));
            if (result.size() > MAX_VISIBLE_CANDIDATES)
                result.subList(MAX_VISIBLE_CANDIDATES, result.size()).clear();

            return new CandidateSet(java.util.List.copyOf(result), diagnostic(ProbeFailure.NONE, level, body,
                    from, requestedEnd, minBuild, maxBuild, minX, maxX, minZ, maxZ, null, to,
                    block));
        } catch(LoadedBlockGetter.UnknownChunk ignored){return candidateFailure(ProbeFailure.MISSING_CHUNK,
                level, body, from, requestedEnd, minBuild, maxBuild, minX, maxX, minZ, maxZ, "shape-query", to);}
    }

    /** Validates an explicitly nominated member of the bounded body-eye candidate set. */
    @Nullable
    public static Target probeSelected(ServerLevel level, LivingEntity body, UUID nominated, double reach) {
        return probeSelectedDiagnostic(level, body, nominated, reach).target();
    }

    private static CandidateSet candidateFailure(ProbeFailure failure, ServerLevel level, LivingEntity body,
            Vec3 from, Vec3 requestedEnd, int minBuild, int maxBuild, int minX, int maxX, int minZ, int maxZ,
            String missingChunk, Vec3 clippedEnd) {
        return new CandidateSet(java.util.List.of(), diagnostic(failure, level, body, from, requestedEnd,
                minBuild, maxBuild, minX, maxX, minZ, maxZ, missingChunk, clippedEnd));
    }

    private static ProbeDiagnostic diagnostic(ProbeFailure failure, ServerLevel level, LivingEntity body,
            Vec3 from, Vec3 requestedEnd, int minBuild, int maxBuild, int minX, int maxX, int minZ, int maxZ,
            String missingChunk, Vec3 clippedEnd) {
        return diagnostic(failure, level, body, from, requestedEnd, minBuild, maxBuild, minX, maxX,
                minZ, maxZ, missingChunk, clippedEnd, null);
    }

    private static ProbeDiagnostic diagnostic(ProbeFailure failure, ServerLevel level, LivingEntity body,
            Vec3 from, Vec3 requestedEnd, int minBuild, int maxBuild, int minX, int maxX, int minZ, int maxZ,
            String missingChunk, Vec3 clippedEnd, BlockHitResult hit) {
        return new ProbeDiagnostic(failure, from, requestedEnd, clippedEnd, minBuild, maxBuild,
                minX, maxX, minZ, maxZ, missingChunk, hit == null ? null : hit.getType(),
                hit == null || hit.getType() != HitResult.Type.BLOCK ? null : hit.getBlockPos(),
                hit == null ? null : hit.getLocation());
    }

    private static ProbeDiagnostic withFailure(ProbeDiagnostic d, ProbeFailure failure) {
        return new ProbeDiagnostic(failure, d.from(), d.requestedEnd(), d.clippedEnd(), d.minBuildHeight(),
                d.maxBuildHeight(), d.minChunkX(), d.maxChunkX(), d.minChunkZ(), d.maxChunkZ(),
                d.missingChunk(), d.blockHitType(), d.blockHitPos(), d.blockHitLocation());
    }

    /**
     * Loaded-only collider ray for loose throws. The LoadedBlockGetter prevents this probe's block reads
     * from loading chunks; arbitrary shape callbacks may still access the world independently.
     */
    @Nullable
    public static Vec3 loadedAimPoint(ServerLevel level, LivingEntity body, double reach) {
        return loadedAimPoint(level, body, reach, body == null ? null : body.getLookAngle());
    }

    /** Loaded-only aim ray with an explicit normalized direction for server-authorized forced throws. */
    public static Vec3 loadedAimPointAlongDirection(ServerLevel level, LivingEntity body, double reach, Vec3 direction) {
        if (level == null || body == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !Double.isFinite(reach)

                || reach <= 0 || reach > MAX_THROW_REACH) return null;
        Vec3 from = body.getEyePosition(), look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01) return null;
        Vec3 to = from.add(look.scale(reach));
        if (!finite(to) || !inside(level, from) || !inside(level, to)) return null;
        return clipLoadedAim(level, body, from, to);
    }

    /** Resolve a captured hand coordinate against the current frame pose, then clip the bounded ray. */
    @Nullable
    public static Vec3 loadedAimPoint(ServerLevel level, LivingEntity body, double reach,
            com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget target) {
        if (level == null || body == null || target == null || !Double.isFinite(reach)
                || reach <= 0 || reach > MAX_THROW_REACH || body.level() != level
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !level.getServer().isSameThread()) return null;
        Vec3 captured = LoadedFrameRayQuery.resolveCapturedTarget(level, target);
        return loadedAimPoint(level, body, reach, captured);
    }

    /** Aim toward an already-resolved captured coordinate, preserving one geometry snapshot per transaction. */
    @Nullable
    public static Vec3 loadedAimPoint(ServerLevel level, LivingEntity body, double reach, Vec3 captured) {
        if (level == null || body == null || captured == null || !Double.isFinite(reach)
                || reach <= 0 || reach > MAX_THROW_REACH || body.level() != level
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !level.getServer().isSameThread()) return null;
        Vec3 from = body.getEyePosition();
        if (!finite(from) || !finite(captured) || !inside(level, from)) return null;
        Vec3 delta = captured.subtract(from);
        if (!finite(delta) || delta.lengthSqr() < 1.0e-8) return null;
        Vec3 to = from.add(delta.normalize().scale(Math.min(reach, delta.length())));
        if (!finite(to) || !inside(level, to)) return null;
        return clipLoadedAim(level, body, from, to);
    }

    private static Vec3 clipLoadedAim(ServerLevel level, LivingEntity body, Vec3 from, Vec3 to) {
        try {
            var query=LoadedFrameRayQuery.clip(level,from,to,MAX_CHUNKS,.125,0,
                    (getter,rayFrom,rayTo)->new ClipContext(rayFrom,rayTo,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            if(query.state()==LoadedFrameRayQuery.State.UNKNOWN)return null;
            Vec3 aim=query.frameHit()!=null?query.frameHit().worldPoint()
                    :query.worldHit()!=null&&query.worldHit().getType()==HitResult.Type.BLOCK?query.worldHit().getLocation():to;
            return finite(aim) ? aim : null;
        } catch (LoadedBlockGetter.UnknownChunk ignored) {
            return null;
        }
    }

    /** Clips only the endpoint Y-boundary, preserving the original ray line and finite inputs. */
    static Vec3 boundedRayEndpoint(Vec3 from, Vec3 requestedEnd, int minBuildHeight, int maxBuildHeight) {
        if (from == null || requestedEnd == null || !finite(from) || !finite(requestedEnd)
                || maxBuildHeight <= minBuildHeight) return null;
        double minRayY = minBuildHeight + 1e-4, maxRayY = maxBuildHeight - 1e-4;
        if (requestedEnd.y >= minRayY && requestedEnd.y <= maxRayY) return requestedEnd;
        double deltaY = requestedEnd.y - from.y;
        if (deltaY == 0) return null;
        double boundary = Mth.clamp(requestedEnd.y, minRayY, maxRayY);
        double fraction = (boundary - from.y) / deltaY;
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) return null;
        Vec3 clipped = from.lerp(requestedEnd, fraction);
        return finite(clipped) ? clipped : null;
    }

    /** Immutable server placement plan; velocity is intentional, never the ItemEntity constructor's random motion. */
    record DropPlacement(Vec3 position, Vec3 velocity, @Nullable UUID supportFrameId, @Nullable Vec3 supportStoragePoint) {
        DropPlacement {
            if (!finite(position) || !finite(velocity) || (supportFrameId != null && supportStoragePoint == null)
                    || (supportStoragePoint != null && !finite(supportStoragePoint)))
                throw new IllegalArgumentException("invalid drop placement");
        }
        DropPlacement(Vec3 position, Vec3 velocity) { this(position, velocity, null, null); }
        boolean surfacePlacement() { return supportStoragePoint != null; }
    }

    /** Prefer the first visible ground surface; otherwise throw a short distance into unobstructed air. */
    @Nullable
    static DropPlacement resolveDrop(ServerLevel level, LivingEntity body) {
        return resolveDrop(level, body, null, .25, .25);
    }

    /** The only excluded entity is supplied by an already-authorized drop route (or trusted fixture). */
    static DropPlacement resolveDrop(ServerLevel level, LivingEntity body, @Nullable Entity excludedCarrier) {
        return resolveDrop(level, body, excludedCarrier, .25, .25);
    }

    static DropPlacement resolveDrop(ServerLevel level, Entity body, @Nullable Entity excludedCarrier,
                                     double width, double height) {
        return resolveDrop(level, body, excludedCarrier, width, height, (Entity) null);
    }

    /** Additional exact entity ignored by the placement sweep, e.g. a live body carried at the hand origin. */
    static DropPlacement resolveDrop(ServerLevel level, Entity body, @Nullable Entity excludedCarrier,
                                     double width, double height, @Nullable Entity additionallyExcluded) {
        if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) return null;
        Vec3 eye = body.getEyePosition(), look = body.getLookAngle();
        // The eye-height item box cannot clear a normal two-block ceiling. Launch from a
        // conservative hand-height point, clamped to the body's usable vertical space.
        double launchY = body.getY() + Mth.clamp(1.125, .0, Math.max(.0, body.getBbHeight() - .375));
        Vec3 launch = new Vec3(body.getX(), launchY, body.getZ());
        if (!level.getServer().isSameThread()) return null;
        if (!finite(eye) || !finite(launch) || !finite(look)
                || look.lengthSqr() < .99 || look.lengthSqr() > 1.01 || !inside(level, eye))
            return fallbackDrop(level, body, excludedCarrier, additionallyExcluded, width, height);
        Vec3 rayEnd = launch.add(look.scale(DROP_AIM_REACH));
        if (inside(level, launch) && inside(level, rayEnd)) {
            try {

                var query=LoadedFrameRayQuery.clip(level,launch,rayEnd,MAX_CHUNKS,.125,0,
                        (getter,rayFrom,rayTo)->new ClipContext(rayFrom,rayTo,ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,body));
                if(query.state()==LoadedFrameRayQuery.State.UNKNOWN)throw new LoadedBlockGetter.UnknownChunk();
                BlockHitResult hit=query.worldHit();
                Vec3 hitPoint=query.frameHit()!=null&&query.frameHit().localFace()==Direction.UP
                        ?query.frameHit().worldPoint():hit!=null&&hit.getType()==HitResult.Type.BLOCK
                        &&hit.getDirection()==Direction.UP?hit.getLocation():null;
                if (hitPoint!=null&&finite(hitPoint)) {
                    var surface = surfacePlacement(level, body, launch, hitPoint,
                            query.frameHit() == null ? null : query.frameHit().frameId(),
                            query.frameHit() == null ? null : query.frameHit().storagePos(), excludedCarrier,
                            additionallyExcluded, width, height);
                    if (surface != null) return surface;
                }
            } catch (LoadedBlockGetter.UnknownChunk ignored) { /* A nearby loaded air candidate may still exist. */ }
        }
        // Farthest safe short throw first; the smaller candidates stop on the near side of a wall.
        for (int step = 10; step >= 1; step--) {
            Vec3 placed = launch.add(look.scale(step * .125));
            if (safeDropPosition(level, body, launch, placed, excludedCarrier, additionallyExcluded, width, height))
                return new DropPlacement(placed, look.scale(.16));
        }
        return fallbackDrop(level, body, excludedCarrier, additionallyExcluded, width, height);
    }

    /** Last-resort conservation placement: body origin is already in a known loaded chunk. */
    @Nullable

    private static DropPlacement fallbackDrop(ServerLevel level, Entity body, @Nullable Entity excludedCarrier,
                                                double width, double height) {
        return fallbackDrop(level, body, excludedCarrier, null, width, height);
    }

    private static DropPlacement fallbackDrop(ServerLevel level, Entity body, @Nullable Entity excludedCarrier,
                                                @Nullable Entity additionallyExcluded, double width, double height) {
        Vec3 origin = body.position();
        if (!finite(origin) || !inside(level, origin)
                || level.getChunkSource().getChunkNow(body.blockPosition().getX() >> 4,
                body.blockPosition().getZ() >> 4) == null
                || !safeDropPosition(level, body, origin, origin, excludedCarrier, additionallyExcluded, width, height)) return null;
        return new DropPlacement(origin, Vec3.ZERO);
    }

    private static boolean safeDropPosition(ServerLevel level, Entity body, Vec3 from, Vec3 destination,
                                             @Nullable Entity excludedCarrier, @Nullable Entity additionallyExcluded,
                                             double width, double height) {
        if (!finite(destination) || !inside(level, destination)) return false;
        try {
            LoadedBlockGetter getter = loadedAlong(level, from, destination, width / 2 + .125);
            // Sweep the complete item box, not just its centre ray. Shape intersections permit
            // partial blocks (slabs, fences) when the item box actually clears them.
            int samples = Math.max(1, (int) Math.ceil(from.distanceTo(destination) / .03125));
            for (int i = 0; i <= samples; i++) {
                Vec3 point = from.lerp(destination, (double) i / samples);
                AABB box = itemBox(point, width, height);
                if (!inside(level, new Vec3(box.minX, box.minY, box.minZ))
                        || !inside(level, new Vec3(box.maxX, box.maxY - .000001, box.maxZ))) return false;
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                    var shape = getter.getBlockState(pos).getCollisionShape(getter, pos);
                    if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()),
                            Shapes.create(box), BooleanOp.AND)) return false;
                }

                // Frame-ray hits are projected into world coordinates; validate the entire item
                // box against the same live frame's loaded local collision shapes as well.
                for (var frame : LoadedFrameRayQuery.frameRayContexts(level, point, point, 0)) {
                    AABB localBox = inverseAabb(frame.pose(), box);
                    if (localBox == null) return false;
                    LoadedBlockGetter frameGetter = LoadedBlockGetter.forFrame(level, frame.frame().getPlot(),
                            localBox.inflate(.001), MAX_CHUNKS);
                    for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(localBox.minX, localBox.minY, localBox.minZ),
                            BlockPos.containing(localBox.maxX, localBox.maxY, localBox.maxZ))) {
                        if (!frame.owns(pos)) continue;
                        var shape = frameGetter.getBlockState(pos).getCollisionShape(frameGetter, pos);
                        if (!shape.isEmpty() && Shapes.joinIsNotEmpty(
                                shape.move(pos.getX(), pos.getY(), pos.getZ()), Shapes.create(localBox), BooleanOp.AND)) return false;
                    }
                }
                // Entity occupancy is part of placement clearance as well as block/frame
                // collision. Ignore only the carrier(s) explicitly authorized by this route.
                for (Entity occupant : level.getEntities(body, box, entity -> entity != excludedCarrier
                        && entity != additionallyExcluded && entity.isAlive()
                        && !entity.noPhysics && net.minecraft.world.entity.EntitySelector.NO_SPECTATORS.test(entity))) {
                    if (!occupant.getBoundingBox().intersects(box)) continue;
                    return false;
                }
            }
            return true;
        } catch (LoadedBlockGetter.UnknownChunk ignored) { return false; }
    }

    private static AABB itemBox(Vec3 point, double width, double height) {
        return new AABB(point.x - width / 2, point.y, point.z - width / 2,
                point.x + width / 2, point.y + height, point.z + width / 2);
    }

    /** Server revalidation for a client camera-ray floor nomination; never falls back to another target. */
    static DropPlacement resolveDrop(ServerLevel level, Entity body, @Nullable Entity excludedCarrier,
                                     double width, double height,
                                     com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget nominated) {
        return resolveDrop(level, body, excludedCarrier, width, height, nominated, null);
    }

    /** Captured-target route with an additional exact live entity excluded from the clearance sweep. */
    static DropPlacement resolveDrop(ServerLevel level, Entity body, @Nullable Entity excludedCarrier,
                                     double width, double height,
                                     com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget nominated,
                                     @Nullable Entity additionallyExcluded) {
        if (nominated == null || !Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0
                || body == null || !level.getServer().isSameThread()) return null;
        Vec3 point = LoadedFrameRayQuery.resolveCapturedTarget(level, nominated);
        Vec3 eye = body.getEyePosition(), look = body.getLookAngle();
        if (point == null || !finite(point) || !finite(eye) || !finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01)
            return null;
        Vec3 delta = point.subtract(eye);
        if (delta.lengthSqr() < 1.0e-8 || !withinDropAimReach(delta.length())) return null;
        Vec3 end = point.add(delta.normalize().scale(DROP_RAY_ENDPOINT_EPSILON));
        try {
            var query = LoadedFrameRayQuery.clip(level, eye, end, MAX_CHUNKS, .125, 0,
                    (getter, from, to) -> new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
            if (query.state() != LoadedFrameRayQuery.State.HIT) return null;
            Vec3 actual;
            UUID support = null;
            BlockPos supportCell = null;
            if (query.frameHit() != null) {
                var hit = query.frameHit();
                if (nominated.frameId() == null || !nominated.frameId().equals(hit.frameId())
                        || nominated.frameCell() == null || !nominated.frameCell().equals(hit.storagePos())
                        || hit.localFace() != Direction.UP) return null;
                actual = hit.worldPoint(); support = hit.frameId(); supportCell = hit.storagePos();
            } else {
                var hit = query.worldHit();
                if (nominated.frameId() != null || hit == null || hit.getType() != HitResult.Type.BLOCK
                        || hit.getDirection() != Direction.UP) return null;
                actual = hit.getLocation();
            }
            if (actual.distanceToSqr(point) > DROP_SURFACE_MATCH_EPSILON * DROP_SURFACE_MATCH_EPSILON) return null;
            double launchY = body.getY() + Mth.clamp(1.125, .0, Math.max(.0, body.getBbHeight() - .375));
            Vec3 launch = new Vec3(body.getX(), launchY, body.getZ());
            return surfacePlacement(level, body, launch, actual, support, supportCell, excludedCarrier,
                    additionallyExcluded, width, height);
        } catch (LoadedBlockGetter.UnknownChunk unknown) { return null; }
    }

    static boolean withinDropAimReach(double distance) {
        return Double.isFinite(distance) && distance > 0 && distance <= DROP_AIM_REACH;
    }

    /** Shared surface offset, full clearance sweep and current support-anchor planning. */
    @Nullable
    private static DropPlacement surfacePlacement(ServerLevel level, Entity body, Vec3 launch, Vec3 hitPoint,
            @Nullable UUID supportFrameId, @Nullable BlockPos supportCell, @Nullable Entity excludedCarrier,
            double width, double height) {
        return surfacePlacement(level, body, launch, hitPoint, supportFrameId, supportCell, excludedCarrier,
                null, width, height);
    }

    private static DropPlacement surfacePlacement(ServerLevel level, Entity body, Vec3 launch, Vec3 hitPoint,
            @Nullable UUID supportFrameId, @Nullable BlockPos supportCell, @Nullable Entity excludedCarrier,
            @Nullable Entity additionallyExcluded, double width, double height) {
        Vec3 normal = new Vec3(0, 1, 0);
        if (supportFrameId != null) {
            if (supportCell == null) return null;
            var orientation = LoadedFrameRayQuery.frameOrientation(level, supportFrameId,
                    Vec3.atCenterOf(supportCell), false, 1f);
            if (orientation == null) return null;
            var rotated = orientation.transform(new org.joml.Vector3d(0, 1, 0));
            normal = new Vec3(rotated.x, rotated.y, rotated.z);
        }
        if (!finite(normal) || normal.lengthSqr() < .99 || normal.lengthSqr() > 1.01) return null;
        Vec3 placed = hitPoint.add(normal.scale(.001));
        if (!safeDropPosition(level, body, launch, placed, excludedCarrier, additionallyExcluded, width, height)) return null;
        if (supportFrameId != null) {
            var anchor = LoadedFrameRayQuery.pointAtWorldPosition(level, supportFrameId, placed);
            if (anchor == null) return null;
            return new DropPlacement(placed, Vec3.ZERO, supportFrameId, anchor.storagePoint());
        }
        return new DropPlacement(placed, Vec3.ZERO, null, placed);
    }

    private static AABB inverseAabb(dev.ryanhcode.sable.companion.math.Pose3dc pose, AABB box) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            Vec3 world = new Vec3(x == 0 ? box.minX : box.maxX, y == 0 ? box.minY : box.maxY,
                    z == 0 ? box.minZ : box.maxZ);
            Vec3 local = pose.transformPositionInverse(world);
            if (!finite(local)) return null;
            minX = Math.min(minX, local.x); minY = Math.min(minY, local.y); minZ = Math.min(minZ, local.z);
            maxX = Math.max(maxX, local.x); maxY = Math.max(maxY, local.y); maxZ = Math.max(maxZ, local.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Loaded-only full physical AABB collision test used before a newly-created item is launched. */
    public static boolean loadedItemBoxClear(ServerLevel level, com.juicyslew.moonstation14.entities.LooseItemEntity item) {
        if (item == null) return false;
        return loadedItemBoxClear(level, item, item.getBoundingBox());
    }

    public static boolean loadedItemBoxClear(ServerLevel level, Entity context, AABB candidateBounds) {
        if (level == null || context == null || context.level() != level || !finite(context.position())) return false;
        AABB box = candidateBounds;
        if (box == null || !Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                || box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0) return false;
        if (!inside(level, new Vec3(box.minX, box.minY, box.minZ))
                || !inside(level, new Vec3(box.maxX, box.maxY - .000001, box.maxZ))) return false;
        try {
            Vec3 candidateFeet = new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
            LoadedBlockGetter getter = loadedAlong(level, candidateFeet, candidateFeet, Math.max(box.getXsize(), box.getZsize()) / 2);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                var shape = getter.getBlockState(pos).getCollisionShape(getter, pos);
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()),
                        Shapes.create(box), BooleanOp.AND)) return false;
            }
            var frames = LoadedFrameRayQuery.frameBoxEndpointQuery(level, box);
            if (!frames.complete()) return false;
            for (var frameContext : frames.contexts()) {
                if (!com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.supportsPose(frameContext.pose())) return false;
                AABB local = inverseAabb(frameContext.pose(), box);
                var plot = frameContext.frame().getPlot();
                if (local == null || plot == null) return false;
                AABB storageBounds = new dev.ryanhcode.sable.companion.math.BoundingBox3d(plot.getBoundingBox()).toMojang().inflate(1.0);
                LoadedBlockGetter frameGetter = LoadedBlockGetter.forFullFrame(level, plot, storageBounds, MAX_CHUNKS);
                var shapes = new com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameShapeQuery(level, context, frameContext.pose(), frameGetter, box);
                BlockPos low = BlockPos.containing(local.minX - 1, local.minY - 1, local.minZ - 1);
                BlockPos high = BlockPos.containing(local.maxX + 1, local.maxY + 1, local.maxZ + 1);
                long cells = (long)(high.getX()-low.getX()+1)*(high.getY()-low.getY()+1)*(high.getZ()-low.getZ()+1);
                if (cells <= 0 || cells > (long)MAX_CHUNKS*16*16*16) return false;
                for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
                    if (!frameContext.owns(pos)) continue;
                    var read = frameContext.read(pos);
                    if (!read.known() || read.state() == null) return false;
                    var state = frameGetter.getBlockState(pos);
                    for (AABB shape : shapes.shape(pos, state).toAabbs()) {
                        if (shape == null || !Double.isFinite(shape.minX) || !Double.isFinite(shape.minY) || !Double.isFinite(shape.minZ)
                                || !Double.isFinite(shape.maxX) || !Double.isFinite(shape.maxY) || !Double.isFinite(shape.maxZ)) return false;
                        if (com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.intersects(
                                box, new org.joml.Quaterniond(), shape.move(pos.getX(), pos.getY(), pos.getZ()), frameContext.pose())) return false;
                    }
                }
            }
            return true;
        } catch (LoadedBlockGetter.UnknownChunk ignored) { return false; }
    }

    /**
     * Collision validation for releasing one live contained body. A stationary-container escape places
     * the actor at its holder's feet; overlap with that exact root is expected during the atomic handoff.
     * World blocks and other physical, non-spectator entities remain blocking.
     */
    static boolean portableBodyReleaseClear(ServerLevel level, LivingEntity body, Entity holder,
                                             Vec3 position, double width, double height) {
        if (level == null || body == null || position == null) return false;
        AABB moved = body.getBoundingBox().move(position.subtract(body.position()));
        if (!loadedItemBoxClear(level, body, moved)) return false;
        return level.getEntities(body, moved, entity -> entity != holder && physicalReleaseBlocker(entity)
                && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)).isEmpty();
    }

    static String portableBodyReleaseBlockers(ServerLevel level, LivingEntity body, Entity holder,
                                               Vec3 position, double width, double height) {
        if (level == null || body == null || position == null) return "invalid-input";
        AABB moved = body.getBoundingBox().move(position.subtract(body.position()));
        return level.getEntities(body, moved, entity -> entity != holder && physicalReleaseBlocker(entity)
                        && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)).stream().limit(3)
                .map(entity -> entity.getType().toShortString() + "/" + entity.getId()
                        + "/uuid=" + entity.getUUID() + "/noPhysics=" + entity.noPhysics
                        + (entity instanceof net.minecraft.server.level.ServerPlayer player
                                ? "/spectator=" + player.isSpectator() : "")
                        + "/pos=" + entity.position())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static boolean physicalReleaseBlocker(Entity entity) {
        return !entity.noPhysics && net.minecraft.world.entity.EntitySelector.NO_SPECTATORS.test(entity);
    }

    private static LoadedBlockGetter loadedAlong(ServerLevel level, Vec3 from, Vec3 to, double margin) {
        return LoadedBlockGetter.along(level,from,to,margin,MAX_CHUNKS);
    }

    @Nullable
    public static Target probe(ServerLevel level, LivingEntity body, ItemEntity nominated, double reach) {
        if (nominated == null) return null;
        Target first = nearestVisible(level, body, reach);
        return first != null && first.itemId().equals(nominated.getUUID())
                && nominated.level() == level && !nominated.isRemoved() && nominated.isAddedToLevel()
                && level.getEntity(nominated.getUUID()) == nominated && nominated.getTarget() == null
                ? first : null;
    }

    /** The first visible item, including reserved/delayed items: they block farther pickups, not grant them. */
    @Nullable
    public static Target nearestVisible(ServerLevel level, LivingEntity body, double reach) {
        CandidateSet candidates = visibleCandidateSet(level, body, reach);
        return candidates.candidates().isEmpty() ? null : candidates.candidates().getFirst();
    }

    /** Read-only reach/line-of-sight check for a storage host; unlike probe, body look direction is irrelevant. */
    public static boolean reachableStorageHost(ServerLevel level, LivingEntity body, ItemEntity target, double reach) {
        return reachableItemPoint(level, body, target, reach) != null;
    }

    /** Validates an explicitly nominated pickup target without requiring the body to face it. */
    public static Target probeNominatedPickup(ServerLevel level, LivingEntity body, UUID targetId, double reach) {
        if (level == null || targetId == null || level.getServer() == null || !level.getServer().isSameThread()) return null;
        Entity found = level.getEntity(targetId);
        if (!(found instanceof ItemEntity item) || item.getItem().isEmpty() || item.hasPickUpDelay()
                || item.getTarget() != null) return null;
        Vec3 point = reachableItemPoint(level, body, item, reach);
        if (point == null || item.getItem().isEmpty() || item.hasPickUpDelay() || item.getTarget() != null
                || level.getEntity(targetId) != item || item.isRemoved()) return null;
        return new Target(targetId, point);
    }

    private static Vec3 reachableItemPoint(ServerLevel level, LivingEntity body, ItemEntity target, double reach) {
        if (level == null || body == null || target == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || target.level() != level || target.isRemoved()
                || !target.isAddedToLevel() || level.getEntity(target.getUUID()) != target || target.getTarget() != null
                || !Double.isFinite(reach) || reach <= 0 || reach > MAX_REACH) return null;
        Vec3 bodyPosition = body.position(), targetPosition = target.position(), eye = body.getEyePosition();
        AABB box = target.getBoundingBox();
        LooseItemQueryGeometry shape=target instanceof LooseItemEntity loose?LooseItemQueryGeometry.forEntity(loose,0):null;
        if(target instanceof LooseItemEntity && shape==null)return null;
        Vec3 point = shape!=null?shape.nearestPoint(eye):new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
        if (!finite(bodyPosition) || !finite(targetPosition) || !finite(eye) || !finite(point)
                || !inside(level, eye) || !inside(level, point) || eye.distanceToSqr(point) > reach * reach) return null;
        int minX = (Math.min(BlockPos.containing(eye).getX(), BlockPos.containing(point).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(eye).getX(), BlockPos.containing(point).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(eye).getZ(), BlockPos.containing(point).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(eye).getZ(), BlockPos.containing(point).getZ()) >> 4) + 1;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) return null;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return null;
        try {
            var query=LoadedFrameRayQuery.clip(level,eye,point,MAX_CHUNKS,0,1,
                    (getter,rayFrom,rayTo)->new ClipContext(rayFrom,rayTo,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            return query.state()==LoadedFrameRayQuery.State.MISS && sameLive(level, body, target)
                    && bodyPosition.equals(body.position()) && targetPosition.equals(target.position())
                    && eye.equals(body.getEyePosition()) && box.equals(target.getBoundingBox())
                    && target.getTarget() == null ? point : null;
        } catch (LoadedBlockGetter.UnknownChunk ignored) { return null; }
    }

    /** Reach against the nearest point of an interaction target's bounds; independent of current gaze. */
    public static boolean withinInteractionReach(LivingEntity body, AABB targetBounds) {
        if (body == null || targetBounds == null || !Double.isFinite(targetBounds.minX) || !Double.isFinite(targetBounds.maxX)
                || !Double.isFinite(targetBounds.minY) || !Double.isFinite(targetBounds.maxY)
                || !Double.isFinite(targetBounds.minZ) || !Double.isFinite(targetBounds.maxZ)) return false;
        Vec3 eye = body.getEyePosition();
        Vec3 closest = new Vec3(Mth.clamp(eye.x, targetBounds.minX, targetBounds.maxX),
                Mth.clamp(eye.y, targetBounds.minY, targetBounds.maxY), Mth.clamp(eye.z, targetBounds.minZ, targetBounds.maxZ));
        return finite(eye) && finite(closest) && eye.distanceToSqr(closest) <= MAX_REACH * MAX_REACH;
    }

    /** Loaded-only server validation for an entity-use target; client hit claims are never consumed. */
    public static boolean loadedReachableEntity(ServerLevel level, LivingEntity body, Entity target, double reach) {
        if (level == null || body == null || target == null || level.getServer() == null
                || !level.getServer().isSameThread() || body.level() != level || target.level() != level
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || target.isRemoved() || !target.isAddedToLevel()
                || level.getEntity(target.getUUID()) != target || !Double.isFinite(reach) || reach <= 0 || reach > MAX_REACH) return false;
        Vec3 eye = body.getEyePosition(), position = target.position(); AABB box = target.getBoundingBox();
        Vec3 point = new Vec3(Mth.clamp(eye.x,box.minX,box.maxX),Mth.clamp(eye.y,box.minY,box.maxY),Mth.clamp(eye.z,box.minZ,box.maxZ));
        if (!finite(eye)||!finite(point)||!inside(level,eye)||!inside(level,point)||eye.distanceToSqr(point)>reach*reach) return false;
        try {
            BlockHitResult hit=loadedAlong(level,eye,point,.125).clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            return hit.getType()==HitResult.Type.MISS && target.level()==level && !target.isRemoved()
                    && level.getEntity(target.getUUID())==target && position.equals(target.position()) && box.equals(target.getBoundingBox());
        } catch (LoadedBlockGetter.UnknownChunk ignored) { return false; }
    }

    /** Loaded-only validation for a previously nominated item target; does not require the current view ray. */
    public static boolean loadedReachableItem(ServerLevel level, LivingEntity body, UUID targetId, double reach) {
        if (level == null || targetId == null || level.getServer() == null || !level.getServer().isSameThread()) return false;
        Entity found = level.getEntity(targetId);
        return found instanceof ItemEntity item && item instanceof com.juicyslew.moonstation14.entities.LooseItemEntity
                && item.getTarget() == null && !item.hasPickUpDelay()
                && reachableStorageHost(level, body, item, reach);
    }

    private static boolean sameLive(ServerLevel level, LivingEntity body, ItemEntity target) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body && target.level() == level && !target.isRemoved()
                && target.isAddedToLevel() && level.getEntity(target.getUUID()) == target;
    }

    private static boolean finite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    private static boolean inside(ServerLevel level, Vec3 vec) {
        return !level.isOutsideBuildHeight(BlockPos.containing(vec))
                && level.getWorldBorder().isWithinBounds(vec);
    }

}
