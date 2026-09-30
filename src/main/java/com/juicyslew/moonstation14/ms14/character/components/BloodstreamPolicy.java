package com.juicyslew.moonstation14.ms14.character.components;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable value carried by a typed Bloodstream component, not a separate catalog authority. */
public record BloodstreamPolicy(Map<String, Double> referenceSolution,
                                List<ResourceLocation> metabolismExclusions,
                                double maxVolumeModifier, double updateIntervalSeconds,
                                double bleedDecayPerUpdate, double maxBleedRate,
                                Map<String, Double> damageBleedMultipliers, double bloodRefreshPerUpdate,
                                double bloodlossThresholdFraction, Map<String, Double> bloodlossDamagePerUpdate,
                                Map<String, Double> bloodlossHealPerUpdate,
                                boolean bloodlossIgnoreResistances, double bleedPuddleThreshold) {
    private static final Codec<Map<String, Double>> DAMAGE_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);
    private static final Codec<Map<String, Double>> SOLUTION_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);
    private static final Codec<BloodstreamPolicy> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SOLUTION_MAP_CODEC.fieldOf("reference_solution").forGetter(BloodstreamPolicy::referenceSolution),
            Codec.list(ResourceLocation.CODEC).fieldOf("metabolism_exclusions").forGetter(BloodstreamPolicy::metabolismExclusions),
            Codec.DOUBLE.fieldOf("max_volume_modifier").forGetter(BloodstreamPolicy::maxVolumeModifier),
            Codec.DOUBLE.fieldOf("update_interval_seconds").forGetter(BloodstreamPolicy::updateIntervalSeconds),
            Codec.DOUBLE.fieldOf("bleed_decay_per_update").forGetter(BloodstreamPolicy::bleedDecayPerUpdate),
            Codec.DOUBLE.fieldOf("max_bleed_rate").forGetter(BloodstreamPolicy::maxBleedRate),
            DAMAGE_MAP_CODEC.fieldOf("damage_bleed_multipliers").forGetter(BloodstreamPolicy::damageBleedMultipliers),
            Codec.DOUBLE.fieldOf("blood_refresh_per_update").forGetter(BloodstreamPolicy::bloodRefreshPerUpdate),
            Codec.DOUBLE.fieldOf("bloodloss_threshold_fraction").forGetter(BloodstreamPolicy::bloodlossThresholdFraction),
            DAMAGE_MAP_CODEC.fieldOf("bloodloss_damage_per_update").forGetter(BloodstreamPolicy::bloodlossDamagePerUpdate),
            DAMAGE_MAP_CODEC.fieldOf("bloodloss_heal_per_update").forGetter(BloodstreamPolicy::bloodlossHealPerUpdate),
            Codec.BOOL.fieldOf("bloodloss_ignore_resistances").forGetter(BloodstreamPolicy::bloodlossIgnoreResistances),
            Codec.DOUBLE.fieldOf("bleed_puddle_threshold").forGetter(BloodstreamPolicy::bleedPuddleThreshold)
    ).apply(instance, BloodstreamPolicy::new));

    public static final Codec<BloodstreamPolicy> CODEC = Codec.of(STRUCTURAL_CODEC, new Decoder<>() {
        @Override
        public <T> DataResult<Pair<BloodstreamPolicy, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "blood must be a JSON object");
                CharacterSchemaAudit.auditBlood(json.getAsJsonObject(), "$.components[0]", true);
                return STRUCTURAL_CODEC.decode(ops, input);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                return DataResult.error(() -> message);
            }
        }
    });

    public BloodstreamPolicy {
        referenceSolution = Map.copyOf(Objects.requireNonNull(referenceSolution, "referenceSolution"));
        metabolismExclusions = List.copyOf(Objects.requireNonNull(metabolismExclusions, "metabolismExclusions"));
        damageBleedMultipliers = Map.copyOf(Objects.requireNonNull(damageBleedMultipliers, "damageBleedMultipliers"));
        bloodlossDamagePerUpdate = Map.copyOf(Objects.requireNonNull(bloodlossDamagePerUpdate, "bloodlossDamagePerUpdate"));
        bloodlossHealPerUpdate = Map.copyOf(Objects.requireNonNull(bloodlossHealPerUpdate, "bloodlossHealPerUpdate"));
        if (new HashSet<>(metabolismExclusions).size() != metabolismExclusions.size()
                || !metabolismExclusions.stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.toSet())
                .containsAll(referenceSolution.keySet())) {
            throw new IllegalArgumentException("metabolismExclusions must be distinct and include every reference blood reagent");
        }
    }
}
