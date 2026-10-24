package com.juicyslew.moonstation14.ms14.damage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure item-damage calculations shared by authoritative world consumers. */
public final class ItemDamageKernel {
    private ItemDamageKernel() { }

    public record Impact(ItemDamageState state, boolean applied, boolean destructionDue) { }

    public static Map<String, Float> filterAllowed(Map<String, Float> raw, Set<String> allowedGroups) {
        return filterAllowed(raw, allowedGroups, Set.of());
    }

    public static Map<String, Float> filterAllowed(Map<String, Float> raw, Set<String> allowedGroups, Set<String> allowedTypes) {
        Map<String, Float> validated = DamageKeys.validateDelta(raw);
        Map<String, Float> result = new LinkedHashMap<>();
        for (var entry : validated.entrySet()) {
            boolean allowed = allowedGroups.stream().filter(DamageKeys.GROUPS::containsKey)
                    .anyMatch(group -> DamageKeys.GROUPS.get(group).contains(entry.getKey()));
            if (allowed || allowedTypes.contains(entry.getKey())) result.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    public static Impact highSpeedImpact(ItemDamageState current, Map<String, Float> damage,
                                         Set<String> allowedGroups, Set<String> allowedTypes, float minimumSpeedPerSecond,
                                         float factor, int cooldownTicks, long gameTick,
                                         double ownHorizontalSpeedBlocksPerTick, boolean horizontalHardContact,
                                         float destructionThreshold) {
        return highSpeedImpact(current, damage, allowedGroups, allowedTypes, minimumSpeedPerSecond, factor, cooldownTicks, gameTick, ownHorizontalSpeedBlocksPerTick, horizontalHardContact, destructionThreshold, DamageModifierData.EMPTY);
    }
    public static Impact highSpeedImpact(ItemDamageState current, Map<String, Float> damage, Set<String> allowedGroups, Set<String> allowedTypes, float minimumSpeedPerSecond, float factor, int cooldownTicks, long gameTick, double ownHorizontalSpeedBlocksPerTick, boolean horizontalHardContact, float destructionThreshold, DamageModifierData modifiers) {
        return highSpeedImpactCanonical(current, damage, allowedGroups, allowedTypes, minimumSpeedPerSecond, factor,
                cooldownTicks, gameTick, ownHorizontalSpeedBlocksPerTick, horizontalHardContact, destructionThreshold, modifiers);
    }

    private static Impact highSpeedImpact(ItemDamageState current, Map<String, Float> damage,
                                         Set<String> allowedGroups, Set<String> allowedTypes, float minimumSpeedPerSecond,
                                         float factor, int cooldownTicks, long gameTick, double ownHorizontalSpeedBlocksPerTick,
                                         boolean horizontalHardContact, float destructionThreshold, boolean extended) {
        return highSpeedImpactCanonical(current, damage, allowedGroups, allowedTypes, minimumSpeedPerSecond, factor,
                cooldownTicks, gameTick, ownHorizontalSpeedBlocksPerTick, horizontalHardContact, destructionThreshold, DamageModifierData.EMPTY);
    }

    private static Impact highSpeedImpactCanonical(ItemDamageState current, Map<String, Float> damage, Set<String> allowedGroups,
                                                   Set<String> allowedTypes, float minimumSpeedPerSecond, float factor, int cooldownTicks,
                                                   long gameTick, double ownHorizontalSpeedBlocksPerTick, boolean horizontalHardContact,
                                                   float destructionThreshold, DamageModifierData modifiers) {
        if (current == null || damage == null || allowedGroups == null || allowedTypes == null || modifiers == null
                || !Float.isFinite(minimumSpeedPerSecond) || minimumSpeedPerSecond <= 0 || !Float.isFinite(factor) || factor < 0
                || cooldownTicks < 0 || gameTick < 0 || !Double.isFinite(ownHorizontalSpeedBlocksPerTick)
                || !Float.isFinite(destructionThreshold) || destructionThreshold <= 0)
            return new Impact(current, false, current != null && DamageReducer.total(current.damage()) >= destructionThreshold);
        float speedPerSecond = (float) (ownHorizontalSpeedBlocksPerTick * 20.0);
        if (!Float.isFinite(speedPerSecond)) return new Impact(current, false, DamageReducer.total(current.damage()) >= destructionThreshold);
        if (!horizontalHardContact || speedPerSecond < minimumSpeedPerSecond || gameTick < current.nextImpactDeadline())
            return new Impact(current, false, DamageReducer.total(current.damage()) >= destructionThreshold);
        float scale = factor * speedPerSecond / minimumSpeedPerSecond;
        if (!Float.isFinite(scale)) return new Impact(current, false, DamageReducer.total(current.damage()) >= destructionThreshold);
        Map<String, Float> scaled = new LinkedHashMap<>();
        for (var entry : filterAllowed(damage, allowedGroups, allowedTypes).entrySet()) {
            String key = entry.getKey(); float value = entry.getValue();
            float amount = value * scale;
            if (!Float.isFinite(amount)) return new Impact(current, false, DamageReducer.total(current.damage()) >= destructionThreshold);
            scaled.put(key, amount);
        }
        Map<String, Float> modified = DamageModifierData.apply(scaled, modifiers);
        if (modified.isEmpty()) return new Impact(current, false, DamageReducer.total(current.damage()) >= destructionThreshold);
        ItemDamageState next = new ItemDamageState(DamageReducer.applyDelta(current.damage(), modified), Math.addExact(gameTick, cooldownTicks));
        return new Impact(next, true, DamageReducer.total(next.damage()) >= destructionThreshold);
    }

    public static Impact highSpeedImpact(ItemDamageState current, Map<String, Float> damage,
                                         Set<String> allowedGroups, float minimumSpeedPerSecond,
                                         float factor, int cooldownTicks, long gameTick,
                                         double ownHorizontalSpeedBlocksPerTick, boolean horizontalHardContact,
                                         float destructionThreshold) {
        return highSpeedImpactCanonical(current, damage, allowedGroups, Set.of(), minimumSpeedPerSecond, factor,
                cooldownTicks, gameTick, ownHorizontalSpeedBlocksPerTick, horizontalHardContact, destructionThreshold, DamageModifierData.EMPTY);
    }

    public static ItemDamageState apply(ItemDamageState current, Map<String, Float> raw,
                                        Set<String> allowedGroups) {
        return apply(current, raw, allowedGroups, Set.of());
    }
    public static ItemDamageState apply(ItemDamageState current, Map<String, Float> raw, Set<String> allowedGroups, Set<String> allowedTypes) {
        return apply(current, raw, allowedGroups, allowedTypes, DamageModifierData.EMPTY);
    }
    public static ItemDamageState apply(ItemDamageState current, Map<String, Float> raw, Set<String> allowedGroups, Set<String> allowedTypes, DamageModifierData modifiers) {
        return new ItemDamageState(DamageReducer.applyDelta(current.damage(), filterAllowed(DamageModifierData.apply(raw, modifiers), allowedGroups, allowedTypes)),
                current.nextImpactDeadline());
    }

    /** True only when validation/filtering leaves at least one positive applicable delta. */
    public static boolean hasApplicableDamage(Map<String, Float> raw, Set<String> allowedGroups) {
        return hasApplicableDamage(raw, allowedGroups, Set.of());
    }
    public static boolean hasApplicableDamage(Map<String, Float> raw, Set<String> allowedGroups, Set<String> allowedTypes) {
        return filterAllowed(raw, allowedGroups, allowedTypes).values().stream().anyMatch(amount -> amount > 0f);
    }
    public static boolean hasApplicableDamage(Map<String, Float> raw, Set<String> groups, Set<String> types, DamageModifierData modifiers) {
        return filterAllowed(DamageModifierData.apply(raw, modifiers), groups, types).values().stream().anyMatch(v -> v > 0f);
    }
}
