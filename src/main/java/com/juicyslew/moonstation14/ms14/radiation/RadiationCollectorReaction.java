package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

/** Pure profile-parameterized radiation/plasma reaction shared by collector runtime and tests. */
public final class RadiationCollectorReaction {
    private RadiationCollectorReaction() { }

    public record Result(double powerWatts, double plasmaLossMoles, GasMixture remaining) { }

    /** Power is evaluated from pre-depletion contents, matching SS14 collector irradiation ordering. */
    public static Result react(GasMixture mixture, GasType reactant, double dose, double chargeModifier,
                               double efficiency, double breakdownRate) {
        if (mixture == null || reactant == null || !Double.isFinite(dose) || dose < 0
                || !Double.isFinite(chargeModifier) || chargeModifier < 0
                || !Double.isFinite(efficiency) || efficiency < 0
                || !Double.isFinite(breakdownRate) || breakdownRate < 0)
            throw new IllegalArgumentException("Invalid radiation reaction input");
        double moles = mixture.moles(reactant);
        double modulation = 1.5 * mixture.temperatureKelvin() / (150 + mixture.temperatureKelvin());
        double power = dose * moles * chargeModifier * efficiency * modulation;
        double loss = Math.min(dose * moles * breakdownRate, moles);
        GasMixture remaining = loss > 0 ? mixture.withGasDelta(reactant, -loss) : mixture;
        return new Result(power, loss, remaining);
    }
}

