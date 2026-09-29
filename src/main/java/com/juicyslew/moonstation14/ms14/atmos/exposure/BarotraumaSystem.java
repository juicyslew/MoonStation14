package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Prototype component membership is the only pressure-hazard authority. */
public final class BarotraumaSystem {
    private BarotraumaSystem() { }

    public static boolean tickIfDue(LivingEntity entity, long gameTime) {
        return tickIfDue(entity, gameTime, AtmosphereService.INSTANCE);
    }

    /** Same resolution path for production and isolated-atmosphere tests. */
    public static boolean tickIfDue(LivingEntity entity, long gameTime, AtmosphereService service) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || !entity.isAlive()
                || service == null || !service.isEnabled()) return false;
        var component = CharacterIdentitySystem.resolveForHost(entity).flatMap(data -> data.component(BarotraumaComponent.class));
        if (component.isEmpty() || Math.floorMod(gameTime + (long) entity.getId(),
                BarotraumaAtmospherePolicy.CADENCE_TICKS) != 0) return false;
        // Unknown is not vacuum. A read must never enroll an entity or create damage state.
        var sample = service.sample(level, BlockPos.containing(entity.getEyePosition()));
        if (sample.isEmpty()) return false;
        var damage = PressureExposure.limitMatchingDamage(
                PressureExposure.damageAt(sample.get().pressureKpa(1.0), component.get()),
                DamageSystem.existingOrVanillaBaseline(entity), component.get());
        return !damage.isEmpty() && DamageSystem.applyHealthChange(entity, damage, 1f, true)
                == DamageSystem.Result.APPLIED;
    }
}
