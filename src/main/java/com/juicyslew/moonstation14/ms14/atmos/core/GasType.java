package com.juicyslew.moonstation14.ms14.atmos.core;

import java.util.Locale;

/** Gases supported by the atmosphere core, with heat capacities in J/(mol K). */
public enum GasType {
    OXYGEN("oxygen", 20.0),
    NITROGEN("nitrogen", 30.0),
    CARBON_DIOXIDE("carbon_dioxide", 30.0),
    PLASMA("plasma", 200.0),
    TRITIUM("tritium", 10.0),
    WATER_VAPOR("water_vapor", 40.0),
    AMMONIA("ammonia", 20.0),
    NITROUS_OXIDE("nitrous_oxide", 40.0),
    FREZON("frezon", 600.0);

    private final String id;
    private final double molarHeatCapacity;

    GasType(String id, double molarHeatCapacity) {
        this.id = id;
        this.molarHeatCapacity = molarHeatCapacity;
    }

    public String id() {
        return id;
    }

    public double molarHeatCapacity() {
        return molarHeatCapacity;
    }

    /** Looks up the stable serialized id, never an enum ordinal. */
    public static GasType fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Gas id cannot be null");
        }
        for (GasType type : values()) {
            if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown gas id: " + id);
    }
}
