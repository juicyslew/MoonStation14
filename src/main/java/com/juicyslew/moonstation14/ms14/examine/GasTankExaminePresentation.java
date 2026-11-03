package com.juicyslew.moonstation14.ms14.examine;

/** SS14 PRESSURE() equivalent: caller supplies the upstream-rounded pressure in kPa. */
public final class GasTankExaminePresentation {
    private static final String[] UNITS = {"kPa", "MPa", "GPa", "TPa", "PBa", "???"};
    private GasTankExaminePresentation() { }

    public static String pressure(double kPa) {
        if (!Double.isFinite(kPa) || kPa < 0) throw new IllegalArgumentException("invalid pressure");
        double value = kPa;
        int places = 0;
        while (value >= 1000.0 && places < 5) { value /= 1000.0; places++; }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, UNITS[places]);
    }

    /** SS14 SharedGasTankSystem.OnExamined applies Math.Round before PRESSURE localization. */
    public static String examinedPressure(double kPa) {
        if (!Double.isFinite(kPa) || kPa < 0) throw new IllegalArgumentException("invalid pressure");
        return pressure(Math.rint(kPa));
    }
}
