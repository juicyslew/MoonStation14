package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalExposureMath;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalRegulatorMath;

/** Immutable ThermalRegulator mob prototype component. */
public record ThermalRegulatorComponent(double normalBodyTemperatureKelvin, double metabolismHeatJoulesPerSecond, double radiatedHeatJoulesPerSecond, double implicitHeatRegulationJoulesPerSecond, double sweatHeatRegulationJoulesPerSecond, double shiveringHeatRegulationJoulesPerSecond, double thermalRegulationThresholdKelvin) implements CharacterComponent {
    public static final String TYPE = "ThermalRegulator";
    @Override public String type() { return TYPE; }
    public static final Codec<ThermalRegulatorComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("type").forGetter(ThermalRegulatorComponent::type),
        Codec.DOUBLE.fieldOf("normal_body_temperature_kelvin").forGetter(ThermalRegulatorComponent::normalBodyTemperatureKelvin),
        Codec.DOUBLE.fieldOf("metabolism_heat_joules_per_second").forGetter(ThermalRegulatorComponent::metabolismHeatJoulesPerSecond),
        Codec.DOUBLE.fieldOf("radiated_heat_joules_per_second").forGetter(ThermalRegulatorComponent::radiatedHeatJoulesPerSecond),
        Codec.DOUBLE.fieldOf("implicit_heat_regulation_joules_per_second").forGetter(ThermalRegulatorComponent::implicitHeatRegulationJoulesPerSecond),
        Codec.DOUBLE.fieldOf("sweat_heat_regulation_joules_per_second").forGetter(ThermalRegulatorComponent::sweatHeatRegulationJoulesPerSecond),
        Codec.DOUBLE.fieldOf("shivering_heat_regulation_joules_per_second").forGetter(ThermalRegulatorComponent::shiveringHeatRegulationJoulesPerSecond),
        Codec.DOUBLE.fieldOf("thermal_regulation_threshold_kelvin").forGetter(ThermalRegulatorComponent::thermalRegulationThresholdKelvin)
    ).apply(instance, (type, normalBodyTemperatureKelvin, metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond, implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond, shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("expected ThermalRegulator component");
        return new ThermalRegulatorComponent(normalBodyTemperatureKelvin, metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond, implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond, shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin);
    }));
    public ThermalRegulatorComponent {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("normal_body_temperature_kelvin", normalBodyTemperatureKelvin);
        json.addProperty("metabolism_heat_joules_per_second", metabolismHeatJoulesPerSecond);
        json.addProperty("radiated_heat_joules_per_second", radiatedHeatJoulesPerSecond);
        json.addProperty("implicit_heat_regulation_joules_per_second", implicitHeatRegulationJoulesPerSecond);
        json.addProperty("sweat_heat_regulation_joules_per_second", sweatHeatRegulationJoulesPerSecond);
        json.addProperty("shivering_heat_regulation_joules_per_second", shiveringHeatRegulationJoulesPerSecond);
        json.addProperty("thermal_regulation_threshold_kelvin", thermalRegulationThresholdKelvin);
        com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(json, "$.components[0]");
        toPolicy(normalBodyTemperatureKelvin, metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond, implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond, shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin);
    }
    private static ThermalRegulatorMath.Policy toPolicy(double normalBodyTemperatureKelvin, double metabolismHeatJoulesPerSecond, double radiatedHeatJoulesPerSecond, double implicitHeatRegulationJoulesPerSecond, double sweatHeatRegulationJoulesPerSecond, double shiveringHeatRegulationJoulesPerSecond, double thermalRegulationThresholdKelvin) { return new ThermalRegulatorMath.Policy(normalBodyTemperatureKelvin, metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond, implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond, shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin); }
    public ThermalRegulatorMath.Policy toPolicy() { return toPolicy(normalBodyTemperatureKelvin, metabolismHeatJoulesPerSecond, radiatedHeatJoulesPerSecond, implicitHeatRegulationJoulesPerSecond, sweatHeatRegulationJoulesPerSecond, shiveringHeatRegulationJoulesPerSecond, thermalRegulationThresholdKelvin); }
}
