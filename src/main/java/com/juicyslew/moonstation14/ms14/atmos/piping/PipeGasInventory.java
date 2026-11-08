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
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_RECORDS = 65_536;
    public static final double DEFAULT_PIPE_TEMPERATURE_KELVIN = 293.15;
    private static final Codec<Double> FINITE = Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= 0
            ? DataResult.success(v) : DataResult.error(() -> "value must be finite and nonnegative"));
    private static final Codec<Double> TEMPERATURE = Codec.DOUBLE.validate(v -> Double.isFinite(v)
            && v >= GasMixture.MIN_TEMPERATURE_KELVIN && v <= GasMixture.MAX_TEMPERATURE_KELVIN
            ? DataResult.success(v) : DataResult.error(() -> "temperature outside supported finite range"));
    private static final Codec<GasType> GAS = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(GasType.fromId(id)); }
        catch (IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    }, GasType::id);
    private record Amount(GasType type, double moles) { }
    private record Record(int x, int y, int z, String layer, String port, String id, double volume, long version) { }
    private record NetworkRecord(String id, long generation, long revision, List<String> members,
                                 double temperature, List<Amount> gases) { }
    private record RetiredRecord(Record owner, double temperature, List<Amount> gases) { }
    private static final Codec<Amount> AMOUNT = RecordCodecBuilder.create(i -> i.group(
            GAS.fieldOf("id").forGetter(Amount::type), FINITE.fieldOf("moles").forGetter(Amount::moles))
            .apply(i, Amount::new));
    private static final Codec<Record> RECORD = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Record::x), Codec.INT.fieldOf("y").forGetter(Record::y), Codec.INT.fieldOf("z").forGetter(Record::z),
             Codec.STRING.fieldOf("layer").forGetter(Record::layer), Codec.STRING.optionalFieldOf("port", "").forGetter(Record::port), Codec.STRING.fieldOf("uuid").forGetter(Record::id),
            FINITE.validate(v -> v > 0 ? DataResult.success(v) : DataResult.error(() -> "volume must be positive")).fieldOf("volume").forGetter(Record::volume),
             Codec.LONG.validate(v -> v > 0 ? DataResult.success(v) : DataResult.error(() -> "entry version must be positive")).fieldOf("entry_version").forGetter(Record::version))
             .apply(i, Record::new));
    private static final Codec<NetworkRecord> NETWORK_RECORD = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(NetworkRecord::id),
            Codec.LONG.validate(v -> v >= 0 ? DataResult.success(v) : DataResult.error(() -> "negative topology generation")).fieldOf("generation").forGetter(NetworkRecord::generation),
            Codec.LONG.validate(v -> v >= 0 ? DataResult.success(v) : DataResult.error(() -> "negative network revision")).fieldOf("revision").forGetter(NetworkRecord::revision),
            Codec.STRING.listOf().fieldOf("members").forGetter(NetworkRecord::members),
             TEMPERATURE.fieldOf("temperature").forGetter(NetworkRecord::temperature), AMOUNT.listOf().fieldOf("gases").forGetter(NetworkRecord::gases))
            .apply(i, NetworkRecord::new));
    private static final Codec<RetiredRecord> RETIRED_RECORD=RecordCodecBuilder.create(i->i.group(
            RECORD.fieldOf("owner").forGetter(RetiredRecord::owner), TEMPERATURE.fieldOf("temperature").forGetter(RetiredRecord::temperature),
            AMOUNT.listOf().fieldOf("gases").forGetter(RetiredRecord::gases)).apply(i,RetiredRecord::new));
    private record Saved(int schema, long revision, List<Record> records, List<RetiredRecord> retired, List<NetworkRecord> networks) { }
    private static final Codec<Saved> STRUCT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("version").forGetter(Saved::schema),
            Codec.LONG.validate(v -> v >= 0 ? DataResult.success(v) : DataResult.error(() -> "revision must be nonnegative")).fieldOf("revision").forGetter(Saved::revision),
            RECORD.listOf().fieldOf("entries").forGetter(Saved::records),
             RETIRED_RECORD.listOf().fieldOf("retired_entries").forGetter(Saved::retired),
             NETWORK_RECORD.listOf().fieldOf("networks").forGetter(Saved::networks))
            .apply(i, Saved::new));
    public static final Codec<PipeGasInventory> CODEC = STRUCT.flatXmap(s -> {
          PipeGasInventory v = decode(s.schema(), s.revision(), s.records(), s.retired(), s.networks());
        return v.error == null ? DataResult.success(v) : DataResult.error(() -> v.error);
    }, v -> v.error == null
             ? DataResult.success(new Saved(SCHEMA_VERSION, v.revision, v.records(), v.retiredRecords(), v.networkRecords())) : DataResult.error(() -> v.error));

    public record Entry(PipeTopology.NodeKey node, UUID incarnation, double volume,
                        long version, boolean active) {
        public Entry { Objects.requireNonNull(node); Objects.requireNonNull(incarnation); }
    }
    public record Retired(PipeTopology.NodeKey node, UUID incarnation, double volume, long version, GasMixture mixture) {
        public Retired { Objects.requireNonNull(node); Objects.requireNonNull(incarnation); Objects.requireNonNull(mixture); validVolume(volume);
            if(version<=0||Double.compare((double)(float)volume,mixture.volumeCubicMeters())!=0) throw new IllegalArgumentException("invalid retired owner state"); }
    }
    public record Network(UUID id, Set<PipeTopology.NodeKey> members, long topologyGeneration, long gasRevision, GasMixture mixture) {
        public Network { Objects.requireNonNull(id); Objects.requireNonNull(members); members=Set.copyOf(members); Objects.requireNonNull(mixture);
            if(members.isEmpty()||topologyGeneration<0||gasRevision<0||!Double.isFinite(mixture.volumeCubicMeters())||mixture.volumeCubicMeters()<=0)
                throw new IllegalArgumentException("invalid pipe network state"); }
    }
    public record ExpectedIdentityVersionAndGasAfter(UUID incarnation, long expectedVersion, GasMixture gasAfter) {
        public ExpectedIdentityVersionAndGasAfter { Objects.requireNonNull(incarnation); Objects.requireNonNull(gasAfter); }
    }
    public record ExpectedIdentityVersionAndDestination(UUID incarnation, long expectedVersion, PipeTopology.NodeKey destination) {
        public ExpectedIdentityVersionAndDestination { Objects.requireNonNull(incarnation); Objects.requireNonNull(destination); }
    }
    private final Map<PipeTopology.NodeKey, Entry> entries;
    private final Map<UUID, Retired> retired;
    private final Map<UUID, Network> networks;
    private final Map<PipeTopology.NodeKey, UUID> membership;
    private final long revision;
    private final String error;

    public PipeGasInventory() { this(Map.of(), Map.of(), Map.of(), 0, null); }
    private PipeGasInventory(Map<PipeTopology.NodeKey, Entry> entries, Map<UUID, Retired> retired, Map<UUID, Network> networks, long revision, String error) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries)); this.retired = Collections.unmodifiableMap(new LinkedHashMap<>(retired));
        this.networks=Collections.unmodifiableMap(new LinkedHashMap<>(networks)); Map<PipeTopology.NodeKey,UUID> members=new HashMap<>();
        for(Network n:networks.values()) for(PipeTopology.NodeKey key:n.members()) members.put(key,n.id());
        this.membership=Collections.unmodifiableMap(members); this.revision = revision; this.error = error;
    }
    /** Gas-only image: topology maps are immutable and intentionally reused without member traversal. */
    private PipeGasInventory(PipeGasInventory source, Map<UUID,Network> gasNetworks) {
        this.entries=source.entries; this.retired=source.retired; this.networks=Collections.unmodifiableMap(gasNetworks);
        this.membership=source.membership; this.revision=increment(source.revision); this.error=null;
    }
    public long revision() { return revision; }
    public int size() { return entries.size(); }
    public Optional<Entry> get(PipeTopology.NodeKey key) { return Optional.ofNullable(entries.get(key)); }
    public Map<PipeTopology.NodeKey, Entry> snapshot() { return entries; }
    public Map<UUID, Retired> retiredSnapshot() { return retired; }
    public Optional<Network> network(PipeTopology.NodeKey key) { UUID id=membership.get(key); return Optional.ofNullable(id==null?null:networks.get(id)); }
    /** Whole shared-network gas; node shares are projections and never stored. */
    public GasMixture mixture(PipeTopology.NodeKey key) { return network(key).orElseThrow(() -> new IllegalStateException("node has no gas network")).mixture(); }
    public GasMixture nodeShare(PipeTopology.NodeKey key) {
        Network n=network(key).orElseThrow(() -> new IllegalStateException("node has no gas network"));
        Entry entry=entries.get(key); if(entry==null) throw new IllegalStateException("network member metadata missing");
        double fraction=entry.volume()/n.mixture().volumeCubicMeters();
        EnumMap<GasType,Double> gas=new EnumMap<>(GasType.class);
        for(var e:n.mixture().gasMoles().entrySet()) { double amount=e.getValue()*fraction; if(!Double.isFinite(amount)) throw new IllegalStateException("unrepresentable node gas share"); gas.put(e.getKey(),amount); }
        return new GasMixture(gas,n.mixture().temperatureKelvin(),entry.volume());
    }
    public PipeGasInventory withNetworkMixtures(Map<PipeTopology.NodeKey, GasMixture> updates) {
        Objects.requireNonNull(updates); if(updates.isEmpty()) return this;
        Map<UUID,GasMixture> changed=new LinkedHashMap<>();
        for(var e:updates.entrySet()) { Network n=network(e.getKey()).orElseThrow(); GasMixture proposed=Objects.requireNonNull(e.getValue());
            if(Double.compare(proposed.volumeCubicMeters(),n.mixture().volumeCubicMeters())!=0) throw new IllegalArgumentException("network gas update must retain network volume");
            GasMixture old=changed.putIfAbsent(n.id(),proposed);
            if(old!=null && !sameMixture(old,proposed)) throw new IllegalArgumentException("contradictory updates for network aliases"); }
        boolean any=false; for(var e:changed.entrySet()) if(!sameMixture(networks.get(e.getKey()).mixture(),e.getValue())) {any=true;break;}
        if(!any) return this;
        Map<UUID,Network> result=new LinkedHashMap<>(networks);
        for(var e:changed.entrySet()) { Network n=result.get(e.getKey()); if(!sameMixture(n.mixture(),e.getValue())) result.put(n.id(),new Network(n.id(),n.members(),n.topologyGeneration(),increment(n.gasRevision()),e.getValue())); }
        return new PipeGasInventory(this,result);
    }
    /** Rebuild complete topology sets. Gas is conserved across merge/split by heat-capacity mixing and volume splitting. */
    public PipeGasInventory remapNetworks(Collection<Set<PipeTopology.NodeKey>> groups) {
        Objects.requireNonNull(groups);
        if(groups.isEmpty()||groups.size()>MAX_RECORDS) throw new IllegalArgumentException("invalid network group count");
        List<Set<PipeTopology.NodeKey>> destinations=new ArrayList<>(); Set<PipeTopology.NodeKey> seen=new HashSet<>();
        for(Set<PipeTopology.NodeKey> group:groups) {
            if(group==null||group.isEmpty()||group.size()>PipeGasLedger.MAX_RECIPIENTS) throw new IllegalArgumentException("invalid network members");
            Set<PipeTopology.NodeKey> copy=Set.copyOf(group);
            for(var key:copy) if(!entries.containsKey(key)||!seen.add(key)) throw new IllegalArgumentException("unknown or duplicate network member");
            destinations.add(copy);
        }
        if(seen.size()!=entries.size()) throw new IllegalArgumentException("network remap must cover every node");
        Map<UUID,List<Integer>> targetsByOld=new LinkedHashMap<>();
        for(Network old:networks.values()) for(int i=0;i<destinations.size();i++)
            if(destinations.get(i).stream().anyMatch(old.members()::contains)) targetsByOld.computeIfAbsent(old.id(),x->new ArrayList<>()).add(i);
        List<List<GasMixture>> contributions=new ArrayList<>(); for(int i=0;i<destinations.size();i++) contributions.add(new ArrayList<>());
        for(Network old:networks.values()) {
            List<Integer> targets=targetsByOld.getOrDefault(old.id(),List.of());
            if(targets.isEmpty()) throw new IllegalArgumentException("old network has no receiving network");
            double[] volumes=targets.stream().mapToDouble(i->destinations.get(i).stream().mapToDouble(k->entries.get(k).volume()).sum()).toArray();
            List<GasMixture> shares=PipeGasLedger.split(old.mixture(),volumes);
            for(int j=0;j<targets.size();j++) contributions.get(targets.get(j)).add(shares.get(j));
        }
        for(int i=0;i<destinations.size();i++) for(var key:destinations.get(i)) if(!membership.containsKey(key))
            contributions.get(i).add(new GasMixture(Map.of(),GasMixture.MIN_TEMPERATURE_KELVIN,entries.get(key).volume()));
        Map<UUID,Network> next=new LinkedHashMap<>(); Set<UUID> used=new HashSet<>(); boolean unchanged=true;
        for(int i=0;i<destinations.size();i++) {
            Set<PipeTopology.NodeKey> group=destinations.get(i); Network exact=null;
            UUID candidate=membership.get(group.iterator().next()); Network current=candidate==null?null:networks.get(candidate);
            if(current!=null&&current.members().equals(group)) exact=current;
            if(exact!=null) { next.put(exact.id(),exact); used.add(exact.id()); continue; }
            unchanged=false; List<GasMixture> incoming=contributions.get(i); GasMixture merged=PipeGasLedger.merge(incoming);
            if(merged.heatCapacity()==0.0) {
                double emptyTemperature=DEFAULT_PIPE_TEMPERATURE_KELVIN;
                for(GasMixture source:incoming) if(Math.abs(source.temperatureKelvin()-emptyTemperature)>0.01
                        && merged.totalMoles()==0.0) emptyTemperature=source.temperatureKelvin();
                merged=new GasMixture(Map.of(),emptyTemperature,merged.volumeCubicMeters());
            }
            double volume=group.stream().mapToDouble(k->entries.get(k).volume()).sum();
            merged=new GasMixture(merged.gasMoles(),merged.temperatureKelvin(),volume);
            long generation=0; for(var oldId:targetsByOld.keySet()) if(targetsByOld.get(oldId).contains(i)) generation=Math.max(generation,networks.get(oldId).topologyGeneration());
            UUID id; do{id=UUID.randomUUID();}while(!used.add(id));
            next.put(id,new Network(id,group,increment(generation),0,merged));
        }
        return unchanged?this:new PipeGasInventory(entries,retired,next,increment(revision),null);
    }

    /** Atomically relocates active owners, preserving their exact finite gas state and incarnation. */
    public PipeGasInventory relocateBatch(Map<PipeTopology.NodeKey, ExpectedIdentityVersionAndDestination> moves) {
        Objects.requireNonNull(moves);
        if (moves.isEmpty() || moves.size() > MAX_RECORDS) throw new IllegalArgumentException("invalid relocation batch size");
        Set<PipeTopology.NodeKey> sources = new HashSet<>(), destinations = new HashSet<>();
        for (var move : moves.entrySet()) {
            PipeTopology.NodeKey source = Objects.requireNonNull(move.getKey());
            ExpectedIdentityVersionAndDestination expected = Objects.requireNonNull(move.getValue());
            Entry old = entries.get(source);
            if (!sources.add(source) || old == null || !old.active() || !old.incarnation().equals(expected.incarnation())
                    || old.version() != expected.expectedVersion()) throw new IllegalStateException("unknown, dormant, or stale pipe relocation source");
            if (!destinations.add(expected.destination())) throw new IllegalArgumentException("duplicate relocation destination");
        }
        for (var move : moves.entrySet()) {
            PipeTopology.NodeKey destination = move.getValue().destination();
            if (entries.containsKey(destination) && !sources.contains(destination)) throw new IllegalStateException("pipe relocation destination is occupied");
        }
        Map<PipeTopology.NodeKey, Entry> result = new LinkedHashMap<>(entries);
        for (PipeTopology.NodeKey source : sources) result.remove(source);
        for (var move : moves.entrySet()) {
            Entry old = entries.get(move.getKey()); PipeTopology.NodeKey destination = move.getValue().destination();
            if (destination.equals(move.getKey())) { result.put(move.getKey(), old); continue; }
            result.put(destination, new Entry(destination, old.incarnation(), old.volume(), increment(old.version()), true));
        }
        boolean changed = moves.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().destination()));
        if(!changed) return this;
        Map<UUID,Network> movedNetworks=new LinkedHashMap<>();
        for(Network n:networks.values()) {
            Set<PipeTopology.NodeKey> members=new HashSet<>(); for(var key:n.members()) { var move=moves.get(key); members.add(move==null?key:move.destination()); }
            if(members.equals(n.members())) { movedNetworks.put(n.id(),n); continue; }
            UUID id=UUID.randomUUID(); double volume=members.stream().mapToDouble(k->result.get(k).volume()).sum();
            movedNetworks.put(id,new Network(id,members,increment(n.topologyGeneration()),n.gasRevision(),new GasMixture(n.mixture().gasMoles(),n.mixture().temperatureKelvin(),volume)));
        }
        return new PipeGasInventory(result, retired, movedNetworks, increment(revision), null);
    }

    public PipeGasInventory admit(PipeTopology.NodeKey key, UUID id, double volume) {
        Objects.requireNonNull(key); Objects.requireNonNull(id); validVolume(volume);
        if (retired.containsKey(id)) throw new IllegalStateException("retired pipe incarnation cannot be admitted");
        for (var entry : entries.entrySet())
            if (!entry.getKey().equals(key) && entry.getValue().incarnation().equals(id))
                throw new IllegalStateException("pipe incarnation is already owned by another node");
        Entry old = entries.get(key);
        if (old != null) {
            if (!old.incarnation().equals(id) || Double.compare(old.volume(), volume) != 0 || old.active()) throw new IllegalStateException("stale, mismatched, or duplicate pipe admission");
            return change(key, new Entry(key, id, volume, increment(old.version()), true));
        }
        if ((long) entries.size() + retired.size() >= MAX_RECORDS) throw new IllegalStateException("pipe inventory record limit exceeded");
        var admitted = new Entry(key, id, volume, 1, true);
        Map<PipeTopology.NodeKey,Entry> owners=new LinkedHashMap<>(entries); owners.put(key,admitted);
        Map<UUID,Network> nets=new LinkedHashMap<>(networks); UUID netId=UUID.randomUUID();
        nets.put(netId,new Network(netId,Set.of(key),0,0,new GasMixture(Map.of(), DEFAULT_PIPE_TEMPERATURE_KELVIN, volume)));
        return new PipeGasInventory(owners,retired,nets,increment(revision),null);
    }
    public PipeGasInventory unload(PipeTopology.NodeKey key, UUID id) {
        Entry old = require(key, id); if (!old.active()) return this;
        return change(key, new Entry(key, id, old.volume(), increment(old.version()), false));
    }
    /** Trusted test/transaction seam: callers must independently provide a conserved transfer plan. */
    public PipeGasInventory withMixture(PipeTopology.NodeKey key, UUID id, long expectedVersion, GasMixture next) {
        Objects.requireNonNull(next); Entry old = require(key, id);
        if (!old.active() || old.version() != expectedVersion) throw new IllegalStateException("stale pipe gas update");
        return withNetworkMixtures(Map.of(key,next));
    }
    /** Applies a conserved multi-node update as one immutable inventory revision. */
    public PipeGasInventory withBatchMixtures(Map<PipeTopology.NodeKey, ExpectedIdentityVersionAndGasAfter> updates) {
        return withBatchMixtures(updates, 0.0);
    }
    /** Atomic thermal work boundary: requested delta is the independently planned net energy added to gas. */
    public PipeGasInventory withBatchMixtures(Map<PipeTopology.NodeKey, ExpectedIdentityVersionAndGasAfter> updates, double accountedEnergyDelta) {
        Objects.requireNonNull(updates);
        if (!Double.isFinite(accountedEnergyDelta)) throw new IllegalArgumentException("accounted energy delta must be finite");
        if (updates.isEmpty() || updates.size() > MAX_RECORDS) throw new IllegalArgumentException("invalid batch size");
        Map<UUID,GasMixture> proposed=new LinkedHashMap<>();
        for (var update : updates.entrySet()) {
            Entry old = entries.get(Objects.requireNonNull(update.getKey()));
            var expected = Objects.requireNonNull(update.getValue());
            if (old == null || !old.active() || !old.incarnation().equals(expected.incarnation()) || old.version() != expected.expectedVersion())
                throw new IllegalStateException("unknown, dormant, or stale pipe gas update");
            GasMixture next=Objects.requireNonNull(expected.gasAfter()); validVolume(old.volume());
            UUID netId=membership.get(update.getKey()); Network net=networks.get(netId);
            if(Double.compare(next.volumeCubicMeters(),net.mixture().volumeCubicMeters())!=0) throw new IllegalArgumentException("network update must retain network volume");
            GasMixture prior=proposed.putIfAbsent(netId,next);
            if(prior!=null&&!sameMixture(prior,next)) throw new IllegalArgumentException("contradictory gas plans for network aliases");
        }
        EnumMap<GasType,Double> beforeGas=new EnumMap<>(GasType.class),afterGas=new EnumMap<>(GasType.class); double beforeEnergy=0,afterEnergy=0;
        Map<PipeTopology.NodeKey,GasMixture> networkUpdates=new LinkedHashMap<>(); boolean anyChange=false;
        for(var e:proposed.entrySet()) { Network n=networks.get(e.getKey()); GasMixture next=e.getValue(); if(sameMixture(n.mixture(),next)) continue;
            anyChange=true; add(beforeGas,n.mixture()); add(afterGas,next); beforeEnergy+=n.mixture().thermalEnergy(); afterEnergy+=next.thermalEnergy();
            networkUpdates.put(n.members().iterator().next(),next);
        }
        if(!anyChange) return this;
        if(!Double.isFinite(beforeEnergy)||!Double.isFinite(afterEnergy)) throw new IllegalArgumentException("batch thermal energy must remain finite");
        for(GasType type:GasType.values()) if(!PipeGasLedger.conservedSpecies(beforeGas.getOrDefault(type,0d),afterGas.getOrDefault(type,0d))) throw new IllegalArgumentException("batch does not conserve "+type);
        double accountedBefore=beforeEnergy+accountedEnergyDelta;
        if(!Double.isFinite(accountedBefore)||!PipeGasLedger.conservedEnergy(accountedBefore,afterEnergy)) throw new IllegalArgumentException("batch does not conserve thermal energy");
        return withNetworkMixtures(networkUpdates);
    }
    private static boolean sameMixture(GasMixture a, GasMixture b) { return a.gasMoles().equals(b.gasMoles()) && Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) == 0 && Double.compare(a.volumeCubicMeters(),b.volumeCubicMeters())==0; }
    private static void add(Map<GasType, Double> total, GasMixture mixture) {
        for (var gas : mixture.gasMoles().entrySet()) total.merge(gas.getKey(), gas.getValue(), Double::sum);
    }
    /** Removes only empty ownership; non-empty gas and energy require a separately accounted transfer. */
    public PipeGasInventory tombstoneEmpty(PipeTopology.NodeKey key, UUID id) {
        Entry old = require(key, id);
        if (!nodeShare(key).gasMoles().isEmpty() || nodeShare(key).thermalEnergy() != 0.0)
            throw new IllegalStateException("cannot remove non-vacuum pipe ownership without an accounted transfer");
        Map<PipeTopology.NodeKey, Entry> copy = new LinkedHashMap<>(entries); copy.remove(key);
        return retireBatch(Map.of(key,id));
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
        Map<UUID, Retired> archive = new LinkedHashMap<>(retired);
        Map<UUID,Network> nets=new LinkedHashMap<>(networks);
        Map<UUID,Set<PipeTopology.NodeKey>> removingByNetwork=new LinkedHashMap<>();
        for(var owner:exactOwners.entrySet()) removingByNetwork.computeIfAbsent(membership.get(owner.getKey()),x->new HashSet<>()).add(owner.getKey());
        for(var batch:removingByNetwork.entrySet()) {
            Network n=networks.get(batch.getKey()); List<PipeTopology.NodeKey> ordered=n.members().stream().sorted(Comparator.comparing(PipeGasInventory::keyString)).toList();
            double[] volumes=ordered.stream().mapToDouble(k->entries.get(k).volume()).toArray();
            List<GasMixture> shares=PipeGasLedger.split(n.mixture(),volumes);
            List<PipeTopology.NodeKey> survivors=new ArrayList<>(); List<GasMixture> survivingShares=new ArrayList<>();
            for(int i=0;i<ordered.size();i++) { var key=ordered.get(i); Entry old=entries.get(key);
                if(batch.getValue().contains(key)) { live.remove(key); GasMixture share=shares.get(i);
                    if(!share.gasMoles().isEmpty()||share.thermalEnergy()!=0.0) {
                        GasMixture archivedShare=new GasMixture(share.gasMoles(),share.temperatureKelvin(),old.volume());
                        archive.put(old.incarnation(),new Retired(old.node(),old.incarnation(),old.volume(),old.version(),archivedShare));
                    } }
                else { survivors.add(key); survivingShares.add(shares.get(i)); }
            }
            nets.remove(n.id());
            if(!survivors.isEmpty()) { GasMixture merged=PipeGasLedger.merge(survivingShares);
                if(merged.heatCapacity()==0.0) merged=new GasMixture(Map.of(),n.mixture().temperatureKelvin(),merged.volumeCubicMeters());
                double survivorVolume=survivors.stream().mapToDouble(k->entries.get(k).volume()).sum();
                GasMixture survivorGas=new GasMixture(merged.gasMoles(),merged.temperatureKelvin(),survivorVolume); UUID nid=UUID.randomUUID();
                nets.put(nid,new Network(nid,new HashSet<>(survivors),increment(n.topologyGeneration()),n.gasRevision(),survivorGas)); }
        }
        return new PipeGasInventory(live, archive, nets, increment(revision), null);
    }
    private Entry require(PipeTopology.NodeKey key, UUID id) {
        Entry e = entries.get(Objects.requireNonNull(key));
        if (e == null || !e.incarnation().equals(Objects.requireNonNull(id))) throw new IllegalStateException("unknown or stale pipe incarnation");
        return e;
    }
    private PipeGasInventory change(PipeTopology.NodeKey key, Entry value) {
        Map<PipeTopology.NodeKey, Entry> copy = new LinkedHashMap<>(entries); copy.put(key, value);
        return new PipeGasInventory(copy, retired, networks, increment(revision), null);
    }
    private static long increment(long value) { return Math.addExact(value, 1L); }
    private static void validVolume(double volume) { if (!Double.isFinite(volume) || volume <= 0) throw new IllegalArgumentException("volume must be positive and finite"); }
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparingInt((Entry e) -> e.node().position().getX())
            .thenComparingInt(e -> e.node().position().getY())
            .thenComparingInt(e -> e.node().position().getZ())
            .thenComparing(e -> e.node().layer())
            .thenComparing(e -> e.node().portId())
            .thenComparing(e -> e.incarnation().toString());
    private List<Record> records() {
        return sortedRecords(entries.values());
    }
    private List<RetiredRecord> retiredRecords() { return retired.values().stream().sorted(Comparator.comparing(r->r.incarnation().toString()))
            .map(r->new RetiredRecord(new Record(r.node().position().getX(),r.node().position().getY(),r.node().position().getZ(),r.node().layer().name(),r.node().portId(),r.incarnation().toString(),r.volume(),r.version()),
                    r.mixture().temperatureKelvin(),r.mixture().gasMoles().entrySet().stream().map(e->new Amount(e.getKey(),e.getValue())).toList())).toList(); }
    private List<NetworkRecord> networkRecords() { return networks.values().stream().sorted(Comparator.comparing(n->n.id().toString()))
            .map(n->new NetworkRecord(n.id().toString(),n.topologyGeneration(),n.gasRevision(),n.members().stream().map(PipeGasInventory::keyString).sorted().toList(),n.mixture().temperatureKelvin(),n.mixture().gasMoles().entrySet().stream().map(e->new Amount(e.getKey(),e.getValue())).toList())).toList(); }
    private static String keyString(PipeTopology.NodeKey k) { return k.position().getX()+","+k.position().getY()+","+k.position().getZ()+","+k.layer().name()+","+k.portId(); }
    private static List<Record> sortedRecords(Collection<Entry> entries) {
        return entries.stream().sorted(ENTRY_ORDER).map(PipeGasInventory::toRecord).toList();
    }
    private static Record toRecord(Entry e) { return new Record(e.node().position().getX(), e.node().position().getY(), e.node().position().getZ(), e.node().layer().name(), e.node().portId(), e.incarnation().toString(), e.volume(), e.version()); }
    private static PipeGasInventory decode(int version, long revision, List<Record> records, List<RetiredRecord> archived, List<NetworkRecord> encodedNetworks) {
        Map<PipeTopology.NodeKey, Entry> result = new LinkedHashMap<>(); Map<UUID, Retired> retired = new LinkedHashMap<>(); Map<UUID,Network> networks=new LinkedHashMap<>(); String error = null;
        if (version != SCHEMA_VERSION) error = "unsupported pipe inventory version: " + version;
        else if (revision < 0) error = "negative inventory revision";
        else if ((long) records.size() + archived.size() > MAX_RECORDS) error = "pipe inventory record limit exceeded";
        Set<UUID> ids = new HashSet<>();
        if (error == null) for (Record r : records) try {
            PipeTopology.Layer layer = PipeTopology.Layer.valueOf(r.layer()); UUID id = UUID.fromString(r.id()); validVolume(r.volume());
            if (r.version() <= 0) throw new IllegalArgumentException("entry version must be positive");
             PipeTopology.NodeKey key = new PipeTopology.NodeKey(new BlockPos(r.x(), r.y(), r.z()), layer, r.port());
            Entry entry = new Entry(key, id, r.volume(), r.version(), false);
            if (result.putIfAbsent(key, entry) != null) throw new IllegalArgumentException("duplicate pipe node");
            if (!ids.add(id)) throw new IllegalArgumentException("duplicate pipe UUID");
        } catch (RuntimeException e) { error = "invalid pipe inventory record: " + e.getMessage(); break; }
        if (error == null) for (RetiredRecord rr : archived) try {
            Entry e = parseRecord(rr.owner(), false); EnumMap<GasType,Double> gas=new EnumMap<>(GasType.class);
            for(Amount a:rr.gases()) if(gas.putIfAbsent(a.type(),a.moles())!=null) throw new IllegalArgumentException("duplicate retired gas id");
            Retired archivedEntry=new Retired(e.node(),e.incarnation(),e.volume(),e.version(),new GasMixture(gas,rr.temperature(),e.volume()));
            if (!ids.add(e.incarnation()) || retired.putIfAbsent(e.incarnation(), archivedEntry) != null) throw new IllegalArgumentException("duplicate pipe UUID");
        } catch (RuntimeException e) { error = "invalid retired pipe inventory record: " + e.getMessage(); break; }
        if(error==null&&(encodedNetworks.size()>MAX_RECORDS||encodedNetworks.size()>records.size())) error="pipe network record limit exceeded";
        Set<UUID> networkIds=new HashSet<>();
        if(error==null) for(NetworkRecord nr:encodedNetworks) try {
            UUID id=UUID.fromString(nr.id()); if(nr.generation()<0||nr.revision()<0) throw new IllegalArgumentException("negative network counters");
            if(ids.contains(id)||!networkIds.add(id)) throw new IllegalArgumentException("duplicate inventory/network UUID");
            EnumMap<GasType,Double> gas=new EnumMap<>(GasType.class); for(Amount a:nr.gases()) if(gas.putIfAbsent(a.type(),a.moles())!=null) throw new IllegalArgumentException("duplicate network gas");
            if(nr.members().isEmpty()||nr.members().size()>PipeGasLedger.MAX_RECIPIENTS) throw new IllegalArgumentException("invalid network member count");
            Set<PipeTopology.NodeKey> members=new HashSet<>(); for(String m:nr.members()) { String[] p=m.split(",",5); if(p.length!=5) throw new IllegalArgumentException("invalid network member");
                var k=new PipeTopology.NodeKey(new BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])),PipeTopology.Layer.valueOf(p[3]),p[4]);
                if(!result.containsKey(k)||!members.add(k)) throw new IllegalArgumentException("unknown or duplicate network member"); }
            if(members.isEmpty()) throw new IllegalArgumentException("network members must not be empty");
            double volume=members.stream().mapToDouble(k->result.get(k).volume()).sum(); validVolume(volume);
            Network n=new Network(id,members,nr.generation(),nr.revision(),new GasMixture(gas,nr.temperature(),volume));
            if(networks.putIfAbsent(id,n)!=null) throw new IllegalArgumentException("duplicate network id");
        } catch(RuntimeException ex) { error="invalid pipe network record: "+ex.getMessage(); break; }
        if(error==null) { Set<PipeTopology.NodeKey> covered=new HashSet<>(); for(Network n:networks.values()) for(var k:n.members()) if(!covered.add(k)) error="node belongs to multiple networks";
            if(covered.size()!=result.size()) error="network membership does not cover every pipe node"; }
        return new PipeGasInventory(result, retired, networks, revision, error);
    }
    private static Entry parseRecord(Record r, boolean active) { PipeTopology.Layer layer = PipeTopology.Layer.valueOf(r.layer()); UUID id = UUID.fromString(r.id()); validVolume(r.volume()); if (r.version() <= 0) throw new IllegalArgumentException("entry version must be positive"); PipeTopology.NodeKey key = new PipeTopology.NodeKey(new BlockPos(r.x(), r.y(), r.z()), layer, r.port()); return new Entry(key, id, r.volume(), r.version(), active); }
}

