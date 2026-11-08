package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"animals_npc_flight"})
public final class AnimalNpcFlightPushRegressionGameTests {
    private AnimalNpcFlightPushRegressionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void elevatedIdleRouteProjectsToSupportedGroundAnchor(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var batType = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().entrySet().stream()
                .filter(e -> e.getKey().equals("bat")).map(java.util.Map.Entry::getValue).findFirst().orElse(null);
        require(batType != null, "registered bat exists");
        var bat = batType.get().create(level);
        require(bat != null, "bat creates");
        bat.setPos(floor.getX() + .5, floor.getY() + 2.5, floor.getZ() + .5);
        require(level.addFreshEntity(bat), "bat added");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(bat, level);
        var flight = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat)
                .flatMap(character -> character.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class))
                .orElseThrow(() -> new GameTestAssertException("resolved bat flight capability"));
        var router = new GroundNpcRoutePlanner();
        for (double[] test : new double[][]{{1.5, .5, .5}, {2.5, .5, .5}, {1.75, .9, .9}}) {
            double cruiseHeight = test[0];
            bat.setPos(floor.getX() + test[1], floor.getY() + cruiseHeight, floor.getZ() + test[2]);
            Vec3 anchor = NpcBehaviorSystem.idleRouteAnchor(bat, flight, bat.getY(), level.getMinBuildHeight());
            var supported = router.randomReachableIdleRoute(bat, 3, anchor, cruiseHeight);
            require(supported.status() == GroundNpcRoutePlanner.RouteStatus.ROUTE && supported.goalSupport() != null,
                    "elevated/fractional off-center start projects onto supported ground at cruise height " + cruiseHeight + ": " + supported);
        }
        // Remove every candidate support under the bounded anchor neighborhood: projection must
        // fail closed rather than manufacture a route or scan outside the local surface bound.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            level.setBlock(floor.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
        Vec3 anchor = NpcBehaviorSystem.idleRouteAnchor(bat, flight, bat.getY(), level.getMinBuildHeight());
        var unsupported = router.randomReachableIdleRoute(bat, 3, anchor, 2.5);
        require(unsupported.status() == GroundNpcRoutePlanner.RouteStatus.NO_PATH
                        || unsupported.status() == GroundNpcRoutePlanner.RouteStatus.DEFERRED,
                "unsupported anchor fails closed within planner budget: " + unsupported);
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1200)
    public static void batCruisesAndRecoversAfterSingleHorizontalImpulse(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
            level.setBlock(floor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var batType = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().entrySet().stream()
                .filter(e -> e.getKey().equals("bat"))
                .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
        require(batType != null, "registered canonical bat entity type exists");
        var bat = batType.get().create(level);
        require(bat != null, "canonical bat creates");
        bat.getRandom().setSeed(140014L);
        bat.setPos(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
        require(level.addFreshEntity(bat), "bat added");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(bat, level);
        require(com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(bat), "bat body reconciled");
        require(bat instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven,
                "bat carries NPC movement marker");
        require(bat instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob
                        && !((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) bat)
                        .moonstation14$isMovementOwned(), "bat starts without player/operator movement ownership");
        long started = level.getGameTime();
        pollAltitude(helper, bat, floor.getY(), started, 600);
    }

    private static void pollAltitude(GameTestHelper h, com.juicyslew.moonstation14.entities.AnimalMob bat,
                                     double floorY, long started, int left) {
        var snap = NpcBehaviorSystem.debugSnapshot(bat);
        Vec3 velocity = bat.getDeltaMovement();
        boolean qualified = bat.getY() >= floorY + .75
                && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.FLYING
                && snap != null && snap.flightPhase() == NpcFlightController.Phase.AIR_TRAVEL
                && snap.idlePoint() != null
                && snap.phaseDeadline() - bat.level().getGameTime() >= 60
                && Math.hypot(bat.getX()-snap.idlePoint().x, bat.getZ()-snap.idlePoint().z) > 1.0
                && Math.hypot(velocity.x, velocity.z) >= .02;
        if (qualified) {
            Vec3 goal = snap == null ? null : snap.idlePoint();
            int idleWait = snap.idleWait();
            double dx = bat.getX() - goal.x, dz = bat.getZ() - goal.z;
            double length = Math.hypot(dx, dz);
            // One modest impulse perpendicular to the current goal direction tests steering, not displacement.
            bat.setDeltaMovement(bat.getDeltaMovement().add(-dz / length * .15, 0, dx / length * .15));
            pollAfterPush(h, bat, started, 45, bat.position(), goal, idleWait, 0, 0, bat.position(), 0);
            return;
        }
        if (left == 0) throw new GameTestAssertException(context(h, bat, started,
                "no qualified active, non-arrived flying idle goal before impulse"));
        h.runAfterDelay(1, () -> pollAltitude(h, bat, floorY, started, left-1));
    }

    private static void pollAfterPush(GameTestHelper h, com.juicyslew.moonstation14.entities.AnimalMob bat,
                                        long started, int left, Vec3 pushedFrom, Vec3 goal, int idleWait,
                                        int steeringSamples, int maxSpeedSamples, Vec3 priorPosition,
                                        double resumedHorizontalPath) {
        var snap = NpcBehaviorSystem.debugSnapshot(bat);
        Vec3 velocity = bat.getDeltaMovement();
        double speedNow = Math.hypot(velocity.x, velocity.z);
        double displacementNow = Math.hypot(bat.getX() - pushedFrom.x, bat.getZ() - pushedFrom.z);
        Vec3 current = snap == null ? null : snap.idlePoint();
        double towardNow = current == null ? -1 : (current.x - bat.getX()) * velocity.x + (current.z - bat.getZ()) * velocity.z;
        int steering = steeringSamples + (45 - left >= 10 && current != null && current.y >= h.getLevel().getMinBuildHeight() && towardNow > 0 ? 1 : 0);
        int bounded = maxSpeedSamples + (speedNow < .8 && displacementNow < 12 ? 1 : 0);
        int elapsed = 45 - left;
        double stepDistance = Math.hypot(bat.getX() - priorPosition.x, bat.getZ() - priorPosition.z);
        double resumedPath = resumedHorizontalPath + (elapsed >= 10 ? stepDistance : 0);
        if (left == 0) {
            require(snap != null && current != null && current.y >= h.getLevel().getMinBuildHeight()
                            && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                            == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.FLYING
                            && bounded == 46 && steering >= 5 && resumedPath >= .5,
                    context(h, bat, started, "recovery bounded=" + bounded + "/46 steer=" + steering + "/36 path="
                            + resumedPath + "/0.5 phase="
                            + (snap == null ? null : snap.flightPhase()) + " remaining=" + (snap == null ? -1 : snap.phaseDeadline()-h.getLevel().getGameTime())));
            h.succeed(); return;
        }
        if (snap == null)
            throw new GameTestAssertException(context(h, bat, started,
                    "NPC debug snapshot disappeared after impulse; originalGoal="+goal+" idleWait="+idleWait));
        Vec3 position = bat.position();
        h.runAfterDelay(1, () -> pollAfterPush(h, bat, started, left-1, pushedFrom, goal, idleWait,
                steering, bounded, position, resumedPath));
    }

    private static String context(GameTestHelper h, com.juicyslew.moonstation14.entities.AnimalMob bat,
                                 long started, String reason) {
        var snapshot = NpcBehaviorSystem.debugSnapshot(bat);
        return reason + "; elapsedTicks="+(h.getLevel().getGameTime()-started)+" pos="+bat.position()
                +" flightState="+com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                +" task="+(snapshot == null ? null : snapshot.task())
                +" goal="+(snapshot == null ? null : snapshot.idlePoint())
                +" idleUntil="+(snapshot == null ? null : snapshot.idleUntil())
                +" routeResult="+(snapshot == null ? null : snapshot.routeResult())
                +" lastMotorStep="+(snapshot == null ? null : snapshot.motorStep());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
