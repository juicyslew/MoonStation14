package com.juicyslew.moonstation14.ms14.construction.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import java.util.function.Consumer;

public record ConstructionCraftState(long epoch,long sequence,UUID body,String recipe,int remaining,String message) implements CustomPacketPayload {
    public static final Type<ConstructionCraftState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","construction_craft_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConstructionCraftState> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeVarLong(v.epoch);b.writeVarLong(v.sequence);b.writeBoolean(v.body!=null);if(v.body!=null)b.writeUUID(v.body);b.writeUtf(v.recipe,256);b.writeVarInt(v.remaining);b.writeUtf(v.message,256);},b->new ConstructionCraftState(b.readVarLong(),b.readVarLong(),b.readBoolean()?b.readUUID():null,b.readUtf(256),b.readVarInt(),b.readUtf(256)));
    private static volatile Consumer<ConstructionCraftState> clientHandler;
    public ConstructionCraftState { if(epoch<0||sequence<0||remaining<0||remaining>64||recipe==null||recipe.length()>256||message==null||message.length()>256)throw new IllegalArgumentException("Invalid craft state"); }
    public static void setClientHandler(Consumer<ConstructionCraftState> handler){clientHandler=handler;}
    public static void deliverClient(ConstructionCraftState state){var h=clientHandler;if(h!=null)h.accept(state);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
