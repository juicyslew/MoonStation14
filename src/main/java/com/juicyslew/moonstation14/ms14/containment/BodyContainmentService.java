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
    /** Exact entity identity for distinguishing duplicate deferred joins from same-UUID cold reloads. */
    private final Map<UUID, LivingEntity> boundBodies = new HashMap<>();
    /** Root frame sampled atomically with the last authoritative contained-body pin. */
    private final Map<UUID, PinFrame> pinFrames = new HashMap<>();
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
        boundBodies.put(body.getUUID(), body);
        saved.put(body.getUUID(), flags);
        savePortableRelation(body, owner, flags);
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
        boundBodies.put(body.getUUID(), body);
        savePortableRelation(body, owner, flags);
        suppress(body);
        pin(body, owner);
        return true;
    }

    /** Transfer ownership atomically without briefly restoring flags or releasing the body. */
    public boolean transfer(LivingEntity body, ContainedBodyOwner expected, ContainedBodyOwner next) {
        if (closed || !validBody(body) || membership.get(body.getUUID()) != expected || !validOwner(next)) return false;
        membership.unbind(body.getUUID(), expected);
        pinFrames.remove(body.getUUID());
        if (!membership.bind(body.getUUID(), next)) {
            membership.bind(body.getUUID(), expected);
            return false;
        }
        var flags = saved.get(body.getUUID());
        boundBodies.put(body.getUUID(), body);
        if (flags != null) savePortableRelation(body, next, flags);
        pin(body, next);
        return true;
    }

    public boolean unbind(LivingEntity body, ContainedBodyOwner expected) {
        if (body == null || membership.get(body.getUUID()) != expected || !membership.unbind(body.getUUID(), expected)) return false;
        boundBodies.remove(body.getUUID(), body);
        pinFrames.remove(body.getUUID());
        restore(body, true);
        saved.remove(body.getUUID());
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
                boundBodies.remove(entry.getKey());
                pinFrames.remove(entry.getKey());
                saved.remove(entry.getKey());
            } else if (!validBody(body) || !validOwner(entry.getValue())) {
                release(body, entry.getValue());
            }
            else {
                suppress(body);
                pin(body, entry.getValue());
            }
        }
    }

    private void release(LivingEntity body, ContainedBodyOwner owner) {
        if (membership.unbind(body.getUUID(), owner)) {
            boundBodies.remove(body.getUUID(), body);
            pinFrames.remove(body.getUUID());
            if (!portableRootUnloaded(owner)) restore(body, true);
            saved.remove(body.getUUID()); // flags were restored above, or relation remains fail-closed until root rejoin
        }
    }

    /** Body coordinate expressed in the root frame that produced its latest canonical pin. */
    public Optional<Vec3> relativeMovementPosition(LivingEntity body, ContainedBodyOwner expectedOwner) {
        if (body == null || expectedOwner == null || closed || !validBody(body)
                || membership.get(body.getUUID()) != expectedOwner || boundBodies.get(body.getUUID()) != body
                || !validOwner(expectedOwner)) return Optional.empty();
        PinFrame frame = pinFrames.get(body.getUUID());
        if (frame == null || frame.body != body || frame.owner != expectedOwner) return Optional.empty();
        Vec3 p = body.position().subtract(frame.rootPosition);
        return finite(p) ? Optional.of(p) : Optional.empty();
    }

    /** Reuse the authoritative containment pin for other server movement owners. */
    public Optional<Vec3> pinContainedBody(LivingEntity body, ContainedBodyOwner expectedOwner) {
        if (body == null || expectedOwner == null || membership.get(body.getUUID()) != expectedOwner
                || boundBodies.get(body.getUUID()) != body || !validBody(body) || !validOwner(expectedOwner))
            return Optional.empty();
        pin(body, expectedOwner);
        PinFrame frame = pinFrames.get(body.getUUID());
        return frame == null || frame.owner != expectedOwner ? Optional.empty() : Optional.of(frame.bodyPosition);
    }

    private boolean portableRootUnloaded(ContainedBodyOwner owner) {
        UUID rootId = owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carrier
                ? carrier.carrier().getUUID()
                : owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner world
                ? world.rootId() : null;
        return rootId != null && level.getEntity(rootId) == null;
    }

    private boolean validBody(LivingEntity body) {
        return isValidBody(body, level);
    }

    /** Side-effect-free exact registry observation for a staged ownership reconciliation. */
    public Optional<ContainedBodyOwner> registeredOwnerOf(LivingEntity body) {
        return body == null ? Optional.empty() : Optional.ofNullable(membership.get(body.getUUID()));
    }

    /** Reconcile only one just-loaded body/root against the actual loaded key ledger. */
    public void reconcileLoadedEntity(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof LivingEntity joiningBody && entity.level() == level
                && joiningBody.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                .PORTABLE_BODY_CONTAINMENT.get()) != null) suppress(joiningBody);
        if (closed || entity == null || entity.level() != level || !entity.isAddedToLevel()
                || level.getEntity(entity.getUUID()) != entity) return;
        if (entity instanceof LivingEntity body) {
            var relation = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                    .PORTABLE_BODY_CONTAINMENT.get());
            if (relation != null) {
                var owner = loadedPortableOwner(level.getEntity(relation.rootId()), relation.reference());
                if (owner != null && validOwner(owner)) {
                    if (!alreadyBoundSameEntity(body, owner, relation)) {
                        boolean clear = clearPriorLoadedIdentity(body, relation);
                        if (clear) bindRestored(body, owner, relation.originalFlags());
                    }
                }
            }
        }
        if (entity instanceof LivingEntity carrier) {
            var references = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying.loadedReferences(carrier);
            if (references == null) return;
            for (var reference : references) {
                LivingEntity body = level.getEntity(reference.body()) instanceof LivingEntity living ? living : null;
                if (body == null) continue;
                var relation = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                        .PORTABLE_BODY_CONTAINMENT.get());
                if (relation == null || !relation.rootId().equals(carrier.getUUID())
                        || !relation.reference().equals(reference)) continue;
                var owner = loadedPortableOwner(carrier, reference);
                if (owner != null && validOwner(owner)) {
                    if (alreadyBoundSameEntity(body, owner, relation)) continue;
                    if (clearPriorLoadedIdentity(body, relation))
                        bindRestored(body, owner, relation.originalFlags());
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.item.ItemEntity root) {
            for (var entry : com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying.directGridKeys(root.getItem())) {
                var reference = com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.reference(entry.stack());
                LivingEntity body = reference == null || !reference.dimension().equals(level.dimension().location())
                        ? null : level.getEntity(reference.body()) instanceof LivingEntity living ? living : null;
                if (body == null) continue;
                var relation = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments
                        .PORTABLE_BODY_CONTAINMENT.get());
                if (relation == null || !relation.rootId().equals(root.getUUID())
                        || !relation.reference().equals(reference)) continue;
                var owner = new com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner(
                        level, root.getUUID(), entry.token(), reference);
                if (validOwner(owner) && !alreadyBoundSameEntity(body, owner, relation)) {
                    if (clearPriorLoadedIdentity(body, relation))
                        bindRestored(body, owner, relation.originalFlags());
                }
            }
        }
    }

    /**
     * A cold entity load may reuse a UUID while this level service still has the previous
     * entity's transient membership/flags. Only the body's persisted relation plus the exact
     * loaded root ledger can authorize forgetting that old in-memory row; do not restore flags
     * onto the newly loaded entity before binding it with the persisted original flags.
     */
    private boolean clearPriorLoadedIdentity(LivingEntity body,
            com.juicyslew.moonstation14.ms14.hands.live.PortableBodyContainmentRelation relation) {
        ContainedBodyOwner prior = membership.get(body.getUUID());
        if (prior != null) {
            boolean sameRoot = prior instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carrier
                    && carrier.carrier().getUUID().equals(relation.rootId())
                    && carrier.reference().equals(relation.reference())
                    || prior instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner world
                    && world.rootId().equals(relation.rootId()) && world.reference().equals(relation.reference());
            if (!sameRoot && prior.isValid()) return false;
            if (!membership.unbind(body.getUUID(), prior)) return false;
            boundBodies.remove(body.getUUID());
            pinFrames.remove(body.getUUID());
        }
        saved.remove(body.getUUID());
        return true;
    }

    private boolean alreadyBoundSameEntity(LivingEntity body, ContainedBodyOwner candidate,
            com.juicyslew.moonstation14.ms14.hands.live.PortableBodyContainmentRelation relation) {
        ContainedBodyOwner registered = membership.get(body.getUUID());
        if (boundBodies.get(body.getUUID()) != body || registered == null
                || !java.util.Objects.equals(saved.get(body.getUUID()), relation.originalFlags())) return false;
        return registered instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carrierCurrent
                && candidate instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carrierNext
                && carrierCurrent.carrier() == carrierNext.carrier() && carrierCurrent.reference().equals(carrierNext.reference())
                && carrierCurrent.handToken().equals(carrierNext.handToken())
                || registered instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner worldCurrent
                && candidate instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner worldNext
                && worldCurrent.rootId().equals(worldNext.rootId()) && worldCurrent.reference().equals(worldNext.reference())
                && worldCurrent.childToken().equals(worldNext.childToken());
    }

    private ContainedBodyOwner loadedPortableOwner(net.minecraft.world.entity.Entity root,
            com.juicyslew.moonstation14.ms14.hands.PortableBodyReference reference) {
        if (root instanceof LivingEntity carrier) {
            var location = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying.locateReference(carrier, reference);
            return location == null ? null : new com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner(
                    carrier, reference, location.childToken());
        }
        if (root instanceof net.minecraft.world.entity.item.ItemEntity item) {
            var match = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying.directGridKeys(item.getItem())
                    .stream().filter(entry -> reference.equals(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys
                            .reference(entry.stack()))).findFirst().orElse(null);
            return match == null ? null : new com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner(
                    level, item.getUUID(), match.token(), reference);
        }
        return null;
    }

    private static void savePortableRelation(LivingEntity body, ContainedBodyOwner owner, ContainedBodyFlags flags) {
        com.juicyslew.moonstation14.ms14.hands.PortableBodyReference reference = null;
        UUID rootId = null;
        if (owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carrier) {
            reference = carrier.reference(); rootId = carrier.carrier().getUUID();
        } else if (owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner world) {
            reference = world.reference(); rootId = world.rootId();
        }
        if (reference != null && rootId != null && reference.body().equals(body.getUUID()))
            body.setData(com.juicyslew.moonstation14.component.ModDataAttachments.PORTABLE_BODY_CONTAINMENT.get(),
                    new com.juicyslew.moonstation14.ms14.hands.live.PortableBodyContainmentRelation(reference, rootId, flags));
    }

    /** Exact loaded-body check that retains resolved critical/dead bodies when native health is zero. */
    public static boolean isValidBody(LivingEntity body, ServerLevel level) {
        if (body == null || level == null || body.isRemoved() || body.level() != level
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body) return false;
        return body.isAlive() || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isRetainedNonAliveBody(body);
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

    private void pin(LivingEntity body, ContainedBodyOwner owner) {
        Vec3 p = owner.positionFor(body);
        Vec3 root = owner.position();
        if (!finite(p) || !finite(root)) return;
        net.minecraft.core.BlockPos before = body.blockPosition();
        net.minecraft.core.BlockPos after = net.minecraft.core.BlockPos.containing(p);
        body.teleportTo(p.x, p.y, p.z);
        body.setDeltaMovement(Vec3.ZERO);
        pinFrames.put(body.getUUID(), new PinFrame(body, owner, root, p));
        if ((before.getX() >> 4) != (after.getX() >> 4) || (before.getZ() >> 4) != (after.getZ() >> 4))
            recordOfflinePortableRelocation(body, owner);
    }

    private static boolean finite(Vec3 p) {
        return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
    private record PinFrame(LivingEntity body, ContainedBodyOwner owner, Vec3 rootPosition, Vec3 bodyPosition) { }

    /** Durable lifecycle writes only on an actual contained-body chunk transition, never each pin tick. */
    private static void recordOfflinePortableRelocation(LivingEntity body, ContainedBodyOwner expectedOwner) {
        if (!(body.level() instanceof ServerLevel level)) return;
        com.juicyslew.moonstation14.ms14.hands.PortableBodyReference reference;
        if (expectedOwner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner owner) {
            reference = owner.reference();
            if (!reference.body().equals(body.getUUID()) || owner.carrier().level() != level
                    || !owner.isValid()) return;
            var location = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying
                    .locateReference(owner.carrier(), reference);
            if (location == null || !location.childToken().equals(owner.handToken())) return;
        } else if (expectedOwner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner owner) {
            reference = owner.reference();
            if (!reference.body().equals(body.getUUID()) || owner.level() != level || !owner.isValid()) return;
        } else return;
        BodyContainmentService service = forLevel(level);
        if (service.registeredOwnerOf(body).orElse(null) != expectedOwner) return;
        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleStartupRuntime
                .recordOfflineBodyRelocation(body, exactBody -> exactBody == body
                        && service.registeredOwnerOf(body).orElse(null) == expectedOwner
                        && reference.equals(expectedOwner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carrier
                                ? carrier.reference()
                                : ((com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner) expectedOwner).reference())
                        && expectedOwner.isValid());
    }

    private void restore(LivingEntity body) { restore(body, true); }

    private void restore(LivingEntity body, boolean clearRelation) {
        ContainedBodyFlags flags = saved.remove(body.getUUID());
        if (flags != null) {
            body.setInvisible(flags.invisible);
            body.noPhysics = flags.noPhysics;
            body.setNoGravity(flags.noGravity);
            setContainedVisual(body, false);
        }
        if (clearRelation) body.removeData(com.juicyslew.moonstation14.component.ModDataAttachments.PORTABLE_BODY_CONTAINMENT.get());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (var entry : membership.snapshot().entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof LivingEntity body) restore(body, false);
        }
        membership.snapshot().forEach(membership::unbind);
        saved.clear();
        boundBodies.clear();
        pinFrames.clear();
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
