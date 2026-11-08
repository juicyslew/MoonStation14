package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/** Client intent for one server-owned ChemMaster dump gesture. */
public record ChemMasterDumpRequest(UUID gestureId, Phase phase, long bodyEpoch, long requestSequence,
                                    UUID sourceId, ResourceLocation dimension, Optional<BlockPos> target)
        implements CustomPacketPayload {
    public enum Phase { BEGIN, RELEASE, CANCEL }

    public static final Type<ChemMasterDumpRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "chemmaster_dump_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChemMasterDumpRequest> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.gestureId);
                buf.writeVarInt(value.phase.ordinal());
                buf.writeVarLong(value.bodyEpoch);
                buf.writeVarLong(value.requestSequence);
                buf.writeUUID(value.sourceId);
                buf.writeResourceLocation(value.dimension);
                buf.writeBoolean(value.target.isPresent());
                value.target.ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buf, pos));
            },
            buf -> {
                UUID gesture = buf.readUUID();
                int phase = buf.readVarInt();
                if (phase < 0 || phase >= Phase.values().length) throw new IllegalArgumentException("invalid dump phase");
                long epoch = buf.readVarLong(), sequence = buf.readVarLong();
                UUID source = buf.readUUID();
                ResourceLocation dimension = buf.readResourceLocation();
                Optional<BlockPos> target = buf.readBoolean()
                        ? Optional.of(BlockPos.STREAM_CODEC.decode(buf)) : Optional.empty();
                return new ChemMasterDumpRequest(gesture, Phase.values()[phase], epoch, sequence, source, dimension, target);
            });

    public ChemMasterDumpRequest {
        if (gestureId == null || phase == null || bodyEpoch <= 0 || requestSequence < 0 || sourceId == null
                || dimension == null || target == null) throw new IllegalArgumentException("invalid dump request");
        if ((phase == Phase.RELEASE) != target.isPresent())
            throw new IllegalArgumentException("only RELEASE carries a target");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

