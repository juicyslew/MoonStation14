package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Detached, sparse persistence for gas-bearing pipe segments in one chunk. */
public final class PipeChunkData {
    public static final int SCHEMA_VERSION = 2;
    /** Provisional persistence bound pending profiling and approval. */
    public static final int MAX_RECORDS = 4096;
    private static final int ALL_PORTS = (1 << Direction.values().length) - 1;
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? DataResult.success(id) : DataResult.error(() -> "invalid pipe segment UUID");
        }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "invalid pipe segment UUID"); }
    }, UUID::toString);
    private static final Codec<PipeTopology.Kind> KIND_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(PipeTopology.Kind.valueOf(value)); }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "unknown pipe segment kind: " + value); }
    }, PipeTopology.Kind::name);
    private static final Codec<PipeTopology.Layer> LAYER_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(PipeTopology.Layer.valueOf(value)); }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "unknown pipe layer: " + value); }
    }, PipeTopology.Layer::name);
    private static final Codec<Double> VOLUME_CODEC = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value > 0.0 ? DataResult.success(value)
                    : DataResult.error(() -> "pipe volume must be positive and finite"));
    private static final Codec<Integer> PORTS_CODEC = Codec.INT.validate(value ->
            value >= 0 && (value & ~ALL_PORTS) == 0 ? DataResult.success(value)
                    : DataResult.error(() -> "invalid pipe port mask"));
    private static final Codec<String> HOST_CODEC = Codec.STRING.validate(PipeChunkData::validateHost);
    private static final Codec<String> INCARNATION_CODEC = Codec.STRING.validate(PipeChunkData::validateIncarnation);
    private static final Codec<RawRecord> RAW_RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 15).fieldOf("x").forGetter(RawRecord::x),
            Codec.INT.fieldOf("y").forGetter(RawRecord::y),
            Codec.intRange(0, 15).fieldOf("z").forGetter(RawRecord::z),
            LAYER_CODEC.optionalFieldOf("layer").forGetter(RawRecord::layer),
            UUID_CODEC.fieldOf("id").forGetter(RawRecord::id),
            KIND_CODEC.fieldOf("kind").forGetter(RawRecord::kind),
            PORTS_CODEC.fieldOf("ports").forGetter(RawRecord::ports),
            VOLUME_CODEC.fieldOf("volume").forGetter(RawRecord::volume),
            HOST_CODEC.optionalFieldOf("host", "").forGetter(RawRecord::host),
            INCARNATION_CODEC.optionalFieldOf("host_incarnation", "").forGetter(RawRecord::hostIncarnation)
    ).apply(instance, RawRecord::new));
    private static final Codec<RawRecord> RECORD_CODEC = RAW_RECORD_CODEC;
    private static final Codec<PipeChunkData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(data -> SCHEMA_VERSION),
            RECORD_CODEC.listOf().validate(records -> records.size() <= MAX_RECORDS
                    ? DataResult.success(records) : DataResult.error(() -> "pipe chunk exceeds record limit")).fieldOf("records").forGetter(data -> data.snapshot().stream().map(record ->
                            new RawRecord(record.x(), record.y(), record.z(), Optional.of(record.layer()), record.id(), record.kind(), record.ports(), record.volume(), record.host(), record.hostIncarnation())).toList())
    ).apply(instance, PipeChunkData::decode));
    public static final Codec<PipeChunkData> CODEC = STRUCTURAL_CODEC.flatXmap(
            data -> data.codecError == null ? DataResult.success(data) : DataResult.error(() -> data.codecError), DataResult::success);

    private final NavigableMap<Key, Record> records = new TreeMap<>();
    private String codecError;

    public boolean add(Record record) {
        Objects.requireNonNull(record, "record");
        if (records.containsKey(new Key(record.x, record.y, record.z, record.layer)) || records.size() >= MAX_RECORDS
                || records.values().stream().anyMatch(existing -> existing.id.equals(record.id))) return false;
        records.put(new Key(record.x, record.y, record.z, record.layer), record);
        return true;
    }

    public Record get(int x, int y, int z) { return get(x, y, z, PipeTopology.Layer.PRIMARY); }
    public Record get(int x, int y, int z, PipeTopology.Layer layer) { return records.get(new Key(x, y, z, layer)); }
    public boolean remove(int x, int y, int z) { return remove(x, y, z, PipeTopology.Layer.PRIMARY); }
    public boolean remove(int x, int y, int z, PipeTopology.Layer layer) { return records.remove(new Key(x, y, z, layer)) != null; }
    public int size() { return records.size(); }
    public boolean isEmpty() { return records.isEmpty(); }
    public List<Record> snapshot() { return List.copyOf(records.values()); }

    private static PipeChunkData decode(int version, List<RawRecord> decoded) {
        PipeChunkData data = new PipeChunkData();
        if (version != 1 && version != SCHEMA_VERSION) data.codecError = "unsupported pipe chunk schema version: " + version;
        else if (decoded.size() > MAX_RECORDS) data.codecError = "pipe chunk exceeds record limit";
        else for (RawRecord raw : decoded) {
            if (version == 1 && raw.layer.isPresent()) { data.codecError = "schema version 1 does not support pipe layers"; break; }
            if (version == SCHEMA_VERSION && raw.layer.isEmpty()) { data.codecError = "schema version 2 requires pipe layer"; break; }
            DataResult<Record> result = toRecord(raw);
            if (result.error().isPresent()) { data.codecError = result.error().get().message(); break; }
            Record record = result.result().orElseThrow();
            if (!data.add(record)) { data.codecError = "duplicate pipe coordinate/layer or segment ID"; break; }
        }
        return data;
    }

    private static DataResult<Record> toRecord(RawRecord raw) {
        try {
            return DataResult.success(new Record(raw.x, raw.y, raw.z, raw.id, raw.kind, raw.ports, raw.volume,
                    raw.host, raw.hostIncarnation, raw.layer.orElse(PipeTopology.Layer.PRIMARY)));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return DataResult.error(() -> exception.getMessage() == null ? "invalid pipe record" : exception.getMessage());
        }
    }

    private record RawRecord(int x, int y, int z, Optional<PipeTopology.Layer> layer, UUID id, PipeTopology.Kind kind, int ports, double volume,
                             String host, String hostIncarnation) { }

    private static DataResult<String> validateHost(String host) {
        if (host.isEmpty()) return DataResult.success(host);
        try {
            return host.length() <= 128 && ResourceLocation.tryParse(host) != null
                    ? DataResult.success(host) : DataResult.error(() -> "invalid pipe host identifier");
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "invalid pipe host identifier");
        }
    }

    private static DataResult<String> validateIncarnation(String incarnation) {
        if (incarnation.isEmpty()) return DataResult.success(incarnation);
        if (incarnation.length() > 128) return DataResult.error(() -> "invalid pipe host incarnation");
        try {
            UUID value = UUID.fromString(incarnation);
            return value.toString().equalsIgnoreCase(incarnation) ? DataResult.success(incarnation)
                    : DataResult.error(() -> "invalid pipe host incarnation");
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "invalid pipe host incarnation");
        }
    }

    private record Key(int x, int y, int z, PipeTopology.Layer layer) implements Comparable<Key> {
        private static final Comparator<Key> ORDER = Comparator.comparingInt(Key::x).thenComparingInt(Key::z).thenComparingInt(Key::y);
        private Key {
            if (x < 0 || x > 15 || z < 0 || z > 15) throw new IllegalArgumentException("local x/z must be in [0, 15]");
            Objects.requireNonNull(layer, "layer");
        }
        @Override public int compareTo(Key other) { int order = ORDER.compare(this, other); return order != 0 ? order : layer.compareTo(other.layer); }
    }

    /** Host metadata is only a persistence hint; it never establishes a current seal or identity. */
    public record Record(int x, int y, int z, UUID id, PipeTopology.Kind kind, int ports, double volume,
                        String host, String hostIncarnation, PipeTopology.Layer layer) {
        public Record(int x, int y, int z, UUID id, PipeTopology.Kind kind, int ports, double volume, String host, String hostIncarnation) {
            this(x, y, z, id, kind, ports, volume, host, hostIncarnation, PipeTopology.Layer.PRIMARY);
        }
        public Record {
            new Key(x, y, z, layer);
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(layer, "layer");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(hostIncarnation, "hostIncarnation");
            if (ports < 0 || (ports & ~ALL_PORTS) != 0) throw new IllegalArgumentException("invalid pipe port mask");
            if (!Double.isFinite(volume) || volume <= 0.0) throw new IllegalArgumentException("volume must be positive and finite");
            if (kind == PipeTopology.Kind.STRAIGHT_SEALED_WALL && Integer.bitCount(ports) != 2) throw new IllegalArgumentException("sealed-wall segment requires exactly two opposite ports");
            if (kind == PipeTopology.Kind.STRAIGHT_SEALED_WALL && (ports & oppositeMask(ports)) == 0) throw new IllegalArgumentException("sealed-wall segment requires opposite ports");
            validateHost(host).getOrThrow(message -> new IllegalArgumentException(message));
            validateIncarnation(hostIncarnation).getOrThrow(message -> new IllegalArgumentException(message));
            if ((host.isEmpty()) != (hostIncarnation.isEmpty())) throw new IllegalArgumentException("host ID and incarnation must both be present or absent");
            if (kind == PipeTopology.Kind.STRAIGHT_SEALED_WALL && host.isEmpty()) throw new IllegalArgumentException("sealed-wall segment requires host metadata");
        }
        private static int oppositeMask(int mask) {
            int result = 0;
            for (Direction direction : Direction.values()) if ((mask & (1 << direction.ordinal())) != 0)
                result |= 1 << direction.getOpposite().ordinal();
            return result;
        }
        public static int portMask(Iterable<Direction> ports) {
            int result = 0;
            for (Direction direction : ports) result |= 1 << Objects.requireNonNull(direction).ordinal();
            return result;
        }
    }
}

