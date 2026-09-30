package com.juicyslew.moonstation14.ms14.chat.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/** Bounded straight-line approximation; never searches around corners or loads chunks. */
public final class LocalSpeechOcclusion {
    static final int MAX_VISITED_CELLS = 160;
    static final int MAX_RAYS = 5;
    private static final int MAX_BOXES_PER_CELL = 32;
    private static final int MAX_BOX_TESTS = 1024;
    private static final double SPEECH_RANGE = 15.0;
    private static final double SOLID_ATTENUATION = 8.5;
    private static final double PROBE_OFFSET = 0.18;
    private static final double[][] OFFSETS = {{0, 0}, {PROBE_OFFSET, 0}, {-PROBE_OFFSET, 0},
            {0, PROBE_OFFSET}, {0, -PROBE_OFFSET}};

    private LocalSpeechOcclusion() { }

    /** Never requests a chunk; shapes use a neighbor-free view. */
    public static boolean audible(ServerLevel level, Vec3 sourceEye, Vec3 listenerEye) {
        if (level == null) return false;
        return audible(sourceEye, listenerEye, pos -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return new Cell(false, null);
            return new Cell(true, chunk.getBlockState(pos).getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
        });
    }

    @FunctionalInterface
    interface BlockSampler {
        Cell sample(BlockPos pos);
    }

    record Cell(boolean loaded, VoxelShape collision) { }

    static boolean audible(Vec3 sourceEye, Vec3 listenerEye, BlockSampler sampler) {
        return audible(sourceEye, listenerEye, sampler, MAX_VISITED_CELLS);
    }

    static boolean audible(Vec3 sourceEye, Vec3 listenerEye, BlockSampler sampler, int maxVisitedCells) {
        if (sourceEye == null || listenerEye == null || sampler == null || maxVisitedCells < 1
                || !valid(sourceEye) || !valid(listenerEye)) return false;
        double distance = sourceEye.distanceTo(listenerEye);
        if (!Double.isFinite(distance) || distance > SPEECH_RANGE) return false;
        // Exactly five parallel eye lines, displaced at most 0.18 blocks. No grid,
        // neighbor exploration or detour around a wall. Each ray pays for the actual
        // length inside collision material; exposed Y faces block a ray outright.
        Map<BlockPos, Cell> cache = new HashMap<>();
        int[] boxTests = {0};
        try {
            for (int i = 0; i < MAX_RAYS; i++) {
                double[] offset = OFFSETS[i];
                Vec3 start = sourceEye.add(offset[0], 0, offset[1]);
                Vec3 end = listenerEye.add(offset[0], 0, offset[1]);
                double[] thickness = {0};
                Boolean clear = BlockGetter.traverseBlocks(start, end, null, (ignored, mutablePos) -> {
                    BlockPos pos = mutablePos.immutable();
                    Cell cell = sample(pos, sampler, cache, maxVisitedCells);
                    VoxelShape shape = cell.collision();
                    if (shape.isEmpty()) return null;
                    // Each voxel box is intersected with this segment, not merely counted
                    // as a cell. VoxelShape boxes have disjoint interiors.
                    var boxes = shape.toAabbs();
                    if (boxes.size() > MAX_BOXES_PER_CELL) throw new SamplingFailure();
                    for (AABB box : boxes) {
                        if (++boxTests[0] > MAX_BOX_TESTS) throw new SamplingFailure();
                        Intersection intersection = intersect(start, end, box.move(pos), distance);
                        if (intersection.bottomFace() && !continuedAcrossFace(start, end, box.move(pos), pos,
                                false, sampler, cache, maxVisitedCells, boxTests)) return false;
                        if (intersection.topFace() && !continuedAcrossFace(start, end, box.move(pos), pos,
                                true, sampler, cache, maxVisitedCells, boxTests)) return false;
                        thickness[0] += intersection.length();
                    }
                    return null;
                }, ignored -> true);
                if (Boolean.TRUE.equals(clear)
                        && distance + SOLID_ATTENUATION * Math.sqrt(thickness[0]) <= SPEECH_RANGE) return true;
            }
        } catch (Exception exception) {
            return false;
        }
        return false;
    }

