package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"animals_air_routes", "animals_npc_flight", "animals", "movement"})
public final class AirNpcRoutePlannerGameTests {
    private AirNpcRoutePlannerGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void plansElevatedRouteOverOpenTopWall(GameTestHelper h) {
        var level = (net.minecraft.server.level.ServerLevel) h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(8, 3, 8));
        var bat = bat(level, p);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++)
                level.setBlock(p.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        require(!level.getBlockState(p.below()).isAir(), "ring interior must have a solid collision floor");
        for (int y = 0; y <= 4; y++) {
            Vec3 ascent = new Vec3(p.getX() + .5, p.getY() + y, p.getZ() + .5);
            require(bodyClear(level, bat, ascent), "ring ascent body must be clear at " + ascent);
        }
        Vec3 crossing = new Vec3(p.getX() + 2.5, p.getY() + 3.25, p.getZ() + .5);
        require(bodyClear(level, bat, crossing), "intended top crossing body must be clear at " + crossing);
        for (int y = 0; y < 3; y++) {
            for (int i = -2; i <= 2; i++) {
                level.setBlock(p.offset(2, y, i), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(p.offset(-2, y, i), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(p.offset(i, y, 2), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(p.offset(i, y, -2), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        Vec3 destination = new Vec3(p.getX() + 4.5, p.getY(), p.getZ() + .5);
        retryAdmittedRoute(h, bat, destination, AirNpcRoutePlanner.RouteStatus.ROUTE, (result, start) -> {
        require(result.status() == AirNpcRoutePlanner.RouteStatus.ROUTE, "open-top wall route: " + result);
        Vec3 previous = start;
        boolean crossedAboveWall = false;
        for (var waypoint : result.waypoints()) {
            Vec3 point = new Vec3(waypoint.x(), waypoint.y(), waypoint.z());
            require(bodyClear(level, bat, point), "waypoint body must be clear: " + point);
            if (crossesRingAboveWall(level, bat, previous, point, p))
                crossedAboveWall = true;
            previous = point;
        }
        require(bodyClear(level, bat, destination), "3D destination body must be clear: " + destination);
        if (crossesRingAboveWall(level, bat, previous, destination, p))
            crossedAboveWall = true;
        require(crossedAboveWall, "a route segment must cross above at least one finite ring wall footprint: " + result.waypoints());
        require(result.waypoints().get(result.waypoints().size() - 1).y() == p.getY(), "endpoint retains destination Y");
        h.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    public static void rejectsSolidDestinationAndHonorsFullBodyCeiling(GameTestHelper h) {
        var level = (net.minecraft.server.level.ServerLevel) h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(8, 3, 8));
        var bat = bat(level, p);
        level.setBlock(p.offset(2, 2, 0), Blocks.STONE.defaultBlockState(), 3);
        Vec3 insideSolid = new Vec3(p.getX() + 2.5, p.getY() + 2.5, p.getZ() + .5);
        retryAdmittedRoute(h, bat, insideSolid, AirNpcRoutePlanner.RouteStatus.NO_PATH, (blocked, start) -> {
        require(blocked.status() == AirNpcRoutePlanner.RouteStatus.NO_PATH, "solid destination must be admitted NO_PATH: " + blocked);
        level.setBlock(p.offset(0, 2, 0), Blocks.STONE.defaultBlockState(), 3);
        Vec3 ceilingDestination = new Vec3(p.getX() + .5, p.getY() + 2.5, p.getZ() + .5);
        retryAdmittedRoute(h, bat, ceilingDestination, AirNpcRoutePlanner.RouteStatus.NO_PATH, (ceiling, routeStart) -> {
            require(ceiling.status() == AirNpcRoutePlanner.RouteStatus.NO_PATH,
                    "full body ceiling must be admitted NO_PATH: " + ceiling);
            h.succeed();
        });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void routesBetweenUnsupportedAirPositions(GameTestHelper h) {
        var level = (net.minecraft.server.level.ServerLevel) h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(8, 6, 8));
        var bat = bat(level, p);
        Vec3 destination = new Vec3(p.getX() + 3.5, p.getY() + 1.0, p.getZ() + .5);
        require(level.getBlockState(p.below()).isAir() && level.getBlockState(BlockPos.containing(destination).below()).isAir(),
                "start and destination must have no supporting floor");
        retryAdmittedRoute(h, bat, destination, AirNpcRoutePlanner.RouteStatus.ROUTE, (result, start) -> {
        require(result.status() == AirNpcRoutePlanner.RouteStatus.ROUTE, "unsupported-air route: " + result);
        require(bodyClear(level, bat, start), "3D start body must be clear");
        for (var waypoint : result.waypoints()) {
            Vec3 point = new Vec3(waypoint.x(), waypoint.y(), waypoint.z());
            require(bodyClear(level, bat, point), "unsupported-air waypoint body must be clear: " + point);
        }
        require(bodyClear(level, bat, destination), "3D destination body must be clear: " + destination);
        h.succeed();
        });
    }

    private static boolean bodyClear(net.minecraft.server.level.ServerLevel level, com.juicyslew.moonstation14.entities.AnimalMob mob, Vec3 point) {
        AABB box = mob.getBoundingBox().move(point.x - mob.getX(), point.y - mob.getY(), point.z - mob.getZ());
        return level.noCollision(mob, box);
    }

    @FunctionalInterface
    private interface AdmittedRouteCheck {
        void run(AirNpcRoutePlanner.AirRouteResult result, Vec3 actualStart);
    }

    private static void retryAdmittedRoute(GameTestHelper h,
            com.juicyslew.moonstation14.entities.AnimalMob mob, Vec3 destination,
            AirNpcRoutePlanner.RouteStatus expected, AdmittedRouteCheck check) {
        retryAdmittedRoute(h, mob, destination, expected, check, 0);
    }

    private static void retryAdmittedRoute(GameTestHelper h,
            com.juicyslew.moonstation14.entities.AnimalMob mob, Vec3 destination,
            AirNpcRoutePlanner.RouteStatus expected, AdmittedRouteCheck check, int tick) {
        Vec3 actualStart = mob.position();
        AirNpcRoutePlanner.AirRouteResult result = new AirNpcRoutePlanner().routeResult(mob, destination);
        if (result.status() == AirNpcRoutePlanner.RouteStatus.DEFERRED
                && "SERVER_TICK_BUDGET".equals(result.detail())) {
            if (tick >= 25) throw new GameTestAssertException("route never admitted within 25 ticks; last result=" + result);
            h.runAfterDelay(1, () -> retryAdmittedRoute(h, mob, destination, expected, check, tick + 1));
            return;
        }
        if (result.status() != expected)
            throw new GameTestAssertException("route admission/result mismatch; expected " + expected + ", got " + result);
        check.run(result, actualStart);
    }

    private static boolean crossesRingAboveWall(net.minecraft.server.level.ServerLevel level,
            com.juicyslew.moonstation14.entities.AnimalMob mob, Vec3 a, Vec3 b, BlockPos ringCenter) {
        double halfWidth = (mob.getBbWidth() / 2.0) + 0.01;
        double bodyBottomOffset = mob.getY() - mob.getBoundingBox().minY;
        boolean intersectsWall = crossesWallPlane(a, b, true, ringCenter.getX() + 2.5,
                ringCenter.getZ() - 2, ringCenter.getZ() + 3, halfWidth, bodyBottomOffset, ringCenter.getY() + 3)
                || crossesWallPlane(a, b, true, ringCenter.getX() - 1.5,
                ringCenter.getZ() - 2, ringCenter.getZ() + 3, halfWidth, bodyBottomOffset, ringCenter.getY() + 3)
                || crossesWallPlane(a, b, false, ringCenter.getZ() + 2.5,
                ringCenter.getX() - 2, ringCenter.getX() + 3, halfWidth, bodyBottomOffset, ringCenter.getY() + 3)
                || crossesWallPlane(a, b, false, ringCenter.getZ() - 1.5,
                ringCenter.getX() - 2, ringCenter.getX() + 3, halfWidth, bodyBottomOffset, ringCenter.getY() + 3);
        if (!intersectsWall) return false;
        int samples = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.1));
        for (int i = 0; i <= samples; i++)
            if (!bodyClear(level, mob, a.lerp(b, i / (double) samples))) return false;
        return true;
    }

    private static boolean crossesWallPlane(Vec3 a, Vec3 b, boolean xPlane, double plane,
            double spanMin, double spanMax, double halfWidth, double bodyBottomOffset, double wallTop) {
        double from = xPlane ? a.x : a.z;
        double to = xPlane ? b.x : b.z;
        if (!((from < plane && to >= plane) || (to < plane && from >= plane))) return false;
        double t = (plane - from) / (to - from);
        Vec3 crossing = a.lerp(b, t);
        double along = xPlane ? crossing.z : crossing.x;
        return along - halfWidth >= spanMin && along + halfWidth <= spanMax
                && crossing.y - bodyBottomOffset > wallTop;
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void unloadedSearchRegionIsDeferredWithoutChunkLoads(GameTestHelper h) {
        var level = (net.minecraft.server.level.ServerLevel) h.getLevel();
        BlockPos p = h.absolutePos(new BlockPos(8, 3, 8));
        int fixtureChunkX = Math.floorDiv(p.getX(), 16), fixtureChunkZ = Math.floorDiv(p.getZ(), 16);
        int loadedX = Integer.MIN_VALUE, loadedZ = Integer.MIN_VALUE, neighborX = 0, neighborZ = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boundarySearch:
        for (int radius = 0; radius <= 32; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int cx = fixtureChunkX + dx, cz = fixtureChunkZ + dz;
                if (!level.hasChunk(cx, cz) || level.getChunkSource().getChunkNow(cx, cz) == null) continue;
                for (int[] direction : directions) {
                    int nx = cx + direction[0], nz = cz + direction[1];
                    if (!level.hasChunk(nx, nz) && level.getChunkSource().getChunkNow(nx, nz) == null) {
                        loadedX = cx; loadedZ = cz; neighborX = nx; neighborZ = nz;
                        break boundarySearch;
                    }
                }
            }
        }
         require(loadedX != Integer.MIN_VALUE, "no loaded/unloaded chunk boundary within bounded 32-chunk fixture search from " + fixtureChunkX + "," + fixtureChunkZ);
        boolean east = neighborX > loadedX, west = neighborX < loadedX;
        boolean south = neighborZ > loadedZ;
        int x = loadedX * 16 + (east ? 13 : west ? 2 : 8);
        int z = loadedZ * 16 + (south ? 13 : neighborZ < loadedZ ? 2 : 8);
        BlockPos batPos = new BlockPos(x, p.getY(), z);
        require(level.hasChunk(loadedX, loadedZ) && !level.hasChunk(neighborX, neighborZ),
                "selected boundary loaded=" + loadedX + "," + loadedZ + " unloaded=" + neighborX + "," + neighborZ);
        var bat = bat(level, batPos);
        try {
            Vec3 destination = new Vec3(x + (east ? 3.5 : west ? -3.5 : 0), p.getY(), z + (south ? 3.5 : neighborZ < loadedZ ? -3.5 : 0));
            int before = level.getChunkSource().getLoadedChunksCount();
            var result = new AirNpcRoutePlanner().routeResult(bat, destination);
            require(result.status() == AirNpcRoutePlanner.RouteStatus.DEFERRED, "unloaded boundary search deferred (loaded=" + loadedX + "," + loadedZ + ", unloaded=" + neighborX + "," + neighborZ + "): " + result);
            require(level.getChunkSource().getLoadedChunksCount() == before, "planner must not force-load chunks at boundary " + neighborX + "," + neighborZ);
            h.succeed();
        } finally {
            bat.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    private static com.juicyslew.moonstation14.entities.AnimalMob bat(net.minecraft.server.level.ServerLevel level, BlockPos p) {
        var mob = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("bat").get().create(level);
        if (mob == null) throw new GameTestAssertException("bat creation");
        mob.setPos(p.getX() + .5, p.getY(), p.getZ() + .5);
        mob.setNoAi(true);
        if (!level.addFreshEntity(mob) || com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob).isEmpty())
            throw new GameTestAssertException("normal entity join resolves bat");
        return mob;
    }

    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
