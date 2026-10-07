package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Read-only block-use admission; bounded getter reads do not sandbox arbitrary shape callbacks. */
public final class BodyBlockUseTargetProbe {
    /** Defensive ceiling in Minecraft block units, not an SS14 distance conversion. */
    public static final double MAX_REACH = 6.0;
    private static final int MAX_CHUNKS = 16;
    private BodyBlockUseTargetProbe() { }

    public record Target(BlockPos pos, Direction face, Vec3 location) {
        public Target { pos = pos.immutable(); }
    }

    @Nullable
    public static Target probe(ServerLevel level, LivingEntity body, BlockPos nominated, Direction nominatedFace) {
        if (level == null || body == null || nominated == null || nominatedFace == null
                || !level.getServer().isSameThread() || body.level() != level || !body.isAlive() || body.isRemoved()
                || level.getEntity(body.getId()) != body) return null;
        // Contained bodies have no camera-derived world ray: their exact live owner grants one
        // immutable block target. Re-read owner and view on each admission; transit has no target.
        var owner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                .ownerOf(body).orElse(null);
        if (owner != null) {
            var view = owner.view();
            if (!owner.isValid() || view == null || !view.ownerIdentity().equals(owner.identity())
                    || view.worldTarget().isEmpty() || !view.worldTarget().orElseThrow().equals(nominated)
                    || !level.getWorldBorder().isWithinBounds(nominated)
                    || level.isOutsideBuildHeight(nominated)) return null;
            var chunk = level.getChunkSource().getChunkNow(nominated.getX() >> 4, nominated.getZ() >> 4);
            if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) return null;
            Vec3 center = Vec3.atCenterOf(nominated);
            return new Target(nominated, Direction.UP, center.add(0, .5, 0));
        }
        Vec3 from = body.getEyePosition(), look = body.getLookAngle();
        if (!finite(from) || !finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01) return null;
        Vec3 to = from.add(look.scale(MAX_REACH));
        if (!finite(to) || from.equals(to) || !inside(level, from) || !inside(level, to)
                || !level.getWorldBorder().isWithinBounds(nominated) || level.isOutsideBuildHeight(nominated)) return null;

        int fromX = BlockPos.containing(from).getX(), toX = BlockPos.containing(to).getX();
        int fromZ = BlockPos.containing(from).getZ(), toZ = BlockPos.containing(to).getZ();
        int minX = (Math.min(fromX, toX) >> 4) - 1, maxX = (Math.max(fromX, toX) >> 4) + 1;
        int minZ = (Math.min(fromZ, toZ) >> 4) - 1, maxZ = (Math.max(fromZ, toZ) >> 4) + 1;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) return null;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return null;

        try {
            LoadedGetter getter = new LoadedGetter(level, minX, maxX, minZ, maxZ);
            BlockHitResult hit = getter.clip(new ClipContext(
                    from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body) {
                @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(
                        BlockState state, BlockGetter ignored, BlockPos pos) {
                    // Keep state/fluid reads bounded, but preserve the real level context for
                    // dynamic shapes (e.g. disposal tube geometry backed by live records).
                    return super.getBlockShape(state, level, pos);
                }
            });
            return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(nominated)
                    && hit.getDirection() == nominatedFace && finite(hit.getLocation())
                    && level.getWorldBorder().isWithinBounds(hit.getLocation())
                    && from.distanceToSqr(hit.getLocation()) <= MAX_REACH * MAX_REACH
                    ? new Target(nominated, hit.getDirection(), hit.getLocation()) : null;
        } catch (MissingChunk ignored) {
            return null;
        } catch (PipeGasTransaction.RecoveryRequiredException recoveryRequired) {
            throw recoveryRequired;
        } catch (RuntimeException deniedRead) {
            return null;
        }
    }


    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
    private static boolean inside(ServerLevel level, Vec3 v) {
        return !level.isOutsideBuildHeight(BlockPos.containing(v)) && level.getWorldBorder().isWithinBounds(v);
    }
    private static final class MissingChunk extends RuntimeException { }

    /** FULL-chunk-only reads. Block shape callbacks may independently access the Level and load chunks. */
    private static final class LoadedGetter implements BlockGetter {
        private final ServerLevel level;
        private final int minX, maxX, minZ, maxZ;
        private LoadedGetter(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
            this.level = level; this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
        }
        private LevelChunk chunk(BlockPos pos) {
            int x = pos.getX() >> 4, z = pos.getZ() >> 4;
            if (x < minX || x > maxX || z < minZ || z > maxZ || level.isOutsideBuildHeight(pos)) throw new MissingChunk();
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) throw new MissingChunk();
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return chunk(pos).getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return chunk(pos).getFluidState(pos); }
        @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) { return chunk(pos).getBlockEntities().get(pos); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
