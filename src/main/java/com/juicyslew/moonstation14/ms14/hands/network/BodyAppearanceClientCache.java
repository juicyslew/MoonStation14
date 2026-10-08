package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Read-only public appearance state, distinct from the private owner/action proof cache. */
public final class BodyAppearanceClientCache {
    private final Map<Key, BodyAppearanceSnapshot> values = new HashMap<>();
    private ResourceLocation dimension;
    private record Key(ResourceLocation dimension, UUID uuid) { }
    public boolean accept(BodyAppearanceSnapshot snapshot, ResourceLocation activeDimension) {
        if (!Objects.equals(snapshot.dimension(),activeDimension)) return false;
        if (dimension != null && !dimension.equals(activeDimension)) clear();
        dimension = activeDimension;
        var key = new Key(snapshot.dimension(), snapshot.entityUuid());
        var old = values.get(key);
        if (old != null && (old.entityId() != snapshot.entityId() || old.revision() >= snapshot.revision())) return false;
        values.put(key, snapshot); return true;
    }
    public Optional<BodyAppearanceSnapshot> get(ResourceLocation dimension, UUID uuid, int entityId) {
        var value=values.get(new Key(dimension,uuid));
        return value!=null && value.entityId()==entityId ? Optional.of(value) : Optional.empty();
    }
    public void remove(ResourceLocation dimension, UUID uuid, int entityId) {
        var key=new Key(dimension,uuid); var value=values.get(key); if(value!=null&&value.entityId()==entityId) values.remove(key);
    }
    public void clearDimension(ResourceLocation dimension) {
        values.keySet().removeIf(key -> key.dimension().equals(dimension));
        if (Objects.equals(this.dimension,dimension)) this.dimension=null;
    }
    public void clear() { values.clear(); dimension=null; }
}
