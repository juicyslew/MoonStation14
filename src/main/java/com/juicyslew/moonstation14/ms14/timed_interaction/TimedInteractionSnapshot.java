package com.juicyslew.moonstation14.ms14.timed_interaction;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Presentation-only state. Contains no source authority or owned item data. */
public record TimedInteractionSnapshot(ResourceLocation dimension, int bodyEntityId, UUID bodyUuid,
                                       long revision, long serverTick, List<View> views)
        implements CustomPacketPayload {
    public static final Type<TimedInteractionSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", "timed_interaction_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimedInteractionSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeResourceLocation(value.dimension); buf.writeVarInt(value.bodyEntityId);
                buf.writeUUID(value.bodyUuid); buf.writeVarLong(value.revision); buf.writeVarLong(value.serverTick);
                buf.writeVarInt(value.views.size());
                for (View view : value.views) {
                    buf.writeVarLong(view.operationID); buf.writeVarLong(view.startTick); buf.writeVarInt(view.durationTicks);
                    buf.writeEnum(view.phase); buf.writeVarLong(view.terminalTick); buf.writeVarLong(view.originatingActionSequence);
                }
            }, buf -> {
                ResourceLocation dimension = buf.readResourceLocation(); int entity = buf.readVarInt(); UUID uuid = buf.readUUID();
                long revision = buf.readVarLong(), tick = buf.readVarLong(); int count = buf.readVarInt();
                if (count < 0 || count > 32) throw new IllegalArgumentException("invalid timed view count");
                java.util.ArrayList<View> views = new java.util.ArrayList<>(count);
                for (int i=0;i<count;i++) views.add(new View(buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                        buf.readEnum(TimedOperation.Phase.class), buf.readVarLong(), buf.readVarLong()));
                return new TimedInteractionSnapshot(dimension, entity, uuid, revision, tick, views);
            });
    private static volatile Consumer<TimedInteractionSnapshot> clientHandler;
    public static void setClientHandler(Consumer<TimedInteractionSnapshot> handler) { clientHandler = handler; }
    public static void deliverClient(TimedInteractionSnapshot snapshot) { var handler=clientHandler; if(handler!=null) handler.accept(snapshot); }
    public TimedInteractionSnapshot {
        if (dimension == null || bodyUuid == null || bodyEntityId < 0 || revision < 0 || serverTick < 0 || views == null || views.size() > 32)
            throw new IllegalArgumentException("invalid timed interaction snapshot");
        views = List.copyOf(views);
    }
    public record View(long operationID, long startTick, int durationTicks, TimedOperation.Phase phase,
                       long terminalTick, long originatingActionSequence) {
        public View {
            if (operationID < 1 || startTick < 0 || durationTicks < 1 || originatingActionSequence < 0)
                throw new IllegalArgumentException("invalid timed interaction view");
            if (phase == null || (phase == TimedOperation.Phase.ACTIVE
                    ? terminalTick != -1 : terminalTick < startTick))
                throw new IllegalArgumentException("invalid timed interaction view phase/timing");
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
