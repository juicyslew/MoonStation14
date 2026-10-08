package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;

import java.util.List;
import java.util.UUID;

/** Public, presentation-only body inventory state. Contains no owner/action credentials or storage contents. */
public record BodyAppearanceSnapshot(ResourceLocation dimension, int entityId, UUID entityUuid,
                                     long revision, List<Entry> hands, List<Entry> equipment)
        implements CustomPacketPayload {
    public static final Type<BodyAppearanceSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "body_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyAppearanceSnapshot> STREAM_CODEC = StreamCodec.of(
            (b, s) -> { b.writeResourceLocation(s.dimension); b.writeVarInt(s.entityId); b.writeUUID(s.entityUuid); b.writeVarLong(s.revision); write(b,s.hands); write(b,s.equipment); },
            b -> new BodyAppearanceSnapshot(b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readVarLong(),read(b),read(b)));
    public record Entry(String id, ItemPresentation item) { }
    private static void write(RegistryFriendlyByteBuf b, List<Entry> entries) { b.writeVarInt(entries.size()); for (var e:entries) { b.writeUtf(e.id,128); b.writeBoolean(e.item!=null); if(e.item!=null) ItemPresentation.STREAM_CODEC.encode(b,e.item); } }
    private static List<Entry> read(RegistryFriendlyByteBuf b) { int n=b.readVarInt(); if(n<0||n>64) throw new IllegalArgumentException("invalid appearance entry count"); var out=new java.util.ArrayList<Entry>(n); for(int i=0;i<n;i++) out.add(new Entry(b.readUtf(128),b.readBoolean()?ItemPresentation.STREAM_CODEC.decode(b):null)); return List.copyOf(out); }
    public BodyAppearanceSnapshot { hands=List.copyOf(hands); equipment=List.copyOf(equipment); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static BodyAppearanceSnapshot from(Entity body, LiveHands state) {
        if (body == null || state == null || !state.compatible(body)) return null;
        var handEntries=state.handIds().stream().map(id -> new Entry(id,state.stackCopy(id).map(s -> s.getCount()>1 ? s.copyWithCount(1) : s).map(ItemPresentation::fromStack).orElse(null))).toList();
        var slots=LiveHands.equipmentSnapshot(body,state).orElse(null);
        var equipment=slots==null ? List.<Entry>of() : slots.slots().stream().map(slot -> new Entry(slot.id(),slot.id().equals("pocket1") || slot.id().equals("pocket2") ? null : slot.stack().map(s -> s.getCount()>1 ? s.copyWithCount(1) : s).map(ItemPresentation::fromStack).orElse(null))).toList();
        return new BodyAppearanceSnapshot(body.level().dimension().location(),body.getId(),body.getUUID(),state.revision(),handEntries,equipment);
    }
}
