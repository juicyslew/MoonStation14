package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalExposureMath;
import com.juicyslew.moonstation14.ms14.atmos.exposure.ThermalRegulatorMath;

/** Immutable TemperatureDamage mob prototype component. */
public record TemperatureDamageComponent(double heatDamageThresholdKelvin, double coldDamageThresholdKelvin, double heatDamagePerSecond, double coldDamagePerSecond, double damageCap) implements CharacterComponent {
    public static final String TYPE = "TemperatureDamage";
    @Override public String type() { return TYPE; }
    public static final Codec<TemperatureDamageComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("type").forGetter(TemperatureDamageComponent::type),
        Codec.DOUBLE.fieldOf("heat_damage_threshold_kelvin").forGetter(TemperatureDamageComponent::heatDamageThresholdKelvin),
        Codec.DOUBLE.fieldOf("cold_damage_threshold_kelvin").forGetter(TemperatureDamageComponent::coldDamageThresholdKelvin),
        Codec.DOUBLE.fieldOf("heat_damage_per_second").forGetter(TemperatureDamageComponent::heatDamagePerSecond),
        Codec.DOUBLE.fieldOf("cold_damage_per_second").forGetter(TemperatureDamageComponent::coldDamagePerSecond),
        Codec.DOUBLE.fieldOf("damage_cap").forGetter(TemperatureDamageComponent::damageCap)
    ).apply(instance, (type, heatDamageThresholdKelvin, coldDamageThresholdKelvin, heatDamagePerSecond, coldDamagePerSecond, damageCap) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("expected TemperatureDamage component");
        return new TemperatureDamageComponent(heatDamageThresholdKelvin, coldDamageThresholdKelvin, heatDamagePerSecond, coldDamagePerSecond, damageCap);
    }));
    public TemperatureDamageComponent {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("heat_damage_threshold_kelvin", heatDamageThresholdKelvin);
        json.addProperty("cold_damage_threshold_kelvin", coldDamageThresholdKelvin);
        json.addProperty("heat_damage_per_second", heatDamagePerSecond);
        json.addProperty("cold_damage_per_second", coldDamagePerSecond);
        json.addProperty("damage_cap", damageCap);
        com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(json, "$.components[0]");
        new ThermalExposureMath.DamageProfile(heatDamageThresholdKelvin, coldDamageThresholdKelvin, heatDamagePerSecond, coldDamagePerSecond, damageCap);
    }
    public ThermalExposureMath.DamageProfile toProfile() { return new ThermalExposureMath.DamageProfile(heatDamageThresholdKelvin, coldDamageThresholdKelvin, heatDamagePerSecond, coldDamagePerSecond, damageCap); }
}
