package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Regression: an artificial initial apex is not a natural jump or a full connected active/inactive A/B. */
@GameTestTags({"shuttle-active-zero-wall-regression"})
@PrefixGameTestTemplate(false)
public final class ActiveZeroWallContactDiagnosticGameTests {
    private ActiveZeroWallContactDiagnosticGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    @GameTestTags({"shuttle-active-zero-wall-regression"})
    public static void activeZeroWallGeometryPreservesAirborneProgress(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = new BlockPos(2720, 300, 2480);
        var scene = SableFixtureScene.acquire(helper, origin, 24, 12, 24);
        SableFixtureScene.awaitEmpty(helper, scene, 100, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            SableFixturePlot.Authored<ShuttleCabinFixture.Built> authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(origin.getX() + 12.5, origin.getY(), origin.getZ() + 12.5), plot -> {
                        var storage = plot.storageOrigin();
                        plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                                ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                        return ShuttleCabinFixture.build(level, storage, plot.engineId());
                    });
            var plot = authored.plot();
            SableFixtureScene.trackPlot(helper, plot);
            SableFixtureScene.awaitStationaryFrame(helper, plot, 80, () -> {
                ShuttleFixtureMotionService.registerFixture(plot.subLevel());
                var snapshot = ShuttleFixtureMotionService.snapshot(level, plot.subLevel());
                var handle = RigidBodyHandle.of(plot.subLevel());
         HullImpactSubstepsExpectation.requireNormalSix(level, "active-zero-wall-regression");
                require(snapshot != null && snapshot.sdkSubsteps() == 6
                                && snapshot.configuredGravity().y() < -1
                                && handle != null && handle.isValid(), "normal active-zero native body and gravity");
                require(ShuttleFixtureMotionService.start(level, plot.subLevel(),
                        new ShuttleMotionRequest(0, 0, 0, 0, 120)), "zero-target controller starts");
                // Deliberate diagnostic seed: one tiny native angular nudge, followed by four ordinary ticks.
                handle.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, 0, -.002));
                helper.runAfterDelay(4, () -> spawnDiagnosticHuman(helper, level, authored, plot, snapshot, handle));
            });
        });
    }

    private static void spawnDiagnosticHuman(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            SableFixturePlot.Authored<ShuttleCabinFixture.Built> authored, SableFixturePlot plot,
            ShuttleFixtureMotionService.Snapshot snapshot, RigidBodyHandle handle) {
                var storage = authored.result().origin();
                var wallBase = storage.offset(5, 1, 0);
                var wallState = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .knownServerBlock(level, plot.subLevel(), wallBase);
                for (int y = 1; y <= 3; y++) {
                    var facePos = storage.offset(5, y, 0);
                    var faceState = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .knownServerBlock(level, plot.subLevel(), facePos);
                    require(faceState != null && faceState.is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                                    && !faceState.getCollisionShape(level, facePos).isEmpty(),
                            "entire relevant selected cabin wall face is known closed steel geometry at " + facePos);
                }
                var sizeProbe = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                        .PlayerCharacterHarnessRegistration.getEntityType().create(level);
                require(sizeProbe != null, "bound-human sizing probe creates");
                double halfWidth = sizeProbe.getBbWidth() * .5;
                sizeProbe.discard();
                var wallPoint = plot.subLevel().logicalPose().transformPosition(
                        new Vector3d(storage.getX() + 5.5, storage.getY() + 2, storage.getZ()));
                var outward = plot.subLevel().logicalPose().transformNormal(new Vector3d(0, 0, -1), new Vector3d()).normalize();
                Vec3 feet = new Vec3(wallPoint.x + outward.x * (halfWidth + .001), wallPoint.y,
                        wallPoint.z + outward.z * (halfWidth + .001));
                 var human = BoundHumanFixture.create(level, feet, "active-zero-wall-regression");
                helper.testInfo.addListener(new GameTestListener() {
                    private void cleanup() { if (!human.isRemoved()) human.discard(); }
                    @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
                    @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                    @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                    @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                            net.minecraft.gametest.framework.GameTestInfo next, GameTestRunner runner) { cleanup(); }
                });
                var startingVelocity = handle.getLinearVelocity(new Vector3d());
                var pose = plot.subLevel().logicalPose();
                var orientation = pose.orientation();
                var box = human.getBoundingBox();
                require(box.maxX < plot.subLevel().boundingBox().toMojang().minX
                                || box.maxZ < plot.subLevel().boundingBox().toMojang().minZ
                                || box.minX > plot.subLevel().boundingBox().toMojang().maxX
                                || box.minZ > plot.subLevel().boundingBox().toMojang().maxZ,
                        "diagnostic body begins outside the cabin hull AABB");
                System.out.println("[active-zero-wall-diagnostic] setup owner=" + plot.engineId()
                        + " wall=" + wallBase + " state=" + wallState + " human=" + human.getBoundingBox()
                        + " initialHumanVelocity=" + human.getDeltaMovement() + " bodyVelocity=" + startingVelocity
                        + " bodyAngular=" + handle.getAngularVelocity(new Vector3d()) + " request=(0,0,0,0,120)"
                        + " shipCOM=" + pose.position() + " shipOrientation=" + orientation
                        + " angular=" + handle.getAngularVelocity(new Vector3d())
                        + " humanInitialVelocity=" + human.getDeltaMovement() + " substeps=" + snapshot.sdkSubsteps()
                        + " gravity=" + snapshot.configuredGravity());
                if (human.getDeltaMovement().lengthSqr() != 0 || human.onGround())
                    throw new GameTestAssertException("diagnostic initial human apex/airborne premise not met: velocity="
                            + human.getDeltaMovement() + " grounded=" + human.onGround());
                System.out.println("[active-zero-wall-diagnostic] outwardWorld=(" + outward.x + "," + outward.y + "," + outward.z
                        + ") predictedProjectionYEstimate=" + (.15625 - .001) * outward.y);
                 runApproach(helper, human, plot, wallBase, level, 12, new AtomicBoolean(), new AtomicBoolean(), new AtomicReference<>());
    }

    private static void runApproach(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, BlockPos wall, net.minecraft.server.level.ServerLevel level,
            int remaining, AtomicBoolean sideLogged, AtomicBoolean regressionCaptured, AtomicReference<Double> acceptedY) {
        if (remaining == 0) {
            if (!regressionCaptured.get()) throw new GameTestAssertException("above-slop pure-side response was not accepted by the airborne geometry proof");
            if (acceptedY.get()==null || human.position().y() >= acceptedY.get()-1e-4)
                throw new GameTestAssertException("ordinary post-contact ticks did not make falling progress");
            ShuttleFixtureMotionService.stop(helper.getLevel(), plot.subLevel());
            helper.succeed();
            return;
        }
        var moveRef = new java.util.concurrent.atomic.AtomicReference<SableOwnedBodyMotion.MoveResult>();
        var prior = human.position();
        boolean wasGrounded = human.onGround();
        var step = new GroundedHarnessWorldStep().step(human, 0, 1000, false, false, 0f, moveRef::set);
        var move = moveRef.get();
        if (move == null) {
            System.out.println("[active-zero-wall-regression] FAILURE noMoveObserver tick=" + helper.getLevel().getGameTime()
                    + " resultPresent=" + step.isPresent() + " humanPosition=" + human.position()
                    + " humanVelocity=" + human.getDeltaMovement() + " grounded=" + human.onGround());
            throw new GameTestAssertException("owned movement step had no MoveResult observer; cannot verify this ordinary tick");
        }
        if (step.isEmpty()) {
            logRejectedStep(helper, human, plot, level, move, step, wasGrounded);
            throw new GameTestAssertException("ordinary owned movement step rejected at tick " + helper.getLevel().getGameTime()
                    + "; full actual fresh collision trace and owner context logged above");
        }
        if (move != null && move.collisionInfoFresh() && move.collisionInfo() != null
                && move.collisionInfo().subLevelHorizontalCollision) {
            var scope = move.collisionScope();
            var info = move.collisionInfo();
            var rows = scope == null ? null : scope.nativeCollisionTrace();
            boolean actualWall = rows != null && rows.stream().anyMatch(row -> plot.engineId().equals(row.frameId())
                    && row.selectedPos() != null && row.selectedPos().getX() == wall.getX()
                    && row.selectedPos().getZ() == wall.getZ()
                    && row.selectedPos().getY() >= wall.getY() && row.selectedPos().getY() <= wall.getY() + 2
                    && row.selectedState() != null && row.selectedState().is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                    && row.selectedState().equals(com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .knownServerBlock(level, plot.subLevel(), row.selectedPos())) && row.geometry() != null && !row.verticalCollision());
            boolean pureSide = !info.verticalCollision && !info.verticalCollisionBelow;
            boolean yResidual = move.requestedOwnDisplacement().y() == 0
                    && Math.abs(move.measuredOwnDisplacement().y()) > com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL;
            boolean qualifies = pureSide && actualWall && yResidual && move.requestedOwnDisplacement().y() == 0
                    && step.isPresent() && move.positionCorrection()!=null
                    && move.positionCorrection().boundedOwnDisplacement().y()==move.requestedOwnDisplacement().y()
                    && move.nativeContactProofDiagnostic().reason()==NativeSideContactProof.Failure.NONE
                    && move.nativeContactProofDiagnostic().positionStage()==NativeContactProofDiagnostic.PositionStage.ACCEPTED && scope != null
                    && scope.nativeCollisionTraceComplete() && scope.geometryTraceComplete()
                    && rows != null && !rows.isEmpty() && rows.size() <= 160;
            boolean firstSide = !sideLogged.getAndSet(true);
            boolean firstRejection = qualifies && !regressionCaptured.getAndSet(true);
            var liveSnapshot = ShuttleFixtureMotionService.snapshot(level, plot.subLevel());
            if (firstRejection) {
                acceptedY.set(human.position().y());
                require(liveSnapshot != null && liveSnapshot.active() && liveSnapshot.request().targetX()==0
                                && liveSnapshot.request().targetY()==0 && liveSnapshot.request().targetZ()==0,
                        "controller remains active with zero target after accepted wall correction");
                require(!human.onGround(), "wall geometry does not grant grounded support");
            }
            var livePose = plot.subLevel().logicalPose();
            if (firstSide || firstRejection) {
                var clip = scope == null ? null : scope.worldClipObservation();
                var correction = move.positionCorrection();
                System.out.println("[active-zero-wall-diagnostic] "
                    + (firstSide ? "firstSide" : "firstQualifyingAccepted") + " tick=" + helper.getLevel().getGameTime()
                    + " controllerActive=" + (liveSnapshot != null && liveSnapshot.active())
                    + " request=" + (liveSnapshot == null ? null : liveSnapshot.request())
                    + " nativeVelocity=" + (liveSnapshot == null ? null : liveSnapshot.worldVelocity())
                    + " pose=" + livePose.position() + " orientation=" + livePose.orientation()
                    + " stepPresent=" + step.isPresent() + " requestVec=" + move.requestedOwnDisplacement()
                    + " measured=" + move.measuredOwnDisplacement() + " accepted=" + move.acceptedOwnDisplacement()
                     + " correctionBounded=" + (correction == null ? null : correction.boundedOwnDisplacement())
                     + " correctionGeometry=" + (correction == null ? null : correction.geometryCorrection()) + " proof=" + move.nativeContactProofDiagnostic()
                    + " collisionFlags={side=" + info.subLevelHorizontalCollision + ",vertical=" + info.verticalCollision
                    + ",below=" + info.verticalCollisionBelow + ",horizontal=" + info.horizontalCollision + "}"
                    + " actualWall=" + actualWall + " pureSide=" + pureSide + " yResidual=" + yResidual
                     + " ownerStepAccepted=" + step.isPresent() + " traceComplete=" + (scope != null && scope.nativeCollisionTraceComplete())
                    + " geometryComplete=" + (scope != null && scope.geometryTraceComplete()) + " geometryObserved=" + (scope != null && scope.geometryObserved())
                     + " worldClipFields={entity=" + (clip == null ? null : clip.entity()) + ",input=" + (clip == null ? null : clip.input())
                     + ",output=" + (clip == null ? null : clip.output()) + ",complete=" + (clip != null && clip.complete())
                     + ",positionBefore=" + (clip == null ? null : clip.positionBefore()) + ",boundsBefore=" + (clip == null ? null : clip.boundsBefore())
                     + ",downwardFloorClip=" + (clip != null && clip.downwardFloorClip()) + "} beforePoint=" + move.beforePoint()
                    + " afterPoint=" + move.afterPoint() + " wasGrounded=" + wasGrounded
                     + " groundedAfter=" + human.onGround() + " traceRows=" + (rows == null ? -1 : rows.size()) + " fullActualTrace=" + rows);
            }
            if (scope == null || !scope.nativeCollisionTraceComplete() || !scope.geometryTraceComplete()
                    || scope.nativeCollisionTrace() == null || scope.nativeCollisionTrace().isEmpty()
                    || scope.nativeCollisionTrace().size() > 160)
                throw new GameTestAssertException("first side contact lacked complete bounded actual native geometry trace: " + scope);
        }
        System.out.println("[active-zero-wall-diagnostic] step tick=" + helper.getLevel().getGameTime()
                + " index=" + (13 - remaining) + " resultPresent=" + step.isPresent() + " movePresent=" + (move != null)
                + " positionBefore=" + prior + " positionAfter=" + human.position() + " wasGrounded=" + wasGrounded + " groundedAfter=" + human.onGround()
                + " bodyVelocity=" + RigidBodyHandle.of(plot.subLevel()).getLinearVelocity(new Vector3d()));
        helper.runAfterDelay(1, () -> runApproach(helper, human, plot, wall, level, remaining - 1, sideLogged, regressionCaptured, acceptedY));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static void logRejectedStep(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity human,
            SableFixturePlot plot, net.minecraft.server.level.ServerLevel level,
            SableOwnedBodyMotion.MoveResult move, java.util.Optional<?> step, boolean wasGrounded) {
        var info=move.collisionInfo();
        var scope=move.collisionScope();
        var rows=scope==null?null:scope.nativeCollisionTrace();
        var clip=scope==null?null:scope.worldClipObservation();
        var snapshot=ShuttleFixtureMotionService.snapshot(level,plot.subLevel());
        var frame=rows==null||rows.isEmpty()?null:rows.getFirst().frameId();
        boolean complete=scope!=null&&scope.nativeCollisionTraceComplete()&&scope.geometryTraceComplete();
        System.out.println("[active-zero-wall-regression] FIRST_REJECTED_STEP tick="+helper.getLevel().getGameTime()
                +" resultPresent="+step.isPresent()+" humanPosition="+human.position()+" humanVelocity="+human.getDeltaMovement()
                +" wasGrounded="+wasGrounded+" groundedNow="+human.onGround()+" collisionInfoFresh="+move.collisionInfoFresh()
                +" currentFrame="+frame+" controller="+snapshot+" request="+move.requestedOwnDisplacement()
                +" nativeMotion="+(info==null||info.motion==null?null:new Vec3(info.motion.x,info.motion.y,info.motion.z))
                +" measured="+move.measuredOwnDisplacement()+" accepted="+move.acceptedOwnDisplacement()
                +" bounded="+(move.positionCorrection()==null?null:move.positionCorrection().boundedOwnDisplacement())
                +" correction="+(move.positionCorrection()==null?null:move.positionCorrection().geometryCorrection())
                +" proof="+move.nativeContactProofDiagnostic()+" collisionFlags="+(info==null?null:
                    "{side="+info.subLevelHorizontalCollision+",vertical="+info.verticalCollision+",below="+info.verticalCollisionBelow
                            +",horizontal="+info.horizontalCollision+"}")
                +" worldClip={entity="+(clip==null?null:clip.entity())+",input="+(clip==null?null:clip.input())
                +",output="+(clip==null?null:clip.output())+",complete="+(clip!=null&&clip.complete())
                +",positionBefore="+(clip==null?null:clip.positionBefore())+",boundsBefore="+(clip==null?null:clip.boundsBefore())
                +",downwardFloorClip="+(clip!=null&&clip.downwardFloorClip())+"} traceCount="+(rows==null?-1:rows.size())
                +" traceComplete="+complete+" geometryObserved="+(scope!=null&&scope.geometryObserved())
                +" traceOverflow="+(rows!=null&&rows.size()>160));
        if (rows!=null && rows.size()<=160) System.out.println("[active-zero-wall-regression] FIRST_REJECTED_STEP_FULL_ACTUAL_TRACE="+rows);
        else System.out.println("[active-zero-wall-regression] FIRST_REJECTED_STEP_FULL_ACTUAL_TRACE_UNAVAILABLE rows="
                +(rows==null?-1:rows.size())+"; no truncation or fabricated rows");
    }
}
