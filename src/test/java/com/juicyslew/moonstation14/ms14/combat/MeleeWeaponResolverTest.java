package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MeleeWeaponResolverTest {
    private static final UUID BODY = UUID.randomUUID();
    private static final MeleeWeaponData NORMAL = new MeleeWeaponData(Map.of("blunt", 5f), 1, 1.5f, false, false, 1, false);
    private static final MeleeWeaponData EQUIPPED = new MeleeWeaponData(Map.of("blunt", 5f), 1, 1.5f, false, false, 1, true);
    private static Optional<MeleeWeaponResolver.Selection> choose(boolean occupied, Optional<MeleeWeaponData> heldData,
            Optional<MeleeWeaponData> gloves, Optional<MeleeWeaponData> unarmed) {
        Optional<MeleeWeaponProfile> held = heldData.map(d -> new MeleeWeaponProfile(d, Map.of(), Optional.empty()));
        Optional<MeleeWeaponProfile> gloveProfile = gloves.map(d -> new MeleeWeaponProfile(d, Map.of(), Optional.empty()));
        return MeleeWeaponResolver.choose(BODY, 4, "right", occupied,
                occupied ? Optional.of("held") : Optional.empty(), Optional.of("gloves"), held, gloveProfile, unarmed);
    }
    @Test void heldUsableWinsAndOccupiedNonweaponBlocksFallback() {
        assertEquals(MeleeWeaponResolver.Kind.HELD, choose(true, Optional.of(NORMAL), Optional.of(NORMAL), Optional.of(NORMAL)).orElseThrow().kind());
        assertTrue(choose(true, Optional.empty(), Optional.of(NORMAL), Optional.of(NORMAL)).isEmpty());
        assertTrue(MeleeWeaponResolver.choose(BODY, 4, "right", true, Optional.empty(), Optional.of("gloves"),
                Optional.empty(), Optional.of(new MeleeWeaponProfile(NORMAL, Map.of(), Optional.empty())), Optional.of(NORMAL)).isEmpty());
    }
    @Test void mustBeEquippedHeldBlocksFallback() {
        assertTrue(choose(true, Optional.of(EQUIPPED), Optional.of(NORMAL), Optional.of(NORMAL)).isEmpty());
    }
    @Test void glovesThenActorUnarmedThenNoSource() {
        var gloveSelection = choose(false, Optional.empty(), Optional.of(NORMAL), Optional.of(EQUIPPED)).orElseThrow();
        assertEquals(MeleeWeaponResolver.Kind.GLOVES, gloveSelection.kind());
        assertEquals(Optional.of("gloves"), gloveSelection.itemToken());
        assertEquals(MeleeWeaponResolver.Kind.UNARMED, choose(false, Optional.empty(), Optional.empty(), Optional.of(EQUIPPED)).orElseThrow().kind());
        assertTrue(choose(false, Optional.empty(), Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test void bindingRejectsChangedRevisionOrToken() {
        var selected = choose(false, Optional.empty(), Optional.of(NORMAL), Optional.of(EQUIPPED)).orElseThrow();
        var revision = new MeleeWeaponResolver.Selection(selected.kind(), selected.profile(), BODY, 5, "right", selected.itemToken());
        var token = new MeleeWeaponResolver.Selection(selected.kind(), selected.profile(), BODY, 4, "right", Optional.of("other"));
        assertFalse(MeleeWeaponResolver.sameBinding(selected, revision));
        assertFalse(MeleeWeaponResolver.sameBinding(selected, token));
        assertTrue(MeleeWeaponResolver.sameBinding(selected, selected));
    }

    @Test void serverChoiceRetainsWholeResolvedProfile() {
        var profile = new MeleeWeaponProfile(NORMAL, Map.of("blunt", 5f),
                Optional.of(new MeleeWeaponProfile.MeleeChemicalInjectorData("food", 500, false)));
        var selected = MeleeWeaponResolver.choose(BODY, 4, "right", true, Optional.of("held"), Optional.empty(),
                Optional.of(profile), Optional.empty(), Optional.empty()).orElseThrow();
        assertSame(profile, selected.profile()); assertSame(profile.weapon(), selected.weapon());
    }

    @Test void ownedStackAdmissionRejectsEitherOneSidedGloveOwnershipState() {
        assertFalse(MeleeWeaponResolver.coherentOwnedStack(true, Optional.empty()));
        assertFalse(MeleeWeaponResolver.coherentOwnedStack(false, Optional.of("stale")));
        assertTrue(MeleeWeaponResolver.coherentOwnedStack(true, Optional.of("owned")));
        assertTrue(MeleeWeaponResolver.coherentOwnedStack(false, Optional.empty()));
        // A coherent worn nonweapon has no melee profile, so it does not prevent actor fallback.
        assertEquals(MeleeWeaponResolver.Kind.UNARMED,
                choose(false, Optional.empty(), Optional.empty(), Optional.of(NORMAL)).orElseThrow().kind());
    }
}
