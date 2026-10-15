package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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
    /** Loose throws may aim up to eight blocks; this does not widen pickup/drop reach. */
    public static final double MAX_THROW_REACH = 8.0;
    /** Ground placement aim distance; deliberately independent of pickup/storage reach. */
    static final double DROP_AIM_REACH = 3.5;
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
        AABB bounds = entity == null ? null : entity.getBoundingBox();
        if (entity == null || entity.level() != level || entity.isRemoved() || !entity.isAddedToLevel()
                || !(entity instanceof ItemEntity || entity instanceof LivingEntity living
                 && PortableBodyCarrying.eligibleLivingTarget(body, living))
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
        AABB bounds = entity.getBoundingBox();
        return inside(level, eye) && inside(level, entity.position()) && withinReach(body, bounds, reach)
                && clearLine(level, body, eye, bounds);
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
        int minX = (Math.min(BlockPos.containing(from).getX(), BlockPos.containing(point).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), BlockPos.containing(point).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(point).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(point).getZ()) >> 4) + 1;
        if ((long)(maxX-minX+1)*(maxZ-minZ+1)>MAX_CHUNKS) return false;
        for (int x=minX;x<=maxX;x++) for (int z=minZ;z<=maxZ;z++)
            if (level.getChunkSource().getChunkNow(x,z)==null) return false;
        try {
            return new LoadedGetter(level,minX,maxX,minZ,maxZ).clip(
                    new ClipContext(from,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body)).getType()==HitResult.Type.MISS;
        } catch (MissingChunk ignored) { return false; }
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
        // A full reach ray may point below the build floor when an NPC faces a nearby floor-level
        // item. Clip only its endpoint to legal Y; the target must still lie on the same ray before
        // this endpoint, and collider occlusion remains authoritative.
        double minRayY = minBuild + 1e-4, maxRayY = maxBuild - 1e-4;
        Vec3 to = requestedEnd;
        if (requestedEnd.y < minRayY || requestedEnd.y > maxRayY) {
            double boundary = Mth.clamp(requestedEnd.y, minRayY, maxRayY);
            double fraction = (boundary - from.y) / (requestedEnd.y - from.y);
            to = from.add(look.scale(reach * fraction));
        }
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
            BlockHitResult block = new LoadedGetter(level,minX,maxX,minZ,maxZ).clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            if (!finite(block.getLocation())) return candidateFailure(ProbeFailure.BLOCKED_OR_MISSED_TARGET,
                    level, body, from, requestedEnd, minBuild, maxBuild, minX, maxX, minZ, maxZ, null, to);
            double blocker=block.getType()==HitResult.Type.BLOCK?from.distanceTo(block.getLocation()):reach;
            java.util.ArrayList<Target> result=new java.util.ArrayList<>();
            int enumerated = 0;
            // Automatic world-item targeting intentionally remains item-only. Living bodies are
            // admitted only through an explicit nominated interaction, never as a foreground
            // candidate that can steal pickup/configurator selection.
            for(Entity entity:level.getEntities(body,new AABB(from,to).inflate(TARGET_MARGIN),
                    e -> e instanceof ItemEntity)) {
                if (++enumerated > MAX_ENUMERATED_CANDIDATES) return candidateFailure(
                        ProbeFailure.BLOCKED_OR_MISSED_TARGET, level, body, from, requestedEnd,
                        minBuild, maxBuild, minX, maxX, minZ, maxZ, "candidate-overflow", to);
                if(entity.level()!=level||entity.isRemoved()||!entity.isAddedToLevel()||level.getEntity(entity.getUUID())!=entity)continue;
                AABB box=entity.getBoundingBox(); Vec3 hit=box.inflate(TARGET_MARGIN).contains(from)?from:box.inflate(TARGET_MARGIN).clip(from,to).orElse(null);
                if(hit==null)continue; double dist=from.distanceToSqr(hit);
                double near=(look.x>=0?box.minX:box.maxX)*look.x+(look.y>=0?box.minY:box.maxY)*look.y+(look.z>=0?box.minZ:box.maxZ)*look.z-from.dot(look);
                boolean overlap=box.contains(from);
                if(!Double.isFinite(dist)||(dist>=blocker*blocker&&!(dist==0&&overlap))||(near>=blocker&&!overlap)||dist>reach*reach)continue;
                result.add(new Target(entity.getUUID(),hit));
            }
            result.sort(java.util.Comparator.comparingDouble((Target t)->from.distanceToSqr(t.hitLocation())).thenComparing(Target::itemId));
            if (result.size() > MAX_VISIBLE_CANDIDATES)
                result.subList(MAX_VISIBLE_CANDIDATES, result.size()).clear();
            return new CandidateSet(java.util.List.copyOf(result), diagnostic(ProbeFailure.NONE, level, body,
                    from, requestedEnd, minBuild, maxBuild, minX, maxX, minZ, maxZ, null, to, block));
        } catch(MissingChunk ignored){return candidateFailure(ProbeFailure.MISSING_CHUNK, level, body, from,
                requestedEnd, minBuild, maxBuild, minX, maxX, minZ, maxZ, "shape-query", to);}
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
     * Loaded-only collider ray for loose throws. The LoadedGetter prevents this probe's block reads
     * from loading chunks; arbitrary shape callbacks may still access the world independently.
     */
    @Nullable
    public static Vec3 loadedAimPoint(ServerLevel level, LivingEntity body, double reach) {
        if (level == null || body == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !Double.isFinite(reach)
                || reach <= 0 || reach > MAX_THROW_REACH) return null;
        Vec3 from = body.getEyePosition(), look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01) return null;
        Vec3 to = from.add(look.scale(reach));
        if (!finite(to) || !inside(level, from) || !inside(level, to)) return null;
        try {
            BlockHitResult hit = loadedAlong(level, from, to).clip(
                    new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
            Vec3 aim = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
            return finite(aim) ? aim : null;
        } catch (MissingChunk ignored) {
            return null;
        }
    }

    /** Immutable server placement plan; velocity is intentional, never the ItemEntity constructor's random motion. */
    record DropPlacement(Vec3 position, Vec3 velocity) { }

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
        return resolveDrop(level, body, excludedCarrier, width, height, null);
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
            return fallbackDrop(level, body);
        Vec3 rayEnd = launch.add(look.scale(DROP_AIM_REACH));
        if (inside(level, launch) && inside(level, rayEnd)) {
            try {
                LoadedGetter getter = loadedAlong(level, launch, rayEnd);
                BlockHitResult hit = getter.clip(new ClipContext(launch, rayEnd, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, body));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
                        && finite(hit.getLocation())) {
                    Vec3 surface = hit.getLocation();
                    Vec3 placed = new Vec3(surface.x, surface.y + .001, surface.z);
                    if (safeDropPosition(level, body, launch, placed, excludedCarrier, additionallyExcluded, width, height))
                        return new DropPlacement(placed, Vec3.ZERO);
                }
            } catch (MissingChunk ignored) { /* A nearby loaded air candidate may still exist. */ }
        }
        // Farthest safe short throw first; the smaller candidates stop on the near side of a wall.
        for (int step = 10; step >= 1; step--) {
            Vec3 placed = launch.add(look.scale(step * .125));
            if (safeDropPosition(level, body, launch, placed, excludedCarrier, additionallyExcluded, width, height))
                return new DropPlacement(placed, look.scale(.16));
        }
        return fallbackDrop(level, body);
    }

    /** Last-resort conservation placement: body origin is already in a known loaded chunk. */
    @Nullable
    private static DropPlacement fallbackDrop(ServerLevel level, Entity body) {
        Vec3 origin = body.position();
        if (!finite(origin) || !inside(level, origin)
                || level.getChunkSource().getChunkNow(body.blockPosition().getX() >> 4,
                body.blockPosition().getZ() >> 4) == null) return null;
        return new DropPlacement(origin, Vec3.ZERO);
    }

    private static boolean safeDropPosition(ServerLevel level, Entity body, Vec3 from, Vec3 destination,
                                             @Nullable Entity excludedCarrier, @Nullable Entity additionallyExcluded,
                                             double width, double height) {
        if (!finite(destination) || !inside(level, destination)) return false;
        try {
            LoadedGetter getter = loadedAlong(level, from, destination, width / 2 + .125);
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
                if (!level.getEntities(body, box, entity -> entity != excludedCarrier && entity != additionallyExcluded
                        && physicalReleaseBlocker(entity) && !(entity instanceof ItemEntity)).isEmpty()) return false;
            }
            return true;
        } catch (MissingChunk ignored) { return false; }
    }

    private static AABB itemBox(Vec3 point, double width, double height) {
        return new AABB(point.x - width / 2, point.y, point.z - width / 2,
                point.x + width / 2, point.y + height, point.z + width / 2);
    }

    /** Loaded-only full physical AABB collision test used before a newly-created item is launched. */
    public static boolean loadedItemBoxClear(ServerLevel level, Vec3 position, double width, double height) {
        if (level == null || position == null || !finite(position) || !Double.isFinite(width) || width <= 0
                || !Double.isFinite(height) || height <= 0) return false;
        AABB box = itemBox(position, width, height);
        if (!inside(level, new Vec3(box.minX, box.minY, box.minZ))
                || !inside(level, new Vec3(box.maxX, box.maxY - .000001, box.maxZ))) return false;
        try {
            LoadedGetter getter = loadedAlong(level, position, position, width / 2);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                var shape = getter.getBlockState(pos).getCollisionShape(getter, pos);
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()),
                        Shapes.create(box), BooleanOp.AND)) return false;
            }
            return true;
        } catch (MissingChunk ignored) { return false; }
    }

    /**
     * Collision validation for releasing one live contained body. A stationary-container escape places
     * the actor at its holder's feet; overlap with that exact root is expected during the atomic handoff.
     * World blocks and other physical, non-spectator entities remain blocking.
     */
    static boolean portableBodyReleaseClear(ServerLevel level, LivingEntity body, Entity holder,
                                             Vec3 position, double width, double height) {
        if (level == null || body == null || position == null || !loadedItemBoxClear(level, position, width, height)) return false;
        AABB moved = body.getBoundingBox().move(position.subtract(body.position()));
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

    private static LoadedGetter loadedAlong(ServerLevel level, Vec3 from, Vec3 to) {
        return loadedAlong(level, from, to, .125);
    }

    private static LoadedGetter loadedAlong(ServerLevel level, Vec3 from, Vec3 to, double margin) {
        int minX = BlockPos.containing(Math.min(from.x, to.x) - margin, from.y, from.z).getX() >> 4;
        int maxX = BlockPos.containing(Math.max(from.x, to.x) + margin, from.y, from.z).getX() >> 4;
        int minZ = BlockPos.containing(from.x, from.y, Math.min(from.z, to.z) - margin).getZ() >> 4;
        int maxZ = BlockPos.containing(from.x, from.y, Math.max(from.z, to.z) + margin).getZ() >> 4;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) throw new MissingChunk();
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) throw new MissingChunk();
        return new LoadedGetter(level, minX, maxX, minZ, maxZ);
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
        if (level == null || body == null || target == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || target.level() != level || target.isRemoved()
                || !target.isAddedToLevel() || level.getEntity(target.getUUID()) != target || target.getTarget() != null
                || !Double.isFinite(reach) || reach <= 0 || reach > MAX_REACH) return false;
        Vec3 bodyPosition = body.position(), targetPosition = target.position(), eye = body.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 point = new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
        if (!finite(bodyPosition) || !finite(targetPosition) || !finite(eye) || !finite(point)
                || !inside(level, eye) || !inside(level, point) || eye.distanceToSqr(point) > reach * reach) return false;
        int minX = (Math.min(BlockPos.containing(eye).getX(), BlockPos.containing(point).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(eye).getX(), BlockPos.containing(point).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(eye).getZ(), BlockPos.containing(point).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(eye).getZ(), BlockPos.containing(point).getZ()) >> 4) + 1;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        try {
            BlockHitResult hit = new LoadedGetter(level, minX, maxX, minZ, maxZ).clip(
                    new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
            return hit.getType() == HitResult.Type.MISS && sameLive(level, body, target)
                    && bodyPosition.equals(body.position()) && targetPosition.equals(target.position())
                    && eye.equals(body.getEyePosition()) && box.equals(target.getBoundingBox())
                    && target.getTarget() == null;
        } catch (MissingChunk ignored) { return false; }
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
            BlockHitResult hit=loadedAlong(level,eye,point).clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            return hit.getType()==HitResult.Type.MISS && target.level()==level && !target.isRemoved()
                    && level.getEntity(target.getUUID())==target && position.equals(target.position()) && box.equals(target.getBoundingBox());
        } catch (MissingChunk ignored) { return false; }
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

    private static final class MissingChunk extends RuntimeException { }

    private static final class LoadedGetter implements BlockGetter {
        private final ServerLevel level;
        private final int minX, maxX, minZ, maxZ;

        private LoadedGetter(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
            this.level = level;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        private LevelChunk chunk(BlockPos pos) {
            int x = pos.getX() >> 4, z = pos.getZ() >> 4;
            if (x < minX || x > maxX || z < minZ || z > maxZ) throw new MissingChunk();
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null || level.isOutsideBuildHeight(pos)) throw new MissingChunk();
            return chunk;
        }

        @Override public BlockState getBlockState(BlockPos pos) { return chunk(pos).getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return chunk(pos).getFluidState(pos); }
        @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return chunk(pos).getBlockEntities().get(pos);
        }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
