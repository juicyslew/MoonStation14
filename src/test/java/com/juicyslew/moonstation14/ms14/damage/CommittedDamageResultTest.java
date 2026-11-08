package com.juicyslew.moonstation14.ms14.damage;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommittedDamageResultTest {
    @Test void resultDefensivelyCopiesAndExposesImmutableCommittedMap() {
        var source = new HashMap<>(Map.of(DamageKeys.BLUNT, 2f));
        var result = new DamageSystem.ChangeResult(DamageSystem.Result.APPLIED, source);
        source.put(DamageKeys.BLUNT, 4f);
        assertEquals(Map.of(DamageKeys.BLUNT, 2f), result.committedPositive());
        assertThrows(UnsupportedOperationException.class,
                () -> result.committedPositive().put(DamageKeys.BLUNT, 3f));
    }

    @Test void appliedWithoutCommittedPositiveDamageIsEmptyAndOnlyCanonicalNonnegativeMapsAllowed() {
        assertEquals(Map.of(), new DamageSystem.ChangeResult(DamageSystem.Result.APPLIED, Map.of()).committedPositive());
        assertThrows(IllegalArgumentException.class,
                () -> new DamageSystem.ChangeResult(DamageSystem.Result.APPLIED, Map.of("not-canonical", 1f)));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageSystem.ChangeResult(DamageSystem.Result.APPLIED, Map.of(DamageKeys.BLUNT, -1f)));
    }
}
