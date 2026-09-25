package com.juicyslew.moonstation14.ms14.player_body_control.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Wire schema only. Payloads are intentionally not registered or handled yet. */
public final class GhostControlPayloads {
    private GhostControlPayloads() { }

    /** Button bits shared with the intent wire form: jump=1, sneak=2, sprint=4. */
    public static final int BUTTON_JUMP = 1;
    public static final int BUTTON_SNEAK = 2;
    public static final int BUTTON_SPRINT = 4;
    private static final int BUTTON_MASK = BUTTON_JUMP | BUTTON_SNEAK | BUTTON_SPRINT;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    /** The entity id is a server-resolved binding hint, never a client-selected body identity. */
    public record Begin(long mindEpoch, int ghostEntityId) implements CustomPacketPayload {
        public static final Type<Begin> TYPE = GhostControlPayloads.type("ghost_control_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, Begin> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeLong(value.mindEpoch); buf.writeInt(value.ghostEntityId); },
                buf -> new Begin(readEpoch(buf), readNonNegativeEntityId(buf)));
        public Begin {
            requireEpoch(mindEpoch);
            if (ghostEntityId < 0) throw new IllegalArgumentException("ghost entity id must be nonnegative");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ready(long epoch) implements CustomPacketPayload {
        public static final Type<Ready> TYPE = GhostControlPayloads.type("ghost_control_ready");
        public static final StreamCodec<RegistryFriendlyByteBuf, Ready> STREAM_CODEC = epochCodec(Ready::new, Ready::epoch);
        public Ready { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Commit(long epoch) implements CustomPacketPayload {
        public static final Type<Commit> TYPE = GhostControlPayloads.type("ghost_control_commit");
        public static final StreamCodec<RegistryFriendlyByteBuf, Commit> STREAM_CODEC = epochCodec(Commit::new, Commit::epoch);
        public Commit { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Quantized wishes contain no client-authored position or entity/body target. */
    public record Intent(long epoch, long sequence, short wishX, short wishZ, byte verticalWish,
                         int buttons, float yaw, float pitch) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = GhostControlPayloads.type("ghost_control_intent");
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeLong(value.epoch); buf.writeLong(value.sequence);
                    buf.writeShort(value.wishX); buf.writeShort(value.wishZ); buf.writeByte(value.verticalWish);
                    buf.writeByte(value.buttons); buf.writeFloat(value.yaw); buf.writeFloat(value.pitch);
                }, buf -> new Intent(readEpoch(buf), readPositive(buf, "sequence"), buf.readShort(), buf.readShort(),
                        buf.readByte(), buf.readUnsignedByte(), buf.readFloat(), buf.readFloat()));
        public Intent {
            requireEpoch(epoch);
            if (sequence <= 0) throw new IllegalArgumentException("sequence must be positive");
            if (Math.abs((int) wishX) > 1000 || Math.abs((int) wishZ) > 1000)
                throw new IllegalArgumentException("wish axes out of range");
            if (verticalWish < -1 || verticalWish > 1)
                throw new IllegalArgumentException("vertical wish out of range");
            if ((buttons & ~BUTTON_MASK) != 0) throw new IllegalArgumentException("unknown ghost control buttons");
            if (!Float.isFinite(yaw) || yaw < -180.0f || yaw > 180.0f)
                throw new IllegalArgumentException("yaw must be finite and within [-180,180]");
            if (!Float.isFinite(pitch) || pitch < -90.0f || pitch > 90.0f)
                throw new IllegalArgumentException("pitch must be finite and within [-90,90]");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Stops the current epoch only; it carries no target and grants no authority over a body. */
    public record Stop(long epoch) implements CustomPacketPayload {
        public static final Type<Stop> TYPE = GhostControlPayloads.type("ghost_control_stop");
        public static final StreamCodec<RegistryFriendlyByteBuf, Stop> STREAM_CODEC = epochCodec(Stop::new, Stop::epoch);
        public Stop { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> epochCodec(
            java.util.function.LongFunction<T> factory, java.util.function.ToLongFunction<T> epoch) {
        return StreamCodec.of((buf, value) -> buf.writeLong(epoch.applyAsLong(value)),
                buf -> factory.apply(readEpoch(buf)));
    }

    private static long readEpoch(RegistryFriendlyByteBuf buf) { return requireEpoch(buf.readLong()); }
    private static long readPositive(RegistryFriendlyByteBuf buf, String label) {
        long value = buf.readLong();
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }
    private static int readNonNegativeEntityId(RegistryFriendlyByteBuf buf) {
        int value = buf.readInt();
        if (value < 0) throw new IllegalArgumentException("ghost entity id must be nonnegative");
        return value;
    }
    private static long requireEpoch(long epoch) {
        if (epoch <= 0) throw new IllegalArgumentException("epoch must be positive");
        return epoch;
    }
}
