package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Plans hotspot exposures without changing gas mixtures; callers own all world verification and writes. */
public final class HotspotSpread {
    private static final double SOURCE_GATE_K = 423.15;
    private static final double QUARTER_CELL_LITERS = 250.0;

    private HotspotSpread() { }

    /** A face-adjacent, finite, loaded, open neighbor supplied by the caller. */
    public record Neighbor(BlockPos position, Direction face, GasMixture mixture, HotspotState hotspot) {
        public Neighbor {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(mixture, "mixture");
        }
    }

    /** Exposure input passed to HotspotKernel.expose, not energy or a mutated mixture. */
    public record Offer(BlockPos position, double temperatureKelvin, double inputVolumeLiters) {
        public Offer {
            position = Objects.requireNonNull(position, "position").immutable();
            if (!Double.isFinite(temperatureKelvin) || temperatureKelvin < 0
                    || !Double.isFinite(inputVolumeLiters) || inputVolumeLiters <= 0)
                throw new IllegalArgumentException("invalid hotspot exposure");
        }
    }

    public record Result(Map<BlockPos, Offer> offers) {
        public Result { offers = Collections.unmodifiableMap(new LinkedHashMap<>(offers)); }
    }

    /** Source state must represent a committed active hotspot; neighbors have already passed world checks. */
    public static Result plan(BlockPos sourcePosition, GasMixture source, HotspotState sourceState,
                              List<Neighbor> verifiedFiniteNeighbors) {
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(source, "source");
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
        if (sourceState == null || !sourceState.skippedFirstProcess()) return new Result(Map.of());

        double exposureTemperature = (sourceState.bypass() ? source.temperatureKelvin()
                : sourceState.temperatureKelvin()) * 0.85;
        if ((sourceState.bypass() ? source.temperatureKelvin() : sourceState.temperatureKelvin()) <= SOURCE_GATE_K)
            return new Result(Map.of());
        Map<BlockPos, Offer> offers = new LinkedHashMap<>();
        for (Direction face : Direction.values()) {
            Neighbor neighbor = faces.get(face);
            if (neighbor == null || neighbor.hotspot() != null) continue;
            if (HotspotKernel.expose(neighbor.mixture(), null, exposureTemperature,
                    QUARTER_CELL_LITERS, false, false).isPresent()) {
                Offer offer = new Offer(neighbor.position(), exposureTemperature, QUARTER_CELL_LITERS);
                offers.put(offer.position(), offer);
            }
        }
        return new Result(offers);
    }
}
