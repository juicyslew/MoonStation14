package com.juicyslew.moonstation14.ms14.examine;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.MoonStation14;

/** Authoritative result tagged to its exact requesting actor session and selected target. */
public record ExamineResponse(long requestId, ExamineTarget target, UUID actorUuid, long epoch,
                              boolean accepted, Component name, Component description,
                              List<ExamineEntry> entries, boolean detailsAllowed,
                              UUID controllerIncarnation) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 64;
    private static final int MAX_COMPONENT_JSON = 8192;
    public static final Type<ExamineResponse> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "examine_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExamineResponse> STREAM_CODEC = StreamCodec.of(
            ExamineResponse::write, ExamineResponse::read);
    public ExamineResponse {
        Objects.requireNonNull(target);
        Objects.requireNonNull(actorUuid);
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many examine entries");
        if (!accepted && (!entries.isEmpty() || detailsAllowed))
            throw new IllegalArgumentException("denied response cannot contain details");
        if (!detailsAllowed && entries.stream().anyMatch(entry -> entry.visibility() == ExamineEntry.Visibility.DETAIL))
            throw new IllegalArgumentException("response cannot disclose unauthorized detail entries");
    }
    /** Compatibility constructor for existing response producers and tests. */
    public ExamineResponse(long requestId, ExamineTarget target, UUID actorUuid, long epoch,
                           boolean accepted, Component name, Component description,
                           List<ExamineEntry> entries, boolean detailsAllowed) {
        this(requestId, target, actorUuid, epoch, accepted, name, description, entries, detailsAllowed, null);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void write(RegistryFriendlyByteBuf buf, ExamineResponse value) {
        buf.writeLong(value.requestId); value.target.write(buf); buf.writeUUID(value.actorUuid); buf.writeLong(value.epoch);
        buf.writeBoolean(value.accepted); writeComponent(buf, value.name); writeComponent(buf, value.description);
        buf.writeVarInt(value.entries.size());
        for (ExamineEntry entry : value.entries) {
            buf.writeEnum(entry.visibility()); writeComponent(buf, entry.text()); buf.writeUtf(entry.group(), 128); buf.writeVarInt(entry.priority());
        }
        buf.writeBoolean(value.detailsAllowed);
        buf.writeBoolean(value.controllerIncarnation != null);
        if (value.controllerIncarnation != null) buf.writeUUID(value.controllerIncarnation);
    }
    private static ExamineResponse read(RegistryFriendlyByteBuf buf) {
        long requestId = buf.readLong(); ExamineTarget target = ExamineTarget.read(buf); UUID actor = buf.readUUID();
        long epoch = buf.readLong(); boolean accepted = buf.readBoolean(); Component name = readComponent(buf);
        Component description = readComponent(buf); int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("invalid examine entry count");
        java.util.ArrayList<ExamineEntry> entries = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new ExamineEntry(buf.readEnum(ExamineEntry.Visibility.class),
                readComponent(buf), buf.readUtf(128), buf.readVarInt()));
        boolean detailsAllowed = buf.readBoolean();
        UUID controllerIncarnation = buf.readBoolean() ? buf.readUUID() : null;
        return new ExamineResponse(requestId, target, actor, epoch, accepted, name, description, entries,
                detailsAllowed, controllerIncarnation);
    }
    private static void writeComponent(RegistryFriendlyByteBuf buf, Component component) {
        String json = net.minecraft.network.chat.Component.Serializer.toJson(component, buf.registryAccess());
        if (json.length() > MAX_COMPONENT_JSON) throw new IllegalArgumentException("examine component too long");
        buf.writeUtf(json, MAX_COMPONENT_JSON);
    }
    private static Component readComponent(RegistryFriendlyByteBuf buf) {
        return net.minecraft.network.chat.Component.Serializer.fromJson(buf.readUtf(MAX_COMPONENT_JSON), buf.registryAccess());
    }
}
