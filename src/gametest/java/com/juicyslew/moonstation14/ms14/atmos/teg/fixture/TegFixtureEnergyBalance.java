package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

/** Conservative thermal boundary proof for the finite fueled fixture. */
public final class TegFixtureEnergyBalance {
    public static final double MATERIAL_MARGIN_JOULES = 1000;
    public static final double INLET_REFERENCE_KELVIN = 293.15;

    private TegFixtureEnergyBalance() { }

    /** Checks endpoint species purity and valid positive carrier stock; endpoints do not prove cumulative retention. */
    public static void verifyCarrierSpeciesIsolation(java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> baseline,
            java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> end,
            com.juicyslew.moonstation14.ms14.atmos.core.GasType expected) {
        java.util.Objects.requireNonNull(baseline);java.util.Objects.requireNonNull(end);java.util.Objects.requireNonNull(expected);
        for(var gas:com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()) {
            double x=baseline.getOrDefault(gas,0d),y=end.getOrDefault(gas,0d);
            if(!Double.isFinite(x)||x<0||!Double.isFinite(y)||y<0)throw new IllegalArgumentException("invalid carrier species inventory: "+gas);
            if(gas!=expected&&(x!=0||y!=0))throw new IllegalArgumentException("foreign "+gas+" in carrier loop: "+x+" -> "+y);
            if(gas==expected&&(!(x>0)||!(y>0)))throw new IllegalArgumentException("carrier stock must be positive: "+x+" -> "+y);
        }
    }

    /** Resolved SS14 conversion profile used to interpret generated electrical joules as net gas heat loss. */
    public record ConversionModel(double efficiency, double powerFactor) {
        public ConversionModel {
            if (!Double.isFinite(efficiency) || efficiency < 0 || efficiency > 1)
                throw new IllegalArgumentException("invalid resolved TEG efficiency");
            if (!Double.isFinite(powerFactor) || powerFactor <= 0)
                throw new IllegalArgumentException("resolved TEG powerFactor must be finite and positive");
        }
        public double modeledThermalSinkForGeneration(double generatedJoules) {
            finite(generatedJoules, "generated energy");
            if (generatedJoules < 0) throw new IllegalArgumentException("negative generated energy");
            double sink = generatedJoules * efficiency / powerFactor;
            finite(sink, "modeled thermal sink");
            return sink;
        }
    }

    public record HeatedStores(double hotCarrierJoules,double burnerJoules,double fuelTemperatureKelvin) {
        public HeatedStores {
            finite(hotCarrierJoules,"hot carrier"); finite(burnerJoules,"burner"); finite(fuelTemperatureKelvin,"fuel temperature");
            if(hotCarrierJoules<0||burnerJoules<0||fuelTemperatureKelvin<0)throw new IllegalArgumentException("negative heated store");
        }
    }

    public static boolean retainsHeatedStores(HeatedStores before,HeatedStores current,
                                              double carrierToleranceJoules,double burnerToleranceJoules,
                                              double fuelToleranceKelvin) {
        finite(carrierToleranceJoules,"carrier tolerance"); finite(burnerToleranceJoules,"burner tolerance"); finite(fuelToleranceKelvin,"fuel tolerance");
        if(carrierToleranceJoules<0||burnerToleranceJoules<0||fuelToleranceKelvin<0)throw new IllegalArgumentException("negative store tolerance");
        return current.hotCarrierJoules()>=before.hotCarrierJoules()-carrierToleranceJoules
                &&current.burnerJoules()>=before.burnerJoules()-burnerToleranceJoules
                &&current.fuelTemperatureKelvin()>=before.fuelTemperatureKelvin()-fuelToleranceKelvin;
    }

