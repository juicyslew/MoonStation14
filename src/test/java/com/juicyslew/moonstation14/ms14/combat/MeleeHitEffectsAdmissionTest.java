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

class MeleeHitEffectsAdmissionTest {
    private static MeleeWeaponResolver.Selection selection(MeleeWeaponResolver.Kind kind, Map<String, Float> selfDamage, boolean injector) {
        var weapon = new MeleeWeaponData(Map.of("blunt", 1f), 1, 1.5f, false, false, 1, false);
        Optional<MeleeWeaponProfile.MeleeChemicalInjectorData> injectorData = injector
                ? Optional.of(new MeleeWeaponProfile.MeleeChemicalInjectorData("food", 100, false)) : Optional.empty();
        return new MeleeWeaponResolver.Selection(kind, new MeleeWeaponProfile(weapon, selfDamage, injectorData),
                UUID.randomUUID(), 0, "main", kind == MeleeWeaponResolver.Kind.UNARMED ? Optional.empty() : Optional.of("token"));
    }

    @Test void onlyRealHitsWithAnArmedDamageProfileAreEligibleEvenWhenHandled() {
        var armed = selection(MeleeWeaponResolver.Kind.HELD, Map.of("blunt", 5f), false);
        var attacker = mock(LivingEntity.class);
        var target = mock(Entity.class);
        var hit = new MeleeAttackEvents.MeleeHitEvent(attacker, List.of(target), armed);
        hit.setHandled(true);
        assertTrue(hit.isHit());
        assertTrue(RegisteredItemMeleeHitEffectsSystem.eligibleForSelfDamage(hit.weaponSelection()));
        assertTrue(RegisteredItemMeleeHitEffectsSystem.eligibleForAnyEffect(hit.weaponSelection()));
        assertTrue(RegisteredItemMeleeHitEffectsSystem.eligibleForEvent(hit));

        var miss = new MeleeAttackEvents.MeleeHitEvent(attacker, List.of(), armed);
        assertFalse(miss.isHit());
        assertFalse(RegisteredItemMeleeHitEffectsSystem.eligibleForEvent(miss));
        assertFalse(RegisteredItemMeleeHitEffectsSystem.eligibleForSelfDamage(null));
        var injectorOnly = selection(MeleeWeaponResolver.Kind.HELD, Map.of(), true);
        var both = selection(MeleeWeaponResolver.Kind.GLOVES, Map.of("blunt", 2f), true);
        var empty = selection(MeleeWeaponResolver.Kind.HELD, Map.of(), false);
        var unarmed = selection(MeleeWeaponResolver.Kind.UNARMED, Map.of("blunt", 5f), true);
        assertTrue(RegisteredItemMeleeHitEffectsSystem.eligibleForAnyEffect(injectorOnly));
        assertTrue(RegisteredItemMeleeHitEffectsSystem.eligibleForAnyEffect(armed));
        assertTrue(RegisteredItemMeleeHitEffectsSystem.eligibleForAnyEffect(both));
        assertFalse(RegisteredItemMeleeHitEffectsSystem.eligibleForAnyEffect(empty));
        assertFalse(RegisteredItemMeleeHitEffectsSystem.eligibleForAnyEffect(unarmed));
        assertFalse(RegisteredItemMeleeHitEffectsSystem.eligibleForSelfDamage(injectorOnly));
    }
}
