package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleePredictionAdmissionTest {
    @Test void sourceDeadlineUsesEstimatedServerTickAndEqualityIsReady() {
        assertFalse(MeleeAttackClientPolicy.sourceDeadlineReady(106, OptionalDouble.of(105.99)));
        assertTrue(MeleeAttackClientPolicy.sourceDeadlineReady(106, OptionalDouble.of(106)));
        assertFalse(MeleeAttackClientPolicy.sourceDeadlineReady(0, OptionalDouble.empty()));
        assertFalse(MeleeAttackClientPolicy.sourceDeadlineReady(0, null));
    }

    @Test void predictedCooldownBindsToSourceRatherThanHandRevision() {
        var policy = new MeleeAttackClientPolicy();
        var body = UUID.randomUUID();
        policy.bind(body, 1);
        var held = new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.HELD, 2, "main", "token-a");
        var req = policy.submit(held, 10, 20, null);
        assertNotNull(req);
        assertTrue(policy.acknowledge(new MeleeAttackReply(body, 1, req.sequence(), MeleeAttackReply.Status.HIT,
                true, MeleeWeaponResolver.Kind.HELD, 2, "token-a", 20), 10));
        assertNull(policy.submit(new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.HELD, 3, "main", "token-a"), 29, 20, null));
        assertNotNull(policy.submit(new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.HELD, 3, "main", "token-b"), 29, 20, null));
    }

    @Test void unarmedSourceIgnoresHandRevisionButPendingRequestStillBlocks() {
        var policy = new MeleeAttackClientPolicy();
        policy.bind(UUID.randomUUID(), 1);
        var first = new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED, 1, "main", "");
        assertNotNull(policy.submit(first, 0, 5, null));
        assertNull(policy.submit(new MeleeAttackClientPolicy.Binding(MeleeWeaponResolver.Kind.UNARMED, 2, "off", ""), 1, 5, null));
    }
}
