package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

import java.util.Map;
import java.util.Optional;
import java.util.function.DoublePredicate;

/** Server-side, character-policy-driven thermal exposure and conservative gas coupling. */
public final class BodyTemperatureSystem {
    private BodyTemperatureSystem() { }

    public static boolean isEligible(LivingEntity entity) {
        return eligibleForThermal(entity instanceof ServerPlayer || entity instanceof Villager,
                entity instanceof ServerPlayer && entity.isSpectator(),
                AtmosphereService.INSTANCE.isEnabled(), profile(entity).isPresent());
    }

    /** Pure policy seam for server-independent eligibility checks. */
    public static boolean eligibleForThermal(boolean supportedActor, boolean spectatorPlayer,
                                             boolean atmosphereEnabled, boolean hasThermalProfile) {
        return supportedActor && !spectatorPlayer && atmosphereEnabled && hasThermalProfile;
    }

    /** Initializes once from character policy; persisted temperatures are never overwritten. */
    public static void reconcile(LivingEntity entity) {
        if (!supported(entity) || !(entity.level() instanceof ServerLevel) || !AtmosphereService.INSTANCE.isEnabled()) return;
        Optional<CharacterData.ThermalData> thermal = profile(entity);
        if (thermal.isEmpty()) return;
        if (entity.getExistingDataOrNull(ModDataAttachments.BODY_TEMPERATURE.get()) == null) {
            MS14Provider.update(entity, MS14Bridges.BODY_TEMPERATURE,
                    new BodyTemperatureAttachment(new BodyTemperatureComponent(thermal.get().currentKelvin())));
        }
    }

    public static Outcome exposeOneSecond(LivingEntity entity, ServerLevel level) {
        if (!supported(entity) || entity.level() != level || !AtmosphereService.INSTANCE.isEnabled()) return Outcome.SKIPPED;
        Optional<CharacterData.ThermalData> selected = profile(entity);
        if (selected.isEmpty()) return Outcome.SKIPPED;
        reconcile(entity);
        BlockPos eye = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        var sample = AtmosphereService.INSTANCE.sample(level, eye);
        if (sample.isEmpty()) return applyStoredBodyDamage(entity, selected.get().toProfile());
        // Strict sample supplies the physical mixture. This presentation read is used only
        // for ownership classification; PROVISIONAL is never accepted for physics.
        var reading = AtmosphereService.INSTANCE.readAtmosphere(level, eye);
        if (reading.isEmpty() || reading.get().status() == AtmosphereReading.Status.PROVISIONAL) {
            return applyStoredBodyDamage(entity, selected.get().toProfile());
        }
        BodyTemperatureAttachment detached = MS14Provider.getDetached(entity, MS14Bridges.BODY_TEMPERATURE);
        BodyTemperatureComponent before = detached.toComponent();
        Outcome result = transact(detached, sample.get(), selected.get().toProfile(),
                energyCommitFor(reading.get().status(), joules -> AtmosphereService.INSTANCE.addEnergy(level, eye, joules)),
                damage -> DamageSystem.applyHealthChange(entity, damage, 1.0f, true)
                        == DamageSystem.Result.APPLIED);
        if (result == Outcome.APPLIED) MS14Provider.updateIfChanged(entity, MS14Bridges.BODY_TEMPERATURE, before, detached);
        return result;
    }

    private static Outcome applyStoredBodyDamage(LivingEntity entity,
                                                 ThermalExposureMath.ThermalProfile profile) {
        BodyTemperatureAttachment stored = entity.getExistingDataOrNull(ModDataAttachments.BODY_TEMPERATURE.get());
        if (stored == null) return Outcome.SKIPPED;
        applyBodyDamage(stored.kelvin(), profile, 1.0,
                damage -> DamageSystem.applyHealthChange(entity, damage, 1.0f, true)
                        == DamageSystem.Result.APPLIED);
        return Outcome.APPLIED;
    }

