package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

import java.util.Locale;
import java.util.Objects;

/** Creates a detached, owner-facing summary of one atmosphere sample. */
public final class AtmosphereSampleFormatter {
    private AtmosphereSampleFormatter() { }

    public static String format(GasMixture mixture) {
        Objects.requireNonNull(mixture, "mixture");
        StringBuilder result = new StringBuilder(String.format(Locale.ROOT,
                "P: %.3f kPa | T: %.2f K | n: %.2f mol | O2: %.2f mol | N2: %.2f mol",
                mixture.pressureKpa(1.0), mixture.temperatureKelvin(), mixture.totalMoles(),
                mixture.moles(GasType.OXYGEN), mixture.moles(GasType.NITROGEN)));
        for (GasType type : GasType.values()) {
            if (type == GasType.OXYGEN || type == GasType.NITROGEN) continue;
            double moles = mixture.moles(type);
            if (moles > 0.0) {
                result.append(String.format(Locale.ROOT, " | %s: %.2f mol", type.id(), moles));
            }
        }
        return result.toString();
    }
}
