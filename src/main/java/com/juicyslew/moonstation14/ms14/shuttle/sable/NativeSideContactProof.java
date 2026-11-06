package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Conservative replay proof for native side-contact clipping and its remaining geometric correction. */
final class NativeSideContactProof {
    private enum VerticalPolicy { NONE, SUPPORTED_FLOOR, TRANSIENT_FLOOR, ASCENDING_FLOOR, CEILING, CEILING_ONLY }
    private static final double EPS = NativeCollisionSlop.MAX_RESIDUAL;

    record Result(MovementVector boundedOwn, MovementVector geometryCorrection,
                  HorizontalContactEvidence horizontalContact) { }

    enum Failure { NONE, INVALID_CONTEXT, UNKNOWN_DATA_OR_FRAME, INVALID_ORDER, REQUEST_ACCUMULATION_MISMATCH,
        INVALID_SELECTED_VECTOR, UNSUPPORTED_VERTICAL_ROW, GEOMETRY_REPLAY_FAILURE, NON_YAW_BODY_GEOMETRY,
        EXCESSIVE_NORMAL_Y, FINAL_TRACE_RECONSTRUCTION_MISMATCH, UNEXPLAINED_Y,
        CONTACT_EVIDENCE_REJECTED, INVALID_INPUT }
    record DiagnosticResult(Optional<Result> result, Failure reason, int rowIndex) {
        DiagnosticResult { if (result == null || reason == null) throw new IllegalArgumentException("diagnostic outcome"); }
    }

    private NativeSideContactProof() { }

    static Optional<Result> validate(MovementVector request, MovementVector nativeMotion,
            MovementVector acceptedMeasured, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        return validateDetailed(request,nativeMotion,acceptedMeasured,frameId,trace,knownState).result();
    }

