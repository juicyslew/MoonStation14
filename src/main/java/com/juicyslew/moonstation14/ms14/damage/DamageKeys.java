package com.juicyslew.moonstation14.ms14.damage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The closed vocabulary used by the living-character damage ledger. */
public final class DamageKeys {
    public static final String BLUNT = "blunt";
    public static final String PIERCING = "piercing";
    public static final String SLASH = "slash";
    public static final String HEAT = "heat";
    public static final String COLD = "cold";
    public static final String SHOCK = "shock";
    public static final String ASPHYXIATION = "asphyxiation";
    public static final String BLOODLOSS = "bloodloss";
    public static final String CAUSTIC = "caustic";
    public static final String POISON = "poison";
    public static final String RADIATION = "radiation";
    public static final String CELLULAR = "cellular";
    public static final String HOLY = "holy";

    public static final List<String> ORDER = List.of(
            BLUNT, PIERCING, SLASH, HEAT, COLD, SHOCK, ASPHYXIATION,
            BLOODLOSS, CAUSTIC, POISON, RADIATION, CELLULAR, HOLY);
    public static final Set<String> ALL = Set.copyOf(ORDER);

    /** Group order is part of the even-healing contract. */
    public static final Map<String, List<String>> GROUPS;
    static {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("brute", List.of(BLUNT, PIERCING, SLASH));
        groups.put("burn", List.of(HEAT, COLD, SHOCK, CAUSTIC));
        groups.put("airloss", List.of(ASPHYXIATION, BLOODLOSS));
        groups.put("toxin", List.of(POISON, RADIATION));
        groups.put("genetic", List.of(CELLULAR));
        groups.put("metaphysical", List.of(HOLY));
        GROUPS = Collections.unmodifiableMap(groups);
    }

    private DamageKeys() {
    }

    public static void requireCanonical(String key) {
        if (key == null || !ALL.contains(key)) {
            throw new IllegalArgumentException("unknown canonical damage key: " + key);
        }
    }

    public static Map<String, Float> validateState(Map<String, Float> source) {
        if (source == null) {
            throw new NullPointerException("damage map");
        }
        Map<String, Float> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : source.entrySet()) {
            requireCanonical(entry.getKey());
            Float value = entry.getValue();
            if (value == null || !Float.isFinite(value) || value < 0f) {
                throw new IllegalArgumentException("damage values must be finite and nonnegative");
            }
            if (value > 0f) {
                clean.put(entry.getKey(), value);
            }
        }
        return Map.copyOf(clean);
    }

    public static Map<String, Float> validateDelta(Map<String, Float> source) {
        if (source == null) {
            throw new NullPointerException("damage delta");
        }
        Map<String, Float> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : source.entrySet()) {
            requireCanonical(entry.getKey());
            Float value = entry.getValue();
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("damage deltas must be finite");
            }
            if (value != 0f) {
                clean.put(entry.getKey(), value);
            }
        }
        return Map.copyOf(clean);
    }

    public static List<String> orderedPresent(Map<String, Float> values) {
        List<String> result = new ArrayList<>();
        for (String key : ORDER) {
            if (values.containsKey(key)) {
                result.add(key);
            }
        }
        return result;
    }
}
