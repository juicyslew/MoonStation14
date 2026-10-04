package com.juicyslew.moonstation14.ms14.disposal.containment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-side, transient exact-body containment. Must be driven from the owning server level's tick. */
public final class DisposalContainmentService implements AutoCloseable {
    private static final Map<ServerLevel, DisposalContainmentService> LEVELS = new java.util.WeakHashMap<>();
    private final ServerLevel level;
    private final DisposalContainmentMembership<DisposalContainmentOwner> membership = new DisposalContainmentMembership<>();
    private final Map<UUID, OriginalFlags> saved = new HashMap<>();
    private boolean closed;

    public DisposalContainmentService(ServerLevel level) {
        this.level = java.util.Objects.requireNonNull(level);
        if (level.isClientSide) throw new IllegalArgumentException("containment is server-only");
        synchronized (LEVELS) {
            DisposalContainmentService previous = LEVELS.putIfAbsent(level, this);
            if (previous != null) throw new IllegalStateException("containment service already exists for level");
        }
    }

    /** The one authoritative containment index for this server level. */
    public static DisposalContainmentService forLevel(ServerLevel level) {
        if (level == null || level.isClientSide) throw new IllegalArgumentException("server level required");
        synchronized (LEVELS) {
            DisposalContainmentService current = LEVELS.get(level);
            if (current != null) return current;
            // The constructor registers itself. Do not use computeIfAbsent here: registering
            // from its mapping function mutates the same WeakHashMap during computation.
            return new DisposalContainmentService(level);
        }
    }

    public static void closeLevel(ServerLevel level) {
        DisposalContainmentService service;
        synchronized (LEVELS) { service = LEVELS.get(level); }
        if (service != null) service.close();
    }

    public static void closeAll() {
        java.util.List<DisposalContainmentService> services;
        synchronized (LEVELS) { services = java.util.List.copyOf(LEVELS.values()); }
        services.forEach(DisposalContainmentService::close);
    }

    /** Bind only the exact currently loaded living entity. This never mounts or removes it. */
    public boolean bind(LivingEntity body, DisposalContainmentOwner owner) {
        if (closed || !validBody(body) || !validOwner(owner) || saved.containsKey(body.getUUID())) return false;
        OriginalFlags flags = capture(body);
        if (!membership.bind(body.getUUID(), owner)) return false;
        saved.put(body.getUUID(), flags);
        suppress(body);
        pin(body, owner);
        return true;
    }

    /** Export the exact pre-containment flags for persistence alongside the owning reference. */
    public Optional<OriginalFlags> export(LivingEntity body, DisposalContainmentOwner expected) {
        if (body == null || membership.get(body.getUUID()) != expected) return Optional.empty();
        return Optional.ofNullable(saved.get(body.getUUID()));
    }

    /** Restore a persisted containment relation without treating currently suppressed flags as original. */
    public boolean bindRestored(LivingEntity body, DisposalContainmentOwner owner, OriginalFlags flags) {
        if (closed || flags == null || !validBody(body) || !validOwner(owner)
                || saved.containsKey(body.getUUID()) || !membership.bind(body.getUUID(), owner)) return false;
        saved.put(body.getUUID(), flags);
        suppress(body);
        pin(body, owner);
        return true;
    }

    /** Transfer ownership atomically without briefly restoring flags or releasing the body. */
    public boolean transfer(LivingEntity body, DisposalContainmentOwner expected, DisposalContainmentOwner next) {
        if (closed || !validBody(body) || membership.get(body.getUUID()) != expected || !validOwner(next)) return false;
        membership.unbind(body.getUUID(), expected);
        if (!membership.bind(body.getUUID(), next)) {
            membership.bind(body.getUUID(), expected);
            return false;
        }
        pin(body, next);
        return true;
    }

    public boolean unbind(LivingEntity body, DisposalContainmentOwner expected) {
        if (body == null || membership.get(body.getUUID()) != expected || !membership.unbind(body.getUUID(), expected)) return false;
        restore(body);
        return true;
    }

    /** A relation authorizes contained interaction only while its exact loaded owner remains valid. */
    public Optional<DisposalContainmentOwner> ownerOf(LivingEntity body) {
        if (body == null) return Optional.empty();
        DisposalContainmentOwner owner = membership.get(body.getUUID());
        if (owner == null) return Optional.empty();
        if (!validBody(body) || !validOwner(owner)) {
            release(body, owner);
            return Optional.empty();
        }
        return Optional.of(owner);
    }

    /** Call once per level tick. Invalid owners release bodies; valid owners authoritatively repin them. */
    public void tick() {
        if (closed) return;
        for (var entry : membership.snapshot().entrySet()) {
            LivingEntity body = level.getEntity(entry.getKey()) instanceof LivingEntity living ? living : null;
            if (body == null) {
                membership.unbind(entry.getKey(), entry.getValue());
                saved.remove(entry.getKey());
            } else if (!validBody(body) || !validOwner(entry.getValue())) release(body, entry.getValue());
            else {
                suppress(body);
                pin(body, entry.getValue());
            }
        }
    }

    private void release(LivingEntity body, DisposalContainmentOwner owner) {
        if (membership.unbind(body.getUUID(), owner)) restore(body);
    }

    private boolean validBody(LivingEntity body) {
        return body != null && !body.isRemoved() && body.isAlive() && body.level() == level
                && body.isAddedToLevel() && level.getEntity(body.getUUID()) == body;
    }

    private boolean validOwner(DisposalContainmentOwner owner) {
        if (owner == null || owner.identity() == null || owner.level() != level || !owner.isValid()) return false;
        Vec3 p = owner.position();
        return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }

    private static void suppress(LivingEntity body) {
        body.setInvisible(true);
        body.noPhysics = true;
        body.setNoGravity(true);
        setContainedVisual(body, true);
        body.setDeltaMovement(Vec3.ZERO);
    }

    private static void pin(LivingEntity body, DisposalContainmentOwner owner) {
        Vec3 p = owner.positionFor(body);
        if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return;
        body.teleportTo(p.x, p.y, p.z);
        body.setDeltaMovement(Vec3.ZERO);
    }

    private void restore(LivingEntity body) {
        OriginalFlags flags = saved.remove(body.getUUID());
        if (flags == null) return;
        body.setInvisible(flags.invisible);
        body.noPhysics = flags.noPhysics;
        body.setNoGravity(flags.noGravity);
        setContainedVisual(body, false);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (var entry : membership.snapshot().entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof LivingEntity body) restore(body);
        }
        membership.snapshot().forEach(membership::unbind);
        saved.clear();
        synchronized (LEVELS) { LEVELS.remove(level, this); }
    }

    private static OriginalFlags capture(LivingEntity body) {
        return new OriginalFlags(body.isInvisible(), body.noPhysics, body.isNoGravity());
    }

    private static void setContainedVisual(LivingEntity body, boolean contained) {
        if (body instanceof DisposalContainedVisual visual) visual.moonstation14$setDisposalContainedVisual(contained);
    }

    /** Immutable original entity flags captured before containment first began. */
    public record OriginalFlags(boolean invisible, boolean noPhysics, boolean noGravity) { }
}
