package com.juicyslew.moonstation14.ms14.damage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure arithmetic for the authoritative typed-damage ledger. */
public final class DamageReducer {
    private DamageReducer() {
    }

    public static Map<String, Float> applyDelta(Map<String, Float> current,
                                                 Map<String, Float> delta) {
        Map<String, Float> state = DamageKeys.validateState(current);
        Map<String, Float> changes = DamageKeys.validateDelta(delta);
        if (changes.isEmpty()) {
            return state;
        }
        Map<String, Float> result = new LinkedHashMap<>(state);
        for (String key : DamageKeys.ORDER) {
            Float change = changes.get(key);
            if (change == null) {
                continue;
            }
            float next = result.getOrDefault(key, 0f) + change;
            if (!Float.isFinite(next)) {
                throw new IllegalArgumentException("damage result must be finite");
            }
            if (next <= 0f) {
                result.remove(key);
            } else {
                result.put(key, next);
            }
        }
        return DamageKeys.validateState(result);
    }

    /**
     * Applies the final post-mitigation positive amount while retaining the
     * proportions of the original typed request, then applies unmodified
     * negative deltas in the same logical mutation.
     */
    public static Map<String, Float> applyMitigated(Map<String, Float> current,
                                                     Map<String, Float> positiveRequest,
                                                     float finalVanillaDamage,
                                                     Map<String, Float> negativeDelta) {
        Map<String, Float> positive = DamageKeys.validateDelta(positiveRequest);
        Map<String, Float> negative = DamageKeys.validateDelta(negativeDelta);
        Map<String, Float> positiveOnly = new LinkedHashMap<>();
        for (String key : DamageKeys.ORDER) {
            float amount = positive.getOrDefault(key, 0f);
            if (amount > 0f) {
                positiveOnly.put(key, amount);
            }
        }
        Map<String, Float> allocation = proportionalAllocation(positiveOnly, finalVanillaDamage * 5f);
        Map<String, Float> changes = new LinkedHashMap<>(allocation);
        for (Map.Entry<String, Float> entry : negative.entrySet()) {
            if (entry.getValue() < 0f) {
                changes.merge(entry.getKey(), entry.getValue(), Float::sum);
            }
        }
        return applyDelta(current, changes);
    }

    /** Allocates a nonnegative total in canonical key order. */
    public static Map<String, Float> proportionalAllocation(Map<String, Float> requested,
                                                              float total) {
        Map<String, Float> values = DamageKeys.validateState(requested);
        if (!Float.isFinite(total) || total < 0f) {
            throw new IllegalArgumentException("allocation total must be finite and nonnegative");
        }
        double requestedTotal = 0d;
        for (String key : DamageKeys.ORDER) {
            requestedTotal += values.getOrDefault(key, 0f);
        }
        Map<String, Float> result = new LinkedHashMap<>();
        if (requestedTotal == 0d || total == 0f) {
            return result;
        }
        float remaining = total;
        String last = null;
        for (String key : DamageKeys.ORDER) {
            float request = values.getOrDefault(key, 0f);
            if (request <= 0f) {
                continue;
            }
            last = key;
            float allocation = (float) (total * (request / requestedTotal));
            allocation = Math.max(0f, Math.min(allocation, remaining));
            result.put(key, allocation);
            remaining -= allocation;
        }
        // Give rounding residue to the last canonical member, preserving the
        // exact aggregate amount whenever it is representable.
        if (last != null && remaining != 0f) {
            result.put(last, result.get(last) + remaining);
        }
        return DamageKeys.validateState(result);
    }

    /** Heals a group evenly, redistributing shares from exhausted members. */
    public static Map<String, Float> healEvenly(Map<String, Float> current,
                                                 List<String> group,
                                                 float requestedHealing) {
        Map<String, Float> state = new LinkedHashMap<>(DamageKeys.validateState(current));
        if (!Float.isFinite(requestedHealing) || requestedHealing < 0f) {
            throw new IllegalArgumentException("healing must be finite and nonnegative");
        }
        if (requestedHealing == 0f) {
            return Map.copyOf(state);
        }
        List<String> active = DamageKeys.ORDER.stream()
                .filter(group::contains)
                .filter(key -> state.getOrDefault(key, 0f) > 0f)
                .toList();
        float availableTotal = 0f;
        for (String key : active) {
            availableTotal += state.get(key);
        }
        float remaining = Math.min(requestedHealing, availableTotal);
        while (!active.isEmpty() && remaining > 0f) {
            float share = remaining / active.size();
            java.util.ArrayList<String> exhausted = new java.util.ArrayList<>();
            for (String key : active) {
                if (state.get(key) <= share) {
                    exhausted.add(key);
                }
            }
            if (!exhausted.isEmpty()) {
                for (String key : exhausted) {
                    remaining -= state.get(key);
                    state.remove(key);
                }
                active = active.stream().filter(key -> !exhausted.contains(key)).toList();
                continue;
            }

            float distributed = 0f;
            for (int index = 0; index < active.size(); index++) {
                String key = active.get(index);
                float available = state.getOrDefault(key, 0f);
                // The final canonical active key receives the residue left by
                // float division, making the aggregate healing deterministic.
                float healed = index == active.size() - 1
                        ? Math.min(available, remaining - distributed)
                        : Math.min(available, share);
                float next = available - healed;
                state.put(key, next);
                distributed += healed;
            }
            remaining -= distributed;
            break;
        }
        return DamageKeys.validateState(state);
    }

    /** Processes healing groups in the stable group order. */
    public static Map<String, Float> healGroups(Map<String, Float> current,
                                                 Map<String, Float> scaledGroupDeltas) {
        Map<String, Float> state = DamageKeys.validateState(current);
        if (scaledGroupDeltas == null) {
            throw new NullPointerException("group deltas");
        }
        for (String groupName : DamageKeys.GROUPS.keySet()) {
            Float delta = scaledGroupDeltas.get(groupName);
            if (delta != null && Float.isFinite(delta) && delta < 0f) {
                state = healEvenly(state, DamageKeys.GROUPS.get(groupName), -delta);
            } else if (delta != null && !Float.isFinite(delta)) {
                throw new IllegalArgumentException("group deltas must be finite");
            }
        }
        return state;
    }

    public static float total(Map<String, Float> state) {
        float total = 0f;
        Map<String, Float> validated = DamageKeys.validateState(state);
        for (String key : DamageKeys.ORDER) {
            total += validated.getOrDefault(key, 0f);
        }
        if (!Float.isFinite(total)) {
            throw new IllegalArgumentException("damage total must be finite");
        }
        return total;
    }
}
