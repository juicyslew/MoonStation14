package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** One detached reaction pass. All thermal quantities in this class are unscaled local joules. */
public final class GasReactionEvaluator {
    private GasReactionEvaluator() {}

    public record Event(ResourceLocation id, GasReactionData.EffectType effect, double extentMoles,
                        Map<GasType, Double> speciesDelta, double energyDeltaJoules) {
        public Event { speciesDelta = Map.copyOf(speciesDelta); }
    }

    public record Result(GasMixture mixture, Map<GasType, Double> speciesDelta,
                         double energyDeltaJoules, List<Event> events) {
        public Result {
            speciesDelta = Map.copyOf(speciesDelta);
            events = List.copyOf(events);
        }
    }

    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original) {
        return evaluate(catalog, original, effect -> true);
    }

    /** Select effects, not prototypes: gates and species requirements retain their original ordering. */
    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                  Predicate<GasReactionData.EffectType> selected) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(selected, "selected");
        List<Map.Entry<ResourceLocation, GasReactionData>> ordered = new ArrayList<>(catalog.asMap().entrySet());
        ordered.sort(Comparator.<Map.Entry<ResourceLocation, GasReactionData>>comparingInt(e -> e.getValue().priority())
                .reversed().thenComparing(Map.Entry::getKey));
        State state = new State(original);
        double gateTemperature = original.temperatureKelvin();
        double gateEnergy = original.thermalEnergy();
        List<Event> events = new ArrayList<>();
        for (var entry : ordered) {
            GasReactionData reaction = Objects.requireNonNull(entry.getValue(), "reaction");
            if (gateTemperature < reaction.minimumTemperature() || gateTemperature > reaction.maximumTemperature()
                    || gateEnergy < reaction.minimumEnergy()) continue;
            boolean eligible = true;
            for (var requirement : reaction.minimumRequirements().entrySet()) {
                if (state.get(requirement.getKey()) < requirement.getValue()) { eligible = false; break; }
            }
            if (!eligible) continue;
            for (GasReactionData.Effect effect : reaction.effects()) {
                if (!selected.test(effect.type())) continue;
                double[] before = state.moles.clone();
                double heatBefore = state.heat;
                double extent = apply(effect.type(), state);
                if (extent <= 0) continue;
                state.validate();
                events.add(new Event(entry.getKey(), effect.type(), extent, delta(before, state.moles), state.heat - heatBefore));
            }
        }
        if (events.isEmpty()) return new Result(original, Map.of(), 0, List.of());
        state.validate();
        // Construct only once, after the entire pass has been validated. Never publish a partial effect.
        GasMixture finalMixture = state.finish();
        return new Result(finalMixture, delta(original, state.moles), state.heat - gateEnergy, events);
    }

    private static Map<GasType, Double> delta(double[] before, double[] after) {
        EnumMap<GasType, Double> changes = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) {
            double change = after[type.ordinal()] - before[type.ordinal()];
            if (change != 0) changes.put(type, change);
        }
        return changes;
    }

    private static Map<GasType, Double> delta(GasMixture before, double[] after) {
        double[] amounts = new double[GasType.values().length];
        for (GasType type : GasType.values()) amounts[type.ordinal()] = before.moles(type);
        return delta(amounts, after);
    }

    private static double apply(GasReactionData.EffectType effect, State s) {
        double oxygen = s.get(GasType.OXYGEN);
        double tritium = s.get(GasType.TRITIUM);
        double temperature = s.temperature();
        switch (effect) {
            case N2O_DECOMPOSITION -> {
                double burned = s.get(GasType.NITROUS_OXIDE) / 2;
                if (burned <= 0) return 0;
                s.consume(GasType.NITROUS_OXIDE, burned);
                s.add(GasType.NITROGEN, burned);
                s.add(GasType.OXYGEN, burned / 2);
                return burned;
            }
            case AMMONIA_OXYGEN -> {
                double ammonia = s.get(GasType.AMMONIA);
                double total = s.total();
                if (total <= 0 || ammonia <= 0 || oxygen <= 0) return 0;
                double rate = Math.pow(ammonia / total, 2) * Math.pow(oxygen / total, 2);
                double burned = Math.min(Math.min(ammonia, oxygen), ammonia / 10 * 2 * rate);
                if (burned <= 0) return 0;
                s.consume(GasType.AMMONIA, burned);
                s.consume(GasType.OXYGEN, burned);
                s.add(GasType.NITROUS_OXIDE, burned / 2);
                s.add(GasType.WATER_VAPOR, burned * 1.5);
                return burned;
            }
            case FREZON_PRODUCTION -> {
                double efficiency = temperature / 73.15;
                if (efficiency <= 0 || efficiency > 1) return 0;
                double catalystLimit = s.get(GasType.NITROGEN) * 10 / efficiency;
                double burned = Math.min(Math.min(oxygen, catalystLimit) / 50, tritium);
                double convertedTritium = burned / 50;
                double convertedOxygen = burned; // burned * 50 / conversion rate 50
                if (convertedTritium <= 0 || convertedOxygen <= 0) return 0;
                double total = convertedOxygen + convertedTritium;
                s.consume(GasType.OXYGEN, convertedOxygen);
                s.consume(GasType.TRITIUM, convertedTritium);
                s.add(GasType.FREZON, total * efficiency);
                s.add(GasType.NITROGEN, total * (1 - efficiency));
                return convertedTritium;
            }
            case FREZON_COOLANT -> {
                double scale = (temperature - 23.15) / 350;
                if (scale <= 0) return 0;
                double modifier = scale > 1 ? Math.min(scale, 10) : 1;
                double burn = s.get(GasType.FREZON) * Math.min(scale, 1) / 20;
                if (burn <= 0.0003) return 0;
                double nitrogen = Math.min(burn * 5, s.get(GasType.NITROGEN));
                double frezon = Math.min(burn, s.get(GasType.FREZON));
                double cooling = -600000 * burn * modifier;
                // Unlike upstream, never consume gas then silently discard invalid cooling.
                if (!Double.isFinite(cooling) || s.heat + cooling < 0) throw new IllegalArgumentException("coolant exceeds thermal energy");
                s.consume(GasType.NITROGEN, nitrogen);
                s.consume(GasType.FREZON, frezon);
                s.add(GasType.NITROUS_OXIDE, nitrogen + frezon);
                s.heat += cooling;
                return burn;
            }
            case TRITIUM_FIRE -> {
                if (tritium <= 0 || oxygen <= 0) return 0;
                boolean low = oxygen < tritium || 143000 > temperature * s.capacity();
                double burned = low ? Math.min(tritium, oxygen / 100)
                        : Math.min(tritium, oxygen / 2) / 10;
                burned = Math.min(burned, oxygen * 2);
                if (burned <= 0) return 0;
                s.consume(GasType.TRITIUM, burned);
                s.consume(GasType.OXYGEN, burned / 2);
                s.add(GasType.WATER_VAPOR, burned);
                s.heat += 284000 * burned * (low ? 1 : 10);
                return burned;
            }
            case PLASMA_FIRE -> {
                double plasma = s.get(GasType.PLASMA);
                if (temperature <= 373.15 || plasma <= 0 || oxygen <= 0) return 0;
                double scale = Math.min(1, (temperature - 373.15) / (1643.15 - 373.15));
                double oxygenRate = 1.4 - scale;
                double ratio = oxygen / plasma;
                double supersaturation = Math.max(0, Math.min(1, (ratio - 32) / 64));
                double rate = (oxygen > plasma * 10 ? plasma : oxygen / 10) * scale / 9;
                // Upstream tests the preliminary rate before clamping to available reagents.
                if (rate <= 0.0003) return 0;
                rate = Math.min(rate, Math.min(plasma, oxygen / oxygenRate));
                if (rate <= 0) return 0;
                s.consume(GasType.PLASMA, rate);
                s.consume(GasType.OXYGEN, rate * oxygenRate);
                s.add(GasType.TRITIUM, rate * supersaturation);
                s.add(GasType.CARBON_DIOXIDE, rate * (1 - supersaturation));
                s.heat += 160000 * rate;
                return rate * (1 + oxygenRate); // upstream fire-result extent
            }
        }
        throw new IllegalArgumentException("unsupported effect: " + effect);
    }

    private static final class State {
        private final double[] moles = new double[GasType.values().length];
        private double heat;

        State(GasMixture mixture) {
            for (GasType gas : GasType.values()) moles[gas.ordinal()] = mixture.moles(gas);
            heat = mixture.thermalEnergy();
            validate();
        }

        double get(GasType gas) { return moles[gas.ordinal()]; }
        void consume(GasType gas, double value) {
            if (!Double.isFinite(value) || value < 0 || value > get(gas)) throw new IllegalArgumentException("invalid consumption");
            moles[gas.ordinal()] -= value;
        }
        void add(GasType gas, double value) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("invalid production");
            moles[gas.ordinal()] += value;
        }
        double total() {
            double total = 0;
            for (double amount : moles) total += amount;
            return total;
        }
        double capacity() {
            double capacity = 0;
            for (GasType gas : GasType.values()) capacity += get(gas) * gas.molarHeatCapacity();
            return capacity;
        }
        double temperature() { return capacity() == 0 ? 0 : heat / capacity(); }
        void validate() {
            for (double amount : moles) if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("invalid reaction inventory");
            double capacity = capacity();
            if (!Double.isFinite(total()) || !Double.isFinite(capacity) || !Double.isFinite(heat) || heat < 0
                    || (capacity == 0 && heat != 0) || !Double.isFinite(temperature()) || temperature() < 0)
                throw new IllegalArgumentException("invalid reaction thermal state");
        }
        GasMixture finish() {
            EnumMap<GasType, Double> complete = new EnumMap<>(GasType.class);
            for (GasType gas : GasType.values()) if (get(gas) > 0) complete.put(gas, get(gas));
            return new GasMixture(complete, capacity() == 0 ? 2.7 : temperature());
        }
    }
}
