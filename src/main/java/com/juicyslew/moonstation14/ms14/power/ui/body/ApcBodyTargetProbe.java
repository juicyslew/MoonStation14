package com.juicyslew.moonstation14.ms14.power.ui.body;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Read-only, server-body-origin APC sight probe; its immutable hit is not interaction or mutation authority.
 * This bounded local-first prototype denies unknown/unloaded reads through its BlockGetter, but cannot
 * sandbox arbitrary mod block shape callbacks that access {@code body.level()} directly. Such callbacks
 * may load chunks independently of the getter. False negatives are acceptable; callers must revalidate.
 */
public final class ApcBodyTargetProbe {
    private ApcBodyTargetProbe() { }

    /** Minecraft block units; a defensive cost/reach ceiling, not an SS14 distance conversion. */
    public static final double MAX_REACH = 6.0;
    public static final int MAX_CHUNKS = 16;

    public record Target(BlockPos pos, Direction face) {
        public Target { pos = pos.immutable(); }
    }

    @Nullable
    public static Target probe(ServerLevel level, LivingEntity body, BlockPos expectedTarget, double maxReach) {
        if (level == null || body == null || expectedTarget == null || !level.getServer().isSameThread()
                || body.level() != level || body.isRemoved() || !body.isAlive()
                || level.getEntity(body.getId()) != body
                || !Double.isFinite(maxReach) || maxReach <= 0 || maxReach > MAX_REACH)
            return null;

        Vec3 from = body.getEyePosition();
        Vec3 look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < 0.99 || look.lengthSqr() > 1.01)
            return null;
        Vec3 to = from.add(look.scale(maxReach));
        if (!finite(to) || from.equals(to) || !inside(level, from) || !inside(level, to)
                || !level.getWorldBorder().isWithinBounds(expectedTarget) || level.isOutsideBuildHeight(expectedTarget))
            return null;

        // One-chunk margin covers adjacent getter queries; further getter reads are denied.
        int minX = (Math.min(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), BlockPos.containing(to).getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), BlockPos.containing(to).getZ()) >> 4) + 1;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) return null;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return null;

        LoadedGetter getter = new LoadedGetter(level, minX, maxX, minZ, maxZ);
        try {
            BlockHitResult hit = getter.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, body));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(expectedTarget)
                    || !finite(hit.getLocation()) || !level.getWorldBorder().isWithinBounds(hit.getLocation())
                    || from.distanceToSqr(hit.getLocation()) > maxReach * maxReach)
                return null;
            LevelChunk chunk = getter.chunk(expectedTarget);
            if (!(chunk.getBlockState(expectedTarget).getBlock() instanceof PowerDeviceBlock block)
                    || block.kind() != PowerDeviceKind.APC
                    || !(chunk.getBlockEntities().get(expectedTarget) instanceof PowerDeviceBlockEntity device)
                    || device.isRemoved() || device.getLevel() != level || !device.getBlockPos().equals(expectedTarget))
                return null;
            return new Target(expectedTarget, hit.getDirection());
        } catch (MissingChunk ignored) {
            // Shape/interaction callbacks can read beyond the ray rectangle; never substitute air.
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

    /** Getter reads use loaded FULL chunks only; arbitrary shape callbacks can still access the Level. */
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
