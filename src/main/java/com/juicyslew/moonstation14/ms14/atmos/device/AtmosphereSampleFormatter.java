package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;

import java.util.Locale;
import java.util.Objects;

/** Creates a detached, owner-facing summary of one atmosphere sample. */
public final class AtmosphereSampleFormatter {
    private AtmosphereSampleFormatter() { }

    public static String format(GasMixture mixture) {
        Objects.requireNonNull(mixture, "mixture");
        StringBuilder result = new StringBuilder(String.format(Locale.ROOT,
                "P: %.3f kPa | T: %.2f K | n: %.2f mol",
                mixture.pressureKpa(1.0), mixture.temperatureKelvin(), mixture.totalMoles()));
        boolean hasGas = false;
        for (GasType type : GasType.values()) {
            double moles = mixture.moles(type);
            if (moles > 0.0) {
                result.append(String.format(Locale.ROOT, " | %s: %.6g mol", type.id(), moles));
                hasGas = true;
            }
        }
        if (!hasGas) result.append(" | gases: none");
        return result.toString();
    }

    public static String format(AtmosphereReading reading) {
        Objects.requireNonNull(reading, "reading");
        String formatted = format(reading.mixture());
        return reading.status() == AtmosphereReading.Status.PROVISIONAL
                ? "Provisional / classification pending | " + formatted
                : formatted;
    }
}
