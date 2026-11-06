package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;

/** Immutable account of the native-contact proof branch evaluated during one owned move. */
public record NativeContactProofDiagnostic(Branch branch, boolean fresh, boolean trackedBody,
        boolean traceComplete, boolean geometryComplete, boolean nativeXMatch, boolean nativeYMatch,
        boolean nativeZMatch, boolean worldClipContext, boolean groundedFloorSide,
        boolean firstNativeContactMatch, NativeSideContactProof.Failure reason, int rowIndex,
        MovementVector request, MovementVector nativeMotion, MovementVector measured,
        MovementVector proofBoundedOwn, MovementVector proofGeometryCorrection, PositionStage positionStage,
        SableOwnedBodyMotion.GroundingDecision groundingDecision, ClearanceObservation clearanceObservation) {
    public enum Branch { NONE, IDLE_FLOOR, WORLD_FLOOR_SIDE, SUPPORTED_FLOOR_SIDE, PURE_SIDE, CEILING_AND_SIDE, CEILING_ONLY_POSITION, TRANSIENT_FLOOR_SIDE_AIRBORNE, ASCENDING_FLOOR_AIRBORNE, UNSUPPORTED_MIXED }
    public enum PositionStage { NOT_EVALUATED, ACCEPTED, REJECTED }
    public enum QueryMode { BODY_VOLUME, SUPPORT_BAND, NOT_EVALUATED }
    public enum Authorization { NOT_EVALUATED, AUTHORIZED, DENIED_IDENTITY, DENIED_PROOF, DENIED_CAPTURE, DENIED_FINAL }
    public record ClearanceObservation(QueryMode mode, NativeFinalFootClearance.Result captured,
            boolean endpointSame, NativeFinalFootClearance.Result finalResult, Authorization authorization,
            NativeFinalFootClearance.QueryObservation capturedDetail, NativeFinalFootClearance.QueryObservation finalDetail) {
        public ClearanceObservation(QueryMode mode, NativeFinalFootClearance.Result captured,
                boolean endpointSame, NativeFinalFootClearance.Result finalResult, Authorization authorization) {
            this(mode, captured, endpointSame, finalResult, authorization, null, null);
        }
        public ClearanceObservation { java.util.Objects.requireNonNull(mode); java.util.Objects.requireNonNull(authorization); }
        public static ClearanceObservation none() { return new ClearanceObservation(QueryMode.NOT_EVALUATED,null,false,null,Authorization.NOT_EVALUATED,null,null); }
        public String token() { return "clearance="+mode+":"+(captured==null?"NOT_EVALUATED":captured)+"/"
                +(endpointSame?"same":"changed")+":"+(finalResult==null?"NOT_EVALUATED":finalResult)+":"+authorization; }
    }
    public NativeContactProofDiagnostic {
        java.util.Objects.requireNonNull(branch);
        java.util.Objects.requireNonNull(reason);
        java.util.Objects.requireNonNull(positionStage);
        java.util.Objects.requireNonNull(groundingDecision);
        if (clearanceObservation == null) clearanceObservation = ClearanceObservation.none();
    }
    static NativeContactProofDiagnostic none() {
        return new NativeContactProofDiagnostic(Branch.NONE,false,false,false,false,false,false,false,
                false,false,false,NativeSideContactProof.Failure.NONE,-1,null,null,null,null,null,PositionStage.NOT_EVALUATED,
                SableOwnedBodyMotion.GroundingDecision.UNCHANGED,ClearanceObservation.none());
    }
}
