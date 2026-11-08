package com.juicyslew.moonstation14.ms14.damage;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DamageOriginTest {
    @Test void damageSourceFactoryRetainsOptionalOrigin() {
        Holder<DamageType> type = Holder.direct(new DamageType("test", 0.1f));
        Entity origin = mock(Entity.class);

        DamageSource attributed = DamageSystem.damageSource(type, origin);
        DamageSource unattributed = DamageSystem.damageSource(type, null);

        assertSame(origin, attributed.getEntity());
        assertNull(unattributed.getEntity());
    }

    @Test void damageEventsExposeOriginAndPreserveLegacyNullAndMapImmutability() {
        LivingEntity victim = mock(LivingEntity.class);
        Entity origin = mock(Entity.class);
        Map<String, Float> delta = new HashMap<>(Map.of(DamageKeys.BLUNT, 2f));
        TypedDamageAppliedEvent attributed = new TypedDamageAppliedEvent(victim, delta, origin);
        delta.put(DamageKeys.BLUNT, 4f);

        assertSame(origin, attributed.origin());
        assertEquals(Map.of(DamageKeys.BLUNT, 2f), attributed.delta());
        assertThrows(UnsupportedOperationException.class,
                () -> attributed.delta().put(DamageKeys.BLUNT, 3f));
        assertNull(new TypedDamageAppliedEvent(victim, Map.of()).origin());
        var feedback = new DamageFeedbackProfile(false, false, false, false);
        assertSame(origin, new AppliedDamageEvent(victim, 1d, feedback, origin).origin());
        assertNull(new AppliedDamageEvent(victim, 1d, feedback).origin());
    }
}
