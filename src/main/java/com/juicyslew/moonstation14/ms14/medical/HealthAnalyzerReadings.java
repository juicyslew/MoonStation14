package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.blood.BloodReducer;
import com.juicyslew.moonstation14.ms14.blood.BloodSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.character.components.HumanoidProfileComponent;
import com.juicyslew.moonstation14.ms14.character.components.SpeciesComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only construction of a health analyzer frame from live server authority state. */
public final class HealthAnalyzerReadings {
    private HealthAnalyzerReadings() { }

    public static HealthAnalyzerSnapshot read(UUID scannerId, long epoch, UUID actorId,
            LivingEntity target, long frame, boolean active, boolean inRange) {
        if (target == null || !(target.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread())
            throw new IllegalArgumentException("analyzer readings require a live server target");

        var identity = CharacterIdentitySystem.resolveForActor(target);
        MobStatePolicy statePolicy = MobStateSystem.policy(target);
        String state = statePolicy == null ? "Unknown"
                : switch (MobStateSystem.currentState(target)) {
                    case ALIVE -> "Alive";
                    case CRITICAL -> "Critical";
                    case DEAD -> "Dead";
                };
        // Revive policy describes threshold-driven healing, not permanent inability to revive.
        // Only an explicit permanent-unrevivable marker may set this warning; none exists yet.
        boolean unrevivable = false;

        Map<String, Float> damage = Map.of();
        var storedDamage = target.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        if (identity.isPresent() && statePolicy != null && storedDamage != null) {
            try { damage = DamageKeys.validateState(storedDamage.getMap()); }
            catch (RuntimeException invalidLedger) { damage = Map.of(); }
        }

        Optional<Float> temperature = Optional.empty();
        var bodyTemperature = target.getExistingDataOrNull(ModDataAttachments.BODY_TEMPERATURE.get());
        if (identity.isPresent() && bodyTemperature != null) {
            double kelvin = bodyTemperature.toComponent().kelvin();
            if (Double.isFinite(kelvin) && kelvin > 0 && kelvin <= Float.MAX_VALUE)
                temperature = Optional.of((float) kelvin);
        }

        Optional<Float> blood = Optional.empty();
        var policy = BloodSystem.resolvePolicy(target);
        if (identity.isPresent() && policy.isPresent()
                && identity.get().component(BloodstreamComponent.class).isPresent()
                && target.getExistingDataOrNull(ModDataAttachments.BLOOD.get()) != null
                && target.getExistingDataOrNull(ModDataAttachments.BLOODSTREAM.get()) != null
                && target.getExistingDataOrNull(ModDataAttachments.REAGENT.get()) == null) {
            try {
                if (BloodSystem.state(target).isPresent()) {
                    ReagentAttachment mixture = MS14Provider.getDetached(target, MS14Bridges.BLOODSTREAM);
                    if (mixture != null) {
                        double fraction = BloodReducer.usableFraction(mixture,
                                BloodReducer.reference(policy.get()), policy.get().maxVolumeModifier());
                        if (Double.isFinite(fraction) && fraction >= 0 && fraction <= Float.MAX_VALUE)
                            blood = Optional.of((float) fraction);
                    }
                }
            } catch (RuntimeException invalidBlood) { blood = Optional.empty(); }
        }

        boolean bleeding = false;
        var bloodState = BloodSystem.state(target).orElse(null);
        if (identity.isPresent() && bloodState != null) bleeding = bloodState.bleedRate() > 0;
        String species = identity.flatMap(value -> value.component(SpeciesComponent.class))
                .map(SpeciesComponent::label)
                .or(() -> identity.flatMap(value -> value.component(HumanoidProfileComponent.class))
                        .map(HumanoidProfileComponent::species)).orElse("Unknown");
        String name = target.getName().getString();
        return HealthAnalyzerSnapshot.readings(scannerId, epoch, actorId, target.getId(), target.getUUID(), frame,
                name, species, state, active, inRange, temperature, blood, bleeding, unrevivable, damage);
    }
}

