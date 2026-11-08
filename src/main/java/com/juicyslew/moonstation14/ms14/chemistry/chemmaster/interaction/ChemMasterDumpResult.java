package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/** Server acknowledgement/rejection correlated to a particular dump request phase. */
public record ChemMasterDumpResult(UUID gestureId, ChemMasterDumpRequest.Phase phase, boolean accepted,
                                   Optional<Component> reason) implements CustomPacketPayload {
    public static final Type<ChemMasterDumpResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "chemmaster_dump_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChemMasterDumpResult> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.gestureId);
                buf.writeVarInt(value.phase.ordinal());
                buf.writeBoolean(value.accepted);
                buf.writeBoolean(value.reason.isPresent());
                value.reason.ifPresent(component -> ComponentSerialization.STREAM_CODEC.encode(buf, component));
            },
            buf -> {
                UUID gesture = buf.readUUID();
                int phase = buf.readVarInt();
                if (phase < 0 || phase >= ChemMasterDumpRequest.Phase.values().length)
                    throw new IllegalArgumentException("invalid dump result phase");
                boolean accepted = buf.readBoolean();
                Optional<Component> reason = buf.readBoolean()
                        ? Optional.of(ComponentSerialization.STREAM_CODEC.decode(buf)) : Optional.empty();
                return new ChemMasterDumpResult(gesture, ChemMasterDumpRequest.Phase.values()[phase], accepted, reason);
            });

    public ChemMasterDumpResult {
        if (gestureId == null || phase == null || reason == null || (accepted && reason.isPresent()))
            throw new IllegalArgumentException("invalid dump result");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

