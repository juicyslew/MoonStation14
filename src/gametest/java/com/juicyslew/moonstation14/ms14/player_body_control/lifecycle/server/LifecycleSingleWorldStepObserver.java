package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

/** Single-owner observer; appends one complete record for every actual lifecycle invocation. */
public final class LifecycleSingleWorldStepObserver {
    private LifecycleSingleWorldStepObserver() { }

    public record Attempt(long serverPhysicalTick, long worldGameTick, int wishX, int wishZ,
                          boolean jump, boolean sprint, float yaw,
                          Optional<GhostControlPayloads.SupportedMovementReference> reference,
                          Vec3 before, Vec3 beforeVelocity, boolean groundedBefore,
                          Vec3 after, Vec3 afterVelocity, boolean groundedAfter,
                          Optional<com.juicyslew.moonstation14.ms14.movement.CharacterMovementState> result,
                          List<SableOwnedBodyMotion.MoveResult> moves, List<NativeMoveEvidence> nativeMoves, String failure) {
        public int callbackCount() { return moves.size(); }
        public SableOwnedBodyMotion.MoveResult move() { return moves.size()==1 ? moves.getFirst() : null; }
        public CompletedNativeStep completedNativeStep() {
            return new CompletedNativeStep(nativeMoves.size()==1?nativeMoves.getFirst():null,
                    result!=null&&result.isPresent(),moves.size(),groundedAfter,failure);
        }
        public String diagnostic() {
            return "attempt{serverTick="+serverPhysicalTick+", gameTick="+worldGameTick+", result="+result
                    +", callbackCount="+moves.size()+", groundedBefore="+groundedBefore+", groundedAfter="+groundedAfter
                    +", failure="+failure+", nativeMoves="+nativeMoves+"}";
        }
    }

    /** Scalar-only copy captured synchronously in GroundedHarnessWorldStep's actual MoveResult callback. */
    public record NativeMoveEvidence(long serverPhysicalTick,long worldGameTick,
            com.juicyslew.moonstation14.ms14.movement.MovementVector requested,
            com.juicyslew.moonstation14.ms14.movement.MovementVector measured,
            com.juicyslew.moonstation14.ms14.movement.MovementVector accepted,
            boolean collisionInfoFresh,boolean collisionInfoPresent,UUID beforeFrameId,UUID afterFrameId,
            Vec3 beforeStoragePoint,Vec3 afterStoragePoint,
            UUID collisionTrackingFrameId,boolean verticalCollision,boolean verticalCollisionBelow,
            boolean horizontalCollision,boolean sublevelHorizontalCollision,boolean groundedAfterNativeMove,
            boolean idleSupportProbeAttested,boolean idleSupportProbeCertified,boolean proofGroundedFloorSide,
            com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.Branch proofBranch,
            boolean proofFresh,boolean proofTrackedBody,
            com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.PositionStage proofPositionStage,
            String proofFailure,
            String proofDiagnostic) {
        public boolean nativeGroundSupportFor(UUID frameId) {
            boolean completedNativeGroundContact=frameId!=null&&collisionInfoFresh&&collisionInfoPresent
                    &&frameId.equals(beforeFrameId)&&frameId.equals(afterFrameId)&&frameId.equals(collisionTrackingFrameId)
                    &&verticalCollision&&verticalCollisionBelow&&!horizontalCollision
                    &&groundedAfterNativeMove;
            if(!completedNativeGroundContact) return false;
            // The immutable native CollisionInfo is the primary grounded-contact evidence. Sable's
            // NativeContactProofDiagnostic is optional correction-branch telemetry: `none()` is not
            // a failed contact proof, and PositionStage is not a universal floor-support verdict.
            return switch(proofBranch) {
                // `none()` is the production default when no optional correction diagnostic ran. The
                // completed fresh native below-contact above is the support proof in that case.
                case NONE -> proofPositionStage==com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED
                        &&"NONE".equals(proofFailure)&&!proofFresh&&!proofTrackedBody
                        &&!idleSupportProbeCertified&&!proofGroundedFloorSide;
                // Populated IDLE diagnostics must be fresh/tracked; ordinary contact needs no correction
                // stage, while a recorded correction uses only the existing certification result.
                case IDLE_FLOOR -> idleSupportProbeAttested&&proofFresh&&proofTrackedBody
                        &&"NONE".equals(proofFailure)&&!proofGroundedFloorSide
                        &&(proofPositionStage==com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED
                        ||(proofPositionStage==com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.PositionStage.ACCEPTED
                        &&idleSupportProbeCertified));
                case SUPPORTED_FLOOR_SIDE -> proofFresh&&proofTrackedBody&&proofGroundedFloorSide
                        &&"NONE".equals(proofFailure)
                        &&proofPositionStage==com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.PositionStage.ACCEPTED;
                default -> false;
            };
        }
    }

