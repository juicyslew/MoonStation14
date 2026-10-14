package com.juicyslew.moonstation14.ms14.ghost_roles.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Bounded wire DTOs for the ghost-role menu. These payloads express requests only; they never grant control. */
public final class GhostRolePayloads {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_TEXT_LENGTH = 128;
    private GhostRolePayloads() { }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    public record Context(UUID ghost, long epoch) {
        public Context {
            if (ghost == null || epoch <= 0) throw new IllegalArgumentException("invalid canonical ghost context");
        }
        static void write(RegistryFriendlyByteBuf buf, Context value) { buf.writeUUID(value.ghost); buf.writeLong(value.epoch); }
        static Context read(RegistryFriendlyByteBuf buf) { return new Context(buf.readUUID(), readEpoch(buf)); }
    }

    public record Entry(UUID target, String roleId, String displayName, boolean teleport, boolean takeover) {
        public Entry {
            if (target == null || roleId == null || roleId.isBlank() || roleId.length() > MAX_TEXT_LENGTH
                    || displayName == null || displayName.length() > MAX_TEXT_LENGTH || (!teleport && !takeover))
                throw new IllegalArgumentException("invalid ghost role entry");
        }
    }

    public record Snapshot(Context context, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = GhostRolePayloads.type("ghost_roles_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    Context.write(buf, value.context); buf.writeVarInt(value.entries.size());
                    for (Entry entry : value.entries) {
                        buf.writeUUID(entry.target); buf.writeUtf(entry.roleId, MAX_TEXT_LENGTH);
                        buf.writeUtf(entry.displayName, MAX_TEXT_LENGTH); buf.writeBoolean(entry.teleport); buf.writeBoolean(entry.takeover);
                    }
                }, buf -> {
                    Context context = Context.read(buf); int count = buf.readVarInt();
                    if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("too many ghost roles");
                    java.util.ArrayList<Entry> entries = new java.util.ArrayList<>(count);
                    for (int i = 0; i < count; i++) entries.add(new Entry(buf.readUUID(), buf.readUtf(MAX_TEXT_LENGTH),
                            buf.readUtf(MAX_TEXT_LENGTH), buf.readBoolean(), buf.readBoolean()));
                    return new Snapshot(context, entries);
                });
        public Snapshot { if (context == null || entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("invalid snapshot"); entries = List.copyOf(entries); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Request(Context context, UUID correlation, UUID target, Action action) implements CustomPacketPayload {
        public static final Type<Request> TYPE = GhostRolePayloads.type("ghost_roles_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { Context.write(buf, value.context); buf.writeUUID(value.correlation); buf.writeUUID(value.target); buf.writeByte(value.action.ordinal()); },
                buf -> { Context c = Context.read(buf); UUID correlation = buf.readUUID(), target = buf.readUUID(); int action = buf.readUnsignedByte();
                    if (action >= Action.values().length) throw new IllegalArgumentException("invalid role action");
                    return new Request(c, correlation, target, Action.values()[action]); });
        public Request { if (context == null || correlation == null || target == null || action == null) throw new IllegalArgumentException("invalid request"); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client subscription refresh request; context is still bound to the committed ghost epoch. */
    public record SnapshotRequest(Context context) implements CustomPacketPayload {
        public static final Type<SnapshotRequest> TYPE = GhostRolePayloads.type("ghost_roles_snapshot_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotRequest> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> Context.write(buf, value.context),
                buf -> new SnapshotRequest(Context.read(buf)));
        public SnapshotRequest { if (context == null) throw new IllegalArgumentException("invalid snapshot request"); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TeleportLook(float yaw, float pitch) {
        public TeleportLook {
            if (!Float.isFinite(yaw) || yaw < -180f || yaw > 180f
                    || !Float.isFinite(pitch) || pitch < -90f || pitch > 90f)
                throw new IllegalArgumentException("invalid teleport look");
        }
    }

    public record Result(Context context, UUID correlation, boolean success, String message,
                         TeleportLook teleportLook) implements CustomPacketPayload {
        public static final Type<Result> TYPE = GhostRolePayloads.type("ghost_roles_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { Context.write(buf, value.context); buf.writeUUID(value.correlation); buf.writeBoolean(value.success); buf.writeUtf(value.message, MAX_TEXT_LENGTH); buf.writeBoolean(value.teleportLook != null); if (value.teleportLook != null) { buf.writeFloat(value.teleportLook.yaw); buf.writeFloat(value.teleportLook.pitch); } },
                buf -> new Result(Context.read(buf), buf.readUUID(), buf.readBoolean(), buf.readUtf(MAX_TEXT_LENGTH),
                        buf.readBoolean() ? new TeleportLook(buf.readFloat(), buf.readFloat()) : null));
        public Result(Context context, UUID correlation, boolean success, String message) {
            this(context, correlation, success, message, null);
        }
        public Result { if (context == null || correlation == null || message == null || message.length() > MAX_TEXT_LENGTH
                || (!success && teleportLook != null)) throw new IllegalArgumentException("invalid result"); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public enum Action { TELEPORT, REQUEST_ROLE }
    private static long readEpoch(RegistryFriendlyByteBuf buf) { long epoch = buf.readLong(); if (epoch <= 0) throw new IllegalArgumentException("invalid ghost epoch"); return epoch; }
}
