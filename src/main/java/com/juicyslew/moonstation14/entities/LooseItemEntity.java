package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;

/** Persistent loose object with independent motion; it deliberately has no ItemEntity pickup/merge/lifespan behavior. */
public class LooseItemEntity extends ItemEntity implements com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending,
        net.neoforged.neoforge.entity.IEntityWithComplexSpawn {
    private static final double FLOOR_SUPPORT_DEPTH = .02;
    private static final double MOVEMENT_EPSILON = 1.0e-6;
    private static final EntityDataAccessor<Float> ROTATION = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SETTLED = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> FRAME_ORIENTATION = SynchedEntityData.defineId(
            LooseItemEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private float angularVelocity;
    private float previousRotationAngle;
    private float currentRotationAngle;
    private long nextSupportCheck;
    private boolean motionBlockedByUnloaded;
    private int creativePickupDelay;
    private final LooseClientPositionInterpolation clientPosition = new LooseClientPositionInterpolation();
    private org.joml.Quaterniond lastSafeWorldOrientation;
    private Vec3 lastSafeFrameStoragePoint;
    private final com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState frameAnchorState = new com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState();
    @Override public com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState sable$frameAnchorState() { return frameAnchorState; }
    private long nativeMoveSequence;
    private NativeMoveCapture nativeMoveCapture;
    private Vec3 nativeMoveBefore;
    private Vec3 nativeMoveRequested;
    private Object nativeMovePreviousCollisionInfo;
    private boolean nativeMoveInFlight;
    private net.minecraft.server.level.ServerLevel nativeMoveLevel;
    private com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint nativeMoveBeforeActorPoint;
    private ClientPresentationObserver clientPresentationObserver;
    private java.util.function.Consumer<NativeFrameContact> nativeFrameContactObserver;
    private java.util.function.Consumer<NativeMoveCapture> nativeMoveCaptureObserver;
    private boolean firstJugImpactDiagnosticLogged;
    private long lastNormalResponseSequence = -1;
    public record NativeFrameContact(Object collisionInfo, java.util.UUID frameId, BlockPos storagePos,
                                     Vec3 normal, boolean verticalCollision, boolean moveInFlight,
                                     Vec3 entityPosition, Vec3 entityVelocity, long serverTick) { }

    /** Primitive-only copy for explicitly armed client-local cargo diagnostics. */
    public record ClientPoint(double x, double y, double z) {
        static ClientPoint copy(Vec3 value) { return value == null ? null : new ClientPoint(value.x, value.y, value.z); }
    }
    public enum ClientObservationPhase { RAW_FRAME_PACKET, CLIENT_TICK }
    public enum ClientPacketClassification { FRAME_STORAGE, UNKNOWN_SUBLEVEL_STORAGE, WORLD_OR_ALREADY_SUBLEVEL }
    public record ClientPresentationSample(java.util.UUID entityUuid, int entityId, ClientObservationPhase phase,
            int entityTickCount, long clientWorldGameTime, long monotonicNanos, boolean actuallyInSubLevel,
            ClientPacketClassification packetClassification, java.util.UUID helperFrameId, ClientPoint rawPacketPoint,
            ClientPoint previousClientWorld, ClientPoint oldXBeforeTick, ClientPoint xoBeforeTick,
            ClientPoint projectedFrameWorld, ClientPoint finalClientWorld, ClientPoint oldXAfterTick,
            ClientPoint xoAfterTick, java.util.UUID committedRenderFrameId, ClientPoint storageAnchor,
            ClientPoint localRenderAnchorPrevious, ClientPoint localRenderAnchorCurrent,
            boolean frameProjectionPaused, java.util.UUID nativeTrackingFrameId, boolean clientSettled,
            boolean clientOnGround, int clientStackCount, boolean clientStackEmpty, ClientPoint clientVelocity) { }
    public record ClientRenderState(java.util.UUID committedFrameId, ClientPoint storageAnchor,
                                    boolean frameProjectionPaused, java.util.UUID nativeTrackingFrameId) { }
    @FunctionalInterface public interface ClientPresentationObserver {
        void observe(ClientPresentationSample sample);
    }

    /** Client-only opt-in for one already-loaded entity; null remains the allocation-free default. */
    public void clientPresentationObserver(ClientPresentationObserver observer) {
        if (level().isClientSide) clientPresentationObserver = observer;
    }
    public boolean hasClientPresentationObserver(ClientPresentationObserver observer) {
        return level().isClientSide && observer != null && clientPresentationObserver == observer;
    }
    public boolean hasClientPresentationObserver() {
        return level().isClientSide && clientPresentationObserver != null;
    }
    /** Read-only cached client anchor/tracking metadata for an armed observation; does not query Sable. */
    public ClientRenderState clientRenderState() {
        if (!level().isClientSide) return null;
        return new ClientRenderState(clientPosition.frameId(), ClientPoint.copy(clientPosition.storagePoint()),
                clientPosition.frameProjectionPaused(), nativeTrackingFrameIdIfKnown());
    }
    public void clearClientPresentationObserver(ClientPresentationObserver observer) {
        if (level().isClientSide && clientPresentationObserver == observer) clientPresentationObserver = null;
    }

    private void observeClientPresentation(ClientPresentationSample sample) {
        ClientPresentationObserver observer = clientPresentationObserver;
        if (observer == null) return;
        try { observer.observe(sample); }
        catch (RuntimeException telemetryFailure) {
            if (clientPresentationObserver == observer) clientPresentationObserver = null;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[loose client observer disabled] entity={} capture callback failed; presentation retained", getUUID(), telemetryFailure);
        }
    }

    private java.util.UUID nativeTrackingFrameIdIfKnown() {
        if (this instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement) {
            var tracked = movement.sable$getTrackingSubLevel();
            return tracked == null ? null : tracked.getUniqueId();
        }
        return null;
    }

    /** Transient observation at the pre-carry native move boundary; never serialized or copied. */
    public record NativeBoxSnapshot(double x, double y, double z, double width, double height, double depth,
                                    double qx, double qy, double qz, double qw) { }
    public record NativePreCutoff(Object collisionInfo, java.util.UUID frameId,
                                  dev.ryanhcode.sable.companion.math.Pose3d logicalPose,
                                  NativeBoxSnapshot solvedBox, Vec3 inheritedMotion,
                                  java.util.List<com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope.NativeCollisionTrace> nativeCorrectionTrace,
                                  boolean nativeCorrectionTraceComplete) {
        public NativePreCutoff(Object collisionInfo, java.util.UUID frameId,
                dev.ryanhcode.sable.companion.math.Pose3d logicalPose, NativeBoxSnapshot solvedBox, Vec3 inheritedMotion) {
            this(collisionInfo, frameId, logicalPose, solvedBox, inheritedMotion, java.util.List.of(), false);
        }
        public NativePreCutoff {
            logicalPose = logicalPose == null ? null : new dev.ryanhcode.sable.companion.math.Pose3d().set(logicalPose);
            inheritedMotion = inheritedMotion == null ? null : new Vec3(inheritedMotion.x, inheritedMotion.y, inheritedMotion.z);
            nativeCorrectionTrace = java.util.List.copyOf(nativeCorrectionTrace);
        }
        @Override public dev.ryanhcode.sable.companion.math.Pose3d logicalPose() {
            return logicalPose == null ? null : new dev.ryanhcode.sable.companion.math.Pose3d().set(logicalPose);
        }
        public static boolean matches(NativePreCutoff observation, Object currentInfo) {
            return observation != null && observation.collisionInfo() == currentInfo;
        }
        public static NativePreCutoff withTraceIfMatches(NativePreCutoff observation, Object returnedInfo,
                java.util.List<com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope.NativeCollisionTrace> trace,
                boolean complete) {
            if (!matches(observation, returnedInfo)) return observation;
            return new NativePreCutoff(observation.collisionInfo(), observation.frameId(), observation.logicalPose(),
                    observation.solvedBox(), observation.inheritedMotion(), trace, complete);
        }
    }
    private NativePreCutoff nativePreCutoff;
    private boolean observeNativePreCutoff;

    public boolean observesNativePreCutoff() { return observeNativePreCutoff; }
    public boolean hasPendingNativeMoveCapture() { return observeNativePreCutoff && nativeMoveInFlight && !level().isClientSide; }
    public void setObserveNativePreCutoff(boolean observe) { observeNativePreCutoff = observe; }

    /** Called only by the compatibility observer at Sable's inheritedMotion cutoff. */
    public void observeNativePreCutoff(Object info, java.util.UUID frameId,
            dev.ryanhcode.sable.companion.math.Pose3dc pose, double x, double y, double z,
            double width, double height, double depth, org.joml.Quaterniondc orientation, Vec3 inherited) {
        if (!observeNativePreCutoff || !nativeMoveInFlight || level().isClientSide || info == null || pose == null || inherited == null) return;
        nativePreCutoff = new NativePreCutoff(info, frameId,
                new dev.ryanhcode.sable.companion.math.Pose3d().set(pose),
                new NativeBoxSnapshot(x, y, z, width, height, depth, orientation.x(), orientation.y(), orientation.z(), orientation.w()),
                new Vec3(inherited.x, inherited.y, inherited.z));
    }

    /** Binds the scoped solver observation only to the pre-cutoff record from this exact collide result. */
    public void observeNativeCorrectionTrace(Object info,
            java.util.List<com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope.NativeCollisionTrace> trace,
            boolean complete) {
        if (!hasPendingNativeMoveCapture() || nativePreCutoff == null || nativePreCutoff.collisionInfo() != info) return;
        nativePreCutoff = NativePreCutoff.withTraceIfMatches(nativePreCutoff, info, trace, complete);
    }

    public record NativeMoveCapture(long sequence, Vec3 requested, Vec3 before, Vec3 atReturn,
                                    Vec3 acceptedOwnDelta, Object previousCollisionInfo,
                                    Object currentCollisionInfo, boolean positionCaptured,
                                    boolean collisionInfoFresh,
                                    com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint beforeActorPoint,
                                    com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint afterActorPoint,
                                    Vec3 frameVelocityDeltaPerSecond, NativePreCutoff preCutoff,
                                    java.util.List<NativeFrameContact> selectedContacts,
                                    boolean selectedContactsOverflow) {
        public NativeMoveCapture {
            selectedContacts = java.util.List.copyOf(selectedContacts);
        }
    }

    public void setNativeFrameContactObserver(java.util.function.Consumer<NativeFrameContact> observer) {
        nativeFrameContactObserver = observer;
    }
    public void setNativeMoveCaptureObserver(java.util.function.Consumer<NativeMoveCapture> observer) {
        nativeMoveCaptureObserver = observer;
    }

    public void beginNativeMoveCapture(Vec3 requested) {
        nativeMoveSequence++;
        nativeMoveCapture = null;
        nativePreCutoff = null;
        nativeMoveBefore = position();
        nativeMoveRequested = requested;
        nativeMoveLevel = level() instanceof net.minecraft.server.level.ServerLevel server ? server : null;
        nativeMoveBeforeActorPoint = nativeMoveLevel == null ? null
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(nativeMoveLevel, this);
        nativeMovePreviousCollisionInfo = this instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                ? movement.sable$getCollisionInfo() : null;
        nativeMoveInFlight = true;
    }

    /** Captures only Sable's already-selected native SAT contact; never changes its collision response. */
    public void observeNativeFrameContact(Object collisionInfo, java.util.UUID frameId, BlockPos pos,
                                          double nx, double ny, double nz, boolean verticalCollision) {
        if (level().isClientSide || collisionInfo == null || frameId == null || pos == null
                || !Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz)) return;
        if (nativeFrameContactObserver == null) return;
        Vec3 normal = new Vec3(nx, ny, nz);
        NativeFrameContact observed = new NativeFrameContact(collisionInfo, frameId, pos.immutable(), normal,
                verticalCollision, nativeMoveInFlight, position(), getDeltaMovement(),
                ((ServerLevel) level()).getServer().getTickCount());
        if (nativeFrameContactObserver != null) nativeFrameContactObserver.accept(observed);
    }

    public void endNativeMoveCapture() {
        if (!nativeMoveInFlight) return;
        Vec3 returned = position();
        Object current = this instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                ? movement.sable$getCollisionInfo() : null;
        var selectedForMove = current instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess access
                ? access.moonstation14$selectedContacts().stream().map(entry -> new NativeFrameContact(current,
                    entry.frameId(), entry.pos(), entry.normal(), entry.verticalCollision(), entry.moveInFlight(),
                    entry.entityPosition(), entry.entityVelocity(), entry.serverTick())).toList() : java.util.List.<NativeFrameContact>of();
        nativeMoveCapture = new NativeMoveCapture(nativeMoveSequence, nativeMoveRequested, nativeMoveBefore,
                returned, returned.subtract(nativeMoveBefore), nativeMovePreviousCollisionInfo, current,
                true, current != null && current != nativeMovePreviousCollisionInfo,
                nativeMoveBeforeActorPoint, null, null, null, selectedForMove,
                current instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess access
                        && access.moonstation14$selectedContactsOverflowed() && !selectedForMove.isEmpty());
        nativeMoveInFlight = false;
        nativeMoveBefore = null;
        nativeMoveRequested = null;
        nativeMovePreviousCollisionInfo = null;
        nativeMoveBeforeActorPoint = null;
    }

    /** Runs at the real method RETURN, after Sable's nonliving movement TAIL has settled tracking. */
    public void finishNativeMoveCapture() {
        NativeMoveCapture capture = nativeMoveCapture;
        if (capture == null || capture.sequence() != nativeMoveSequence) return;
        var level = nativeMoveLevel;
        var afterPoint = level != null && level() == level
                ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, this) : null;
        Vec3 frameDelta = null;
        if (capture.collisionInfoFresh() && level != null && level() == level
                && capture.beforeActorPoint() != null && afterPoint != null
                && !java.util.Objects.equals(capture.beforeActorPoint().frameId(), afterPoint.frameId())) {
            frameDelta = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity
                    .pointDeltaPerSecond(level, capture.beforeActorPoint(), afterPoint);
        }
        nativeMoveCapture = new NativeMoveCapture(capture.sequence(), capture.requested(), capture.before(),
                capture.atReturn(), capture.acceptedOwnDelta(), capture.previousCollisionInfo(),
                capture.currentCollisionInfo(), capture.positionCaptured(), capture.collisionInfoFresh(),
                capture.beforeActorPoint(), afterPoint, frameDelta,
                observeNativePreCutoff && NativePreCutoff.matches(nativePreCutoff, capture.currentCollisionInfo())
                        ? nativePreCutoff : null, capture.selectedContacts(), capture.selectedContactsOverflow());
        if (nativeMoveCaptureObserver != null) nativeMoveCaptureObserver.accept(nativeMoveCapture);
        nativePreCutoff = null;
        nativeMoveLevel = null;
    }

    public NativeMoveCapture nativeMoveCapture() { return nativeMoveCapture; }

    /** Executes exactly one native move and returns only this call's accepted own displacement. */
    private NativeMoveCapture moveWithOwnCapture(Vec3 requested) {
        long previousSequence = nativeMoveSequence;
        move(MoverType.SELF, requested);
        NativeMoveCapture capture = nativeMoveCapture;
        return capture != null && capture.sequence() == previousSequence + 1 && capture.positionCaptured()
                ? capture : null;
    }

    public LooseItemEntity(EntityType<? extends LooseItemEntity> type, Level level) { super(type, level); }

    @Override public void writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        var point = level() instanceof ServerLevel server
                ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(server, this) : null;
        writeInitialSpawnAnchor(buffer, point);
    }

    @Override public void readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        InitialSpawnAnchor anchor = readInitialSpawnAnchor(buffer);
        switch (anchor.kind()) {
            case WORLD -> clientPosition.packetMetadata(LooseClientPositionInterpolation.PacketKind.WORLD, null, null);
            case UNKNOWN_STORAGE -> clientPosition.packetMetadata(LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, null, null);
            case FRAME -> {
                if (!clientPosition.initializeSpawnFrameAnchor(anchor.frameId(), anchor.storagePoint()))
                    clientPosition.packetMetadata(LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, null, null);
            }
        }
    }

    record InitialSpawnAnchor(LooseClientPositionInterpolation.PacketKind kind, java.util.UUID frameId, Vec3 storagePoint) { }

    /** The exact frame-anchor bytes shared by the NeoForge complex-spawn writer and reader. */
    static void writeInitialSpawnAnchor(net.minecraft.network.RegistryFriendlyByteBuf buffer,
            com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point) {
        LooseClientPositionInterpolation.PacketKind kind = point == null || !finite(point.worldPoint())
                || !finite(point.storagePoint()) ? LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE
                : point.frameId() == null ? LooseClientPositionInterpolation.PacketKind.WORLD
                : LooseClientPositionInterpolation.PacketKind.FRAME;
        buffer.writeEnum(kind);
        if (kind != LooseClientPositionInterpolation.PacketKind.FRAME) return;
        buffer.writeUUID(point.frameId());
        buffer.writeDouble(point.storagePoint().x);
        buffer.writeDouble(point.storagePoint().y);
        buffer.writeDouble(point.storagePoint().z);
    }

    static InitialSpawnAnchor readInitialSpawnAnchor(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        LooseClientPositionInterpolation.PacketKind kind = buffer.readEnum(LooseClientPositionInterpolation.PacketKind.class);
        if (kind != LooseClientPositionInterpolation.PacketKind.FRAME) return new InitialSpawnAnchor(kind, null, null);
        java.util.UUID id = buffer.readUUID();
        Vec3 point = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        if (id == null || !finite(point))
            return new InitialSpawnAnchor(LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, null, null);
        return new InitialSpawnAnchor(kind, id, point);
    }

    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        ItemStack stack = getItem();
        if (stack == null || stack.isEmpty()) return super.getDimensions(pose);
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, level().isClientSide);
            return net.minecraft.world.entity.EntityDimensions.scalable(profile.physicalWidth(), profile.physicalHeight());
        } catch (RuntimeException unavailableDuringInitialization) {
            return super.getDimensions(pose);
        }
    }

    @Override public void setItem(ItemStack stack) {
        super.setItem(stack);
        refreshDimensions();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROTATION, 0f);
        builder.define(SETTLED, false);
        builder.define(FRAME_ORIENTATION, encodeOrientation(LooseFrameOrientation.snapshot(null, new org.joml.Quaterniond())));
    }

    public static LooseItemEntity create(Level level, Vec3 position, ItemStack stack) {
        LooseItemEntity entity = new LooseItemEntity(ModEntities.LOOSE_ITEM.get(), level);
        entity.setPos(position);
        entity.setItem(stack.copy());
        entity.setUnlimitedLifetime();
        entity.setDeltaMovement(Vec3.ZERO);
        return entity;
    }

    /** Creates a replacement item using the actor's currently admitted native-frame world basis. */
    public static LooseItemEntity createForActor(net.minecraft.world.entity.LivingEntity actor,
                                                  Vec3 position, ItemStack stack) {
        if (actor == null) throw new IllegalArgumentException("actor is required");
        LooseItemEntity entity = create(actor.level(), position, stack);
        if (!(actor.level() instanceof ServerLevel server)) return entity;
        // Contained actors use their containment root for placement; do not infer an unrelated
        // native frame basis from the contained body's tracking state.
        if (com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(server)
                .ownerOf(actor).isPresent()) return entity;
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(server, actor);
        if (point == null || point.frameId() == null) return entity;
        var orientation = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .frameOrientation(server, point.frameId(), point.storagePoint(), false, 1f);
        if (orientation == null) return entity;
        var snapshot = LooseFrameOrientation.snapshot(null, orientation);
        entity.setOrientationSnapshot(snapshot);
        entity.lastSafeWorldOrientation = new org.joml.Quaterniond(orientation);
        entity.lastSafeFrameStoragePoint = null;
        return entity;
    }

    /**
     * Commits the canonical orientation's exact initial native-frame parent before spawn data and
     * tracked entity data are captured. This is only valid for a fresh, unadded server entity.
     */
    public boolean prepareInitialFrameState(ServerLevel server) {
        if (server == null || level() != server || server.isClientSide || !server.getServer().isSameThread()
                || isAddedToLevel() || isRemoved()) return false;
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(server, this);
        if (point == null) return false;
        updateFrameOrientation(server, point);
        LooseFrameOrientation.Snapshot snapshot = orientationSnapshot();
        if (snapshot == null || !java.util.Objects.equals(snapshot.frameId(), point.frameId())) return false;
        org.joml.Quaterniond parent = point.frameId() == null ? new org.joml.Quaterniond()
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        server, point.frameId(), point.storagePoint(), false, 1f);
        if (parent == null) return false;
        org.joml.Quaterniond resolved = LooseFrameOrientation.resolveAuthoritative(
                snapshot, point.frameId(), parent, null, lastSafeWorldOrientation);
        if (resolved == null || !finiteQuaternion(resolved)) return false;
        lastSafeWorldOrientation = new org.joml.Quaterniond(resolved);
        lastSafeFrameStoragePoint = point.frameId() == null ? null : point.storagePoint();
        return true;
    }

    /**
     * Keep copies on the registered loose-item type. ItemPickupParticle copies its
     * source before rendering it, and the entity type selects the renderer.
     */
    @Override public LooseItemEntity copy() {
        LooseItemEntity copy = new LooseItemEntity(ModEntities.LOOSE_ITEM.get(), level());
        copy.copyPosition(this);
        copy.setItem(getItem().copy());
        copy.setDeltaMovement(getDeltaMovement());
        copy.setRotationAngle(getRotationAngle());
        if (orientationSnapshot() != null) copy.setOrientationSnapshot(orientationSnapshot());
        copy.lastSafeFrameStoragePoint = lastSafeFrameStoragePoint;
        copy.setSettled(settled());
        copy.angularVelocity = angularVelocity;
        copy.previousRotationAngle = previousRotationAngle;
        copy.currentRotationAngle = currentRotationAngle;
        copy.nextSupportCheck = nextSupportCheck;
        copy.motionBlockedByUnloaded = motionBlockedByUnloaded;
        copy.creativePickupDelay = creativePickupDelay;
        return copy;
    }

    public LooseObjectProfile profile() {
        return LooseObjectProfile.resolveForStack(getItem(), level().isClientSide);
    }

    public void launch(Vec3 velocity, float angularVelocity) {
        if (velocity == null || !finite(velocity)) return;
        setDeltaMovement(clamp(velocity, 2.5));
        this.angularVelocity = Float.isFinite(angularVelocity) ? Math.max(-1.0f, Math.min(1.0f, angularVelocity)) : 0;
        setSettled(false);
    }

    public void applyImpulse(Vec3 impulse) {
        if (impulse == null || !finite(impulse)) return;
        double mass = profile().mass();
        if (!Double.isFinite(mass) || mass <= 0) return;
        launch(clamp(getDeltaMovement().add(impulse.scale(1.0 / Math.max(.01, mass))), 2.5), angularVelocity);
    }

    /** Applies the shared normalized-mass tether delta in native blocks/tick and wakes a settled item. */
    public void applyPullVelocityChange(Vec3 deltaPerTick) {
        if (level().isClientSide || deltaPerTick == null || !finite(deltaPerTick)) return;
        Vec3 next = getDeltaMovement().add(deltaPerTick);
        if (!finite(next)) return;
        setDeltaMovement(next);
        setSettled(false);
    }

    /** Collision-resolved rope correction consumed immediately rather than queued as a weak impulse. */
    public void moveByPull(Vec3 correction) {
        if (level().isClientSide || correction == null || !finite(correction)) return;
        var sweep = getBoundingBox().expandTowards(correction);
        if (!chunksLoaded(sweep)) return;
        NativeMoveCapture capture = moveWithOwnCapture(correction);
        if (capture == null) {
            setSettled(false);
            return;
        }
        Vec3 actual = capture.acceptedOwnDelta();
        if (actual.horizontalDistanceSqr() > 1.0e-10) {
            Vec3 velocity = getDeltaMovement();
            setDeltaMovement(0, velocity.y, 0);
            setSettled(false);
        }
        commitFrameVelocityChange(capture);
    }

    public boolean settled() { return entityData.get(SETTLED); }
    public float getRotationAngle() { return entityData.get(ROTATION); }
    public float getRotationAngle(float partialTick) {
        float delta = Mth.wrapDegrees((float) Math.toDegrees(currentRotationAngle - previousRotationAngle));
        return previousRotationAngle + (float) Math.toRadians(delta) * Mth.clamp(partialTick, 0, 1);
    }
    public void setRotationAngle(float angle) { if (Float.isFinite(angle)) entityData.set(ROTATION, angle); }
    private void setSettled(boolean value) { entityData.set(SETTLED, value); }

    public void setCreativePickupDelay(int ticks) { creativePickupDelay = Math.max(2, Math.min(40, ticks)); }

    @Override public void playerTouch(net.minecraft.world.entity.player.Player player) {
        if (creativePickupDelay > 0 || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !serverPlayer.isCreative()
                || com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(serverPlayer).isPresent()) return;
        super.playerTouch(player);
    }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (!ROTATION.equals(accessor) && !SETTLED.equals(accessor) && !FRAME_ORIENTATION.equals(accessor)) refreshDimensions();
        if (!level().isClientSide && ROTATION.equals(accessor)) {
            previousRotationAngle = currentRotationAngle;
            currentRotationAngle = getRotationAngle();
        }
    }

    @Override public void tick() {
        if (isRemoved() || getItem().isEmpty()) return;
        if (!level().isClientSide && !frameAnchorState.initialize((ServerLevel) level(), this)) return;
        // Preserve base entity interpolation bookkeeping without running ItemEntity's pickup, merge, or expiry loop.
        ClientPresentationObserver clientObserver = level().isClientSide ? clientPresentationObserver : null;
        Vec3 previousClientWorld = level().isClientSide ? position() : null;
        double oldXBeforeTick = clientObserver == null ? 0 : xOld;
        double oldYBeforeTick = clientObserver == null ? 0 : yOld;
        double oldZBeforeTick = clientObserver == null ? 0 : zOld;
        double xoBeforeTick = clientObserver == null ? 0 : xo;
        double yoBeforeTick = clientObserver == null ? 0 : yo;
        double zoBeforeTick = clientObserver == null ? 0 : zo;
        xo = getX(); yo = getY(); zo = getZ(); xOld = getX(); yOld = getY(); zOld = getZ();
        tickCount++;
        if (!level().isClientSide && creativePickupDelay > 0) creativePickupDelay--;
        if (level().isClientSide) {
            Vec3 previousWorld = previousClientWorld;
            Vec3 projectedFrameWorld = null;
            clientPosition.beginRenderTick(previousWorld, (id, point) -> {
                var anchor = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .pointAtWorldPosition(level(), id, point, true, 0f);
                return anchor == null ? null : anchor.storagePoint();
            });
            advanceClientPosition();
            if (clientPosition.frameId() != null && clientPosition.storagePoint() != null) {
                Vec3 projected = clientPosition.projectFrame((id, point) ->
                        com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                .projectActorPoint(level(), id, point));
                // Unknown/unloaded frames fail closed: retain the last safe world presentation.
                if (projected != null) {
                    projectedFrameWorld = projected;
                    if (clientPosition.shouldSnapFrameProjection(getX(), getY(), getZ(), projected)) {
                        clientPosition.snapRenderAnchorToCurrent();
                        xo = xOld = projected.x;
                        yo = yOld = projected.y;
                        zo = zOld = projected.z;
                    }
                    setPos(projected);
                }
            }
            refreshClientProfileDimensions();
            previousRotationAngle = currentRotationAngle;
            currentRotationAngle = getRotationAngle();
            if (clientObserver != null) {
                ItemStack observedStack = getItem();
                observeClientPresentation(new ClientPresentationSample(getUUID(), getId(),
                    ClientObservationPhase.CLIENT_TICK, tickCount, level().getGameTime(), System.nanoTime(), false,
                    null, null, null, ClientPoint.copy(previousClientWorld),
                    new ClientPoint(oldXBeforeTick, oldYBeforeTick, oldZBeforeTick),
                    new ClientPoint(xoBeforeTick, yoBeforeTick, zoBeforeTick), ClientPoint.copy(projectedFrameWorld),
                    ClientPoint.copy(position()), new ClientPoint(xOld, yOld, zOld), new ClientPoint(xo, yo, zo),
                    clientPosition.frameId(), ClientPoint.copy(clientPosition.storagePoint()),
                    ClientPoint.copy(clientPosition.renderAnchor(0f)), ClientPoint.copy(clientPosition.renderAnchor(1f)),
                    clientPosition.frameProjectionPaused(), nativeTrackingFrameIdIfKnown(), settled(), onGround(),
                    observedStack.getCount(), observedStack.isEmpty(), ClientPoint.copy(getDeltaMovement())));
            }
            return;
        }
        evaluateGrenadeFuse();
        if (isRemoved()) return;
        updateFrameOrientation((ServerLevel) level(), com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .currentActorPoint((ServerLevel) level(), this));
        Vec3 tether = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                .deltaVelocityPerSecond(this).scale(.05);
        if (tether.lengthSqr() > 0) applyPullVelocityChange(tether);
        if (settled()) {
            var ownerPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(
                    (ServerLevel) level(), this);
            if (ownerPoint == null) {
                setSettled(false);
                return;
            }
            if (ownerPoint.frameId() != null) {
                maintainTrackedFrameSupport((ServerLevel) level(), ownerPoint.frameId());
                ownerPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(
                        (ServerLevel) level(), this);
                updateFrameOrientation((ServerLevel) level(), ownerPoint);
                return;
            }
            // A world-floor-settled item normally avoids native movement entirely. Let the
            // native swept collision path run only when Sable's loaded-frame broadphase says
            // a frame currently overlaps this item's bounds; this admits real hull contact
            // without making every settled item scan/move each tick.
            boolean frameOverlap = hasLoadedFrameOverlap((ServerLevel) level());
            if (tickCount < nextSupportCheck && !frameOverlap) return;
            if (frameOverlap) {
                setSettled(false);
            } else {
            nextSupportCheck = tickCount + 10;
            if (hasFloorSupport()) return;
            setSettled(false);
            }
        }
        Vec3 velocity = getDeltaMovement();
        if (!finite(velocity)) velocity = Vec3.ZERO;
        motionBlockedByUnloaded = false;
        Vec3 travel = velocity.add(0, -0.04, 0);
        // Avoid collision scans which could touch unloaded chunks; bound the swept search to a small neighborhood.
        // Entity.move's block collision query is the swept box (plus numerical tolerance),
        // not a one-block halo. A halo would freeze valid motion near unloaded neighbors.
        // Account for vanilla step-up collision probing when enabled on this entity.
        double stepHeight = maxUpStep();
        var sweep = getBoundingBox().expandTowards(travel);
        if (stepHeight > 0) sweep = sweep.expandTowards(0, stepHeight, 0);
        if (!chunksLoaded(sweep)) {
            motionBlockedByUnloaded = true;
            // Retain velocity (including gravity accumulated on earlier ticks) and retry soon.
            nextSupportCheck = tickCount + 5;
            return;
        }
        NativeMoveCapture capture = moveWithOwnCapture(travel);
        if (capture == null) {
            setSettled(false);
            return;
        }
        Vec3 actual = capture.acceptedOwnDelta();
        boolean hitX = Math.abs(actual.x - travel.x) > 1.0e-6;
        boolean hitY = Math.abs(actual.y - travel.y) > 1.0e-6;
        boolean hitZ = Math.abs(actual.z - travel.z) > 1.0e-6;
        var ownerPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(
                (ServerLevel) level(), this);
        updateFrameOrientation((ServerLevel) level(), ownerPoint);
        boolean frameSupport = currentFrameSupport(capture, ownerPoint);
        boolean supported = onGround() || frameSupport;
        double approachSpeed = travel.length();
        double restitution = profile().restitution();
        double vx = hitX ? -travel.x * restitution : travel.x;
        double vy = hitY ? -travel.y * restitution : travel.y;
        double vz = hitZ ? -travel.z * restitution : travel.z;
        if (supported && Math.abs(vy) < .12) vy = 0;
        double friction = Math.max(0, Math.min(.25, profile().friction()));
        double horizontalDamp = supported || hitX || hitZ ? .86 : .995;
        double contactFriction = supported || hitX || hitZ ? 1 - friction : 1;
        vx *= contactFriction * horizontalDamp;
        vz *= contactFriction * horizontalDamp;
        vy *= .995;
        Vec3 next = commitFrameVelocity(new Vec3(vx, vy, vz), capture.frameVelocityDeltaPerSecond());
        next = applyNativeNormalResponse((ServerLevel) level(), capture, travel, next, restitution);
        boolean frameChanged = nativeFrameChanged(capture);
        float angle = getRotationAngle() + angularVelocity;
        setRotationAngle(Float.isFinite(angle) ? angle : 0);
        boolean currentSupport = frameSupport || ownerPoint != null && ownerPoint.frameId() == null
                && onGround() && hasFloorSupport();
        if (shouldSettle(next, currentSupport, frameChanged, capture.sequence(), lastNormalResponseSequence)) {
            next = Vec3.ZERO;
            angularVelocity = 0;
            setSettled(true);
            nextSupportCheck = tickCount + 10;
        }
        setDeltaMovement(next);
        boolean clippedImpact = LooseImpactContact.clippedOwnTravel(capture.requested().x, capture.requested().y, capture.requested().z,
                actual.x, actual.y, actual.z, MOVEMENT_EPSILON);
        if (clippedImpact) {
            boolean breakOnImpact = profile().breakOnImpact();
            double breakThreshold = breakOnImpact ? profile().breakThreshold() : Double.NaN;
            boolean eligible = LooseMotionDiagnostics.impactEligible(approachSpeed, breakThreshold, breakOnImpact);
            if (LooseMotionDiagnostics.persistenceObserves(this)) {
                var collision = capture.currentCollisionInfo();
                LooseMotionDiagnostics.observeImpact(this, "clippedOwnTravel=true breakOnImpact=" + breakOnImpact
                        + " threshold=" + (breakOnImpact ? breakThreshold : "NOT_EVALUATED")
                        + " approach=" + approachSpeed + " eligible=" + eligible + " requested=" + capture.requested()
                        + " acceptedOwnDelta=" + actual + " hitAxes=" + hitX + "," + hitY + "," + hitZ
                        + " collisionInfoFresh=" + capture.collisionInfoFresh()
                        + " collisionInfoPresent=" + (collision != null));
            }
            if (eligible) {
                BlockPos landing = findSpillSurface((ServerLevel) level(), capture);
                if (!firstJugImpactDiagnosticLogged && LooseMotionDiagnostics.persistenceObserves(this)) {
                    firstJugImpactDiagnosticLogged = true;
                    var contactRows = capture.selectedContacts().stream().limit(8)
                            .map(c -> c.frameId() + "/" + c.storagePos() + "/" + c.normal()).toList();
                    System.out.println("JUG_FIRST_BREAK seq=" + capture.sequence() + " request=" + capture.requested()
                            + " accepted=" + actual + " speed=" + approachSpeed + " frameContacts=" + contactRows
                            + " worldCandidate=" + worldImpactContact((ServerLevel) level(), capture)
                            + " landing=" + landing);
                }
                breakJug(capture, landing);
            }
        }
    }

    static boolean shouldSettle(Vec3 velocity, boolean supported, boolean frameChanged,
                                long captureSequence, long responseSequence) {
        return velocity.lengthSqr() < .0004 && supported && !frameChanged && responseSequence != captureSequence;
    }

    /** Replaces only the selected side plane's normal component after ordinary/world response. */
    private Vec3 applyNativeNormalResponse(ServerLevel server, NativeMoveCapture capture, Vec3 travel,
                                           Vec3 baselineAfterFrameChange, double restitution) {
        if (noNativeNormalEvidence(capture, lastNormalResponseSequence)) return baselineAfterFrameChange;
        var before = capture.beforeActorPoint();
        var after = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(server, this);
        if (before == null || after == null) return baselineAfterFrameChange;
        Vec3 beforeCarrier = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity.pointVelocityAt(server, before);
        Vec3 afterCarrier = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity.pointVelocityAt(server, after);
        if (beforeCarrier == null || afterCarrier == null) return baselineAfterFrameChange;
        Vec3 incoming = travel.scale(20).add(beforeCarrier);
        Vec3 baselineAbsolute = baselineAfterFrameChange.scale(20).add(afterCarrier);
        java.util.ArrayList<NativeFrameContact> appliedPlanes = new java.util.ArrayList<>();
        for (NativeFrameContact contact : capture.selectedContacts()) {
            if (contact.collisionInfo() != capture.currentCollisionInfo()) continue;
            Vec3 n = contact.normal();
            if (n == null || contact.verticalCollision() || Math.abs(n.y) > .5) continue;
            boolean clipped = Math.abs(n.x) > .5 && Math.abs(capture.acceptedOwnDelta().x - capture.requested().x) > MOVEMENT_EPSILON
                    || Math.abs(n.z) > .5 && Math.abs(capture.acceptedOwnDelta().z - capture.requested().z) > MOVEMENT_EPSILON;
            if (!clipped) continue;
            var frame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server).getSubLevel(contact.frameId());
            if (!(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel sub) || sub.isRemoved()
                    || sub.getLevel() != server || dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server).getSubLevel(contact.frameId()) != sub) continue;
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.pointAtWorldPosition(server,
                    contact.frameId(), position());
            if (point == null) continue;
            Vec3 surfaceVelocity = com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity.pointVelocityAt(server, point);
            if (surfaceVelocity == null) continue;
            boolean duplicate = appliedPlanes.stream().anyMatch(previous -> previous.frameId().equals(contact.frameId())
                    && previous.normal().dot(n) > .999);
            if (duplicate) continue;
            appliedPlanes.add(contact);
            Vec3 changed = com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactPolicy
                    .replaceNormal(incoming, baselineAbsolute, surfaceVelocity, n, restitution);
            if (!changed.equals(baselineAbsolute)) lastNormalResponseSequence = capture.sequence();
            if (nativeMoveCaptureObserver != null) System.out.println("LOOSE_NATIVE_NORMAL_RESPONSE seq=" + capture.sequence()
                    + " frame=" + contact.frameId() + " incomingMps=" + incoming + " surfaceMps=" + surfaceVelocity
                    + " normal=" + n + " baselineMps=" + baselineAbsolute + " outgoingMps=" + changed
                    + " afterCarrierMps=" + afterCarrier + " responseApplied=" + (lastNormalResponseSequence == capture.sequence()));
            incoming = incoming.add(n.scale(changed.subtract(incoming).dot(n)));
            baselineAbsolute = changed;
        }
        return baselineAbsolute.subtract(afterCarrier).scale(.05);
    }

    static boolean noNativeNormalEvidence(NativeMoveCapture capture, long alreadyAppliedSequence) {
        return capture == null || !capture.positionCaptured() || !capture.collisionInfoFresh()
                || capture.currentCollisionInfo() == null || capture.selectedContactsOverflow()
                || capture.selectedContacts().isEmpty() || alreadyAppliedSequence == capture.sequence();
    }

    private boolean hasLoadedFrameOverlap(ServerLevel server) {
        var bounds = getBoundingBox();
        var candidates = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                new Vec3(bounds.minX, bounds.minY, bounds.minZ), new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ));
        for (var frame : dev.ryanhcode.sable.Sable.HELPER.getAllIntersecting(server, candidates)) {
            if (frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel sub
                    && !sub.isRemoved() && sub.getLevel() == server) return true;
        }
        return false;
    }

    static CompoundTag encodeOrientation(LooseFrameOrientation.Snapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        if (snapshot.frameId() != null) tag.putUUID("Frame", snapshot.frameId());
        tag.putDouble("X", snapshot.x()); tag.putDouble("Y", snapshot.y());
        tag.putDouble("Z", snapshot.z()); tag.putDouble("W", snapshot.w());
        return tag;
    }

    static LooseFrameOrientation.Snapshot decodeOrientation(CompoundTag tag) {
        if (tag == null || !tag.contains("X", CompoundTag.TAG_DOUBLE)
                || !tag.contains("Y", CompoundTag.TAG_DOUBLE) || !tag.contains("Z", CompoundTag.TAG_DOUBLE)
                || !tag.contains("W", CompoundTag.TAG_DOUBLE)) return null;
        java.util.UUID frame = tag != null && tag.hasUUID("Frame") ? tag.getUUID("Frame") : null;
        double x = tag.getDouble("X"), y = tag.getDouble("Y"), z = tag.getDouble("Z"), w = tag.getDouble("W");
        double norm = x*x + y*y + z*z + w*w;
        if (!Double.isFinite(norm) || norm < 1.0e-20) return null;
        return new LooseFrameOrientation.Snapshot(frame, x, y, z, w);
    }

    public LooseFrameOrientation.Snapshot orientationSnapshot() {
        return decodeOrientation(entityData.get(FRAME_ORIENTATION));
    }

    /** Authoritative world basis for query adapters; unknown frame pose holds only an existing safe pose. */
    public org.joml.Quaterniond authoritativeQueryOrientation(ServerLevel server) {
        LooseFrameOrientation.Snapshot snapshot = orientationSnapshot();
        if (snapshot == null) return lastSafeWorldOrientation == null ? null : new org.joml.Quaterniond(lastSafeWorldOrientation);
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(server, this);
        if (point == null) return lastSafeWorldOrientation == null ? null : new org.joml.Quaterniond(lastSafeWorldOrientation);
        java.util.UUID currentFrame = point.frameId();
        org.joml.Quaterniond parent = currentFrame == null ? new org.joml.Quaterniond()
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        server, currentFrame, point.storagePoint(), false, 1f);
        if (parent == null) return lastSafeWorldOrientation == null ? null : new org.joml.Quaterniond(lastSafeWorldOrientation);
        org.joml.Quaterniond oldParent = java.util.Objects.equals(snapshot.frameId(), currentFrame)
                || snapshot.frameId() == null || lastSafeFrameStoragePoint == null ? null
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        server, snapshot.frameId(), lastSafeFrameStoragePoint, false, 1f);
        org.joml.Quaterniond resolved = LooseFrameOrientation.resolveAuthoritative(
                snapshot, currentFrame, parent, oldParent, lastSafeWorldOrientation);
        return resolved == null ? (lastSafeWorldOrientation == null ? null : new org.joml.Quaterniond(lastSafeWorldOrientation)) : resolved;
    }

    private void setOrientationSnapshot(LooseFrameOrientation.Snapshot snapshot) {
        entityData.set(FRAME_ORIENTATION, encodeOrientation(snapshot));
    }

    /** Tracks only the same native owner as position and carries a safe world basis across its changes. */
    private void updateFrameOrientation(ServerLevel server,
            com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point) {
        if (point == null) return;
        LooseFrameOrientation.Snapshot current = orientationSnapshot();
        if (current == null) return;
        java.util.UUID frameId = point.frameId();
        org.joml.Quaterniond parent = frameId == null ? new org.joml.Quaterniond()
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        server, frameId, point.storagePoint(), false, 1f);
        if (parent == null) return;
        var oldParent = java.util.Objects.equals(current.frameId(), frameId)
                || current.frameId() == null || lastSafeFrameStoragePoint == null ? null
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        server, current.frameId(), lastSafeFrameStoragePoint, false, 1f);
        org.joml.Quaterniond worldBasis = LooseFrameOrientation.resolveAuthoritative(
                current, frameId, parent, oldParent, lastSafeWorldOrientation);
        if (worldBasis == null) return;
        if (!java.util.Objects.equals(current.frameId(), frameId)) {
            setOrientationSnapshot(LooseFrameOrientation.snapshot(frameId,
                    LooseFrameOrientation.localBasis(parent, worldBasis)));
        }
        lastSafeWorldOrientation = new org.joml.Quaterniond(worldBasis);
        lastSafeFrameStoragePoint = frameId == null ? null : point.storagePoint();
    }

    /** Current render basis only when the authoritative snapshot and committed position anchor agree. */
    public org.joml.Quaterniond renderOrientation(float partialTick) {
        LooseFrameOrientation.Snapshot snapshot = orientationSnapshot();
        if (snapshot == null) return lastSafeWorldOrientation == null ? null : new org.joml.Quaterniond(lastSafeWorldOrientation);
        boolean paused = clientPosition.frameProjectionPaused();
        java.util.UUID committedFrame = paused ? null : clientPosition.frameId();
        var parent = !paused && committedFrame != null
                ? com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        level(), committedFrame, clientPosition.storagePoint(), true, partialTick) : null;
        var resolved = LooseFrameOrientation.resolveRender(snapshot, committedFrame, paused, parent,
                lastSafeWorldOrientation);
        if (resolved != null) lastSafeWorldOrientation = new org.joml.Quaterniond(resolved);
        return resolved;
    }

    /** Presentation point projected from the committed storage anchor using the orientation's exact render pose. */
    public Vec3 renderPosition(float partialTick) {
        Vec3 ordinary = ordinaryInterpolatedWorldPosition(partialTick);
        if (!level().isClientSide) return ordinary;
        Vec3 anchor = clientPosition.renderAnchor(partialTick);
        if (anchor == null) return ordinary;
        Vec3 projected = projectCommittedRenderPoint(anchor, partialTick);
        return projected == null ? ordinary : projected;
    }

    /** Projects any local presentation point through the same committed render pose as the pivot. */
    private Vec3 projectCommittedRenderPoint(Vec3 storagePoint, float partialTick) {
        java.util.UUID frameId = committedRenderFrameId(partialTick);
        if (frameId == null || !finitePoint(storagePoint)) return null;
        return com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.projectActorPoint(
                level(), frameId, storagePoint, true, partialTick);
    }

    /** The ordinary renderer pivot before this entity renderer's presentation offset. */
    public Vec3 ordinaryInterpolatedWorldPosition(float partialTick) {
        float partial=Mth.clamp(partialTick,0,1);
        return new Vec3(Mth.lerp(partial,xOld,getX()),Mth.lerp(partial,yOld,getY()),Mth.lerp(partial,zOld,getZ()));
    }

    /** True only for the client custom-frame path whose committed render anchor can add a mesh offset. */
    public boolean usesCustomFramePresentation(float partialTick) {
        return level().isClientSide && Float.isFinite(partialTick) && !isNativeFrameTracked()
                && committedRenderFrameId(partialTick)!=null;
    }

    /** Exact renderer offset, shared by mesh placement and inherited light-probe placement. */
    public Vec3 presentationOffset(float partialTick) {
        if (!usesCustomFramePresentation(partialTick)) return Vec3.ZERO;
        Vec3 rendered=renderPosition(partialTick);
        Vec3 ordinary=ordinaryInterpolatedWorldPosition(partialTick);
        Vec3 offset=finitePresentationOffset(rendered,ordinary);
        return offset==null?Vec3.ZERO:offset;
    }

    /** Finite guarded point delta used by the live renderer/light-probe path. */
    public static Vec3 finitePresentationOffset(Vec3 rendered,Vec3 ordinary) {
        if (!finitePoint(rendered)||!finitePoint(ordinary)) return null;
        Vec3 offset=rendered.subtract(ordinary);
        return finitePoint(offset)?offset:null;
    }

    @Override public Vec3 getLightProbePosition(float partialTick) {
        Vec3 inherited=super.getLightProbePosition(partialTick);
        if (!usesCustomFramePresentation(partialTick)) return inherited;
        Vec3 ordinary=ordinaryInterpolatedWorldPosition(partialTick);
        Vec3 probeOffset=finitePresentationOffset(inherited,ordinary);
        Vec3 anchor=clientPosition.renderAnchor(partialTick);
        if (probeOffset==null||anchor==null) return inherited;
        Vec3 projected=projectCommittedRenderPoint(anchor.add(probeOffset),partialTick);
        return finitePoint(projected)?projected:inherited;
    }

    private static boolean finitePoint(Vec3 point) {
        return point!=null&&Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z);
    }

    /** Read-only exact frame identity committed for this interpolated render anchor; never uses SDK containment. */
    public java.util.UUID committedRenderFrameId(float partialTick) {
        if (!level().isClientSide || clientPosition.frameProjectionPaused()) return null;
        java.util.UUID frameId = clientPosition.frameId();
        return frameId != null && clientPosition.renderAnchor(partialTick) != null ? frameId : null;
    }

    /** True when Sable's outer renderer applies the tracked-frame pose to the entity mesh. */
    public boolean isNativeFrameTracked() {
        return this instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                && movement.sable$getTrackingSubLevel() != null;
    }

    /** Called at the raw SDK packet boundary, before Sable converts plot coordinates to world. */
    public void stageClientFramePacket(java.util.UUID frameId, Vec3 packetPoint) {
        if (!level().isClientSide) return;
        if (frameId == null) {
            clientPosition.packetMetadata(LooseClientPositionInterpolation.PacketKind.WORLD, null, null);
            return;
        }
        if (packetPoint == null || !Double.isFinite(packetPoint.x) || !Double.isFinite(packetPoint.y)
                || !Double.isFinite(packetPoint.z)) return;
        clientPosition.packetMetadata(LooseClientPositionInterpolation.PacketKind.FRAME, frameId, packetPoint);
    }

    /** Callback inserted at the pinned SDK lerp helper's HEAD with its raw arguments. */
    public static void recordClientPacketFrame(net.minecraft.world.entity.Entity entity,
            double x, double y, double z, boolean actuallyInSubLevel) {
        if (!(entity instanceof LooseItemEntity loose) || !entity.level().isClientSide
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        Vec3 rawPoint = new Vec3(x, y, z);
        var frame = dev.ryanhcode.sable.Sable.HELPER.getContaining(entity.level(), rawPoint);
        if (frame != null && !actuallyInSubLevel) {
            loose.stageClientFramePacket(frame.getUniqueId(), rawPoint);
            loose.recordClientPacketObservation(x, y, z, actuallyInSubLevel,
                    ClientPacketClassification.FRAME_STORAGE, frame.getUniqueId());
            return;
        } else if (frame == null && dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(entity.level())
                .inBounds(BlockPos.containing(rawPoint))) {
            loose.dropUnknownClientFramePacket();
            loose.recordClientPacketObservation(x, y, z, actuallyInSubLevel,
                    ClientPacketClassification.UNKNOWN_SUBLEVEL_STORAGE, null);
            return;
        } else {
            loose.stageClientFramePacket(null, null);
            loose.recordClientPacketObservation(x, y, z, actuallyInSubLevel,
                    ClientPacketClassification.WORLD_OR_ALREADY_SUBLEVEL,
                    frame == null ? null : frame.getUniqueId());
        }
    }

    private void recordClientPacketObservation(double x, double y, double z, boolean actuallyInSubLevel,
            ClientPacketClassification classification, java.util.UUID helperFrameId) {
        if (clientPresentationObserver == null) return;
        ItemStack observedStack = getItem();
        observeClientPresentation(new ClientPresentationSample(getUUID(), getId(), ClientObservationPhase.RAW_FRAME_PACKET,
                tickCount, level().getGameTime(), System.nanoTime(), actuallyInSubLevel, classification, helperFrameId,
                new ClientPoint(x, y, z), null, null, null, null, null, null, null,
                clientPosition.frameId(), ClientPoint.copy(clientPosition.storagePoint()),
                ClientPoint.copy(clientPosition.renderAnchor(0f)), ClientPoint.copy(clientPosition.renderAnchor(1f)),
                clientPosition.frameProjectionPaused(), nativeTrackingFrameIdIfKnown(), settled(), onGround(),
                observedStack.getCount(), observedStack.isEmpty(), ClientPoint.copy(getDeltaMovement())));
    }

    public void dropUnknownClientFramePacket() {
        if (level().isClientSide) clientPosition.packetMetadata(
                LooseClientPositionInterpolation.PacketKind.UNKNOWN_STORAGE, null, null);
    }

    /** Maintains only current support on an already tracked native frame; Sable's move TAIL carries once. */
    private void maintainTrackedFrameSupport(ServerLevel server, java.util.UUID frameId) {
        Vec3 probe = new Vec3(0, -FLOOR_SUPPORT_DEPTH, 0);
        var sweep = getBoundingBox().expandTowards(probe);
        double stepHeight = maxUpStep();
        if (stepHeight > 0) sweep = sweep.expandTowards(0, stepHeight, 0);
        if (!chunksLoaded(sweep)) {
            motionBlockedByUnloaded = true;
            setSettled(false);
            return;
        }
        motionBlockedByUnloaded = false;
        NativeMoveCapture capture = moveWithOwnCapture(probe);
        var after = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(server, this);
        commitFrameVelocityChange(capture);
        if (!currentFrameSupport(capture, after) || !frameId.equals(after.frameId())) setSettled(false);
    }

    /** Fresh downward clipping plus the exact live post-move native frame proves frame-floor contact. */
    private static boolean currentFrameSupport(NativeMoveCapture capture,
            com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point) {
        return capture != null && capture.positionCaptured() && capture.collisionInfoFresh()
                && point != null && point.frameId() != null
                && capture.requested().y < -MOVEMENT_EPSILON
                && capture.acceptedOwnDelta().y > capture.requested().y + MOVEMENT_EPSILON;
    }

    /** Applies only an exact fresh native frame transition; unknown points never mean map-zero velocity. */
    private void commitFrameVelocityChange(NativeMoveCapture capture) {
        if (!nativeFrameChanged(capture)) return;
        Vec3 next = commitFrameVelocity(getDeltaMovement(), capture.frameVelocityDeltaPerSecond());
        if (finite(next)) setDeltaMovement(next);
        setSettled(false);
    }

    private static Vec3 commitFrameVelocity(Vec3 ownVelocity, Vec3 deltaPerSecond) {
        if (deltaPerSecond == null || !finite(deltaPerSecond)) return ownVelocity;
        Vec3 corrected = ownVelocity.add(deltaPerSecond.scale(.05));
        return finite(corrected) ? corrected : ownVelocity;
    }

    private static boolean nativeFrameChanged(NativeMoveCapture capture) {
        return capture != null && capture.collisionInfoFresh() && capture.beforeActorPoint() != null
                && capture.afterActorPoint() != null
                && !java.util.Objects.equals(capture.beforeActorPoint().frameId(), capture.afterActorPoint().frameId());
    }

    /** Stage vanilla entity-position packets and move only the client presentation toward them. */
    @Override public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!level().isClientSide) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        // Entity.lerpTo teleports immediately in 1.21.1. Keep its rotation handling but
        // stage position locally so tick() can preserve the previous render position.
        setRot(yRot, xRot);
        // Sable has already supplied raw packet metadata; its projected world lerp is
        // not a second position owner for frame-anchored cargo.
        if (clientPosition.frameId() != null) return;
        if (clientPosition.stage(getX(), getY(), getZ(), x, y, z)) {
            // Keep both vanilla renderer histories continuous at correction/teleport snaps.
            setPos(x, y, z);
            xo = xOld = x; yo = yOld = y; zo = zOld = z;
        }
    }

    @Override public double lerpTargetX() { return clientPosition.targetX(getX()); }
    @Override public double lerpTargetY() { return clientPosition.targetY(getY()); }
    @Override public double lerpTargetZ() { return clientPosition.targetZ(getZ()); }

    private void advanceClientPosition() {
        if (!clientPosition.pending()) return;
        double[] position = clientPosition.advance(getX(), getY(), getZ());
        setPos(position[0], position[1], position[2]);
    }

    /** Client catalogs can arrive after the tracked entity and its initial fallback-sized box. */
    private void refreshClientProfileDimensions() {
        try {
            var catalog = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientLooseObjectProfiles();
            if (catalog.size() == 0 || getItem().isEmpty()) return;
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(getItem(), true);
            if (Float.compare(getBbWidth(), profile.physicalWidth()) != 0
                    || Float.compare(getBbHeight(), profile.physicalHeight()) != 0) refreshDimensions();
        } catch (RuntimeException unavailableOrUnknownProfile) {
            // Keep the current dimensions until a valid client profile is available; never consult server data.
        }
    }

    private void evaluateGrenadeFuse() {
        if (!(level() instanceof ServerLevel server)) return;
        GrenadeFuseData data = getItem().get(ModDataComponents.GRENADE_FUSE.get());
        if (data == null || data.phase() != GrenadeFuse.Phase.ARMED) return;
        try {
            GrenadeFuse.Evaluation result = data.toFuse().evaluate(server.getGameTime());
            if (result.beepDue()) server.playSound(null, getX(), getY(), getZ(), ModSounds.GRENADE_BEEP.get(), SoundSource.BLOCKS, .35f, 1f);
            getItem().set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(result.state()));
            if (result.expired()) {
                BlockPos at = blockPosition();
                discard();
                GrenadeDetonationService.detonate(server, at);
            }
        } catch (RuntimeException invalid) {
            discard();
        }
    }

    private void breakJug(NativeMoveCapture impact, BlockPos landing) {
        if (!(level() instanceof ServerLevel server)) return;
        ReagentAttachment contents = null;
        var component = getItem().get(ModDataComponents.REAGENT.get());
        if (component != null) contents = new ReagentAttachment(component);
        if (contents == null || contents.isEmpty()) {
            if (LooseMotionDiagnostics.persistenceObserves(this))
                LooseMotionDiagnostics.observeSpill(this, "outcome=NO_REAGENT componentPresent=" + (component != null));
            playJugBreakFeedback(server);
            discard();
            return;
        }
        ReagentAttachment spill = new ReagentAttachment(contents.getMap());
        if (landing == null) {
            if (LooseMotionDiagnostics.persistenceObserves(this))
                LooseMotionDiagnostics.observeSpill(this, "outcome=NO_LOADED_SPILL_SURFACE contents=" + contents.getMap());
            return;
        }
        ReagentSystem.handleSpillSolution(spill, server, landing);
        // The spill bridge removes only admitted quantity from its argument; keep any remainder authoritative.
        if (spill.isEmpty()) {
            getItem().remove(ModDataComponents.REAGENT.get());
            if (LooseMotionDiagnostics.persistenceObserves(this))
                LooseMotionDiagnostics.observeSpill(this, "outcome=SPILL_RESULT_FULL_BEFORE_DISCARD landing=" + landing
                        + " original=" + contents.getMap() + " remainder=" + spill.getMap());
            playJugBreakFeedback(server);
            discard();
        } else if (!spill.equals(contents)) {
            getItem().set(ModDataComponents.REAGENT.get(), spill.toComponent());
            if (LooseMotionDiagnostics.persistenceObserves(this))
                LooseMotionDiagnostics.observeSpill(this, "outcome=SPILL_RESULT_PARTIAL_KEPT landing=" + landing
                        + " original=" + contents.getMap() + " remainder=" + spill.getMap());
        } else if (LooseMotionDiagnostics.persistenceObserves(this)) {
            LooseMotionDiagnostics.observeSpill(this, "outcome=SPILL_RESULT_UNCHANGED landing=" + landing
                    + " original=" + contents.getMap() + " remainder=" + spill.getMap());
        }
    }

    /** Attribute the contact to the first loaded surface hit by this move, not the actor's tracking frame. */
    private BlockPos findSpillSurface(ServerLevel server, NativeMoveCapture impact) {
        if (impact == null || !impact.positionCaptured() || !impact.collisionInfoFresh()) return null;
        // A truncated native frame witness set cannot certify a single collision domain.
        // A world-only impact has no frame witnesses and remains eligible for its exact box proof.
        if (impact.selectedContactsOverflow() && !impact.selectedContacts().isEmpty()) return null;
        var world = worldImpactContact(server, impact);
        var frames = impact.selectedContacts().stream().filter(c -> c.collisionInfo() == impact.currentCollisionInfo()).toList();
        boolean clippedX = Math.abs(impact.acceptedOwnDelta().x - impact.requested().x) > MOVEMENT_EPSILON;
        boolean clippedY = Math.abs(impact.acceptedOwnDelta().y - impact.requested().y) > MOVEMENT_EPSILON;
        boolean clippedZ = Math.abs(impact.acceptedOwnDelta().z - impact.requested().z) > MOVEMENT_EPSILON;
        var frameContacts = frames.stream().filter(c -> clippedX && Math.abs(c.normal().x) > .5
                        || clippedY && Math.abs(c.normal().y) > .5 || clippedZ && Math.abs(c.normal().z) > .5)
                .toList();
        var frameDomains = frameContacts.stream().map(NativeFrameContact::frameId).distinct().toList();
        if (world == null && frameDomains.size() == 1) {
            java.util.UUID frameId = frameDomains.get(0);
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server);
            var frame = container.getSubLevel(frameId);
            if (!(frame instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel sub)
                    || sub.isRemoved() || sub.getLevel() != server || container.getSubLevel(frameId) != sub) return null;
            var localImpact = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .pointAtWorldPosition(server, frameId, position());
            if (localImpact == null) return null;
            var floorContacts = frameContacts.stream().filter(c -> c.frameId().equals(frameId)
                            && c.normal().y > .5)
                    .sorted(java.util.Comparator.comparingDouble(c -> horizontalDistanceSqr(
                            c.storagePos().getX() + .5, c.storagePos().getZ() + .5, localImpact.storagePoint())))
                    .toList();
            if (!floorContacts.isEmpty()) {
                BlockPos selectedFloor = floorContacts.get(0).storagePos();
                var selectedState = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .knownServerBlock(server, sub, selectedFloor);
                if (selectedState == null) return null;
                return selectedFloor;
            }
            BlockPos beneathAcceptedFeet = BlockPos.containing(localImpact.storagePoint().x,
                    localImpact.storagePoint().y - .001, localImpact.storagePoint().z);
            return findLoadedSurface(server, sub, beneathAcceptedFeet, false);
        }
        // Any simultaneous frame/world blocker on the clipped axis is ambiguous; do not guess a grid.
        if (world == null || !frameContacts.isEmpty()) return null;
        return findLoadedSurface(server, null, world, false);
    }

    private static double horizontalDistanceSqr(double x, double z, Vec3 localPoint) {
        double dx = x - localPoint.x, dz = z - localPoint.z;
        return dx * dx + dz * dz;
    }

    /** Full swept AABB against ordinary-world collision shapes, matching vanilla's axis clipping. */
    private BlockPos worldImpactContact(ServerLevel server, NativeMoveCapture impact) {
        AABB start = getBoundingBox().move(impact.before().subtract(position()));
        AABB swept = start.expandTowards(impact.requested()).inflate(1.0E-5);
        BlockPos min = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos max = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        java.util.ArrayList<net.minecraft.world.phys.shapes.VoxelShape> shapes = new java.util.ArrayList<>();
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!server.hasChunkAt(pos)) return null;
            if (dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(server)
                    .getPlot(new net.minecraft.world.level.ChunkPos(pos)) != null) continue;
            var state = server.getBlockState(pos);
            var shape = state.getCollisionShape(server, pos, net.minecraft.world.phys.shapes.CollisionContext.of(this));
            if (!shape.isEmpty()) {
                shapes.add(shape.move(pos.getX(), pos.getY(), pos.getZ()));
                positions.add(pos.immutable());
            }
        }
        Vec3 clipped = clipVanillaWorldMotion(start, shapes, impact.requested());
        Vec3 requested = impact.requested();
        double x = clipped.x, y = clipped.y, z = clipped.z;
        AABB box = start.move(clipped);
        Vec3 accepted = impact.acceptedOwnDelta();
        if (Math.abs(x - accepted.x) > MOVEMENT_EPSILON || Math.abs(y - accepted.y) > MOVEMENT_EPSILON
                || Math.abs(z - accepted.z) > MOVEMENT_EPSILON) return null;
        if (Math.abs(x - requested.x) <= MOVEMENT_EPSILON && Math.abs(y - requested.y) <= MOVEMENT_EPSILON
                && Math.abs(z - requested.z) <= MOVEMENT_EPSILON) return null;
        // Return the world support column at the actually accepted impact position; the bounded
        // surface scan below still validates every cell and never forces residency.
        return BlockPos.containing(box.getCenter().x, box.minY - .001, box.getCenter().z);
    }

    /** Mirrors Entity.collideBoundingBox's loaded-shape axis order for ordinary world collision proof. */
    static Vec3 clipVanillaWorldMotion(AABB start, java.util.List<net.minecraft.world.phys.shapes.VoxelShape> shapes,
                                       Vec3 requested) {
        double y = net.minecraft.world.phys.shapes.Shapes.collide(net.minecraft.core.Direction.Axis.Y,
                start, shapes, requested.y);
        AABB box = start.move(0, y, 0);
        double x, z;
        if (Math.abs(requested.x) < Math.abs(requested.z)) {
            x = net.minecraft.world.phys.shapes.Shapes.collide(net.minecraft.core.Direction.Axis.X, box, shapes, requested.x);
            box = box.move(x, 0, 0);
            z = net.minecraft.world.phys.shapes.Shapes.collide(net.minecraft.core.Direction.Axis.Z, box, shapes, requested.z);
        } else {
            z = net.minecraft.world.phys.shapes.Shapes.collide(net.minecraft.core.Direction.Axis.Z, box, shapes, requested.z);
            box = box.move(0, 0, z);
            x = net.minecraft.world.phys.shapes.Shapes.collide(net.minecraft.core.Direction.Axis.X, box, shapes, requested.x);
        }
        return new Vec3(x, y, z);
    }

    private BlockPos findLoadedSurface(ServerLevel server, dev.ryanhcode.sable.sublevel.ServerSubLevel frame,
                                       BlockPos contact, boolean skipContactBlock) {
        int x = contact.getX();
        int z = contact.getZ();
        int startY = contact.getY() - (skipContactBlock ? 1 : 0);
        int minY = Math.max(server.getMinBuildHeight(), startY - 512);
        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = frame == null ? (server.hasChunkAt(pos) ? server.getBlockState(pos) : null)
                    : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.knownServerBlock(server, frame, pos);
            if (state == null) return null;
            if (state.is(com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get())) return pos;
            var shape = state.getCollisionShape(server, pos);
            if (!shape.isEmpty()) {
                for (var box : shape.toAabbs()) {
                    if (box.maxY > 0 && box.minY < 1) return pos;
                }
                return null;
            }
        }
        return null;
    }

    private void playJugBreakFeedback(ServerLevel server) {
        server.playSound(null, getX(), getBoundingBox().minY, getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.BLOCKS, 1f, 1f);
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(),
                getBoundingBox().minY + .03, getZ(), 6, .08, .02, .08, .04);
    }

    private boolean hasFloorSupport() {
        var bounds = getBoundingBox();
        BlockPos below = BlockPos.containing(getX(), bounds.minY - FLOOR_SUPPORT_DEPTH, getZ());
        // Unknown support is not treated as permanent support: settled entities periodically retry this
        // check, while the swept-chunk guard below prevents motion/collision queries into unloaded chunks.
        if (!level().hasChunkAt(below)) return false;
        var shape = level().getBlockState(below).getCollisionShape(level(), below);
        for (var box : shape.toAabbs()) if (Math.abs(below.getY() + box.maxY - bounds.minY) < .04
                && bounds.maxX > below.getX() + box.minX && bounds.minX < below.getX() + box.maxX
                && bounds.maxZ > below.getZ() + box.minZ && bounds.minZ < below.getZ() + box.maxZ) return true;
        return false;
    }

    private boolean chunksLoaded(net.minecraft.world.phys.AABB box) {
        LooseChunkBounds bounds = LooseChunkBounds.from(box);
        int minX = bounds.minX(), maxX = bounds.maxX();
        int minZ = bounds.minZ(), maxZ = bounds.maxZ();
        if (maxX - minX > 4 || maxZ - minZ > 4) return false;
        for (int x=minX; x<=maxX; x++) for (int z=minZ; z<=maxZ; z++) if (!level().hasChunk(x,z)) return false;
        return true;
    }
    public boolean motionBlockedByUnloaded() { return motionBlockedByUnloaded; }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static boolean finiteQuaternion(org.joml.Quaterniond q) {
        double norm = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w;
        return Double.isFinite(norm) && Math.abs(norm - 1) <= 1.0e-6;
    }
    private static Vec3 clamp(Vec3 v, double max) { double l=v.length(); return !Double.isFinite(l) ? Vec3.ZERO : l > max ? v.scale(max/l) : v; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (level() instanceof ServerLevel server) frameAnchorState.write(tag, server, this);
        tag.putFloat("LooseRotation", getRotationAngle()); tag.putFloat("LooseAngularVelocity", angularVelocity);
        tag.putBoolean("LooseSettled", settled());
        var orientation = orientationSnapshot();
        if (orientation != null) tag.put("LooseFrameOrientation", encodeOrientation(orientation));
        if (lastSafeFrameStoragePoint != null) {
            CompoundTag point = new CompoundTag();
            point.putDouble("X", lastSafeFrameStoragePoint.x); point.putDouble("Y", lastSafeFrameStoragePoint.y);
            point.putDouble("Z", lastSafeFrameStoragePoint.z); tag.put("LooseFrameStoragePoint", point);
        }
        if (creativePickupDelay > 0) tag.putInt("LooseCreativePickupDelay", creativePickupDelay);
        LooseMotionDiagnostics.observePersistence(this, tag, LooseMotionDiagnostics.PersistenceWatch.Phase.SAVE);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        frameAnchorState.read(tag);
        setRotationAngle(tag.getFloat("LooseRotation"));
        if (tag.contains("LooseFrameOrientation", CompoundTag.TAG_COMPOUND)) {
            var orientation = decodeOrientation(tag.getCompound("LooseFrameOrientation"));
            if (orientation != null) setOrientationSnapshot(orientation);
        }
        if (tag.contains("LooseFrameStoragePoint", CompoundTag.TAG_COMPOUND)) {
            CompoundTag point = tag.getCompound("LooseFrameStoragePoint");
            Vec3 decoded = new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z"));
            lastSafeFrameStoragePoint = finite(decoded) ? decoded : null;
        }
        angularVelocity = tag.contains("LooseAngularVelocity") && Float.isFinite(tag.getFloat("LooseAngularVelocity")) ? tag.getFloat("LooseAngularVelocity") : 0;
        setSettled(tag.getBoolean("LooseSettled"));
        creativePickupDelay = Math.max(0, Math.min(40, tag.getInt("LooseCreativePickupDelay")));
        LooseMotionDiagnostics.observePersistence(this, tag, LooseMotionDiagnostics.PersistenceWatch.Phase.READ);
    }
}
