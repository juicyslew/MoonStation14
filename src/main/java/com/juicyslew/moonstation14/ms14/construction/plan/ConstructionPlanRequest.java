package com.juicyslew.moonstation14.ms14.construction.plan;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Authenticated intent to change the actor's ephemeral body construction plan. */
public record ConstructionPlanRequest(UUID body, long epoch, long sequence, Action action, String recipe)
        implements CustomPacketPayload {
    public enum Action { SELECT, CLEAR, ROTATE, FAMILY_VARIANT }
    public static final Type<ConstructionPlanRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "construction_plan_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionPlanRequest> STREAM_CODEC = StreamCodec.of((b,v)->{
        b.writeUUID(v.body()); b.writeVarLong(v.epoch()); b.writeVarLong(v.sequence()); b.writeEnum(v.action()); b.writeUtf(v.recipe(),256);
    }, b->new ConstructionPlanRequest(b.readUUID(),b.readVarLong(),b.readVarLong(),b.readEnum(Action.class),b.readUtf(256)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
