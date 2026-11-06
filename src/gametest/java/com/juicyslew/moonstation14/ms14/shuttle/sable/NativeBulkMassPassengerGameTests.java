package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.client.ClientFramePredictionState;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Bulk, ordinary loaded-block mass edits while a real fixture HUMAN occupies a resolved cabin. */
@GameTestTags({"shuttle-bulk-mass-passenger-prediction", "shuttle"})
@PrefixGameTestTemplate(false)
public final class NativeBulkMassPassengerGameTests {
    private NativeBulkMassPassengerGameTests() { }

    @net.minecraft.gametest.framework.GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=400)
    @GameTestTags({"shuttle-bulk-mass-roundtrip"})
    public static void bulkBallastRoundTripKeepsCabinPassengerAndPredictionLive(GameTestHelper helper) {
        // Distinct, unused region within the ordinary native-fixture operating radius.
        // Use the known-clear ordinary cabin-floor placement exercised by ShuttleAdminPassengerMotionGameTests.
        MovingCabinAuthorityFixture.start(helper, new BlockPos(4000, 300, 1800), "bulk-mass-passenger", 2.5, 1, 2.5, 12,
                (ready, human) -> {
                    var frame = ready.plot().subLevel();
                    ShuttleFixtureMotionService.registerFixture(frame);
                    double padTop = ready.scene().supportSurfaceY();
                    if (!ShuttleFixtureMotionService.start(ready.level(), frame,
                            new ShuttleMotionRequest(0, .75, 0, 0, 400))) {
                        helper.fail("fixture cabin clearance lift controller did not start"); return;
                    }
                    awaitClearance(helper, ready, human, padTop, 0);
                });
    }

    private static void awaitClearance(GameTestHelper helper, MovingCabinAuthorityFixture.Ready ready,
                                       net.minecraft.world.entity.LivingEntity human, double padTop, int waited) {
        var frame = ready.plot().subLevel();
        double bottom = frame.boundingBox().toMojang().minY;
        if (bottom <= padTop + 2.0) {
            if (waited >= 200) { helper.fail("physical passenger lift failed pad clearance; bottom=" + bottom); return; }
            helper.runAfterDelay(1, () -> awaitClearance(helper, ready, human, padTop, waited + 1));
            return;
        }
        if (!ShuttleFixtureMotionService.start(ready.level(), frame, new ShuttleMotionRequest(.5, 0, 0, 0, 80))) {
            helper.fail("fixture cabin horizontal motion controller did not start"); return;
        }
        var startPose = new org.joml.Vector3d(frame.logicalPose().position());
        awaitFlight(helper, ready, human, startPose.x, 0);
    }

    private static void awaitFlight(GameTestHelper helper, MovingCabinAuthorityFixture.Ready ready,
                                    net.minecraft.world.entity.LivingEntity human, double startX, int waited) {
        var frame = ready.plot().subLevel();
        var velocity = RigidBodyHandle.of(frame).getLinearVelocity(new Vector3d());
        double x = frame.logicalPose().position().x();
        if (Math.abs(velocity.x) <= .1 || x - startX <= .0625) {
            if (waited >= 40) { helper.fail("physical horizontal flight witness timed out; vx=" + velocity.x + " dx=" + (x-startX)); return; }
            helper.runAfterDelay(1, () -> awaitFlight(helper, ready, human, startX, waited + 1));
            return;
        }
        runBulk(helper, ready, human);
    }

    private static void runBulk(GameTestHelper helper, MovingCabinAuthorityFixture.Ready ready,
                                net.minecraft.world.entity.LivingEntity human) {
                    var frame = ready.plot().subLevel();
                    RigidBodyHandle.of(frame);
                    var tracker = frame.getMassTracker();
                    if (tracker == null) { helper.fail("authored cabin has no native mass tracker"); return; }
                    double baseMass = tracker.getMass();
                    Vector3d baseCom = new Vector3d(tracker.getCenterOfMass());
                    var baseTensor = new org.joml.Matrix3d(tracker.getInertiaTensor());
                    var prediction = new ClientFramePredictionState();
                    long epoch = 1;
                    var storage = ready.cabin().origin();
                    // 12 x 8 x 4 = 384 contiguous STONE cells attached to the cabin's +X side.
                    // The extension is beyond the 8-wide occupied cabin and does not touch its floor/head volume.
                    BlockPos extension = storage.offset(ShuttleCabinFixture.WIDTH, 0, 0);
                    ready.plot().ensureStorageFootprint(extension, 12, 8, 4);
                    helper.runAfterDelay(2, () -> {
                        // Take the paired SDK pose, native actor point and body world position immediately
                        // before the one bulk callback; no earlier fixture-start sample is used as an oracle.
                        var beforePoint = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                        var beforeWorld = new Vector3d(human.position().x, human.position().y, human.position().z);
                        var beforeInterval = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                        var fixedDeckPoint = new Vector3d(storage.getX() + 2.5, storage.getY() + 1.0, storage.getZ() + 2.5);
                        var beforeDeckWorld = frame.logicalPose().transformPosition(new Vector3d(fixedDeckPoint));
                        var beforePassengerLocal = vector(beforePoint.storagePoint());
                     var beforePassengerWorld = frame.logicalPose().transformPosition(new Vector3d(beforePassengerLocal));
                     prediction.authorityProjected(beforePoint, epoch, beforeInterval);
                     var physics = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(ready.level());
                     var pipeline = physics.getPipeline();
                      // Pinned-source hypothesis: each storage edit rebases the native pose through
                      // float-backed transform arithmetic. DLL behavior remains unverified; check every step.
                      var initialNativePose = pipeline.readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
                      var oracle = new BulkMassQuantizationOracle(new Vector3d(initialNativePose.position()),
                              new org.joml.Quaterniond(initialNativePose.orientation()), new Vector3d(tracker.getCenterOfMass()));
                     int authored = write(ready.plot(), extension, 12, 8, 4, Blocks.STONE.defaultBlockState(), () -> {
                         oracle.rebase(new Vector3d(frame.getMassTracker().getCenterOfMass()));
                         verifyOraclePose(helper, physics, frame, oracle);
                     });
                         var addedTrackerNow = frame.getMassTracker();
                        var afterPoint = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                        var afterWorld = new Vector3d(human.position().x, human.position().y, human.position().z);
                         var afterInterval = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                         logPassengerDiagnostics("after-add", ready, human, frame, beforePoint.storagePoint(), null, null);
                         var afterDeckWorld = frame.logicalPose().transformPosition(new Vector3d(fixedDeckPoint));
                         var afterPassengerWorld = frame.logicalPose().transformPosition(new Vector3d(beforePassengerLocal));
                          var predictedPose = oracle.logicalPose();
                          var predictedDeckWorld = predictedPose.transformPosition(new Vector3d(fixedDeckPoint));
                          var predictedPassengerWorld = predictedPose.transformPosition(new Vector3d(beforePassengerLocal));
                          double geometryBudget = geometryUlpBudget(afterDeckWorld, predictedDeckWorld, afterPassengerWorld, predictedPassengerWorld);
                          double deckResidual = afterDeckWorld.distance(predictedDeckWorld), passengerResidual = afterPassengerWorld.distance(predictedPassengerWorld);
                          com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[bulk-mass-passenger] edit=add tick={} blocks={} humanDistance={} deckDrift={} deckModelResidual={} passengerModelResidual={} geometryBudget={} bodyBefore=({},{},{}) bodyAfter=({},{},{}) deckBefore=({},{},{}) deckAfter=({},{},{}) mass={}->{} COM=({},{},{})-({}, {}, {}) tensorDelta={}",
                                  ready.level().getGameTime(), authored, beforeWorld.distance(afterWorld), beforeDeckWorld.distance(afterDeckWorld), deckResidual, passengerResidual, geometryBudget,
                                 beforeWorld.x, beforeWorld.y, beforeWorld.z, afterWorld.x, afterWorld.y, afterWorld.z,
                                 beforeDeckWorld.x, beforeDeckWorld.y, beforeDeckWorld.z, afterDeckWorld.x, afterDeckWorld.y, afterDeckWorld.z,
                                 baseMass, addedTrackerNow.getMass(), baseCom.x, baseCom.y, baseCom.z,
                                  addedTrackerNow.getCenterOfMass().x(), addedTrackerNow.getCenterOfMass().y(), addedTrackerNow.getCenterOfMass().z(),
                                 NativeMassChangeResponseGameTests.maxTensorDifference(baseTensor, addedTrackerNow.getInertiaTensor()));
                          if (afterPoint == null || !ready.plot().engineId().equals(afterPoint.frameId())
                                 || beforeWorld.distance(afterWorld) > 1.0e-6
                                  || deckResidual > geometryBudget || passengerResidual > geometryBudget) {
                             helper.fail("same-callback COM rebase/model mismatch: human=" + beforeWorld + "->" + afterWorld
                                    + " fixedDeck=" + beforeDeckWorld + "->" + afterDeckWorld
                                    + " fixedPassenger=" + beforePassengerWorld + "->" + afterPassengerWorld
                                    + " actor=" + beforePoint + "->" + afterPoint); return;
                        }
                        // COM-only pose rebase is projected as authority, so it cannot be admitted as
                        // inherited motion again by pending replay. This uses captured native poses/actor point.
                        helper.runAfterDelay(2, () -> {
                             var addedTracker = frame.getMassTracker();
                             var addedMass = addedTracker.getMass();
                             var addedCom = new Vector3d(addedTracker.getCenterOfMass());
                             var addedTensor = new org.joml.Matrix3d(addedTracker.getInertiaTensor());
                             if (!(addedMass > baseMass * 1.25) || addedCom.distance(baseCom) < 1.0
                                     || NativeMassChangeResponseGameTests.maxTensorDifference(baseTensor, addedTensor) <= 1.0) {
                                 helper.fail("post-physics ballast lacked meaningful mass/COM/tensor change: cells=" + authored
                                         + " mass=" + baseMass + "->" + addedMass + " COM=" + baseCom + "->" + addedCom); return;
                             }
                             var settledPoint = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                              if (settledPoint == null || !ready.plot().engineId().equals(settledPoint.frameId())) {
                                 helper.fail("native passenger support not fresh after ballast physics boundary"); return;
                             }
                             var intervalPoint = new Vector3d(human.position().x, human.position().y, human.position().z);
                             double dedupBound = Math.max(Math.ulp((float) intervalPoint.x), Math.max(
                                     Math.ulp((float) intervalPoint.y), Math.ulp((float) intervalPoint.z))) * 16.0;
                             if (beforeWorld.distance(intervalPoint) <= dedupBound) {
                                 helper.fail("moving fixture produced no physical passenger interval witness: " + beforeWorld + "->" + intervalPoint); return;
                             }
                             prediction.authorityProjected(afterPoint, epoch, afterInterval);
                             if (!prediction.allowNativeCarrier(true, epoch, settledPoint, NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose()))) {
                                 helper.fail("new physical native interval was not admitted after ballast edit"); return;
                             }
                             if (prediction.allowNativeCarrier(true, epoch, settledPoint, NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose()))) {
                                 helper.fail("repeated pending replay re-admitted the same physical interval"); return;
                             }
                              ready.owner().drainSnapshots();
                              var beforeRemoveSnapshot = ready.owner().latestSnapshot().get();
                              var beforeRemovePoint = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                              var beforeRemoveBodyWorld = new Vector3d(human.position().x, human.position().y, human.position().z);
                              var beforeRemoveBodyLocal = beforeRemovePoint == null ? null : vector(beforeRemovePoint.storagePoint());
                              logPassengerDiagnostics("before-remove", ready, human, frame,
                                      beforeRemovePoint == null ? null : beforeRemovePoint.storagePoint(), beforeRemoveSnapshot, null);
                              var beforeRemoveInterval = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                              var beforeRemoveLogicalPose = new dev.ryanhcode.sable.companion.math.Pose3d(frame.logicalPose());
                             if (beforeRemovePoint == null) { helper.fail("passenger actor point missing before removal"); return; }
                              prediction.authorityProjected(beforeRemovePoint, epoch, beforeRemoveInterval);
                              var beforeRemoveWorldAnchor = new Vector3d(beforeRemoveBodyWorld);
                              var removeNativePose = pipeline.readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
                              var removeOracle = new BulkMassQuantizationOracle(new Vector3d(removeNativePose.position()),
                                      new org.joml.Quaterniond(removeNativePose.orientation()), new Vector3d(addedTracker.getCenterOfMass()));
                                 int removed = write(ready.plot(), extension, 12, 8, 4, Blocks.AIR.defaultBlockState(), () -> {
                                     removeOracle.rebase(new Vector3d(frame.getMassTracker().getCenterOfMass()));
                                     verifyOraclePose(helper, physics, frame, removeOracle);
                                });
                                var removedTrackerNow = frame.getMassTracker();
                                var afterRemovePoint = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                                var afterRemoveBodyWorld = new Vector3d(human.position().x, human.position().y, human.position().z);
                                 var afterRemoveLogicalPose = new dev.ryanhcode.sable.companion.math.Pose3d(frame.logicalPose());
                                 var removalReferenceDeckPoint = new Vector3d(storage.getX() + 2.5, storage.getY() + 1.0, storage.getZ() + 2.5);
                                 var beforeRemoveDeckWorld = beforeRemoveLogicalPose.transformPosition(new Vector3d(removalReferenceDeckPoint));
                                 var afterRemoveDeckWorld = afterRemoveLogicalPose.transformPosition(new Vector3d(removalReferenceDeckPoint));
                                  var beforeRemovePassengerWorld = beforeRemoveLogicalPose.transformPosition(new Vector3d(beforeRemoveBodyLocal));
                                  var afterRemovePassengerWorld = afterRemoveLogicalPose.transformPosition(new Vector3d(beforeRemoveBodyLocal));
                                  var removePredictedPose = removeOracle.logicalPose();
                                  var predictedRemoveDeckWorld = removePredictedPose.transformPosition(new Vector3d(removalReferenceDeckPoint));
                                  var predictedRemovePassengerWorld = removePredictedPose.transformPosition(new Vector3d(beforeRemoveBodyLocal));
                                 double removalGeometryBudget = geometryUlpBudget(afterRemoveDeckWorld, predictedRemoveDeckWorld,
                                         afterRemovePassengerWorld, predictedRemovePassengerWorld);
                                 double removalDeckResidual = afterRemoveDeckWorld.distance(predictedRemoveDeckWorld);
                                 double removalPassengerResidual = afterRemovePassengerWorld.distance(predictedRemovePassengerWorld);
                                double removalBodyDistance = beforeRemoveBodyWorld.distance(afterRemoveBodyWorld);
                                 double removalDeckDistance = beforeRemoveDeckWorld.distance(afterRemoveDeckWorld);
                                 double removalPassengerDistance = beforeRemovePassengerWorld.distance(afterRemovePassengerWorld);
                                 final String[] immediateRemovalFailure = { null };
                                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[bulk-mass-passenger] edit=remove-immediate tick={} blocks={} bodyDistance={} deckDistance={} passengerGeometryDistance={} geometryBudget={} bodyBefore={} bodyAfter={} deckBefore={} deckAfter={} passengerBefore={} passengerAfter={} localBefore={} localAfter={} poseLast={} poseLogical={} mass={}->{} COM={} -> {} tensorDelta={}",
                                        ready.level().getGameTime(), removed, removalBodyDistance, removalDeckDistance, removalPassengerDistance,
                                        removalGeometryBudget, precise(beforeRemoveBodyWorld), precise(afterRemoveBodyWorld),
                                        precise(beforeRemoveDeckWorld), precise(afterRemoveDeckWorld), precise(beforeRemovePassengerWorld),
                                        precise(afterRemovePassengerWorld), precise(beforeRemoveBodyLocal),
                                        afterRemovePoint == null ? "none" : precise(afterRemovePoint.storagePoint()),
                                        precise(frame.lastPose().position()), precise(frame.logicalPose().position()), addedMass,
                                        removedTrackerNow.getMass(), precise(addedCom), precise(removedTrackerNow.getCenterOfMass()),
                                        NativeMassChangeResponseGameTests.maxTensorDifference(addedTensor, removedTrackerNow.getInertiaTensor()));
                                  if (removalBodyDistance > 1.0e-6
                                           || removalDeckResidual > removalGeometryBudget || removalPassengerResidual > removalGeometryBudget) {
                                     immediateRemovalFailure[0] = "same-callback removal continuity: bodyDistance=" + removalBodyDistance
                                             + " deckDistance=" + removalDeckDistance + " passengerDistance=" + removalPassengerDistance
                                              + " modelResiduals=" + removalDeckResidual + "," + removalPassengerResidual
                                              + " geometryBudget=" + removalGeometryBudget;
                                }
                               com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[bulk-mass-passenger] edit=remove tick={} blocks={} mass={}->{} COM=({},{},{})-({},{},{}) tensorDelta={}",
                                       ready.level().getGameTime(), removed, addedMass, removedTrackerNow.getMass(), addedCom.x, addedCom.y, addedCom.z,
                                        removedTrackerNow.getCenterOfMass().x(), removedTrackerNow.getCenterOfMass().y(), removedTrackerNow.getCenterOfMass().z(),
                                       NativeMassChangeResponseGameTests.maxTensorDifference(addedTensor, removedTrackerNow.getInertiaTensor()));
                             helper.runAfterDelay(2, () -> {
                                 var restored = frame.getMassTracker();
                                 if (restored == null || Math.abs(restored.getMass() - baseMass) > baseMass * .03
                                         || new Vector3d(restored.getCenterOfMass()).distance(baseCom) > 1.0
                                         || NativeMassChangeResponseGameTests.maxTensorDifference(baseTensor, restored.getInertiaTensor()) > 1.0) {
                                     helper.fail("post-physics ballast removal failed mass/COM/tensor round trip"); return;
                                 }
                                 var actualPoint = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                                 var actualPose = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose());
                                  var rebasedExpectedStorage = afterRemoveLogicalPose.transformPositionInverse(new Vector3d(beforeRemoveWorldAnchor));
                                  var priorWorldProjected = frame.logicalPose().transformPosition(new Vector3d(rebasedExpectedStorage));
                                 ready.owner().drainSnapshots();
                                 var finalSnapshot = ready.owner().latestSnapshot().get();
                                 logPassengerDiagnostics("final", ready, human, frame, beforeRemovePoint.storagePoint(), finalSnapshot,
                                         "bodyWorldBefore=" + beforeRemoveBodyWorld + " bodyLocalBefore=" + beforeRemoveBodyLocal
                                                 + " bodyWorldNow=" + human.position());
                              if (actualPoint == null || !ready.plot().engineId().equals(actualPoint.frameId())
                                        || !prediction.allowNativeCarrier(true, epoch, actualPoint, actualPose)) {
                                    helper.fail("next physical native interval was not admitted after removal"); return;
                                }
                                // The projected prior local point is the independent carrier reference; allow only
                                // actual subsequent physics movement, and require a fresh native support witness.
                                var nativeInfo = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)(Object)human).sable$getCollisionInfo();
                                  double residualBudget = Math.max(Math.ulp((float) priorWorldProjected.x), Math.max(
                                          Math.ulp((float) priorWorldProjected.y), Math.ulp((float) priorWorldProjected.z))) * 16.0;
                                  com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[bulk-mass-passenger] edit=remove-final projected={} body={} residualBudget={} immediateRemovalFailure={}",
                                          priorWorldProjected, human.position(), residualBudget, immediateRemovalFailure[0]);
                                  if (immediateRemovalFailure[0] != null) {
                                      helper.fail(immediateRemovalFailure[0]); return;
                                  }
                                  if (nativeInfo == null || nativeInfo.trackingSubLevel != frame || human.position().distanceToSqr(
                                         new net.minecraft.world.phys.Vec3(priorWorldProjected.x, priorWorldProjected.y, priorWorldProjected.z)) > residualBudget * residualBudget) {
                                     helper.fail("passenger lost native identity/support or diverged from independently projected carrier point: projected="
                                             + priorWorldProjected + " body=" + human.position() + " point=" + actualPoint
                                             + " residualBudget=" + residualBudget); return;
                                }
                                helper.succeed();
                            });
                        });
                });
    }

    @net.minecraft.gametest.framework.GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=200)
    public static void bulkRemovalOfActualPassengerSupportReleasesRatherThanCarries(GameTestHelper helper) {
        // Match the known-clear ordinary cabin-floor placement; support cells are selected from the actual actor point below.
        MovingCabinAuthorityFixture.start(helper, new BlockPos(4200, 300, 1800), "bulk-mass-support-loss", 2.5, 1, 2.5,
                (ready, human) -> {
                    var point = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                    if (point == null || !ready.plot().engineId().equals(point.frameId())) {
                        helper.fail("passenger lacks actual native cabin support tracking"); return;
                    }
                    // Remove every support layer below the actor's witnessed current floor in one callback: this is intentionally
                    // distinct from the intact-floor mass/COM round-trip above.
                    helper.runAfterDelay(1, () -> {
                        var current = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                        if (current == null) { helper.fail("fresh passenger actor point missing before support edit"); return; }
                         var local = vector(current.storagePoint());
                         int floorY = (int) Math.floor(local.y - .01);
                         var nativeInfoBefore = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)(Object)human).sable$getCollisionInfo();
                         if (!human.onGround() || nativeInfoBefore == null || nativeInfoBefore.trackingSubLevel != ready.plot().subLevel()) {
                             helper.fail("support-loss baseline lacks grounded native floor contact"); return;
                         }
                        int centerX = net.minecraft.core.BlockPos.containing(local.x, floorY + 1.0e-4, local.z).getX();
                        int centerZ = net.minecraft.core.BlockPos.containing(local.x, floorY + 1.0e-4, local.z).getZ();
                         // The body can straddle a cell boundary; clear a 3x3 footprint and every layer down to cabin origin.
                         int minX = centerX - 1, maxX = centerX + 1, minZ = centerZ - 1, maxZ = centerZ + 1;
                        if (floorY >= ready.cabin().origin().getY() + ShuttleCabinFixture.HEIGHT || centerX < ready.cabin().origin().getX()
                                || centerX >= ready.cabin().origin().getX() + ShuttleCabinFixture.WIDTH
                                || centerZ < ready.cabin().origin().getZ() || centerZ >= ready.cabin().origin().getZ() + ShuttleCabinFixture.DEPTH) {
                            helper.fail("passenger local support coordinate outside authored cabin: " + local); return;
                        }
                         int removed = 0;
                         for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                          for (int y = floorY; y >= ready.cabin().origin().getY(); y--) {
                             BlockPos cell = new BlockPos(x, y, z);
                             var state = LoadedFrameRayQuery.knownServerBlock(ready.level(), ready.plot().subLevel(), cell);
                             if (state == null) { helper.fail("unknown support read at " + cell); return; }
                              if (y == floorY && x == centerX && z == centerZ) {
                                  var collision = state.getCollisionShape(ready.plot().subLevel().getLevel(), cell);
                                  if (!state.is(ModBlocks.STEEL_WALL_BLOCK.get()) || collision.isEmpty()
                                          || nativeInfoBefore == null || !nativeInfoBefore.verticalCollisionBelow) {
                                      helper.fail("actual center authored floor lacks resolved steel-wall collision/native support at " + cell
                                              + ": material=" + state + " native=" + nativeInfoBefore); return;
                                  }
                              }
                             if (state.isAir()) continue;
                             if (!ready.plot().setStorageBlock(cell, Blocks.AIR.defaultBlockState())) { helper.fail("deck removal rejected at " + cell); return; }
                             removed++;
                          }
                        }
                        if (removed == 0) { helper.fail("support removal selected no physical footprint cells"); return; }
                        final int removedCount = removed;
                         var originalLocal = new Vector3d(local);
                        helper.runAfterDelay(6, () -> {
                            var actual = LoadedFrameRayQuery.currentActorPoint(ready.level(), human);
                            var nowLocal = actual != null && ready.plot().engineId().equals(actual.frameId())
                                    ? vector(actual.storagePoint()) : null;
                            var nowPose = ready.plot().subLevel().logicalPose();
                            var projected = nowLocal == null ? null : nowPose.transformPosition(new Vector3d(nowLocal));
                             var info = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)(Object)human).sable$getCollisionInfo();
                              boolean oldFrame = actual != null && ready.plot().engineId().equals(actual.frameId());
                             boolean freshWorldContact = !oldFrame && human.onGround() && info != null && info.trackingSubLevel == null;
                              var currentReference = nowPose.transformPosition(new Vector3d(originalLocal));
                              boolean fellBelowReference = human.position().y < currentReference.y - .1;
                             if (oldFrame || !fellBelowReference || !freshWorldContact && human.onGround()) {
                                helper.fail("removed physical floor did not release passenger into legitimate fall; removed=" + removedCount
                                        + " floorY=" + floorY + " body=" + human.position() + " local=" + nowLocal
                                             + " projected=" + projected + " onGround=" + human.onGround() + " point=" + actual + " nativeInfo=" + info); return;
                            }
                            helper.succeed();
                        });
                    });
                });
    }

    private static int write(SableFixturePlot plot, BlockPos origin, int width, int height, int depth,
                             net.minecraft.world.level.block.state.BlockState state) {
        return write(plot, origin, width, height, depth, state, () -> { });
    }

    private static int write(SableFixturePlot plot, BlockPos origin, int width, int height, int depth,
                             net.minecraft.world.level.block.state.BlockState state, Runnable afterCell) {
        int count = 0;
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) {
            if (!plot.setStorageBlock(origin.offset(x, y, z), state)) throw new IllegalStateException("loaded bulk write rejected");
            afterCell.run();
            count++;
        }
        return count;
    }

    private static void verifyOraclePose(GameTestHelper helper,
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics,
            dev.ryanhcode.sable.sublevel.ServerSubLevel frame, BulkMassQuantizationOracle oracle) {
        var actual = physics.getPipeline().readPose(frame, new dev.ryanhcode.sable.companion.math.Pose3d());
        var expectedPosition = oracle.position();
        var actualPosition = new Vector3d(actual.position());
        var expectedRotation = oracle.rotation();
        var actualRotation = new org.joml.Quaterniond(actual.orientation());
        double positionError = actualPosition.distance(expectedPosition);
        double positionAllowance = 32.0 * Math.max(Math.ulp(expectedPosition.x), Math.max(
                Math.ulp(expectedPosition.y), Math.ulp(expectedPosition.z)));
        // Compare quaternion components rather than a dot-product (which assumes unit quaternions).
        // Sign is equivalent; a larger difference is a real pose change, not an updated oracle input.
        double direct = quaternionDifference(expectedRotation, actualRotation, false);
        double negated = quaternionDifference(expectedRotation, actualRotation, true);
        double rotationError = Math.min(direct, negated);
        double rotationAllowance = 32.0 * Math.max(Math.ulp(expectedRotation.x), Math.max(
                Math.ulp(expectedRotation.y), Math.max(Math.ulp(expectedRotation.z), Math.ulp(expectedRotation.w))));
        if (positionError > positionAllowance || rotationError > rotationAllowance) {
            throw new IllegalStateException("bulk independent stock oracle mismatch at rebase " + oracle.rebases()
                    + ": expected=" + precise(expectedPosition) + ", actual=" + precise(actualPosition)
                    + ", logical=" + precise(oracle.logicalPosition()) + ", COM=" + precise(oracle.centerOfMass())
                    + ", expectedQuaternion=" + java.util.Arrays.toString(new double[]{expectedRotation.x, expectedRotation.y, expectedRotation.z, expectedRotation.w})
                    + ", actualQuaternion=" + java.util.Arrays.toString(new double[]{actualRotation.x, actualRotation.y, actualRotation.z, actualRotation.w})
                    + ", positionError=" + positionError + " allowance=" + positionAllowance
                    + ", rotationError=" + rotationError + " allowance=" + rotationAllowance);
        }
    }

    private static double quaternionDifference(org.joml.Quaterniondc expected, org.joml.Quaterniondc actual, boolean negate) {
        double sign = negate ? -1.0 : 1.0;
        return Math.max(Math.abs(expected.x() - sign * actual.x()), Math.max(Math.abs(expected.y() - sign * actual.y()),
                Math.max(Math.abs(expected.z() - sign * actual.z()), Math.abs(expected.w() - sign * actual.w()))));
    }

    private static Vector3d vector(net.minecraft.world.phys.Vec3 value) {
        return new Vector3d(value.x, value.y, value.z);
    }

    private static double geometryUlpBudget(Vector3d... points) {
        double ulp = 0;
        for (var point : points) ulp = Math.max(ulp, Math.max(Math.ulp((float) point.x),
                Math.max(Math.ulp((float) point.y), Math.ulp((float) point.z))));
        return 16.0 * ulp;
    }

    private static void logPassengerDiagnostics(String stage, MovingCabinAuthorityFixture.Ready ready,
            net.minecraft.world.entity.LivingEntity human, dev.ryanhcode.sable.sublevel.ServerSubLevel frame,
            net.minecraft.world.phys.Vec3 expectedLocal,
            com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot snapshot,
            String extra) {
        var ext = (dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)(Object) human;
        var info = ext.sable$getCollisionInfo();
        var local = expectedLocal == null ? null : vector(expectedLocal);
        var projected = local == null ? null : frame.logicalPose().transformPosition(new Vector3d(local));
        var body = new Vector3d(human.position().x, human.position().y, human.position().z);
        var residual = projected == null ? null : new Vector3d(body).sub(projected);
        var frameState = snapshot == null ? null : snapshot.frameState().orElse(null);
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[bulk-mass-passenger] stage={} tick={} bodyTick={} expectedLocal={} projected={} body={} residual={} norm={} nativeInfo={} trackingMatches={} below={} horizontal={} onGround={} motion={} inheritedMotion={} poseLast={} poseLogical={} snapshot={} frameState={} snapshotWorld={} extra={}",
                stage, ready.level().getGameTime(), human.tickCount, precise(local), precise(projected), precise(body), precise(residual),
                residual == null ? "none" : residual.length(), info,
                info != null && info.trackingSubLevel == frame,
                info != null && info.verticalCollisionBelow, info != null && info.horizontalCollision,
                human.onGround(), info == null ? "none" : precise(info.motion), info == null ? "none" : precise(info.inheritedMotion),
                precise(frame.lastPose().position()), precise(frame.logicalPose().position()), snapshot,
                frameState, snapshot == null ? "none" : precise(new Vector3d(snapshot.x(), snapshot.y(), snapshot.z())), extra);
    }

    private static String precise(org.joml.Vector3dc value) {
        return value == null ? "none" : java.util.Arrays.toString(new double[]{value.x(), value.y(), value.z()});
    }

    private static String precise(net.minecraft.world.phys.Vec3 value) {
        return value == null ? "none" : java.util.Arrays.toString(new double[]{value.x, value.y, value.z});
    }
}
