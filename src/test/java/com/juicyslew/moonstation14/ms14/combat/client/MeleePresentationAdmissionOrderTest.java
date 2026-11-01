package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeleePresentationAdmissionOrderTest {
    @Test void jugReadinessUsesResolvedWeaponKindForStackRequirement() {
        assertTrue(MeleeStrikePresentationClientDiagnostic.jugSelectionReady(com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED, false, true));
        assertFalse(MeleeStrikePresentationClientDiagnostic.jugSelectionReady(com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD, false, true));
        assertFalse(MeleeStrikePresentationClientDiagnostic.jugSelectionReady(com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.GLOVES, false, true));
        assertFalse(MeleeStrikePresentationClientDiagnostic.jugSelectionReady(com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED, false, false));
        assertFalse(MeleeStrikePresentationClientDiagnostic.jugSelectionReady(null, false, true));
    }
    @Test void preparesWindowAndMouseBeforeBlockedAdmission() {
        List<String> events = new ArrayList<>();
        boolean combatMode = false;
        boolean admitted = MeleeStrikePresentationClientDiagnostic.prepareBeforeAdmission(
                () -> events.add("prepare-screen-and-grab-mouse"),
                () -> {
                    events.add("combat-mode-guard");
                    if (!combatMode) {
                        events.add("request-toggle");
                        return false;
                    }
                    return true;
                });
        assertFalse(admitted);
        assertEquals(List.of("prepare-screen-and-grab-mouse", "combat-mode-guard", "request-toggle"), events);
    }
}
