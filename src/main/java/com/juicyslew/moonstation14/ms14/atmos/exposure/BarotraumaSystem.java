package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
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
        if (entity == null || !(entity.level() instanceof ServerLevel level) || !physicalSourceEligible(entity)
                || service == null) return false;
        return tickIfDue(entity, gameTime, service, ModCharacters.catalog(level));
    }

    /** Read one explicit, immutable character snapshot without changing the production catalog. */
    public static boolean tickIfDue(LivingEntity entity, long gameTime, AtmosphereService service,
                                    PrototypeCatalog<CharacterData> snapshot) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || !physicalSourceEligible(entity)
                || service == null || snapshot == null) return false;
        var component = CharacterIdentitySystem.resolveForActor(entity, snapshot)
                .flatMap(data -> data.component(BarotraumaComponent.class));
        if (component.isEmpty() || Math.floorMod(gameTime + (long) entity.getId(),
                BarotraumaAtmospherePolicy.CADENCE_TICKS) != 0) return false;
        // Unknown is not vacuum. A read must never enroll an entity or create damage state.
        var selection = ExposureAirRouting.select(entity);
        double pressure;
        if (selection.contained()) {
            if (selection.source().isEmpty()) return false;
            var sample = selection.source().get().sample();
            if (sample.isEmpty()) return false;
            pressure = sample.get().pressureKpa();
        } else {
            var sample = service.sample(level, BlockPos.containing(entity.getEyePosition()));
            if (sample.isEmpty()) return false;
            pressure = sample.get().pressureKpa(1.0);
        }
        var damage = PressureExposure.limitMatchingDamage(
                PressureExposure.damageAt(pressure, component.get()),
                DamageSystem.existingOrVanillaBaseline(entity), component.get());
        return !damage.isEmpty() && DamageSystem.applyHealthChange(entity, damage, 1f, true, DamageSystem.Cause.RESPIRATION)
                == DamageSystem.Result.APPLIED;
    }

    private static boolean physicalSourceEligible(LivingEntity entity) {
        return entity.isAlive() || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem
                .isRetainedNonAliveBody(entity);
    }
}

