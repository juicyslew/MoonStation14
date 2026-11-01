package com.juicyslew.moonstation14.ms14.atmos.monitor;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Derived round-robin worklist of loaded monitor endpoints; identity checked and bounded per atmosphere stage. */
public final class AtmosMonitorWorklist {
    public record Registration(BlockPos position, UUID identity, long generation) {
        public Registration { position = position.immutable(); if (identity == null) throw new IllegalArgumentException("missing monitor identity"); }
    }
    public interface Device {
        UUID identity();
        boolean available();
        boolean paidAndPowered();
        void sample(long atmosphereCycleIdentity);
    }
    public record DrainResult(int inspected, int sampled) { }

    private final ArrayDeque<Registration> pending = new ArrayDeque<>();
    private final Map<BlockPos, Registration> active = new HashMap<>();
    private final Map<Registration, Long> lastProcessedCycle = new HashMap<>();
    private long sequence;

    public synchronized void register(BlockPos position, UUID identity) {
        BlockPos key = position.immutable();
        Registration prior = active.get(key);
        if (prior != null && prior.identity().equals(identity)) return;
        Registration registration = new Registration(key, identity, ++sequence);
        if (prior != null) lastProcessedCycle.remove(prior);
        active.put(registration.position(), registration);
        pending.addLast(registration);
    }
    public synchronized void unregister(BlockPos position, UUID identity) {
        Registration current = active.get(position);
        if (current != null && current.identity().equals(identity)) {
            active.remove(position);
            lastProcessedCycle.remove(current);
        }
    }
    public synchronized int size() { return active.size(); }

    /** A stage slice examines only its cap/deadline; active endpoints are requeued for later cycles. */
    public DrainResult drain(long cycleIdentity, int maxInspected, BooleanSupplier deadlineReached,
                             Function<Registration, Device> resolver) {
        if (maxInspected < 0 || deadlineReached == null || resolver == null) throw new IllegalArgumentException("invalid monitor stage budget");
        int inspected = 0, sampled = 0;
        while (inspected < maxInspected && (inspected == 0 || !deadlineReached.getAsBoolean())) {
            Registration registration;
            synchronized (this) {
                registration = pending.pollFirst();
                if (registration == null) break;
                inspected++;
                if (!registration.equals(active.get(registration.position()))) continue;
                if (java.util.Objects.equals(lastProcessedCycle.get(registration), cycleIdentity)) {
                    pending.addLast(registration);
                    continue;
                }
                lastProcessedCycle.put(registration, cycleIdentity);
            }
            Device device = resolver.apply(registration);
            boolean retain = device != null && device.available() && registration.identity().equals(device.identity());
            if (retain && device.paidAndPowered()) {
                device.sample(cycleIdentity);
                sampled++;
            }
            synchronized (this) {
                if (!retain) {
                    active.remove(registration.position(), registration);
                    lastProcessedCycle.remove(registration);
                }
                else if (registration.equals(active.get(registration.position()))) pending.addLast(registration);
            }
        }
        return new DrainResult(inspected, sampled);
    }
}
