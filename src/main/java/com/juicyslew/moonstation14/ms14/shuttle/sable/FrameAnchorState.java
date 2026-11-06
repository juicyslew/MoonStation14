package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import java.util.UUID;

/** Single owner for the serialized authority status and one-time reconstruction barrier. */
public final class FrameAnchorState {
    public enum DiagnosticPhase { BEFORE_BODY_SUPER, AFTER_BODY_SUPER, SAVE, UNLOAD_CALLBACK }
    public enum Status { WORLD, FRAME, UNRESOLVED }
    private static final String STATUS = "Status";
    private Status status = Status.WORLD;
    private FrameAnchor anchor;
    private String reason = "";
    private boolean waitingWitness;
    private boolean restoredFromFrame;
    private int saveWitnesses;
    private String lastSaveSignature = "";
    private boolean lifecycleDiagnosticArmed;
    private int lifecycleDiagnosticEvents;
    private boolean lifecycleDiagnosticTruncated;
    private boolean postRestoreMotionDiagnosticArmed;
    private int postRestoreMotionDiagnosticEvents;
    private boolean postRestoreMotionDiagnosticTruncated;
    private FrameAnchor postRestoreHydratedAnchor;
    private final PhysicalComposition physicalComposition = new PhysicalComposition();
    PhysicalComposition physicalComposition() { return physicalComposition; }

    /** Actor-owned proof of the exact physical point accepted after a fresh movement. */
    static final class PhysicalComposition {
        private FrameAnchor point;
        private Vec3 worldPoint;
        private Object owner, level;
        private long sequence;
        private boolean engaged, invalidated, loggedCommit;

        void observe(Object level, Object nativeOwner, Vec3 actualWorld, LoadedFrameRayQuery.ActorPoint afterPoint,
                     boolean fresh, boolean ownerValidated) {
            if (nativeOwner == null && afterPoint != null && afterPoint.frameId() == null
                    && finite(actualWorld) && finite(afterPoint.worldPoint()) && actualWorld.equals(afterPoint.worldPoint())) {
                point = null; worldPoint = null; owner = null; this.level = null; engaged = false; invalidated = false;
                return;
            }
            if (fresh) {
                if (finite(actualWorld) && afterPoint != null && finite(afterPoint.worldPoint())
                        && actualWorld.equals(afterPoint.worldPoint())) {
                    if (afterPoint.frameId() != null && level != null && finite(afterPoint.storagePoint()) && nativeOwner != null && ownerValidated) {
                        point = new FrameAnchor(afterPoint.frameId(), afterPoint.storagePoint());
                        worldPoint = afterPoint.worldPoint();
                        owner = nativeOwner; this.level = level; sequence++; engaged = true; invalidated = false;
                        return;
                    }
                }
                invalidate(); return;
            }
            if (point == null || this.level != level || owner != nativeOwner || !finite(actualWorld)
                    || !actualWorld.equals(worldPoint)) invalidate();
        }
        FrameAnchor candidate(Object level, Object owner, Vec3 actualWorld) {
            return !invalidated && point != null && this.level == level && this.owner == owner
                    && finite(actualWorld) && actualWorld.equals(worldPoint) ? point : null;
        }
        boolean requiresCommittedPoint() { return engaged || invalidated; }
        void clearWorld() { point = null; worldPoint = null; owner = null; level = null; engaged = false; invalidated = false; }
        FrameAnchor point() { return point; }
        Vec3 worldPoint() { return worldPoint; }
        Object owner() { return owner; }
        Object level() { return level; }
        long sequence() { return sequence; }
        boolean invalidated() { return invalidated; }
        private void invalidate() { point = null; worldPoint = null; owner = null; level = null; engaged = true; invalidated = true; }
        private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
        boolean markFirstCommitLog() { if (loggedCommit) return false; loggedCommit = true; return true; }
        void reset() { point = null; worldPoint = null; owner = null; level = null; sequence = 0; engaged = false; invalidated = false; loggedCommit = false; }
    }

