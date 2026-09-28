package com.juicyslew.moonstation14.ms14.power.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;

import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Sparse, versioned local-host-face cable records for one chunk. */
public final class CableChunkData {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_RECORDS = 4096;
    private static final Codec<Direction> FACE_CODEC = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(Direction.valueOf(id)); }
        catch (IllegalArgumentException exception) { return DataResult.error(() -> "unknown cable face: " + id); }
    }, Direction::name);
    private static final Codec<CableTier> TIER_CODEC = Codec.STRING.comapFlatMap(id -> {
        for (CableTier tier : CableTier.values()) if (tier.getSerializedName().equals(id)) return DataResult.success(tier);
        return DataResult.error(() -> "unknown cable tier: " + id);
    }, CableTier::getSerializedName);
    private static final Codec<Record> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 15).fieldOf("x").forGetter(Record::x),
            Codec.INT.fieldOf("y").forGetter(Record::y),
            Codec.intRange(0, 15).fieldOf("z").forGetter(Record::z),
            FACE_CODEC.fieldOf("face").forGetter(Record::face),
            TIER_CODEC.fieldOf("tier").forGetter(Record::tier),
            Codec.STRING.optionalFieldOf("host", "").forGetter(Record::host)
    ).apply(instance, Record::new));
    private static final Codec<CableChunkData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(data -> SCHEMA_VERSION),
            RECORD_CODEC.listOf().fieldOf("records").forGetter(CableChunkData::snapshot)
    ).apply(instance, CableChunkData::decode));
    public static final Codec<CableChunkData> CODEC = STRUCTURAL_CODEC.flatXmap(
            data -> data.codecError == null ? DataResult.success(data) : DataResult.error(() -> data.codecError), DataResult::success);

    private final NavigableMap<Key, StoredRecord> records = new TreeMap<>();
    private String codecError;

    public boolean put(int x, int y, int z, Direction face, CableTier tier) {
        return put(x, y, z, face, tier, "");
    }

    public boolean put(int x, int y, int z, Direction face, CableTier tier, String host) {
        validate(x, z);
        java.util.Objects.requireNonNull(tier, "tier");
        java.util.Objects.requireNonNull(host, "host");
        Key key = new Key(x, y, z, face, tier);
        if (records.containsKey(key) || records.size() >= MAX_RECORDS) return false;
        records.put(key, new StoredRecord(host));
        return true;
    }

    public boolean contains(int x, int y, int z, Direction face, CableTier tier) {
        validate(x, z);
        return records.containsKey(new Key(x, y, z, face, tier));
    }

    public CableTier get(int x, int y, int z, Direction face) {
        validate(x, z);
        List<Key> matches = matching(x, y, z, face);
        return matches.size() == 1 ? matches.get(0).tier : null;
    }

    public CableTier get(int x, int y, int z, Direction face, CableTier tier) {
        validate(x, z);
        return records.containsKey(new Key(x, y, z, face, tier)) ? tier : null;
    }

    public String host(int x, int y, int z, Direction face) {
        validate(x, z);
        List<Key> matches = matching(x, y, z, face);
        if (matches.size() != 1) return null;
        StoredRecord record = records.get(matches.get(0));
        return record == null ? null : record.host;
    }

    public String host(int x, int y, int z, Direction face, CableTier tier) {
        validate(x, z);
        StoredRecord record = records.get(new Key(x, y, z, face, tier));
        return record == null ? null : record.host;
    }

    public int removeHostUnlessIdentity(int x, int y, int z, String host) {
        validate(x, z);
        int before = records.size();
        records.entrySet().removeIf(entry -> entry.getKey().x == x && entry.getKey().y == y
                && entry.getKey().z == z && !entry.getValue().host.equals(host));
        return before - records.size();
    }

    public boolean remove(int x, int y, int z, Direction face) {
        validate(x, z);
        java.util.Objects.requireNonNull(face, "face");
        List<Key> matches = matching(x, y, z, face);
        return matches.size() == 1 && records.remove(matches.get(0)) != null;
    }

    public boolean remove(int x, int y, int z, Direction face, CableTier tier) {
        validate(x, z);
        return records.remove(new Key(x, y, z, face, tier)) != null;
    }

    public int removeHost(int x, int y, int z) {
        validate(x, z);
        int before = records.size();
        records.keySet().removeIf(key -> key.x == x && key.y == y && key.z == z);
        return before - records.size();
    }

    public int size() { return records.size(); }
    public boolean isEmpty() { return records.isEmpty(); }
    public boolean hasPersistedState() { return !records.isEmpty(); }

    public List<Record> snapshot() {
        return records.entrySet().stream().map(entry -> new Record(entry.getKey().x, entry.getKey().y,
                entry.getKey().z, entry.getKey().face, entry.getKey().tier, entry.getValue().host)).toList();
    }

    private static CableChunkData decode(int version, List<Record> decoded) {
        CableChunkData data = new CableChunkData();
        if (version != 1 && version != SCHEMA_VERSION) {
            data.codecError = "unsupported cable chunk schema version: " + version;
        } else if (decoded.size() > MAX_RECORDS) {
            data.codecError = "cable chunk exceeds record limit";
        } else {
            java.util.HashSet<String> legacyFaces = new java.util.HashSet<>();
            for (Record record : decoded) {
                if (version == 1 && !legacyFaces.add(record.x + ":" + record.y + ":" + record.z + ":" + record.face)) {
                    data.codecError = "duplicate host face in legacy cable data";
                    break;
                }
                if (!data.put(record.x, record.y, record.z, record.face, record.tier, record.host)) {
                    data.codecError = "duplicate cable record or cable record limit exceeded";
                    break;
                }
            }
        }
        return data;
    }

    private List<Key> matching(int x, int y, int z, Direction face) {
        java.util.Objects.requireNonNull(face, "face");
        return records.keySet().stream().filter(key -> key.x == x && key.y == y && key.z == z && key.face == face).toList();
    }

    private static void validate(int x, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) throw new IllegalArgumentException("local x/z must be in [0, 15]");
    }

    private record Key(int x, int y, int z, Direction face, CableTier tier) implements Comparable<Key> {
        private static final Comparator<Key> ORDER = Comparator.comparingInt(Key::x).thenComparingInt(Key::z)
                .thenComparingInt(Key::y).thenComparingInt(key -> key.face.ordinal()).thenComparingInt(key -> key.tier.ordinal());
        private Key { java.util.Objects.requireNonNull(face); java.util.Objects.requireNonNull(tier); }
        @Override public int compareTo(Key other) { return ORDER.compare(this, other); }
    }
    private record StoredRecord(String host) { }
    public record Record(int x, int y, int z, Direction face, CableTier tier, String host) {
        public Record(int x, int y, int z, Direction face, CableTier tier) {
            this(x, y, z, face, tier, "");
        }
    }
}
