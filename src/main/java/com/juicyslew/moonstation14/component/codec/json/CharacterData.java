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
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponent;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;
import com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent;
import com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent;
import com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.character.components.StomachPrototypeComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, data-only character policy. The catalog key supplies identity. */
public record CharacterData(List<ResourceLocation> hostEntityTypes,
                            List<CharacterComponent> components) {
    private static final Codec<CharacterData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(ResourceLocation.CODEC).optionalFieldOf("host_entity_types", List.of())
                            .forGetter(CharacterData::hostEntityTypes),
                    Codec.list(CharacterComponentRegistry.CODEC).optionalFieldOf("components", List.of()).forGetter(CharacterData::components))
                    .apply(instance, CharacterData::new));

    private static final Decoder<CharacterData> STRICT_DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<CharacterData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                // JsonOps conversion can dereference nested JsonNull before the schema audit runs.
                // Check optional policy blocks on raw JSON before JsonOps can dereference nested nulls.
                if (input instanceof JsonObject character) {
                    CharacterSchemaAudit.rejectLegacyMovement(character);
                    CharacterSchemaAudit.rejectLegacyMetabolizerTypes(character);
                    CharacterSchemaAudit.rejectLegacySlip(character);
                    CharacterSchemaAudit.rejectLegacyBlood(character);
                    CharacterSchemaAudit.rejectLegacyLungs(character);
                    CharacterSchemaAudit.rejectLegacyThermal(character);
                    CharacterSchemaAudit.auditComponentsIfPresent(character);
                    CharacterSchemaAudit.rejectLegacyHands(character);
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
        Objects.requireNonNull(hostEntityTypes, "hostEntityTypes");
        hostEntityTypes = List.copyOf(hostEntityTypes);
        components = List.copyOf(Objects.requireNonNull(components, "components"));
        if (components.size() > 32 || components.stream().map(CharacterComponent::type).distinct().count() != components.size())
            throw new IllegalArgumentException("components must contain at most 32 distinct types");
        if (components.stream().anyMatch(component -> !CharacterComponentRegistry.registered(component.type())))
            throw new IllegalArgumentException("components must use registered types");
        if (components.stream().anyMatch(StomachPrototypeComponent.class::isInstance)
                && components.stream().noneMatch(BloodstreamComponent.class::isInstance))
            throw new IllegalArgumentException("Stomach requires Bloodstream component");
        var temperature = components.stream().filter(TemperatureComponent.class::isInstance)
                .map(TemperatureComponent.class::cast).findFirst();
        var damage = components.stream().filter(TemperatureDamageComponent.class::isInstance)
                .map(TemperatureDamageComponent.class::cast).findFirst();
        var regulator = components.stream().filter(ThermalRegulatorComponent.class::isInstance)
                .map(ThermalRegulatorComponent.class::cast).findFirst();
        if (temperature.isEmpty() && (damage.isPresent() || regulator.isPresent()))
            throw new IllegalArgumentException("TemperatureDamage/ThermalRegulator requires Temperature component");
        if (damage.isPresent()) {
            double cold = damage.get().coldDamageThresholdKelvin();
            double heat = damage.get().heatDamageThresholdKelvin();
            if (temperature.get().currentKelvin() <= cold || temperature.get().currentKelvin() >= heat)
                throw new IllegalArgumentException("Temperature.current_kelvin must be between damage thresholds");
            if (regulator.isPresent() && (regulator.get().normalBodyTemperatureKelvin() <= cold
                    || regulator.get().normalBodyTemperatureKelvin() >= heat))
                throw new IllegalArgumentException("ThermalRegulator.normal_body_temperature_kelvin must be between damage thresholds");
        }
    }

    public CharacterData(List<ResourceLocation> hostEntityTypes) {
        this(hostEntityTypes, List.of());
    }

    public CharacterData() {
        this(List.of());
    }

    public <T extends CharacterComponent> Optional<T> component(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return components.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public Optional<BarotraumaComponent> barotrauma() { return component(BarotraumaComponent.class); }

}
