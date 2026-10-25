package com.juicyslew.moonstation14.ms14.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;

/** Bounded loaded-only surface A*. It produces waypoints only; the shared grounded motor executes them. */
public final class GroundNpcRoutePlanner {
    private static final int MAX_VISITED = 700;
    /** Upstream idle selection counts popped nodes, not route length. */
    private static final int MAX_IDLE_EXPANSIONS = 40;
    private static final int MAX_HORIZONTAL = 12;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static long totalCalls;
    private static long totalExpansions;
    private static long totalDeferrals;
    public enum RouteStatus { ROUTE, NO_PATH, DEFERRED }
    public record RouteResult(RouteStatus status, List<Waypoint> waypoints, Waypoint startSupport,
                              Waypoint goalSupport, int rejectedEdges, String detail) {
        public RouteResult(RouteStatus status, List<Waypoint> waypoints) {
            this(status, waypoints, null, null, 0, null);
        }
    }
    record DiagnosticSnapshot(long calls, long expandedNodes, long deferredCalls) { }
    record TickBudgetSnapshot(int routeCalls, int expansions) { }
    public record Waypoint(double x, double y, double z) {
        public Vec3 position() { return new Vec3(x, y, z); }
    }
    private record Node(int x, int z, double y) {
        Waypoint waypoint() { return new Waypoint(x + .5, y, z + .5); }
    }
    private record Open(Node node, double score) { }
    private record TraversableNeighbor(Node node, double stepCost) { }
    private record NeighborBatch(List<TraversableNeighbor> neighbors, int rejectedEdges) { }
    private record SurfaceKey(int x, int z, long nearYBits) { }
    private static final class RouteAttempt {
        final NpcRouteWorkDiagnostic.Measurement measurement;
        final boolean cacheEnabled;
        final Map<SurfaceKey, List<Node>> surfaces = new HashMap<>();
        long surfaceQueries, surfaceCacheHits, surfaceComputations, standableCalls, noCollisionProbes;
        RouteAttempt(NpcRouteWorkDiagnostic.Measurement measurement, boolean cacheEnabled) {
            this.measurement = measurement; this.cacheEnabled = cacheEnabled;
        }
    }
    record RouteWorkStats(long surfaceQueries, long surfaceCacheHits, long surfaceComputations,
                          long standableCalls, long noCollisionProbes) { }
    record MeasuredRouteResult(RouteResult result, RouteWorkStats work) { }

    public List<Waypoint> route(Mob mob, Vec3 target) {
        return routeResult(mob, target).waypoints();
    }

    RouteResult routeResult(Mob mob, Vec3 target) {
        var measurement = mob == null ? null : NpcRouteWorkDiagnostic.begin(mob.getType());
        String outcome = "ERROR";
        try {
            RouteResult result = routeResultMeasured(mob, target, new RouteAttempt(measurement, true));
            outcome = result.status().name();
            return result;
        } finally {
            if (measurement != null) measurement.finish(outcome);
        }
    }

    /** Package test seam: exercises the production planner with a per-call cache choice and counters. */
    MeasuredRouteResult routeResultForTest(Mob mob, Vec3 target, boolean cacheEnabled) {
        RouteAttempt attempt = new RouteAttempt(NpcRouteWorkDiagnostic.testMeasurement(), cacheEnabled);
        RouteResult result = routeResultMeasured(mob, target, attempt);
        return new MeasuredRouteResult(result, new RouteWorkStats(attempt.surfaceQueries, attempt.surfaceCacheHits,
                attempt.surfaceComputations, attempt.measurement.standableCalls(), attempt.measurement.noCollisionProbes()));
    }

