package com.juicyslew.moonstation14.ms14.construction.plan;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Authoritative plan snapshot, including inactive snapshots used to confirm clear. */
public record ConstructionPlanState(UUID body, long epoch, long sequence, long revision, String recipe, int rotation)
        implements CustomPacketPayload {
    public static final Type<ConstructionPlanState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "construction_plan_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionPlanState> STREAM_CODEC = StreamCodec.of((b,v)->{
        b.writeUUID(v.body()); b.writeVarLong(v.epoch()); b.writeVarLong(v.sequence()); b.writeVarLong(v.revision());
        b.writeUtf(v.recipe() == null ? "" : v.recipe(), 256); b.writeVarInt(v.rotation());
    }, b->{UUID body=b.readUUID();long epoch=b.readVarLong(),sequence=b.readVarLong(),revision=b.readVarLong();String recipe=b.readUtf(256);int rotation=b.readVarInt();return new ConstructionPlanState(body,epoch,sequence,revision,recipe.isEmpty()?null:recipe,rotation);});
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

