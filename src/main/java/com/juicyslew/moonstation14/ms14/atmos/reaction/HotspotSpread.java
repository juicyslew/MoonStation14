package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Pure heat-transfer proposal. The caller alone verifies finite ownership, loading and open faces,
 * and must separately arrange any multi-cell atomic commit; this class never ignites or writes cells. */
public final class HotspotSpread {
    private static final double SOURCE_GATE_K = 423.15;
    private static final double VIABLE_K = 373.15;
    private static final double MARGIN_K = 1.0;
    private static final double MINIMUM_MOLES = 0.01;
    private static final double HOT_FRACTION = 0.25;
    private static final double RADIATION_FRACTION = 0.85;

    private HotspotSpread() {}

    /** A separately verified finite neighbor on a face of the source. */
    public record Neighbor(BlockPos position, Direction face, GasMixture mixture) {
        public Neighbor {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(mixture, "mixture");
        }
    }

    /** Recipient state and heat credited on this face, in local (unscaled) joules. */
    public record Offer(BlockPos position, GasMixture mixture, double hotFraction,
                        double hotTemperatureKelvin, double energyJoules) {
        public Offer {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(mixture, "mixture");
            if (hotFraction != HOT_FRACTION || !Double.isFinite(hotTemperatureKelvin)
                    || hotTemperatureKelvin < 0 || !Double.isFinite(energyJoules) || energyJoules <= 0)
                throw new IllegalArgumentException("offer must transfer finite positive joules");
        }
    }

    public record Result(GasMixture source, Map<Direction, Offer> offers) {
        public Result {
            Objects.requireNonNull(source, "source");
            offers = Collections.unmodifiableMap(new LinkedHashMap<>(offers));
        }
    }

    /** Accepts at most one neighbor per adjacent position and face; input order never affects allocation.
     * No offer is made without an actively burning source (established by the caller's actual fire event).
     * Each eligible face receives a quarter-volume hot portion at 85% of source temperature;
     * only its explicitly calculated energy is debited from the source.
     * A shortfall skips that face instead of sending ineffective heat. */
    public static Result plan(BlockPos sourcePosition, GasMixture postReactionSource,
                              boolean activelyBurning, List<Neighbor> verifiedFiniteNeighbors) {
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(postReactionSource, "postReactionSource");
        Objects.requireNonNull(verifiedFiniteNeighbors, "verifiedFiniteNeighbors");
        if (verifiedFiniteNeighbors.size() > Direction.values().length)
            throw new IllegalArgumentException("at most six neighbors");
        Map<Direction, Neighbor> faces = new LinkedHashMap<>();
        for (Neighbor neighbor : verifiedFiniteNeighbors) {
            Objects.requireNonNull(neighbor, "neighbor");
            if (!sourcePosition.relative(neighbor.face()).equals(neighbor.position()))
                throw new IllegalArgumentException("neighbor is not on its specified face");
            if (faces.putIfAbsent(neighbor.face(), neighbor) != null)
                throw new IllegalArgumentException("duplicate neighbor face");
        }
        double sourceCapacity = postReactionSource.heatCapacity();
        if (!activelyBurning || postReactionSource.temperatureKelvin() <= SOURCE_GATE_K
                || sourceCapacity <= 0 || !fuelAndOxygen(postReactionSource, 0))
            return new Result(postReactionSource, Map.of());

        double remainingEnergy = postReactionSource.thermalEnergy();
        double radiationTemperature = postReactionSource.temperatureKelvin() * RADIATION_FRACTION;
        double reservedEnergy = sourceCapacity * (VIABLE_K + MARGIN_K);
        if (!Double.isFinite(reservedEnergy) || remainingEnergy <= reservedEnergy)
            return new Result(postReactionSource, Map.of());
        Map<Direction, Offer> accepted = new LinkedHashMap<>();
        // Direction's enum order, not caller's collection order, fixes budget allocation.
        for (Direction face : Direction.values()) {
            Neighbor neighbor = faces.get(face);
            if (neighbor == null) continue;
            GasMixture recipient = neighbor.mixture();
            double capacity = recipient.heatCapacity();
            double bulkTemperature = recipient.temperatureKelvin();
            if (capacity <= 0 || radiationTemperature <= VIABLE_K
                    || radiationTemperature <= bulkTemperature
                    || !fuelAndOxygen(recipient, MINIMUM_MOLES / HOT_FRACTION)) continue;
            double energy = HOT_FRACTION * capacity * (radiationTemperature - bulkTemperature);
            if (!Double.isFinite(energy) || energy <= 0 || energy > remainingEnergy - reservedEnergy) continue;
            double nextEnergy = remainingEnergy - energy;
            if (nextEnergy >= remainingEnergy) continue;
            GasMixture heated = recipient.withThermalEnergy(recipient.thermalEnergy() + energy);
            accepted.put(face, new Offer(neighbor.position(), heated, HOT_FRACTION,
                    radiationTemperature, energy));
            remainingEnergy = nextEnergy;
        }
        if (accepted.isEmpty()) return new Result(postReactionSource, Map.of());
        return new Result(postReactionSource.withThermalEnergy(remainingEnergy), accepted);
    }

    private static boolean fuelAndOxygen(GasMixture gas, double minimum) {
        return gas.moles(GasType.OXYGEN) > 0 && gas.moles(GasType.OXYGEN) >= minimum
                && ((gas.moles(GasType.TRITIUM) > 0 && gas.moles(GasType.TRITIUM) >= minimum)
                    || (gas.moles(GasType.PLASMA) > 0 && gas.moles(GasType.PLASMA) >= minimum));
    }
}
