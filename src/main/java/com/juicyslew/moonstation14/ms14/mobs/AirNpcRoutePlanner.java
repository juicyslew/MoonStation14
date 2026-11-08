package com.juicyslew.moonstation14.ms14.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Bounded, loaded-only vanilla 3D flight route search. Returns waypoints; never moves the mob. */
public final class AirNpcRoutePlanner {
    private static final int MAX_VISITED_NODES = 700;
    private static final int MAX_AXIS = 12;
    private static final int MAX_VERTICAL = 8;
    // PathFinder's maxRange also caps cumulative walked distance. This is a bounded local
    // detour allowance, separate from the 12-block XZ and 8-block Y spatial limits; it
    // does not guarantee a route through every maze or path.
    private static final int MAX_ROUTE_TRAVEL = 2 * MAX_AXIS + 2 * MAX_VERTICAL;
    private static final double NODE_STEP = 0.5;
    private static final double DESTINATION_TOLERANCE = 1.0e-6;

    public enum RouteStatus { ROUTE, NO_PATH, DEFERRED }
    public record AirRouteResult(RouteStatus status, List<GroundNpcRoutePlanner.Waypoint> waypoints,
                                 String detail) { }

    public AirRouteResult routeResult(Mob mob, Vec3 destination) {
        if (mob == null || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)
                || destination == null || !finite(destination))
            return result(RouteStatus.NO_PATH, "INVALID_INPUT");
        Vec3 origin = mob.position();
        if (!finite(origin)) return result(RouteStatus.NO_PATH, "INVALID_START");
        if (!insideAllowedVolume(origin, destination))
            return result(RouteStatus.NO_PATH, "OUT_OF_BOUNDS");

        AABB body = mob.getBoundingBox();
        AABB targetBody = body.move(destination.x - origin.x, destination.y - origin.y, destination.z - origin.z);
        if (!insideWorld(body, level) || !insideWorld(targetBody, level))
            return result(RouteStatus.NO_PATH, "OUT_OF_WORLD");
        AABB bounds = new AABB(Math.min(origin.x, destination.x) - MAX_AXIS,
                Math.min(origin.y, destination.y) - MAX_VERTICAL,
                Math.min(origin.z, destination.z) - MAX_AXIS,
                Math.max(origin.x, destination.x) + MAX_AXIS,
                Math.max(origin.y, destination.y) + MAX_VERTICAL,
                Math.max(origin.z, destination.z) + MAX_AXIS);
        bounds = new AABB(bounds.minX, Math.max(bounds.minY, level.getMinBuildHeight()), bounds.minZ,
                bounds.maxX, Math.min(bounds.maxY, level.getMaxBuildHeight() - 1.0e-4), bounds.maxZ);
        if (!GroundNpcRoutePlanner.loadedVolume(level, bounds))
            return result(RouteStatus.DEFERRED, "UNLOADED_OR_OUT_OF_WORLD_REGION");

