package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.world.entity.Entity;

/** Compact typed SDK collision report, used only on rejected or explicitly sampled movement. */
public final class OwnedNativeMoveDiagnostic {
    private OwnedNativeMoveDiagnostic() { }

    /** Full collision traces are expensive to format; ordinary warnings retain only the compact rejection. */
    public static boolean shouldDescribeRejection(boolean explicitDiagnostic) {
        return explicitDiagnostic;
    }

    public static String describe(Entity entity, SableOwnedBodyMotion.MoveResult move) {
        return describe(entity, move, false);
    }

    /** Opt-in full ordered trace; defaults preserve compact formatting. */
    public static String describe(Entity entity, SableOwnedBodyMotion.MoveResult move, boolean fullTrace) {
        var info = move == null ? null : move.collisionInfo();
        var tracking = entity instanceof EntityMovementExtension movement
                ? movement.sable$getTrackingSubLevel() : null;
        StringBuilder contacts = new StringBuilder("[");
        if (info != null && info.firstCollisions != null) {
            int count = 0;
            for (var entry : info.firstCollisions.entrySet()) {
                if (count++ == 4) { contacts.append("..."); break; }
                if (contacts.length() > 1) contacts.append(',');
                var contact = entry.getValue();
                contacts.append("frame=").append(entry.getKey().getUniqueId())
                        .append(" BODYLOCAL=").append(contact.localLocation())
                        .append(" normal=").append(contact.globalDirection())
                        .append(" horizontal=").append(contact.horizontal())
                        .append(" block=").append(contact.block());
            }
        }
        contacts.append(']');
        var trackedFrame = info == null ? null : info.trackingSubLevel;
        return "actorType=" + entity.getType() + " actorPose=" + entity.position() + " actorDimensions="
                + entity.getBbWidth() + "x" + entity.getBbHeight() + " actorYaw=" + entity.getYRot()
                 + " ownRequest=" + (move == null ? null : move.requestedOwnDisplacement())
                 + " idleSupportProbeAttested=" + (move != null && move.idleSupportProbeAttested())
                 + " idleSupportProbeCertified=" + (move != null && move.idleSupportProbeCertified())
                + " nativeFresh=" + (move != null && move.collisionInfoFresh())
                + " measuredOwnPreCarry=" + (move == null ? null : move.measuredOwnDisplacement())
                + " acceptedOwnNormalized=" + (move == null ? null : move.acceptedOwnDisplacement())
                + " collisionPosePolicy=" + (move == null ? null : move.collisionPosePolicy())
                 + " horizontalContactEvidence=" + (move == null ? null : move.horizontalContactEvidence())
                  + " nativeContactProof=" + (move == null ? null : move.nativeContactProofDiagnostic())
                  + " clearanceWitness=" + (move == null || move.nativeContactProofDiagnostic() == null
                          ? null : clearanceWitness(move.nativeContactProofDiagnostic().clearanceObservation()))
                + " sdkBodyYawInputPose=" + (move == null || move.collisionScope() == null
                        ? null : move.collisionScope().observedSdkYawSource())
                + " selectedOwnedBodyYawPose=" + (move == null || move.collisionScope() == null
                        ? null : move.collisionScope().selectedOwnedYawPose())
                + " microCarryCutoffRetained=" + (move != null && move.collisionScope() != null
                        && move.collisionScope().microCarryCutoffRetained())
                 + " trackedPoseInterval=" + (trackedFrame == null ? null
                         : "[" + trackedFrame.lastPose() + " -> " + trackedFrame.logicalPose() + "]")
                 + " startingVelocityBasis=" + (move == null || move.collisionScope() == null
                         ? null : move.collisionScope().startingBasis())
                 + " baseTransportRawVsSelected=" + (move == null || move.collisionScope() == null
                         ? null : move.collisionScope().transportObservation())
                 + " nativeTracking=" + (tracking == null ? null : tracking.getUniqueId())
                + " nativeMotion=" + (info == null ? null : info.motion)
                + " inherited=" + (info == null ? null : info.inheritedMotion)
                + " collisionFlags=" + (info == null ? null : flags(info))
                 + " contacts=" + contacts
                  + " nativeCorrectionTraceComplete=" + (move == null || move.collisionScope() == null
                          || move.collisionScope().nativeCollisionTraceComplete())
                  + " geometryObserved=" + (move != null && move.collisionScope() != null
                          && move.collisionScope().geometryObserved())
                  + " geometryTraceComplete=" + (move == null || move.collisionScope() == null
                          || move.collisionScope().geometryTraceComplete())
                  + " nativeCorrectionTrace=" + traceSummary(move, fullTrace)
                 + " worldClip=" + worldClipSummary(move)
                 + " yawSamplesComplete=" + (move == null || move.collisionScope() == null || move.collisionScope().yawSamplesComplete())
                 + " yawSamples=" + (move == null || move.collisionScope() == null ? null : move.collisionScope().yawSamples())
                + " pointBefore=" + (move == null ? null : move.beforePoint())
                + " pointAfter=" + (move == null ? null : move.afterPoint());
    }

