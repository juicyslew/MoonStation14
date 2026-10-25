package com.juicyslew.moonstation14.ms14.combat;

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
}
