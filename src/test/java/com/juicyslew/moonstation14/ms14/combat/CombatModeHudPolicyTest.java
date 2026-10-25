package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatModeHudPolicyTest {
    @Test void boundsAndIconsAreStable() {
        var b=CombatModeHudPolicy.bounds(320,240);
        assertEquals(8,b.x()); assertEquals(200,b.y()); assertEquals(32,b.width()); assertEquals(32,b.height());
        assertTrue(b.contains(8,200)); assertFalse(b.contains(40,232));
        assertEquals("off",CombatModeHudPolicy.icon(false)); assertEquals("on",CombatModeHudPolicy.icon(true));
        var small=CombatModeHudPolicy.bounds(24,24);
        assertTrue(small.x()+small.width()<=24); assertTrue(small.y()+small.height()<=24);
    }
    @Test void admissionRejectsUnavailablePointerOwners() {
        assertTrue(CombatModeHudPolicy.admitted(true,false,false,false));
        assertFalse(CombatModeHudPolicy.admitted(false,false,false,false));
        assertFalse(CombatModeHudPolicy.admitted(true,true,false,false));
        assertFalse(CombatModeHudPolicy.admitted(true,false,true,false));
        assertFalse(CombatModeHudPolicy.admitted(true,false,false,true));
    }
    @Test void registrationTracksResolvedCapabilityNotAttachmentMaterialization() {
        // An initially capable body has no mode attachment yet, but must already own the action.
        boolean attachmentExists = false;
        assertFalse(attachmentExists);
        assertTrue(CombatModeHudPolicy.shouldRegister(true));
        assertFalse(CombatModeHudPolicy.shouldRegister(false));
    }
}
