package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineResponse;
import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;

import java.util.UUID;

/** Exact-request and actor-epoch response correlation shared by the live callback and tests. */
public final class ExamineResponseStamp {
    private ExamineResponseStamp() { }
    public static boolean matches(ExamineResponse response, long requestId, ExamineTarget pendingTarget,
                                  ExamineTarget selectedTarget, UUID actor, long epoch,
                                  UUID expectedControllerIncarnation, boolean allowInitialIncarnation) {
        return correlates(response, requestId, pendingTarget, selectedTarget, actor, epoch)
                && (expectedControllerIncarnation != null
                ? expectedControllerIncarnation.equals(response.controllerIncarnation())
                : allowInitialIncarnation ? response.controllerIncarnation() != null
                : response.controllerIncarnation() == null);
    }
    public static boolean correlates(ExamineResponse response, long requestId, ExamineTarget pendingTarget,
                                     ExamineTarget selectedTarget, UUID actor, long epoch) {
        return response != null && response.requestId() == requestId && pendingTarget != null
                && pendingTarget.equals(response.target()) && pendingTarget.equals(selectedTarget)
                && actor != null && actor.equals(response.actorUuid())
                && epoch == response.epoch();
    }

    /** Denials expose only their safe header; accepted results always require normal incarnation matching. */
    public static boolean acceptsBoundary(ExamineResponse response, long requestId, ExamineTarget pendingTarget,
                                           ExamineTarget selectedTarget, UUID actor, long epoch,
                                           UUID expectedControllerIncarnation, boolean allowInitialIncarnation) {
        return response != null && (response.accepted()
                ? matches(response, requestId, pendingTarget, selectedTarget, actor, epoch,
                        expectedControllerIncarnation, allowInitialIncarnation)
                : correlates(response, requestId, pendingTarget, selectedTarget, actor, epoch));
    }
}
