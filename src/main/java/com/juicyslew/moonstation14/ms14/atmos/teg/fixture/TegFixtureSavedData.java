package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Durable ownership and one-shot finite-charge admission for disposable TEG fixtures. */
public final class TegFixtureSavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_teg_fixture";
    public static final int MAX_FIXTURES = 8;
    public static final Factory<TegFixtureSavedData> FACTORY = new Factory<>(TegFixtureSavedData::new, TegFixtureSavedData::load, null);
    public enum Mode { FINITE_CHARGE, FUELED, CONTINUOUS }
    public enum ContinuousPhase { NONE, BUILT, STARTUP, IGNITION, RUNNING, HEATER_OFF }
    public enum AutomaticStartupStatus { WAITING_START, PREPARING_COLD, ACTIVE, COMPLETE, FAILED }
    public record AutomaticStartup(BlockPos origin, AutomaticStartupStatus status, UUID initiator, long armTick, String failureReason) {
        public AutomaticStartup { origin = new BlockPos(Objects.requireNonNull(origin)); Objects.requireNonNull(status); Objects.requireNonNull(initiator); Objects.requireNonNull(failureReason); if (armTick < 0) throw new IllegalArgumentException("invalid automatic startup arm tick"); }
        @Override public BlockPos origin() { return new BlockPos(origin); }
    }
    public record Entry(BlockPos origin, UUID core, UUID north, UUID south, UUID token, boolean initialized, Mode mode,
                        Map<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,UUID> ownedGas,
                        Map<BlockPos,UUID> ownedMachines, ContinuousPhase phase, long phaseStartedTick,
                        double phaseCo2Baseline,long phasePowerSampleFloor) {
        public Entry(BlockPos origin, UUID core, UUID north, UUID south, UUID token, boolean initialized) {
            this(origin, core, north, south, token, initialized, Mode.FINITE_CHARGE,Map.of(),Map.of(),ContinuousPhase.NONE,-1,0,-1);
        }
        public Entry(BlockPos origin, UUID core, UUID north, UUID south, UUID token, boolean initialized, Mode mode) { this(origin,core,north,south,token,initialized,mode,Map.of(),Map.of(),mode==Mode.CONTINUOUS?ContinuousPhase.BUILT:ContinuousPhase.NONE,-1,0,-1); }
        public Entry(BlockPos origin, UUID core, UUID north, UUID south, UUID token, boolean initialized, Mode mode,
                     Map<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,UUID> ownedGas,
                     Map<BlockPos,UUID> ownedMachines) {
            this(origin,core,north,south,token,initialized,mode,ownedGas,ownedMachines,
                    mode==Mode.CONTINUOUS?(initialized?ContinuousPhase.STARTUP:ContinuousPhase.BUILT):ContinuousPhase.NONE,-1,0,-1);
        }
        public Entry {
            origin = new BlockPos(Objects.requireNonNull(origin));
            Objects.requireNonNull(mode);
            Objects.requireNonNull(phase);
            var ids = List.of(Objects.requireNonNull(core), Objects.requireNonNull(north), Objects.requireNonNull(south),Objects.requireNonNull(token));
            if (new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("fixture identities must be unique");
            ownedGas=Map.copyOf(Objects.requireNonNull(ownedGas));
            Map<BlockPos,UUID> machineCopy=new HashMap<>(); Objects.requireNonNull(ownedMachines).forEach((pos,id)->machineCopy.put(Objects.requireNonNull(pos).immutable(),Objects.requireNonNull(id)));
            ownedMachines=Map.copyOf(machineCopy);
            if(!Double.isFinite(phaseCo2Baseline)||phaseCo2Baseline<0||phaseStartedTick < -1||phasePowerSampleFloor < -1)
                throw new IllegalArgumentException("invalid continuous operation phase sample");
            if(mode!=Mode.CONTINUOUS&&phase!=ContinuousPhase.NONE)throw new IllegalArgumentException("operation phase only valid for continuous fixtures");
            if(mode!=Mode.FUELED&&mode!=Mode.CONTINUOUS&&(!ownedGas.isEmpty()||!ownedMachines.isEmpty()))throw new IllegalArgumentException("ownership fingerprint only valid for gas-owning fixtures");
            if(new HashSet<>(ownedGas.values()).size()!=ownedGas.size()||new HashSet<>(ownedMachines.values()).size()!=ownedMachines.size())throw new IllegalArgumentException("duplicate ownership identity");
        }
        @Override public BlockPos origin() { return new BlockPos(origin); }
        public boolean matchesGasOwners(Map<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,UUID> current) { return ownedGas.equals(current); }
        private Entry initializedCopy() { return initializedCopy(phaseStartedTick); }
        private Entry initializedCopy(long admissionTick) { return new Entry(origin, core, north, south, token, true, mode,ownedGas,ownedMachines,mode==Mode.CONTINUOUS?ContinuousPhase.STARTUP:phase,mode==Mode.CONTINUOUS?admissionTick:phaseStartedTick,phaseCo2Baseline,phasePowerSampleFloor); }
        private Entry phaseCopy(ContinuousPhase next,long tick,double co2,long floor) { return new Entry(origin,core,north,south,token,initialized,mode,ownedGas,ownedMachines,next,tick,co2,floor); }
    }
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<AutomaticStartup> AUTOMATIC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(AutomaticStartup::origin),
            Codec.STRING.xmap(AutomaticStartupStatus::valueOf, AutomaticStartupStatus::name).fieldOf("status").forGetter(AutomaticStartup::status),
            UUID_CODEC.fieldOf("initiator").forGetter(AutomaticStartup::initiator), Codec.LONG.fieldOf("armTick").forGetter(AutomaticStartup::armTick),
            Codec.STRING.optionalFieldOf("failureReason", "").forGetter(AutomaticStartup::failureReason)
    ).apply(i, AutomaticStartup::new));
    private static final Codec<Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(Entry::origin), UUID_CODEC.fieldOf("core").forGetter(Entry::core),
            UUID_CODEC.fieldOf("north").forGetter(Entry::north), UUID_CODEC.fieldOf("south").forGetter(Entry::south),
            UUID_CODEC.fieldOf("token").forGetter(Entry::token), Codec.BOOL.fieldOf("initialized").forGetter(Entry::initialized),
            Codec.STRING.xmap(Mode::valueOf, Mode::name).optionalFieldOf("mode", Mode.FINITE_CHARGE).forGetter(Entry::mode),
            Codec.unboundedMap(Codec.STRING,UUID_CODEC).optionalFieldOf("gasOwners",Map.of()).forGetter(e->gasStrings(e.ownedGas())),
             Codec.unboundedMap(Codec.STRING,UUID_CODEC).optionalFieldOf("machineOwners",Map.of()).forGetter(e->machineStrings(e.ownedMachines())),
             Codec.STRING.xmap(ContinuousPhase::valueOf,ContinuousPhase::name).optionalFieldOf("phase",ContinuousPhase.NONE).forGetter(Entry::phase),
             Codec.LONG.optionalFieldOf("phaseStartedTick",-1L).forGetter(Entry::phaseStartedTick),
             Codec.DOUBLE.optionalFieldOf("phaseCo2Baseline",0d).forGetter(Entry::phaseCo2Baseline),
             Codec.LONG.optionalFieldOf("phasePowerSampleFloor",-1L).forGetter(Entry::phasePowerSampleFloor)
    ).apply(i, (origin,core,north,south,token,initialized,mode,gas,machines,phase,tick,co2,floor)->new Entry(origin,core,north,south,token,initialized,mode,parseGas(gas),parseMachines(machines),
            mode==Mode.CONTINUOUS&&phase==ContinuousPhase.NONE?(initialized?ContinuousPhase.STARTUP:ContinuousPhase.BUILT):phase,tick,co2,floor)));
    private static Map<String,UUID> gasStrings(Map<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,UUID> m){Map<String,UUID> r=new TreeMap<>();m.forEach((k,v)->r.put(k.position().asLong()+"|"+k.layer()+"|"+k.portId(),v));return r;}
    private static Map<String,UUID> machineStrings(Map<BlockPos,UUID> m){Map<String,UUID> r=new TreeMap<>();m.forEach((k,v)->r.put(k.asLong()+"",v));return r;}
    private static Map<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,UUID> parseGas(Map<String,UUID> m){Map<com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey,UUID> r=new HashMap<>();m.forEach((s,v)->{var a=s.split("\\|",3);if(a.length!=3||r.put(new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(BlockPos.of(Long.parseLong(a[0])),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.valueOf(a[1]),a[2]),v)!=null)throw new IllegalArgumentException("invalid gas ownership");});return r;}
    private static Map<BlockPos,UUID> parseMachines(Map<String,UUID> m){Map<BlockPos,UUID> r=new HashMap<>();m.forEach((s,v)->{if(r.put(BlockPos.of(Long.parseLong(s)),v)!=null)throw new IllegalArgumentException("invalid machine ownership");});return r;}
    private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();
    private final Map<BlockPos, AutomaticStartup> automaticStartups = new LinkedHashMap<>();
    public static Optional<TegFixtureSavedData> existing(ServerLevel level) { return Optional.ofNullable(level.getDataStorage().get(FACTORY, DATA_NAME)); }
    public static TegFixtureSavedData forMutation(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("TEG fixture state requires server thread");
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
    public Optional<Entry> entry(BlockPos pos) { return Optional.ofNullable(entries.get(new BlockPos(pos))); }
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public int count() { return entries.size(); }
    public Optional<AutomaticStartup> automaticStartup(BlockPos origin) { return Optional.ofNullable(automaticStartups.get(new BlockPos(origin))); }
    public Optional<String> armAutomaticStartup(Entry entry, UUID initiator, long tick) {
        Entry current=entries.get(entry.origin());
        if(current==null||!current.equals(entry)||current.mode()!=Mode.CONTINUOUS||current.initialized()||current.phase()!=ContinuousPhase.BUILT) return Optional.of("automatic startup requires the current uninitialized BUILT continuous fixture");
        if(initiator==null) return Optional.of("automatic startup requires an initiator");
        if(tick<0) return Optional.of("invalid arm tick");
        if(automaticStartups.containsKey(current.origin())) return Optional.of("automatic startup is already armed or terminal");
        automaticStartups.put(current.origin(),new AutomaticStartup(current.origin(),AutomaticStartupStatus.WAITING_START,initiator,tick,""));setDirty();return Optional.empty();
    }
    public boolean updateAutomaticStartup(BlockPos origin, AutomaticStartupStatus expected, AutomaticStartupStatus next, String failureReason) {
        AutomaticStartup current=automaticStartups.get(new BlockPos(origin));
        if(current==null||current.status()!=expected||next==null) return false;
        boolean terminal=current.status()==AutomaticStartupStatus.COMPLETE||current.status()==AutomaticStartupStatus.FAILED;
        if(terminal||next==AutomaticStartupStatus.WAITING_START||next==expected) return false;
        if(next==AutomaticStartupStatus.FAILED&&(failureReason==null||failureReason.isBlank())) return false;
        automaticStartups.put(current.origin(),new AutomaticStartup(current.origin(),next,current.initiator(),current.armTick(),next==AutomaticStartupStatus.FAILED?failureReason:""));setDirty();return true;
    }
    public boolean recordBuilt(Entry entry) {
        if (entries.containsKey(entry.origin()) || entries.size() >= MAX_FIXTURES) return false;
        entries.put(entry.origin(), entry); setDirty(); return true;
    }
    public boolean markInitialized(Entry entry) {
        Entry current = entries.get(entry.origin());
        if (current == null || current.initialized() || !current.equals(entry)) return false;
        entries.put(current.origin(), current.initializedCopy()); setDirty(); return true;
    }
    public boolean markInitialized(Entry entry,long admissionTick) {
        Entry current = entries.get(entry.origin());
        if (current == null || current.initialized() || !current.equals(entry) || admissionTick < 0) return false;
        entries.put(current.origin(), current.initializedCopy(admissionTick)); setDirty(); return true;
    }
    public boolean transition(Entry entry,ContinuousPhase expected,ContinuousPhase next,long tick,double co2Baseline) {
        return transition(entry,expected,next,tick,co2Baseline,entry.phasePowerSampleFloor());
    }
    public boolean transition(Entry entry,ContinuousPhase expected,ContinuousPhase next,long tick,double co2Baseline,long powerSampleFloor) {
        Entry current=entries.get(entry.origin());
        if(current==null||!current.equals(entry)||current.mode()!=Mode.CONTINUOUS||current.phase()!=expected||next==null||tick<0||!Double.isFinite(co2Baseline)||co2Baseline<0||powerSampleFloor < -1)return false;
        entries.put(current.origin(),current.phaseCopy(next,tick,co2Baseline,powerSampleFloor));setDirty();return true;
    }
    public static TegFixtureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var list = ENTRY.listOf().parse(NbtOps.INSTANCE, tag.get("fixtures"))
                .getOrThrow(message -> new IllegalArgumentException("Invalid TEG fixture data: " + message));
        if (list.size() > MAX_FIXTURES) throw new IllegalArgumentException("Too many TEG fixtures");
        TegFixtureSavedData data = new TegFixtureSavedData();
        for (Entry entry : list) if (data.entries.putIfAbsent(entry.origin(), entry) != null)
            throw new IllegalArgumentException("Duplicate TEG fixture origin");
        if(tag.contains("automaticStartups")) {
            var startups=AUTOMATIC.listOf().parse(NbtOps.INSTANCE,tag.get("automaticStartups")).getOrThrow(message -> new IllegalArgumentException("Invalid TEG automatic startup data: " + message));
            if(startups.size()>MAX_FIXTURES) throw new IllegalArgumentException("Too many automatic TEG startups");
            for(var startup:startups) if(!data.entries.containsKey(startup.origin())||data.entries.get(startup.origin()).mode()!=Mode.CONTINUOUS||data.automaticStartups.putIfAbsent(startup.origin(),startup)!=null) throw new IllegalArgumentException("Invalid automatic TEG startup origin");
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("fixtures", ENTRY.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(entries.values())
                ).getOrThrow(message -> new IllegalStateException("Could not encode TEG fixtures: " + message)));
        if(!automaticStartups.isEmpty()) tag.put("automaticStartups",AUTOMATIC.listOf().encodeStart(NbtOps.INSTANCE,List.copyOf(automaticStartups.values())).getOrThrow(message -> new IllegalStateException("Could not encode automatic TEG startups: " + message)));
        return tag;
    }
}
