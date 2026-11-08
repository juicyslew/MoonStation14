package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MeleeAttackPolicyTest {
    @Test void meleeBaseIsScaledOnceAndBothBonusesRemainUnscaled() {
        assertEquals(java.util.Map.of("slash", 10f, "blunt", 3f), MeleeDamageComposition.compose(
                java.util.Map.of("slash", 4f), 2f, java.util.Map.of("slash", 2f), java.util.Map.of("blunt", 3f)));
    }
    @Test void policyRejectsEachGateAndAllowsValidAttack() {
        assertEquals(MeleeAttackPolicy.Reason.NOT_COMBAT_MODE, MeleeAttackPolicy.evaluate(false, false, false, false, true, false, false));
        assertEquals(MeleeAttackPolicy.Reason.STUNNED, MeleeAttackPolicy.evaluate(true, true, false, false, true, false, false));
        assertEquals(MeleeAttackPolicy.Reason.CRITICAL, MeleeAttackPolicy.evaluate(true, false, true, false, true, false, false));
        assertEquals(MeleeAttackPolicy.Reason.DEAD, MeleeAttackPolicy.evaluate(true, false, false, true, true, false, false));
        assertEquals(MeleeAttackPolicy.Reason.COOLDOWN, MeleeAttackPolicy.evaluate(true, false, false, false, true, true, false));
        assertEquals(MeleeAttackPolicy.Reason.SOURCE_MISMATCH, MeleeAttackPolicy.evaluate(true, false, false, false, false, false, false));
        assertEquals(MeleeAttackPolicy.Reason.UNARMED_SELF_TARGET, MeleeAttackPolicy.evaluate(true, false, false, false, true, false, true));
        assertEquals(MeleeAttackPolicy.Reason.ALLOWED, MeleeAttackPolicy.evaluate(true, false, false, false, true, false, false));
    }

    @Test void policyUsesCurrentPrecedenceAndDoesNotGateOnProneOrSlip() {
        assertEquals(MeleeAttackPolicy.Reason.SOURCE_MISMATCH, MeleeAttackPolicy.evaluate(false, true, true, true, false, true, true));
        assertEquals(MeleeAttackPolicy.Reason.COOLDOWN, MeleeAttackPolicy.evaluate(true, true, true, true, true, true, true));
        assertEquals(MeleeAttackPolicy.Reason.NOT_COMBAT_MODE, MeleeAttackPolicy.evaluate(false, true, true, true, true, false, true));
        assertEquals(MeleeAttackPolicy.Reason.STUNNED, MeleeAttackPolicy.evaluate(true, true, true, true, true, false, true));
        assertEquals(MeleeAttackPolicy.Reason.CRITICAL, MeleeAttackPolicy.evaluate(true, false, true, true, true, false, true));
        assertEquals(MeleeAttackPolicy.Reason.DEAD, MeleeAttackPolicy.evaluate(true, false, false, true, true, false, true));
        assertEquals(MeleeAttackPolicy.Reason.UNARMED_SELF_TARGET, MeleeAttackPolicy.evaluate(true, false, false, false, true, false, true));
        assertEquals(MeleeAttackPolicy.Reason.ALLOWED, MeleeAttackPolicy.evaluate(true, false, false, false, true, false, false));
    }

    @Test void meleeHitEventCopiesWideTargetsAndMissIsEmpty() {
        var attacker = mock(LivingEntity.class);
        var target = mock(Entity.class);
        var second = mock(Entity.class);
        var third = mock(Entity.class);
        var selection = selection();
        var mutable = new java.util.ArrayList<Entity>(List.of(target, second, third));
        var hit = new MeleeAttackEvents.MeleeHitEvent(attacker, mutable, selection);
        mutable.clear();
        assertEquals(List.of(target, second, third), hit.targets());
        assertThrows(UnsupportedOperationException.class, () -> hit.targets().add(target));
        assertThrows(IllegalArgumentException.class, () -> new MeleeAttackEvents.MeleeHitEvent(attacker, java.util.Arrays.asList(target, null), selection));
        var miss = new MeleeAttackEvents.MeleeHitEvent(attacker, List.of(), selection);
        assertTrue(miss.targets().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> miss.targets().add(target));
        assertFalse(hit.handled());
        hit.setHandled(true);
        assertTrue(hit.handled());
        hit.setHandled(false);
        assertFalse(hit.handled());
    }

    @Test void preAttemptAndAttemptAreNeoForgeCancellableEvents() {
        var attacker = mock(LivingEntity.class);
        var target = Optional.<Entity>of(mock(Entity.class));
        var selection = selection();
        var pre = new MeleeAttackEvents.SharedMeleeAttackAttemptEvent(attacker, target, selection);
        var disarm = new MeleeAttackEvents.SharedMeleeAttackAttemptEvent(attacker, target, selection, true);
        assertFalse(pre.disarm());
        assertTrue(disarm.disarm());
        var attempt = new MeleeAttackEvents.AttemptMeleeEvent(attacker, target, selection);
        assertFalse(pre.isCanceled());
        assertFalse(attempt.isCanceled());
        pre.setCanceled(true);
        attempt.setCanceled(true);
        assertTrue(pre.isCanceled());
        assertTrue(attempt.isCanceled());
    }

    private static MeleeWeaponResolver.Selection selection() {
        var weapon = new MeleeWeaponData(Map.of("blunt", 1f), 1, 1f, false, false, 1, false);
        return new MeleeWeaponResolver.Selection(MeleeWeaponResolver.Kind.UNARMED, new MeleeWeaponProfile(weapon, java.util.Map.of(), Optional.empty()), UUID.randomUUID(), 0, "right", Optional.empty());
    }
}
