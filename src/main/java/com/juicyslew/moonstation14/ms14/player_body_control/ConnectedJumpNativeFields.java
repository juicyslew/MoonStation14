package com.juicyslew.moonstation14.ms14.player_body_control;

import com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion;

/** Compact full-precision native result fields shared by server and client capture adapters. */
public final class ConnectedJumpNativeFields {
    private ConnectedJumpNativeFields() { }
    public static String describe(SableOwnedBodyMotion.MoveResult move) {
        if (move == null) return "native=unavailable";
        var info=move.collisionInfo(); var motion=info==null?null:info.motion;
        var inherited=info==null?null:info.inheritedMotion;
        return "requested="+v(move.requestedOwnDisplacement().x(),move.requestedOwnDisplacement().y(),move.requestedOwnDisplacement().z())
                +";measured="+v(move.measuredOwnDisplacement().x(),move.measuredOwnDisplacement().y(),move.measuredOwnDisplacement().z())
                +";bounded="+v(move.boundedOwnDisplacement().x(),move.boundedOwnDisplacement().y(),move.boundedOwnDisplacement().z())
                +";rawMotion="+(motion==null?"unavailable":v(motion.x,motion.y,motion.z))
                +";inherited="+(inherited==null?"unavailable":v(inherited.x,inherited.y,inherited.z))
                +";frameDeltaPerSecond="+v(move.frameVelocityDeltaPerSecond().x(),move.frameVelocityDeltaPerSecond().y(),move.frameVelocityDeltaPerSecond().z())
                +";fresh="+move.collisionInfoFresh()+";vertical="+(info!=null&&info.verticalCollision)
                +";verticalBelow="+(info!=null&&info.verticalCollisionBelow)+";horizontal="+(info!=null&&info.horizontalCollision)
                +";sublevelHorizontal="+(info!=null&&info.subLevelHorizontalCollision)
                +";idleProbeAttested="+move.idleSupportProbeAttested()+";idleProbeCertified="+move.idleSupportProbeCertified()
                +";geometryCorrection="+(move.positionCorrection()==null?"absent":v(move.positionCorrection().geometryCorrection().x(),move.positionCorrection().geometryCorrection().y(),move.positionCorrection().geometryCorrection().z()))
                +";normalTarget="+(move.normalTarget()==null?"absent":"present")
                +";proof="+proof(move.nativeContactProofDiagnostic());
    }
    /** Reconstruct carrier only from one exact fresh native result with no idle-probe suppression. */
    public static String actualCarrier(SableOwnedBodyMotion.MoveResult move,int observedCount,
            double beforeX,double beforeY,double beforeZ,double afterX,double afterY,double afterZ) {
        if(move==null||observedCount!=1||!move.collisionInfoFresh()||move.idleSupportProbeAttested()
                ||move.idleSupportProbeCertified())return "unavailable";
        var suppression=move.idleProbeSuppressionDisplacement();
        if(suppression.x()!=0d||suppression.y()!=0d||suppression.z()!=0d)return "unavailable";
        var own=move.measuredOwnDisplacement();
        return v(afterX-beforeX-own.x(),afterY-beforeY-own.y(),afterZ-beforeZ-own.z());
    }
    private static String proof(com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic p) {
        if(p==null)return "absent";
        return p.clearanceObservation().token()+",branch="+p.branch()+",fresh="+p.fresh()+",trace="+p.traceComplete()+",geometry="+p.geometryComplete()
                +",xyz="+p.nativeXMatch()+"/"+p.nativeYMatch()+"/"+p.nativeZMatch()+",stage="+p.positionStage()
                +",reason="+p.reason()+",row="+p.rowIndex()+",grounding="+p.groundingDecision();
    }
    private static String v(double x,double y,double z) { return "("+Double.toString(x)+","+Double.toString(y)+","+Double.toString(z)+")"; }
}
