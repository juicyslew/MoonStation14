package com.juicyslew.moonstation14.ms14.movement.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Wire schema only. These payload types are deliberately not registered or handled yet. */
public final class MovementPayloads {
    private MovementPayloads() { }

    public static final int BUTTON_JUMP = 1;
    public static final int BUTTON_SNEAK = 2;
    public static final int BUTTON_SPRINT = 4;
    private static final int BUTTON_MASK = BUTTON_JUMP | BUTTON_SNEAK | BUTTON_SPRINT;
    private static final double MAX_COMPONENT = 30_000_000.0;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    public record Begin(long epoch) implements CustomPacketPayload {
        public static final Type<Begin> TYPE = MovementPayloads.type("movement_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, Begin> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.epoch), buf -> new Begin(readEpoch(buf)));
        public Begin { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Acknowledge(long epoch) implements CustomPacketPayload {
        public static final Type<Acknowledge> TYPE = MovementPayloads.type("movement_acknowledge");
        public static final StreamCodec<RegistryFriendlyByteBuf, Acknowledge> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.epoch), buf -> new Acknowledge(readEpoch(buf)));
        public Acknowledge { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Commit(long epoch) implements CustomPacketPayload {
        public static final Type<Commit> TYPE = MovementPayloads.type("movement_commit");
        public static final StreamCodec<RegistryFriendlyByteBuf, Commit> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.epoch), buf -> new Commit(readEpoch(buf)));
        public Commit { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Disable(long epoch) implements CustomPacketPayload {
        public static final Type<Disable> TYPE = MovementPayloads.type("movement_disable");
        public static final StreamCodec<RegistryFriendlyByteBuf, Disable> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.epoch), buf -> new Disable(readEpoch(buf)));
        public Disable { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DisableAcknowledge(long epoch) implements CustomPacketPayload {
        public static final Type<DisableAcknowledge> TYPE = MovementPayloads.type("movement_disable_acknowledge");
        public static final StreamCodec<RegistryFriendlyByteBuf, DisableAcknowledge> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.epoch), buf -> new DisableAcknowledge(readEpoch(buf)));
        public DisableAcknowledge { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResumeVanilla(long epoch) implements CustomPacketPayload {
        public static final Type<ResumeVanilla> TYPE = MovementPayloads.type("movement_resume_vanilla");
        public static final StreamCodec<RegistryFriendlyByteBuf, ResumeVanilla> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.epoch), buf -> new ResumeVanilla(readEpoch(buf)));
        public ResumeVanilla { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResumeAcknowledge(long epoch) implements CustomPacketPayload {
        public static final Type<ResumeAcknowledge> TYPE = MovementPayloads.type("movement_resume_acknowledge");
        public static final StreamCodec<RegistryFriendlyByteBuf, ResumeAcknowledge> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeLong(value.epoch), buf -> new ResumeAcknowledge(readEpoch(buf)));
        public ResumeAcknowledge { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Axes are signed quantized values in [-1000,1000]; no position is accepted from a client. */
    public record Intent(long epoch, long sequence, short wishX, short wishZ, int buttons)
            implements CustomPacketPayload {
        public static final Type<Intent> TYPE = MovementPayloads.type("movement_intent");
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeLong(value.epoch); buf.writeLong(value.sequence);
                    buf.writeShort(value.wishX); buf.writeShort(value.wishZ); buf.writeByte(value.buttons);
                }, buf -> new Intent(readEpoch(buf), readPositive(buf, "sequence"), buf.readShort(),
                        buf.readShort(), buf.readUnsignedByte()));
        public Intent {
            requireEpoch(epoch);
            if (sequence <= 0) throw new IllegalArgumentException("sequence must be positive");
            if (Math.abs((int) wishX) > 1000 || Math.abs((int) wishZ) > 1000)
                throw new IllegalArgumentException("wish axes out of range");
            if ((buttons & ~BUTTON_MASK) != 0) throw new IllegalArgumentException("unknown movement buttons");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server-authored state snapshot and input acknowledgement. */
    public record Snapshot(long epoch, long acknowledgedSequence,
                           double x, double y, double z, double velocityX, double velocityY, double velocityZ,
                           boolean onGround) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = MovementPayloads.type("movement_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeLong(value.epoch); buf.writeLong(value.acknowledgedSequence);
                    buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
                    buf.writeDouble(value.velocityX); buf.writeDouble(value.velocityY); buf.writeDouble(value.velocityZ);
                    buf.writeBoolean(value.onGround);
                }, buf -> new Snapshot(readEpoch(buf), readNonNegative(buf, "acknowledged sequence"),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readBoolean()));
        public Snapshot {
            requireEpoch(epoch);
            if (acknowledgedSequence < 0) throw new IllegalArgumentException("acknowledged sequence must be nonnegative");
            finiteBounded(x, "x"); finiteBounded(y, "y"); finiteBounded(z, "z");
            finiteBounded(velocityX, "velocityX"); finiteBounded(velocityY, "velocityY");
            finiteBounded(velocityZ, "velocityZ");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static long readEpoch(RegistryFriendlyByteBuf buf) { return requireEpoch(buf.readLong()); }
    private static long readPositive(RegistryFriendlyByteBuf buf, String label) {
        long value = buf.readLong();
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }
    private static long readNonNegative(RegistryFriendlyByteBuf buf, String label) {
        long value = buf.readLong();
        if (value < 0) throw new IllegalArgumentException(label + " must be nonnegative");
        return value;
    }
    private static long requireEpoch(long epoch) {
        if (epoch <= 0) throw new IllegalArgumentException("epoch must be positive");
        return epoch;
    }
    private static void finiteBounded(double value, String name) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_COMPONENT)
            throw new IllegalArgumentException(name + " must be finite and within world bounds");
    }
}
