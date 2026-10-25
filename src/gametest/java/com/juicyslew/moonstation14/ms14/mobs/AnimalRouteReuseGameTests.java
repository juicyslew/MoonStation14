package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags("animals_route_reuse")
public final class AnimalRouteReuseGameTests {
    private AnimalRouteReuseGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void flatDirectRouteMatchesWithoutAssumingReuse(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, base, 7);
        var mob = mouse(helper, base);
        var planner = new GroundNpcRoutePlanner();
        Vec3 target = base.offset(3, 0, 0).getCenter().add(0, -.5, 0);
        var cached = planner.routeResultForTest(mob, target, true);
        var uncached = planner.routeResultForTest(mob, target, false);
        compare(cached.result(), uncached.result());
        require(cached.result().status() == GroundNpcRoutePlanner.RouteStatus.ROUTE, "flat direct route exists");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags("animals_idle_reachable")
    public static void idlePlannerReturnsReachableRouteIncludingStart(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, base, 7);
        var mob = mouse(helper, base);
        var planner = new GroundNpcRoutePlanner();
        var result = planner.randomReachableIdleRoute(mob, 6);
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE, "reachable idle choice exists");
        require(result.goalSupport() != null, "goal has resolved support");
        require(result.waypoints().isEmpty() == (result.goalSupport().position().distanceTo(mob.position()) < .1),
                "start choice is a valid empty route; other choices already have waypoints");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    @GameTestTags({"animals_idle_reachable", "npc-route-regression"})
    public static void animalSelectedStartCompletesNaturalArrivalIntoIdleWait(GameTestHelper helper) {
        var base = helper.absolutePos(new BlockPos(8, 1, 8));
        helper.getLevel().setBlock(base.below(), Blocks.STONE.defaultBlockState(), 3);
        // No adjacent supported cells: the only reachable idle choice is the animal's current cell.
        for (int y = 0; y <= 2; y++) for (int n = -1; n <= 1; n++) {
            helper.getLevel().setBlock(base.offset(1, y, n), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(-1, y, n), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(n, y, 1), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(n, y, -1), Blocks.STONE.defaultBlockState(), 3);
        }
        var type = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("cow");
        require(type != null, "registered cow host exists");
        var animal = type.get().create((net.minecraft.server.level.ServerLevel) helper.getLevel());
        require(animal != null, "cow constructed");
        animal.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
        require(helper.getLevel().addFreshEntity(animal), "cow joined world");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(
                animal, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        awaitStartIdleWait(helper, animal, 0);
    }

    private static void awaitStartIdleWait(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.AnimalMob animal, int elapsed) {
        var state = NpcBehaviorSystem.debugSnapshot(animal);
        if (state != null && state.task() == null
                && state.idlePoint() == null && state.idleUntil() > helper.getLevel().getGameTime()) {
            require(state.idleWait() >= 40 && state.idleWait() <= 140,
                    "selected-start arrival enters resolved idle wait: " + state);
            helper.succeed();
            return;
        }
        if (elapsed >= 100) throw new GameTestAssertException("natural selected-start idle did not enter arrival wait: " + state);
        helper.runAfterDelay(1, () -> awaitStartIdleWait(helper, animal, elapsed + 1));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"animals_idle_reachable", "mouse-route"})
    public static void offCenterSelectedStartRetainsCenterWaypoint(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, base, 5);
        var mob = mouse(helper, base);
        mob.setPos(base.getX() + .05, base.getY(), base.getZ() + .05);
        double startDistance = base.getCenter().add(0, -.5, 0).distanceTo(mob.position());
        require(startDistance > .6, "off-center start clears arrival threshold before route query");
        sampleOffCenterSelectedStart(helper, mob, base, 0);
    }

    private static void sampleOffCenterSelectedStart(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.MouseMob mob, BlockPos base, int deferrals) {
        // Custom-NPC stepping may reposition the fixture between ticks; retain the intended off-center query origin.
        mob.setPos(base.getX() + .05, base.getY(), base.getZ() + .05);
        double distance = base.getCenter().add(0, -.5, 0).distanceTo(mob.position());
        require(distance > .6, "off-center start clears arrival threshold before route query: distance=" + distance);
        var result = new GroundNpcRoutePlanner().randomReachableIdleRoute(mob, 0);
        if (result.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED) {
            if (deferrals >= 12) throw new GameTestAssertException("off-center idle query remained deferred after bounded retries: status="
                    + result.status() + " detail=" + result.detail());
            helper.runAfterDelay(1, () -> sampleOffCenterSelectedStart(helper, mob, base, deferrals + 1));
            return;
        }
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE,
                "off-center idle selection status=" + result.status() + " detail=" + result.detail());
        require(result.goalSupport() != null && result.waypoints().size() == 1
                        && result.waypoints().get(0).equals(result.startSupport()),
                "selected start includes its verified center waypoint: " + result);
        require(result.waypoints().get(0).position().distanceTo(mob.position()) > .6,
                "center waypoint clears arrival threshold: start=" + mob.position() + " route=" + result);
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"animals_idle_reachable", "mouse-route"})
    public static void idleSelectionStaysInsideSealedReachableComponent(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, base, 7);
        var mob = mouse(helper, base);
        for (int y = 0; y <= 2; y++) for (int n = -2; n <= 2; n++) {
            helper.getLevel().setBlock(base.offset(2, y, n), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(-2, y, n), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(n, y, 2), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(n, y, -2), Blocks.STONE.defaultBlockState(), 3);
        }
        GroundNpcRoutePlanner planner = new GroundNpcRoutePlanner();
        var outside = planner.nearestSupportedPoint(mob, base.offset(4, 0, 0).getCenter().add(0, -.5, 0), 1.45);
        require(outside != null, "outside location has geometric support");
        sampleSealedIdleRoute(helper, planner, mob, base, 0, 0);
    }

    private static void sampleSealedIdleRoute(GameTestHelper helper, GroundNpcRoutePlanner planner,
                                               com.juicyslew.moonstation14.entities.MouseMob mob,
                                               BlockPos base, int sample, int deferrals) {
        if (sample == 12) {
            require(sample >= 8, "enough non-deferred idle choices");
            helper.succeed();
            return;
        }
        var before = GroundNpcRoutePlanner.tickBudget((net.minecraft.server.level.ServerLevel) helper.getLevel());
        var result = planner.randomReachableIdleRoute(mob, 6);
        var after = GroundNpcRoutePlanner.tickBudget((net.minecraft.server.level.ServerLevel) helper.getLevel());
        if (result.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED) {
            require(deferrals < 12, "idle query remains deferred across fresh ticks");
            helper.runAfterDelay(1, () -> sampleSealedIdleRoute(helper, planner, mob, base, sample, deferrals + 1));
            return;
        }
        require(result.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE, "reachable idle selection status=" + result.status());
        require(after.expansions() - before.expansions() <= 40, "idle query expansion bound");
        require(result.goalSupport() != null && Math.abs(result.goalSupport().position().x - base.getX() - .5) <= 1.5
                        && Math.abs(result.goalSupport().position().z - base.getZ() - .5) <= 1.5,
                "idle endpoint remains in sealed component");
        for (var waypoint : result.waypoints()) {
            require(Math.abs(waypoint.x() - base.getX() - .5) <= 1.5
                            && Math.abs(waypoint.z() - base.getZ() - .5) <= 1.5,
                    "route waypoint remains in sealed component");
            var box = mob.getBoundingBox().move(waypoint.x() - mob.getX(), waypoint.y() + .001 - mob.getY(),
                    waypoint.z() - mob.getZ());
            require(helper.getLevel().noCollision(mob, box), "route waypoint is collision-free");
        }
        require(result.goalSupport().position().distanceTo(result.startSupport().position()) == 0
                        || !result.waypoints().isEmpty(), "non-start endpoint has retained route");
        helper.runAfterDelay(1, () -> sampleSealedIdleRoute(helper, planner, mob, base, sample + 1, deferrals));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void cachedAndUncachedSearchMatchWithRealReuse(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, base, 7);
        var mob = mouse(helper, base);
        for (int z = -3; z <= 3; z++) for (int y = 0; y <= 2; y++)
            helper.getLevel().setBlock(base.offset(2, y, z), Blocks.STONE.defaultBlockState(), 3);
        GroundNpcRoutePlanner planner = new GroundNpcRoutePlanner();
        Vec3 target = base.offset(4, 0, 3).getCenter().add(0, -.5, 0);
        var cached = planner.routeResultForTest(mob, target, true);
        var uncached = planner.routeResultForTest(mob, target, false);
        compare(cached.result(), uncached.result());
        require(cached.result().status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED, "cached call not deferred");
        require(uncached.result().status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED, "uncached call not deferred");
        require(cached.result().status() == GroundNpcRoutePlanner.RouteStatus.ROUTE, "corridor is reachable");
        require(cached.work().surfaceCacheHits() > 0, "non-direct search reuses exact per-attempt surface queries");
        require(cached.work().surfaceComputations() < uncached.work().surfaceComputations(), "reuse reduces measured computations");
        require(cached.work().standableCalls() < uncached.work().standableCalls(), "reuse reduces actual standable calls");
        require(cached.work().noCollisionProbes() < uncached.work().noCollisionProbes(), "reuse reduces actual collision probes");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void unreachableVolumeHasEquivalentBoundedNoPath(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, base, 7);
        var mob = mouse(helper, base);
        for (int y = 0; y <= 2; y++) for (int n = -2; n <= 2; n++) {
            helper.getLevel().setBlock(base.offset(2, y, n), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(-2, y, n), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(n, y, 2), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.offset(n, y, -2), Blocks.STONE.defaultBlockState(), 3);
        }
        Vec3 goal = base.offset(4, 0, 0).getCenter().add(0, -.5, 0);
        var planner = new GroundNpcRoutePlanner();
        var cached = planner.routeResultForTest(mob, goal, true);
        var uncached = planner.routeResultForTest(mob, goal, false);
        compare(cached.result(), uncached.result());
        require(cached.result().status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH, "closed wall blocks route");
        require("SEARCH_EXHAUSTED".equals(cached.result().detail()), "search reached the wall and exhausted without projection failure");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void perAttemptCacheDoesNotHideWorldChanges(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        floor(helper, base, 5);
        var mob = mouse(helper, base);
        GroundNpcRoutePlanner planner = new GroundNpcRoutePlanner();
        Vec3 goal = base.offset(3, 0, 0).getCenter().add(0, -.5, 0);
        var before = planner.routeResultForTest(mob, goal, true);
        require(before.result().status() == GroundNpcRoutePlanner.RouteStatus.ROUTE, "open route initially exists");
        // A wall across the corridor must be observed by the next independent route attempt.
        for (int x = 1; x <= 3; x++) for (int y = 0; y <= 2; y++)
            helper.getLevel().setBlock(base.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
        var afterCached = planner.routeResultForTest(mob, goal, true);
        var afterUncached = planner.routeResultForTest(mob, goal, false);
        compare(afterCached.result(), afterUncached.result());
        require(afterCached.result().status() != GroundNpcRoutePlanner.RouteStatus.DEFERRED, "changed-world call not deferred");
        require(!before.result().waypoints().equals(afterCached.result().waypoints())
                || before.result().status() != afterCached.result().status(), "obstruction changes route outcome or waypoints");
        helper.succeed();
    }

    private static void compare(GroundNpcRoutePlanner.RouteResult a, GroundNpcRoutePlanner.RouteResult b) {
        require(a.status() == b.status(), "status parity");
        require(a.waypoints().equals(b.waypoints()), "waypoint parity");
        require(java.util.Objects.equals(a.startSupport(), b.startSupport()), "start support parity");
        require(java.util.Objects.equals(a.goalSupport(), b.goalSupport()), "goal support parity");
        require(java.util.Objects.equals(a.detail(), b.detail()), "diagnostic reason parity");
    }

    private static com.juicyslew.moonstation14.entities.MouseMob mouse(GameTestHelper helper, BlockPos pos) {
        var mob = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(helper.getLevel());
        require(mob != null, "mouse created");
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        require(helper.getLevel().addFreshEntity(mob), "mouse added");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(
                mob, (net.minecraft.server.level.ServerLevel) helper.getLevel());
        com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(mob);
        return mob;
    }
    private static void floor(GameTestHelper helper, BlockPos base, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            helper.getLevel().setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
