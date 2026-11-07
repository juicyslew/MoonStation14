package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.teg.TegSupplyPolicy;

/** Pure presentation selection. This never owns or changes machine operation. */
public final class AtmosMachineAppearance {
    private AtmosMachineAppearance() { }

    public static String project(AtmosMachineProfile profile, boolean powered, boolean enabled,
                                 boolean blocked, TegSupplyPolicy.State ramp) {
        String base = profile.visual() == null ? "" : profile.visual().state();
        return switch (profile.kind()) {
            case PRESSURE_PUMP -> base + (enabled && powered ? "On" : "");
            case VOLUME_PUMP -> !enabled || !powered ? base : blocked ? base + "Blocked" : base + "On";
            case MIXER, FILTER -> base + (enabled ? "On" : "");
            case MANUAL_VALVE -> base + (enabled ? "On" : "");
            case HEATER, FREEZER -> base + (powered ? "On" : "Off");
            case PASSIVE_HEAT_EXCHANGER -> base;
            case GAS_PIPE_MANIFOLD -> base;
            case TEG_CENTER -> base + "-outputlevel" + tegLevel(powered, ramp,
                    profile.teg().minimumVisualWatts(), profile.teg().maximumVisualWatts());
            case TEG_CIRCULATOR -> base;
        };
    }

    /** SS14 TegSystem.UpdateAppearance rounding, bounded to overlays 0..11. */
    public static int tegLevel(boolean powered, TegSupplyPolicy.State ramp) {
        return tegLevel(powered, ramp, 5000, 200000);
    }
    public static int tegLevel(boolean powered, TegSupplyPolicy.State ramp, double minimum, double maximum) {
        if (!powered) return 0;
        double ratio = (ramp.rampPositionWatts() - minimum) / (maximum - minimum);
        if (ratio <= 0) return 0;
        if (ratio >= 1) return 11;
        return Math.min(11, (int) Math.ceil(ratio * 10));
    }

    /** Native DISPLAY values are deliberately compact and profile-specific, never a boolean cartesian product. */
    public static int nativeDisplay(AtmosMachineProfile profile, String visualState, boolean powered) {
        if (profile.kind() == AtmosMachineProfile.Kind.TEG_CENTER) {
            int marker = visualState.lastIndexOf("outputlevel");
            if (marker >= 0) return powered ? 1 + Integer.parseInt(visualState.substring(marker + 11)) : 0;
        }
        return switch (profile.kind()) {
            case PRESSURE_PUMP -> visualState.endsWith("On") ? 13 : 14;
            case VOLUME_PUMP -> visualState.equals(profile.visual().state()) ? 15 : visualState.endsWith("Blocked") ? 16 : 17;
            case MIXER -> visualState.endsWith("On") ? 18 : 19;
            case FILTER -> visualState.endsWith("On") ? 20 : 21;
            case MANUAL_VALVE -> visualState.endsWith("On") ? 22 : 23;
            case HEATER, FREEZER -> visualState.endsWith("On") ? 24 : 25;
            case PASSIVE_HEAT_EXCHANGER -> 0;
            case GAS_PIPE_MANIFOLD -> 0;
            case TEG_CIRCULATOR -> 26;
            case TEG_CENTER -> 0;
        };
    }

    /** SS14 UpdateCirculatorAppearance: observations are from an accepted prior core transfer. */
    public static int circulatorDisplay(boolean powered, double previousPressureDeltaKpa,
                                        double previousMolesTransferred, double fastThresholdKpa) {
        if (!powered) return 26;
        if (!Double.isFinite(previousPressureDeltaKpa) || !Double.isFinite(previousMolesTransferred)
                || previousPressureDeltaKpa <= 0 || previousMolesTransferred <= 0) return 27;
        return previousPressureDeltaKpa > fastThresholdKpa ? 29 : 28;
    }
}
