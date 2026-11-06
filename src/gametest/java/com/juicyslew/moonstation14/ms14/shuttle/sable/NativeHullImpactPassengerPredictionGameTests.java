package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.client.ClientFramePredictionState;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real bound-human observation during the shared native hull/terrain impact trajectory. */
@GameTestTags({"shuttle-impact-passenger-prediction"})
@PrefixGameTestTemplate(false)
public final class NativeHullImpactPassengerPredictionGameTests {
    private static final int PLOT_X = 1800;
    private NativeHullImpactPassengerPredictionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 1200)
    public static void nativeImpactPassengerAndPredictionBasis(GameTestHelper helper) {
        var state = new PassengerRun(helper.getLevel());
        HullTerrainImpactGameTests.furnishedTwentyMetresPerSecond(helper, PLOT_X,
                (plot, launch) -> state.spawnAndWait(helper, plot, launch, 100),
                state::sample, state::beforeSuccess);
    }

    private static final class PassengerRun {
        private final ServerLevel level;
        private final GroundedHarnessWorldStep step = GroundedHarnessWorldStep.observingNativeGeometry();
        private final ClientFramePredictionState prediction = new ClientFramePredictionState();
        private com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body;
        private long lastStepTick = Long.MIN_VALUE;
        private int samples;
        private int supportedSamples;
        private int impactAdjacentSamples;
        private int admittedIntervals;
        private java.util.UUID committedFrame;
        private Vec3 lastWorldReference;
        private double previousHullVy = Double.NaN;
        private SableFixturePlot plot;
        PassengerRun(ServerLevel level) { this.level = level; }

        void spawnAndWait(GameTestHelper helper, SableFixturePlot plot, Runnable launch, int remaining) {
            this.plot = plot;
            if (body == null) {
                // Cabin room is authored above the known plain floor, away from the fitted machinery.
                Vec3 local = new Vec3(plot.storageOrigin().getX() + 3.5,
                        plot.storageOrigin().getY() + 1.01, plot.storageOrigin().getZ() + 3.5);
                var world = plot.subLevel().logicalPose().transformPosition(new org.joml.Vector3d(local.x, local.y, local.z));
                Vec3 feet = new Vec3(world.x, world.y, world.z);
                body = BoundHumanFixture.create(level, feet, "native-impact-passenger");
            }
            long tick = level.getServer().getTickCount();
            if (lastStepTick != tick) {
                lastStepTick = tick;
                step.step(body, 0, 0, false, false, 0f);
            }
            LoadedFrameRayQuery.ActorPoint point = LoadedFrameRayQuery.currentActorPoint(level, body);
            if (body.onGround() && point != null && plot.engineId().equals(point.frameId())) {
                helper.runAfterDelay(1, launch);
                return;
            }
            if (remaining <= 0) throw new GameTestAssertException("bound passenger did not naturally settle on the hull floor; point=" + point + " grounded=" + body.onGround());
            helper.runAfterDelay(1, () -> spawnAndWait(helper, plot, launch, remaining - 1));
        }

        void sample(HullTerrainImpactProbe.Observation hull) {
            samples++;
            long tick = level.getServer().getTickCount();
            if (lastStepTick == tick) return;
            lastStepTick = tick;
            double previousObservedHullVy = previousHullVy;
            previousHullVy = hull.linearVelocity().y();
            Vec3 before = body.position();
            AABB preMoveBounds = body.getBoundingBox();
            boolean wasGrounded = body.onGround();
            var beforePoint = LoadedFrameRayQuery.currentActorPoint(level, body);
            var container = SubLevelContainer.getContainer(level);
            var beforeFrame = beforePoint != null && beforePoint.frameId() != null
                    ? container.getSubLevel(beforePoint.frameId()) : null;
            var beforeLastPose = beforeFrame != null ? new dev.ryanhcode.sable.companion.math.Pose3d(beforeFrame.lastPose()) : null;
            var beforeLogicalPose = beforeFrame != null ? new dev.ryanhcode.sable.companion.math.Pose3d(beforeFrame.logicalPose()) : null;
            final SableOwnedBodyMotion.MoveResult[] move = {null};
            final Vec3[] bridgeEndpoint = {null};
            var result = step.step(body, 0, 0, false, false, 0f, value -> {
                move[0] = value;
                bridgeEndpoint[0] = body.position();
            });
            if (result.isEmpty() || move[0] == null) throw new GameTestAssertException("native passenger body step did not produce a move result");
            var afterPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
            var nativeMove = move[0];
            boolean supported = body.onGround() && nativeMove.collisionInfoFresh()
                    && beforePoint != null && afterPoint != null && beforePoint.frameId() != null
                    && plot.engineId().equals(beforePoint.frameId())
                    && java.util.Objects.equals(beforePoint.frameId(), afterPoint.frameId())
                    && java.util.Objects.equals(beforePoint.frameId(), nativeMove.beforePoint() == null ? null : nativeMove.beforePoint().frameId())
                    && java.util.Objects.equals(beforePoint.frameId(), nativeMove.afterPoint() == null ? null : nativeMove.afterPoint().frameId());
            if (supported) {
                supportedSamples++;
                // The motor's accepted own displacement is collision-resolved; never compare its
                // bounded carrier-inclusive result to the actor endpoint as an own-motion oracle.
                var frame = SubLevelContainer.getContainer(level).getSubLevel(beforePoint.frameId());
                if (!(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverFrame)
                        || frame != plot.subLevel() || nativeMove.collisionScope() == null
                        || !nativeMove.collisionScope().nativeCollisionTraceComplete())
                    throw new GameTestAssertException("supported passenger lacks complete live native frame trace");
                var info = nativeMove.collisionInfo();
                if (!(info instanceof dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo collision)
                        || collision.motion == null || !collision.verticalCollision || !collision.verticalCollisionBelow
                        || collision.horizontalCollision || collision.subLevelHorizontalCollision)
                    throw new GameTestAssertException("supported passenger sample is not native floor-only contact");
                var trace = nativeMove.collisionScope().nativeCollisionTrace();
                if (!SableOwnedBodyMotion.nativeFloorTraceAdmissible(nativeMove.requestedOwnDisplacement(),
                        collision.motion.x, collision.motion.y, collision.motion.z, trace, beforePoint.frameId(), row -> {
                            if (row.selectedPos() == null || row.selectedState() == null) return false;
                            var actual = LoadedFrameRayQuery.knownServerBlock(level, serverFrame, row.selectedPos());
                            return actual != null && actual.equals(row.selectedState());
                        })) throw new GameTestAssertException("native floor trace failed independent own-motion certification");
                Vec3 own = new Vec3(collision.motion.x, collision.motion.y, collision.motion.z);
                Vec3 vanillaAppliedOwn = vanillaAppliedOwn(own);
                Vec3 ownPoint = before.add(vanillaAppliedOwn);
                // Sable's inherited-motion anchor is the original feet position: collisionMotion
                // (own displacement) is accumulated separately and is not part of carrier projection.
                Vec3 storagePoint = beforeLastPose.transformPositionInverse(before);
                Vec3 carrierReference = beforeLogicalPose.transformPosition(storagePoint);
                Vec3 expected = carrierReference.add(vanillaAppliedOwn);
                boolean independentlyCertifiedNegativeIdle = certifiedNegativeIdleSuppression(
                        before, preMoveBounds, wasGrounded, body.onGround(), carrierReference,
                        vanillaAppliedOwn, nativeMove, collision, serverFrame, trace, beforePoint, afterPoint);
                if (independentlyCertifiedNegativeIdle) expected = carrierReference;
                Vec3 actual = body.position();
                // SDK inverse/forward rigid projection is bounded to 32 operand-scaled ULPs,
                // plus the adapter's existing residual allowance. Include the intermediate
                // world-to-storage result: at this plot origin its ULPs exceed the world ULPs.
                double budget = com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                        + 32 * Math.max(Math.max(Math.max(Math.max(Math.ulp(before.x), Math.ulp(ownPoint.x)), Math.ulp(storagePoint.x)), Math.ulp(carrierReference.x)), Math.ulp(expected.x));
                double budgetY = com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                        + 32 * Math.max(Math.max(Math.max(Math.max(Math.ulp(before.y), Math.ulp(ownPoint.y)), Math.ulp(storagePoint.y)), Math.ulp(carrierReference.y)), Math.ulp(expected.y));
                double budgetZ = com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                        + 32 * Math.max(Math.max(Math.max(Math.max(Math.ulp(before.z), Math.ulp(ownPoint.z)), Math.ulp(storagePoint.z)), Math.ulp(carrierReference.z)), Math.ulp(expected.z));
                if (independentlyCertifiedNegativeIdle) {
                    var suppression = nativeMove.idleProbeSuppressionDisplacement();
                    Vec3 expectedSuppression = vanillaAppliedOwn.scale(-1);
                    if (!nativeMove.idleSupportProbeCertified() || suppression == null
                            || Math.abs(suppression.x()-expectedSuppression.x)>budget
                            || Math.abs(suppression.y()-expectedSuppression.y)>budgetY
                            || Math.abs(suppression.z()-expectedSuppression.z)>budgetZ)
                        throw new GameTestAssertException("certified negative idle probe suppression disagrees with independently projected own response");
                }
                if (Math.abs(expected.x-actual.x)>budget || Math.abs(expected.y-actual.y)>budgetY
                        || Math.abs(expected.z-actual.z)>budgetZ) {
                    logEndpointAccounting(tick, body, before, own, vanillaAppliedOwn, ownPoint, carrierReference, expected, actual,
                            bridgeEndpoint[0], beforePoint, beforeLastPose, beforeLogicalPose, nativeMove,
                            collision, budget, budgetY, budgetZ);
                    throw new GameTestAssertException("fresh supported passenger endpoint diverged from certified own+carrier projection: expected=" + expected + " actual=" + body.position());
                }
                boolean nearSupport = HullTerrainGeometry.nearSupportSurface(hull.hullBounds(), -47, .15, .15);
                boolean descending = hull.linearVelocity().y() < -0.5;
                boolean previousDescentDecelerated = previousObservedHullVy < -0.5
                        && hull.linearVelocity().y() > previousObservedHullVy;
                if (nearSupport && hull.contactPos() != null && (descending || previousDescentDecelerated)) impactAdjacentSamples++;
            }
            if (afterPoint == null) {
                prediction.pause();
                return;
            }
            if (afterPoint.frameId() == null) {
                prediction.ordinaryWorldAuthority(afterPoint);
                committedFrame = null;
                lastWorldReference = null;
                return;
            }
            var actualFrame = container.getSubLevel(afterPoint.frameId());
            var actualPoses = actualFrame != null
                    ? NativeFrameInterval.PosePair.capture(new dev.ryanhcode.sable.companion.math.Pose3d(actualFrame.lastPose()),
                    new dev.ryanhcode.sable.companion.math.Pose3d(actualFrame.logicalPose())) : null;
            if (actualPoses == null) { prediction.pause(); return; }
            boolean sameAuthority = java.util.Objects.equals(committedFrame, afterPoint.frameId())
                    && prediction.velocityBasis() != null
                    && java.util.Objects.equals(prediction.velocityBasisFrame(), afterPoint.frameId());
            if (!sameAuthority) {
                prediction.authorityProjected(afterPoint, 1, actualPoses);
                committedFrame = afterPoint.frameId();
            } else {
                boolean admitted = prediction.allowNativeCarrier(true, 1, afterPoint, actualPoses);
                if (admitted) admittedIntervals++;
                else if (lastWorldReference != null && actualFrame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel) {
                    var currentReference = actualFrame.logicalPose().transformPosition(new org.joml.Vector3d(
                            plot.storageOrigin().getX(), plot.storageOrigin().getY(), plot.storageOrigin().getZ()));
                    if (lastWorldReference.distanceTo(new Vec3(currentReference.x, currentReference.y, currentReference.z)) > 1e-6)
                        throw new GameTestAssertException("advanced native frame interval was not admitted");
                }
                prediction.authorityProjected(afterPoint, 1, actualPoses);
                if (prediction.allowNativeCarrier(false, 1, afterPoint, actualPoses)
                        || prediction.allowNativeCarrier(true, 1, afterPoint, actualPoses))
                    throw new GameTestAssertException("committed interval was re-admitted for replay/live prediction");
            }
            if (actualFrame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel)
                lastWorldReference = new Vec3(actualFrame.logicalPose().transformPosition(new org.joml.Vector3d(
                        plot.storageOrigin().getX(), plot.storageOrigin().getY(), plot.storageOrigin().getZ())).x,
                        actualFrame.logicalPose().transformPosition(new org.joml.Vector3d(plot.storageOrigin().getX(), plot.storageOrigin().getY(), plot.storageOrigin().getZ())).y,
                        actualFrame.logicalPose().transformPosition(new org.joml.Vector3d(plot.storageOrigin().getX(), plot.storageOrigin().getY(), plot.storageOrigin().getZ())).z);
        }

        private boolean certifiedNegativeIdleSuppression(Vec3 before, AABB preMoveBounds,
                boolean wasGrounded, boolean groundedAfter, Vec3 carrierReference, Vec3 appliedOwn,
                SableOwnedBodyMotion.MoveResult move,
                dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo collision,
                dev.ryanhcode.sable.sublevel.ServerSubLevel frame,
                java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
                LoadedFrameRayQuery.ActorPoint beforePoint, LoadedFrameRayQuery.ActorPoint afterPoint) {
            if (!wasGrounded || !groundedAfter || !move.idleSupportProbeAttested() || !move.collisionInfoFresh()
                    || collision.motion == null || !collision.verticalCollision || !collision.verticalCollisionBelow
                    || collision.horizontalCollision || collision.subLevelHorizontalCollision
                    || !java.util.Objects.equals(beforePoint.frameId(), afterPoint.frameId())
                    || !frame.getUniqueId().equals(beforePoint.frameId())
                    || move.requestedOwnDisplacement().x()!=0 || move.requestedOwnDisplacement().z()!=0
                    || move.requestedOwnDisplacement().y()>=0 || move.measuredOwnDisplacement().y()>=0
                    || appliedOwn.y()>=0 || trace == null || trace.isEmpty()) return false;
            var proof = NativeSideContactProof.validateNegativeIdleSupportProbe(move.requestedOwnDisplacement(),
                    new com.juicyslew.moonstation14.ms14.movement.MovementVector(
                            collision.motion.x, collision.motion.y, collision.motion.z),
                    move.measuredOwnDisplacement(), frame.getUniqueId(), trace, row -> {
                        if (row.selectedPos()==null || row.selectedState()==null) return false;
                        var actual = LoadedFrameRayQuery.knownServerBlock(level, frame, row.selectedPos());
                        return actual != null && actual.equals(row.selectedState());
                    });
            if (proof.isEmpty()) return false;
            Vec3 projectedCarrier = carrierReference.subtract(before);
            AABB candidate = preMoveBounds.move(projectedCarrier);
            var yaw = NativeActorYaw.capture(body, move.collisionScope()).orElse(null);
            return yaw != null
                    && NativeFinalFootClearance.classifyCandidateBox(level, body, candidate, yaw)
                            == NativeFinalFootClearance.Result.CLEAR
                    && NativeIdleProbeEndpointSupport.supports(level, body, candidate, yaw, frame, trace);
        }

        // Minecraft 1.21.1 / NeoForge 21.1.228 Entity.move writes the clipped position only
        // when clip.lengthSqr() > 1.0E-7 (verified in the mapped runtime bytecode).
        private static Vec3 vanillaAppliedOwn(Vec3 certifiedNativeMotion) {
            return certifiedNativeMotion.lengthSqr() > 1.0E-7 ? certifiedNativeMotion : Vec3.ZERO;
        }

        private static void logEndpointAccounting(long tick,
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                Vec3 before, Vec3 own, Vec3 vanillaAppliedOwn, Vec3 ownPoint, Vec3 carrierReference, Vec3 expected, Vec3 actual, Vec3 bridge,
                LoadedFrameRayQuery.ActorPoint point,
                dev.ryanhcode.sable.companion.math.Pose3d lastPose,
                dev.ryanhcode.sable.companion.math.Pose3d logicalPose,
                SableOwnedBodyMotion.MoveResult move,
                dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.CollisionInfo collision,
                double bx, double by, double bz) {
            var response = new double[]{0, 0, 0};
            int count = 0;
            var rows = move.collisionScope().nativeCollisionTrace();
            CurrentFrameCollisionScope.NativeCollisionTrace last = null;
            if (rows != null) for (var row : rows) {
                response[0] += row.responseX(); response[1] += row.responseY(); response[2] += row.responseZ();
                count++; last = row;
            }
            var measured = move.measuredOwnDisplacement();
            var suppressed = move.idleProbeSuppressionDisplacement();
            double[] reconstructed = {bridge.x-before.x-measured.x()-suppressed.x(),
                    bridge.y-before.y-measured.y()-suppressed.y(), bridge.z-before.z-measured.z()-suppressed.z()};
            System.out.println("[impact-passenger-accounting] tick=" + tick + " body=" + body.getUUID()
                    + " frame=" + (point == null ? null : point.frameId()) + " beforeWorld=" + vec(before)
                    + " requestedOwn=" + vec(move.requestedOwnDisplacement()) + " rawNativeMotion=" + vec(own)
                    + " vanillaAppliedOwn=" + vec(vanillaAppliedOwn)
                    + " traceResponseSum=" + arr(response) + " traceRows=" + count + " traceLast="
                    + (last == null ? "null" : last.selectedPos()+"/"+last.substep()+"/"+last.iteration())
                    + " requestedPlusResponse=" + arr(new double[]{move.requestedOwnDisplacement().x()+response[0],move.requestedOwnDisplacement().y()+response[1],move.requestedOwnDisplacement().z()+response[2]})
                    + " measuredOwn=" + vec(measured) + " acceptedOwn=" + vec(move.acceptedOwnDisplacement())
                    + " boundedOwn=" + vec(move.boundedOwnDisplacement()) + " bridgeEndpoint=" + vec(bridge)
                     + " finalEndpoint=" + vec(actual) + " expectedOwnPoint=" + vec(ownPoint) + " sdkPreOwnAnchorCarrierReference=" + vec(carrierReference)
                     + " sdkExpectedFinal=" + vec(expected) + " sdkCarrierDelta=" + vec(carrierReference.subtract(before)) + " nativeInheritedMotion=" + vec(collision.inheritedMotion)
                    + " reconstructed-applied-carrier=" + arr(reconstructed) + " idleSuppression=" + vec(suppressed)
                    + " idleAttested=" + move.idleSupportProbeAttested() + " idleCertified=" + move.idleSupportProbeCertified()
                    + " positionCorrection=" + move.positionCorrection() + " actualFinalMinusBridge=" + vec(actual.subtract(bridge))
                    + " actualResidual=" + vec(actual.subtract(expected)) + " budgets=" + arr(new double[]{bx,by,bz})
                    + " lastPosePosition=" + posePosition(lastPose) + " logicalPosePosition=" + posePosition(logicalPose)
                    + " traceComplete=" + move.collisionScope().nativeCollisionTraceComplete());
        }
        private static String vec(Vec3 v) { return v == null ? "null" : arr(new double[]{v.x,v.y,v.z}); }
        private static String vec(com.juicyslew.moonstation14.ms14.movement.MovementVector v) { return v == null ? "null" : arr(new double[]{v.x(),v.y(),v.z()}); }
        private static String arr(double[] v) { return "["+Double.toString(v[0])+","+Double.toString(v[1])+","+Double.toString(v[2])+"]"; }
        private static String posePosition(dev.ryanhcode.sable.companion.math.Pose3d p) {
            if (p == null) return "null";
            var v = p.position(); return arr(new double[]{v.x,v.y,v.z});
        }

        void beforeSuccess() {
            if (samples == 0 || supportedSamples == 0 || impactAdjacentSamples == 0 || admittedIntervals == 0)
                throw new GameTestAssertException("non-vacuity failure: no impact-adjacent fresh supported passenger observation and physical native interval admission; samples=" + samples + " supported=" + supportedSamples + " adjacent=" + impactAdjacentSamples + " admitted=" + admittedIntervals);
        }
    }
}
