package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics;
import com.juicyslew.moonstation14.ms14.animals.flight.FlightState;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Wire schemas for ghost-control sessions, authoritative snapshots, and client prediction. */
public final class GhostControlPayloads {
    private GhostControlPayloads() { }

    /** Button bits shared with the intent wire form: jump=1, sneak=2, sprint=4. */
    public static final int BUTTON_JUMP = 1;
    public static final int BUTTON_SNEAK = 2;
    public static final int BUTTON_SPRINT = 4;
    /** Shared bound for untrusted input backlog, server admission bursts, and prediction history. */
    public static final int MAX_PENDING_INTENTS = 64;
    public static final int BUTTON_MASK = BUTTON_JUMP | BUTTON_SNEAK | BUTTON_SPRINT;
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
    public record SupportedMovementReference(java.util.UUID frameId, float localMovementYaw) {
        public SupportedMovementReference {
            java.util.Objects.requireNonNull(frameId, "frameId");
            if (!Float.isFinite(localMovementYaw) || localMovementYaw < -180f || localMovementYaw > 180f)
                throw new IllegalArgumentException("local movement yaw must be finite and within [-180,180]");
        }
    }

    public record Intent(long epoch, long sequence, long intendedSimulationTick, short wishX, short wishZ, byte verticalWish,
                         int buttons, float yaw, float pitch,
                         java.util.Optional<SupportedMovementReference> supportedMovementReference) implements CustomPacketPayload {
        public static final Type<Intent> TYPE = GhostControlPayloads.type("ghost_control_intent");
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeLong(value.epoch); buf.writeLong(value.sequence); buf.writeLong(value.intendedSimulationTick);
                    buf.writeShort(value.wishX); buf.writeShort(value.wishZ); buf.writeByte(value.verticalWish);
                    buf.writeByte(value.buttons); buf.writeFloat(value.yaw); buf.writeFloat(value.pitch);
                    buf.writeBoolean(value.supportedMovementReference.isPresent());
                    value.supportedMovementReference.ifPresent(reference -> { buf.writeUUID(reference.frameId()); buf.writeFloat(reference.localMovementYaw()); });
                }, buf -> new Intent(readEpoch(buf), readPositive(buf, "sequence"), readNonNegative(buf, "intended simulation tick"), buf.readShort(), buf.readShort(),
                        buf.readByte(), buf.readUnsignedByte(), buf.readFloat(), buf.readFloat(), readSupportedMovementReference(buf)));
        public Intent {
            requireEpoch(epoch);
            if (sequence <= 0) throw new IllegalArgumentException("sequence must be positive");
            requireNonNegative(intendedSimulationTick, "intended simulation tick");
            supportedMovementReference = java.util.Objects.requireNonNull(supportedMovementReference, "supportedMovementReference");
            new GhostControlState(wishX, wishZ, verticalWish, buttons, yaw, pitch);
        }
        public Intent(long epoch, long sequence, short wishX, short wishZ, byte verticalWish, int buttons, float yaw, float pitch) {
            this(epoch, sequence, 0, wishX, wishZ, verticalWish, buttons, yaw, pitch, java.util.Optional.empty());
        }
        public Intent(long epoch, long sequence, long intendedSimulationTick, short wishX, short wishZ, byte verticalWish, int buttons, float yaw, float pitch) {
            this(epoch, sequence, intendedSimulationTick, wishX, wishZ, verticalWish, buttons, yaw, pitch, java.util.Optional.empty());
        }
        public Intent(long epoch, long sequence, short wishX, short wishZ, byte verticalWish, int buttons, float yaw, float pitch,
                      java.util.Optional<SupportedMovementReference> supportedMovementReference) {
            this(epoch, sequence, 0, wishX, wishZ, verticalWish, buttons, yaw, pitch, supportedMovementReference);
        }
        public GhostControlState toControlState() { return new GhostControlState(wishX, wishZ, verticalWish, buttons, yaw, pitch); }
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
     * Server-authored authoritative harness state. The applied acknowledgement identifies the latest update applied;
     * the settled-through sequence identifies updates finalized by an authoritative world step. Multiple ordered
     * updates may be folded before one normal physics step; this does not imply one physics step per message. Lower
     * sequences may have been applied earlier or permanently skipped (for example, across bounded-history overflow),
     * so settlement does not prove every sequence was individually used. Receivers discard updates through the
     * settled-through sequence and replay
     * newer transitions at their intended simulation ticks, relative to the snapshot's server tick and seeded by
     * its held control and natural flight state. Velocity is in blocks/second and bounded to 512 on each axis to
     * allow extreme lube launches.
     */
    public record Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind,
                           long serverGameTick, long acknowledgedAppliedSequence, long settledThroughSequence,
                           double x, double y, double z, float yaw, float pitch,
                           double velocityX, double velocityY, double velocityZ, boolean onGround,
                               float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked,
                               int externalFlightTicks, MovementMode movementMode,
                               java.util.Optional<ContainedBodyView> containedView,
                               java.util.Optional<FrameState> frameState, long movementRebaseRevision,
                               java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> angularRecoveryState,
                               java.util.OptionalLong completedSableSimulationStep,
                               java.util.Optional<com.juicyslew.moonstation14.ms14.movement.AirMotionReference> airMotionReference,
                                GhostControlState heldControlState, FlightState naturalFlightState,
                                ExternalFlightPhysics externalFlightPhysics) implements CustomPacketPayload {
        public enum MovementMode { GROUNDED, UNIT, TRANSIT }
        /** Validated current native-frame identity and storage-space body point (not an authority grant). */
        public record FrameState(java.util.UUID frameId, double x, double y, double z) {
            public FrameState {
                java.util.Objects.requireNonNull(frameId, "frameId");
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                    throw new IllegalArgumentException("frame storage point must be finite");
            }
        }
        public static final Type<Snapshot> TYPE = GhostControlPayloads.type("ghost_control_snapshot");
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeLong(value.epoch); buf.writeInt(value.harnessEntityId); buf.writeByte(wireId(value.harnessKind));
                    buf.writeLong(value.serverGameTick); buf.writeLong(value.acknowledgedAppliedSequence);
                    buf.writeLong(value.settledThroughSequence);
                    buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
                    buf.writeFloat(value.yaw); buf.writeFloat(value.pitch);
                    buf.writeDouble(value.velocityX); buf.writeDouble(value.velocityY); buf.writeDouble(value.velocityZ);
                    buf.writeBoolean(value.onGround); buf.writeFloat(value.surfaceFactor);
                    buf.writeFloat(value.voluntarySpeedFactor); buf.writeBoolean(value.movementBlocked);
                    buf.writeVarInt(value.externalFlightTicks); buf.writeEnum(value.movementMode);
                    buf.writeBoolean(value.containedView.isPresent());
                    value.containedView.ifPresent(view -> {
                        buf.writeUUID(view.ownerIdentity()); buf.writeBoolean(view.worldTarget().isPresent());
                        view.worldTarget().ifPresent(buf::writeBlockPos); buf.writeBoolean(view.cameraCollision());
                        buf.writeBoolean(view.renderParent().isPresent()); view.renderParent().ifPresent(parent -> {
                            buf.writeInt(parent.entityId()); buf.writeUUID(parent.entityUuid());
                            buf.writeDouble(parent.bodyFeetOffset().x); buf.writeDouble(parent.bodyFeetOffset().y);
                            buf.writeDouble(parent.bodyFeetOffset().z);
                        });
                    });
                    buf.writeBoolean(value.frameState.isPresent());
                    value.frameState.ifPresent(frame -> { buf.writeUUID(frame.frameId()); buf.writeDouble(frame.x()); buf.writeDouble(frame.y()); buf.writeDouble(frame.z()); });
                    buf.writeLong(value.movementRebaseRevision);
                    buf.writeBoolean(value.angularRecoveryState.isPresent()); value.angularRecoveryState.ifPresent(s -> writeAngular(buf, s));
                    buf.writeBoolean(value.completedSableSimulationStep.isPresent());
                    if (value.completedSableSimulationStep.isPresent()) buf.writeLong(value.completedSableSimulationStep.getAsLong());
                    buf.writeBoolean(value.airMotionReference.isPresent());
                    value.airMotionReference.ifPresent(r -> { buf.writeUUID(r.frameId()); buf.writeDouble(r.pointVelocity().x()); buf.writeDouble(r.pointVelocity().y()); buf.writeDouble(r.pointVelocity().z()); });
                    buf.writeShort(value.heldControlState.wishX()); buf.writeShort(value.heldControlState.wishZ());
                    buf.writeByte(value.heldControlState.verticalWish()); buf.writeByte(value.heldControlState.buttons());
                    buf.writeFloat(value.heldControlState.yaw()); buf.writeFloat(value.heldControlState.pitch());
                    buf.writeEnum(value.naturalFlightState);
                    buf.writeEnum(value.externalFlightPhysics);
                }, buf -> {
                    long epoch = readEpoch(buf); int entityId = readNonNegativeEntityId(buf); MobHarnessKind kind = readHarnessKind(buf);
                    long tick = readNonNegative(buf, "server game tick");
                    long applied = readNonNegative(buf, "acknowledged applied sequence");
                    long settled = readNonNegative(buf, "settled through sequence");
                    double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
                    float yaw = buf.readFloat(), pitch = buf.readFloat();
                    double vx = buf.readDouble(), vy = buf.readDouble(), vz = buf.readDouble(); boolean onGround = buf.readBoolean();
                    float surface = buf.readFloat(), voluntary = buf.readFloat(); boolean blocked = buf.readBoolean();
                    int flight = readExternalFlightTicks(buf); MovementMode mode = buf.readEnum(MovementMode.class);
                    java.util.Optional<ContainedBodyView> view = readContainedView(buf);
                    java.util.Optional<FrameState> frame = readFrameState(buf);
                    long rebase = readNonNegative(buf, "movement rebase revision");
                    var angular = readAngular(buf); var sableStep = readSableStep(buf); var airReference = readAirMotionReference(buf);
                    GhostControlState held = new GhostControlState(buf.readShort(), buf.readShort(), buf.readByte(), buf.readUnsignedByte(), buf.readFloat(), buf.readFloat());
                    FlightState naturalFlight = buf.readEnum(FlightState.class);
                    ExternalFlightPhysics externalFlightPhysics = buf.readEnum(ExternalFlightPhysics.class);
                    return new Snapshot(epoch, entityId, kind, tick, applied, settled, x, y, z, yaw, pitch,
                            vx, vy, vz, onGround, surface, voluntary, blocked, flight, mode, view, frame, rebase,
                            angular, sableStep, airReference, held, naturalFlight, externalFlightPhysics);
                });
        public Snapshot {
            requireEpoch(epoch);
            containedView = java.util.Objects.requireNonNull(containedView);
            frameState = java.util.Objects.requireNonNull(frameState);
            angularRecoveryState = java.util.Objects.requireNonNull(angularRecoveryState);
            completedSableSimulationStep = java.util.Objects.requireNonNull(completedSableSimulationStep);
            airMotionReference = java.util.Objects.requireNonNull(airMotionReference);
            heldControlState = java.util.Objects.requireNonNull(heldControlState);
            naturalFlightState = java.util.Objects.requireNonNull(naturalFlightState);
            externalFlightPhysics = java.util.Objects.requireNonNull(externalFlightPhysics, "externalFlightPhysics");
            if (harnessKind == MobHarnessKind.GHOST && naturalFlightState != FlightState.GROUNDED)
                throw new IllegalArgumentException("ghost cannot have natural flight state");
            new GhostControlState(heldControlState.wishX(), heldControlState.wishZ(), heldControlState.verticalWish(),
                    heldControlState.buttons(), heldControlState.yaw(), heldControlState.pitch());
            if (completedSableSimulationStep.isPresent() && completedSableSimulationStep.getAsLong() <= 0) throw new IllegalArgumentException("completed Sable simulation step must be positive");
            if (angularRecoveryState.isPresent() && (harnessKind == MobHarnessKind.GHOST || movementMode != MovementMode.GROUNDED))
                throw new IllegalArgumentException("angular recovery requires a grounded character harness");
            if (harnessKind == MobHarnessKind.GHOST && frameState.isPresent()) throw new IllegalArgumentException("ghost cannot have frame state");
            if (harnessKind == MobHarnessKind.GHOST && containedView.isPresent()) throw new IllegalArgumentException("ghost cannot have contained view");
            if (harnessEntityId < 0) throw new IllegalArgumentException("harness entity id must be nonnegative");
            if (harnessKind == null) throw new IllegalArgumentException("harness kind must be specified");
            if (movementMode == null || harnessKind == MobHarnessKind.GHOST && movementMode != MovementMode.GROUNDED)
                throw new IllegalArgumentException("invalid movement mode for harness kind");
            requireNonNegative(serverGameTick, "server game tick");
            requireNonNegative(acknowledgedAppliedSequence, "acknowledged applied sequence");
            requireNonNegative(settledThroughSequence, "settled through sequence");
            if (settledThroughSequence < acknowledgedAppliedSequence)
                throw new IllegalArgumentException("settled through sequence must be at least acknowledged applied sequence");
            requireNonNegative(movementRebaseRevision, "movement rebase revision");
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
        /** Previous full union shape; defaults the newly synchronized flight-physics mode. */
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind,
                        long serverGameTick, long acknowledgedAppliedSequence, long settledThroughSequence,
                        double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround,
                        float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked,
                        int externalFlightTicks, MovementMode movementMode,
                        java.util.Optional<ContainedBodyView> containedView,
                        java.util.Optional<FrameState> frameState, long movementRebaseRevision,
                        java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> angularRecoveryState,
                        java.util.OptionalLong completedSableSimulationStep,
                        java.util.Optional<com.juicyslew.moonstation14.ms14.movement.AirMotionReference> airMotionReference,
                        GhostControlState heldControlState, FlightState naturalFlightState) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, settledThroughSequence,
                    x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor,
                    movementBlocked, externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision,
                    angularRecoveryState, completedSableSimulationStep, airMotionReference, heldControlState,
                    naturalFlightState, ExternalFlightPhysics.SCRIPTED_IMPULSE);
        }
        /** Main control shape predating settlement metadata; defaults newer frame metadata and seeds neutral control. */
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind,
                        long serverGameTick, long acknowledgedAppliedSequence,
                        double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround,
                        float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked,
                        int externalFlightTicks, MovementMode movementMode,
                        java.util.Optional<ContainedBodyView> containedView,
                        GhostControlState heldControlState, FlightState naturalFlightState) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence,
                    acknowledgedAppliedSequence, x, y, z, yaw, pitch, velocityX, velocityY, velocityZ,
                    onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked, externalFlightTicks,
                    movementMode, containedView, java.util.Optional.empty(), 0, java.util.Optional.empty(),
                    java.util.OptionalLong.empty(), java.util.Optional.empty(), heldControlState, naturalFlightState);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround, float surfaceFactor,
                        float voluntarySpeedFactor, boolean movementBlocked, int externalFlightTicks, MovementMode movementMode,
                        java.util.Optional<ContainedBodyView> containedView, java.util.Optional<FrameState> frameState,
                        long movementRebaseRevision) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked,
                    externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision, java.util.Optional.empty(), java.util.OptionalLong.empty(), java.util.Optional.empty(), GhostControlState.neutral(), FlightState.GROUNDED);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick, long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch, double velocityX, double velocityY, double velocityZ, boolean onGround, float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked, int externalFlightTicks, MovementMode movementMode, java.util.Optional<ContainedBodyView> containedView, java.util.Optional<FrameState> frameState, long movementRebaseRevision, java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> angularRecoveryState) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, acknowledgedAppliedSequence, x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked, externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision, angularRecoveryState, java.util.OptionalLong.empty(), java.util.Optional.empty(), GhostControlState.neutral(), FlightState.GROUNDED);
        }
        /** Previous full canonical shape, before held/natural control and settlement metadata were added. */
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround,
                        float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked, int externalFlightTicks,
                        MovementMode movementMode, java.util.Optional<ContainedBodyView> containedView,
                        java.util.Optional<FrameState> frameState, long movementRebaseRevision,
                        java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> angularRecoveryState,
                        java.util.OptionalLong completedSableSimulationStep,
                        java.util.Optional<com.juicyslew.moonstation14.ms14.movement.AirMotionReference> airMotionReference) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence,
                    acknowledgedAppliedSequence, x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround,
                    surfaceFactor, voluntarySpeedFactor, movementBlocked, externalFlightTicks, movementMode,
                    containedView, frameState, movementRebaseRevision, angularRecoveryState,
                    completedSableSimulationStep, airMotionReference, GhostControlState.neutral(), FlightState.GROUNDED);
        }
        public Snapshot withAngularRecoveryState(java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> state) {
            return new Snapshot(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, settledThroughSequence, x, y, z,
                    yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked,
                    externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision, state, completedSableSimulationStep,
                    airMotionReference, heldControlState, naturalFlightState, externalFlightPhysics);
        }
        public Snapshot withServerTemporalMetadata(java.util.OptionalLong completedStep,
                java.util.Optional<com.juicyslew.moonstation14.ms14.movement.AirMotionReference> airReference) {
            return new Snapshot(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, settledThroughSequence, x, y, z,
                    yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor,
                    movementBlocked, externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision,
                    angularRecoveryState, completedStep, airReference, heldControlState, naturalFlightState, externalFlightPhysics);
        }
        public Snapshot withInputSettlement(long settledSequence) {
            return new Snapshot(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, settledSequence,
                    x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor,
                    movementBlocked, externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision,
                    angularRecoveryState, completedSableSimulationStep, airMotionReference, heldControlState, naturalFlightState, externalFlightPhysics);
        }
        public Snapshot withControlState(GhostControlState held, FlightState naturalFlight) {
            return new Snapshot(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, settledThroughSequence,
                    x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor,
                    movementBlocked, externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision,
                    angularRecoveryState, completedSableSimulationStep, airMotionReference, held, naturalFlight, externalFlightPhysics);
        }
        public Snapshot withExternalFlightPhysics(ExternalFlightPhysics mode) {
            return new Snapshot(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, settledThroughSequence,
                    x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor,
                    movementBlocked, externalFlightTicks, movementMode, containedView, frameState, movementRebaseRevision,
                    angularRecoveryState, completedSableSimulationStep, airMotionReference, heldControlState, naturalFlightState, mode);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround,
                        float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked, int externalFlightTicks,
                        MovementMode movementMode, java.util.Optional<ContainedBodyView> containedView,
                        java.util.Optional<FrameState> frameState) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked,
                    externalFlightTicks, movementMode, containedView, frameState, 0);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround,
                        float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked, int externalFlightTicks,
                        MovementMode movementMode, java.util.Optional<ContainedBodyView> containedView) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked,
                    externalFlightTicks, movementMode, containedView, java.util.Optional.empty());
        }
        /** Compatibility constructor preserving the former ghost snapshot defaults. */
        public Snapshot(long epoch, int ghostEntityId, long serverGameTick, long acknowledgedAppliedSequence,
                        double x, double y, double z, float yaw, float pitch) {
            this(epoch, ghostEntityId, MobHarnessKind.GHOST, serverGameTick, acknowledgedAppliedSequence,
                      x, y, z, yaw, pitch, 0, 0, 0, false, 1, 1, false, 0, MovementMode.GROUNDED, java.util.Optional.empty(), GhostControlState.neutral(), FlightState.GROUNDED);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind,
                        long serverGameTick, long acknowledgedAppliedSequence,
                        double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround,
                        float surfaceFactor, float voluntarySpeedFactor, boolean movementBlocked) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence,
                    x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, onGround, surfaceFactor,
                      voluntarySpeedFactor, movementBlocked, 0, MovementMode.GROUNDED, java.util.Optional.empty(), GhostControlState.neutral(), FlightState.GROUNDED);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround, float surfaceFactor,
                        float voluntarySpeedFactor, boolean movementBlocked, MovementMode movementMode) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                     velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked, 0,
                       movementMode, java.util.Optional.empty(), GhostControlState.neutral(), FlightState.GROUNDED);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround, float surfaceFactor,
                        float voluntarySpeedFactor, boolean movementBlocked, MovementMode movementMode,
                        java.util.Optional<ContainedBodyView> containedView) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked,
                     0, movementMode, containedView, GhostControlState.neutral(), FlightState.GROUNDED);
        }
        public Snapshot(long epoch, int harnessEntityId, MobHarnessKind harnessKind, long serverGameTick,
                        long acknowledgedAppliedSequence, double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, boolean onGround, float surfaceFactor,
                        float voluntarySpeedFactor, boolean movementBlocked, int externalFlightTicks, MovementMode movementMode,
                        java.util.Optional<ContainedBodyView> containedView, GhostControlState heldControlState) {
            this(epoch, harnessEntityId, harnessKind, serverGameTick, acknowledgedAppliedSequence, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, onGround, surfaceFactor, voluntarySpeedFactor, movementBlocked,
                    externalFlightTicks, movementMode, containedView, heldControlState, FlightState.GROUNDED);
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

    private static java.util.Optional<SupportedMovementReference> readSupportedMovementReference(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? java.util.Optional.of(new SupportedMovementReference(buf.readUUID(), buf.readFloat())) : java.util.Optional.empty();
    }

    private static java.util.Optional<Snapshot.FrameState> readFrameState(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? java.util.Optional.of(new Snapshot.FrameState(buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble())) : java.util.Optional.empty();
    }

    private static java.util.OptionalLong readSableStep(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return java.util.OptionalLong.empty();
        long step = readNonNegative(buf, "completed Sable simulation step");
        if (step == 0) throw new IllegalArgumentException("completed Sable simulation step must be positive");
        return java.util.OptionalLong.of(step);
    }

    private static java.util.Optional<com.juicyslew.moonstation14.ms14.movement.AirMotionReference> readAirMotionReference(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return java.util.Optional.empty();
        return java.util.Optional.of(new com.juicyslew.moonstation14.ms14.movement.AirMotionReference(buf.readUUID(),
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    }

    private static void writeAngular(RegistryFriendlyByteBuf buf, com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State s) {
        var q = s.orientation(); var w = s.worldAngularVelocity();
        buf.writeDouble(q.w()); buf.writeDouble(q.x()); buf.writeDouble(q.y()); buf.writeDouble(q.z());
        buf.writeDouble(w.x()); buf.writeDouble(w.y()); buf.writeDouble(w.z()); buf.writeDouble(s.airborneSeconds()); buf.writeBoolean(s.recoveryActive());
    }
    private static java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> readAngular(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return java.util.Optional.empty();
        return java.util.Optional.of(new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State(
                new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Quat(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readDouble(), buf.readBoolean()));
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
