package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Body-action intent; acceptance is always decided by the server. */
public record CombatModeRequest(long epoch, long sequence, boolean desiredMode) implements CustomPacketPayload {
    public static final Type<CombatModeRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "combat_mode_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatModeRequest> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarLong(v.epoch); b.writeVarLong(v.sequence); b.writeBoolean(v.desiredMode); },
            b -> new CombatModeRequest(b.readVarLong(), b.readVarLong(), b.readBoolean()));
    public CombatModeRequest { if (epoch <= 0 || sequence <= 0) throw new IllegalArgumentException("invalid combat mode request"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
