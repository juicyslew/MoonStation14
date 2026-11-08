package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GunStopShootRequest(long epoch,long triggerId,String hand,String token) implements CustomPacketPayload {
    public static final Type<GunStopShootRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14","gun_stop_shoot_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GunStopShootRequest> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeVarLong(v.epoch);b.writeVarLong(v.triggerId);b.writeUtf(v.hand,32);b.writeUtf(v.token,128);},b->new GunStopShootRequest(b.readVarLong(),b.readVarLong(),b.readUtf(32),b.readUtf(128)));
    public GunStopShootRequest { if(epoch<=0||triggerId<=0||hand==null||token==null)throw new IllegalArgumentException("invalid stop request"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
