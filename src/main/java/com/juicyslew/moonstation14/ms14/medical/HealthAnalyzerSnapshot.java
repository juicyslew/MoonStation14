package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.ms14.damage.DamageKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Immutable, server-authored reading set sent to an analyzer window. */
public record HealthAnalyzerSnapshot(UUID scannerId, long epoch, UUID actorId,
                                     int targetEntityId, UUID targetId, long frame,
                                     String name, String species, String state,
                                     boolean active, boolean inRange,
                                     Optional<Float> temperatureKelvin,
                                     Optional<Float> usableBloodFraction,
                                     boolean bleeding, boolean unrevivable,
                                     float totalDamage, List<GroupReading> groups,
                                     Map<String, Float> damage) {
    public HealthAnalyzerSnapshot {
        if (scannerId == null || actorId == null || targetId == null || epoch < 0 || frame < 0
                || targetEntityId < 0 || name == null || species == null || state == null
                || !Float.isFinite(totalDamage) || totalDamage < 0)
            throw new IllegalArgumentException("invalid analyzer snapshot");
        temperatureKelvin = finiteOptional(temperatureKelvin, true);
        usableBloodFraction = finiteOptional(usableBloodFraction, false);
        damage = DamageKeys.validateState(damage);
        var ordered = new ArrayList<>(groups);
        ordered.sort(Comparator.comparingDouble(GroupReading::total).reversed().thenComparing(GroupReading::name));
        groups = List.copyOf(ordered);
    }

    public static HealthAnalyzerSnapshot readings(UUID scannerId, long epoch, UUID actorId,
            int targetEntityId, UUID targetId, long frame, String name, String species, String state,
            boolean active, boolean inRange, Optional<Float> temperatureKelvin,
            Optional<Float> usableBloodFraction, boolean bleeding, boolean unrevivable,
            Map<String, Float> rawDamage) {
        Map<String, Float> damage = DamageKeys.validateState(rawDamage);
        List<GroupReading> groups = new ArrayList<>();
        for (var group : DamageKeys.GROUPS.entrySet()) {
            float total = 0;
            Map<String, Float> members = new LinkedHashMap<>();
            for (String key : group.getValue()) {
                float value = damage.getOrDefault(key, 0f);
                if (value > 0) { members.put(key, value); total += value; }
            }
            if (total > 0) groups.add(new GroupReading(group.getKey(), total, members));
        }
        return new HealthAnalyzerSnapshot(scannerId, epoch, actorId, targetEntityId, targetId, frame,
                name, species, state, active, inRange, temperatureKelvin, usableBloodFraction,
                bleeding, unrevivable, damage.values().stream().mapToDouble(Float::doubleValue).sum() > Float.MAX_VALUE
                ? Float.MAX_VALUE : (float) damage.values().stream().mapToDouble(Float::doubleValue).sum(), groups, damage);
    }

    private static Optional<Float> finiteOptional(Optional<Float> value, boolean positive) {
        if (value == null || value.isEmpty()) return Optional.empty();
        float number = value.get();
        if (!Float.isFinite(number) || (positive && number <= 0) || (!positive && number < 0))
            return Optional.empty();
        return value;
    }

    public record GroupReading(String name, float total, Map<String, Float> damage) {
        public GroupReading {
            if (name == null || !Float.isFinite(total) || total <= 0) throw new IllegalArgumentException("invalid damage group");
            damage = DamageKeys.validateState(damage);
        }
    }
}

