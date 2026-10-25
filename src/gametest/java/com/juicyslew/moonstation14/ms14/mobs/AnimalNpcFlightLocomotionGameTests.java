package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"animals_npc_flight", "npc-flight", "animals", "movement"})
public final class AnimalNpcFlightLocomotionGameTests {
    private AnimalNpcFlightLocomotionGameTests() { }

@GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 850)
    public static void normallyJoinedBatNpcActuallyRoamsInAir(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, 8, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var bat = createBat(level, origin);
        require(bat != null, "normal addFreshEntity join resolves bat prototypes");
        var flight = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElseThrow()
                .component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        observeNormalJoin(helper, bat, origin, 0, flight.npcGroundedTicks(), flight.npcAirborneTicks(), bat.getY(), bat.getY(), bat.getY(), bat.getX(), bat.getZ(), 0, 0, 0, 0);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 400)
    public static void normallyJoinedNpcUsesResolvedCruiseSpeedAcrossShortRouteNodes(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, 8, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var bat = createBat(level, origin);
        require(bat != null, "normal addFreshEntity join resolves bat prototypes");
        bat.getRandom().setSeed(140014L);
        var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElseThrow();
        var flight = resolved.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        var movement = com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.fromCharacterData(resolved);
        double cap = movement.sprintSpeedPerSecond() * flight.airborneSpeedMultiplier();
        sampleNpcCruise(helper, bat, origin, cap, 300, 0, 0, 0d, 0d, 0d);
    }

    private static void sampleNpcCruise(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.AnimalMob bat, BlockPos origin, double cap, int left,
            int samples, int shortNodeSamples, double speedTotal, double maxSpeed, double shortSpeedTotal) {
        helper.runAfterDelay(1, () -> {
            var snapshot = NpcBehaviorSystem.debugSnapshot(bat);
            var velocity = bat.getDeltaMovement();
            boolean airTravelMultiNode = snapshot != null
                    && snapshot.flightPhase() == NpcFlightController.Phase.AIR_TRAVEL
                    && snapshot.route() != null && snapshot.route().size() > 1
                    && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                    == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.FLYING;
            // Entity velocity is the movement motor's horizontal speed in blocks/tick;
            // convert to blocks/second. Displacement is deliberately not used here:
            // route curvature/collision can shorten it without reducing commanded cruise.
            double speed = Math.hypot(velocity.x, velocity.z) * 20d;
            boolean shortNode = airTravelMultiNode
                    && Math.hypot(bat.getX() - snapshot.route().get(0).position().x,
                            bat.getZ() - snapshot.route().get(0).position().z) <= 1.25;
            int nextSamples = samples + (airTravelMultiNode ? 1 : 0);
            int nextShort = shortNodeSamples + (shortNode ? 1 : 0);
            double nextTotal = speedTotal + (airTravelMultiNode ? speed : 0);
            double nextMax = airTravelMultiNode ? Math.max(maxSpeed, speed) : maxSpeed;
            double nextShortSpeed = shortSpeedTotal + (shortNode ? speed : 0);
            if (left > 1) {
                sampleNpcCruise(helper, bat, origin, cap, left - 1, nextSamples, nextShort,
                        nextTotal, nextMax, nextShortSpeed);
                return;
            }
            double average = nextSamples == 0 ? 0 : nextTotal / nextSamples;
            double shortAverage = nextShort == 0 ? 0 : nextShortSpeed / nextShort;
            require(nextSamples >= 8 && nextShort >= 2,
                    "insufficient AIR_TRAVEL multi-node/short-node samples; samples=" + nextSamples
                            + ", shortNode=" + nextShort + ", pos=" + bat.position() + ", snapshot=" + snapshot);
            require(average >= cap * .75 && shortAverage >= cap * .75 && nextMax <= cap + .25,
                    "autonomous cruise displacement must approach resolved airborne sprint cap; average=" + average
                            + ", shortMean=" + shortAverage + ", cap=" + cap + ", max=" + nextMax
                            + ", samples=" + nextSamples + ", shortNode=" + nextShort
                            + ", phase=" + (snapshot == null ? null : snapshot.flightPhase())
                            + ", route=" + (snapshot == null ? null : snapshot.route())
                            + ", pos=" + bat.position() + ", snapshot=" + snapshot);
            helper.succeed();
        });
    }

@GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 850)
    public static void autonomousNpcHonorsLandingRequestWithoutDamage(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        var bat = createBat(level, origin);
        require(bat != null, "normal addFreshEntity join resolves bat prototypes");
        var flight = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElseThrow()
                .component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        awaitAutonomousAscent(helper, bat, origin, bat.getY(), flight.npcGroundedTicks(), 0);
    }

    private static void awaitAutonomousAscent(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.AnimalMob bat, BlockPos origin, double startY, int groundedTicks, int tick) {
        helper.runAfterDelay(1, () -> {
            if (bat.getY() >= startY + 2.75 && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                    != com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED) {
                float health = bat.getHealth();
                var damage = java.util.Map.copyOf(com.juicyslew.moonstation14.ms14.damage.DamageSystem.existingOrVanillaBaseline(bat));
                require(com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.requestLanding(bat), "landing request accepted");
                observeAutonomousLanding(helper, bat, origin, health, damage, bat.getY(), 0);
            } else if (tick < groundedTicks * 1.25 + 100) awaitAutonomousAscent(helper, bat, origin, startY, groundedTicks, tick + 1);
            else throw new GameTestAssertException("autonomous NPC did not ascend above 2.75; y=" + bat.getY());
        });
    }

    private static void observeAutonomousLanding(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.AnimalMob bat, BlockPos origin, float health,
            java.util.Map<String, Float> damage,
            double previousY, int tick) {
        helper.runAfterDelay(1, () -> {
            var state = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat);
            require(state == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.LANDING
                            || state == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED,
                    "NPC must retain authoritative landing intent, got " + state);
            require(bat.getY() <= previousY + .03, "landing request must not be countermanded by cruise ascent");
            require(bat.getHealth() == health && com.juicyslew.moonstation14.ms14.damage.DamageSystem
                    .existingOrVanillaBaseline(bat).equals(damage), "powered landing causes no health or typed-ledger damage");
            if (bat.onGround() && state == com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED) {
                helper.succeed(); return;
            }
            if (tick >= 90) throw new GameTestAssertException("autonomous NPC did not reach ground after landing request; y=" + bat.getY());
            observeAutonomousLanding(helper, bat, origin, health, damage, bat.getY(), tick + 1);
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1200)
    public static void idleFlightLegsRetainHoverHeightWithoutUpwardRebase(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, 9, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var bat = createBat(level, origin);
        require(bat != null, "normal addFreshEntity join resolves bat prototypes");
        var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElseThrow();
        var flight = resolved.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        int deadline = (int) Math.ceil(flight.npcGroundedTicks() * 1.25 + flight.npcAirborneTicks() * 1.25 + 120);
        observeAirGoals(helper, bat, origin, deadline, null, 0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, 0);
    }

    private static void observeAirGoals(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.AnimalMob bat, BlockPos origin, int tick,
            net.minecraft.world.phys.Vec3 previousGoal, int goals, double minHeight, double maxHeight, int observedTick, int travelSamples) {
        helper.runAfterDelay(1, () -> {
            var snapshot = NpcBehaviorSystem.debugSnapshot(bat);
            long now = helper.getLevel().getGameTime();
            double height = bat.getY() - origin.getY();
            boolean hovering = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                    != com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED;
            var goal = snapshot == null ? null : snapshot.idlePoint();
            int reachedGoals = goals + (hovering && height >= 2.75 && goal != null && !goal.equals(previousGoal) ? 1 : 0);
            double min = hovering && height >= 2.75 ? Math.min(minHeight, height) : minHeight;
            double max = hovering ? Math.max(maxHeight, height) : maxHeight;
            int travel = hovering && height >= 2.75 ? travelSamples + 1 : travelSamples;
            if (observedTick + 1 < tick && (reachedGoals < 2 || travel < 20)) {
                observeAirGoals(helper, bat, origin, tick, goal, reachedGoals, min, max, observedTick + 1, travel);
                return;
            }
            require(reachedGoals >= 2 && travel >= 20, flightFailure(bat, snapshot, "expected multiple airborne destinations and sustained cruise", reachedGoals, observedTick));
            require(min >= 2.5 && max <= 5.5, flightFailure(bat, snapshot, "altitude must not rebase upward across airborne destinations", reachedGoals, observedTick) + "; min=" + min + ", max=" + max);
            helper.succeed();
        });
    }

    private static String flightFailure(com.juicyslew.moonstation14.entities.AnimalMob bat,
            NpcBehaviorSystem.DebugSnapshot snapshot, String reason, int legs, int tick) {
        return reason + "; tick=" + tick + ", legs=" + legs + ", pos=" + bat.position()
                + ", flightState=" + com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                + ", task=" + (snapshot == null ? null : snapshot.task())
                + ", routeResult=" + (snapshot == null ? null : snapshot.routeResult())
                + ", idlePoint=" + (snapshot == null ? null : snapshot.idlePoint())
                + ", idleUntil=" + (snapshot == null ? null : snapshot.idleUntil());
    }

    private static void observeNormalJoin(GameTestHelper helper,
            com.juicyslew.moonstation14.entities.AnimalMob bat, BlockPos origin, int tick,
            int groundedTicks, int airborneTicks, double initialY, double minElevatedY, double maxY, double initialX, double initialZ,
            int elevated, int horizontalSamples, int streak, int maxStreak) {
        helper.runAfterDelay(1, () -> {
            var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(bat).orElse(null);
            var flight = resolved == null ? null : resolved.component(
                    com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElse(null);
            var npc = resolved == null ? null : resolved.component(
                    com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.class).orElse(null);
            double y = bat.getY();
            boolean airborne = flight != null && com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat)
                    != com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED;
            boolean aboveFloor = airborne && y >= initialY + 2.75;
            int elevatedNow = elevated + (aboveFloor ? 1 : 0);
            int streakNow = aboveFloor ? streak + 1 : 0;
            int maxStreakNow = Math.max(maxStreak, streakNow);
            int horizontalNow = horizontalSamples +
                    (Math.hypot(bat.getX() - initialX, bat.getZ() - initialZ) > .5 ? 1 : 0);
            double minY = elevatedNow > 0 ? Math.min(minElevatedY, y) : minElevatedY;
            double max = Math.max(maxY, y);
            if (bat.getBoundingBox().maxY > origin.getY() + 8.001)
                failFlight(bat, initialY, max, tick, elevatedNow, horizontalNow, "crossed loaded ceiling");
            if (tick + 1 < groundedTicks + airborneTicks + 100) {
                observeNormalJoin(helper, bat, origin, tick + 1, groundedTicks, airborneTicks, initialY, minY, max,
                        initialX, initialZ, elevatedNow, horizontalNow, streakNow, maxStreakNow);
                return;
            }
            if (resolved == null || flight == null || npc == null || elevatedNow < 20 || maxStreakNow < 5 || horizontalNow < 1)
                throw new GameTestAssertException("normal-join NPC flight evidence failed; tick=" + tick
                        + ", initialY=" + initialY + ", y=" + y + ", minElevatedY=" + minY
                        + ", maxY=" + max + ", elevatedSamples=" + elevatedNow + ", maxElevatedStreak=" + maxStreakNow
                        + ", horizontalSamples=" + horizontalNow + ", flightState="
                        + com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(bat));
            helper.succeed();
        });
    }

@GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 900)
    public static void resolvedAutonomousFlyerAscendsAndRespectsLoadedCeiling(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, 4, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var flyer = createBat(level, origin);
        require(flyer != null, "shipped bat provides resolved Flight + NPC host");
        flyer.setNoAi(true);
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(flyer, level);
        require(com.juicyslew.moonstation14.ms14.organ.BodySystem.reconcile(flyer), "NPC flyer body reconciled");
        double startY = flyer.getY();
        var flight = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(flyer).orElseThrow()
                .component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).orElseThrow();
        int observationTicks = (int) Math.ceil(flight.npcGroundedTicks() * 1.25
                + flight.npcAirborneTicks() * 1.25 + 100);
        require(observationTicks + 50 <= 900, "resolved observation window plus scheduling margin fits fixture timeout: " + observationTicks);
        observeFlight(helper, flyer, origin, startY, 0, 0, 0, startY, observationTicks);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void cruiseHeightUsesFractionalSupportCollisionTop(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(origin.below(), Blocks.STONE_SLAB.defaultBlockState(), 3);
        var bat = createBat(level, origin);
        require(bat != null, "resolved bat NPC exists");
        bat.setNoAi(true);
        bat.setPos(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5);
        double target = NpcBehaviorSystem.safeCruiseY(bat, 3);
        double supportTop = origin.getY() - 0.5; // Bottom slab occupies origin.below().
        require(Math.abs(target - (supportTop + 3)) < .08,
                "cruise target is requested height above slab collision top; target=" + target);
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void cruiseHeightClampsBelowFractionalCeiling(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        // This slab lies between the flyer and its requested endpoint, while the endpoint itself is clear.
        BlockPos obstacle = origin.above(2);
        level.setBlock(obstacle, Blocks.STONE_SLAB.defaultBlockState(), 3);
        var bat = createBat(level, origin);
        require(bat != null, "resolved bat NPC exists");
        bat.setNoAi(true);
        double unconstrainedTarget = origin.getY() + 5;
        double target = NpcBehaviorSystem.safeCruiseY(bat, 5);
        double obstacleTop = obstacle.getY() + 0.5;
        var unconstrainedBody = bat.getBoundingBox().move(0, unconstrainedTarget - bat.getY(), 0);
        require(level.noCollision(bat, unconstrainedBody), "unconstrained requested endpoint body is collision-free");
        require(unconstrainedTarget > obstacleTop + 0.05 && obstacleTop > bat.getY(),
                "unconstrained cruise target must be beyond the intervening slab");
        require(target + bat.getBbHeight() <= obstacle.getY() - 0.05,
                "safe cruise target stops below obstacle with body clearance; target=" + target
                        + " obstacleBottom=" + obstacle.getY() + " height=" + bat.getBbHeight());
        helper.succeed();
    }

    private static void observeFlight(GameTestHelper helper, com.juicyslew.moonstation14.entities.AnimalMob flyer,
                                      BlockPos origin, double startY, int ticks, int elevatedFlightSamples,
                                      int upwardSamples, double maxY, int observationTicks) {
        helper.runAfterDelay(1, () -> {
            double y = flyer.getY();
            double observedMax = Math.max(maxY, y);
            var flightState = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(flyer);
            boolean elevatedNaturalFlight = flightState != com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED
                    && y >= startY + .6;
            int elevated = elevatedFlightSamples + (elevatedNaturalFlight ? 1 : 0);
            int upward = upwardSamples + (flyer.getDeltaMovement().y > .04 ? 1 : 0);
            var body = flyer.getBoundingBox();
            // The finite roof occupies these block volumes. Touching a boundary (within
            // epsilon) is not penetration; routing around the footprint is valid.
            double epsilon = .001;
            double roofMinX = origin.getX() - 5, roofMaxX = origin.getX() + 6;
            double roofMinY = origin.getY() + 4, roofMaxY = origin.getY() + 5;
            double roofMinZ = origin.getZ() - 5, roofMaxZ = origin.getZ() + 6;
            boolean intersectsRoof = body.maxX > roofMinX + epsilon && body.minX < roofMaxX - epsilon
                    && body.maxY > roofMinY + epsilon && body.minY < roofMaxY - epsilon
                    && body.maxZ > roofMinZ + epsilon && body.minZ < roofMaxZ - epsilon;
            if (intersectsRoof)
                throw new GameTestAssertException("intersected loaded roof; phase=tick " + ticks
                        + ", pos=" + flyer.position() + ", bodyAABB=" + body
                        + ", roofAABB=[(" + roofMinX + "," + roofMinY + "," + roofMinZ + ")..("
                        + roofMaxX + "," + roofMaxY + "," + roofMaxZ + ")], startY=" + startY
                        + ", maxY=" + observedMax + ", elevatedFlightSamples=" + elevated
                        + ", upwardSamples=" + upward + ", flightState="
                        + com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(flyer)
                        + ", motor=" + flyer.getDeltaMovement());
            if (ticks + 1 < observationTicks) {
                observeFlight(helper, flyer, origin, startY, ticks + 1, elevated, upward, observedMax, observationTicks);
                return;
            }
            if (!flyer.isAlive()) failFlight(flyer, startY, observedMax, 100, elevated, upward, "died");
            // Idle goals are selected uniformly, including nearby goals that can be reached and landed at
            // before this observation ends. Require natural elevated flight and motor ascent, not a fixed
            // number of cruise samples; sustained active-goal steering is covered by the push regression.
            if (elevated < 1 || upward < 2)
                failFlight(flyer, startY, observedMax, observationTicks, elevated, upward, "no autonomous ascent/elevated flight");
            helper.succeed();
        });
    }

    private static void failFlight(com.juicyslew.moonstation14.entities.AnimalMob flyer, double startY,
                                   double maxY, int ticks, int elevated, int upward, String reason) {
        throw new GameTestAssertException(reason + "; startY=" + startY + ", y=" + flyer.getY()
                + ", maxY=" + maxY + ", ticks=" + ticks + ", elevatedFlightSamples=" + elevated
                + ", upwardSamples=" + upward + ", flightState="
                + com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(flyer)
                + ", alive=" + flyer.isAlive() + ", motor=" + flyer.getDeltaMovement());
    }

    private static com.juicyslew.moonstation14.entities.AnimalMob createBat(
            net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        var mob = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("bat").get().create(level);
        if (mob == null) return null;
        mob.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        if (!level.addFreshEntity(mob)) { mob.discard(); return null; }
        var resolved = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        if (resolved != null && resolved.component(com.juicyslew.moonstation14.ms14.character.components.FlightComponent.class).isPresent()
                && resolved.component(com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.class).isPresent())
            return mob;
        mob.discard();
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
