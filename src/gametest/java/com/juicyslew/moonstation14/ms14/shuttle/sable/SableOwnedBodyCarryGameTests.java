package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleManualFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.AirMotionReference;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import com.juicyslew.moonstation14.ms14.player_body_control.client.ClientFramePredictionState;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.Mob;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import org.joml.Vector3d;
import org.joml.Quaterniond;

import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.ArrayDeque;

/** Native pose/body continuity checks through the production human and corpse movement owners. */
@GameTestTags({"shuttle-body-carry", "shuttle"})
@PrefixGameTestTemplate(false)
public final class SableOwnedBodyCarryGameTests {
    static final int MICRO_SCENE_TICKS = 40;
    static final int MICRO_MASS_TICKS = 6;
    static final int MICRO_SUPPORT_TICKS = 28;
    static final int MICRO_INITIAL_HOVER_TICKS = 128;
    static final int MICRO_POST_SUPPORT_HOVER_TICKS = 64;
    static final int MICRO_SAMPLE_TICKS = 1;
    static final int MICRO_SERVICE_TICKS = 240;
    static final int MICRO_TEST_DEADLINE_TICKS = 300;
    static final int MICRO_STABLE_SAMPLES = 3;
    static final double MICRO_HOVER_REST_MOTION_BUDGET = 2.5e-5;
    private static final int NATIVE_SCENE_READY_TICKS = 100;
    private static final int SLOW_NATIVE_SCENE_READY_TICKS = SableFixtureScene.READINESS_TICKS;
    private static final int DECK_SIZE = 6;
    private static final BlockPos REGISTERED_CARGO_DEPARTURE_ORIGIN = new BlockPos(2000, 300, 3300);
    static final BlockPos ROTATING_PIG_IMPACT_ORIGIN = new BlockPos(2200, 300, 3300);
    private static final double STRONG_CARGO_IMPULSE_SPEED = 20.0;
    private static final int STRONG_CARGO_IMPULSE_TICKS = 60;
    static final BlockPos STRONG_CARGO_ORIGIN = new BlockPos(1500, 300, 4000);
    static final BlockPos TRANSLATING_PIG_IMPACT_ORIGIN = new BlockPos(1560, 300, 640);
    private static final double MIN_YAW_QUERY_ROTATION = .02;
    private SableOwnedBodyCarryGameTests() { }

    @GameTestTags({"shuttle-cargo-motion", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void looseMoveObserverCapturesOwnMoveBeforeNativeCargoCarry(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1200, 300, 640), 8, (level, plot, storage) -> {
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BEAKER_LARGE.get());
            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
            long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(12.5f);
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                    com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water, cents)));
            RegisteredCargo root = registerCargoRoot(level, deckFeet(plot, storage, 2, 2), stack, "shuttle-cargo-");
            new CargoMoveProbe(helper, level, plot, root.entity(), root.token(), root.world(), water, cents).register();
        });
    }

    @GameTestTags({"shuttle-cargo-motion", "shuttle", "jug", "shuttle-feedback08-failure-followup"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void nativeCarrierDisplacementDoesNotQualifyJugOwnImpact(GameTestHelper helper) {
        withDeck(helper, new BlockPos(2200, 300, 3000), 8, (level, plot, storage) -> {
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
            long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(5f);
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                    com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water, cents)));
            RegisteredCargo root = registerCargoRoot(level, deckFeet(plot, storage, 2, 2), stack, "shuttle-jug-");
            new JugOwnImpactProbe(helper, level, plot, root.entity(), root.token(), root.world(), water, cents).register();
        });
    }

    @GameTestTags({"shuttle-cargo-support", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void settledCargoTracksNativeSupportAndWakesWhenDeckSupportIsRemoved(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1280, 300, 640), 8, (level, plot, storage) -> {
            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
            long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(12.5f);
            var reagent = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water, cents));
            var jugStack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            var beakerStack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BEAKER_LARGE.get());
            jugStack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), reagent);
            beakerStack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), reagent);
            RegisteredCargo jug = registerCargoRoot(level, deckFeet(plot, storage, 2, 2), jugStack, "shuttle-support-jug-");
            RegisteredCargo beaker = registerCargoRoot(level, deckFeetAt(plot, storage, 4.5, 4.5), beakerStack,
                    "shuttle-support-beaker-");
            new CargoSupportMonitor(helper, level, plot, storage, jug, beaker, water, cents).register();
        });
    }

    private static void assertNativeSolvedGeometry(ServerLevel level, SableFixturePlot plot, BlockPos storage,
                                                   CargoSupportMonitor.CargoSupportSample sample, RegisteredCargo cargo, int tick) {
        var observation = sample.capture().preCutoff();
        require(observation != null && observation.frameId() != null && observation.frameId().equals(plot.engineId())
                        && com.juicyslew.moonstation14.entities.LooseItemEntity.NativePreCutoff
                        .matches(observation, sample.capture().currentCollisionInfo()),
                "strong-impulse native solved geometry has matching known frame observation");
        var solved = observation.solvedBox();
        require(solved != null && observation.logicalPose() != null,
                "strong-impulse native solved box and copied logical pose are present");
        var obb = new dev.ryanhcode.sable.api.math.OrientedBoundingBox3d(
                new Vector3d(solved.x(), solved.y(), solved.z()),
                new Vector3d(solved.width(), solved.height(), solved.depth()),
                new org.joml.Quaterniond(solved.qx(), solved.qy(), solved.qz(), solved.qw()),
                new dev.ryanhcode.sable.api.math.LevelReusedVectors());
        var vertices = new Vector3d[8];
        for (int i = 0; i < vertices.length; i++) vertices[i] = new Vector3d();
        double minFloorY = Double.POSITIVE_INFINITY, maxFloorY = Double.NEGATIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (var vertex : obb.vertices(vertices)) {
            var local = observation.logicalPose().transformPositionInverse(vertex);
            minFloorY = Math.min(minFloorY, local.y); maxFloorY = Math.max(maxFloorY, local.y);
            minX = Math.min(minX, local.x); maxX = Math.max(maxX, local.x);
            minZ = Math.min(minZ, local.z); maxZ = Math.max(maxZ, local.z);
        }
        double deckTop = storage.getY() + 1.0;
        double allowance = NativeCollisionSlop.MAX_RESIDUAL + Math.ulp(deckTop) * 4;
        Vector3d deckUp = new Vector3d();
        observation.logicalPose().transformNormal(new Vector3d(0, 1, 0), deckUp).normalize();
        Vec3 solvedFeet = new Vec3(solved.x(), solved.y() - solved.height() * .5, solved.z());
        Vec3 rootDelta = sample.position().subtract(solvedFeet);
        Vec3 rawCarrier = observation.inheritedMotion();
        require(sample.capture().currentCollisionInfo() instanceof SubLevelEntityCollision.CollisionInfo,
                "native collision info has the exact Sable CollisionInfo type");
        SubLevelEntityCollision.CollisionInfo info =
                (SubLevelEntityCollision.CollisionInfo) sample.capture().currentCollisionInfo();
        Vec3 nativeMotion = info.motion;
        Vec3 acceptedOwn = sample.capture().acceptedOwnDelta();
        Vec3 inherited = info.inheritedMotion;
        boolean omittedCarrier = info != null && inherited == null && rawCarrier.lengthSqr() < 1.0e-8;
        boolean tinyNativeWriteSkipped = ownDeltaMatchesNativeMotion(acceptedOwn, nativeMotion);
        Vec3 expectedRootDelta = acceptedOwn.subtract(nativeMotion)
                .subtract(omittedCarrier ? rawCarrier : Vec3.ZERO);
        Vec3 rootError = rootDelta.subtract(expectedRootDelta);
        var trace = observation.nativeCorrectionTrace();
        var last = trace == null || trace.isEmpty() ? null : trace.get(trace.size() - 1);
        boolean knownLastFloor = last != null && last.iteration() == 3 && last.substeps() > 0
                && last.substep() == last.substeps() && plot.engineId().equals(last.frameId())
                && last.selectedPos() != null && last.selectedPos().getY() == storage.getY()
                && last.selectedPos().getX() >= storage.getX() && last.selectedPos().getX() < storage.getX() + DECK_SIZE
                && last.selectedPos().getZ() >= storage.getZ() && last.selectedPos().getZ() < storage.getZ() + DECK_SIZE
                && last.selectedState() != null
                && last.selectedState().equals(LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), last.selectedPos()));
        double selectedAlignment = last == null ? Double.NaN : new Vector3d(last.selectedX(), last.selectedY(), last.selectedZ()).normalize().dot(deckUp);
        double predictedRemaining = Double.NaN;
        if (knownLastFloor && Double.isFinite(selectedAlignment)
                && Math.abs(1.0 - selectedAlignment) <= NativeCollisionSlop.MAX_RESIDUAL) {
            predictedRemaining = new Vector3d(last.selectedX() - last.appliedX(),
                    last.selectedY() - last.appliedY(), last.selectedZ() - last.appliedZ()).dot(deckUp);
            if (!Double.isFinite(predictedRemaining) || predictedRemaining < 0) predictedRemaining = Double.NaN;
        }
        double observedRemaining = deckTop - minFloorY;
        double planeError = observedRemaining - predictedRemaining;
        boolean planeProof = Double.isFinite(predictedRemaining)
                && Math.abs(planeError) <= 1.0e-6 + Math.ulp(deckTop) * 4;
        boolean floorHeightValid = planeProof || Math.abs(observedRemaining) <= allowance;
        if (!floorHeightValid || rootError.length() > allowance)
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "strong-impulse geometry failure tick={} cargo={} minLocalY={} maxLocalY={} expectedFloor={} allowance={} deckUP={} lastMTV={} lastApplied={} predictedResidual={} observedRemaining={} planeError={} planeProof={} nativeRootDelta={} rootDifference={} smallSkip={} nativeMotion={} acceptedOwn={} rawCarrier={} infoInherited={} frame={} solvedFeet={} observation={}",
                    tick, cargo.token(), minFloorY, maxFloorY, deckTop, allowance, deckUp, last, predictedRemaining, observedRemaining, planeError, planeProof, rootDelta, rootError,
                    tinyNativeWriteSkipped, nativeMotion, acceptedOwn, rawCarrier, inherited,
                    plot.engineId(), solvedFeet, observation);
        require(floorHeightValid,
                "cargo floor height is strict or independently matches final SDK plane residual");
        require((tinyNativeWriteSkipped || acceptedOwn.distanceTo(nativeMotion) <= 1.0e-6)
                        && (!omittedCarrier || rawCarrier.lengthSqr() < 1.0e-8)
                        && rootError.length() <= allowance,
                "physical cargo root matches native solved feet accounting only verified tiny write/carrier skips");
        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(Math.nextDown(maxX));
        int z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(Math.nextDown(maxZ));
        require(x0 >= storage.getX() && x1 < storage.getX() + DECK_SIZE
                        && z0 >= storage.getZ() && z1 < storage.getZ() + DECK_SIZE,
                "solved cargo footprint falls within authored deck cells");
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            BlockPos cell = new BlockPos(x, storage.getY(), z);
            var state = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), cell);
            require(state != null && state.isSolid() && !state.getCollisionShape(level, cell).isEmpty()
                            && Math.abs(state.getCollisionShape(level, cell).max(net.minecraft.core.Direction.Axis.Y)
                            + cell.getY() - deckTop) <= 1.0e-6,
                    "projected cargo footprint observes authored solid deck top in loaded frame");
        }
    }

    /** The cargo own delta must be the native motion, except the vanilla tiny-motion write cutoff. */
    private static boolean ownDeltaMatchesNativeMotion(Vec3 acceptedOwn, Vec3 nativeMotion) {
        if (acceptedOwn == null || nativeMotion == null) return false;
        if (acceptedOwn.distanceTo(nativeMotion) <= NativeCollisionSlop.MAX_RESIDUAL) return true;
        return nativeMotion.lengthSqr() <= 1.0e-7 && acceptedOwn.equals(Vec3.ZERO);
    }

    /** Diagnostic-only proof of floor correction backed exclusively by the authored furnished cabin deck. */
    private static Vec3 certifiedCargoFloorGeometry(ServerLevel level, SableFixturePlot plot, BlockPos storage,
                                                     CargoSupportMonitor.CargoSupportSample sample) {
        if (sample == null || sample.capture() == null) return null;
        var capture = sample.capture();
        var observation = capture.preCutoff();
        if (!capture.positionCaptured() || !capture.collisionInfoFresh() || observation == null
                || !observation.nativeCorrectionTraceComplete()
                || !com.juicyslew.moonstation14.entities.LooseItemEntity.NativePreCutoff
                .matches(observation, capture.currentCollisionInfo())
                || !(capture.currentCollisionInfo() instanceof SubLevelEntityCollision.CollisionInfo info)
                || info.motion == null || !sample.onGround() || sample.trackingId() == null
                || !sample.trackingId().equals(plot.engineId()) || !plot.engineId().equals(observation.frameId())
                || !info.verticalCollision || !info.verticalCollisionBelow || info.horizontalCollision
                || info.subLevelHorizontalCollision || info.firstCollisions == null
                || !ownDeltaMatchesNativeMotion(capture.acceptedOwnDelta(), info.motion)
                || info.firstCollisions.values().stream().anyMatch(contact -> contact != null && contact.horizontal())) return null;
        var trace = observation.nativeCorrectionTrace();
        if (!SableOwnedBodyMotion.nativeFloorTraceAdmissible(new MovementVector(capture.requested().x,
                capture.requested().y, capture.requested().z), info.motion.x, info.motion.y,
                info.motion.z, trace, plot.engineId(), row -> {
                    if (row.selectedPos() == null || row.selectedState() == null) return false;
                    BlockPos pos = row.selectedPos();
                    if (pos.getY() != storage.getY() || pos.getX() < storage.getX()
                            || pos.getX() >= storage.getX() + ShuttleCabinFixture.WIDTH
                            || pos.getZ() < storage.getZ() || pos.getZ() >= storage.getZ() + ShuttleCabinFixture.DEPTH)
                        return false;
                    var actual = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), pos);
                    if (actual == null || !actual.equals(row.selectedState()) || !actual.isSolid()) return false;
                    var shape = actual.getCollisionShape(level, pos);
                    return !shape.isEmpty() && Math.abs(shape.max(net.minecraft.core.Direction.Axis.Y) - 1.0) <= 1.0e-6;
                })) return null;
        var pose = observation.logicalPose();
        return pose == null ? null : pose.transformNormalInverse(capture.acceptedOwnDelta());
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-cargo-strong-impulse", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void settledCargoTracksNativeSupportUnderStrongImpulse(GameTestHelper helper) {
        int sceneWidth = strongCargoSceneWidth();
        withFurnishedCabin(helper, STRONG_CARGO_ORIGIN, sceneWidth, (level, plot, storage) -> {
            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
            long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(12.5f);
            var reagent = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water, cents));
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), reagent);
            var beakerStack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BEAKER_LARGE.get());
            beakerStack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), reagent);
            RegisteredCargo jug = registerCargoRoot(level, cabinPoint(plot, storage, 2.5, 1, 2.5), stack, "shuttle-impulse-jug-");
            RegisteredCargo beaker = registerCargoRoot(level, cabinPoint(plot, storage, 4.5, 1, 4.5), beakerStack,
                    "shuttle-impulse-beaker-");
            new CargoSupportMonitor(helper, level, plot, storage, jug, beaker, water, cents, true).register();
        });
    }

    @GameTestTags({"shuttle-cargo-transition", "shuttle-frame-transition", "shuttle", "loose-objects", "shuttle-feedback08-failure-followup"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void registeredCargoPreservesNativePointVelocityWhenLeavingFrame(GameTestHelper helper) {
        withDeck(helper, REGISTERED_CARGO_DEPARTURE_ORIGIN, 16, (level, plot, storage) -> {
            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
            long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(12.5f);
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BEAKER_LARGE.get());
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                    com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water, cents)));
            RegisteredCargo root = registerCargoRoot(level, deckFeet(plot, storage, 2, 2), stack,
                    "shuttle-departure-beaker-");
            new CargoDepartureMonitor(helper, level, plot, storage, root, water, cents).register();
        });
    }

    @GameTestTags({"shuttle-cargo-support", "shuttle-frame-transition", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void rotatingCargoDepartureUsesSdkPointVelocity(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1360, 300, 640), (level, plot, storage) -> {
            RegisteredCargo root = createFilledBeaker(level, deckFeetAt(plot, storage, 1.5, 2.5), "cargo-rotating-");
            new CargoFrameTransitionProbe(helper, level, plot, null, storage, root,
                    CargoTransitionKind.ROTATING_DEPARTURE).register();
        });
    }

    @GameTestTags({"shuttle-exterior-impact-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void translatingHullSideImpactDoesNotBecomeFrameCarry(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1480, 300, 640), 8, (level, plot, storage) -> {
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            RegisteredCargo jug = registerCargoRoot(level, exteriorWorldFeet(plot, false), stack,
                    "exterior-impact-jug-");
            new ExteriorImpactProbe(helper, level, plot, jug.entity()).register();
        });
    }

    @GameTestTags({"shuttle-mob-exterior-impact-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void translatingHullSideImpactLaunchesVanillaPig(GameTestHelper helper) {
        withDeck(helper, TRANSLATING_PIG_IMPACT_ORIGIN, 8, 1, 100, (level, plot, storage) -> {
            setupPigHullImpact(helper, level, plot, false);
        });
    }

    @GameTestTags({"shuttle-owned-exterior-impact-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void translatingHullSideImpactCommitsBoundHumanNormalVelocity(GameTestHelper helper) {
        runBoundHumanHullImpact(helper, new BlockPos(1680, 300, 640), false, false);
    }

    @GameTestTags({"shuttle-replay-world-floor-side-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void replayWorldFloorClipAgainstFrameSide(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1880, 300, 640), 8, (level, plot, storage) -> {
            SableFixtureScene.awaitStationaryFrame(helper, plot, NATIVE_SCENE_READY_TICKS, () -> {
                var hull = plot.subLevel().boundingBox().toMojang();
                var type = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration.getEntityType();
                var sized = type.create(level);
                require(sized != null, "bound Human sizing probe creates");
                double halfWidth = sized.getBbWidth() * .5;
                sized.discard();
                double x = hull.minX - halfWidth - .10;
                double z = (hull.minZ + hull.maxZ) * .5;
                var human = BoundHumanFixture.create(level, new Vec3(x, 300.121297, z),
                        "replay-world-floor-frame-side");
                helper.testInfo.addListener(new GameTestListener() {
                    private void close() { if (!human.isRemoved()) human.discard(); }
                    @Override public void testStructureLoaded(GameTestInfo i) { }
                    @Override public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { close(); }
                });
                var before = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(before != null && before.frameId() == null,
                        "Human starts at a known world point without frame ownership");
                require(level.getBlockState(BlockPos.containing(x, 299.99, z)).isSolid(),
                        "withDeck ground corridor provides ordinary world stone support");
                var initialBox = human.getBoundingBox();
                double sideGap = hull.minX - initialBox.maxX;
                require(initialBox.maxX < hull.minX && sideGap > 0 && sideGap < .2
                                && verticalOverlap(human.getBoundingBox(), hull),
                        "pre-move outside-side setup hull=" + hull + " human=" + initialBox + " sideGap=" + sideGap
                                + " padTop=300 feet=" + initialBox.minY + " deckTop=301");
                Vec3 start = human.position();
                var result = SableOwnedBodyMotion.move(human, new MovementVector(.23849, -.44483, -.14908),
                        new SableOwnedBodyMotion.MoveOptions(false, before,
                                SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY,
                                SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, false, false));
                var info = result.collisionInfo();
                var scope = result.collisionScope();
                require(result.collisionInfoFresh() && info != null && info.motion != null,
                        "native move produced fresh collision result and measured motion; result=" + result);
                require(scope != null && scope.worldClipObservation() != null
                                && scope.worldClipObservation().complete()
                                && scope.worldClipObservation().downwardFloorClip(),
                        "complete downward ordinary-world floor clip observation exists; scope=" + scope);
                require(Math.abs(info.motion.y - result.measuredOwnDisplacement().y()) > .01,
                        "native floor clip is independently measured rather than frame-floor correction; native="
                                + info.motion + " measured=" + result.measuredOwnDisplacement());
                var side = scope.nativeCollisionTrace().stream().filter(row -> row.frameId() != null
                        && !row.verticalCollision() && plot.engineId().equals(row.frameId())).findFirst().orElse(null);
                require(side != null && side.geometry() != null && scope.geometryTraceComplete()
                                && scope.nativeCollisionTraceComplete(),
                        "selected nonvertical frame side and captured geometry are present; scope=" + scope);
                var after = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(after != null && after.frameId() == null && result.beforePoint() != null
                                && result.beforePoint().frameId() == null && result.afterPoint() != null
                                && result.afterPoint().frameId() == null,
                        "before/after points remain known world points");
                require(scope.nativeCollisionTraceComplete() && scope.geometryTraceComplete(),
                        "native geometry evidence did not overflow; scope=" + scope);
                require(result.positionCorrection() != null || result.horizontalContactEvidence() != null,
                        "bounded movement result carries native position/contact certificate; result=" + result);
                var requested = result.requestedOwnDisplacement();
                var nativeCollision = result.toCollisionResult(human.onGround());
                var resolver = MovementCollisionResolver.withIdleSupportProbeContext((position, request, wasGrounded, idleProbe) -> {
                    require(request.equals(requested), "motor request differs from captured native request: motor="
                            + request + " native=" + requested);
                    return nativeCollision;
                });
                var movementEnv = new CharacterMovementEnvironment(.05, 9.8, 0, 1,
                        MovementVector.ZERO, 32, human.maxUpStep(), resolver);
                var motorState = new CharacterMovementState(new MovementVector(start.x, start.y, start.z),
                        requested.scale(20), false);
                var motorResult = new CharacterMovementMotor(new CharacterMovementPolicy(0, 0, 0, 0, 0, 0))
                        .tick(motorState, new CharacterMovementCommand(0, 0, false, false), movementEnv, false, true);
                var nativePosition = start.add(nativeCollision.actualDisplacement().x(),
                        nativeCollision.actualDisplacement().y(), nativeCollision.actualDisplacement().z());
                if (result.positionCorrection() != null) {
                    var correction = result.positionCorrection().geometryCorrection();
                    nativePosition = nativePosition.add(correction.x(), correction.y(), correction.z());
                }
                require(motorResult.position().equals(new MovementVector(nativePosition.x, nativePosition.y,
                                nativePosition.z)) && motorResult.onGround() == nativeCollision.onGround(),
                        "strict motor accepted native world clip; requested=" + requested + " actual="
                                + nativeCollision.actualDisplacement() + " correction=" + result.positionCorrection());
                require(human.position().distanceTo(start) > 0,
                        "native bounded move committed a physical position delta");
                helper.succeed();
            });
        });
    }

    @GameTestTags({"shuttle-boarding-proof-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 240)
    public static void firstWorldBoardingMoveAdmitsSupportedFloorAndSide(GameTestHelper helper) {
        withDeck(helper, new BlockPos(2000, 300, 3000), 8, 1, SLOW_NATIVE_SCENE_READY_TICKS,
                (level, plot, storage) -> {
            SableFixtureScene.awaitStationaryFrame(helper, plot, NATIVE_SCENE_READY_TICKS, () -> {
                BlockPos floor = storage.offset(2, 0, 2);
                BlockPos side = storage.offset(3, 1, 2);
                // setBlock returns whether the state changed, so always attempt both writes and
                // validate the loaded native-frame collision geometry rather than write results.
                plot.setStorageBlock(floor, Blocks.STONE.defaultBlockState());
                plot.setStorageBlock(side, Blocks.STONE.defaultBlockState());
                var floorState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), floor);
                var sideState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), side);
                require(floorState != null && floorState.is(Blocks.STONE)
                                && !floorState.getCollisionShape(level, floor).isEmpty()
                                && sideState != null && sideState.is(Blocks.STONE)
                                && !sideState.getCollisionShape(level, side).isEmpty(),
                        "authored first-boarding floor and adjacent side geometry");
                Vector3d localTarget = new Vector3d(storage.getX() + 2.78, storage.getY() + 1.15,
                        storage.getZ() + 2.5);
                Vector3d worldTarget = plot.subLevel().logicalPose().transformPosition(localTarget);
                var human = BoundHumanFixture.create(level,
                        new Vec3(worldTarget.x, worldTarget.y, worldTarget.z), "first-world-floor-side-boarding");
                human.setDeltaMovement(0, -.3, 0); // Initial falling premise only; the owner step remains authoritative.
                helper.testInfo.addListener(new GameTestListener() {
                    private void close() { if (!human.isRemoved()) human.discard(); }
                    @Override public void testStructureLoaded(GameTestInfo i) { }
                    @Override public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { close(); }
                });
                var before = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(before != null && before.frameId() == null,
                        "first boarding move begins at known world point before any frame tracking");
                AtomicReference<SableOwnedBodyMotion.MoveResult> observed = new AtomicReference<>();
                var stepped = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f, observed::set);
                var move = observed.get();
                require(stepped.isPresent() && move != null && move.beforePoint() != null
                                && move.beforePoint().frameId() == null && move.afterPoint() != null
                                && plot.engineId().equals(move.afterPoint().frameId()),
                        "single fresh owner step transitions the world body onto the exact frame");
                var scope = move.collisionScope();
                var info = move.collisionInfo();
                var trace = scope == null ? null : scope.nativeCollisionTrace();
                var proof = move.nativeContactProofDiagnostic();
                require(move.collisionInfoFresh() && info != null && scope != null
                                && scope.nativeCollisionTraceComplete() && scope.geometryTraceComplete()
                                && trace != null && !trace.isEmpty(),
                        "first move retains fresh complete native and geometry evidence");
                require(trace.getFirst().frameId() != null
                                && trace.stream().anyMatch(row -> !row.verticalCollision() && row.geometry() != null
                                        && row.selectedPos() != null)
                                && trace.stream().anyMatch(row -> row.verticalCollision() && row.geometry() != null
                                        && row.selectedY() > 0 && row.selectedPos() != null),
                        "same first native move witnesses actual frame side and positive floor rows: " + trace);
                require(proof != null && proof.branch() == NativeContactProofDiagnostic.Branch.SUPPORTED_FLOOR_SIDE
                                && proof.positionStage() == NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                && move.positionCorrection() != null && move.boundedOwnDisplacement() != null
                                && info.verticalCollisionBelow && human.onGround(),
                        "strict grounded motor accepts committed SUPPORTED_FLOOR_SIDE position correction: " + proof);
                helper.succeed();
            });
                });
    }

    @GameTestTags({"shuttle-ceiling-side-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void ownedHumanJumpClipsCabinCeilingAndSide(GameTestHelper helper) {
        runCabinCeilingSideOwnerStep(helper, new BlockPos(2000, 300, 640), true);
    }

    @GameTestTags({"shuttle-ceiling-side-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void ownedHumanDescendingClipsCabinCeilingAndSide(GameTestHelper helper) {
        runCabinCeilingSideOwnerStep(helper, new BlockPos(2040, 300, 640), false);
    }

    /** Exercises the production owner step against the canonical native cabin roof and wall. */
    private static void runCabinCeilingSideOwnerStep(GameTestHelper helper, BlockPos origin, boolean jump) {
        withFurnishedCabin(helper, origin, (level, plot, storage) -> {
            HullImpactSubstepsExpectation.requireNormalSix(level, jump ? "ceiling-jump" : "ceiling-descend");
            var humanType = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration.getEntityType();
            var sizing = humanType.create(level);
            require(sizing != null, "bound Human live prototype sizing probe creates");
            double width = sizing.getBbWidth(), height = sizing.getBbHeight();
            sizing.discard();
            double x = jump ? 1 + width * .5 + .01 : 1 + width * .5 - .02;
            double feetY = jump ? 1.05 : 3 - height + .10;
            var human = BoundHumanFixture.create(level,
                    cabinPoint(plot, storage, x, feetY, 4.5), "owned-ceiling-side");
            if (!jump) human.setDeltaMovement(0, -0.07544406705017148, 0);
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { if (!human.isRemoved()) human.discard(); }
                @Override public void testStructureLoaded(GameTestInfo i) { }
                @Override public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                @Override public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { close(); }
            });
            if (jump) {
                awaitCabinFloorGrounding(helper, level, plot, storage, human, 180, width, height);
                return;
            }
            runCabinCeilingSideOwnerStepNow(helper, level, plot, storage, human, width, height, false);
        });
    }

    private static void awaitCabinFloorGrounding(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            BlockPos storage, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int left, double width, double height) {
        if (left <= 0) throw new GameTestAssertException("jump case did not obtain real authored floor support; body=" + human.getBoundingBox());
        if (human.onGround()) {
            var point = LoadedFrameRayQuery.currentActorPoint(level, human);
            BlockPos floor = new BlockPos(storage.getX() + 1, storage.getY(), storage.getZ() + 4);
            var state = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), floor);
            require(point != null && plot.engineId().equals(point.frameId()) && state != null && state.isSolid()
                            && !state.getCollisionShape(level, floor).isEmpty(),
                    "jump starts from tracked frame and known authored cabin floor");
            runCabinCeilingSideOwnerStepNow(helper, level, plot, storage, human, width, height, true);
            return;
        }
        require(new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f).isPresent(),
                "neutral owner support step accepted while awaiting actual floor grounding");
        helper.runAfterDelay(1, () -> awaitCabinFloorGrounding(helper, level, plot, storage, human, left - 1, width, height));
    }

    private static void runCabinCeilingSideOwnerStepNow(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            BlockPos storage, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            double width, double height, boolean jump) {
            Vec3 start = human.position();
            var boundsBefore = human.getBoundingBox();
            BlockPos wall = new BlockPos(storage.getX(), storage.getY() + 1, storage.getZ() + 4);
            BlockPos roof = new BlockPos(storage.getX() + 1, storage.getY() + 3, storage.getZ() + 4);
            var wallState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), wall);
            var roofState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), roof);
            require(wallState != null && wallState.isSolid() && !wallState.getCollisionShape(level, wall).isEmpty()
                            && roofState != null && roofState.isSolid() && !roofState.getCollisionShape(level, roof).isEmpty(),
                    "authored lower-room wall and ceiling collision shapes are loaded");
            var feetPoint = LoadedFrameRayQuery.pointAtWorldPosition(level, plot.engineId(), human.position());
            var headPoint = LoadedFrameRayQuery.pointAtWorldPosition(level, plot.engineId(),
                    new Vec3(human.getX(), boundsBefore.maxY, human.getZ()));
            require(feetPoint != null && headPoint != null && plot.engineId().equals(feetPoint.frameId())
                            && plot.engineId().equals(headPoint.frameId()),
                    "body feet and head resolve to known authored cabin frame points");
            require(jump ? feetPoint.storagePoint().y >= storage.getY() + 1.0 - 1e-6
                            : headPoint.storagePoint().y > storage.getY() + 3.0
                                    && feetPoint.storagePoint().y < storage.getY() + 3.0,
                    "initial body pose establishes intended real floor/ceiling contact premise: " + boundsBefore
                            + " storageFeet=" + feetPoint + " storageHead=" + headPoint
                            + " width=" + width + " height=" + height);
            if (jump) require(human.onGround(), "jump owner step begins with genuine grounded state");
            var actorPointBefore = LoadedFrameRayQuery.currentActorPoint(level, human);
            var dimensionsBefore = human.getBbWidth() + "x" + human.getBbHeight();
            boolean groundedBefore = human.onGround();
            Vec3 velocityBefore = human.getDeltaMovement();
            boolean canActBefore = CharacterControlSystem.canAct(human);
            var characterBefore = CharacterIdentitySystem.resolveForHost(human).map(Object::toString).orElse(null);
            AtomicReference<SableOwnedBodyMotion.MoveResult> nativeMove = new AtomicReference<>();
            var stepped = new GroundedHarnessWorldStep()
                    .step(human, -GroundedHarnessMotor.INPUT_QUANTIZATION, 0, jump, false, 0f, nativeMove::set);
            var move = nativeMove.get();
            boolean stepPresent = stepped.isPresent();
            boolean movePresent = move != null;
            boolean fresh = movePresent && move.collisionInfoFresh();
            boolean infoPresent = movePresent && move.collisionInfo() != null;
            boolean correctionPresent = movePresent && move.positionCorrection() != null;
            if (!(stepPresent && movePresent && fresh && infoPresent && correctionPresent)) {
                var actorPointAfter = LoadedFrameRayQuery.currentActorPoint(level, human);
                String reason = "owner prerequisites step=" + stepPresent + " move=" + movePresent
                        + " fresh=" + fresh + " info=" + infoPresent + " positionCorrection=" + correctionPresent;
                String nativeProof = move == null || move.nativeContactProofDiagnostic() == null ? "unavailable"
                        : move.nativeContactProofDiagnostic().branch() + "/" + move.nativeContactProofDiagnostic().reason();
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("CEILING_SIDE_FAILURE {} native={}", reason,
                        OwnedNativeMoveDiagnostic.describe(human, move));
                throw new GameTestAssertException((reason + " proof=" + nativeProof + " pre=" + start
                        + " post=" + human.position() + " ground=" + human.onGround()
                        + " correction=" + correctionPresent).substring(0, Math.min(850,
                        (reason + " proof=" + nativeProof + " pre=" + start + " post=" + human.position()
                                + " ground=" + human.onGround() + " correction=" + correctionPresent).length())));
            }
            require(move.nativeContactProofDiagnostic().branch()
                            == NativeContactProofDiagnostic.Branch.CEILING_AND_SIDE,
                    "owner diagnostic selects CEILING_AND_SIDE: " + move.nativeContactProofDiagnostic());
            var info = move.collisionInfo();
            var correction = move.positionCorrection().geometryCorrection();
            if (!jump) System.out.println("CEILING_SIDE descending requestY=" + move.requestedOwnDisplacement().y()
                    + " boundedY=" + move.boundedOwnDisplacement().y() + " measuredY=" + move.measuredOwnDisplacement().y()
                    + " nativeY=" + info.motion.y + " geometryY=" + correction.y()
                    + " rawBelow=" + info.verticalCollisionBelow + " resolved=" + move.nativeContactProofDiagnostic().groundingDecision()
                    + " bodyGround=" + human.onGround()
                    + " stepGround=" + stepped.get().onGround());
            require(Double.isFinite(human.getX()) && Double.isFinite(human.getY()) && Double.isFinite(human.getZ())
                            && (jump ? correction.y() <= NativeCollisionSlop.MAX_RESIDUAL
                                    && move.requestedOwnDisplacement().y() > 0
                                    && move.boundedOwnDisplacement().y() < move.requestedOwnDisplacement().y()
                                    && move.boundedOwnDisplacement().y() >= 0 && !info.verticalCollisionBelow
                                    : correction.y() < 0 && move.requestedOwnDisplacement().y() < 0
                                            && Math.abs(move.boundedOwnDisplacement().y()
                                                    - move.requestedOwnDisplacement().y()) <= NativeCollisionSlop.MAX_RESIDUAL
                                             && move.nativeContactProofDiagnostic().groundingDecision()
                                                 == SableOwnedBodyMotion.GroundingDecision.CEILING_ONLY_AIRBORNE
                                              && !human.onGround() && !stepped.get().onGround()
                                              && stepped.get().velocity().y() < 0d
                                              && human.getDeltaMovement().y < 0d),
                    "finite commit with branch-appropriate ceiling geometry correction and no floor support");
            require(info.subLevelHorizontalCollision && info.verticalCollision,
                    "native frame reports both side and ceiling contacts");
            Vec3 committed = human.position().subtract(start);
            Vec3 accounted = new Vec3(move.boundedOwnDisplacement().x(), move.boundedOwnDisplacement().y(),
                    move.boundedOwnDisplacement().z()).add(correction.x(), correction.y(), correction.z());
            require(committed.distanceTo(accounted) < 1.0e-5,
                    "physical owner commit equals bounded native displacement plus geometry correction");
            System.out.println("CEILING_SIDE proof=" + move.nativeContactProofDiagnostic().branch()
                    + " request=" + move.requestedOwnDisplacement() + " native=" + info.motion
                    + " measured=" + move.measuredOwnDisplacement() + " correction=" + correction);
            helper.succeed();
    }

    @GameTestTags({"shuttle-owned-exterior-impact-regression", "shuttle-owned-rotating-exterior-impact", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void rotatingHullSideImpactCommitsBoundHumanNormalVelocity(GameTestHelper helper) {
        runBoundHumanHullImpact(helper, new BlockPos(1720, 300, 640), true, false);
    }

    @GameTestTags({"shuttle-owned-exterior-impact-regression", "shuttle-owned-airborne-impact-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void translatingHullSideImpactSeparatesAirborneBoundHuman(GameTestHelper helper) {
        runBoundHumanHullImpact(helper, new BlockPos(1760, 300, 640), false, true);
    }

    @GameTestTags({"shuttle-owned-exterior-impact-regression", "shuttle-owned-airborne-impact-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void rotatingHullSideImpactSeparatesAirborneBoundHuman(GameTestHelper helper) {
        runBoundHumanHullImpact(helper, new BlockPos(1800, 300, 640), true, true);
    }

    private static void runBoundHumanHullImpact(GameTestHelper helper, BlockPos origin, boolean rotating, boolean airborne) {
        withDeck(helper, origin, 8, (level, plot, storage) -> {
            SableFixtureScene.awaitStationaryFrame(helper, plot, 80, () -> {
                var hull = plot.subLevel().boundingBox().toMojang();
                double feetY = 300.0;
                var prototype = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration.getEntityType();
                var sized = prototype.create(level);
                require(sized != null, "bound human sizing probe creates");
                double x = hull.maxX + sized.getBbWidth() * .5 + .02;
                sized.discard();
                double z = rotating ? hull.maxZ - .75 : (hull.minZ + hull.maxZ) * .5;
                var human = BoundHumanFixture.create(level, new Vec3(x, feetY, z), "owned-human-hull-impact");
                helper.testInfo.addListener(new GameTestListener() {
                    private void close() { if (!human.isRemoved()) human.discard(); }
                    @Override public void testStructureLoaded(GameTestInfo i) { }
                    @Override public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { close(); }
                });
                awaitBoundHumanExteriorContact(helper, level, plot, storage, human, rotating, airborne, 40);
            });
        });
    }

    private static void awaitBoundHumanExteriorContact(GameTestHelper helper, ServerLevel level,
            SableFixturePlot plot, BlockPos storageOrigin,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            boolean rotating, boolean airborne, int left) {
        if (left <= 0) throw new GameTestAssertException("bound Human did not settle on exterior world pad");
        if (!human.onGround()) {
            new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f)
                    .orElseThrow(() -> new GameTestAssertException("bound Human support step rejected"));
            helper.runAfterDelay(1, () -> awaitBoundHumanExteriorContact(helper, level, plot, storageOrigin, human, rotating, airborne, left - 1));
            return;
        }
        var hull = plot.subLevel().boundingBox().toMojang();
        require(level.getBlockState(BlockPos.containing(human.getX(), human.getBoundingBox().minY - .01,
                        human.getZ())).isSolid(), "bound Human has real world pad support");
        require(human.getBoundingBox().minX - hull.maxX >= .02 - 1e-6 && verticalOverlap(human.getBoundingBox(), hull),
                "bound Human begins clear of hull with side vertical span overlap");
        var point = LoadedFrameRayQuery.currentActorPoint(level, human);
        require(point != null && point.frameId() == null, "controlled Human starts with a known world ActorPoint");
        if (airborne) {
            require(new GroundedHarnessWorldStep().step(human, 0, 0, true, false, 0f).isPresent(),
                    "real owner jump input accepted before airborne hull impact");
            require(!human.onGround(), "owner jump leaves Human airborne before native impulse");
        }
        helper.runAfterDelay(1, () -> {
            if (rotating) RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, .5, 0));
            else RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            helper.runAfterDelay(1, () -> awaitHumanImpact(helper, level, plot, storageOrigin, human, rotating, airborne, 30, null, null, 0, false));
        });
    }

    private static void awaitHumanImpact(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, BlockPos storageOrigin,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            boolean rotating, boolean airborne, int left, Vec3 contactPosition, Vec3 firstNormal, int followupTicks,
            boolean freeFlightMomentumObserved) {
        if (left <= 0) {
            var bounds = human.getBoundingBox(); var hull = plot.subLevel().boundingBox().toMojang();
            var ci = ((EntityMovementExtension)(Object)human).sable$getCollisionInfo();
            int count = ci instanceof dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo c
                    ? ((NativeSelectedContactAccess)(Object)c).moonstation14$selectedContacts().size() : 0;
            throw new GameTestAssertException("bound Human impact timeout rotating=" + rotating + " lastNative=" + ci
                    + " journalCount=" + count + " bboxGap=" + (bounds.minX - hull.maxX) + " steps=" + (30-left));
        }
        boolean wasFollowingUp = contactPosition != null;
        AtomicReference<SableOwnedBodyMotion.MoveResult> observed = new AtomicReference<>();
        var step = GroundedHarnessWorldStep.observingNativeGeometry().step(human, 0, 0, false, false, 0f, observed::set)
                .orElseThrow(() -> new GameTestAssertException("controlled Human hull-contact owner step rejected"));
        var result = observed.get();
        var info = result == null ? null : result.collisionInfo();
        if (result != null && result.collisionInfoFresh() && info != null
                && result.normalTarget() != null) {
            var journal = (NativeSelectedContactAccess)(Object) info;
            require(!journal.moonstation14$selectedContactsOverflowed(),
                    "selected-contact journal did not overflow");
            require(result.beforePoint() != null && result.afterPoint() != null
                            && result.beforePoint().frameId() == null && result.afterPoint().frameId() == null,
                    "controlled translation endpoints are known world points");
            var selected = journal.moonstation14$selectedContacts().stream()
                    .filter(e -> e.serverTick() == level.getServer().getTickCount()
                            && e.levelGameTime() == level.getGameTime() && !e.verticalCollision()
                            && plot.engineId().equals(e.frameId()) && e.normal().x > .5).findFirst().orElse(null);
            if (selected != null) {
            if (airborne) require(!human.onGround(), "initial selected hull contact occurs while genuinely airborne");
            var witness = LoadedFrameRayQuery.pointAtWorldPosition(level, selected.frameId(), selected.entityPosition());
            var frameVelocity = SableFrameVelocity.pointVelocityAt(level, witness);
            var angular = RigidBodyHandle.of(plot.subLevel()).getAngularVelocity(new Vector3d());
            var linear = RigidBodyHandle.of(plot.subLevel()).getLinearVelocity(new Vector3d());
            var angularPoint = frameVelocity == null ? null : frameVelocity.subtract(new Vec3(linear.x, linear.y, linear.z));
            System.out.println("BOUND_HUMAN_IMPACT phase=" + (contactPosition == null ? "INITIAL" : "FOLLOWUP") + " tick=" + level.getGameTime() + " target=" + result.normalTarget()
                    + " requested=" + result.requestedOwnDisplacement() + " measured=" + info.motion + " fresh=" + result.collisionInfoFresh()
                    + " endpoints=" + result.beforePoint() + "/" + result.afterPoint() + " normal=" + selected.normal()
                    + " pointVelocity=" + frameVelocity + " committed=" + human.getDeltaMovement());
            require(((EntityMovementExtension)(Object)human).sable$getTrackingSubLevel() == null,
                    "exterior impact does not attach controlled Human to native frame");
            if (contactPosition == null) {
                require(result.normalTarget().outgoingNormalSpeed() > .05,
                        "first target displacement points along selected outward normal");
                Vec3 velocity = human.getDeltaMovement().scale(20);
                require(velocity.dot(selected.normal()) > .05, "same-owner-step outward velocity: " + velocity);
                if (rotating) require(angular.lengthSquared() > 1e-8 && angularPoint != null
                                && angularPoint.dot(selected.normal()) > .05,
                        "same selected contact has outward actual angular point-velocity contribution");
                // Freeze the initial contact evidence; later physical ticks only
                // establish natural outward travel and separation.
                contactPosition = human.position();
                firstNormal = selected.normal();
                followupTicks = airborne ? 8 : 4;
            }
            }
        }
        if (contactPosition != null && ((EntityMovementExtension)(Object)human).sable$getTrackingSubLevel() != null)
            throw new GameTestAssertException("unexpected native ownership during impact followup");
        boolean nextFreeFlightMomentumObserved = freeFlightMomentumObserved;
        if (wasFollowingUp && airborne && result != null && result.collisionInfoFresh() && info != null
                && info instanceof dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo c) {
            var journal = (NativeSelectedContactAccess)(Object)c;
            require(!journal.moonstation14$selectedContactsOverflowed(), "followup journal is complete");
            boolean sideContact = journal.moonstation14$selectedContacts().stream().anyMatch(e ->
                    e.serverTick() == level.getServer().getTickCount() && e.levelGameTime() == level.getGameTime()
                            && !e.verticalCollision() && plot.engineId().equals(e.frameId()));
            if (!sideContact) {
                Vec3 velocity = human.getDeltaMovement().scale(20);
                Vec3 delta = result.afterPoint() == null || result.beforePoint() == null ? Vec3.ZERO
                        : result.afterPoint().worldPoint().subtract(result.beforePoint().worldPoint());
                nextFreeFlightMomentumObserved |= velocity.dot(firstNormal) > .05 && delta.dot(firstNormal) > 0;
            }
        }
        if (wasFollowingUp && followupTicks > 0) {
            followupTicks--;
            if (followupTicks == 0) {
                require(!airborne || nextFreeFlightMomentumObserved,
                        "subsequent no-contact owner step integrates outward committed momentum");
                require(human.position().subtract(contactPosition).dot(firstNormal) > (airborne ? .02 : 0),
                        "subsequent owner ticks produce sufficient displacement along first selected normal");
                var point = LoadedFrameRayQuery.currentActorPoint(level, human);
                var clearance = ownedHullClearance(level, plot, storageOrigin, human);
                if (!airborne && clearance.status() == OwnedHullClearanceStatus.TOUCHING) {
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "BOUND_HUMAN_CLEARANCE grounded contact-stop status=TOUCHING maxMTV={}",
                            clearance.maximumPenetration());
                } else if (clearance.status() != OwnedHullClearanceStatus.COMPLETE_CLEAR) {
                    var bounds = human.getBoundingBox();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                            "BOUND_HUMAN_CLEARANCE status={} maxMTV={} body={} framePose={} nearestShape={} rotating={}",
                            clearance.status(), clearance.maximumPenetration(), bounds,
                            plot.subLevel().logicalPose(), "authored deck collision pieces", rotating);
                }
                require(point != null && point.frameId() == null
                                && (airborne ? clearance.status() == OwnedHullClearanceStatus.COMPLETE_CLEAR
                                        : clearance.status() == OwnedHullClearanceStatus.COMPLETE_CLEAR
                                                || clearance.status() == OwnedHullClearanceStatus.TOUCHING),
                        (airborne ? "airborne positive-gap separation" : "grounded contact-stop (not separation)")
                                + " later owner ticks remain world-owned with exact authored hull clearance status=" + clearance.status()
                                + " maxMTV=" + clearance.maximumPenetration() + " body=" + human.getBoundingBox()
                                + " framePose=" + plot.subLevel().logicalPose() + " nearestShape=authored-deck collision pieces");
                helper.succeed();
                return;
            }
        }
        Vec3 nextContactPosition = contactPosition;
        Vec3 nextFirstNormal = firstNormal;
        int nextFollowupTicks = followupTicks;
        boolean scheduledFreeFlightMomentumObserved = nextFreeFlightMomentumObserved;
        helper.runAfterDelay(1, () -> awaitHumanImpact(helper, level, plot, storageOrigin, human, rotating, airborne, left - 1,
                nextContactPosition, nextFirstNormal, nextFollowupTicks, scheduledFreeFlightMomentumObserved));
    }

    @GameTestTags({"shuttle-mob-exterior-impact-regression", "shuttle-exterior-rotating-impact"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void rotatingHullSideImpactLaunchesVanillaPig(GameTestHelper helper) {
        withDeck(helper, ROTATING_PIG_IMPACT_ORIGIN, 8, 1, 100,
                (level, plot, storage) -> setupPigHullImpact(helper, level, plot, true));
    }

    @GameTestTags({"shuttle-mob-supported-carry", "shuttle-owner-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void supportedInteriorVanillaPigRemainsCarriedUnderControlImpulse(GameTestHelper helper) {
        // Match the supported exterior pig fixtures: the world corridor provides the
        // ordinary stone support pad that lets the native frame settle before spawning.
        withDeck(helper, new BlockPos(2600, 300, 3000), 8, 1, 40, (level, plot, storage, scene) -> {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[CHUNK_READINESS_DIAGNOSTIC] fixture=supported-pig phase=READY_AFTER_AUTHOR testTick={} min={} max={}",
                    helper.getTick(), scene.tickets.diagnostic(scene.min), scene.tickets.diagnostic(scene.max));
            SableFixtureScene.awaitStationaryFrame(helper, plot, 60, () -> {
                var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
                require(pig != null, "vanilla pig entity creates");
                pig.goalSelector.removeAllGoals(goal -> true);
                pig.targetSelector.removeAllGoals(goal -> true);
                pig.getNavigation().stop();
                Vec3 feet = deckFeetAt(plot, storage, 3, 3);
                pig.moveTo(feet.x, feet.y + .2, feet.z, 0, 0);
                level.addFreshEntity(pig);
                helper.testInfo.addListener(new GameTestListener() {
                    private void close() { if (!pig.isRemoved()) pig.discard(); }
                    @Override public void testStructureLoaded(GameTestInfo i) { }
                    @Override public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { close(); }
                });
                awaitSupportedInteriorPig(helper, level, plot, storage, pig, 40, () -> {
                    var ext = (EntityMovementExtension)(Object)pig;
                    require(ext.sable$getTrackingSubLevel() == plot.subLevel(),
                            "settled pig is natively tracked by this exact deck");
                    var local = plot.subLevel().logicalPose().transformPositionInverse(new Vector3d(pig.getX(),
                            pig.getBoundingBox().minY, pig.getZ()));
                    Vec3 initial = new Vec3(local.x, local.y, local.z);
                    var frame = plot.subLevel();
                    Vector3d initialFramePosition = frame.logicalPose().position().get(new Vector3d());
                    RigidBodyHandle.of(frame).addLinearAndAngularVelocity(new Vector3d(.5, 0, .5),
                            new Vector3d(0, .5, 0));
                    helper.runAfterDelay(35, () -> {
                        require(!pig.isRemoved(), "pig remains present after physical ticks");
                        require(ext.sable$getTrackingSubLevel() == plot.subLevel(),
                                "supported pig remains tracked by exact deck, not ejected to exterior");
                        var end = plot.subLevel().logicalPose().transformPositionInverse(new Vector3d(pig.getX(),
                                pig.getBoundingBox().minY, pig.getZ()));
                        Vec3 delta = new Vec3(end.x, end.y, end.z).subtract(initial);
                        require(delta.horizontalDistance() < 1.0,
                                "pig local position remains bounded during native frame impulse: " + delta);
                        require(pig.onGround(), "pig remains physically supported after control impulse");
                        Vector3d finalFramePosition = frame.logicalPose().position().get(new Vector3d());
                        require(finalFramePosition.distance(initialFramePosition) > .02,
                                "native frame physically moves under control impulse: start=" + initialFramePosition
                                        + " end=" + finalFramePosition);
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTestTags({"shuttle-puddle-slip-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void nativeDeckPuddleSlipAtYawZero(GameTestHelper helper) {
        nativeDeckPuddleSlip(helper, new BlockPos(1920, 300, 640), false);
    }

    @GameTestTags({"shuttle-puddle-slip-regression", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 200)
    public static void nativeDeckPuddleSlipAtYawedPose(GameTestHelper helper) {
        nativeDeckPuddleSlip(helper, new BlockPos(2800, 300, 3000), true);
    }

    private static void nativeDeckPuddleSlip(GameTestHelper helper, BlockPos origin, boolean yawed) {
        withDeck(helper, origin, 8, (level, plot, storage) -> {
            HullImpactSubstepsExpectation.requireNormalSix(level, yawed ? "puddle-yawed" : "puddle-zero");
            SableFixtureScene.awaitStationaryFrame(helper, plot, NATIVE_SCENE_READY_TICKS, () -> {
                var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
                BlockPos source = storage.offset(2, 1, 2);
                require(plot.setStorageBlock(source, com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get().defaultBlockState()),
                        "authored native-storage puddle block writes");
                var puddle = level.getBlockEntity(source);
                require(puddle instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity,
                        "native deck puddle storage is loaded");
                var contents = new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment();
                contents.specificAdd(water, 20f, ((com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity)puddle).getCapacity());
                MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
                // The corresponding ordinary-world cell is intentionally dry: only frame-local lookup can find this source.
                Vec3 feet = deckFeetAt(plot, storage, 2.5, 2.5);
                var human = BoundHumanFixture.create(level, feet, "native-deck-puddle-slip");
                helper.testInfo.addListener(new GameTestListener() {
                    private void close() { if (!human.isRemoved()) human.discard(); }
                    @Override public void testStructureLoaded(GameTestInfo i) { }
                    @Override public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                    @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { close(); }
                });
                awaitNativePuddleSlip(helper, level, plot, source, human, yawed, "support", 60, 0);
            });
        });
    }

    private static void awaitNativePuddleSlip(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            BlockPos source, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            boolean yawed, String phase, int left, double initialYaw) {
        var point = LoadedFrameRayQuery.currentActorPoint(level, human);
        double yaw = 0;
        if (point != null && plot.engineId().equals(point.frameId())) {
            var forward = plot.subLevel().logicalPose().orientation().transform(new Vector3d(0, 0, 1));
            yaw = Math.atan2(forward.x, forward.z);
        }
        if (left <= 0) {
            var query = com.juicyslew.moonstation14.ms14.slip.SharedPuddleContactQuery.query(human, human.getBoundingBox());
            throw new GameTestAssertException("puddle settle phase=" + phase + " yaw=" + yaw + " point=" + point
                    + " ground=" + human.onGround() + " query=" + query.state());
        }
        if (phase.equals("support") && point != null && plot.engineId().equals(point.frameId()) && human.onGround()) {
            if (yawed) {
                final double acquiredYaw = yaw;
                RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, .6, 0));
                helper.runAfterDelay(1, () -> awaitNativePuddleSlip(helper, level, plot, source, human, true, "yaw", left - 1, acquiredYaw));
                return;
            }
        } else if (point == null || !plot.engineId().equals(point.frameId()) || !human.onGround()
                || (phase.equals("yaw") && Math.abs(yaw - initialYaw) <= MIN_YAW_QUERY_ROTATION)) {
            new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f);
            helper.runAfterDelay(1, () -> awaitNativePuddleSlip(helper, level, plot, source, human, yawed, phase, left - 1, initialYaw));
            return;
        }
        require(!yawed || Math.abs(yaw) > MIN_YAW_QUERY_ROTATION,
                "frame has settled at a genuinely yawed native pose: " + yaw + " threshold=" + MIN_YAW_QUERY_ROTATION);
        require(level.getBlockState(source).is(com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get()),
                "source exists in frame storage rather than corresponding world cell");
        var worldSource = plot.subLevel().logicalPose().transformPosition(new Vector3d(source.getX() + .5,
                source.getY() + .5, source.getZ() + .5));
        BlockPos worldSourceCell = BlockPos.containing(worldSource.x, worldSource.y, worldSource.z);
        require(!worldSourceCell.equals(source)
                        && !level.getBlockState(worldSourceCell).is(com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get()),
                "transformed ordinary-world source cell is distinct and dry");
        var worldBounds = human.getBoundingBox();
        for (BlockPos candidate : BlockPos.betweenClosed(BlockPos.containing(worldBounds.minX, worldBounds.minY,
                        worldBounds.minZ), BlockPos.containing(Math.nextDown(worldBounds.maxX),
                        Math.nextDown(worldBounds.maxY), Math.nextDown(worldBounds.maxZ)))) {
            require(!level.getBlockState(candidate).is(com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get()),
                    "ordinary-world actor candidate cells contain no puddles");
        }
        var sharedQuery = com.juicyslew.moonstation14.ms14.slip.SharedPuddleContactQuery.query(human,
                human.getBoundingBox());
        require(sharedQuery.state() == com.juicyslew.moonstation14.ms14.slip.SharedPuddleContactQuery.State.AVAILABLE
                        && plot.engineId().equals(sharedQuery.frameId()),
                "production shared contact query is available for exact native frame");
        var sharedContact = sharedQuery.contacts().stream().filter(contact -> contact.source() == level.getBlockEntity(source)
                && contact.sourcePosition().equals(source) && plot.engineId().equals(contact.frameId())
                && contact.triggerRatio() > .3).findFirst().orElse(null);
        require(sharedContact != null, "shared query reports exact authored source and meaningful body overlap");
        var feetQuery = com.juicyslew.moonstation14.ms14.slip.SharedPuddleContactQuery.queryFeetBand(human);
        require(feetQuery.available() && plot.engineId().equals(feetQuery.frameId())
                        && feetQuery.contacts().stream().anyMatch(contact -> contact.source() == level.getBlockEntity(source)
                                && contact.sourcePosition().equals(source) && plot.engineId().equals(contact.frameId())),
                "production feet-band query reports exact native source");
        var listenerEvents = new java.util.concurrent.atomic.AtomicReference<com.juicyslew.moonstation14.ms14.slip.SlipEvent>();
        java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> listener = event -> {
            if (event.target() == human) listenerEvents.set(event);
        };
        require(com.juicyslew.moonstation14.ms14.slip.SlipSystem.addListener(listener), "slip listener registered");
        try {
            human.setDeltaMovement(.2, 0, 0);
            Vec3 before = human.position();
            var accepted = new GroundedHarnessWorldStep().step(human, 0, 1000, false, true, -90f);
            require(accepted.isPresent() && human.position().distanceTo(before) * 20d > 3.5,
                    "real grounded owner step physically accepts above-threshold travel");
            var postStepQuery = com.juicyslew.moonstation14.ms14.slip.SharedPuddleContactQuery.query(human,
                    human.getBoundingBox());
            require(postStepQuery.available() && plot.engineId().equals(postStepQuery.frameId())
                            && postStepQuery.contacts().stream().anyMatch(contact -> contact.source() == level.getBlockEntity(source)
                                    && contact.sourcePosition().equals(source) && plot.engineId().equals(contact.frameId())
                                    && contact.triggerRatio() > .3),
                    "production post-step shared query retains meaningful exact-source contact");
            var event = listenerEvents.get();
            require(event != null && event.source() == level.getBlockEntity(source)
                            && event.sourcePosition().equals(source),
                    "post-motor shared query admits exact native storage puddle source");
            require(com.juicyslew.moonstation14.ms14.slip.SlipSystem.isSliding(human),
                    "native source admission transitions actor to sliding");
            require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isKnockedDown(human),
                    "admitted water profile applies its live knockdown status");
            double friction = com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem.frictionFactor(human);
            require(friction < 1d && friction > 0d,
                    "sliding feet-band query applies non-neutral friction from admitted source: " + friction);
            helper.succeed();
        } finally {
            com.juicyslew.moonstation14.ms14.slip.SlipSystem.removeListener(listener);
        }
    }

    private static void awaitSupportedInteriorPig(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
                                                  BlockPos storage, Mob pig, int ticksLeft, Runnable supported) {
        var ext = (EntityMovementExtension)(Object)pig;
        var point = LoadedFrameRayQuery.currentActorPoint(level, pig);
        boolean deckFoot = ext.sable$getTrackingSubLevel() == plot.subLevel() && point != null
                && plot.engineId().equals(point.frameId()) && point.storagePoint() != null
                && point.storagePoint().y >= storage.getY() + 1 - .08 && point.storagePoint().y <= storage.getY() + 1.05;
        BlockPos supportPos = point == null || point.storagePoint() == null ? null
                : BlockPos.containing(point.storagePoint().x, point.storagePoint().y - .01, point.storagePoint().z);
        var supportState = supportPos == null ? null : LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), supportPos);
        boolean deckBlock = supportState != null && supportState.isSolid();
        if (pig.onGround() && deckFoot && deckBlock) { supported.run(); return; }
        if (ticksLeft <= 0) throw new GameTestAssertException("pig did not acquire native deck support; tracking="
                + ext.sable$getTrackingSubLevel() + " point=" + point + " onGround=" + pig.onGround()
                + " worldFoot=" + BlockPos.containing(pig.getX(), pig.getBoundingBox().minY - .01, pig.getZ())
                + " storageFoot=" + supportPos + " frameBlock=" + supportState);
        helper.runAfterDelay(1, () -> awaitSupportedInteriorPig(helper, level, plot, storage, pig, ticksLeft - 1, supported));
    }

    private static void setupPigHullImpact(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, boolean rotating) {
            SableFixtureScene.awaitStationaryFrame(helper, plot, 80, () -> {
                var hull = plot.subLevel().boundingBox().toMojang();
                var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
                require(pig != null, "vanilla pig entity creates");
                // Preserve vanilla travel/gravity while preventing autonomous wander from contaminating baseline.
                pig.goalSelector.removeAllGoals(goal -> true);
                pig.targetSelector.removeAllGoals(goal -> true);
                pig.getNavigation().stop();
                double x = hull.maxX + pig.getBbWidth() * .5 + .02;
                double z = rotating ? hull.maxZ - .75 : (hull.minZ + hull.maxZ) * .5;
                // withDeck already authors the stone support pad across the ground corridor.
                // Its top is the scene origin Y (300 here); do not duplicate a floor from
                // rounded native hull bounds, which can place the pig inside this pad.
                double padTop = 300.0;
                BlockPos supportPos = BlockPos.containing(x, padTop - .01, z);
                var supportState = level.getBlockState(supportPos);
                double feetY = padTop;
                pig.moveTo(x, feetY + .2, z, 0, 0);
                level.addFreshEntity(pig);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "PIG_HULL_SETUP spawn hull={} pig={} padTop={} supportPos={} supportState={} velocity={} onGround={} loaded={}",
                        hull, pig.getBoundingBox(), padTop, supportPos, supportState, pig.getDeltaMovement(), pig.onGround(),
                        level.hasChunkAt(BlockPos.containing(x, feetY, pig.getZ())));
                new PigHullImpactProbe(helper, level, plot, pig, rotating).register();
            });
    }

    private static final class PigHullImpactProbe {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final Mob pig;
        private final boolean rotating;
        private Consumer<EntityTickEvent.Post> listener;
        private dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo collision;
        private com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess.Entry selected;
        private Vec3 contactPosition, contactVelocity;
        private dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo preMotionInfo;
        private long contactTick;
        private Vec3 rotationalPointVelocity;
        private Vector3d angularAtContact, linearAtContact;
        PigHullImpactProbe(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, Mob pig, boolean rotating) {
            this.helper=helper; this.level=level; this.plot=plot; this.pig=pig; this.rotating=rotating;
        }
        void register() {
            helper.testInfo.addListener(new GameTestListener() {
                private void close(){ if (listener != null) NeoForge.EVENT_BUS.unregister(listener); if (!pig.isRemoved()) pig.discard(); }
                @Override public void testStructureLoaded(GameTestInfo i){}
                @Override public void testPassed(GameTestInfo i,GameTestRunner r){close();}
                @Override public void testFailed(GameTestInfo i,GameTestRunner r){close();}
                @Override public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){close();}
            });
            helper.runAfterDelay(30, () -> {
                var point = LoadedFrameRayQuery.currentActorPoint(level,pig);
                require(point != null && point.frameId()==null
                                && ((EntityMovementExtension)(Object)pig).sable$getTrackingSubLevel()==null,
                        "pig is world tracked before impact");
                if (!pig.onGround()) throw new GameTestAssertException("pig did not settle on authored world floor; hull="
                        + plot.subLevel().boundingBox().toMojang() + " pig=" + pig.getBoundingBox()
                        + " velocity=" + pig.getDeltaMovement() + " onGround=" + pig.onGround()
                        + " tracking=" + ((EntityMovementExtension)(Object)pig).sable$getTrackingSubLevel()
                        + " support=" + level.getBlockState(BlockPos.containing(pig.getX(), pig.getBoundingBox().minY - .01, pig.getZ()))
                        + " loaded=" + level.hasChunkAt(BlockPos.containing(pig.getX(), pig.getBoundingBox().minY, pig.getZ())));
                require(Math.hypot(pig.getDeltaMovement().x, pig.getDeltaMovement().z) < .01,
                        "pig has near-zero horizontal speed before hull motion");
                require(level.getBlockState(BlockPos.containing(pig.getX(), pig.getBoundingBox().minY - .01, pig.getZ()))
                                .isSolid(), "pig has actual world-floor support before hull motion");
                require(pig.getBoundingBox().minX > plot.subLevel().boundingBox().toMojang().maxX,
                        "pig starts outside hull bounds");
                var hullBounds = plot.subLevel().boundingBox().toMojang();
                var pigBounds = pig.getBoundingBox();
                double verticalOverlap = Math.min(hullBounds.maxY, pigBounds.maxY) - Math.max(hullBounds.minY, pigBounds.minY);
                BlockPos supportPos = BlockPos.containing(pig.getX(), pigBounds.minY - .01, pig.getZ());
                var supportState = level.getBlockState(supportPos);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "PIG_HULL_IMPACT_ARM_CHECK hull={} pig={} padBlock={} padState={} verticalOverlap={} xGap={}",
                        hullBounds, pigBounds, supportPos, supportState, verticalOverlap, pigBounds.minX - hullBounds.maxX);
                require(pigBounds.minX - hullBounds.maxX > 0, "pig has positive initial clearance from hull side");
                require(verticalOverlap > .1, "pig vertically overlaps hull side by more than 0.1 blocks; hull="
                        + hullBounds + " pig=" + pigBounds + " padBlock=" + supportPos + " padState=" + supportState
                        + " exactOverlap=" + verticalOverlap);
                require(level.hasChunkAt(BlockPos.containing(pig.getX(), pig.getBoundingBox().minY, pig.getZ())),
                        "pig and floor are in loaded world chunks");
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "PIG_HULL_IMPACT armed hull={} pig={} xGap={} verticalOverlap={} tracking={} onGround={} frameLoaded={}",
                        hullBounds, pigBounds, pigBounds.minX - hullBounds.maxX, verticalOverlap,
                        ((EntityMovementExtension)(Object)pig).sable$getTrackingSubLevel(), pig.onGround(),
                        level.hasChunkAt(BlockPos.containing(pig.getX(), pig.getBoundingBox().minY, pig.getZ())));
                preMotionInfo = ((EntityMovementExtension)(Object)pig).sable$getCollisionInfo();
                listener = event -> {
                if (event.getEntity() != pig || selected != null) return;
                var info = ((EntityMovementExtension)(Object)pig).sable$getCollisionInfo();
                if (!(info instanceof dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo ci)
                        || ci == preMotionInfo) return;
                var journal = (com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess)(Object)ci;
                if (journal.moonstation14$selectedContactsOverflowed()) return;
                for (var e : journal.moonstation14$selectedContacts()) {
                    if (e.serverTick() != level.getServer().getTickCount() || event.getEntity() != pig || e.verticalCollision()
                            || e.frameId() == null || !plot.engineId().equals(e.frameId())
                            || Math.hypot(e.normal().x, e.normal().z) < .5 || e.normal().x < .5) {
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                "PIG_HULL_REJECT tick={} frame={} normal={} vertical={} candidateTick={}",
                                level.getServer().getTickCount(), e.frameId(), e.normal(), e.verticalCollision(), e.serverTick());
                        continue;
                    }
                    selected=e; collision=ci; contactTick=e.serverTick(); contactPosition=pig.position();
                    contactVelocity=pig.getDeltaMovement().scale(20);
                    if (rotating) {
                        var witness = LoadedFrameRayQuery.pointAtWorldPosition(level, e.frameId(), e.entityPosition());
                        var body = RigidBodyHandle.of(plot.subLevel());
                        angularAtContact = body.getAngularVelocity(new Vector3d());
                        linearAtContact = body.getLinearVelocity(new Vector3d());
                        rotationalPointVelocity = SableFrameVelocity.pointVelocityAt(level, witness);
                    }
                    break;
                }
                };
                NeoForge.EVENT_BUS.addListener(listener);
                var body = RigidBodyHandle.of(plot.subLevel());
                if (rotating) body.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0,.5,0));
                else body.addLinearAndAngularVelocity(new Vector3d(2,0,0),new Vector3d());
                poll(0);
            });
        }
        private void poll(int n) {
            if (n > 70) {
                var hull = plot.subLevel().boundingBox().toMojang(); var pigBounds = pig.getBoundingBox();
                throw new GameTestAssertException("pig received no eligible fresh hull side contact; hull=" + hull
                        + " pig=" + pigBounds + " xGap=" + (pigBounds.minX - hull.maxX)
                        + " verticalOverlap=" + (Math.min(hull.maxY, pigBounds.maxY) - Math.max(hull.minY, pigBounds.minY)));
            }
            if (n > 0 && n % 10 == 0) {
                var hull = plot.subLevel().boundingBox().toMojang();
                var info = ((EntityMovementExtension)(Object)pig).sable$getCollisionInfo();
                int journalCount = 0; boolean overflow = false;
                if (info instanceof dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo ci) {
                    var journal = (com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess)(Object)ci;
                    journalCount = journal.moonstation14$selectedContacts().size();
                    overflow = journal.moonstation14$selectedContactsOverflowed();
                }
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "PIG_HULL_PROGRESS tick={} hull={} pointVelocity={} pig={} velocity={} tracking={} onGround={} infoSame={} journalCount={} overflow={}",
                        level.getServer().getTickCount(), hull,
                        SableFrameVelocity.pointVelocityAt(level,
                                LoadedFrameRayQuery.pointAtWorldPosition(level, plot.engineId(), pig.position())), pig.getBoundingBox(),
                        pig.getDeltaMovement(), ((EntityMovementExtension)(Object)pig).sable$getTrackingSubLevel(), pig.onGround(),
                        info == preMotionInfo, journalCount, overflow);
            }
            if (selected == null) { helper.runAfterDelay(1,()->poll(n+1)); return; }
            require(collision != null && collision != preMotionInfo && selected.serverTick() == contactTick,
                    "fresh selected contact belongs to the pig post-tick event");
            require(contactVelocity.dot(selected.normal()) > .05, "same-tick pig velocity points outward from selected side");
            var contactPoint = LoadedFrameRayQuery.pointAtWorldPosition(level, selected.frameId(), selected.entityPosition());
            require(contactPoint != null && plot.engineId().equals(contactPoint.frameId()),
                    "selected witness point resolves on the impacting frame");
            Vec3 hullPointVelocity = SableFrameVelocity.pointVelocityAt(level, contactPoint);
            require(hullPointVelocity != null && hullPointVelocity.dot(selected.normal()) > 0,
                    "selected contact point has positive outward hull velocity");
            if (rotating) {
                require(angularAtContact != null && angularAtContact.lengthSquared() > 1.0e-8
                                && rotationalPointVelocity != null && linearAtContact != null
                                && rotationalPointVelocity.subtract(new Vec3(linearAtContact.x, linearAtContact.y,
                                linearAtContact.z)).dot(selected.normal()) > .05,
                        "actual selected contact point has outward angular point-velocity contribution");
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "PIG_HULL_ROTATION_CONTACT tick={} normal={} nativeYaw={} pointVelocity={} linearVelocity={} angularContribution={}",
                        contactTick, selected.normal(), angularAtContact.y, rotationalPointVelocity, linearAtContact,
                        rotationalPointVelocity.subtract(new Vec3(linearAtContact.x, linearAtContact.y, linearAtContact.z)));
            }
            var point=LoadedFrameRayQuery.currentActorPoint(level,pig);
            require(point != null && point.frameId()==null, "contact leaves pig tracked in world");
            System.out.println("PIG_HULL_IMPACT hull="+plot.subLevel().boundingBox().toMojang()+" pig="+pig.getBoundingBox()
                    +" contact="+selected.entityPosition()+" velocity="+contactVelocity+" normal="+selected.normal()
                    +" entryTick="+contactTick+" witnessPoint="+contactPoint+" hullPointVelocity="+hullPointVelocity);
            helper.runAfterDelay(4,()-> {
                var finalPoint=LoadedFrameRayQuery.currentActorPoint(level,pig);
                Vec3 finalVelocity=pig.getDeltaMovement().scale(20);
                require(pig.position().subtract(contactPosition).dot(selected.normal()) > .05
                                && finalPoint != null && finalPoint.frameId()==null
                                && ((EntityMovementExtension)(Object)pig).sable$getTrackingSubLevel()==null
                                && pig.getBoundingBox().minX > plot.subLevel().boundingBox().toMojang().maxX,
                        "pig has actual outward displacement, separates from hull, and remains unattached to a frame");
                System.out.println("PIG_HULL_IMPACT finalTick="+level.getServer().getTickCount()+" finalPig="+pig.getBoundingBox()
                        +" finalVelocity="+finalVelocity+" displacement="+pig.position().subtract(contactPosition));
                helper.succeed();
            });
        }
    }

    @GameTestTags({"shuttle-exterior-impact-regression", "shuttle-exterior-rotating-impact", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void rotatingHullCornerImpactIsDistinctFromSupportedInteriorCarry(GameTestHelper helper) {
        withDeck(helper, new BlockPos(2400, 300, 3000), 8, (level, plot, storage) -> {
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            RegisteredCargo exterior = registerCargoRoot(level, exteriorWorldFeet(plot, true), stack,
                    "rotating-exterior-impact-");
            RegisteredCargo interior = createFilledBeaker(level, deckFeetAt(plot, storage, 2.5, 2.5),
                    "rotating-interior-control-");
            new RotatingExteriorImpactProbe(helper, level, plot, storage, exterior.entity(), interior.entity()).register();
        });
    }

    @GameTestTags({"jug-impact-contact-regression", "jug-impact-native-frame-floor", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 240)
    public static void jugNativeFrameFloorImpactUsesSelectedContactAfterRealSceneReadiness(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1800, 300, 640), 4, (level, plot, storage) -> {
            requireStrongImpulseConfig(helper, "[jug-frame-impact-config]", false);
            SableFixtureScene.awaitStationaryFrame(helper, plot, 80, () -> {
                RegisteredCargo readiness = createFilledBeaker(level, deckFeetAt(plot, storage, 4.5, 4.5),
                        "jug-impact-readiness-");
                BoundedGameTestWait.await(helper, 80,
                        () -> "native-frame readiness cargo did not settle on " + plot.engineId(),
                        () -> {
                            var point = LoadedFrameRayQuery.currentActorPoint(level, readiness.entity());
                            return readiness.entity().settled() && point != null
                                    && plot.engineId().equals(point.frameId());
                        }, () -> {
                            readiness.entity().discard();
                            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
                            long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(10f);
                            var stack = new net.minecraft.world.item.ItemStack(
                                    com.juicyslew.moonstation14.item.ModItems.JUG.get());
                            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                                    com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water, cents)));
                            RegisteredCargo jug = registerCargoRoot(level, deckFeetAt(plot, storage, 2.5, 2.5),
                                    stack, "jug-native-frame-impact-");
                            var watch = com.juicyslew.moonstation14.entities.LooseMotionDiagnostics
                                    .watchPersistence(level, java.util.List.of(jug.entity().getUUID()));
                            helper.testInfo.addListener(new GameTestListener() {
                                private void close() { watch.close(); }
                                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest,
                                                                       GameTestRunner runner) { close(); }
                            });
                            jug.entity().launch(new Vec3(.75, -.02, 0), 0);
                            BoundedGameTestWait.await(helper, 12,
                                    () -> "native jug impact timeout pos=" + jug.entity().position()
                                            + " settled=" + jug.entity().settled(),
                                    jug.entity()::isRemoved, () -> {
                                        require(jug.entity().nativeMoveCapture() != null
                                                        && jug.entity().nativeMoveCapture().collisionInfoFresh(),
                                                "impact was captured during an actual fresh native movement call");
                                        long spilled = 0;
                                        int puddles = 0;
                                        for (int x = 0; x < DECK_SIZE; x++) for (int z = 0; z < DECK_SIZE; z++) {
                                            BlockPos pos = storage.offset(x, 1, z);
                                            if (!level.getBlockState(pos).is(com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get())) continue;
                                            var puddle = level.getBlockEntity(pos);
                                            if (!(puddle instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity)) continue;
                                            puddles++;
                                            var contents = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
                                            spilled += com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(
                                                    contents.getMap().getOrDefault(water, 0f));
                                        }
                                        require(puddles > 0 && spilled == cents,
                                                "native frame-floor puddle placement preserves exactly 1000 water cents; puddles="
                                                        + puddles + " cents=" + spilled);
                                        System.out.println("JUG_NATIVE_FRAME_WATCH events=" + watch.snapshot().events().size()
                                                + " counts=" + watch.snapshot().counts());
                                        helper.succeed();
                                    });
                        });
            });
        });
    }

    private record ExteriorImpactSample(
            com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture capture,
            Vec3 position, Vec3 velocityMps, Vec3 inheritedMotion, Vec3 contactNormal,
            UUID frameId, boolean onGround) { }

    private static final class RotatingExteriorImpactProbe {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final BlockPos storage;
        private final com.juicyslew.moonstation14.entities.LooseItemEntity exterior, interior;
        private final AtomicReference<ExteriorImpactSample> contact = new AtomicReference<>();
        private com.juicyslew.moonstation14.entities.LooseItemEntity.NativeFrameContact selected;
        private com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture completedCapture;
        private Consumer<EntityTickEvent.Post> postListener;
        private Vec3 interiorStart;
        private Vector3d angularStart;
        private Vector3d angularAtContact;
        private Quaterniond orientationStart;
        private Quaterniond orientationAtContact;
        private Vec3 rotationalPointVelocity;
        private Vector3d linearVelocityAtContact;
        private boolean armed;

        RotatingExteriorImpactProbe(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, BlockPos storage,
                                    com.juicyslew.moonstation14.entities.LooseItemEntity exterior,
                                    com.juicyslew.moonstation14.entities.LooseItemEntity interior) {
            this.helper=helper; this.level=level; this.plot=plot; this.storage=storage;
            this.exterior=exterior; this.interior=interior;
        }

        void register() {
            exterior.setNativeFrameContactObserver(value -> acceptSelected(value, exterior));
            exterior.setNativeMoveCaptureObserver(capture -> {
                if (selected != null && selected.moveInFlight() && selected.collisionInfo() == capture.currentCollisionInfo())
                    completedCapture = capture;
            });
            postListener = this::afterEntityTick;
            NeoForge.EVENT_BUS.addListener(postListener);
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { armed = false; exterior.setNativeFrameContactObserver(null); exterior.setNativeMoveCaptureObserver(null); NeoForge.EVENT_BUS.unregister(postListener); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
            helper.runAfterDelay(30, () -> {
                require(exterior.onGround(), "exterior jug is settled on world ground before native rotation");
                var hullBounds = plot.subLevel().boundingBox().toMojang();
                var exteriorPoint = LoadedFrameRayQuery.currentActorPoint(level, exterior);
                System.out.println("EXTERIOR_ROTATION_PRE pose=" + plot.subLevel().logicalPose()
                        + " hull=" + hullBounds + " angular="
                        + RigidBodyHandle.of(plot.subLevel()).getAngularVelocity(new Vector3d())
                        + " exterior=" + exterior.position() + " exteriorBox=" + exterior.getBoundingBox()
                        + " floor=" + exterior.onGround() + " tracking="
                        + ((EntityMovementExtension)(Object)exterior).sable$getTrackingSubLevel()
                        + " point=" + exteriorPoint);
                require(exteriorPoint != null && exteriorPoint.frameId() == null
                                && ((EntityMovementExtension)(Object)exterior).sable$getTrackingSubLevel() == null,
                        "rotation target is world-floor cargo, not pre-carried deck cargo");
                require(exterior.getBoundingBox().minX > hullBounds.maxX
                                && verticalOverlap(exterior.getBoundingBox(), hullBounds),
                        "rotation target clears east side and spans side-wall height before yaw");
                require(level.getBlockState(BlockPos.containing(exterior.getX(), exterior.getBoundingBox().minY - .01,
                                exterior.getZ())).is(Blocks.STONE),
                        "rotation target has measured ordinary world-stone support, not native deck support");
                require(interior.onGround() && interior.settled(), "interior beaker starts on actual native deck support");
                var point = LoadedFrameRayQuery.currentActorPoint(level, interior);
                require(point != null && plot.engineId().equals(point.frameId()), "interior control resolves to this live frame");
                interiorStart = point.storagePoint();
                var body = RigidBodyHandle.of(plot.subLevel());
                angularStart = body.getAngularVelocity(new Vector3d());
                orientationStart = new Quaterniond(plot.subLevel().logicalPose().orientation());
                armed = true;
                body.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, .5, 0));
                poll(0);
            });
        }

        private void acceptSelected(com.juicyslew.moonstation14.entities.LooseItemEntity.NativeFrameContact value,
                                    com.juicyslew.moonstation14.entities.LooseItemEntity entity) {
            if (!armed || selected != null || value.verticalCollision() || !plot.engineId().equals(value.frameId())
                    || Math.hypot(value.normal().x, value.normal().z) < .5) return;
            selected = value;
            var body = RigidBodyHandle.of(plot.subLevel());
            angularAtContact = body.getAngularVelocity(new Vector3d());
            linearVelocityAtContact = body.getLinearVelocity(new Vector3d());
            orientationAtContact = new Quaterniond(plot.subLevel().logicalPose().orientation());
            LoadedFrameRayQuery.ActorPoint contactPoint = LoadedFrameRayQuery.pointAtWorldPosition(
                    level, value.frameId(), value.entityPosition());
            rotationalPointVelocity = SableFrameVelocity.pointVelocityAt(level, contactPoint);
            System.out.println("EXTERIOR_ROTATION_SELECTED phase=" + (value.moveInFlight() ? "move" : "outside-move")
                    + " tick=" + value.serverTick() + " frame=" + value.frameId() + " normal=" + value.normal());
            if (!value.moveInFlight()) throw new GameTestAssertException("selected rotating exterior contact was outside Entity.move; same-tick final response sampling unsupported (contactTick=" + value.serverTick() + ")");
        }

        private void afterEntityTick(EntityTickEvent.Post event) {
            if (event.getEntity() != exterior || selected == null || !selected.moveInFlight()
                    || selected.serverTick() != level.getServer().getTickCount()) return;
            var capture = completedCapture;
            if (capture == null || !capture.collisionInfoFresh()
                    || capture.currentCollisionInfo() != selected.collisionInfo()) return;
            var point = LoadedFrameRayQuery.currentActorPoint(level, exterior);
            contact.compareAndSet(null, new ExteriorImpactSample(capture, exterior.position(),
                    exterior.getDeltaMovement().scale(20), Vec3.ZERO, selected.normal(),
                    point == null ? null : point.frameId(), exterior.onGround()));
            System.out.println("EXTERIOR_ROTATION_POST contactTick=" + selected.serverTick() + " sampleTick=" + level.getServer().getTickCount()
                    + " phase=entity-post pos=" + exterior.position() + " velocity=" + exterior.getDeltaMovement() + " normal=" + selected.normal());
        }

        private void poll(int waited) {
            if (waited >= 60) throw new GameTestAssertException("no fresh rotating exterior contact; hull="
                    + plot.subLevel().boundingBox().toMojang() + " pose=" + plot.subLevel().logicalPose()
                    + " angular=" + RigidBodyHandle.of(plot.subLevel()).getAngularVelocity(new Vector3d())
                    + " exterior=" + exterior.position() + " box=" + exterior.getBoundingBox()
                    + " floor=" + exterior.onGround());
            ExteriorImpactSample sample = contact.get();
            var point = LoadedFrameRayQuery.currentActorPoint(level, interior);
            if (sample == null || point == null || !plot.engineId().equals(point.frameId())) {
                if (waited == 10 || waited == 30 || waited == 50) System.out.println("EXTERIOR_ROTATION_PROGRESS t="
                        + waited + " pose=" + plot.subLevel().logicalPose() + " hull="
                        + plot.subLevel().boundingBox().toMojang() + " angular="
                        + RigidBodyHandle.of(plot.subLevel()).getAngularVelocity(new Vector3d())
                        + " exterior=" + exterior.position() + " box=" + exterior.getBoundingBox());
                helper.runAfterDelay(1, () -> poll(waited + 1)); return;
            }
            Vector3d angularNow = angularAtContact;
            Vec3 localDelta = point.storagePoint().subtract(interiorStart);
            require(angularNow != null && angularNow.y > 1.0e-8 && angularNow.y <= .75,
                    "contact occurs under measured bounded native angular velocity, not a pose teleport");
            require(orientationStart != null && orientationAtContact != null
                            && Math.abs(orientationStart.dot(orientationAtContact)) < 1.0 - 1.0e-8,
                    "hull orientation measurably changes before selected contact");
            require(angularNow != null && Double.isFinite(angularNow.x) && Double.isFinite(angularNow.y)
                            && Double.isFinite(angularNow.z) && angularNow.lengthSquared() > 1.0e-8,
                    "selected contact captures finite nonzero angular velocity");
            require(rotationalPointVelocity != null && linearVelocityAtContact != null
                            && Double.isFinite(linearVelocityAtContact.x) && Double.isFinite(linearVelocityAtContact.y)
                            && Double.isFinite(linearVelocityAtContact.z)
                            && rotationalPointVelocity.subtract(new Vec3(linearVelocityAtContact.x,
                                    linearVelocityAtContact.y, linearVelocityAtContact.z)).length() > .05,
                    "contact point velocity has meaningful rotational contribution beyond hull linear velocity");
            require(localDelta.length() < .08 && interior.settled() && interior.onGround(),
                    "supported interior beaker retains deck-relative pose and support during exterior impact");
            require(sample.frameId() == null && sample.velocityMps().length() > .05,
                    "exterior contact response remains a world-body velocity, not a frame-attached carry");
            require(sample.velocityMps().dot(sample.contactNormal()) > .05,
                    "first fresh native side-contact response has outward normal velocity");
            System.out.println("SHUTTLE_ROTATING_EXTERIOR_IMPACT frame=" + plot.engineId() + " phase="
                    + (sample.capture() == null ? "outside-move" : "move") + " seq="
                    + (sample.capture() == null ? null : sample.capture().sequence()) + " inherited=" + sample.inheritedMotion() + " outgoing="
                    + sample.velocityMps() + " normal=" + sample.contactNormal() + " normalMps="
                    + sample.velocityMps().dot(sample.contactNormal()) + " interiorLocalDelta=" + localDelta + " angularStart=" + angularStart
                    + " angularNow=" + angularNow);
            helper.succeed();
        }
    }

    /** Records the real native move before accepting an exterior side impact as a successful fixture. */
    private static final class ExteriorImpactProbe {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final com.juicyslew.moonstation14.entities.LooseItemEntity jug;
        private final AtomicReference<ExteriorImpactSample> contact = new AtomicReference<>();
        private com.juicyslew.moonstation14.entities.LooseItemEntity.NativeFrameContact selected;
        private boolean armed;
        private com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture completedCapture;
        private Consumer<EntityTickEvent.Post> postListener;

        ExteriorImpactProbe(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
                            com.juicyslew.moonstation14.entities.LooseItemEntity jug) {
            this.helper = helper; this.level = level; this.plot = plot; this.jug = jug;
        }

        void register() {
            jug.setNativeFrameContactObserver(value -> acceptSelected(value));
            jug.setNativeMoveCaptureObserver(capture -> {
                if (selected != null && selected.moveInFlight() && selected.collisionInfo() == capture.currentCollisionInfo())
                    completedCapture = capture;
            });
            postListener = this::afterEntityTick;
            NeoForge.EVENT_BUS.addListener(postListener);
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { armed = false; jug.setNativeFrameContactObserver(null); jug.setNativeMoveCaptureObserver(null); NeoForge.EVENT_BUS.unregister(postListener); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
            helper.runAfterDelay(30, () -> {
                require(jug.onGround(), "exterior jug settles on ordinary world floor before the hull advances");
                var hullBounds = plot.subLevel().boundingBox().toMojang();
                var jugPoint = LoadedFrameRayQuery.currentActorPoint(level, jug);
                var body = RigidBodyHandle.of(plot.subLevel());
                System.out.println("EXTERIOR_TRANSLATION_PRE pose=" + plot.subLevel().logicalPose()
                        + " hull=" + hullBounds + " bodyV=" + body.getLinearVelocity(new Vector3d())
                        + " jug=" + jug.position() + " jugBox=" + jug.getBoundingBox() + " floor=" + jug.onGround()
                        + " tracking=" + ((EntityMovementExtension)(Object)jug).sable$getTrackingSubLevel()
                        + " framePoint=" + jugPoint);
                require(jugPoint != null && jugPoint.frameId() == null
                                && ((EntityMovementExtension)(Object)jug).sable$getTrackingSubLevel() == null,
                        "translation target is world-floor cargo, not pre-carried deck cargo");
                require(jug.getBoundingBox().minX > hullBounds.maxX
                                && verticalOverlap(jug.getBoundingBox(), hullBounds),
                        "translation target clears hull side and spans side-wall height before nudge");
                armed = true;
                body.addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
                poll(0);
            });
        }

        private void acceptSelected(com.juicyslew.moonstation14.entities.LooseItemEntity.NativeFrameContact value) {
            if (!armed || selected != null || value.verticalCollision() || !plot.engineId().equals(value.frameId())
                    || Math.hypot(value.normal().x, value.normal().z) < .5) return;
            selected = value;
            System.out.println("EXTERIOR_TRANSLATION_SELECTED phase=" + (value.moveInFlight() ? "move" : "outside-move")
                    + " tick=" + value.serverTick() + " frame=" + value.frameId() + " normal=" + value.normal());
            if (!value.moveInFlight()) throw new GameTestAssertException("selected translating exterior contact was outside Entity.move; same-tick final response sampling unsupported (contactTick=" + value.serverTick() + ")");
        }

        private void afterEntityTick(EntityTickEvent.Post event) {
            if (event.getEntity() != jug || selected == null || !selected.moveInFlight()
                    || selected.serverTick() != level.getServer().getTickCount()) return;
            var capture = completedCapture;
            if (capture == null || !capture.collisionInfoFresh()
                    || capture.currentCollisionInfo() != selected.collisionInfo()) return;
            var point = LoadedFrameRayQuery.currentActorPoint(level, jug);
            contact.compareAndSet(null, new ExteriorImpactSample(capture, jug.position(),
                    jug.getDeltaMovement().scale(20), Vec3.ZERO, selected.normal(),
                    point == null ? null : point.frameId(), jug.onGround()));
            System.out.println("EXTERIOR_TRANSLATION_POST contactTick=" + selected.serverTick() + " sampleTick=" + level.getServer().getTickCount() + " phase=entity-post pos="
                    + jug.position() + " velocity=" + jug.getDeltaMovement() + " normal=" + selected.normal());
        }

        private void poll(int waited) {
            if (waited >= 50) throw new GameTestAssertException("no fresh exterior contact; hull="
                    + plot.subLevel().boundingBox().toMojang() + " pose=" + plot.subLevel().logicalPose()
                    + " bodyV=" + RigidBodyHandle.of(plot.subLevel()).getLinearVelocity(new Vector3d())
                    + " jug=" + jug.position() + " jugBox=" + jug.getBoundingBox() + " floor=" + jug.onGround()
                    + " tracking=" + ((EntityMovementExtension)(Object)jug).sable$getTrackingSubLevel());
            ExteriorImpactSample sample = contact.get();
            if (sample == null) {
                if (waited == 10 || waited == 25 || waited == 40) System.out.println("EXTERIOR_TRANSLATION_PROGRESS t="
                        + waited + " pose=" + plot.subLevel().logicalPose() + " hull="
                        + plot.subLevel().boundingBox().toMojang() + " bodyV="
                        + RigidBodyHandle.of(plot.subLevel()).getLinearVelocity(new Vector3d())
                        + " jug=" + jug.position() + " jugBox=" + jug.getBoundingBox());
                helper.runAfterDelay(1, () -> poll(waited + 1)); return;
            }
            System.out.println("SHUTTLE_EXTERIOR_IMPACT phase=" + (sample.capture() == null ? "outside-move" : "move")
                    + " seq=" + (sample.capture() == null ? null : sample.capture().sequence())
                    + " requested=" + (sample.capture() == null ? null : sample.capture().requested())
                    + " accepted=" + (sample.capture() == null ? null : sample.capture().acceptedOwnDelta())
                    + " totalMps=" + sample.velocityMps() + " inherited=" + sample.inheritedMotion()
                    + " normal=" + sample.contactNormal() + " normalMps=" + sample.velocityMps().dot(sample.contactNormal())
                    + " frame=" + sample.frameId() + " grounded=" + sample.onGround());
            require(sample.frameId() == null, "the impacted jug remains in the world, not tracked as deck cargo");
            require(sample.capture() == null || sample.capture().requested().lengthSqr() > 1.0e-8,
                    "in-move impact is observed during a normal nonzero native jug move");
            require(Math.abs(sample.velocityMps().x) > .05,
                    "first fresh side-contact tick has measurable outgoing horizontal response");
            require(sample.velocityMps().dot(sample.contactNormal()) > .05,
                    "first fresh native side-contact response has outward normal velocity");
            helper.runAfterDelay(2, () -> {
                var after = LoadedFrameRayQuery.currentActorPoint(level, jug);
                boolean floorSettled = jug.onGround() && jug.settled();
                System.out.println("EXTERIOR_TRANSLATION_LATE contactPos=" + sample.position()
                        + " contactVelocityMps=" + sample.velocityMps() + " laterPos=" + jug.position()
                        + " laterVelocity=" + jug.getDeltaMovement() + " settled=" + jug.settled()
                        + " onGround=" + jug.onGround() + " floorSettled=" + floorSettled
                        + " frame=" + (after == null ? null : after.frameId()));
                require(after != null && after.frameId() == null,
                        "post-impact jug remains detached from the swept native deck");
                require(jug.position().subtract(sample.position()).horizontalDistance() > .02,
                        "normal jug movement persists after the deck has swept past contact");
                require(Math.abs(jug.getDeltaMovement().x * 20) > .05 || floorSettled,
                        "post-separation velocity remains outward or item naturally settles on world floor");
                helper.succeed();
            });
        }

    }

    @GameTestTags({"shuttle-cargo-motion", "shuttle-frame-transition", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 140)
    public static void cargoBoardsFirstMovingNativeDeckWithPointVelocity(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1400, 300, 640), 8, (level, plot, storage) -> {
            RegisteredCargo root = createFilledBeaker(level,
                    deckFeet(plot, storage, 2, 2).add(0, .15, 0), "cargo-boarding-");
            new CargoFrameTransitionProbe(helper, level, plot, null, storage, root,
                    CargoTransitionKind.BOARDING).register();
        });
    }

    @GameTestTags({"shuttle-cargo-motion", "shuttle-frame-transition", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void cargoCrossesDirectlyBetweenIndependentNativeDecks(GameTestHelper helper) {
        withDeckPair(helper, new BlockPos(1440, 300, 640), .25, (plotA, storageA, plotB, storageB) -> {
            ServerLevel level = helper.getLevel();
            RegisteredCargo root = createFilledBeaker(level, deckFeetAt(plotA, storageA, 5.7, 2.5),
                    "cargo-frame-swap-");
            new CargoFrameTransitionProbe(helper, level, plotA, plotB, storageA, root,
                    CargoTransitionKind.FRAME_SWAP).register();
        });
    }

    @GameTestTags({"shuttle-puddle-cross-grid"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void puddleOverflowFindsOffsetYawedLowerFrameBesideLocalNeighbor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sceneOrigin = new BlockPos(3000, 250, 3000);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, 24, 64, 24);
        SableFixtureScene.awaitEmpty(helper, scene, 40, () -> {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[CHUNK_READINESS_DIAGNOSTIC] fixture=puddle-overflow phase=READY_BEFORE_AUTHOR testTick={} min={} max={}",
                    helper.getTick(), scene.tickets.diagnostic(scene.min), scene.tickets.diagnostic(scene.max));
            var upper = allocateDeck(level, new Vector3d(sceneOrigin.getX() + 10.5, 300, sceneOrigin.getZ() + 10.5));
            var lower = allocateDeck(level, new Vector3d(sceneOrigin.getX() + 13.75, 296, sceneOrigin.getZ() + 11.2));
            SableFixtureScene.trackPlot(helper, upper.plot());
            SableFixtureScene.trackPlot(helper, lower.plot());
            RigidBodyHandle.of(lower.plot().subLevel()).addLinearAndAngularVelocity(
                    new Vector3d(), new Vector3d(0, .5, 0));
            awaitPuddleFrameYaw(helper, upper.plot(), upper.result(), lower.plot(), lower.result(), 0);
        });
    }

    private static void awaitPuddleFrameYaw(GameTestHelper helper, SableFixturePlot upper, BlockPos upperStorage,
                                             SableFixturePlot lower, BlockPos lowerStorage, int waited) {
        double yaw = frameYaw(lower);
        if (Math.abs(yaw) < .01) {
            if (waited >= 8) throw new GameTestAssertException("lower native frame did not acquire measured yaw");
            helper.runAfterDelay(1, () -> awaitPuddleFrameYaw(helper, upper, upperStorage, lower, lowerStorage, waited + 1));
            return;
        }
        BlockPos upperFloor = upperStorage;
        BlockPos lowerFloor = lowerStorage;
        for (int x = 0; x < DECK_SIZE; x++) for (int z = 0; z < DECK_SIZE; z++) {
            upper.setStorageBlock(upperFloor.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            lower.setStorageBlock(lowerFloor.offset(x, 0, z), Blocks.STONE.defaultBlockState());
        }
        BlockPos sourcePos = upperFloor.offset(DECK_SIZE - 1, 1, 2);
        BlockPos localNeighbor = upperFloor.offset(DECK_SIZE - 2, 1, 2);
        for (BlockPos wall : new BlockPos[]{sourcePos.north(), sourcePos.south(), localNeighbor.north(),
                localNeighbor.south(), localNeighbor.west()})
            upper.setStorageBlock(wall, Blocks.STONE.defaultBlockState());
        upper.setStorageBlock(sourcePos, com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get().defaultBlockState());
        upper.setStorageBlock(localNeighbor, com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get().defaultBlockState());
        var source = (com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity) helper.getLevel().getBlockEntity(sourcePos);
        var neighbor = (com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity) helper.getLevel().getBlockEntity(localNeighbor);
        require(source != null && neighbor != null, "authored upper-edge and interior puddle block entities exist");
        fillPuddleForNativeOverflow(source, 60f);
        fillPuddleForNativeOverflow(neighbor, .1f);
        long expected = MS14Provider.get(source, MS14Bridges.REAGENT).totalUnits()
                + MS14Provider.get(neighbor, MS14Bridges.REAGENT).totalUnits();
        require(!upper.engineId().equals(lower.engineId()), "overflow fixtures are independent native frames");
        require(Math.abs(frameYaw(lower)) > .01, "lower frame yaw is measured from its live native pose");
        require(Math.abs(frameYaw(upper)) < .01, "upper source frame remains unrotated");
        pollNativePuddleOverflow(helper, upper, upperFloor, lower, lowerFloor, expected, 0);
    }

    private static void pollNativePuddleOverflow(GameTestHelper helper, SableFixturePlot upper, BlockPos upperFloor,
                                                  SableFixturePlot lower, BlockPos lowerFloor, long expected, int waited) {
        long lowerUnits = puddleUnitsInDeck(helper.getLevel(), lowerFloor);
        if (lowerUnits > 0) {
            long total = puddleUnitsInDeck(helper.getLevel(), upperFloor) + lowerUnits;
            require(total == expected, "normal puddle overflow conserves reagent cents across both native decks");
            helper.succeed();
            return;
        }
        if (waited >= 30) throw new GameTestAssertException("live puddle heartbeat did not reach the offset yawed lower frame; "
                + "upper=" + upper.subLevel().logicalPose() + " lower=" + lower.subLevel().logicalPose()
                + " upperUnits=" + puddleUnitsInDeck(helper.getLevel(), upperFloor));
        helper.runAfterDelay(1, () -> pollNativePuddleOverflow(helper, upper, upperFloor, lower, lowerFloor, expected, waited + 1));
    }

    private static long puddleUnitsInDeck(ServerLevel level, BlockPos floor) {
        long total = 0;
        for (int x = 0; x < DECK_SIZE; x++) for (int y = 1; y <= 2; y++) for (int z = 0; z < DECK_SIZE; z++) {
            BlockPos pos = floor.offset(x, y, z);
            if (level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity puddle)
                total += MS14Provider.get(puddle, MS14Bridges.REAGENT).totalUnits();
        }
        return total;
    }

    private static void fillPuddleForNativeOverflow(com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity puddle,
                                                     float volume) {
        var contents = new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment();
        contents.specificAdd(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water"), volume,
                puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);
    }

    private static RegisteredCargo createFilledBeaker(ServerLevel level, Vec3 position, String tokenPrefix) {
        var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(12.5f);
        var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BEAKER_LARGE.get());
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water, cents)));
        return registerCargoRoot(level, position, stack, tokenPrefix);
    }

    private enum CargoTransitionKind { ROTATING_DEPARTURE, BOARDING, FRAME_SWAP }

    /** One pre/post sampler exercises normal cargo ticks only; assertions run from GameTest callbacks. */
    private static final class CargoFrameTransitionProbe {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot frameA;
        private final SableFixturePlot frameB;
        private final BlockPos storageA;
        private final RegisteredCargo root;
        private final CargoTransitionKind kind;
        private final net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water =
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        private final long cents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(12.5f);
        private final AtomicReference<CargoTransitionPre> before = new AtomicReference<>();
        private final AtomicReference<CargoTransitionPost> transition = new AtomicReference<>();
        private java.util.function.Consumer<EntityTickEvent.Pre> beforeListener;
        private java.util.function.Consumer<EntityTickEvent.Post> afterListener;
        private boolean armed;

        CargoFrameTransitionProbe(GameTestHelper helper, ServerLevel level, SableFixturePlot frameA,
                                  SableFixturePlot frameB, BlockPos storageA, RegisteredCargo root,
                                  CargoTransitionKind kind) {
            this.helper=helper; this.level=level; this.frameA=frameA; this.frameB=frameB;
            this.storageA=storageA; this.root=root; this.kind=kind;
        }

        void register() {
            beforeListener = event -> {
                if (event.getEntity() != root.entity() || transition.get() != null) return;
                var cargo = root.entity();
                var movement = (EntityMovementExtension) (Object) cargo;
                var tracking = movement.sable$getTrackingSubLevel();
                UUID trackingId = tracking == null ? null : tracking.getUniqueId();
                var point = LoadedFrameRayQuery.currentActorPoint(level, cargo);
                Vector3d pointMps = velocityAt(point);
                var body = RigidBodyHandle.of(frameA.subLevel());
                Vector3d com = body.getLinearVelocity(new Vector3d());
                Vector3d angular = body.getAngularVelocity(new Vector3d());
                before.set(new CargoTransitionPre(level.getGameTime(), movement.sable$getCollisionInfo(),
                        cargo.nativeMoveCapture(), cargo.getDeltaMovement().scale(20), point, trackingId,
                        pointMps, com, angular, cargo.onGround(), cargo.settled(), cargo.isNoGravity(), cargo.noPhysics));
            };
            afterListener = event -> {
                if (event.getEntity() != root.entity() || transition.get() != null) return;
                CargoTransitionPre old = before.get();
                if (old == null || old.gameTime() != level.getGameTime() || !armed) return;
                var cargo = root.entity();
                var capture = cargo.nativeMoveCapture();
                if (capture == null || !capture.collisionInfoFresh() || capture.beforeActorPoint() == null
                        || capture.afterActorPoint() == null
                        || java.util.Objects.equals(capture.beforeActorPoint().frameId(), capture.afterActorPoint().frameId())) return;
                var movement = (EntityMovementExtension) (Object) cargo;
                var tracked = movement.sable$getTrackingSubLevel();
                UUID finalTracking = tracked == null ? null : tracked.getUniqueId();
                if (!matchesTransition(capture.beforeActorPoint().frameId(), capture.afterActorPoint().frameId(), finalTracking)) return;
                Vector3d oldPoint = old.pointVelocityMps();
                if (oldPoint == null) return;
                Vector3d newPoint = velocityAt(capture.afterActorPoint());
                if (newPoint == null || capture.frameVelocityDeltaPerSecond() == null) return;
                Vec3 ownPostMps = cargo.getDeltaMovement().scale(20);
                Vector3d expected = new Vector3d(old.ownVelocityMps().x + oldPoint.x - newPoint.x,
                        old.ownVelocityMps().y + oldPoint.y - newPoint.y,
                        old.ownVelocityMps().z + oldPoint.z - newPoint.z);
                Vec3 actualDelta = capture.frameVelocityDeltaPerSecond();
                double sharedMathError = new Vector3d(actualDelta.x, actualDelta.y, actualDelta.z)
                        .sub(oldPoint).add(newPoint).length();
                double ownError = kind == CargoTransitionKind.FRAME_SWAP
                        ? Math.abs(ownPostMps.z - expected.z)
                        : Math.hypot(ownPostMps.x - expected.x, ownPostMps.z - expected.z);
                CargoTransitionPost result = new CargoTransitionPost(capture, old, ownPostMps, oldPoint, newPoint,
                        expected, ownError,
                        sharedMathError, finalTracking, currentOwnIdentity());
                transition.set(result);
            };
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { NeoForge.EVENT_BUS.unregister(beforeListener); NeoForge.EVENT_BUS.unregister(afterListener); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
            NeoForge.EVENT_BUS.addListener(beforeListener);
            NeoForge.EVENT_BUS.addListener(afterListener);
            if (kind == CargoTransitionKind.BOARDING) armBoarding();
            else awaitSettledAndArm(0);
        }

        private void armBoarding() {
            var cargo = root.entity();
            var point = LoadedFrameRayQuery.currentActorPoint(level, cargo);
            require(point != null && point.frameId() == null
                            && ((EntityMovementExtension) (Object) cargo).sable$getTrackingSubLevel() == null
                            && !cargo.onGround() && !cargo.settled(),
                    "boarding cargo begins airborne in ordinary world before its first deck contact");
            require(cargo.getDeltaMovement().horizontalDistanceSqr() < 1.0e-12,
                    "first-contact boarding begins with zero own-horizontal velocity and no pull impulse");
            require(!cargo.isNoGravity() && !cargo.noPhysics, "boarding uses normal gravity and collision");
            RigidBodyHandle.of(frameA.subLevel()).addLinearAndAngularVelocity(new Vector3d(2, 0, 2), new Vector3d());
            armed = true;
            System.out.println("SHUTTLE_CARGO_BOARD_ARMED worldPoint=" + point + " own=" + cargo.getDeltaMovement());
            poll(0);
        }

        private void awaitSettledAndArm(int waited) {
            if (waited >= 90) throw new GameTestAssertException("cargo did not establish settled native support: " + root.entity());
            var cargo = root.entity();
            var point = LoadedFrameRayQuery.currentActorPoint(level, cargo);
            var capture = cargo.nativeMoveCapture();
            if (!cargo.settled() || !cargo.onGround() || point == null || !frameA.engineId().equals(point.frameId())
                    || capture == null || !capture.collisionInfoFresh()) {
                helper.runAfterDelay(1, () -> awaitSettledAndArm(waited + 1));
                return;
            }
            require(((EntityMovementExtension) (Object) cargo).sable$getTrackingSubLevel() != null
                            && ((EntityMovementExtension) (Object) cargo).sable$getTrackingSubLevel().getUniqueId()
                            .equals(frameA.engineId()), "cargo is actually settled and tracked on its expected native deck");
            if (kind == CargoTransitionKind.ROTATING_DEPARTURE) armRotatingDeparture(point);
            else armFrameSwap(point);
            armed = true;
            poll(0);
        }

        private void armRotatingDeparture(LoadedFrameRayQuery.ActorPoint point) {
            var handle = RigidBodyHandle.of(frameA.subLevel());
            handle.addLinearAndAngularVelocity(new Vector3d(.5, 0, .5), new Vector3d(0, .8, 0));
            BlockPos feet = BlockPos.containing(point.storagePoint());
            removeOwnedSupport(frameA, feet);
            System.out.println("SHUTTLE_CARGO_ROTATE_ARMED frame=" + frameA.engineId() + " storage="
                    + point.storagePoint() + " ownMps=" + root.entity().getDeltaMovement().scale(20));
        }

        private void armFrameSwap(LoadedFrameRayQuery.ActorPoint point) {
            require(frameB != null && frameA.engineId().equals(point.frameId()),
                    "direct-swap beaker begins settled on exact frame A");
            RigidBodyHandle.of(frameA.subLevel()).addLinearAndAngularVelocity(new Vector3d(0, 0, 4), new Vector3d());
            RigidBodyHandle.of(frameB.subLevel()).addLinearAndAngularVelocity(new Vector3d(0, 0, -4), new Vector3d());
            root.entity().launch(new Vec3(.3, .04, 0), 0);
            System.out.println("SHUTTLE_CARGO_SWAP_ARMED A=" + frameA.engineId() + " B=" + frameB.engineId()
                    + " point=" + point.storagePoint() + " own=" + root.entity().getDeltaMovement().scale(20));
        }

        private void removeOwnedSupport(SableFixturePlot plot, BlockPos feet) {
            for (int x = feet.getX() - 1; x <= feet.getX(); x++) for (int z = feet.getZ() - 1; z <= feet.getZ(); z++)
                require(plot.setStorageBlock(new BlockPos(x, feet.getY() - 1, z), Blocks.AIR.defaultBlockState()),
                        "only cargo-owned native support is removed");
        }

        private boolean matchesTransition(UUID oldId, UUID newId, UUID finalTracking) {
            return switch (kind) {
                case ROTATING_DEPARTURE -> frameA.engineId().equals(oldId) && newId == null && finalTracking == null;
                case BOARDING -> oldId == null && frameA.engineId().equals(newId)
                        && frameA.engineId().equals(finalTracking);
                case FRAME_SWAP -> frameA.engineId().equals(oldId) && frameB.engineId().equals(newId)
                        && frameB.engineId().equals(finalTracking);
            };
        }

        private Vector3d velocityAt(LoadedFrameRayQuery.ActorPoint point) {
            if (point == null) return null;
            if (point.frameId() == null) return new Vector3d();
            SableFixturePlot owner = frameA.engineId().equals(point.frameId()) ? frameA
                    : frameB != null && frameB.engineId().equals(point.frameId()) ? frameB : null;
            if (owner == null || point.storagePoint() == null
                    || !owner.engineId().equals(point.frameId())) return null;
            Vector3d velocity = new Vector3d();
            Sable.HELPER.getVelocity(level, owner.subLevel(), new Vector3d(point.storagePoint().x,
                    point.storagePoint().y, point.storagePoint().z), velocity);
            return Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)
                    ? velocity : null;
        }

        private boolean currentOwnIdentity() {
            var entity = root.entity();
            var location = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                    .forLevel(level).snapshot().locationOf(root.token());
            var reagent = entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            return level.getEntity(entity.getUUID()) == entity && !entity.isRemoved() && reagent != null
                    && reagent.centContents().getOrDefault(water, 0L) == cents
                    && location.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                            root.world(), entity.getUUID())::equals).isPresent();
        }

        private void poll(int waited) {
            if (waited >= 30) throw new GameTestAssertException("no direct expected native cargo frame transition: " +
                    "kind=" + kind + " current=" + LoadedFrameRayQuery.currentActorPoint(level, root.entity())
                            + " capture=" + root.entity().nativeMoveCapture());
            CargoTransitionPost result = transition.get();
            if (result == null) { helper.runAfterDelay(1, () -> poll(waited + 1)); return; }
            verifyTransition(result);
        }

        private void verifyTransition(CargoTransitionPost result) {
            var capture = result.capture();
            System.out.println("SHUTTLE_CARGO_TRANSITION kind=" + kind + " seq=" + capture.sequence()
                    + " old=" + capture.beforeActorPoint().frameId() + " new=" + capture.afterActorPoint().frameId()
                    + " fresh=" + capture.collisionInfoFresh() + " request=" + capture.requested()
                    + " acceptedOwn=" + capture.acceptedOwnDelta() + " oldPointMps=" + result.oldPointMps()
                    + " newPointMps=" + result.newPointMps() + " frameDeltaMps="
                    + capture.frameVelocityDeltaPerSecond() + " ownPreMps=" + result.pre().ownVelocityMps()
                    + " ownPostMps=" + result.ownPostMps() + " expectedMps=" + result.expectedOwnMps()
                    + " testedError=" + result.ownHorizontalError() + " identity=" + result.registeredRoot());
            require(capture.collisionInfoFresh() && capture.beforeActorPoint() != null
                            && capture.afterActorPoint() != null && result.finalTrackingId() != null
                            == (kind != CargoTransitionKind.ROTATING_DEPARTURE),
                    "one fresh normal owner move has exact final tracking for this transition");
            require(result.sharedMathError() < .02, "shared SDK helper equals independently measured old-minus-new point velocity");
            require(result.ownHorizontalError() <= .02,
                    "cargo post-tick own horizontal velocity matches pre-own plus signed SDK point delta: "
                            + result.ownHorizontalError());
            require(result.registeredRoot(), "exact registered filled beaker, token, and 12.5-unit water survive transition");
            require(!result.pre().noGravity() && !result.pre().noPhysics()
                            && !root.entity().isNoGravity() && !root.entity().noPhysics,
                    "transition is produced by normal cargo gravity and collision");
            switch (kind) {
                case ROTATING_DEPARTURE -> verifyRotatingDeparture(result);
                case BOARDING -> verifyBoarding(result);
                case FRAME_SWAP -> verifyFrameSwap(result);
            }
        }

        private void verifyRotatingDeparture(CargoTransitionPost result) {
            Vector3d linear = result.pre().frameLinearMps();
            Vector3d angular = result.pre().frameAngularRadPerSecond();
            double angularPointContribution = Math.hypot(result.oldPointMps().x - linear.x,
                    result.oldPointMps().z - linear.z);
            require(angular.length() > .1 && angular.y > .1 && angularPointContribution > .05,
                    "measured cargo old-point velocity includes nontrivial actual native angular motion");
            require(Math.hypot(result.oldPointMps().x, result.oldPointMps().z) > .1,
                    "rotating-frame departure carries measurable SDK point velocity");
            require(result.oldPointMps().distance(result.newPointMps()) > .1,
                    "frame-to-world delta uses old native point minus valid world zero");
            System.out.println("SHUTTLE_CARGO_ROTATING_DEPARTURE seq=" + result.capture().sequence()
                    + " ownRequested=" + result.capture().requested() + " ownAccepted="
                    + result.capture().acceptedOwnDelta() + " oldPointMps=" + result.oldPointMps()
                    + " COMmps=" + linear + " angularRadPerSec=" + angular + " angularContribution="
                    + angularPointContribution + " newPointMps=" + result.newPointMps() + " ownPre="
                    + result.pre().ownVelocityMps() + " ownPost=" + result.ownPostMps()
                    + " error=" + result.ownHorizontalError());
            awaitPostDepartureTick(0, result.ownPostMps());
        }

        private void verifyBoarding(CargoTransitionPost result) {
            require(result.pre().trackingId() == null && result.finalTrackingId() != null
                            && frameA.engineId().equals(result.finalTrackingId()),
                    "first actual cargo contact boards directly from world into expected live frame");
            require(Math.hypot(result.newPointMps().x, result.newPointMps().z) > .1,
                    "newly boarded frame has measured nonzero SDK point velocity");
            var currentPoint = LoadedFrameRayQuery.currentActorPoint(level, root.entity());
            require(root.entity().onGround() && currentPoint != null && frameA.engineId().equals(currentPoint.frameId()),
                    "boarding transition ends in current native deck contact on the expected frame");
            require(!root.entity().settled() && root.entity().getDeltaMovement().horizontalDistance() > 1.0e-4,
                    "transition tick does not auto-settle and erase its newly inherited frame velocity");
            helper.succeed();
        }

        private void verifyFrameSwap(CargoTransitionPost result) {
            require(frameA.engineId().equals(result.pre().trackingId())
                            && frameB.engineId().equals(result.finalTrackingId()),
                    "cargo crosses directly from exact frame A to independent frame B");
            require(Math.abs(result.oldPointMps().z - result.newPointMps().z) > .1,
                    "opposed independent-frame SDK Z point velocities make transition basis nonvacuous");
            require(Math.abs(result.ownPostMps().z - result.expectedOwnMps().z) <= .02,
                    "cargo Z velocity preserves the measured old-minus-new map-point basis");
            require(root.entity().onGround(), "cargo resolves support on frame B after direct transition");
            helper.succeed();
        }

        private void awaitPostDepartureTick(int waited, Vec3 previousOwnMps) {
            if (waited >= 3) throw new GameTestAssertException("cargo did not perform next automatic world tick");
            helper.runAfterDelay(1, () -> {
                var capture = root.entity().nativeMoveCapture();
                var point = LoadedFrameRayQuery.currentActorPoint(level, root.entity());
                if (capture == null || capture.sequence() <= transition.get().capture().sequence()
                        || point == null || point.frameId() != null) {
                    awaitPostDepartureTick(waited + 1, previousOwnMps); return;
                }
                require(capture.collisionInfoFresh() && capture.frameVelocityDeltaPerSecond() == null,
                        "next normal world owner tick is fresh without replaying native point delta");
                Vec3 actual = root.entity().getDeltaMovement().scale(20);
                double error = Math.hypot(actual.x - previousOwnMps.x * .995,
                        actual.z - previousOwnMps.z * .995);
                require(error <= .08, "airborne cargo receives only existing .995 horizontal damping after departure: " + error);
                require(currentOwnIdentity(), "departed rotating cargo keeps same registered reagent root");
                System.out.println("SHUTTLE_CARGO_ROTATING_AIRBORNE seq=" + capture.sequence() + " velocityMps="
                        + actual + " dragError=" + error + " point=" + point);
                helper.succeed();
            });
        }

        private record CargoTransitionPre(long gameTime, Object previousCollisionInfo,
                                          com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture previousCapture,
                                          Vec3 ownVelocityMps, LoadedFrameRayQuery.ActorPoint point,
                                          UUID trackingId, Vector3d pointVelocityMps,
                                          Vector3d frameLinearMps, Vector3d frameAngularRadPerSecond,
                                          boolean onGround, boolean settled, boolean noGravity, boolean noPhysics) { }

        private record CargoTransitionPost(
                com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture capture,
                CargoTransitionPre pre, Vec3 ownPostMps, Vector3d oldPointMps, Vector3d newPointMps,
                Vector3d expectedOwnMps, double ownHorizontalError, double sharedMathError,
                UUID finalTrackingId, boolean registeredRoot) { }
    }

    private static final class CargoDepartureMonitor {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final BlockPos storage;
        private final RegisteredCargo root;
        private final net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water;
        private final long cents;
        private final AtomicReference<CargoDeparturePre> pre = new AtomicReference<>();
        private final AtomicReference<CargoDeparturePost> departure = new AtomicReference<>();
        private java.util.function.Consumer<EntityTickEvent.Pre> beforeListener;
        private java.util.function.Consumer<EntityTickEvent.Post> afterListener;
        private UUID frameId;

        CargoDepartureMonitor(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, BlockPos storage,
                              RegisteredCargo root,
                              net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water,
                              long cents) {
            this.helper=helper; this.level=level; this.plot=plot; this.storage=storage;
            this.root=root; this.water=water; this.cents=cents;
        }

        void register() {
            beforeListener = event -> {
                if (event.getEntity() != root.entity() || departure.get() != null) return;
                var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, root.entity());
                var movement = (EntityMovementExtension) (Object) root.entity();
                var tracked = movement.sable$getTrackingSubLevel();
                UUID trackedId = tracked == null ? null : tracked.getUniqueId();
                Vector3d pointMps = point != null && frameId != null && frameId.equals(point.frameId())
                        ? pointVelocity(level, plot, point) : null;
                pre.set(new CargoDeparturePre(level.getGameTime(), root.entity().nativeMoveCapture(),
                        root.entity().getDeltaMovement().scale(20), point, trackedId, pointMps));
            };
            afterListener = event -> {
                if (event.getEntity() != root.entity() || departure.get() != null) return;
                CargoDeparturePre previous = pre.get();
                if (previous == null || previous.gameTime() != level.getGameTime()) return;
                var capture = root.entity().nativeMoveCapture();
                if (frameId == null) return;
                if (capture == null || !capture.collisionInfoFresh() || capture.beforeActorPoint() == null
                        || !frameId.equals(capture.beforeActorPoint().frameId()) || capture.afterActorPoint() == null
                        || capture.afterActorPoint().frameId() != null) return;
                var tracking = ((EntityMovementExtension) (Object) root.entity()).sable$getTrackingSubLevel();
                if (tracking != null || capture.frameVelocityDeltaPerSecond() == null) return;
                Vec3 ownPostMps = root.entity().getDeltaMovement().scale(20);
                Vector3d sdkOldPoint = previous.pointVelocityMps();
                if (sdkOldPoint == null) sdkOldPoint = pointVelocity(level, plot, capture.beforeActorPoint());
                double expectedX = previous.ownVelocityMps().x + sdkOldPoint.x;
                double expectedZ = previous.ownVelocityMps().z + sdkOldPoint.z;
                double error = Math.hypot(ownPostMps.x - expectedX, ownPostMps.z - expectedZ);
                var location = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                        .forLevel(level).snapshot().locationOf(root.token());
                var reagent = root.entity().getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
                long content = reagent == null ? 0L : reagent.centContents().getOrDefault(water, 0L);
                boolean registered = level.getEntity(root.entity().getUUID()) == root.entity()
                        && location.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                                root.world(), root.entity().getUUID())::equals).isPresent() && content == cents;
                CargoDeparturePost result = new CargoDeparturePost(capture, previous, ownPostMps,
                        new Vec3(sdkOldPoint.x, sdkOldPoint.y, sdkOldPoint.z), error, registered);
                departure.set(result);
                System.out.println("SHUTTLE_CARGO_DEPARTURE seq=" + capture.sequence() + " requested=" + capture.requested()
                        + " acceptedOwn=" + capture.acceptedOwnDelta() + " oldPointMps=" + result.oldPointMps()
                        + " sharedDeltaMps=" + capture.frameVelocityDeltaPerSecond() + " ownPreMps="
                        + previous.ownVelocityMps() + " ownPostMps=" + ownPostMps + " horizontalError=" + error
                        + " oldFrame=" + capture.beforeActorPoint().frameId() + " newFrame="
                        + capture.afterActorPoint().frameId() + " fresh=" + capture.collisionInfoFresh()
                        + " registeredRoot=" + registered);
            };
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { NeoForge.EVENT_BUS.unregister(beforeListener); NeoForge.EVENT_BUS.unregister(afterListener); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
            NeoForge.EVENT_BUS.addListener(beforeListener);
            NeoForge.EVENT_BUS.addListener(afterListener);
            awaitSettled(0);
        }

        private void awaitSettled(int waited) {
            if (waited >= 100) throw new GameTestAssertException("registered beaker did not settle on native deck");
            var entity = root.entity();
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, entity);
            var capture = entity.nativeMoveCapture();
            if (!entity.settled() || point == null || !plot.engineId().equals(point.frameId()) || capture == null
                    || !capture.collisionInfoFresh()) {
                helper.runAfterDelay(1, () -> awaitSettled(waited + 1));
                return;
            }
            frameId = point.frameId();
            require(frameId.equals(((EntityMovementExtension) (Object) entity).sable$getTrackingSubLevel().getUniqueId()),
                    "beaker is settled and tracked on the exact live native frame before support removal");
            RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            BlockPos feet = BlockPos.containing(point.storagePoint());
            int writeIndex = 0;
            String firstFailedWitness = null;
            for (int x = feet.getX() - 1; x <= feet.getX(); x++) for (int z = feet.getZ() - 1; z <= feet.getZ(); z++) {
                // Select the authored deck directly; tiny native settling residuals can put rawFeet below it.
                BlockPos pos = new BlockPos(x, storage.getY(), z);
                boolean registeredBefore = SubLevelContainer.getContainer(level).getSubLevel(plot.engineId()) == plot.subLevel();
                var beforeState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), pos);
                boolean writeResult = plot.setStorageBlock(pos, Blocks.AIR.defaultBlockState());
                var afterState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), pos);
                boolean registeredAfter = SubLevelContainer.getContainer(level).getSubLevel(plot.engineId()) == plot.subLevel();
                String witness = "index=" + writeIndex + " pos=" + pos + " before=" + beforeState
                        + " result=" + writeResult + " after=" + afterState + " storageY=" + storage.getY()
                        + " point=" + point.storagePoint() + " rawFeet=" + feet + " captureSeq=" + capture.sequence()
                        + " registeredBefore=" + registeredBefore + " registeredAfter=" + registeredAfter
                        + " plotRemoved=" + plot.subLevel().isRemoved();
                System.out.println("SHUTTLE_CARGO_DEPARTURE_SUPPORT_WRITE " + witness);
                if (!writeResult && firstFailedWitness == null) firstFailedWitness = witness;
                require(registeredBefore && !plot.subLevel().isRemoved() && beforeState != null
                                && beforeState.is(Blocks.STONE),
                        "selected authored deck support is known stone on the registered live plot; witness=" + witness);
                require(writeResult, "owned native support write succeeded; witness=" + witness
                        + (firstFailedWitness == null ? "" : " firstFailed=" + firstFailedWitness));
                require(afterState != null && afterState.isAir() && registeredAfter && !plot.subLevel().isRemoved(),
                        "support removal leaves known air and exact plot registered; witness=" + witness);
                writeIndex++;
            }
            System.out.println("SHUTTLE_CARGO_DEPARTURE_ARMED frame=" + frameId + " seq=" + capture.sequence()
                    + " storage=" + point.storagePoint() + " own=" + entity.getDeltaMovement().scale(20));
            awaitDeparture(0);
        }

        private void awaitDeparture(int waited) {
            if (waited >= 12) throw new GameTestAssertException("cargo did not observe fresh native-to-world tracking change: "
                    + root.entity().nativeMoveCapture());
            CargoDeparturePost result = departure.get();
            if (result == null) { helper.runAfterDelay(1, () -> awaitDeparture(waited + 1)); return; }
            require(result.capture().acceptedOwnDelta().y < 0,
                    "departure is caused during actual downward own motion, not carrier-only displacement");
            require(result.oldPointMps().horizontalDistance() > .1,
                    "departed native point carries measurable horizontal SDK velocity");
            require(result.capture().frameVelocityDeltaPerSecond() != null
                            && result.capture().frameVelocityDeltaPerSecond().subtract(result.oldPointMps()).horizontalDistance() < .02,
                    "shared exact-point velocity math maps old native point to world zero once");
            require(result.horizontalError() <= .04,
                    "automatic cargo tick preserves SDK old-point horizontal velocity within .04 m/s");
            require(result.registeredRoot(), "departure retains exact registered beaker root/token/reagent contents");
            awaitAirborne(0, result.ownPostMps());
        }

        private void awaitAirborne(int waited, Vec3 previousMps) {
            if (waited >= 8) throw new GameTestAssertException("departed beaker failed to fall to bounded world corridor");
            helper.runAfterDelay(1, () -> {
                var entity = root.entity();
                var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, entity);
                var tracking = ((EntityMovementExtension) (Object) entity).sable$getTrackingSubLevel();
                var capture = entity.nativeMoveCapture();
                require(point != null && point.frameId() == null && tracking == null,
                        "cargo remains a valid world actor after native departure");
                require(capture != null && capture.collisionInfoFresh() && capture.frameVelocityDeltaPerSecond() == null,
                        "next world movement is fresh but does not replay native frame momentum");
                Vec3 actual = entity.getDeltaMovement().scale(20);
                double error = Math.hypot(actual.x - previousMps.x * .995, actual.z - previousMps.z * .995);
                require(error <= .08, "airborne friction decays inherited map velocity once without another frame delta: " + error);
                var location = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                        .forLevel(level).snapshot().locationOf(root.token());
                var reagent = entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
                long content = reagent == null ? 0L : reagent.centContents().getOrDefault(water, 0L);
                require(level.getEntity(entity.getUUID()) == entity && content == cents
                                && location.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                                        root.world(), entity.getUUID())::equals).isPresent(),
                        "same cargo identity and reagent owner survive post-transition gravity tick; reagentMissing="
                                + (reagent == null));
                System.out.println("SHUTTLE_CARGO_AIRBORNE seq=" + capture.sequence() + " own="
                        + capture.acceptedOwnDelta() + " velocityMps=" + actual + " horizontalError=" + error
                        + " point=" + point);
                helper.succeed();
            });
        }

        private record CargoDeparturePre(long gameTime,
                com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture previousCapture,
                Vec3 ownVelocityMps,
                com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point,
                UUID trackingId, Vector3d pointVelocityMps) { }

        private record CargoDeparturePost(
                com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture capture,
                CargoDeparturePre previous, Vec3 ownPostMps, Vec3 oldPointMps,
                double horizontalError, boolean registeredRoot) { }
    }

    public static RegisteredCargo registerCargoRoot(ServerLevel level, Vec3 position,
            net.minecraft.world.item.ItemStack stack, String tokenPrefix) {
        var entity = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, position, stack);
        require(level.addFreshEntity(entity), "registered mod loose cargo admitted: " + stack.getItem());
        UUID world = com.juicyslew.moonstation14.ms14.disposal.item.DisposalWorldPayloadStage.worldIdentity(level);
        var token = new com.juicyslew.moonstation14.ms14.hands.ItemToken(tokenPrefix + UUID.randomUUID());
        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(level);
        require(ledger.register(token, new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                world, entity.getUUID())).accepted(), "normal ownership ledger admits exact cargo root");
        return new RegisteredCargo(entity, token, world);
    }

    public record RegisteredCargo(com.juicyslew.moonstation14.entities.LooseItemEntity entity,
                                    com.juicyslew.moonstation14.ms14.hands.ItemToken token, UUID world) { }

    private static final class CargoSupportMonitor {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final BlockPos storage;
        private final RegisteredCargo jug;
        private final RegisteredCargo beaker;
        private final net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water;
        private final long cents;
        private final boolean strongImpulse;
        private final AtomicReference<CargoSupportSample> jugLatest = new AtomicReference<>();
        private final AtomicReference<CargoSupportSample> beakerLatest = new AtomicReference<>();
        private java.util.function.Consumer<EntityTickEvent.Post> listener;
        private UUID frameId;

        CargoSupportMonitor(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, BlockPos storage,
                            RegisteredCargo jug, RegisteredCargo beaker,
                            net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water,
                            long cents) {
            this(helper, level, plot, storage, jug, beaker, water, cents, false);
        }

        CargoSupportMonitor(GameTestHelper helper, ServerLevel level, SableFixturePlot plot, BlockPos storage,
                            RegisteredCargo jug, RegisteredCargo beaker,
                            net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water,
                            long cents, boolean strongImpulse) {
            this.helper=helper; this.level=level; this.plot=plot; this.storage=storage;
            this.jug=jug; this.beaker=beaker; this.water=water; this.cents=cents;
            this.strongImpulse=strongImpulse;
        }

        void register() {
            listener = event -> {
                if (event.getEntity() == jug.entity()) jugLatest.set(sample(jug));
                else if (event.getEntity() == beaker.entity()) beakerLatest.set(sample(beaker));
            };
            helper.testInfo.addListener(new GameTestListener() {
                private void close() {
                    NeoForge.EVENT_BUS.unregister(listener);
                    jug.entity().setObserveNativePreCutoff(false);
                    beaker.entity().setObserveNativePreCutoff(false);
                }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
            NeoForge.EVENT_BUS.addListener(listener);
            awaitSettled(0);
        }

        private CargoSupportSample sample(RegisteredCargo root) {
            var entity = root.entity();
            var capture = entity.nativeMoveCapture();
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, entity);
            var tracking = ((EntityMovementExtension) (Object) entity).sable$getTrackingSubLevel();
            var location = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                    .forLevel(level).snapshot().locationOf(root.token());
            var reagent = entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            return new CargoSupportSample(capture, entity.position(), entity.getDeltaMovement(), point,
                    tracking == null ? null : tracking.getUniqueId(), entity.settled(), entity.onGround(),
                    !entity.isRemoved() && level.getEntity(entity.getUUID()) == entity,
                    location.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                            root.world(), entity.getUUID())::equals).isPresent(),
                    reagent == null ? 0 : reagent.centContents().getOrDefault(water, 0L));
        }

        private void awaitSettled(int waited) {
            if (waited >= 80) throw new GameTestAssertException("cargo never settled on exact native frame: " + summary());
            CargoSupportSample a = jugLatest.get(), b = beakerLatest.get();
            if (!currentFrameSupportSample(a) || !currentFrameSupportSample(b) || !a.settled() || !b.settled()) {
                helper.runAfterDelay(1, () -> awaitSettled(waited + 1));
                return;
            }
            frameId = a.point().frameId();
            require(frameId.equals(plot.engineId()) && frameId.equals(b.point().frameId())
                            && frameId.equals(a.trackingId()) && frameId.equals(b.trackingId()),
                    "both settled real cargo roots resolve through exact live native frame UUID");
            requireCargoIdentity(a, jug, "jug");
            requireCargoIdentity(b, beaker, "beaker");
            System.out.println("SHUTTLE_CARGO_SETTLED frame=" + frameId + " jugSeq=" + a.capture().sequence()
                    + " beakerSeq=" + b.capture().sequence() + " ownY=" + a.capture().acceptedOwnDelta().y
                    + "/" + b.capture().acceptedOwnDelta().y + " fresh=" + a.capture().collisionInfoFresh()
                    + "/" + b.capture().collisionInfoFresh());
            if (strongImpulse) {
                jug.entity().setObserveNativePreCutoff(true);
                beaker.entity().setObserveNativePreCutoff(true);
                impulseCarry(0, a, b, a.position(), b.position(), a.point().storagePoint(), b.point().storagePoint(),
                        Vec3.ZERO, Vec3.ZERO);
                return;
            }
            carry(0, a, b, a.position(), b.position(), a.point().storagePoint(), b.point().storagePoint());
        }

        private void impulseCarry(int tick, CargoSupportSample previous, CargoSupportSample previousBeaker,
                                  Vec3 worldStart, Vec3 beakerWorldStart, Vec3 localStart, Vec3 beakerLocalStart,
                                  Vec3 jugProvenTotal, Vec3 beakerProvenTotal) {
            if (tick == 0) RigidBodyHandle.of(plot.subLevel())
                    .addLinearAndAngularVelocity(new Vector3d(STRONG_CARGO_IMPULSE_SPEED, 0, 0), new Vector3d());
            if (tick >= STRONG_CARGO_IMPULSE_TICKS) {
                System.out.println("SHUTTLE_CARGO_STRONG_IMPULSE ticks=" + STRONG_CARGO_IMPULSE_TICKS + " frame=" + frameId
                        + " worldDelta=" + jugLatest.get().position().subtract(worldStart)
                        + " localError=" + jugLatest.get().point().storagePoint().distanceTo(localStart));
                helper.succeed();
                return;
            }
            helper.runAfterDelay(1, () -> {
                CargoSupportSample now = jugLatest.get();
                CargoSupportSample beakerNow = beakerLatest.get();
                logImpulseFreshnessFailure("jug", tick + 1, previous, now, jug, worldStart, localStart);
                logImpulseFreshnessFailure("beaker", tick + 1, previousBeaker, beakerNow, beaker, beakerWorldStart, beakerLocalStart);
                require(currentFrameSupportSample(now) && now.settled() && frameId.equals(now.point().frameId())
                                && frameId.equals(now.trackingId()), "strong-impulse tick retains settled cargo frame tracking");
                require(now.capture().sequence() == previous.capture().sequence() + 1,
                        "jug sequence advances exactly once per impulse tick");
                require(now.capture().collisionInfoFresh(), "jug has fresh native collision info per impulse tick");
                require(currentFrameSupportSample(beakerNow) && beakerNow.settled()
                                && frameId.equals(beakerNow.point().frameId()) && frameId.equals(beakerNow.trackingId())
                                && beakerNow.capture().collisionInfoFresh()
                                && beakerNow.capture().sequence() == previousBeaker.capture().sequence() + 1,
                        "beaker remains fresh settled native-frame cargo with a new tick sequence");
                requireCargoIdentity(beakerNow, beaker, "strong impulse beaker tick " + (tick + 1));
                Vec3 jugProven = certifiedCargoFloorGeometry(level, plot, storage, now);
                Vec3 beakerProven = certifiedCargoFloorGeometry(level, plot, storage, beakerNow);
                if (jugProven == null) logCargoGeometryRefusal("jug", tick + 1, now);
                if (beakerProven == null) logCargoGeometryRefusal("beaker", tick + 1, beakerNow);
                Vec3 jugTotal = jugProven == null ? jugProvenTotal : jugProvenTotal.add(jugProven);
                Vec3 beakerTotal = beakerProven == null ? beakerProvenTotal : beakerProvenTotal.add(beakerProven);
                Vec3 jugRaw = now.point().storagePoint().subtract(localStart);
                Vec3 beakerRaw = beakerNow.point().storagePoint().subtract(beakerLocalStart);
                Vec3 jugResidual = jugRaw.subtract(jugTotal);
                Vec3 beakerResidual = beakerRaw.subtract(beakerTotal);
                double jugHorizontalDelta = jugResidual.horizontalDistance();
                if (jugHorizontalDelta > .02) {
                    logStrongCargoHorizontalFailure("jug", tick + 1, previous, now, jug, worldStart, localStart,
                            jugRaw, jugTotal, jugResidual);
                }
                require(jugHorizontalDelta <= .02,
                        "cargo horizontal deck-relative displacement remains within .02");
                double beakerHorizontalDelta = beakerResidual.horizontalDistance();
                if (beakerHorizontalDelta > .02) {
                    logStrongCargoHorizontalFailure("beaker", tick + 1, previousBeaker, beakerNow, beaker, beakerWorldStart, beakerLocalStart,
                            beakerRaw, beakerTotal, beakerResidual);
                }
                require(beakerHorizontalDelta <= .02,
                        "beaker horizontal deck-relative displacement remains within .02");
                assertNativeSolvedGeometry(level, plot, storage, now, jug, tick + 1);
                assertNativeSolvedGeometry(level, plot, storage, beakerNow, beaker, tick + 1);
                requireCargoIdentity(now, jug, "strong impulse tick " + (tick + 1));
                impulseCarry(tick + 1, now, beakerNow, worldStart, beakerWorldStart, localStart, beakerLocalStart,
                        jugTotal, beakerTotal);
            });
        }

        private void logStrongCargoHorizontalFailure(String label, int tick, CargoSupportSample prior,
                CargoSupportSample now, RegisteredCargo root, Vec3 worldStart, Vec3 localStart,
                Vec3 rawDelta, Vec3 provenGeometry, Vec3 residual) {
            var entity = root.entity();
            var capture = now == null ? null : now.capture();
            var pre = capture == null ? null : capture.preCutoff();
            var point = now == null ? null : now.point();
            var delta = point == null ? null : point.storagePoint().subtract(localStart);
            var box = entity.getBoundingBox();
            var frame = plot.subLevel();
            String evidence = "root=" + label + " tick=" + tick + " RAW=" + rawDelta
                    + " PROVEN=" + provenGeometry + " RESIDUAL=" + residual + " deltaXYZ=" + delta
                    + " localStart=" + localStart + " currentStorage=" + (point == null ? null : point.storagePoint())
                    + " worldStart=" + worldStart + " currentWorld=" + (now == null ? null : now.position())
                    + " rootPosition=" + entity.position() + " rootBox=" + box
                    + " priorNative=" + (prior == null ? null : prior.capture()) + " currentNative=" + capture
                    + " priorCopiedPose=" + (prior == null || prior.capture() == null ? null : prior.capture().preCutoff())
                    + " currentCopiedPose=" + (pre == null ? null : pre.logicalPose())
                    + " copiedBox=" + (pre == null ? null : pre.solvedBox())
                    + " typedCollisionInfo=" + (capture == null ? null : capture.currentCollisionInfo())
                    + " typedMotion=" + (pre == null ? null : pre.inheritedMotion())
                    + " flags={traceComplete=" + (pre != null && pre.nativeCorrectionTraceComplete())
                    + ",fresh=" + (capture != null && capture.collisionInfoFresh()) + "}"
                    + " frameLast=" + frame.lastPose() + " frameCurrent=" + frame.logicalPose()
                    + " orderedTrace=" + (pre == null ? null : pre.nativeCorrectionTrace());
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("strong cargo horizontal failure: {}", evidence);
            String failure = "strong cargo horizontal displacement: root=" + label + " tick=" + tick + " delta=" + delta
                    + " localStart=" + localStart + " currentStorage=" + (point == null ? null : point.storagePoint())
                    + " nativeMove=" + entity.nativeMoveCapture();
            throw new GameTestAssertException(failure.substring(0, Math.min(899, failure.length())));
        }

        private void logCargoGeometryRefusal(String label, int tick, CargoSupportSample sample) {
            var capture = sample == null ? null : sample.capture();
            var pre = capture == null ? null : capture.preCutoff();
            var info = capture == null ? null : capture.currentCollisionInfo();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "strong cargo geometry refused root={} tick={} capture={} point={} settled={} grounded={} info={} traceComplete={} trace={}",
                    label, tick, capture, sample == null ? null : sample.point(), sample != null && sample.settled(),
                    sample != null && sample.onGround(), info, pre != null && pre.nativeCorrectionTraceComplete(),
                    pre == null ? null : pre.nativeCorrectionTrace());
        }

        private void logImpulseFreshnessFailure(String label, int tick, CargoSupportSample prior,
                                                CargoSupportSample now, RegisteredCargo root,
                                                Vec3 worldStart, Vec3 localStart) {
            var capture = now == null ? null : now.capture();
            if (capture != null && prior != null && capture.sequence() == prior.capture().sequence() + 1
                    && capture.collisionInfoFresh()) return;
            var entity = root.entity();
            Object info = capture == null ? null : capture.currentCollisionInfo();
            var frame = plot.subLevel();
            var pre = capture == null ? null : capture.preCutoff();
            var delta = now == null || now.point() == null ? null : now.point().storagePoint().subtract(localStart);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "strong impulse first failure root={} tick={} point={} trackingId={} settled={} grounded={} fresh={} positionCaptured={} sequence={} priorSequence={} currentNativeInfo={} preCutoff={} copiedFramePose={} localDelta={} actualReagent={} rootPresent={} position={} priorPosition={} worldStart={} frameLastPose={} frameLogicalPose={}",
                    label, tick, now == null ? null : now.point(), now == null ? null : now.trackingId(),
                    now != null && now.settled(), now != null && now.onGround(), capture != null && capture.collisionInfoFresh(),
                    capture != null && capture.positionCaptured(), capture == null ? null : capture.sequence(),
                    prior == null || prior.capture() == null ? null : prior.capture().sequence(), info, pre,
                    pre == null ? null : pre.logicalPose(), delta, now == null ? null : now.waterCents(),
                    now != null && now.rootPresent(), now == null ? null : now.position(),
                    prior == null ? null : prior.position(), worldStart, frame.lastPose(), frame.logicalPose());
        }

        private void carry(int tick, CargoSupportSample previousJug, CargoSupportSample previousBeaker,
                           Vec3 jugWorldStart, Vec3 beakerWorldStart, Vec3 jugStorageStart, Vec3 beakerStorageStart) {
            if (tick == 0) {
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel())
                        .addLinearAndAngularVelocity(new Vector3d(2, 0, 2), new Vector3d());
            }
            if (tick >= 6) {
                CargoSupportSample a = jugLatest.get(), b = beakerLatest.get();
                double jugWorldDelta = a.position().subtract(jugWorldStart).horizontalDistance();
                double beakerWorldDelta = b.position().subtract(beakerWorldStart).horizontalDistance();
                double jugFrameError = a.point().storagePoint().distanceTo(jugStorageStart);
                double beakerFrameError = b.point().storagePoint().distanceTo(beakerStorageStart);
                require(jugWorldDelta > .1 && beakerWorldDelta > .1,
                        "both settled cargo roots follow one native carrier impulse in world space");
                require(jugFrameError <= .02 && beakerFrameError <= .02,
                        "both cargo roots retain same frame-relative position over six support ticks");
                System.out.println("SHUTTLE_CARGO_FRAME_CARRIED ticks=6 jugSeq=" + a.capture().sequence()
                        + " beakerSeq=" + b.capture().sequence() + " probe=" + a.capture().requested()
                        + " own=" + a.capture().acceptedOwnDelta() + "/" + b.capture().acceptedOwnDelta()
                        + " fresh=" + a.capture().collisionInfoFresh() + "/" + b.capture().collisionInfoFresh()
                        + " worldDelta=" + jugWorldDelta + "/" + beakerWorldDelta
                        + " frameError=" + jugFrameError + "/" + beakerFrameError
                        + " storedVelocity=" + a.velocity() + "/" + b.velocity());
                removeJugSupport(jugStorageStart);
                awaitSupportLoss(0, jugLatest.get(), beakerLatest.get(), jugWorldStart, beakerWorldStart,
                        beakerStorageStart);
                return;
            }
            helper.runAfterDelay(1, () -> {
                CargoSupportSample a = jugLatest.get(), b = beakerLatest.get();
                assertCarriedTick(a, previousJug, jug, "jug");
                assertCarriedTick(b, previousBeaker, beaker, "beaker");
                require(a.point().storagePoint().distanceTo(jugStorageStart) <= .02
                                && b.point().storagePoint().distanceTo(beakerStorageStart) <= .02,
                        "native cargo carrier motion leaves both storage-frame positions stable within .02");
                carry(tick + 1, a, b, jugWorldStart, beakerWorldStart, jugStorageStart, beakerStorageStart);
            });
        }

        private void assertCarriedTick(CargoSupportSample now, CargoSupportSample before, RegisteredCargo root, String label) {
            require(currentFrameSupportSample(now) && now.settled() && frameId.equals(now.point().frameId())
                            && now.trackingId().equals(frameId), label + " remains currently settled on same native frame");
            require(now.capture().sequence() == before.capture().sequence() + 1,
                    label + " receives exactly one normal owner move per carried tick");
            require(now.capture().collisionInfoFresh() && Math.abs(now.capture().requested().y + .02) < 1.0e-6
                            && now.capture().acceptedOwnDelta().lengthSqr() < 1.0e-10,
                    label + " per-tick downward native support probe clips own motion without a second carry");
            require(now.velocity().lengthSqr() < 1.0e-8, label + " keeps own stored velocity zero during carrier motion");
            requireCargoIdentity(now, root, label);
        }

        private void removeJugSupport(Vec3 jugStoragePoint) {
            BlockPos foot = BlockPos.containing(jugStoragePoint.x, jugStoragePoint.y - .01, jugStoragePoint.z);
            for (int x = foot.getX() - 1; x <= foot.getX(); x++) for (int z = foot.getZ() - 1; z <= foot.getZ(); z++)
                require(plot.setStorageBlock(new BlockPos(x, foot.getY(), z), Blocks.AIR.defaultBlockState()),
                        "native plot author removes only owned jug support cell");
            System.out.println("SHUTTLE_CARGO_SUPPORT_REMOVED localFloor=" + foot);
        }

        private void awaitSupportLoss(int waited, CargoSupportSample previousJug, CargoSupportSample previousBeaker,
                                      Vec3 jugWorldStart, Vec3 beakerWorldStart, Vec3 beakerStorageStart) {
            if (waited >= 40) throw new GameTestAssertException("cargo failed to leave removed native support: " + summary());
            helper.runAfterDelay(1, () -> {
                CargoSupportSample a = jugLatest.get(), b = beakerLatest.get();
                if (a.capture().sequence() == previousJug.capture().sequence()) {
                    awaitSupportLoss(waited + 1, previousJug, previousBeaker, jugWorldStart, beakerWorldStart, beakerStorageStart);
                    return;
                }
                if (a.settled()) {
                    require(a.capture().sequence() == previousJug.capture().sequence() + 1,
                            "settled jug processes each native support cadence tick");
                    require(a.capture().collisionInfoFresh() && Math.abs(a.capture().requested().y + .02) < 1.0e-6,
                            "support removal detected by fresh native probe");
                    require(a.capture().acceptedOwnDelta().y < -.019,
                            "lost native support accepts the normal .02 downward probe");
                    awaitSupportLoss(waited + 1, a, b, jugWorldStart, beakerWorldStart, beakerStorageStart);
                    return;
                }
                require(a.capture().requested().y < 0 && a.capture().acceptedOwnDelta().y < 0,
                        "next unsettled normal owner tick resumes gravity motion");
                requireCargoIdentity(a, jug, "jug after support loss");
                require(b.settled() && frameId.equals(b.point().frameId())
                                && b.point().storagePoint().distanceTo(beakerStorageStart) <= .02,
                        "separate beaker remains supported and carried after only jug support is removed");
                UUID beforeFrame = frameId;
                if (a.point() == null || a.trackingId() != null) {
                    awaitWorldLanding(waited + 1, a, b, jugWorldStart, beakerWorldStart, beakerStorageStart, beforeFrame);
                    return;
                }
                System.out.println("SHUTTLE_CARGO_SUPPORT_LOST seq=" + a.capture().sequence() + " ownY="
                        + a.capture().acceptedOwnDelta().y + " trackingBefore=" + beforeFrame + " trackingAfter=null");
                awaitWorldLanding(waited + 1, a, b, jugWorldStart, beakerWorldStart, beakerStorageStart, beforeFrame);
            });
        }

        private void awaitWorldLanding(int waited, CargoSupportSample previousJug, CargoSupportSample previousBeaker,
                                       Vec3 jugWorldStart, Vec3 beakerWorldStart, Vec3 beakerStorageStart,
                                       UUID previousFrame) {
            if (waited >= 40) throw new GameTestAssertException("jug did not return to valid world actor point after support loss: " + summary());
            CargoSupportSample current = jugLatest.get(), other = beakerLatest.get();
            if (current.capture().sequence() <= previousJug.capture().sequence()
                    || current.point() == null || current.point().frameId() != null || current.trackingId() != null) {
                helper.runAfterDelay(1, () -> awaitWorldLanding(waited + 1, current, other,
                        jugWorldStart, beakerWorldStart, beakerStorageStart, previousFrame));
                return;
            }
            require(current.capture().sequence() == previousJug.capture().sequence() + 1,
                    "world transition occurs through one newer normal jug move");
            require(current.capture().requested().y < 0 && current.capture().acceptedOwnDelta().y < 0,
                    "world transition retains real downward own travel, not carrier-only movement");
            requireCargoIdentity(current, jug, "jug after world return");
            require(other.settled() && other.point() != null && frameId.equals(other.point().frameId())
                            && other.point().storagePoint().distanceTo(beakerStorageStart) <= .02
                            && other.capture().collisionInfoFresh(),
                    "other registered beaker remains natively supported through jug's frame departure");
            System.out.println("SHUTTLE_CARGO_WORLD_RETURN seq=" + current.capture().sequence()
                    + " trackingBefore=" + previousFrame + " trackingAfter=" + current.trackingId()
                    + " ownY=" + current.capture().acceptedOwnDelta().y + " settled=" + current.settled());
            helper.succeed();
        }

        private boolean currentFrameSupportSample(CargoSupportSample sample) {
            return sample != null && sample.capture() != null && sample.capture().positionCaptured()
                    && sample.capture().collisionInfoFresh() && sample.point() != null
                    && sample.point().frameId() != null && sample.point().frameId().equals(sample.trackingId())
                    && sample.capture().requested().y < -1.0e-6
                    && sample.capture().acceptedOwnDelta().y > sample.capture().requested().y + 1.0e-6;
        }

        private void requireCargoIdentity(CargoSupportSample sample, RegisteredCargo root, String label) {
            require(sample.rootPresent() && sample.ledgerOwnsRoot() && sample.waterCents() == cents,
                    label + " retains exact registered root, ownership token, and water cents");
        }

        private String summary() {
            return "frame=" + frameId + " jug=" + jugLatest.get() + " beaker=" + beakerLatest.get();
        }

        private record CargoSupportSample(
                com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture capture,
                Vec3 position, Vec3 velocity,
                com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point,
                UUID trackingId, boolean settled, boolean onGround, boolean rootPresent,
                boolean ledgerOwnsRoot, long waterCents) { }
    }

    private static final class JugOwnImpactProbe {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final com.juicyslew.moonstation14.entities.LooseItemEntity jug;
        private final UUID id;
        private final com.juicyslew.moonstation14.ms14.hands.ItemToken token;
        private final UUID world;
        private final net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water;
        private final long cents;
        private java.util.function.Consumer<EntityTickEvent.Post> listener;
        private final AtomicReference<JugSnapshot> pending = new AtomicReference<>();

        JugOwnImpactProbe(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
                          com.juicyslew.moonstation14.entities.LooseItemEntity jug,
                          com.juicyslew.moonstation14.ms14.hands.ItemToken token, UUID world,
                          net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water,
                          long cents) {
            this.helper=helper; this.level=level; this.plot=plot; this.jug=jug; this.id=jug.getUUID();
            this.token=token; this.world=world; this.water=water; this.cents=cents;
        }

        void register() {
            listener = event -> {
                if (event.getEntity() != jug || pending.get() != null) return;
                var capture = jug.nativeMoveCapture();
                if (capture == null || !capture.positionCaptured() || Math.abs(capture.requested().x - .75) > .001) return;
                var location = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                        .forLevel(level).snapshot().locationOf(token);
                var stack = jug.getItem();
                var reagent = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
                JugSnapshot witness = new JugSnapshot(capture, jug.position(), jug.position().subtract(capture.before()),
                        level.getEntity(id) == jug,
                        location.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(world, id)::equals).isPresent(),
                        stack.getItem().toString(), stack.getCount(), stack.isEmpty(), jug.isRemoved(), reagent != null,
                        reagent == null ? 0L : reagent.centContents().getOrDefault(water, 0L));
                pending.compareAndSet(null, witness);
            };
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { NeoForge.EVENT_BUS.unregister(listener); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
            NeoForge.EVENT_BUS.addListener(listener);
            helper.runAfterDelay(20, () -> {
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel())
                        .addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
                jug.launch(new Vec3(.75, .04, 0), 0);
                await(0);
            });
        }

        private void await(int waited) {
            if (waited >= 60) throw new GameTestAssertException("no normal jug owner tick captured at own request .75");
            JugSnapshot snapshot = pending.getAndSet(null);
            if (snapshot == null) { helper.runAfterDelay(1, () -> await(waited + 1)); return; }
            var capture = snapshot.capture();
            System.out.println("SHUTTLE_JUG_OWN_IMPACT seq=" + capture.sequence() + " request=" + capture.requested()
                    + " own=" + capture.acceptedOwnDelta() + " total=" + snapshot.totalDelta()
                    + " infoFresh=" + capture.collisionInfoFresh() + " rootPresent=" + snapshot.rootPresent()
                    + " reagentPresent=" + snapshot.reagentPresent() + " waterCents=" + snapshot.waterCents()
                    + " item=" + snapshot.itemType() + " count=" + snapshot.count() + " empty=" + snapshot.empty()
                    + " removed=" + snapshot.removed() + " position=" + snapshot.position());
            require(Math.abs(capture.acceptedOwnDelta().x - .75) < .01,
                    "clear deck accepts full jug own travel without axis collision");
            require(Math.abs(snapshot.totalDelta().x - capture.acceptedOwnDelta().x) > .001,
                    "same tick carrier displacement differs from jug own travel");
            require(snapshot.rootPresent() && snapshot.ledgerOwnsRoot() && snapshot.reagentPresent()
                            && snapshot.waterCents() == cents && !snapshot.empty() && !snapshot.removed()
                            && snapshot.count() == 1
                            && snapshot.itemType().equals(com.juicyslew.moonstation14.item.ModItems.JUG.get().toString()),
                    "jug remains same live registered root with exact water contents on owner tick");
            helper.succeed();
        }

        private record JugSnapshot(com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture capture,
                                   Vec3 position, Vec3 totalDelta, boolean rootPresent, boolean ledgerOwnsRoot,
                                   String itemType, int count, boolean empty, boolean removed,
                                   boolean reagentPresent, long waterCents) { }
    }

    private static final class CargoMoveProbe {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final com.juicyslew.moonstation14.entities.LooseItemEntity cargo;
        private final UUID id;
        private final com.juicyslew.moonstation14.ms14.hands.ItemToken token;
        private final UUID world;
        private final net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water;
        private final long cents;
        private java.util.function.Consumer<EntityTickEvent.Post> listener;
        private final AtomicReference<CargoSnapshot> pending = new AtomicReference<>();
        private boolean done;

        CargoMoveProbe(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
                       com.juicyslew.moonstation14.entities.LooseItemEntity cargo,
                       com.juicyslew.moonstation14.ms14.hands.ItemToken token, UUID world,
                       net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water, long cents) {
            this.helper=helper; this.level=level; this.plot=plot; this.cargo=cargo; this.id=cargo.getUUID();
            this.token=token; this.world=world; this.water=water; this.cents=cents;
        }

        void register() {
            listener = event -> {
                if (event.getEntity() != cargo || done) return;
                var capture = cargo.nativeMoveCapture();
                if (capture == null || !capture.positionCaptured()) return;
                double requestX = capture.requested().x;
                if (Math.abs(requestX - .15) > .001 && Math.abs(requestX) > .001) return;
                if (pending.get() != null) return;
                var location = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                        .forLevel(level).snapshot().locationOf(token);
                var reagent = cargo.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
                long waterCents = reagent == null ? 0L : reagent.centContents().getOrDefault(water, 0L);
                UUID tracking = ((EntityMovementExtension) (Object) cargo).sable$getTrackingSubLevel() == null ? null
                        : ((EntityMovementExtension) (Object) cargo).sable$getTrackingSubLevel().getUniqueId();
                pending.compareAndSet(null, new CargoSnapshot(capture, cargo.position().subtract(capture.before()),
                        level.getEntity(id) == cargo,
                        location.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(world, id)::equals).isPresent(),
                        waterCents, tracking));
            };
            // Register test-terminal cleanup before exposing the listener to world events.
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { NeoForge.EVENT_BUS.unregister(listener); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
            NeoForge.EVENT_BUS.addListener(listener);
            helper.runAfterDelay(20, () -> {
                var body = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel());
                body.addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
                cargo.launch(new Vec3(.15, .04, 0), 0);
                awaitPhase(helper, false, 0);
            });
        }

        private void awaitPhase(GameTestHelper helper, boolean zeroPhase, int waited) {
            if (waited >= 60) throw new GameTestAssertException("missing cargo native capture for phase zero=" + zeroPhase);
            CargoSnapshot snapshot = pending.getAndSet(null);
            if (snapshot == null || (zeroPhase ? snapshot.capture().sequence() <= firstSequence
                    : Math.abs(snapshot.capture().requested().x - .15) > .001)) {
                helper.runAfterDelay(1, () -> awaitPhase(helper, zeroPhase, waited + 1));
                return;
            }
            var capture = snapshot.capture();
            double deltaErrorX = snapshot.totalWorldDelta().x - capture.acceptedOwnDelta().x;
            System.out.println("SHUTTLE_CARGO_MOVE phase=" + (zeroPhase ? "zero" : "carried") + " seq=" + capture.sequence()
                    + " request=" + capture.requested() + " own=" + capture.acceptedOwnDelta()
                    + " total=" + snapshot.totalWorldDelta() + " infoFresh=" + capture.collisionInfoFresh()
                    + " tracking=" + snapshot.tracking());
            require(snapshot.rootPresent() && snapshot.ledgerOwnsRoot() && snapshot.waterCents() == cents,
                    "same real cargo root, ownership token, and reagent contents survive observed move");
            if (!zeroPhase) {
                firstSequence = capture.sequence();
                require(Math.abs(capture.acceptedOwnDelta().x - .15) < .01,
                        "native own displacement is captured before Sable cargo carry: " + capture);
                require(Math.abs(deltaErrorX) > .001,
                        "post-carry world displacement includes independently applied native cargo carry");
                require(capture.collisionInfoFresh(), "ordinary nonzero native move refreshed Sable collision info");
                // Neutralize next-tick gravity without imposing a nonzero owner displacement.
                cargo.launch(new Vec3(0, .04, 0), 0);
                helper.runAfterDelay(1, () -> awaitPhase(helper, true, 0));
            } else {
                require(capture.sequence() > firstSequence, "zero-move proof is a newer native move invocation");
                require(capture.acceptedOwnDelta().equals(Vec3.ZERO),
                        "ordinary owner tick's gravity-cancelled zero request is captured as known zero");
                System.out.println("SHUTTLE_CARGO_ZERO_RETURN seq=" + capture.sequence() + " known=true infoFresh="
                        + capture.collisionInfoFresh());
                done = true;
                helper.succeed();
            }
        }

        private long firstSequence;
        private record CargoSnapshot(com.juicyslew.moonstation14.entities.LooseItemEntity.NativeMoveCapture capture,
                                     Vec3 totalWorldDelta, boolean rootPresent, boolean ledgerOwnsRoot,
                                     long waterCents, UUID tracking) { }
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 120)
    public static void boundHumanStandsAndWalksRelativeToTranslatingNativeDeck(GameTestHelper helper) {
        withDeck(helper, new BlockPos(240, 300, 200), 8, 1, NATIVE_SCENE_READY_TICKS,
                (level, plot, storage, scene) -> {
            SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level,scene,plot,storage,DECK_SIZE,DECK_SIZE);
            var nativeFloor=LoadedFrameRayQuery.knownServerBlock(level,plot.subLevel(),storage);
            require(nativeFloor!=null&&nativeFloor.is(Blocks.STONE),"translating frame has an actual authored stone deck floor");
            Vec3 spawn = deckFeet(plot, storage, 2, 2);
            var human = BoundHumanFixture.create(level, spawn, "sable-human-carry");
            awaitNativeDeckSupport(helper, human, 24, () -> {
                require(human.onGround(), "human makes actual native deck contact before carrier motion");
                var standing = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f)
                        .orElseThrow(() -> new GameTestAssertException("owned human standing motor step was rejected"));
                Vector3d relativeBefore = localPosition(plot, human.position());
                UUID rootId = human.getUUID();
                Object binding = human.playerCharacterBinding();
                Object hands = human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(hands != null && Math.abs(standing.position().x() - human.getX()) < 1e-8
                                && Math.abs(standing.position().y() - human.getY()) < 1e-8
                                && Math.abs(standing.position().z() - human.getZ()) < 1e-8,
                        "idle authoritative result includes final world-space body position and retains real hands");
                  var frame=plot.subLevel();
                  ShuttleFixtureMotionService.registerFixture(frame);
                  attachDiagnosticCleanup(helper,human,()->ShuttleFixtureMotionService.stop(level,frame));
                  require(ShuttleFixtureMotionService.start(level,frame,new ShuttleMotionRequest(.5,0,.5,0,240)),
                         "existing bounded physical velocity-feedback fixture controller starts");
                 awaitMeasuredFixtureTransport(helper,level,frame,human,plot,spawn,relativeBefore,rootId,binding,hands,0);
             });
         });
    }

    private static void awaitMeasuredFixtureTransport(GameTestHelper helper, ServerLevel level, ServerSubLevel frame,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, Vec3 spawn, Vector3d relativeBefore,
            UUID rootId, Object binding, Object hands, int waited) {
        var measured=ShuttleFixtureMotionService.snapshot(level,frame);
        if(waited>=60) throw new GameTestAssertException("physical fixture controller did not establish measured transport velocity: "+measured);
        if(waited>0)new GroundedHarnessWorldStep().step(human,0,0,false,false,0f)
                .orElseThrow(()->new GameTestAssertException("owned human warmup motor step was rejected"));
        var velocity=measured==null?null:measured.transportVelocity();
        boolean ready=velocity!=null&&Math.abs(velocity.x()-.5)<=.15&&Math.abs(velocity.z()-.5)<=.15
                &&Math.abs(velocity.y())<=.1&&measured.worldAngularVelocity().x()*measured.worldAngularVelocity().x()
                +measured.worldAngularVelocity().y()*measured.worldAngularVelocity().y()
                +measured.worldAngularVelocity().z()*measured.worldAngularVelocity().z()<=.01;
        if(!ready){
            helper.runAfterDelay(1,()->awaitMeasuredFixtureTransport(helper,level,frame,human,plot,spawn,relativeBefore,rootId,binding,hands,waited+1));
            return;
        }
        tickHuman(helper, human, 0, 0, 12, () -> {
                    require(helper.getLevel().getEntity(rootId) == human && human.playerCharacterBinding().equals(binding)
                                    && human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands,
                            "standing carry preserves the exact root, binding, and hand owner");
                    require(localPosition(plot, human.position()).distance(relativeBefore) < .14,
                            "standing human remains near the same deck point under measured native translation");
                    require(human.getDeltaMovement().horizontalDistance() < .01,
                            "platform translation is not retained as voluntary motor/animation velocity while standing");
                    require(human.position().subtract(spawn).horizontalDistance() > .12,
                            "native moving plot produces observable world-space human translation");
                    var walkedBefore = localPosition(plot, human.position());
                tickHuman(helper, human, 0, 1000, 10, () -> {
                        Vector3d walkedAfter = localPosition(plot, human.position());
                        require(walkedAfter.distance(walkedBefore) > .12,
                                "voluntary walk changes deck-relative position independently of carrier motion");
                        helper.succeed();
                    });
                });
    }

    @GameTestTags({"shuttle-cabin-wall-contact", "shuttle", "shuttle-body-carry"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void stationaryCabinWallDoesNotBecomeHumanFloorSupport(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos sceneOrigin=new BlockPos(1760,300,1440);
        SableFixtureScene.Region scene=SableFixtureScene.acquire(helper,sceneOrigin,32,12,32);
        SableFixtureScene.awaitEmpty(helper,scene,NATIVE_SCENE_READY_TICKS,()->{
            try {
                SableFixtureScene.authorStoneSupportPad(level,scene);
                Vector3d anchor=new Vector3d(1772.5,300,1452.5);
                var authored=SableFixturePlot.allocateAndAuthor(level,anchor,plot->{
                    BlockPos storage=plot.storageOrigin();
                    plot.ensureStorageFootprint(storage,ShuttleCabinFixture.WIDTH,ShuttleCabinFixture.HEIGHT,ShuttleCabinFixture.DEPTH);
                    ShuttleCabinFixture.buildHull(level,storage,plot::setStorageBlock);
                    return storage;
                });
                SableFixtureScene.trackPlot(helper,authored.plot());
                SableFixturePlot plot=authored.plot();
                BlockPos storage=authored.result();
                SableFixtureScene.awaitStationaryFrame(helper,plot,80,()->{
                    Vec3 spawn=cabinPoint(plot,storage,1.5,1,9.5);
                    var human=BoundHumanFixture.create(level,spawn,"stationary-cabin-hull-wall");
                    awaitCabinFloorContact(helper,level,human,plot,storage,32,()->
                            new CabinWallProbe(helper,human,plot,storage).run(0));
                });
            } catch(RuntimeException|Error failure){throw failure;}
        });
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle", "shuttle-body-carry"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void interiorWestWallDownwardProbeDoesNotInventStep(GameTestHelper helper) {
        interiorWallStepCase(helper, new BlockPos(1800, 300, 1480), InteriorFace.WEST, -.001);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void interiorEastWallUpwardProbeDoesNotInventStep(GameTestHelper helper) {
        interiorWallStepCase(helper, new BlockPos(1840, 300, 1480), InteriorFace.EAST, .12);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void interiorNorthWallJumpProbeDoesNotInventStep(GameTestHelper helper) {
        interiorWallStepCase(helper, new BlockPos(1880, 300, 1480), InteriorFace.NORTH, .42);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void interiorSouthWallDownwardProbeDoesNotInventStep(GameTestHelper helper) {
        interiorWallStepCase(helper, new BlockPos(1920, 300, 1480), InteriorFace.SOUTH, -.001);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void rotatedWestWallDiagonalProbeDoesNotInventStep(GameTestHelper helper) {
        interiorWallStepCase(helper, new BlockPos(2000, 300, 1480), InteriorFace.WEST, -.001, true);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void rotatedNorthWallDiagonalProbeDoesNotInventStep(GameTestHelper helper) {
        interiorWallStepCase(helper, new BlockPos(2040, 300, 1480), InteriorFace.NORTH, .12, true);
    }

    private enum InteriorFace { WEST, EAST, NORTH, SOUTH }

    private static void interiorWallStepCase(GameTestHelper helper, BlockPos sceneOrigin,
            InteriorFace face, double verticalRequest) {
        interiorWallStepCase(helper, sceneOrigin, face, verticalRequest, false);
    }

    private static void interiorWallStepCase(GameTestHelper helper, BlockPos sceneOrigin,
            InteriorFace face, double verticalRequest, boolean nativeYaw) {
        ServerLevel level = helper.getLevel();
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, 32, 12, 32);
        SableFixtureScene.awaitEmpty(helper, scene, NATIVE_SCENE_READY_TICKS, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(sceneOrigin.getX() + 12.5, sceneOrigin.getY() + 1.0, sceneOrigin.getZ() + 12.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                        ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                ShuttleCabinFixture.buildHull(level, storage, plot::setStorageBlock);
                return storage;
            });
            SableFixtureScene.trackPlot(helper, authored.plot());
            SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level, scene, authored.plot(),
                    authored.result(), ShuttleCabinFixture.WIDTH, ShuttleCabinFixture.DEPTH);
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 80, () -> {
                if (nativeYaw) {
                    var bodyHandle = RigidBodyHandle.of(authored.plot().subLevel());
                    bodyHandle.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, .4, 0));
                    BoundedGameTestWait.await(helper, 12, () -> "supported frame did not acquire native yaw; "
                                    + authored.plot().subLevel().logicalPose(),
                            () -> Math.abs(frameYaw(authored.plot())) > 1.0e-3,
                            () -> {
                                bodyHandle.addLinearAndAngularVelocity(new Vector3d(),
                                        bodyHandle.getAngularVelocity(new Vector3d()).negate());
                                SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 40, () -> {
                                    require(Math.abs(frameYaw(authored.plot())) > 1.0e-3,
                                            "native yaw is retained after real angular braking");
                                    probeInteriorWallBody(helper, level, authored.plot(), authored.result(), face,
                                            verticalRequest, true);
                                });
                            });
                    return;
                }
                probeInteriorWallBody(helper, level, authored.plot(), authored.result(), face, verticalRequest, false);
            });
        });
    }

    private static double frameYaw(SableFixturePlot plot) {
        Vector3d forward = plot.subLevel().logicalPose().orientation().transform(new Vector3d(0, 0, 1));
        return Math.atan2(forward.x, forward.z);
    }

    private static void probeInteriorWallBody(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            BlockPos storage, InteriorFace face, double verticalRequest, boolean requireYaw) {
                if (requireYaw) require(Math.abs(frameYaw(plot)) > 1.0e-3,
                        "native-supported frame has nonzero yaw before actor probe; pose=" + plot.subLevel().logicalPose());
                double x = face == InteriorFace.WEST ? 1.35 : face == InteriorFace.EAST ? 6.65
                        : face == InteriorFace.NORTH && requireYaw ? 5.5 : 4.0;
                double z = face == InteriorFace.NORTH ? 1.35 : face == InteriorFace.SOUTH ? 11.65 : 6.5;
                Vec3 spawn = cabinPoint(plot, storage, x, 1, z);
                var human = BoundHumanFixture.create(level, spawn, "interior-wall-step-probe");
                awaitCabinFloorContact(helper, level, human, plot, storage, 32, () -> {
                    require(human.onGround(), "probe starts from the helper's fresh native steel-floor support contact");
                    onNextEligibleOwnerTickPre(helper, level, () -> {
                    Vector3d localDirection = switch (face) {
                        case WEST -> new Vector3d(-1, 0, 0);
                        case EAST -> new Vector3d(1, 0, 0);
                        case NORTH -> new Vector3d(0, 0, -1);
                        case SOUTH -> new Vector3d(0, 0, 1);
                    };
                    Vector3d localTangent = switch (face) {
                        case WEST -> new Vector3d(0, 0, -1);
                        case EAST -> new Vector3d(0, 0, 1);
                        case NORTH -> new Vector3d(1, 0, 0);
                        case SOUTH -> new Vector3d(-1, 0, 0);
                    };
                    plot.subLevel().logicalPose().orientation().transform(localDirection);
                    plot.subLevel().logicalPose().orientation().transform(localTangent);
                    human.setDeltaMovement(localDirection.x * .2 + localTangent.x * .05,
                            verticalRequest == -.001 ? -.02 : 0,
                            localDirection.z * .2 + localTangent.z * .05);
                    if (verticalRequest == .12) human.setDeltaMovement(human.getDeltaMovement().x, .006,
                            human.getDeltaMovement().z);
                    int wishX = face == InteriorFace.WEST ? -1000 : face == InteriorFace.EAST ? 1000 : 0;
                    int wishZ = face == InteriorFace.NORTH ? -1000 : face == InteriorFace.SOUTH ? 1000 : 0;
                    wishZ = wishZ == 0 ? (face == InteriorFace.WEST ? -250 : face == InteriorFace.EAST ? 250 : 0) : wishZ;
                    wishX = wishX == 0 ? (face == InteriorFace.NORTH ? 250 : face == InteriorFace.SOUTH ? -250 : 0) : wishX;
                    SableOwnedBodyMotion.MoveResult[] observedMove = {null};
                    MindControlledMob owner = (MindControlledMob) (Object) human;
                    long ownerTick = level.getServer().getTickCount();
                    require(!owner.moonstation14$physicalStepConsumed(level, ownerTick),
                            "scheduled owner phase starts with unused physical-step budget; tick=" + ownerTick);
                    var motorResult = GroundedHarnessWorldStep.observingNativeGeometry().step(human,
                            wishX, wishZ, verticalRequest == .42, false, frameLocalXAxisYaw(plot),
                            move -> observedMove[0] = move);
                    require(owner.moonstation14$physicalStepConsumed(level, ownerTick),
                            "movement attempt consumed this fresh tick's physical-step budget; tick=" + ownerTick);
                    if (motorResult.isEmpty() || observedMove[0] == null)
                        throw new GameTestAssertException(logInteriorWallFailure(human, plot, face,
                                wishX, wishZ, verticalRequest, "movement-not-observed", observedMove[0]));
                    var result = observedMove[0];
                    var trace = result.collisionScope().nativeCollisionTrace();
                    if (requireYaw) {
                        boolean obliqueSide = trace.stream().anyMatch(row -> {
                            BlockPos selected = row.selectedPos();
                            double normalLength = Math.hypot(row.selectedX(), row.selectedZ());
                            return plot.engineId().equals(row.frameId()) && !row.verticalCollision()
                                    && selected != null && selected.getY() >= storage.getY() + 1
                                    && row.selectedState() != null
                                    && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                                    && normalLength > 0d && Math.abs(row.selectedX()/normalLength) > 1.0e-3
                                    && Math.abs(row.selectedZ()/normalLength) > 1.0e-3;
                        });
                        if (!obliqueSide) throw new GameTestAssertException(logInteriorWallFailure(human, plot, face,
                                wishX, wishZ, verticalRequest, "exact-frame-oblique-side-missing", result));
                        if (verticalRequest < 0d) {
                            boolean exactCabinFloor = trace.stream().anyMatch(row -> {
                                BlockPos selected = row.selectedPos();
                                return plot.engineId().equals(row.frameId()) && row.verticalCollision()
                                        && row.selectedY() > 0d && selected != null
                                        && selected.getY() == storage.getY()
                                        && selected.getX() >= storage.getX()
                                        && selected.getX() < storage.getX() + ShuttleCabinFixture.WIDTH
                                        && selected.getZ() >= storage.getZ()
                                        && selected.getZ() < storage.getZ() + ShuttleCabinFixture.DEPTH
                                        && row.selectedState() != null
                                        && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get());
                            });
                            if (!exactCabinFloor) throw new GameTestAssertException(logInteriorWallFailure(human, plot,
                                    face, wishX, wishZ, verticalRequest, "exact-frame-cabin-floor-missing", result));
                        }
                    }
                    boolean wallSelected = trace.stream().anyMatch(row -> row.selectedState() != null
                            && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                            && !row.verticalCollision()
                            && Math.hypot(row.selectedX(), row.selectedZ()) > 1.0e-5);
                    if (!wallSelected) throw new GameTestAssertException(logInteriorWallFailure(human, plot, face,
                            wishX, wishZ, verticalRequest, "steel-wall-not-selected", result));
                    double stepRise = trace.stream().mapToDouble(row -> row.responseY() - row.appliedY()).sum();
                    if (Math.abs(stepRise) > .005) throw new GameTestAssertException(logInteriorWallFailure(human, plot,
                            face, wishX, wishZ, verticalRequest, "invented-step-rise=" + stepRise, result));
                    if (verticalRequest < 0d)
                        require(human.onGround() && result.collisionInfo().verticalCollisionBelow,
                                "downward side probe retains only real cabin-floor support; face=" + face
                                        + ", onGround=" + human.onGround() + ", info=" + result.collisionInfo());
                    });
                });
    }

    private static String logInteriorWallFailure(
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, InteriorFace face, int wishX, int wishZ, double requestY, String failureKind,
            SableOwnedBodyMotion.MoveResult move) {
        String tag = "owned-wall-probe-" + human.getUUID();
        String diagnostic = OwnedNativeMoveDiagnostic.describe(human, move);
        Object trace = move == null || move.collisionScope() == null
                ? "<no-move-or-scope>" : move.collisionScope().nativeCollisionTrace();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                "[{}] full native wall probe failure case={} face={} actor={} frame={} request=({}, {}, {}) diagnostic={} trace={}",
                tag, "interior-wall-diagonal", face, human.getUUID(), plot.engineId(), wishX, wishZ, requestY,
                diagnostic, trace);
        String measured = move == null ? "none" : String.valueOf(move.measuredOwnDisplacement());
        return "wallprobe " + failureKind + " face=" + face + " actor=" + human.getUUID()
                + " req=" + wishX + "/" + wishZ + "/" + requestY + " measured=" + measured
                + " frame=" + plot.engineId() + " log=" + tag;
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void interiorCabinCeilingRemainsHeadCollisionNotStepSupport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sceneOrigin = new BlockPos(1960, 300, 1480);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, 32, 12, 32);
        SableFixtureScene.awaitEmpty(helper, scene, NATIVE_SCENE_READY_TICKS, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(sceneOrigin.getX() + 12.5, sceneOrigin.getY(), sceneOrigin.getZ() + 12.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                        ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                ShuttleCabinFixture.buildHull(level, storage, plot::setStorageBlock);
                return storage;
            });
            SableFixtureScene.trackPlot(helper, authored.plot());
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 80, () -> {
                Vec3 spawn = cabinPoint(authored.plot(), authored.result(), 4, 4, 6.5);
                var human = BoundHumanFixture.create(level, spawn, "interior-ceiling-head-impact");
                require(!human.onGround(), "head-control Human starts unsupported in open cabin air");
                human.setDeltaMovement(0, .25, 0);
                SableOwnedBodyMotion.MoveResult[] observedMove = {null};
                var motorResult = GroundedHarnessWorldStep.observingNativeGeometry().step(human,
                        0, 0, false, false, 0f, move -> observedMove[0] = move);
                require(motorResult.isPresent() && observedMove[0] != null,
                        "shared grounded movement motor accepts the real head-impact movement");
                var result = observedMove[0];
                var info = result.collisionInfo();
                boolean selectedCeiling = result.collisionScope().nativeCollisionTrace().stream().anyMatch(row ->
                        row.verticalCollision() && row.selectedState() != null
                                && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                                && row.selectedY() < 0d);
                require(info != null && info.verticalCollision && !info.verticalCollisionBelow,
                        "actual steel cabin ceiling hit remains vertical and not below-support; info=" + info
                                + ", trace=" + result.collisionScope().nativeCollisionTrace());
                require(selectedCeiling, "head impact is selected against the real steel ceiling from above");
                require(result.measuredOwnDisplacement().y() <= .401,
                        "head impact cannot synthesize upward step displacement; move=" + result.measuredOwnDisplacement());
                helper.succeed();
            });
        });
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-step-probe", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void ownedNativeStepOntoAuthoredHalfSlabRemainsEnabled(GameTestHelper helper) {
        withDeck(helper, new BlockPos(2040, 300, 1200), 0, 3, NATIVE_SCENE_READY_TICKS,
                (level, plot, storage) -> {
            plot.ensureStorageFootprint(storage, DECK_SIZE, 5, DECK_SIZE);
            BlockPos obstacle = storage.offset(4, 1, 3);
            require(plot.setStorageBlock(obstacle, Blocks.STONE_SLAB.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                    net.minecraft.world.level.block.state.properties.SlabType.BOTTOM)),
                    "real lower half slab is authored on the live native frame");
            var human = BoundHumanFixture.create(level, deckFeetAt(plot, storage, 3.35, 3.5),
                    "owned-positive-native-half-slab-step");
            awaitNativeDeckSupport(helper, human, plot, storage, 32, () -> {
                require(human.onGround(), "step control starts after fresh native floor support");
                var move = SableOwnedBodyMotion.move(human, new MovementVector(.6, 0, 0),
                        new SableOwnedBodyMotion.MoveOptions(true, null,
                                SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                                SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER));
                var trace = move.collisionScope().nativeCollisionTrace();
                boolean slabSelected = trace.stream().anyMatch(row -> row.selectedState() != null
                        && row.selectedState().is(Blocks.STONE_SLAB));
                if (!slabSelected) throw new GameTestAssertException(logPositiveStepFailure(human, move,
                        "authored-slab-not-selected"));
                double stepRise = trace.stream().mapToDouble(row -> row.responseY() - row.appliedY()).sum();
                if (!(stepRise > .25 && stepRise <= human.maxUpStep() + .02))
                    throw new GameTestAssertException(logPositiveStepFailure(human, move,
                            "step-rise-out-of-range=" + stepRise));
                Vector3d localFeet = localPosition(plot, human.position());
                double slabTop = obstacle.getY() + .5;
                if (!(localFeet.y >= slabTop - .08 && localFeet.y <= slabTop + .08))
                    throw new GameTestAssertException(logPositiveStepFailure(human, move,
                            "final-foot-height=" + localFeet.y + ", slabTop=" + slabTop));
                BlockPos aboveHead = BlockPos.containing(localFeet.x, localFeet.y + human.getBbHeight() - .01,
                        localFeet.z);
                require(LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), aboveHead).isAir(),
                        "positive step control has clear head space, not a ceiling penetration");
                helper.succeed();
            });
        });
    }

    private static String logPositiveStepFailure(
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableOwnedBodyMotion.MoveResult move, String failureKind) {
        String tag = "owned-native-step-" + human.getUUID();
        Object trace = move == null || move.collisionScope() == null
                ? "<no-move-or-scope>" : move.collisionScope().nativeCollisionTrace();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                "[{}] full native positive-step failure actor={} diagnostic={} trace={}", tag, human.getUUID(),
                OwnedNativeMoveDiagnostic.describe(human, move), trace);
        return "positive-step " + failureKind + " actor=" + human.getUUID() + " log=" + tag;
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-exterior-wall-faces"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    public static void exteriorWestWallJumpDoesNotBecomeHumanFloorSupport(GameTestHelper helper) {
        exteriorWallCase(helper, new BlockPos(2800, 300, 2800), ExteriorFace.WEST);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-exterior-wall-faces"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    public static void exteriorEastWallJumpDoesNotBecomeHumanFloorSupport(GameTestHelper helper) {
        exteriorWallCase(helper, new BlockPos(2840, 300, 2800), ExteriorFace.EAST);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-exterior-wall-faces"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    public static void exteriorNorthWallJumpDoesNotBecomeHumanFloorSupport(GameTestHelper helper) {
        exteriorWallCase(helper, new BlockPos(2880, 300, 2800), ExteriorFace.NORTH);
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-exterior-wall-faces"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    public static void exteriorSouthWallJumpDoesNotBecomeHumanFloorSupport(GameTestHelper helper) {
        exteriorWallCase(helper, new BlockPos(2920, 300, 2800), ExteriorFace.SOUTH);
    }

    private static void exteriorWallCase(GameTestHelper helper, BlockPos sceneOrigin, ExteriorFace face) {
        ServerLevel level = helper.getLevel();
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, 32, 12, 32);
        SableFixtureScene.awaitEmpty(helper, scene, NATIVE_SCENE_READY_TICKS, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(sceneOrigin.getX() + 12.5, sceneOrigin.getY(), sceneOrigin.getZ() + 12.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                        ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                ShuttleCabinFixture.build(level, storage, plot.engineId());
                return storage;
            });
            SableFixtureScene.trackPlot(helper, authored.plot());
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 80, () -> {
                double x = face == ExteriorFace.WEST ? -.4 : face == ExteriorFace.EAST ? 8.4 : 1.5;
                double z = face == ExteriorFace.NORTH ? -.4 : face == ExteriorFace.SOUTH ? 13.4 : 6.5;
                Vector3d worldFeet = authored.plot().subLevel().logicalPose().transformPosition(new Vector3d(
                        authored.result().getX() + x, authored.result().getY(), authored.result().getZ() + z));
                BlockPos supportPos = BlockPos.containing(worldFeet.x, sceneOrigin.getY() - 1, worldFeet.z);
                var supportState = level.getBlockState(supportPos);
                var supportShape = supportState.getCollisionShape(level, supportPos);
                require(!supportShape.isEmpty(), "exterior spawn terrain support shape is nonempty at " + supportPos
                        + " state=" + supportState);
                double supportTop = supportPos.getY() + supportShape.max(net.minecraft.core.Direction.Axis.Y);
                require(Math.abs(supportTop - sceneOrigin.getY()) < 1.0e-6,
                        "exterior spawn has loaded terrain support shape at " + supportPos + " shape=" + supportShape);
                worldFeet.y = supportTop + .08;
                var human = BoundHumanFixture.create(level, new Vec3(worldFeet.x, worldFeet.y, worldFeet.z),
                        "exterior-" + face.name().toLowerCase() + "-wall-jump");
                require(human.getBoundingBox().minY >= supportTop,
                        "exterior Human starts above actual terrain support plane: feet=" + human.getBoundingBox().minY
                                + ", top=" + supportTop + ", support=" + supportPos + ", shape=" + supportShape);
                awaitExteriorTerrainSupport(helper, human, 80, () ->
                        new ExteriorWallProbe(helper, human, authored.plot(), authored.result(), face,
                                sceneOrigin.getY(), supportPos, supportShape, worldFeet.y).run(0));
            });
        });
    }

    private static void awaitExteriorTerrainSupport(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int ticksLeft, Runnable supported) {
        var result = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f);
        if (result.isPresent() && human.onGround()) { supported.run(); return; }
        if (ticksLeft <= 0) throw new GameTestAssertException("exterior human did not naturally land on terrain pad; "
                + "position=" + human.position() + ", onGround=" + human.onGround() + ", native="
                + OwnedNativeMoveDiagnostic.describe(human, null));
        helper.runAfterDelay(1, () -> awaitExteriorTerrainSupport(helper, human, ticksLeft - 1, supported));
    }

    private enum ExteriorFace { WEST, EAST, NORTH, SOUTH }

    private static final class ExteriorWallProbe {
        private final GameTestHelper helper;
        private final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human;
        private final SableFixturePlot plot;
        private final BlockPos storage;
        private final GroundedHarnessWorldStep step = GroundedHarnessWorldStep.observingNativeGeometry();
        private final ExteriorFace face;
        private final double terrainTop;
        private final BlockPos supportPos;
        private final net.minecraft.world.phys.shapes.VoxelShape supportShape;
        private final double initialFeetY;
        private boolean airborneWallContact;
        private int groundedSideContacts;
        private int airborneSideContacts;
        private double maxJumpRise;
        private double closestSideClearance = Double.POSITIVE_INFINITY;
        private final java.util.List<String> trace = new ArrayList<>();

        private ExteriorWallProbe(GameTestHelper helper,
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
                SableFixturePlot plot, BlockPos storage, ExteriorFace face, double terrainTop,
                BlockPos supportPos, net.minecraft.world.phys.shapes.VoxelShape supportShape, double initialFeetY) {
            this.helper = helper; this.human = human; this.plot = plot; this.storage = storage; this.face = face;
            this.terrainTop = terrainTop;
            this.supportPos = supportPos; this.supportShape = supportShape; this.initialFeetY = initialFeetY;
        }

        private void run(int tick) {
        if (tick >= 108) {
                if (!airborneWallContact) {
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                            "[exterior wall probe trace] face={} samples={}", face, trace);
                    throw new GameTestAssertException("no airborne native wall contact; face=" + face
                            + ", groundedContacts=" + groundedSideContacts + ", airborneContacts=" + airborneSideContacts
                            + ", jumpRise=" + maxJumpRise + ", clearance=" + closestSideClearance
                            + ", feet=" + human.position() + ", mass=" + plot.subLevel().getMassTracker().getMass()
                            + ", pose=" + plot.subLevel().logicalPose() + ", pivot="
                            + plot.subLevel().getMassTracker().getCenterOfMass()
                            + ", last=" + trace.get(trace.size() - 1));
                }
                helper.succeed();
                return;
            }
            helper.runAfterDelay(1, () -> {
                Vec3 before = human.position();
                Vec3 velocityBefore = human.getDeltaMovement();
                int inward = tick < 35 ? 1000 : 0;
                int tangent = tick < 55 ? 1000 : 0;
                int wishX = face == ExteriorFace.WEST ? inward : face == ExteriorFace.EAST ? -inward
                        : face == ExteriorFace.NORTH ? tangent : -tangent;
                int wishZ = face == ExteriorFace.NORTH ? inward : face == ExteriorFace.SOUTH ? -inward : tangent;
                boolean jump = tick >= 5 && tick < 55 && tick % 10 == 5;
                var observed = new SableOwnedBodyMotion.MoveResult[1];
                var moved = step.step(human, wishX, wishZ, jump, false,
                        frameLocalXAxisYaw(plot), result -> observed[0] = result);
                Vec3 after = human.position();
                boolean finite = Double.isFinite(after.x) && Double.isFinite(after.y) && Double.isFinite(after.z);
                var info = observed[0] == null ? null : observed[0].collisionInfo();
                var contact = info == null || info.firstCollisions == null ? null : info.firstCollisions.get(plot.subLevel());
                boolean sideContactThisTick = false;
                if (contact != null) {
                    var normal = contact.globalDirection();
                    Vector3d faceNormal = switch (face) {
                        case WEST, EAST -> plot.subLevel().logicalPose().orientation().transform(new Vector3d(1, 0, 0));
                        case NORTH, SOUTH -> plot.subLevel().logicalPose().orientation().transform(new Vector3d(0, 0, 1));
                    };
                    sideContactThisTick = Math.abs(normal.x() * faceNormal.x + normal.y() * faceNormal.y
                            + normal.z() * faceNormal.z) > .7;
                }
                boolean aboveTerrain = human.getY() > terrainTop + .25;
                Vector3d local = plot.subLevel().logicalPose().transformPositionInverse(
                        new Vector3d(after.x, after.y, after.z));
                double clearance = switch (face) {
                    case WEST -> local.x - storage.getX();
                    case EAST -> storage.getX() + ShuttleCabinFixture.WIDTH - local.x;
                    case NORTH -> local.z - storage.getZ();
                    case SOUTH -> storage.getZ() + ShuttleCabinFixture.DEPTH - local.z;
                };
                closestSideClearance = Math.min(closestSideClearance, Math.abs(clearance));
                maxJumpRise = Math.max(maxJumpRise, human.getY() - terrainTop);
                boolean groundedContact = sideContactThisTick && human.onGround();
                boolean airborneContact = sideContactThisTick && observed[0] != null
                        && observed[0].collisionInfoFresh() && !human.onGround()
                        && (info == null || !info.verticalCollisionBelow);
                if (groundedContact) groundedSideContacts++;
                if (airborneContact) airborneSideContacts++;
                trace.add("t=" + tick + " before=" + before + " after=" + after + " ownVel=" + velocityBefore
                        + "->" + human.getDeltaMovement() + " wish=" + wishX + "," + wishZ + " jump=" + jump
                        + " alive=" + human.isAlive() + " health=" + human.getHealth() + " fresh="
                        + (observed[0] != null && observed[0].collisionInfoFresh()) + " contact=" + contact
                        + " side=" + sideContactThisTick + " groundedSide=" + groundedContact + " airborneSide="
                        + airborneContact + " onGround=" + human.onGround() + " verticalBelow="
                        + (info != null && info.verticalCollisionBelow) + " rise=" + (human.getY() - terrainTop)
                        + " clearance=" + clearance + " pose=" + plot.subLevel().logicalPose());
                Vector3d localFeet = localPosition(plot, after);
                boolean belowHullRoof = localFeet.y < storage.getY() + ShuttleCabinFixture.HEIGHT - 1.0;
                if (aboveTerrain && belowHullRoof) {
                    if (human.onGround() || (info != null && info.verticalCollisionBelow))
                        wallFail(tick, before, after, wishX, wishZ, jump, observed[0], "side wall reported floor support");
                    if (airborneContact) airborneWallContact = true;
                }
                if (moved.isEmpty() || observed[0] == null || !finite) {
                    wallFail(tick, before, after, wishX, wishZ, jump, observed[0], "movement rejected/non-finite");
                }
                run(tick + 1);
            });
        }

        private void wallFail(int tick, Vec3 before, Vec3 after, int wishX, int wishZ, boolean jump,
                              SableOwnedBodyMotion.MoveResult observed, String reason) {
            String nativeTrace = observed == null || observed.collisionScope() == null ? "none"
                    : observed.collisionScope().nativeCollisionTrace().toString();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[exterior wall probe first failure] reason={} face={} tick={} requestedInput=({}, {}) jump={} before={} after={} velocity={} native={} nativeCorrections={} history={}",
                    reason, face, tick, wishX, wishZ, jump, before, after, human.getDeltaMovement(),
                    OwnedNativeMoveDiagnostic.describe(human, observed), nativeTrace, trace);
            String compact = reason + "; face=" + face + ", tick=" + tick + ", before=" + before
                    + ", after=" + after + ", request=(" + wishX + "," + wishZ + "), accepted="
                    + (observed == null ? "none" : observed.acceptedOwnDisplacement())
                    + "; full diagnostic recorded in server log";
            throw new GameTestAssertException(compact.length() <= 900 ? compact : compact.substring(0, 897) + "...");
        }
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-owned-hitbox-pose", "shuttle-body-carry", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void trackedHumanHitboxYawIgnoresOtherFramesReusedSinkPose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sceneOrigin = new BlockPos(2160, 300, 1824);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, 128, 8, 32);
        SableFixtureScene.awaitEmpty(helper, scene, 50, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authoredA = SableFixturePlot.allocateAndAuthor(level, new Vector3d(2172.5, 300, 1840.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                        ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                ShuttleCabinFixture.buildHull(level, storage, plot::setStorageBlock);
                return storage;
            });
            var authoredB = SableFixturePlot.allocateAndAuthor(level, new Vector3d(2252.5, 300, 1840.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, DECK_SIZE, 1, DECK_SIZE);
                for (int x = 0; x < DECK_SIZE; x++) for (int z = 0; z < DECK_SIZE; z++)
                    plot.setStorageBlock(storage.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                return storage;
            });
            SableFixtureScene.trackPlot(helper, authoredA.plot());
            SableFixtureScene.trackPlot(helper, authoredB.plot());
            ServerSubLevel frameA = authoredA.plot().subLevel();
            ServerSubLevel frameB = authoredB.plot().subLevel();
            org.joml.Quaterniond yaw45 = new org.joml.Quaterniond().rotationY(Math.PI / 4);
            frameB.logicalPose().orientation().set(yaw45);
            RigidBodyHandle.of(frameB).teleport(frameB.logicalPose().position(), yaw45);
            frameB.updateLastPose();
            frameB.updateBoundingBox();

            var humanA = BoundHumanFixture.create(level,
                    cabinPoint(authoredA.plot(), authoredA.result(), 1.35, 1, 9.5), "owned-hitbox-frame-a");
            var humanB = BoundHumanFixture.create(level,
                    deckFeet(authoredB.plot(), authoredB.result(), 2, 2), "owned-hitbox-frame-b");
            awaitNativeDeckSupport(helper, humanA, 28, () -> awaitNativeDeckSupport(helper, humanB, 32, () -> {
                require(SableOwnedBodyMotion.MoveOptions.serverDefaults().bodyPosePolicy()
                                == SableOwnedBodyMotion.BodyPosePolicy.STOCK_SDK,
                        "ordinary server, corpse, and cargo paths retain the stock SDK body-yaw policy");
                probeTrackedHitboxYaw(helper, humanA, humanB, frameA, frameB,
                        cabinPoint(authoredA.plot(), authoredA.result(), 1.35, 1, 11.5), 0);
            }));
        });
    }

    @GameTestTags({"shuttle-micro-carry", "shuttle-owned-hitbox-pose", "shuttle-body-carry", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = MICRO_TEST_DEADLINE_TICKS)
    public static void ownedCharacterRetainsFreshSubCutoffNativeFrameCarryOnce(GameTestHelper helper) {
        withDeck(helper, new BlockPos(32, 300, 32), 8, 16, MICRO_SCENE_TICKS, (level, plot, storage, scene) -> {
            var cells = new ArrayList<BlockPos>();
            for (int x = 0; x < scene.width; x++) for (int z = 0; z < scene.depth; z++)
                for (int layer = 1; layer <= scene.supportThickness; layer++) cells.add(scene.supportCell(x, layer, z));
            for (BlockPos cell : cells) {
                var state = level.getBlockState(cell);
                require(level.hasChunkAt(cell) && state.is(Blocks.STONE),
                        "micro-carry authored support pad cell is loaded actual stone at " + cell + ": " + state);
            }
            SableFixtureScene.clearStoneSupportPad(level, scene);
            for (BlockPos cell : cells)
                require(level.getBlockState(cell).isAir(), "micro-carry support pad cleared before physics at " + cell);
            require(scene.authored.isEmpty(), "micro-carry exclusively owned support-pad ledger is empty");
            awaitMicroCarryBody(helper, level, plot, storage, scene);
        });
    }

    private static void awaitMicroCarryBody(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
                                             BlockPos storage, SableFixtureScene.Region scene) {
        var handle = RigidBodyHandle.of(plot.subLevel());
        BoundedGameTestWait.await(helper, MICRO_MASS_TICKS, () -> "micro-carry fixture did not acquire valid positive-mass body",
                () -> handle != null && handle.isValid() && plot.subLevel().getMassTracker() != null
                        && plot.subLevel().getMassTracker().getMass() > 0, () -> {
            com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.registerFixture(plot.subLevel());
            require(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.start(level,
                            plot.subLevel(), new com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest(0, 0, 0, 0, MICRO_SERVICE_TICKS)),
                    "micro-carry zero-target physical controller starts");
            helper.testInfo.addListener(new GameTestListener() {
                private void stop() {
                    com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(level, plot.subLevel());
                }
                @Override public void testStructureLoaded(GameTestInfo i) { }
                @Override public void testPassed(GameTestInfo i, GameTestRunner r) { stop(); }
                @Override public void testFailed(GameTestInfo i, GameTestRunner r) { stop(); }
                @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { stop(); }
            });
            awaitMicroCarryHover(helper, level, plot, new Vector3d(storage.getX()+2.5, storage.getY(), storage.getZ()+2.5),
                    MICRO_INITIAL_HOVER_TICKS, 0, -1, () -> {
                var human = BoundHumanFixture.create(level, deckFeet(plot, storage, 2, 2), "micro-owned-human-carry");
                awaitNativeDeckSupport(helper, human, plot, storage, MICRO_SUPPORT_TICKS, () -> {
                    var point = LoadedFrameRayQuery.currentActorPoint(level, human);
                    require(point != null && plot.engineId().equals(point.frameId()),
                            "micro-carry human established current exact-frame floor contact");
                    Vector3d actualStoragePoint = new Vector3d(point.storagePoint().x, point.storagePoint().y,
                            point.storagePoint().z);
                    awaitMicroCarryHover(helper, level, plot, actualStoragePoint, MICRO_POST_SUPPORT_HOVER_TICKS, 0, -1,
                            () -> probeMicroCarry(helper, level, plot, storage, human));
                });
            });
        });
    }

    @GameTestTags("shuttle-doorway-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 400)
    public static void manualDoorwayJumpRemainsBelowActualLintelAndLands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sceneOrigin = new BlockPos(4000, 300, 4000);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, 32, 12, 32);
        SableFixtureScene.awaitEmpty(helper, scene, NATIVE_SCENE_READY_TICKS, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(sceneOrigin.getX() + 12.5, sceneOrigin.getY(), sceneOrigin.getZ() + 12.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleManualFixture.WIDTH,
                        ShuttleManualFixture.HEIGHT, ShuttleManualFixture.DEPTH);
                ShuttleManualFixture.build(level, storage, plot.engineId(), plot::setStorageBlock);
                return storage;
            });
            SableFixtureScene.trackPlot(helper, authored.plot());
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 80, () -> {
                // At x=13.5,z=.8 the standing Human's horizontal body overlaps the
                // actual z=0 doorway lintel, but has the manual fixture's full 1.2-block head clearance.
                Vec3 spawn = cabinPoint(authored.plot(), authored.result(), 13.5, 1, .8);
                var human = BoundHumanFixture.create(level, spawn, "manual-doorway-jump-diagnostic");
                awaitManualDoorwayFloorContact(helper, level, human, authored.plot(), authored.result(), 40, () ->
                        captureManualDoorwayJump(helper, level, human, authored.plot(), authored.result()));
            });
        });
    }

    @GameTestTags({"shuttle-rotating-contact-diagnostic", "shuttle-carry-basis-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 500)
    public static void unsupportedRotatingEastWallSideContactHasNoTangentialNativeCarrier(GameTestHelper helper) {
        withManualCausalCabin(helper, new BlockPos(5000, 300, 5000), fixture -> {
            var handle = RigidBodyHandle.of(fixture.plot().subLevel());
            var frame = fixture.plot().subLevel();
            var level = fixture.level();
            com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.registerFixture(frame);
            require(handle != null && handle.isValid()
                            && com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.start(
                                    level, frame, new com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest(
                                            0, 0, 0, .75, 120)),
                    "manual frame native yaw service starts");
            helper.runAfterDelay(12, () -> {
                var pose = frame.logicalPose();
                // Fixed storage-space contact point; capture code transforms copies through each sampled pose.
                Vector3d localWall = new Vector3d(fixture.storage().getX() + ShuttleManualFixture.WIDTH,
                        fixture.storage().getY() + 1.5, fixture.storage().getZ() + 5.5);
                Vector3d wallPoint = pose.transformPosition(new Vector3d(localWall));
                Vector3d outward = pose.orientation().transform(new Vector3d(1, 0, 0)).normalize();
                var sizing = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                        .PlayerCharacterHarnessRegistration.getEntityType().create(level);
                require(sizing != null, "normal human sizing probe available");
                double halfWidth = sizing.getBbWidth() * .5;
                sizing.discard();
                double floorY = fixture.scene().supportSurfaceY() + .05;
                Vec3 start = new Vec3(wallPoint.x + outward.x * (halfWidth + .45), floorY,
                        wallPoint.z + outward.z * (halfWidth + .45));
                var human = BoundHumanFixture.create(level, start, "rotating-east-wall-world-contact");
                attachDiagnosticCleanup(helper, human, () ->
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(level, frame));
                var point = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(point != null && point.frameId() == null,
                        "east-wall actor is a WORLD point before contact; point=" + point + " box=" + human.getBoundingBox());
                awaitDiagnosticWorldSupport(helper, fixture, human, () ->
                        captureRotatingWallSteps(helper, fixture, human, localWall, 20, -1000, false,
                                fixture.storage().getY(), .45, null, false));
            });
        });
    }

    @GameTestTags({"shuttle-rotating-contact-diagnostic", "shuttle-airborne-wall-cause-diagnostic",
            "shuttle-oblique-jump-cause-diagnostic"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 500)
    public static void rotatingEastWallPushesAirborneWorldActorOnZeroInput(GameTestHelper helper) {
        withManualCausalCabin(helper, new BlockPos(5000, 300, 5060), fixture -> {
            var frame = fixture.plot().subLevel();
            var level = fixture.level();
            var handle = RigidBodyHandle.of(frame);
            com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.registerFixture(frame);
            helper.runAfterDelay(12, () -> {
                // Fixed local contact point at the +Z lever arm: positive yaw moves the east wall toward +X.
                Vector3d localWall = new Vector3d(fixture.storage().getX() + ShuttleManualFixture.WIDTH,
                        fixture.storage().getY() + 1.5, fixture.storage().getZ() + 7.5);
                var pose = frame.logicalPose();
                Vector3d wallPoint = pose.transformPosition(new Vector3d(localWall));
                Vector3d outward = pose.orientation().transform(new Vector3d(1, 0, 0)).normalize();
                var sizing = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                        .PlayerCharacterHarnessRegistration.getEntityType().create(level);
                require(sizing != null, "normal human sizing probe available");
                double halfWidth = sizing.getBbWidth() * .5;
                sizing.discard();
                double initialGap = .03;
                Vec3 start = new Vec3(wallPoint.x + outward.x * (halfWidth + initialGap),
                        fixture.scene().supportSurfaceY() + .05,
                        wallPoint.z + outward.z * (halfWidth + initialGap));
                var human = BoundHumanFixture.create(level, start, "rotating-east-wall-airborne-push");
                attachDiagnosticCleanup(helper, human, () ->
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(level, frame));
                var point = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(point != null && point.frameId() == null,
                        "airborne push actor begins in WORLD basis; point=" + point);
                BlockPos eastWallCell = fixture.storage().offset(ShuttleManualFixture.WIDTH - 1, 1, 7);
                var eastWallState = level.getBlockState(eastWallCell);
                require(eastWallState.is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                                && !eastWallState.getCollisionShape(level, eastWallCell).isEmpty(),
                        "airborne-push starting wall is actual nonempty manual steel");
                var wallShape = eastWallState.getCollisionShape(level, eastWallCell).toAabbs().getFirst();
                var localWallBounds = new net.minecraft.world.phys.AABB(
                        eastWallCell.getX() + wallShape.minX, eastWallCell.getY() + wallShape.minY,
                        eastWallCell.getZ() + wallShape.minZ, eastWallCell.getX() + wallShape.maxX,
                        eastWallCell.getY() + wallShape.maxY, eastWallCell.getZ() + wallShape.maxZ);
                require(!HullTerrainGeometry.againstTerrain(localWallBounds,
                                frame.logicalPose().bakeIntoMatrix(new org.joml.Matrix4d()),
                                human.getBoundingBox(), new dev.ryanhcode.sable.api.math.LevelReusedVectors()).overlaps(),
                        "initial .03m gap is nonpenetrating against transformed actual east-wall collision shape");
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "[AIRBORNE_WALL_SETUP] initialGap={}m actualWallCell={} humanBounds={} state={} shipInitiallyStationary=true",
                        initialGap, eastWallCell, human.getBoundingBox(), eastWallState);
                awaitDiagnosticWorldSupport(helper, fixture, human, () -> {
                    require(handle != null && handle.isValid(), "manual frame native body remains valid after support");
                    // Start stationary. The observer jumps first and applies this native ship impulse only
                    // after the actor is above the deck and still rising.
                    captureRotatingWallSteps(helper, fixture, human, localWall, 20, 0, true,
                            fixture.storage().getY() + 1, initialGap, handle, true);
                });
            });
        });
    }

    @GameTestTags("shuttle-airborne-wall-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 500)
    public static void rotatingManualDoorwayJumpCapturesFirstRejectedMove(GameTestHelper helper) {
        withManualCausalCabin(helper, new BlockPos(7400, 300, 5000), fixture -> {
            Vec3 feet = cabinPoint(fixture.plot(), fixture.storage(), 13.5, 1, .8);
            var human = BoundHumanFixture.create(fixture.level(), feet, "rotating-manual-doorway-jump-cause");
            var frame = fixture.plot().subLevel();
            attachDiagnosticCleanup(helper, human, () ->
                    com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(
                            fixture.level(), frame));
            awaitManualDoorwayFloorContact(helper, fixture.level(), human, fixture.plot(), fixture.storage(), 40, () ->
                    captureManualDoorwayJump(helper, fixture.level(), human, fixture.plot(), fixture.storage(), true));
        });
    }

    @GameTestTags("shuttle-oblique-jump-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 850)
    public static void obliqueYaw45DoorwayJumpCapturesFirstRejectedMove(GameTestHelper helper) {
        runObliqueDoorwayPhase(helper, new BlockPos(100, 300, 1000), 45);
    }

    @GameTestTags("shuttle-oblique-jump-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 850)
    public static void obliqueYaw135DoorwayJumpCapturesFirstRejectedMove(GameTestHelper helper) {
        runObliqueDoorwayPhase(helper, new BlockPos(250, 300, 1000), 135);
    }

    @GameTestTags("shuttle-oblique-jump-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 850)
    public static void obliqueYaw225DoorwayJumpCapturesFirstRejectedMove(GameTestHelper helper) {
        runObliqueDoorwayPhase(helper, new BlockPos(400, 300, 1000), 225);
    }

    @GameTestTags("shuttle-oblique-jump-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 850)
    public static void obliqueYaw315DoorwayJumpCapturesFirstRejectedMove(GameTestHelper helper) {
        runObliqueDoorwayPhase(helper, new BlockPos(550, 300, 1000), 315);
    }

    @GameTestTags("shuttle-world-air-braking-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void stationaryWorldHumanUsesNormalAirborneHorizontalRetention(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(9000, 300, 9000);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, origin, 32, 12, 32);
        SableFixtureScene.awaitEmpty(helper, scene, NATIVE_SCENE_READY_TICKS, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var feet = new Vec3(origin.getX() + 16.5, scene.supportSurfaceY() + .05, origin.getZ() + 16.5);
            var human = BoundHumanFixture.create(level, feet, "stationary-world-air-braking-control");
            attachWorldControlCleanup(helper, human);
            captureWorldAirBrakingControl(helper, level, scene, human);
        });
    }

    private static void attachWorldControlCleanup(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human) {
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() { if (!human.isRemoved()) human.discard(); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
    }

    private static void captureWorldAirBrakingControl(GameTestHelper helper, ServerLevel level,
            SableFixtureScene.Region scene,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human) {
        enum Stage { WORLD_SUPPORT, WALK, JUMP, AIR }
        Stage[] stage = {Stage.WORLD_SUPPORT};
        int[] walkTicks = {0}, airTicks = {0}, preEvents = {0};
        Vec3[] previousPostVelocity = {null};
        boolean[] worldSupportProven = {false};
        long registered = level.getServer().getTickCount();
        Consumer<ServerTickEvent.Pre> listener = new Consumer<>() {
            private boolean closed;
            private void close() { if (!closed) { closed = true; NeoForge.EVENT_BUS.unregister(this); } }
            @Override public void accept(ServerTickEvent.Pre event) {
                if (closed || event.getServer() != level.getServer()
                        || event.getServer().getTickCount() <= registered) return;
                try {
                    if (++preEvents[0] > 80) throw new GameTestAssertException(
                            "world-air-braking control exceeded bounded 80 owner steps; stage=" + stage[0]);
                    Stage stepStage = stage[0];
                    int wishX = stepStage == Stage.WALK || stepStage == Stage.JUMP ? 1000 : 0;
                    boolean jump = stepStage == Stage.JUMP;
                    Vec3 beforeVelocity = human.getDeltaMovement();
                    var beforePoint = LoadedFrameRayQuery.currentActorPoint(level, human);
                    boolean groundedBefore = human.onGround();
                    AtomicReference<SableOwnedBodyMotion.MoveResult> observed = new AtomicReference<>();
                    var result = GroundedHarnessWorldStep.observingNativeGeometry().step(human, wishX, 0,
                            jump, false, 0f, observed::set);
                    var move = observed.get();
                    if (move == null) throw new GameTestAssertException("WORLD air control native observer missing");
                    if (result.isEmpty()) {
                        var proof = move.nativeContactProofDiagnostic();
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                "[WORLD_AIR_BRAKING_REJECT] stage={} tick={} request={} measured={} branch={} reason={} rowIndex={} proof={}",
                                stage[0], level.getServer().getTickCount(), move.requestedOwnDisplacement(),
                                move.measuredOwnDisplacement(), proof.branch(), proof.reason(), proof.rowIndex(), proof);
                        var scope = move.collisionScope();
                        if (scope != null) com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                "[WORLD_AIR_BRAKING_REJECT_FULL_TRACE] {}", scope.nativeCollisionTrace());
                        throw new GameTestAssertException("world-air-braking native owner step rejected; stage=" + stage[0]
                                + " branch=" + proof.branch() + " reason=" + proof.reason() + " row=" + proof.rowIndex());
                    }
                    Vec3 afterVelocity = human.getDeltaMovement();
                    var afterPoint = LoadedFrameRayQuery.currentActorPoint(level, human);
                    boolean worldOnly = beforePoint != null && beforePoint.frameId() == null
                            && move.beforePoint() != null && move.beforePoint().frameId() == null
                            && move.afterPoint() != null && move.afterPoint().frameId() == null
                            && afterPoint != null && afterPoint.frameId() == null;
                    require(worldOnly, "no-ship control must remain in WORLD basis at every owner step");
                    require(human.airMotionReference().isEmpty(), "stationary WORLD control has no AirMotionReference");
                    require(!com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(human)
                                    && !com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(human),
                            "stationary WORLD control has no external flight state");
                    var scope = move.collisionScope();
                    var worldClip = scope == null ? null : scope.worldClipObservation();
                    if (stage[0] == Stage.WORLD_SUPPORT) {
                        boolean supported = human.onGround() && worldClip != null && worldClip.complete()
                                && worldClip.downwardFloorClip()
                                && level.getBlockState(BlockPos.containing(human.getX(), scene.supportSurfaceY() - .01,
                                        human.getZ())).is(Blocks.STONE);
                        if (supported) {
                            worldSupportProven[0] = true;
                            stage[0] = Stage.WALK;
                        }
                    } else if (stage[0] == Stage.WALK) {
                        walkTicks[0]++;
                        double horizontalSpeedPerSecond = Math.hypot(afterVelocity.x, afterVelocity.z) * 20d;
                        if (walkTicks[0] >= 8 && horizontalSpeedPerSecond > 1d && human.onGround())
                            stage[0] = Stage.JUMP;
                    } else if (stage[0] == Stage.JUMP) {
                        require(groundedBefore && !human.onGround(), "ordinary jump must leave proven WORLD support");
                        stage[0] = Stage.AIR;
                        previousPostVelocity[0] = afterVelocity;
                    } else {
                        require(!human.onGround(), "the four no-input samples must remain airborne");
                        double expectedX = beforeVelocity.x * com.juicyslew.moonstation14.ms14.player_body_control.movement
                                .GroundedHarnessPhysics.AIR_HORIZONTAL_RETENTION;
                        double expectedZ = beforeVelocity.z * com.juicyslew.moonstation14.ms14.player_body_control.movement
                                .GroundedHarnessPhysics.AIR_HORIZONTAL_RETENTION;
                        double continuityX = previousPostVelocity[0] == null ? Double.POSITIVE_INFINITY
                                : Math.abs(previousPostVelocity[0].x - beforeVelocity.x);
                        double continuityZ = previousPostVelocity[0] == null ? Double.POSITIVE_INFINITY
                                : Math.abs(previousPostVelocity[0].z - beforeVelocity.z);
                        require(continuityX <= velocityArithmeticAllowance(previousPostVelocity[0].x, beforeVelocity.x)
                                        && continuityZ <= velocityArithmeticAllowance(previousPostVelocity[0].z, beforeVelocity.z),
                                "WORLD native entity tick altered horizontal velocity between owner steps: dx="
                                        + continuityX + " dz=" + continuityZ);
                        require(Math.abs(afterVelocity.x - expectedX)
                                        <= velocityArithmeticAllowance(afterVelocity.x, expectedX)
                                        && Math.abs(afterVelocity.z - expectedZ)
                                        <= velocityArithmeticAllowance(afterVelocity.z, expectedZ),
                                "WORLD no-input air retention differs from .91F motor factor: expected="
                                        + expectedX + "/" + expectedZ + " actual=" + afterVelocity.x + "/" + afterVelocity.z);
                        previousPostVelocity[0] = afterVelocity;
                        airTicks[0]++;
                    }
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "[WORLD_AIR_BRAKING_STEP] tick={} stage={} wishX={} jump={} groundBefore={} groundAfter={} velocityBefore={} velocityAfter={} request={} measuredOwn={} basis={}=>{} airReference={} externalFlight={} worldSupportProven={} airIndex={}",
                            level.getServer().getTickCount(), stepStage, wishX, jump, groundedBefore, human.onGround(),
                            beforeVelocity, afterVelocity, move.requestedOwnDisplacement(), move.measuredOwnDisplacement(),
                            move.beforePoint().frameId(), move.afterPoint().frameId(), human.airMotionReference().orElse(null),
                            com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(human),
                            worldSupportProven[0], airTicks[0]);
                    if (airTicks[0] == 4) {
                        require(worldSupportProven[0] && walkTicks[0] >= 8,
                                "control must include real world support and scripted walk before jump");
                        close();
                        BoundedGameTestWait.safeDelay(helper, 1, helper::succeed);
                    }
                } catch (RuntimeException | AssertionError failure) {
                    close(); BoundedGameTestWait.safeDelay(helper, 1, () -> helper.testInfo.fail(failure));
                }
            }
        };
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() { NeoForge.EVENT_BUS.unregister(listener); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
        NeoForge.EVENT_BUS.addListener(listener);
    }

    private static double velocityArithmeticAllowance(double a, double b) {
        return 8d * (Math.ulp(a) + Math.ulp(b));
    }

    private static void runObliqueDoorwayPhase(GameTestHelper helper, BlockPos origin, double targetDegrees) {
        withManualCausalCabin(helper, origin, fixture -> {
            var frame = fixture.plot().subLevel();
            var level = fixture.level();
            var handle = RigidBodyHandle.of(frame);
            helper.testInfo.addListener(new GameTestListener() {
                private void cleanup() {
                    com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(level, frame);
                }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
            });
            require(handle != null && handle.isValid(), "native yaw phase fixture body valid before body setup");
            Vec3 feet = cabinPoint(fixture.plot(), fixture.storage(), 13.5, 1, .8);
            var human = BoundHumanFixture.create(level, feet, "oblique-doorway-jump-" + (int) targetDegrees);
            attachDiagnosticCleanup(helper, human, () ->
                    com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(level, frame));
            awaitManualDoorwayFloorContact(helper, level, human, fixture.plot(), fixture.storage(), 40, () -> {
                com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.registerFixture(frame);
                require(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.start(
                                level, frame, new com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest(
                                        0, 0, 0, .75, 400)),
                        "native yaw phase fixture motion starts after real support");
                var initialOrientation = new Quaterniond(frame.logicalPose().orientation());
                awaitObliqueYawPhase(helper, fixture, initialOrientation, Math.toRadians(targetDegrees),
                        240, new ObliqueYawPhaseTracker(), () -> {
                    var currentYaw = new Quaterniond(frame.logicalPose().orientation());
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "[OBLIQUE_DOORWAY_PHASE] requestedDegrees={} reachedQuaternion={}",
                            targetDegrees, currentYaw);
                    captureManualDoorwayJump(helper, level, human, fixture.plot(), fixture.storage(),
                            DoorwayCaptureMode.OBLIQUE_MOVING_ACCEPTANCE);
                });
            });
        });
    }

    private static final class ObliqueYawPhaseTracker {
        double previousSignedYaw;
        double accumulatedAbsoluteYaw;
    }

    private static void awaitObliqueYawPhase(GameTestHelper helper, ManualCausalFixture fixture,
            Quaterniond initialOrientation, double targetRadians, int ticksLeft,
            ObliqueYawPhaseTracker tracker, Runnable reached) {
        var relative = new Quaterniond(initialOrientation).invert()
                .mul(new Quaterniond(fixture.plot().subLevel().logicalPose().orientation())).normalize();
        double signed = Math.atan2(2d * (relative.w * relative.y + relative.x * relative.z),
                1d - 2d * (relative.y * relative.y + relative.x * relative.x));
        double delta = signed - tracker.previousSignedYaw;
        while (delta > Math.PI) delta -= 2d * Math.PI;
        while (delta < -Math.PI) delta += 2d * Math.PI;
        tracker.accumulatedAbsoluteYaw += Math.abs(delta);
        tracker.previousSignedYaw = signed;
        if (Math.abs(tracker.accumulatedAbsoluteYaw - targetRadians) <= .04d) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[OBLIQUE_DOORWAY_PHASE_REACHED] accumulatedDegrees={} signedDegrees={} quaternion={}",
                    Math.toDegrees(tracker.accumulatedAbsoluteYaw), Math.toDegrees(signed), relative);
            reached.run();
            return;
        }
        if (ticksLeft <= 0 || tracker.accumulatedAbsoluteYaw > targetRadians + .08d) {
            helper.testInfo.fail(new GameTestAssertException("native yaw phase not observed: targetDegrees="
                    + Math.toDegrees(targetRadians) + " accumulatedDegrees="
                    + Math.toDegrees(tracker.accumulatedAbsoluteYaw) + " signedDegrees="
                    + Math.toDegrees(signed)));
            return;
        }
        helper.runAfterDelay(1, () -> awaitObliqueYawPhase(helper, fixture, initialOrientation,
                targetRadians, ticksLeft - 1, tracker, reached));
    }

    @GameTestTags({"shuttle-landing-cause-diagnostic", "shuttle-carry-basis-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 600)
    public static void steadyTranslationJumpCapturesFirstLandingCarrierAndWorldDisplacement(GameTestHelper helper) {
        withManualCausalCabin(helper, new BlockPos(6200, 300, 7000), fixture -> {
            Vec3 feet = cabinPoint(fixture.plot(), fixture.storage(), 13.5, 1, 6.5);
            var human = BoundHumanFixture.create(fixture.level(), feet, "steady-translation-landing-diagnostic");
            attachDiagnosticCleanup(helper, human, () ->
                    com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(
                            fixture.level(), fixture.plot().subLevel()));
            awaitManualDoorwayFloorContact(helper, fixture.level(), human, fixture.plot(), fixture.storage(), 40, () -> {
                var frame = fixture.plot().subLevel();
                com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.registerFixture(frame);
                require(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.start(
                                fixture.level(), frame,
                                new com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest(0, 0, 2, 0, 120)),
                        "steady translation controller starts");
                captureTranslatedLanding(helper, fixture, human, 0, new ArrayList<>());
            });
        });
    }

    private record ManualCausalFixture(ServerLevel level, SableFixtureScene.Region scene, SableFixturePlot plot,
                                       ShuttleCabinFixture.Built cabin) {
        BlockPos storage() { return cabin.origin(); }
    }

    @GameTestTags("shuttle-rotation-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 2600)
    public static void unoccupiedNativeRotationCauseDiagnostic(GameTestHelper helper) {
        runRotationCauseDiagnostic(helper, new BlockPos(600, 200, 600), false);
    }

    @GameTestTags("shuttle-rotation-cause-diagnostic")
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 2600)
    public static void occupiedNativeRotationCauseDiagnostic(GameTestHelper helper) {
        runRotationCauseDiagnostic(helper, new BlockPos(900, 200, 600), true);
    }

    static void nativeYawFeedbackAfterRollPerturbation(GameTestHelper helper) {
        withManualCausalCabin(helper, new BlockPos(1500, 200, 600), fixture -> {
            var frame = fixture.plot().subLevel();
            var handle = RigidBodyHandle.of(frame);
            require(handle != null && handle.isValid(), "yaw perturbation native body exists frame="+fixture.plot().engineId());
            ShuttleFixtureMotionService.registerFixture(frame);
            var run = new YawPerturbationRun(fixture, handle);
            attachDiagnosticCleanup(helper, null, () -> {
                if (run.closed.compareAndSet(false, true)) {
                    if (run.observations.failure() != null) dumpYawObservations(run, "cleanup-premise-failure");
                    ShuttleFixtureMotionService.disableAngularControlObservation(fixture.level(), frame);
                    if (run.observationListener != null) NeoForge.EVENT_BUS.unregister(run.observationListener);
                    ShuttleFixtureMotionService.stop(fixture.level(), frame);
                    ShuttleFixtureMotionService.unregisterFixture(fixture.level(), frame);
                    fixture.plot().close();
                }
            });
            HullImpactSubstepsExpectation.requireNormalSix(fixture.level(), "yaw-perturbation");
            require(ShuttleFixtureMotionService.start(fixture.level(), frame,
                    new ShuttleMotionRequest(0, 1.5, 0, 0, 600)), "yaw perturbation physical lift starts");
            awaitRotationLiftHeight(helper, fixture, handle, false, 0,
                    () -> beginYawPerturbationStudy(helper, run));
        });
    }

    private static final class YawPerturbationRun {
        final ManualCausalFixture fixture;
        final RigidBodyHandle handle;
        final AtomicBoolean closed = new AtomicBoolean();
        final YawPerturbationDiagnosticPlan.FirstUnsafe unsafe = new YawPerturbationDiagnosticPlan.FirstUnsafe();
        final YawPerturbationObservationWindow observations;
        Consumer<dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent> observationListener;
        long previousWorldTick;
        int previousSubsteps;
        long holdStartWorldTick;
        int holdStartSubsteps;
        int steadyTicks, waitTicks, holdSamples;
        double baselineTilt, minTilt = Double.POSITIVE_INFINITY, maxTilt, maxOffAxis;
        double preSeedMinTilt = Double.POSITIVE_INFINITY, preSeedMaxTilt, preSeedBaselineTilt = Double.NaN;
        double minYaw = Double.POSITIVE_INFINITY, maxYaw = Double.NEGATIVE_INFINITY;
        boolean preSeedGrowthLogged, growthLogged, seedApplied;
        String previousNativeWitness;
        YawPerturbationRun(ManualCausalFixture fixture, RigidBodyHandle handle) {
            this.fixture = fixture; this.handle = handle; this.observations = new YawPerturbationObservationWindow(fixture.plot().engineId());
        }
    }

    private static void beginYawPerturbationStudy(GameTestHelper helper, YawPerturbationRun run) {
        if (run.closed.get()) return;
        var fixture = run.fixture;
        var frame = fixture.plot().subLevel();
        if (!ShuttleFixtureMotionService.enableAngularControlObservation(fixture.level(), frame)) {
            dumpYawObservations(run, "observation-enable-failure");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("observation-enable",
                    fixture.plot().engineId(), 0, fixture.level().getGameTime(), "registered fixture telemetry enable failed"));
        }
        run.observationListener = event -> observeYawAngularControl(run, event);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, run.observationListener);
        boolean started = ShuttleFixtureMotionService.start(fixture.level(), frame,
                new ShuttleMotionRequest(0, 0, 0, YawPerturbationDiagnosticPlan.TARGET_YAW,
                        YawPerturbationDiagnosticPlan.REQUEST_DURATION));
        if (!started) {
            dumpYawObservations(run, "request-start-failure");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("request-start-failure",
                    fixture.plot().engineId(), 0, fixture.level().getGameTime(), "single yaw request did not start"));
        }
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), frame);
        if (snapshot == null || !snapshot.active() || snapshot.sdkSubsteps() != 6) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[yaw-roll-cause] phase=request-start-premise frame={} worldTick={} snapshot={}",
                    fixture.plot().engineId(), fixture.level().getGameTime(), snapshot);
            dumpYawObservations(run, "request-start-premise");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("request-start-premise",
                    fixture.plot().engineId(), 0, fixture.level().getGameTime(), "controller or six-substep premise failed"));
        }
        run.previousWorldTick = fixture.level().getGameTime();
        run.previousSubsteps = snapshot.appliedSubsteps();
        helper.runAfterDelay(1, () -> awaitSteadyYaw(helper, run));
    }

    private static void observeYawAngularControl(YawPerturbationRun run,
            dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent event) {
        if (run.closed.get() || event.getPhysicsSystem() == null
                || event.getPhysicsSystem().getLevel() != run.fixture.level()) return;
        var fixture = run.fixture;
        var frame = fixture.plot().subLevel();
        if (!ShuttleFixtureMotionService.angularControlObservationEnabled(fixture.level(), frame)) {
            run.observations.reject("fixture service telemetry disabled while yaw request expected active");
            return;
        }
        var observation = ShuttleFixtureMotionService.latestAngularControlObservation(fixture.level(), frame);
        if (observation == null) return;
        var request = observation.request();
        if (request == null || request.targetX() != 0d || request.targetY() != 0d || request.targetZ() != 0d
                || request.targetYawRate() != YawPerturbationDiagnosticPlan.TARGET_YAW
                || request.durationTicks() != YawPerturbationDiagnosticPlan.REQUEST_DURATION) {
            run.observations.reject("unexpected controller request identity/target");
            return;
        }
        run.observations.accept(observation);
    }

    private static void failYawObservationIfNeeded(GameTestHelper helper, YawPerturbationRun run,
            String phase, int sample, long worldTick) {
        String failure = run.observations.failure();
        if (failure == null) return;
        dumpYawObservations(run, phase + "-premise-failure");
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                "[yaw-roll-cause] phase=observation-premise-failure frame={} probePhase={} sample={} worldTick={} reason={}",
                run.fixture.plot().engineId(), phase, sample, worldTick, failure);
        throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("observation-premise-failure",
                run.fixture.plot().engineId(), sample, worldTick, failure));
    }

    private static void dumpYawObservations(YawPerturbationRun run, String phase) {
        if (run.observations.dumped()) return;
        var fixture = run.fixture;
        var frame = fixture.plot().subLevel();
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), frame);
        if (snapshot != null) {
            if (snapshot.active()) {
                var latest = ShuttleFixtureMotionService.latestAngularControlObservation(fixture.level(), frame);
                if (latest == null) run.observations.reject("active controller has no latest observation at dump");
                else {
                    var accepted = run.observations.accept(latest);
                    if (accepted == YawPerturbationObservationWindow.Result.FAILED)
                        run.observations.reject("latest observation failed terminal flush");
                }
            }
            run.observations.markExpectedOrdinal(snapshot.appliedSubsteps());
        }
        var dump = run.observations.dumpOnce(phase);
        if (dump == null) return;
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[yaw-roll-cause-native-intervals] {}", dump.header());
        for (int i = 0; i < dump.rows().size(); i++) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[yaw-roll-cause-native-interval] record={}/{} {}", i + 1, dump.rows().size(), dump.rows().get(i));
    }

    private static String yawObservationStatus(YawPerturbationRun run) {
        return "telemetryEnabled="+ShuttleFixtureMotionService.angularControlObservationEnabled(
                run.fixture.level(), run.fixture.plot().subLevel())
                +",latestOrdinal="+run.observations.lastOrdinal()+",rolling="+run.observations.rollingRows()
                +",firstAfterSeed="+run.observations.firstAfterSeedRows()+",late="+run.observations.lateRows()
                +",retained="+run.observations.retainedRows()+",complete="+run.observations.complete()
                +",overflow="+run.observations.overflow()+",alphaField=observedFreeAlphaInput"
                +",intervals=[yaw-roll-cause-native-intervals]";
    }

    private static void awaitSteadyYaw(GameTestHelper helper, YawPerturbationRun run) {
        if (run.closed.get()) return;
        var fixture = run.fixture;
        var frame = fixture.plot().subLevel();
        long worldTick = fixture.level().getGameTime();
        failYawObservationIfNeeded(helper, run, "spinup", run.waitTicks, worldTick);
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), frame);
        var pose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(fixture.level()).getPipeline()
                .readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
        var omega = run.handle.getAngularVelocity(new Vector3d());
        if (snapshot == null || !snapshot.active() || snapshot.sdkSubsteps() != 6
                || !YawPerturbationDiagnosticPlan.consecutiveWorldStep(run.previousWorldTick, worldTick,
                        run.previousSubsteps, snapshot.appliedSubsteps())
                || !finitePose(pose) || !YawPerturbationDiagnosticPlan.finiteQuaternion(pose.orientation())
                || !finite(omega)) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[yaw-roll-cause] phase=spinup-premise-failure frame={} worldTick={} previousTick={} snapshot={} omegaWorld={} qComponents={}",
                    fixture.plot().engineId(), worldTick, run.previousWorldTick, snapshot, vectorComponents(omega),
                    pose == null ? null : quaternionComponents(pose.orientation()));
            dumpYawObservations(run, "spinup-premise-failure");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("spinup-premise-failure",
                    fixture.plot().engineId(), run.waitTicks, worldTick, "clock/controller/native pose premise failed"));
        }
        double tilt = YawPerturbationDiagnosticPlan.upTiltDegrees(pose.orientation());
        if (!Double.isFinite(tilt)) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[yaw-roll-cause] phase=spinup-invalid-tilt frame={} worldTick={} qComponents={}",
                    fixture.plot().engineId(), worldTick, quaternionComponents(pose.orientation()));
            dumpYawObservations(run, "spinup-invalid-tilt");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("spinup-invalid-tilt",
                    fixture.plot().engineId(), run.waitTicks, worldTick, "native up-tilt invalid"));
        }
        run.preSeedMinTilt = Math.min(run.preSeedMinTilt, tilt);
        run.preSeedMaxTilt = Math.max(run.preSeedMaxTilt, tilt);
        if (!Double.isFinite(run.preSeedBaselineTilt)) run.preSeedBaselineTilt = tilt;
        String witness = nativeWitness(pose, omega, frame);
        if (!run.preSeedGrowthLogged && tilt - run.preSeedBaselineTilt >= .1d) {
            run.preSeedGrowthLogged = true;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[yaw-roll-cause] phase=first-growth-spinup frame={} worldTick={} tiltDeg={} preSeedBaselineTiltDeg={} witness={}",
                    fixture.plot().engineId(), worldTick, Double.toString(tilt), Double.toString(run.preSeedBaselineTilt), witness);
        }
        run.maxOffAxis = Math.max(run.maxOffAxis, Math.hypot(omega.x, omega.z));
        run.minYaw = Math.min(run.minYaw, omega.y); run.maxYaw = Math.max(run.maxYaw, omega.y);
        if (tilt > YawPerturbationDiagnosticPlan.GROSS_UP_TILT_DEGREES) {
            dumpYawObservations(run, "spinup-unsafe-capsize");
            if (run.unsafe.first()) com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[yaw-roll-cause] phase=spinup-unsafe-capsize frame={} worldTick={} tiltDeg={} limitDeg={} previousNativeWitness={} currentNativeWitness={} telemetry={}",
                    fixture.plot().engineId(), worldTick, Double.toString(tilt), YawPerturbationDiagnosticPlan.GROSS_UP_TILT_DEGREES,
                    run.previousNativeWitness, witness, yawObservationStatus(run));
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("spinup-unsafe-capsize",
                    fixture.plot().engineId(), run.waitTicks, worldTick, "tilt="+Double.toString(tilt)+" limit=45; see logged witness"));
        }
        run.previousNativeWitness = witness;
        run.steadyTicks = YawPerturbationDiagnosticPlan.nextSteadyCount(run.steadyTicks,
                YawPerturbationDiagnosticPlan.stableYaw(omega.x, omega.y, omega.z));
        run.previousWorldTick = worldTick;
        run.previousSubsteps = snapshot.appliedSubsteps();
        run.waitTicks++;
        if (run.waitTicks % 20 == 0) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[yaw-roll-cause] phase=spinup-settle frame={} worldTick={} waitTicks={} stableTicks={} omegaWorld={} qComponents={} upTiltDeg={} preSeedTiltRange=[{},{}]",
                fixture.plot().engineId(), worldTick, run.waitTicks, run.steadyTicks, vectorComponents(omega), quaternionComponents(pose.orientation()),
                tilt, run.preSeedMinTilt, run.preSeedMaxTilt);
        if (run.steadyTicks >= YawPerturbationDiagnosticPlan.STEADY_TICKS_REQUIRED) {
            if (run.seedApplied) {
                dumpYawObservations(run, "seed-once-premise");
                throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("seed-once-premise",
                        fixture.plot().engineId(), run.waitTicks, worldTick, "roll seed already applied"));
            }
            run.seedApplied = true;
            applyYawRollSeed(helper, run, pose, omega, snapshot, worldTick);
            return;
        }
        if (run.waitTicks >= YawPerturbationDiagnosticPlan.STEADY_WAIT_MAX_TICKS) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[yaw-roll-cause] phase=steady-timeout frame={} worldTick={} waitTicks={} stableTicks={} omegaWorld={} witness={}",
                    fixture.plot().engineId(), worldTick, run.waitTicks, run.steadyTicks, vectorComponents(omega), witness);
            dumpYawObservations(run, "steady-timeout");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("steady-timeout",
                    fixture.plot().engineId(), run.waitTicks, worldTick, "40 consecutive yaw samples not achieved"));
        }
        helper.runAfterDelay(1, () -> awaitSteadyYaw(helper, run));
    }

    private static void applyYawRollSeed(GameTestHelper helper, YawPerturbationRun run,
            dev.ryanhcode.sable.companion.math.Pose3dc pose, Vector3d beforeOmega,
            ShuttleFixtureMotionService.Snapshot snapshot, long worldTick) {
        var fixture = run.fixture;
        var frame = fixture.plot().subLevel();
        var latest = ShuttleFixtureMotionService.latestAngularControlObservation(fixture.level(), frame);
        if (latest != null && latest.controllerSubstepOrdinal() > run.observations.lastOrdinal())
            run.observations.accept(latest);
        if (latest == null || !run.observations.markSeed(latest.controllerSubstepOrdinal())) {
            dumpYawObservations(run, "seed-observation-premise");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("seed-observation-premise",
                    fixture.plot().engineId(), 0, worldTick, "latest controller ordinal unavailable"));
        }
        failYawObservationIfNeeded(helper, run, "seed", 0, worldTick);
        run.baselineTilt = run.minTilt = run.maxTilt = YawPerturbationDiagnosticPlan.upTiltDegrees(pose.orientation());
        run.maxOffAxis = Math.hypot(beforeOmega.x, beforeOmega.z);
        run.minYaw = run.maxYaw = beforeOmega.y;
        var mass = frame.getMassTracker();
        var sdkVelocity = run.handle.getLinearVelocity(new Vector3d());
        var transport = snapshot.transportVelocity();
        var baselineUp = YawPerturbationDiagnosticPlan.worldUpUnit(pose.orientation());
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[yaw-roll-cause] phase=baseline frame={} worldTick={} qComponents={} worldUp={} upTiltDeg={} preSeedBaselineTilt={} preSeedTiltRange=[{},{}] omegaWorld={} sdkVelocityWorld={} transport={} worldCOM={} pivot={} localCOM={} mass={} inertia9={} request={}",
                fixture.plot().engineId(), worldTick, quaternionComponents(pose.orientation()), vectorComponents(baselineUp),
                run.baselineTilt, run.preSeedBaselineTilt, run.preSeedMinTilt, run.preSeedMaxTilt, vectorComponents(beforeOmega), vectorComponents(sdkVelocity), transport,
                vectorComponents(pose.position()), vectorComponents(pose.rotationPoint()), vectorComponents(mass.getCenterOfMass()),
                mass.getMass(), inertiaComponents(mass.getInertiaTensor()), snapshot.request());
        var worldDelta = YawPerturbationDiagnosticPlan.worldRollDelta(pose.orientation());
        run.handle.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(worldDelta));
        var afterOmega = run.handle.getAngularVelocity(new Vector3d());
        if (!YawPerturbationDiagnosticPlan.achievedDelta(beforeOmega, afterOmega, worldDelta)) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[yaw-roll-cause] phase=seed-premise-failure frame={} worldTick={} requestedWorldDelta={} beforeOmega={} afterOmega={}",
                    fixture.plot().engineId(), worldTick, vectorComponents(worldDelta), vectorComponents(beforeOmega), vectorComponents(afterOmega));
            dumpYawObservations(run, "seed-premise-failure");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("seed-premise-failure",
                    fixture.plot().engineId(), 0, worldTick, "physical local-Z velocity delta not achieved"));
        }
        run.maxOffAxis = Math.max(run.maxOffAxis, Math.hypot(afterOmega.x, afterOmega.z));
        run.minYaw = Math.min(run.minYaw, afterOmega.y); run.maxYaw = Math.max(run.maxYaw, afterOmega.y);
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[yaw-roll-cause] phase=physical-seed frame={} worldTick={} requestedLocalDelta=(0,0,{}) requestedWorldDelta={} beforeOmega={} afterOmega={} achievedDelta={} maxOffAxisOmegaIncludingSeed={}",
                fixture.plot().engineId(), worldTick, YawPerturbationDiagnosticPlan.LOCAL_ROLL_DELTA,
                vectorComponents(worldDelta), vectorComponents(beforeOmega), vectorComponents(afterOmega),
                vectorComponents(new Vector3d(afterOmega).sub(beforeOmega)), run.maxOffAxis);
        run.previousNativeWitness = nativeWitness(pose, afterOmega, frame);
        run.holdStartWorldTick = worldTick;
        run.holdStartSubsteps = snapshot.appliedSubsteps();
        helper.runAfterDelay(1, () -> holdYawPerturbation(helper, run));
    }

    private static void holdYawPerturbation(GameTestHelper helper, YawPerturbationRun run) {
        if (run.closed.get()) return;
        var fixture = run.fixture;
        var frame = fixture.plot().subLevel();
        long worldTick = fixture.level().getGameTime();
        failYawObservationIfNeeded(helper, run, "hold", run.holdSamples, worldTick);
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), frame);
        var pose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(fixture.level()).getPipeline()
                .readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
        var omega = run.handle.getAngularVelocity(new Vector3d());
        if (snapshot == null || !snapshot.active() || snapshot.sdkSubsteps() != 6
                || !YawPerturbationDiagnosticPlan.consecutiveWorldStep(run.previousWorldTick, worldTick,
                        run.previousSubsteps, snapshot.appliedSubsteps())
                || snapshot.remainingTicks() <= 0 || !finitePose(pose)
                || !YawPerturbationDiagnosticPlan.finiteQuaternion(pose.orientation()) || !finite(omega)) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[yaw-roll-cause] phase=hold-premise-failure frame={} holdSample={} worldTick={} previousTick={} snapshot={} omegaWorld={} qComponents={}",
                    fixture.plot().engineId(), run.holdSamples, worldTick, run.previousWorldTick, snapshot,
                    vectorComponents(omega), pose == null ? null : quaternionComponents(pose.orientation()));
            dumpYawObservations(run, "hold-premise-failure");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("hold-premise-failure",
                    fixture.plot().engineId(), run.holdSamples, worldTick, "clock/controller/native pose premise failed"));
        }
        double tilt = YawPerturbationDiagnosticPlan.upTiltDegrees(pose.orientation());
        if (!Double.isFinite(tilt)) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[yaw-roll-cause] phase=hold-invalid-tilt frame={} holdSample={} worldTick={} qComponents={}",
                    fixture.plot().engineId(), run.holdSamples, worldTick, quaternionComponents(pose.orientation()));
            dumpYawObservations(run, "hold-invalid-tilt");
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("hold-invalid-tilt",
                    fixture.plot().engineId(), run.holdSamples, worldTick, "native up-tilt invalid"));
        }
        run.maxTilt = Math.max(run.maxTilt, tilt);
        run.minTilt = Math.min(run.minTilt, tilt);
        run.maxOffAxis = Math.max(run.maxOffAxis, Math.hypot(omega.x, omega.z));
        run.minYaw = Math.min(run.minYaw, omega.y); run.maxYaw = Math.max(run.maxYaw, omega.y);
        run.holdSamples++;
        String currentWitness = nativeWitness(pose, omega, frame);
        boolean growth = tilt - run.baselineTilt >= .1d;
        if (growth && !run.growthLogged) {
            run.growthLogged = true;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[yaw-roll-cause] phase=first-growth frame={} holdSample={} worldTick={} tiltDeg={} baselineTiltDeg={} omegaWorld={} witness={}",
                    fixture.plot().engineId(), run.holdSamples, worldTick, tilt, run.baselineTilt, vectorComponents(omega), currentWitness);
        }
        if (tilt > YawPerturbationDiagnosticPlan.GROSS_UP_TILT_DEGREES) {
            dumpYawObservations(run, "unsafe-capsize");
            if (run.unsafe.first()) com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[yaw-roll-cause] phase=unsafe-capsize frame={} holdSample={} worldTick={} tiltDeg={} limitDeg={} previousNativeWitness={} currentNativeWitness={} telemetry={}",
                    fixture.plot().engineId(), run.holdSamples, worldTick, Double.toString(tilt), YawPerturbationDiagnosticPlan.GROSS_UP_TILT_DEGREES,
                    run.previousNativeWitness, currentWitness, yawObservationStatus(run));
            throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("unsafe-capsize",
                    fixture.plot().engineId(), run.holdSamples, worldTick,
                    "tilt="+Double.toString(tilt)+" limit=45; full prior/current witness logged"));
        }
        run.previousWorldTick = worldTick; run.previousSubsteps = snapshot.appliedSubsteps();
        run.previousNativeWitness = currentWitness;
        if (run.holdSamples % 20 == 0) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[yaw-roll-cause] phase=hold frame={} holdSample={} worldTick={} tiltDeg={} tiltRangeBaselineSeedHold=[{},{}] omegaWorld={} maxOffAxisOmegaFullRequestSeedHold={} yawRangeFullRequestSeedHold=[{},{}] sdkVelocityWorld={} transport={} qComponents={}",
                fixture.plot().engineId(), run.holdSamples, worldTick, tilt, run.minTilt, run.maxTilt, vectorComponents(omega), run.maxOffAxis,
                run.minYaw, run.maxYaw, vectorComponents(run.handle.getLinearVelocity(new Vector3d())), snapshot.transportVelocity(),
                quaternionComponents(pose.orientation()));
        if (run.holdSamples >= YawPerturbationDiagnosticPlan.HOLD_TICKS) {
            dumpYawObservations(run, "terminal-hold");
            if (!run.observations.complete() || run.observations.overflow()
                    || run.observations.firstAfterSeedRows() != YawPerturbationObservationWindow.FIRST_AFTER_SEED_CAP)
                throw new GameTestAssertException(YawPerturbationDiagnosticPlan.failureSummary("observation-window-incomplete",
                        fixture.plot().engineId(), run.holdSamples, worldTick, "ordinal capture incomplete or capped"));
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[yaw-roll-cause] PASS observation only exact-owner-roll-NOT-REPRODUCED frame={} holdSamples={} worldTicks=[{},{}] appliedSubsteps=[{},{}] preSeedTiltRange=[{},{}] tiltRangeBaselineSeedHold=[{},{}] maxOffAxisOmegaFullRequestSeedHold={} yawRangeFullRequestSeedHold=[{},{}] actor=false camera=false packetOrClientReplay=false rawObservedFreeAlphaInputCaptured=true alignedAlphaEstimator=unmeasured",
                    fixture.plot().engineId(), run.holdSamples, run.holdStartWorldTick, worldTick, run.holdStartSubsteps,
                    snapshot.appliedSubsteps(), run.preSeedMinTilt, run.preSeedMaxTilt, run.minTilt, run.maxTilt,
                    run.maxOffAxis, run.minYaw, run.maxYaw);
            helper.succeed(); return;
        }
        helper.runAfterDelay(1, () -> holdYawPerturbation(helper, run));
    }

    private static boolean finite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static String vectorComponents(org.joml.Vector3dc vector) {
        if (vector == null) return "null";
        return "("+Double.toString(vector.x())+","+Double.toString(vector.y())+","+Double.toString(vector.z())+")";
    }

    private static String quaternionComponents(org.joml.Quaterniondc q) {
        if (q == null) return "null";
        return "("+Double.toString(q.x())+","+Double.toString(q.y())+","+Double.toString(q.z())+","+Double.toString(q.w())+")";
    }

    private static String inertiaComponents(org.joml.Matrix3dc i) {
        return "("+Double.toString(i.m00())+","+Double.toString(i.m01())+","+Double.toString(i.m02())+","+
                Double.toString(i.m10())+","+Double.toString(i.m11())+","+Double.toString(i.m12())+","+
                Double.toString(i.m20())+","+Double.toString(i.m21())+","+Double.toString(i.m22())+")";
    }

    private static String nativeWitness(dev.ryanhcode.sable.companion.math.Pose3dc pose, Vector3d omega,
                                        dev.ryanhcode.sable.sublevel.ServerSubLevel frame) {
        var up = YawPerturbationDiagnosticPlan.worldUpUnit(pose.orientation());
        var mass = frame.getMassTracker();
        return "qComponents="+quaternionComponents(pose.orientation())+",worldUp="+vectorComponents(up)
                +",omegaWorld="+vectorComponents(omega)+",worldCOM="+vectorComponents(pose.position())
                +",pivot="+vectorComponents(pose.rotationPoint())
                +",localCOM="+(mass == null ? null : vectorComponents(mass.getCenterOfMass()))
                +",mass="+(mass == null ? null : Double.toString(mass.getMass()))
                +",inertia9="+(mass == null ? null : inertiaComponents(mass.getInertiaTensor()));
    }

    static void cornerNeighborhoodNativeProofDiagnostic(GameTestHelper helper) {
        withManualCausalCabin(helper, new BlockPos(1200, 200, 600), fixture -> {
            var frame = fixture.plot().subLevel();
            var handle = RigidBodyHandle.of(frame);
            require(handle != null && handle.isValid(), "corner diagnostic native body exists");
            ShuttleFixtureMotionService.registerFixture(frame);
            attachDiagnosticCleanup(helper, null, () -> { ShuttleFixtureMotionService.stop(fixture.level(), frame); fixture.plot().close(); });
            HullImpactSubstepsExpectation.requireNormalSix(fixture.level(), "corner-cause");
            require(ShuttleFixtureMotionService.start(fixture.level(), frame, new ShuttleMotionRequest(0, 1.5, 0, 0, 600)),
                    "corner diagnostic physical lift starts");
            awaitRotationLiftHeight(helper, fixture, handle, false, 0, () -> startCornerNeighborhoodProbe(helper, fixture, handle));
        });
    }

    private static void runRotationCauseDiagnostic(GameTestHelper helper, BlockPos origin, boolean occupied) {
        withManualCausalCabin(helper, origin, fixture -> {
            var frame = fixture.plot().subLevel();
            var level = fixture.level();
            var handle = RigidBodyHandle.of(frame);
            require(handle != null && handle.isValid(), "rotation diagnostic native body exists");
            ShuttleFixtureMotionService.registerFixture(frame);
            attachDiagnosticCleanup(helper, null, () -> { ShuttleFixtureMotionService.stop(level, frame); fixture.plot().close(); });
            HullImpactSubstepsExpectation.requireNormalSix(level, "rotation-cause-" + occupied);
            require(ShuttleFixtureMotionService.start(level, frame, new ShuttleMotionRequest(0, 1.5, 0, 0, 600)),
                    "bounded physical clearance lift starts");
            awaitRotationLiftHeight(helper, fixture, handle, occupied, 0, null);
        });
    }

    private static void awaitRotationLiftHeight(GameTestHelper helper, ManualCausalFixture fixture, RigidBodyHandle handle,
                                                boolean occupied, int waited, Runnable afterHover) {
        var frame = fixture.plot().subLevel();
        double bottom = frame.boundingBox().toMojang().minY;
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), frame);
        require(snapshot != null && snapshot.active(), "lift controller remained active while awaiting physical clearance; frame=" + frame.getUniqueId()+" occupied="+occupied);
        var nativePose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(fixture.level()).getPipeline()
                .readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
        require(finitePose(nativePose), "native lift pose remained finite");
        boolean heightReady = bottom >= fixture.scene().supportSurfaceY() + 15;
        if (RotationCauseDiagnosticPlan.setupStage(heightReady, false) != RotationCauseDiagnosticPlan.SetupStage.LIFT) {
            require(ShuttleFixtureMotionService.start(fixture.level(), frame, new ShuttleMotionRequest(0, 0, 0, 0, 2400)),
                    "bounded hover request starts immediately after height readiness; frame=" + frame.getUniqueId()+" occupied="+occupied);
            awaitRotationHover(helper, fixture, handle, occupied, 0, afterHover);
            return;
        }
        if (waited >= 600) throw new GameTestAssertException("physical native lift did not clear pad to 15 blocks; frame="+frame.getUniqueId()+" occupied="+occupied+" bottom=" + bottom
                + " sdkVelocity=" + handle.getLinearVelocity(new Vector3d()));
        helper.runAfterDelay(1, () -> awaitRotationLiftHeight(helper, fixture, handle, occupied, waited + 1, afterHover));
    }

    private static void awaitRotationHover(GameTestHelper helper, ManualCausalFixture fixture, RigidBodyHandle handle,
                                           boolean occupied, int waited, Runnable afterHover) {
        var frame = fixture.plot().subLevel();
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), frame);
        var nativePose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(fixture.level()).getPipeline()
                .readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
        var velocity = handle.getLinearVelocity(new Vector3d());
        var transport = snapshot == null ? null : snapshot.transportVelocity();
        require(snapshot != null && snapshot.active(), "hover controller remained active while awaiting physical settling; frame="+frame.getUniqueId()+" occupied="+occupied);
        require(transport != null && Double.isFinite(transport.x()) && Double.isFinite(transport.y()) && Double.isFinite(transport.z())
                        && finitePose(nativePose) && Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z),
                "native hover pose and transport velocity remain finite");
        if (waited % 20 == 0) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[rotation-cause-hover-wait] frame={} occupied={} waitIndex={} worldTick={} transportY={} sdkLinearY={} appliedSubsteps={}",
                frame.getUniqueId(), occupied, waited, fixture.level().getGameTime(), transport.y(), velocity.y, snapshot.appliedSubsteps());
        if (RotationCauseDiagnosticPlan.setupStage(true, Math.abs(transport.y()) < .05)
                == RotationCauseDiagnosticPlan.SetupStage.READY) {
            if (afterHover != null) { helper.runAfterDelay(1, afterHover); return; }
            var human = occupied ? BoundHumanFixture.create(fixture.level(),
                    cabinPoint(fixture.plot(), fixture.storage(), 13.5, 1, 6.5), "rotation-cause-occupant") : null;
            if (human == null) helper.runAfterDelay(1, () -> runRotationPhases(helper, fixture, handle, null, 0,
                    new RotationPredictionProbe(fixture.plot().subLevel().getUniqueId(), false)));
            else {
                attachDiagnosticCleanup(helper, human, () -> { ShuttleFixtureMotionService.stop(fixture.level(), frame); fixture.plot().close(); });
                awaitManualDoorwayFloorContact(helper, fixture.level(), human, fixture.plot(), fixture.storage(), 80,
                        () -> helper.runAfterDelay(1, () -> runRotationPhases(helper, fixture, handle, human, 0,
                                new RotationPredictionProbe(fixture.plot().subLevel().getUniqueId(), true))));
            }
            return;
        }
        if (waited >= 200) throw new GameTestAssertException("native hover failed to settle within bounded wait; frame="+frame.getUniqueId()+" occupied="+occupied+
                " transportVelocity="+transport+" sdkVelocity=" + velocity);
        helper.runAfterDelay(1, () -> awaitRotationHover(helper, fixture, handle, occupied, waited + 1, afterHover));
    }

    private static void startCornerNeighborhoodProbe(GameTestHelper helper, ManualCausalFixture fixture, RigidBodyHandle handle) {
        var frame = fixture.plot().subLevel();
        require(ShuttleFixtureMotionService.start(fixture.level(), frame, new ShuttleMotionRequest(0, 0, 0, 2, 2400)),
                "corner yaw request starts after physical hover");
        awaitCornerYawReady(helper, fixture, handle, 0);
    }

    private static void awaitCornerYawReady(GameTestHelper helper, ManualCausalFixture fixture, RigidBodyHandle handle, int waited) {
        var omega = handle.getAngularVelocity(new Vector3d());
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), fixture.plot().subLevel());
        boolean finiteOmega = Double.isFinite(omega.x) && Double.isFinite(omega.y) && Double.isFinite(omega.z);
        if (snapshot != null && snapshot.active() && snapshot.sdkSubsteps() == 6 && finiteOmega
                && Math.abs(omega.y - 2d) < .2 && Math.abs(omega.x) < .15 && Math.abs(omega.z) < .15) {
            var human = BoundHumanFixture.create(fixture.level(), cabinPoint(fixture.plot(), fixture.storage(), 17.8, 1, 10.6),
                    "native-corner-cause-human");
            attachDiagnosticCleanup(helper, human, () -> { ShuttleFixtureMotionService.stop(fixture.level(), fixture.plot().subLevel()); fixture.plot().close(); });
            awaitManualDoorwayFloorContact(helper, fixture.level(), human, fixture.plot(), fixture.storage(), 80,
                    () -> {
                        long floorReadyWorldTime = fixture.level().getGameTime();
                        helper.runAfterDelay(1, () -> runCornerNeighborhoodSteps(helper, fixture, human, 0, 0, 0, 0, 0,
                                false, false, false, false, new CornerCauseDiagnosticPlan.LookLatch(), floorReadyWorldTime));
                    });
            return;
        }
        if (waited >= 240) throw new GameTestAssertException("corner yaw did not physically reach target frame="+fixture.plot().engineId()+" setupWaitIndex="+waited+" worldTick="+fixture.level().getGameTime()+" omega=" + omega);
        helper.runAfterDelay(1, () -> awaitCornerYawReady(helper, fixture, handle, waited + 1));
    }

    private static void runCornerNeighborhoodSteps(GameTestHelper helper, ManualCausalFixture fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int tick, int groundedApproach, int sideRows, int compounds, int pureAirCompounds,
            boolean jumpAttempted, boolean jumpValid, boolean airborne,
            boolean airSide, CornerCauseDiagnosticPlan.LookLatch lookLatch, long previousWorldTime) {
        if (tick >= 80) {
            String provenance = "frame=" + fixture.plot().engineId() + " probeIndex=" + tick + " worldTick=" + fixture.level().getGameTime();
            if (!jumpValid || !airborne || !airSide || pureAirCompounds <= 0) throw new GameTestAssertException("corner probe inconclusive " + provenance + ": jump="+jumpValid+" air="+airborne+" airSide="+airSide+" sameMovePureAirCompounds="+pureAirCompounds);
            require(sideRows > 0 && compounds > 0, "corner probe inconclusive " + provenance + ": sideRows=" + sideRows + " sameMoveCompound=" + compounds + " sameMovePureAirCompounds="+pureAirCompounds);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[corner-cause] PASS observation only {}: owner corner rejection NOT REPRODUCED; accepted same-move PURE_SIDE airborne compounds={}; this server probe is not client packet/render/replay or exact replay (owner initial state unknown)", provenance, pureAirCompounds);
            helper.succeed(); return;
        }
            long worldTime = fixture.level().getGameTime();
            if (worldTime != previousWorldTime + 1) throw new GameTestAssertException("corner driver world tick discontinuity previous="+previousWorldTime+" current="+worldTime);
            var frame = fixture.plot().subLevel();
            var frameSnapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), frame);
            if (frameSnapshot == null || !frameSnapshot.active() || frameSnapshot.sdkSubsteps() != 6)
                throw new GameTestAssertException("corner native frame/controller unavailable frame="+fixture.plot().engineId()+" probeIndex="+tick+" worldTick="+worldTime+" snapshot="+frameSnapshot);
            boolean grounded = human.onGround();
            var input = CornerCauseDiagnosticPlan.input(grounded, groundedApproach, jumpAttempted);
            boolean issueJump = input.jump();
            float baseInputYaw = CornerCauseDiagnosticPlan.inputYawForDeck(frame.logicalPose().orientation());
            float selectedBaseInputYaw = lookLatch.select(grounded, baseInputYaw);
            AtomicReference<SableOwnedBodyMotion.MoveResult> observed = new AtomicReference<>();
            var result = GroundedHarnessWorldStep.observingNativeGeometry().step(human, input.wishX(), input.wishZ(),
                    issueJump, false, selectedBaseInputYaw, observed::set);
            var move = observed.get();
            if (move == null) throw new GameTestAssertException("corner probe native move observer absent frame="+fixture.plot().engineId()+" probeIndex="+tick+" worldTick="+worldTime);
            if (result.isEmpty()) {
                var nativePose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(fixture.level()).getPipeline()
                        .readPose(fixture.plot().subLevel(), new dev.ryanhcode.sable.companion.math.Pose3d());
                var angular = RigidBodyHandle.of(fixture.plot().subLevel());
                var omega = angular == null || !angular.isValid() ? null : angular.getAngularVelocity(new Vector3d());
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[corner-cause] rejection frame={} probeIndex={} worldTick={} pose={} omega={} grounded={} proof={} full={}",
                        fixture.plot().engineId(), tick, worldTime, nativePose, omega, grounded, move.nativeContactProofDiagnostic(),
                        OwnedNativeMoveDiagnostic.describe(human, move, true));
                throw new GameTestAssertException("corner probe native movement rejected frame="+fixture.plot().engineId()+" probeIndex="+tick+" worldTick="+worldTime+" proof=" + move.nativeContactProofDiagnostic());
            }
            var scope = move.collisionScope();
            if (!move.collisionInfoFresh() || scope == null || !scope.nativeCollisionTraceComplete()
                    || !scope.geometryTraceComplete() || scope.nativeCollisionTrace() == null)
                throw new GameTestAssertException("corner probe incomplete native trace frame="+fixture.plot().engineId()+" probeIndex="+tick+" worldTick="+worldTime+" move=" + OwnedNativeMoveDiagnostic.describe(human, move, true));
            UUID beforeFrame = move.beforePoint() instanceof LoadedFrameRayQuery.ActorPoint p ? p.frameId() : null;
            var airReference = human.airMotionReference().orElse(null);
            if (!CornerCauseDiagnosticPlan.supportContextValid(grounded, move.beforePoint() != null,
                    fixture.plot().engineId(), beforeFrame,
                    airReference == null ? null : airReference.frameId()))
                throw new GameTestAssertException("corner probe actor had invalid support context frame="+fixture.plot().engineId()+" probeIndex="+tick+" worldTick="+worldTime+" groundedBefore="+grounded+" beforeFrame="+beforeFrame+" frozenAirReference="+airReference);
            var rows = scope.nativeCollisionTrace();
            if (rows.size() > 160) throw new GameTestAssertException("corner trace exceeds bounded native capture frame="+fixture.plot().engineId()+" probeIndex="+tick+" worldTick="+worldTime+" rows="+rows.size());
            boolean departedToAir = grounded && result.isPresent() && !human.onGround();
            boolean launchObserved = issueJump && move.requestedOwnDisplacement().y() > 0 && departedToAir;
            if (departedToAir) lookLatch.latchDeparture(baseInputYaw);
            boolean nowAir = airborne || launchObserved;
            int nextApproach = grounded && !issueJump ? groundedApproach + 1 : groundedApproach;
            var contacts = CornerCauseDiagnosticPlan.classify(rows, fixture.plot().engineId(), fixture.storage().getX(), fixture.storage().getZ());
            int nextSides = sideRows + contacts.sideRows();
            int nextCompounds = compounds + (contacts.compound() ? 1 : 0);
            boolean pureAirCompound = CornerCauseDiagnosticPlan.pureAirCompound(contacts.compound(), human.onGround(),
                    move.nativeContactProofDiagnostic() == null ? null : move.nativeContactProofDiagnostic().branch());
            int nextPureAirCompounds = pureAirCompounds + (pureAirCompound ? 1 : 0);
            boolean nowAirSide = airSide || (!human.onGround() && contacts.sideRows() > 0);
            if (tick % 10 == 0) {
                var sampledPose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(fixture.level()).getPipeline()
                        .readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
                var sampleHandle = RigidBodyHandle.of(frame);
                var sampledOmega = sampleHandle == null || !sampleHandle.isValid() ? null : sampleHandle.getAngularVelocity(new Vector3d());
                if (!finitePose(sampledPose) || sampledOmega == null || !Double.isFinite(sampledOmega.x) || !Double.isFinite(sampledOmega.y)
                        || !Double.isFinite(sampledOmega.z))
                    throw new GameTestAssertException("corner native pose/omega nonfinite frame="+fixture.plot().engineId()+" probeIndex="+tick+" worldTick="+worldTime+" pose="+sampledPose+" omega="+sampledOmega);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "[corner-cause] frame={} probeIndex={} worldTick={} grounded={} jump={} accepted={} pos={} request={} measured={} proof={} nativePose={} omega={}",
                        fixture.plot().engineId(), tick, worldTime, grounded, issueJump, result.isPresent(), human.position(), move.requestedOwnDisplacement(),
                        move.measuredOwnDisplacement(), move.nativeContactProofDiagnostic(), sampledPose, sampledOmega);
            }
            if (pureAirCompound && pureAirCompounds == 0) com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[corner-cause] FIRST_PURE_AIR_COMPOUND frame={} probeIndex={} worldTick={} branch={} full={}",
                    fixture.plot().engineId(), tick, worldTime, move.nativeContactProofDiagnostic().branch(), OwnedNativeMoveDiagnostic.describe(human, move, true));
            if (tick % 10 == 0) com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[corner-cause] coverage frame={} probeIndex={} worldTick={} sideRows={} sameMoveCompound={} sameMovePureAirCompounds={} jumpValid={} airborne={} airSide={}", fixture.plot().engineId(), tick, worldTime, nextSides, nextCompounds, nextPureAirCompounds, jumpValid || launchObserved, nowAir, nowAirSide);
            int nextTick = tick + 1;
            final int sideCountForNext = nextSides;
            final int compoundCountForNext = nextCompounds;
            helper.runAfterDelay(1, () -> runCornerNeighborhoodSteps(helper, fixture, human, nextTick, nextApproach,
                    sideCountForNext, compoundCountForNext, nextPureAirCompounds, jumpAttempted || issueJump, jumpValid || launchObserved,
                    nowAir, nowAirSide, lookLatch, worldTime));
    }

    private static boolean finitePose(dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        var p = pose.position(); var q = pose.orientation(); var r = pose.rotationPoint();
        return Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())
                && Double.isFinite(q.x()) && Double.isFinite(q.y()) && Double.isFinite(q.z()) && Double.isFinite(q.w())
                && Double.isFinite(r.x()) && Double.isFinite(r.y()) && Double.isFinite(r.z());
    }

    /** One bounded native observation driver shared by occupied and unoccupied cases. */
    private static void runRotationPhases(GameTestHelper helper, ManualCausalFixture fixture, RigidBodyHandle handle,
                                          com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
                                          int tick, RotationPredictionProbe prediction) {
        final int total = 1160;
        var phase = RotationCauseDiagnosticPlan.phaseAt(tick);
        if (RotationCauseDiagnosticPlan.retargetAt(tick)) {
            require(ShuttleFixtureMotionService.start(fixture.level(), fixture.plot().subLevel(),
                    new ShuttleMotionRequest(0, phase.verticalY(), 0, phase.yaw(), RotationCauseDiagnosticPlan.REQUEST_DURATION)),
                    "bounded phase request starts at driver tick " + tick);
        }
        if (tick == 360 || tick == 460 || tick == 560 || tick == 660 || tick == 760)
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[rotation-cause-phase] frame={} occupied={} phase={} driverIndex={} worldTick={}",
                    fixture.plot().subLevel().getUniqueId(), human != null, phase.label(), tick, fixture.level().getGameTime());
        var owner = fixture.plot().subLevel();
        var snapshot = ShuttleFixtureMotionService.snapshot(fixture.level(), owner);
        require(snapshot != null && snapshot.active() && snapshot.sdkSubsteps() == 6,
                "native controller active with six substeps at driver tick " + tick);
        var pose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(fixture.level()).getPipeline()
                .readPose(owner, new dev.ryanhcode.sable.companion.math.Pose3d());
        var omega = handle.getAngularVelocity(new Vector3d());
        var up = pose.orientation().transform(new Vector3d(0, 1, 0));
        var linear = handle.getLinearVelocity(new Vector3d());
        prediction.sample(tick, fixture.level().getGameTime(), pose,
                new Vec3(linear.x, linear.y, linear.z), new Vec3(omega.x, omega.y, omega.z), phase.label(),
                snapshot.appliedSubsteps(), RotationCauseDiagnosticPlan.retargetAt(tick));
        if (tick % 20 == 0 || tick == total - 1) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[rotation-cause-native] frame={} occupied={} driverIndex={} worldTick={} modelClockHz=20 sdkCounter=unavailable q=({}, {}, {}, {}) up=({}, {}, {}) linear=({}, {}, {}) omega=({}, {}, {}) worldCOM=({}, {}, {}) storageCOM={} pivot={} mass={} inertia={} appliedSubsteps={}",
                owner.getUniqueId(), human != null, tick, fixture.level().getGameTime(), pose.orientation().x, pose.orientation().y, pose.orientation().z, pose.orientation().w,
                up.x, up.y, up.z, linear.x, linear.y, linear.z, omega.x, omega.y, omega.z,
                pose.position().x, pose.position().y, pose.position().z, owner.getMassTracker().getCenterOfMass(),
                pose.rotationPoint(), owner.getMassTracker().getMass(), owner.getMassTracker().getInertiaTensor(), snapshot.appliedSubsteps());
        if (up.y < Math.cos(Math.toRadians(45))) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn("[rotation-cause-first-unsafe] frame={} occupied={} prior={} currentIndex={} worldTick={} q=({}, {}, {}, {}) up=({}, {}, {}) omega=({}, {}, {})",
                    owner.getUniqueId(), human != null, prediction.previous, tick, fixture.level().getGameTime(), pose.orientation().x, pose.orientation().y,
                    pose.orientation().z, pose.orientation().w, up.x, up.y, up.z, omega.x, omega.y, omega.z);
            throw new GameTestAssertException("native diagnostic exceeded 45 degree capsize observation at driverIndex=" + tick);
        }
        prediction.remember(tick, fixture.level().getGameTime(), pose, up, linear, omega, phase.label());
        if (human != null) {
            AtomicReference<SableOwnedBodyMotion.MoveResult> observed = new AtomicReference<>();
            var bodyBefore = human.position(); var boundsBefore = human.getBoundingBox();
            var stepResult = GroundedHarnessWorldStep.observingNativeGeometry().step(human, 0, 0, false, false,
                    frameLocalXAxisYaw(fixture.plot()), observed::set);
            var move = observed.get();
            if (stepResult.isEmpty()) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                        "[rotation-cause-first-rejection] frame={} occupied=true index={} worldTick={} result=empty proof={} pre={} post={} preBounds={} postBounds={} tracked={} bodyVelocity={} beforeFrame={} afterFrame={} request={} accepted={} measured={} trace={}",
                        owner.getUniqueId(), tick, fixture.level().getGameTime(), move == null ? "missing" : move.nativeContactProofDiagnostic(),
                        bodyBefore, human.position(), boundsBefore, human.getBoundingBox(),
                        LoadedFrameRayQuery.currentActorPoint(fixture.level(), human), human.getDeltaMovement(),
                        move == null ? "missing" : move.beforePoint(), move == null ? "missing" : move.afterPoint(),
                        move == null ? "missing" : move.requestedOwnDisplacement(), move == null ? "missing" : move.acceptedOwnDisplacement(),
                        move == null ? "missing" : move.measuredOwnDisplacement(),
                        move == null || move.collisionScope() == null ? "missing" : move.collisionScope().nativeCollisionTrace());
                throw new GameTestAssertException("occupied native world-step returned empty (rejection/validation) at driverIndex=" + tick);
            }
            if (move == null) throw new GameTestAssertException("occupied world-step present but native movement observation missing at " + tick);
            if (tick % 20 == 0) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[rotation-cause-occupant] frame={} occupied=true driverIndex={} worldTick={} grounded={} position={} tracked={} logicalLookUp=({}, {}, {}) result=APPLIED air={}",
                    owner.getUniqueId(),
                    tick, fixture.level().getGameTime(), human.onGround(), human.position(), LoadedFrameRayQuery.currentActorPoint(fixture.level(), human),
                    human.getUpVector(1).x, human.getUpVector(1).y, human.getUpVector(1).z, !human.onGround());
        }
        if (tick + 1 < total) helper.runAfterDelay(1, () -> runRotationPhases(helper, fixture, handle, human, tick + 1, prediction));
        else {
            require(tick == total - 1, "full bounded rotation phase protocol observed");
            prediction.finish(total);
            ShuttleFixtureMotionService.stop(fixture.level(), owner);
            fixture.plot().close();
            helper.runAfterDelay(1, helper::succeed);
        }
    }

    /** Constant-velocity timeline experiment over already sampled native state; model ticks are driver indices, not SDK ticks. */
    static final class RotationPredictionProbe {
        private record Pending(long target, int horizon, String phase, AuthoritativeFrameTimeline.Result predicted) { }
        private record Key(String phase, int horizon) { }
        private static final class Errors {
            int count; double maxAngle, maxCom; String witness = "none";
            void add(double angle, double com, String data) {
                count++;
                if (angle > maxAngle || com > maxCom) { maxAngle = Math.max(maxAngle, angle); maxCom = Math.max(maxCom, com); witness = data; }
            }
        }
        private final AuthoritativeFrameTimeline timeline = new AuthoritativeFrameTimeline();
        private final UUID levelId = UUID.randomUUID(), frameId;
        private final ArrayDeque<Pending> pending = new ArrayDeque<>(30);
        private final Map<Key, Errors> errors = new java.util.HashMap<>();
        private int expectedIndex, horizon2Count, horizon14Count;
        private Vector3d initialPivot;
        private String previous = "none";
        void remember(int index, long worldTick, dev.ryanhcode.sable.companion.math.Pose3d pose,
                      Vector3d up, Vector3d linear, Vector3d omega, String phase) {
            previous = "frame="+fixtureFrame+",occupied="+occupied+",phase=" + phase + ",index=" + index + ",worldTick=" + worldTick
                    + ",q=" + pose.orientation().x + "/" + pose.orientation().y + "/" + pose.orientation().z + "/" + pose.orientation().w
                    + ",up=" + up.x + "/" + up.y + "/" + up.z + ",linear=" + linear.x + "/" + linear.y + "/" + linear.z
                    + ",omega=" + omega.x + "/" + omega.y + "/" + omega.z;
        }
        private final UUID fixtureFrame;
        private final boolean occupied;
        private long previousWorldTick = Long.MIN_VALUE;
        private int previousApplied = -1;
        RotationPredictionProbe(UUID fixtureFrame, boolean occupied) {
            this.fixtureFrame = fixtureFrame; this.frameId = fixtureFrame; this.occupied = occupied;
            timeline.bind(levelId, frameId);
        }
        void sample(int index, long worldTick, dev.ryanhcode.sable.companion.math.Pose3d pose,
                    Vec3 linear, Vec3 angular, String phase, int appliedSubsteps, boolean retarget) {
            String identity = "frame=" + fixtureFrame + " occupied=" + occupied;
            if (index != expectedIndex) throw new GameTestAssertException(identity+" prediction model index discontinuity expected=" + expectedIndex + " actual=" + index);
            if (previousWorldTick != Long.MIN_VALUE && !RotationCauseDiagnosticPlan.consecutiveWorldClock(previousWorldTick, worldTick))
                throw new GameTestAssertException(identity+" MODEL 20Hz world-time premise failed index="+index+" previousWorldTick="+previousWorldTick+" currentWorldTick="+worldTick);
            if (previousApplied >= 0 && !RotationCauseDiagnosticPlan.sixAppliedSteps(previousApplied, appliedSubsteps, retarget))
                throw new GameTestAssertException(identity+" native applied-substep cadence failed index="+index+" previous="+previousApplied+
                        " current="+appliedSubsteps+" retarget="+retarget);
            expectedIndex++;
            previousWorldTick = worldTick; previousApplied = appliedSubsteps;
            var pivot = new Vector3d(pose.rotationPoint());
            if (initialPivot == null) initialPivot = new Vector3d(pivot);
            else if (pivot.distance(initialPivot) > 1e-8) throw new GameTestAssertException(identity+" storage rotation pivot changed; cannot compare COM basis at index=" + index
                    + " initial=" + initialPivot + " current=" + pivot);
            if (!timeline.receive(levelId, frameId, index, pose, linear, angular))
                throw new GameTestAssertException(identity+" native model sample rejected at driverIndex=" + index);
            for (var it = pending.iterator(); it.hasNext();) {
                var p = it.next();
                if (p.target() != index) continue;
                var actual = timeline.sample(frameId, index);
                double angle;
                try { angle = QuaternionAngularDistance.radians(p.predicted().pose().orientation(), actual.pose().orientation()); }
                catch (IllegalArgumentException invalid) { throw new GameTestAssertException(identity+" invalid prediction/native orientation at target=" + index + ": " + invalid.getMessage()); }
                var pp = p.predicted().pose().position(); var ap = actual.pose().position();
                double comError = pp.distance(ap);
                errors.computeIfAbsent(new Key(p.phase(), p.horizon()), ignored -> new Errors()).add(angle, comError,
                        "source=" + (index-p.horizon()) + ",target=" + index + ",worldTick=" + worldTick + ",pred=" + pp + ",actual=" + ap);
                if (p.horizon() == 2) horizon2Count++; else horizon14Count++;
                it.remove();
            }
            for (int horizon : new int[]{2, 14}) {
                var result = timeline.sample(frameId, index + horizon);
                if (result.kind() == AuthoritativeFrameTimeline.Kind.PREDICTED) {
                    if (pending.size() >= 30) throw new GameTestAssertException(identity+" bounded prediction comparison queue overflow at index=" + index);
                    pending.addLast(new Pending(index + horizon, horizon, phase, result));
                }
            }
        }
        void finish(int total) {
            if (horizon2Count != total - 2 || horizon14Count != total - 14)
                throw new GameTestAssertException("frame="+fixtureFrame+" occupied="+occupied+" prediction comparisons incomplete h2=" + horizon2Count + "/" + (total-2)
                        + " h14=" + horizon14Count + "/" + (total-14) + " pending=" + pending.size());
            errors.forEach((key, value) -> com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[rotation-cause-model-summary] frame={} occupied={} phase={} horizon={} count={} maxAngleRad={} maxWorldComError={} witness={}",
                    fixtureFrame, occupied, key.phase(), key.horizon(), value.count, value.maxAngle, value.maxCom, value.witness));
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[rotation-cause-model-coverage] frame={} occupied={} modelIndexClock=true h2={} expected={} h14={} expected={} tailExcludedH2=2 tailExcludedH14=14",
                    fixtureFrame, occupied, horizon2Count, total-2, horizon14Count, total-14);
        }
    }

    private static void withManualCausalCabin(GameTestHelper helper, BlockPos origin,
                                               Consumer<ManualCausalFixture> ready) {
        ServerLevel level = helper.getLevel();
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, origin, 48, 12, 48);
        SableFixtureScene.awaitEmpty(helper, scene, NATIVE_SCENE_READY_TICKS, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(origin.getX() + 24.5, origin.getY(), origin.getZ() + 24.5), plot -> {
                BlockPos storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleManualFixture.WIDTH,
                        ShuttleManualFixture.HEIGHT, ShuttleManualFixture.DEPTH);
                return ShuttleManualFixture.build(level, storage, plot.engineId(), plot::setStorageBlock);
            });
            SableFixtureScene.trackPlot(helper, authored.plot());
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), NATIVE_SCENE_READY_TICKS,
                    () -> ready.accept(new ManualCausalFixture(level, scene, authored.plot(), authored.result())));
        });
    }

    private static void attachDiagnosticCleanup(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            Runnable extraCleanup) {
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() {
                extraCleanup.run();
                if (human != null && !human.isRemoved()) human.discard();
            }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
    }

    private static void awaitDiagnosticWorldSupport(GameTestHelper helper, ManualCausalFixture fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            Runnable supported) {
        long registrationTick = fixture.level().getServer().getTickCount();
        int[] events = {0};
        Consumer<ServerTickEvent.Pre> listener = new Consumer<>() {
            private boolean closed;
            private void close() { if (!closed) { closed = true; NeoForge.EVENT_BUS.unregister(this); } }
            @Override public void accept(ServerTickEvent.Pre event) {
                if (closed || event.getServer() != fixture.level().getServer()
                        || event.getServer().getTickCount() <= registrationTick) return;
                try {
                    if (++events[0] > 40) {
                        close(); helper.testInfo.fail(new GameTestAssertException("rotating-wall actor failed real world-pad support")); return;
                    }
                    AtomicReference<SableOwnedBodyMotion.MoveResult> observed = new AtomicReference<>();
                    var step = GroundedHarnessWorldStep.observingNativeGeometry().step(human, 0, 0, false, false,
                            frameLocalXAxisYaw(fixture.plot()), observed::set);
                    var move = observed.get();
                    var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), human);
                    var clip = move == null || move.collisionScope() == null ? null : move.collisionScope().worldClipObservation();
                    boolean worldFloor = step.isPresent() && move != null && human.onGround() && point != null
                            && point.frameId() == null && move.beforePoint() != null && move.beforePoint().frameId() == null
                            && clip != null && clip.complete() && clip.downwardFloorClip()
                            && fixture.level().getBlockState(BlockPos.containing(human.getX(), fixture.scene().supportSurfaceY() - .01,
                                    human.getZ())).is(Blocks.STONE);
                    if (worldFloor) { close(); supported.run(); }
                } catch (RuntimeException | AssertionError failure) {
                    close(); helper.testInfo.fail(failure);
                }
            }
        };
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() { NeoForge.EVENT_BUS.unregister(listener); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
        NeoForge.EVENT_BUS.addListener(listener);
    }

    private record CausalMotionSample(int tick, Vec3 before, Vec3 after, Vec3 boundsBeforeMin,
                                      Vec3 boundsBeforeMax, Vec3 boundsAfterMin, Vec3 boundsAfterMax,
                                      MovementVector requested, MovementVector measured,
                                      Vec3 totalCarrier, Vec3 inherited, Object beforePoint, Object afterPoint,
                                      Object tracked, long serverTick, boolean groundedBefore, boolean groundedAfter,
                                      boolean vertical, boolean below, boolean side, boolean frameFloorRow,
                                      int supportRowCount,
                                      double surfaceNormalSpeed, double surfaceTangentSpeed,
                                      double wallTravelSinceStart,
                                      Vec3 sampleNormal, Vec3 sampleTangent, double requestedNormalSpeed,
                                      double normalCarrier, double tangentCarrier,
                                      SableOwnedBodyMotion.MoveResult move, boolean accepted) { }

    private static void captureRotatingWallSteps(GameTestHelper helper, ManualCausalFixture fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            Vector3d localWall, int count, int wishX, boolean jumpFirst, int minimumWallY, double initialGap,
            RigidBodyHandle impulseHandle, boolean kickAfterRising) {
        var samples = new ArrayList<CausalMotionSample>(count);
        var initialPose = fixture.plot().subLevel().logicalPose();
        Vector3d initialWallWorld = initialPose.transformPosition(new Vector3d(localWall));
        Vector3d initialWallNormal = initialPose.orientation().transform(new Vector3d(1, 0, 0)).normalize();
        long registered = fixture.level().getServer().getTickCount();
        int[] tick = {0};
        boolean[] airborne = {false};
        boolean[] attemptedJump = {false};
        boolean[] jumpAccepted = {false};
        boolean[] kickApplied = {false};
        boolean[] positiveMotionAfterKick = {false};
        boolean[] firstContactTrace = {false};
        boolean[] firstRejectedTrace = {false};
        Consumer<ServerTickEvent.Pre> listener = new Consumer<>() {
            private boolean closed;
            private void close() { if (!closed) { closed = true; NeoForge.EVENT_BUS.unregister(this); } }
            @Override public void accept(ServerTickEvent.Pre event) {
                if (closed || event.getServer() != fixture.level().getServer()
                        || event.getServer().getTickCount() <= registered) return;
                try {
                    int i = tick[0]++;
                    var before = human.position();
                    var beforeBounds = human.getBoundingBox();
                    boolean groundedBefore = human.onGround();
                    // A fixed storage-space wall point transformed through the exact sampled SDK pose endpoints
                    // supplies the actual current normal closing speed before the optional jump request.
                    var interval = jumpFirst ? NativeFrameInterval.PosePair.capture(
                            fixture.plot().subLevel().lastPose(), fixture.plot().subLevel().logicalPose()) : null;
                    double surfaceNormalSpeed = Double.NaN;
                    Vector3d surfaceVelocity = null;
                    if (interval != null) {
                        Vector3d p0 = new Vector3d(localWall);
                        Vector3d p1 = new Vector3d(localWall);
                        new org.joml.Matrix4d().set(interval.last().matrix()).transformPosition(p0);
                        new org.joml.Matrix4d().set(interval.logical().matrix()).transformPosition(p1);
                        surfaceVelocity = p1.sub(p0).mul(20d);
                        var orientation = fixture.plot().subLevel().logicalPose().orientation();
                        surfaceNormalSpeed = surfaceVelocity.dot(orientation.transform(new Vector3d(1, 0, 0)).normalize());
                    }
                    AtomicReference<SableOwnedBodyMotion.MoveResult> captured = new AtomicReference<>();
                    boolean issueJump = jumpFirst && !attemptedJump[0]
                            && (kickAfterRising || surfaceNormalSpeed > 0d);
                    if (issueJump) attemptedJump[0] = true;
                    if (issueJump) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "[AIRBORNE_WALL_SETUP] initialGap={} measuredPreJumpSurfaceNormalSpeed={} tick={} localWall={}",
                            initialGap, surfaceNormalSpeed, fixture.level().getServer().getTickCount(), localWall);
                    int currentWishX = jumpFirst ? 0 : wishX;
                    var result = GroundedHarnessWorldStep.observingNativeGeometry().step(human, currentWishX, 0,
                            issueJump, false, frameLocalXAxisYaw(fixture.plot()), captured::set);
                    if (issueJump) jumpAccepted[0] = result.isPresent();
                    var move = captured.get();
                    if (move == null) throw new GameTestAssertException("native owner-step observer absent at " + i);
                    if (interval == null) {
                        // Preserve the established receding WALK measurement phase; only the airborne
                        // jump gate needs the pre-move measured interval to confirm positive motion.
                        interval = NativeFrameInterval.PosePair.capture(fixture.plot().subLevel().lastPose(),
                                fixture.plot().subLevel().logicalPose());
                        Vector3d p0 = new Vector3d(localWall);
                        Vector3d p1 = new Vector3d(localWall);
                        new org.joml.Matrix4d().set(interval.last().matrix()).transformPosition(p0);
                        new org.joml.Matrix4d().set(interval.logical().matrix()).transformPosition(p1);
                        surfaceVelocity = p1.sub(p0).mul(20d);
                    }
                    var sampledOrientation = fixture.plot().subLevel().logicalPose().orientation();
                    Vector3d normal = sampledOrientation.transform(new Vector3d(1, 0, 0)).normalize();
                    Vector3d tangent = sampledOrientation.transform(new Vector3d(0, 0, 1)).normalize();
                    if (!Double.isFinite(surfaceNormalSpeed)) surfaceNormalSpeed = surfaceVelocity.dot(normal);
                    Vector3d sampledWallWorld = new Vector3d(localWall);
                    new org.joml.Matrix4d().set(interval.logical().matrix()).transformPosition(sampledWallWorld);
                    double wallTravelSinceStart = sampledWallWorld.sub(initialWallWorld).dot(initialWallNormal);
                    var after = human.position();
                    var info = move.collisionInfo();
                    Vec3 inherited = info == null || info.inheritedMotion == null ? Vec3.ZERO : info.inheritedMotion;
                    Vec3 own = new Vec3(move.measuredOwnDisplacement().x(), move.measuredOwnDisplacement().y(),
                            move.measuredOwnDisplacement().z());
                    Vec3 carrier = after.subtract(before.add(own));
                    double requestedNormalSpeed = (move.requestedOwnDisplacement().x() * normal.x
                            + move.requestedOwnDisplacement().y() * normal.y
                            + move.requestedOwnDisplacement().z() * normal.z) * 20d;
                    double normalCarrier = carrier.x * normal.x + carrier.y * normal.y + carrier.z * normal.z;
                    double tangentCarrier = carrier.x * tangent.x + carrier.y * tangent.y + carrier.z * tangent.z;
                    var scope = move.collisionScope();
                    if (!human.onGround()) airborne[0] = true;
                    if (kickApplied[0] && surfaceNormalSpeed > 0d) positiveMotionAfterKick[0] = true;
                    boolean frameFloorRow = scope != null && scope.nativeCollisionTrace().stream().anyMatch(row ->
                            fixture.plot().engineId().equals(row.frameId()) && row.verticalCollision()
                                    && row.selectedPos() != null && row.selectedPos().getY() == fixture.storage().getY());
                    int supportRowCount = scope == null ? 0 : (int) scope.nativeCollisionTrace().stream().filter(row ->
                            fixture.plot().engineId().equals(row.frameId()) && row.verticalCollision()
                                    && row.selectedPos() != null && row.selectedPos().getY() == fixture.storage().getY()).count();
                    Object tracked = info == null ? null : info.trackingSubLevel;
                    var sample = new CausalMotionSample(i, before, after,
                            new Vec3(beforeBounds.minX, beforeBounds.minY, beforeBounds.minZ),
                            new Vec3(beforeBounds.maxX, beforeBounds.maxY, beforeBounds.maxZ),
                            new Vec3(human.getBoundingBox().minX, human.getBoundingBox().minY, human.getBoundingBox().minZ),
                            new Vec3(human.getBoundingBox().maxX, human.getBoundingBox().maxY, human.getBoundingBox().maxZ),
                            move.requestedOwnDisplacement(), move.measuredOwnDisplacement(), carrier, inherited,
                            move.beforePoint(), move.afterPoint(), tracked, fixture.level().getServer().getTickCount(),
                            groundedBefore, human.onGround(),
                            info != null && info.verticalCollision, info != null && info.verticalCollisionBelow,
                            info != null && info.subLevelHorizontalCollision, frameFloorRow, supportRowCount,
                            surfaceNormalSpeed, surfaceVelocity.dot(tangent), wallTravelSinceStart,
                            new Vec3(normal.x, normal.y, normal.z), new Vec3(tangent.x, tangent.y, tangent.z),
                            requestedNormalSpeed, normalCarrier, tangentCarrier,
                            move, result.isPresent());
                    samples.add(sample); // Keep the observer even when the motor rejects this step.
                    boolean exactWall = hasExactEastWallRow(fixture, localWall, move, minimumWallY);
                    if (exactWall) {
                        logRotatingContactSummary(fixture, localWall, sample, move, result.isPresent());
                        if (kickAfterRising) com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                "[AIRBORNE_WALL_GAP] initialGap={}m wallNormalTravelSinceStart={}m remainingProjectedGap={}m measuredNormalSpeed={}mps",
                                initialGap, wallTravelSinceStart, initialGap - wallTravelSinceStart, surfaceNormalSpeed);
                        if (!firstContactTrace[0]) {
                            firstContactTrace[0] = true;
                            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                    "[rotating-contact-causal] FIRST_CONTACT rows={} proof={}",
                                    scope == null ? null : scope.nativeCollisionTrace(), move.nativeContactProofDiagnostic());
                        }
                    }
                    if (result.isEmpty() && !firstRejectedTrace[0]) {
                        firstRejectedTrace[0] = true;
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                "[rotating-contact-causal] FIRST_REJECTED tick={} accepted=false request={} measured={} carrier={} branch={} reason={} rowIndex={} positionStage={} rows={}",
                                i, move.requestedOwnDisplacement(), move.measuredOwnDisplacement(), carrier,
                                move.nativeContactProofDiagnostic().branch(), move.nativeContactProofDiagnostic().reason(),
                                move.nativeContactProofDiagnostic().rowIndex(), move.nativeContactProofDiagnostic().positionStage(),
                                scope == null ? null : scope.nativeCollisionTrace());
                    }
                    if (kickAfterRising && !kickApplied[0] && !human.onGround()
                            && human.position().y > fixture.plot().subLevel().logicalPose().transformPosition(
                                    new Vector3d(fixture.storage().getX() + 13.5, fixture.storage().getY() + 1,
                                            fixture.storage().getZ() + 7.5)).y
                            && human.getDeltaMovement().y > 0d) {
                        require(impulseHandle != null && impulseHandle.isValid(),
                                "rising airborne wall stimulus retains native frame body");
                        impulseHandle.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, 1, 0));
                        kickApplied[0] = true;
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                "[AIRBORNE_WALL_STIMULUS] appliedNativeYawImpulse=1.0radps initialGap={} feetY={} deckTopY={} risingVelocityY={}",
                                initialGap, human.position().y,
                                fixture.plot().subLevel().logicalPose().transformPosition(new Vector3d(
                                        fixture.storage().getX() + 13.5, fixture.storage().getY() + 1,
                                        fixture.storage().getZ() + 7.5)).y, human.getDeltaMovement().y);
                    }
                    if (tick[0] >= count) {
                        close();
                        BoundedGameTestWait.safeDelay(helper, 1, () -> {
                            var contact = samples.stream().filter(s -> hasExactEastWallRow(
                                            fixture, localWall, s.move(), minimumWallY))
                                    .findFirst().orElse(null);
                            require(contact != null && Math.abs(contact.surfaceTangentSpeed()) > .2,
                                    boundedCausalSummary("rotating-contact", samples, contact));
                            if (jumpFirst)
                                require(contact.surfaceNormalSpeed() > 0d,
                                        boundedCausalSummary("wall-did-not-push-toward-airborne-actor", samples, contact));
                            require(!contact.frameFloorRow(), boundedCausalSummary("frame-floor-support-row", samples, contact));
                            if (jumpFirst) {
                                require(jumpAccepted[0] && airborne[0] && !contact.groundedBefore()
                                                && contact.beforePoint() instanceof LoadedFrameRayQuery.ActorPoint p
                                                && p.frameId() == null,
                                        boundedCausalSummary("airborne-contact-basis", samples, contact));
                                if (kickAfterRising) {
                                    require(kickApplied[0] && positiveMotionAfterKick[0]
                                                    && contact.surfaceNormalSpeed() > 0d,
                                            boundedCausalSummary("airborne-wall-did-not-close-after-rise", samples, contact));
                                }
                            } else {
                                require(contact.surfaceNormalSpeed() < 0d,
                                        boundedCausalSummary("wall-not-receding", samples, contact));
                                require(contact.requestedNormalSpeed() < contact.surfaceNormalSpeed(),
                                        boundedCausalSummary("walk-not-faster-than-receding-wall", samples, contact));
                                require(contact.beforePoint() instanceof LoadedFrameRayQuery.ActorPoint p && p.frameId() == null
                                                && contact.groundedBefore(),
                                        boundedCausalSummary("rotating-contact-no-world-support", samples, contact));
                                require(Math.abs(contact.tangentCarrier()) <= 1.0e-6,
                                        boundedCausalSummary("unsupported-tangential-carrier", samples, contact));
                            }
                            require(samples.size() == count, "bounded owner sample count=" + samples.size());
                            com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.stop(
                                    fixture.level(), fixture.plot().subLevel());
                            helper.succeed();
                        });
                    }
                } catch (RuntimeException | AssertionError failure) {
                    close();
                    BoundedGameTestWait.safeDelay(helper, 1, () -> helper.testInfo.fail(failure));
                }
            }
        };
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() { NeoForge.EVENT_BUS.unregister(listener); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
        NeoForge.EVENT_BUS.addListener(listener);
    }

    private static String boundedCausalSummary(String id, java.util.List<CausalMotionSample> samples,
                                                CausalMotionSample sample) {
        int len = sample == null ? -1 : sample.tick();
        double carrier = sample == null ? Double.NaN : sample.totalCarrier().length();
        return id + " n=" + samples.size() + " sample=" + len + " carrierLength=" + carrier;
    }

    private static boolean hasExactEastWallRow(ManualCausalFixture fixture, Vector3d localWall,
                                                SableOwnedBodyMotion.MoveResult move, int minimumWallY) {
        var scope = move == null ? null : move.collisionScope();
        if (scope == null) return false;
        int wallZ = (int) Math.floor(localWall.z);
        return scope.nativeCollisionTrace().stream().anyMatch(row ->
                fixture.plot().engineId().equals(row.frameId()) && !row.verticalCollision()
                        && row.selectedPos() != null
                        && row.selectedPos().getX() == fixture.storage().getX() + ShuttleManualFixture.WIDTH - 1
                        && row.selectedPos().getY() >= minimumWallY
                        && row.selectedPos().getY() <= fixture.storage().getY() + 3
                        && Math.abs(row.selectedPos().getZ() - wallZ) <= 1
                        && row.selectedState() != null && row.selectedState().is(
                                com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()));
    }

    private static void logRotatingContactSummary(ManualCausalFixture fixture, Vector3d localWall, CausalMotionSample sample,
            SableOwnedBodyMotion.MoveResult move, boolean accepted) {
        var proof = move.nativeContactProofDiagnostic();
        var before = move.beforePoint() instanceof LoadedFrameRayQuery.ActorPoint p ? p.frameId() : null;
        var after = move.afterPoint() instanceof LoadedFrameRayQuery.ActorPoint p ? p.frameId() : null;
        int selectedY = move.collisionScope().nativeCollisionTrace().stream().filter(row ->
                        fixture.plot().engineId().equals(row.frameId()) && !row.verticalCollision()
                                && row.selectedPos() != null
                                && row.selectedPos().getX() == fixture.storage().getX() + ShuttleManualFixture.WIDTH - 1
                                && row.selectedPos().getY() >= fixture.storage().getY()
                                && row.selectedPos().getY() <= fixture.storage().getY() + 3
                                && Math.abs(row.selectedPos().getZ() - (int) Math.floor(localWall.z)) <= 1)
                .mapToInt(row -> row.selectedPos().getY()).findFirst().orElse(Integer.MIN_VALUE);
        String sideClass = selectedY == fixture.storage().getY() ? "FLOOR_BEAM_SIDE" : "WALL_SIDE";
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[ROTATING_CONTACT_SUMMARY] class={} selectedY={} serverTick={} accepted={} request={} measuredOwn={} carrier={} beforeFrame={} afterFrame={} supportRows={} surfaceNormalSpeed={} surfaceTangentSpeed={} normalCarrier={} tangentCarrier={} branch={} reason={} rowIndex={} positionStage={}",
                sideClass, selectedY, sample.serverTick(), accepted, move.requestedOwnDisplacement(), move.measuredOwnDisplacement(),
                sample.totalCarrier(), before, after, sample.supportRowCount(), sample.surfaceNormalSpeed(),
                sample.surfaceTangentSpeed(), sample.normalCarrier(), sample.tangentCarrier(), proof.branch(),
                proof.reason(), proof.rowIndex(), proof.positionStage());
    }

    private static void captureTranslatedLanding(GameTestHelper helper, ManualCausalFixture fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int stable, ArrayList<String> lines) {
        long registered = fixture.level().getServer().getTickCount();
        int[] ticks = {0};
        int[] stableTicks = {stable};
        boolean[] launched = {false};
        boolean[] jumpAttempted = {false};
        boolean[] launchEvidence = {false};
        boolean[] airborne = {false};
        int[] warmupTicks = {0};
        int[] flightTicks = {0};
        Consumer<ServerTickEvent.Pre> listener = new Consumer<>() {
            private boolean closed;
            private void close() { if (!closed) { closed = true; NeoForge.EVENT_BUS.unregister(this); } }
            @Override public void accept(ServerTickEvent.Pre event) {
                if (closed || event.getServer() != fixture.level().getServer()
                        || event.getServer().getTickCount() <= registered) return;
                try {
                    var frame = fixture.plot().subLevel();
                    var snapshot = com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService
                            .snapshot(fixture.level(), frame);
                    var interval = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                    var local = new Vector3d(fixture.storage().getX()+13.5, fixture.storage().getY()+1,
                            fixture.storage().getZ()+6.5);
                    Vector3d a = new Vector3d(local), b = new Vector3d(local);
                    new org.joml.Matrix4d().set(interval.last().matrix()).transformPosition(a);
                    new org.joml.Matrix4d().set(interval.logical().matrix()).transformPosition(b);
                    double zRate = (b.z-a.z)*20d;
                    boolean translating = snapshot != null && snapshot.active() && snapshot.remainingTicks() > 0
                            && Math.abs(zRate-2d) < .35 && Math.abs(b.x-a.x)*20d < .25;
                    AtomicReference<SableOwnedBodyMotion.MoveResult> observed = new AtomicReference<>();
                    boolean issueJump = !jumpAttempted[0] && translating && stableTicks[0] >= 4 && human.onGround();
                    if (issueJump) jumpAttempted[0] = true;
                    Vec3 before = human.position();
                    Vec3 beforeVelocity = human.getDeltaMovement();
                    var beforeBounds = human.getBoundingBox();
                    var airReferenceBeforeStep = human.airMotionReference().orElse(null);
                    var step = GroundedHarnessWorldStep.observingNativeGeometry().step(human, 0, 0, issueJump,
                            false, frameLocalXAxisYaw(fixture.plot()), observed::set);
                    var move = observed.get();
                    if (move == null) throw new GameTestAssertException("landing capture owner-step observer missing");
                    if (translating) stableTicks[0]++; else stableTicks[0] = 0;
                    if (issueJump) launched[0] = step.isPresent();
                    if (issueJump) launchEvidence[0] = human.airMotionReference().isPresent()
                            && move.beforePoint() != null && fixture.plot().engineId().equals(move.beforePoint().frameId());
                    if (!human.onGround()) airborne[0] = true;
                    if (!launched[0]) warmupTicks[0]++; else flightTicks[0]++;
                    var info = move.collisionInfo();
                    Vec3 inherited = info == null || info.inheritedMotion == null ? Vec3.ZERO : info.inheritedMotion;
                    Vec3 own = new Vec3(move.measuredOwnDisplacement().x(), move.measuredOwnDisplacement().y(),
                            move.measuredOwnDisplacement().z());
                    Vec3 total = human.position().subtract(before);
                    Vec3 carrier = total.subtract(own);
                    var beforePoint = move.beforePoint();
                    var afterPoint = move.afterPoint();
                    Vec3 pointVelocityBefore = beforePoint == null || beforePoint.frameId() == null ? Vec3.ZERO
                            : com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity.pointVelocityAt(
                                    fixture.level(), beforePoint);
                    Vec3 pointVelocityAfter = afterPoint == null || afterPoint.frameId() == null ? Vec3.ZERO
                            : com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity.pointVelocityAt(
                                    fixture.level(), afterPoint);
                    var sample = "t="+ticks[0]+" launch="+issueJump+" landed="+(airborne[0]&&human.onGround())
                            +" bodyBoundsBefore="+beforeBounds+" bodyBoundsAfter="+human.getBoundingBox()
                            +" bodyBefore="+before+" bodyAfter="+human.position()
                            +" stepAccepted="+step.isPresent()+" beforePoint="+beforePoint+" afterPoint="+afterPoint
                            +" onGround="+human.onGround()+" frozenAirReferenceBeforeStep="+airReferenceBeforeStep
                            +" frozenAirReferenceAfterStep="+human.airMotionReference().orElse(null)
                            +" requestedWorld="+move.requestedOwnDisplacement()+" measuredOwn="+move.measuredOwnDisplacement()
                            +" totalBodyDelta="+total+" residualAppliedCarrier="+carrier+" inheritedMotion="+inherited
                            +" frameVelocityDeltaAfterMove="+move.frameVelocityDeltaPerSecond()
                            +" nativeMotion="+(info==null?null:info.motion)+" tracking="+(info==null?null:info.trackingSubLevel)
                            +" sdkPreDeltaMovement="+(info==null?null:info.preDeltaMovement)
                            +" preStepBodyVelocity="+beforeVelocity
                            +" collisionFlags={vertical="+(info!=null&&info.verticalCollision)+",below="
                            +(info!=null&&info.verticalCollisionBelow)+",side="+(info!=null&&info.subLevelHorizontalCollision)+"}"
                            +" resultingVelocity="+human.getDeltaMovement()
                            +" pointVelocityBefore="+pointVelocityBefore+" pointVelocityAfter="+pointVelocityAfter
                            +" interval="+interval+" zRate="+zRate+" proof="+move.nativeContactProofDiagnostic()
                            +" rows="+(move.collisionScope()==null?null:move.collisionScope().nativeCollisionTrace());
                    lines.add(sample);
                    ticks[0]++;
                    if (issueJump || (airborne[0] && human.onGround()))
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[landing-cause-causal] {}", sample);
                    if (airborne[0] && human.onGround()) {
                        close();
                        BoundedGameTestWait.safeDelay(helper, 1, () -> {
                            require(launched[0], "natural jump did not launch; landing capture samples="+lines.size());
                            require(launchEvidence[0], "launch lacked frozen air reference or exact frame basis");
                            require(lines.size() <= 100 && lines.size() > 1,
                                    "bounded flight capture size="+lines.size());
                            require(move.collisionInfoFresh() && move.collisionScope() != null,
                                    "first landing must include fresh native move and scope");
                            require(move.beforePoint() != null && move.afterPoint() != null,
                                    "landing endpoints must have actor-basis samples");
                            var landingInfo = move.collisionInfo();
                            var landingRows = move.collisionScope().nativeCollisionTrace();
                            boolean sideRows = landingRows.stream().anyMatch(row ->
                                    fixture.plot().engineId().equals(row.frameId()) && !row.verticalCollision());
                            require(move.beforePoint().frameId() == null
                                            && fixture.plot().engineId().equals(move.afterPoint().frameId()),
                                    "first landing is an actual WORLD-to-this-frame basis transition");
                            require(landingInfo != null && landingInfo.verticalCollision
                                            && landingInfo.verticalCollisionBelow
                                            && !landingInfo.subLevelHorizontalCollision
                                            && !landingInfo.horizontalCollision && !sideRows,
                                    "flat first landing has same-frame floor support and no side contact rows");
                            require(fixture.plot().engineId().equals(move.afterPoint().frameId()),
                                    "first landing must attach to the translating manual cabin; after=" + move.afterPoint());
                            require(landingRows.stream().anyMatch(row ->
                                            fixture.plot().engineId().equals(row.frameId()) && row.verticalCollision()
                                                    && row.selectedPos() != null
                                                    && row.selectedY() > 0d
                                                    && row.selectedPos().getY() == fixture.storage().getY()),
                                    "first landing must retain exact native frame-floor rows");
                            double ownX = move.measuredOwnDisplacement().x();
                            double ownZ = move.measuredOwnDisplacement().z();
                            double carrierXTolerance = NativeCollisionSlop.MAX_RESIDUAL
                                    + 4d * (Math.ulp(before.x) + Math.ulp(human.position().x) + Math.ulp(ownX));
                            double carrierZTolerance = NativeCollisionSlop.MAX_RESIDUAL
                                    + 4d * (Math.ulp(before.z) + Math.ulp(human.position().z) + Math.ulp(ownZ));
                            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                    "[LANDING_CARRY_BASIS] beforeFrame={} afterFrame={} requestedWorld={} measuredOwn={} totalBodyDelta={} residualCarrier={} toleranceXZ={}/{} sideRows={} floorRows={}",
                                    move.beforePoint().frameId(), move.afterPoint().frameId(), move.requestedOwnDisplacement(),
                                    move.measuredOwnDisplacement(), total, carrier, carrierXTolerance, carrierZTolerance,
                                    sideRows, landingRows.stream().filter(row -> fixture.plot().engineId().equals(row.frameId())
                                            && row.verticalCollision() && row.selectedPos() != null
                                            && row.selectedPos().getY() == fixture.storage().getY()).count());
                            require(Math.abs(carrier.x) <= carrierXTolerance && Math.abs(carrier.z) <= carrierZTolerance,
                                    "WORLD request on first landing must not receive extra passenger transport: carrier="
                                            + carrier + " tolerance=" + carrierXTolerance + "/" + carrierZTolerance);
                            helper.succeed();
                        });
                    } else if (!launched[0] && warmupTicks[0] >= 80) {
                        close(); helper.testInfo.fail(new GameTestAssertException(
                                "bounded steady-translation warmup produced no jump; launched="+launched[0]));
                    } else if (launched[0] && flightTicks[0] >= 100) {
                        close(); helper.testInfo.fail(new GameTestAssertException(
                                "bounded steady-translation flight failed to land; n="+lines.size()+" launched="+launched[0]));
                    }
                } catch (RuntimeException | AssertionError failure) {
                    close(); BoundedGameTestWait.safeDelay(helper, 1, () -> helper.testInfo.fail(failure));
                }
            }
        };
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() { NeoForge.EVENT_BUS.unregister(listener); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
        NeoForge.EVENT_BUS.addListener(listener);
    }

    private record DoorwayJumpSample(int tick, net.minecraft.world.phys.AABB bounds, Vec3 position,
                                     Vec3 velocity, boolean onGround, Object tracking,
                                     SableOwnedBodyMotion.MoveResult move, double lintelPenetration,
                                     boolean accepted) { }

    private static void captureManualDoorwayJump(GameTestHelper helper, ServerLevel level,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage) {
        captureManualDoorwayJump(helper, level, human, plot, storage, DoorwayCaptureMode.STRICT_CLEARANCE);
    }

    private static void captureManualDoorwayJump(GameTestHelper helper, ServerLevel level,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage, boolean rotatingDiagnostic) {
        captureManualDoorwayJump(helper, level, human, plot, storage, rotatingDiagnostic
                ? DoorwayCaptureMode.ROTATING_IMPULSE_STRICT : DoorwayCaptureMode.STRICT_CLEARANCE);
    }

    private enum DoorwayCaptureMode { STRICT_CLEARANCE, ROTATING_IMPULSE_STRICT, OBLIQUE_MOVING_ACCEPTANCE }

    private static void captureManualDoorwayJump(GameTestHelper helper, ServerLevel level,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage, DoorwayCaptureMode mode) {
        boolean rotatingDiagnostic = mode == DoorwayCaptureMode.ROTATING_IMPULSE_STRICT;
        boolean obliqueDiagnostic = mode == DoorwayCaptureMode.OBLIQUE_MOVING_ACCEPTANCE;
        if (rotatingDiagnostic) {
            RigidBodyHandle handle = RigidBodyHandle.of(plot.subLevel());
            require(handle != null && handle.isValid(), "rotating doorway native frame body valid");
            // Ordinary native angular impulse, only after the character has genuine native deck support.
            handle.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, .75, 0));
        }
        final int maxTicks = 80;
        var samples = new ArrayList<DoorwayJumpSample>(maxTicks);
        AtomicBoolean removed = new AtomicBoolean();
        int[] tick = {0};
        long registrationTick = level.getServer().getTickCount();
        boolean[] airborne = {false};
        boolean[] jumped = {false};
        DoorwayJumpSample[] firstRejected = {null};
        Consumer<ServerTickEvent.Pre> listener = new Consumer<>() {
            private void remove() {
                if (removed.compareAndSet(false, true)) NeoForge.EVENT_BUS.unregister(this);
            }

            @Override public void accept(ServerTickEvent.Pre event) {
                if (removed.get() || event.getServer() != level.getServer()) return;
                if (event.getServer().getTickCount() <= registrationTick) return;
                try {
                    int current = tick[0]++;
                    boolean issueJump = current == 0;
                    SableOwnedBodyMotion.MoveResult[] observed = {null};
                    var result = GroundedHarnessWorldStep.observingNativeGeometry().step(human,
                            0, 0, issueJump, false, frameLocalXAxisYaw(plot), move -> observed[0] = move);
                    if (issueJump) jumped[0] = result.isPresent() && observed[0] != null;
                    if (!human.onGround()) airborne[0] = true;
                    var bounds = human.getBoundingBox();
                    double penetration = intersectsActualLintel(level, plot, storage, bounds);
                    Object tracking = observed[0] == null || observed[0].collisionInfo() == null ? null
                            : observed[0].collisionInfo().trackingSubLevel;
                    samples.add(new DoorwayJumpSample(current, bounds, human.position(), human.getDeltaMovement(),
                            human.onGround(), tracking, observed[0], penetration, result.isPresent()));
                    if (obliqueDiagnostic && observed[0] != null) {
                        var move = observed[0];
                        var info = move.collisionInfo();
                        var proof = move.nativeContactProofDiagnostic();
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                "[OBLIQUE_DOORWAY_STEP] tick={} accepted={} bounds={} position={} velocity={} request={} measuredOwn={} inherited={} nativeMotion={} basis={}->{} branch={} reason={} rowIndex={} positionStage={} rawPenetration={}",
                                current, result.isPresent(), bounds, human.position(), human.getDeltaMovement(),
                                move.requestedOwnDisplacement(), move.measuredOwnDisplacement(),
                                info == null ? null : info.inheritedMotion, info == null ? null : info.motion,
                                move.beforePoint() == null ? null : move.beforePoint().frameId(),
                                move.afterPoint() == null ? null : move.afterPoint().frameId(), proof.branch(),
                                proof.reason(), proof.rowIndex(), proof.positionStage(), penetration);
                    }
                    if (observed[0] != null && result.isEmpty() && firstRejected[0] == null) {
                        firstRejected[0] = samples.getLast();
                        var rejectedMove = observed[0];
                        var scope = rejectedMove.collisionScope();
                        var rows = scope == null ? null : scope.nativeCollisionTrace();
                        var proof = rejectedMove.nativeContactProofDiagnostic();
                        var doorwayRows = rows == null ? null : rows.stream().filter(row -> {
                            if (row.selectedPos() == null || !plot.engineId().equals(row.frameId())) return false;
                            boolean ceiling = row.verticalCollision() && row.selectedY() < 0d
                                    && row.selectedPos().getX() >= storage.getX() + 12
                                    && row.selectedPos().getX() <= storage.getX() + 14
                                    && row.selectedPos().getZ() == storage.getZ()
                                    && row.selectedPos().getY() == storage.getY() + 4;
                            boolean steelSide = !row.verticalCollision() && row.selectedState() != null
                                    && row.selectedState().is(
                                            com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get());
                            return ceiling || steelSide;
                        }).map(row -> (row.verticalCollision() ? "CEILING:" : "SIDE:") + row).toList();
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                "[DOORWAY_FIRST_REJECT] tick={} accepted=false request={} measuredOwn={} beforeFrame={} afterFrame={} branch={} reason={} rowIndex={} positionStage={} ceilingSideRows={}",
                                current, rejectedMove.requestedOwnDisplacement(), rejectedMove.measuredOwnDisplacement(),
                                rejectedMove.beforePoint() == null ? null : rejectedMove.beforePoint().frameId(),
                                rejectedMove.afterPoint() == null ? null : rejectedMove.afterPoint().frameId(),
                                proof.branch(), proof.reason(), proof.rowIndex(), proof.positionStage(), doorwayRows);
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                "[DOORWAY_FIRST_REJECT_FULL_TRACE] rows={} proof={}", rows, proof);
                    }
                    // Preserve the whole ascent and descent before judging any violation.
                    if ((airborne[0] && current > 2 && human.onGround()) || current + 1 == maxTicks) {
                        remove();
                        BoundedGameTestWait.safeDelay(helper, 1, () -> {
                            String trace = doorwayJumpTrace(samples, human);
                            if ((rotatingDiagnostic || obliqueDiagnostic) && firstRejected[0] != null) {
                                var rejected = firstRejected[0].move();
                                var proof = rejected.nativeContactProofDiagnostic();
                                throw new GameTestAssertException((obliqueDiagnostic ? "oblique-doorway-motor-reject" : "rotating-doorway-motor-reject") + " tick="
                                        + firstRejected[0].tick() + " branch=" + proof.branch() + " reason="
                                        + proof.reason() + " row=" + proof.rowIndex());
                            }
                            require(jumped[0], "native jump request was not accepted; " + trace);
                            if ((rotatingDiagnostic || obliqueDiagnostic) && (!airborne[0] || !human.onGround()))
                                throw new GameTestAssertException((obliqueDiagnostic
                                        ? "oblique-doorway-gravity-hang" : "rotating-doorway-gravity-hang")
                                        + " n=" + samples.size()
                                        + " airborne=" + airborne[0] + " grounded=" + human.onGround());
                            require(airborne[0] && human.onGround(), "jump did not complete airborne descent/landing; " + trace);
                            if (!obliqueDiagnostic) {
                                DoorwayJumpSample firstPenetration = samples.stream()
                                        .filter(s -> s.lintelPenetration() > NativeCollisionSlop.MAX_RESIDUAL)
                                        .findFirst().orElse(null);
                                if (firstPenetration != null) {
                                    com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                            "[manual-doorway-jump] first actual lintel penetration sample={} nativeRows={}",
                                            firstPenetration, firstPenetration.move() == null
                                                    || firstPenetration.move().collisionScope() == null ? null
                                                    : firstPenetration.move().collisionScope().nativeCollisionTrace());
                                    throw new GameTestAssertException(trace);
                                }
                            }
                            boolean actualCeiling = samples.stream().filter(s -> s.move() != null)
                                    .flatMap(s -> s.move().collisionScope().nativeCollisionTrace().stream())
                                    .anyMatch(row -> row.verticalCollision() && row.selectedY() < 0d
                                            && row.selectedPos() != null
                                            && row.selectedPos().getY() == storage.getY() + 4
                                            && row.selectedPos().getX() >= storage.getX() + 12
                                            && row.selectedPos().getX() <= storage.getX() + 14
                                            && row.selectedPos().getZ() == storage.getZ());
                            require(actualCeiling, "jump trajectory never selected the actual steel doorway lintel; " + trace);
                            helper.succeed();
                        });
                    }
                } catch (RuntimeException | AssertionError failure) {
                    remove();
                    BoundedGameTestWait.safeDelay(helper, 1, () -> helper.testInfo.fail(failure));
                }
            }
        };
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() { if (removed.compareAndSet(false, true)) NeoForge.EVENT_BUS.unregister(listener); }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
        NeoForge.EVENT_BUS.addListener(listener);
    }

    private static double intersectsActualLintel(ServerLevel level, SableFixturePlot plot, BlockPos storage,
                                                  net.minecraft.world.phys.AABB body) {
        var pose = plot.subLevel().logicalPose().bakeIntoMatrix(new org.joml.Matrix4d());
        var scratch = new dev.ryanhcode.sable.api.math.LevelReusedVectors();
        double deepest = 0d;
        for (int x = 12; x <= 14; x++) {
            BlockPos lintel = storage.offset(x, 4, 0);
            var state = level.getBlockState(lintel);
            var shapes = state.getCollisionShape(level, lintel).toAabbs();
            require(state.is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()) && !shapes.isEmpty(),
                    "manual doorway lintel must be actual nonempty steel at " + lintel + ", state=" + state);
            for (var shape : shapes) {
                var localShape = new net.minecraft.world.phys.AABB(lintel.getX() + shape.minX,
                        lintel.getY() + shape.minY, lintel.getZ() + shape.minZ,
                        lintel.getX() + shape.maxX, lintel.getY() + shape.maxY, lintel.getZ() + shape.maxZ);
                var contact = HullTerrainGeometry.againstTerrain(localShape, pose, body, scratch);
                deepest = Math.max(deepest, contact.penetrationDepth());
            }
        }
        return deepest;
    }

    private static String doorwayJumpTrace(java.util.List<DoorwayJumpSample> samples,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human) {
        String tag = "manual-doorway-jump";
        DoorwayJumpSample first = samples.stream()
                .filter(s -> s.lintelPenetration() > NativeCollisionSlop.MAX_RESIDUAL).findFirst().orElse(null);
        var compact = samples.stream().map(s -> "t" + s.tick() + ":p=" + s.position() + ":ground=" + s.onGround()
                + ":depth=" + s.lintelPenetration() + ":accepted=" + s.accepted()).toList();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[{}] bounded trajectory={}", tag, compact);
        return tag + " n=" + samples.size() + " firstBad=" + (first == null ? "none" : first.tick())
                + " depth=" + (first == null ? 0d : first.lintelPenetration());
    }

    private static void awaitManualDoorwayFloorContact(GameTestHelper helper, ServerLevel level,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage, int ticksLeft, Runnable supported) {
        SableOwnedBodyMotion.MoveResult[] observed = {null};
        var movement = new GroundedHarnessWorldStep().step(human, 0, 0, false, false,
                frameLocalXAxisYaw(plot), move -> observed[0] = move);
        var move = observed[0];
        boolean exactFloor = movement.isPresent() && move != null && move.collisionInfoFresh()
                && move.collisionInfo() != null && move.collisionInfo().trackingSubLevel == plot.subLevel()
                && move.collisionInfo().verticalCollisionBelow && human.onGround()
                && move.collisionScope().nativeCollisionTrace().stream().anyMatch(row ->
                        plot.engineId().equals(row.frameId()) && row.verticalCollision() && row.selectedY() > 0
                                && row.selectedPos() != null && row.selectedPos().getY() == storage.getY()
                                && row.selectedState() != null
                                && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()));
        if (exactFloor) { supported.run(); return; }
        if (ticksLeft <= 0) {
            String tag = "manual-doorway-floor-" + human.getUUID();
            var scope = move == null ? null : move.collisionScope();
            throw logFloorSupportTimeout(tag, "expectedFrame=" + plot.engineId() + " expectedStorageFloorY="
                    + storage.getY() + " position=" + human.position() + " onGround=" + human.onGround()
                    + " native=" + OwnedNativeMoveDiagnostic.describe(human, move)
                    + " rows=" + (scope == null ? null : scope.nativeCollisionTrace()));
        }
        helper.runAfterDelay(1, () -> awaitManualDoorwayFloorContact(helper, level, human, plot, storage,
                ticksLeft - 1, supported));
    }

    private static void awaitMicroCarryHover(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            Vector3d storagePoint, int remainingTicks, int stableSamples, int lastCallbacks, Runnable ready) {
        var snapshot = com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService
                .snapshot(level, plot.subLevel());
        var gravity = snapshot == null ? null : snapshot.configuredGravity();
        boolean gravityFinite = gravity != null && Double.isFinite(gravity.x())
                && Double.isFinite(gravity.y()) && Double.isFinite(gravity.z());
        boolean fresh = snapshot != null && snapshot.active() && snapshot.sdkSubsteps() == 6
                && gravityFinite && snapshot.appliedSubsteps() - lastCallbacks >= 6;
        Vector3d before = plot.subLevel().lastPose().transformPosition(new Vector3d(storagePoint), new Vector3d());
        Vector3d after = plot.subLevel().logicalPose().transformPosition(new Vector3d(storagePoint), new Vector3d());
        double footDelta = before.distance(after);
        var transport = snapshot == null ? null : snapshot.transportVelocity();
        boolean stable = fresh && Double.isFinite(footDelta) && footDelta <= MICRO_HOVER_REST_MOTION_BUDGET
                && transport != null && Double.isFinite(transport.x()) && Double.isFinite(transport.y())
                && Double.isFinite(transport.z()) && Math.sqrt(transport.x()*transport.x()
                + transport.y()*transport.y()+transport.z()*transport.z())*.05 <= MICRO_HOVER_REST_MOTION_BUDGET;
        int nextStable = stable ? stableSamples + 1 : 0;
        if (nextStable >= MICRO_STABLE_SAMPLES) { ready.run(); return; }
        if (remainingTicks <= 0) throw logFloorSupportTimeout("micro-carry-hover-" + plot.engineId(),
                "snapshot=" + snapshot + " storageFootDelta=" + footDelta + " callbacks="
                        + (snapshot == null ? "unavailable" : snapshot.appliedSubsteps()) + " orientation="
                        + plot.subLevel().logicalPose().orientation());
        int callbacks = snapshot == null ? lastCallbacks : snapshot.appliedSubsteps();
        helper.runAfterDelay(1, () -> awaitMicroCarryHover(helper, level, plot, storagePoint,
                remainingTicks-1, nextStable, callbacks, ready));
    }

    private static void probeMicroCarry(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            BlockPos storage, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human) {
        ServerSubLevel frame = plot.subLevel();
        EntityMovementExtension movement = (EntityMovementExtension) (Object) human;
        LoadedFrameRayQuery.ActorPoint floorPoint = LoadedFrameRayQuery.currentActorPoint(level, human);
        var snapshot = com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService
                .snapshot(level, frame);
        var handle = RigidBodyHandle.of(frame);
        double mass = frame.getMassTracker() == null ? Double.NaN : frame.getMassTracker().getMass();
        if (floorPoint == null || !plot.engineId().equals(floorPoint.frameId()) || !human.onGround()
                || movement.sable$getTrackingSubLevel() != frame || handle == null || !handle.isValid()
                || !Double.isFinite(mass) || mass <= 0) {
            throw new GameTestAssertException("micro-carry probe precondition failed point=" + floorPoint
                    + " tracking=" + movement.sable$getTrackingSubLevel() + " grounded=" + human.onGround()
                    + " mass=" + mass + " frame=" + plot.engineId() + " snapshot=" + snapshot);
        }
        NativeFrameInterval.PosePair beforePoses = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
        double desiredPointSpeed = .001;
        var inertia = frame.getMassTracker() == null ? null : frame.getMassTracker().getInertiaTensor();
        Vector3d lever = new Vector3d(floorPoint.worldPoint().x, floorPoint.worldPoint().y,
                floorPoint.worldPoint().z).sub(frame.logicalPose().position());
        Vector3d angularImpulse = microYawImpulse(lever, frame.logicalPose().orientation(), inertia, desiredPointSpeed);
        if (snapshot == null || !snapshot.active() || snapshot.sdkSubsteps() != 6
                || inertia == null || !finiteVector(angularImpulse) || angularImpulse.lengthSquared() == 0)
            throw new GameTestAssertException("micro-carry angular impulse invalid mass=" + mass + " lever=" + lever);
        MicroNativeDiagnostic diagnostic = new MicroNativeDiagnostic(level, frame, handle);
        diagnostic.start(helper);
        diagnostic.log("beforeImpulse", Double.NaN, desiredPointSpeed);
        handle.applyLinearAndAngularImpulse(new Vector3d(), angularImpulse, true);
        diagnostic.log("afterImpulse", Double.NaN, desiredPointSpeed);
        BoundedGameTestWait.safeDelay(helper, MICRO_SAMPLE_TICKS, () -> {
            diagnostic.close();
            diagnostic.log("nextTick", Double.NaN, desiredPointSpeed);
            NativeFrameInterval.PosePair currentPoses = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
            if (beforePoses.equals(currentPoses))
                throw new GameTestAssertException("micro-carry native pose did not change; poses=" + currentPoses);
            Vector3d storagePoint = new Vector3d(floorPoint.storagePoint().x, floorPoint.storagePoint().y,
                    floorPoint.storagePoint().z);
            Vector3d previousWorldPoint = frame.lastPose().transformPosition(storagePoint, new Vector3d());
            Vector3d currentWorldPoint = frame.logicalPose().transformPosition(storagePoint, new Vector3d());
            Vector3d poseDelta = currentWorldPoint.sub(previousWorldPoint, new Vector3d());
            double resolution = pointResolution(floorPoint);
            double poseDeltaLength = poseDelta.length();
            if (!Double.isFinite(poseDelta.x) || !Double.isFinite(poseDelta.y) || !Double.isFinite(poseDelta.z)
                    || !Double.isFinite(poseDeltaLength) || poseDeltaLength <= resolution || poseDeltaLength >= 1.0e-4)
                throw new GameTestAssertException("micro-carry physical pose delta invalid delta=" + poseDelta
                        + " length=" + poseDeltaLength + " pointResolution=" + resolution + " poses=" + currentPoses);
            LoadedFrameRayQuery.ActorPoint knownPoint = LoadedFrameRayQuery.currentActorPoint(level, human);
            if (knownPoint == null || !plot.engineId().equals(knownPoint.frameId()))
                throw new GameTestAssertException("micro-carry lost exact-frame actor point=" + knownPoint);
            Vec3 beforeStock = human.position();
            var stock = SableOwnedBodyMotion.move(human, new MovementVector(0, -.001, 0),
                    SableOwnedBodyMotion.MoveOptions.serverDefaults());
            Vec3 stockDelta = human.position().subtract(beforeStock);
            int carrierAxis = dominantHorizontalAxis(poseDelta);
            double applicationTolerance = 4 * Math.ulp(axis(beforeStock, carrierAxis));
            require(stock.collisionInfoFresh() && stock.collisionInfo() != null
                            && stock.collisionInfo().trackingSubLevel == frame
                            && stock.collisionInfo().inheritedMotion == null,
                    "stock micro-carry control is fresh exact-frame and publishes no SDK inherited motion: "
                            + OwnedNativeMoveDiagnostic.describe(human, stock));
            require(Math.abs(axis(stockDelta, carrierAxis)) <= applicationTolerance,
                    "stock SDK control receives no measurable micro-carry displacement");
            require(NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose()).equals(currentPoses),
                    "stock control and owned step share the same native pose interval");
            Vec3 beforeOwned = human.position();
            SableOwnedBodyMotion.MoveResult[] captured = {null};
            require(new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f,
                            result -> captured[0] = result).isPresent() && captured[0] != null,
                    "production grounded owner step returns a fresh micro-carry move");
            var owned = captured[0];
            var raw = owned.collisionInfo() == null ? null : owned.collisionInfo().inheritedMotion;
            require(owned.collisionInfoFresh() && owned.collisionInfo().trackingSubLevel == frame && raw != null
                            && Double.isFinite(raw.x) && Double.isFinite(raw.y) && Double.isFinite(raw.z)
                            && raw.length() > resolution && raw.length() < 1.0e-4,
                    "owned native carry is fresh, finite, exact-frame, and above point precision; raw=" + raw);
            require(owned.collisionScope() != null && owned.collisionScope().microCarryCutoffRetained(),
                    "owned collision scope retains micro-carry cutoff evidence");
            double nativeDominant = axis(raw, carrierAxis);
            double ownDominant = axis(owned.measuredOwnDisplacement(), carrierAxis);
            require(Double.isFinite(nativeDominant) && Math.abs(nativeDominant) > resolution
                            && Math.abs(nativeDominant) < 1.0e-4
                            && nativeDominant >= .25 * desiredPointSpeed * GroundedHarnessPhysics.TICK_SECONDS,
                    "native carry dominant-axis signal is finite and above point precision");
            require(Double.isFinite(ownDominant) && Math.abs(ownDominant) <= applicationTolerance,
                    "owned own-motion remains below physical application tolerance");
            Vec3 actualDelta = human.position().subtract(beforeOwned);
            double expectedDelta = ownDominant + nativeDominant;
            require(Math.abs(axis(actualDelta, carrierAxis) - expectedDelta) <= applicationTolerance,
                    "owned physical commit equals its own displacement plus real native bridge carry");
            require(human.onGround(), "micro-carry owned step preserves real grounded support");
            helper.succeed();
        });
    }

    static Vector3d microYawImpulse(Vector3d lever, Quaterniond orientation,
                                    org.joml.Matrix3dc inertia, double pointSpeed) {
        if (lever == null || orientation == null || inertia == null || !finiteVector(lever)
                || !Double.isFinite(pointSpeed) || pointSpeed <= 0) return null;
        double radius = Math.hypot(lever.x, lever.z);
        if (!Double.isFinite(radius) || radius <= .05) return null;
        Vector3d tangent = new Vector3d(lever.z, 0, -lever.x).div(radius);
        int dominant = Math.abs(tangent.x) >= Math.abs(tangent.z) ? 0 : 2;
        double sign = (dominant == 0 ? tangent.x : tangent.z) < 0 ? -1 : 1;
        Vector3d worldOmega = new Vector3d(0, sign * pointSpeed / radius, 0);
        Vector3d localOmega = orientation.transformInverse(worldOmega, new Vector3d());
        Vector3d impulse = inertia.transform(localOmega, new Vector3d());
        return finiteVector(impulse) && impulse.lengthSquared() > 0 ? impulse : null;
    }

    private static boolean finiteVector(Vector3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    /** Bounded fixture-only capture of native integration versus published frame pose. */
    private static final class MicroNativeDiagnostic {
        private final ServerLevel level;
        private final ServerSubLevel frame;
        private final RigidBodyHandle handle;
        private final java.util.List<String> rows = new ArrayList<>();
        private int substepRows;
        private Consumer<dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent> listener;
        private Vector3d previousCom;
        private boolean closed;
        MicroNativeDiagnostic(ServerLevel level, ServerSubLevel frame, RigidBodyHandle handle) {
            this.level = level; this.frame = frame; this.handle = handle;
        }
        void start(GameTestHelper helper) {
            listener = event -> {
                if (closed || event.getPhysicsSystem().getLevel() != level || frame.isRemoved()
                        || frame.getLevel() != level || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame
                        || !handle.isValid() || substepRows >= 8 || rows.size() >= 12) return;
                try {
                    capture("substep", event.getTimeStep(), Double.NaN);
                    substepRows++;
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("MICRO_NATIVE_DIAGNOSTIC {}", rows.get(rows.size()-1));
                }
                catch (RuntimeException failure) {
                    if (rows.size() < 12) {
                        substepRows++;
                        rows.add("phase=substep captureError=" + failure.getClass().getSimpleName());
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("MICRO_NATIVE_DIAGNOSTIC {}", rows.get(rows.size()-1));
                    }
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, listener);
            helper.testInfo.addListener(new GameTestListener() {
                private void cleanup() { close(); }
                @Override public void testStructureLoaded(GameTestInfo i) { }
                @Override public void testPassed(GameTestInfo i, GameTestRunner r) { cleanup(); }
                @Override public void testFailed(GameTestInfo i, GameTestRunner r) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo a, GameTestInfo b, GameTestRunner r) { cleanup(); }
            });
        }
        void log(String phase, double dt, double desired) {
            int before = rows.size();
            try { capture(phase, dt, desired); }
            catch (RuntimeException failure) { if (rows.size() < 12) rows.add("phase="+phase+" captureError=" + failure.getClass().getSimpleName()); }
            if (!phase.equals("substep"))
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("MICRO_NATIVE_DIAGNOSTIC {}",
                        rows.size() > before ? rows.get(rows.size()-1) : "phase="+phase+" captureSkipped=totalRowBudgetExhausted");
        }
        private void capture(String phase, double dt, double desired) {
            if (rows.size() >= 12) return;
            Vector3d v = handle.getLinearVelocity(new Vector3d()), w = handle.getAngularVelocity(new Vector3d());
            var pose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline()
                    .readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
            Vector3d comPosition = new Vector3d(pose.position());
            Vector3d delta = previousCom == null ? new Vector3d() : comPosition.sub(previousCom, new Vector3d());
            previousCom = comPosition;
            var s = com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService.snapshot(level, frame);
            rows.add("phase="+phase+" tick="+level.getGameTime()+" dt="+dt+" callbacks="+(s==null?-1:s.appliedSubsteps())
                    +" mass="+(s==null?Double.NaN:s.mergedMass())+" desiredPointSpeed="+desired+" sdkV="+vector(v)+" sdkW="+vector(w)
                    +" nativeCOM="+vector(comPosition)+" pivot="+vector(pose.rotationPoint())+" q="+quaternion(pose.orientation())+" dCOM="+vector(delta)
                    +" last="+vector(frame.lastPose().position())+" logical="+vector(frame.logicalPose().position())+" gravity="
                    +(s==null?null:s.configuredGravity())+" damping="+(s==null?Double.NaN:s.configuredUniversalDrag())
                    +" substeps="+(s==null?-1:s.sdkSubsteps()));
        }
        private static String vector(org.joml.Vector3dc v) { return "("+Double.toString(v.x())+","+Double.toString(v.y())+","+Double.toString(v.z())+")"; }
        private static String quaternion(org.joml.Quaterniondc q) { return "("+Double.toString(q.x())+","+Double.toString(q.y())+","+Double.toString(q.z())+","+Double.toString(q.w())+")"; }
        void close() { if (closed) return; closed=true; if(listener!=null){NeoForge.EVENT_BUS.unregister(listener);listener=null;} }
    }

    @GameTestTags({"shuttle-strong-impulse", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 240)
    public static void cabinHumanSurvivesStrongNativeNegativeXImpulse(GameTestHelper helper) {
        requireStrongImpulseConfig(helper, "[strong-impulse-default-config]", false);
        strongCabinHumanImpulse(helper, new BlockPos(320, 240, 320), new Vector3d(-20, 0, 0));
    }

    @GameTestTags({"shuttle-strong-impulse", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 240)
    public static void cabinHumanSurvivesStrongNativePositiveYImpulse(GameTestHelper helper) {
        strongCabinHumanImpulse(helper, new BlockPos(360, 260, 320), new Vector3d(0, 20, 0));
    }

    @GameTestTags({"shuttle-strong-impulse-geometry-diagnostic"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 240, batch = "shuttle-strong-impulse-geometry-diagnostic")
    public static void diagnoseStrongNativeNegativeXImpulseGeometry(GameTestHelper helper) {
        requireStrongImpulseConfig(helper, "[strong-impulse-geometry-config]", true);
        strongCabinHumanImpulse(helper, new BlockPos(320, 240, 320), new Vector3d(-20, 0, 0), true);
    }

    private static void requireStrongImpulseConfig(GameTestHelper helper, String marker, boolean observeGeometry) {
        int effectiveSubsteps = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(helper.getLevel())
                .getConfig().substepsPerTick;
        String expectedSubsteps = System.getProperty("moonstation14.hullImpactExpectedSubsteps");
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "{} observeGeometry={} effectiveSubsteps={} expectedSubsteps={}",
                marker, observeGeometry, effectiveSubsteps, expectedSubsteps == null ? "none" : expectedSubsteps);
        HullImpactSubstepsExpectation.requireExpected(effectiveSubsteps, expectedSubsteps);
    }

    private static void strongCabinHumanImpulse(GameTestHelper helper, BlockPos origin, Vector3d velocity) {
        strongCabinHumanImpulse(helper, origin, velocity, false);
    }

    private static void strongCabinHumanImpulse(GameTestHelper helper, BlockPos origin, Vector3d velocity,
                                                boolean observeGeometry) {
        withFurnishedCabin(helper, origin, (level, plot, storage) -> {
                var human = BoundHumanFixture.create(level,
                        cabinPoint(plot, storage, 1.5, 1, 9.5), "strong-native-impulse-human");
                awaitCabinFloorContact(helper, level, human, plot, storage, 32, () -> {
                    RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(velocity, new Vector3d());
                    new StrongImpulseCabinProbe(helper, level, human, plot, storage, observeGeometry).tick(60);
                });
        });
    }

    /** Canonical furnished cabin allocation and native scene/support lifecycle shared by body and cargo probes. */
    private static void withFurnishedCabin(GameTestHelper helper, BlockPos origin, CabinWork work) {
        withFurnishedCabin(helper, origin, 32, work);
    }

    private static void withFurnishedCabin(GameTestHelper helper, BlockPos origin, int sceneWidthX, CabinWork work) {
        ServerLevel level = helper.getLevel();
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, origin, sceneWidthX, 12, 32);
        SableFixtureScene.awaitEmpty(helper, scene, 40, () -> {
            double cx = origin.getX() + 12.5, cz = origin.getZ() + 12.5;
            var motionLease = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.acquire(helper,
                    new net.minecraft.world.phys.AABB(cx - 76, origin.getY() - 60, cz - 76,
                            cx + 76, origin.getY() + 72, cz + 76));
            var southwest = BlockPos.containing(cx - 76, origin.getY() - 60, cz - 76);
            var northeast = BlockPos.containing(cx + 76, origin.getY() + 72, cz + 76);
            com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.awaitEntityTicking(helper,
                    motionLease, 80, () -> { }, southwest, northeast);
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(origin.getX() + 12.5, origin.getY(), origin.getZ() + 12.5), plot -> {
                        BlockPos storage = plot.storageOrigin();
                        plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                                ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                        ShuttleCabinFixture.build(level, storage, plot.engineId());
                        return storage;
                    });
            SableFixtureScene.trackPlot(helper, authored.plot());
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 80,
                    () -> work.run(level, authored.plot(), authored.result()));
        });
    }

    static int strongCargoSceneWidth() {
        double travel = STRONG_CARGO_IMPULSE_SPEED * STRONG_CARGO_IMPULSE_TICKS / 20.0;
        double required = 12.5 + travel + ShuttleCabinFixture.WIDTH + 12.0;
        return (int) (Math.ceil(required / 16.0) * 16.0);
    }

    @FunctionalInterface
    private interface CabinWork { void run(ServerLevel level, SableFixturePlot plot, BlockPos storage); }

    private static final class StrongImpulseCabinProbe {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human;
        private final SableFixturePlot plot;
        private final BlockPos storage;
        private final boolean observeGeometry;
        private final StringBuilder trace = new StringBuilder();
        private int tick;
         private int angledFloorCorrectionTicks;
         private int idleProbeCertifiedTicks;
         private boolean loggedFirstIdleProbeCertified;
        StrongImpulseCabinProbe(GameTestHelper helper, ServerLevel level,
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
                SableFixturePlot plot, BlockPos storage, boolean observeGeometry) {
            this.helper = helper; this.level = level; this.human = human; this.plot = plot; this.storage = storage;
            this.observeGeometry = observeGeometry;
        }
        void tick(int remaining) {
            var movement = (EntityMovementExtension) (Object) human;
            Object prior = movement.sable$getCollisionInfo();
            Vec3 before = human.position();
            SableOwnedBodyMotion.MoveResult[] observedMove = {null};
            var step = observeGeometry ? GroundedHarnessWorldStep.observingNativeGeometry()
                    : new GroundedHarnessWorldStep();
            var result = step.step(human, 0, 0, false, false,
                    frameLocalXAxisYaw(plot), move -> observedMove[0] = move);
            var info = movement.sable$getCollisionInfo();
            Vec3 after = human.position();
            Vector3d local = plot.subLevel().logicalPose().transformPositionInverse(
                    new Vector3d(after.x, after.y, after.z));
            var point = LoadedFrameRayQuery.currentActorPoint(level, human);
             var move = observedMove[0];
             boolean idleProbeCertified = move != null && move.idleSupportProbeCertified();
             if (idleProbeCertified) {
                 idleProbeCertifiedTicks++;
                 if (!loggedFirstIdleProbeCertified) {
                     loggedFirstIdleProbeCertified = true;
                     com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                             "[strong-impulse-first-idle-probe-certified] tick={} nativeMove={}", tick,
                             OwnedNativeMoveDiagnostic.describe(human, move));
                 }
             }
            var correction = move == null ? null : move.positionCorrection();
            var boundedOwn = move == null ? null : move.boundedOwnDisplacement();
            var geometryCorrection = correction == null ? null : correction.geometryCorrection();
            boolean angledFloorCorrection = boundedOwn != null && boundedOwn.equals(
                    com.juicyslew.moonstation14.ms14.movement.MovementVector.ZERO)
                    && correction != null && geometryCorrection != null
                    && Math.hypot(geometryCorrection.x(), geometryCorrection.z()) > 1.0e-6
                    && info != null && info.verticalCollisionBelow;
            if (angledFloorCorrection) {
                angledFloorCorrectionTicks++;
            }
            String row = "tick=" + tick + " world=" + after + " local=" + local
                    + " requested=" + (observedMove[0] == null ? null : observedMove[0].requestedOwnDisplacement())
                    + " acceptedOwn=" + (observedMove[0] == null ? null : observedMove[0].acceptedOwnDisplacement())
                    + " measuredOwn=" + (observedMove[0] == null ? null : observedMove[0].measuredOwnDisplacement())
                    + " boundedOwn=" + boundedOwn + " geometryCorrection=" + geometryCorrection
                     + " positionCorrectionCertificate=" + (correction != null)
                     + " idleSupportProbeAttested=" + (move != null && move.idleSupportProbeAttested())
                     + " idleSupportProbeCertified=" + idleProbeCertified
                    + " tracking=" + movement.sable$getTrackingSubLevel() + " nativeFresh=" + (info != null && info != prior)
                    + " carry=" + (info == null ? null : info.inheritedMotion) + " grounded=" + human.onGround()
                    + " framePoint=" + point + " geometryObserved=" + observeGeometry + "\n";
            if (trace.length() < 24000) trace.append(row);
             if (result.isEmpty() || !Double.isFinite(after.x) || !Double.isFinite(after.y) || !Double.isFinite(after.z)
                    || local.x < storage.getX() - human.getBbWidth() || local.x > storage.getX() + ShuttleCabinFixture.WIDTH
                    || local.z < storage.getZ() - human.getBbWidth() || local.z > storage.getZ() + ShuttleCabinFixture.DEPTH
                    || local.y < storage.getY() + .8 || local.y > storage.getY() + ShuttleCabinFixture.HEIGHT) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error("strong impulse trace geometryObserved={}: {} nativeMove={}", observeGeometry, trace,
                        com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic.describe(human, observedMove[0]));
                String fullNativeTrace = observedMove[0] == null || observedMove[0].collisionScope() == null ? "none"
                        : observedMove[0].collisionScope().nativeCollisionTrace().toString();
                String failure = "native strong impulse body continuity failed geometryObserved=" + observeGeometry + ": " + row + trace + " nativeCorrections=" + fullNativeTrace;
                throw new GameTestAssertException(failure.substring(0, Math.min(850, failure.length())));
            }
            int nextRemaining = remaining - 1;
            if (nextRemaining == 0) {
                 System.out.println("SHUTTLE_STRONG_IMPULSE_COMPLETE geometryObserved=" + observeGeometry
                         + " angledFloorCorrectionTicks=" + angledFloorCorrectionTicks
                         + " idleProbeCertifiedTicks=" + idleProbeCertifiedTicks
                        + " ticks=" + (tick + 1) + " last=" + row);
                helper.succeed(); return;
            }
            tick++;
            helper.runAfterDelay(1, () -> this.tick(nextRemaining));
        }
    }

    private static void probeTrackedHitboxYaw(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity humanA,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity humanB,
            ServerSubLevel frameA, ServerSubLevel frameB, Vec3 stockProbeSpawn, int walkTicks) {
        require(new GroundedHarnessWorldStep().step(humanB, 0, 0, false, false, 0f).isPresent(),
                "real owned CHARACTER collision on the 45-degree frame primes the level's reused sink naturally");
        SableOwnedBodyMotion.MoveResult[] observed = {null};
        int wishX = walkTicks == 0 ? 0 : -1000;
        require(new GroundedHarnessWorldStep().step(humanA, wishX, 0, false, false, 0f,
                        move -> observed[0] = move).isPresent() && observed[0] != null,
                "identity-frame owned CHARACTER takes its ordinary grounded motor step");
        var move = observed[0];
        var scope = move.collisionScope();
        require(scope != null && scope.observedSdkYawSource() != null && scope.selectedOwnedYawPose() != null,
                "tracked-body yaw scope records the actual pre-overwrite SDK sink pose and selected live tracking pose");
        double primedYaw = poseYaw(scope.observedSdkYawSource());
        double selectedYaw = poseYaw(scope.selectedOwnedYawPose());
        require(Math.abs(Math.abs(primedYaw) - Math.PI / 4) < .02,
                "the actual SDK sink pose presented to A was naturally primed from live 45-degree frame B; yaw="
                        + primedYaw + ", sinkPose=" + scope.observedSdkYawSource());
        require(Math.abs(selectedYaw) < .02 && frameA.logicalPose().orientation().angle() < .02,
                "owned collision chooses identity yaw from its own native tracking frame A, never sink frame B; selected="
                        + scope.selectedOwnedYawPose() + ", A=" + frameA.logicalPose() + ", B=" + frameB.logicalPose());
        require(move.collisionInfoFresh() && move.collisionInfo().trackingSubLevel == frameA,
                "body-yaw source follows fresh native tracking identity A");
        require(Double.isFinite(move.acceptedOwnDisplacement().x()) && Double.isFinite(move.acceptedOwnDisplacement().y())
                        && Double.isFinite(move.acceptedOwnDisplacement().z()),
                "owned move displacement remains finite");
        if (walkTicks == 0) {
            require(humanA.onGround() && move.collisionInfo().verticalCollisionBelow
                            && Math.hypot(move.acceptedOwnDisplacement().x(), move.acceptedOwnDisplacement().z()) < .001
                            && Math.abs(move.acceptedOwnDisplacement().y()) < .002,
                    "idle body at the identity hull has real floor support without a phantom opposite horizontal push; move="
                            + OwnedNativeMoveDiagnostic.describe(humanA, move));
            helper.runAfterDelay(1, () -> probeTrackedHitboxYaw(helper, humanA, humanB, frameA, frameB,
                    stockProbeSpawn, 1));
            return;
        }
        var wallContact = move.collisionInfo().firstCollisions == null ? null
                : move.collisionInfo().firstCollisions.get(frameA);
        if (wallContact != null && wallContact.horizontal()
                && wallContact.globalDirection().x() > .9
                && wallContact.block().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())) {
            require(humanA.onGround() && move.collisionInfo().verticalCollisionBelow,
                    "A's stacked wall side contact does not replace its actual deck-floor support");
            requireClearAboveBody(helper.getLevel(), humanA);
            require(move.acceptedOwnDisplacement().x() <= NativeCollisionSlop.MAX_RESIDUAL,
                    "approaching the west hull wall creates no phantom positive own displacement");
            verifyDefaultStockContactProof(helper, humanB, frameA, stockProbeSpawn);
            return;
        }
        if (walkTicks >= 18)
            throw new GameTestAssertException("owned body never reached identity-frame stacked wall with correct own-frame yaw; "
                    + "tick=" + walkTicks + ", contact=" + wallContact + ", A=" + frameA.logicalPose()
                    + ", B=" + frameB.logicalPose() + ", native=" + OwnedNativeMoveDiagnostic.describe(humanA, move));
        helper.runAfterDelay(1, () -> probeTrackedHitboxYaw(helper, humanA, humanB, frameA, frameB,
                stockProbeSpawn, walkTicks + 1));
    }

    private static void verifyDefaultStockContactProof(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity frameBHuman,
            ServerSubLevel frameA, Vec3 spawn) {
        var stockHuman = BoundHumanFixture.create(helper.getLevel(), spawn, "owned-hitbox-stock-default-proof");
        awaitNativeDeckSupport(helper, stockHuman, 24, () -> {
            require(((EntityMovementExtension) (Object) stockHuman).sable$getTrackingSubLevel() == frameA,
                    "default-path proof actor has actual live tracking identity A");
            BoundedGameTestWait.safeDelay(helper, 1, () -> {
                require(new GroundedHarnessWorldStep().step(frameBHuman, 0, 0, false, false, 0f).isPresent(),
                        "45-degree frame B naturally re-primes the shared SDK sink before the STOCK_SDK contact");
                var stock = SableOwnedBodyMotion.move(stockHuman, new MovementVector(-.05, 0, 0),
                        SableOwnedBodyMotion.MoveOptions.serverDefaults());
                var info = stock.collisionInfo();
                var contact = info == null || info.firstCollisions == null ? null : info.firstCollisions.get(frameA);
                require(stock.collisionInfoFresh() && contact != null && contact.horizontal()
                                && contact.block().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()),
                        "default STOCK_SDK control sample actually contacts A's canonical side wall; move="
                                + OwnedNativeMoveDiagnostic.describe(stockHuman, stock));
                require(stock.collisionScope() == null && stock.horizontalContactEvidence() == null,
                        "a fresh real default-stock horizontal contact receives no owned-CHARACTER certificate");
                helper.succeed();
            });
        });
    }

    private static double poseYaw(dev.ryanhcode.sable.companion.math.Pose3dc pose) {
        var xAxis = pose.orientation().transform(new Vector3d(1, 0, 0));
        return Math.atan2(xAxis.z, xAxis.x);
    }

    private static double pointResolution(LoadedFrameRayQuery.ActorPoint point) {
        Vec3 world = point.worldPoint();
        Vec3 storage = point.storagePoint();
        double ulp = Math.max(Math.max(Math.ulp(world.x), Math.ulp(world.y)), Math.ulp(world.z));
        ulp = Math.max(ulp, Math.max(Math.max(Math.ulp(storage.x), Math.ulp(storage.y)), Math.ulp(storage.z)));
        return 16 * ulp;
    }

    private static int dominantHorizontalAxis(Vector3d vector) {
        return Math.abs(vector.x) >= Math.abs(vector.z) ? 0 : 2;
    }

    private static double axis(Vec3 vector, int axis) {
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    private static double axis(MovementVector vector, int axis) {
        return axis == 0 ? vector.x() : axis == 1 ? vector.y() : vector.z();
    }

    private static void requireClearAboveBody(ServerLevel level,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body) {
        var box = body.getBoundingBox();
        int minX = net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ).getX();
        int maxX = net.minecraft.core.BlockPos.containing(Math.nextDown(box.maxX), box.minY, box.minZ).getX();
        int minZ = net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ).getZ();
        int maxZ = net.minecraft.core.BlockPos.containing(box.minX, box.minY, Math.nextDown(box.maxZ)).getZ();
        int fromY = net.minecraft.core.BlockPos.containing(box.minX, box.maxY, box.minZ).getY();
        int toY = net.minecraft.core.BlockPos.containing(box.minX, box.maxY + 1.0, box.minZ).getY();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            for (int y = fromY; y <= toY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                require(level.hasChunkAt(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(),
                        "canonical cabin has loaded, collision-clear space immediately above the Human bounding box at " + pos);
            }
    }

    private static void awaitCabinFloorContact(GameTestHelper helper, ServerLevel level,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage, int ticksLeft, Runnable supported) {
        var movement = (EntityMovementExtension) (Object) human;
        Object previous = movement.sable$getCollisionInfo();
        var step = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, frameLocalXAxisYaw(plot));
        var info = movement.sable$getCollisionInfo();
        Vector3d localFeet = plot.subLevel().logicalPose().transformPositionInverse(
                new Vector3d(human.getX(), human.getY(), human.getZ()));
        double halfWidth = human.getBbWidth() / 2.0;
        double floorTop = storage.getY() + 1.0;
        BlockPos floorCell = BlockPos.containing(localFeet.x, localFeet.y - 1.0e-4, localFeet.z);
        var floorState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), floorCell);
        var contact = info == null || info.firstCollisions == null ? null : info.firstCollisions.get(plot.subLevel());
        Vector3d nativeUp = plot.subLevel().logicalPose().orientation().transform(new Vector3d(0, 1, 0));
        double supportNormalDot = contact == null ? Double.NaN
                : contact.globalDirection().x() * nativeUp.x + contact.globalDirection().y() * nativeUp.y
                + contact.globalDirection().z() * nativeUp.z;
        boolean clearOfHullWall = localFeet.x - storage.getX() - halfWidth >= 1.0 - .05
                && localFeet.x - storage.getX() + halfWidth <= ShuttleCabinFixture.WIDTH - 1.0 + .05;
        boolean exactFloor = step.isPresent() && info != null && info != previous && info.verticalCollisionBelow
                && human.onGround() && Math.abs(localFeet.y - floorTop) <= .1 && clearOfHullWall
                && floorCell.getY() == storage.getY() && floorState != null
                && floorState.is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                && contact != null && contact.block().equals(floorState) && supportNormalDot > .7;
        if (step.isPresent() && exactFloor) {
            supported.run();
            return;
        }
        if (ticksLeft <= 0) {
            String tag = "cabin-floor-support-" + human.getUUID();
            throw logFloorSupportTimeout(tag, "expectedFrame=" + plot.engineId() + " position=" + human.position()
                    + " localFeet=" + localFeet + " floorCell=" + floorCell + " floorState=" + floorState
                    + " selectedContact=" + contact + " supportNormalDot=" + supportNormalDot
                    + " wallClearance=" + (localFeet.x - storage.getX() - halfWidth) + " onGround=" + human.onGround()
                    + " native=" + OwnedNativeMoveDiagnostic.describe(human, null)
                    + " pose=" + plot.subLevel().logicalPose());
        }
        helper.runAfterDelay(1, () -> awaitCabinFloorContact(helper, level, human, plot, storage, ticksLeft - 1, supported));
    }

    /** Run exactly one owner movement attempt in a fresh server-tick PRE phase. */
    private static void onNextEligibleOwnerTickPre(GameTestHelper helper, ServerLevel level, Runnable movement) {
        var server = level.getServer();
        long registrationTick = server.getTickCount();
        long[] lastTick = {-1};
        int[] preEvents = {0};
        boolean[] removed = {false};
        Consumer<ServerTickEvent.Pre> listener = new Consumer<>() {
            private void remove() { if (!removed[0]) { removed[0] = true; NeoForge.EVENT_BUS.unregister(this); } }
            @Override public void accept(ServerTickEvent.Pre event) {
                if (removed[0] || event.getServer() != server) return;
                long current = server.getTickCount();
                if (++preEvents[0] > 3) {
                    remove();
                    BoundedGameTestWait.safeDelay(helper, 1, () -> helper.testInfo.fail(
                            new GameTestAssertException("no fresh ServerTickEvent.Pre within three events; registered="
                                    + registrationTick + ", current=" + current)));
                    return;
                }
                if (current == lastTick[0]) return;
                lastTick[0] = current;
                // If this event still reports the registration tick, defer to another PRE; never reset ownership budget.
                if (current <= registrationTick) return;
                try {
                    movement.run();
                    remove();
                    BoundedGameTestWait.safeDelay(helper, 1, helper::succeed);
                } catch (RuntimeException | AssertionError failure) {
                    remove();
                    BoundedGameTestWait.safeDelay(helper, 1, () -> helper.testInfo.fail(failure));
                }
            }
        };
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() { if (!removed[0]) { removed[0] = true; NeoForge.EVENT_BUS.unregister(listener); } }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
        NeoForge.EVENT_BUS.addListener(listener);
    }

    private static void awaitExactNativeDeckFloor(GameTestHelper helper, ServerLevel level,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage, int ticksLeft, Runnable supported) {
        var movement = (EntityMovementExtension) (Object) human;
        Object previous = movement.sable$getCollisionInfo();
        var step = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, frameLocalXAxisYaw(plot));
        var info = movement.sable$getCollisionInfo();
        Vector3d localFeet = localPosition(plot, human.position());
        BlockPos floorCell = BlockPos.containing(localFeet.x, storage.getY(), localFeet.z);
        var floorState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), floorCell);
        var contact = info == null || info.firstCollisions == null ? null : info.firstCollisions.get(plot.subLevel());
        Vector3d up = plot.subLevel().logicalPose().orientation().transform(new Vector3d(0, 1, 0));
        double normalDot = contact == null ? Double.NaN
                : contact.globalDirection().x() * up.x + contact.globalDirection().y() * up.y
                + contact.globalDirection().z() * up.z;
        boolean exactFloor = step.isPresent() && info != null && info != previous && info.verticalCollisionBelow
                && human.onGround() && Math.abs(localFeet.y - (storage.getY() + 1.0)) <= .02
                && floorState != null && floorState.is(Blocks.STONE)
                && contact != null && contact.block().equals(floorState) && normalDot > .7;
        if (exactFloor) {
            supported.run();
            return;
        }
        if (ticksLeft <= 0)
            throw new GameTestAssertException("replay actor did not settle onto fresh native current-pose deck floor; localFeet="
                    + localFeet + ", floorCell=" + floorCell + ", floorState=" + floorState + ", selectedContact="
                    + contact + ", supportNormalDot=" + normalDot + ", onGround=" + human.onGround()
                    + ", native=" + OwnedNativeMoveDiagnostic.describe(human, null));
        helper.runAfterDelay(1, () -> awaitExactNativeDeckFloor(helper, level, human, plot, storage,
                ticksLeft - 1, supported));
    }

    private static Vec3 cabinPoint(SableFixturePlot plot, BlockPos storage, double x, double y, double z) {
        Vector3d point = plot.subLevel().logicalPose().transformPosition(new Vector3d(
                storage.getX() + x, storage.getY() + y, storage.getZ() + z));
        return new Vec3(point.x, point.y, point.z);
    }

    private static float frameLocalXAxisYaw(SableFixturePlot plot) {
        var axis = plot.subLevel().logicalPose().orientation().transform(new Vector3d(1, 0, 0));
        return (float) Math.toDegrees(Math.atan2(axis.z, axis.x));
    }

    private static final class CabinWallProbe {
        private final GameTestHelper helper;
        private final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human;
        private final SableFixturePlot plot;
        private final BlockPos storage;
        private final GroundedHarnessWorldStep step = new GroundedHarnessWorldStep();
        private final Vector3d initialFramePosition;
        private boolean sawSideWallContact;
        private boolean sawAirborneWallContact;
        private int currentWishX;
        private int currentWishZ;

        private CabinWallProbe(GameTestHelper helper,
                               com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
                               SableFixturePlot plot, BlockPos storage) {
            this.helper = helper; this.human = human; this.plot = plot; this.storage = storage;
            this.initialFramePosition = plot.subLevel().logicalPose().position().get(new Vector3d());
        }

        private void run(int tick) {
            if (tick >= 108) {
                require(sawSideWallContact, "actor reached a real stacked cabin side hull wall");
                require(sawAirborneWallContact,
                        "toward-wall jump plus tangential input produced airborne native side-wall contact without floor support");
                helper.succeed();
                return;
            }
            helper.runAfterDelay(1, () -> {
                Vec3 before = human.position();
                boolean jump = tick == 20;
                int wishX = tick < 39 ? -1000 : tick < 56 ? 1000 : tick % 24 < 12 ? -1000 : 1000;
                int wishZ = tick >= 20 && tick < 39 ? 1000 : 0;
                currentWishX = wishX;
                currentWishZ = wishZ;
                var result = step.step(human, wishX, wishZ, jump, false, frameLocalXAxisYaw(plot));
                Vec3 accepted = human.position().subtract(before);
                var movement = (EntityMovementExtension) (Object) human;
                var info = movement.sable$getCollisionInfo();
                var pose = plot.subLevel().logicalPose();
                boolean finite = Double.isFinite(human.getX()) && Double.isFinite(human.getY())
                        && Double.isFinite(human.getZ()) && Double.isFinite(accepted.lengthSqr());
                Vector3d currentFramePosition = plot.subLevel().logicalPose().position().get(new Vector3d());
                if (currentFramePosition.distance(initialFramePosition) > .08)
                    fail(tick, before, accepted, info, "stationary cabin native frame drifted instead of settling");
                Vector3d localFeet = plot.subLevel().logicalPose().transformPositionInverse(
                        new Vector3d(human.getX(), human.getY(), human.getZ()));
                double feetAboveRoomFloor = localFeet.y - storage.getY() - 1.0;
                double halfWidth = human.getBbWidth() / 2.0;
                boolean alongsideStackedWall = localFeet.x - storage.getX() - halfWidth <= 1.15
                        && localFeet.x - storage.getX() + halfWidth >= 1.0
                        && localFeet.y - storage.getY() < ShuttleCabinFixture.HEIGHT - 1.0;
                boolean penetratesSideHull = localFeet.x - storage.getX() - halfWidth < 1.0 - .05;
                if (penetratesSideHull)
                    fail(tick, before, accepted, info, "human entered stacked side-hull volume");
                boolean sideContactThisTick = false;
                if (info != null && info.firstCollisions != null) {
                    var contact = info.firstCollisions.get(plot.subLevel());
                    if (contact != null) {
                        var localX = plot.subLevel().logicalPose().orientation().transform(new Vector3d(1, 0, 0));
                        var normal = contact.globalDirection();
                        sideContactThisTick = Math.abs(normal.x() * localX.x + normal.y() * localX.y + normal.z() * localX.z) > .7;
                        if (sideContactThisTick) sawSideWallContact = true;
                    }
                }
                if (feetAboveRoomFloor > .25 && feetAboveRoomFloor < 4.0 && alongsideStackedWall) {
                    if (human.onGround())
                        fail(tick, before, accepted, info, "vertical side wall falsely set onGround above room floor");
                    if (info != null && info.verticalCollisionBelow)
                        fail(tick, before, accepted, info, "vertical side wall falsely reported below-floor native contact");
                    if (sideContactThisTick && currentWishX < 0 && currentWishZ != 0)
                        sawAirborneWallContact = true;
                }
                if (jump && result.isEmpty()) fail(tick, before, accepted, info, "toward-wall jump world step rejected");
                if (result.isEmpty() || !finite) fail(tick, before, accepted, info, "non-finite or rejected production movement");
                if (human.onGround() && feetAboveRoomFloor <= .25)
                    require(info != null && info.verticalCollisionBelow,
                            diagnostic(tick, before, accepted, info, "cabin floor support lacks native floor contact"));
                run(tick + 1);
            });
        }

        private void fail(int tick, Vec3 before, Vec3 accepted, SubLevelEntityCollision.CollisionInfo info, String reason) {
            throw new GameTestAssertException(diagnostic(tick, before, accepted, info, reason));
        }

        private String diagnostic(int tick, Vec3 before, Vec3 accepted,
                                  SubLevelEntityCollision.CollisionInfo info, String reason) {
            return reason + "; tick=" + tick + ", requestedWishX=" + currentWishX + ", requestedWishZ=" + currentWishZ + "; poseYaw=" + frameLocalXAxisYaw(plot)
                    + "), acceptedWorld=" + accepted + ", nativeFreshInfo=" + info + ", verticalBelow="
                    + (info != null && info.verticalCollisionBelow) + ", firstContacts="
                    + (info == null ? null : info.firstCollisions) + ", frameLogicalPose=" + plot.subLevel().logicalPose()
                    + ", nativeDiagnostic=" + OwnedNativeMoveDiagnostic.describe(human, null)
                    + ", before=" + before + ", current=" + human.position() + ", onGround=" + human.onGround();
        }

    }

    @GameTestTags({"shuttle-stationary-contact", "shuttle-movement-recovery"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 300)
    public static void stationaryDeckWallContactKeepsSupportedHumanValid(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos world=new BlockPos(1680,300,1200);
        BlockPos sceneOrigin=world.offset(-8,0,-8);
        SableFixtureScene.Region scene=SableFixtureScene.acquire(helper,sceneOrigin,DECK_SIZE+16,3,DECK_SIZE+16);
        SableFixtureScene.awaitEmpty(helper,scene,SableFixtureScene.READINESS_TICKS,()->{
            SableFixtureScene.authorStoneSupportPad(level,scene);
            var authored=SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(world.getX()+.5,world.getY(),world.getZ()+.5),plot->{
                BlockPos storage=plot.storageOrigin();
                plot.ensureStorageFootprint(storage,DECK_SIZE,3,DECK_SIZE);
                for(int x=0;x<DECK_SIZE;x++)for(int z=0;z<DECK_SIZE;z++)
                    if(!plot.setStorageBlock(storage.offset(x,0,z),Blocks.STONE.defaultBlockState()))
                        throw new IllegalStateException("stationary native deck floor authoring failed");
                for(int y=1;y<=2;y++)if(!plot.setStorageBlock(storage.offset(4,y,3),Blocks.STONE.defaultBlockState()))
                    throw new IllegalStateException("stationary native wall cell authoring failed at y="+y);
                return storage;
            });
            SableFixtureScene.trackPlot(helper,authored.plot());
            SableFixturePlot plot=authored.plot();
            BlockPos storage=authored.result();
            SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level,scene,plot,storage,DECK_SIZE,DECK_SIZE);
            var nativeFloor=LoadedFrameRayQuery.knownServerBlock(level,plot.subLevel(),storage);
            require(nativeFloor!=null&&nativeFloor.is(Blocks.STONE),"stationary deck has actual native stone floor");
            Vec3 feet=deckFeetAt(plot,storage,3.35,3.5);
            var human=BoundHumanFixture.create(level,feet,"stationary-native-contact");
            awaitNativeDeckSupport(helper,human,30,()->{
                require(human.onGround(),"Human is supported by the real stationary Sable deck before wall probing");
                runStationaryWallProbe(helper,human,0,120,0);
            });
        });
    }

    private static void runStationaryWallProbe(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int tick, int remaining, int rejected) {
        if (tick >= remaining) {
            require(rejected == 0 && human.onGround(),
                    "stationary wall-contact baseline preserves native floor support without a bounded motor rejection; rejected="
                            + rejected + " position=" + human.position());
            helper.succeed();
            return;
        }
        helper.runAfterDelay(1, () -> {
            var result = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f);
            runStationaryWallProbe(helper, human, tick + 1, remaining, rejected + (result.isEmpty() ? 1 : 0));
        });
    }

    @GameTestTags({"shuttle-owner-regression", "shuttle-client-frame", "shuttle-frame-transition", "shuttle-stationary-contact"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 220)
    public static void clippedStockCarrierNeverBecomesNegativeOwnDisplacement(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1520, 300, 1040), 8, 4, NATIVE_SCENE_READY_TICKS,
                (level, plot, storage) -> {
            var human = BoundHumanFixture.create(level, deckFeetAt(plot, storage, 5.15, 2.5), "client-carrier-clip");
            awaitNativeDeckSupport(helper, human, 32, () -> {
                var movement = (EntityMovementExtension) (Object) human;
                ServerSubLevel frame = (ServerSubLevel) movement.sable$getTrackingSubLevel();
                require(frame != null && frame.getUniqueId().equals(plot.engineId()), "real Human has the exact native frame tracker");
                ClientFramePredictionState policy = new ClientFramePredictionState();
                var initial = LoadedFrameRayQuery.currentActorPoint(level, human);
                var initialPose = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                policy.authorityProjected(initial, 91, initialPose);
                Vec3 initialPosition = human.position();
                BlockPos initialFeet = BlockPos.containing(initialPosition);
                int wallY = initialFeet.getY() + 1;
                int wallX = (int) Math.ceil(human.getBoundingBox().maxX);
                BlockPos wall = new BlockPos(wallX, wallY, initialFeet.getZ());
                BlockPos wallUpper = wall.above();
                double initialWallGap = wall.getX() - human.getBoundingBox().maxX;
                require(Double.isFinite(initialWallGap) && initialWallGap > 0 && initialWallGap < .1,
                        "stationary ordinary wall must be within one initial carrier displacement; gap=" + initialWallGap);
                require(level.getBlockState(wall).isAir() && level.getBlockState(wallUpper).isAir(),
                        "pre-leased fixture wall volume is empty");
                level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(wallUpper, Blocks.STONE.defaultBlockState(), 3);
                helper.testInfo.addListener(new GameTestListener() {
                    private void cleanup() {
                        if (level.getBlockState(wall).equals(Blocks.STONE.defaultBlockState()))
                            level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
                        if (level.getBlockState(wallUpper).equals(Blocks.STONE.defaultBlockState()))
                            level.setBlock(wallUpper, Blocks.AIR.defaultBlockState(), 3);
                    }
                    @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
                    @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                    @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                    @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                });
                RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
                BoundedGameTestWait.await(helper, 20, () -> "SDK frame did not advance a real native pose interval",
                        () -> !initialPose.equals(NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose())), () -> {
                            Vec3 start = human.position();
                            BlockPos feet = BlockPos.containing(start);
                             var actualBefore = LoadedFrameRayQuery.currentActorPoint(level, human);
                             var basis = LoadedFrameRayQuery.pointAtWorldPosition(level, plot.engineId(), human.position());
                             var pose = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                             double wallGap = wall.getX() - human.getBoundingBox().maxX;
                             require(Double.isFinite(wallGap) && wallGap >= -.001 && wallGap < .01,
                                     "actual native-carried body reaches the pre-placed ordinary wall; gap="
                                             + wallGap + ", body=" + human.getBoundingBox() + ", wall=" + wall);
                            require(actualBefore != null && basis != null && policy.allowNativeCarrier(true, 91, actualBefore, pose),
                                    "same real native frame and new SDK interval admit the stock inheritedMotion path");
                            var previousInfo = movement.sable$getCollisionInfo();
                            var result = SableOwnedBodyMotion.move(human, new MovementVector(0, -.01, 0),
                                    new SableOwnedBodyMotion.MoveOptions(true, basis));
                            var info = movement.sable$getCollisionInfo();
                            if (info == null || info == previousInfo || info.inheritedMotion == null
                                    || Math.abs(info.inheritedMotion.x) <= .001)
                                throw new GameTestAssertException("fresh SDK collision info lacks expected horizontal carrier: "
                                        + "fresh=" + (info != null && info != previousInfo) + ", inherited="
                                        + (info == null ? null : info.inheritedMotion) + ", tracking="
                                        + (info == null ? null : info.trackingSubLevel) + ", before=" + actualBefore
                                        + ", after=" + result.afterPoint() + ", initialPose=" + initialPose
                                        + ", currentPose=" + pose + ", body=" + human.position() + ", wall=" + wall);
                            double rawCarrierX = Math.abs(info.inheritedMotion.x);
                            double actualX = human.getX() - start.x;
                            require(Math.abs(result.acceptedOwnDisplacement().x()) < .005,
                                    "accepted own X remains zero even when stock carrier is clipped; own="
                                            + result.acceptedOwnDisplacement() + ", rawCarrierX=" + rawCarrierX);
                            double wallFaceGap = wall.getX() - human.getBoundingBox().maxX;
                            if (!(rawCarrierX - Math.abs(actualX) > .002 && wallFaceGap >= -.001 && wallFaceGap < .01))
                                throw new GameTestAssertException("ordinary-world wall did not clip stock inheritedMotion: actualX=" + actualX
                                        + ", rawCarrier=" + info.inheritedMotion + ", rawCarrierX=" + rawCarrierX
                                        + ", start=" + start + ", body=" + human.position() + ", feet=" + feet
                                        + ", wall=" + wall + ", wallFaceGap=" + wallFaceGap + ", wallState=" + level.getBlockState(wall)
                                        + ", result=" + result);
                             helper.succeed();
                        });
            });
        });
    }

    @GameTestTags({"shuttle-client-frame", "shuttle-frame-transition"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 120)
    public static void replaySuppressesFreshStockCarrierAcrossSeveralOwnMoves(GameTestHelper helper) {
        withDeck(helper, new BlockPos(1560, 300, 1040), 8, (level, plot, storage) -> {
            var human = BoundHumanFixture.create(level, deckFeet(plot, storage, 2, 2), "client-replay-own-only");
            awaitNativeDeckSupport(helper, human, 28, () -> {
                awaitExactNativeDeckFloor(helper, level, human, plot, storage, 24, () -> {
                    var movement = (EntityMovementExtension) (Object) human;
                    ServerSubLevel frame = (ServerSubLevel) movement.sable$getTrackingSubLevel();
                    require(frame != null && frame.getUniqueId().equals(plot.engineId()), "replay body is really tracked by authored frame");
                    Vec3 worldBeforeFrameAdvance = human.position();
                    Vector3d knownStorage = localPosition(plot, worldBeforeFrameAdvance);
                    require(Math.abs(knownStorage.y - (storage.getY() + 1.0)) <= .02,
                            "saved replay foot point is on the current logical deck plane; storagePoint=" + knownStorage
                                    + ", floorY=" + (storage.getY() + 1.0));
                    BlockPos supportCell = BlockPos.containing(knownStorage.x, storage.getY(), knownStorage.z);
                    var supportState = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), supportCell);
                    require(supportCell.getY() == storage.getY() && supportCell.getX() >= storage.getX()
                                    && supportCell.getX() < storage.getX() + DECK_SIZE
                                    && supportCell.getZ() >= storage.getZ() && supportCell.getZ() < storage.getZ() + DECK_SIZE
                                    && supportState != null && supportState.is(Blocks.STONE),
                            "saved current-pose floor point lies over an actual authored deck cell; storagePoint=" + knownStorage
                                    + ", cell=" + supportCell);
                    var initialPose = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                    RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
                    BoundedGameTestWait.safeDelay(helper, 2, () -> {
                        ClientFramePredictionState policy = new ClientFramePredictionState();
                        var poses = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                        require(!poses.equals(initialPose), "native fixture advanced to a distinct last/logical frame interval");
                        Vec3 storagePoint = new Vec3(knownStorage.x, knownStorage.y, knownStorage.z);
                        Vec3 projectedAuthority = LoadedFrameRayQuery.projectActorPoint(level, frame.getUniqueId(), storagePoint);
                        require(projectedAuthority != null, "known pre-advance storage point projects through the current native logical pose");
                        // One fixture authority projection with a small clearance above the saved floor point;
                        // the following native downward sweep must establish actual floor contact.
                        human.setPos(projectedAuthority.add(0, .025, 0));
                        var before = LoadedFrameRayQuery.currentActorPoint(level, human);
                        require(before != null && frame.getUniqueId().equals(before.frameId()),
                                "fixture body is placed at current projected authority plus probe clearance while retaining SDK identity");
                        policy.authorityProjected(before, 92, poses);
                        Vec3[] ownTotals = {Vec3.ZERO};
                        double[] rawCarry = {0};
                        Object previous = movement.sable$getCollisionInfo();
                        for (int i = 0; i < 3; i++) {
                        var current = LoadedFrameRayQuery.currentActorPoint(level, human);
                        var basis = LoadedFrameRayQuery.pointAtWorldPosition(level, plot.engineId(), human.position());
                        MovementVector requested = new MovementVector(.01, -.05, 0);
                        Vec3 beforeWorld = human.position();
                        var move = SableOwnedBodyMotion.move(human, requested,
                                new SableOwnedBodyMotion.MoveOptions(
                                 policy.allowNativeCarrier(false, 92, current, poses), basis,
                                         policy.collisionPosePolicy(false,
                                                 frame == movement.sable$getTrackingSubLevel(), false),
                                         SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER));
                        var info = movement.sable$getCollisionInfo();
                        require(move.collisionPosePolicy() == SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY,
                                "replay explicitly opts into current frame collision geometry");
                        require(info != null && info != previous,
                                "each replay Entity.move produces fresh SDK collision info");
                        require(info.inheritedMotion == null || info.inheritedMotion.lengthSqr() < 1e-8,
                                "replay collision itself reads current frame geometry and returns no repeated SDK interval carry; info="
                                        + OwnedNativeMoveDiagnostic.describe(human, move));
                        if (info.firstCollisions == null || !info.firstCollisions.containsKey(frame)
                                || !info.verticalCollisionBelow || !human.onGround())
                            throw new GameTestAssertException("current-pose replay lost real native deck support; requested="
                                    + requested + ", accepted=" + move.acceptedOwnDisplacement()
                                    + ", observed=" + human.position().subtract(beforeWorld)
                                    + ", localFeet=" + localPosition(plot, human.position())
                                    + ", frameLastPose=" + frame.lastPose() + ", frameLogicalPose=" + frame.logicalPose()
                                    + ", supportStorage=" + knownStorage + "/" + supportCell + ":"
                                    + level.getBlockState(supportCell)
                                    + ", native=" + OwnedNativeMoveDiagnostic.describe(human, move));
                        previous = info;
                        ownTotals[0] = ownTotals[0].add(new Vec3(move.acceptedOwnDisplacement().x(),
                                move.acceptedOwnDisplacement().y(), move.acceptedOwnDisplacement().z()));
                        if (info.inheritedMotion != null) rawCarry[0] += info.inheritedMotion.x;
                        }
                        double totalX = human.getX() - projectedAuthority.x;
                        require(Math.abs(ownTotals[0].x - .03) < .006 && Math.abs(totalX - .03) < .01,
                                "three same-interval replay moves sum OWN displacement only: accepted=" + ownTotals[0]
                                        + ", observed=" + totalX + ", discardedSDKCarrierSum=" + rawCarry[0]);
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTestTags({"shuttle-client-frame", "shuttle-frame-transition"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 130)
    public static void authorityFrameBRejectsNativeTrackingAAndUsesBVelocityBasis(GameTestHelper helper) {
        withDeckPair(helper, new BlockPos(1600, 300, 1040), .2, (plotA, storageA, plotB, storageB) -> {
            ServerLevel level = helper.getLevel();
            var human = BoundHumanFixture.create(level, deckFeet(plotA, storageA, 2, 2), "client-authority-frame-b");
            awaitNativeDeckSupport(helper, human, 32, () -> {
                var movement = (EntityMovementExtension) (Object) human;
                var frameA = (ServerSubLevel) movement.sable$getTrackingSubLevel();
                require(frameA != null && frameA.getUniqueId().equals(plotA.engineId()), "body begins with actual native A tracking");
                var sourceA = LoadedFrameRayQuery.currentActorPoint(level, human);
                var targetB = LoadedFrameRayQuery.pointAtWorldPosition(level, plotB.engineId(), deckFeet(plotB, storageB, 2, 2));
                require(sourceA != null && targetB != null, "both exact frame points are loaded and validated");
                var intervalB = NativeFrameInterval.PosePair.capture(plotB.subLevel().lastPose(), plotB.subLevel().logicalPose());
                ClientFramePredictionState policy = new ClientFramePredictionState();
                policy.authorityProjected(targetB, 93, intervalB);
                human.setPos(targetB.worldPoint()); // one authority-snapshot projection; never edit SDK tracking
                require(movement.sable$getTrackingSubLevel() == frameA, "authority B placement leaves SDK's old A tracker untouched");
                var nativeBefore = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(nativeBefore == null || !plotB.engineId().equals(nativeBefore.frameId()),
                        "native pre-move identity cannot silently replace authority B; actual=" + nativeBefore
                                + ", oldA=" + plotA.engineId() + ", authorityB=" + plotB.engineId());
                require(!policy.allowNativeCarrier(true, 93, nativeBefore, intervalB),
                        "B snapshot basis rejects stale or unresolved native pre-move identity");
                var before = human.position();
                var moved = SableOwnedBodyMotion.move(human, new MovementVector(0, .01, 0),
                        new SableOwnedBodyMotion.MoveOptions(false, targetB));
                require(Math.abs(human.getX() - before.x) < .005, "stale A point velocity is not turned into a recurring X carry");
                require(moved.frameVelocityDeltaPerSecond() == MovementVector.ZERO
                                || moved.frameVelocityDeltaPerSecond() != null
                                && Math.sqrt(Math.pow(moved.frameVelocityDeltaPerSecond().x(), 2)
                                + Math.pow(moved.frameVelocityDeltaPerSecond().y(), 2)
                                + Math.pow(moved.frameVelocityDeltaPerSecond().z(), 2)) < .02,
                        "first synchronized frame-B move has no A-to-B velocity delta");
                require(moved.collisionInfoFresh(), "real native move produced fresh SDK collision result");
                policy.acceptFreshMove(moved.afterPoint());
                require(moved.afterPoint() != null && java.util.Objects.equals(policy.velocityBasisFrame(), moved.afterPoint().frameId()),
                        "accepted native collision point, including an explicit world identity, becomes next velocity basis");
                helper.succeed();
            });
        });
    }

    @GameTestTags({"shuttle-movement-recovery", "movement", "shuttle-client-frame", "shuttle-native-contact-following-step"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void tangentNativeContactSlopDoesNotRejectFollowingHumanIntent(GameTestHelper helper) {
        requireStrongImpulseConfig(helper, "[native-contact-following-step-config]", false);
        withDeck(helper, new BlockPos(1640, 300, 1120), 8, 4, (level, plot, storage) -> {
            var human = BoundHumanFixture.create(level, deckFeetAt(plot, storage, 5.2, 2.8), "native-contact-slop");
            awaitNativeDeckSupport(helper, human, 30, () -> {
                require(human.onGround(), "registered Human is settled on real native floor before side contact");
                Vec3 feet = human.position();
                int wallY = BlockPos.containing(feet).getY() + 1;
                BlockPos xWall = new BlockPos(BlockPos.containing(feet).getX() - 1, wallY, BlockPos.containing(feet).getZ());
                BlockPos zWall = new BlockPos(BlockPos.containing(feet).getX(), wallY, BlockPos.containing(feet).getZ() - 1);
                BlockPos[] authoredWalls = {xWall, xWall.above(), zWall, zWall.above()};
                for (BlockPos block : authoredWalls)
                    require(level.getBlockState(block).isAir(), "leased side-contact wall cell is clear: " + block);
                helper.testInfo.addListener(new GameTestListener() {
                    private void cleanup() { for (BlockPos block : authoredWalls)
                        if (level.getBlockState(block).equals(Blocks.STONE.defaultBlockState()))
                            level.setBlock(block, Blocks.AIR.defaultBlockState(), 3); }
                    @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
                    @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                    @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                    @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                });
                for (BlockPos block : authoredWalls) level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
                ServerLevel serverLevel = (ServerLevel) level;
                MindControlledMob owner = (MindControlledMob) (Object) human;
                GroundedHarnessWorldStep step = new GroundedHarnessWorldStep();
                var originalBinding = human.playerCharacterBinding();
                require(originalBinding != null && originalBinding.mindId() != null,
                        "enrolled body starts with its Mind binding");
                long firstTick = serverLevel.getServer().getTickCount();
                require(!owner.moonstation14$physicalStepConsumed(serverLevel, firstTick),
                        "first manual step starts with an unused physical-step budget");
                final com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult[] firstNative = {null};
                var first = step.step(human, 0, 1000, false, false, 156.14905f, moved -> firstNative[0] = moved);
                require(first.isPresent(), "native angled wall contact plus 2.1e-7 orthogonal float residue remains a valid bounded world step");
                require(firstNative[0] != null, "first intent reached the native collision resolver");
                require(owner.moonstation14$physicalStepConsumed(serverLevel, firstTick),
                        "first manual step consumes its physical-step budget");
                Vec3 afterFirst = human.position();
                require(Double.isFinite(afterFirst.x) && Double.isFinite(afterFirst.y) && Double.isFinite(afterFirst.z),
                        "accepted collision leaves the true native body position finite");
                helper.runAfterDelay(1, () -> {
                    long secondTick = serverLevel.getServer().getTickCount();
                    require(secondTick > firstTick, "following intent executes on a later physical server tick");
                    require(!owner.moonstation14$physicalStepConsumed(serverLevel, secondTick),
                            "following manual step starts with an unused physical-step budget");
                    final com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult[] secondNative = {null};
                    var second = step.step(human, 0, 1000, false, false, 156.14905f, moved -> secondNative[0] = moved);
                    require(second.isPresent(), "next normal intent remains usable after tolerated native contact slop");
                    require(secondNative[0] != null, "following intent reached the native collision resolver");
                    require(owner.moonstation14$physicalStepConsumed(serverLevel, secondTick),
                            "following manual step consumes its physical-step budget");
                    require(originalBinding.equals(human.playerCharacterBinding()),
                            "the original enrolled body/Mind binding survives native side contact");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void typedDamageCorpseRemainsOnTranslatingNativeDeck(GameTestHelper helper) {
        withDeck(helper, new BlockPos(280, 300, 200), (level, plot, storage) -> {
            var corpse = BoundHumanFixture.create(level, deckFeet(plot, storage, 3, 3), "sable-corpse-carry");
            require(DamageSystem.applyHealthChange(corpse, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                            == DamageSystem.Result.APPLIED && MobStateSystem.isDead(corpse)
                            && OwnedCorpsePhysics.eligible(corpse),
                    "retained corpse is produced by actual typed damage and remains in corpse owner policy");
            UUID rootId = corpse.getUUID();
            Object binding = corpse.playerCharacterBinding();
            Object hands = corpse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            Vector3d relativeBefore = localPosition(plot, corpse.position());
            Vec3 worldBefore = corpse.position();
            RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(.5, 0, .5), new Vector3d());
            BoundedGameTestWait.safeDelay(helper, 18, () -> {
                require(helper.getLevel().getEntity(rootId) == corpse && MobStateSystem.isDead(corpse)
                                && corpse.playerCharacterBinding().equals(binding)
                                && corpse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands,
                        "auto-ticked corpse preserves body/Mind/hands identities while carried");
                require(localPosition(plot, corpse.position()).distance(relativeBefore) < .2,
                        "real medical corpse physics stays near its deck-relative point during native translation");
                require(corpse.position().subtract(worldBefore).horizontalDistance() > .1,
                        "native plot translates the corpse in world space rather than leaving it at its spawn");
                helper.succeed();
            });
        });
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 140)
    public static void boundHumanFrameDepartureRecordsNativeVelocityEvidence(GameTestHelper helper) {
        BlockPos world = new BlockPos(960, 300, 640);
        ServerLevel level = helper.getLevel();
        withDeck(helper, world, 8, (ignored, plot, storage) -> {
            var human = BoundHumanFixture.create(level, deckFeet(plot, storage, 2, 2), "sable-frame-transition");
            UUID rootId = human.getUUID();
            Object binding = human.playerCharacterBinding();
            Object hands = human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            awaitNativeDeckSupport(helper, human, 30, () -> {
                require(human.onGround(), "real bound human begins on native deck");
                var movement = (EntityMovementExtension) (Object) human;
                ServerSubLevel frame = (ServerSubLevel) movement.sable$getTrackingSubLevel();
                require(frame != null, "native tracking frame established before transition");
                RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(2, 0, 2), new Vector3d());
                observeDeparture(helper, human, frame.getUniqueId(), rootId, binding, hands, false, 0);
            });
        });
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle", "shuttle-rotating-departure-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 140)
    public static void boundHumanDeparturePreservesRotatingFramePointVelocity(GameTestHelper helper) {
        BlockPos world = new BlockPos(1040, 300, 640);
        ServerLevel level = helper.getLevel();
        withDeck(helper, world, 8, (ignored, plot, storage) -> {
            var human = BoundHumanFixture.create(level, deckFeet(plot, storage, 1, 2), "sable-rotating-frame-transition");
            UUID rootId = human.getUUID();
            Object binding = human.playerCharacterBinding();
            Object hands = human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            awaitNativeDeckSupport(helper, human, plot, storage, 30, () -> {
                require(human.onGround(), "real bound human establishes native deck support before rotation");
                var movement = (EntityMovementExtension) (Object) human;
                ServerSubLevel frame = (ServerSubLevel) movement.sable$getTrackingSubLevel();
                require(frame != null && plot.engineId().equals(frame.getUniqueId()),
                        "human is tracked on the exact native frame before rotation");
                var handle = RigidBodyHandle.of(plot.subLevel());
                Vector3d requestedAngular = new Vector3d(0, 2.0, 0);
                handle.addLinearAndAngularVelocity(new Vector3d(.5, 0, .5), requestedAngular);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "[rotating-departure-launch] expectedFrame={} requestedAngular={} admittedAngular={} currentLinear={} actorPoint={} onGround={}",
                        frame.getUniqueId(), requestedAngular, handle.getAngularVelocity(new Vector3d()),
                        handle.getLinearVelocity(new Vector3d()), human.position(), human.onGround());
                observeDeparture(helper, human, frame.getUniqueId(), rootId, binding, hands, true, 0);
            });
        });
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void boundHumanBoardingPreservesNativeFramePointVelocity(GameTestHelper helper) {
        runBoundHumanBoarding(helper, new BlockPos(1080, 300, 640), "sable-frame-boarding",
                new Vector3d(2, 0, 2), new Vector3d(), 0, null);
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle", "shuttle-boarding-proof-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void boundHumanBoardsDeckMovingAwayAtFiveMps(GameTestHelper helper) {
        runBoundHumanBoarding(helper, new BlockPos(1160, 300, 640), "sable-frame-boarding-away-five",
                new Vector3d(0, 0, 5), new Vector3d(), 0, 1);
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle", "shuttle-boarding-proof-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 160)
    public static void boundHumanBoardsDeckApproachingAtFiveMps(GameTestHelper helper) {
        runBoundHumanBoarding(helper, new BlockPos(1240, 300, 640), "sable-frame-boarding-approach-five",
                new Vector3d(0, 0, -5), new Vector3d(), 0, -1, SLOW_NATIVE_SCENE_READY_TICKS);
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle", "shuttle-boarding-proof-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 120)
    public static void boundHumanBoardsSpinningDeckDuringDescent(GameTestHelper helper) {
        runBoundHumanBoarding(helper, new BlockPos(1320, 300, 640), "sable-frame-boarding-spin-descent",
                new Vector3d(), new Vector3d(0, 1, 0), .8, null);
    }

    private static GameTestAssertException logFloorSupportTimeout(String tag, String fullDiagnostic) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[{}] full bounded-wait diagnostic={}", tag, fullDiagnostic);
        return new GameTestAssertException(tag + " timeout; full diagnostic logged once");
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle-carry-basis-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 180)
    public static void boundHumanBoardsRisingDeckWithUpwardFloorContact(GameTestHelper helper) {
        runBoundHumanBoarding(helper, new BlockPos(920, 300, 2400), "sable-rising-frame-positive-control",
                new Vector3d(0, 2, 2), new Vector3d(), .4, null, SLOW_NATIVE_SCENE_READY_TICKS, true);
    }

    private static void runBoundHumanBoarding(GameTestHelper helper, BlockPos world, String name,
                                               Vector3d linear, Vector3d angular, double height,
                                               Integer expectedZSign) {
        runBoundHumanBoarding(helper, world, name, linear, angular, height, expectedZSign, 40);
    }

    private static void runBoundHumanBoarding(GameTestHelper helper, BlockPos world, String name,
                                               Vector3d linear, Vector3d angular, double height,
                                               Integer expectedZSign, int sceneReadinessTicks) {
        runBoundHumanBoarding(helper, world, name, linear, angular, height, expectedZSign,
                sceneReadinessTicks, false);
    }

    private static void runBoundHumanBoarding(GameTestHelper helper, BlockPos world, String name,
                                               Vector3d linear, Vector3d angular, double height,
                                               Integer expectedZSign, int sceneReadinessTicks,
                                               boolean requireUpwardFrameFloor) {
        ServerLevel level = helper.getLevel();
        withDeck(helper, world, 8, 1, sceneReadinessTicks, (ignored, plot, storage) -> {
            Vec3 spawn = deckFeet(plot, storage, 2, 2).add(0, .15 + height, 0);
            var human = BoundHumanFixture.create(level, spawn, name);
            UUID rootId = human.getUUID();
            Object binding = human.playerCharacterBinding();
            Object hands = human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var movement = (EntityMovementExtension) (Object) human;
            require(!human.onGround() && movement.sable$getTrackingSubLevel() == null,
                    "boarding fixture begins airborne in ordinary world, not already tracked on its deck");
            // Give the spinning carrier an initial world-up flight velocity so it reaches the deck
            // before native gravity returns it to the ordinary-world ground pad. Boarding tags run concurrently.
            Vector3d launchLinear = new Vector3d(linear);
            if (angular.lengthSquared() > 0) launchLinear.y += 4;
            RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(launchLinear, angular);
            observeBoarding(helper, human, plot, rootId, binding, hands, 0, expectedZSign,
                    angular.lengthSquared() > 0, requireUpwardFrameFloor);
        });
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 140)
    public static void boundHumanSwapBetweenIndependentFramesPreservesMapVelocity(GameTestHelper helper) {
        BlockPos world = new BlockPos(1120, 300, 640);
        ServerLevel level = helper.getLevel();
        withDeckPair(helper, world, .25, (a, storageA, b, storageB) -> {
            var human = BoundHumanFixture.create(level, deckFeetAt(a, storageA, 5.0, 3.0),
                    "sable-independent-frame-swap");
            UUID rootId = human.getUUID();
            Object binding = human.playerCharacterBinding();
            Object hands = human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            require(hands != null, "normal bound Human has live hand owner before frame swap");
            awaitNativeDeckSupport(helper, human, 30, () -> {
                require(human.onGround(), "body establishes actual ground support before transferring between frames");
                var prePoint = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(prePoint != null && a.engineId().equals(prePoint.frameId()),
                        "body starts tracked on exact native frame A before any frame motion");
                var initialA = a.subLevel().logicalPose().position().get(new Vector3d());
                var initialB = b.subLevel().logicalPose().position().get(new Vector3d());
                // One native command per plot; measured point velocities, not these requested values, govern assertions.
                RigidBodyHandle.of(a.subLevel()).addLinearAndAngularVelocity(new Vector3d(0, 0, 4), new Vector3d());
                RigidBodyHandle.of(b.subLevel()).addLinearAndAngularVelocity(new Vector3d(0, 0, -4), new Vector3d());
                observeFrameSwap(helper, human, a, b, rootId, binding, hands, initialA, initialB, 0);
            });
        });
    }

    private static void observeFrameSwap(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot frameA, SableFixturePlot frameB, UUID rootId, Object binding, Object hands,
            Vector3d initialPoseA, Vector3d initialPoseB, int ticks) {
        if (ticks >= 24) throw new GameTestAssertException("no direct fresh native A-to-B transition; A="
                + frameA.engineId() + " B=" + frameB.engineId() + " actor="
                + LoadedFrameRayQuery.currentActorPoint((ServerLevel) human.level(), human));
        helper.runAfterDelay(1, () -> {
            ServerLevel level = (ServerLevel) human.level();
            var movement = (EntityMovementExtension) (Object) human;
            Object previousInfo = movement.sable$getCollisionInfo();
            var previousTracking = movement.sable$getTrackingSubLevel();
            UUID previousFrame = previousTracking == null ? null : previousTracking.getUniqueId();
            var oldPoint = LoadedFrameRayQuery.currentActorPoint(level, human);
            Vec3 ownPreMinecraft = human.getDeltaMovement();
            MovementVector ownPreMps = new MovementVector(ownPreMinecraft.x * 20d,
                    ownPreMinecraft.y * 20d, ownPreMinecraft.z * 20d);
            require(frameA.engineId().equals(previousFrame) && oldPoint != null
                            && frameA.engineId().equals(oldPoint.frameId()),
                    "walking test remains on frame A until the sampled owner step changes frames");

            CharacterMovementState baseline = expectedClearGroundedWalk(human, 1000, 0);
            CharacterMovementState actual = new GroundedHarnessWorldStep().step(human, 1000, 0, false, false, 0f)
                    .orElseThrow(() -> new GameTestAssertException("single actual Human owner step rejected during A-to-B walk"));
            Object currentInfo = movement.sable$getCollisionInfo();
            UUID currentFrame = movement.sable$getTrackingSubLevel() == null
                    ? null : movement.sable$getTrackingSubLevel().getUniqueId();
            boolean fresh = currentInfo != null && currentInfo != previousInfo;
            if (fresh && frameB.engineId().equals(currentFrame) && frameA.engineId().equals(previousFrame)) {
                var newPoint = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(newPoint != null && frameB.engineId().equals(newPoint.frameId()),
                        "new native tracking UUID resolves through current loaded actor point");
                Vector3d oldPointVelocity = pointVelocity(level, frameA, oldPoint);
                Vector3d newPointVelocity = pointVelocity(level, frameB, newPoint);
                Vec3 bodyPostMinecraft = human.getDeltaMovement().scale(20);
                MovementVector baselineVelocity = baseline.velocity();
                double expectedX = baselineVelocity.x() + oldPointVelocity.x - newPointVelocity.x;
                double expectedZ = baselineVelocity.z() + oldPointVelocity.z - newPointVelocity.z;
                double error = Math.hypot(bodyPostMinecraft.x - expectedX, bodyPostMinecraft.z - expectedZ);
                Vector3d poseA = frameA.subLevel().logicalPose().position().get(new Vector3d());
                Vector3d poseB = frameB.subLevel().logicalPose().position().get(new Vector3d());
                System.out.println("FRAME_SWAP evidence freshInfo=" + fresh + " old=" + previousFrame + " new=" + currentFrame
                        + " ownPreBodyMps=" + ownPreMps + " baselineMotorMps=" + baselineVelocity
                        + " oldPointMps=" + oldPointVelocity + " newPointMps=" + newPointVelocity
                        + " expectedPostMps=" + expectedX + "," + expectedZ + " actualPostMps="
                        + bodyPostMinecraft.x + "," + bodyPostMinecraft.z + " errorMps=" + error
                        + " grounded=" + human.onGround() + " poseDeltaA=" + poseA.distance(initialPoseA)
                        + " poseDeltaB=" + poseB.distance(initialPoseB));
                require(Double.isFinite(oldPointVelocity.x) && Double.isFinite(oldPointVelocity.y)
                                && Double.isFinite(oldPointVelocity.z) && Double.isFinite(newPointVelocity.x)
                                && Double.isFinite(newPointVelocity.y) && Double.isFinite(newPointVelocity.z)
                                && Math.hypot(oldPointVelocity.x - newPointVelocity.x,
                                oldPointVelocity.z - newPointVelocity.z) > .05,
                        "direct frame swap has distinct, nonzero measured old/new native point velocity");
                require(error <= .02, "direct A-to-B swap did not preserve motor map-horizontal velocity; error="
                        + error + " m/s; baseline=" + baselineVelocity + " oldPoint=" + oldPointVelocity
                        + " newPoint=" + newPointVelocity + " actual=" + bodyPostMinecraft);
                require(human.onGround(), "A-to-B transition resolves live native deck support");
                require(poseA.distance(initialPoseA) > .01 && poseB.distance(initialPoseB) > .01,
                        "both independently allocated native frames physically moved during the transition");
                require(helper.getLevel().getEntity(rootId) == human && human.isAlive()
                                && human.playerCharacterBinding().equals(binding)
                                && human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands,
                        "same real Human, Mind binding, and hands survive native frame swap");
                Vec3 bodyPost = human.getDeltaMovement().scale(20);
                double committedError = Math.sqrt(Math.pow(actual.velocity().x() - bodyPost.x, 2)
                        + Math.pow(actual.velocity().y() - bodyPost.y, 2)
                        + Math.pow(actual.velocity().z() - bodyPost.z, 2));
                require(committedError <= .02,
                        "authoritative CharacterMovementState matches corrected body velocity in m/s");
                helper.succeed();
                return;
            }
            require(frameA.engineId().equals(previousFrame) && (currentFrame == null
                            || frameA.engineId().equals(currentFrame) || frameB.engineId().equals(currentFrame)),
                    "fixture observes direct A-to-B transition without a world/third-frame detour; pre="
                            + previousFrame + " post=" + currentFrame + " freshInfo=" + fresh);
            observeFrameSwap(helper, human, frameA, frameB, rootId, binding, hands, initialPoseA, initialPoseB, ticks + 1);
        });
    }

    private static CharacterMovementState expectedClearGroundedWalk(
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int wishX, int wishZ) {
        return expectedClearMotor(human, wishX, wishZ, false);
    }

    private static CharacterMovementState expectedClearMotor(
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            boolean jump) {
        return expectedClearMotor(human, 0, 0, jump);
    }

    private static CharacterMovementState expectedClearMotor(
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int wishX, int wishZ, boolean jump) {
        return expectedClearMotor(human, wishX, wishZ, jump, null);
    }

    private static CharacterMovementState expectedClearMotor(
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            int wishX, int wishZ, boolean jump, AirMotionReference expectedAirReference) {
        var data = CharacterIdentitySystem.resolveForActor(human).orElseThrow(
                () -> new GameTestAssertException("resolved Human movement prototype unavailable for baseline"));
        CharacterMovementPolicy policy = CharacterMovementPolicy.fromCharacterData(data);
        require(!com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(human)
                        && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canMove(human)
                        && !com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(human)
                        && !com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(human),
                "baseline isolates normal alive, upright grounded Human movement");
        Vec3 tetherImpulse = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                .deltaVelocityPerSecond(human);
        MovementVector tether = new MovementVector(tetherImpulse.x, tetherImpulse.y, tetherImpulse.z);
        Vec3 position = human.position();
        Vec3 velocity = human.getDeltaMovement();
        var state = new CharacterMovementState(new MovementVector(position.x, position.y, position.z),
                new MovementVector(velocity.x * 20d, velocity.y * 20d, velocity.z * 20d), human.onGround(), expectedAirReference);
        double surfaceFactor = com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem.frictionFactor(human);
        double voluntaryFactor = com.juicyslew.moonstation14.ms14.movement.PullMovementPolicy.voluntaryMovementFactor(
                com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isKnockedDown(human), false,
                com.juicyslew.moonstation14.ms14.standing.PostureSystem.crawlMultiplier(human),
                com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService.movementMultiplier(human, false));
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(
                com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS,
                com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND,
                com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 32d, human.maxUpStep(),
                (ignoredPosition, requested, wasOnGround) ->
                        new com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver.CollisionResult(
                                requested, wasOnGround && requested.y() < 0d),
                surfaceFactor, voluntaryFactor, tether);
        return new com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor(policy)
                .tick(state, wishX, wishZ, jump, false, 0f, environment, false, false);
    }

    private static void observeBoarding(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, UUID rootId, Object binding, Object hands, int ticks,
            Integer expectedZSign, boolean expectSpin, boolean requireUpwardFrameFloor) {
        if (ticks >= 24) throw new GameTestAssertException("no fresh world-to-native-frame boarding observed; frame="
                + plot.engineId() + ", body=" + human.position() + ", velocity=" + human.getDeltaMovement());
        helper.runAfterDelay(1, () -> {
            ServerLevel level = (ServerLevel) human.level();
            EntityMovementExtension movement = (EntityMovementExtension) (Object) human;
            Object beforeInfo = movement.sable$getCollisionInfo();
            UUID preFrame = movement.sable$getTrackingSubLevel() == null
                    ? null : movement.sable$getTrackingSubLevel().getUniqueId();
            Vec3 ownPre = human.getDeltaMovement().scale(20);
            AtomicReference<SableOwnedBodyMotion.MoveResult> observedMove = new AtomicReference<>();
            var step = (requireUpwardFrameFloor ? GroundedHarnessWorldStep.observingNativeGeometry()
                    : new GroundedHarnessWorldStep()).step(human, 0, 0, false, false, 0f,
                    requireUpwardFrameFloor ? observedMove::set : null)
                    .orElseThrow(() -> new GameTestAssertException("normal one-owner boarding motor tick rejected"));
            Object afterInfo = movement.sable$getCollisionInfo();
            UUID postFrame = movement.sable$getTrackingSubLevel() == null
                    ? null : movement.sable$getTrackingSubLevel().getUniqueId();
            if (preFrame == null && plot.engineId().equals(postFrame) && afterInfo != null && afterInfo != beforeInfo) {
                var actorPoint = LoadedFrameRayQuery.currentActorPoint(level, human);
                require(actorPoint != null && plot.engineId().equals(actorPoint.frameId()),
                        "boarded human resolves through exact currently loaded native frame point");
                var frame = plot.subLevel();
                Vector3d newPointVelocity = new Vector3d();
                Sable.HELPER.getVelocity(level, frame,
                        new Vector3d(actorPoint.storagePoint().x, actorPoint.storagePoint().y,
                                actorPoint.storagePoint().z), newPointVelocity);
                Vec3 bodyPost = human.getDeltaMovement().scale(20);
                double retainedMapX = bodyPost.x + newPointVelocity.x;
                double retainedMapZ = bodyPost.z + newPointVelocity.z;
                double error = Math.hypot(retainedMapX - ownPre.x, retainedMapZ - ownPre.z);
                System.out.println("FRAME_BOARD evidence freshInfo=true old=world new=" + postFrame
                        + " newPointMps=" + newPointVelocity + " ownPreMps=" + ownPre + " ownPostMps=" + bodyPost
                        + " bodyResultMps=" + step.velocity() + " retainedMapHorizontal=" + retainedMapX + "," + retainedMapZ
                        + " errorMps=" + error + " grounded=" + human.onGround());
                require(Double.isFinite(newPointVelocity.x) && Double.isFinite(newPointVelocity.y)
                                && Double.isFinite(newPointVelocity.z)
                                && Math.hypot(newPointVelocity.x, newPointVelocity.z) > .1,
                        "actual SDK native point velocity is nonzero; fixture is not a vacuous boarding gate");
                if (expectedZSign != null)
                    require(Math.abs(newPointVelocity.z) > 4 && Math.signum(newPointVelocity.z) == expectedZSign,
                            "five-m/s boarding must retain measured signed native Z point velocity: " + newPointVelocity);
                if (expectSpin) {
                    Vector3d angularAtContact = RigidBodyHandle.of(frame).getAngularVelocity(new Vector3d());
                    require(Math.abs(angularAtContact.y) > .5,
                            "spinning-deck angular Y remains meaningful at first boarding contact: " + angularAtContact);
                    require(Math.abs(newPointVelocity.x) + Math.abs(newPointVelocity.z) > .5,
                            "descending boarding must observe actual spinning-frame point motion: " + newPointVelocity);
                }
                if (requireUpwardFrameFloor) {
                    var move = observedMove.get();
                    var info = move == null ? null : move.collisionInfo();
                    var rows = move == null || move.collisionScope() == null ? null
                            : move.collisionScope().nativeCollisionTrace();
                    boolean sameFrameUpwardFloor = move != null && move.collisionInfoFresh()
                            && move.collisionScope() != null && move.collisionScope().nativeCollisionTraceComplete()
                            && move.collisionScope().geometryTraceComplete()
                            && rows.stream().anyMatch(row -> plot.engineId().equals(row.frameId())
                                    && row.verticalCollision() && row.selectedY() > 0d
                                    && row.selectedPos() != null
                                    && row.selectedPos().getY() == plot.storageOrigin().getY()
                                    && row.selectedState() != null && row.selectedState().is(Blocks.STONE));
                    require(sameFrameUpwardFloor && info != null && info.verticalCollision
                                    && info.verticalCollisionBelow && info.trackingSubLevel == plot.subLevel()
                                    && human.onGround(),
                            "rising WORLD-to-frame positive control requires fresh same-frame upward floor row and grounding; move="
                                    + (move == null ? null : OwnedNativeMoveDiagnostic.describe(human, move))
                                    + ", rows=" + rows);
                    require(newPointVelocity.y > 0d,
                            "same-frame positive-control point moves upward at actual boarding: " + newPointVelocity);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "[RISING_BOARDING_POSITIVE_CONTROL] pointVelocity={} rowCount={} rows={}",
                            newPointVelocity, rows.size(), rows);
                }
                require(error <= .02, "world-to-frame boarding failed to preserve horizontal map velocity: " + error + " m/s");
                require(human.onGround(), "boarding collision resolves actual deck support");
                require(helper.getLevel().getEntity(rootId) == human && human.isAlive()
                                && human.playerCharacterBinding().equals(binding)
                                && human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands,
                        "normal boarding preserves exact live Human, Mind binding, and hands owners");
                double resultError = Math.sqrt(Math.pow(step.velocity().x() - bodyPost.x, 2)
                        + Math.pow(step.velocity().y() - bodyPost.y, 2)
                        + Math.pow(step.velocity().z() - bodyPost.z, 2));
                require(resultError < .02,
                        "authoritative CharacterMovementState equals committed body velocity in m/s, error=" + resultError);
                helper.succeed();
                return;
            }
            require(preFrame == null || plot.engineId().equals(preFrame),
                    "boarding fixture never switches through an unexpected native frame");
            observeBoarding(helper, human, plot, rootId, binding, hands, ticks + 1, expectedZSign, expectSpin,
                    requireUpwardFrameFloor);
        });
    }

    @GameTestTags({"shuttle-frame-transition", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 120)
    public static void typedDamageCorpsePreservesVelocityOnAutomaticFrameDeparture(GameTestHelper helper) {
        BlockPos world = new BlockPos(1000, 300, 640);
        ServerLevel level = helper.getLevel();
        withDeck(helper, world, 8, (ignored, plot, storage) -> {
            var body = BoundHumanFixture.create(level, deckFeet(plot, storage, 2, 2), "sable-corpse-frame-transition");
            awaitNativeDeckSupport(helper, body, 30, () -> {
                require(body.onGround(), "bound human establishes real native deck support before typed death");
                var actorPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
                require(actorPoint != null && plot.engineId().equals(actorPoint.frameId()),
                        "bound body has exact loaded native plot tracking before corpse transition");
                UUID bodyId = body.getUUID();
                Object binding = body.playerCharacterBinding();
                Object hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(hands != null, "actual bound body has live hands ownership");
                CorpseDepartureMonitor monitor = new CorpseDepartureMonitor(level, plot, storage, body,
                        bodyId, binding, hands);
                monitor.register(helper);
                require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(body)
                                && OwnedCorpsePhysics.eligible(body),
                        "retained medical corpse is created by typed damage and remains eligible for its production owner");
                awaitCorpseMotorSettled(helper, body, plot, monitor, 0);
            });
        });
    }

    private static void awaitCorpseMotorSettled(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity corpse,
            SableFixturePlot plot, CorpseDepartureMonitor monitor, int ticks) {
        if (ticks >= 30) throw new GameTestAssertException("typed corpse did not settle on its native deck before frame nudge; "
                + corpse.position() + " vMps=" + corpse.getDeltaMovement().scale(20));
        helper.runAfterDelay(1, () -> {
            var movement = (EntityMovementExtension) (Object) corpse;
            if (corpse.onGround() && movement.sable$getTrackingSubLevel() != null
                    && plot.engineId().equals(movement.sable$getTrackingSubLevel().getUniqueId())
                    && corpse.getDeltaMovement().horizontalDistance() * 20d < .02) {
                // Only the native deck receives this one-shot motion; CorpsePhysics owns all body ticks.
                RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(new Vector3d(2, 0, 2), new Vector3d());
                awaitCorpseTransition(helper, monitor, 0);
                return;
            }
            awaitCorpseMotorSettled(helper, corpse, plot, monitor, ticks + 1);
        });
    }

    private static void awaitCorpseTransition(GameTestHelper helper, CorpseDepartureMonitor monitor, int ticks) {
        if (ticks >= 80) throw new GameTestAssertException("automatic medical corpse did not depart and land; " + monitor.summary());
        helper.runAfterDelay(1, () -> {
            if (monitor.landed()) {
                monitor.assertIdentityAndHealth();
                helper.succeed();
                return;
            }
            awaitCorpseTransition(helper, monitor, ticks + 1);
        });
    }

    private static final class CorpseDepartureMonitor {
        private final ServerLevel level;
        private final SableFixturePlot plot;
        private final BlockPos storageOrigin;
        private final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity corpse;
        private final UUID bodyId;
        private final Object binding;
        private final Object hands;
        private GameTestHelper helper;
        private final AtomicBoolean deckOpened = new AtomicBoolean();
        private final AtomicReference<CorpsePreSample> pending = new AtomicReference<>();
        private final AtomicReference<CorpseTransitionEvidence> departure = new AtomicReference<>();
        private volatile boolean landed;
        private volatile int verifiedAirborneTicks;
        private Consumer<EntityTickEvent.Pre> beforeListener;
        private Consumer<EntityTickEvent.Post> afterListener;
        private boolean registered;

        private CorpseDepartureMonitor(ServerLevel level, SableFixturePlot plot, BlockPos storageOrigin,
                                       com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity corpse,
                                       UUID bodyId, Object binding, Object hands) {
            this.level = level; this.plot = plot; this.storageOrigin = storageOrigin; this.corpse = corpse;
            this.bodyId = bodyId; this.binding = binding; this.hands = hands;
        }

        void register(GameTestHelper helper) {
            this.helper = helper;
            beforeListener = event -> before(event);
            afterListener = event -> after(event);
            NeoForge.EVENT_BUS.addListener(beforeListener);
            NeoForge.EVENT_BUS.addListener(afterListener);
            registered = true;
            helper.testInfo.addListener(new GameTestListener() {
                private void close() { unregister(); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
            });
        }

        private void before(EntityTickEvent.Pre event) {
            try { beforeObserved(event); }
            catch (GameTestAssertException failure) { failOwnedTest(failure); }
        }

        private void beforeObserved(EntityTickEvent.Pre event) {
            if (event.getEntity() != corpse || corpse.level() != level || corpse.isRemoved()) return;
            var movement = (EntityMovementExtension) (Object) corpse;
            Object collisionInfo = movement.sable$getCollisionInfo();
            var sub = movement.sable$getTrackingSubLevel();
            if (departure.get() != null && sub == null) {
                pending.set(new CorpsePreSample(level.getGameTime(), collisionInfo, null, new Vector3d(),
                        corpse.getDeltaMovement().scale(20), corpse.position(), corpse.onGround()));
                return;
            }
            if (sub == null || !plot.engineId().equals(sub.getUniqueId()) || !(sub instanceof ServerSubLevel frame)
                    || frame.isRemoved() || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame) return;
            LoadedFrameRayQuery.ActorPoint point = LoadedFrameRayQuery.currentActorPoint(level, corpse);
            if (point == null || !plot.engineId().equals(point.frameId())) return;
            Vector3d carrier = new Vector3d();
            Sable.HELPER.getVelocity(level, frame,
                    new Vector3d(point.storagePoint().x, point.storagePoint().y, point.storagePoint().z), carrier);
            if (!Double.isFinite(carrier.x) || !Double.isFinite(carrier.y) || !Double.isFinite(carrier.z)
                    || Math.hypot(carrier.x, carrier.z) <= .1) return;

            BlockPos feetStorage = BlockPos.containing(point.storagePoint());
            BlockPos floorCenter = null;
            CorpsePreSample sample = new CorpsePreSample(level.getGameTime(), collisionInfo, frame.getUniqueId(),
                    carrier, corpse.getDeltaMovement().scale(20), corpse.position(), corpse.onGround());
            pending.set(sample);
            if (deckOpened.compareAndSet(false, true)) {
                for (int offset = -2; offset <= 1; offset++) {
                    BlockPos candidate = feetStorage.offset(0, offset, 0);
                    if (level.getBlockState(candidate).is(Blocks.STONE)) { floorCenter = candidate; break; }
                }
                if (floorCenter == null) { deckOpened.set(false); pending.set(null); return; }
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    BlockPos remove = floorCenter.offset(x, 0, z);
                    if (!level.getBlockState(remove).is(Blocks.STONE)) continue;
                    plot.setStorageBlock(remove, Blocks.AIR.defaultBlockState());
                    if (!level.getBlockState(remove).isAir())
                        throw new GameTestAssertException("owned plot failed normal deck-block removal at " + remove
                                + " state=" + level.getBlockState(remove));
                }
                System.out.println("CORPSE_FRAME pre gameTime=" + sample.gameTime + " tracking=" + sample.frameId
                        + " carrierMps=" + sample.carrierMps + " ownPreMps=" + sample.ownVelocityMps
                        + " collisionRef=" + System.identityHashCode(sample.collisionInfo) + " supportRemoved=" + floorCenter);
            }
        }

        private void after(EntityTickEvent.Post event) {
            try { afterObserved(event); }
            catch (GameTestAssertException failure) { failOwnedTest(failure); }
        }

        private void afterObserved(EntityTickEvent.Post event) {
            if (event.getEntity() != corpse || corpse.level() != level || corpse.isRemoved()) return;
            CorpsePreSample sample = pending.get();
            if (sample != null && sample.gameTime == level.getGameTime() && departure.get() == null) {
                var movement = (EntityMovementExtension) (Object) corpse;
                Object collisionInfo = movement.sable$getCollisionInfo();
                UUID postFrame = movement.sable$getTrackingSubLevel() == null
                        ? null : movement.sable$getTrackingSubLevel().getUniqueId();
                if (collisionInfo != sample.collisionInfo || !sample.frameId.equals(postFrame)) {
                    Vec3 ownPost = corpse.getDeltaMovement().scale(20);
                    double expectedX = sample.ownVelocityMps.x + sample.carrierMps.x;
                    double expectedZ = sample.ownVelocityMps.z + sample.carrierMps.z;
                    double error = Math.hypot(ownPost.x - expectedX, ownPost.z - expectedZ);
                    if (collisionInfo != sample.collisionInfo && postFrame == null) {
                        CorpseTransitionEvidence evidence = new CorpseTransitionEvidence(sample, collisionInfo, postFrame,
                                ownPost, error);
                        departure.set(evidence);
                        System.out.println("CORPSE_FRAME departure preTime=" + sample.gameTime + " postTime=" + level.getGameTime()
                                + " freshInfo=true old=" + sample.frameId + " new=null oldCarrierMps=" + sample.carrierMps
                                + " ownPreMps=" + sample.ownVelocityMps + " ownPostMps=" + ownPost
                                + " horizontalErrorMps=" + error);
                        require(error <= .02, "automatic corpse map-horizontal velocity mismatch=" + error + " m/s");
                    }
                }
                pending.compareAndSet(sample, null);
            }
            if (sample != null && sample.gameTime == level.getGameTime() && departure.get() != null
                    && sample.frameId == null) {
                UUID postFrame = ((EntityMovementExtension) (Object) corpse).sable$getTrackingSubLevel() == null ? null
                        : ((EntityMovementExtension) (Object) corpse).sable$getTrackingSubLevel().getUniqueId();
                require(postFrame == null, "corpse remains in world frame after departure until landing");
                if (!sample.onGround && !corpse.onGround()) {
                    Vec3 ownPost = corpse.getDeltaMovement().scale(20);
                    double expectedX = sample.ownVelocityMps.x * GroundedHarnessPhysics.AIR_HORIZONTAL_RETENTION;
                    double expectedZ = sample.ownVelocityMps.z * GroundedHarnessPhysics.AIR_HORIZONTAL_RETENTION;
                    double error = Math.hypot(ownPost.x - expectedX, ownPost.z - expectedZ);
                    System.out.println("CORPSE_FRAME airborne tick=" + sample.gameTime + " ownPreMps="
                            + sample.ownVelocityMps + " ownPostMps=" + ownPost + " expectedAirPostMps=("
                            + expectedX + "," + ownPost.y + "," + expectedZ + ") residualMps=" + error);
                    require(error <= .1, "airborne world-frame owner tick differs from ordinary horizontal air retention: " + error + " m/s");
                    verifiedAirborneTicks++;
                }
                pending.compareAndSet(sample, null);
            }
            if (departure.get() != null && corpse.onGround()
                    && ((EntityMovementExtension) (Object) corpse).sable$getTrackingSubLevel() == null) {
                BlockPos below = BlockPos.containing(corpse.getX(), corpse.getY() - .05, corpse.getZ());
                if (!level.getBlockState(below).isAir()) landed = true;
            }
        }

        boolean landed() { return landed; }

        void assertIdentityAndHealth() {
            require(departure.get() != null && landed && verifiedAirborneTicks > 0,
                    "automatic corpse departure, one untouched airborne tick, and ordinary-world landing observed");
            require(level.getEntity(bodyId) == corpse && MobStateSystem.isDead(corpse)
                            && OwnedCorpsePhysics.eligible(corpse)
                            && corpse.playerCharacterBinding().equals(binding)
                            && corpse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands,
                    "medical corpse remains same typed dead body with Mind/binding/hands through landing");
        }

        String summary() {
            return "deckOpened=" + deckOpened.get() + ", departure=" + departure.get() + ", airborneTicks=" + verifiedAirborneTicks + ", landed=" + landed
                    + ", corpsePresent=" + (level.getEntity(bodyId) == corpse);
        }

        private void unregister() {
            if (!registered) return;
            registered = false;
            NeoForge.EVENT_BUS.unregister(beforeListener);
            NeoForge.EVENT_BUS.unregister(afterListener);
        }

        private void failOwnedTest(Throwable failure) {
            unregister();
            if (helper != null && !helper.testInfo.isDone()) helper.testInfo.fail(failure);
        }
    }

    private record CorpsePreSample(long gameTime, Object collisionInfo, UUID frameId, Vector3d carrierMps,
                                   Vec3 ownVelocityMps, Vec3 worldPosition, boolean onGround) { }
    private record CorpseTransitionEvidence(CorpsePreSample pre, Object collisionInfo, UUID postFrame,
                                             Vec3 ownPostMps, double horizontalErrorMps) { }

    private static void observeDeparture(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            UUID oldFrame, UUID rootId, Object binding, Object hands, boolean requireAngularPoint, int ticks) {
        if (ticks >= 24) throw new GameTestAssertException("no fresh supported-frame departure observed within bound; frame=" + oldFrame);
        helper.runAfterDelay(1, () -> {
            EntityMovementExtension movement = (EntityMovementExtension) (Object) human;
            Object before = movement.sable$getCollisionInfo();
            var preSub = movement.sable$getTrackingSubLevel();
            UUID pre = preSub == null ? null : preSub.getUniqueId();
            Vec3 ownPre = human.getDeltaMovement().scale(20);
            Vector3d oldCarrier = null;
            Vector3d nativeLinear = null;
            Vector3d nativeAngular = null;
            LoadedFrameRayQuery.ActorPoint actorPoint = LoadedFrameRayQuery.currentActorPoint(
                    (ServerLevel) human.level(), human);
            dev.ryanhcode.sable.sublevel.SubLevel oldSub = null;
            if (oldFrame.equals(pre) && preSub instanceof ServerSubLevel serverSub
                    && !serverSub.isRemoved()
                    && dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(human.level()).getSubLevel(oldFrame) == serverSub
                    && actorPoint != null && oldFrame.equals(actorPoint.frameId())) {
                oldSub = serverSub;
                oldCarrier = new Vector3d();
                Vector3d storagePoint = new Vector3d(actorPoint.storagePoint().x,
                        actorPoint.storagePoint().y, actorPoint.storagePoint().z);
                Sable.HELPER.getVelocity(human.level(), serverSub, storagePoint, oldCarrier);
                var handle = RigidBodyHandle.of(serverSub);
                nativeLinear = handle.getLinearVelocity(new Vector3d());
                nativeAngular = handle.getAngularVelocity(new Vector3d());
            }
            boolean angularPointNonVacuous = oldCarrier != null && nativeLinear != null && nativeAngular != null
                    && nativeAngular.length() > .1 && Math.hypot(oldCarrier.x - nativeLinear.x,
                    oldCarrier.z - nativeLinear.z) > .05;
            boolean jumpNow = !requireAngularPoint || angularPointNonVacuous;
            // Rotation must be measurable at the point before the single actual jump owner step.
            var step = new GroundedHarnessWorldStep().step(human, 0, 0, jumpNow, false, 0f)
                    .orElseThrow(() -> new GameTestAssertException("owned transition step rejected"));
            Object after = movement.sable$getCollisionInfo();
            UUID post = movement.sable$getTrackingSubLevel() == null ? null : movement.sable$getTrackingSubLevel().getUniqueId();
            if (after != before && oldFrame.equals(pre) && post == null && oldCarrier != null && oldSub != null) {
                var info = (SubLevelEntityCollision.CollisionInfo) after;
                Vec3 ownPost = human.getDeltaMovement().scale(20);
                double expectedX = ownPre.x + oldCarrier.x;
                double expectedZ = ownPre.z + oldCarrier.z;
                double error = Math.hypot(ownPost.x - expectedX, ownPost.z - expectedZ);
                System.out.println("FRAME_TRANSITION evidence old=" + oldFrame + " new=null freshInfo=true nativeLinearMps="
                        + nativeLinear + " nativeAngularRadPerSec=" + nativeAngular + " oldPointMps=" + oldCarrier
                        + " pointVsCenterHorizontal=" + (nativeLinear == null ? "unknown"
                        : Math.hypot(oldCarrier.x - nativeLinear.x, oldCarrier.z - nativeLinear.z)) + " infoTracking="
                        + info.trackingSubLevel + " oldCarrierMps=" + oldCarrier + " ownPreMps="
                        + ownPre + " ownPostMps=" + ownPost + " expectedHorizontal=" + expectedX + "," + expectedZ
                        + " errorMps=" + error + " ownerResult=" + step.velocity());
                require(error <= .02, "map-horizontal velocity continuity mismatch=" + error + " m/s; expected="
                        + expectedX + "," + expectedZ + " actual=" + ownPost.x + "," + ownPost.z);
                require(Math.hypot(ownPost.x, ownPost.z) > .1,
                        "jump departure retains measurable inherited horizontal velocity");
                if (requireAngularPoint) require(angularPointNonVacuous,
                        "actual SDK point velocity includes a nonzero angular contribution distinct from COM velocity");
                require(Math.hypot(step.velocity().x() - ownPost.x, step.velocity().z() - ownPost.z) < .02,
                        "authoritative motor result reports corrected velocity in m/s");
                verifyDepartureAndLanding(helper, human, oldFrame, rootId, binding, hands, expectedX, expectedZ, 0);
                return;
            }
            observeDeparture(helper, human, oldFrame, rootId, binding, hands, requireAngularPoint, ticks + 1);
        });
    }

    private static void verifyDepartureAndLanding(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            UUID oldFrame, UUID rootId, Object binding, Object hands, double expectedX, double expectedZ, int ticks) {
        if (ticks >= 60) throw new GameTestAssertException("departed human did not land on the bounded world-floor corridor");
        helper.runAfterDelay(1, () -> {
            var movement = (EntityMovementExtension) (Object) human;
            Object collisionBefore = movement.sable$getCollisionInfo();
            var old = movement.sable$getTrackingSubLevel();
            if (old != null && old.getUniqueId().equals(oldFrame)) {
                // The same frame's later disappearance may not reapply the transition delta.
            }
            var state = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f)
                    .orElseThrow(() -> new GameTestAssertException("owned post-jump motor step rejected"));
            var actual = human.getDeltaMovement().scale(20);
            UUID postFrame = movement.sable$getTrackingSubLevel() == null ? null : movement.sable$getTrackingSubLevel().getUniqueId();
            System.out.println("FRAME_TRANSITION follow tick=" + ticks + " fresh=" + (movement.sable$getCollisionInfo() != collisionBefore)
                    + " pre=" + (old == null ? null : old.getUniqueId()) + " post=" + postFrame + " velocity=" + actual);
            require(movement.sable$getCollisionInfo() != collisionBefore,
                    "each scripted airborne step has fresh native collision evidence");
            require(Math.hypot(actual.x, actual.z) < 12,
                    "airborne map velocity remains bounded; a one-shot frame delta must not replay every tick; actual=" + actual);
            if (human.onGround()) {
                require(movement.sable$getCollisionInfo() != collisionBefore,
                        "landing step has fresh native collision evidence");
                require(movement.sable$getTrackingSubLevel() != null
                                && movement.sable$getTrackingSubLevel().getUniqueId().equals(oldFrame),
                        "human lands with current tracking on the actual moving native deck");
                require(helper.getLevel().getEntity(rootId) == human
                                && human.playerCharacterBinding().equals(binding)
                                && human.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == hands,
                        "departure and landing preserve exact bound body, Mind, and hands identities");
                require(Math.hypot(state.velocity().x() - actual.x, state.velocity().z() - actual.z) < .02,
                        "landed authoritative state retains corrected motor velocity");
                helper.succeed(); return;
            }
            require(old == null && postFrame == null,
                    "post-departure airborne samples remain in world frame until actual landing");
            verifyDepartureAndLanding(helper, human, oldFrame, rootId, binding, hands, expectedX, expectedZ, ticks + 1);
        });
    }

    private static void withDeck(GameTestHelper helper, BlockPos world, DeckWork work) {
        withDeck(helper, world, 0, 1, 40, work);
    }

    private static void withDeck(GameTestHelper helper, BlockPos world, int groundCorridor, DeckWork work) {
        withDeck(helper, world, groundCorridor, 1, 40, work);
    }

    private static void withDeck(GameTestHelper helper, BlockPos world, int groundCorridor, int sceneHeight, DeckWork work) {
        withDeck(helper, world, groundCorridor, sceneHeight, SableFixtureScene.READINESS_TICKS, work);
    }

    private static void withDeck(GameTestHelper helper, BlockPos world, int groundCorridor, int sceneHeight,
            int readinessTicks, DeckWork work) {
        withDeck(helper, world, groundCorridor, sceneHeight, readinessTicks,
                (level, plot, storage, scene) -> work.run(level, plot, storage));
    }

    private static void withDeck(GameTestHelper helper, BlockPos world, int groundCorridor, int sceneHeight,
            int readinessTicks, DeckSceneWork work) {
        ServerLevel level = helper.getLevel();
        BlockPos sceneOrigin = world.offset(-groundCorridor, 0, -groundCorridor);
        int sceneSize = DECK_SIZE + groundCorridor * 2;
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, sceneSize, sceneHeight, sceneSize);
        SableFixtureScene.awaitEmpty(helper, scene, readinessTicks, () -> {
            try {
                if (groundCorridor > 0) SableFixtureScene.authorStoneSupportPad(level, scene);
                var authored = allocateDeck(level, new Vector3d(world.getX() + .5, world.getY(), world.getZ() + .5));
                SableFixtureScene.trackPlot(helper, authored.plot());
                work.run(level, authored.plot(), authored.result(), scene);
            } catch (RuntimeException | Error failure) {
                throw failure;
            }
        });
    }

    private static void withDeckPair(GameTestHelper helper, BlockPos world, double gap, DeckPairWork work) {
        if (!Double.isFinite(gap) || gap < .15 || gap > .25) throw new IllegalArgumentException("frame gap must be 0.15-0.25 blocks");
        ServerLevel level = helper.getLevel();
        int margin = 8;
        int width = DECK_SIZE * 2 + (int) Math.ceil(gap) + margin * 2;
        int depth = DECK_SIZE + margin * 2;
        BlockPos sceneOrigin = world.offset(-margin, 0, -margin);
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, sceneOrigin, width, 1, depth);
        SableFixtureScene.awaitEmpty(helper, scene, 40, () -> {
            try {
                SableFixtureScene.authorStoneSupportPad(level, scene);
                var authoredA = allocateDeck(level,
                        new Vector3d(world.getX() + .5, world.getY(), world.getZ() + .5));
                SableFixtureScene.trackPlot(helper, authoredA.plot());
                var authoredB = allocateDeck(level, new Vector3d(world.getX() + DECK_SIZE + gap + .5,
                        world.getY(), world.getZ() + .5));
                SableFixtureScene.trackPlot(helper, authoredB.plot());
                work.run(authoredA.plot(), authoredA.result(), authoredB.plot(), authoredB.result());
            } catch (RuntimeException | Error failure) {
                throw failure;
            }
        });
    }

    private static SableFixturePlot.Authored<BlockPos> allocateDeck(ServerLevel level, Vector3d worldCenter) {
        return SableFixturePlot.allocateAndAuthor(level, worldCenter, building -> {
            BlockPos origin = building.storageOrigin();
            building.ensureStorageFootprint(origin, DECK_SIZE, 1, DECK_SIZE);
            for (int x = 0; x < DECK_SIZE; x++) for (int z = 0; z < DECK_SIZE; z++)
                if (!building.setStorageBlock(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState()))
                    throw new IllegalStateException("native body-carry deck block write failed");
            return origin;
        });
    }

    @GameTestTags({"shuttle-rotating-jump-regression", "shuttle-frame-transition", "shuttle"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 200)
    public static void boundHumanRotatingJumpLaunchesBallistically(GameTestHelper helper) {
        // Keep this scene separate from the concurrently selected moving-away boarding fixture.
        BlockPos world = new BlockPos(1480, 300, 2200);
        ServerLevel level = helper.getLevel();
        withDeck(helper, world, 8, 1, SLOW_NATIVE_SCENE_READY_TICKS, (ignored, plot, storage) -> {
            var human = BoundHumanFixture.create(level, deckFeet(plot, storage, 1, 2), "sable-rotating-jump-flight");
            awaitNativeDeckSupport(helper, human, plot, storage, 30, () -> {
                require(human.onGround(), "jump fixture settles on real native support before carrier motion");
                var movement = (EntityMovementExtension) (Object) human;
                ServerSubLevel frame = (ServerSubLevel) movement.sable$getTrackingSubLevel();
                require(frame != null && plot.engineId().equals(frame.getUniqueId()),
                        "settled jumper has exact native frame tracking");
                RigidBodyHandle.of(plot.subLevel()).addLinearAndAngularVelocity(
                        new Vector3d(.5, 0, .5), new Vector3d(0, 2, 0));
                helper.runAfterDelay(1, () -> launchRotatingJump(helper, human, plot, storage, frame, 0));
            });
        });
    }

    @GameTestTags({"shuttle-rolled-support-cadence"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void ownedHumanRemainsSupportedThroughPhysicalHullRollAndBrake(GameTestHelper helper) {
        withDeck(helper, new BlockPos(2120, 300, 640), 8, (level, plot, storage) -> {
            HullImpactSubstepsExpectation.requireNormalSix(level, "owned-rolled-support-cadence");
            var human = BoundHumanFixture.create(level, deckFeet(plot, storage, 2, 2), "rolled-owned-human-support");
            awaitNativeDeckSupport(helper, human, plot, storage, 30, () -> {
                require(human.onGround(), "human naturally settles on authored deck before hull motion");
                var body = RigidBodyHandle.of(plot.subLevel());
                var mass = plot.subLevel().getMassTracker();
                require(mass != null && mass.getMass() > 0 && mass.getInertiaTensor() != null,
                        "resolved merged hull mass and inertia are available");
                var pose = plot.subLevel().logicalPose();
                // RigidBodyHandle impulse vectors are both local; rotate desired world-up lift into body space.
                var liftLocal = pose.orientation().transformInverse(new Vector3d(0, mass.getMass() * 12.0, 0));
                var rollLocal = mass.getInertiaTensor().transform(new Vector3d(0, 0, 3.0));
                body.applyLinearAndAngularImpulse(liftLocal, rollLocal);
                helper.runAfterDelay(1, () -> rolledSupportSample(helper, level, plot, storage, human,
                        body, mass.getInertiaTensor(), human.getUUID(), human.playerCharacterBinding().toString(),
                        0, 0, 0, 0, Double.NaN, Double.NaN, null, 0, 0));
            });
        });
    }

    private static void rolledSupportSample(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            BlockPos storage, com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            RigidBodyHandle body, org.joml.Matrix3dc inertia, UUID humanId, String bindingId, int tick, int totalFloors,
            int tiltedFloors, int postBrakeFloors, double beforeBrakeTilt, double postBrakeTilt,
            Vector3d previousDeckPosition, double postBrakeTangentDrift, int suppressionSamples) {
        if (tick >= 16) throw new GameTestAssertException("rolled support probe timed out tick=" + tick + " floors=" + totalFloors);
        SableOwnedBodyMotion.MoveResult[] observed = {null};
        var stepped = new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f, value -> observed[0] = value);
        var move = observed[0];
        var scope = move == null ? null : move.collisionScope();
        var info = move == null ? null : move.collisionInfo();
        var orientation = plot.subLevel().logicalPose().orientation();
        var up = orientation.transform(new Vector3d(0, 1, 0));
        double tilt = up.angle(new Vector3d(0, 1, 0));
        var omega = body.getAngularVelocity(new Vector3d());
        var trace = scope == null ? null : scope.nativeCollisionTrace();
        boolean fresh = move != null && move.collisionInfoFresh() && info != null && scope != null;
        boolean complete = fresh && scope.nativeCollisionTraceComplete() && scope.geometryTraceComplete()
                && trace != null && !trace.isEmpty() && info.motion != null;
        boolean knownFloor = complete && plot.engineId().equals(info.trackingSubLevel == null ? null
                : info.trackingSubLevel.getUniqueId()) && SableOwnedBodyMotion.nativeFloorTraceAdmissible(
                move.requestedOwnDisplacement(), info.motion.x, info.motion.y, info.motion.z, trace,
                plot.engineId(), row -> {
                    if (row.selectedPos() == null || row.selectedState() == null || row.geometry() == null
                            || row.selectedPos().getY() != storage.getY()
                            || row.selectedPos().getX() < storage.getX() || row.selectedPos().getX() >= storage.getX() + DECK_SIZE
                            || row.selectedPos().getZ() < storage.getZ() || row.selectedPos().getZ() >= storage.getZ() + DECK_SIZE) return false;
                    var actual = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), row.selectedPos());
                    return actual != null && actual.equals(row.selectedState()) && actual.is(Blocks.STONE);
                });
        var beforePoint = move == null ? null : move.beforePoint();
        var afterPoint = move == null ? null : move.afterPoint();
        var livePoint = LoadedFrameRayQuery.currentActorPoint(level, human);
        boolean ownership = beforePoint != null && afterPoint != null && livePoint != null
                && plot.engineId().equals(beforePoint.frameId()) && plot.engineId().equals(afterPoint.frameId())
                && plot.engineId().equals(livePoint.frameId());
        int totalNext = totalFloors + (knownFloor ? 1 : 0);
        int tiltedNext = tiltedFloors + (knownFloor && tilt >= Math.toRadians(30) ? 1 : 0);
        int postNext = postBrakeFloors + (knownFloor && tick > 4 ? 1 : 0);
        // Compare the physical logical body pose in the authored deck's moving coordinates. This
        // removes real hull translation/rotation (including its point carry), without trusting the
        // movement report. Local Y is deliberately excluded: upright body bounds can receive
        // legitimate normal/height correction on a tilted deck.
        Vector3d deckPosition = plot.subLevel().logicalPose().transformPositionInverse(
                new Vector3d(human.getX(), human.getY(), human.getZ()));
        double nextTangentDrift = postBrakeTangentDrift;
        if (tick > 4 && previousDeckPosition != null) {
            double tangentStep = Math.hypot(deckPosition.x - previousDeckPosition.x,
                    deckPosition.z - previousDeckPosition.z);
            nextTangentDrift += tangentStep;
            require(tangentStep <= .002 && nextTangentDrift <= .002,
                    "neutral post-brake physical pose has bounded cumulative deck-tangent drift; step="
                            + tangentStep + " cumulative=" + nextTangentDrift);
        }

        int suppressionNext = suppressionSamples;
        var suppressed = move == null ? null : move.idleProbeSuppressionDisplacement();
        if (suppressed != null && Math.hypot(Math.hypot(suppressed.x(), suppressed.y()), suppressed.z())
                > NativeCollisionSlop.MAX_RESIDUAL) {
            suppressionNext++;
            var raw = move.measuredOwnDisplacement();
            require(tick > 4 && move.idleSupportProbeAttested() && move.idleSupportProbeCertified()
                            && raw != null && raw.y() < 0
                            && move.boundedOwnDisplacement().equals(MovementVector.ZERO)
                            && move.positionCorrection() == null
                            && Math.hypot(Math.hypot(suppressed.x() + raw.x(), suppressed.y() + raw.y()),
                                    suppressed.z() + raw.z()) <= NativeCollisionSlop.MAX_RESIDUAL,
                    "physical idle-probe suppression is certified, excludes raw probe, and retains actual carry; move=" + move);
        }
        System.out.println("ROLLED_SUPPORT tick=" + tick + " ground=" + human.onGround() + " frame="
                + (move == null || move.afterPoint() == null ? null : move.afterPoint().frameId()) + " point="
                + afterPoint + " q=" + orientation + " omega=" + omega
                + " tilt=" + tilt + " knownFloor=" + knownFloor + " fresh=" + fresh + " complete=" + complete
                + " ownership=" + ownership + " stepped=" + stepped.isPresent() + " counts=" + totalNext + "/" + tiltedNext + "/" + postNext
                + " rows=" + (trace == null ? 0 : trace.size()) + " pos=" + human.position() + " deckDrift="
                + nextTangentDrift + " suppression=" + suppressed + " suppressionCount=" + suppressionNext
                + " boundedOwn=" + (move == null ? null : move.boundedOwnDisplacement()) + " idleAttested="
                + (move != null && move.idleSupportProbeAttested()) + " idleCertified="
                + (move != null && move.idleSupportProbeCertified()) + " request="
                + (move == null ? null : move.requestedOwnDisplacement()) + " native=" + (info == null ? null : info.motion)
                + " inherited=" + (info == null ? null : info.inheritedMotion) + " traceHead="
                + (trace == null ? "none" : trace.stream().limit(4).toList()));
        require(!human.isRemoved() && humanId.equals(human.getUUID())
                        && bindingId.equals(human.playerCharacterBinding().toString()),
                "same live bound human identity persists across lifecycle samples");
        if (knownFloor) {
            require(stepped.isPresent(), "known floor sample has accepted normal owner step");
            require(ownership, "known floor sample preserves exact tracked native frame at before/after/live points");
            require(human.onGround(), "independently authored native floor trace agrees with grounded result");
        }
        if (tick == 4) {
            require(tilt >= Math.toRadians(30), "physical roll achieved meaningful measured tilt before brake: " + tilt);
            require(tiltedNext >= 1, "at least one independently known floor sample occurs at measured tilt >=30 degrees");
            var localOmega = orientation.transformInverse(new Vector3d(omega));
            var counter = inertia.transform(localOmega.negate());
            body.applyLinearAndAngularImpulse(new Vector3d(), counter);
        }
        if (tick >= 7) {
            require(beforeBrakeTilt >= Math.toRadians(30), "roll phase was nonvacuous before braking");
            require(omega.length() < .25, "physical counter-impulse stopped measured roll rate: " + omega);
            require(Math.abs(tilt - postBrakeTilt) < Math.toRadians(15), "measured hull tilt remains stable after physical brake");
            require(postNext >= 2, "at least two independently known authored-floor samples after brake");
            require(suppressionNext >= 1, "at least one post-brake sample exercises certified physical idle-probe suppression");
        }
        if (tick >= 11) {
            require(totalNext >= 4, "at least four independent authored-floor samples across roll lifecycle");
            helper.succeed();
        } else {
            final double nextBeforeBrakeTilt = tick == 4 ? tilt : beforeBrakeTilt;
            final double nextPostBrakeTilt = tick == 4 ? tilt : postBrakeTilt;
            final double scheduledTangentDrift = nextTangentDrift;
            final int scheduledSuppressionSamples = suppressionNext;
            helper.runAfterDelay(1, () -> rolledSupportSample(helper, level, plot, storage, human,
                    body, inertia, humanId, bindingId, tick + 1, totalNext, tiltedNext, postNext,
                    nextBeforeBrakeTilt, nextPostBrakeTilt, deckPosition, scheduledTangentDrift, scheduledSuppressionSamples));
        }
    }

    private static void launchRotatingJump(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage, ServerSubLevel frame, int tick) {
        if (tick >= 12) throw new GameTestAssertException("rotating jump launch did not detach in bounded attempts");
        helper.runAfterDelay(1, () -> {
            ServerLevel level = (ServerLevel) human.level();
            require(human.onGround(), "first launch attempt is truly grounded, not a head-contact/rejected airborne jump");
            var beforePoint = LoadedFrameRayQuery.currentActorPoint(level, human);
            require(beforePoint != null && frame.getUniqueId().equals(beforePoint.frameId()),
                    "capture exact frame actor point immediately before grounded motor jump");
            Vec3 carrier = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity
                    .pointVelocityAt(level, beforePoint);
            require(carrier != null, "SDK frame point velocity resolves at launch");
            Vector3d launchStoragePoint = new Vector3d(beforePoint.storagePoint().x,
                    beforePoint.storagePoint().y, beforePoint.storagePoint().z);
            Vector3d omega = RigidBodyHandle.of(frame).getAngularVelocity(new Vector3d());
            Vector3d com = RigidBodyHandle.of(frame).getLinearVelocity(new Vector3d());
            require(Math.hypot(carrier.x - com.x, carrier.z - com.z) > .05 && Math.abs(omega.y) > .1,
                    "launch carrier point velocity contains meaningful measured angular contribution");

            CharacterMovementState motor = expectedClearMotor(human, true);
            Vec3 beforePosition = human.position();
            var movement = (EntityMovementExtension) (Object) human;
            Object previousInfo = movement.sable$getCollisionInfo();
            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult[] observed = {null};
            var accepted = new GroundedHarnessWorldStep().step(human, 0, 0, true, false, 0f, value -> observed[0] = value)
                    .orElseThrow(() -> new GameTestAssertException("real grounded motor jump step rejected"));
            var launchInfo = (SubLevelEntityCollision.CollisionInfo) movement.sable$getCollisionInfo();
            require(observed[0] != null && observed[0].collisionInfoFresh()
                            && observed[0].collisionInfo() == launchInfo && launchInfo != previousInfo
                            && launchInfo.motion != null && launchInfo.motion.y() > 0,
                    "fresh native move confirms positive upward launch, rather than head contact or rejected jump");
            require(observed[0].beforePoint() != null && frame.getUniqueId().equals(observed[0].beforePoint().frameId()),
                    "observed exact first jump native move begins on carrier frame");
            require(observed[0].afterPoint() != null && observed[0].afterPoint().frameId() == null,
                    "fresh airborne launch move records departure into ordinary world");
            Vec3 actualVelocity = human.getDeltaMovement().scale(20);
            Vec3 expectedVelocity = new Vec3(motor.velocity().x() + carrier.x,
                    motor.velocity().y() + carrier.y, motor.velocity().z() + carrier.z);
            double velocityError = actualVelocity.distanceTo(expectedVelocity);
            System.out.println("ROTATING_JUMP_LAUNCH tick=" + tick + " grounded=true frame=" + frame.getUniqueId()
                    + " omega=" + omega + " comMps=" + com + " sdkPointMps=" + carrier
                    + " requestedOwn=" + observed[0].requestedOwnDisplacement()
                    + " acceptedOwn=" + observed[0].acceptedOwnDisplacement()
                    + " measuredOwn=" + observed[0].measuredOwnDisplacement()
                    + " inheritedNative=" + launchInfo.inheritedMotion + " motorVelocity=" + motor.velocity()
                    + " actualVelocity=" + actualVelocity + " residualMps=" + velocityError
                    + " before=" + beforePosition + " after=" + human.position());
            if (velocityError > .02) {
                throw new GameTestAssertException("first grounded jump failed immediate SDK point-velocity inheritance: residual="
                        + velocityError + " m/s, expected=" + expectedVelocity + " actual=" + actualVelocity
                        + " tracking=" + (movement.sable$getTrackingSubLevel() == null ? null
                        : movement.sable$getTrackingSubLevel().getUniqueId()));
            }
            require(!human.onGround() && accepted.velocity().y() > 0,
                    "positive accepted motor jump leaves the body airborne with upward velocity");
            require(observed[0].requestedOwnDisplacement().y() > 0
                            && observed[0].measuredOwnDisplacement().y() > 0,
                    "native jump move accepted actual upward own displacement");
            Vec3 launchDisplacement = human.position().subtract(beforePosition);
            MovementVector expectedLaunchOwn = new MovementVector(motor.position().x() - beforePosition.x,
                    motor.position().y() - beforePosition.y, motor.position().z() - beforePosition.z);
            Vec3 launchInherited = launchInfo.inheritedMotion == null ? Vec3.ZERO : launchInfo.inheritedMotion;
            double launchPositionError = Math.sqrt(Math.pow(launchDisplacement.x - expectedLaunchOwn.x() - launchInherited.x, 2)
                    + Math.pow(launchDisplacement.y - expectedLaunchOwn.y() - launchInherited.y, 2)
                    + Math.pow(launchDisplacement.z - expectedLaunchOwn.z() - launchInherited.z, 2));
            require(launchPositionError <= nativePositionAllowance(beforePosition),
                    "first launch physical displacement equals motor own motion plus this move's measured native inherited displacement; residual="
                            + launchPositionError + " blocks");
            var expectedAirReference = new AirMotionReference(frame.getUniqueId(),
                    new MovementVector(carrier.x, carrier.y, carrier.z));
            require(java.util.Objects.equals(human.airMotionReference().orElse(null), expectedAirReference),
                    "takeoff freezes the independently captured SDK frame point velocity and frame identity");
            // Jump contact may detach the actor before this first airborne sample. The fresh
            // launch move above is itself the departure witness; do not wait for a second
            // frame->world transition that can no longer occur.
            var flightPhase = new NativeJumpFlightPhase(frame.getUniqueId());
            require(flightPhase.observeDeparture(observed[0].collisionInfoFresh(), !human.onGround(),
                            observed[0].beforePoint().frameId(), observed[0].afterPoint().frameId()),
                    "fresh exact-frame to world airborne departure authorizes one native impulse");
            require(!frame.isRemoved()
                            && SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) == frame,
                    "departed launch frame remains loaded for controlled impulse");
            RigidBodyHandle.of(frame).addLinearAndAngularVelocity(new Vector3d(2, 0, 0), new Vector3d());
            continueRotatingJumpFlight(helper, human, plot, storage, frame, launchStoragePoint,
                    expectedAirReference, flightPhase, 0, 12, true);
        });
    }

    private static void continueRotatingJumpFlight(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos storage, ServerSubLevel launchFrame, Vector3d launchStoragePoint,
            AirMotionReference expectedAirReference, NativeJumpFlightPhase phase,
            int tick, int remaining, boolean nativeImpulseApplied) {
        if (remaining == 0) {
            require(nativeImpulseApplied, "measured native-frame impulse followed observed airborne frame departure");
            require(phase.observations() == 12 && phase.airProofComplete(),
                    "12-tick observation lifecycle includes nonvacuous ascent, descent, and changed SDK motion");
            helper.succeed();
            return;
        }
        helper.runAfterDelay(1, () -> {
            ServerLevel level = (ServerLevel) human.level();
            var movement = (EntityMovementExtension) (Object) human;
            UUID preFrame = movement.sable$getTrackingSubLevel() == null ? null
                    : movement.sable$getTrackingSubLevel().getUniqueId();
            boolean groundedBefore = human.onGround();
            require(groundedBefore || preFrame == null || launchFrame.getUniqueId().equals(preFrame),
                    "airborne flight tracking is either the exact launch frame or world");
            if (!groundedBefore) require(java.util.Objects.equals(human.airMotionReference().orElse(null), expectedAirReference),
                    "airborne owner retains captured frame/point baseline without following carrier motion");
            Vec3 sdkPointVelocity = null;
            if (nativeImpulseApplied) {
                require(!launchFrame.isRemoved()
                                && SubLevelContainer.getContainer(level).getSubLevel(launchFrame.getUniqueId()) == launchFrame,
                        "launch frame remains the same loaded native frame for point-velocity resample");
                Vector3d sampledPoint = new Vector3d();
                Sable.HELPER.getVelocity(level, launchFrame, launchStoragePoint, sampledPoint);
                sdkPointVelocity = new Vec3(sampledPoint.x, sampledPoint.y, sampledPoint.z);
            }
            Vec3 before = human.position();
            Vec3 incomingVelocity = human.getDeltaMovement().scale(20);
            CharacterMovementState motor = groundedBefore ? expectedClearMotor(human, 0, 0, false)
                    : expectedClearMotor(human, 0, 0, false, expectedAirReference);
            Object previousInfo = movement.sable$getCollisionInfo();
            var observed = new com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult[1];
            new GroundedHarnessWorldStep().step(human, 0, 0, false, false, 0f, value -> observed[0] = value)
                    .orElseThrow(() -> new GameTestAssertException("neutral post-jump owner step rejected"));
            UUID postFrame = movement.sable$getTrackingSubLevel() == null ? null
                    : movement.sable$getTrackingSubLevel().getUniqueId();
            require(postFrame == null || launchFrame.getUniqueId().equals(postFrame),
                    "jump does not acquire an unrelated tracking frame");
            if (groundedBefore || human.onGround()) {
                require(phase.airProofComplete(), "landing cannot preempt required airborne ascent/descent and SDK proof");
                boolean knownLanding = knownLaunchDeckFloor(level, plot, storage, human, observed[0]);
                require(knownLanding, knownLanding ? "landing is independently witnessed on the authored launch-deck floor"
                        : landingWitnessFailure(level, plot, storage, human, observed[0]));
                if (!groundedBefore) phase.observeLanding(observed[0].collisionInfoFresh(), true);
                else phase.observeGrounded(observed[0].collisionInfoFresh(), true);
                require(human.onGround() && human.airMotionReference().isEmpty(),
                        "verified landing clears air reference and remains grounded");
                if (remaining == 1) continueRotatingJumpFlight(helper, human, plot, storage, launchFrame,
                        launchStoragePoint, expectedAirReference, phase, tick + 1, 0, nativeImpulseApplied);
                else continueRotatingJumpFlight(helper, human, plot, storage, launchFrame,
                        launchStoragePoint, expectedAirReference, phase, tick + 1, remaining - 1, nativeImpulseApplied);
                return;
            }
            require(!human.onGround() && java.util.Objects.equals(human.airMotionReference().orElse(null), expectedAirReference),
                    "airborne owner keeps exact immutable baseline");
            boolean changedNow = sdkPointVelocity != null && sdkPointVelocity.distanceTo(new Vec3(
                    expectedAirReference.pointVelocity().x(), expectedAirReference.pointVelocity().y(),
                    expectedAirReference.pointVelocity().z())) > .5;
            Vec3 displacement = human.position().subtract(before);
            MovementVector predicted = new MovementVector(motor.position().x() - before.x,
                    motor.position().y() - before.y, motor.position().z() - before.z);
            double positionError = Math.sqrt(Math.pow(displacement.x - predicted.x(), 2)
                    + Math.pow(displacement.y - predicted.y(), 2) + Math.pow(displacement.z - predicted.z(), 2));
            Vec3 actualVelocity = human.getDeltaMovement().scale(20);
            double velocityError = actualVelocity.distanceTo(new Vec3(motor.velocity().x(), motor.velocity().y(), motor.velocity().z()));
            Vec3 inherited = observed[0] == null || observed[0].collisionInfo() == null
                    ? null : observed[0].collisionInfo().inheritedMotion;
            boolean nextImpulseApplied = nativeImpulseApplied;
            System.out.println("ROTATING_JUMP_FLIGHT tick=" + tick + " preVelocity=" + incomingVelocity
                    + " requestedOwn=" + (observed[0] == null ? null : observed[0].requestedOwnDisplacement())
                    + " measuredOwn=" + (observed[0] == null ? null : observed[0].measuredOwnDisplacement())
                    + " inheritedNative=" + inherited + " actualDisplacement=" + displacement
                    + " motorDisplacement=" + predicted + " positionResidual=" + positionError
                    + " actualVelocity=" + actualVelocity + " motorVelocity=" + motor.velocity()
                    + " velocityResidual=" + velocityError + " launchBaseline=" + expectedAirReference.pointVelocity()
                    + " bodyBaseline=" + human.airMotionReference().orElseThrow().pointVelocity()
                    + " sdkLaunchPointVelocity=" + sdkPointVelocity + " sdkPointChanged=" + changedNow
                    + " tracking=" + postFrame + " impulseApplied=" + nextImpulseApplied);
            require(positionError <= nativePositionAllowance(before),
                    "neutral airborne physical displacement follows the resolved character motor, without renewed carrier carry; residual="
                            + positionError + " blocks");
            require(velocityError <= .02,
                    "neutral airborne velocity follows the motor gravity/drag path, without another carrier point impulse; residual="
                            + velocityError + " m/s");
            var airInfo = observed[0] == null ? null : observed[0].collisionInfo();
            phase.observeAir(observed[0] != null && observed[0].collisionInfoFresh(), !human.onGround(),
                    airInfo != null && !airInfo.verticalCollision && !airInfo.horizontalCollision,
                    incomingVelocity.y, actualVelocity.y, changedNow);
            continueRotatingJumpFlight(helper, human, plot, storage, launchFrame, launchStoragePoint,
                    expectedAirReference, phase, tick + 1, remaining - 1, nextImpulseApplied);
        });
    }

    private static boolean knownLaunchDeckFloor(ServerLevel level, SableFixturePlot plot, BlockPos storage, Mob body,
            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult move) {
        if (move == null || !move.collisionInfoFresh() || move.collisionInfo() == null
                || move.collisionScope() == null || move.collisionInfo().motion == null
                || move.requestedOwnDisplacement() == null || move.requestedOwnDisplacement().y() > 0d
                || !move.collisionInfo().verticalCollision || !move.collisionInfo().verticalCollisionBelow
                || move.collisionInfo().horizontalCollision || move.collisionInfo().subLevelHorizontalCollision
                || !body.onGround()) return false;
        var trace = move.collisionScope().nativeCollisionTrace();
        var info = move.collisionInfo();
        return NativeJumpFlightPhase.landingWitnessAdmissible(move.collisionInfoFresh(),
                move.collisionScope().nativeCollisionTraceComplete() && move.collisionScope().geometryTraceComplete(),
                info.trackingSubLevel != null && plot.engineId().equals(info.trackingSubLevel.getUniqueId()),
                body.onGround(), info.verticalCollision && info.verticalCollisionBelow
                        && !info.horizontalCollision && !info.subLevelHorizontalCollision,
                info.firstCollisions != null && info.firstCollisions.entrySet().stream().anyMatch(entry ->
                        entry.getKey() != null && plot.engineId().equals(entry.getKey().getUniqueId())
                                && entry.getValue() != null && !entry.getValue().horizontal()
                                && Double.isFinite(entry.getValue().globalDirection().y())
                                && entry.getValue().globalDirection().y() > 0.99),
                move.requestedOwnDisplacement(), move.measuredOwnDisplacement(), info.motion.x, info.motion.y,
                info.motion.z, trace, plot.engineId(), row -> {
                    if (row.selectedPos() == null || row.selectedState() == null || row.geometry() == null
                            || row.selectedPos().getY() != storage.getY()
                            || row.selectedPos().getX() < storage.getX() || row.selectedPos().getX() >= storage.getX() + DECK_SIZE
                            || row.selectedPos().getZ() < storage.getZ() || row.selectedPos().getZ() >= storage.getZ() + DECK_SIZE) return false;
                    var actual = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), row.selectedPos());
                    return actual != null && actual.equals(row.selectedState()) && actual.is(Blocks.STONE);
                });
    }

    private static String landingWitnessFailure(ServerLevel level, SableFixturePlot plot, BlockPos storage,
            Mob body, com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult move) {
        var info = move == null ? null : move.collisionInfo();
        var scope = move == null ? null : move.collisionScope();
        var rows = scope == null ? null : scope.nativeCollisionTrace();
        StringBuilder detail = new StringBuilder("landing witness rejected: fresh=")
                .append(move != null && move.collisionInfoFresh()).append(" traceComplete=")
                .append(scope != null && scope.nativeCollisionTraceComplete()).append(" geometryComplete=")
                .append(scope != null && scope.geometryTraceComplete()).append(" onGround=").append(body.onGround())
                .append(" tracking=").append(info == null || info.trackingSubLevel == null ? null
                        : info.trackingSubLevel.getUniqueId()).append(" request=")
                .append(move == null ? null : move.requestedOwnDisplacement()).append(" nativeMotion=")
                .append(info == null ? null : info.motion).append(" measured=")
                .append(move == null ? null : move.measuredOwnDisplacement()).append(" afterPoint=")
                .append(move == null ? null : move.afterPoint());
        if (info != null && info.firstCollisions != null) {
            detail.append(" firstContacts=");
            info.firstCollisions.entrySet().stream().limit(4).forEach(entry -> {
                var contact = entry.getValue();
                detail.append('{').append(entry.getKey() == null ? null : entry.getKey().getUniqueId())
                        .append(",upwardNormal=").append(contact != null
                                && Double.isFinite(contact.globalDirection().y()) && contact.globalDirection().y() > 0.99)
                        .append(",horizontal=").append(contact != null && contact.horizontal())
                        .append(",normal=").append(contact == null ? null : contact.globalDirection()).append('}');
            });
        }
        detail.append(" traceRows=");
        if (rows != null) rows.stream().limit(4).forEach(row -> {
            boolean knownDeckStone = false;
            if (row != null && row.selectedPos() != null && row.selectedState() != null
                    && row.selectedPos().getY() == storage.getY()
                    && row.selectedPos().getX() >= storage.getX() && row.selectedPos().getX() < storage.getX() + DECK_SIZE
                    && row.selectedPos().getZ() >= storage.getZ() && row.selectedPos().getZ() < storage.getZ() + DECK_SIZE) {
                var actual = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), row.selectedPos());
                knownDeckStone = actual != null && actual.equals(row.selectedState()) && actual.is(Blocks.STONE);
            }
            detail.append('{').append(row == null ? null : row.selectedPos()).append(",state=")
                    .append(row == null ? null : row.selectedState()).append(",response=")
                    .append(row == null ? null : row.responseX() + "," + row.responseY() + "," + row.responseZ())
                    .append(",knownDeckStone=").append(knownDeckStone).append('}');
        });
        return detail.toString();
    }

    private static double nativePositionAllowance(Vec3 at) {
        double coordinateUlp = Math.sqrt(Math.pow(Math.ulp(at.x), 2) + Math.pow(Math.ulp(at.y), 2)
                + Math.pow(Math.ulp(at.z), 2));
        return Math.sqrt(3) * com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                + coordinateUlp;
    }

    private enum OwnedHullClearanceStatus { COMPLETE_CLEAR, TOUCHING, OVERLAP, UNKNOWN }

    private record OwnedHullClearance(OwnedHullClearanceStatus status, double maximumPenetration) { }

    /** Exact authored-deck collision-shape witness; unknown storage is never treated as clear. */
    private static OwnedHullClearance ownedHullClearance(ServerLevel level, SableFixturePlot plot,
                                                         BlockPos storageOrigin, Mob human) {
        var bounds = human.getBoundingBox();
        var bodyCenter = new Vector3d((bounds.minX + bounds.maxX) * .5,
                (bounds.minY + bounds.maxY) * .5, (bounds.minZ + bounds.maxZ) * .5);
        var bodySize = new Vector3d(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY,
                bounds.maxZ - bounds.minZ);
        double maxPenetration = 0;
        boolean touching = false;
        for (int x = 0; x < DECK_SIZE; x++) for (int z = 0; z < DECK_SIZE; z++) {
            BlockPos pos = storageOrigin.offset(x, 0, z);
            if (!level.hasChunkAt(pos)) return new OwnedHullClearance(OwnedHullClearanceStatus.UNKNOWN, maxPenetration);
            var state = LoadedFrameRayQuery.knownServerBlock(level, plot.subLevel(), pos);
            if (state == null) return new OwnedHullClearance(OwnedHullClearanceStatus.UNKNOWN, maxPenetration);
            for (var piece : state.getCollisionShape(level, pos).toAabbs()) {
                var center = new Vector3d(pos.getX() + (piece.minX + piece.maxX) * .5,
                        pos.getY() + (piece.minY + piece.maxY) * .5, pos.getZ() + (piece.minZ + piece.maxZ) * .5);
                var pose = plot.subLevel().logicalPose();
                pose.transformPosition(center);
                var shape = new dev.ryanhcode.sable.api.math.OrientedBoundingBox3d(center,
                        new Vector3d((piece.maxX - piece.minX) * pose.scale().x(),
                                (piece.maxY - piece.minY) * pose.scale().y(),
                                (piece.maxZ - piece.minZ) * pose.scale().z()), pose.orientation(),
                        new dev.ryanhcode.sable.api.math.LevelReusedVectors());
                var actual = new dev.ryanhcode.sable.api.math.OrientedBoundingBox3d(new Vector3d(bodyCenter),
                        new Vector3d(bodySize), new Quaterniond(),
                        new dev.ryanhcode.sable.api.math.LevelReusedVectors());
                double penetration = new Vector3d(dev.ryanhcode.sable.api.math.OrientedBoundingBox3d
                        .satToleranced(actual, shape, 0)).length();
                maxPenetration = Math.max(maxPenetration, penetration);
                var inflated = new dev.ryanhcode.sable.api.math.OrientedBoundingBox3d(new Vector3d(bodyCenter),
                        new Vector3d(bodySize).add(2 * NativeCollisionSlop.MAX_RESIDUAL,
                                2 * NativeCollisionSlop.MAX_RESIDUAL, 2 * NativeCollisionSlop.MAX_RESIDUAL), new Quaterniond(),
                        new dev.ryanhcode.sable.api.math.LevelReusedVectors());
                if (new Vector3d(dev.ryanhcode.sable.api.math.OrientedBoundingBox3d
                        .satToleranced(inflated, shape, 0)).lengthSquared() > 0) touching = true;
            }
        }
        if (maxPenetration > NativeCollisionSlop.MAX_RESIDUAL)
            return new OwnedHullClearance(OwnedHullClearanceStatus.OVERLAP, maxPenetration);
        return new OwnedHullClearance(touching ? OwnedHullClearanceStatus.TOUCHING
                : OwnedHullClearanceStatus.COMPLETE_CLEAR, maxPenetration);
    }

    private static Vec3 deckFeet(SableFixturePlot plot, BlockPos origin, int x, int z) {
        Vector3d point = plot.subLevel().logicalPose().transformPosition(new Vector3d(
                origin.getX() + x + .5, origin.getY() + 1, origin.getZ() + z + .5));
        return new Vec3(point.x, point.y, point.z);
    }

    public static Vec3 deckFeetAt(SableFixturePlot plot, BlockPos origin, double storageX, double storageZ) {
        Vector3d point = plot.subLevel().logicalPose().transformPosition(new Vector3d(
                origin.getX() + storageX, origin.getY() + 1, origin.getZ() + storageZ));
        return new Vec3(point.x, point.y, point.z);
    }

    /** Place a jug just clear of the measured native hull, over the fixture's ordinary world support pad. */
    private static Vec3 exteriorWorldFeet(SableFixturePlot plot, boolean rotationCorner) {
        var bounds = plot.subLevel().boundingBox().toMojang();
        double z = rotationCorner ? bounds.maxZ - .75 : (bounds.minZ + bounds.maxZ) * .5;
        return new Vec3(bounds.maxX + .27, bounds.minY, z);
    }

    private static boolean verticalOverlap(net.minecraft.world.phys.AABB item, net.minecraft.world.phys.AABB hull) {
        return item.maxY > hull.minY && item.minY < hull.maxY;
    }

    private static Vector3d localPosition(SableFixturePlot plot, Vec3 world) {
        return plot.subLevel().logicalPose().transformPositionInverse(new Vector3d(world.x, world.y, world.z));
    }

    private static Vector3d pointVelocity(ServerLevel level, SableFixturePlot plot,
                                          LoadedFrameRayQuery.ActorPoint point) {
        require(point != null && plot.engineId().equals(point.frameId())
                        && SubLevelContainer.getContainer(level).getSubLevel(point.frameId()) == plot.subLevel(),
                "SDK point-velocity sample belongs to the exact live plot UUID");
        Vector3d result = new Vector3d();
        Sable.HELPER.getVelocity(level, plot.subLevel(), new Vector3d(point.storagePoint().x,
                point.storagePoint().y, point.storagePoint().z), result);
        return result;
    }

    private static void tickHuman(GameTestHelper helper,
                                  com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                                  int wishX, int wishZ, int count, Runnable done) {
        tickHuman(helper, body, wishX, wishZ, 0, count, done);
    }

    private static void tickHuman(GameTestHelper helper,
                                  com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                                  int wishX, int wishZ, int index, int count, Runnable done) {
        if (index >= count) { done.run(); return; }
        helper.runAfterDelay(1, () -> {
            new GroundedHarnessWorldStep().step(body, wishX, wishZ, false, false, 0f)
                    .orElseThrow(() -> new GameTestAssertException("scripted normal human motor tick was rejected"));
            tickHuman(helper, body, wishX, wishZ, index + 1, count, done);
        });
    }

    private static void awaitNativeDeckSupport(GameTestHelper helper,
                                               com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                                               int ticksLeft, Runnable supported) {
        if (body.onGround()) { supported.run(); return; }
        if (ticksLeft <= 0) throw new GameTestAssertException("human failed to establish native deck support; position="
                + body.position() + ", velocity=" + body.getDeltaMovement());
        new GroundedHarnessWorldStep().step(body, 0, 0, false, false, 0f)
                .orElseThrow(() -> new GameTestAssertException("owned human support motor step was rejected"));
        helper.runAfterDelay(1, () -> awaitNativeDeckSupport(helper, body, ticksLeft - 1, supported));
    }

    /** Strong counterpart for step controls: an onGround bit alone is not a native support witness. */
    private static void awaitNativeDeckSupport(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            SableFixturePlot plot, BlockPos storage, int ticksLeft, Runnable supported) {
        var movement = (EntityMovementExtension) (Object) body;
        Object previous = movement.sable$getCollisionInfo();
        SableOwnedBodyMotion.MoveResult[] observed = {null};
        var step = new GroundedHarnessWorldStep().step(body, 0, 0, false, false,
                frameLocalXAxisYaw(plot), move -> observed[0] = move);
        var move = observed[0];
        var info = move == null ? null : move.collisionInfo();
        boolean exactFloorRow = move != null && move.collisionScope() != null
                && move.collisionScope().nativeCollisionTraceComplete()
                && move.collisionScope().nativeCollisionTrace().stream().anyMatch(row ->
                        plot.engineId().equals(row.frameId()) && row.selectedPos() != null
                                && row.selectedPos().getY() == storage.getY()
                                && row.selectedY() > 0d && row.verticalCollision()
                                && row.selectedState() != null && row.selectedState().is(Blocks.STONE));
        boolean freshFrameSupport = step.isPresent() && move != null && move.collisionInfoFresh()
                && info != null && info != previous && info.trackingSubLevel == plot.subLevel()
                && info.verticalCollision && info.verticalCollisionBelow && body.onGround() && exactFloorRow;
        if (freshFrameSupport) { supported.run(); return; }
        if (ticksLeft <= 0) throw new GameTestAssertException("human failed to establish fresh exact-frame native deck-floor support; position="
                + body.position() + ", onGround=" + body.onGround() + ", expectedFrame=" + plot.engineId()
                + ", info=" + info + ", trace=" + (move == null || move.collisionScope() == null
                        ? null : move.collisionScope().nativeCollisionTrace()));
        helper.runAfterDelay(1, () -> awaitNativeDeckSupport(helper, body, plot, storage, ticksLeft - 1, supported));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    @FunctionalInterface private interface DeckWork {
        void run(ServerLevel level, SableFixturePlot plot, BlockPos storageOrigin);
    }

    @FunctionalInterface private interface DeckSceneWork {
        void run(ServerLevel level, SableFixturePlot plot, BlockPos storageOrigin, SableFixtureScene.Region scene);
    }

    @FunctionalInterface private interface DeckPairWork {
        void run(SableFixturePlot frameA, BlockPos storageA, SableFixturePlot frameB, BlockPos storageB);
    }
}
