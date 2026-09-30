package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalExposureMath;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalRegulatorMath;

/** Immutable Temperature mob prototype component. */
public record TemperatureComponent(double massKg, double specificHeatJoulesPerKgKelvin, double atmosphereTransferEfficiency, double currentKelvin, double spaceHeatCapacityJoulesPerKelvin, double spaceHeatScale, double spaceTemperatureKelvin) implements CharacterComponent {
    public static final String TYPE = "Temperature";
    @Override public String type() { return TYPE; }
    public static final Codec<TemperatureComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("type").forGetter(TemperatureComponent::type),
        Codec.DOUBLE.fieldOf("mass_kg").forGetter(TemperatureComponent::massKg),
        Codec.DOUBLE.fieldOf("specific_heat_joules_per_kg_kelvin").forGetter(TemperatureComponent::specificHeatJoulesPerKgKelvin),
        Codec.DOUBLE.fieldOf("atmosphere_transfer_efficiency").forGetter(TemperatureComponent::atmosphereTransferEfficiency),
        Codec.DOUBLE.fieldOf("current_kelvin").forGetter(TemperatureComponent::currentKelvin),
        Codec.DOUBLE.fieldOf("space_heat_capacity_joules_per_kelvin").forGetter(TemperatureComponent::spaceHeatCapacityJoulesPerKelvin),
        Codec.DOUBLE.fieldOf("space_heat_scale").forGetter(TemperatureComponent::spaceHeatScale),
        Codec.DOUBLE.fieldOf("space_temperature_kelvin").forGetter(TemperatureComponent::spaceTemperatureKelvin)
    ).apply(instance, (type, massKg, specificHeatJoulesPerKgKelvin, atmosphereTransferEfficiency, currentKelvin, spaceHeatCapacityJoulesPerKelvin, spaceHeatScale, spaceTemperatureKelvin) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("expected Temperature component");
        return new TemperatureComponent(massKg, specificHeatJoulesPerKgKelvin, atmosphereTransferEfficiency, currentKelvin, spaceHeatCapacityJoulesPerKelvin, spaceHeatScale, spaceTemperatureKelvin);
    }));
    public TemperatureComponent {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("mass_kg", massKg);
        json.addProperty("specific_heat_joules_per_kg_kelvin", specificHeatJoulesPerKgKelvin);
        json.addProperty("atmosphere_transfer_efficiency", atmosphereTransferEfficiency);
        json.addProperty("current_kelvin", currentKelvin);
        json.addProperty("space_heat_capacity_joules_per_kelvin", spaceHeatCapacityJoulesPerKelvin);
        json.addProperty("space_heat_scale", spaceHeatScale);
        json.addProperty("space_temperature_kelvin", spaceTemperatureKelvin);
        com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(json, "$.components[0]");
        new ThermalExposureMath.TemperatureProfile(massKg, specificHeatJoulesPerKgKelvin, atmosphereTransferEfficiency);
        if (!Double.isFinite(currentKelvin) || currentKelvin <= 0) throw new IllegalArgumentException("invalid currentKelvin");
        new ThermalExposureMath.VacuumPolicy(spaceHeatCapacityJoulesPerKelvin, spaceHeatScale, spaceTemperatureKelvin);
    }
    public ThermalExposureMath.TemperatureProfile toProfile() { return new ThermalExposureMath.TemperatureProfile(massKg, specificHeatJoulesPerKgKelvin, atmosphereTransferEfficiency); }
    public ThermalExposureMath.VacuumPolicy toVacuumPolicy() { return new ThermalExposureMath.VacuumPolicy(spaceHeatCapacityJoulesPerKelvin, spaceHeatScale, spaceTemperatureKelvin); }
}