    /** Applies at most one typed damage transaction for a body-state interval. */
    public static boolean applyBodyDamage(double bodyKelvin,
                                          ThermalExposureMath.ThermalProfile profile,
                                          double seconds,
                                          java.util.function.Predicate<Map<String, Float>> damageSink) {
        if (damageSink == null) return false;
        Map<String, Float> damage;
        try {
            damage = thresholdDamage(bodyKelvin, profile, seconds);
        } catch (IllegalArgumentException invalidAmounts) {
            return false;
        }
        return damage.isEmpty() || damageSink.test(damage);
    }

    /** Body-state-only typed damage for one scheduler interval; empty means below thresholds. */
    public static Map<String, Float> thresholdDamage(double bodyKelvin,
                                                     ThermalExposureMath.ThermalProfile profile,
                                                     double seconds) {
        ThermalExposureMath.DamageAmounts amounts = ThermalExposureMath.damageAt(bodyKelvin, profile, seconds);
        if (amounts.heat() > Float.MAX_VALUE || amounts.cold() > Float.MAX_VALUE) {
            throw new IllegalArgumentException("thermal damage exceeds typed-damage range");
        }
        java.util.LinkedHashMap<String, Float> result = new java.util.LinkedHashMap<>();
        if (amounts.heat() > 0) result.put(DamageKeys.HEAT, (float) amounts.heat());
        if (amounts.cold() > 0) result.put(DamageKeys.COLD, (float) amounts.cold());
        return Map.copyOf(result);
    }

    /** Applies SS14 AdjustTemperature's joules directly to bound body heat capacity. */
    public static EffectResult adjustHeat(Entity target, float amountJoules, float scale) {
        if (!(target instanceof LivingEntity living) || !(target.level() instanceof ServerLevel)
                || !supported(living)) return EffectResult.SKIPPED_UNSUPPORTED;
        if (!AtmosphereService.INSTANCE.isEnabled()) return EffectResult.SKIPPED_UNSUPPORTED;
        Optional<CharacterData.ThermalData> thermal = profile(living);
        if (thermal.isEmpty()) return EffectResult.SKIPPED_UNSUPPORTED;
        if (!Float.isFinite(amountJoules) || !Float.isFinite(scale)) return EffectResult.FAILED;
        float scaledJoules = amountJoules * scale;
        if (!Float.isFinite(scaledJoules)) return EffectResult.FAILED;

        ThermalExposureMath.ThermalProfile bodyProfile = thermal.get().toProfile();
        BodyTemperatureAttachment stored = living.getExistingDataOrNull(ModDataAttachments.BODY_TEMPERATURE.get());
        BodyTemperatureAttachment detached = stored == null
                ? new BodyTemperatureAttachment(new BodyTemperatureComponent(thermal.get().currentKelvin()))
                : MS14Provider.getDetached(living, MS14Bridges.BODY_TEMPERATURE);
        BodyTemperatureComponent before = stored == null
                ? BodyTemperatureComponent.DEFAULT : detached.toComponent();
        EffectResult adjusted = adjustHeat(detached, bodyProfile, scaledJoules);
        if (adjusted == EffectResult.APPLIED) {
            MS14Provider.updateIfChanged(living, MS14Bridges.BODY_TEMPERATURE, before, detached);
        }
        return adjusted;
    }

    /** Pure thermal arithmetic seam: positive joules heat, negative joules cool. */
    public static EffectResult adjustHeat(BodyTemperatureAttachment detached,
                                          ThermalExposureMath.ThermalProfile profile,
                                          float amountJoules) {
        if (detached == null || profile == null) return EffectResult.SKIPPED_UNSUPPORTED;
        if (!Float.isFinite(amountJoules)) return EffectResult.FAILED;
        double heatCapacity = profile.bodyHeatCapacityJoulesPerKelvin();
        double deltaKelvin = amountJoules / heatCapacity;
        double nextKelvin = detached.kelvin() + deltaKelvin;
        if (!Double.isFinite(heatCapacity) || heatCapacity <= 0 || !Double.isFinite(deltaKelvin)
                || !Double.isFinite(nextKelvin) || nextKelvin < 2.7 || nextKelvin > 20000) {
            return EffectResult.FAILED;
        }
        detached.setKelvin(nextKelvin);
        return EffectResult.APPLIED;
    }

