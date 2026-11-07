package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.world.entity.LivingEntity;

/** Shared typed health/armor and stamina application for ranged impact backends. */
public final class GunImpactEffects {
    private GunImpactEffects() { }

    public static void apply(LivingEntity target, ProjectileProfile profile, LivingEntity source) {
        if (!profile.damage().isEmpty())
            DamageSystem.applyHealthChangeDetailed(target, profile.damage(), 1f, profile.ignoreResistances(), DamageSystem.Cause.COMBAT, source);
        if (profile.staminaDamage() > 0f)
            com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.takeStaminaDamage(target, profile.staminaDamage());
    }
}
