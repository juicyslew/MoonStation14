package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Serialization-only evidence for owner teardown; never participates in live gameplay queries. */
public final class FrameAnchorCaptureService {
    record Capture(FrameAnchorState.Status status, FrameAnchor anchor, String reason) { }
    record Allocation(int plotX, int plotZ, int logSize) {
        boolean contains(Vec3 p) {
            if (p == null || logSize < 0 || logSize > 30) return false;
            double size = 16.0 * (1L << logSize);
            return Double.isFinite(size) && p.x >= (double) plotX * size && p.x < ((double) plotX + 1) * size
                    && p.z >= (double) plotZ * size && p.z < ((double) plotZ + 1) * size;
        }
    }
    private record Proof(UUID id, Allocation allocation, dev.ryanhcode.sable.companion.math.Pose3d pose) { }
    private record Qualification(FrameAnchorState.Status status, UUID id, Allocation allocation, dev.ryanhcode.sable.companion.math.Pose3dc pose, String reason) { }
    static final class OwnershipProofs {
        private final WeakHashMap<Object, Proof> proofs = new WeakHashMap<>();
        boolean active = true;
        void removed(Object owner, UUID id, SubLevelRemovalReason reason,
                     dev.ryanhcode.sable.companion.math.Pose3d logicalPose, Allocation allocation) {
            if (!active) return;
            if (reason != SubLevelRemovalReason.UNLOADED || owner == null || id == null || logicalPose == null || allocation == null) {
                proofs.remove(owner); return;
            }
            try {
                var copy = new dev.ryanhcode.sable.companion.math.Pose3d().set(logicalPose);
                if (!finite(copy.transformPosition(new Vec3(0, 0, 0)))) { proofs.remove(owner); return; }
                proofs.put(owner, new Proof(id, allocation, copy));
            } catch (RuntimeException invalid) { proofs.remove(owner); }
        }
        Capture capture(Object owner, Object registered, UUID id, boolean sameLevel, boolean removed,
                        Vec3 world, dev.ryanhcode.sable.companion.math.Pose3dc livePose, Allocation allocation) {
            Qualification q = qualify(owner, registered, id, sameLevel, removed, allocation);
            if (q.status() == FrameAnchorState.Status.WORLD) return world();
            if (q.status() != FrameAnchorState.Status.FRAME) return unknown(q.reason());
            if (registered == owner) {
                if (livePose == null) return unknown("live-pose-invalid");
                try {
                    Vec3 local = livePose.transformPositionInverse(world);
                    if (!finite(local) || !q.allocation().contains(local)) return unknown("live-point-outside-allocation");
                    return new Capture(FrameAnchorState.Status.FRAME, new FrameAnchor(q.id(), local), "live");
                } catch (RuntimeException invalid) { return unknown("live-pose-invalid"); }
            }
            try {
                Vec3 local = q.pose().transformPositionInverse(world);
                if (!finite(local) || !q.allocation().contains(local)) return unknown("unloaded-point-invalid");
                return new Capture(FrameAnchorState.Status.FRAME, new FrameAnchor(q.id(), local), "unloaded");
            } catch (RuntimeException invalid) { return unknown("unloaded-pose-invalid"); }
        }
        Capture captureCommitted(Object owner, Object registered, UUID id, boolean sameLevel, boolean removed,
                                 Allocation liveAllocation, FrameAnchor candidate) {
            Qualification q = qualify(owner, registered, id, sameLevel, removed, liveAllocation);
            if (q.status() != FrameAnchorState.Status.FRAME) return q.status() == FrameAnchorState.Status.WORLD ? world() : unknown(q.reason());
            if (candidate == null || !id.equals(candidate.frameId())) return unknown("committed-point-missing-or-mismatch");
            Allocation allocation = q.allocation();
            Vec3 local = candidate.storagePoint();
            if (!finite(local) || !allocation.contains(local)) return unknown("committed-point-outside-allocation");
            return new Capture(FrameAnchorState.Status.FRAME, candidate, registered == owner ? "committed-live" : "committed-unloaded");
        }
        private Qualification qualify(Object owner, Object registered, UUID id, boolean sameLevel, boolean removed, Allocation liveAllocation) {
            if (!active) return new Qualification(FrameAnchorState.Status.UNRESOLVED,null,null,null,"closed");
            if (owner == null) return new Qualification(FrameAnchorState.Status.WORLD,null,null,null,"world");
            if (!sameLevel || id == null) return new Qualification(FrameAnchorState.Status.UNRESOLVED,null,null,null,"owner-level-or-id");
            if (registered == owner && !removed) return liveAllocation == null
                    ? new Qualification(FrameAnchorState.Status.UNRESOLVED,null,null,null,"live-allocation-unavailable")
                    : new Qualification(FrameAnchorState.Status.FRAME,id,liveAllocation,null,"live");
            if (registered != null) return new Qualification(FrameAnchorState.Status.UNRESOLVED,null,null,null,"owner-instance-replaced");
            Proof proof=proofs.get(owner);
            return proof == null || !proof.id().equals(id)
                    ? new Qualification(FrameAnchorState.Status.UNRESOLVED,null,null,null,"unload-proof-missing")
                    : new Qualification(FrameAnchorState.Status.FRAME,id,proof.allocation(),proof.pose(),"unloaded");
        }
        void close() { proofs.clear(); active = false; }
    }
    private static final Map<ServerLevel, FrameAnchorCaptureService> LEVELS = new WeakHashMap<>();
    private final java.lang.ref.WeakReference<ServerLevel> levelRef;
    private final OwnershipProofs ownership = new OwnershipProofs();
    private boolean lifecycleDiagnostics;
    private final SubLevelObserver observer = new SubLevelObserver() {
        @Override public void onSubLevelRemoved(SubLevel sub, SubLevelRemovalReason reason) {
            observeLifecycleRemoval(sub, reason);
            recordRemoval(sub, reason);
        }
    };
    void enableLifecycleDiagnostics() { lifecycleDiagnostics = true; }
    boolean lifecycleDiagnosticsEnabled() { return lifecycleDiagnostics && ownership.active; }
    private void observeLifecycleRemoval(SubLevel sub, SubLevelRemovalReason reason) {
        if (!lifecycleDiagnostics || !ownership.active) return;
        ServerLevel level = levelRef.get();
        if (level == null) return;
        try {
            var container = SubLevelContainer.getContainer(level);
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof FrameAnchorPending pending)) continue;
                var state = pending.sable$frameAnchorState();
                if (state == null || !state.lifecycleDiagnosticsArmed()) continue;
                var owner = entity instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement
                        ? movement.sable$getTrackingSubLevel() : null;
                UUID callbackId = sub == null ? null : sub.getUniqueId();
                boolean callbackRegistered = callbackId != null && container != null && container.getSubLevel(callbackId) == sub;
                String details = "callbackSub=" + callbackId + " reason=" + reason + " callbackRemoved="
                        + (sub != null && sub.isRemoved()) + " callbackRegistered=" + callbackRegistered
                        + " nativeOwnerIsCallback=" + (owner == sub);
                state.traceLifecycle(level, entity, FrameAnchorState.DiagnosticPhase.UNLOAD_CALLBACK, details);
            }
        } catch (RuntimeException failure) {
            lifecycleDiagnostics = false;
            try {
                String message = failure.getClass().getSimpleName() + ":" + String.valueOf(failure.getMessage());
                if (message.length() > 256) message = message.substring(0, 256);
                System.out.println("[SableFrameAnchor] UNLOAD_DIAGNOSTIC_ERROR tick=" + level.getGameTime()
                        + " callbackSub=" + (sub == null ? "unknown" : sub.getUniqueId()) + " reason=" + message);
            } catch (RuntimeException ignoredDiagnosticFailure) { }
        }
    }
    private void recordRemoval(SubLevel sub, SubLevelRemovalReason reason) {
        if (!ownership.active) return;
        if (reason != SubLevelRemovalReason.UNLOADED) { ownership.removed(sub, null, reason, null, null); return; }
        ServerLevel level = levelRef.get();
        if (sub == null || level == null) { ownership.removed(sub, null, reason, null, null); return; }
        try {
            var container = SubLevelContainer.getContainer(level);
            var plot = sub.getPlot();
            UUID id = sub.getUniqueId();
            var pose = sub.logicalPose(); // Serializer snapshots logicalPose, not the provider's live pose.
            if (container == null || plot == null || id == null || sub.getLevel() != level || container.getSubLevel(id) != sub) {
                ownership.removed(sub, id, reason, null, null); return;
            }
            ownership.removed(sub, id, reason, pose,
                    new Allocation(plot.plotPos.x, plot.plotPos.z, container.getLogPlotSize()));
        } catch (RuntimeException unavailable) { ownership.removed(sub, null, reason, null, null); }
    }
    private FrameAnchorCaptureService(ServerLevel level) { this.levelRef = new java.lang.ref.WeakReference<>(level); }
    public static synchronized FrameAnchorCaptureService install(ServerLevel level) {
        if (level == null) return null;
        FrameAnchorCaptureService existing = LEVELS.get(level);
        if (existing != null && existing.ownership.active) return existing;
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        var service = new FrameAnchorCaptureService(level);
        container.addObserver(service.observer);
        LEVELS.put(level, service);
        return service;
    }
    public static synchronized void close(ServerLevel level) {
        var service = LEVELS.remove(level);
        if (service != null) { service.ownership.close(); service.levelRef.clear(); }
    }
    Capture capture(Entity actor, FrameAnchorState.PhysicalComposition composition) {
        ServerLevel level = levelRef.get();
        if (!ownership.active || level == null || actor == null || actor.level() != level || !finite(actor.position())
                || !level.getWorldBorder().isWithinBounds(actor.position())) return unknown("invalid-actor");
        if (!(actor instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement))
            return composition != null && composition.requiresCommittedPoint()
                    ? ownership.captureCommitted(null,null,null,true,false,null,null)
                    : ownership.capture(null, null, null, true, false, actor.position(), null, null);
        SubLevel owner = movement.sable$getTrackingSubLevel();
        if (owner == null) return composition != null && composition.requiresCommittedPoint()
                ? ownership.captureCommitted(null,null,null,true,false,null,null)
                : ownership.capture(null,null,null,true,false,actor.position(),null,null);
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return unknown("owner-level-or-id");
        UUID id = owner.getUniqueId();
        SubLevel registered = id == null ? null : container.getSubLevel(id);
        boolean live = id != null && owner.getLevel() == level && registered == owner && !owner.isRemoved();
        Allocation allocation = null;
        if (live) try {
            var plot = owner.getPlot();
            if (plot != null) allocation = new Allocation(plot.plotPos.x, plot.plotPos.z, container.getLogPlotSize());
        } catch (RuntimeException ignored) { }
        if (composition != null && composition.requiresCommittedPoint())
            return ownership.captureCommitted(owner,registered,id,owner.getLevel()==level,owner.isRemoved(),allocation,
                    composition.candidate(level,owner,actor.position()));
        dev.ryanhcode.sable.companion.math.Pose3dc pose = null;
        if (live) try { pose = level instanceof dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension provider
                ? provider.sable$getPose(owner) : owner.logicalPose(); } catch (RuntimeException ignored) { }
        return ownership.capture(owner, registered, id, owner.getLevel() == level, owner.isRemoved(), actor.position(), pose, allocation);
    }
    private static Capture world() { return new Capture(FrameAnchorState.Status.WORLD, null, "world"); }
    private static Capture unknown(String reason) { return new Capture(FrameAnchorState.Status.UNRESOLVED, null, reason); }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
}
