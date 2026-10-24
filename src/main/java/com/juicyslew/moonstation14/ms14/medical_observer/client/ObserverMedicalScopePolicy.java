package com.juicyslew.moonstation14.ms14.medical_observer.client;

import com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads;

/** Keeps cached medical observer snapshots advisory to, and scoped by, the canonical controlled actor. */
public final class ObserverMedicalScopePolicy {
    private ObserverMedicalScopePolicy() { }

    public record ReturnCredentialState(ObserverPayloads.Snapshot credential, long epoch,
                                        java.util.UUID ghostId) { }

    public static boolean expose(boolean canonicalGhost, boolean characterControl,
                                 boolean snapshotMatchesOwnedCharacter) {
        if (canonicalGhost) return false;
        return !characterControl || snapshotMatchesOwnedCharacter;
    }

    /** Reconcile observer return metadata without allowing stale or unrelated snapshots to revoke it. */
    public static ReturnCredentialState reconcileReturnCredential(boolean canonicalGhost,
            ReturnCredentialState current, ObserverPayloads.Snapshot incoming,
            java.util.UUID canonicalGhostId) {
        if (!canonicalGhost || incoming == null || canonicalGhostId == null) return current;
        if (current == null) current = new ReturnCredentialState(null, 0, canonicalGhostId);
        if (!canonicalGhostId.equals(current.ghostId())) current = new ReturnCredentialState(null, 0, canonicalGhostId);
        if (incoming.epoch() < current.epoch()) return current;
        if (incoming.mode() == ObserverPayloads.Mode.END) {
            if (current.credential() != null && incoming.epoch() == current.credential().epoch()
                    && incoming.body().equals(current.credential().body()))
                return new ReturnCredentialState(null, current.epoch(), current.ghostId());
            return current;
        }
        if (incoming.mode() != ObserverPayloads.Mode.GHOST || !canonicalGhostId.equals(incoming.ghost())) return current;
        return new ReturnCredentialState(incoming.returnAvailable() ? incoming : null,
                incoming.epoch(), canonicalGhostId);
    }

    public static boolean consumeReturn(boolean canonicalGhost, ObserverPayloads.Snapshot credential) {
        return canonicalGhost && credential != null && credential.mode() == ObserverPayloads.Mode.GHOST
                && credential.returnAvailable();
    }

    /** Builds the production return request only from the credential for this canonical ghost. */
    public static ObserverPayloads.Request returnRequest(boolean canonicalGhost, ReturnCredentialState state,
                                                          java.util.UUID canonicalGhostId) {
        if (state == null || canonicalGhostId == null || !canonicalGhostId.equals(state.ghostId())
                || !consumeReturn(canonicalGhost, state.credential())) return null;
        return new ObserverPayloads.Request(ObserverPayloads.Request.Kind.RETURN, state.credential().epoch());
    }
}
