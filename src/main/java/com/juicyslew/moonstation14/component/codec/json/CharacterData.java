package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalExposureMath;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponent;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, data-only character policy. The catalog key supplies identity. */
public record CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                            List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal,
                            List<String> hands, Optional<BloodData> blood, Optional<LungsData> lungs,
                            List<CharacterComponent> components, Optional<Set<MetabolizerTypeEnum>> metabolizerTypes) {
    private static final Codec<CharacterData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    SlipTargetData.CODEC.fieldOf("slip_data").forGetter(CharacterData::slipData),
                    MovementData.CODEC.optionalFieldOf("movement").forGetter(CharacterData::movement),
                    Codec.list(ResourceLocation.CODEC).optionalFieldOf("host_entity_types", List.of())
                            .forGetter(CharacterData::hostEntityTypes),
                    ThermalData.CODEC.optionalFieldOf("thermal").forGetter(CharacterData::thermal),
                    Codec.list(Codec.STRING).optionalFieldOf("hands", List.of()).forGetter(CharacterData::hands),
                    BloodData.CODEC.optionalFieldOf("blood").forGetter(CharacterData::blood),
                    LungsData.CODEC.optionalFieldOf("lungs").forGetter(CharacterData::lungs),
                    Codec.list(CharacterComponentRegistry.CODEC).optionalFieldOf("components", List.of()).forGetter(CharacterData::components),
                    Codec.list(MetabolizerTypeEnum.CODEC).xmap(Set::copyOf, List::copyOf)
                            .optionalFieldOf("metabolizer_types").forGetter(CharacterData::metabolizerTypes))
                    .apply(instance, CharacterData::new));

    private static final Decoder<CharacterData> STRICT_DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<CharacterData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                // JsonOps conversion can dereference nested JsonNull before the schema audit runs.
                // Check optional policy blocks on raw JSON before JsonOps can dereference nested nulls.
                if (input instanceof JsonObject character) {
                    CharacterSchemaAudit.auditMetabolizerTypes(character);
                    CharacterSchemaAudit.auditBloodIfPresent(character);
                    CharacterSchemaAudit.auditLungsIfPresent(character);
                    CharacterSchemaAudit.auditThermalIfPresent(character);
                    CharacterSchemaAudit.auditComponentsIfPresent(character);
                    if (character.has("hands")) CharacterSchemaAudit.auditHands(character.get("hands"));
                }
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "character must be a JSON object");
                }
                CharacterSchemaAudit.audit(json.getAsJsonObject());
                return STRUCTURAL_CODEC.decode(ops, input);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                return DataResult.error(() -> message);
            }
        }
    };

    public static final Codec<CharacterData> CODEC = Codec.of(STRUCTURAL_CODEC, STRICT_DECODER);

    public CharacterData {
        Objects.requireNonNull(slipData, "slipData");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(hostEntityTypes, "hostEntityTypes");
        Objects.requireNonNull(thermal, "thermal");
        Objects.requireNonNull(hands, "hands");
        hostEntityTypes = List.copyOf(hostEntityTypes);
        hands = List.copyOf(hands);
        if (!hands.isEmpty()) HandState.create(hands);
        Objects.requireNonNull(blood, "blood");
        Objects.requireNonNull(lungs, "lungs");
        components = List.copyOf(Objects.requireNonNull(components, "components"));
        if (components.size() > 16 || components.stream().map(CharacterComponent::type).distinct().count() != components.size())
            throw new IllegalArgumentException("components must contain at most 16 distinct types");
        if (components.stream().anyMatch(component -> !CharacterComponentRegistry.registered(component.type())))
            throw new IllegalArgumentException("components must use registered types");
        Objects.requireNonNull(metabolizerTypes, "metabolizerTypes");
        metabolizerTypes = metabolizerTypes.map(Set::copyOf);
    }

    /** Compatibility for callers using the pre-hands record signature. */
    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal,
                         Optional<BloodData> blood, Optional<LungsData> lungs, List<CharacterComponent> components,
                         Optional<Set<MetabolizerTypeEnum>> metabolizerTypes) {
        this(slipData, movement, hostEntityTypes, thermal, List.of(), blood, lungs, components, metabolizerTypes);
    }

    /** Compatibility for callers using the hands-only record signature. */
    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal, List<String> hands) {
        this(slipData, movement, hostEntityTypes, thermal, hands, Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    }

    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal,
                         Optional<BloodData> blood, Optional<LungsData> lungs, List<CharacterComponent> components) {
        this(slipData, movement, hostEntityTypes, thermal, blood, lungs, components, Optional.empty());
    }

    /** Compatibility for callers using the pre-pressure record signature. */
    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal,
                         Optional<BloodData> blood, Optional<LungsData> lungs) {
        this(slipData, movement, hostEntityTypes, thermal, blood, lungs, List.of());
    }

    /** Backward-compatible constructor for character policies predating blood data. */
    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal) {
        this(slipData, movement, hostEntityTypes, thermal, List.of());
    }

    /** Backward-compatible constructor for character policies predating lung data. */
    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal,
                         Optional<BloodData> blood) {
        this(slipData, movement, hostEntityTypes, thermal, blood, Optional.empty());
    }

    /** Backward-compatible constructor for character policies predating thermal data. */
    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes) {
        this(slipData, movement, hostEntityTypes, Optional.empty(), List.of());
    }

    /** Backward-compatible slip-only prototype constructor. */
    public CharacterData(SlipTargetData slipData) {
        this(slipData, Optional.empty(), List.of(), Optional.empty(), List.of());
    }

    public <T extends CharacterComponent> Optional<T> component(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return components.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public Optional<BarotraumaComponent> barotrauma() { return component(BarotraumaComponent.class); }

    /** Explicit prototype-owned respiratory policy; no gameplay defaults are supplied here. */
    public record LungsData(double breathIntervalSeconds, double breathVolumeLiters,
                             double maxLungMoles, double breathMolesToSaturationMultiplier, double maxSaturation,
                             double initialSaturation, double minSaturation,
                            double saturationLossPerUpdate, double suffocationThreshold,
                            double suffocationDamagePerUpdate, double suffocationRecoveryPerUpdate,
                             boolean suffocationIgnoreResistances,
                             Map<String, Map<String, Double>> toxicGasDamagePerMole,
                             double toxicGasDamageCapPerInhale) {
        private static final Codec<LungsData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("breath_interval_seconds").forGetter(LungsData::breathIntervalSeconds),
                 Codec.DOUBLE.fieldOf("breath_volume_liters").forGetter(LungsData::breathVolumeLiters),
                 Codec.DOUBLE.fieldOf("max_lung_moles").forGetter(LungsData::maxLungMoles),
                 Codec.DOUBLE.fieldOf("breath_moles_to_saturation_multiplier").forGetter(LungsData::breathMolesToSaturationMultiplier),
                 Codec.DOUBLE.fieldOf("max_saturation").forGetter(LungsData::maxSaturation),
                 Codec.DOUBLE.fieldOf("initial_saturation").forGetter(LungsData::initialSaturation),
                 Codec.DOUBLE.fieldOf("min_saturation").forGetter(LungsData::minSaturation),
                Codec.DOUBLE.fieldOf("saturation_loss_per_update").forGetter(LungsData::saturationLossPerUpdate),
                Codec.DOUBLE.fieldOf("suffocation_threshold").forGetter(LungsData::suffocationThreshold),
                Codec.DOUBLE.fieldOf("suffocation_damage_per_update").forGetter(LungsData::suffocationDamagePerUpdate),
                Codec.DOUBLE.fieldOf("suffocation_recovery_per_update").forGetter(LungsData::suffocationRecoveryPerUpdate),
                 Codec.BOOL.fieldOf("suffocation_ignore_resistances").forGetter(LungsData::suffocationIgnoreResistances),
                 Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.DOUBLE))
                         .fieldOf("toxic_gas_damage_per_mole").forGetter(LungsData::toxicGasDamagePerMole),
                 Codec.DOUBLE.fieldOf("toxic_gas_damage_cap_per_inhale").forGetter(LungsData::toxicGasDamageCapPerInhale)
        ).apply(instance, LungsData::new));

        public static final Codec<LungsData> CODEC = Codec.of(STRUCTURAL_CODEC, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<LungsData, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) return DataResult.error(() -> "lungs must be a JSON object");
                    CharacterSchemaAudit.auditLungs(json.getAsJsonObject());
                    return STRUCTURAL_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            }
        });

        public LungsData {
            Objects.requireNonNull(toxicGasDamagePerMole, "toxicGasDamagePerMole");
            toxicGasDamagePerMole = toxicGasDamagePerMole.entrySet().stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                            entry -> Map.copyOf(entry.getValue())));
        }

        /** Programmatic fixture without toxin entries. */
        public LungsData(double interval, double liters, double maxMoles, double multiplier,
                         double max, double initial, double min, double loss, double threshold,
                         double damage, double recovery, boolean ignore) {
            this(interval, liters, maxMoles, multiplier, max, initial, min, loss, threshold,
                    damage, recovery, ignore, Map.of(), 0.0);
        }
    }

    /** All cadence and gameplay amounts are explicit character prototype policy. */
    public record BloodData(Map<String, Double> referenceSolution,
                            List<ResourceLocation> metabolismExclusions,
                            double maxVolumeModifier, double updateIntervalSeconds,
                            double bleedDecayPerUpdate, double maxBleedRate,
                            Map<String, Double> damageBleedMultipliers, double bloodRefreshPerUpdate,
                             double bloodlossThresholdFraction, Map<String, Double> bloodlossDamagePerUpdate,
                              Map<String, Double> bloodlossHealPerUpdate,
                               boolean bloodlossIgnoreResistances, double bleedPuddleThreshold) {
        private static final Codec<Map<String, Double>> DAMAGE_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);
        private static final Codec<Map<String, Double>> SOLUTION_MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);
        private static final Codec<BloodData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SOLUTION_MAP_CODEC.fieldOf("reference_solution").forGetter(BloodData::referenceSolution),
                Codec.list(ResourceLocation.CODEC).fieldOf("metabolism_exclusions").forGetter(BloodData::metabolismExclusions),
                Codec.DOUBLE.fieldOf("max_volume_modifier").forGetter(BloodData::maxVolumeModifier),
                Codec.DOUBLE.fieldOf("update_interval_seconds").forGetter(BloodData::updateIntervalSeconds),
                Codec.DOUBLE.fieldOf("bleed_decay_per_update").forGetter(BloodData::bleedDecayPerUpdate),
                Codec.DOUBLE.fieldOf("max_bleed_rate").forGetter(BloodData::maxBleedRate),
                DAMAGE_MAP_CODEC.fieldOf("damage_bleed_multipliers").forGetter(BloodData::damageBleedMultipliers),
                Codec.DOUBLE.fieldOf("blood_refresh_per_update").forGetter(BloodData::bloodRefreshPerUpdate),
                Codec.DOUBLE.fieldOf("bloodloss_threshold_fraction").forGetter(BloodData::bloodlossThresholdFraction),
                DAMAGE_MAP_CODEC.fieldOf("bloodloss_damage_per_update").forGetter(BloodData::bloodlossDamagePerUpdate),
                DAMAGE_MAP_CODEC.fieldOf("bloodloss_heal_per_update").forGetter(BloodData::bloodlossHealPerUpdate),
                  Codec.BOOL.fieldOf("bloodloss_ignore_resistances").forGetter(BloodData::bloodlossIgnoreResistances),
                   Codec.DOUBLE.fieldOf("bleed_puddle_threshold").forGetter(BloodData::bleedPuddleThreshold)
         ).apply(instance, BloodData::new));

        public static final Codec<BloodData> CODEC = Codec.of(STRUCTURAL_CODEC, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<BloodData, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) return DataResult.error(() -> "blood must be a JSON object");
                    CharacterSchemaAudit.auditBlood(json.getAsJsonObject());
                    return STRUCTURAL_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            }
        });

        public BloodData {
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

    /** Optional per-character thermal exposure policy. currentKelvin is the initial body temperature. */
    public record ThermalData(double massKg, double specificHeatJoulesPerKgKelvin,
                               double atmosphereTransferEfficiency, double heatDamageThresholdKelvin,
                               double coldDamageThresholdKelvin, double currentKelvin,
                               double heatDamagePerSecond, double coldDamagePerSecond, double damageCap,
                               double normalBodyTemperatureKelvin, double metabolismHeatJoulesPerSecond,
                               double radiatedHeatJoulesPerSecond, double implicitHeatRegulationJoulesPerSecond,
                               double sweatHeatRegulationJoulesPerSecond, double shiveringHeatRegulationJoulesPerSecond,
                                double thermalRegulationThresholdKelvin,
                                double spaceHeatCapacityJoulesPerKelvin, double spaceHeatScale,
                                double spaceTemperatureKelvin) {
         // All fields are required doubles; the flat map avoids DFU's 16-argument builder ceiling.
         private static final Codec<ThermalData> STRUCTURAL_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                 .xmap(values -> new ThermalData(
                         values.get("mass_kg"), values.get("specific_heat_joules_per_kg_kelvin"),
                         values.get("atmosphere_transfer_efficiency"), values.get("heat_damage_threshold_kelvin"),
                         values.get("cold_damage_threshold_kelvin"), values.get("current_kelvin"),
                         values.get("heat_damage_per_second"), values.get("cold_damage_per_second"),
                         values.get("damage_cap"), values.get("normal_body_temperature_kelvin"),
                         values.get("metabolism_heat_joules_per_second"), values.get("radiated_heat_joules_per_second"),
                         values.get("implicit_heat_regulation_joules_per_second"),
                         values.get("sweat_heat_regulation_joules_per_second"),
                         values.get("shivering_heat_regulation_joules_per_second"),
                         values.get("thermal_regulation_threshold_kelvin"),
                         values.get("space_heat_capacity_joules_per_kelvin"), values.get("space_heat_scale"),
                         values.get("space_temperature_kelvin")), data -> Map.ofEntries(
                         Map.entry("mass_kg", data.massKg()),
                         Map.entry("specific_heat_joules_per_kg_kelvin", data.specificHeatJoulesPerKgKelvin()),
                         Map.entry("atmosphere_transfer_efficiency", data.atmosphereTransferEfficiency()),
                         Map.entry("heat_damage_threshold_kelvin", data.heatDamageThresholdKelvin()),
                         Map.entry("cold_damage_threshold_kelvin", data.coldDamageThresholdKelvin()),
                         Map.entry("current_kelvin", data.currentKelvin()),
                         Map.entry("heat_damage_per_second", data.heatDamagePerSecond()),
                         Map.entry("cold_damage_per_second", data.coldDamagePerSecond()),
                         Map.entry("damage_cap", data.damageCap()),
                         Map.entry("normal_body_temperature_kelvin", data.normalBodyTemperatureKelvin()),
                         Map.entry("metabolism_heat_joules_per_second", data.metabolismHeatJoulesPerSecond()),
                         Map.entry("radiated_heat_joules_per_second", data.radiatedHeatJoulesPerSecond()),
                         Map.entry("implicit_heat_regulation_joules_per_second", data.implicitHeatRegulationJoulesPerSecond()),
                         Map.entry("sweat_heat_regulation_joules_per_second", data.sweatHeatRegulationJoulesPerSecond()),
                         Map.entry("shivering_heat_regulation_joules_per_second", data.shiveringHeatRegulationJoulesPerSecond()),
                         Map.entry("thermal_regulation_threshold_kelvin", data.thermalRegulationThresholdKelvin()),
                         Map.entry("space_heat_capacity_joules_per_kelvin", data.spaceHeatCapacityJoulesPerKelvin()),
                         Map.entry("space_heat_scale", data.spaceHeatScale()),
                         Map.entry("space_temperature_kelvin", data.spaceTemperatureKelvin())));

        public static final Codec<ThermalData> CODEC = Codec.of(STRUCTURAL_CODEC, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<ThermalData, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) return DataResult.error(() -> "thermal must be a JSON object");
                    CharacterSchemaAudit.auditThermal(json.getAsJsonObject());
                    return STRUCTURAL_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            }
        });

        public ThermalData {
             // Fixture mass is source-derived (circle area times fixture density); no runtime SS14 physics fixture exists here.
            ThermalExposureMath.ThermalProfile profile = toProfile(massKg, specificHeatJoulesPerKgKelvin,
                    atmosphereTransferEfficiency, heatDamageThresholdKelvin, coldDamageThresholdKelvin,
                    heatDamagePerSecond, coldDamagePerSecond, damageCap);
            if (!Double.isFinite(currentKelvin) || currentKelvin <= 0.0
                    || currentKelvin <= coldDamageThresholdKelvin || currentKelvin >= heatDamageThresholdKelvin) {
                throw new IllegalArgumentException("currentKelvin must be finite and between thermal thresholds");
            }
             new com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalRegulatorMath.Policy(
                    normalBodyTemperatureKelvin, metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond,
                    implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond,
                     shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin);
             new ThermalExposureMath.VacuumPolicy(spaceHeatCapacityJoulesPerKelvin, spaceHeatScale,
                     spaceTemperatureKelvin);
            if (normalBodyTemperatureKelvin <= coldDamageThresholdKelvin
                    || normalBodyTemperatureKelvin >= heatDamageThresholdKelvin) {
                throw new IllegalArgumentException("normal body temperature must be between damage thresholds");
            }
        }

        public ThermalExposureMath.ThermalProfile toProfile() {
            return toProfile(massKg, specificHeatJoulesPerKgKelvin, atmosphereTransferEfficiency,
                    heatDamageThresholdKelvin, coldDamageThresholdKelvin, heatDamagePerSecond,
                    coldDamagePerSecond, damageCap);
        }

        public com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalRegulatorMath.Policy toRegulationPolicy() {
            return new com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalRegulatorMath.Policy(
                    normalBodyTemperatureKelvin, metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond,
                    implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond,
                    shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin);
        }

        public ThermalExposureMath.VacuumPolicy toVacuumPolicy() {
            return new ThermalExposureMath.VacuumPolicy(spaceHeatCapacityJoulesPerKelvin, spaceHeatScale,
                    spaceTemperatureKelvin);
        }

        private static ThermalExposureMath.ThermalProfile toProfile(double massKg, double specificHeat,
                double efficiency, double heatThreshold, double coldThreshold, double heatDamage,
                double coldDamage, double cap) {
            return new ThermalExposureMath.ThermalProfile(massKg, specificHeat, efficiency,
                    heatThreshold, coldThreshold, heatDamage, coldDamage, cap);
        }
    }

    /**
     * Generic grounded movement parameters; species capability policy remains data-driven.
     * Speech remains unmodeled in this character codec; movement data alone does not grant or deny speech.
     */
    public record MovementData(String mode, double acceleration, double walkSpeed, double sprintSpeed,
                               double groundFrictionWithInput, double groundFrictionWithoutInput,
                               double minimumFrictionSpeed) {
        private static final Codec<MovementData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("mode").forGetter(MovementData::mode),
                Codec.DOUBLE.fieldOf("acceleration").forGetter(MovementData::acceleration),
                Codec.DOUBLE.fieldOf("walk_speed").forGetter(MovementData::walkSpeed),
                Codec.DOUBLE.fieldOf("sprint_speed").forGetter(MovementData::sprintSpeed),
                Codec.DOUBLE.fieldOf("ground_friction_with_input").forGetter(MovementData::groundFrictionWithInput),
                Codec.DOUBLE.fieldOf("ground_friction_without_input").forGetter(MovementData::groundFrictionWithoutInput),
                Codec.DOUBLE.fieldOf("minimum_friction_speed").forGetter(MovementData::minimumFrictionSpeed)
        ).apply(instance, MovementData::new));

        public static final Codec<MovementData> CODEC = Codec.of(STRUCTURAL_CODEC, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<MovementData, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) return DataResult.error(() -> "movement must be a JSON object");
                    CharacterSchemaAudit.auditMovement(json.getAsJsonObject());
                    return STRUCTURAL_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            }
        });
    }

    /**
     * Typed target capability bundle; separate from source-side reagent SlipData. Entries are local policy,
     * not claims about upstream species components; the Pig prototype's inert/no-slip selection is provisional.
     */
    public record SlipTargetData(boolean canReceiveStun, boolean noSlip, boolean standingEligible,
                                 boolean proneEligible, List<ReactiveGroup> reactiveGroups,
                                 List<ReactiveMethod> reactiveMethods) {
        private static final Codec<SlipTargetData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.BOOL.fieldOf("can_receive_stun").forGetter(SlipTargetData::canReceiveStun),
                        Codec.BOOL.fieldOf("no_slip").forGetter(SlipTargetData::noSlip),
                        Codec.BOOL.fieldOf("standing_eligible").forGetter(SlipTargetData::standingEligible),
                        Codec.BOOL.fieldOf("prone_eligible").forGetter(SlipTargetData::proneEligible),
                        Codec.list(ReactiveGroup.CODEC).fieldOf("reactive_groups").forGetter(SlipTargetData::reactiveGroups),
                        Codec.list(ReactiveMethod.CODEC).fieldOf("reactive_methods").forGetter(SlipTargetData::reactiveMethods)
                ).apply(instance, SlipTargetData::new));

        private static final Decoder<SlipTargetData> STRICT_DECODER = new Decoder<>() {
            @Override
            public <T> DataResult<Pair<SlipTargetData, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) {
                        return DataResult.error(() -> "slip_data must be a JSON object");
                    }
                    CharacterSchemaAudit.auditSlipData(json.getAsJsonObject());
                    return STRUCTURAL_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            }
        };

        public static final Codec<SlipTargetData> CODEC = Codec.of(STRUCTURAL_CODEC, STRICT_DECODER);

        public SlipTargetData {
            Objects.requireNonNull(reactiveGroups, "reactiveGroups");
            Objects.requireNonNull(reactiveMethods, "reactiveMethods");
            reactiveGroups = distinctCopy(reactiveGroups, "reactiveGroups");
            reactiveMethods = distinctCopy(reactiveMethods, "reactiveMethods");
            if (reactiveGroups.isEmpty() != reactiveMethods.isEmpty()) {
                throw new IllegalArgumentException("reactive groups and methods must either both be empty or both be populated");
            }
        }

        private static <T> List<T> distinctCopy(List<T> values, String field) {
            List<T> copy = List.copyOf(values);
            if (new HashSet<>(copy).size() != copy.size()) {
                throw new IllegalArgumentException(field + " must not contain duplicates");
            }
            return copy;
        }
    }

    /** These are the inherited MobHuman target groups relevant to required Touch reactions. */
    public enum ReactiveGroup {
        FLAMMABLE("flammable"), EXTINGUISH("extinguish"), ACIDIC("acidic");

        public static final Codec<ReactiveGroup> CODEC = Codec.STRING.comapFlatMap(value -> {
            for (ReactiveGroup group : values()) {
                if (group.serialized.equals(value)) return DataResult.success(group);
            }
            return DataResult.error(() -> "unknown canonical reactive group '" + value + "'");
        }, ReactiveGroup::serialized);

        private final String serialized;
        ReactiveGroup(String serialized) { this.serialized = serialized; }
        public String serialized() { return serialized; }
    }

    /** Local supported reactive method; canonical spelling follows upstream `touch`. */
    public enum ReactiveMethod {
        TOUCH;

        public static final Codec<ReactiveMethod> CODEC = Codec.STRING.comapFlatMap(value ->
                "touch".equals(value) ? DataResult.success(TOUCH)
                        : DataResult.error(() -> "unknown canonical reactive method '" + value + "'"),
                ignored -> "touch");
    }
}
