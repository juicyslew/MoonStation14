package com.juicyslew.moonstation14.ms14.disposal.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeChunkData;
import com.juicyslew.moonstation14.ms14.atmos.piping.SealedWallHostEligibility;
import com.juicyslew.moonstation14.ms14.sealedwall.SealedWallHostGenerations;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Dimension-persistent disposal topology. Lookups never cause chunk loads. */
public final class DisposalTubeWorldData extends SavedData {
    public static final String DATA_NAME = "moonstation14_disposal_tubes";
    public static final Factory<DisposalTubeWorldData> FACTORY = new Factory<>(DisposalTubeWorldData::new, DisposalTubeWorldData::load, null);
    private static volatile Consumer<DisposalTubeRecord> disconnectListener = ignored -> {};
    private static volatile java.util.function.BiConsumer<ServerLevel, Change> changeListener = (level, change) -> {};
    public record Change(DisposalTubeRecord record, boolean removed) { }
    public static void setChangeListener(java.util.function.BiConsumer<ServerLevel, Change> listener) { changeListener = Objects.requireNonNull(listener); }
    private final Map<Long, DisposalTubeRecord> records = new HashMap<>();
    private final List<Consumer<DisposalTubeRecord>> scopedDisconnectListeners = new ArrayList<>();

    public enum LookupKind { PRESENT, ABSENT, UNKNOWN }
    public record Lookup(LookupKind kind, DisposalTubeRecord record) {}

    public static void setDisconnectListener(Consumer<DisposalTubeRecord> listener) {
        disconnectListener = Objects.requireNonNull(listener);
    }
    /** Disconnect observer scoped to this dimension's SavedData instance. */
    public void addDisconnectListener(Consumer<DisposalTubeRecord> listener) { scopedDisconnectListeners.add(Objects.requireNonNull(listener)); }
    private void disconnected(DisposalTubeRecord record) {
        scopedDisconnectListeners.forEach(listener -> listener.accept(record));
        disconnectListener.accept(record);
    }
    public static Optional<DisposalTubeWorldData> existing(ServerLevel level) {
        return Optional.ofNullable(level.getDataStorage().get(FACTORY, DATA_NAME));
    }
    public static DisposalTubeWorldData forMutation(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Disposal mutation requires server thread");
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Lookup lookup(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = fullChunk(level, pos);
        if (chunk == null) return new Lookup(LookupKind.UNKNOWN, null);
        DisposalTubeRecord record = records.get(pos.asLong());
        if (record == null) return new Lookup(LookupKind.ABSENT, null);
        if (!candidateLiveHost(level,chunk,record)) return new Lookup(LookupKind.ABSENT,null);
        return new Lookup(LookupKind.PRESENT, record);
    }
    private static boolean candidateLiveHost(ServerLevel level, LevelChunk chunk, DisposalTubeRecord record) {
        BlockPos pos=record.pos();
        if (record.hostKind()==DisposalTubeRecord.HostKind.EXPOSED)
            return record.hostGeneration()==null && record.sealedHostId().isEmpty()
                    && chunk.getBlockState(pos).is(ModBlocks.DISPOSAL_TUBE.get());
        if (record.hostKind()!=DisposalTubeRecord.HostKind.SEALED_WALL || record.hostGeneration()==null) return false;
        String blockId=BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()).toString();
        var generations=chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        return blockId.equals(record.sealedHostId()) && generations!=null
                && generations.active(pos.asLong()).filter(record.hostGeneration()::equals).isPresent()
                && SealedWallHostEligibility.defaults().eligible(level,pos);
    }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Caller supplies an already resolved, supported prototype ID. */
    public boolean add(ServerLevel level, DisposalTubeRecord record, Predicate<BlockPos> stationFloor) {
        LevelChunk chunk=fullChunk(level,record.pos());
        if (!level.getServer().isSameThread() || chunk==null || !stationFloor.test(record.pos()) || !candidateLiveHost(level,chunk,record)
                || records.containsKey(record.pos().asLong()) || record.revision() != 0) return false;
        records.put(record.pos().asLong(), record); setDirty(); changed(level, record, false); return true;
    }

