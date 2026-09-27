package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** Sparse per-chunk overrides. Missing cells inherit their dimension ambient state. */
public final class AtmosphereChunkData {
    public static final int SCHEMA_VERSION = 2;

    private static final Codec<Double> NONNEGATIVE_FINITE = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= 0.0 ? DataResult.success(value)
                    : DataResult.error(() -> "value must be finite and nonnegative"));
    private static final Codec<Integer> SIGNED_Y = Codec.LONG.comapFlatMap(value ->
            value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    ? DataResult.success(value.intValue())
                    : DataResult.error(() -> "y must be a signed 32-bit integer"), Integer::longValue);
    private static final Codec<GasType> GAS_TYPE = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(GasType.fromId(id)); }
        catch (IllegalArgumentException exception) { return DataResult.error(exception::getMessage); }
    }, GasType::id);
    private static final Codec<GasAmount> GAS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GAS_TYPE.fieldOf("id").forGetter(GasAmount::type),
            NONNEGATIVE_FINITE.fieldOf("moles").forGetter(GasAmount::moles)
    ).apply(instance, GasAmount::new));
    private static final Codec<Cell> CELL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 15).fieldOf("x").forGetter(cell -> cell.position.x),
            SIGNED_Y.fieldOf("y").forGetter(cell -> cell.position.y),
            Codec.intRange(0, 15).fieldOf("z").forGetter(cell -> cell.position.z),
            NONNEGATIVE_FINITE.fieldOf("temperature").forGetter(Cell::temperature),
            GAS_CODEC.listOf().fieldOf("gases").forGetter(Cell::gases)
    ).apply(instance, (x, y, z, temperature, gases) -> new Cell(new Position(x, y, z), temperature, gases)));
    private static final Codec<CellPosition> FINITE_CELL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 15).fieldOf("x").forGetter(CellPosition::x),
            SIGNED_Y.fieldOf("y").forGetter(CellPosition::y),
            Codec.intRange(0, 15).fieldOf("z").forGetter(CellPosition::z)
    ).apply(instance, CellPosition::new));
    private static final Codec<AtmosphereChunkData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(data -> SCHEMA_VERSION),
            CELL_CODEC.listOf().fieldOf("cells").forGetter(data -> data.cellsForCodec()),
            FINITE_CELL_CODEC.listOf().optionalFieldOf("finite_cells").forGetter(data -> Optional.of(data.finiteCellsForCodec()))
    ).apply(instance, (version, cells, finiteCells) -> decode(version, cells, finiteCells.orElse(List.of()))));

    /** Versioned stable-ID representation; invalid or unknown persisted state is rejected. */
    public static final Codec<AtmosphereChunkData> CODEC = STRUCTURAL_CODEC.flatXmap(
            data -> data.codecError == null ? DataResult.success(data) : DataResult.error(() -> data.codecError),
            DataResult::success);

    private final NavigableMap<Position, GasMixture> cells = new TreeMap<>(AtmosphereChunkData::comparePositions);
    private final NavigableSet<Position> finiteClaims = new TreeSet<>(AtmosphereChunkData::comparePositions);
    private String codecError;

    public AtmosphereChunkData() { }

    /** Returns null when the coordinate has no explicit override. */
    public GasMixture get(int localX, int y, int localZ) {
        return cells.get(position(localX, y, localZ));
    }

    /**
     * Sets or removes an override. Returns true only when persisted state changed.
     * The owning server must call LevelChunk.setUnsaved(true) when this returns true.
     */
    public boolean put(int localX, int y, int localZ, GasMixture state, GasMixture ambient) {
        Position position = position(localX, y, localZ);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ambient, "ambient");
        GasMixture old = cells.get(position);
        if (stateEquals(state, ambient)) {
            if (old == null) return false;
            cells.remove(position);
            return true;
        }
        if (stateEquals(old, state)) return false;
        cells.put(position, state);
        return true;
    }

    public int size() { return cells.size(); }

    /** Claims finite-cell ownership without materializing a gas override. */
    public boolean isFiniteClaimed(int localX, int y, int localZ) {
        return finiteClaims.contains(position(localX, y, localZ));
    }

    /** Claims finite-cell ownership without changing gas state. */
    public boolean claimFinite(int localX, int y, int localZ) {
        return finiteClaims.add(position(localX, y, localZ));
    }

    /** Adds claims atomically after validating every coordinate. */
    public boolean claimFiniteAll(Collection<CellPosition> positions) {
        Objects.requireNonNull(positions, "positions");
        List<Position> validated = new ArrayList<>(positions.size());
        for (CellPosition cell : positions) {
            Objects.requireNonNull(cell, "position");
            validated.add(position(cell.x(), cell.y(), cell.z()));
        }
        boolean changed = false;
        for (Position position : validated) changed |= finiteClaims.add(position);
        return changed;
    }

    /** Immutable detached claims in deterministic x/z/y order. */
    public List<CellPosition> finiteClaimSnapshot() {
        return finiteClaims.stream().map(position -> new CellPosition(position.x, position.y, position.z)).toList();
    }

    public int claimCount() { return finiteClaims.size(); }

    public boolean hasPersistedState() { return !cells.isEmpty() || !finiteClaims.isEmpty(); }

    /**
     * Returns the next sparse override in deterministic x/z/y order without copying or retaining an
     * iterator over mutable storage. Entries added before the cursor are picked up on the next full
     * pass; additions after it and replacements/removals are reflected by subsequent calls.
     */
    public Optional<Map.Entry<CellPosition, GasMixture>> nextAfter(CellPosition cursor) {
        Map.Entry<Position, GasMixture> next = cursor == null
                ? cells.firstEntry()
                : cells.higherEntry(new Position(cursor.x(), cursor.y(), cursor.z()));
        if (next == null) return Optional.empty();
        Position position = next.getKey();
        return Optional.of(Map.entry(
                new CellPosition(position.x, position.y, position.z), next.getValue()));
    }

    /** Immutable coordinate-to-state snapshot suitable for re-enqueueing work. */
    public Map<CellPosition, GasMixture> snapshot() {
        Map<CellPosition, GasMixture> result = new LinkedHashMap<>();
        cells.forEach((position, state) -> result.put(new CellPosition(position.x, position.y, position.z), state));
        return Collections.unmodifiableMap(result);
    }

    /** Returns an immutable detached snapshot of overrides in one local vertical column, sorted by y. */
    public List<CellPosition> columnSnapshot(int localX, int localZ) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15)
            throw new IllegalArgumentException("local x/z must be in [0, 15]");
        Position first = new Position(localX, Integer.MIN_VALUE, localZ);
        Position last = new Position(localX, Integer.MAX_VALUE, localZ);
        List<CellPosition> result = new ArrayList<>();
        cells.subMap(first, true, last, true).keySet().forEach(position ->
                result.add(new CellPosition(position.x, position.y, position.z)));
        return List.copyOf(result);
    }

    /** Alias documenting that returned cell entries are an immutable snapshot. */
    public Map<CellPosition, GasMixture> entries() { return snapshot(); }

    private List<Cell> cellsForCodec() {
        List<Cell> result = new ArrayList<>(cells.size());
        cells.forEach((position, state) -> result.add(Cell.from(position, state)));
        return result;
    }

    private List<CellPosition> finiteCellsForCodec() { return finiteClaimSnapshot(); }

    private static AtmosphereChunkData decode(int version, List<Cell> decodedCells, List<CellPosition> decodedClaims) {
        AtmosphereChunkData data = new AtmosphereChunkData();
        if (version != 1 && version != SCHEMA_VERSION) {
            data.codecError = "unsupported atmosphere chunk schema version: " + version;
            return data;
        }
        for (Cell cell : decodedCells) {
            if (data.cells.containsKey(cell.position)) {
                data.codecError = "duplicate local coordinate: " + cell.position;
                return data;
            }
            try {
                Map<GasType, Double> moles = new java.util.EnumMap<>(GasType.class);
                for (GasAmount amount : cell.gases) {
                    if (moles.putIfAbsent(amount.type, amount.moles) != null) {
                        data.codecError = "duplicate gas id: " + amount.type.id();
                        return data;
                    }
                }
                data.cells.put(cell.position, new GasMixture(moles, cell.temperature));
                // Version 1 had no separate ownership record. Preserve every old override as a
                // finite claim; potentially sky-exposed legacy gas requires explicit later policy,
                // and must not be silently deleted during decoding.
                if (version == 1) data.finiteClaims.add(cell.position);
            } catch (IllegalArgumentException exception) {
                data.codecError = exception.getMessage();
                return data;
            }
        }
        for (CellPosition cell : decodedClaims) {
            Position position = position(cell.x(), cell.y(), cell.z());
            if (!data.finiteClaims.add(position)) {
                data.codecError = "duplicate finite local coordinate: " + position;
                return data;
            }
        }
        return data;
    }

    private static boolean stateEquals(GasMixture left, GasMixture right) {
        return left != null && left.temperatureKelvin() == right.temperatureKelvin()
                && left.gasMoles().equals(right.gasMoles());
    }

    private static Position position(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) throw new IllegalArgumentException("local x/z must be in [0, 15]");
        return new Position(x, y, z);
    }

    private static int comparePositions(Position left, Position right) {
        int x = Integer.compare(left.x, right.x);
        if (x != 0) return x;
        int z = Integer.compare(left.z, right.z);
        return z != 0 ? z : Integer.compare(left.y, right.y);
    }

    public record CellPosition(int x, int y, int z) { }
    private record Position(int x, int y, int z) { }
    private record GasAmount(GasType type, double moles) { }
    private record Cell(Position position, double temperature, List<GasAmount> gases) {
        private static Cell from(Position position, GasMixture state) {
            List<GasAmount> gases = state.gasMoles().entrySet().stream()
                    .map(entry -> new GasAmount(entry.getKey(), entry.getValue())).toList();
            return new Cell(position, state.temperatureKelvin(), gases);
        }
    }
}
