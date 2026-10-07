package com.juicyslew.moonstation14.ms14.disposal.client;

import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Bounded authoritative records for chunks currently delivered by the server. */
public final class DisposalTubeClientCache {
    private final LinkedHashMap<Long, Snapshot> chunks = new LinkedHashMap<>(16, .75f, true);
    private ResourceLocation dimension;
    public boolean replace(ResourceLocation dimension, int x, int z, boolean reset, List<DisposalTubeRecord> records) {
        if (this.dimension != null && !this.dimension.equals(dimension)) clear();
        this.dimension = dimension;
        long key=ChunkPos.asLong(x,z); Snapshot old=chunks.get(key);
        var merged=new LinkedHashMap<java.util.UUID,DisposalTubeRecord>();
        if(!reset&&old!=null)for(var record:old.records)merged.put(record.id(),record);
        for(var record:records)merged.put(record.id(),record);
        chunks.put(key, new Snapshot(List.copyOf(merged.values())));
        return true;
    }
    public void remove(ResourceLocation dimension, int x, int z) { if (Objects.equals(this.dimension, dimension)) chunks.remove(ChunkPos.asLong(x,z)); }
    /** Exact synchronized lookup for client block collision/outline; never consults server data. */
    public Optional<DisposalTubeRecord> get(ResourceLocation dimension, net.minecraft.core.BlockPos pos) {
        if (!Objects.equals(this.dimension, dimension)) return Optional.empty();
        Snapshot snapshot=chunks.get(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
        if(snapshot==null)return Optional.empty();
        return snapshot.records.stream().filter(record->record.pos().equals(pos)).findFirst();
    }
    public List<DisposalTubeRecord> visible(ResourceLocation dimension, double x, double y, double z, double radius) {
        if (!Objects.equals(this.dimension, dimension)) return List.of();
        double r2 = radius*radius; var result = new ArrayList<DisposalTubeRecord>();
        for (Snapshot snapshot : chunks.values()) for (DisposalTubeRecord r : snapshot.records) {
            double dx=r.pos().getX()+.5-x, dy=r.pos().getY()+.5-y, dz=r.pos().getZ()+.5-z;
            if (dx*dx+dy*dy+dz*dz <= r2) result.add(r);
        }
        return List.copyOf(result);
    }
    public void clear() { chunks.clear(); dimension=null; }
    private record Snapshot(List<DisposalTubeRecord> records) { Snapshot { records=List.copyOf(records); } }
}