    /** Measured hot-loop plus burner thermal stock above the actual room-temperature inlet reference. */
    public record HotThermalStock(double rawJoules,double heatCapacityJoulesPerKelvin,double excessJoules) { }
    public static HotThermalStock hotThermalStock(double hotJoules,double burnerJoules,
            java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> hotMoles,
            java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> burnerMoles) {
        finite(hotJoules,"hot loop thermal");finite(burnerJoules,"burner thermal");
        if(hotJoules<0||burnerJoules<0)throw new IllegalArgumentException("negative hot thermal stock");
        double capacity=heatCapacity(hotMoles)+heatCapacity(burnerMoles);
        double raw=hotJoules+burnerJoules;
        double excess=raw-capacity*INLET_REFERENCE_KELVIN;
        finite(raw,"hot raw thermal");finite(excess,"hot thermal excess");return new HotThermalStock(raw,capacity,excess);
    }
    public static double hotExcessJoules(double hotJoules,double burnerJoules,
            java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> hotMoles,
            java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> burnerMoles) {
        return hotThermalStock(hotJoules,burnerJoules,hotMoles,burnerMoles).excessJoules();
    }
    public static boolean retainsHotExcess(double beforeJoules,double currentJoules,double toleranceJoules) {
        finite(beforeJoules,"hot excess before");finite(currentJoules,"hot excess current");finite(toleranceJoules,"hot excess tolerance");
        if(toleranceJoules<0)throw new IllegalArgumentException("negative hot excess tolerance");
        return currentJoules>=beforeJoules-toleranceJoules;
    }
    /** SPACE proves a non-increasing cold store; planetary ambient records its signed delta without claiming that steady-state boundary. */
    public static boolean retainsColdStock(boolean strictSpace,double coldDeltaJoules,double toleranceJoules) {
        finite(coldDeltaJoules,"cold thermal delta");finite(toleranceJoules,"cold thermal tolerance");
        if(toleranceJoules<0)throw new IllegalArgumentException("negative cold thermal tolerance");
        return !strictSpace||coldDeltaJoules<=toleranceJoules;
    }
    private static double heatCapacity(java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Double> moles) {
        java.util.Objects.requireNonNull(moles);double capacity=0;
        for(var gas:com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()) {
            double amount=moles.getOrDefault(gas,0d);finite(amount,"hot-store moles");
            if(amount<0)throw new IllegalArgumentException("negative hot-store moles");
            capacity+=amount*gas.molarHeatCapacity();
        }
        finite(capacity,"hot-store heat capacity");return capacity;
    }

    public record Observation(double hotRoom, double hotPipe, double coldRoom, double coldPipe,
                              double startupApc, double generatedJoules, ConversionModel conversionModel) {
        public Observation {
            java.util.Objects.requireNonNull(conversionModel, "resolved TEG conversion model");
            finite(hotRoom, "hot room"); finite(hotPipe, "hot pipe");
            finite(coldRoom, "cold room"); finite(coldPipe, "cold pipe");
            finite(startupApc, "startup APC"); finite(generatedJoules, "generated energy");
            if (startupApc < 0 || generatedJoules < 0) throw new IllegalArgumentException("negative energy counter");
        }
        public double thermalInventory() {
            double inventory = hotRoom + hotPipe + coldRoom + coldPipe;
            finite(inventory, "thermal inventory");
            return inventory;
        }
    }

    public record Result(double thermalDelta, double startupConsumed, double generatedDelta, double modeledThermalSinkDelta,
                         double chemicalExcess) {
        public Result {
            finite(thermalDelta, "thermal delta"); finite(startupConsumed, "startup consumption");
            finite(generatedDelta, "generated delta"); finite(modeledThermalSinkDelta, "modeled thermal sink delta");
            finite(chemicalExcess, "chemical excess");
        }
        public boolean provesChemicalExcess() { return chemicalExcess > MATERIAL_MARGIN_JOULES; }
    }

    public static Result compare(Observation baseline, Observation current) {
        double thermalDelta = current.thermalInventory() - baseline.thermalInventory();
        double startupConsumed = baseline.startupApc() - current.startupApc();
        double generatedDelta = current.generatedJoules() - baseline.generatedJoules();
        if (!baseline.conversionModel().equals(current.conversionModel())) throw new IllegalArgumentException("resolved TEG conversion model changed during finite boundary");
        double modeledThermalSinkDelta=current.conversionModel().modeledThermalSinkForGeneration(current.generatedJoules())
                -current.conversionModel().modeledThermalSinkForGeneration(baseline.generatedJoules());
        finite(thermalDelta, "thermal delta"); finite(startupConsumed, "startup consumption"); finite(generatedDelta, "generated delta");
        if (startupConsumed < 0 || generatedDelta < 0) throw new IllegalArgumentException("energy counter reset/increase");
        double chemicalExcess = thermalDelta + modeledThermalSinkDelta - startupConsumed;
        finite(chemicalExcess, "chemical excess");
        return new Result(thermalDelta, startupConsumed, generatedDelta, modeledThermalSinkDelta, chemicalExcess);
    }

