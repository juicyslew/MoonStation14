package com.juicyslew.moonstation14.ms14.singularity.containment;

import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.function.Predicate;

/** Pure axis-aligned eight-corner enclosure proof over already-established edge connections. */
public final class ClosedFieldVolume {
    private ClosedFieldVolume() {}
    public record Bounds(BlockPos min, BlockPos max) {}
    /** Generator failsafe proof: center is inside the box and every one of six face distances is rated. */
    public static boolean containsWithinFaceDistance(Bounds bounds, net.minecraft.world.phys.Vec3 center, double distance) {
        return bounds != null && center != null && Double.isFinite(distance) && distance >= 0
                && center.x >= FieldGeometry.center(bounds.min().getX()) && center.x <= FieldGeometry.center(bounds.max().getX())
                && center.y >= FieldGeometry.center(bounds.min().getY()) && center.y <= FieldGeometry.center(bounds.max().getY())
                && center.z >= FieldGeometry.center(bounds.min().getZ()) && center.z <= FieldGeometry.center(bounds.max().getZ())
                && center.x - FieldGeometry.center(bounds.min().getX()) <= distance && FieldGeometry.center(bounds.max().getX()) - center.x <= distance
                && center.y - FieldGeometry.center(bounds.min().getY()) <= distance && FieldGeometry.center(bounds.max().getY()) - center.y <= distance
                && center.z - FieldGeometry.center(bounds.min().getZ()) <= distance && FieldGeometry.center(bounds.max().getZ()) - center.z <= distance;
    }
    public static Optional<Bounds> derive(Collection<BlockPos> corners, Set<Edge> validEdges, Predicate<BlockPos> loaded) {
        if (corners.size() != 8 || new HashSet<>(corners).size() != 8) return Optional.empty();
        int minX=corners.stream().mapToInt(BlockPos::getX).min().orElseThrow(), maxX=corners.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minY=corners.stream().mapToInt(BlockPos::getY).min().orElseThrow(), maxY=corners.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int minZ=corners.stream().mapToInt(BlockPos::getZ).min().orElseThrow(), maxZ=corners.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        if (maxX-minX > 8 || maxY-minY > 8 || maxZ-minZ > 8 || minX==maxX || minY==maxY || minZ==maxZ) return Optional.empty();
        Set<BlockPos> expected=new HashSet<>();
        for (int x : new int[]{minX,maxX}) for(int y:new int[]{minY,maxY}) for(int z:new int[]{minZ,maxZ}) expected.add(new BlockPos(x,y,z));
        if (!expected.equals(new HashSet<>(corners))) return Optional.empty();
        Set<Edge> required=new HashSet<>();
        for (BlockPos a : expected) for (BlockPos b : expected) {
            int differing = (a.getX()!=b.getX()?1:0)+(a.getY()!=b.getY()?1:0)+(a.getZ()!=b.getZ()?1:0);
            if (differing == 1) required.add(new Edge(a,b));
        }
        if (!validEdges.containsAll(required) || !expected.stream().allMatch(loaded)) return Optional.empty();
        return Optional.of(new Bounds(new BlockPos(minX,minY,minZ),new BlockPos(maxX,maxY,maxZ)));
    }
    public record Edge(BlockPos first, BlockPos second) {
        public Edge { if (first.compareTo(second)>0) { BlockPos t=first; first=second; second=t; } }
    }
}

