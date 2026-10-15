package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Wire schemas for the standalone ghost-control play-phase registrations; prediction handling is not installed. */
public final class GhostControlPayloads {
    private GhostControlPayloads() { }

    /** Button bits shared with the intent wire form: jump=1, sneak=2, sprint=4. */
    public static final int BUTTON_JUMP = 1;
    public static final int BUTTON_SNEAK = 2;
    public static final int BUTTON_SPRINT = 4;
    private static final int BUTTON_MASK = BUTTON_JUMP | BUTTON_SNEAK | BUTTON_SPRINT;
    private static final double WORLD_BOUND = 30_000_000.0;
    /** Velocity wire bound in blocks/second; deliberately allows extreme lube launches. */
    public static final double MAX_VELOCITY_BLOCKS_PER_SECOND = 512.0;
    /** Conservative wire limit for finite, nonnegative character surface multipliers. */
    private static final float MAX_SURFACE_FACTOR = 64.0f;
    /** Conservative wire limit for finite, nonnegative character voluntary-speed multipliers. */
    private static final float MAX_VOLUNTARY_SPEED_FACTOR = 8.0f;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    private static int wireId(MobHarnessKind kind) {
        if (kind == null) throw new IllegalArgumentException("harness kind must be specified");
        return switch (kind) {
            case GHOST -> 0;
            case CHARACTER -> 1;
        };
    }