    private static Cell sample(BlockPos pos, BlockSampler sampler, Map<BlockPos, Cell> cache, int maxVisitedCells) {
        Cell cell = cache.get(pos);
        if (cell == null) {
            if (cache.size() >= maxVisitedCells) throw new SamplingFailure();
            cell = sampler.sample(pos);
            if (cell == null || !cell.loaded() || cell.collision() == null) throw new SamplingFailure();
            cache.put(pos.immutable(), cell);
        }
        return cell;
    }

    private static boolean continuedAcrossFace(Vec3 start, Vec3 end, AABB box, BlockPos pos, boolean top,
                                               BlockSampler sampler, Map<BlockPos, Cell> cache, int maxVisitedCells,
                                               int[] boxTests) {
        double faceY = top ? box.maxY : box.minY;
        if (Math.abs(faceY - (top ? pos.getY() + 1 : pos.getY())) > 1.0e-10) return false;
        BlockPos neighbor = top ? pos.above() : pos.below();
        VoxelShape shape = sample(neighbor, sampler, cache, maxVisitedCells).collision();
        var boxes = shape.toAabbs();
        if (boxes.size() > MAX_BOXES_PER_CELL) throw new SamplingFailure();
        double t = (faceY - start.y) / (end.y - start.y);
        // Inspect just across the seam, towards the adjacent material, not a
        // nearby column that the angled ray never actually touches.
        double step = (top ? 1.0 : -1.0) * 1.0e-8 / (end.y - start.y);
        double x = start.x + (t + step) * (end.x - start.x) - neighbor.getX();
        double y = faceY + (top ? 1.0e-8 : -1.0e-8) - neighbor.getY();
        double z = start.z + (t + step) * (end.z - start.z) - neighbor.getZ();
        for (AABB adjacent : boxes) {
            if (++boxTests[0] > MAX_BOX_TESTS) throw new SamplingFailure();
            if (x > adjacent.minX && x < adjacent.maxX && y > adjacent.minY && y < adjacent.maxY
                    && z > adjacent.minZ && z < adjacent.maxZ) return true;
        }
        return false;
    }

    private record Intersection(double length, boolean bottomFace, boolean topFace) { }

    private static Intersection intersect(Vec3 start, Vec3 end, AABB box, double distance) {
        double[] origin = {start.x, start.y, start.z};
        double[] delta = {end.x - start.x, end.y - start.y, end.z - start.z};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        double lo = 0, hi = 1;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0e-12) {
                if (origin[axis] <= min[axis] || origin[axis] >= max[axis]) return new Intersection(0, false, false);
            } else {
                double a = (min[axis] - origin[axis]) / delta[axis];
                double b = (max[axis] - origin[axis]) / delta[axis];
                lo = Math.max(lo, Math.min(a, b));
                hi = Math.min(hi, Math.max(a, b));
                if (hi <= lo) return new Intersection(0, false, false);
            }
        }
        // An oblique ray may enter through a side and leave through the top (or
        // start inside the box). Test both Y boundaries of the actual intersected
        // box, rather than only the first face returned by VoxelShape.clip.
        boolean bottomFace = false, topFace = false;
        if (Math.abs(delta[1]) >= 1.0e-12) {
            double bottom = (min[1] - origin[1]) / delta[1];
            double top = (max[1] - origin[1]) / delta[1];
            double yEntry = Math.min(bottom, top);
            double yExit = Math.max(bottom, top);
            bottomFace = bottom >= 0 && bottom <= 1
                    && (Math.abs(lo - bottom) < 1.0e-10 || Math.abs(hi - bottom) < 1.0e-10);
            topFace = top >= 0 && top <= 1
                    && (Math.abs(lo - top) < 1.0e-10 || Math.abs(hi - top) < 1.0e-10);
        }
        return new Intersection((hi - lo) * distance, bottomFace, topFace);
    }

    private static boolean valid(Vec3 position) {
        return LocalSpeechPolicy.validPosition(position.x, position.y, position.z);
    }

    private static final class SamplingFailure extends RuntimeException { }
}
