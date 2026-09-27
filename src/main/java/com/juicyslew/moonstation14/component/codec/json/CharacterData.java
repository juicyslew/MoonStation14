package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalExposureMath;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, data-only character policy. The catalog key supplies identity. */
public record CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                            List<ResourceLocation> hostEntityTypes, Optional<ThermalData> thermal) {
    private static final Codec<CharacterData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    SlipTargetData.CODEC.fieldOf("slip_data").forGetter(CharacterData::slipData),
                    MovementData.CODEC.optionalFieldOf("movement").forGetter(CharacterData::movement),
                    Codec.list(ResourceLocation.CODEC).optionalFieldOf("host_entity_types", List.of())
                            .forGetter(CharacterData::hostEntityTypes),
                    ThermalData.CODEC.optionalFieldOf("thermal").forGetter(CharacterData::thermal))
                    .apply(instance, CharacterData::new));

    private static final Decoder<CharacterData> STRICT_DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<CharacterData, T>> decode(DynamicOps<T> ops, T input) {
            try {
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
        hostEntityTypes = List.copyOf(hostEntityTypes);
    }

    /** Backward-compatible constructor for character policies predating thermal data. */
    public CharacterData(SlipTargetData slipData, Optional<MovementData> movement,
                         List<ResourceLocation> hostEntityTypes) {
        this(slipData, movement, hostEntityTypes, Optional.empty());
    }

    /** Backward-compatible slip-only prototype constructor. */
    public CharacterData(SlipTargetData slipData) {
        this(slipData, Optional.empty(), List.of(), Optional.empty());
    }

    /** Optional per-character thermal exposure policy. currentKelvin is the initial body temperature. */
    public record ThermalData(double massKg, double specificHeatJoulesPerKgKelvin,
                              double atmosphereTransferEfficiency, double heatDamageThresholdKelvin,
                              double coldDamageThresholdKelvin, double currentKelvin,
                              double heatDamagePerSecond, double coldDamagePerSecond, double damageCap) {
        private static final Codec<ThermalData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("mass_kg").forGetter(ThermalData::massKg),
                Codec.DOUBLE.fieldOf("specific_heat_joules_per_kg_kelvin").forGetter(ThermalData::specificHeatJoulesPerKgKelvin),
                Codec.DOUBLE.fieldOf("atmosphere_transfer_efficiency").forGetter(ThermalData::atmosphereTransferEfficiency),
                Codec.DOUBLE.fieldOf("heat_damage_threshold_kelvin").forGetter(ThermalData::heatDamageThresholdKelvin),
                Codec.DOUBLE.fieldOf("cold_damage_threshold_kelvin").forGetter(ThermalData::coldDamageThresholdKelvin),
                Codec.DOUBLE.fieldOf("current_kelvin").forGetter(ThermalData::currentKelvin),
                Codec.DOUBLE.fieldOf("heat_damage_per_second").forGetter(ThermalData::heatDamagePerSecond),
                Codec.DOUBLE.fieldOf("cold_damage_per_second").forGetter(ThermalData::coldDamagePerSecond),
                Codec.DOUBLE.fieldOf("damage_cap").forGetter(ThermalData::damageCap)
        ).apply(instance, ThermalData::new));

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
            // SS14 Physics.FixturesMass is not available in this Minecraft approximation; mass is explicit policy data.
            ThermalExposureMath.ThermalProfile profile = toProfile(massKg, specificHeatJoulesPerKgKelvin,
                    atmosphereTransferEfficiency, heatDamageThresholdKelvin, coldDamageThresholdKelvin,
                    heatDamagePerSecond, coldDamagePerSecond, damageCap);
            if (!Double.isFinite(currentKelvin) || currentKelvin <= 0.0
                    || currentKelvin <= coldDamageThresholdKelvin || currentKelvin >= heatDamageThresholdKelvin) {
                throw new IllegalArgumentException("currentKelvin must be finite and between thermal thresholds");
            }
        }

        public ThermalExposureMath.ThermalProfile toProfile() {
            return toProfile(massKg, specificHeatJoulesPerKgKelvin, atmosphereTransferEfficiency,
                    heatDamageThresholdKelvin, coldDamageThresholdKelvin, heatDamagePerSecond,
                    coldDamagePerSecond, damageCap);
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
     * Speech/hands remain unmodeled in this character codec until generic systems implement and enforce those capabilities;
     * movement data alone does not grant or deny speech.
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
