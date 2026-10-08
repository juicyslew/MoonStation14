package com.juicyslew.moonstation14.ms14.construction.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Dimension-persistent construction intent/progress, separate from block and inventory state. */
public final class ConstructionWorldData extends SavedData {
    public static final String DATA_NAME = "moonstation14_construction";
    public static final Factory<ConstructionWorldData> FACTORY = new Factory<>(ConstructionWorldData::new, ConstructionWorldData::load, null);

    /** hostId is a new UUID for each placed foundation incarnation. */
    public record Record(BlockPos pos, UUID hostId, String hostBlock, String recipeId, String goal,
                         String current, int edgeIndex, int stepIndex, boolean anchored) {
        public Record(BlockPos pos, UUID hostId, String hostBlock, String recipeId, String goal,
                      String current, int edgeIndex, int stepIndex) {
            this(pos, hostId, hostBlock, recipeId, goal, current, edgeIndex, stepIndex, true);
        }
        public Record {
            pos = Objects.requireNonNull(pos).immutable(); Objects.requireNonNull(hostId);
            require(hostBlock); require(recipeId); require(goal); require(current);
            if (edgeIndex < 0 || stepIndex < 0) throw new IllegalArgumentException("negative construction progress");
        }
        private static void require(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("blank construction field"); }
        public boolean complete() { return current.equals(goal); }
    }

    private final Map<Long, Record> records = new HashMap<>();
    private static final ThreadLocal<Boolean> INTERNAL_BLOCK_WRITE = ThreadLocal.withInitial(() -> false);
    public static boolean internalBlockWrite() { return INTERNAL_BLOCK_WRITE.get(); }
    public static boolean setBlockInternal(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags) {
        boolean old = INTERNAL_BLOCK_WRITE.get(); INTERNAL_BLOCK_WRITE.set(true);
        try { return level.setBlock(pos, state, flags); }
        finally { if (old) INTERNAL_BLOCK_WRITE.set(true); else INTERNAL_BLOCK_WRITE.remove(); }
    }
    public static void invalidateExternalBlockWrite(ServerLevel level, BlockPos pos) {
        if (internalBlockWrite() || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        existing(level).ifPresent(data -> {
            Record record = data.records.remove(pos.asLong());
            if (record != null) data.setDirty();
        });
    }
    public static Optional<ConstructionWorldData> existing(ServerLevel level) {
        return Optional.ofNullable(level.getDataStorage().get(FACTORY, DATA_NAME));
    }
    public static ConstructionWorldData forMutation(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Construction mutation requires server thread");
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
    public Optional<Record> get(BlockPos pos) { return Optional.ofNullable(records.get(pos.asLong())); }
    public boolean put(ServerLevel level, Record record) {
        if (!level.getServer().isSameThread() || !loaded(level, record.pos()) || records.containsKey(record.pos().asLong())) return false;
        records.put(record.pos().asLong(), record); setDirty(); return true;
    }
    /** Compare full incarnation token before progression; stale work can never advance a replacement host. */
    public boolean replace(ServerLevel level, Record expected, Record replacement) {
        if (!level.getServer().isSameThread() || !loaded(level, expected.pos()) || !expected.pos().equals(replacement.pos())
                || !expected.hostId().equals(replacement.hostId())
                || !expected.equals(records.get(expected.pos().asLong()))) return false;
        records.put(expected.pos().asLong(), replacement); setDirty(); return true;
    }
    public boolean remove(ServerLevel level, Record expected) {
        if (!level.getServer().isSameThread() || !loaded(level, expected.pos()) || !expected.equals(records.get(expected.pos().asLong())) || !records.remove(expected.pos().asLong(), expected)) return false;
        setDirty(); return true;
    }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()
                && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
    public static ConstructionWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("schema") != 1 && tag.getInt("schema") != 2) throw new IllegalArgumentException("Unsupported construction schema");
        var data = new ConstructionWorldData(); var list = tag.getList("records", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var r = decodeRecord(list.getCompound(i));
            if (data.records.put(r.pos().asLong(), r) != null) throw new IllegalArgumentException("Duplicate construction cell");
        }
        return data;
    }
    public static Record decodeRecord(CompoundTag t) {
        return new Record(BlockPos.of(t.getLong("pos")), t.getUUID("host"), t.getString("block"), t.getString("recipe"),
                t.getString("goal"), t.getString("current"), t.getInt("edge"), t.getInt("step"),
                !t.contains("anchored") || t.getBoolean("anchored"));
    }
    public static CompoundTag encodeRecord(Record r) {
        var t = new CompoundTag(); t.putLong("pos", r.pos().asLong()); t.putUUID("host", r.hostId()); t.putString("block", r.hostBlock());
        t.putString("recipe", r.recipeId()); t.putString("goal", r.goal()); t.putString("current", r.current());
        t.putInt("edge", r.edgeIndex()); t.putInt("step", r.stepIndex()); t.putBoolean("anchored", r.anchored()); return t;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        records.values().stream().sorted(Comparator.comparingLong(r -> r.pos().asLong())).forEach(r -> {
            list.add(encodeRecord(r));
        });
        tag.putInt("schema", 2); tag.put("records", list); return tag;
    }
}