    /** Completed lifecycle result paired with its immediately-frozen native move evidence. */
    public record CompletedNativeStep(NativeMoveEvidence move,boolean resultAccepted,int moveCount,
                                      boolean groundedAfterAttempt,String failure) {
        public boolean supportedGroundedFrame(UUID frameId) {
            return move!=null&&resultAccepted&&moveCount==1&&groundedAfterAttempt&&failure==null
                    &&move.nativeGroundSupportFor(frameId);
        }
        public String diagnostic() {
            return "completedStep{resultAccepted="+resultAccepted+", moveCount="+moveCount
                    +", groundedAfterAttempt="+groundedAfterAttempt+", failure="+failure+", move="+move+"}";
        }
    }

    /** Mutable scratch read exists only while copying SDK CollisionInfo fields; never stored in Attempt. */
    static final class NativeContactRead {
        UUID trackingFrameId;
        boolean present,verticalCollision,verticalCollisionBelow,horizontalCollision,sublevelHorizontalCollision;
        NativeContactRead(UUID trackingFrameId,boolean present,boolean verticalCollision,boolean verticalCollisionBelow,
                boolean horizontalCollision,boolean sublevelHorizontalCollision) {
            this.trackingFrameId=trackingFrameId;this.present=present;this.verticalCollision=verticalCollision;
            this.verticalCollisionBelow=verticalCollisionBelow;this.horizontalCollision=horizontalCollision;
            this.sublevelHorizontalCollision=sublevelHorizontalCollision;
        }
    }

    static NativeMoveEvidence freezeNativeMove(long tick,long gameTick,
            com.juicyslew.moonstation14.ms14.movement.MovementVector requested,
            com.juicyslew.moonstation14.ms14.movement.MovementVector measured,
            com.juicyslew.moonstation14.ms14.movement.MovementVector accepted,boolean collisionInfoFresh,
            UUID beforeFrameId,UUID afterFrameId,NativeContactRead contact,boolean groundedAfterNativeMove,
            Vec3 beforeStoragePoint,Vec3 afterStoragePoint,
            boolean idleSupportProbeAttested,boolean idleSupportProbeCertified,
            com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic proof) {
        String proofString=proof.toString();
        return freezeNativeMoveFields(tick,gameTick,requested,measured,accepted,collisionInfoFresh,
                beforeFrameId,afterFrameId,contact,groundedAfterNativeMove,beforeStoragePoint,afterStoragePoint,
                idleSupportProbeAttested,idleSupportProbeCertified,proof.branch(),proof.fresh(),proof.trackedBody(),
            proof.groundedFloorSide(),proof.positionStage(),proofString.contains("reason=NONE")?"NONE":"NON_NONE",proofString);
    }

    static NativeMoveEvidence freezeNativeMoveFields(long tick,long gameTick,
            com.juicyslew.moonstation14.ms14.movement.MovementVector requested,
            com.juicyslew.moonstation14.ms14.movement.MovementVector measured,
            com.juicyslew.moonstation14.ms14.movement.MovementVector accepted,boolean collisionInfoFresh,
            UUID beforeFrameId,UUID afterFrameId,NativeContactRead contact,boolean groundedAfterNativeMove,
            Vec3 beforeStoragePoint,Vec3 afterStoragePoint,boolean idleSupportProbeAttested,
            boolean idleSupportProbeCertified,
            com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.Branch proofBranch,
            boolean proofFresh,boolean proofTrackedBody,boolean proofGroundedFloorSide,
            com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic.PositionStage proofPositionStage,
            String proofFailure,String proofDiagnostic) {
        return new NativeMoveEvidence(tick,gameTick,requested,measured,accepted,collisionInfoFresh,contact.present,
                beforeFrameId,afterFrameId,beforeStoragePoint,afterStoragePoint,contact.trackingFrameId,contact.verticalCollision,contact.verticalCollisionBelow,
                contact.horizontalCollision,contact.sublevelHorizontalCollision,groundedAfterNativeMove,
                idleSupportProbeAttested,idleSupportProbeCertified,proofGroundedFloorSide,proofBranch,proofFresh,proofTrackedBody,
                proofPositionStage,proofFailure,proofDiagnostic);
    }

