package com.juicyslew.moonstation14.ms14.explosion.core;

import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Pure insertion-ordered queue; position matching uses the local world’s full 3D grid. */
public final class PendingExplosionQueue {
    public enum Status { ACCEPTED, MERGED, REJECTED }
    public record Request(ResourceLocation dimension, BlastPlanner.Pos origin, ExplosiveSourceData source,
                          double maxCombineDistance) {
        public Request {
            if (dimension == null || origin == null || source == null) throw new IllegalArgumentException("request fields cannot be null");
            if (!Double.isFinite(maxCombineDistance) || maxCombineDistance < 0) throw new IllegalArgumentException("combine distance must be finite and nonnegative");
            source.proposal();
        }
    }
    public record Admission(Status status, int index) { }
    private final List<Request> pending = new ArrayList<>();

    public Admission add(Request request) {
        if (request == null) return new Admission(Status.REJECTED, -1);
        for (int i = 0; i < pending.size(); i++) {
            Request first = pending.get(i);
            if (!first.dimension().equals(request.dimension()) || !first.source().explosionType().equals(request.source().explosionType())) continue;
            double dx = (double) first.origin().x() - request.origin().x();
            double dy = (double) first.origin().y() - request.origin().y();
            double dz = (double) first.origin().z() - request.origin().z();
            // The already-pending request owns the prototype threshold for this merge.
            double distance = first.maxCombineDistance();
            if (dx*dx + dy*dy + dz*dz > distance*distance) continue;
            try {
                ExplosiveSourceData old = first.source(), incoming = request.source();
                ExplosiveSourceData merged = new ExplosiveSourceData(old.totalIntensity() + incoming.totalIntensity(),
                        old.intensitySlope(), old.maxIntensity(), old.explosionType());
                pending.set(i, new Request(first.dimension(), first.origin(), merged, first.maxCombineDistance()));
                return new Admission(Status.MERGED, i);
            } catch (IllegalArgumentException ex) { return new Admission(Status.REJECTED, -1); }
        }
        pending.add(request);
        return new Admission(Status.ACCEPTED, pending.size() - 1);
    }
    /** Removes before returning, so a caller executing it cannot be merged into. */
    public Request poll() { return pending.isEmpty() ? null : pending.remove(0); }
    public int size() { return pending.size(); }
    public List<Request> snapshot() { return List.copyOf(pending); }
}
