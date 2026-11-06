package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.doors.AirlockBumpSystem;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import java.util.UUID;

/** Native collision move boundary for the existing human and corpse motors. */
public final class SableOwnedBodyMotion {
    private SableOwnedBodyMotion() { }

    /** Carries from this exact native collision result, while returning only accepted motor motion. */
    /** measuredOwnDisplacement is the raw position delta immediately after native own move, before slop normalization or carrier application. */
    public record MoveResult(MovementVector requestedOwnDisplacement, MovementVector acceptedOwnDisplacement, MovementVector measuredOwnDisplacement,
                              MovementVector frameVelocityDeltaPerSecond,
                              NativeNormalContactPolicy.Target normalTarget,
                             boolean collisionInfoFresh, LoadedFrameRayQuery.ActorPoint beforePoint,
                              LoadedFrameRayQuery.ActorPoint afterPoint,
                              SubLevelEntityCollision.CollisionInfo collisionInfo,
                              CollisionPosePolicy collisionPosePolicy,
                                HorizontalContactEvidence horizontalContactEvidence,
                                CurrentFrameCollisionScope.ScopedResult<Void> collisionScope,
                                 ContactPositionCorrectionEvidence positionCorrection,
                                  MovementVector boundedOwnDisplacement,
                                   boolean idleSupportProbeAttested,
                                   boolean idleSupportProbeCertified,
                                   MovementVector idleProbeSuppressionDisplacement,
                                    NativeContactProofDiagnostic nativeContactProofDiagnostic,
                                    NativeNormalContactFinalizer.LandingWitness landingWitness) {
        public MovementCollisionResolver.CollisionResult toCollisionResult(boolean onGround) {
            return new MovementCollisionResolver.CollisionResult(boundedOwnDisplacement, onGround,
                    horizontalContactEvidence, positionCorrection);
        }
    }

    /** Per-call authority choice; server defaults preserve the existing native collision bridge. */
    public enum CollisionPosePolicy { STOCK_NATIVE_INTERVAL, CURRENT_FRAME_GEOMETRY }
    public enum BodyPosePolicy { STOCK_SDK, TRACKED_CHARACTER }

    public record MoveOptions(boolean applyNativeInheritedMotion,
                               LoadedFrameRayQuery.ActorPoint velocityBasis,
                               CollisionPosePolicy collisionPosePolicy,
                                BodyPosePolicy bodyPosePolicy,
                                boolean observeGeometry,
                                boolean allowContactTarget) {
        public MoveOptions {
            java.util.Objects.requireNonNull(collisionPosePolicy);
            java.util.Objects.requireNonNull(bodyPosePolicy);
        }
        public MoveOptions(boolean applyNativeInheritedMotion, LoadedFrameRayQuery.ActorPoint velocityBasis,
                           CollisionPosePolicy collisionPosePolicy) {
            this(applyNativeInheritedMotion, velocityBasis, collisionPosePolicy, BodyPosePolicy.STOCK_SDK, false, true);
        }
        public MoveOptions(boolean applyNativeInheritedMotion, LoadedFrameRayQuery.ActorPoint velocityBasis,
                           CollisionPosePolicy collisionPosePolicy, BodyPosePolicy bodyPosePolicy) {
            this(applyNativeInheritedMotion, velocityBasis, collisionPosePolicy, bodyPosePolicy, false, true);
        }
        public MoveOptions(boolean applyNativeInheritedMotion, LoadedFrameRayQuery.ActorPoint velocityBasis,
                           CollisionPosePolicy collisionPosePolicy, BodyPosePolicy bodyPosePolicy, boolean observeGeometry) {
            this(applyNativeInheritedMotion, velocityBasis, collisionPosePolicy, bodyPosePolicy, observeGeometry, true);
        }
        public MoveOptions(boolean applyNativeInheritedMotion, LoadedFrameRayQuery.ActorPoint velocityBasis) {
            this(applyNativeInheritedMotion, velocityBasis, CollisionPosePolicy.STOCK_NATIVE_INTERVAL, BodyPosePolicy.STOCK_SDK);
        }
        public static MoveOptions serverDefaults() {
            return new MoveOptions(true, null, CollisionPosePolicy.STOCK_NATIVE_INTERVAL, BodyPosePolicy.STOCK_SDK, false, true);
        }
    }

    public static MoveResult move(Mob body, MovementVector requested) {
        return move(body, requested, MoveOptions.serverDefaults());
    }

    public static MoveResult move(Mob body, MovementVector requested, MoveOptions options) {
        return move(body, requested, options, false);
    }

    /** Explicit motor attestation: true only for the movement motor's actual idle support probe branch. */
    public static MoveResult move(Mob body, MovementVector requested, MoveOptions options, boolean idleSupportProbe) {
        return move(body, requested, options, idleSupportProbe, false);
    }