    /** Installs the real exposed owner block and record atomically at a loaded air cell. */
    public boolean installExposed(ServerLevel level, DisposalTubeRecord record) {
        if (!level.getServer().isSameThread() || record.hostKind()!=DisposalTubeRecord.HostKind.EXPOSED) return false;
        LevelChunk chunk=fullChunk(level,record.pos());
        if (chunk==null || !chunk.getBlockState(record.pos()).isAir() || records.containsKey(record.pos().asLong()) || record.revision()!=0) return false;
        PipeChunkData pipes=chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get());
        if (pipes!=null && pipes.snapshot().stream().anyMatch(r -> r.x()==(record.pos().getX()&15) && r.y()==record.pos().getY() && r.z()==(record.pos().getZ()&15))) return false;
        var prior=chunk.getBlockState(record.pos());
        if (!level.setBlock(record.pos(),ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(),3)) return false;
        if (add(level,record,p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get()))) return true;
        if (level.getBlockState(record.pos()).is(ModBlocks.DISPOSAL_TUBE.get())) level.setBlock(record.pos(),prior,3);
        return false;
    }

    /** Enrolls a straight disposal segment in an existing sealed steel-wall incarnation. */
    public boolean installSealed(ServerLevel level, BlockPos pos, DisposalOrientation frame, String prototypeId) {
        if (!level.getServer().isSameThread() || frame.forward().getAxis()==frame.up().getAxis()) return false;
        net.minecraft.resources.ResourceLocation parsed;
        try { parsed=prototypeId==null ? null : ModDisposals.id(prototypeId); }
        catch (IllegalArgumentException invalidId) { return false; }
        var prototype=parsed==null ? null : PrototypeRuntime.serverDisposals().get(parsed);
        if (prototype==null || prototype.tube()==null || prototype.tube().kind()!=com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Kind.STRAIGHT
                || !parsed.equals(ModDisposals.id("disposal_pipe"))) return false;
        LevelChunk chunk=fullChunk(level,pos);
        if (chunk==null || records.containsKey(pos.asLong()) || !SealedWallHostEligibility.defaults().eligible(level,pos)) return false;
        PipeChunkData pipes=chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get());
        if (pipes!=null && pipes.snapshot().stream().anyMatch(r -> r.x()==(pos.getX()&15) && r.y()==pos.getY() && r.z()==(pos.getZ()&15))) return false;
        SealedWallHostGenerations generations=chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        boolean created=generations==null;
        if (created) generations=chunk.getData(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        UUID generation=generations.active(pos.asLong()).orElse(null);
        boolean enrolled=false;
        if(generation==null){ if(generations.hasRecord(pos.asLong())) return false; generation=UUID.randomUUID(); enrolled=generations.registerIfAbsent(pos.asLong(),generation); if(!enrolled)return false; }
        String host=BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()).toString();
        DisposalTubeRecord record=new DisposalTubeRecord(UUID.randomUUID(),pos,parsed.toString(),frame,prototype.anchored(),"","",0,DisposalTubeRecord.HostKind.SEALED_WALL,host,generation);
        if(add(level,record,p -> SealedWallHostEligibility.defaults().eligible(level,p))) { if(enrolled) chunk.setUnsaved(true); return true; }
        if(enrolled) { generations.removeActiveExact(pos.asLong(),generation); chunk.setUnsaved(true); }
        return false;
    }

    private static LevelChunk fullChunk(ServerLevel level, BlockPos pos) {
        if (pos.getY()<level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight()) return null;
        var chunk=level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        return chunk instanceof LevelChunk full ? full : null;
    }
    public boolean rotate(ServerLevel level, DisposalTubeRecord exact, Direction direction, Predicate<BlockPos> editableFloor) {
        if (!precondition(level, exact, editableFloor) || direction == null || !direction.getAxis().isHorizontal()) return false;
        try { return rotate(level, exact, new DisposalOrientation(direction, exact.frame().up()), editableFloor); }
        catch (IllegalArgumentException unsupportedFrame) { return false; }
    }
    public boolean rotate(ServerLevel level, DisposalTubeRecord exact, DisposalOrientation frame, Predicate<BlockPos> editableFloor) {
        if (frame == null || !precondition(level, exact, editableFloor)) return false;
        long revision = next(exact); if (revision < 0) return false;
        return replace(level, exact, new DisposalTubeRecord(exact.id(), exact.pos(), exact.prototypeId(), frame, exact.anchored(), exact.routingTag(), exact.routingFilter(), revision, exact.hostKind(), exact.sealedHostId(), exact.hostGeneration()));
    }
    public boolean anchor(ServerLevel level, DisposalTubeRecord exact, boolean anchored, Predicate<BlockPos> editableFloor) {
        if (!precondition(level, exact, editableFloor)) return false;
        long revision = next(exact); if (revision < 0) return false;
         boolean changed = replace(level, exact, new DisposalTubeRecord(exact.id(), exact.pos(), exact.prototypeId(), exact.frame(), anchored, exact.routingTag(), exact.routingFilter(), revision, exact.hostKind(), exact.sealedHostId(), exact.hostGeneration()));
        if (changed && exact.anchored() && !anchored) disconnected(exact);
        return changed;
    }
    public boolean configure(ServerLevel level, DisposalTubeRecord exact, String tag, String filter, Predicate<BlockPos> editableFloor) {
        if (!precondition(level, exact, editableFloor)) return false;
        long revision = next(exact); if (revision < 0) return false;
         return replace(level, exact, new DisposalTubeRecord(exact.id(), exact.pos(), exact.prototypeId(), exact.frame(), exact.anchored(), tag, filter, revision, exact.hostKind(), exact.sealedHostId(), exact.hostGeneration()));
    }
    /** Replace only the resolved tube variant; ownership, orientation and valid routing configuration remain intact. */
    public boolean convert(ServerLevel level, DisposalTubeRecord exact, String resolvedPrototypeId,
                           boolean preserveRouting, Predicate<BlockPos> editableFloor) {
        if (resolvedPrototypeId == null || resolvedPrototypeId.isBlank() || !precondition(level, exact, editableFloor)) return false;
        long revision = next(exact); if (revision < 0) return false;
        boolean changed = replace(level, exact, new DisposalTubeRecord(exact.id(), exact.pos(), resolvedPrototypeId,
                 exact.frame(), exact.anchored(), preserveRouting ? exact.routingTag() : "",
                 preserveRouting ? exact.routingFilter() : "", revision, exact.hostKind(), exact.sealedHostId(), exact.hostGeneration()));
        if (changed) disconnected(exact);
        return changed;
    }
    public boolean remove(ServerLevel level, DisposalTubeRecord exact, Predicate<BlockPos> stationFloor) {
        if (!precondition(level, exact, stationFloor)) return false;
        records.remove(exact.pos().asLong()); setDirty(); changed(level, exact, true); disconnected(exact); return true;
    }
    /** Invalidates a removed/replaced host; invoke from host lifecycle integration. */
    public boolean invalidate(ServerLevel level, BlockPos pos) {
        if (!loaded(level, pos)) return false;
        DisposalTubeRecord record = records.remove(pos.asLong());
        if (record == null) return false;
        setDirty(); changed(level, record, true); disconnected(record); return true;
    }
    private boolean precondition(ServerLevel level, DisposalTubeRecord exact, Predicate<BlockPos> exposedFloor) {
        return loaded(level, exact.pos()) && exposedFloor.test(exact.pos()) && exact.equals(records.get(exact.pos().asLong()));
    }
    private boolean replace(ServerLevel level, DisposalTubeRecord old, DisposalTubeRecord value) {
        records.put(old.pos().asLong(), value); setDirty(); changed(level, value, false); return true;
    }
    private static long next(DisposalTubeRecord record) { try { return Math.addExact(record.revision(), 1); } catch (ArithmeticException overflow) { return -1; } }
    private static void changed(ServerLevel level, DisposalTubeRecord record, boolean removed) { changeListener.accept(level, new Change(record, removed)); }
    public List<DisposalTubeRecord> inChunk(int chunkX, int chunkZ) {
        return records.values().stream().filter(r -> (r.pos().getX() >> 4) == chunkX && (r.pos().getZ() >> 4) == chunkZ)
                .sorted(Comparator.comparingLong(r -> r.pos().asLong())).toList();
    }

    public static DisposalTubeWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("schema") || tag.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported disposal topology schema");
        DisposalTubeWorldData data = new DisposalTubeWorldData();
        ListTag list = tag.getList("records", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            var direction = Direction.byName(t.getString("direction"));
            if (direction == null) throw new IllegalArgumentException("Invalid disposal tube orientation");
            if (!t.contains("up") || !t.contains("hostKind") || !t.contains("sealedHostId")) throw new IllegalArgumentException("Incomplete disposal topology record");
            var up = Direction.byName(t.getString("up"));
            if (up == null) throw new IllegalArgumentException("Invalid disposal tube up direction");
            var frame = new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(direction, up);
            var hostKind = DisposalTubeRecord.HostKind.valueOf(t.getString("hostKind"));
            var hostGeneration = t.hasUUID("hostGeneration") ? t.getUUID("hostGeneration") : null;
            DisposalTubeRecord r = new DisposalTubeRecord(t.getUUID("id"), BlockPos.of(t.getLong("pos")), t.getString("prototype"), frame, t.getBoolean("anchored"), t.getString("tag"), t.getString("filter"), t.getLong("revision"), hostKind, t.getString("sealedHostId"), hostGeneration);
            if (data.records.put(r.pos().asLong(), r) != null) throw new IllegalArgumentException("Duplicate disposal tube cell");
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        records.values().stream().sorted(Comparator.comparingLong(r -> r.pos().asLong())).forEach(r -> {
            CompoundTag t = new CompoundTag(); t.putUUID("id", r.id()); t.putLong("pos", r.pos().asLong()); t.putString("prototype", r.prototypeId()); t.putString("direction", r.frame().forward().getName()); t.putString("up", r.frame().up().getName()); t.putBoolean("anchored", r.anchored()); t.putString("tag", Objects.requireNonNullElse(r.routingTag(), "")); t.putString("filter", Objects.requireNonNullElse(r.routingFilter(), "")); t.putLong("revision", r.revision()); t.putString("hostKind", r.hostKind().name()); t.putString("sealedHostId", Objects.requireNonNullElse(r.sealedHostId(), "")); if (r.hostGeneration() != null) t.putUUID("hostGeneration", r.hostGeneration()); list.add(t);
        });
        tag.putInt("schema",1); tag.put("records", list); return tag;
    }
}