    /** Heater-off continuous boundary. Ambient outflow is signed; positive is heat to the environment. */
    public record BoundaryObservation(double thermalInventoryJoules, double generatedJoules, ConversionModel conversionModel,
                                      double minerThermalJoules, double exportedGasThermalJoules,
                                      double ambientHeatOutJoules, double spaceHeatOutJoules) {
        public BoundaryObservation {
            java.util.Objects.requireNonNull(conversionModel, "resolved TEG conversion model");
            finite(thermalInventoryJoules, "thermal inventory");
            finite(generatedJoules, "generated energy");
            finite(minerThermalJoules, "miner thermal input");
            finite(exportedGasThermalJoules, "exported gas thermal energy");
            finite(ambientHeatOutJoules, "ambient heat out");
            finite(spaceHeatOutJoules, "space heat out");
            if (thermalInventoryJoules < 0 || generatedJoules < 0 || minerThermalJoules < 0 ||
                    exportedGasThermalJoules < 0 || spaceHeatOutJoules < 0)
                throw new IllegalArgumentException("negative energy counter");
        }
    }

    public record BoundaryResult(double thermalDeltaJoules, double generatedDeltaJoules, double modeledThermalSinkDeltaJoules,
                                  double minerThermalDeltaJoules, double exportedGasThermalDeltaJoules,
                                  double ambientHeatOutDeltaJoules, double spaceHeatOutDeltaJoules,
                                  double inferredReactionHeatJoules) {
        public BoundaryResult {
            finite(thermalDeltaJoules, "thermal delta"); finite(generatedDeltaJoules, "generated delta");
            finite(modeledThermalSinkDeltaJoules, "modeled thermal sink delta");
            finite(minerThermalDeltaJoules, "miner thermal delta"); finite(exportedGasThermalDeltaJoules, "exported gas thermal delta");
            finite(ambientHeatOutDeltaJoules, "ambient heat delta"); finite(spaceHeatOutDeltaJoules, "space heat delta");
            finite(inferredReactionHeatJoules, "inferred reaction heat");
        }
        public boolean provesChemicalExcess() { return inferredReactionHeatJoules > MATERIAL_MARGIN_JOULES; }
    }

    public static BoundaryResult compareBoundary(BoundaryObservation baseline, BoundaryObservation current) {
        double thermal = current.thermalInventoryJoules() - baseline.thermalInventoryJoules();
        double generated = current.generatedJoules() - baseline.generatedJoules();
        if (!baseline.conversionModel().equals(current.conversionModel())) throw new IllegalArgumentException("resolved TEG conversion model changed during continuous boundary");
        double thermalSink=current.conversionModel().modeledThermalSinkForGeneration(current.generatedJoules())
                -current.conversionModel().modeledThermalSinkForGeneration(baseline.generatedJoules());
        double miner = current.minerThermalJoules() - baseline.minerThermalJoules();
        double exported = current.exportedGasThermalJoules() - baseline.exportedGasThermalJoules();
        double ambient = current.ambientHeatOutJoules() - baseline.ambientHeatOutJoules();
        double space = current.spaceHeatOutJoules() - baseline.spaceHeatOutJoules();
        finite(thermal, "thermal delta"); finite(generated, "generated delta");
        finite(miner, "miner thermal delta"); finite(exported, "exported gas delta");
        finite(ambient, "ambient heat delta"); finite(space, "space heat delta");
        if (generated < 0 || miner < 0 || exported < 0 || space < 0)
            throw new IllegalArgumentException("energy counter reset/decrease");
        double inferredReactionHeat = thermal + thermalSink + exported + ambient + space - miner;
        finite(inferredReactionHeat, "inferred reaction heat");
        return new BoundaryResult(thermal, generated, thermalSink, miner, exported, ambient, space, inferredReactionHeat);
    }

    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite " + name);
    }
}
