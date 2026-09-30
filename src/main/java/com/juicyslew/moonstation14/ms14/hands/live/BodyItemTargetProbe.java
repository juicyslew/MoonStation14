package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
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
    public static final int MAX_CHUNKS = 16;
    public static final double TARGET_MARGIN = 0.20;

    private BodyItemTargetProbe() { }

    public record Target(UUID itemId, Vec3 hitLocation) { }

    /** Conservative short drop path. A missing chunk or any destination collider leaves the item at the feet. */
    @Nullable
    static Vec3 forwardDropSpot(ServerLevel level, LivingEntity body) {
        Vec3 feet = body.position();
        float yaw = body.getYRot();
        if (!finite(feet) || !Float.isFinite(yaw)) return null;
        double angle = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(angle) * .7, 0, Math.cos(angle) * .7);
        Vec3 destination = feet.add(forward);
        Vec3 from = feet.add(0, .125, 0), to = destination.add(0, .125, 0);
        if (!inside(level, destination) || !inside(level, to)) return null;
        int minX = (Math.min(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) + 1;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) return null;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return null;
        try {
            LoadedGetter getter = new LoadedGetter(level, minX, maxX, minZ, maxZ);
            if (getter.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, body)).getType() != HitResult.Type.MISS) return null;
            // Reject any collidable destination cell that may contain the quarter-block item box.
            for (int x = BlockPos.containing(destination.x - .125, destination.y, destination.z).getX();
                 x <= BlockPos.containing(destination.x + .125, destination.y, destination.z).getX(); x++)
                for (int z = BlockPos.containing(destination.x, destination.y, destination.z - .125).getZ();
                     z <= BlockPos.containing(destination.x, destination.y, destination.z + .125).getZ(); z++)
                    for (int y = BlockPos.containing(destination).getY();
                         y <= BlockPos.containing(destination.add(0, .25, 0)).getY(); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!getter.getBlockState(pos).getCollisionShape(getter, pos).isEmpty()) return null;
                    }
            return destination;
        } catch (MissingChunk ignored) {
            return null;
        }
    }

    @Nullable
    public static Target probe(ServerLevel level, LivingEntity body, ItemEntity nominated, double reach) {
        if (level == null || body == null || nominated == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body
                || nominated.level() != level || nominated.isRemoved() || !nominated.isAddedToLevel()
                || level.getEntity(nominated.getUUID()) != nominated || nominated.getTarget() != null
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
            boolean ambiguous = false;
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
                if (!Double.isFinite(distance) || distance >= blockerDistance * blockerDistance
                        || nearFace >= blockerDistance || distance > reach * reach) continue;
                // Equal-distance hits are ambiguous; never pick based on entity iteration order.
                if (distance == firstDistance) ambiguous = true;
                if (distance < firstDistance) {
                    first = item;
                    firstHit = hit;
                    firstDistance = distance;
                    ambiguous = false;
                }
            }
            return !ambiguous && first == nominated ? new Target(nominated.getUUID(), firstHit) : null;
        } catch (MissingChunk ignored) {
            return null;
        }
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
