package com.juicyslew.moonstation14.ms14.combat;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server decision metadata, suitable for an eventual acknowledgement payload. */
public record CombatModeRequestResult(Status status, UUID bodyUUID, long epoch, long sequence, boolean actualMode) implements CustomPacketPayload {
    public static final Type<CombatModeRequestResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "combat_mode_request_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatModeRequestResult> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.status.ordinal()); b.writeBoolean(v.bodyUUID != null); if (v.bodyUUID != null) b.writeUUID(v.bodyUUID); b.writeVarLong(v.epoch); b.writeVarLong(v.sequence); b.writeBoolean(v.actualMode); },
            b -> { int status = b.readVarInt(); if (status < 0 || status >= Status.values().length) throw new IllegalArgumentException("invalid combat mode status"); UUID id = b.readBoolean() ? b.readUUID() : null; return new CombatModeRequestResult(Status.values()[status], id, b.readVarLong(), b.readVarLong(), b.readBoolean()); });
    public enum Status { ACCEPTED, NO_AUTHORITY, WRONG_EPOCH, REPLAY, RATE_LIMITED, NO_CAPABILITY, SET_FAILED }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