    static DiagnosticResult validateDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector acceptedMeasured, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        return validateInternalDetailed(request,nativeMotion,acceptedMeasured,frameId,trace,knownState,VerticalPolicy.NONE,null);
    }

    static DiagnosticResult validateWorldFloorSideDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState,
            CurrentFrameCollisionScope.WorldClipObservation worldClip) {
        if(request==null||nativeMotion==null||measuredOwn==null||worldClip==null||!worldClip.complete()
                ||request.y()>=0d||nativeMotion.y()>=0d||measuredOwn.y()>0d||worldClip.input()==null||worldClip.output()==null
                ||!vecMatches(worldClip.input(),nativeMotion)||!vecMatches(worldClip.output(),measuredOwn)||!worldClip.downwardFloorClip())
            return failure(Failure.INVALID_CONTEXT,-1);
        return validateInternalDetailed(request,nativeMotion,measuredOwn,frameId,trace,knownState,VerticalPolicy.NONE,worldClip);
    }

    /** Geometry proof for a transient floor clip; callers must independently prove final-world clearance. */
    static DiagnosticResult validateTransientFloorAndSideDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState,
            CurrentFrameCollisionScope.WorldClipObservation worldClip) {
        if (request == null || request.y() >= 0d || nativeMotion == null || measuredOwn == null || worldClip == null
                || !worldClip.complete() || worldClip.input() == null || worldClip.output() == null
                || !vecExactlyMatches(worldClip.input(), worldClip.output())
                || !vecMatches(worldClip.input(), nativeMotion) || !vecMatches(worldClip.output(), nativeMotion)
                || !near(nativeMotion, measuredOwn)) return failure(Failure.INVALID_CONTEXT, -1);
        return validateInternalDetailed(request, nativeMotion, measuredOwn, frameId, trace, knownState,
                VerticalPolicy.TRANSIENT_FLOOR, worldClip);
    }

    static Optional<Result> validateSupportedFloorAndSide(MovementVector request, MovementVector nativeMotion,
            MovementVector acceptedMeasured, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState, boolean freshNativeFloorSupport) {
        if (!freshNativeFloorSupport || request == null || request.y() >= 0d) return Optional.empty();
        return validateInternal(request, nativeMotion, acceptedMeasured, frameId, trace, knownState, VerticalPolicy.SUPPORTED_FLOOR, null);
    }

    static DiagnosticResult validateSupportedFloorAndSideDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector acceptedMeasured, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState, boolean freshNativeFloorSupport) {
        if (!freshNativeFloorSupport || request == null || request.y() >= 0d)
            return failure(Failure.INVALID_CONTEXT, -1);
        return validateInternalDetailed(request, nativeMotion, acceptedMeasured, frameId, trace, knownState, VerticalPolicy.SUPPORTED_FLOOR, null);
    }

    static Optional<Result> validateCeilingAndSide(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        return validateCeilingAndSideDetailed(request,nativeMotion,measuredOwn,frameId,trace,knownState).result();
    }

    static DiagnosticResult validateCeilingAndSideDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        if (request==null) return failure(Failure.INVALID_CONTEXT,-1);
        return validateInternalDetailed(request,nativeMotion,measuredOwn,frameId,trace,knownState,VerticalPolicy.CEILING,null);
    }

    /** Ceiling-only native geometry proof; world clipping is bound independently from native responses. */
    static DiagnosticResult validateCeilingOnlyDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState,
            CurrentFrameCollisionScope.WorldClipObservation worldClip) {
        if (request == null || nativeMotion == null || measuredOwn == null || worldClip == null
                || !worldClip.complete() || worldClip.input() == null || worldClip.output() == null
                || !vecMatches(worldClip.input(), nativeMotion) || !vecMatches(worldClip.output(), measuredOwn))
            return failure(Failure.INVALID_CONTEXT, -1);
        boolean identity = vecExactlyMatches(worldClip.input(), worldClip.output());
        boolean floorClip = worldClip.downwardFloorClipWithUnchangedOrVanillaZeroedXZ();
        if (!identity && !floorClip) return failure(Failure.INVALID_CONTEXT, -1);
        return validateInternalDetailed(request, nativeMotion, measuredOwn, frameId, trace, knownState,
                VerticalPolicy.CEILING_ONLY, worldClip);
    }

    /** Geometry-only ascending-floor proof; it does not establish grounded support. */
    static DiagnosticResult validateAscendingFloorDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState,
            CurrentFrameCollisionScope.WorldClipObservation worldClip) {
        if (request == null || nativeMotion == null || measuredOwn == null || request.y() <= 0d
                || worldClip == null || !worldClip.complete() || worldClip.input() == null || worldClip.output() == null
                || worldClip.downwardFloorClip() || !vecExactlyMatches(worldClip.input(), worldClip.output())
                || !vecMatches(worldClip.input(), nativeMotion) || !vecMatches(worldClip.output(), measuredOwn)
                || !near(nativeMotion, measuredOwn)) return failure(Failure.INVALID_CONTEXT, -1);
        return validateInternalDetailed(request, nativeMotion, measuredOwn, frameId, trace, knownState,
                VerticalPolicy.ASCENDING_FLOOR, worldClip);
    }

    /** Dedicated floor-top certificate for the motor-attested, zero-horizontal idle probe. */
    static Optional<Result> validateIdleSupportProbe(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        return validateIdleSupportProbeDomain(request, nativeMotion, measuredOwn, frameId, trace, knownState, false);
    }

    /** Geometry-only validation for the attested probe's downhill response; it never certifies support. */
    static Optional<Result> validateNegativeIdleSupportProbe(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState) {
        return validateIdleSupportProbeDomain(request, nativeMotion, measuredOwn, frameId, trace, knownState, true);
    }

    private static Optional<Result> validateIdleSupportProbeDomain(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState, boolean negativeDomain) {
        if (request == null || nativeMotion == null || measuredOwn == null || frameId == null || trace == null
                || trace.isEmpty() || knownState == null || request.x()!=0d || request.z()!=0d || request.y()>=0d
                || (negativeDomain ? measuredOwn.y() >= 0d : measuredOwn.y() <= 0d)
                || !near(nativeMotion, measuredOwn)) return Optional.empty();
        try {
            NativeSideCollisionWitness witness=new NativeSideCollisionWitness();
            int n=-1, step=0, iteration=-1; MovementVector responses=MovementVector.ZERO;
            Double plane=null; int meaningful=0;
            double[] stepMatrix=null;
            for (var row:trace) {
                if (row==null || !frameId.equals(row.frameId()) || row.selectedPos()==null || row.selectedState()==null
                        || !knownState.test(row)) return Optional.empty();
                if(n<0)n=row.substeps();
                if(!validOrder(row,n,step,iteration))return Optional.empty();
                MovementVector selected=v(row.selectedX(),row.selectedY(),row.selectedZ());
                MovementVector applied=v(row.appliedX(),row.appliedY(),row.appliedZ());
                MovementVector response=v(row.responseX(),row.responseY(),row.responseZ());
                if(!near(request.scale(row.substep()/(double)n).add(responses),v(row.collisionMotionBeforeX(),row.collisionMotionBeforeY(),row.collisionMotionBeforeZ())))return Optional.empty();
                var g=row.geometry(); if(g==null||replayGeometry(row,selected,applied,response,witness)==null)return Optional.empty();
                double[] matrix=g.subLevelPose().matrix();
                if(row.substep()!=step)stepMatrix=matrix;
                else for(int i=0;i<16;i++)if(Math.abs(matrix[i]-stepMatrix[i])>EPS)return Optional.empty();
                var b=g.selectedStorageBounds(); var p=row.selectedPos();
                if(!(b.maxX>b.minX)||!(b.maxY>b.minY)||!(b.maxZ>b.minZ)
                        ||b.minX<p.getX()-EPS||b.maxX>p.getX()+1+EPS||b.minY<p.getY()-EPS||b.maxY>p.getY()+1+EPS
                        ||b.minZ<p.getZ()-EPS||b.maxZ>p.getZ()+1+EPS)return Optional.empty();
                double len=length(selected);
                if(plane==null)plane=b.maxY; else if(Math.abs(plane-b.maxY)>EPS)return Optional.empty();
                if(len>EPS){
                    double ux=matrix[4],uy=matrix[5],uz=matrix[6];
                    if(uy<=0d||Math.abs(selected.x()/len-ux)>EPS||Math.abs(selected.y()/len-uy)>EPS||Math.abs(selected.z()/len-uz)>EPS)return Optional.empty(); meaningful++;
                }
                responses=responses.add(response); step=row.substep();iteration=row.iteration();
            }
             if(meaningful==0||!near(request.add(responses),nativeMotion))return Optional.empty();
            // Negative responses are only a geometry certificate for a separately guarded endpoint
            // decision; never convert the artificial probe into admissible own movement here.
            return Optional.of(new Result(MovementVector.ZERO,measuredOwn,null));
        } catch(RuntimeException invalid){return Optional.empty();}
    }

    static Optional<Result> validateWorldFloorSide(MovementVector request, MovementVector nativeMotion,
            MovementVector measuredOwn, UUID frameId, List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState,
            CurrentFrameCollisionScope.WorldClipObservation worldClip) {
        return validateWorldFloorSideDetailed(request,nativeMotion,measuredOwn,frameId,trace,knownState,worldClip).result();
    }

    private static Optional<Result> validateInternal(MovementVector request, MovementVector nativeMotion,
            MovementVector acceptedMeasured, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState, VerticalPolicy verticalPolicy,
            CurrentFrameCollisionScope.WorldClipObservation worldClip) {
        return validateInternalDetailed(request,nativeMotion,acceptedMeasured,frameId,trace,knownState,verticalPolicy,worldClip).result();
    }

    private static DiagnosticResult validateInternalDetailed(MovementVector request, MovementVector nativeMotion,
            MovementVector acceptedMeasured, UUID frameId,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> knownState, VerticalPolicy verticalPolicy,
            CurrentFrameCollisionScope.WorldClipObservation worldClip) {
        if (request == null || nativeMotion == null || acceptedMeasured == null || frameId == null
                || trace == null || trace.isEmpty() || knownState == null) return failure(Failure.INVALID_CONTEXT,-1);
        try {
            NativeSideCollisionWitness witness = new NativeSideCollisionWitness();
            int substeps = -1, lastStep = 0, lastIteration = -1;
            MovementVector responses = MovementVector.ZERO, witnessed = MovementVector.ZERO;
            MovementVector selectedSum = MovementVector.ZERO, sideAppliedSum = MovementVector.ZERO;
            MovementVector ceilingSelected = MovementVector.ZERO;
            MovementVector sideResponseSum = MovementVector.ZERO;
            MovementVector floorAppliedSum = MovementVector.ZERO, floorResponseSum = MovementVector.ZERO;
            MovementVector transientFloorSelected = MovementVector.ZERO;
            MovementVector firstNormal = null;
            boolean sawFloor = false, sawCeiling = false, sawSide = false;
            List<MovementVector> selectedRows = new ArrayList<>();
            int rowIndex = 0;
            for (var row : trace) {
                if (row == null || !frameId.equals(row.frameId()) || row.selectedPos() == null
                        || row.selectedState() == null || !knownState.test(row)) return failure(Failure.UNKNOWN_DATA_OR_FRAME,rowIndex);
                if (substeps < 0) substeps = row.substeps();
                if (!validOrder(row, substeps, lastStep, lastIteration)) return failure(Failure.INVALID_ORDER,rowIndex);
                MovementVector expected = request.scale(row.substep() / (double) substeps).add(responses);
                if (!near(expected, v(row.collisionMotionBeforeX(), row.collisionMotionBeforeY(), row.collisionMotionBeforeZ()))) return failure(Failure.REQUEST_ACCUMULATION_MISMATCH,rowIndex);
                MovementVector selected = v(row.selectedX(), row.selectedY(), row.selectedZ());
                MovementVector applied = v(row.appliedX(), row.appliedY(), row.appliedZ());
                MovementVector response = v(row.responseX(), row.responseY(), row.responseZ());
                double len = length(selected);
                if (!Double.isFinite(len) || !(len > 0d)) return failure(Failure.INVALID_SELECTED_VECTOR,rowIndex);
                if (row.verticalCollision()) {
                    if ((verticalPolicy==VerticalPolicy.SUPPORTED_FLOOR || verticalPolicy==VerticalPolicy.TRANSIENT_FLOOR
                            || verticalPolicy==VerticalPolicy.ASCENDING_FLOOR)
                            && selected.y() > Math.hypot(selected.x(), selected.z())
                            && SableOwnedBodyMotion.nativeFloorAppliedResponseAdmissible(selected, applied, response)) {
                        if (verticalPolicy==VerticalPolicy.TRANSIENT_FLOOR || verticalPolicy==VerticalPolicy.ASCENDING_FLOOR) {
                            MovementVector calculated = replayGeometry(row, selected, applied, response, witness);
                            if (calculated == null) return failure(Failure.GEOMETRY_REPLAY_FAILURE,rowIndex);
                            witnessed = witnessed.add(calculated);
                            transientFloorSelected = transientFloorSelected.add(selected);
                        }
                        sawFloor = true; selectedRows.add(null);
                        floorAppliedSum = floorAppliedSum.add(applied); floorResponseSum = floorResponseSum.add(response);
                    } else if ((verticalPolicy==VerticalPolicy.CEILING || verticalPolicy==VerticalPolicy.CEILING_ONLY)
                            && selected.y() < -Math.hypot(selected.x(), selected.z())) {
                        MovementVector calculated=replayGeometry(row,selected,applied,response,witness);
                        if (calculated==null)
                            return failure(Failure.GEOMETRY_REPLAY_FAILURE,rowIndex);
                        witnessed=witnessed.add(calculated);
                        ceilingSelected=ceilingSelected.add(selected);
                        sawCeiling=true; selectedRows.add(selected);
                    } else return failure(Failure.UNSUPPORTED_VERTICAL_ROW,rowIndex);
                } else {
                    var geometry = row.geometry();
                    MovementVector calculated = replayGeometry(row, selected, applied, response, witness);
                    if (calculated == null) return failure(Failure.GEOMETRY_REPLAY_FAILURE,rowIndex);
                    witnessed = witnessed.add(calculated);
                    selectedSum = selectedSum.add(selected);
                    sideAppliedSum = sideAppliedSum.add(applied);
                    sideResponseSum = sideResponseSum.add(response);
                    selectedRows.add(selected);
                    if (firstNormal == null && len > 0d) firstNormal = selected.scale(1d / len);
                    var q = geometry.entityBoundsOrientation();
                    if (Math.abs(q.x()) > EPS || Math.abs(q.z()) > EPS) return failure(Failure.NON_YAW_BODY_GEOMETRY,rowIndex);
                    double ny = selected.y();
                    if (Math.abs(ny / len) > .6) return failure(Failure.EXCESSIVE_NORMAL_Y,rowIndex);
                    sawSide = true;
                }
                responses = responses.add(response);
                lastStep = row.substep(); lastIteration = row.iteration();
                rowIndex++;
            }
            if ((!sawSide && verticalPolicy!=VerticalPolicy.CEILING_ONLY && verticalPolicy!=VerticalPolicy.ASCENDING_FLOOR)
                            || ((verticalPolicy==VerticalPolicy.SUPPORTED_FLOOR || verticalPolicy==VerticalPolicy.TRANSIENT_FLOOR
                                || verticalPolicy==VerticalPolicy.ASCENDING_FLOOR) && !sawFloor)
                    || ((verticalPolicy==VerticalPolicy.CEILING || verticalPolicy==VerticalPolicy.CEILING_ONLY) && !sawCeiling)
                    || (verticalPolicy==VerticalPolicy.CEILING_ONLY && sawSide)
                    || (sawFloor && verticalPolicy!=VerticalPolicy.SUPPORTED_FLOOR && verticalPolicy!=VerticalPolicy.TRANSIENT_FLOOR
                        && verticalPolicy!=VerticalPolicy.ASCENDING_FLOOR)
                    || (verticalPolicy==VerticalPolicy.CEILING && !near(nativeMotion,acceptedMeasured))
                    || !near(witnessed, selectedSum.add(ceilingSelected).add(transientFloorSelected)) || !near(sideAppliedSum, selectedSum)
                    || !near(sideResponseSum, selectedSum)
                    || (sawFloor && !near(floorAppliedSum, floorResponseSum))
                     || !near(request.add(responses), nativeMotion)) return failure(Failure.FINAL_TRACE_RECONSTRUCTION_MISMATCH,trace.size()-1);
            OwnResponseReplay replayRecord = replayOwnResponsesAt(request, substeps, trace, selectedRows);
            MovementVector own = replayRecord.aggregate();
            MovementVector replay = request.add(own);
            double boundedY = acceptedMeasured.y();
            if (verticalPolicy==VerticalPolicy.TRANSIENT_FLOOR && sawFloor) {
                boundedY = request.y() + replayRecord.floorClippedOwnY();
                if (boundedY < request.y() || boundedY > 0d
                        || Math.abs(acceptedMeasured.y()-boundedY) > EPS) return failure(Failure.UNEXPLAINED_Y,trace.size()-1);
            } else if (verticalPolicy==VerticalPolicy.ASCENDING_FLOOR && sawFloor) {
                if (acceptedMeasured.y() < request.y()) return failure(Failure.UNEXPLAINED_Y,trace.size()-1);
                boundedY=request.y();
            } else if (sawFloor) {
                if (acceptedMeasured.y() < request.y()) return failure(Failure.UNEXPLAINED_Y,trace.size()-1);
                boundedY = Math.min(0d, acceptedMeasured.y());
            } else if(sawCeiling) {
                // A ceiling can clip upward own motion, but it cannot authorize clipping a
                // descending request. Preserve the full request in that case.
                boundedY=request.y()<0d?request.y():Math.max(0d,Math.min(request.y(),request.y()+own.y()));
                if (acceptedMeasured.y()>request.y()+EPS)
                    return failure(Failure.UNEXPLAINED_Y,trace.size()-1);
                if (verticalPolicy==VerticalPolicy.CEILING_ONLY && request.y()>0d && boundedY<=EPS)
                    boundedY=0d;
            } else if(worldClip!=null) {
                boundedY=acceptedMeasured.y();
            } else {
                // Pure side rows cannot authorize a vertical own-motion clip. Preserve the exact
                // requested Y. The independently replayed complete side MTV sum must explain the
                // entire signed geometric remainder; this is not a slop expansion.
                if (Math.abs(nativeMotion.y()-acceptedMeasured.y()) > EPS
                        || Math.abs((acceptedMeasured.y()-request.y())-selectedSum.y()) > EPS)
                    return failure(Failure.UNEXPLAINED_Y,trace.size()-1);
                boundedY = request.y();
            }
            MovementVector bounded = NativeCollisionSlop.accepted(request,
                    verticalPolicy == VerticalPolicy.CEILING_ONLY
                            ? new MovementVector(request.x(), boundedY, request.z())
                            : new MovementVector(replay.x(), boundedY, replay.z()));
            MovementVector correction = new MovementVector(acceptedMeasured.x()-bounded.x(),
                    acceptedMeasured.y()-bounded.y(), acceptedMeasured.z()-bounded.z());
            if (sawFloor && verticalPolicy != VerticalPolicy.TRANSIENT_FLOOR && correction.y() < 0d) return failure(Failure.UNEXPLAINED_Y,trace.size()-1);
            HorizontalContactEvidence contact = null;
            if (firstNormal != null && !sameXZ(request, bounded)) {
                List<HorizontalContactEvidence.Projection> projections = replayRecord.projections();
                contact = HorizontalContactEvidence.validate(request, bounded, firstNormal.x(), firstNormal.y(), firstNormal.z()).orElse(null);
                if (contact == null && !projections.isEmpty())
                    contact = HorizontalContactEvidence.validateOrdered(request, bounded, substeps, projections).orElse(null);
            }
            if (!sameXZ(request, bounded) && contact == null) return failure(Failure.CONTACT_EVIDENCE_REJECTED,trace.size()-1);
            return new DiagnosticResult(Optional.of(new Result(bounded, correction, contact)),Failure.NONE,-1);
        } catch (RuntimeException invalid) { return failure(Failure.INVALID_INPUT,-1); }
    }

    private static DiagnosticResult failure(Failure reason,int rowIndex) {
        return new DiagnosticResult(Optional.empty(),reason,rowIndex);
    }

    record OwnResponseReplay(MovementVector aggregate, double floorClippedOwnY,
            List<HorizontalContactEvidence.Projection> projections) { }

    static MovementVector ownResponsesAt(MovementVector request, int n,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, List<MovementVector> selectedRows) {
        return replayOwnResponsesAt(request, n, rows, selectedRows).aggregate();
    }

    static OwnResponseReplay replayOwnResponsesAt(MovementVector request, int n,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, List<MovementVector> selectedRows) {
        MovementVector accumulated = MovementVector.ZERO;
        double floorClippedOwnY = 0d;
        double ceilingClippedOwnY = 0d;
        int rowIndex = 0;
        List<HorizontalContactEvidence.Projection> projections = new ArrayList<>();
        for (int step=1; step<=n; step++) {
            double ownStepY = request.y()*step/(double)n + floorClippedOwnY + ceilingClippedOwnY;
            MovementVector acc = new MovementVector(request.x()*step/(double)n + accumulated.x(),
                    ownStepY, request.z()*step/(double)n+accumulated.z());
            while (rowIndex < rows.size() && rows.get(rowIndex).substep() == step) {
                int currentRow=rowIndex;
                MovementVector mtv = selectedRows.get(rowIndex++);
                if (mtv == null) {
                    // A verified supported-floor row clips this tick's downward own request, not
                    // the complete SDK response (which also contains native floor geometry).
                    if (request.y() < 0d) {
                        floorClippedOwnY = -request.y()*step/(double)n;
                        ownStepY = 0d;
                        acc = new MovementVector(request.x()*step/(double)n+accumulated.x(),
                                ownStepY, request.z()*step/(double)n+accumulated.z());
                    }
                    continue;
                }
                if (rows.get(currentRow).verticalCollision() && mtv.y()<0d) {
                    if (ownStepY>0d) {
                        double correction=Math.max(-ownStepY,mtv.y());
                        ceilingClippedOwnY+=correction;
                        ownStepY+=correction;
                    }
                    acc=new MovementVector(request.x()*step/(double)n+accumulated.x(),ownStepY,
                            request.z()*step/(double)n+accumulated.z());
                }
                double len = length(mtv);
                if (!(len > 0d)) continue;
                MovementVector beforeProjection = acc;
                double lambda = Math.min(len, Math.max(0d, -(acc.x()*mtv.x()+acc.y()*mtv.y()+acc.z()*mtv.z())/len));
                MovementVector projection = new MovementVector(mtv.x()/len*lambda, 0, mtv.z()/len*lambda);
                accumulated = accumulated.add(projection);
                if (lambda > 0d) projections.add(new HorizontalContactEvidence.Projection(step, beforeProjection,
                        new MovementVector(beforeProjection.x()+projection.x(), beforeProjection.y(), beforeProjection.z()+projection.z()),
                        projection, mtv.x()/len, mtv.y()/len, mtv.z()/len));
                acc = new MovementVector(request.x()*step/(double)n+accumulated.x(),
                        ownStepY, request.z()*step/(double)n+accumulated.z());
            }
        }
        return new OwnResponseReplay(new MovementVector(accumulated.x(),ceilingClippedOwnY,accumulated.z()), floorClippedOwnY,
                List.copyOf(projections));
    }

    private static MovementVector v(double x,double y,double z) { return new MovementVector(x,y,z); }
    private static boolean validOrder(CurrentFrameCollisionScope.NativeCollisionTrace row,int n,int step,int iteration) {
        return n>0&&n<=10&&row.substeps()==n&&row.substep()>=1&&row.substep()<=n&&row.iteration()>=0&&row.iteration()<=3
                &&row.substep()>=step&&(row.substep()!=step||row.iteration()>iteration)
                &&(row.substep()==step||row.iteration()==0);
    }
    private static MovementVector replayGeometry(CurrentFrameCollisionScope.NativeCollisionTrace row,MovementVector selected,
            MovementVector applied,MovementVector response,NativeSideCollisionWitness witness) {
        NativeCollisionGeometry g=row.geometry();
        if(g==null||g.substeps()!=row.substeps()||g.iteration()!=row.iteration()||!near(g.maxMtv(),selected)
                ||!near(g.collisionMotion(),v(row.collisionMotionBeforeX(),row.collisionMotionBeforeY(),row.collisionMotionBeforeZ()))
                ||!near(applied,selected)||!near(response,selected))return null;
        MovementVector replay=witness.separation(g);
        return replay!=null&&near(replay,selected)?replay:null;
    }
    private static boolean vecMatches(Vec3 value,MovementVector expected) {
        return nearAxis(value.x,expected.x())&&nearAxis(value.y,expected.y())&&nearAxis(value.z,expected.z());
    }
    private static boolean vecExactlyMatches(Vec3 left, Vec3 right) {
        return Double.compare(left.x, right.x) == 0 && Double.compare(left.y, right.y) == 0
                && Double.compare(left.z, right.z) == 0;
    }
    private static boolean nearAxis(double actual,double expected) {
        return Double.isFinite(actual)&&Double.isFinite(expected)
                &&Math.abs(actual-expected)<=EPS+Math.ulp(actual)+Math.ulp(expected);
    }
    private static double length(MovementVector v) { return Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z()); }
    private static boolean near(MovementVector a, MovementVector b) {
        return Math.hypot(Math.hypot(a.x()-b.x(), a.y()-b.y()), a.z()-b.z()) <= EPS;
    }
    private static boolean sameXZ(MovementVector a, MovementVector b) { return a.x()==b.x() && a.z()==b.z(); }
}
