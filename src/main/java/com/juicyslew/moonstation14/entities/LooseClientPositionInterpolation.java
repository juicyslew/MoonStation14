package com.juicyslew.moonstation14.entities;

/** Client-only, one-tick authoritative position buffer; contains no entity simulation. */
public final class LooseClientPositionInterpolation {
    private static final double SNAP_DISTANCE_SQUARED = 16.0;
    private double targetX, targetY, targetZ;
    private boolean pending;
    private java.util.UUID frameId;
    private net.minecraft.world.phys.Vec3 storagePoint;
    private boolean frameProjectionPaused;
    private java.util.UUID renderFrameId;
    private net.minecraft.world.phys.Vec3 previousRenderAnchor, currentRenderAnchor;

    public enum PacketKind { FRAME, WORLD, UNKNOWN_STORAGE }

    /** Metadata only: unknown SDK storage packets are dropped without disturbing a valid anchor. */
    public void packetMetadata(PacketKind kind, java.util.UUID id, net.minecraft.world.phys.Vec3 point) {
        if (kind == PacketKind.UNKNOWN_STORAGE) {
            pending = false;
            frameProjectionPaused = true;
            clearRenderAnchors();
            return;
        }
        if (kind == PacketKind.FRAME && (id == null || point == null || !finite(point))) return;
        pending = false;
        frameProjectionPaused = false;
        if (kind == PacketKind.WORLD) { frameId = null; storagePoint = null; clearRenderAnchors(); return; }
        if (!java.util.Objects.equals(frameId, id)) clearRenderAnchors();
        frameId = id;
        storagePoint = point;
    }
    /** Seeds a frame anchor received with the entity's initial spawn, before any client tick. */
    public boolean initializeSpawnFrameAnchor(java.util.UUID id, net.minecraft.world.phys.Vec3 point) {
        if (id == null || point == null || !finite(point)) return false;
        packetMetadata(PacketKind.FRAME, id, point);
        renderFrameId = id;
        previousRenderAnchor = point;
        currentRenderAnchor = point;
        return true;
    }
    public java.util.UUID frameId() { return frameId; }
    public net.minecraft.world.phys.Vec3 storagePoint() { return storagePoint; }
    public boolean frameProjectionPaused() { return frameProjectionPaused; }
    private void clearRenderAnchors() { renderFrameId = null; previousRenderAnchor = currentRenderAnchor = null; }
    /** Snapshot packet metadata only at the client-tick boundary, never during packet receipt. */
    public void beginRenderTick(net.minecraft.world.phys.Vec3 previousWorld,
            java.util.function.BiFunction<java.util.UUID, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3> inverseAtRenderZero) {
        if (frameProjectionPaused || frameId == null || storagePoint == null) { clearRenderAnchors(); return; }
        if (!frameId.equals(renderFrameId) || currentRenderAnchor == null) {
            renderFrameId = frameId;
            var inverse = previousWorld == null || inverseAtRenderZero == null ? null
                    : inverseAtRenderZero.apply(frameId, previousWorld);
            if (inverse == null || !finite(inverse)) { clearRenderAnchors(); return; }
            previousRenderAnchor = inverse;
            currentRenderAnchor = storagePoint;
        } else {
            previousRenderAnchor = currentRenderAnchor;
            currentRenderAnchor = storagePoint;
        }
    }
    public net.minecraft.world.phys.Vec3 renderAnchor(float partialTick) {
        if (!Float.isFinite(partialTick) || frameProjectionPaused || frameId == null || !frameId.equals(renderFrameId)
                || previousRenderAnchor == null || currentRenderAnchor == null) return null;
        return previousRenderAnchor.lerp(currentRenderAnchor, net.minecraft.util.Mth.clamp(partialTick, 0f, 1f));
    }
    /** Aligns both presentation endpoints after the owner's existing large-correction snap decision. */
    public void snapRenderAnchorToCurrent() {
        if (frameProjectionPaused || frameId == null || !frameId.equals(renderFrameId)
                || currentRenderAnchor == null) return;
        previousRenderAnchor = currentRenderAnchor;
    }
    /** Resolves the same exact anchor anew each tick; null means freeze, never storage-space fallback. */
    public net.minecraft.world.phys.Vec3 projectFrame(java.util.function.BiFunction<java.util.UUID,
            net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3> projector) {
        return frameProjectionPaused || frameId == null || storagePoint == null
                ? null : projector.apply(frameId, storagePoint);
    }
    public boolean shouldSnapFrameProjection(double currentX, double currentY, double currentZ,
                                              net.minecraft.world.phys.Vec3 projected) {
        return projected != null && largeCorrection(currentX, currentY, currentZ,
                projected.x, projected.y, projected.z);
    }
    public void clearPending() { pending = false; }
    private static boolean finite(net.minecraft.world.phys.Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }

    /** Latest packet wins; large corrections snap to avoid a streak across the world. */
    public boolean stage(double currentX, double currentY, double currentZ, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        targetX = x; targetY = y; targetZ = z; pending = true;
        if (largeCorrection(currentX, currentY, currentZ, x, y, z)) {
            pending = false;
            return true;
        }
        return false;
    }

    private static boolean largeCorrection(double currentX, double currentY, double currentZ,
                                           double x, double y, double z) {
        double dx = x - currentX, dy = y - currentY, dz = z - currentZ;
        return dx * dx + dy * dy + dz * dz > SNAP_DISTANCE_SQUARED;
    }

    public double targetX(double current) { return pending ? targetX : current; }
    public double targetY(double current) { return pending ? targetY : current; }
    public double targetZ(double current) { return pending ? targetZ : current; }
    public boolean pending() { return pending; }

    /** Applies the entire latest server position in one client tick, then clears it. */
    public double[] advance(double currentX, double currentY, double currentZ) {
        if (!pending) return new double[]{currentX, currentY, currentZ};
        pending = false;
        return new double[]{targetX, targetY, targetZ};
    }
}

