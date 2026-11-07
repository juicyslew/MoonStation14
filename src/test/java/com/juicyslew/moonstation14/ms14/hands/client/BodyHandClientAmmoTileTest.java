package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyHandClientAmmoTileTest {
    @Test void ammoTileFollowsSourceHandIdentityInEitherHandOrdering() {
        var a = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), UUID.randomUUID(),
                1, 1, "left", List.of(new BodyHandStateSnapshot.Hand("left", "a", "x", 1),
                new BodyHandStateSnapshot.Hand("right", "b", "y", 1)));
        assertEquals(0, BodyHandClient.ammoCounterHandIndex(a, "left"));
        assertEquals(1, BodyHandClient.ammoCounterHandIndex(a, "right"));
        var reversed = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, a.accountId(), a.bodyId(),
                1, 1, "right", List.of(new BodyHandStateSnapshot.Hand("right", "b", "y", 1),
                new BodyHandStateSnapshot.Hand("left", "a", "x", 1)));
        assertEquals(1, BodyHandClient.ammoCounterHandIndex(reversed, "left"));
        assertEquals(0, BodyHandClient.ammoCounterHandIndex(reversed, "right"));
    }
}
