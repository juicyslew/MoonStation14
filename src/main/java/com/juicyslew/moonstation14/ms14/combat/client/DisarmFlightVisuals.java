package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.TransientDisarmFlightPayload;

/** Short-lived client-owned release streak state. */
public final class DisarmFlightVisuals {
    public static final long CUE_LIFETIME_NANOS = 1_200_000_000L;
    public static final long FULL_VISIBILITY_NANOS = 600_000_000L;
    private static final java.util.Map<java.util.UUID, Flight> FLIGHTS = new java.util.LinkedHashMap<>();
    private static net.minecraft.resources.ResourceLocation world;
    private DisarmFlightVisuals() { }
    public static void receive(TransientDisarmFlightPayload payload) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        receive(payload, mc.level == null ? null : mc.level.dimension().location(), System.nanoTime());
    }
    public static void receive(TransientDisarmFlightPayload payload, net.minecraft.resources.ResourceLocation dimension, long now) {
        if (payload == null || payload.velocity() == null || !Double.isFinite(payload.velocity().x) || !Double.isFinite(payload.velocity().y) || !Double.isFinite(payload.velocity().z) || payload.velocity().lengthSqr() < 1.0e-8) return;
        reconcileWorld(dimension);
        if (dimension == null || !dimension.equals(payload.dimension())) return;
        Flight previous = FLIGHTS.get(payload.item());
        // TCP retransmission/duplicate publication of the same still-live release must not
        // keep the transient streak alive indefinitely.
        if (previous != null && now - previous.started < CUE_LIFETIME_NANOS
                && previous.origin.equals(payload.origin()) && previous.velocity.equals(payload.velocity())) return;
        FLIGHTS.put(payload.item(), new Flight(payload.origin(), payload.velocity(), now, payload.participant(), payload.actor()));
    }
    public static void clear() { FLIGHTS.clear(); world = null; }
    public static void reconcileWorld(net.minecraft.resources.ResourceLocation dimension) {
        if (!java.util.Objects.equals(world, dimension)) { FLIGHTS.clear(); world = dimension; }
    }
    public static java.util.List<Flight> sample(long now) {
        FLIGHTS.values().removeIf(f -> now - f.started >= CUE_LIFETIME_NANOS);
        return java.util.List.copyOf(FLIGHTS.values());
    }
    /** Read-only live snapshot retaining item identity for diagnostics. */
    public static java.util.Map<java.util.UUID, Flight> snapshotByItem(long now) {
        FLIGHTS.values().removeIf(f -> now - f.started >= CUE_LIFETIME_NANOS);
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(FLIGHTS));
    }
    public record Flight(net.minecraft.world.phys.Vec3 origin, net.minecraft.world.phys.Vec3 velocity, long started, boolean participant, boolean actor) {
        public Flight(net.minecraft.world.phys.Vec3 origin, net.minecraft.world.phys.Vec3 velocity, long started, boolean participant) { this(origin,velocity,started,participant,false); }
    }
}
