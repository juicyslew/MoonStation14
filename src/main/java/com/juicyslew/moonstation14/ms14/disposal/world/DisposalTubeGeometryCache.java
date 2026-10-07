package com.juicyslew.moonstation14.ms14.disposal.world;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/** Shared immutable products for the finite set of physical tube port configurations. */
public final class DisposalTubeGeometryCache {
    private static final int MAX_ENTRIES = 256;
    private static final Map<Key, Products> CACHE = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Products> eldest) { return size() > MAX_ENTRIES; }
    };
    private DisposalTubeGeometryCache() { }

    public static synchronized Products get(DisposalEntityData.TubeGeometry geometry, DisposalEntityData.Tube tube, DisposalOrientation frame) {
        EnumSet<Direction> ports = EnumSet.noneOf(Direction.class);
        for (var port : DisposalTubeRouting.connections(tube, frame)) ports.add(port.toMC());
        if (tube.kind() == DisposalEntityData.Kind.TRUNK) ports.add(Direction.UP);
        int mask = 0;
        for (Direction direction : ports) mask |= 1 << direction.ordinal();
        Key key = new Key(Double.doubleToLongBits(geometry.diameter()), mask);
        return CACHE.computeIfAbsent(key, ignored -> build(geometry, tube, frame));
    }

    private static Products build(DisposalEntityData.TubeGeometry geometry, DisposalEntityData.Tube tube, DisposalOrientation frame) {
        var boxes = geometry.buildBoxes(tube, frame);
        // CSG voxel grids grow with the number of distinct boundary coordinates. Quantize
        // collision bounds outward to cap every axis at the 1/16 block lattice (17 points).
        VoxelShape shape = Shapes.empty();
        for (AABB box : boxes) {
            double x0 = floorGrid(box.minX), y0 = floorGrid(box.minY), z0 = floorGrid(box.minZ);
            double x1 = ceilGrid(box.maxX), y1 = ceilGrid(box.maxY), z1 = ceilGrid(box.maxZ);
            VoxelShape piece = Shapes.box(x0, y0, z0, x1, y1, z1);
            shape = Shapes.joinUnoptimized(shape, piece, BooleanOp.OR);
        }
        return new Products(boxes, shape, geometry.buildMesh(tube, frame));
    }

    private static double floorGrid(double value) { return Math.floor(value * 16.0) / 16.0; }
    private static double ceilGrid(double value) { return Math.ceil(value * 16.0) / 16.0; }

    private record Key(long diameter, int ports) { }
    public record Products(List<AABB> boxes, VoxelShape shape, List<DisposalMeshGeometry.Quad> mesh) { }
}
