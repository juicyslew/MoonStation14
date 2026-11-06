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
    /** Loose throws may aim up to eight blocks; this does not widen pickup/drop reach. */
    public static final double MAX_THROW_REACH = 8.0;
    /** Ground placement aim distance; deliberately independent of pickup/storage reach. */
    public static final double DROP_AIM_REACH = 3.5;
    private static final double DROP_RAY_ENDPOINT_EPSILON = 1.0e-5;
    private static final double DROP_SURFACE_MATCH_EPSILON = 1.0e-5;
    public static final int MAX_CHUNKS = 16;
    public static final double TARGET_MARGIN = 0.20;

    private BodyItemTargetProbe() { }

    public record Target(UUID itemId, Vec3 hitLocation) { }

    /** At most this many visible ray candidates are enumerated; overflow fails closed. */
    public static final int MAX_VISIBLE_CANDIDATES = 32;
    private static final int MAX_ENUMERATED_CANDIDATES = 256;

    /** Deterministic visible pile members along the body-eye ray. */
    public static java.util.List<Target> visibleCandidates(ServerLevel level, LivingEntity body, double reach) {
        if (level == null || body == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !Double.isFinite(reach) || reach <= 0 || reach > MAX_REACH)
            return java.util.List.of();
        Vec3 from = body.getEyePosition(), look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01) return java.util.List.of();
        Vec3 to = from.add(look.scale(reach));
        if (!finite(to) || !inside(level, from) || !inside(level, to)) return java.util.List.of();
        int minX = (Math.min(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) + 1;
        if ((long)(maxX-minX+1)*(maxZ-minZ+1)>MAX_CHUNKS) return java.util.List.of();
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) if(level.getChunkSource().getChunkNow(x,z)==null)return java.util.List.of();
        try {
            var query=LoadedFrameRayQuery.clip(level,from,to,MAX_CHUNKS,0,1,
                    (getter,rayFrom,rayTo)->new ClipContext(rayFrom,rayTo,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            if(query.state()==LoadedFrameRayQuery.State.UNKNOWN)return java.util.List.of();
            BlockHitResult block=query.worldHit();
            double blocker=query.frameHit()!=null?from.distanceTo(query.frameHit().worldPoint()):reach;
            if(block!=null&&block.getType()==HitResult.Type.BLOCK)blocker=Math.min(blocker,from.distanceTo(block.getLocation()));
            java.util.ArrayList<Target> result=new java.util.ArrayList<>();
            int enumerated = 0;
            java.util.ArrayList<ItemEntity> candidates=new java.util.ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(ItemEntity.class),new AABB(from,to).inflate(TARGET_MARGIN + LooseItemQueryGeometry.maximumProfileRadius(false)),e->true,candidates,257);
            if(candidates.size()>256)return java.util.List.of();
            for(Entity entity:candidates) {
                if (++enumerated > MAX_ENUMERATED_CANDIDATES) return java.util.List.of();
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
                result.add(new Target(item.getUUID(),hit));
            }
            result.sort(java.util.Comparator.comparingDouble((Target t)->from.distanceToSqr(t.hitLocation())).thenComparing(Target::itemId));
            if (result.size() > MAX_VISIBLE_CANDIDATES)
                result.subList(MAX_VISIBLE_CANDIDATES, result.size()).clear();
            return java.util.List.copyOf(result);
        } catch(LoadedBlockGetter.UnknownChunk ignored){return java.util.List.of();}
    }

    /** Validates an explicitly nominated member of the bounded body-eye candidate set. */
    @Nullable
    public static Target probeSelected(ServerLevel level, LivingEntity body, UUID nominated, double reach) {
        if (nominated == null) return null;
        for (Target target : visibleCandidates(level, body, reach))
            if (nominated.equals(target.itemId())) return target;
        return null;
    }

    /**
     * Loaded-only collider ray for loose throws. The LoadedBlockGetter prevents this probe's block reads
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

    static DropPlacement resolveDrop(ServerLevel level, LivingEntity body, @Nullable Entity excludedCarrier,
                                     double width, double height) {
        if (!Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) return null;
        Vec3 eye = body.getEyePosition(), look = body.getLookAngle();
        // The eye-height item box cannot clear a normal two-block ceiling. Launch from a
        // conservative hand-height point, clamped to the body's usable vertical space.
        double launchY = body.getY() + Mth.clamp(1.125, .0, Math.max(.0, body.getBbHeight() - .375));
        Vec3 launch = new Vec3(body.getX(), launchY, body.getZ());
        if (!level.getServer().isSameThread()) return null;
        if (!finite(eye) || !finite(launch) || !finite(look)
                || look.lengthSqr() < .99 || look.lengthSqr() > 1.01 || !inside(level, eye))
            return fallbackDrop(level, body, excludedCarrier, width, height);
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
                            query.frameHit() == null ? null : query.frameHit().storagePos(), excludedCarrier, width, height);
                    if (surface != null) return surface;
                }
            } catch (LoadedBlockGetter.UnknownChunk ignored) { /* A nearby loaded air candidate may still exist. */ }
        }
        // Farthest safe short throw first; the smaller candidates stop on the near side of a wall.
        for (int step = 10; step >= 1; step--) {
            Vec3 placed = launch.add(look.scale(step * .125));
            if (safeDropPosition(level, body, launch, placed, excludedCarrier, width, height))
                return new DropPlacement(placed, look.scale(.16));
        }
        return fallbackDrop(level, body, excludedCarrier, width, height);
    }

    /** Last-resort conservation placement: body origin is already in a known loaded chunk. */
    @Nullable
    private static DropPlacement fallbackDrop(ServerLevel level, LivingEntity body, @Nullable Entity excludedCarrier,
                                               double width, double height) {
        Vec3 origin = body.position();
        if (!finite(origin) || !inside(level, origin)
                || level.getChunkSource().getChunkNow(body.blockPosition().getX() >> 4,
                body.blockPosition().getZ() >> 4) == null
                || !safeDropPosition(level, body, origin, origin, excludedCarrier, width, height)) return null;
        return new DropPlacement(origin, Vec3.ZERO);
    }

    private static boolean safeDropPosition(ServerLevel level, LivingEntity body, Vec3 from, Vec3 destination,
                                            @Nullable Entity excludedCarrier, double width, double height) {
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
                if (!level.getEntities(body, box, entity -> entity != excludedCarrier
                        && !(entity instanceof ItemEntity)).isEmpty()) return false;
            }
            return true;
        } catch (LoadedBlockGetter.UnknownChunk ignored) { return false; }
    }

    private static AABB itemBox(Vec3 point, double width, double height) {
        return new AABB(point.x - width / 2, point.y, point.z - width / 2,
                point.x + width / 2, point.y + height, point.z + width / 2);
    }

    /** Server revalidation for a client camera-ray floor nomination; never falls back to another target. */
    static DropPlacement resolveDrop(ServerLevel level, LivingEntity body, @Nullable Entity excludedCarrier,
                                     double width, double height,
                                     com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget nominated) {
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
            return surfacePlacement(level, body, launch, actual, support, supportCell, excludedCarrier, width, height);
        } catch (LoadedBlockGetter.UnknownChunk unknown) { return null; }
    }

    static boolean withinDropAimReach(double distance) {
        return Double.isFinite(distance) && distance > 0 && distance <= DROP_AIM_REACH;
    }

    /** Shared surface offset, full clearance sweep and current support-anchor planning. */
    @Nullable
    private static DropPlacement surfacePlacement(ServerLevel level, LivingEntity body, Vec3 launch, Vec3 hitPoint,
            @Nullable UUID supportFrameId, @Nullable BlockPos supportCell, @Nullable Entity excludedCarrier,
            double width, double height) {
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
        if (!safeDropPosition(level, body, launch, placed, excludedCarrier, width, height)) return null;
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
        if (level == null || item == null || item.level() != level || !finite(item.position())) return false;
        AABB box = item.getBoundingBox();
        if (box == null || !Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                || box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0) return false;
        if (!inside(level, new Vec3(box.minX, box.minY, box.minZ))
                || !inside(level, new Vec3(box.maxX, box.maxY - .000001, box.maxZ))) return false;
        try {
            LoadedBlockGetter getter = loadedAlong(level, item.position(), item.position(), Math.max(box.getXsize(), box.getZsize()) / 2);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                var shape = getter.getBlockState(pos).getCollisionShape(getter, pos);
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()),
                        Shapes.create(box), BooleanOp.AND)) return false;
            }
            var frames = LoadedFrameRayQuery.frameBoxEndpointQuery(level, box);
            if (!frames.complete()) return false;
            for (var context : frames.contexts()) {
                if (!com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.supportsPose(context.pose())) return false;
                AABB local = inverseAabb(context.pose(), box);
                var plot = context.frame().getPlot();
                if (local == null || plot == null) return false;
                AABB storageBounds = new dev.ryanhcode.sable.companion.math.BoundingBox3d(plot.getBoundingBox()).toMojang().inflate(1.0);
                LoadedBlockGetter frameGetter = LoadedBlockGetter.forFullFrame(level, plot, storageBounds, MAX_CHUNKS);
                var shapes = new com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameShapeQuery(level, item, context.pose(), frameGetter);
                BlockPos low = BlockPos.containing(local.minX - 1, local.minY - 1, local.minZ - 1);
                BlockPos high = BlockPos.containing(local.maxX + 1, local.maxY + 1, local.maxZ + 1);
                long cells = (long)(high.getX()-low.getX()+1)*(high.getY()-low.getY()+1)*(high.getZ()-low.getZ()+1);
                if (cells <= 0 || cells > (long)MAX_CHUNKS*16*16*16) return false;
                for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
                    if (!context.owns(pos)) continue;
                    var read = context.read(pos);
                    if (!read.known() || read.state() == null) return false;
                    var state = frameGetter.getBlockState(pos);
                    for (AABB shape : shapes.shape(pos, state).toAabbs()) {
                        if (shape == null || !Double.isFinite(shape.minX) || !Double.isFinite(shape.minY) || !Double.isFinite(shape.minZ)
                                || !Double.isFinite(shape.maxX) || !Double.isFinite(shape.maxY) || !Double.isFinite(shape.maxZ)) return false;
                        if (com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.intersects(
                                box, new org.joml.Quaterniond(), shape.move(pos.getX(), pos.getY(), pos.getZ()), context.pose())) return false;
                    }
                }
            }
            return true;
        } catch (LoadedBlockGetter.UnknownChunk ignored) { return false; }
    }

    private static LoadedBlockGetter loadedAlong(ServerLevel level, Vec3 from, Vec3 to) {
        return loadedAlong(level, from, to, .125);
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
        if (level == null || body == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || !Double.isFinite(reach) || reach <= 0 || reach > MAX_REACH)
            return null;

        Vec3 from = body.getEyePosition();
        Vec3 look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < 0.99 || look.lengthSqr() > 1.01)
            return null;
        Vec3 to = from.add(look.scale(reach));
        if (!finite(to) || from.equals(to) || !inside(level, from) || !inside(level, to)) return null;

        // Include one chunk of shape-query margin around every chunk crossed by this short segment.
        int minX = (Math.min(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) + 1;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) return null;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return null;

        try {
            var query=LoadedFrameRayQuery.clip(level,from,to,MAX_CHUNKS,0,1,
                    (getter,rayFrom,rayTo)->new ClipContext(rayFrom,rayTo,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body));
            if(query.state()==LoadedFrameRayQuery.State.UNKNOWN)return null;
            BlockHitResult block=query.worldHit();
            double blockerDistance=query.frameHit()!=null?from.distanceTo(query.frameHit().worldPoint()):reach;
            if(block!=null&&block.getType()==HitResult.Type.BLOCK)blockerDistance=Math.min(blockerDistance,from.distanceTo(block.getLocation()));
            ItemEntity first = null;
            Vec3 firstHit = null;
            double firstDistance = Double.POSITIVE_INFINITY;
            // ServerLevel's entity index enumerates loaded entities; do not use Level.clip or chunk lookups.
            java.util.ArrayList<ItemEntity> candidates=new java.util.ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(ItemEntity.class),new AABB(from,to).inflate(TARGET_MARGIN + LooseItemQueryGeometry.maximumProfileRadius(false)),e->true,candidates,257);
            if(candidates.size()>256)return null;
            for (Entity entity : candidates) {
                ItemEntity item = (ItemEntity) entity;
                if (item.level() != level || item.isRemoved() || !item.isAddedToLevel()
                        || level.getEntity(item.getUUID()) != item) continue;
                AABB box = item.getBoundingBox();
                LooseItemQueryGeometry shape=item instanceof LooseItemEntity loose?LooseItemQueryGeometry.forEntity(loose,0):null;
                if(item instanceof LooseItemEntity && shape==null)continue;
                Vec3 hit = shape!=null?shape.inflated(TARGET_MARGIN).clip(from,to):box.inflate(TARGET_MARGIN).contains(from)?from
                        : box.inflate(TARGET_MARGIN).clip(from, to).orElse(null);
                if (hit == null || !finite(hit)) continue;
                double distance = from.distanceToSqr(hit);
                // The inflated hit can precede a wall even when the real item is entirely behind it.
                // Require the near face of the actual box to be on the visible side of the collider.
                double nearFace = shape!=null?shape.minimumProjection(from,look):(look.x >= 0 ? box.minX : box.maxX) * look.x
                        + (look.y >= 0 ? box.minY : box.maxY) * look.y
                        + (look.z >= 0 ? box.minZ : box.maxZ) * look.z - from.dot(look);
                boolean eyeOverlap = shape!=null?shape.clip(from,from)!=null:box.contains(from);
                if (!Double.isFinite(distance)
                        || (distance >= blockerDistance * blockerDistance && !(distance == 0 && eyeOverlap))
                        || (nearFace >= blockerDistance && !eyeOverlap) || distance > reach * reach) continue;
                // Exact hit-distance ties use UUID ordering, never the entity index's iteration order.
                if (distance < firstDistance || distance == firstDistance
                        && (first == null || item.getUUID().compareTo(first.getUUID()) < 0)) {
                    first = item;
                    firstHit = hit;
                    firstDistance = distance;
                }
            }
            return first == null ? null : new Target(first.getUUID(), firstHit);
        } catch (LoadedBlockGetter.UnknownChunk ignored) {
            return null;
        }
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
