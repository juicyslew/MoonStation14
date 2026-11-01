package com.juicyslew.moonstation14.ms14.combat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeSecondaryGestureTest {
    @Test void selectsBySourceAndRequiresExplicitUnarmedDisarmPermission() {
        assertEquals(MeleeAttackRequest.Mode.DISARM, MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.UNARMED, true, true));
        assertEquals(MeleeAttackRequest.Mode.WIDE, MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.UNARMED, false, true));
        assertEquals(MeleeAttackRequest.Mode.WIDE, MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.HELD, true, true));
        assertEquals(MeleeAttackRequest.Mode.WIDE, MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.GLOVES, true, true));
        assertNull(MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.UNARMED, true, null));
        assertNull(MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.UNARMED, true, false));
    }

    @Test void allowsDisarmOnlyForAltDisarmEnabledUnarmedSelectionWithPermission() {
        MeleeWeaponResolver.Selection unarmed = selection(MeleeWeaponResolver.Kind.UNARMED, true, Optional.empty());
        assertTrue(MeleeSecondaryGesture.allowsDisarm(unarmed, true));
        assertFalse(MeleeSecondaryGesture.allowsDisarm(unarmed, false));
        assertFalse(MeleeSecondaryGesture.allowsDisarm(unarmed, null));

        assertFalse(MeleeSecondaryGesture.allowsDisarm(
                selection(MeleeWeaponResolver.Kind.UNARMED, false, Optional.empty()), true));
        assertEquals(MeleeAttackRequest.Mode.WIDE,
                MeleeSecondaryGesture.mode(MeleeWeaponResolver.Kind.UNARMED, false, true));
        assertFalse(MeleeSecondaryGesture.allowsDisarm(
                selection(MeleeWeaponResolver.Kind.HELD, true, Optional.of("held-item")), true));
        assertFalse(MeleeSecondaryGesture.allowsDisarm(
                selection(MeleeWeaponResolver.Kind.GLOVES, true, Optional.of("gloves")), true));
        assertFalse(MeleeSecondaryGesture.allowsDisarm(null, true));
    }

    @Test void inactiveHeldItemTokenDoesNotAffectUnarmedDisarmEligibility() {
        MeleeWeaponResolver.Selection unarmedWithInactiveToken =
                selection(MeleeWeaponResolver.Kind.UNARMED, true, Optional.of("inactive-held-item"));
        assertTrue(MeleeSecondaryGesture.allowsDisarm(unarmedWithInactiveToken, true));
    }

    private static MeleeWeaponResolver.Selection selection(MeleeWeaponResolver.Kind kind, boolean altDisarm,
                                                            Optional<String> itemToken) {
        MeleeWeaponData weapon = new MeleeWeaponData(Map.of("blunt", 1f), 1f, 1f, false, false, 1f, false);
        weapon = new MeleeWeaponData(weapon.damage(), weapon.attackRate(), weapon.range(), weapon.resistanceBypass(),
                weapon.autoAttack(), weapon.clickDamageModifier(), weapon.mustBeEquippedToUse(), weapon.audio(),
                weapon.arc(), weapon.animationOffset(), weapon.wideAnimationRotation(), weapon.bluntStaminaDamageFactor(),
                weapon.resetOnHandSelected(), weapon.angleDegrees(), weapon.wideArc(), altDisarm);
        return new MeleeWeaponResolver.Selection(kind, new MeleeWeaponProfile(weapon, Map.of(), Optional.empty()),
                UUID.randomUUID(), 0L, "mainhand", itemToken);
    }
}
