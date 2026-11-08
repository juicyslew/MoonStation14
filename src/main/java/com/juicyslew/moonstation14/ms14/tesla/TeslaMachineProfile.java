package com.juicyslew.moonstation14.ms14.tesla;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent;
import java.util.List;
import java.util.Optional;

/** Gameplay-only machine settings; generator commissioning remains owned by the singularity generator path. */
public record TeslaMachineProfile(String kind, double capacityJoules, double chargePerStrikeJoules,
        double supplyWatts, double rampWattsPerSecond, LightningTargetComponent target, double flashSeconds,
        int destructionThreshold, int salvageMin, int salvageMax, Repair repair, List<String> examineDamageMessages) {
    public record Repair(double durationSeconds, int fuelCost, String quality) { }
    public static final Codec<Repair> REPAIR_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("durationSeconds").forGetter(Repair::durationSeconds),
            Codec.INT.fieldOf("fuelCost").forGetter(Repair::fuelCost),
            Codec.STRING.fieldOf("quality").forGetter(Repair::quality)).apply(i, Repair::new));
    public static final Codec<TeslaMachineProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(TeslaMachineProfile::kind), Codec.DOUBLE.optionalFieldOf("capacityJoules",0d).forGetter(TeslaMachineProfile::capacityJoules),
            Codec.DOUBLE.optionalFieldOf("chargePerStrikeJoules",0d).forGetter(TeslaMachineProfile::chargePerStrikeJoules), Codec.DOUBLE.optionalFieldOf("supplyWatts",0d).forGetter(TeslaMachineProfile::supplyWatts),
            Codec.DOUBLE.optionalFieldOf("rampWattsPerSecond",0d).forGetter(TeslaMachineProfile::rampWattsPerSecond), LightningTargetComponent.CODEC.optionalFieldOf("target").forGetter(p -> java.util.Optional.ofNullable(p.target())),
            Codec.DOUBLE.optionalFieldOf("flashSeconds",4d).forGetter(TeslaMachineProfile::flashSeconds),
            Codec.INT.optionalFieldOf("destructionThreshold",0).forGetter(TeslaMachineProfile::destructionThreshold),
            Codec.INT.optionalFieldOf("salvageMin",0).forGetter(TeslaMachineProfile::salvageMin),
            Codec.INT.optionalFieldOf("salvageMax",0).forGetter(TeslaMachineProfile::salvageMax),
            REPAIR_CODEC.optionalFieldOf("repair").forGetter(p -> Optional.ofNullable(p.repair())),
            Codec.STRING.listOf().optionalFieldOf("examineDamageMessages").forGetter(p -> p.examineDamageMessages().isEmpty() ? Optional.empty() : Optional.of(p.examineDamageMessages()))
    ).apply(i, (kind, capacity, charge, supply, ramp, target, flash, threshold, salvageMin, salvageMax, repair, messages) ->
            new TeslaMachineProfile(kind, capacity, charge, supply, ramp, target.orElse(null), flash, threshold,
                    salvageMin, salvageMax, repair.orElse(null), messages.orElse(List.of()))));
    public void validate() {
        if (kind == null || capacityJoules < 0 || chargePerStrikeJoules < 0 || supplyWatts < 0 || rampWattsPerSecond < 0 || flashSeconds < 0 || destructionThreshold < 0 || salvageMin < 0 || salvageMax < salvageMin || repair != null && (!Double.isFinite(repair.durationSeconds()) || repair.durationSeconds() <= 0 || repair.fuelCost() < 0 || repair.quality() == null) || target != null && (target.priority() < 0 || target.probability() < 0 || target.probability() > 1 || target.resistance() < 0 || target.structuralDamage() < 0))
            throw new IllegalArgumentException("invalid Tesla machine profile");
        if (examineDamageMessages != null && !examineDamageMessages.isEmpty() && examineDamageMessages.size() != 6)
            throw new IllegalArgumentException("examineDamageMessages must contain exactly six bands");
    }
}
