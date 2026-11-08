package com.juicyslew.moonstation14.ms14.atmos.piping;

/** Pure projection of authoritative ventilation configuration into delivered display states. */
public final class VentilationDeviceAppearance {
    private VentilationDeviceAppearance() { }

    public static String project(VentilationDeviceConfig.Kind kind, VentilationDeviceConfig.Settings settings,
                                 boolean powered, boolean valid) {
        if (kind == null || settings == null || !valid || !powered || !settings.configuredEnabled()) return "off";
        if (kind == VentilationDeviceConfig.Kind.VENT) return "vent_out";
        return settings.scrubberMode() == VentilationDeviceConfig.ScrubberMode.SIPHON ? "scrub_purge" : "scrub_on";
    }

    public static int layer(VentilationDeviceConfig.Settings settings) {
        return switch (settings.selectedLayer()) {
            case PRIMARY -> 0;
            case SECONDARY -> 1;
            case TERTIARY -> 2;
        };
    }

    /** Converts the projected display identifier to the block state's native display index. */
    public static int display(String display) {
        return switch (display) { case "vent_out" -> 1; case "scrub_on" -> 2; case "scrub_purge" -> 3; default -> 0; };
    }
}
