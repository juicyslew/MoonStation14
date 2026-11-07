package com.juicyslew.moonstation14.ms14.power.cable;

import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class CableCutGeometryTest {
    private static final List<CableTier> ALL = List.of(CableTier.HV, CableTier.MV, CableTier.APC);

    @Test void selectsProductionCapQuadsOnEveryFaceAndSignedHost() {
        for (Direction face : Direction.values()) for (int sign : List.of(-1, 1)) {
            BlockPos host = new BlockPos(sign * 37, -sign * 19, sign * 91);
            for (CableTier tier : ALL) {
                CableFaceNode node = new CableFaceNode(host, face, tier);
                var corners = CableVisualGeometry.centerCorners(node, CableVisualGeometry.CAP_HALF_WIDTH);
                double[] center = center(corners, face);
                assertEquals(Optional.of(tier), CableCutGeometry.select(host, face, hit(host, face, center), face, ALL, List.of()),
                        face + " " + tier);
            }
        }
    }

    @Test void missesGapsAndRejectsWrongFaceBoundsAndNonFinite() {
        BlockPos host = BlockPos.ZERO;
        assertTrue(CableCutGeometry.select(host, Direction.UP, new Vec3(.08, .5, .08), Direction.UP, ALL, List.of()).isEmpty());
        assertTrue(CableCutGeometry.select(host, Direction.UP, new Vec3(0, .5, 0), Direction.DOWN, ALL, List.of()).isEmpty());
        assertTrue(CableCutGeometry.select(host, Direction.UP, new Vec3(0, .5, .51), Direction.UP, ALL, List.of()).isEmpty());
        assertTrue(CableCutGeometry.select(host, Direction.UP, new Vec3(Double.NaN, .5, 0), Direction.UP, ALL, List.of()).isEmpty());
    }

    @Test void largeCoordinatesSubtractHostInDoubleBeforeProjection() {
        BlockPos host = new BlockPos(20_000_000, -20_000_000, 20_000_000);
        var face = Direction.NORTH;
        var node = new CableFaceNode(host, face, CableTier.MV);
        double[] center = center(CableVisualGeometry.centerCorners(node, CableVisualGeometry.CAP_HALF_WIDTH), face);
        assertEquals(Optional.of(CableTier.MV), CableCutGeometry.select(host, face, hit(host, face, center), face, ALL, List.of()));
    }

    private static double[] center(List<CableVisualGeometry.Vector3> quad, Direction face) {
        double u=0, v=0;
        for (var p : quad) {
            double[] projected = project(face, p.x(), p.y(), p.z()); u += projected[0]; v += projected[1];
        }
        return new double[]{u/quad.size(), v/quad.size()};
    }

    private static Vec3 hit(BlockPos host, Direction face, double[] uv) {
        double x=0,y=0,z=0;
        switch (face.getAxis()) {
            case X -> { x=face.getStepX()*.5; y=uv[0]; z=uv[1]; }
            case Y -> { x=uv[0]; y=face.getStepY()*.5; z=uv[1]; }
            case Z -> { x=uv[0]; y=uv[1]; z=face.getStepZ()*.5; }
        }
        return new Vec3(host.getX()+.5+x, host.getY()+.5+y, host.getZ()+.5+z);
    }
    private static double[] project(Direction face, double x, double y, double z) {
        return switch (face.getAxis()) { case X -> new double[]{y,z}; case Y -> new double[]{x,z}; case Z -> new double[]{x,y}; };
    }
}
