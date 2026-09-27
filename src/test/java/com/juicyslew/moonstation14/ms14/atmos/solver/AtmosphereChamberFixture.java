package com.juicyslew.moonstation14.ms14.atmos.solver;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure in-memory, finite-cell atmosphere topology for solver tests; it creates no game level. */
final class AtmosphereChamberFixture {
    static final double ONE_ATMOSPHERE_KPA = 101.325;
    static final double ROOM_TEMPERATURE_KELVIN = 293.15;
    private static final double GAS_CONSTANT = 8.31446261815324;
    private static final List<BlockPos> FACE_OFFSETS = List.of(
            new BlockPos(0, -1, 0), new BlockPos(0, 1, 0),
            new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));

    enum NeighborKind { FINITE, WALL, EXTERIOR, UNKNOWN }

    record Snapshot(Map<BlockPos, GasMixture> finiteCells, Set<BlockPos> walls,
                    Set<BlockPos> exterior, Set<BlockPos> unloaded) { }

    final Map<BlockPos, GasMixture> finiteCells = new LinkedHashMap<>();
    final Set<BlockPos> walls = new LinkedHashSet<>();
    final Set<BlockPos> exterior = new LinkedHashSet<>();
    final Set<BlockPos> unloaded = new LinkedHashSet<>();
    private final int width;
    private final int height;
    private final int depth;
    private final boolean twoRooms;

    private AtmosphereChamberFixture(int width, int height, int depth, boolean twoRooms) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Chamber dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.twoRooms = twoRooms;
    }

    static AtmosphereChamberFixture sealedRoom(int x, int y, int z,
                                                Map<GasType, Double> perCell, double kelvin) {
        AtmosphereChamberFixture chamber = new AtmosphereChamberFixture(x, y, z, false);
        GasMixture cell = new GasMixture(perCell, kelvin);
        chamber.fillRoom(0, cell);
        chamber.addRoomShell(0);
        return chamber;
    }

    static AtmosphereChamberFixture twoRooms(int width, int height, int depth,
                                               Map<GasType, Double> leftMix,
                                               Map<GasType, Double> rightMix) {
        AtmosphereChamberFixture chamber = new AtmosphereChamberFixture(width, height, depth, true);
        chamber.fillRoom(0, new GasMixture(leftMix, ROOM_TEMPERATURE_KELVIN));
        chamber.fillRoom(width + 1, new GasMixture(rightMix, ROOM_TEMPERATURE_KELVIN));
        chamber.addRoomShell(0);
        chamber.addRoomShell(width + 1);
        chamber.fullWall();
        return chamber;
    }

    /** Pure gas inventory per 1 m3 at the specified ideal-gas pressure and temperature. */
    static Map<GasType, Double> pureGasAtPressure(GasType gas, double pressureKpa, double kelvin) {
        Objects.requireNonNull(gas, "gas");
        if (!Double.isFinite(pressureKpa) || pressureKpa < 0.0 || !Double.isFinite(kelvin) || kelvin <= 0.0) {
            throw new IllegalArgumentException("Pressure must be nonnegative and temperature must be positive");
        }
        return Map.of(gas, pressureKpa * 1000.0 / (GAS_CONSTANT * kelvin));
    }

    List<BlockPos> neighbors(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        List<BlockPos> result = new ArrayList<>(FACE_OFFSETS.size());
        for (BlockPos offset : FACE_OFFSETS) result.add(pos.offset(offset));
        return List.copyOf(result);
    }

    NeighborKind neighborKind(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        if (finiteCells.containsKey(pos)) return NeighborKind.FINITE;
        if (walls.contains(pos)) return NeighborKind.WALL;
        if (exterior.contains(pos)) return NeighborKind.EXTERIOR;
        return NeighborKind.UNKNOWN;
    }

    int faceOpenings(BlockPos pos) {
        int openings = 0;
        for (BlockPos neighbor : neighbors(pos)) {
            NeighborKind kind = neighborKind(neighbor);
            if (kind == NeighborKind.FINITE || kind == NeighborKind.EXTERIOR) openings++;
        }
        return openings;
    }

    /** Opens a central door-sized hole in the wall dividing the two chambers. */
    void openSingleDoor() {
        requireTwoRooms();
        BlockPos door = dividerPosition(height / 2, depth / 2);
        walls.remove(door);
        finiteCells.put(door, finiteCells.get(new BlockPos(width - 1, height / 2, depth / 2)));
    }

    void fullWall() {
        requireTwoRooms();
        finiteCells.keySet().removeIf(pos -> pos.getX() == width);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) walls.add(dividerPosition(y, z));
        }
    }

    /** Opens {@code count} deterministic positions along the top shell to adjacent immutable vacuum. */
    void openExteriorFace(int count) {
        if (count < 0 || count > width * depth) {
            throw new IllegalArgumentException("Exterior opening count must be between zero and chamber roof area");
        }
        for (int i = 0; i < count; i++) {
            int x = i % width;
            int z = i / width;
            BlockPos roof = new BlockPos(x, height, z);
            walls.remove(roof);
            exterior.add(roof);
        }
    }

    /** Adds source gas at 20 mol/s for the requested duration and returns the resulting cell state. */
    GasMixture inject20MolPerSecond(BlockPos pos, GasType gas, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) throw new IllegalArgumentException("seconds must be nonnegative");
        GasMixture mixture = Objects.requireNonNull(finiteCells.get(pos), "Source position is not a finite cell");
        GasMixture injected = mixture.withGasDelta(Objects.requireNonNull(gas, "gas"), 20.0 * seconds);
        finiteCells.put(pos.immutable(), injected);
        return injected;
    }

    Snapshot snapshot() {
        Map<BlockPos, GasMixture> cellCopy = new LinkedHashMap<>();
        finiteCells.forEach((pos, mixture) -> cellCopy.put(pos.immutable(), mixture));
        return new Snapshot(Collections.unmodifiableMap(cellCopy),
                Collections.unmodifiableSet(copyPositions(walls)),
                Collections.unmodifiableSet(copyPositions(exterior)),
                Collections.unmodifiableSet(copyPositions(unloaded)));
    }

    private void fillRoom(int xOffset, GasMixture mixture) {
        for (int x = xOffset; x < xOffset + width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) finiteCells.put(new BlockPos(x, y, z), mixture);
            }
        }
    }

    private void addRoomShell(int xOffset) {
        for (int x = xOffset - 1; x <= xOffset + width; x++) {
            for (int y = -1; y <= height; y++) {
                for (int z = -1; z <= depth; z++) {
                    if (x < xOffset || x >= xOffset + width || y < 0 || y >= height || z < 0 || z >= depth) {
                        walls.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private BlockPos dividerPosition(int y, int z) {
        return new BlockPos(width, y, z);
    }

    private void requireTwoRooms() {
        if (!twoRooms) throw new IllegalStateException("Operation requires a two-room fixture");
    }

    private static Set<BlockPos> copyPositions(Set<BlockPos> source) {
        Set<BlockPos> copy = new LinkedHashSet<>();
        source.forEach(pos -> copy.add(pos.immutable()));
        return copy;
    }
}
