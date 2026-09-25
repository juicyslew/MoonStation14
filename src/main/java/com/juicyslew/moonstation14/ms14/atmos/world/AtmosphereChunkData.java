package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sparse per-chunk overrides. Missing cells inherit their dimension ambient state. */
public final class AtmosphereChunkData {
    public static final int SCHEMA_VERSION = 1;

    private static final Codec<Double> NONNEGATIVE_FINITE = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= 0.0 ? DataResult.success(value)
                    : DataResult.error(() -> "value must be finite and nonnegative"));
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
            Codec.INT.fieldOf("y").forGetter(cell -> cell.position.y),
            Codec.intRange(0, 15).fieldOf("z").forGetter(cell -> cell.position.z),
            NONNEGATIVE_FINITE.fieldOf("temperature").forGetter(Cell::temperature),
            GAS_CODEC.listOf().fieldOf("gases").forGetter(Cell::gases)
    ).apply(instance, (x, y, z, temperature, gases) -> new Cell(new Position(x, y, z), temperature, gases)));
    private static final Codec<AtmosphereChunkData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(data -> SCHEMA_VERSION),
            CELL_CODEC.listOf().fieldOf("cells").forGetter(data -> data.cellsForCodec())
    ).apply(instance, AtmosphereChunkData::decode));

    /** Versioned stable-ID representation; invalid or unknown persisted state is rejected. */
    public static final Codec<AtmosphereChunkData> CODEC = STRUCTURAL_CODEC.flatXmap(
            data -> data.codecError == null ? DataResult.success(data) : DataResult.error(() -> data.codecError),
            DataResult::success);

    private final Map<Position, GasMixture> cells = new LinkedHashMap<>();
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

    /** Immutable coordinate-to-state snapshot suitable for re-enqueueing work. */
    public Map<CellPosition, GasMixture> snapshot() {
        Map<CellPosition, GasMixture> result = new LinkedHashMap<>();
        cells.forEach((position, state) -> result.put(new CellPosition(position.x, position.y, position.z), state));
        return Collections.unmodifiableMap(result);
    }

    /** Alias documenting that returned cell entries are an immutable snapshot. */
    public Map<CellPosition, GasMixture> entries() { return snapshot(); }

    private List<Cell> cellsForCodec() {
        List<Cell> result = new ArrayList<>(cells.size());
        cells.forEach((position, state) -> result.add(Cell.from(position, state)));
        return result;
    }

    private static AtmosphereChunkData decode(int version, List<Cell> decodedCells) {
        AtmosphereChunkData data = new AtmosphereChunkData();
        if (version != SCHEMA_VERSION) {
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
            } catch (IllegalArgumentException exception) {
                data.codecError = exception.getMessage();
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
