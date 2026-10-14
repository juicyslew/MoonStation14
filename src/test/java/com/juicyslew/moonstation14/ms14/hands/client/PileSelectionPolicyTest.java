package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PileSelectionPolicyTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test void selectionTracksAimRankingAndRetainsCycleUntilRankingChanges() {
        PileSelectionPolicy policy = new PileSelectionPolicy();
        assertEquals(A, policy.update(List.of(A, B)));
        assertEquals(B, policy.cycle(List.of(A, B)));
        assertEquals(B, policy.update(List.of(A, B)));
        assertEquals(C, policy.update(List.of(C, B, A)));
        assertEquals(0, policy.index(List.of(C, B, A)));
        assertEquals(C, policy.update(List.of(C, A)));
        policy.clear();
        assertEquals(A, policy.update(List.of(A, B)));
        assertNull(policy.update(List.of()));
        assertNull(policy.cycle(null));
        assertEquals(A, policy.update(List.of(A, B)));
    }

    @Test void cyclingWrapsAndStartsAtFirstWhenSelectionIsAbsent() {
        PileSelectionPolicy policy = new PileSelectionPolicy();
        assertEquals(A, policy.cycle(List.of(A, B, C)));
        assertEquals(B, policy.cycle(List.of(A, B, C)));
        assertEquals(C, policy.cycle(List.of(A, B, C)));
        assertEquals(A, policy.cycle(List.of(A, B, C)));
    }
}
