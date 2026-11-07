package com.juicyslew.moonstation14.ms14.power.cable;

import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Pure narrow hit-target selector for the procedural cable quads. */
public final class CableCutGeometry {
    private CableCutGeometry() { }

    public static Optional<CableTier> select(BlockPos host, Direction face, Vec3 hit, Direction hitFace,
                                             List<CableTier> tiers, List<CableFaceNode> neighbors) {
        if (hit == null || hitFace != face || !Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)) return Optional.empty();
        double x = hit.x - ((double) host.getX() + .5), y = hit.y - ((double) host.getY() + .5), z = hit.z - ((double) host.getZ() + .5);
        double normal = switch (face.getAxis()) { case X -> x * face.getStepX(); case Y -> y * face.getStepY(); case Z -> z * face.getStepZ(); };
        if (Math.abs(normal - .5) > .005) return Optional.empty();
        double[] point = project(face, x, y, z);
        double u = point[0], v = point[1];
        if (u < -.5 || u > .5 || v < -.5 || v > .5) return Optional.empty();
        CableTier selected = null;
        for (CableTier tier : tiers) {
            CableFaceNode node = new CableFaceNode(host, face, tier);
            for (List<CableVisualGeometry.Vector3> quad : CableVisualGeometry.quads(node, neighbors)) {
                if (contains(quad, face, u, v) && (selected == null || CableVisualGeometry.drawPriority(tier) > CableVisualGeometry.drawPriority(selected))) selected = tier;
            }
        }
        return Optional.ofNullable(selected);
    }

    private static double[] project(Direction face, double x, double y, double z) {
        return switch (face.getAxis()) { case X -> new double[]{y, z}; case Y -> new double[]{x, z}; case Z -> new double[]{x, y}; };
    }

    private static boolean contains(List<CableVisualGeometry.Vector3> q, Direction face, double u, double v) {
        boolean inside = false;
        for (int i=0,j=q.size()-1; i<q.size(); j=i++) {
            var a=q.get(i); var b=q.get(j);
            double[] ap = project(face, a.x(), a.y(), a.z()), bp = project(face, b.x(), b.y(), b.z());
            double au=ap[0], av=ap[1], bu=bp[0], bv=bp[1];
            if (Math.abs((bu-au)*(v-av)-(bv-av)*(u-au)) < 1e-8 && u >= Math.min(au,bu)-1e-8 && u <= Math.max(au,bu)+1e-8 && v >= Math.min(av,bv)-1e-8 && v <= Math.max(av,bv)+1e-8) return true;
            if ((av > v) != (bv > v) && u < (bu-au)*(v-av)/(bv-av)+au) inside=!inside;
        }
        return inside;
    }
}
