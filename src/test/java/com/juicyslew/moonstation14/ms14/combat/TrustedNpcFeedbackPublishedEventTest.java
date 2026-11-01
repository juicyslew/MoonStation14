package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrustedNpcFeedbackPublishedEventTest {
    @Test void receiptsAllowNoVictimButRequireAttackerAndPreserveActualHitVictim() {
        var attacker = org.mockito.Mockito.mock(Mob.class);
        var hit = org.mockito.Mockito.mock(net.minecraft.world.entity.LivingEntity.class);
        var miss = new MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent(attacker, null, null, null);
        assertSame(attacker, miss.attacker());
        assertNull(miss.victim());
        var receipt = new MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent(attacker, hit, null, null);
        assertSame(hit, receipt.victim());
        assertThrows(NullPointerException.class, () -> new MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent(null, null, null, null));
    }
}
