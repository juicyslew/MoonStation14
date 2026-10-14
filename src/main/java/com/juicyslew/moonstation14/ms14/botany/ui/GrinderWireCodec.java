package com.juicyslew.moonstation14.ms14.botany.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class GrinderWireCodec {
    private static final int MAX_SLOTS = 64, MAX_ERROR = 128;
    private GrinderWireCodec() { }
    static void writeControl(RegistryFriendlyByteBuf b, GrinderControl c) {
        b.writeUUID(c.sessionId()); b.writeUUID(c.placement()); b.writeVarLong(c.expectedStateRevision());
        b.writeVarInt(c.operation().ordinal()); b.writeVarInt(c.slotIndex());
    }
    static GrinderControl readControl(RegistryFriendlyByteBuf b) {
        UUID session=b.readUUID(), placement=b.readUUID(); long revision=b.readVarLong();
        int op=b.readVarInt(); if(op<0||op>=GrinderControl.Operation.values().length) throw new IllegalArgumentException("invalid grinder operation");
        return new GrinderControl(session,placement,revision,GrinderControl.Operation.values()[op],b.readVarInt());
    }
    static void writeSnapshot(RegistryFriendlyByteBuf b, GrinderSnapshot s) {
        b.writeUUID(s.sessionId()); b.writeUUID(s.placement()); b.writeUUID(s.body()); b.writeVarLong(s.bodyEpoch());
        b.writeResourceLocation(s.dimension()); BlockPos.STREAM_CODEC.encode(b,s.position()); b.writeVarLong(s.stateRevision()); b.writeVarLong(s.beakerRevision());
        if(s.chamber().size()>MAX_SLOTS) throw new IllegalArgumentException("too many grinder slots"); b.writeVarInt(s.chamber().size());
        for(var slot:s.chamber()) { b.writeVarInt(slot.index()); b.writeVarLong(slot.revision()); b.writeBoolean(slot.proof()!=null); if(slot.proof()!=null)b.writeUUID(slot.proof()); ItemStack.OPTIONAL_STREAM_CODEC.encode(b,slot.item()); }
        b.writeBoolean(s.powered()); b.writeBoolean(s.active()); b.writeVarLong(s.startTick()); b.writeVarLong(s.endTick()); b.writeBoolean(s.open());
        b.writeBoolean(s.rejection()!=null); if(s.rejection()!=null)b.writeUtf(s.rejection(),MAX_ERROR);
    }
    static GrinderSnapshot readSnapshot(RegistryFriendlyByteBuf b) {
        UUID id=b.readUUID(), placement=b.readUUID(), body=b.readUUID(); long epoch=b.readVarLong(); ResourceLocation dim=b.readResourceLocation(); BlockPos pos=BlockPos.STREAM_CODEC.decode(b);
        long revision=b.readVarLong(), beaker=b.readVarLong(); int count=b.readVarInt(); if(count<0||count>MAX_SLOTS)throw new IllegalArgumentException("invalid grinder slot count");
        List<GrinderSnapshot.Slot> slots=new ArrayList<>(count); for(int i=0;i<count;i++) slots.add(new GrinderSnapshot.Slot(b.readVarInt(),b.readVarLong(),b.readBoolean()?b.readUUID():null,ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));
        boolean powered=b.readBoolean(), active=b.readBoolean(); long start=b.readVarLong(), end=b.readVarLong(); boolean open=b.readBoolean(); Optional<String> error=b.readBoolean()?Optional.of(b.readUtf(MAX_ERROR)):Optional.empty();
        return new GrinderSnapshot(id,placement,body,epoch,dim,pos,revision,beaker,slots,powered,active,start,end,open,error.orElse(null));
    }
}
