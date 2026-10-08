package com.juicyslew.moonstation14.ms14.construction.network;

import com.juicyslew.moonstation14.ms14.construction.domain.CraftQueue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Authenticated intent; body identity is always server-derived. */
public record ConstructionCraftRequest(long epoch, long sequence, long handsRevision, String recipe, int quantity,
                                       boolean cancel) implements CustomPacketPayload {
    public static final Type<ConstructionCraftRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "construction_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionCraftRequest> STREAM_CODEC = StreamCodec.of(
            (b,v)->{b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeVarLong(v.handsRevision);b.writeUtf(v.recipe,256);b.writeVarInt(v.quantity);b.writeBoolean(v.cancel);},
            b->new ConstructionCraftRequest(b.readVarLong(),b.readVarLong(),b.readVarLong(),b.readUtf(256),b.readVarInt(),b.readBoolean()));
    public ConstructionCraftRequest { if(epoch<=0||sequence<=0||handsRevision<0||recipe==null||recipe.isBlank()||recipe.length()>256||(!cancel&&(quantity<1||quantity>CraftQueue.MAX_QUANTITY))||cancel&&quantity!=0)throw new IllegalArgumentException("Invalid craft request"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