    private RouteResult routeResultMeasured(Mob mob, Vec3 target, RouteAttempt attempt) {
        NpcRouteWorkDiagnostic.Measurement measurement = attempt.measurement;
        totalCalls++;
        if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved() || target == null)
            return new RouteResult(RouteStatus.NO_PATH, List.of());
        MinecraftServer server = level.getServer();
        if (!NpcRouteBudget.begin(server)) {
            totalDeferrals++;
            return new RouteResult(RouteStatus.DEFERRED, List.of());
        }
        Node start = nearestSurface(mob, level, mob.position(), Math.sqrt(.5) + 1e-6, attempt);
        Node goal = nearestSurface(mob, level, target, 1.45, attempt);
        String invalidProjection = start == null || goal == null ? null : startProjectionFailure(mob, level, start, measurement);
        if (start == null || goal == null || invalidProjection != null)
            return new RouteResult(RouteStatus.NO_PATH, List.of(), start == null ? null : start.waypoint(),
                    goal == null ? null : goal.waypoint(), 0, start == null ? "NO_START_SUPPORT"
                    : goal == null ? "NO_GOAL_SUPPORT" : invalidProjection);
        if (Math.abs(start.y - goal.y) < .05 && directWalkClear(mob, level,
                start.waypoint().position(), goal.waypoint().position(), measurement)) {
            return new RouteResult(RouteStatus.ROUTE, start.equals(goal) ? List.of() : List.of(goal.waypoint()),
                    start.waypoint(), goal.waypoint(), 0, "DIRECT_CLEAR");
        }
        PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingDouble(Open::score));
        Map<Node, Double> cost = new HashMap<>();
        Map<Node, Node> parent = new HashMap<>();
        Set<Node> closed = new HashSet<>();
        cost.put(start, 0d);
        open.add(new Open(start, heuristic(start, goal)));
        int localExpansions = 0;
        int rejectedEdges = 0;
        while (!open.isEmpty() && localExpansions < MAX_VISITED
                && NpcRouteBudget.remainingExpansions() > 0) {
            Node current = open.remove().node();
            if (!closed.add(current)) continue;
            localExpansions++;
            NpcRouteBudget.expanded(1);
            totalExpansions++;
            if (measurement != null) measurement.expandedNode();
            if (current.equals(goal)) return new RouteResult(RouteStatus.ROUTE,
                    reconstruct(start, current, parent), start.waypoint(), goal.waypoint(), rejectedEdges, "A_STAR");
            NeighborBatch batch = traversableNeighbors(mob, level, current, start, MAX_HORIZONTAL, closed, attempt);
            rejectedEdges += batch.rejectedEdges();
            for (TraversableNeighbor neighbor : batch.neighbors()) {
                    Node next = neighbor.node();
                    double nextCost = cost.get(current) + neighbor.stepCost();
                    if (nextCost >= cost.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                    cost.put(next, nextCost);
                    parent.put(next, current);
                    open.add(new Open(next, nextCost + heuristic(next, goal)));
            }
        }
        if (!open.isEmpty() && NpcRouteBudget.remainingExpansions() == 0) {
            totalDeferrals++;
            return new RouteResult(RouteStatus.DEFERRED, List.of(), start.waypoint(), goal.waypoint(),
                    rejectedEdges, "SERVER_TICK_BUDGET");
        }
        return new RouteResult(RouteStatus.NO_PATH, List.of(), start.waypoint(), goal.waypoint(), rejectedEdges,
                "SEARCH_EXHAUSTED");
    }

    static DiagnosticSnapshot diagnostics() {
        return new DiagnosticSnapshot(totalCalls, totalExpansions, totalDeferrals);
    }

    static TickBudgetSnapshot tickBudget(ServerLevel level) {
        if (level == null) return new TickBudgetSnapshot(0, 0);
        return new TickBudgetSnapshot(NpcRouteBudget.calls(level.getServer()), NpcRouteBudget.expansions(level.getServer()));
    }


    /** The graph is cell-centered; validate the short real-body-to-node connector before seeding A*. */
    private static String startProjectionFailure(Mob mob, ServerLevel level, Node start, NpcRouteWorkDiagnostic.Measurement measurement) {
        Vec3 from = mob.position();
        if (!Double.isFinite(from.x) || !Double.isFinite(from.y) || !Double.isFinite(from.z)) return "INVALID_START_POSITION";
        double dx = start.x + .5 - from.x, dz = start.z + .5 - from.z;
        AABB body = mob.getBoundingBox();
        int samples = 4;
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            AABB projected = body.move(dx * t, .001, dz * t);
            if (!loaded(level, projected)) return "UNLOADED_START_PROJECTION_SAMPLE_" + i + "_" + projected;
            if (measurement != null) measurement.noCollisionProbe();
            if (!level.noCollision(mob, projected)) return "COLLIDING_START_PROJECTION_SAMPLE_" + i + "_" + projected
                    + "_blocks=" + collidingBlocks(mob, level, projected)
                    + "_entities=" + level.getEntities(mob, projected).stream().map(entity -> entity.getType().toString()).toList();
        }
        return null;
    }

    private static List<String> collidingBlocks(Mob mob, ServerLevel level, AABB box) {
        List<String> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var shape = physicalShape(mob, level, pos, CollisionContext.of(mob));
            for (AABB part : shape.toAabbs()) {
                AABB world = part.move(pos.getX(), pos.getY(), pos.getZ());
                if (world.intersects(box)) result.add(pos + "=" + level.getBlockState(pos) + ":" + world);
            }
        }
        return List.copyOf(result);
    }

    /** True only when this actor has a loaded, collision-valid support surface at the supplied feet position. */
    public boolean hasSupportedSurface(Mob mob, Vec3 feetPosition) {
        if (!(mob.level() instanceof ServerLevel level) || feetPosition == null) return false;
        int x = BlockPos.containing(feetPosition.x, feetPosition.y, feetPosition.z).getX();
        int z = BlockPos.containing(feetPosition.x, feetPosition.y, feetPosition.z).getZ();
        return surfaces(mob, level, x, z, feetPosition.y, null).stream()
                .anyMatch(node -> Math.abs(node.y - feetPosition.y) < .01);
    }

    /** Resolve an arbitrary idle target to the center of its nearest valid graph node. */
    public Vec3 nearestSupportedPoint(Mob mob, Vec3 point, double radius) {
        if (!(mob.level() instanceof ServerLevel level) || point == null || !Double.isFinite(radius) || radius < 0)
            return null;
        Node node = nearestSurface(mob, level, point, radius, null);
        return node == null ? null : node.waypoint().position();
    }

    /** Select a uniformly random reachable idle destination while retaining its already-built route. */
    RouteResult randomReachableIdleRoute(Mob mob, double range) {
        return randomReachableIdleRoute(mob, range, mob.position());
    }

    /** Flying idle legs project the actor's horizontal position onto nearby ground support. */
    RouteResult randomReachableIdleRoute(Mob mob, double range, Vec3 startAnchor) {
        return randomReachableIdleRoute(mob, range, startAnchor, 0);
    }

    RouteResult randomReachableIdleRoute(Mob mob, double range, Vec3 startAnchor, double maximumStartDrop) {
        if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved() || !Double.isFinite(range) || range < 0)
            return new RouteResult(RouteStatus.NO_PATH, List.of());
        if (startAnchor == null || !Double.isFinite(startAnchor.y))
            return new RouteResult(RouteStatus.NO_PATH, List.of());
        MinecraftServer server = level.getServer();
        if (!NpcRouteBudget.begin(server)) {
            totalDeferrals++;
            return new RouteResult(RouteStatus.DEFERRED, List.of());
        }
        totalCalls++;
        var measurement = NpcRouteWorkDiagnostic.begin(mob.getType());
        String outcome = "ERROR";
        try {
            RouteAttempt attempt = new RouteAttempt(measurement, true);
            Node start = maximumStartDrop > 0
                    ? nearestSurfaceBelow(mob, level, startAnchor, Math.sqrt(.5) + 1e-6, maximumStartDrop, attempt)
                    : nearestSurface(mob, level, startAnchor, Math.sqrt(.5) + 1e-6, attempt);
            if (start == null) { outcome = "NO_PATH"; return new RouteResult(RouteStatus.NO_PATH, List.of()); }
            String projectionFailure = startProjectionFailure(mob, level, start, measurement);
            if (projectionFailure != null) { outcome = "NO_PATH"; return new RouteResult(RouteStatus.NO_PATH, List.of(), start.waypoint(), null, 0, projectionFailure); }
            Map<Node, Node> parent = new HashMap<>();
            Map<Node, Double> costs = new HashMap<>();
            PriorityQueue<Open> queue = new PriorityQueue<>(Comparator.comparingDouble(Open::score));
            Set<Node> closed = new HashSet<>();
            costs.put(start, 0d); queue.add(new Open(start, 0));
            int popped = 0, rejected = 0;
            while (!queue.isEmpty() && popped < MAX_IDLE_EXPANSIONS
                    && NpcRouteBudget.remainingExpansions() > 0) {
                Node current = queue.remove().node();
                if (!closed.add(current)) continue;
                popped++; NpcRouteBudget.expanded(1); totalExpansions++;
                if (measurement != null) measurement.expandedNode();
                NeighborBatch batch = traversableNeighbors(mob, level, current, start,
                        Math.min(MAX_HORIZONTAL, range), closed, attempt);
                rejected += batch.rejectedEdges();
                for (TraversableNeighbor neighbor : batch.neighbors()) {
                    Node next = neighbor.node();
                    double cost = costs.get(current) + neighbor.stepCost();
                    if (cost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                    costs.put(next,cost); parent.put(next,current); queue.add(new Open(next,cost));
                }
            }
            if (NpcRouteBudget.remainingExpansions() == 0 && !queue.isEmpty()) { totalDeferrals++; outcome="DEFERRED"; return new RouteResult(RouteStatus.DEFERRED,List.of(),start.waypoint(),null,rejected,"SERVER_TICK_BUDGET"); }
            List<Node> reached = costs.keySet().stream().filter(node -> Math.sqrt(
                    (node.x-start.x)*(double)(node.x-start.x)+(node.z-start.z)*(double)(node.z-start.z)
                            +(node.y-start.y)*(node.y-start.y)) <= range).toList();
            Node goal = reached.get(mob.getRandom().nextInt(reached.size()));
            outcome="ROUTE";
            List<Waypoint> route = reconstruct(start, goal, parent);
            // A selected start node still needs a motor waypoint when the actor was projected
            // from an off-center position; otherwise an empty route can never satisfy arrival.
            if (route.isEmpty() && mob.position().distanceTo(start.waypoint().position()) > .6)
                route = List.of(start.waypoint());
            return new RouteResult(RouteStatus.ROUTE,route,start.waypoint(),goal.waypoint(),rejected,"IDLE_REACHABLE_BFS_"+popped);
        } finally { if (measurement != null) measurement.finish(outcome); }
    }

    /** Bounded vertical support projection sharing the route attempt's loaded/collision surface checks. */
    private static Node nearestSurfaceBelow(Mob mob, ServerLevel level, Vec3 point, double radius,
                                             double maximumDrop, RouteAttempt attempt) {
        if (!Double.isFinite(maximumDrop) || maximumDrop <= 0) return null;
        int samples = (int) Math.ceil(maximumDrop);
        Node best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= samples; step++) {
            double nearY = point.y - Math.min(maximumDrop, step);
            Node candidate = nearestSurface(mob, level, new Vec3(point.x, nearY, point.z), radius, attempt);
            if (candidate == null) continue;
            double distance = Math.abs(candidate.y - point.y);
            if (candidate.y <= point.y + .05 && distance <= maximumDrop + 1e-6 && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Loaded-only projection to the nearest collision-supported footprint beneath an actor. */
    Waypoint projectGroundBelow(Mob mob, double maximumDrop) {
        if (mob == null || !(mob.level() instanceof ServerLevel level) || mob.isRemoved()) return null;
        Node node = nearestSurfaceBelow(mob, level, mob.position(), .71, maximumDrop,
                new RouteAttempt(null, true));
        return node == null ? null : node.waypoint();
    }

    /** Loaded-only vertical collision clip for a flight target after support projection. */
    double clipVerticalTarget(Mob mob, double targetY) {
        if (mob == null || !(mob.level() instanceof ServerLevel level) || !Double.isFinite(targetY)) return Double.NaN;
        AABB body = mob.getBoundingBox();
        double delta = targetY - mob.getY();
        AABB swept = body.expandTowards(0, delta, 0);
        if (!loaded(level, swept)) return Double.NaN;
        double clipped = net.minecraft.world.phys.shapes.Shapes.collide(Direction.Axis.Y, body,
                level.getCollisions(mob, swept), delta);
        return mob.getY() + clipped;
    }

    /** Produces only geometrically traversable edges; search-specific scoring stays with each caller. */
    private static NeighborBatch traversableNeighbors(Mob mob, ServerLevel level, Node current, Node start,
                                                       double bound, Set<Node> closed, RouteAttempt attempt) {
        List<TraversableNeighbor> result = new ArrayList<>();
        int rejected = 0;
        NpcRouteWorkDiagnostic.Measurement measurement = attempt.measurement;
        for (int[] step : NEIGHBORS) {
            int x = current.x + step[0], z = current.z + step[1];
            if (Math.abs(x - start.x) > bound || Math.abs(z - start.z) > bound) continue;
            boolean diagonal = step[0] != 0 && step[1] != 0;
            if (diagonal && (!cardinalConnectorClear(mob, level, current, x, current.z, attempt)
                    || !cardinalConnectorClear(mob, level, current, current.x, z, attempt))) continue;
            for (Node next : surfaces(mob, level, x, z, current.y, attempt)) {
                if (closed.contains(next)) continue;
                double rise = next.y - current.y;
                if (rise > 1.0001 || rise < -1.0001 || !edgeClear(mob, level, current, next, measurement)) {
                    rejected++;
                    continue;
                }
                double stepCost = (diagonal ? Math.sqrt(2) : 1)
                        + Math.max(0, rise) * .4 + Math.max(0, -rise) * .6;
                result.add(new TraversableNeighbor(next, stepCost));
            }
        }
        return new NeighborBatch(result, rejected);
    }

    private static Node nearestSurface(Mob mob, ServerLevel level, Vec3 point, double radius, RouteAttempt attempt) {
        NpcRouteWorkDiagnostic.Measurement measurement = attempt == null ? null : attempt.measurement;
        Node best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int cx = BlockPos.containing(point.x, point.y, point.z).getX();
        int cz = BlockPos.containing(point.x, point.y, point.z).getZ();
        int reach = Math.max(1, (int) Math.ceil(radius));
        for (int x = cx - reach; x <= cx + reach; x++) for (int z = cz - reach; z <= cz + reach; z++)
            for (Node candidate : surfaces(mob, level, x, z, point.y, attempt)) {
                Waypoint wp = candidate.waypoint();
                double dx = wp.x - point.x, dz = wp.z - point.z, dy = wp.y - point.y;
                double distance = dx * dx + dz * dz + dy * dy;
                if (distance <= radius * radius && distance < bestDistance) { best = candidate; bestDistance = distance; }
            }
        return best;
    }

    private static List<Node> surfaces(Mob mob, ServerLevel level, int x, int z, double nearY, RouteAttempt attempt) {
        NpcRouteWorkDiagnostic.Measurement measurement = attempt == null ? null : attempt.measurement;
        if (attempt != null) attempt.surfaceQueries++;
        if (measurement != null) measurement.surfaceQuery();
        SurfaceKey key = attempt == null || !attempt.cacheEnabled ? null : new SurfaceKey(x, z, Double.doubleToLongBits(nearY));
        if (attempt != null && attempt.cacheEnabled) {
            List<Node> cached = attempt.surfaces.get(key);
            if (cached != null) { attempt.surfaceCacheHits++; if (measurement != null) measurement.surfaceCacheHit(); return cached; }
        }
        if (attempt != null) attempt.surfaceComputations++;
        if (measurement != null) measurement.surfaceComputation();
        List<Node> result = new ArrayList<>(4);
        int base = BlockPos.containing(x + .5, nearY, z + .5).getY();
        for (int by = base - 2; by <= base + 1; by++) {
            BlockPos support = new BlockPos(x, by, z);
            if (!loaded(level, support)) continue;
            var state = level.getBlockState(support);
            if (!level.getFluidState(support).isEmpty()) continue;
            var shape = physicalShape(mob, level, support, CollisionContext.of(mob));
            for (AABB part : shape.toAabbs()) {
                if (part.maxY <= 0 || part.maxY > 1 || part.maxY < part.minY) continue;
                double y = by + part.maxY;
                if (Math.abs(y - nearY) > 1.0001 && y > nearY + 1.0001) continue;
                Node node = new Node(x, z, y);
                if (standable(mob, level, node, measurement)) result.add(node);
            }
        }
        List<Node> immutable = List.copyOf(result);
        if (attempt != null && attempt.cacheEnabled) attempt.surfaces.put(key, immutable);
        return immutable;
    }

    private static boolean standable(Mob mob, ServerLevel level, Node node, NpcRouteWorkDiagnostic.Measurement measurement) {
        if (measurement != null) measurement.standableCall();
        AABB box = mob.getBoundingBox().move(node.x + .5 - mob.getX(), node.y + .001 - mob.getY(), node.z + .5 - mob.getZ());
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight() || !loaded(level, box)) return false;
        BlockPos feet = BlockPos.containing(node.x + .5, node.y + 1e-5, node.z + .5);
        if (!level.getFluidState(feet).isEmpty()) return false;
        if (measurement != null) measurement.noCollisionProbe();
        return level.noCollision(mob, box) && supportedFootprint(mob, level, node, box);
    }

    private static boolean supportedFootprint(Mob mob, ServerLevel level, Node node, AABB box) {
        BlockPos min = BlockPos.containing(box.minX + 1e-4, node.y - .08, box.minZ + 1e-4);
        BlockPos max = BlockPos.containing(box.maxX - 1e-4, node.y + .01, box.maxZ - 1e-4);
        List<AABB> tops = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!loaded(level, pos) || !level.getFluidState(pos).isEmpty()) return false;
            var shape = physicalShape(mob, level, pos, CollisionContext.of(mob));
            for (AABB part : shape.toAabbs()) {
                AABB world = part.move(pos.getX(), pos.getY(), pos.getZ());
                if (world.maxY >= node.y - .08 && world.maxY <= node.y + .01) tops.add(world);
            }
        }
        if (tops.isEmpty()) return false;
        // Do not accept a single supporting sliver under the actor's center: that turns a narrow
        // ledge over a deep pit into a false graph node. Sample the full collision footprint at a
        // resolution finer than supported voxel-shape edges (stair/slab boxes remain valid).
        for (int ix = 0; ix < 8; ix++) for (int iz = 0; iz < 8; iz++) {
            double x = box.minX + (box.maxX - box.minX) * (ix + .5) / 8;
            double z = box.minZ + (box.maxZ - box.minZ) * (iz + .5) / 8;
            boolean covered = false;
            for (AABB top : tops) if (x >= top.minX && x <= top.maxX && z >= top.minZ && z <= top.maxZ) {
                covered = true;
                break;
            }
            if (!covered) return false;
        }
        return true;
    }

    private static net.minecraft.world.phys.shapes.VoxelShape physicalShape(Mob mob, ServerLevel level,
            BlockPos pos, CollisionContext context) {
        var state = level.getBlockState(pos);
        return com.juicyslew.moonstation14.ms14.doors.AirlockPassagePolicy
                .physicalShape(mob, state, level, pos, context);
    }

    private static boolean edgeClear(Mob mob, ServerLevel level, Node from, Node to, NpcRouteWorkDiagnostic.Measurement measurement) {
        if (measurement != null) measurement.edgeClearCall();
        double rise = to.y - from.y;
        Vec3 start = new Vec3(from.x + .5, from.y, from.z + .5);
        Vec3 end = new Vec3(to.x + .5, to.y, to.z + .5);
        AABB base = mob.getBoundingBox().move(start.x - mob.getX(), start.y - mob.getY(), start.z - mob.getZ());
        int samples = 4;
        if (rise < -.05) {
            // Clear the ledge at the current elevation before descending at the supported
            // destination. Interpolating the drop while still overlapping the ledge rejects
            // physically valid one-block drops (the swept body clips the ledge's side).
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB across = base.move((end.x - start.x) * t, .001, (end.z - start.z) * t);
                if (!loaded(level, across)) return false;
                if (measurement != null) measurement.noCollisionProbe();
                if (!level.noCollision(mob, across)) return false;
            }
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB down = base.move(end.x - start.x, rise * t + .001, end.z - start.z);
                if (!loaded(level, down)) return false;
                if (measurement != null) measurement.noCollisionProbe();
                if (!level.noCollision(mob, down)) return false;
            }
            return true;
        }
        if (rise > .05) {
            // A jump can be launched while the body is pressed against the step face: the
            // shared motor advances vertical motion even when block collision clips horizontal
            // motion. Validate that launch, the raised horizontal footprint, and the landing
            // separately instead of inventing a parabolic path that clips the riser's side.
            double lift = rise + .05;
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB launch = base.move(0, lift * t, 0);
                if (!loaded(level, launch)) return false;
                if (measurement != null) measurement.noCollisionProbe();
                if (!level.noCollision(mob, launch)) return false;
            }
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB across = base.move((end.x - start.x) * t, lift, (end.z - start.z) * t);
                if (!loaded(level, across)) return false;
                if (measurement != null) measurement.noCollisionProbe();
                if (!level.noCollision(mob, across)) return false;
            }
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB landing = base.move(end.x - start.x, lift - (lift - rise) * t, end.z - start.z);
                if (!loaded(level, landing)) return false;
                if (measurement != null) measurement.noCollisionProbe();
                if (!level.noCollision(mob, landing)) return false;
            }
            return true;
        }
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            double vertical = .001;
            AABB swept = base.move((end.x - start.x) * t, vertical,
                    (end.z - start.z) * t);
            if (!loaded(level, swept)) return false;
            if (measurement != null) measurement.noCollisionProbe();
            if (!level.noCollision(mob, swept)) return false;
        }
        return true;
    }

    private static boolean cardinalConnectorClear(Mob mob, ServerLevel level, Node from, int x, int z, RouteAttempt attempt) {
        NpcRouteWorkDiagnostic.Measurement measurement = attempt.measurement;
        for (Node adjacent : surfaces(mob, level, x, z, from.y, attempt)) {
            if (Math.abs(adjacent.y - from.y) <= 1.0001 && edgeClear(mob, level, from, adjacent, measurement)) return true;
        }
        return false;
    }

    /** Straight grounded passage check used by open-area fast paths and never by interaction LOS. */
    private static boolean directWalkClear(Mob mob, ServerLevel level, Vec3 from, Vec3 to, NpcRouteWorkDiagnostic.Measurement measurement) {
        if (Math.abs(from.y - to.y) > .05) return false;
        double dx = to.x - from.x, dz = to.z - from.z;
        double distance = Math.hypot(dx, dz);
        if (distance > MAX_HORIZONTAL * Math.sqrt(2)) return false;
        int samples = Math.max(1, (int) Math.ceil(distance / .18));
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 point = new Vec3(from.x + dx * t, from.y, from.z + dz * t);
            AABB box = mob.getBoundingBox().move(point.x - mob.getX(), point.y + .001 - mob.getY(),
                    point.z - mob.getZ());
            if (!loaded(level, box)) return false;
            if (measurement != null) measurement.noCollisionProbe();
            if (!level.noCollision(mob, box)) return false;
            BlockPos cell = BlockPos.containing(point.x, point.y, point.z);
            Node support = new Node(cell.getX(), cell.getZ(), point.y);
            if (!supportedFootprint(mob, level, support, box)) return false;
        }
        return true;
    }

    private static boolean loaded(ServerLevel level, AABB box) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) if (!loaded(level, pos)) return false;
        return true;
    }
    /** Shared loaded-only volume preflight for bounded planners; never requests chunks. */
    static boolean loadedVolume(ServerLevel level, AABB box) { return loaded(level, box); }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.hasChunkAt(pos);
    }
    private static double heuristic(Node a, Node b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z) + Math.abs(a.y - b.y) * 1.25;
    }
    private static List<Waypoint> reconstruct(Node start, Node end, Map<Node, Node> parent) {
        List<Waypoint> reversed = new ArrayList<>();
        for (Node at = end; !at.equals(start); at = parent.get(at)) {
            if (at == null) return List.of();
            reversed.add(at.waypoint());
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }
}
