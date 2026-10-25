package com.juicyslew.moonstation14.ms14.disposal.transport;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Dimension-owned exact gas records awaiting acceptance by a release carrier. */
public final class DisposalPendingAirSavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_disposal_pending_air";
    public static final Factory<DisposalPendingAirSavedData> FACTORY = new Factory<>(DisposalPendingAirSavedData::new, DisposalPendingAirSavedData::load, null);
    public record Record(UUID owner, BlockPos floor, GasMixture gas) { }
    private final Map<UUID, Record> records = new LinkedHashMap<>();
    public static DisposalPendingAirSavedData existing(ServerLevel level) { return level.getDataStorage().get(FACTORY, DATA_NAME); }
    public static DisposalPendingAirSavedData forMutation(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME); }
    public List<Record> records() { return List.copyOf(records.values()); }
    public boolean put(Record record) {
        Record old = records.putIfAbsent(record.owner(), record);
        if (old != null) return old.equals(record);
        setDirty(); return true;
    }
    public boolean remove(UUID owner, Record exact) {
        if (!records.remove(owner, exact)) return false;
        setDirty(); return true;
    }
    public static void retryLoaded(ServerLevel level) {
        var data = existing(level); if (data == null) return;
        for (Record record : data.records()) {
            if (!level.hasChunkAt(record.floor())) continue;
            var carrier = DisposalTransitHolder.spawnRemovalAir(level, record.floor(), record.gas());
            if (carrier.isPresent()) data.remove(record.owner(), record);
        }
    }
    public static DisposalPendingAirSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new DisposalPendingAirSavedData(); ListTag list = tag.getList("Records", CompoundTag.TAG_COMPOUND);
        for (int i=0;i<list.size();i++) { CompoundTag t=list.getCompound(i); Record r=new Record(t.getUUID("Owner"), BlockPos.of(t.getLong("Floor")), DisposalTransitHolder.decodeAir(t.getCompound("Air")).sample().orElseThrow().mixture()); data.records.put(r.owner(),r); }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list=new ListTag(); records.values().forEach(r->{ CompoundTag t=new CompoundTag(); t.putUUID("Owner",r.owner()); t.putLong("Floor",r.floor().asLong()); t.put("Air",DisposalTransitHolder.encodeAir(r.gas(),1)); list.add(t); }); tag.put("Records",list); return tag;
    }
}

