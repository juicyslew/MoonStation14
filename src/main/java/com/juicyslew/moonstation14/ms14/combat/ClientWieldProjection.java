package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

/** Read-only validation of wield evidence bound to the current confirmed hand snapshot. */
public final class ClientWieldProjection {
    private ClientWieldProjection() { }

    public static boolean confirms(BodyHandStateSnapshot snapshot, long epoch, String hand, String token) {
        if (snapshot == null || snapshot.reason() != BodyHandStateSnapshot.Reason.OK || snapshot.epoch() != epoch
                || snapshot.wieldProjection() == null) return false;
        var proof = snapshot.wieldProjection();
        var source = snapshot.hands().stream().filter(h -> h.id().equals(proof.sourceHand())).findFirst().orElse(null);
        return proof.sourceHand().equals(hand) && proof.sourceToken().equals(token)
                && source != null && token.equals(source.token());
    }
}
