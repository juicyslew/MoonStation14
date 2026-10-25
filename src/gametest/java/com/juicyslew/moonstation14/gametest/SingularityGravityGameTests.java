package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native entity adapters for the shared singularity gravity kernel. */
@PrefixGameTestTemplate(false)
@GameTestTags({"singularity-gravity"})
public final class SingularityGravityGameTests {
    private SingularityGravityGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", batch="singularity_gravity", timeoutTicks=100)
    public static void nativeLivingTravelAppliesSharedGravityOnce(GameTestHelper h) {
        var level = h.getLevel();
        Villager actor = h.spawn(EntityType.VILLAGER, new BlockPos(3, 4, 3));
        actor.setNoAi(true);
        actor.setDeltaMovement(Vec3.ZERO);
        SingularityEntity source = createSource(h, new Vec3(actor.getX() + 2, actor.getY() + 1, actor.getZ()));
        awaitPulse(h, source, actor, 60, () -> {
            Vec3 sharedDeltaVelocity = com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                    .deltaVelocity(level, actor.position(), level.getGameTime());
            require(sharedDeltaVelocity.distanceTo(new Vec3(1.5, .75, 0)) < 1.0e-5,
                    "fixed level-one fixture kernel delta-velocity is 1.5,.75 blocks/second: " + sharedDeltaVelocity);
            Vec3 expected = new Vec3(.075, .0375, 0);
            Vec3 beforeVelocity = actor.getDeltaMovement();
            actor.setDeltaMovement(Vec3.ZERO);
            Vec3 before = actor.position();
            // Keep vanilla AI suppressed while waiting for the pulse; enable native travel
            // admission only for this direct call (no AI tick occurs between these statements).
            actor.setNoAi(false);
            require(actor.isEffectiveAi(), "native travel fixture actor is AI-effective for direct travel call");
            actor.travel(Vec3.ZERO);
            double movedX = actor.getX() - before.x;
            require(Math.abs(movedX - expected.x) < 1.0e-5,
                    "native travel applies exactly one current shared-pulse horizontal delta; expected=" + expected + " movedX=" + movedX
                            + " pos=" + before + "->" + actor.position() + " velocity=" + beforeVelocity + "->" + actor.getDeltaMovement()
                            + " pulse=" + com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                                    .deltaVelocity(level, before, level.getGameTime())
                            + " source=" + source.position() + " sourceLevel=" + source.level().dimension()
                            + " time=" + level.getGameTime() + " ai=" + actor.isNoAi() + "/" + actor.isEffectiveAi()
                            + " physics=" + actor.noPhysics + " passenger=" + actor.isPassenger()
                            + " dead=" + actor.isDeadOrDying() + " spectator=" + actor.isSpectator()
                            + " movementOwned=" + (actor instanceof MindControlledMob owned && owned.moonstation14$isMovementOwned())
                            + " corpseEligible=" + com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(actor)
                            + " medical(alive/critical/dead/incap)="
                            + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(actor) + "/"
                            + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(actor) + "/"
                            + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(actor) + "/"
                            + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(actor));
            source.discard();
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", batch="singularity_gravity", timeoutTicks=100)
    public static void movementOwnedMobIsNotDoubleIntegratedByNativeTravel(GameTestHelper h) {
        var level = h.getLevel();
        Villager actor = h.spawn(EntityType.VILLAGER, new BlockPos(3, 4, 3));
        require(BodySystem.reconcile(actor), "mapped native body has resolved character identity");
        actor.getPersistentData().putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        GroundedHarnessLease lease = GroundedHarnessLease.tryAcquire(actor).orElseThrow(
                () -> new GameTestAssertException("configured native Villager acquires real grounded movement lease"));
        watchLease(h, lease);
        SingularityEntity source = createSource(h, new Vec3(actor.getX() + 2, actor.getY() + 1, actor.getZ()));
        awaitPulse(h, source, actor, 60, () -> {
            Vec3 pulse = com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                    .deltaVelocity(level, actor.position(), level.getGameTime());
            require(pulse.x > 0, "owned motor test begins on a live rightward shared gravity pulse: " + pulse);
            var motor = new GroundedHarnessWorldStep();
            Vec3 beforeMotor = actor.position();
            var accepted = motor.step(actor, 0, 0, false, false, 0f);
            require(accepted.isPresent(), "real grounded harness motor accepts the owned body's server step");
            Vec3 afterMotor = actor.position();
            Vec3 motorVelocity = actor.getDeltaMovement();
            require(afterMotor.x > beforeMotor.x && motorVelocity.lengthSqr() > 1.0e-12,
                    "owned motor consumes the shared gravity pulse during this tick");
            actor.travel(Vec3.ZERO);
            require(actor.position().equals(afterMotor) && actor.getDeltaMovement().equals(motorVelocity),
                    "native travel cancellation leaves the one motor result unchanged; no second gravity delta");
            source.discard();
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", batch="singularity_gravity", timeoutTicks=100)
    public static void liveLooseItemGravityWakesSettledItem(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos floor = h.absolutePos(new BlockPos(3, 3, 3));
        LooseItemEntity item = LooseItemEntity.create(level, floor.above().getCenter(), new ItemStack(Items.STICK));
        watch(h, item);
        require(level.addFreshEntity(item), "exact spawned world-item UUID is live in the level");
        for (int i = 0; i < 30 && !item.settled(); i++) item.tick();
        require(item.settled(), "loose item reaches its ordinary settled state before source arrives");
        SingularityEntity source = createSource(h, item.position().add(2, 1, 0));
        LooseItemEntity detached = LooseItemEntity.create(level, item.position(), new ItemStack(Items.STICK));
        watch(h, detached);
        awaitPulse(h, source, item, 60, () -> {
            require(level.getEntity(item.getUUID()) == item && !item.isRemoved(),
                    "gravity target remains the exact authoritative loose-item entity");
            require(level.getEntity(detached.getId()) != detached,
                    "same-level unregistered item is not an authoritative world-item UUID");
            double detachedX = detached.getX();
            detached.tick(); // Ordinary vertical gravity may occur; isolate horizontal singularity response.
            require(Math.abs(detached.getX() - detachedX) < 1.0e-9,
                    "detached presentation item receives no singularity horizontal impulse");
            item.tick();
            require(!item.settled() && item.getDeltaMovement().x > 0,
                    "gravity routes through applyPullVelocityChange and wakes settled item");
            item.discard(); source.discard(); h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", batch="singularity_gravity", timeoutTicks=240)
    @GameTestTags({"pig-repro", "nativegravity"})
    public static void nativePigAtEightBlockRangeIsConsumed(GameTestHelper h) {
        runNativePigCase(h, new BlockPos(3, 4, 3), new Vec3(7.5, 0, 0), "horizontal");
    }

    @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", batch="singularity_gravity", timeoutTicks=240)
    @GameTestTags({"pig-repro", "nativegravity"})
    public static void nativePigFallingAboveSourceIsConsumed(GameTestHelper h) {
        runNativePigCase(h, new BlockPos(3, 11, 3), new Vec3(0, -7, 0), "above");
    }

    @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", batch="singularity_gravity", timeoutTicks=80)
    @GameTestTags({"pig-repro", "nativegravity"})
    public static void nativePigAlreadyInsideHorizonIsConsumed(GameTestHelper h) {
        runNativePigCase(h, new BlockPos(3, 4, 3), new Vec3(1, 0, 0), "inside");
    }

    @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", batch="pig_dormant_gravity", timeoutTicks=800)
    @GameTestTags({"dormant-gravity-pulse"})
    public static void quietSourceDoesNotAccumulateDormantGravityPulses(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 2; x <= 24; x++) for (int z = 2; z <= 4; z++)
            level.setBlock(h.absolutePos(new BlockPos(x, 3, z)), Blocks.STONE.defaultBlockState(), 3);
        BlockPos expectedPig = new BlockPos(3, 4, 3);
        Vec3 sourcePosition = h.absolutePos(new BlockPos(7, 4, 3)).getCenter().add(0, -.5, 0);
        SingularityEntity source = createSource(h, sourcePosition);
        source.feed(800); // 180 startup + 800 feed = level 3 with finite energy below level 4.
        require(source.singularityLevel() == 3, "quiet-source fixture begins at level three");
        var stableSource = source.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        stableSource.putInt("WalkTimer", 1000);
        source.load(stableSource);
        source.setDeltaMovement(Vec3.ZERO);
        long quietStart = level.getGameTime();
        watch(h, source);
        // No gravity consumer exists during this idle reactor interval.
        h.runAfterDelay(600, () -> {
            long now = level.getGameTime();
            double dormantElapsed = .05;
            Pig pig = h.spawn(EntityType.PIG, expectedPig);
            watch(h, pig);
            require(pig.position().distanceTo(source.position()) > 2.5 && pig.position().distanceTo(source.position()) <= 8,
                    "ordinary AI pig begins outside level-3 horizon but inside gravity range");
            System.out.println("[pig-dormant] spawned time=" + now + " quietTicks=" + (now - quietStart)
                    + " continuousElapsed=" + dormantElapsed
                    + " pig=" + pig.position() + " source=" + source.position() + " level=" + source.singularityLevel()
                    + " energy=" + source.energy());
            traceDormantPig(h, source, pig, now, 0, pig.position(), 0, 0, pig.position().distanceTo(source.position()),
                    dormantElapsed, false);
        });
    }

    private static void traceDormantPig(GameTestHelper h, SingularityEntity source, Pig pig, long spawnTime, int ticks,
                                        Vec3 previous, double maxStep, double maxSpeed, double minDistance,
                                        double dormantElapsed, boolean sawNativePullMotion) {
        Vec3 step = pig.position().subtract(previous);
        double currentStep = step.length();
        maxStep = Math.max(maxStep, currentStep);
        maxSpeed = Math.max(maxSpeed, pig.getDeltaMovement().length());
        double distance = pig.position().distanceTo(source.position());
        Vec3 pulse = SingularityGravityPulse.deltaVelocity(h.getLevel(), pig.position(), h.getLevel().getGameTime());
        sawNativePullMotion |= pulse.lengthSqr() > 1.0e-12 && step.dot(pulse) > 1.0e-6;
        System.out.println("[pig-dormant] tick=" + ticks + " time=" + h.getLevel().getGameTime() + " pos=" + pig.position()
                + " step=" + currentStep + " maxStep=" + maxStep + " velocity=" + pig.getDeltaMovement()
                + " maxSpeed=" + maxSpeed + " distance=" + distance + " horizonTouch=" +
                com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry.touches(source.position(), 2.5, pig.getBoundingBox())
                + " dead=" + pig.isDeadOrDying() + " sourceEnergy=" + source.energy() + " sourceLevel=" + source.singularityLevel());
        if (pig.isDeadOrDying() || pig.isRemoved()) {
            System.out.println("[pig-dormant] accepted-death time=" + h.getLevel().getGameTime() + " pos=" + pig.position()
                    + " distance=" + distance + " minDistance=" + minDistance + " maxStep=" + maxStep
                    + " maxSpeed=" + maxSpeed + " horizonTouch=" +
                    com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry.touches(source.position(), 2.5, pig.getBoundingBox())
                    + " pulse=" + pulse + " sourceLevel=" + source.singularityLevel() + " sourceEnergy=" + source.energy()
                    + " dormantElapsed=" + dormantElapsed + " spawnTime=" + spawnTime);
            require(pig.isDeadOrDying() && pig instanceof com.juicyslew.moonstation14.mixin.LivingEntityDeathStateAccessor death
                            && death.ms14$isDead(), "overdue-source reproduction pig reaches accepted native death");
            require(com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry
                            .touches(source.position(), 2.5, pig.getBoundingBox()),
                    "pig is accepted dead while intersecting the resolved level-3 horizon");
            require(sawNativePullMotion && minDistance < 4 && maxStep < 5,
                    "nominal source pulses move pig inward without skipping its horizon; minDistance=" + minDistance
                            + " maxStep=" + maxStep + " maxSpeed=" + maxSpeed);
            h.succeed(); return;
        }
        Vec3 nextPrevious = pig.position();
        double observedMaxStep = maxStep, observedMaxSpeed = maxSpeed, observedMinDistance = Math.min(minDistance, distance);
        boolean observedNativePull = sawNativePullMotion;
        h.runAfterDelay(1, () -> traceDormantPig(h, source, pig, spawnTime, ticks + 1, nextPrevious,
                observedMaxStep, observedMaxSpeed, observedMinDistance, dormantElapsed, observedNativePull));
    }

    private static void runNativePigCase(GameTestHelper h, BlockPos pigSpawn, Vec3 sourcePosition, String scenario) {
        var level = h.getLevel();
        // Keep the naturally wandering pig supported throughout the horizontal approach corridor.
        for (int x = 2; x <= 12; x++) for (int z = 2; z <= 4; z++)
            level.setBlock(h.absolutePos(new BlockPos(x, 3, z)), Blocks.STONE.defaultBlockState(), 3);
        Pig pig = h.spawn(EntityType.PIG, pigSpawn);
        SingularityEntity source = createSource(h, pig.position().add(sourcePosition));
        source.feed(500);
        require(source.singularityLevel() == 3, "fixture source resolves to level three");
        // Freeze only this fixture's random source walk; production free 3D motion is unchanged.
        var stableSource = source.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        stableSource.putInt("WalkTimer", 1000);
        source.load(stableSource);
        source.setDeltaMovement(Vec3.ZERO);
        require(com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse.sourceDeltaVelocity(
                        source.position(), pig.position(), source.singularityLevel(), source.gravityProfile(), .5).lengthSqr() > 0,
                scenario + " pig starts within resolved level-3 gravity range");
        watch(h, pig);
        watch(h, source);
        double initialDistance = pig.position().distanceTo(source.position());
        tracePig(h, source, pig, scenario, 0, initialDistance, initialDistance, 0, 0, source.energy(), false);
    }

    private static void tracePig(GameTestHelper h, SingularityEntity source, Pig pig, String scenario, int ticks,
                                 double initialDistance, double minDistance, double maxSpeed, double maxStep,
                                 double startingEnergy, boolean sawNativePullMotion) {
        Vec3 pulse = com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                .deltaVelocity(h.getLevel(), pig.position(), h.getLevel().getGameTime());
        Vec3 stepVector = pig.position().subtract(new Vec3(pig.xo, pig.yo, pig.zo));
        boolean pullMotion = pulse.lengthSqr() > 1.0e-12 && stepVector.dot(pulse) > 1.0e-6;
        sawNativePullMotion |= pullMotion;
        if (pig.isDeadOrDying() || pig.isRemoved()) {
            System.out.println("[pig-gravity] scenario=" + scenario + " accepted-death gameTime=" + h.getLevel().getGameTime()
                    + " pos=" + pig.position() + " velocity=" + pig.getDeltaMovement() + " maxSpeed=" + maxSpeed
                    + " maxStep=" + maxStep + " initialDistance=" + initialDistance + " minDistance=" + minDistance
                    + " horizonRadius=" + Math.max(0, source.singularityLevel() - .5)
                    + " touching=" + com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry
                            .touches(source.position(), Math.max(0, source.singularityLevel() - .5), pig.getBoundingBox())
                    + " sourceLevel=" + source.singularityLevel() + " sourceEnergy=" + source.energy()
                    + " startEnergy=" + startingEnergy + " pulse=" + pulse);
            require(pig.isDeadOrDying() && pig instanceof com.juicyslew.moonstation14.mixin.LivingEntityDeathStateAccessor death
                            && death.ms14$isDead(), scenario + " pig must reach accepted native death");
            if (!scenario.equals("inside")) require(minDistance < initialDistance - .1,
                    scenario + " native pig trajectory must demonstrably approach the source; initial=" + initialDistance
                            + " min=" + minDistance + " maxSpeed=" + maxSpeed);
            if (!scenario.equals("inside")) require(sawNativePullMotion,
                    scenario + " native pig must show displacement aligned with a live source gravity pulse");
            source.discard(); h.succeed(); return;
        }
        if (ticks >= 160) {
            if (scenario.equals("horizontal")) {
                require(!pig.isDeadOrDying() && sawNativePullMotion && maxStep < 5,
                        "normal-AI horizontal pig receives bounded source pull without skipping the horizon; maxStep="
                                + maxStep + " maxSpeed=" + maxSpeed + " minDistance=" + minDistance);
                System.out.println("[pig-gravity] scenario=horizontal bounded-survivor gameTime=" + h.getLevel().getGameTime()
                        + " pos=" + pig.position() + " maxSpeed=" + maxSpeed + " maxStep=" + maxStep
                        + " initialDistance=" + initialDistance + " minDistance=" + minDistance
                        + " sourceLevel=" + source.singularityLevel() + " sourceEnergy=" + source.energy());
                source.discard(); h.succeed(); return;
            }
            throw new GameTestAssertException(scenario + " pig survived bounded horizon approach; ticks=" + ticks
                    + " pos=" + pig.position() + " velocity=" + pig.getDeltaMovement() + " speed=" + pig.getDeltaMovement().length()
                    + " health=" + pig.getHealth() + " dead=" + pig.isDeadOrDying() + " maxSpeed=" + maxSpeed + " maxStep=" + maxStep
                    + " initialDistance=" + initialDistance + " minDistance=" + minDistance + " source=" + source.position()
                    + " level=" + source.singularityLevel() + " energy=" + source.energy() + " startEnergy=" + startingEnergy
                    + " time=" + h.getLevel().getGameTime());
        }
        if (ticks % 10 == 0) System.out.println("[pig-gravity] scenario=" + scenario + " tick=" + ticks + " gameTime=" + h.getLevel().getGameTime()
                + " pos=" + pig.position() + " vel=" + pig.getDeltaMovement() + " speed=" + pig.getDeltaMovement().length()
                + " pulse=" + pulse + " health=" + pig.getHealth()
                + " dead=" + pig.isDeadOrDying() + " dist=" + pig.position().distanceTo(source.position())
                + " source=" + source.position() + " level=" + source.singularityLevel());
        double distance = pig.position().distanceTo(source.position());
        double step = pig.position().distanceTo(new Vec3(pig.xo, pig.yo, pig.zo));
        double max = Math.max(maxSpeed, pig.getDeltaMovement().length());
        boolean observedPullMotion = sawNativePullMotion;
        h.runAfterDelay(1, () -> tracePig(h, source, pig, scenario, ticks + 1, initialDistance,
                Math.min(minDistance, distance), max, Math.max(maxStep, step), startingEnergy, observedPullMotion));
    }

    private static void awaitPulse(GameTestHelper h, SingularityEntity source,
                                   net.minecraft.world.entity.Entity target, int remaining,
                                   Runnable action) {
        var level = h.getLevel();
        Vec3 pulse = com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                .deltaVelocity(level, target.position(), level.getGameTime());
        if (pulse.lengthSqr() > 1.0e-12) {
            action.run();
            return;
        }
        if (remaining <= 0) {
            throw new GameTestAssertException("no live shared pulse before bounded deadline; time=" + level.getGameTime()
                    + " target=" + target.position() + " source=" + source.position() + " pulse=" + pulse);
        }
        h.runAfterDelay(1, () -> awaitPulse(h, source, target, remaining - 1, action));
    }

    private static SingularityEntity createSource(GameTestHelper h, Vec3 position) {
        SingularityEntity source = ModEntities.SINGULARITY.get().create(h.getLevel());
        require(source != null, "registered singularity entity created");
        watch(h, source);
        source.setPos(position);
        require(h.getLevel().addFreshEntity(source), "singularity source spawned");
        source.tick(); // initialize from its resolved gameplay profile and finite startup energy
        return source;
    }

    private static void watch(GameTestHelper helper, net.minecraft.world.entity.Entity entity) {
        helper.testInfo.addListener(new GameTestListener() {
            private void clean() { entity.discard(); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { clean(); }
        });
    }

    private static void watchLease(GameTestHelper helper, GroundedHarnessLease lease) {
        helper.testInfo.addListener(new GameTestListener() {
            private void clean() { lease.close(); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { clean(); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