        BlockPos goal = BlockPos.containing(destination);
        MinecraftServer server = level.getServer();
        if (!NpcRouteBudget.begin(server)) return result(RouteStatus.DEFERRED, "SERVER_TICK_BUDGET");
        int allowance = Math.min(MAX_VISITED_NODES, NpcRouteBudget.remainingExpansions());
        if (allowance <= 0) return result(RouteStatus.DEFERRED, "SERVER_TICK_BUDGET");
        BudgetedFlyNodeEvaluator evaluator = new BudgetedFlyNodeEvaluator(allowance, origin, goal);
        Path path;
        try {
            path = new PathFinder(evaluator, allowance).findPath(new PathNavigationRegion(level,
                        BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)),
                         mob, Set.of(goal), MAX_ROUTE_TRAVEL, 0, 1.0f);
        } finally {
            evaluator.doneOnce();
        }
        if (path == null || !path.canReach()) return result(RouteStatus.NO_PATH,
                evaluator.failureDetail(path, goal));
        List<GroundNpcRoutePlanner.Waypoint> waypoints = new ArrayList<>();
        Vec3 previous = origin;
        for (int i = 1; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            Vec3 point = new Vec3(node.x + .5, node.y, node.z + .5);
            if (!insideAllowedVolume(origin, point))
                return result(RouteStatus.NO_PATH, "PATH_OUT_OF_BOUNDS");
            if (!clearSegment(mob, level, previous, point)) return result(RouteStatus.NO_PATH, "BLOCKED_SEGMENT");
            waypoints.add(new GroundNpcRoutePlanner.Waypoint(point.x, point.y, point.z));
            previous = point;
        }
        if (!clearSegment(mob, level, previous, destination)) return result(RouteStatus.NO_PATH, "BLOCKED_GOAL_SEGMENT");
        if (previous.distanceTo(destination) > DESTINATION_TOLERANCE)
            waypoints.add(new GroundNpcRoutePlanner.Waypoint(destination.x, destination.y, destination.z));
        return new AirRouteResult(RouteStatus.ROUTE, List.copyOf(waypoints), "VANILLA_FLY_PATH");
    }

    private static boolean clearSegment(Mob mob, ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int samples = Math.max(1, (int) Math.ceil(delta.length() / NODE_STEP));
        AABB body = mob.getBoundingBox();
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 p = from.add(delta.scale(t));
            AABB at = body.move(p.x - mob.getX(), p.y - mob.getY(), p.z - mob.getZ());
            if (at.minY < level.getMinBuildHeight() || at.maxY >= level.getMaxBuildHeight()
                    || !GroundNpcRoutePlanner.loadedVolume(level, at) || !level.noCollision(mob, at)) return false;
            BlockPos feet = BlockPos.containing(p);
            if (!level.getFluidState(feet).isEmpty() || level.getBlockState(feet).getBlock() instanceof net.minecraft.world.level.block.MagmaBlock)
                return false;
        }
        return true;
    }

    private static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
    private static boolean insideAllowedVolume(Vec3 origin, Vec3 point) {
        return Math.abs(point.x - origin.x) <= MAX_AXIS && Math.abs(point.z - origin.z) <= MAX_AXIS
                && Math.abs(point.y - origin.y) <= MAX_VERTICAL;
    }
    private static boolean insideWorld(AABB box, ServerLevel level) {
        return box.minY >= level.getMinBuildHeight() && box.maxY < level.getMaxBuildHeight();
    }
    private static final class BudgetedFlyNodeEvaluator extends FlyNodeEvaluator {
        private final int allowance;
        private final Vec3 origin;
        private final BlockPos goal;
        private int expanded;
        private int minExpandedY = Integer.MAX_VALUE;
        private int maxExpandedY = Integer.MIN_VALUE;
        private boolean cleaned;
        private String startFact = "start=unavailable";
        private String probeFacts = "";
        private BudgetedFlyNodeEvaluator(int allowance, Vec3 origin, BlockPos goal) {
            this.allowance = allowance;
            this.origin = origin;
            this.goal = goal.immutable();
        }
        @Override public Node getStart() {
            Node start = super.getStart();
            startFact = start == null ? "start=null" : "start=" + start.x + "," + start.y + "," + start.z
                    + "/" + start.type;
            if (mob != null && start != null) {
                StringBuilder facts = new StringBuilder(" size=").append(mob.getBbWidth()).append('x').append(mob.getBbHeight());
                int sx = start.x, sy = start.y, sz = start.z;
                for (int dy = 1; dy <= 4; dy++) {
                    var type = getCachedPathType(sx, sy + dy, sz);
                    facts.append(" y+").append(dy).append('=').append(type).append('/').append(mob.getPathfindingMalus(type));
                }
                int dx = Integer.compare(goal.getX(), sx), dz = Integer.compare(goal.getZ(), sz);
                var crossing = getCachedPathType(sx + dx * 2, sy + 3, sz + dz * 2);
                facts.append(" crossing=").append(crossing).append('/').append(mob.getPathfindingMalus(crossing));
                probeFacts = facts.toString();
            }
            return start;
        }
        private String failureDetail(Path path, BlockPos goal) {
            String pathFact = path == null ? "path=null" : "path=partial,reachable=" + path.canReach()
                    + ",end=" + path.getEndNode().x + "," + path.getEndNode().y + "," + path.getEndNode().z
                    + ",nodes=" + path.getNodeCount();
            String detail = "NO_REACHABLE_PATH " + pathFact + " " + startFact + " expanded=" + expanded + "/" + allowance
                    + " y=" + minExpandedY + ".." + maxExpandedY + probeFacts + " goal=" + goal.getX() + "," + goal.getY() + "," + goal.getZ();
            return detail.substring(0, Math.min(600, detail.length()));
        }
        @Override public int getNeighbors(Node[] neighbors, Node node) {
            if (expanded >= allowance || NpcRouteBudget.remainingExpansions() <= 0) return 0;
            expanded++;
            minExpandedY = Math.min(minExpandedY, node.y);
            maxExpandedY = Math.max(maxExpandedY, node.y);
            NpcRouteBudget.expanded(1);
            int count = super.getNeighbors(neighbors, node);
            int accepted = 0;
            for (int i = 0; i < count; i++) {
                Node candidate = neighbors[i];
                if (insideAllowedVolume(origin, new Vec3(candidate.x + .5, candidate.y, candidate.z + .5)))
                    neighbors[accepted++] = candidate;
            }
            return accepted;
        }
        private void doneOnce() { if (!cleaned) { cleaned = true; super.done(); } }
        @Override public void done() { doneOnce(); }
    }
    private static AirRouteResult result(RouteStatus status, String detail) {
        return new AirRouteResult(status, List.of(), detail);
    }
}
