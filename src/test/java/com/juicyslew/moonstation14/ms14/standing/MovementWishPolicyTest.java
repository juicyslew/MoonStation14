package com.juicyslew.moonstation14.ms14.standing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementWishPolicyTest {
    @Test void firstWishAndDirectionChangesNotifyImmediatelyThenUseKeepalive() {
        assertTrue(MovementWishPolicy.shouldNotify(1, 0, 100, 99, 5));
        assertTrue(MovementWishPolicy.shouldNotify(2, 1, 101, 100, 5));
        assertFalse(MovementWishPolicy.shouldNotify(2, 2, 104, 100, 5));
        assertTrue(MovementWishPolicy.shouldNotify(2, 2, 105, 100, 5));
        assertTrue(MovementWishPolicy.shouldNotify(0, 2, 106, 100, 5));
        assertTrue(MovementWishPolicy.shouldNotify(2, 0, 107, 100, 5));
    }
}
