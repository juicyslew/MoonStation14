package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransitInterpolationPolicyTest {
    private static final UUID BODY = new UUID(0, 1);
    private static final UUID HOLDER = new UUID(0, 2);
    private static final long EPOCH = 7;

    @Test void onlyConsecutiveSamplesForSameTransitOwnerInterpolate() {
        var policy = new TransitInterpolationPolicy();
        assertEquals(0, policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER), "first transit sample snaps");
        assertEquals(2, policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER));
    }

    @Test void unitAndMissingViewSamplesSnapAndResetTransitContext() {
        var policy = new TransitInterpolationPolicy();
        policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER);
        assertEquals(0, policy.accept(EPOCH, BODY, mode("UNIT"), HOLDER));
        assertEquals(0, policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER));
        assertEquals(0, policy.accept(EPOCH, BODY, mode("TRANSIT"), null));
        assertEquals(0, policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER));
    }

    @Test void holderBodyAndEpochChangesSnapThenEstablishNewContext() {
        var policy = new TransitInterpolationPolicy();
        policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER);
        assertEquals(0, policy.accept(EPOCH, BODY, mode("TRANSIT"), new UUID(0, 3)));
        assertEquals(2, policy.accept(EPOCH, BODY, mode("TRANSIT"), new UUID(0, 3)));
        assertEquals(0, policy.accept(EPOCH, new UUID(0, 4), mode("TRANSIT"), HOLDER));
        assertEquals(0, policy.accept(EPOCH + 1, new UUID(0, 4), mode("TRANSIT"), HOLDER));
    }

    @Test void groundedExitAndExplicitClearDiscardOldTransitContext() {
        var policy = new TransitInterpolationPolicy();
        policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER);
        assertEquals(0, policy.accept(EPOCH, BODY, mode("GROUNDED"), null));
        assertEquals(0, policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER));
        policy.clear();
        assertEquals(0, policy.accept(EPOCH, BODY, mode("TRANSIT"), HOLDER));
    }

    private static GhostControlPayloads.Snapshot.MovementMode mode(String name) {
        return GhostControlPayloads.Snapshot.MovementMode.valueOf(name);
    }
}
