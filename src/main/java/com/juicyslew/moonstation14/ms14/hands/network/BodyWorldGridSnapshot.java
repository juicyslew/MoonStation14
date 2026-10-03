package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import java.util.*;
import java.util.function.Consumer;

/** Read-only, NBT-free observation. */
public record BodyWorldGridSnapshot(long requestedEpoch, long sequence, Reason reason, UUID accountId, UUID bodyId,
                                    long epoch, long handRevision, UUID entityId, String kind, int width, int height,
                                    List<Entry> entries, UUID session, long hostRevision) implements CustomPacketPayload {
    public enum Reason { OK, NO_AUTHORITY, NOT_READY, STALE, REPLAY, RATE_LIMITED, LIMIT, UNAVAILABLE }
    public BodyWorldGridSnapshot(long requestedEpoch, long sequence, Reason reason, UUID accountId, UUID bodyId,
                                 long epoch, long handRevision, UUID entityId, String kind, int width, int height,
                                 List<Entry> entries) {
        this(requestedEpoch, sequence, reason, accountId, bodyId, epoch, handRevision, entityId, kind, width, height,
                entries, null, 0);
    }
    public record Entry(String token, String itemId, int count, int x, int y, int rotation) {
        public Entry {
            if (token == null || token.isBlank() || token.length() > ItemToken.MAX_LENGTH || count < 1 || count > 99
                    || x < 0 || x > 7 || y < 0 || y > 3 || (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)
                    || itemId == null || itemId.length() > 256) throw new IllegalArgumentException("Invalid world grid entry");
        }
    }
    public static final Type<BodyWorldGridSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_world_grid_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWorldGridSnapshot> STREAM_CODEC = StreamCodec.of((b,s) -> {
        b.writeVarLong(s.requestedEpoch); b.writeVarLong(s.sequence); b.writeEnum(s.reason);
        if (s.reason == Reason.OK) {
            b.writeUUID(s.accountId); b.writeUUID(s.bodyId); b.writeVarLong(s.epoch); b.writeVarLong(s.handRevision); b.writeUUID(s.entityId); b.writeUUID(s.session); b.writeVarLong(s.hostRevision);
            b.writeUtf(s.kind,4); b.writeByte(s.width); b.writeByte(s.height); b.writeVarInt(s.entries.size());
            for (Entry e:s.entries) { b.writeUtf(e.token,ItemToken.MAX_LENGTH); b.writeUtf(e.itemId,256); b.writeVarInt(e.count); b.writeByte(e.x); b.writeByte(e.y); b.writeVarInt(e.rotation); }
        }
    }, b -> {
        long requested=b.readVarLong(), seq=b.readVarLong(); Reason r=b.readEnum(Reason.class);
        if(r!=Reason.OK) { if(b.isReadable()) throw new IllegalArgumentException("Unexpected rejection data"); return new BodyWorldGridSnapshot(requested,seq,r,null,null,0,0,null,null,0,0,List.of()); }
         UUID account=b.readUUID(), body=b.readUUID(); long epoch=b.readVarLong(), rev=b.readVarLong(); UUID entity=b.readUUID(), session=b.readUUID(); long hostRevision=b.readVarLong();
        String kind=b.readUtf(4); int w=b.readUnsignedByte(), h=b.readUnsignedByte(), n=b.readVarInt();
        if(n<0||n>32) throw new IllegalArgumentException("Invalid entry count"); List<Entry> es=new ArrayList<>(n);
        for(int i=0;i<n;i++) es.add(new Entry(b.readUtf(ItemToken.MAX_LENGTH),b.readUtf(256),b.readVarInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readVarInt()));
        if(b.isReadable()) throw new IllegalArgumentException("Trailing snapshot data"); return new BodyWorldGridSnapshot(requested,seq,r,account,body,epoch,rev,entity,kind,w,h,es,session,hostRevision);
    });
    public BodyWorldGridSnapshot {
        if(requestedEpoch<=0||sequence<=0||reason==null||entries==null) throw new IllegalArgumentException("Invalid snapshot"); entries=List.copyOf(entries);
        if(reason==Reason.OK) {
            if(accountId==null||bodyId==null||entityId==null||session==null||hostRevision<0||epoch<=0||handRevision<0||entries.size()>32
                    || !("bag".equals(kind)&&width==7&&height==4 || "belt".equals(kind)&&width==8&&height==2)) throw new IllegalArgumentException("Invalid grid host");
            var tokens=new HashSet<String>(); var occupied=GridGeometry.Snapshot.empty(GridGeometry.StorageMask.rectangle(width,height));
            for(Entry e:entries) { if(!tokens.add(e.token)) throw new IllegalArgumentException("Duplicate token");
                var id=ResourceLocation.tryParse(e.itemId); if(id==null||!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item");
                var p=occupied.place(e.token,ItemFootprints.forId(id),new GridGeometry.Cell(e.x,e.y),GridGeometry.Rotation.values()[e.rotation/90]);
                if(!p.accepted()) throw new IllegalArgumentException("Invalid placement"); occupied=p.snapshot(); }
        } else if(accountId!=null||bodyId!=null||epoch!=0||handRevision!=0||entityId!=null||kind!=null||width!=0||height!=0||!entries.isEmpty()||session!=null||hostRevision!=0) throw new IllegalArgumentException("Invalid rejection");
    }
    public static BodyWorldGridSnapshot rejected(BodyWorldGridQuery q, Reason r) { return new BodyWorldGridSnapshot(q.epoch(),q.sequence(),r,null,null,0,0,null,null,0,0,List.of()); }
    private static volatile Consumer<BodyWorldGridSnapshot> clientHandler;
    public static void setClientHandler(Consumer<BodyWorldGridSnapshot> h) { clientHandler=h; }
    static void deliverClient(BodyWorldGridSnapshot s) { var h=clientHandler; if(h!=null) h.accept(s); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
