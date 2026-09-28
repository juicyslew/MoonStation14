package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleProfileCodecTest {
    private static final UUID ACCOUNT = id(1), MIND = id(2), BODY = id(3), EPOCH = id(4);

    @Test void roundTripsImmutableVersionedRecordAndHonestGhostState() {
        var profile = profile(SavedLifecycleProfile.State.GHOST, null);
        assertEquals(profile, LifecycleProfileCodec.decode(LifecycleProfileCodec.encode(profile)));
        assertThrows(UnsupportedOperationException.class, () -> profile.appearance().put("hair", "blue"));
        var offlineGhost = new SavedLifecycleProfile(1, ACCOUNT, "main", MIND, BODY, "minecraft:overworld",
                new SavedLifecycleProfile.Location(1, 2, 3), Map.of(), EPOCH, 8,
                SavedLifecycleProfile.State.GHOST_OFFLINE, 2, 123L);
        assertEquals(offlineGhost, LifecycleProfileCodec.decode(LifecycleProfileCodec.encode(offlineGhost)));
    }

    @Test void rejectsUnsupportedInvalidDuplicateAndTruncatedInput() {
        String encoded = LifecycleProfileCodec.encode(profile(SavedLifecycleProfile.State.ACTIVE, null));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decode(encoded.replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decode(encoded.substring(0, encoded.length() - 4)));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decode(encoded.replace("\"profileKey\":\"main\"", "\"profileKey\":\"\"")));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decode(encoded.replace("\"profileKey\":\"main\"", "\"profileKey\":\"main\",\"profileKey\":\"other\"")));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decode(encoded.replace("\"revision\":1", "\"revision\":1,\"surprise\":true")));
        assertThrows(IllegalArgumentException.class, () -> new SavedLifecycleProfile(1, ACCOUNT, "main", MIND, BODY,
                "dimension", new SavedLifecycleProfile.Location(Double.NaN, 0, 0), Map.of(), EPOCH, 0,
                SavedLifecycleProfile.State.ACTIVE, 0, null));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decode(
                encoded.replace("\"state\":\"ACTIVE\"", "\"state\":\"FUTURE_STATE\"")));
    }

    @Test void batchRejectsAnyDuplicateOwnershipAxis() {
        String first = LifecycleProfileCodec.encode(profile(SavedLifecycleProfile.State.ACTIVE, null));
        var second = new SavedLifecycleProfile(1, id(5), "main", id(6), id(7), "dimension",
                new SavedLifecycleProfile.Location(0, 0, 0), Map.of(), EPOCH, 1,
                SavedLifecycleProfile.State.ACTIVE, 1, null);
        assertEquals(2, LifecycleProfileCodec.decodeBatch("[" + first + "," + LifecycleProfileCodec.encode(second) + "]").size());
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decodeBatch("[" + first + "," + first + "]"));
        var duplicateMind = new SavedLifecycleProfile(1, id(5), "main", MIND, id(8), "dimension",
                new SavedLifecycleProfile.Location(0, 0, 0), Map.of(), EPOCH, 1,
                SavedLifecycleProfile.State.ACTIVE, 1, null);
        var duplicateBody = new SavedLifecycleProfile(1, id(5), "main", id(9), BODY, "dimension",
                new SavedLifecycleProfile.Location(0, 0, 0), Map.of(), EPOCH, 1,
                SavedLifecycleProfile.State.ACTIVE, 1, null);
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decodeBatch("[" + first + "," + LifecycleProfileCodec.encode(duplicateMind) + "]"));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decodeBatch("[" + first + "," + LifecycleProfileCodec.encode(duplicateBody) + "]"));
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decodeBatch("[" + first));
        var sameAccountDifferentOwnership = new SavedLifecycleProfile(1, ACCOUNT, "alternate", id(8), id(9), "dimension",
                new SavedLifecycleProfile.Location(0, 0, 0), Map.of(), EPOCH, 1,
                SavedLifecycleProfile.State.ACTIVE, 1, null);
        assertThrows(IllegalArgumentException.class, () -> LifecycleProfileCodec.decodeBatch(
                "[" + first + "," + LifecycleProfileCodec.encode(sameAccountDifferentOwnership) + "]"));
    }

    @Test void preparingEnrollmentIsStrictVersionedAndNotOffline() {
        var preparing = profile(SavedLifecycleProfile.State.PREPARING, null);
        assertEquals(preparing, LifecycleProfileCodec.decode(LifecycleProfileCodec.encode(preparing)));
        assertThrows(IllegalArgumentException.class, () -> profile(SavedLifecycleProfile.State.PREPARING, 0L));
    }

    @Test void deadClaimSurvivesSchemaRoundTripWithOfflineTimestamp() {
        var claim = profile(SavedLifecycleProfile.State.DEAD_CLAIM, 1234L);
        assertEquals(claim, LifecycleProfileCodec.decode(LifecycleProfileCodec.encode(claim)));
        assertEquals(claim, LifecycleProfileCodec.decodeStore(LifecycleProfileCodec.encodeStore(
                new LifecycleStoreEnvelope(1, 7, EPOCH, 9, List.of(claim)))).profiles().get(0));
    }

    @Test void bodyReconciliationNeverFallsBackForUncertainKnownBody() {
        assertEquals(BodyReconciliation.Decision.SAME_BODY_AVAILABLE,
                BodyReconciliation.decide(BodyReconciliation.Observation.LOADED_ALIVE_MATCH));
        assertEquals(BodyReconciliation.Decision.DEFER_KNOWN_BODY,
                BodyReconciliation.decide(BodyReconciliation.Observation.UNLOADED));
        for (var observation : List.of(BodyReconciliation.Observation.MISSING, BodyReconciliation.Observation.AMBIGUOUS,
                BodyReconciliation.Observation.LOADED_DEAD, BodyReconciliation.Observation.OWNER_MISMATCH))
            assertEquals(BodyReconciliation.Decision.RECOVERY_REQUIRED, BodyReconciliation.decide(observation));
    }

    private static SavedLifecycleProfile profile(SavedLifecycleProfile.State state, Long offline) {
        return new SavedLifecycleProfile(1, ACCOUNT, "main", MIND, BODY, "minecraft:overworld",
                new SavedLifecycleProfile.Location(1.25, 64, -8), Map.of("skin", "default"), EPOCH,
                7, state, 1, offline);
    }
    private static UUID id(long value) { return new UUID(0, value); }
}
