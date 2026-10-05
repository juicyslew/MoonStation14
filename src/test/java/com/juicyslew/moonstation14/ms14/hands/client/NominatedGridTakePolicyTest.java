package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NominatedGridTakePolicyTest {
    private static BodyHandStateSnapshot.Hand hand(String id, String token) {
        return new BodyHandStateSnapshot.Hand(id, token, token == null ? null : "minecraft:stone", token == null ? 0 : 1);
    }

    @Test void nominatedTakeUsesExactEmptyDestinationAndChildProof() {
        var child = new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 1, 0, 0, 0);
        assertTrue(BodyHandClientPolicy.nominatedGridTakeEligible(hand("inactive", null), child, child, null));
        assertFalse(BodyHandClientPolicy.nominatedGridTakeEligible(hand("occupied", "held"), child, child, null));
        assertFalse(BodyHandClientPolicy.nominatedGridTakeEligible(hand("inactive", null), child,
                new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 1, 1, 0, 0), null));
        assertFalse(BodyHandClientPolicy.nominatedGridTakeEligible(hand("host", null), child, child, "host"));
    }
}