    private static String clearanceWitness(NativeContactProofDiagnostic.ClearanceObservation observation) {
        if (observation == null) return null;
        return "captured=" + observation.capturedDetail() + ",final=" + observation.finalDetail();
    }

    /** Small rejection-only summary that keeps decision values ahead of the verbose captured trace. */
    public static String rejectionSummary(MovementVector requested, MovementVector returnedActual,
            boolean groundedAtCatch, String reason, SableOwnedBodyMotion.MoveResult move) {
        var proof = move == null ? null : move.nativeContactProofDiagnostic();
        var evidence = move == null ? null : move.horizontalContactEvidence();
        String boundedReason = reason == null ? "unavailable" : reason.replace('\n', ' ').replace('\r', ' ');
        if (boundedReason.length() > 240) boundedReason = boundedReason.substring(0, 240) + "…";
        return "motorRejection reason=\"" + boundedReason + "\" motorRequested=" + requested
                + " motorReturnedActual=" + returnedActual + " groundedAtCatch=" + groundedAtCatch
                + " nativeMeasured=" + (move == null ? null : move.measuredOwnDisplacement())
                + " nativeBounded=" + (move == null ? null : move.boundedOwnDisplacement())
                + " positionCorrection=" + (move == null || move.positionCorrection() == null ? "absent"
                        : "present:" + move.positionCorrection().geometryCorrection())
                + " positionCorrectionMatches=" + (move == null || move.positionCorrection() == null
                        ? "unavailable" : move.positionCorrection().matches(requested,
                                move.boundedOwnDisplacement(), groundedAtCatch))
                + " horizontalEvidence=" + (evidence == null ? "absent" : "present")
                + " horizontalEvidenceMatches=" + (evidence == null || requested == null || returnedActual == null
                        ? "unavailable" : evidence.matches(requested, returnedActual))
                 + " " + (proof == null ? "clearance=NOT_EVALUATED" : proof.clearanceObservation().token())
                 + " positionStage=" + (proof == null ? "unavailable" : proof.positionStage())
                + " groundingDecision=" + (proof == null ? "unavailable" : proof.groundingDecision());
    }

    private static String worldClipSummary(SableOwnedBodyMotion.MoveResult move) {
        if (move == null || move.collisionScope() == null) return null;
        var clip = move.collisionScope().worldClipObservation();
        if (clip == null) return "absent";
        return "complete:" + clip.complete() + ",entity:" + clip.entity().getId()
                + ",downwardFloorClip:" + clip.downwardFloorClip()
                + ",input:" + clip.input() + ",output:" + clip.output()
                + ",positionBefore:" + clip.positionBefore() + ",boundsBefore:" + clip.boundsBefore();
    }

