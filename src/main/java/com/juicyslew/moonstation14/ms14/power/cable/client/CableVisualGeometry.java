package com.juicyslew.moonstation14.ms14.power.cable.client;

import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import net.minecraft.core.Direction;

import java.util.List;

/** Pure face-local geometry for topology-backed surface wire spokes. */
public final class CableVisualGeometry {
    private CableVisualGeometry() { }

    /** Returns the four corners of a square cap centered on the node, in its face plane. */
    public static List<Vector3> centerCorners(Direction face, float halfWidth) {
        if (halfWidth < 0) throw new IllegalArgumentException("halfWidth must be non-negative");
        return switch (face.getAxis()) {
            case X -> List.of(new Vector3(0, -halfWidth, -halfWidth), new Vector3(0, halfWidth, -halfWidth),
                    new Vector3(0, halfWidth, halfWidth), new Vector3(0, -halfWidth, halfWidth));
            case Y -> List.of(new Vector3(-halfWidth, 0, -halfWidth), new Vector3(halfWidth, 0, -halfWidth),
                    new Vector3(halfWidth, 0, halfWidth), new Vector3(-halfWidth, 0, halfWidth));
            case Z -> List.of(new Vector3(-halfWidth, -halfWidth, 0), new Vector3(halfWidth, -halfWidth, 0),
                    new Vector3(halfWidth, halfWidth, 0), new Vector3(-halfWidth, halfWidth, 0));
        };
    }

    /** Tier-separated cap corners, expressed relative to the host center. */
    public static List<Vector3> centerCorners(CableFaceNode node, float halfWidth) {
        Vector3 offset = laneOffset(node.face(), lane(node.tier()));
        return centerCorners(node.face(), halfWidth).stream()
                .map(corner -> add(corner, offset)).toList();
    }

    /** Exact quad corners for a continuous spoke, relative to this node's host center. */
    public static List<Vector3> spokeCorners(CableFaceNode node, CableFaceNode neighbor, float halfWidth) {
        if (halfWidth < 0) throw new IllegalArgumentException("halfWidth must be non-negative");
        var edge = PowerTopology.cableEdge(node, neighbor)
                .orElseThrow(() -> new IllegalArgumentException("not a cable edge"));
        Direction face = node.face();
        Vector3 start = laneOffset(face, lane(node.tier()));
        Vector3 end;
        if (edge.kind() == PowerTopology.EdgeKind.COPLANAR) {
            int dx = neighbor.host().getX() - node.host().getX();
            int dy = neighbor.host().getY() - node.host().getY();
            int dz = neighbor.host().getZ() - node.host().getZ();
            // Clamp the movement axis to the shared block boundary; the reciprocal
            // spoke therefore ends at precisely the same seam.
            end = new Vector3(dx == 0 ? start.x() : dx * .5F,
                    dy == 0 ? start.y() : dy * .5F,
                    dz == 0 ? start.z() : dz * .5F);
        } else {
            Direction other = neighbor.face();
            Vector3 n = new Vector3(face.getStepX(), face.getStepY(), face.getStepZ());
            Vector3 o = new Vector3(other.getStepX(), other.getStepY(), other.getStepZ());
            Vector3 cross = cross(n, o);
            // Choose a world-canonical direction along the cube edge. The
            // ordered face pair reverses its cross product at the reciprocal
            // endpoint, but tier lanes must not reverse with it.
            Vector3 sharedAxis = new Vector3(Math.abs(cross.x()), Math.abs(cross.y()), Math.abs(cross.z()));
            // Both face projections terminate on the same 3D cube edge. The lane
            // displacement is along that edge, so it remains consistent after a turn.
            end = add(scale(o, -.5F), scale(sharedAxis, lane(node.tier())));
        }
        Vector3 direction = subtract(end, start);
        Vector3 side = cross(new Vector3(face.getStepX(), face.getStepY(), face.getStepZ()), direction);
        float sideLength = length(side);
        if (sideLength == 0) return List.of();
        side = scale(side, halfWidth / sideLength);
        Vector3 startMinus = subtract(start, side), endMinus = subtract(end, side);
        Vector3 endPlus = add(end, side), startPlus = add(start, side);
        if (edge.kind() == PowerTopology.EdgeKind.COPLANAR) {
            int axis = neighbor.host().getX() != node.host().getX() ? 0
                    : neighbor.host().getY() != node.host().getY() ? 1 : 2;
            float boundary = axis == 0 ? (neighbor.host().getX() > node.host().getX() ? .5F : -.5F)
                    : axis == 1 ? (neighbor.host().getY() > node.host().getY() ? .5F : -.5F)
                    : (neighbor.host().getZ() > node.host().getZ() ? .5F : -.5F);
            endMinus = clampAxis(endMinus, axis, boundary);
            endPlus = clampAxis(endPlus, axis, boundary);
        } else {
            Direction other = neighbor.face();
            int axis = other.getAxis().ordinal();
            float boundary = switch (other.getAxis()) {
                case X -> other.getStepX() > 0 ? -.5F : .5F;
                case Y -> other.getStepY() > 0 ? -.5F : .5F;
                case Z -> other.getStepZ() > 0 ? -.5F : .5F;
            };
            endMinus = clampAxis(endMinus, axis, boundary);
            endPlus = clampAxis(endPlus, axis, boundary);
        }
        return List.of(startMinus, endMinus, endPlus, startPlus);
    }

