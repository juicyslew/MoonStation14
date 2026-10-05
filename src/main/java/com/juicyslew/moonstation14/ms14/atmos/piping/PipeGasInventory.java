package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.*;

/** Detached immutable finite gas ownership snapshot. It has no world or ticker dependency. */
public final class PipeGasInventory {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RECORDS = 65_536;
    private static final Codec<Double> FINITE = Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= 0
            ? DataResult.success(v) : DataResult.error(() -> "value must be finite and nonnegative"));
    private static final Codec<GasType> GAS = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(GasType.fromId(id)); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, GasType::id);
    private record Amount(GasType type, double moles) { }
    private record Record(int x, int y, int z, String layer, String id, double volume, long version,
                          double temperature, List<Amount> gases) { }
    private static final Codec<Amount> AMOUNT = RecordCodecBuilder.create(i -> i.group(
            GAS.fieldOf("id").forGetter(Amount::type), FINITE.fieldOf("moles").forGetter(Amount::moles))
            .apply(i, Amount::new));
    private static final Codec<Record> RECORD = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Record::x), Codec.INT.fieldOf("y").forGetter(Record::y), Codec.INT.fieldOf("z").forGetter(Record::z),
            Codec.STRING.fieldOf("layer").forGetter(Record::layer), Codec.STRING.fieldOf("uuid").forGetter(Record::id),
            FINITE.validate(v -> v > 0 ? DataResult.success(v) : DataResult.error(() -> "volume must be positive")).fieldOf("volume").forGetter(Record::volume),
             Codec.LONG.validate(v -> v > 0 ? DataResult.success(v) : DataResult.error(() -> "entry version must be positive")).fieldOf("entry_version").forGetter(Record::version),
            FINITE.fieldOf("temperature").forGetter(Record::temperature), AMOUNT.listOf().fieldOf("gases").forGetter(Record::gases))
            .apply(i, Record::new));
    private record Saved(int schema, long revision, List<Record> records, List<Record> retired) { }
    private static final Codec<Saved> STRUCT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("version").forGetter(Saved::schema),
            Codec.LONG.validate(v -> v >= 0 ? DataResult.success(v) : DataResult.error(() -> "revision must be nonnegative")).fieldOf("revision").forGetter(Saved::revision),
            RECORD.listOf().fieldOf("entries").forGetter(Saved::records),
            RECORD.listOf().optionalFieldOf("retired_entries", List.of()).forGetter(Saved::retired))
            .apply(i, Saved::new));
    public static final Codec<PipeGasInventory> CODEC = STRUCT.flatXmap(s -> {
         PipeGasInventory v = decode(s.schema(), s.revision(), s.records(), s.retired());
        return v.error == null ? DataResult.success(v) : DataResult.error(() -> v.error);
    }, v -> v.error == null
             ? DataResult.success(new Saved(SCHEMA_VERSION, v.revision, v.records(), v.retiredRecords())) : DataResult.error(() -> v.error));

    public record Entry(PipeTopology.NodeKey node, UUID incarnation, double volume, GasMixture mixture,
                        long version, boolean active) {
        public Entry { Objects.requireNonNull(node); Objects.requireNonNull(incarnation); Objects.requireNonNull(mixture); }
    }
    public record ExpectedIdentityVersionAndGasAfter(UUID incarnation, long expectedVersion, GasMixture gasAfter) {
        public ExpectedIdentityVersionAndGasAfter { Objects.requireNonNull(incarnation); Objects.requireNonNull(gasAfter); }
    }
    private final Map<PipeTopology.NodeKey, Entry> entries;
    private final Map<UUID, Entry> retired;
    private final long revision;
    private final String error;

    public PipeGasInventory() { this(Map.of(), Map.of(), 0, null); }
    private PipeGasInventory(Map<PipeTopology.NodeKey, Entry> entries, Map<UUID, Entry> retired, long revision, String error) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries)); this.retired = Collections.unmodifiableMap(new LinkedHashMap<>(retired)); this.revision = revision; this.error = error;
    }
    public long revision() { return revision; }
    public int size() { return entries.size(); }
    public Optional<Entry> get(PipeTopology.NodeKey key) { return Optional.ofNullable(entries.get(key)); }
    public Map<PipeTopology.NodeKey, Entry> snapshot() { return entries; }
    public Map<UUID, Entry> retiredSnapshot() { return retired; }

    public PipeGasInventory admit(PipeTopology.NodeKey key, UUID id, double volume) {
        Objects.requireNonNull(key); Objects.requireNonNull(id); validVolume(volume);
        if (retired.containsKey(id)) throw new IllegalStateException("retired pipe incarnation cannot be admitted");
        for (var entry : entries.entrySet())
            if (!entry.getKey().equals(key) && entry.getValue().incarnation().equals(id))
                throw new IllegalStateException("pipe incarnation is already owned by another node");
        Entry old = entries.get(key);
        if (old != null) {
            if (!old.incarnation().equals(id) || Double.compare(old.volume(), volume) != 0 || old.active()) throw new IllegalStateException("stale, mismatched, or duplicate pipe admission");
            return change(key, new Entry(key, id, volume, old.mixture(), increment(old.version()), true));
        }
        if ((long) entries.size() + retired.size() >= MAX_RECORDS) throw new IllegalStateException("pipe inventory record limit exceeded");
        return change(key, new Entry(key, id, volume, GasMixture.vacuum(), 1, true));
    }
    public PipeGasInventory unload(PipeTopology.NodeKey key, UUID id) {
        Entry old = require(key, id); if (!old.active()) return this;
        return change(key, new Entry(key, id, old.volume(), old.mixture(), increment(old.version()), false));
    }
    /** Trusted test/transaction seam: callers must independently provide a conserved transfer plan. */
    public PipeGasInventory withMixture(PipeTopology.NodeKey key, UUID id, long expectedVersion, GasMixture next) {
        Objects.requireNonNull(next); Entry old = require(key, id);
        if (!old.active() || old.version() != expectedVersion) throw new IllegalStateException("stale pipe gas update");
        return change(key, new Entry(key, id, old.volume(), next, increment(old.version()), true));
    }
    /** Applies a conserved multi-node update as one immutable inventory revision. */
    public PipeGasInventory withBatchMixtures(Map<PipeTopology.NodeKey, ExpectedIdentityVersionAndGasAfter> updates) {
        Objects.requireNonNull(updates);
        if (updates.isEmpty() || updates.size() > MAX_RECORDS) throw new IllegalArgumentException("invalid batch size");
        EnumMap<GasType, Double> beforeGas = new EnumMap<>(GasType.class), afterGas = new EnumMap<>(GasType.class);
        double beforeEnergy = 0, afterEnergy = 0;
        Map<PipeTopology.NodeKey, Entry> changed = new LinkedHashMap<>();
        boolean anyChange = false;
        for (var update : updates.entrySet()) {
            Entry old = entries.get(Objects.requireNonNull(update.getKey()));
            var expected = Objects.requireNonNull(update.getValue());
            if (old == null || !old.active() || !old.incarnation().equals(expected.incarnation()) || old.version() != expected.expectedVersion())
                throw new IllegalStateException("unknown, dormant, or stale pipe gas update");
            GasMixture next = expected.gasAfter(); validVolume(old.volume());
            if (sameMixture(old.mixture(), next)) continue;
            anyChange = true;
            add(beforeGas, old.mixture()); add(afterGas, next);
            beforeEnergy += old.mixture().thermalEnergy(); afterEnergy += next.thermalEnergy();
            changed.put(update.getKey(), new Entry(old.node(), old.incarnation(), old.volume(), next, increment(old.version()), true));
        }
        if (!anyChange) throw new IllegalArgumentException("batch contains no changes");
        for (GasType type : GasType.values()) if (!conserved(beforeGas.getOrDefault(type, 0.0), afterGas.getOrDefault(type, 0.0)))
            throw new IllegalArgumentException("batch does not conserve " + type);
        if (!conserved(beforeEnergy, afterEnergy)) throw new IllegalArgumentException("batch does not conserve thermal energy");
        Map<PipeTopology.NodeKey, Entry> result = new LinkedHashMap<>(entries); result.putAll(changed);
        return new PipeGasInventory(result, retired, increment(revision), null);
    }
    private static boolean sameMixture(GasMixture a, GasMixture b) { return a.gasMoles().equals(b.gasMoles()) && Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) == 0; }
    private static void add(Map<GasType, Double> total, GasMixture mixture) {
        for (var gas : mixture.gasMoles().entrySet()) total.merge(gas.getKey(), gas.getValue(), Double::sum);
    }
    private static boolean conserved(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) return false;
        return Math.abs(a - b) <= Math.max(Math.ulp(a) * 8, Math.max(Math.ulp(b) * 8, Math.max(Math.abs(a), Math.abs(b)) * 1e-14));
    }
    /** Removes only empty ownership; non-empty gas and energy require a separately accounted transfer. */
    public PipeGasInventory tombstoneEmpty(PipeTopology.NodeKey key, UUID id) {
        Entry old = require(key, id);
        if (!old.mixture().gasMoles().isEmpty() || old.mixture().thermalEnergy() != 0.0)
            throw new IllegalStateException("cannot remove non-vacuum pipe ownership without an accounted transfer");
        Map<PipeTopology.NodeKey, Entry> copy = new LinkedHashMap<>(entries); copy.remove(key);
        return new PipeGasInventory(copy, retired, increment(revision), null);
    }
    /** Retires this exact incarnation; non-vacuum ownership is preserved in an inactive UUID archive. */
    public PipeGasInventory retire(PipeTopology.NodeKey key, UUID id) {
        Entry old = entries.get(Objects.requireNonNull(key));
        if (old == null || !old.incarnation().equals(Objects.requireNonNull(id))) return this;
        return retireBatch(Map.of(key, id));
    }
    /** Retires exact lane owners in one immutable revision, preserving all non-vacuum gas in the archive. */
    public PipeGasInventory retireBatch(Map<PipeTopology.NodeKey, UUID> exactOwners) {
        Objects.requireNonNull(exactOwners);
        if (exactOwners.isEmpty()) return this;
        if (exactOwners.size() > MAX_RECORDS) throw new IllegalArgumentException("invalid retirement batch size");
        for (var owner : exactOwners.entrySet()) {
            Entry entry = entries.get(Objects.requireNonNull(owner.getKey()));
            UUID id = Objects.requireNonNull(owner.getValue());
            if (entry == null || !entry.incarnation().equals(id)) throw new IllegalStateException("unknown or stale pipe incarnation in retirement batch");
        }
        Map<PipeTopology.NodeKey, Entry> live = new LinkedHashMap<>(entries);
        Map<UUID, Entry> archive = new LinkedHashMap<>(retired);
        for (var owner : exactOwners.entrySet()) {
            Entry old = live.remove(owner.getKey());
            if (!old.mixture().gasMoles().isEmpty() || old.mixture().thermalEnergy() != 0.0)
                archive.put(old.incarnation(), new Entry(old.node(), old.incarnation(), old.volume(), old.mixture(), old.version(), false));
        }
        return new PipeGasInventory(live, archive, increment(revision), null);
    }
    private Entry require(PipeTopology.NodeKey key, UUID id) {
        Entry e = entries.get(Objects.requireNonNull(key));
        if (e == null || !e.incarnation().equals(Objects.requireNonNull(id))) throw new IllegalStateException("unknown or stale pipe incarnation");
        return e;
    }
    private PipeGasInventory change(PipeTopology.NodeKey key, Entry value) {
        Map<PipeTopology.NodeKey, Entry> copy = new LinkedHashMap<>(entries); copy.put(key, value);
        return new PipeGasInventory(copy, retired, increment(revision), null);
    }
    private static long increment(long value) { return Math.addExact(value, 1L); }
    private static void validVolume(double volume) { if (!Double.isFinite(volume) || volume <= 0) throw new IllegalArgumentException("volume must be positive and finite"); }
    private List<Record> records() {
        List<Entry> sorted = new ArrayList<>(entries.values()); sorted.sort(Comparator.comparingInt((Entry e) -> e.node().position().getX()).thenComparingInt(e -> e.node().position().getY()).thenComparingInt(e -> e.node().position().getZ()).thenComparing(e -> e.node().layer()));
        List<Record> result = new ArrayList<>();
        for (Entry e : sorted) result.add(toRecord(e));
        return result;
    }
    private List<Record> retiredRecords() { return retired.values().stream().map(PipeGasInventory::toRecord).toList(); }
    private static Record toRecord(Entry e) { return new Record(e.node().position().getX(), e.node().position().getY(), e.node().position().getZ(), e.node().layer().name(), e.incarnation().toString(), e.volume(), e.version(), e.mixture().temperatureKelvin(), e.mixture().gasMoles().entrySet().stream().map(a -> new Amount(a.getKey(), a.getValue())).toList()); }
    private static PipeGasInventory decode(int version, long revision, List<Record> records, List<Record> archived) {
        Map<PipeTopology.NodeKey, Entry> result = new LinkedHashMap<>(); Map<UUID, Entry> retired = new LinkedHashMap<>(); String error = null;
        if (version != SCHEMA_VERSION) error = "unsupported pipe inventory version: " + version;
        else if (revision < 0) error = "negative inventory revision";
        else if ((long) records.size() + archived.size() > MAX_RECORDS) error = "pipe inventory record limit exceeded";
        Set<UUID> ids = new HashSet<>();
        if (error == null) for (Record r : records) try {
            PipeTopology.Layer layer = PipeTopology.Layer.valueOf(r.layer()); UUID id = UUID.fromString(r.id()); validVolume(r.volume());
            if (r.version() <= 0) throw new IllegalArgumentException("entry version must be positive");
            PipeTopology.NodeKey key = new PipeTopology.NodeKey(new BlockPos(r.x(), r.y(), r.z()), layer);
            EnumMap<GasType, Double> gas = new EnumMap<>(GasType.class);
            for (Amount a : r.gases()) if (gas.putIfAbsent(a.type(), a.moles()) != null) throw new IllegalArgumentException("duplicate gas id");
            Entry entry = new Entry(key, id, r.volume(), new GasMixture(gas, r.temperature()), r.version(), false);
            if (result.putIfAbsent(key, entry) != null) throw new IllegalArgumentException("duplicate pipe node");
            if (!ids.add(id)) throw new IllegalArgumentException("duplicate pipe UUID");
        } catch (RuntimeException e) { error = "invalid pipe inventory record: " + e.getMessage(); break; }
        if (error == null) for (Record r : archived) try {
            Entry e = parseRecord(r, false);
            if (!ids.add(e.incarnation()) || retired.putIfAbsent(e.incarnation(), e) != null) throw new IllegalArgumentException("duplicate pipe UUID");
        } catch (RuntimeException e) { error = "invalid retired pipe inventory record: " + e.getMessage(); break; }
        return new PipeGasInventory(result, retired, revision, error);
    }
    private static Entry parseRecord(Record r, boolean active) { PipeTopology.Layer layer = PipeTopology.Layer.valueOf(r.layer()); UUID id = UUID.fromString(r.id()); validVolume(r.volume()); if (r.version() <= 0) throw new IllegalArgumentException("entry version must be positive"); PipeTopology.NodeKey key = new PipeTopology.NodeKey(new BlockPos(r.x(), r.y(), r.z()), layer); EnumMap<GasType, Double> gas = new EnumMap<>(GasType.class); for (Amount a : r.gases()) if (gas.putIfAbsent(a.type(), a.moles()) != null) throw new IllegalArgumentException("duplicate gas id"); return new Entry(key, id, r.volume(), new GasMixture(gas, r.temperature()), r.version(), active); }
}
