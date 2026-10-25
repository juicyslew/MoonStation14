package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A single client press nomination; all firing facts remain server-owned. */
public record GunShotRequest(long epoch, long sequence, long expectedHandRevision, String activeHand,
                             String expectedItemToken, Vec3 nominatedAimPoint) implements CustomPacketPayload {
    public static final Type<GunShotRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "gun_shot_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunShotRequest> STREAM_CODEC = StreamCodec.of((b,v) -> {
        b.writeVarLong(v.epoch); b.writeVarLong(v.sequence); b.writeVarLong(v.expectedHandRevision);
        b.writeUtf(v.activeHand,32); b.writeUtf(v.expectedItemToken,128);
        b.writeDouble(v.nominatedAimPoint.x); b.writeDouble(v.nominatedAimPoint.y); b.writeDouble(v.nominatedAimPoint.z);
    }, b -> new GunShotRequest(b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readUtf(32), b.readUtf(128),
            new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    public GunShotRequest {
        if(epoch<=0||sequence<=0||expectedHandRevision<0||activeHand==null||expectedItemToken==null||nominatedAimPoint==null
                ||!Double.isFinite(nominatedAimPoint.x)||!Double.isFinite(nominatedAimPoint.y)||!Double.isFinite(nominatedAimPoint.z))
            throw new IllegalArgumentException("invalid gun shot request");
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