    /** Motor jump initiation is only an attestation; native collision acceptance remains authoritative. */
    public static MoveResult move(Mob body, MovementVector requested, MoveOptions options, boolean idleSupportProbe,
                                  boolean motorJumpInitiated) {
        java.util.Objects.requireNonNull(options);
        EntityMovementExtension movement = body instanceof EntityMovementExtension extension ? extension : null;
        EntityExtension collision = body instanceof EntityExtension extension ? extension : null;
        // Sable retains the last collision info on the entity. Its move redirect
        // replaces that reference only when this move actually runs collide().
        SubLevelEntityCollision.CollisionInfo previous = movement == null ? null : movement.sable$getCollisionInfo();
        Level level = body.level();
        LoadedFrameRayQuery.ActorPoint beforePoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        LoadedFrameRayQuery.ActorPoint velocityBasis = options.velocityBasis() == null ? beforePoint : options.velocityBasis();
        Vec3 before = body.position();
        boolean wasGrounded = body.onGround();
        // World-floor clip observations are also used by the client replay proof. Capture the
        // exact pre-move box on both sides; server-only consumers remain unchanged below.
        AABB preMoveBounds = body.getBoundingBox();
        Vec3 requestedVec = new Vec3(requested.x(), requested.y(), requested.z());
        Runnable move = () -> body.move(MoverType.SELF, requestedVec);
        CurrentFrameCollisionScope.ScopedResult<Void> collisionScope = null;
        if (options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER) {
            collisionScope = CurrentFrameCollisionScope.withTrackedCharacterPose(body,
                    options.collisionPosePolicy() == CollisionPosePolicy.CURRENT_FRAME_GEOMETRY,
                    options.observeGeometry(),
                    velocityBasis,
                    () -> { move.run(); return null; });
        } else if (options.collisionPosePolicy() == CollisionPosePolicy.CURRENT_FRAME_GEOMETRY) {
            CurrentFrameCollisionScope.withCurrentGeometry(body, () -> { move.run(); return null; });
        } else move.run();
        Vec3 afterOwnMove = body.position();
        Vec3 actualOwn=afterOwnMove.subtract(before);
        MovementVector measuredOwn = new MovementVector(actualOwn.x, actualOwn.y, actualOwn.z);
        MovementVector accepted = com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.accepted(
                requested, measuredOwn);
        SubLevelEntityCollision.CollisionInfo info = movement == null ? null : movement.sable$getCollisionInfo();
        boolean fresh = info != null && info != previous;
        HorizontalContactEvidence horizontalContact = fresh && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER
                 ? horizontalContactEvidence(body, level, requested, actualOwn, accepted, before, afterOwnMove, info)
                : null;
        TransientContactCandidate transientCandidate = prepareTransientContactCandidate(body, level, options, requested,
                measuredOwn, beforePoint, before, preMoveBounds, afterOwnMove, info, fresh, collisionScope);

        // LivingEntity travel normally consumes this once after Entity.move. Our owned path
        // cancels travel, so bridge that native result here; never estimate carrier velocity.
        Vec3 actualAppliedCarrier = Vec3.ZERO;
        if (movement != null && collision != null) {
            if (options.applyNativeInheritedMotion() && fresh && info.inheritedMotion != null) {
                Vec3 carrierBefore = body.position();
                body.setPos(carrierBefore.add(collision.sable$vanillaCollide(info.inheritedMotion)));
                actualAppliedCarrier = body.position().subtract(carrierBefore);
            }
        }
        if (confirmsNativeJumpDetach(motorJumpInitiated, wasGrounded, requested.y(), measuredOwn.y(),
                fresh, body.onGround())) {
            var tracked = movement == null ? null : movement.sable$getTrackingSubLevel();
            LoadedFrameRayQuery.ActorPoint carriedPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
            // Only release the exact frame associated with this move. Inherited native carry above
            // is applied first so the post-move point records the legitimate first-launch carry.
            if (tracked != null && info != null && confirmsMatchingJumpFrame(beforePoint, carriedPoint,
                    tracked.getUniqueId(), info.trackingSubLevel == null ? null : info.trackingSubLevel.getUniqueId())) {
                movement.sable$setTrackingSubLevel(null);
            }
        }
        if (level instanceof ServerLevel) {
            if (fresh) {
                AirlockBumpSystem.afterNativeMove(body, preMoveBounds, requestedVec, info);
            } else {
                AirlockBumpSystem.noNativeContact(body);
            }
        }

        MovementVector frameDelta = MovementVector.ZERO;
        LoadedFrameRayQuery.ActorPoint afterPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        ContactPositionCorrectionEvidence positionCorrection = floorCorrection(body, level, options, requested,
                measuredOwn, accepted, beforePoint, afterPoint, before, afterOwnMove, info, fresh,
                collisionScope, actualOwn,movement);
        HorizontalContactEvidence resolvedContact = horizontalContact;
        MovementVector boundedOwn = accepted;
        boolean idleSupportProbeCertified = false;
        boolean idleProbeResponseSuppressed = false;
        Vec3 idleProbeSuppressionDisplacement = Vec3.ZERO;
        GroundingDecision groundingDecision = GroundingDecision.UNCHANGED;
        NativeContactProofDiagnostic nativeContactProofDiagnostic = NativeContactProofDiagnostic.none();
        if (positionCorrection != null) boundedOwn = positionCorrection.boundedOwnDisplacement();
        else {
            boolean observedWorldFloorClip = fresh && info != null && info.motion != null
                    && collisionScope != null && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER
                    && observedWorldFloorClip(body, requested, measuredOwn, info, collisionScope,
                            before, afterOwnMove, preMoveBounds);
              boolean pureSideGeometryCandidate = info != null && info.motion != null && !info.verticalCollision
                     && !info.verticalCollisionBelow
                     && measuredOwn.y() != requested.y()
                     && Double.isFinite(measuredOwn.y()) && Double.isFinite(requested.y())
                     && Math.abs(info.motion.y-measuredOwn.y()) <= NativeCollisionSlop.MAX_RESIDUAL;
             boolean ceilingAndSide = collisionScope != null
                        && rowsHaveCeilingAndSide(collisionScope.nativeCollisionTrace());
             var worldClipObservation = collisionScope == null ? null : collisionScope.worldClipObservation();
             boolean ceilingOnlyPosition = fresh && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER
                     && info != null && info.motion != null && info.firstCollisions != null && collisionScope != null
                      && collisionScope.nativeCollisionTraceComplete() && collisionScope.geometryTraceComplete()
                      && ceilingOnlyPositionContext(body, new MovementVector(info.motion.x, info.motion.y, info.motion.z),
                              measuredOwn, collisionScope.nativeCollisionTrace(), worldClipObservation, before, preMoveBounds);
             if (!idleProbeResponseSuppressed && fresh
                && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER
                 && info != null && info.motion != null && collisionScope != null
                     && outerPositionProofAdmissible(horizontalContact, observedWorldFloorClip, pureSideGeometryCandidate,
                            ceilingAndSide, ceilingOnlyPosition, transientCandidate, info)
                 && collisionScope.nativeCollisionTraceComplete() && collisionScope.geometryTraceComplete()
                  && matchesNativeMotion(info.motion.x(), measuredOwn.x(), before.x, afterOwnMove.x)
                  && (observedWorldFloorClip || ceilingOnlyPosition
                       || matchesNativeMotion(info.motion.y(), measuredOwn.y(), before.y, afterOwnMove.y))
                  && matchesNativeMotion(info.motion.z(), measuredOwn.z(), before.z, afterOwnMove.z)) {
            var rows = collisionScope.nativeCollisionTrace();
            SubLevel liveFrame = rows.isEmpty() ? null : SubLevelContainer.getContainer(level).getSubLevel(rows.getFirst().frameId());
            boolean validFrame = liveFrame != null && !liveFrame.isRemoved() && liveFrame.getLevel() == level
                    && liveFrame.getPlot() != null
                    && SubLevelContainer.getContainer(level).getSubLevel(liveFrame.getUniqueId()) == liveFrame
                    && (level.isClientSide ? liveFrame instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel
                        : liveFrame instanceof ServerSubLevel);
              boolean groundedFloorSide = body.onGround() && requested.y() < 0d && info.verticalCollision
                     && info.verticalCollisionBelow && info.subLevelHorizontalCollision
                     && !info.horizontalCollision && info.trackingSubLevel == liveFrame
                     && supportedFloorSideContactMatches(info, liveFrame, rows)
                     && !transientCandidateOwnsSupportedFloorSide(transientCandidate, info)
                     && beforePoint != null && afterPoint != null
                     && groundedFloorSideOriginAdmissible(liveFrame.getUniqueId(), beforePoint.frameId(), true)
                     && liveFrame.getUniqueId().equals(afterPoint.frameId())
                      && matchesNativeMotion(info.motion.y(), measuredOwn.y(), before.y, afterOwnMove.y);
            if (validFrame && info.firstCollisions != null) {
                   boolean idleBranch = idleSupportProbe && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER
                           && requested.x() == 0d && requested.z() == 0d && requested.y() < 0d
                           && body.onGround() && info.verticalCollision && info.verticalCollisionBelow
                           && !info.horizontalCollision && !info.subLevelHorizontalCollision
                           && info.trackingSubLevel == liveFrame && idleProbeFirstContactMatches(info, liveFrame, rows);
                   if (idleBranch && wasGrounded && measuredOwn.y() < 0d) {
                       MovementVector nativeMotionForSuppression = new MovementVector(info.motion.x(), info.motion.y(), info.motion.z());
                       var negativeProof = NativeSideContactProof.validateNegativeIdleSupportProbe(requested,
                               nativeMotionForSuppression, measuredOwn, liveFrame.getUniqueId(), rows,
                               row -> row.selectedPos() != null && row.selectedState() != null && knownSelectedState(level, row));
                        var carriedPointForSuppression = LoadedFrameRayQuery.currentActorPoint(level, body);
                        boolean exactFrame = beforePoint != null && carriedPointForSuppression != null
                                && liveFrame.getUniqueId().equals(beforePoint.frameId())
                                && liveFrame.getUniqueId().equals(carriedPointForSuppression.frameId());
                        if (negativeProof.isPresent() && exactFrame) {
                            Vec3 candidatePosition = before.add(actualAppliedCarrier);
                            AABB candidateBounds = preMoveBounds.move(actualAppliedCarrier);
                            NativeActorYaw candidateYaw = NativeActorYaw.capture(body, collisionScope).orElse(null);
                            if (candidateYaw != null
                                    && NativeFinalFootClearance.classifyCandidateBox(level, body, candidateBounds, candidateYaw)
                                            == NativeFinalFootClearance.Result.CLEAR
                                    && NativeIdleProbeEndpointSupport.supports(level, body, candidateBounds,
                                            candidateYaw, liveFrame, rows)) {
                                Vec3 nativeEndpoint = body.position();
                                body.setPos(candidatePosition);
                                var correctedPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
                                if (candidatePosition.equals(body.position()) && correctedPoint != null
                                        && liveFrame.getUniqueId().equals(correctedPoint.frameId())) {
                                    idleProbeSuppressionDisplacement = body.position().subtract(nativeEndpoint);
                                    idleProbeResponseSuppressed = true;
                                    idleSupportProbeCertified = true;
                                    resolvedContact = null;
                                    boundedOwn = MovementVector.ZERO;
                                } else body.setPos(nativeEndpoint);
                            }
                        }
                   }
                   boolean firstContactMatches = !idleProbeResponseSuppressed && firstNativeContactMatches(info, liveFrame, rows);
                   var idleProbeFloor = idleBranch && !idleProbeResponseSuppressed
                          ? NativeSideContactProof.validateIdleSupportProbe(requested,
                                  new MovementVector(info.motion.x(), info.motion.y(), info.motion.z()), measuredOwn,
                                  liveFrame.getUniqueId(), rows, row -> row.selectedPos() != null
                                          && row.selectedState() != null && knownSelectedState(level, row)).orElse(null)
                          : null;
                  MovementVector nativeMotion = new MovementVector(info.motion.x(), info.motion.y(), info.motion.z());
              NativeContactProofDiagnostic.Branch proofBranch = idleBranch ? NativeContactProofDiagnostic.Branch.IDLE_FLOOR
                          : groundedFloorSide ? NativeContactProofDiagnostic.Branch.SUPPORTED_FLOOR_SIDE
                           : observedWorldFloorClip ? NativeContactProofDiagnostic.Branch.WORLD_FLOOR_SIDE
                            : ceilingAndSide ? NativeContactProofDiagnostic.Branch.CEILING_AND_SIDE
                            : ceilingOnlyPosition ? NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                           : !info.verticalCollision ? NativeContactProofDiagnostic.Branch.PURE_SIDE
                            : transientCandidate != null && transientCandidate.policy() == TransientPolicy.NEGATIVE
                                ? NativeContactProofDiagnostic.Branch.TRANSIENT_FLOOR_SIDE_AIRBORNE
                            : transientCandidate != null ? NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE
                           : NativeContactProofDiagnostic.Branch.UNSUPPORTED_MIXED;
                  NativeSideContactProof.DiagnosticResult detailed = null;
                  var proof = idleProbeFloor;
                  if (proof == null && proofBranch == NativeContactProofDiagnostic.Branch.SUPPORTED_FLOOR_SIDE) {
                      detailed = NativeSideContactProof.validateSupportedFloorAndSideDetailed(requested,nativeMotion,measuredOwn,
                              liveFrame.getUniqueId(),rows,row -> row.selectedPos()!=null&&row.selectedState()!=null&&knownSelectedState(level,row),true);
                      proof = detailed.result().orElse(null);
                  } else if (proof == null && proofBranch == NativeContactProofDiagnostic.Branch.WORLD_FLOOR_SIDE && firstContactMatches) {
                       detailed = NativeSideContactProof.validateWorldFloorSideDetailed(requested,nativeMotion,measuredOwn,
                               liveFrame.getUniqueId(),rows,row -> row.selectedPos()!=null&&row.selectedState()!=null&&knownSelectedState(level,row),
                              collisionScope.worldClipObservation());
                      proof = detailed.result().orElse(null);
                   } else if (proof == null && proofBranch == NativeContactProofDiagnostic.Branch.PURE_SIDE && firstContactMatches) {
                      detailed = NativeSideContactProof.validateDetailed(requested,nativeMotion,measuredOwn,liveFrame.getUniqueId(),rows,
                              row -> row.selectedPos()!=null&&row.selectedState()!=null&&knownSelectedState(level,row));
                        proof = detailed.result().orElse(null);
                      } else if (proof == null && proofBranch == NativeContactProofDiagnostic.Branch.TRANSIENT_FLOOR_SIDE_AIRBORNE
                             && transientCandidate != null && transientCandidate.info() == info) {
                        detailed = transientCandidate.diagnostic();
                        proof = transientCandidate.proof();
                     } else if (proof == null && proofBranch == NativeContactProofDiagnostic.Branch.CEILING_AND_SIDE) {
                       detailed = NativeSideContactProof.validateCeilingAndSideDetailed(requested,nativeMotion,measuredOwn,
                               liveFrame.getUniqueId(),rows,row -> row.selectedPos()!=null&&row.selectedState()!=null&&knownSelectedState(level,row));
                         proof = detailed.result().orElse(null);
                      } else if (proof == null && proofBranch == NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE
                             && transientCandidate != null && transientCandidate.info() == info) {
                         detailed = transientCandidate.diagnostic();
                         proof = transientCandidate.proof();
                     } else if (proof == null && proofBranch == NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION) {
                         detailed = NativeSideContactProof.validateCeilingOnlyDetailed(requested,nativeMotion,measuredOwn,
                                 liveFrame.getUniqueId(),rows,row -> row.selectedPos()!=null&&row.selectedState()!=null&&knownSelectedState(level,row),
                                collisionScope.worldClipObservation());
                        proof = detailed.result().orElse(null);
                  }
                   boolean clipContext = observedWorldFloorClip || (proofBranch == NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                             && worldClipMatchesCurrentMove(body, nativeMotion, measuredOwn, collisionScope.worldClipObservation(), before, preMoveBounds));
                  var clip = collisionScope.worldClipObservation();
                  if (proofBranch == NativeContactProofDiagnostic.Branch.WORLD_FLOOR_SIDE)
                      clipContext = clip != null && clip.complete() && clip.downwardFloorClip()
                               && clip.matchesCall(body,nativeMotion == null ? null : new Vec3(nativeMotion.x(),nativeMotion.y(),nativeMotion.z()),
                                       new Vec3(measuredOwn.x(),measuredOwn.y(),measuredOwn.z()),before,preMoveBounds);
                   NativeSideContactProof.Failure proofFailure = detailed==null?NativeSideContactProof.Failure.NONE:detailed.reason();
                  NativeContactProofDiagnostic.PositionStage positionStage = NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED;
                  var clearanceObservation = NativeContactProofDiagnostic.ClearanceObservation.none();
                 if (proof != null) {
                     var position = idleProbeFloor != null
                             ? ContactPositionCorrectionEvidence.validateSupportedFloor(requested, MovementVector.ZERO,
                                     measuredOwn).orElse(null)
                             : groundedFloorSide
                            ? ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(requested,
                                    proof.boundedOwn(), proof.geometryCorrection(), proof.horizontalContact()).orElse(null)
                               : proofBranch == NativeContactProofDiagnostic.Branch.CEILING_AND_SIDE
                               || proofBranch == NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                              ? ContactPositionCorrectionEvidence.validateCeilingAndSide(requested, proof.boundedOwn(),
                                       proof.geometryCorrection(), proof.horizontalContact()).orElse(null)
                               : proofBranch == NativeContactProofDiagnostic.Branch.TRANSIENT_FLOOR_SIDE_AIRBORNE
                               ? ContactPositionCorrectionEvidence.validateTransientFloorAndSide(requested, proof.boundedOwn(),
                                       proof.geometryCorrection(), proof.horizontalContact()).orElse(null)
                               : proofBranch == NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE
                               ? ContactPositionCorrectionEvidence.validateAscendingFloor(requested, proof.boundedOwn(),
                                       proof.geometryCorrection(), proof.horizontalContact()).orElse(null)
                              : proofBranch == NativeContactProofDiagnostic.Branch.PURE_SIDE
                                  && !body.onGround()
                                  && Math.abs(proof.geometryCorrection().y()) > NativeCollisionSlop.MAX_RESIDUAL
                              ? ContactPositionCorrectionEvidence.validateAirborneSideGeometry(requested, proof.boundedOwn(),
                                     proof.geometryCorrection(), proof.horizontalContact()).orElse(null)
                              : ContactPositionCorrectionEvidence.validateSide(requested, proof.boundedOwn(),
                                     proof.geometryCorrection(), proof.horizontalContact()).orElse(null);
                       if (position != null) {
                          boundedOwn = proof.boundedOwn(); resolvedContact = proof.horizontalContact();
                          positionCorrection = position;
                          idleSupportProbeCertified = idleProbeFloor != null;
                          positionStage = NativeContactProofDiagnostic.PositionStage.ACCEPTED;
                           if (proofBranch == NativeContactProofDiagnostic.Branch.CEILING_AND_SIDE
                                   && knownNoWorldFloor(body, nativeMotion, measuredOwn, before, preMoveBounds,
                                          afterOwnMove, collisionScope.worldClipObservation())) {
                              body.setOnGround(false);
                               groundingDecision = GroundingDecision.CEILING_ONLY_AIRBORNE;
                           }
                            if (proofBranch == NativeContactProofDiagnostic.Branch.TRANSIENT_FLOOR_SIDE_AIRBORNE
                                    || proofBranch == NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE) {
                               boolean sameEndpoint = transientEndpointUnchanged(transientCandidate, info,
                                       body.position(), body.getBoundingBox());
                                NativeFinalFootClearance.QueryObservation[] finalObserved = options.observeGeometry() && !sameEndpoint
                                        ? new NativeFinalFootClearance.QueryObservation[1] : null;
                                NativeFinalFootClearance.Result finalClearance = sameEndpoint
                                        ? transientCandidate.clearance()
                                         : transientCandidate.nativeYaw() == null ? NativeFinalFootClearance.Result.UNKNOWN
                                                  : queryTransientClearance(level, body, transientCandidate.nativeYaw(), transientCandidate.policy(),
                                                          finalObserved == null ? null : value -> finalObserved[0] = value);
                               finalClearance = transientFinalClearance(transientCandidate, sameEndpoint, finalClearance);
                                 var authorization = transientCandidateAuthorization(transientCandidate, info, finalClearance);
                                 boolean ascendingReleased = proofBranch != NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE;
                                  if (authorization == NativeContactProofDiagnostic.Authorization.AUTHORIZED
                                          && !ascendingReleased) {
                                      ascendingReleased = movement != null && releaseAscendingTracking(movement, body, level, beforePoint, info);
                                      if (ascendingReleased) {
                                          // Require the actual refreshed point to confirm world ownership;
                                          // the pre-clear match alone does not establish the postcondition.
                                          afterPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
                                          ascendingReleased = confirmsAscendingWorldRelease(afterPoint);
                                      }
                                      if (!ascendingReleased) authorization = NativeContactProofDiagnostic.Authorization.DENIED_IDENTITY;
                                 }
                                  clearanceObservation = new NativeContactProofDiagnostic.ClearanceObservation(
                                         transientClearanceMode(transientCandidate.policy()), transientCandidate.clearance(),
                                         sameEndpoint, finalClearance, authorization, transientCandidate.clearanceObservation(),
                                         sameEndpoint ? transientCandidate.clearanceObservation()
                                                 : finalObserved == null ? null : finalObserved[0]);
                                  if (authorization == NativeContactProofDiagnostic.Authorization.AUTHORIZED) {
                                     body.setOnGround(false);
                                      groundingDecision = proofBranch == NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE
                                            ? GroundingDecision.ASCENDING_FLOOR_AIRBORNE : GroundingDecision.TRANSIENT_FLOOR_SIDE_AIRBORNE;
                               } else {
                                   boundedOwn = accepted;
                                   resolvedContact = horizontalContact;
                                   positionCorrection = null;
                                   positionStage = NativeContactProofDiagnostic.PositionStage.REJECTED;
                               }
                           }
                      } else {
                          positionStage = NativeContactProofDiagnostic.PositionStage.REJECTED;
                      }
                  }
                   nativeContactProofDiagnostic = new NativeContactProofDiagnostic(proofBranch,fresh,
                           options.bodyPosePolicy()==BodyPosePolicy.TRACKED_CHARACTER,collisionScope.nativeCollisionTraceComplete(),
                            collisionScope.geometryTraceComplete(),matchesNativeMotion(info.motion.x(),measuredOwn.x(),before.x,afterOwnMove.x),
                            matchesNativeMotion(info.motion.y(),measuredOwn.y(),before.y,afterOwnMove.y),
                            matchesNativeMotion(info.motion.z(),measuredOwn.z(),before.z,afterOwnMove.z),clipContext,
                           groundedFloorSide,firstContactMatches,proofFailure,detailed==null?-1:detailed.rowIndex(),
                            requested,nativeMotion,measuredOwn,proof==null?null:proof.boundedOwn(),
                             proof==null?null:proof.geometryCorrection(),positionStage,groundingDecision,clearanceObservation);
            }
        }
        }
        if (idleProbeResponseSuppressed) {
            afterPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
            positionCorrection = null;
            boundedOwn = MovementVector.ZERO;
            resolvedContact = null;
        }
        if (fresh && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER && movement != null) {
            SubLevel supportFrame=nativeFrameHasFinalFloorSupport(movement,body,info,collisionScope);
            // Resolve before final actor-point publication and frame-velocity rebasing. The raw
            // CollisionInfo is retained through the proof above; now both live tracker and its
            // published info agree on a proved support frame, or explicit WORLD/null.
            afterPoint=synchronizeFinalSupportReference(movement,body,level,info,supportFrame);
        }
        if (velocityBasis != null && afterPoint != null && movement != null && collision != null) {
            if (fresh && !java.util.Objects.equals(velocityBasis.frameId(), afterPoint.frameId())) {
                Vec3 delta = SableFrameVelocity.pointDeltaPerSecond(level, velocityBasis, afterPoint);
                if (delta != null) {
                    frameDelta = new MovementVector(delta.x, delta.y, delta.z);
                }
            }
        }
        if (level instanceof ServerLevel serverLevel && body instanceof FrameAnchorPending pending
                && FrameAnchorState.mayCommit(pending.sable$frameAnchorInitializationPending())) {
            pending.sable$frameAnchorState().observePhysicalMove(serverLevel, body, afterPoint, fresh);
        }
         NativeNormalContactPolicy.Target normalTarget = options.allowContactTarget()
                  ? nativeNormalTarget(level, body, info, fresh, beforePoint, afterPoint, requested) : null;
          NativeNormalContactFinalizer.LandingWitness landingWitness = null;
          if (fresh && options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER && !motorJumpInitiated
                  && !idleSupportProbe && beforePoint != null && afterPoint != null
                  && afterPoint.frameId() != null
                  && !java.util.Objects.equals(beforePoint.frameId(), afterPoint.frameId()) && body.onGround()
                  && info != null && info.trackingSubLevel != null && collisionScope != null
                  && collisionScope.nativeCollisionTraceComplete()) {
              var row = nativeFinalFloorRow(body, info.trackingSubLevel, collisionScope);
              if (row != null) {
                  double length = Math.sqrt(row.selectedX()*row.selectedX()+row.selectedY()*row.selectedY()+row.selectedZ()*row.selectedZ());
                  if (length > 0 && Double.isFinite(length)) {
                      Vec3 normal = new Vec3(row.selectedX()/length,row.selectedY()/length,row.selectedZ()/length);
                      double incomingNormalSpeed = (requested.x()*normal.x + requested.y()*normal.y
                              + requested.z()*normal.z) / com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS
                              + frameDelta.x()*normal.x + frameDelta.y()*normal.y + frameDelta.z()*normal.z;
                      landingWitness = new NativeNormalContactFinalizer.LandingWitness(beforePoint.frameId(), afterPoint.frameId(),
                              normal, incomingNormalSpeed, false);
                  }
              }
          }
          MoveResult result = new MoveResult(requested, accepted, measuredOwn, frameDelta, normalTarget, fresh, beforePoint, afterPoint, info,
                  options.collisionPosePolicy(), resolvedContact, collisionScope, positionCorrection, boundedOwn,
                  idleSupportProbe, idleSupportProbeCertified,
                  new MovementVector(idleProbeSuppressionDisplacement.x, idleProbeSuppressionDisplacement.y, idleProbeSuppressionDisplacement.z),
                   nativeContactProofDiagnostic, landingWitness);
        if (level instanceof ServerLevel serverLevel && body instanceof FrameAnchorPending pending
                && FrameAnchorState.mayCommit(pending.sable$frameAnchorInitializationPending())) {
            pending.sable$frameAnchorState().tracePostRestoreMotion(serverLevel, body, result, before, afterOwnMove, body.position());
        }
        return result;
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace nativeFinalFloorRow(Mob body, SubLevel frame,
            CurrentFrameCollisionScope.ScopedResult<?> scope) {
        UUID id=frame.getUniqueId();
        for (var row : scope.nativeCollisionTrace()) {
            if (row != null && id.equals(row.frameId()) && potentialNativeUpwardFloorRow(row)
                    && row.selectedPos()!=null && row.selectedState()!=null && knownSelectedState(body.level(), row)) {
                Vector3d up=nativeFrameUpAtSubstep(body.level(), frame, row);
                if (nativeFloorRowAdmitsSupport(row,id,up.x,up.y,up.z)) return row;
            }
        }
        return null;
    }

    private static SubLevel nativeFrameHasFinalFloorSupport(EntityMovementExtension movement,Mob body,
            SubLevelEntityCollision.CollisionInfo info,
            CurrentFrameCollisionScope.ScopedResult<?> scope) {
        if(!body.onGround()||info==null||info.trackingSubLevel==null||movement==null||scope==null
                ||!scope.nativeCollisionTraceComplete())return null;
        var container=SubLevelContainer.getContainer(body.level());
        SubLevel frame=info.trackingSubLevel;
        UUID id=frame.getUniqueId();
        SubLevel liveTracker=movement.sable$getTrackingSubLevel();
        if(!nativeSupportCandidateInstancesMatch(frame,liveTracker)
                ||!nativeSupportCandidateMatches(id,liveTracker.getUniqueId())
                ||frame.isRemoved()||frame.getLevel()!=body.level()||frame.getPlot()==null||container==null
                ||container.getSubLevel(id)!=frame
                ||(body.level().isClientSide?!(frame instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                    :!(frame instanceof ServerSubLevel)))return null;
        return nativeFloorRowsAdmitSupport(id,scope.nativeCollisionTrace(),
                row->knownSelectedState(body.level(),row),row->nativeFrameUpAtSubstep(body.level(),frame,row))?frame:null;
    }

    static boolean nativeSupportCandidateMatches(UUID finalInfoFrame,UUID actualLiveFrame) {
        return finalInfoFrame!=null&&finalInfoFrame.equals(actualLiveFrame);
    }

    static boolean nativeSupportCandidateInstancesMatch(Object finalInfoFrame,Object actualLiveFrame) {
        return finalInfoFrame!=null&&finalInfoFrame==actualLiveFrame;
    }

    private static Vector3d nativeFrameUpAtSubstep(Level level,SubLevel frame,
            CurrentFrameCollisionScope.NativeCollisionTrace row) {
        if(level==null||frame==null||row==null||row.substeps()<=0||row.substep()<1||row.substep()>row.substeps())
            throw unavailableSupportPose();
        try {
            OwnedFrameSimulationScope temporal=OwnedFrameSimulationScope.active(level,frame);
            dev.ryanhcode.sable.companion.math.Pose3dc from=temporal==null?frame.lastPose():temporal.from(frame);
            dev.ryanhcode.sable.companion.math.Pose3dc to=temporal==null?frame.logicalPose():temporal.to(frame);
            // collide constructs subLevelPose from this same interval at i/substeps;
            // use its full pose interpolation, including pivots, rather than matrix lerp.
            var atStep=new dev.ryanhcode.sable.companion.math.Pose3d(from)
                    .lerp(to,row.substep()/(double)row.substeps());
            Vector3d up=atStep.orientation().transform(new Vector3d(0,1,0));
            double length=up.length();
            if(!Double.isFinite(length)||Math.abs(length-1d)>NativeCollisionSlop.MAX_RESIDUAL)
                throw unavailableSupportPose();
            return up;
        } catch(RuntimeException invalidInterval) {
            if("collision resolution must be finite and bounded by requested displacement".equals(invalidInterval.getMessage()))
                throw invalidInterval;
            throw unavailableSupportPose();
        }
    }

    private static boolean potentialNativeUpwardFloorRow(CurrentFrameCollisionScope.NativeCollisionTrace row) {
        if(row==null||!row.verticalCollision())return false;
        double x=row.selectedX(),y=row.selectedY(),z=row.selectedZ();
        double length=Math.sqrt(x*x+y*y+z*z);
        return Double.isFinite(length)&&length>0d&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)
                &&y/length>.6d;
    }

    private static IllegalArgumentException unavailableSupportPose() {
        return new IllegalArgumentException("collision resolution must be finite and bounded by requested displacement");
    }

    /** Exact floor-row gate independent of optional SAT diagnostic geometry. */
    static boolean nativeFloorRowsAdmitSupport(boolean grounded,java.util.UUID candidateFrame,
            java.util.UUID finalNativeFrame,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,double upX,double upY,double upZ,
            java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        double upLength=Math.sqrt(upX*upX+upY*upY+upZ*upZ);
        if(!grounded||candidateFrame==null||rows==null||rows.isEmpty()||knownState==null
                ||!Double.isFinite(upLength)||Math.abs(upLength-1d)>NativeCollisionSlop.MAX_RESIDUAL)return false;
        if(!candidateFrame.equals(finalNativeFrame))return false;
        for(var row:rows) {
            if(row!=null&&candidateFrame.equals(row.frameId())&&potentialNativeUpwardFloorRow(row)
                    &&row.selectedPos()!=null&&row.selectedState()!=null&&knownState.test(row)
                    &&nativeFloorRowAdmitsSupport(row,candidateFrame,upX,upY,upZ))return true;
        }
        return false;
    }

    static boolean nativeFloorRowAdmitsSupport(CurrentFrameCollisionScope.NativeCollisionTrace row,
            UUID candidateFrame,double upX,double upY,double upZ) {
        if(row==null||candidateFrame==null||!candidateFrame.equals(row.frameId())
                ||row.selectedPos()==null||row.selectedState()==null||!potentialNativeUpwardFloorRow(row))return false;
        double length=Math.sqrt(row.selectedX()*row.selectedX()+row.selectedY()*row.selectedY()+row.selectedZ()*row.selectedZ());
        double upLength=Math.sqrt(upX*upX+upY*upY+upZ*upZ);
        if(!Double.isFinite(upLength)||Math.abs(upLength-1d)>NativeCollisionSlop.MAX_RESIDUAL)return false;
        double frameUpDot=(row.selectedX()*upX+row.selectedY()*upY+row.selectedZ()*upZ)/length;
        if(!(frameUpDot>.7d))return false;
        // Pinned EntitySubLevelUtil returns null custom orientation: collide's SDK entityUp is world UP.
        double sdkUpDot=row.selectedY()/length;
        return nativeSdkFloorResponseAdmissible(row,
                new MovementVector(row.selectedX(),row.selectedY(),row.selectedZ()),sdkUpDot,length);
    }

    /** Bounded row-wise consumer: each collision row receives its own exact native substep-up pose. */
    static boolean nativeFloorRowsAdmitSupport(UUID finalNativeFrame,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,
            java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState,
            java.util.function.Function<CurrentFrameCollisionScope.NativeCollisionTrace,Vector3d> frameUpAtRow) {
        if(finalNativeFrame==null||rows==null||rows.isEmpty()||knownState==null||frameUpAtRow==null)return false;
        for(var row:rows) {
            if(row==null||!finalNativeFrame.equals(row.frameId())||!potentialNativeUpwardFloorRow(row)
                    ||row.selectedPos()==null||row.selectedState()==null||!knownState.test(row))continue;
            Vector3d up=frameUpAtRow.apply(row);
            if(up==null)throw unavailableSupportPose();
            if(nativeFloorRowAdmitsSupport(row,finalNativeFrame,up.x,up.y,up.z))return true;
        }
        return false;
    }

    /** Matches the pinned Sable entityUp MTV redirect; frame-up support filtering is evaluated separately. */
    private static boolean nativeSdkFloorResponseAdmissible(CurrentFrameCollisionScope.NativeCollisionTrace row,
            MovementVector selected,double upDot,double length) {
        return nativeFloorResponseVectorsMatch(expectedSdkFloorAppliedResponse(selected,upDot,length),
                new MovementVector(row.appliedX(),row.appliedY(),row.appliedZ()),
                new MovementVector(row.responseX(),row.responseY(),row.responseZ()));
    }

    private static MovementVector expectedSdkFloorAppliedResponse(MovementVector selected,double sdkUpDot,double length) {
        return sdkUpDot>.8d?new MovementVector(0d,length,0d):selected;
    }

    private static LoadedFrameRayQuery.ActorPoint synchronizeFinalSupportReference(EntityMovementExtension movement,
            Mob body,Level level,SubLevelEntityCollision.CollisionInfo info,SubLevel supportFrame) {
        movement.sable$setTrackingSubLevel(supportFrame);
        if(info!=null)info.trackingSubLevel=supportFrame;
        var point=LoadedFrameRayQuery.currentActorPoint(level,body);
        return requireSupportedActorPoint(supportFrame==null?null:supportFrame.getUniqueId(),point);
    }

    static LoadedFrameRayQuery.ActorPoint requireSupportedActorPoint(java.util.UUID supportFrameId,
            LoadedFrameRayQuery.ActorPoint point) {
        if(supportFrameId!=null&&(point==null||!supportFrameId.equals(point.frameId())))
            throw new IllegalArgumentException("collision resolution must be finite and bounded by requested displacement");
        return point;
    }

    static boolean confirmsNativeJumpDetach(boolean motorJumpInitiated, boolean wasGrounded,
            double requestedY, double measuredOwnY, boolean freshCollisionInfo, boolean onGroundAfter) {
        return motorJumpInitiated && wasGrounded && requestedY > 0d && measuredOwnY > 0d
                && Math.abs(measuredOwnY - requestedY) <= Math.max(NativeCollisionSlop.MAX_RESIDUAL,
                        Math.ulp(Math.max(Math.abs(requestedY), Math.abs(measuredOwnY))))
                && freshCollisionInfo && !onGroundAfter;
    }

    static boolean confirmsMatchingJumpFrame(LoadedFrameRayQuery.ActorPoint beforePoint,
            LoadedFrameRayQuery.ActorPoint carriedPoint, java.util.UUID trackedFrameId,
            java.util.UUID collisionFrameId) {
        return beforePoint != null && carriedPoint != null && trackedFrameId != null
                && trackedFrameId.equals(beforePoint.frameId()) && trackedFrameId.equals(carriedPoint.frameId())
                && trackedFrameId.equals(collisionFrameId);
    }

    /** Release only tracking proven to belong to this authorized ascending contact. */
    static boolean ascendingTrackingReleaseMatches(java.util.UUID trackedFrameId,
            LoadedFrameRayQuery.ActorPoint beforePoint, LoadedFrameRayQuery.ActorPoint carriedPoint,
            java.util.UUID collisionFrameId) {
        return trackedFrameId != null && collisionFrameId != null && carriedPoint != null
                && trackedFrameId.equals(collisionFrameId) && trackedFrameId.equals(carriedPoint.frameId())
                && (beforePoint == null || beforePoint.frameId() == null
                    || trackedFrameId.equals(beforePoint.frameId()));
    }

    static boolean confirmsAscendingWorldRelease(LoadedFrameRayQuery.ActorPoint refreshedPoint) {
        return refreshedPoint != null && refreshedPoint.frameId() == null;
    }

    private static boolean releaseAscendingTracking(EntityMovementExtension movement, Entity body, Level level,
            LoadedFrameRayQuery.ActorPoint beforePoint, SubLevelEntityCollision.CollisionInfo info) {
        var tracked = movement.sable$getTrackingSubLevel();
        var frame = info == null ? null : info.trackingSubLevel;
        var carriedPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        if (frame == null || !ascendingTrackingReleaseMatches(tracked == null ? null : tracked.getUniqueId(),
                beforePoint, carriedPoint, frame.getUniqueId())) return false;
        movement.sable$setTrackingSubLevel(null);
        return true;
    }

    public enum GroundingDecision { UNCHANGED, CEILING_ONLY_AIRBORNE, TRANSIENT_FLOOR_SIDE_AIRBORNE, ASCENDING_FLOOR_AIRBORNE }

    enum TransientPolicy { NEGATIVE, ASCENDING }

    record TransientContactCandidate(NativeSideContactProof.Result proof,
            NativeSideContactProof.DiagnosticResult diagnostic, Object info,
            Vec3 endpointPosition, AABB endpointBounds, NativeActorYaw nativeYaw,
            NativeFinalFootClearance.Result clearance, TransientPolicy policy,
            NativeFinalFootClearance.QueryObservation clearanceObservation) {
        TransientContactCandidate(NativeSideContactProof.Result proof, NativeSideContactProof.DiagnosticResult diagnostic,
                Object info, Vec3 endpointPosition, AABB endpointBounds, NativeActorYaw nativeYaw,
                NativeFinalFootClearance.Result clearance) {
            this(proof, diagnostic, info, endpointPosition, endpointBounds, nativeYaw, clearance, TransientPolicy.NEGATIVE, null);
        }
        TransientContactCandidate(NativeSideContactProof.Result proof, NativeSideContactProof.DiagnosticResult diagnostic,
                Object info, Vec3 endpointPosition, AABB endpointBounds, NativeActorYaw nativeYaw,
                NativeFinalFootClearance.Result clearance, TransientPolicy policy) {
            this(proof, diagnostic, info, endpointPosition, endpointBounds, nativeYaw, clearance, policy, null);
        }
    }

    static boolean transientCandidateAuthorizes(TransientContactCandidate candidate, Object currentInfo,
            NativeFinalFootClearance.Result finalClearance) {
        return transientCandidateAuthorization(candidate,currentInfo,finalClearance)
                == NativeContactProofDiagnostic.Authorization.AUTHORIZED;
    }

    static NativeContactProofDiagnostic.Authorization transientCandidateAuthorization(TransientContactCandidate candidate,
            Object currentInfo, NativeFinalFootClearance.Result finalClearance) {
        if (candidate == null || candidate.info() != currentInfo) return NativeContactProofDiagnostic.Authorization.DENIED_IDENTITY;
        if (candidate.proof() == null) return NativeContactProofDiagnostic.Authorization.DENIED_PROOF;
        if (candidate.clearance() != NativeFinalFootClearance.Result.CLEAR) return NativeContactProofDiagnostic.Authorization.DENIED_CAPTURE;
        if (finalClearance != NativeFinalFootClearance.Result.CLEAR) return NativeContactProofDiagnostic.Authorization.DENIED_FINAL;
        return NativeContactProofDiagnostic.Authorization.AUTHORIZED;
    }

    /** Ascending repair must test body penetration; negative transient retains the historical support-band test. */
    static NativeFinalFootClearance.Result queryTransientClearance(Level level, Mob body, NativeActorYaw yaw,
            TransientPolicy policy) {
        return queryTransientClearance(level, body, yaw, policy, null);
    }

    static NativeFinalFootClearance.Result queryTransientClearance(Level level, Mob body, NativeActorYaw yaw,
            TransientPolicy policy, java.util.function.Consumer<NativeFinalFootClearance.QueryObservation> observer) {
        return transientClearanceMode(policy) == NativeContactProofDiagnostic.QueryMode.BODY_VOLUME
                ? NativeFinalFootClearance.classifyCandidateBox(level, body, body.getBoundingBox(), yaw, 16, 1, observer)
                : NativeFinalFootClearance.classify(level, body, yaw, 16, 1, observer);
    }

    static NativeContactProofDiagnostic.QueryMode transientClearanceMode(TransientPolicy policy) {
        return policy == TransientPolicy.ASCENDING ? NativeContactProofDiagnostic.QueryMode.BODY_VOLUME
                : NativeContactProofDiagnostic.QueryMode.SUPPORT_BAND;
    }

    static boolean outerPositionProofAdmissible(HorizontalContactEvidence horizontalContact,
            boolean observedWorldFloorClip, boolean unexplainedPureSideY, boolean ceilingAndSide,
            boolean ceilingOnlyPosition, TransientContactCandidate transientCandidate, Object currentInfo) {
        return horizontalContact == null || observedWorldFloorClip || unexplainedPureSideY || ceilingAndSide
                || ceilingOnlyPosition || (transientCandidate != null
                    && transientCandidate.policy() == TransientPolicy.ASCENDING
                    && transientCandidate.info() == currentInfo && transientCandidate.proof() != null
                    && transientCandidate.proof().horizontalContact() != null);
    }

    static boolean groundedFloorSideOriginAdmissible(java.util.UUID targetFrame, java.util.UUID beforeFrame, boolean beforePointKnown) {
        return targetFrame != null && beforePointKnown && (beforeFrame == null || targetFrame.equals(beforeFrame));
    }

    /** A clear, identity-bound negative transient candidate owns this contact before grounded support. */
    static boolean transientCandidateOwnsSupportedFloorSide(TransientContactCandidate candidate, Object currentInfo) {
        return candidate != null && candidate.proof() != null && candidate.policy() == TransientPolicy.NEGATIVE
                && candidate.info() == currentInfo && candidate.clearance() == NativeFinalFootClearance.Result.CLEAR;
    }

    static boolean transientEndpointUnchanged(TransientContactCandidate candidate, Object currentInfo,
            Vec3 currentPosition, AABB currentBounds) {
        return candidate != null && candidate.info() == currentInfo && candidate.endpointPosition() != null
                && candidate.endpointPosition().equals(currentPosition) && candidate.endpointBounds() != null
                && candidate.endpointBounds().equals(currentBounds);
    }

    static NativeFinalFootClearance.Result transientFinalClearance(TransientContactCandidate candidate,
            boolean sameEndpoint, NativeFinalFootClearance.Result queriedPostCarry) {
        return candidate == null ? NativeFinalFootClearance.Result.UNKNOWN
                : sameEndpoint ? candidate.clearance() : queriedPostCarry;
    }

    private static TransientContactCandidate prepareTransientContactCandidate(Mob body, Level level, MoveOptions options,
            MovementVector requested, MovementVector measured, LoadedFrameRayQuery.ActorPoint beforePoint,
            Vec3 before, AABB preMoveBounds, Vec3 afterOwnMove, SubLevelEntityCollision.CollisionInfo info,
            boolean fresh, CurrentFrameCollisionScope.ScopedResult<Void> scope) {
        if (!fresh || options.bodyPosePolicy() != BodyPosePolicy.TRACKED_CHARACTER || requested.y() == 0d
                || info == null || info.motion == null || scope == null || !scope.nativeCollisionTraceComplete()
                || !scope.geometryTraceComplete() || !matchesNativeMotion(info.motion.x(), measured.x(), before.x, afterOwnMove.x)
                || !matchesNativeMotion(info.motion.y(), measured.y(), before.y, afterOwnMove.y)
                || !matchesNativeMotion(info.motion.z(), measured.z(), before.z, afterOwnMove.z)) return null;
        var rows = scope.nativeCollisionTrace();
        boolean ascending = requested.y() > 0d;
        if (rows.isEmpty() || (ascending ? !rowsHaveAscendingFloor(rows) : !rowsHaveTransientFloorAndSide(rows))) return null;
        var frameId = rows.getFirst().frameId();
        SubLevel frame = SubLevelContainer.getContainer(level).getSubLevel(frameId);
        if (frame == null || frame.isRemoved() || frame.getLevel() != level || frame.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame
                || (level.isClientSide ? !(frame instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                    : !(frame instanceof ServerSubLevel))) return null;
        var afterOwnPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
        if (beforePoint == null || afterOwnPoint == null
                || !transientBeforePointAdmissible(ascending ? TransientPolicy.ASCENDING : TransientPolicy.NEGATIVE,
                        frameId, beforePoint.frameId(), true)
                || !frameId.equals(afterOwnPoint.frameId()) || info.trackingSubLevel != frame
                || !(ascending ? ascendingFirstContactMatches(info, frame, rows) : firstNativeContactMatches(info, frame, rows))) return null;
        var clip = scope.worldClipObservation();
        Vec3 nativeMotion = new Vec3(info.motion.x(), info.motion.y(), info.motion.z());
        Vec3 measuredOutput = new Vec3(measured.x(), measured.y(), measured.z());
        if (clip == null || !clip.complete() || clip.input() == null || clip.output() == null
                || !clip.input().equals(clip.output()) || clip.downwardFloorClip()
                || !clip.matchesCall(body, nativeMotion, measuredOutput, before, preMoveBounds)) return null;
        var diagnostic = ascending ? NativeSideContactProof.validateAscendingFloorDetailed(requested,
                new MovementVector(info.motion.x(), info.motion.y(), info.motion.z()), measured, frameId, rows,
                row -> row.selectedPos() != null && row.selectedState() != null && knownSelectedState(level, row), clip)
                : NativeSideContactProof.validateTransientFloorAndSideDetailed(requested,
                new MovementVector(info.motion.x(), info.motion.y(), info.motion.z()), measured, frameId, rows,
                row -> row.selectedPos() != null && row.selectedState() != null && knownSelectedState(level, row), clip);
        var proof = diagnostic.result().orElse(null);
        if (proof == null) return null;
        var nativeYaw = NativeActorYaw.capture(body, scope).orElse(null);
        if (nativeYaw == null) return null;
        var policy = ascending ? TransientPolicy.ASCENDING : TransientPolicy.NEGATIVE;
        NativeFinalFootClearance.QueryObservation[] observed = options.observeGeometry()
                ? new NativeFinalFootClearance.QueryObservation[1] : null;
        var clearance = queryTransientClearance(level, body, nativeYaw, policy,
                observed == null ? null : value -> observed[0] = value);
        return new TransientContactCandidate(proof, diagnostic, info, afterOwnMove, body.getBoundingBox(), nativeYaw, clearance,
                policy, observed == null ? null : observed[0]);
    }

    private static boolean rowsHaveTransientFloorAndSide(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (rows == null) return false;
        boolean floor = false, side = false;
        for (var row : rows) {
            if (row == null) return false;
            if (row.verticalCollision() && row.selectedY() > Math.hypot(row.selectedX(), row.selectedZ())) floor = true;
            if (!row.verticalCollision()) side = true;
        }
        return floor && side;
    }

    static boolean rowsHaveAscendingFloor(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (rows == null || rows.isEmpty()) return false;
        boolean floor = false;
        for (var row : rows) {
            if (row == null) return false;
            if (!row.verticalCollision()) continue;
            if (row.selectedY() <= Math.hypot(row.selectedX(), row.selectedZ())) return false;
            floor = true;
        }
        return floor;
    }

    private static boolean ascendingFirstContactMatches(SubLevelEntityCollision.CollisionInfo info, SubLevel frame,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (info.firstCollisions == null || frame == null || rows == null || rows.isEmpty()) return false;
        return ascendingFirstContactMatches(frame.getUniqueId(), info.firstCollisions.get(frame), rows);
    }

    static boolean ascendingFirstContactMatches(java.util.UUID frameId,
            SubLevelEntityCollision.FirstCollisionInfo contact,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (frameId == null || contact == null || rows == null || rows.isEmpty()) return false;
        var first = rows.getFirst();
        if (first == null) return false;
        return ascendingFirstContactMatches(frameId, first.frameId(),
                first.verticalCollision(), contact.horizontal(), first.selectedX(), first.selectedY(), first.selectedZ(),
                contact.globalDirection().x(), contact.globalDirection().y(), contact.globalDirection().z());
    }

    static boolean ascendingFirstContactMatches(java.util.UUID expectedFrame, java.util.UUID rowFrame,
            boolean vertical, boolean contactHorizontal, double x, double y, double z,
            double nx, double ny, double nz) {
        return expectedFrame != null && expectedFrame.equals(rowFrame) && contactHorizontal == !vertical
                && normalizedSelectedRowMatches(x, y, z, nx, ny, nz);
    }

    static boolean knownNoWorldFloor(Entity body, MovementVector nativeMotion, MovementVector measured,
            Vec3 before, AABB preMoveBounds, Vec3 after,
            CurrentFrameCollisionScope.WorldClipObservation observation) {
        if (body == null || nativeMotion == null || measured == null || before == null || after == null
                || observation == null || !observation.complete()) return false;
        Vec3 nativeInput = new Vec3(nativeMotion.x(), nativeMotion.y(), nativeMotion.z());
        Vec3 measuredOutput = new Vec3(measured.x(), measured.y(), measured.z());
        return observation.matchesCall(body, nativeInput, measuredOutput, before, preMoveBounds)
                && !observation.downwardFloorClip()
                && matchesNativeMotion(nativeMotion.x(), measured.x(), before.x, after.x)
                && matchesNativeMotion(nativeMotion.y(), measured.y(), before.y, after.y)
                && matchesNativeMotion(nativeMotion.z(), measured.z(), before.z, after.z);
    }

    private static NativeNormalContactPolicy.Target nativeNormalTarget(Level level, Mob body,
            SubLevelEntityCollision.CollisionInfo info, boolean fresh,
            LoadedFrameRayQuery.ActorPoint before, LoadedFrameRayQuery.ActorPoint after, MovementVector requested) {
        if (!fresh || before == null || after == null
                || before.frameId() != null || after.frameId() != null
                || !(info instanceof NativeSelectedContactAccess contacts)
                || contacts.moonstation14$selectedContactsOverflowed()) return null;
        Vec3 incoming = new Vec3(requested.x() / .05, requested.y() / .05, requested.z() / .05);
        return NativeHullContactTargetSelector.select(incoming, contacts.moonstation14$selectedContacts(),
                level.getGameTime(), contacts.moonstation14$selectedContactsOverflowed(), entry -> {
                    var point = LoadedFrameRayQuery.pointAtWorldPosition(level, entry.frameId(), entry.entityPosition());
                    return point == null ? null : SableFrameVelocity.pointVelocityAt(level, point);
                });
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static boolean rowsHaveCeilingAndSide(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (rows == null) return false;
        boolean ceiling = false, side = false;
        for (var row : rows) {
            if (row == null) return false;
            if (row.verticalCollision() && row.selectedY() < 0d) ceiling = true;
            if (!row.verticalCollision()) side = true;
        }
        return ceiling && side;
    }

    private static boolean rowsHaveCeilingOnly(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (rows == null || rows.isEmpty()) return false;
        for (var row : rows) if (row == null || !row.verticalCollision() || row.selectedY() >= 0d
                || !(row.selectedY() < Math.hypot(row.selectedX(), row.selectedZ()))) return false;
        return true;
    }

    static boolean worldClipMatchesCurrentMove(Entity body, MovementVector nativeMotion, MovementVector measured,
            CurrentFrameCollisionScope.WorldClipObservation observation, Vec3 before, AABB bounds) {
        return body != null && nativeMotion != null && measured != null && observation != null && observation.complete()
                && observation.matchesCall(body, new Vec3(nativeMotion.x(),nativeMotion.y(),nativeMotion.z()),
                        new Vec3(measured.x(),measured.y(),measured.z()), before, bounds);
    }

    static boolean ceilingOnlyPositionContext(Entity body, MovementVector nativeMotion, MovementVector measured,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,
            CurrentFrameCollisionScope.WorldClipObservation observation, Vec3 before, AABB bounds) {
        return nativeMotion != null && rowsHaveCeilingOnly(rows)
                && worldClipMatchesCurrentMove(body,nativeMotion,measured,observation,before,bounds);
    }

    private static boolean firstNativeContactMatches(SubLevelEntityCollision.CollisionInfo info, SubLevel frame,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (info.firstCollisions == null || rows.isEmpty()) return false;
        var contact = info.firstCollisions.get(frame);
        return nativeFirstContactMatches(frame.getUniqueId(),contact,rows);
    }

    static boolean nativeFirstContactMatches(java.util.UUID frameId,
            SubLevelEntityCollision.FirstCollisionInfo contact,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        return firstSelectedContactMatches(frameId, contact, rows, true);
    }

    private static boolean firstSelectedContactMatches(java.util.UUID frameId,
            SubLevelEntityCollision.FirstCollisionInfo contact,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, boolean horizontal) {
        if (frameId == null || contact == null || contact.horizontal() != horizontal || rows == null || rows.isEmpty()) return false;
        var first = rows.getFirst();
        if (first == null || !frameId.equals(first.frameId()) || first.verticalCollision() == horizontal) return false;
        var normal = contact.globalDirection();
        return normalizedSelectedRowMatches(first.selectedX(), first.selectedY(), first.selectedZ(), normal.x(), normal.y(), normal.z());
    }

    static boolean supportedFloorSideContactMatches(java.util.UUID frameId,
            SubLevelEntityCollision.FirstCollisionInfo contact,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,
            double upX, double upY, double upZ) {
        double upLength = Math.sqrt(upX*upX + upY*upY + upZ*upZ);
        if (!Double.isFinite(upLength) || Math.abs(upLength - 1d) > NativeCollisionSlop.MAX_RESIDUAL
                || !firstSelectedContactMatches(frameId, contact, rows, contact != null && contact.horizontal())) return false;
        for (var row : rows) {
            if (row == null || !frameId.equals(row.frameId())) return false;
            if (!row.verticalCollision() || row.selectedPos() == null) continue;
            double x=row.selectedX(), y=row.selectedY(), z=row.selectedZ();
            double length=Math.sqrt(x*x+y*y+z*z);
            if (Double.isFinite(length) && length > 0d
                    && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && normalizedSelectedRowMatches(x,y,z,x/length,y/length,z/length)
                    && (x*upX+y*upY+z*upZ)/length > .7) return true;
        }
        return false;
    }

    private static boolean supportedFloorSideContactMatches(SubLevelEntityCollision.CollisionInfo info, SubLevel frame,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if (info == null || info.firstCollisions == null || frame == null) return false;
        var up = frame.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
        return supportedFloorSideContactMatches(frame.getUniqueId(), info.firstCollisions.get(frame), rows, up.x, up.y, up.z);
    }

    private static boolean normalizedSelectedRowMatches(double x, double y, double z, double nx, double ny, double nz) {
        double length = Math.sqrt(x*x + y*y + z*z);
        if (!Double.isFinite(length) || !(length > 0d)) return false;
        double eps = NativeCollisionSlop.MAX_RESIDUAL;
        return Double.isFinite(nx) && Double.isFinite(ny) && Double.isFinite(nz)
                && Math.abs(Math.sqrt(nx*nx + ny*ny + nz*nz) - 1d) <= eps
                && Math.abs(nx - x/length) <= eps && Math.abs(ny - y/length) <= eps
                && Math.abs(nz - z/length) <= eps;
    }

    static boolean transientBeforePointAdmissible(TransientPolicy policy, java.util.UUID frame,
            java.util.UUID beforeFrame, boolean pointKnown) {
        if (policy == null || frame == null || !pointKnown) return false;
        return policy == TransientPolicy.ASCENDING ? beforeFrame == null || frame.equals(beforeFrame)
                : frame.equals(beforeFrame);
    }

    private static boolean idleProbeFirstContactMatches(SubLevelEntityCollision.CollisionInfo info, SubLevel frame,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows) {
        if(info.firstCollisions==null||rows==null)return false;
        var contact=info.firstCollisions.get(frame); if(contact==null)return false;
        var first=rows.stream().filter(r->r.geometry()!=null && Math.sqrt(r.selectedX()*r.selectedX()+r.selectedY()*r.selectedY()+r.selectedZ()*r.selectedZ())>NativeCollisionSlop.MAX_RESIDUAL).findFirst().orElse(null);
        if(first==null)return false;
        double[] m=first.geometry().subLevelPose().matrix(); var n=contact.globalDirection();
        double len=Math.sqrt(first.selectedX()*first.selectedX()+first.selectedY()*first.selectedY()+first.selectedZ()*first.selectedZ());
        return Math.abs(n.x()-first.selectedX()/len)<=NativeCollisionSlop.MAX_RESIDUAL
                &&Math.abs(n.y()-first.selectedY()/len)<=NativeCollisionSlop.MAX_RESIDUAL
                &&Math.abs(n.z()-first.selectedZ()/len)<=NativeCollisionSlop.MAX_RESIDUAL
                && Math.abs(first.selectedX()/len-m[4])<=NativeCollisionSlop.MAX_RESIDUAL
                && Math.abs(first.selectedY()/len-m[5])<=NativeCollisionSlop.MAX_RESIDUAL
                && Math.abs(first.selectedZ()/len-m[6])<=NativeCollisionSlop.MAX_RESIDUAL;
    }

    private static boolean observedWorldFloorClip(Mob body, MovementVector requested, MovementVector measured,
            SubLevelEntityCollision.CollisionInfo info, CurrentFrameCollisionScope.ScopedResult<Void> scope,
            Vec3 before, Vec3 after, AABB preMoveBounds) {
        return worldFloorClipContextAdmissible(body,requested,measured,info.motion,body.onGround(),
                info.verticalCollision,info.verticalCollisionBelow,info.horizontalCollision,
                scope.worldClipObservation(),before,preMoveBounds,after);
    }

    /** World-floor admission deliberately relies on observed vanilla clipping, not Sable's aggregate side flag. */
    static boolean worldFloorClipContextAdmissible(Entity body, MovementVector requested, MovementVector measured,
            Vec3 nativeInput, boolean grounded, boolean verticalCollision, boolean collisionBelow,
            boolean worldHorizontalCollision, CurrentFrameCollisionScope.WorldClipObservation observation,
            Vec3 before, AABB preMoveBounds, Vec3 after) {
        if (body==null||requested==null||measured==null||nativeInput==null||!grounded
                ||requested.y()>=0d||nativeInput.y>=0d||!verticalCollision||!collisionBelow
                ||worldHorizontalCollision) return false;
        Vec3 measuredOutput=new Vec3(measured.x(),measured.y(),measured.z());
        return observation!=null && observation.downwardFloorClip()
                && observation.matchesCall(body,nativeInput,measuredOutput,before,preMoveBounds)
                && matchesNativeMotion(observation.input().x,nativeInput.x,before.x,after.x)
                && matchesNativeMotion(observation.input().y,nativeInput.y,before.y,after.y)
                && matchesNativeMotion(observation.input().z,nativeInput.z,before.z,after.z);
    }

    private static boolean nativeFloorContactMatches(SubLevelEntityCollision.CollisionInfo info, SubLevel frame) {
        if (info.firstCollisions == null || frame == null) return false;
        var contact = info.firstCollisions.get(frame);
        if (contact == null || contact.horizontal()) return false;
        var normal = contact.globalDirection();
        double nx = normal.x(), ny = normal.y(), nz = normal.z();
        double length = Math.sqrt(nx*nx + ny*ny + nz*nz);
        Vector3d frameUp = frame.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
        double upLength = Math.sqrt(frameUp.x*frameUp.x + frameUp.y*frameUp.y + frameUp.z*frameUp.z);
        return Double.isFinite(length) && Math.abs(length - 1d) <= NativeCollisionSlop.MAX_RESIDUAL
                && Double.isFinite(upLength) && Math.abs(upLength - 1d) <= NativeCollisionSlop.MAX_RESIDUAL
                && nx*frameUp.x + ny*frameUp.y + nz*frameUp.z > .7;
    }

    /** Shared SDK floor-axis redirect budget used by standalone and mixed floor/side trace proofs. */
    static boolean nativeFloorAppliedResponseAdmissible(MovementVector selected, MovementVector applied,
            MovementVector response) {
        double eps = NativeCollisionSlop.MAX_RESIDUAL;
        if (selected == null || applied == null || response == null || selected.y() <= 0d
                || !(selected.y() > Math.hypot(selected.x(), selected.z()))) return false;
        double length = Math.sqrt(selected.x()*selected.x()+selected.y()*selected.y()+selected.z()*selected.z());
        return Double.isFinite(length) && length > 0d
                && nativeFloorResponseVectorsMatch(expectedSdkFloorAppliedResponse(selected,selected.y()/length,length),
                        applied,response);
    }

    /** Single shared strict component budget for SDK MTV application and observed collisionMotion response. */
    private static boolean nativeFloorResponseVectorsMatch(MovementVector expected,MovementVector applied,
            MovementVector response) {
        double eps=NativeCollisionSlop.MAX_RESIDUAL;
        return expected!=null&&applied!=null&&response!=null
                &&Math.abs(applied.x()-expected.x())<=eps&&Math.abs(applied.y()-expected.y())<=eps
                &&Math.abs(applied.z()-expected.z())<=eps
                &&Math.abs(response.x()-applied.x())<=eps
                &&Math.abs(response.y()-applied.y())<=eps
                &&Math.abs(response.z()-applied.z())<=eps;
    }

    private static ContactPositionCorrectionEvidence floorCorrection(Mob body, Level level, MoveOptions options,
            MovementVector request, MovementVector measured, MovementVector accepted,
            LoadedFrameRayQuery.ActorPoint beforePoint, LoadedFrameRayQuery.ActorPoint afterPoint,
            Vec3 before, Vec3 after, SubLevelEntityCollision.CollisionInfo info, boolean fresh,
            CurrentFrameCollisionScope.ScopedResult<Void> scope, Vec3 measuredVec,EntityMovementExtension movement) {
        double eps = NativeCollisionSlop.MAX_RESIDUAL;
        if (info == null || scope == null || beforePoint == null || afterPoint == null
                || !nativeFloorContextAdmissible(fresh, options.bodyPosePolicy() == BodyPosePolicy.TRACKED_CHARACTER,
                         body.onGround(), scope.nativeCollisionTraceComplete(), info.verticalCollision,
                         info.verticalCollisionBelow, info.horizontalCollision, info.subLevelHorizontalCollision,
                         beforePoint.frameId(), afterPoint.frameId(),
                         info.trackingSubLevel == null ? null : info.trackingSubLevel.getUniqueId(),
                         nativeFrameHasFinalFloorSupport(movement,body,info,scope)!=null)
                || info.motion == null || request.y() > 0d || measured.y() <= 0d
                || !matchesNativeMotion(info.motion.x(), measuredVec.x, before.x, after.x)
                || !matchesNativeMotion(info.motion.y(), measuredVec.y, before.y, after.y)
                || !matchesNativeMotion(info.motion.z(), measuredVec.z, before.z, after.z)) return null;
        if(info.trackingSubLevel==null||info.firstCollisions==null)return null;
        var trace = scope.nativeCollisionTrace();
        java.util.UUID floorFrame=beforePoint.frameId()==null
                ? info.trackingSubLevel.getUniqueId() : beforePoint.frameId();
        if (!nativeFloorTraceAdmissible(request, info.motion.x(), info.motion.y(),
                info.motion.z(), trace, floorFrame, row -> row.selectedPos() != null
                        && row.selectedState() != null && knownSelectedState(level, row))) return null;
        for (var row : trace) {
            SubLevel frame = SubLevelContainer.getContainer(level).getSubLevel(row.frameId());
            if (frame == null) return null;
        }
        for (var entry : info.firstCollisions.entrySet()) if (entry.getValue() != null && entry.getValue().horizontal()) return null;
        return ContactPositionCorrectionEvidence.validateSupportedFloor(request, MovementVector.ZERO, measured).orElse(null);
    }

    static boolean nativeFloorContextAdmissible(boolean fresh, boolean trackedCharacter, boolean grounded,
            boolean traceComplete, boolean verticalCollision, boolean collisionBelow, boolean horizontalCollision,
            boolean sublevelHorizontalCollision, java.util.UUID beforeFrame, java.util.UUID afterFrame,
            java.util.UUID nativeTrackingFrame) {
        return nativeFloorContextAdmissible(fresh,trackedCharacter,grounded,traceComplete,verticalCollision,
                collisionBelow,horizontalCollision,sublevelHorizontalCollision,beforeFrame,afterFrame,nativeTrackingFrame,false);
    }

    static boolean nativeFloorContextAdmissible(boolean fresh, boolean trackedCharacter, boolean grounded,
            boolean traceComplete, boolean verticalCollision, boolean collisionBelow, boolean horizontalCollision,
            boolean sublevelHorizontalCollision, java.util.UUID beforeFrame, java.util.UUID afterFrame,
            java.util.UUID nativeTrackingFrame,boolean verifiedFirstBoardingFloor) {
        return fresh && trackedCharacter && grounded && traceComplete && verticalCollision && collisionBelow
                && !horizontalCollision && !sublevelHorizontalCollision
                && (beforeFrame != null && beforeFrame.equals(afterFrame) && beforeFrame.equals(nativeTrackingFrame)
                    || beforeFrame == null && verifiedFirstBoardingFloor);
    }

    private static boolean knownSelectedState(Level level, CurrentFrameCollisionScope.NativeCollisionTrace row) {
        SubLevel frame = SubLevelContainer.getContainer(level).getSubLevel(row.frameId());
        if (frame == null) return false;
        net.minecraft.world.level.block.state.BlockState current;
        if (level instanceof ServerLevel server && frame instanceof ServerSubLevel serverFrame)
            current = LoadedFrameRayQuery.knownServerBlock(server, serverFrame, row.selectedPos());
        else if (level.isClientSide)
            current = LoadedFrameRayQuery.knownClientBlock(level, frame, row.selectedPos());
        else return false;
        return current != null && current.equals(row.selectedState());
    }

    /** Pure trace admission seam; loaded-state validation is supplied by the live native caller. */
    static boolean nativeFloorTraceAdmissible(MovementVector request, double nativeX, double nativeY,
            double nativeZ, java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            java.util.UUID frameId, java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        double eps = NativeCollisionSlop.MAX_RESIDUAL;
        if (request == null || trace == null || trace.isEmpty() || frameId == null || knownState == null
                || request.y() > 0d || Math.hypot(request.x(), request.z()) > eps
                || !Double.isFinite(nativeX) || !Double.isFinite(nativeY) || !Double.isFinite(nativeZ)) return false;
        double x = request.x(), y = request.y(), z = request.z();
        double expectedX = request.x(), expectedY = request.y(), expectedZ = request.z();
        for (var row : trace) {
            if (row == null || !frameId.equals(row.frameId()) || row.selectedPos() == null || row.selectedState() == null
                    || !Double.isFinite(row.selectedX()) || !Double.isFinite(row.selectedY())
                    || !Double.isFinite(row.selectedZ()) || !Double.isFinite(row.appliedY())
                    || !Double.isFinite(row.responseY()) || row.responseY() < 0d || !knownState.test(row)) return false;
            MovementVector selected = new MovementVector(row.selectedX(), row.selectedY(), row.selectedZ());
            MovementVector applied = new MovementVector(row.appliedX(), row.appliedY(), row.appliedZ());
            MovementVector response = new MovementVector(row.responseX(), row.responseY(), row.responseZ());
            if (!nativeFloorAppliedResponseAdmissible(selected, applied, response)) return false;
            double expectedAppliedX = applied.x(), expectedAppliedY = applied.y(), expectedAppliedZ = applied.z();
            x += row.responseX(); y += row.responseY(); z += row.responseZ();
            expectedX += expectedAppliedX; expectedY += expectedAppliedY; expectedZ += expectedAppliedZ;
        }
        var conversion=trace.getLast();
        return Math.abs(x-nativeX) <= eps && Math.abs(y-nativeY) <= eps && Math.abs(z-nativeZ) <= eps
                && Math.abs(expectedX-nativeX) <= eps && Math.abs(expectedY-nativeY) <= eps
                && Math.abs(expectedZ-nativeZ) <= eps;
    }

    /** Narrow Minecraft-native adapter proof; it does not claim parity with SS14's robust solver. */
    private static HorizontalContactEvidence horizontalContactEvidence(Mob body, Level level, MovementVector requested,
            Vec3 measuredActualOwn, MovementVector accepted, Vec3 before, Vec3 after, SubLevelEntityCollision.CollisionInfo info) {
        if (level == null || info.motion == null || !matchesNativeMotion(info.motion.x(), measuredActualOwn.x, before.x, after.x)
                || !matchesNativeMotion(info.motion.y(), measuredActualOwn.y, before.y, after.y)
                || !matchesNativeMotion(info.motion.z(), measuredActualOwn.z, before.z, after.z)
                || info.firstCollisions == null) return null;
        SubLevel selectedFrame = null;
        SubLevelEntityCollision.FirstCollisionInfo selectedContact = null;
        for (var entry : info.firstCollisions.entrySet()) {
            var contact = entry.getValue();
            if (contact == null || !contact.horizontal()) continue;
            if (selectedContact != null) return null; // no inferred multi-plane certificate
            selectedFrame = entry.getKey();
            selectedContact = contact;
        }
        if (selectedFrame == null || selectedFrame.isRemoved() || selectedFrame.getLevel() != level
                || selectedFrame.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(selectedFrame.getUniqueId()) != selectedFrame
                || level.isClientSide && !(selectedFrame instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel)
                || !level.isClientSide && !(selectedFrame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel)) return null;
        var nativeNormal = selectedContact.globalDirection();
        double nx = nativeNormal.x(), ny = nativeNormal.y(), nz = nativeNormal.z();
        if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz)
                || Math.abs(Math.sqrt(nx * nx + ny * ny + nz * nz) - 1d) > NativeCollisionSlop.MAX_RESIDUAL) return null;
        return HorizontalContactEvidence.validate(requested, accepted, nx, ny, nz).orElse(null);
    }

    private static boolean matchesNativeMotion(double nativeAxis, double measuredAxis, double before, double after) {
        if (!Double.isFinite(nativeAxis) || !Double.isFinite(measuredAxis)) return false;
        double allowance = NativeCollisionSlop.MAX_RESIDUAL + Math.ulp(before) + Math.ulp(after);
        return Math.abs(nativeAxis - measuredAxis) <= allowance;
    }
}
