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
    /** Minecraft blocks, a defensive ray ceiling, not a conversion from SS14 interaction units. */
    public static final double MAX_REACH = 5.0;
    /** Ground placement aim distance; deliberately independent of pickup/storage reach. */
    static final double DROP_AIM_REACH = 3.5;
    public static final int MAX_CHUNKS = 16;
    public static final double TARGET_MARGIN = 0.20;

    private BodyItemTargetProbe() { }

    public record Target(UUID itemId, Vec3 hitLocation) { }

    /** Immutable server placement plan; velocity is intentional, never the ItemEntity constructor's random motion. */
    record DropPlacement(Vec3 position, Vec3 velocity) { }

    /** Prefer the first visible ground surface; otherwise throw a short distance into unobstructed air. */
    @Nullable
    static DropPlacement resolveDrop(ServerLevel level, LivingEntity body) {
        return resolveDrop(level, body, null);
    }

    /** The only excluded entity is supplied by an already-authorized drop route (or trusted fixture). */
    static DropPlacement resolveDrop(ServerLevel level, LivingEntity body, @Nullable Entity excludedCarrier) {
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
                    if (safeDropPosition(level, body, launch, placed, excludedCarrier))
                        return new DropPlacement(placed, Vec3.ZERO);
                }
            } catch (MissingChunk ignored) { /* A nearby loaded air candidate may still exist. */ }
        }
        // Farthest safe short throw first; the smaller candidates stop on the near side of a wall.
        for (int step = 10; step >= 2; step--) {
            Vec3 placed = launch.add(look.scale(step * .125));
            if (safeDropPosition(level, body, launch, placed, excludedCarrier))
                return new DropPlacement(placed, look.scale(.16));
        }
        return fallbackDrop(level, body);
    }

    /** Last-resort conservation placement: body origin is already in a known loaded chunk. */
    @Nullable
    private static DropPlacement fallbackDrop(ServerLevel level, LivingEntity body) {
        Vec3 origin = body.position();
        if (!finite(origin) || !inside(level, origin)
                || level.getChunkSource().getChunkNow(body.blockPosition().getX() >> 4,
                body.blockPosition().getZ() >> 4) == null) return null;
        return new DropPlacement(origin, Vec3.ZERO);
    }

    private static boolean safeDropPosition(ServerLevel level, LivingEntity body, Vec3 from, Vec3 destination,
                                            @Nullable Entity excludedCarrier) {
        if (!finite(destination) || !inside(level, destination)) return false;
        try {
            LoadedGetter getter = loadedAlong(level, from, destination);
            // Sweep the complete item box, not just its centre ray. Shape intersections permit
            // partial blocks (slabs, fences) when the item box actually clears them.
            int samples = Math.max(1, (int) Math.ceil(from.distanceTo(destination) / .03125));
            for (int i = 0; i <= samples; i++) {
                Vec3 point = from.lerp(destination, (double) i / samples);
                AABB box = itemBox(point);
                if (!inside(level, new Vec3(box.minX, box.minY, box.minZ))
                        || !inside(level, new Vec3(box.maxX, box.maxY - .000001, box.maxZ))) return false;
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                    var shape = getter.getBlockState(pos).getCollisionShape(getter, pos);
                    if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()),
                            Shapes.create(box), BooleanOp.AND)) return false;
                }
                if (!level.getEntities(body, box, entity -> entity != excludedCarrier).isEmpty()) return false;
            }
            return true;
        } catch (MissingChunk ignored) { return false; }
    }

    private static AABB itemBox(Vec3 point) {
        return new AABB(point.x - .125, point.y, point.z - .125,
                point.x + .125, point.y + .25, point.z + .125);
    }

    private static LoadedGetter loadedAlong(ServerLevel level, Vec3 from, Vec3 to) {
        int minX = BlockPos.containing(Math.min(from.x, to.x) - .125, from.y, from.z).getX() >> 4;
        int maxX = BlockPos.containing(Math.max(from.x, to.x) + .125, from.y, from.z).getX() >> 4;
        int minZ = BlockPos.containing(from.x, from.y, Math.min(from.z, to.z) - .125).getZ() >> 4;
        int maxZ = BlockPos.containing(from.x, from.y, Math.max(from.z, to.z) + .125).getZ() >> 4;
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
            BlockHitResult block = new LoadedGetter(level, minX, maxX, minZ, maxZ).clip(
                    new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
            if (!finite(block.getLocation())) return null;
            double blockerDistance = block.getType() == HitResult.Type.BLOCK
                    ? from.distanceTo(block.getLocation()) : reach;
            ItemEntity first = null;
            Vec3 firstHit = null;
            double firstDistance = Double.POSITIVE_INFINITY;
            // ServerLevel's entity index enumerates loaded entities; do not use Level.clip or chunk lookups.
            for (ItemEntity item : level.getEntities(net.minecraft.world.entity.EntityType.ITEM,
                    new AABB(from, to).inflate(TARGET_MARGIN), entity -> true)) {
                if (item.level() != level || item.isRemoved() || !item.isAddedToLevel()
                        || level.getEntity(item.getUUID()) != item) continue;
                AABB box = item.getBoundingBox();
                Vec3 hit = box.inflate(TARGET_MARGIN).contains(from) ? from
                        : box.inflate(TARGET_MARGIN).clip(from, to).orElse(null);
                if (hit == null || !finite(hit)) continue;
                double distance = from.distanceToSqr(hit);
                // The inflated hit can precede a wall even when the real item is entirely behind it.
                // Require the near face of the actual box to be on the visible side of the collider.
                double nearFace = (look.x >= 0 ? box.minX : box.maxX) * look.x
                        + (look.y >= 0 ? box.minY : box.maxY) * look.y
                        + (look.z >= 0 ? box.minZ : box.maxZ) * look.z - from.dot(look);
                boolean eyeOverlap = box.contains(from);
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
        } catch (MissingChunk ignored) {
            return null;
        }
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