    private static String traceSummary(SableOwnedBodyMotion.MoveResult move, boolean fullTrace) {
        if (move == null || move.collisionScope() == null) return null;
        var trace = move.collisionScope().nativeCollisionTrace();
        var proof = move.nativeContactProofDiagnostic();
        int index = proof == null ? -1 : proof.rowIndex();
        return fullTrace ? fullTraceSummary(trace, index, !move.collisionScope().nativeCollisionTraceComplete())
                : failureRowSummary(trace, index);
    }

    static String fullTraceSummary(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> trace, int index) {
        return fullTraceSummary(trace, index, false);
    }

    static String fullTraceSummary(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> trace, int index,
                                   boolean truncated) {
        if (trace == null) return failureRowSummary(null, index) + " fullTraceUnavailable";
        StringBuilder out = new StringBuilder(failureRowSummary(trace, index)).append(" fullTrace[rows=")
                .append(trace.size()).append(", truncated=").append(truncated).append("]{");
        for (int i = 0; i < trace.size(); i++) {
            if (i > 0) out.append(';');
            out.append(fullRowSummary("row[index=" + i + ",", trace.get(i)));
        }
        return out.append('}').toString();
    }

    private static String fullRowSummary(String prefix, CurrentFrameCollisionScope.NativeCollisionTrace row) {
        return prefix + "frameId=" + row.frameId() + ",substep=" + row.substep() + ",substeps=" + row.substeps()
                + ",iteration=" + row.iteration() + ",preCollisionMotion=" + row.collisionMotionBeforeX() + ","
                + row.collisionMotionBeforeY() + "," + row.collisionMotionBeforeZ()
                + ",verticalCollision=" + row.verticalCollision() + "," + rowSummary("", row);
    }

    static String failureRowSummary(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> trace, int index) {
        if (trace == null) return "unavailable (native trace absent; index=" + index + ")";
        if (index >= 0 && index < trace.size()) {
            var row = trace.get(index);
            return rowSummary("failureRow[index=" + index + ",", row);
        }
        StringBuilder summary = new StringBuilder("failureRowUnavailable(index=").append(index)
                .append(", rows=").append(trace.size()).append(") trace=[");
        java.util.HashSet<Integer> included = new java.util.HashSet<>();
        for (int i = 0; i < trace.size() && included.size() < 3; i++) {
            if (included.add(i)) summary.append(rowSummary("row[index=" + i + ",", trace.get(i))).append(';');
        }
        int witness = -1;
        for (int i = 0; i < trace.size(); i++) {
            var row = trace.get(i);
            if (row.appliedX() != 0d || row.appliedY() != 0d || row.appliedZ() != 0d
                    || row.responseX() != 0d || row.responseY() != 0d || row.responseZ() != 0d) {
                if (row.verticalCollision()) { witness = i; break; }
            }
        }
        if (witness >= 0) {
            if (included.add(witness)) summary.append(rowSummary("verticalWitness[index=" + witness + ",", trace.get(witness))).append(';');
            else summary.append("verticalWitnessAlreadyIncluded[index=").append(witness).append("]; ");
        } else summary.append("verticalWitness=absent; ");
        return summary.append(']').toString();
    }

    private static String rowSummary(String prefix, CurrentFrameCollisionScope.NativeCollisionTrace row) {
        return prefix + "selectedPos=" + row.selectedPos() + ",selectedState=" + row.selectedState()
                + ",block=" + row.block() + ",blockState=" + row.blockState() + ",selected="
                + row.selectedX() + "," + row.selectedY() + "," + row.selectedZ() + ",applied="
                + row.appliedX() + "," + row.appliedY() + "," + row.appliedZ() + ",response="
                + row.responseX() + "," + row.responseY() + "," + row.responseZ() + ",geometry="
                + (row.geometry() == null ? "absent" : row.geometry()) + "]";
    }

    private static String flags(SubLevelEntityCollision.CollisionInfo info) {
        return "below:" + info.verticalCollisionBelow + ",vertical:" + info.verticalCollision
                + ",horizontal:" + info.horizontalCollision + ",sublevelHorizontal:" + info.subLevelHorizontalCollision
                + ",minorHorizontal:" + info.minorHorizontalCollision;
    }
}
