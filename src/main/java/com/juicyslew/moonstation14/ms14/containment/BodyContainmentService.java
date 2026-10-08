package com.juicyslew.moonstation14.ms14.containment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-side, transient exact-body containment. Must be driven from the owning server level's tick. */
public final class BodyContainmentService implements AutoCloseable {
    private static final Map<ServerLevel, BodyContainmentService> LEVELS = new java.util.WeakHashMap<>();
    private final ServerLevel level;
    private final ContainedBodyMembership<ContainedBodyOwner> membership = new ContainedBodyMembership<>();
    private final Map<UUID, ContainedBodyFlags> saved = new HashMap<>();
    private boolean closed;

    public BodyContainmentService(ServerLevel level) {
        this.level = java.util.Objects.requireNonNull(level);
        if (level.isClientSide) throw new IllegalArgumentException("containment is server-only");
        synchronized (LEVELS) {
            BodyContainmentService previous = LEVELS.putIfAbsent(level, this);
            if (previous != null) throw new IllegalStateException("containment service already exists for level");
        }
    }

    /** The one authoritative containment index for this server level. */
    public static BodyContainmentService forLevel(ServerLevel level) {
        if (level == null || level.isClientSide) throw new IllegalArgumentException("server level required");
        synchronized (LEVELS) {
            BodyContainmentService current = LEVELS.get(level);
            if (current != null) return current;
            // The constructor registers itself. Do not use computeIfAbsent here: registering
            // from its mapping function mutates the same WeakHashMap during computation.
            return new BodyContainmentService(level);
        }
    }

    public static void closeLevel(ServerLevel level) {
        BodyContainmentService service;
        synchronized (LEVELS) { service = LEVELS.get(level); }
        if (service != null) service.close();
    }

    public static void closeAll() {
        java.util.List<BodyContainmentService> services;
        synchronized (LEVELS) { services = java.util.List.copyOf(LEVELS.values()); }
        services.forEach(BodyContainmentService::close);
    }

    /** Bind only the exact currently loaded living entity. This never mounts or removes it. */
    public boolean bind(LivingEntity body, ContainedBodyOwner owner) {
        if (closed || !validBody(body) || !validOwner(owner) || saved.containsKey(body.getUUID())) return false;
        if (body instanceof net.minecraft.world.entity.Mob mob)
            com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.cancel(mob);
        ContainedBodyFlags flags = capture(body);
        if (!membership.bind(body.getUUID(), owner)) return false;
        saved.put(body.getUUID(), flags);
        suppress(body);
        pin(body, owner);
        return true;
    }

    /** Export the exact pre-containment flags for persistence alongside the owning reference. */
    public Optional<ContainedBodyFlags> export(LivingEntity body, ContainedBodyOwner expected) {
        if (body == null || membership.get(body.getUUID()) != expected) return Optional.empty();
        return Optional.ofNullable(saved.get(body.getUUID()));
    }

    /** Restore a persisted containment relation without treating currently suppressed flags as original. */
    public boolean bindRestored(LivingEntity body, ContainedBodyOwner owner, ContainedBodyFlags flags) {
        if (closed || flags == null || !validBody(body) || !validOwner(owner)
                || saved.containsKey(body.getUUID()) || !membership.bind(body.getUUID(), owner)) return false;
        if (body instanceof net.minecraft.world.entity.Mob mob)
            com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.cancel(mob);
        saved.put(body.getUUID(), flags);
        suppress(body);
        pin(body, owner);
        return true;
    }

    /** Transfer ownership atomically without briefly restoring flags or releasing the body. */
    public boolean transfer(LivingEntity body, ContainedBodyOwner expected, ContainedBodyOwner next) {
        if (closed || !validBody(body) || membership.get(body.getUUID()) != expected || !validOwner(next)) return false;
        membership.unbind(body.getUUID(), expected);
        if (!membership.bind(body.getUUID(), next)) {
            membership.bind(body.getUUID(), expected);
            return false;
        }
        pin(body, next);
        return true;
    }

    public boolean unbind(LivingEntity body, ContainedBodyOwner expected) {
        if (body == null || membership.get(body.getUUID()) != expected || !membership.unbind(body.getUUID(), expected)) return false;
        restore(body);
        return true;
    }

    /** A relation authorizes contained interaction only while its exact loaded owner remains valid. */
    public Optional<ContainedBodyOwner> ownerOf(LivingEntity body) {
        if (body == null) return Optional.empty();
        ContainedBodyOwner owner = membership.get(body.getUUID());
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

    private void release(LivingEntity body, ContainedBodyOwner owner) {
        if (membership.unbind(body.getUUID(), owner)) restore(body);
    }

    private boolean validBody(LivingEntity body) {
        return isValidBody(body, level);
    }

    /** Exact loaded-body check that also retains resolved medical corpses after native death. */
    public static boolean isValidBody(LivingEntity body, ServerLevel level) {
        if (body == null || level == null || body.isRemoved() || body.level() != level
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body) return false;
        return body.isAlive() || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.policy(body) != null
                && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(body);
    }

    private boolean validOwner(ContainedBodyOwner owner) {
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

    private static void pin(LivingEntity body, ContainedBodyOwner owner) {
        Vec3 p = owner.positionFor(body);
        if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return;
        body.teleportTo(p.x, p.y, p.z);
        body.setDeltaMovement(Vec3.ZERO);
    }

    private void restore(LivingEntity body) {
        ContainedBodyFlags flags = saved.remove(body.getUUID());
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

    private static ContainedBodyFlags capture(LivingEntity body) {
        return new ContainedBodyFlags(body.isInvisible(), body.noPhysics, body.isNoGravity());
    }

    private static void setContainedVisual(LivingEntity body, boolean contained) {
        if (body instanceof ContainedBodyVisual visual) visual.moonstation14$setContainedBodyVisual(contained);
    }

    /** Immutable original entity flags captured before containment first began. */
    public record ContainedBodyFlags(boolean invisible, boolean noPhysics, boolean noGravity) { }
}
