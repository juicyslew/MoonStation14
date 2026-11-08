package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-dimension once-only admission and initialization guard for atmosphere machinery fixtures. */
public final class AtmosMachineryFixtureSavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_atmos_machinery_fixture";
    public static final Factory<AtmosMachineryFixtureSavedData> FACTORY = new Factory<>(
            AtmosMachineryFixtureSavedData::new, AtmosMachineryFixtureSavedData::load, null);

    public record PortOwner(BlockPos host, String portId, UUID incarnation) {
        public PortOwner {
            host = new BlockPos(Objects.requireNonNull(host));
            Objects.requireNonNull(portId);
            Objects.requireNonNull(incarnation);
        }
        @Override public BlockPos host() { return new BlockPos(host); }
    }

    public record MinerOwner(BlockPos host, UUID placementIdentity) {
        public MinerOwner {
            host = new BlockPos(Objects.requireNonNull(host));
            Objects.requireNonNull(placementIdentity);
        }
        @Override public BlockPos host() { return new BlockPos(host); }
    }

    public record RoomDeviceOwner(BlockPos position, UUID deviceId) {
        public RoomDeviceOwner { position = new BlockPos(Objects.requireNonNull(position)); Objects.requireNonNull(deviceId); }
        @Override public BlockPos position() { return new BlockPos(position); }
    }

    public record Entry(BlockPos origin, List<PortOwner> expectedPortOwners, boolean initialized,
                        List<MinerOwner> minerOwners, List<RoomDeviceOwner> roomDeviceOwners,
                        boolean laboratoryStarted, Map<String, Double> baseline,
                        Set<String> reservedDemos, Map<String, Map<String, Double>> demoBaselines) {
        public Entry {
            origin = new BlockPos(Objects.requireNonNull(origin));
            expectedPortOwners = List.copyOf(Objects.requireNonNull(expectedPortOwners));
            minerOwners = List.copyOf(Objects.requireNonNull(minerOwners));
            roomDeviceOwners = List.copyOf(Objects.requireNonNull(roomDeviceOwners));
            baseline = Map.copyOf(Objects.requireNonNull(baseline));
            reservedDemos = Set.copyOf(Objects.requireNonNull(reservedDemos));
            demoBaselines = Map.copyOf(Objects.requireNonNull(demoBaselines));
            if (baseline.values().stream().anyMatch(value -> value == null || !Double.isFinite(value)))
                throw new IllegalArgumentException("Laboratory baseline values must be finite");
        }
        public Entry(BlockPos origin, List<PortOwner> ports, boolean initialized, List<MinerOwner> miners,
                     List<RoomDeviceOwner> roomDevices, boolean started, Map<String,Double> baseline) {
            this(origin,ports,initialized,miners,roomDevices,started,baseline,Set.of(),Map.of());
        }
        public Entry(BlockPos origin, List<PortOwner> expectedPortOwners, boolean initialized,
                     List<MinerOwner> minerOwners, boolean laboratoryStarted, Map<String, Double> baseline) {
            this(origin, expectedPortOwners, initialized, minerOwners, List.of(), laboratoryStarted, baseline);
        }
        @Override public BlockPos origin() { return new BlockPos(origin); }
        public boolean ownsMiner(BlockPos host, UUID placementIdentity) {
            return host != null && placementIdentity != null && minerOwners.stream().anyMatch(owner ->
                    owner.host().equals(host) && owner.placementIdentity().equals(placementIdentity));
        }
        /** Old global-start records have no per-demo identities and cannot safely be reseeded. */
        public boolean legacyGlobalStart() { return laboratoryStarted && reservedDemos.isEmpty(); }
        /** The existing global guard is the one-shot auxiliary ventilation/miner admission. */
        public boolean auxiliaryStartupAvailable() { return !laboratoryStarted; }
        private Entry initializedCopy() { return copy(true,minerOwners,roomDeviceOwners,laboratoryStarted,baseline,reservedDemos,demoBaselines); }
        private Entry withMiners(List<MinerOwner> owners) { return copy(initialized,owners,roomDeviceOwners,laboratoryStarted,baseline,reservedDemos,demoBaselines); }
        private Entry withRoomDevices(List<RoomDeviceOwner> owners) { return copy(initialized,minerOwners,owners,laboratoryStarted,baseline,reservedDemos,demoBaselines); }
        private Entry started(Map<String, Double> values) { return copy(initialized,minerOwners,roomDeviceOwners,true,values,reservedDemos,demoBaselines); }
        private Entry copy(boolean i,List<MinerOwner> m,List<RoomDeviceOwner> r,boolean s,Map<String,Double> b,Set<String> reserved,Map<String,Map<String,Double>> demo) { return new Entry(origin,expectedPortOwners,i,m,r,s,b,reserved,demo); }
    }

    private static final Codec<PortOwner> PORT_OWNER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("host").forGetter(PortOwner::host),
            Codec.STRING.fieldOf("portId").forGetter(PortOwner::portId),
            Codec.STRING.fieldOf("incarnation").xmap(UUID::fromString, UUID::toString).forGetter(PortOwner::incarnation)
    ).apply(instance, PortOwner::new));
    private static final Codec<MinerOwner> MINER_OWNER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("host").forGetter(MinerOwner::host),
            Codec.STRING.fieldOf("placementIdentity").xmap(UUID::fromString, UUID::toString).forGetter(MinerOwner::placementIdentity)
    ).apply(instance, MinerOwner::new));
    private static final Codec<RoomDeviceOwner> ROOM_DEVICE_OWNER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("position").forGetter(RoomDeviceOwner::position),
            Codec.STRING.fieldOf("deviceId").xmap(UUID::fromString, UUID::toString).forGetter(RoomDeviceOwner::deviceId)
    ).apply(instance, RoomDeviceOwner::new));
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(Entry::origin),
            PORT_OWNER_CODEC.listOf().fieldOf("expectedPortOwners").forGetter(Entry::expectedPortOwners),
            Codec.BOOL.fieldOf("initialized").forGetter(Entry::initialized),
            MINER_OWNER_CODEC.listOf().fieldOf("minerOwners").forGetter(Entry::minerOwners),
            ROOM_DEVICE_OWNER_CODEC.listOf().optionalFieldOf("roomDeviceOwners", List.of()).forGetter(Entry::roomDeviceOwners),
            Codec.BOOL.fieldOf("laboratoryStarted").forGetter(Entry::laboratoryStarted),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("baseline").forGetter(Entry::baseline)
            ,Codec.STRING.listOf().optionalFieldOf("reservedDemos",List.of()).forGetter(e->e.reservedDemos().stream().toList()),
            Codec.unboundedMap(Codec.STRING,Codec.unboundedMap(Codec.STRING,Codec.DOUBLE)).optionalFieldOf("demoBaselines",Map.of()).forGetter(Entry::demoBaselines)
    ).apply(instance, (origin, ports, initialized, miners, rooms, started, baseline,reserved,demo) -> new Entry(origin, ports, initialized, miners, rooms, started, baseline,new HashSet<>(reserved),demo)));
    private static final Codec<List<Entry>> CODEC = ENTRY_CODEC.listOf().validate(entries -> {
        Set<BlockPos> origins = new HashSet<>();
        for (Entry entry : entries)
            if (!origins.add(entry.origin()))
                return com.mojang.serialization.DataResult.error(() -> "Duplicate atmosphere machinery fixture origin");
        return com.mojang.serialization.DataResult.success(entries);
    });

    private final LinkedHashMap<BlockPos, Entry> entries = new LinkedHashMap<>();
    public AtmosMachineryFixtureSavedData() { }
    private AtmosMachineryFixtureSavedData(List<Entry> restored) {
        for (Entry entry : restored) entries.put(entry.origin(), entry);
    }

    public static Optional<AtmosMachineryFixtureSavedData> existing(ServerLevel level) {
        return Optional.ofNullable(Objects.requireNonNull(level).getDataStorage().get(FACTORY, DATA_NAME));
    }
    public static AtmosMachineryFixtureSavedData forMutation(ServerLevel level) {
        Objects.requireNonNull(level);
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Atmosphere machinery fixture saved data mutation requires server thread");
        AtmosMachineryFixtureSavedData data = level.getDataStorage().get(FACTORY, DATA_NAME);
        return data != null ? data : level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<Entry> entry(BlockPos origin) {
        return Optional.ofNullable(entries.get(new BlockPos(Objects.requireNonNull(origin))));
    }

    public List<Entry> entries() { return List.copyOf(entries.values()); }

    public boolean recordBuilt(BlockPos origin, List<PortOwner> expectedPortOwners) {
        Entry candidate = new Entry(origin, expectedPortOwners, false, List.of(), false, Map.of());
        if (entries.containsKey(candidate.origin())) return false;
        entries.put(candidate.origin(), candidate);
        setDirty();
        return true;
    }

    public boolean recordMiners(BlockPos origin, List<MinerOwner> expectedMinerOwners) {
        BlockPos key = new BlockPos(Objects.requireNonNull(origin));
        Entry old = entries.get(key);
        if (old == null || old.laboratoryStarted() || !old.minerOwners().isEmpty()) return false;
        List<MinerOwner> owners = List.copyOf(Objects.requireNonNull(expectedMinerOwners));
        if (owners.size() != 3 || owners.stream().map(MinerOwner::host).distinct().count() != 3
                || owners.stream().map(MinerOwner::placementIdentity).distinct().count() != 3) return false;
        entries.put(key, old.withMiners(owners));
        setDirty();
        return true;
    }

    public boolean recordRoomDevices(BlockPos origin, List<RoomDeviceOwner> owners) {
        BlockPos key = new BlockPos(Objects.requireNonNull(origin)); Entry old = entries.get(key);
        List<RoomDeviceOwner> copy = List.copyOf(Objects.requireNonNull(owners));
        if (old == null || old.laboratoryStarted() || !old.roomDeviceOwners().isEmpty() || copy.isEmpty()
                || copy.stream().map(RoomDeviceOwner::position).distinct().count() != copy.size()
                || copy.stream().map(RoomDeviceOwner::deviceId).distinct().count() != copy.size()) return false;
        entries.put(key, old.withRoomDevices(copy)); setDirty(); return true;
    }

    public boolean markLaboratoryStarted(BlockPos origin, List<PortOwner> expectedPortOwners,
                                         List<MinerOwner> expectedMinerOwners, Map<String, Double> baseline) {
        BlockPos key = new BlockPos(Objects.requireNonNull(origin));
        Entry old = entries.get(key);
        if (old == null || old.laboratoryStarted()
                || !samePortOwners(old.expectedPortOwners(), expectedPortOwners)
                || !old.minerOwners().equals(List.copyOf(expectedMinerOwners))) return false;
        Map<String, Double> values = Map.copyOf(Objects.requireNonNull(baseline));
        if (values.values().stream().anyMatch(value -> value == null || !Double.isFinite(value)))
            throw new IllegalArgumentException("Laboratory baseline values must be finite");
        Map<String,Double> merged=new LinkedHashMap<>(old.baseline());values.forEach(merged::putIfAbsent);
        entries.put(key, old.started(merged));
        setDirty();
        return true;
    }

    /** Records the post-seeding baseline exactly once after an irreversible startup reservation. */
    public boolean recordLaboratoryBaseline(BlockPos origin, List<PortOwner> expectedPortOwners,
                                             List<MinerOwner> expectedMinerOwners, Map<String, Double> baseline) {
        BlockPos key = new BlockPos(Objects.requireNonNull(origin));
        Entry old = entries.get(key);
        if (old == null || !old.laboratoryStarted()
                || !samePortOwners(old.expectedPortOwners(), expectedPortOwners)
                || !old.minerOwners().equals(List.copyOf(expectedMinerOwners))) return false;
        Map<String, Double> values = Map.copyOf(Objects.requireNonNull(baseline));
        if (values.isEmpty() || values.values().stream().anyMatch(value -> value == null || !Double.isFinite(value))) return false;
        Map<String,Double> merged=new LinkedHashMap<>(old.baseline());values.forEach(merged::putIfAbsent);
        if(merged.equals(old.baseline()))return false;
        entries.put(key, old.started(merged));
        setDirty();
        return true;
    }

    public boolean reserveDemo(BlockPos origin, String demo, List<PortOwner> expectedPortOwners) {
        BlockPos key=new BlockPos(origin); Entry old=entries.get(key);
        if(old==null || !samePortOwners(old.expectedPortOwners(),expectedPortOwners) || old.reservedDemos().contains(demo))return false;
        Set<String> reserved=new HashSet<>(old.reservedDemos());reserved.add(demo);
        entries.put(key,old.copy(old.initialized(),old.minerOwners(),old.roomDeviceOwners(),old.laboratoryStarted(),old.baseline(),reserved,old.demoBaselines()));setDirty();return true;
    }
    public boolean recordDemoBaseline(BlockPos origin,String demo,Map<String,Double> values) {
        BlockPos key=new BlockPos(origin);Entry old=entries.get(key);if(old==null||!old.reservedDemos().contains(demo)||old.demoBaselines().containsKey(demo))return false;
        Map<String,Double> snapshot=Map.copyOf(values);if(snapshot.isEmpty()||snapshot.values().stream().anyMatch(v->v==null||!Double.isFinite(v)))return false;
        Map<String,Map<String,Double>> all=new HashMap<>(old.demoBaselines());all.put(demo,snapshot);
        Map<String,Double> merged=new LinkedHashMap<>(old.baseline());snapshot.forEach(merged::putIfAbsent);
        entries.put(key,old.copy(old.initialized(),old.minerOwners(),old.roomDeviceOwners(),old.laboratoryStarted(),merged,old.reservedDemos(),all));setDirty();return true;
    }

    public boolean markInitialized(BlockPos origin, List<PortOwner> expectedPortOwners) {
        Entry old = entries.get(new BlockPos(Objects.requireNonNull(origin)));
        if (old == null || old.initialized() || !samePortOwners(old.expectedPortOwners(), expectedPortOwners)) return false;
        entries.put(old.origin(), old.initializedCopy());
        setDirty();
        return true;
    }

    private static boolean samePortOwners(List<PortOwner> expected, List<PortOwner> actual) {
        List<PortOwner> owners = List.copyOf(Objects.requireNonNull(actual));
        Set<PortOwner> expectedSet = new HashSet<>(expected);
        Set<PortOwner> actualSet = new HashSet<>(owners);
        return expectedSet.size() == expected.size() && actualSet.size() == owners.size()
                && expectedSet.size() == actualSet.size() && expectedSet.equals(actualSet);
    }

    public static AtmosMachineryFixtureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("fixtures", CompoundTag.TAG_LIST))
            throw new IllegalArgumentException("Missing atmosphere machinery fixture data");
        List<Entry> decoded = CODEC.parse(NbtOps.INSTANCE, tag.get("fixtures"))
                .getOrThrow(message -> new IllegalArgumentException("Invalid atmosphere machinery fixtures: " + message));
        return new AtmosMachineryFixtureSavedData(decoded);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("fixtures", CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(entries.values()))
                .getOrThrow(message -> new IllegalStateException("Could not encode atmosphere machinery fixtures: " + message)));
        return tag;
    }
}
