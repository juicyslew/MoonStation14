package com.juicyslew.moonstation14.ms14.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnockdownRenderPolicyTest {
    @Test
    void pronePoseRequiresActiveKnockdownAndNormalStandingRenderState() {
        assertTrue(policy(true, false, false));
        assertFalse(policy(false, false, false)); // stun-only, expired or absent status
        assertFalse(policy(true, true, false)); // death remains vanilla corpse pose
        assertFalse(policy(true, false, true)); // sleep retains vanilla sleeping pose
        assertFalse(excluded(true, false, false, true, false, false, false, false)); // passenger
        assertFalse(excluded(true, false, false, false, true, false, false, false)); // spectator
        assertFalse(excluded(true, false, false, false, false, true, false, false)); // flight
        assertFalse(excluded(true, false, false, false, false, false, true, false)); // swimming/water
        assertFalse(excluded(true, false, false, false, false, false, false, true)); // climb
        assertTrue(KnockdownRenderPolicy.isUpsideDownName("Dinnerbone"));
        assertTrue(KnockdownRenderPolicy.isUpsideDownName("Grumm"));
        assertFalse(KnockdownRenderPolicy.isUpsideDownName("ordinary name"));
    }

    private static boolean excluded(boolean down, boolean dead, boolean asleep, boolean passenger,
                                    boolean spectator, boolean flying, boolean swimming, boolean climbing) {
        return KnockdownRenderPolicy.shouldRenderProne(down, dead, asleep, passenger, spectator,
                flying, swimming, climbing);
    }

    private static boolean policy(boolean knockedDown, boolean dead, boolean sleeping) {
        return KnockdownRenderPolicy.shouldRenderProne(knockedDown, dead, sleeping,
                false, false, false, false, false);
    }
}