    public void armLifecycleDiagnostics(net.minecraft.server.level.ServerLevel level) {
        lifecycleDiagnosticArmed = true;
        var service = FrameAnchorCaptureService.install(level);
        if (service != null) service.enableLifecycleDiagnostics();
    }
    public boolean lifecycleDiagnosticsArmed() { return lifecycleDiagnosticArmed; }
    public static boolean diagnosticBudgetAllows(int used, boolean truncated) { return used < 64 && !truncated; }
    public void traceLifecycle(net.minecraft.server.level.ServerLevel level, Entity actor, DiagnosticPhase phase) {
        traceLifecycle(level, actor, phase, "");
    }
    public void traceLifecycle(net.minecraft.server.level.ServerLevel level, Entity actor, DiagnosticPhase phase, String details) {
        if (!lifecycleDiagnosticArmed || lifecycleDiagnosticTruncated) return;
        try {
            traceLifecycleUnsafe(level, actor, phase, details);
        } catch (RuntimeException failure) {
            lifecycleDiagnosticTruncated = true;
            lifecycleDiagnosticArmed = false;
            try {
                String entity = actor == null ? "unknown" : String.valueOf(actor.getUUID());
                String tick = level == null ? "unknown" : String.valueOf(level.getGameTime());
                String reason = failure.getClass().getSimpleName() + ":" + String.valueOf(failure.getMessage());
                if (reason.length() > 256) reason = reason.substring(0, 256);
                System.out.println("[SableFrameAnchor] TRACE_ERROR phase=" + phase + " entity=" + entity
                        + " tick=" + tick + " reason=" + reason);
            } catch (RuntimeException ignoredDiagnosticFailure) { }
        }
    }
    private void traceLifecycleUnsafe(net.minecraft.server.level.ServerLevel level, Entity actor, DiagnosticPhase phase, String details) {
        if (!diagnosticBudgetAllows(lifecycleDiagnosticEvents, false)) {
            lifecycleDiagnosticTruncated = true;
            System.out.println("[SableFrameAnchor] TRACE_TRUNCATED entity=" + actor.getUUID());
            return;
        }
        lifecycleDiagnosticEvents++;
        var pos = actor.position();
        var block = net.minecraft.core.BlockPos.containing(pos);
        var chunk = level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4);
        var owner = actor instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                ? movement.sable$getTrackingSubLevel() : null;
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        var registered = owner == null || container == null ? null : container.getSubLevel(owner.getUniqueId());
        System.out.println("[SableFrameAnchor] BODY_TRACE tick=" + level.getGameTime() + " phase=" + phase
                + " entity=" + actor.getUUID() + " pos=" + pos + " velocity=" + actor.getDeltaMovement()
                + " onGround=" + actor.onGround() + " removed=" + actor.isRemoved() + " removal=" + actor.getRemovalReason()
                + " owner=" + (owner == null ? "null" : owner.getUniqueId()) + " ownerRemoved=" + (owner != null && owner.isRemoved())
                + " registeredSame=" + (owner != null && registered == owner) + " registered=" + (registered == null ? "null" : registered.getUniqueId())
                 + " status=" + status + " pending=" + pending() + " restored=" + restoredFromFrame
                 + " compositionSeq=" + physicalComposition.sequence() + " committedFrame=" + (physicalComposition.point() == null ? "null" : physicalComposition.point().frameId())
                 + " committedLocal=" + (physicalComposition.point() == null ? "null" : physicalComposition.point().storagePoint())
                 + " committedWorld=" + physicalComposition.worldPoint() + " compositionInvalid=" + physicalComposition.invalidated()
                 + " requiresCommitted=" + physicalComposition.requiresCommittedPoint()
                + " ticking=" + level.isPositionEntityTicking(block) + " full=" + (chunk == null ? "null"
                : chunk instanceof net.minecraft.world.level.chunk.LevelChunk full ? full.getFullStatus() : "NON_FULL")
                + (details == null || details.isBlank() ? "" : " details=" + details));
    }

    public Status status() { return status; }
    public FrameAnchor anchor() { return anchor; }
    public boolean pending() { return status != Status.WORLD; }
    public String reason() { return reason; }
    public boolean restoredFromFrame() { return restoredFromFrame; }
    public boolean postRestoreMotionDiagnosticsArmed() { return postRestoreMotionDiagnosticArmed && restoredFromFrame; }
    static boolean postRestoreMotionBudgetAllows(int used, boolean truncated) { return used < 64 && !truncated; }
    void armPostRestoreMotionDiagnostics(boolean scopeEnabled, boolean living) {
        if (status == Status.FRAME && scopeEnabled && living) postRestoreMotionDiagnosticArmed = true;
    }
    int nextPostRestoreMotionSample() {
        if (!postRestoreMotionDiagnosticArmed || !restoredFromFrame || postRestoreMotionDiagnosticTruncated) return 0;
        if (!postRestoreMotionBudgetAllows(postRestoreMotionDiagnosticEvents, false)) {
            postRestoreMotionDiagnosticTruncated = true; return -1;
        }
        return ++postRestoreMotionDiagnosticEvents;
    }
    void disablePostRestoreMotionDiagnostics() { postRestoreMotionDiagnosticArmed = false; }
    FrameAnchor postRestoreHydratedAnchor() { return postRestoreHydratedAnchor; }

    /** Records the physical result only; native ownership admission is supplied by this server adapter. */
    public void observePhysicalMove(net.minecraft.server.level.ServerLevel level, Entity actor,
            LoadedFrameRayQuery.ActorPoint afterPoint, boolean fresh) {
        if (!(actor instanceof FrameAnchorPending pending) || !mayCommit(pending.sable$frameAnchorInitializationPending())) return;
        var movement = actor instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension ext ? ext : null;
        var owner = movement == null ? null : movement.sable$getTrackingSubLevel();
        boolean valid = false;
        if (fresh && level != null && owner != null && !owner.isRemoved() && owner.getLevel() == level
                && owner.getPlot() != null && afterPoint != null && afterPoint.frameId() != null
                && actor.level() == level && finite(actor.position()) && level.getWorldBorder().isWithinBounds(actor.position())
                && owner.getUniqueId().equals(afterPoint.frameId())) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            var registered = container == null ? null : container.getSubLevel(owner.getUniqueId());
            valid = registered == owner && finite(afterPoint.worldPoint()) && finite(afterPoint.storagePoint())
                    && actor.position().equals(afterPoint.worldPoint());
        }
        physicalComposition.observe(level, owner, actor.position(), afterPoint, fresh, valid);
        if (fresh && valid && physicalComposition.markFirstCommitLog()) {
            try { System.out.println("[SableFrameAnchor] COMMIT entity=" + actor.getUUID() + " frame=" + afterPoint.frameId()
                    + " local=" + afterPoint.storagePoint() + " world=" + afterPoint.worldPoint() + " seq=" + physicalComposition.sequence()); }
            catch (RuntimeException failure) { try { System.out.println("[SableFrameAnchor] TRACE_ERROR phase=COMMIT entity=" + actor.getUUID()); } catch (RuntimeException ignored) { } }
        }
    }
    static boolean mayCommit(boolean initializationPending) { return !initializationPending; }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }

    public void read(CompoundTag entityTag) {
        physicalComposition.reset();
        restoredFromFrame = false;
        postRestoreMotionDiagnosticArmed = false; postRestoreMotionDiagnosticEvents = 0;
        postRestoreMotionDiagnosticTruncated = false; postRestoreHydratedAnchor = null;
        var decoded = FrameAnchor.load(entityTag);
        anchor = decoded.anchor();
        if (!decoded.present()) { status = Status.WORLD; reason = ""; waitingWitness = false; return; }
        if (anchor != null) { status = Status.FRAME; reason = "awaiting-exact-frame"; waitingWitness = false; }
        else { status = Status.UNRESOLVED; reason = decoded.reason(); waitingWitness = false; }
    }

    /** Writes only proven current ownership; UNKNOWN is explicit and WORLD removes stale data. */
    public void write(CompoundTag tag, net.minecraft.server.level.ServerLevel level, Entity actor) {
        if (level != null && actor != null) traceLifecycle(level, actor, DiagnosticPhase.SAVE);
        if (pending()) {
            if (status == Status.FRAME) tag.put(FrameAnchor.KEY, anchor.save());
            else putUnresolved(tag, reason);
            return;
        }
        var service = FrameAnchorCaptureService.install(level);
        var capture = service == null ? new FrameAnchorCaptureService.Capture(Status.UNRESOLVED, null, "observer-unavailable")
                : service.capture(actor, physicalComposition);
        writeCapture(tag, capture);
        if (capture.status() == Status.WORLD) physicalComposition.clearWorld();
        String signature = capture.status() + ":" + capture.reason();
        if (!signature.equals(lastSaveSignature) && saveWitnesses < 8) {
            lastSaveSignature = signature; saveWitnesses++;
            System.out.println("[SableFrameAnchor] SAVE entity=" + (actor == null ? "unknown" : actor.getUUID())
                    + " status=" + capture.status() + " proof=" + capture.reason()
                    + (capture.anchor() == null ? "" : " frame=" + capture.anchor().frameId() + " storage=" + capture.anchor().storagePoint())
                    + (physicalComposition.requiresCommittedPoint() ? " compositionSeq=" + physicalComposition.sequence()
                    + " committedWorld=" + physicalComposition.worldPoint() + " committedLocal="
                    + (physicalComposition.point() == null ? "null" : physicalComposition.point().storagePoint()) : ""));
        }
    }
    static void writeCapture(CompoundTag tag, FrameAnchorCaptureService.Capture capture) {
        if (capture == null || capture.status() == Status.UNRESOLVED) putUnresolved(tag, capture == null ? "unknown" : capture.reason());
        else if (capture.status() == Status.WORLD) writeWorld(tag);
        else if (capture.anchor() != null) tag.put(FrameAnchor.KEY, capture.anchor().save());
        else putUnresolved(tag, "frame-capture-without-anchor");
    }
    static void writeWorld(CompoundTag tag) { tag.remove(FrameAnchor.KEY); }
    private static void putUnresolved(CompoundTag tag, String reason) {
        CompoundTag marker = new CompoundTag(); marker.putString(STATUS, "UNRESOLVED");
        marker.putString("Reason", reason == null ? "unknown" : reason);
        tag.put(FrameAnchor.KEY, marker);
    }

    public boolean initialize(net.minecraft.server.level.ServerLevel level, Entity actor) {
        return initialize(level, actor, false);
    }

    /** One-time living restoration immediately before its admitted ordinary native movement attempt. */
    public boolean initializeForOwnedMovement(net.minecraft.server.level.ServerLevel level, Entity actor) {
        return initialize(level, actor, true);
    }

    private boolean initialize(net.minecraft.server.level.ServerLevel level, Entity actor, boolean ownedMovementEntry) {
        if (status == Status.WORLD) return true;
        if (status == Status.UNRESOLVED) { witness(actor, "WAIT", reason); return false; }
        var service = level == null ? null : FrameAnchorCaptureService.install(level);
        armPostRestoreMotionDiagnostics(service != null && service.lifecycleDiagnosticsEnabled(),
                actor instanceof net.minecraft.world.entity.LivingEntity);
        return initializeAttempt(() -> ownedMovementEntry
                        ? FrameAnchor.hydrateForOwnedMovement(level, actor, anchor)
                        : FrameAnchor.hydrate(level, actor, anchor),
                () -> witness(actor, "WAIT", "frame-or-residency-unavailable"),
                completed -> initializedWitness(actor, completed));
    }
    void tracePostRestoreMotion(net.minecraft.server.level.ServerLevel level, Entity actor,
            SableOwnedBodyMotion.MoveResult result, Vec3 before, Vec3 afterOwn, Vec3 afterCarry) {
        if (!postRestoreMotionDiagnosticsArmed() || level == null) return;
        try {
            int sample = nextPostRestoreMotionSample();
            if (sample == 0) return;
            if (sample == -1) { System.out.println("[SableFrameAnchor] POST_RESTORE_TRUNCATED entity=" + actor.getUUID()); return; }
            var p = physicalComposition.point();
            System.out.println("[SableFrameAnchor] POST_RESTORE_MOTION tick=" + level.getGameTime() + " entity=" + actor.getUUID()
                    + " attempt=" + sample + " hydrated=" + postRestoreHydratedAnchor
                    + " before=" + before + " afterOwn=" + afterOwn + " afterCarry=" + afterCarry
                    + " velocity=" + actor.getDeltaMovement() + " ground=" + actor.onGround()
                    + " serverTick=" + level.getServer().getTickCount()
                    + " requested=" + result.requestedOwnDisplacement() + " measured=" + result.measuredOwnDisplacement()
                    + " accepted=" + result.acceptedOwnDisplacement() + " frameVelocityDeltaPerSecond=" + result.frameVelocityDeltaPerSecond()
                    + " nativeInheritedDisplacement=" + (result.collisionInfoFresh() && result.collisionInfo() != null ? result.collisionInfo().inheritedMotion : "null/stale")
                    + " postOwnWorldDelta=" + afterCarry.subtract(afterOwn)
                    + " beforePoint=" + result.beforePoint() + " afterPoint=" + result.afterPoint()
                    + " committed=" + (p == null ? "null" : p.storagePoint()) + " seq=" + physicalComposition.sequence()
                    + " native=" + OwnedNativeMoveDiagnostic.describe(actor, result));
        } catch (RuntimeException failure) {
            disablePostRestoreMotionDiagnostics();
            try { System.out.println("[SableFrameAnchor] TRACE_ERROR phase=POST_RESTORE entity=" + actor.getUUID()); }
            catch (RuntimeException ignored) { }
        }
    }
    boolean initializeAttempt(java.util.function.BooleanSupplier hydrate, Runnable waiting,
                              java.util.function.Consumer<FrameAnchor> initialized) {
        if (status == Status.WORLD) return true;
        if (status == Status.UNRESOLVED) { waiting.run(); return false; }
        if (!hydrate.getAsBoolean()) { waiting.run(); return false; }
        var completed = anchor;
        status = Status.WORLD; anchor = null; reason = ""; waitingWitness = false;
        restoredFromFrame = true;
        if (postRestoreMotionDiagnosticArmed) postRestoreHydratedAnchor = completed;
        initialized.accept(completed);
        return true;
    }
    private void initializedWitness(Entity actor, FrameAnchor completed) {
        System.out.println("[SableFrameAnchor] INITIALIZED entity=" + actor.getUUID() + " frame=" + completed.frameId()
                + " storage=" + completed.storagePoint() + " world=" + actor.position());
    }
    private void witness(Entity actor, String state, String why) {
        if (waitingWitness) return;
        waitingWitness = true;
        System.out.println("[SableFrameAnchor] " + state + " entity=" + actor.getUUID() + " status=" + status + " reason=" + why
                + (anchor == null ? "" : " frame=" + anchor.frameId() + " storage=" + anchor.storagePoint()));
    }
}