    private static Vector3 clampAxis(Vector3 point, int axis, float value) {
        return switch (axis) {
            case 0 -> new Vector3(value, point.y, point.z);
            case 1 -> new Vector3(point.x, value, point.z);
            case 2 -> new Vector3(point.x, point.y, value);
            default -> throw new IllegalArgumentException();
        };
    }

    /** Stable tier lanes shared by every face orientation. */
    public static float lane(CableTier tier) {
        return switch (tier) { case HV -> -.16F; case MV -> 0F; case APC -> .16F; };
    }

    private static Vector3 laneOffset(Direction face, float lane) {
        return switch (face.getAxis()) {
            case X -> new Vector3(0, lane, lane);
            case Y -> new Vector3(lane, 0, lane);
            case Z -> new Vector3(lane, lane, 0);
        };
    }

    private static Vector3 add(Vector3 a, Vector3 b) { return new Vector3(a.x + b.x, a.y + b.y, a.z + b.z); }
    private static Vector3 subtract(Vector3 a, Vector3 b) { return new Vector3(a.x - b.x, a.y - b.y, a.z - b.z); }
    private static Vector3 scale(Vector3 a, float scale) { return new Vector3(a.x * scale, a.y * scale, a.z * scale); }
    private static Vector3 cross(Vector3 a, Vector3 b) {
        return new Vector3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }
    private static float length(Vector3 a) { return (float) Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z); }

    /** Returns a spoke endpoint relative to the host center, on the actual shared surface boundary. */
    public static Vector3 endpoint(CableFaceNode node, CableFaceNode neighbor) {
        var edge = PowerTopology.cableEdge(node, neighbor).orElseThrow(() -> new IllegalArgumentException("not a cable edge"));
        Direction face = node.face();
        if (edge.kind() == PowerTopology.EdgeKind.COPLANAR) {
            int dx = neighbor.host().getX() - node.host().getX();
            int dy = neighbor.host().getY() - node.host().getY();
            int dz = neighbor.host().getZ() - node.host().getZ();
            return new Vector3(dx * .5F, dy * .5F, dz * .5F);
        }
        Direction other = neighbor.face();
        // The face-normal component is supplied by the renderer's fixed surface offset;
        // only the tangent displacement belongs in this face-local spoke.
        return new Vector3(-other.getStepX() * .5F, -other.getStepY() * .5F, -other.getStepZ() * .5F);
    }

    public record Vector3(float x, float y, float z) { }
}
