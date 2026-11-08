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
        if (nominated == null || nominatedFace == null) return null;
        return select(level, body, nominated, nominatedFace);
    }

    /** Nominated melee point chooses a ray only; the server's first OUTLINE hit remains authoritative. */
    @Nullable
    public static Target probeNominatedContact(ServerLevel level, LivingEntity body, BlockPos pos,
                                                Direction face, Vec3 nominatedPoint) {
        return probeNominatedContact(level, body, pos, face, nominatedPoint, MAX_REACH);
    }

    /** Nominated melee contact bounded by the resolved weapon reach. */
    @Nullable
    public static Target probeNominatedContact(ServerLevel level, LivingEntity body, BlockPos pos,
                                                Direction face, Vec3 nominatedPoint, double maximumReach) {
        if (pos == null || face == null || nominatedPoint == null || !finite(nominatedPoint)
                || !Double.isFinite(maximumReach) || maximumReach <= 0 || maximumReach > MAX_REACH
                || !new net.minecraft.world.phys.AABB(pos).inflate(1.0e-5).contains(nominatedPoint)
                || !onFace(pos, face, nominatedPoint)) return null;
        Vec3 end = nominatedPoint.add(-face.getStepX() * 1.0e-3, -face.getStepY() * 1.0e-3, -face.getStepZ() * 1.0e-3);
        return select(level, body, pos, face, end, maximumReach);
    }

    @Nullable
    public static Target probeSelected(ServerLevel level, LivingEntity body) {
        return select(level, body, null, null);
    }

    /** Shared bounded ray; nominated calls validate identity/face, selected calls return the actual first hit. */
    @Nullable
    private static Target select(ServerLevel level, LivingEntity body, @Nullable BlockPos nominated,
                                 @Nullable Direction nominatedFace) {
        return select(level, body, nominated, nominatedFace, null);
    }

    @Nullable
    private static Target select(ServerLevel level, LivingEntity body, @Nullable BlockPos nominated,
                                  @Nullable Direction nominatedFace, @Nullable Vec3 nominatedEnd) {
        return select(level, body, nominated, nominatedFace, nominatedEnd, MAX_REACH);
    }

    private static Target select(ServerLevel level, LivingEntity body, @Nullable BlockPos nominated,
                                  @Nullable Direction nominatedFace, @Nullable Vec3 nominatedEnd, double maximumReach) {
        if (level == null || body == null || (nominated == null) != (nominatedFace == null)
                || !level.getServer().isSameThread() || body.level() != level || !body.isAlive() || body.isRemoved()
                || level.getEntity(body.getId()) != body) return null;
        // Contained bodies have no camera-derived world ray: their exact live owner grants one
        // immutable block target. Re-read owner and view on each admission; transit has no target.
        var owner = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                .ownerOf(body).orElse(null);
        if (owner != null) {
            // Ordinary block use may use a container's world target; nominated melee has no
            // corresponding world-reach admission and must not inherit that permission.
            if (nominatedEnd != null) return null;
            var view = owner.view();
            if (!owner.isValid() || view == null || !view.ownerIdentity().equals(owner.identity())
                    || view.worldTarget().isEmpty()) return null;
            BlockPos pos = view.worldTarget().orElseThrow();
            if ((nominated != null && !pos.equals(nominated))
                    || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return null;
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) return null;
            Vec3 center = Vec3.atCenterOf(pos);
            return new Target(pos, Direction.UP, center.add(0, .5, 0));
        }
        Vec3 from = body.getEyePosition(), look = body.getLookAngle();
        if (!finite(from) || nominatedEnd == null && (!finite(look) || look.lengthSqr() < .99 || look.lengthSqr() > 1.01)) return null;
        Vec3 to = nominatedEnd == null ? from.add(look.scale(maximumReach)) : nominatedEnd;
        if (!finite(to) || from.equals(to) || !inside(level, from) || !inside(level, to)
                || (nominated != null && (!level.getWorldBorder().isWithinBounds(nominated)
                || level.isOutsideBuildHeight(nominated)))) return null;

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
            // Nominated admission requires an exact match; selection reports the ray's actual first hit.
            boolean validHit = hit.getType() == HitResult.Type.BLOCK && finite(hit.getLocation())
                    && (nominated == null || hit.getBlockPos().equals(nominated) && hit.getDirection() == nominatedFace);
            return validHit
                    && level.getWorldBorder().isWithinBounds(hit.getLocation())
                    && from.distanceToSqr(hit.getLocation()) <= maximumReach * maximumReach
                    ? new Target(hit.getBlockPos(), hit.getDirection(), hit.getLocation()) : null;
        } catch (MissingChunk ignored) {
            return null;
        } catch (PipeGasTransaction.RecoveryRequiredException recoveryRequired) {
            throw recoveryRequired;
        } catch (RuntimeException deniedRead) {
            return null;
        }
    }

    /** Bounded distance/loading check for block positions already admitted by the body-eye caller. */
    public static boolean withinReach(ServerLevel level, LivingEntity body, BlockPos pos) {
        if (level == null || body == null || pos == null || body.level() != level || !level.getServer().isSameThread()
                || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                && body.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_REACH * MAX_REACH;
    }

    /** UI continuation admission: range and visible loaded identity, without requiring current aim. */
    public static boolean lineOfSight(ServerLevel level, LivingEntity body, BlockPos pos) {
        if (!withinReach(level, body, pos)) return false;
        var from = body.getEyePosition(); var to = Vec3.atCenterOf(pos);
        if (!finite(from) || !finite(to)) return false;
        int minX = (Math.min(BlockPos.containing(from).getX(), pos.getX()) >> 4) - 1;
        int maxX = (Math.max(BlockPos.containing(from).getX(), pos.getX()) >> 4) + 1;
        int minZ = (Math.min(BlockPos.containing(from).getZ(), pos.getZ()) >> 4) - 1;
        int maxZ = (Math.max(BlockPos.containing(from).getZ(), pos.getZ()) >> 4) + 1;
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_CHUNKS) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        try {
            var getter = new LoadedGetter(level, minX, maxX, minZ, maxZ);
            var hit = getter.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body) {
                @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(
                        BlockState state, BlockGetter ignored, BlockPos blockPos) {
                    return super.getBlockShape(state, level, blockPos);
                }
            });
            return hit.getType() == HitResult.Type.MISS
                    || hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
        } catch (MissingChunk ignored) {
            return false;
        } catch (RuntimeException deniedRead) {
            return false;
        }
    }


    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
    private static boolean onFace(BlockPos p, Direction face, Vec3 v) {
        double epsilon = 1.0e-4;
        return switch (face) {
            case WEST -> Math.abs(v.x - p.getX()) <= epsilon;
            case EAST -> Math.abs(v.x - (p.getX() + 1)) <= epsilon;
            case DOWN -> Math.abs(v.y - p.getY()) <= epsilon;
            case UP -> Math.abs(v.y - (p.getY() + 1)) <= epsilon;
            case NORTH -> Math.abs(v.z - p.getZ()) <= epsilon;
            case SOUTH -> Math.abs(v.z - (p.getZ() + 1)) <= epsilon;
        };
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
