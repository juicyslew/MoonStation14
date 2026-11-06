package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import java.util.UUID;

/** Raw committed-session/body identity only; deliberately does not consult tracker or medical eligibility. */
final class OwnedSimulationTimelineIdentity {
    private OwnedSimulationTimelineIdentity() { }

    static boolean matches(boolean committed, long activeEpoch, long pendingEpoch, MobHarnessKind kind,
            int boundEntityId, UUID boundBodyUuid, Object boundLevel,
            int candidateEntityId, UUID candidateBodyUuid, Object candidateLevel) {
        return committed && activeEpoch > 0 && activeEpoch == pendingEpoch && kind == MobHarnessKind.CHARACTER
                && boundEntityId >= 0 && boundBodyUuid != null && boundLevel != null
                && candidateEntityId == boundEntityId && boundBodyUuid.equals(candidateBodyUuid)
                && candidateLevel == boundLevel;
    }
}