    private static UUID frameId(com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point) {
        return point==null?null:point.frameId();
    }
    private static Vec3 storagePoint(com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point) {
        return point==null?null:new Vec3(point.storagePoint().x,point.storagePoint().y,point.storagePoint().z);
    }

    /** Return all actual invocation records for this completed server tick; caller enforces exactly one. */
    public static List<Attempt> attemptsAtServerTick(List<Attempt> attempts,long tick) {
        return attempts.stream().filter(attempt->attempt.serverPhysicalTick()==tick).toList();
    }

    public static AutoCloseable install(ServerPlayer owner, AtomicReference<List<Attempt>> attempts) {
        return install(owner, attempts, Integer.MAX_VALUE, new AtomicBoolean(),false);
    }

    /** Bounded fixture-only observer; overflow is reported without changing production stepping. */
    public static AutoCloseable install(ServerPlayer owner, AtomicReference<List<Attempt>> attempts,
                                         int maximumAttempts, AtomicBoolean overflow) {
        return install(owner,attempts,maximumAttempts,overflow,false);
    }

    /** Additive fixture opt-in: freezes mutable Sable contact fields at the actual MoveResult callback. */
    public static AutoCloseable installWithNativeContactEvidence(ServerPlayer owner,
            AtomicReference<List<Attempt>> attempts,int maximumAttempts,AtomicBoolean overflow) {
        return install(owner,attempts,maximumAttempts,overflow,true);
    }

    private static AutoCloseable install(ServerPlayer owner,AtomicReference<List<Attempt>> attempts,
                                         int maximumAttempts,AtomicBoolean overflow,boolean freezeNativeEvidence) {
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        var production = new GroundedHarnessWorldStep();
        return LifecycleCharacterSessionControl.withWorldStepForTesting(owner,
                 (body, x, z, jump, sneak, sprint, yaw, originSequence, observer, reference) -> {
                    long serverTick=owner.level().getServer().getTickCount();
                    long gameTick=body.level().getGameTime();
                    Vec3 before=body.position(), beforeVelocity=body.getDeltaMovement();
                    boolean groundedBefore=body.onGround();
                    var moves=new ArrayList<SableOwnedBodyMotion.MoveResult>();
                    var nativeMoves=new ArrayList<NativeMoveEvidence>();
                    Optional<com.juicyslew.moonstation14.ms14.movement.CharacterMovementState> result=null;
                    String failure=null;
                    try {
                         result=production.step(body,x,z,jump,sneak,sprint,yaw,originSequence,moved->{
                             moves.add(moved);
                             if(observer!=null) observer.accept(moved);
                            if(!freezeNativeEvidence) return;
                            long callbackTick=owner.level().getServer().getTickCount();
                            long callbackGameTick=body.level().getGameTime();
                            var info=moved.collisionInfo();
                            var tracking=info==null||info.trackingSubLevel==null?null:info.trackingSubLevel.getUniqueId();
                            var contact=new NativeContactRead(tracking,info!=null,info!=null&&info.verticalCollision,
                                    info!=null&&info.verticalCollisionBelow,info!=null&&info.horizontalCollision,
                                    info!=null&&info.subLevelHorizontalCollision);
                            var proof=moved.nativeContactProofDiagnostic();
                            nativeMoves.add(freezeNativeMove(callbackTick,callbackGameTick,
                                    moved.requestedOwnDisplacement(),moved.measuredOwnDisplacement(),moved.acceptedOwnDisplacement(),
                                    moved.collisionInfoFresh(),frameId(moved.beforePoint()),frameId(moved.afterPoint()),contact,
                                    body.onGround(),storagePoint(moved.beforePoint()),storagePoint(moved.afterPoint()),
                                    moved.idleSupportProbeAttested(),moved.idleSupportProbeCertified(),proof));
                        },reference);
                        return result;
                    } catch (RuntimeException | Error thrown) {
                        failure=thrown.getClass().getName()+": "+thrown.getMessage();
                        throw thrown;
                    } finally {
                        var attempt=new Attempt(serverTick,gameTick,x,z,jump,sprint,yaw,reference,
                                before,beforeVelocity,groundedBefore,body.position(),body.getDeltaMovement(),body.onGround(),
                                result,List.copyOf(moves),List.copyOf(nativeMoves),failure);
                        attempts.updateAndGet(existing -> {
                            if (existing.size() >= maximumAttempts) { overflow.set(true); return existing; }
                            var appended = new ArrayList<>(existing); appended.add(attempt); return List.copyOf(appended);
                        });
                    }
                });
    }
}
