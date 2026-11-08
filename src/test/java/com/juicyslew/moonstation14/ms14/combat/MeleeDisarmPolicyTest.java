package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeDisarmPolicyTest {
    @Test void markerOverridesHaveSourceParityAndNoOrdinaryProneInput() {
        assertEquals(1f, DisarmPolicy.failureChance(.75f, .2f, true, true));
        assertEquals(0f, DisarmPolicy.failureChance(.75f, .2f, false, true));
        assertEquals(.95f, DisarmPolicy.failureChance(.75f, .2f, false, false));
    }
    @Test void pushUsesFailureComplementAndResolvedThreshold() {
        assertEquals(40f, DisarmPolicy.pushDamage(.6f, 100f), 0.0001f);
    }
    @Test void forcedThrowOutcomesChooseExactlyOneConsumer() {
        assertFalse(DisarmPolicy.shouldFallbackToStamina(BodyLooseThrow.Result.SUCCESS));
        assertTrue(DisarmPolicy.shouldTerminate(BodyLooseThrow.Result.SUCCESS));
        assertTrue(DisarmPolicy.shouldFallbackToStamina(BodyLooseThrow.Result.DENIED));
        assertFalse(DisarmPolicy.shouldTerminate(BodyLooseThrow.Result.DENIED));
        assertFalse(DisarmPolicy.shouldFallbackToStamina(BodyLooseThrow.Result.RECOVERY_REQUIRED));
        assertTrue(DisarmPolicy.shouldTerminate(BodyLooseThrow.Result.RECOVERY_REQUIRED));
    }
    @Test void staminaSkipUsesStaminaCriticalStateNotMobState() {
        assertTrue(DisarmPolicy.skipStaminaWhenCritical(true));
        assertFalse(DisarmPolicy.skipStaminaWhenCritical(false));
    }
    @Test void heldDescriptorStackIsDefensivelyCopied() {
        var input = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var owned = new MeleeAttackEvents.OwnedItem("main", "token", 7, input);
        input.shrink(1);
        var read = owned.stack();
        read.shrink(1);
        assertEquals(1, owned.stack().getCount());
    }
}