    /** The entity id is a server-resolved binding hint, never a client-selected body identity. */
    public record Begin(long mindEpoch, int harnessEntityId, MobHarnessKind harnessKind, double ghostWalkSpeed, double ghostSprintSpeed) implements CustomPacketPayload {
        public static final Type<Begin> TYPE = GhostControlPayloads.type("ghost_control_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, Begin> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeLong(value.mindEpoch); buf.writeInt(value.harnessEntityId); buf.writeByte(wireId(value.harnessKind)); if (value.harnessKind == MobHarnessKind.GHOST) { buf.writeDouble(value.ghostWalkSpeed); buf.writeDouble(value.ghostSprintSpeed); } },
                buf -> { long epoch = readEpoch(buf); int id = readNonNegativeEntityId(buf); MobHarnessKind kind = readHarnessKind(buf); return kind == MobHarnessKind.GHOST ? new Begin(epoch, id, kind, buf.readDouble(), buf.readDouble()) : new Begin(epoch, id, kind); });
        public Begin {
            requireEpoch(mindEpoch);
            if (harnessEntityId < 0) throw new IllegalArgumentException("harness entity id must be nonnegative");
            if (harnessKind == null) throw new IllegalArgumentException("harness kind must be specified");
            if (harnessKind == MobHarnessKind.GHOST && (!Double.isFinite(ghostWalkSpeed) || ghostWalkSpeed <= 0 || !Double.isFinite(ghostSprintSpeed) || ghostSprintSpeed <= 0)) throw new IllegalArgumentException("ghost movement speeds must be finite and positive");
            if (harnessKind == MobHarnessKind.CHARACTER && (ghostWalkSpeed != 0 || ghostSprintSpeed != 0)) throw new IllegalArgumentException("character begin cannot carry ghost movement");
        }
        public Begin(long epoch, int id, MobHarnessKind kind) { this(epoch, id, kind, kind == MobHarnessKind.GHOST ? 8 : 0, kind == MobHarnessKind.GHOST ? 12 : 0); }
        /** Compatibility constructor for the existing ghost-only controller. */
        public Begin(long mindEpoch, int ghostEntityId) { this(mindEpoch, ghostEntityId, MobHarnessKind.GHOST); }
        /** Compatibility accessor for existing ghost-only code. */
        public int ghostEntityId() { return harnessEntityId; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ready(long epoch) implements CustomPacketPayload {
        public static final Type<Ready> TYPE = GhostControlPayloads.type("ghost_control_ready");
        public static final StreamCodec<RegistryFriendlyByteBuf, Ready> STREAM_CODEC = epochCodec(Ready::new, Ready::epoch);
        public Ready { requireEpoch(epoch); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Announces a server-resolved prospective body without changing the currently committed owner. */
    public record Offer(long currentEpoch, int targetEntityId, MobHarnessKind targetKind) implements CustomPacketPayload {
        public static final Type<Offer> TYPE = GhostControlPayloads.type("ghost_control_offer");
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeLong(value.currentEpoch); buf.writeInt(value.targetEntityId); buf.writeByte(wireId(value.targetKind)); },
                buf -> new Offer(readEpoch(buf), readNonNegativeEntityId(buf), readHarnessKind(buf)));
        public Offer {
            requireEpoch(currentEpoch);
            if (targetEntityId < 0) throw new IllegalArgumentException("harness entity id must be nonnegative");
            if (targetKind == null) throw new IllegalArgumentException("harness kind must be specified");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client readiness for the exact server-offered entity; never grants control by itself. */
    public record OfferReady(long currentEpoch, int targetEntityId, MobHarnessKind targetKind) implements CustomPacketPayload {
        public static final Type<OfferReady> TYPE = GhostControlPayloads.type("ghost_control_offer_ready");
        public static final StreamCodec<RegistryFriendlyByteBuf, OfferReady> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeLong(value.currentEpoch); buf.writeInt(value.targetEntityId); buf.writeByte(wireId(value.targetKind)); },
                buf -> new OfferReady(readEpoch(buf), readNonNegativeEntityId(buf), readHarnessKind(buf)));
        public OfferReady {
            requireEpoch(currentEpoch);
            if (targetEntityId < 0) throw new IllegalArgumentException("harness entity id must be nonnegative");
            if (targetKind == null) throw new IllegalArgumentException("harness kind must be specified");
        }
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

    /**
     * Server-authored authoritative harness state. The acknowledgement is a settled-through sequence: the greatest
     * sequence actually applied, with every lower sequence either applied earlier or permanently skipped (for
     * example, because only one intent may be accepted per tick). It does not mean every sequence up to the
     * acknowledgement was applied. Receivers may discard pending intents through this sequence and replay newer
     * ones. Velocity is in blocks/second and bounded to 512 on each axis to allow extreme lube launches.
     */
    public record Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind,
                           long serverGameTick, long acknowledgedAppliedSequence,
                           double x, double y, double z, float yaw, float pitch,
                           double velocityX, double velocityY, double velocityZ, boolean onGround,
                              float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked,
                              int externalFlightTicks,
                              MovementMode movementMode, java.util.Optional<ContainedBodyView> containedView) implements CustomPacketPayload {
        public enum MovementMode { GROUNDED, UNIT, TRANSIT }
        public static final Type<Snapshot> TYPE = GhostControlPayloads.type("ghost_control_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeLong(value.epoch); buf.writeInt(value.harnessEntityId); buf.writeByte(wireId(value.harnessKind));
                    buf.writeLong(value.serverGameTick); buf.writeLong(value.acknowledgedAppliedSequence);
                    buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
                    buf.writeFloat(value.yaw); buf.writeFloat(value.pitch);
                    buf.writeDouble(value.velocityX); buf.writeDouble(value.velocityY); buf.writeDouble(value.velocityZ);
                    buf.writeBoolean(value.onGround); buf.writeFloat(value.surfaceFactor);
                     buf.writeFloat(value.voluntarySpeedFactor); buf.writeBoolean(value.movementBlocked); buf.writeVarInt(value.externalFlightTicks); buf.writeEnum(value.movementMode);
                     buf.writeBoolean(value.containedView.isPresent());
                       value.containedView.ifPresent(view -> { buf.writeUUID(view.ownerIdentity()); buf.writeBoolean(view.worldTarget().isPresent());
                           view.worldTarget().ifPresent(pos -> buf.writeBlockPos(pos)); buf.writeBoolean(view.cameraCollision());
                           buf.writeBoolean(view.renderParent().isPresent()); view.renderParent().ifPresent(parent -> {
                               buf.writeInt(parent.entityId()); buf.writeUUID(parent.entityUuid());
                               buf.writeDouble(parent.bodyFeetOffset().x); buf.writeDouble(parent.bodyFeetOffset().y); buf.writeDouble(parent.bodyFeetOffset().z);
                           });
                       });
                }, buf -> new Snapshot(readEpoch(buf), readNonNegativeEntityId(buf), readHarnessKind(buf),
                        readNonNegative(buf, "server game tick"), readNonNegative(buf, "acknowledged applied sequence"),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(),
                           buf.readFloat(), buf.readFloat(), buf.readBoolean(), readExternalFlightTicks(buf), buf.readEnum(MovementMode.class), readContainedView(buf)));
        public Snapshot {
            requireEpoch(epoch);
            containedView = java.util.Objects.requireNonNull(containedView);
            if (harnessKind == MobHarnessKind.GHOST && containedView.isPresent()) throw new IllegalArgumentException("ghost cannot have contained view");
            if (harnessEntityId < 0) throw new IllegalArgumentException("harness entity id must be nonnegative");
            if (harnessKind == null) throw new IllegalArgumentException("harness kind must be specified");
            if (movementMode == null || harnessKind == MobHarnessKind.GHOST && movementMode != MovementMode.GROUNDED)
                throw new IllegalArgumentException("invalid movement mode for harness kind");
            requireNonNegative(serverGameTick, "server game tick");
            requireNonNegative(acknowledgedAppliedSequence, "acknowledged applied sequence");
            if (externalFlightTicks < 0) throw new IllegalArgumentException("external flight ticks must be nonnegative");
            if ((harnessKind == MobHarnessKind.GHOST || movementMode != MovementMode.GROUNDED) && externalFlightTicks != 0)
                throw new IllegalArgumentException("external flight requires a grounded character harness");
            requireWorldCoordinate(x, "x");
            requireWorldCoordinate(y, "y");
            requireWorldCoordinate(z, "z");
            if (!Float.isFinite(yaw) || yaw < -180.0f || yaw > 180.0f)
                throw new IllegalArgumentException("yaw must be finite and within [-180,180]");
            if (!Float.isFinite(pitch) || pitch < -90.0f || pitch > 90.0f)
                throw new IllegalArgumentException("pitch must be finite and within [-90,90]");
            requireVelocity(velocityX, "velocity x");
            requireVelocity(velocityY, "velocity y");
            requireVelocity(velocityZ, "velocity z");
            requireFactor(surfaceFactor, "surface factor", MAX_SURFACE_FACTOR);
            requireFactor(voluntarySpeedFactor, "voluntary speed factor", MAX_VOLUNTARY_SPEED_FACTOR);
        }
        /** Compatibility constructor preserving the former ghost snapshot defaults. */
        public Snapshot(long epoch, int ghostEntityId, long serverGameTick, long acknowledgedAppliedSequence,
                        double x, double y, double z, float yaw, float pitch) {
            this(epoch, ghostEntityId, MobHarnessKind.GHOST, serverGameTick, acknowledgedAppliedSequence,
                     x, y, z, yaw, pitch, 0, 0, 0, false, 1, 1, false, 0, MovementMode.GROUNDED, java.util.Optional.empty());
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind,
                        long serverGameTick, long acknowledgedAppliedSequence,
                        double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround,
                        float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence,
                    x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor,
                     voluntarySpeedFactor, movementBlocked, 0, MovementMode.GROUNDED, java.util.Optional.empty());
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround, float surfaceFactor,
                        float voluntarySpeedFactor, boolean movementBlocked, MovementMode movementMode) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                     velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked, 0,
                     movementMode, java.util.Optional.empty());
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround, float surfaceFactor,
                        float voluntarySpeedFactor, boolean movementBlocked, MovementMode movementMode,
                        java.util.Optional<ContainedBodyView> containedView) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked,
                    0, movementMode, containedView);
        }
        /** Compatibility accessor for existing ghost-only code. */
        public int ghostEntityId() { return harnessEntityId; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static java.util.Optional<ContainedBodyView> readContainedView(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return java.util.Optional.empty();
        java.util.UUID owner = buf.readUUID();
        java.util.Optional<BlockPos> target = buf.readBoolean() ? java.util.Optional.of(buf.readBlockPos().immutable()) : java.util.Optional.empty();
        boolean collision = buf.readBoolean();
        java.util.Optional<ContainedBodyView.RenderParent> parent = java.util.Optional.empty();
        if (buf.readBoolean()) parent = java.util.Optional.of(new ContainedBodyView.RenderParent(readNonNegativeEntityId(buf), buf.readUUID(),
                new net.minecraft.world.phys.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
        return java.util.Optional.of(new ContainedBodyView(owner, target, collision, parent));
    }

    private static int readExternalFlightTicks(RegistryFriendlyByteBuf buf) {
        int ticks = buf.readVarInt();
        if (ticks < 0) throw new IllegalArgumentException("external flight ticks must be nonnegative");
        return ticks;
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
        if (value < 0) throw new IllegalArgumentException("harness entity id must be nonnegative");
        return value;
    }
    private static MobHarnessKind readHarnessKind(RegistryFriendlyByteBuf buf) {
        return switch (buf.readUnsignedByte()) {
            case 0 -> MobHarnessKind.GHOST;
            case 1 -> MobHarnessKind.CHARACTER;
            default -> throw new IllegalArgumentException("unknown mob harness kind");
        };
    }
    private static long readNonNegative(RegistryFriendlyByteBuf buf, String label) {
        long value = buf.readLong();
        requireNonNegative(value, label);
        return value;
    }
    private static void requireNonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must be nonnegative");
    }
    private static void requireWorldCoordinate(double value, String axis) {
        if (!Double.isFinite(value) || value < -WORLD_BOUND || value > WORLD_BOUND)
            throw new IllegalArgumentException(axis + " must be finite and within Minecraft world bounds");
    }
    private static void requireVelocity(double value, String axis) {
        if (!Double.isFinite(value) || value < -MAX_VELOCITY_BLOCKS_PER_SECOND || value > MAX_VELOCITY_BLOCKS_PER_SECOND)
            throw new IllegalArgumentException(axis + " must be finite and within +/-" + MAX_VELOCITY_BLOCKS_PER_SECOND + " blocks/second");
    }
    private static void requireFactor(float value, String label, float maximum) {
        if (!Float.isFinite(value) || value < 0.0f || value > maximum)
            throw new IllegalArgumentException(label + " must be finite and within [0," + maximum + "]");
    }
    private static long requireEpoch(long epoch) {
        if (epoch <= 0) throw new IllegalArgumentException("epoch must be positive");
        return epoch;
    }
}