    public static EffectResult adjustHeat(BodyTemperatureAttachment detached,
                                          ThermalExposureMath.ThermalProfile profile,
                                          float amountJoules, float scale) {
        if (!Float.isFinite(amountJoules) || !Float.isFinite(scale)) return EffectResult.FAILED;
        float scaledJoules = amountJoules * scale;
        return Float.isFinite(scaledJoules) ? adjustHeat(detached, profile, scaledJoules) : EffectResult.FAILED;
    }

    /** Selects the thermal reservoir policy; immutable exterior energy is not world-persisted. */
    public static DoublePredicate energyCommitFor(AtmosphereReading.Status status,
                                                   DoublePredicate finiteGasWriter) {
        if (status == null || finiteGasWriter == null) return ignored -> false;
        return switch (status) {
            case FINITE -> finiteGasWriter;
            // SS14-style map atmosphere is an immutable thermal reservoir. There is no
            // exterior ledger in AtmosphereService, so accepted exchange is intentionally untracked.
            case EXTERIOR -> ignored -> true;
            case PROVISIONAL -> ignored -> false;
        };
    }

    /** Pure preflight + ordered commit seam. Gas energy is committed before body state/damage. */
    public static Outcome transact(BodyTemperatureAttachment detached,
                                   com.juicyslew.moonstation14.ms14.atmos.core.GasMixture gas,
                                   ThermalExposureMath.ThermalProfile profile,
                                   DoublePredicate energyCommit,
                                   java.util.function.Predicate<Map<String, Float>> damageSink) {
        if (detached == null || gas == null || profile == null || energyCommit == null || damageSink == null) return Outcome.SKIPPED;
        BodyTemperatureComponent old = detached.toComponent();
        ThermalExposureMath.ExposureResult exposure;
        try {
            exposure = ThermalExposureMath.expose(old.kelvin(), gas, profile, 1.0);
            double next = exposure.bodyTemperatureKelvin();
            double delta = next - old.kelvin();
            double energy = exposure.environmentEnergyDeltaJoules();
            if (!Double.isFinite(next) || next < 2.7 || next > 20000 || !Double.isFinite(delta)
                    || !Double.isFinite(energy)) return Outcome.SKIPPED;
            double heat = exposure.damage().heat();
            double cold = exposure.damage().cold();
            if (!Double.isFinite(heat) || heat < 0 || !Double.isFinite(cold) || cold < 0
                    || heat > Float.MAX_VALUE || cold > Float.MAX_VALUE) return Outcome.SKIPPED;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Outcome.SKIPPED;
        }
        // Nothing after this point is speculative: a failed energy write aborts all body
        // effects. The server-thread caller commits the detached body once energy is accepted.
        double energy = exposure.environmentEnergyDeltaJoules();
        if (energy != 0.0 && !energyCommit.test(energy)) return Outcome.SKIPPED;
        detached.setKelvin(exposure.bodyTemperatureKelvin());
        double heat = exposure.damage().heat();
        double cold = exposure.damage().cold();
        if (heat != 0 || cold != 0) {
            java.util.LinkedHashMap<String, Float> typed = new java.util.LinkedHashMap<>();
            if (heat != 0) typed.put(DamageKeys.HEAT, (float) heat);
            if (cold != 0) typed.put(DamageKeys.COLD, (float) cold);
            // Attempt exactly once. A canceled/invulnerable DamageSystem operation is not
            // retried on later ticks; thermal state still advances conservatively.
            damageSink.test(Map.copyOf(typed));
        }
        return Outcome.APPLIED;
    }

    private static Optional<CharacterData.ThermalData> profile(LivingEntity entity) {
        return CharacterIdentitySystem.resolve(entity).flatMap(CharacterData::thermal);
    }

    private static boolean supported(LivingEntity entity) {
        return entity instanceof ServerPlayer player && !player.isSpectator()
                || entity instanceof Villager;
    }

    public enum Outcome { APPLIED, SKIPPED }
}
