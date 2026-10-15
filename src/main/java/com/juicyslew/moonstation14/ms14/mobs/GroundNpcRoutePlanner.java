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
    private static final int MAX_SERVER_TICK_ROUTE_CALLS = 12;
    // At most ten full searches across a level per tick; staggered entity decisions share this
    // budget without delaying the shared one-per-tick movement motor.
    private static final int MAX_SERVER_TICK_EXPANSIONS = 7_000;
    private static final int MAX_HORIZONTAL = 12;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static MinecraftServer budgetServer;
    private static int budgetTick = Integer.MIN_VALUE;
    private static int tickExpansions;
    private static int tickRouteCalls;
    private static long totalCalls;
    private static long totalExpansions;
    private static long totalDeferrals;
    static {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> resetBudget(event.getServer()));
    }
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

    public List<Waypoint> route(Mob mob, Vec3 target) {
        return routeResult(mob, target).waypoints();
    }

    RouteResult routeResult(Mob mob, Vec3 target) {
        totalCalls++;
        if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved() || target == null)
            return new RouteResult(RouteStatus.NO_PATH, List.of());
        MinecraftServer server = level.getServer();
        int currentTick = server.getTickCount();
        if (budgetServer != server || budgetTick != currentTick) {
            budgetServer = server;
            budgetTick = currentTick;
            tickExpansions = 0;
            tickRouteCalls = 0;
        }
        if (tickRouteCalls >= MAX_SERVER_TICK_ROUTE_CALLS || tickExpansions >= MAX_SERVER_TICK_EXPANSIONS) {
            totalDeferrals++;
            return new RouteResult(RouteStatus.DEFERRED, List.of());
        }
        tickRouteCalls++;
        Node start = nearestSurface(mob, level, mob.position(), Math.sqrt(.5) + 1e-6);
        Node goal = nearestSurface(mob, level, target, 1.45);
        String invalidProjection = start == null || goal == null ? null : startProjectionFailure(mob, level, start);
        if (start == null || goal == null || invalidProjection != null)
            return new RouteResult(RouteStatus.NO_PATH, List.of(), start == null ? null : start.waypoint(),
                    goal == null ? null : goal.waypoint(), 0, start == null ? "NO_START_SUPPORT"
                    : goal == null ? "NO_GOAL_SUPPORT" : invalidProjection);
        if (Math.abs(start.y - goal.y) < .05 && directWalkClear(mob, level,
                start.waypoint().position(), goal.waypoint().position())) {
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
                && tickExpansions < MAX_SERVER_TICK_EXPANSIONS) {
            Node current = open.remove().node();
            if (!closed.add(current)) continue;
            localExpansions++;
            tickExpansions++;
            totalExpansions++;
            if (current.equals(goal)) return new RouteResult(RouteStatus.ROUTE,
                    reconstruct(start, current, parent), start.waypoint(), goal.waypoint(), rejectedEdges, "A_STAR");
            for (int[] step : NEIGHBORS) {
                int x = current.x + step[0], z = current.z + step[1];
                if (Math.abs(x - start.x) > MAX_HORIZONTAL || Math.abs(z - start.z) > MAX_HORIZONTAL) continue;
                boolean diagonal = step[0] != 0 && step[1] != 0;
                if (diagonal && (!cardinalConnectorClear(mob, level, current, current.x + step[0], current.z)
                        || !cardinalConnectorClear(mob, level, current, current.x, current.z + step[1]))) continue;
                for (Node next : surfaces(mob, level, x, z, current.y)) {
                    if (closed.contains(next)) continue;
                    double rise = next.y - current.y;
                    if (rise > 1.0001 || rise < -1.0001 || !edgeClear(mob, level, current, next)) {
                        rejectedEdges++;
                        continue;
                    }
                    double nextCost = cost.get(current) + (diagonal ? Math.sqrt(2) : 1)
                            + Math.max(0, rise) * .4 + Math.max(0, -rise) * .6;
                    if (nextCost >= cost.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                    cost.put(next, nextCost);
                    parent.put(next, current);
                    open.add(new Open(next, nextCost + heuristic(next, goal)));
                }
            }
        }
        if (!open.isEmpty() && tickExpansions >= MAX_SERVER_TICK_EXPANSIONS) {
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
        if (level == null || budgetServer != level.getServer() || budgetTick != level.getServer().getTickCount())
            return new TickBudgetSnapshot(0, 0);
        return new TickBudgetSnapshot(tickRouteCalls, tickExpansions);
    }

    private static void resetBudget(MinecraftServer server) {
        if (budgetServer != server) return;
        budgetServer = null;
        budgetTick = Integer.MIN_VALUE;
        tickRouteCalls = 0;
        tickExpansions = 0;
    }

    /** The graph is cell-centered; validate the short real-body-to-node connector before seeding A*. */
    private static String startProjectionFailure(Mob mob, ServerLevel level, Node start) {
        Vec3 from = mob.position();
        if (!Double.isFinite(from.x) || !Double.isFinite(from.y) || !Double.isFinite(from.z)) return "INVALID_START_POSITION";
        double dx = start.x + .5 - from.x, dz = start.z + .5 - from.z;
        AABB body = mob.getBoundingBox();
        int samples = 4;
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            AABB projected = body.move(dx * t, .001, dz * t);
            if (!loaded(level, projected)) return "UNLOADED_START_PROJECTION_SAMPLE_" + i + "_" + projected;
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
        return surfaces(mob, level, x, z, feetPosition.y).stream()
                .anyMatch(node -> Math.abs(node.y - feetPosition.y) < .01);
    }

    /** Resolve an arbitrary idle target to the center of its nearest valid graph node. */
    public Vec3 nearestSupportedPoint(Mob mob, Vec3 point, double radius) {
        if (!(mob.level() instanceof ServerLevel level) || point == null || !Double.isFinite(radius) || radius < 0)
            return null;
        Node node = nearestSurface(mob, level, point, radius);
        return node == null ? null : node.waypoint().position();
    }

    private static Node nearestSurface(Mob mob, ServerLevel level, Vec3 point, double radius) {
        Node best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int cx = BlockPos.containing(point.x, point.y, point.z).getX();
        int cz = BlockPos.containing(point.x, point.y, point.z).getZ();
        int reach = Math.max(1, (int) Math.ceil(radius));
        for (int x = cx - reach; x <= cx + reach; x++) for (int z = cz - reach; z <= cz + reach; z++)
            for (Node candidate : surfaces(mob, level, x, z, point.y)) {
                Waypoint wp = candidate.waypoint();
                double dx = wp.x - point.x, dz = wp.z - point.z, dy = wp.y - point.y;
                double distance = dx * dx + dz * dz + dy * dy;
                if (distance <= radius * radius && distance < bestDistance) { best = candidate; bestDistance = distance; }
            }
        return best;
    }

    private static List<Node> surfaces(Mob mob, ServerLevel level, int x, int z, double nearY) {
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
                if (standable(mob, level, node)) result.add(node);
            }
        }
        return result;
    }

    private static boolean standable(Mob mob, ServerLevel level, Node node) {
        AABB box = mob.getBoundingBox().move(node.x + .5 - mob.getX(), node.y + .001 - mob.getY(), node.z + .5 - mob.getZ());
        if (box.minY < level.getMinBuildHeight() || box.maxY >= level.getMaxBuildHeight() || !loaded(level, box)) return false;
        BlockPos feet = BlockPos.containing(node.x + .5, node.y + 1e-5, node.z + .5);
        if (!level.getFluidState(feet).isEmpty()) return false;
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

    private static boolean edgeClear(Mob mob, ServerLevel level, Node from, Node to) {
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
                if (!loaded(level, across) || !level.noCollision(mob, across)) return false;
            }
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB down = base.move(end.x - start.x, rise * t + .001, end.z - start.z);
                if (!loaded(level, down) || !level.noCollision(mob, down)) return false;
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
                if (!loaded(level, launch) || !level.noCollision(mob, launch)) return false;
            }
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB across = base.move((end.x - start.x) * t, lift, (end.z - start.z) * t);
                if (!loaded(level, across) || !level.noCollision(mob, across)) return false;
            }
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples;
                AABB landing = base.move(end.x - start.x, lift - (lift - rise) * t, end.z - start.z);
                if (!loaded(level, landing) || !level.noCollision(mob, landing)) return false;
            }
            return true;
        }
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            double vertical = .001;
            AABB swept = base.move((end.x - start.x) * t, vertical,
                    (end.z - start.z) * t);
            if (!loaded(level, swept) || !level.noCollision(mob, swept)) return false;
        }
        return true;
    }

    private static boolean cardinalConnectorClear(Mob mob, ServerLevel level, Node from, int x, int z) {
        for (Node adjacent : surfaces(mob, level, x, z, from.y)) {
            if (Math.abs(adjacent.y - from.y) <= 1.0001 && edgeClear(mob, level, from, adjacent)) return true;
        }
        return false;
    }

    /** Straight grounded passage check used by open-area fast paths and never by interaction LOS. */
    private static boolean directWalkClear(Mob mob, ServerLevel level, Vec3 from, Vec3 to) {
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
            if (!loaded(level, box) || !level.noCollision(mob, box)) return false;
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
