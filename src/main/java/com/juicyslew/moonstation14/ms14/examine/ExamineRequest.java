package com.juicyslew.moonstation14.ms14.examine;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.MoonStation14;

/** Client intent only; actor and epoch are checked against server session authority. */
public record ExamineRequest(long requestId, UUID actorUuid, long epoch, ExamineTarget target,
                             UUID controllerIncarnation) implements CustomPacketPayload {
    public static final Type<ExamineRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "examine_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExamineRequest> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeLong(value.requestId); buf.writeUUID(value.actorUuid); buf.writeLong(value.epoch); value.target.write(buf);
                writeOptionalUuid(buf, value.controllerIncarnation);
            },
            buf -> new ExamineRequest(buf.readLong(), buf.readUUID(), buf.readLong(), ExamineTarget.read(buf), readOptionalUuid(buf)));
    public ExamineRequest {
        Objects.requireNonNull(actorUuid);
        Objects.requireNonNull(target);
        if (epoch < 0 || (target.kind() == ExamineTarget.Kind.ENTITY && target.entityId() <= 0))
            throw new IllegalArgumentException("invalid examine request identity");
    }
    /** Compatibility constructor for an initial query before the client has a controller stamp. */
    public ExamineRequest(long requestId, UUID actorUuid, long epoch, ExamineTarget target) {
        this(requestId, actorUuid, epoch, target, null);
    }
    private static void writeOptionalUuid(RegistryFriendlyByteBuf buf, UUID value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeUUID(value);
    }
    private static UUID readOptionalUuid(RegistryFriendlyByteBuf buf) { return buf.readBoolean() ? buf.readUUID() : null; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

