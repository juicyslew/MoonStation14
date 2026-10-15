package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared escape adapter for canonical portable carrier/world roots; no owner index or removal path. */
final class PortableBodyEscapeCustody {
    private PortableBodyEscapeCustody() { }

    static boolean tryEscape(ContainedBodyOwner owner, LivingEntity body) {
        return tryEscape(owner, body, 0L);
    }

    static boolean tryEscape(ContainedBodyOwner owner, LivingEntity body, long originSequence) {
        if (owner == null || body == null || !(body.level() instanceof ServerLevel level)
                || owner.level() != level || owner.stage() != ContainedBodyOwner.Stage.STATIONARY) return false;
        Entity capturedRoot = owner instanceof PortableBodyCarrierOwner carrier ? carrier.carrier()
                : owner instanceof PortableWorldStorageOwner world ? world.root() : null;
        java.util.UUID rootId = capturedRoot == null ? null : capturedRoot.getUUID();
        var snapshot = PortableBodyReleaseTransaction.capture(body);
        if (snapshot == null || snapshot.rootOwner() != owner) {
            PortableBodyEscapeService.diagnostic(body, rootId, "start-denied", "snapshot=" + (snapshot != null)
                    + ",sameOwner=" + (snapshot != null && snapshot.rootOwner() == owner)
                    + ",root=" + entityReport(level, capturedRoot));
            return false;
        }
        var custody = new EscapeCustody(owner, snapshot, body, level, rootId, capturedRoot);
        PortableBodyEscapeService.diagnostic(body, rootId, "started", "root=" + entityReport(level, capturedRoot)
                + ",snapshotCurrent=" + PortableBodyReleaseTransaction.isCurrent(snapshot)
                + ",accessible=" + custody.isAccessible());
        ServerPlayer presenter = null;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            // Portable bodies need not have hands (mice deliberately do not). A committed
            // lifecycle session is already the trusted authority for its exact active body.
            var activeBody = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                    .LifecycleCharacterSessionControl.activeCharacterBody(player).orElse(null);
            if (activeBody != null && activeBody.body() == body) { presenter = player; break; }
            var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(player).orElse(null);
            if (authority != null && authority.body() == body) { presenter = player; break; }
        }
        long priorOperation = activeOperationId(body);
        var result = PortableBodyEscapeService.request(body, presenter, originSequence, custody);
        if (result != TimedInteractionEngine.StartResult.DENIED && priorOperation < 0
                && owner instanceof PortableBodyCarrierOwner carrierOwner) {
            long operationId = activeOperationId(body);
            if (operationId >= 0) carrierOwner.bindEscapeCustody(custody, operationId);
        }
        return result != TimedInteractionEngine.StartResult.DENIED;
    }

    private static long activeOperationId(LivingEntity body) {
        var state = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
        if (state == null) return -1;
        return state.operations().stream().filter(op -> op.phase() == TimedOperation.Phase.ACTIVE
                && op.kind().equals(PortableBodyEscapeService.OPERATION_KIND)
                && op.sourceKey().equals(body.getUUID().toString())).mapToLong(TimedOperation::id).findFirst().orElse(-1);
    }

    static PreparedRefresh prepareTransfer(PortableBodyCarrierOwner source, LivingEntity body) {
        if (source == null || source.referencedBody() != body || source.activeEscapeCustody() == null) return null;
        long operationId = source.activeEscapeOperationId();
        EscapeCustody custody = source.activeEscapeCustody();
        return operationId >= 0 && custody.matchesIdentityAndOperation(source, body, operationId)
                && custody.isCurrent() ? new PreparedRefresh(source, custody, body, operationId) : null;
    }

    static final class PreparedRefresh {
        private final PortableBodyCarrierOwner source;
        private final EscapeCustody custody;
        private final LivingEntity body;
        private final long operationId;
        private boolean consumed;

        private PreparedRefresh(PortableBodyCarrierOwner source, EscapeCustody custody,
                                LivingEntity body, long operationId) {
            this.source = source;
            this.custody = custody;
            this.body = body;
            this.operationId = operationId;
        }

        boolean apply(PortableBodyCarrierOwner next) {
            EscapeCustody alreadyBound = next == null ? null : next.activeEscapeCustody();
            if (next == null || next.carrier() != source.carrier()
                    || !next.reference().equals(source.reference()) || next.referencedBody() != body
                    || alreadyBound != null && (alreadyBound != custody
                    || next.activeEscapeOperationId() != operationId) || consumed
                    || !custody.matchesIdentityAndOperation(source, body, operationId)) return false;
            consumed = true;
            if (!(body.level() instanceof ServerLevel serverLevel)) return false;
            var containment = BodyContainmentService.forLevel(serverLevel);
            if (containment.registeredOwnerOf(body).orElse(null) != next) return false;
            var refreshed = PortableBodyReleaseTransaction.capture(body);
            if (refreshed == null || refreshed.rootOwner() != next
                    || !PortableBodyReleaseTransaction.isCurrent(refreshed)
                    || containment.registeredOwnerOf(body).orElse(null) != next) return false;
            custody.refresh(next, refreshed);
            next.bindEscapeCustody(custody, operationId);
            return true;
        }
    }

    static final class EscapeCustody implements PortableBodyEscapeService.Custody {
        private ContainedBodyOwner owner;
        private PortableBodyReleaseTransaction.Snapshot snapshot;
        private final LivingEntity body;
        private final ServerLevel level;
        private final java.util.UUID rootId;
        private final Entity capturedRoot;

        private EscapeCustody(ContainedBodyOwner owner, PortableBodyReleaseTransaction.Snapshot snapshot,
                              LivingEntity body, ServerLevel level, java.util.UUID rootId, Entity capturedRoot) {
            this.owner = owner;
            this.snapshot = snapshot;
            this.body = body;
            this.level = level;
            this.rootId = rootId;
            this.capturedRoot = capturedRoot;
        }

        @Override public java.util.UUID rootId() { return rootId; }

        private boolean matchesIdentityAndOperation(PortableBodyCarrierOwner expected, LivingEntity exactBody, long operationId) {
            return body == exactBody && owner == expected && snapshot.rootOwner() == expected
                    && expected.activeEscapeCustody() == this && expected.activeEscapeOperationId() == operationId
                    && activeOperationId(body) == operationId;
        }

        private void refresh(ContainedBodyOwner next, PortableBodyReleaseTransaction.Snapshot nextSnapshot) {
            owner = next;
            snapshot = nextSnapshot;
        }

            @Override public boolean isCurrent() {
                boolean sameOwner = snapshot.rootOwner() == owner;
                boolean ownerValid = owner.isValid();
                boolean registered = BodyContainmentService.forLevel(level).registeredOwnerOf(body).orElse(null) == owner;
                boolean current = PortableBodyReleaseTransaction.isCurrent(snapshot);
                if (!sameOwner || !ownerValid || !registered || !current)
                    PortableBodyEscapeService.diagnostic(body, rootId, "first-invalid-current", "sameOwner=" + sameOwner
                            + ",ownerValid=" + ownerValid + ",registered=" + registered + ",snapshotCurrent=" + current
                            + ",root=" + entityReport(level, capturedRoot));
                return sameOwner && ownerValid && registered && current;
            }

            @Override public boolean canRemoveReference() { return isCurrent(); }

            @Override public boolean isAccessible() {
                Entity root = owner instanceof PortableBodyCarrierOwner carrier ? carrier.carrier()
                        : owner instanceof PortableWorldStorageOwner world ? world.root() : null;
                boolean rootLive = root != null && root.level() == level && root.isAddedToLevel() && !root.isRemoved()
                        && level.getEntity(root.getUUID()) == root && owner.isValid()
                        ;
                boolean reachable = rootLive && BodyItemTargetProbe.withinInteractionReach(body, root.getBoundingBox());
                if (!reachable) PortableBodyEscapeService.diagnostic(body, rootId, "inaccessible",
                        "rootLive=" + rootLive + ",reachable=" + reachable + ",body=" + body.position()
                                + ",root=" + entityReport(level, root));
                return reachable;
            }

            @Override public Vec3 rootPosition() {
                return isCurrent() ? owner.position() : null;
            }

            @Override public Vec3 movementPosition(LivingEntity exactBody) {
                if (exactBody != body || !isCurrent()) return null;
                return BodyContainmentService.forLevel(level).relativeMovementPosition(body, owner).orElse(null);
            }

            @Override public boolean releaseOriginalBody() {
                boolean current = isCurrent();
                boolean removable = current && canRemoveReference();
                boolean accessible = removable && isAccessible();
                if (!current || !removable || !accessible) {
                    PortableBodyEscapeService.diagnostic(body, rootId, "completion-denied-gate",
                            "current=" + current + ",removable=" + removable + ",accessible=" + accessible);
                    return false;
                }
                Vec3 formerRoot = owner.position();
                Entity holder = owner instanceof PortableBodyCarrierOwner carrier ? carrier.carrier()
                        : owner instanceof PortableWorldStorageOwner world ? world.root() : null;
                LivingEntity placementActor = owner instanceof PortableBodyCarrierOwner carrier ? carrier.carrier() : body;
                var drop = BodyItemTargetProbe.resolveDrop(level, placementActor, holder,
                        body.getBbWidth(), body.getBbHeight(), body);
                boolean placementClear = drop != null && BodyItemTargetProbe.portableBodyReleaseClear(level, body, holder,
                        drop.position(), body.getBbWidth(), body.getBbHeight());
                if (!placementClear) {
                    String blockers = drop == null ? "no-placement" : BodyItemTargetProbe.portableBodyReleaseBlockers(
                            level, body, holder, drop.position(), body.getBbWidth(), body.getBbHeight());
                    PortableBodyEscapeService.diagnostic(body, rootId, "placement-denied", "drop=" + drop
                            + ",clear=" + placementClear + ",blockers=" + blockers + ",body=" + body.position()
                            + ",holder=" + (holder == null ? "null" : holder.getType().toShortString() + "/" + holder.getId()));
                    return false;
                }
                var result = PortableBodyReleaseTransaction.release(snapshot,
                        new PortableBodyReleaseTransaction.Placement(drop.position(), drop.velocity(),
                                () -> holder != null && holder.level() == level && !holder.isRemoved()
                                        && holder.isAddedToLevel() && level.getEntity(holder.getUUID()) == holder
                                        && BodyItemTargetProbe.portableBodyReleaseClear(level, body, holder, drop.position(),
                                        body.getBbWidth(), body.getBbHeight())));
                if (result != PortableBodyReleaseTransaction.Result.SUCCESS) return false;
                PortableBodyEscapeService.diagnostic(body, rootId, "release-result", result.toString());
                if (body instanceof net.minecraft.world.entity.Mob mob)
                    com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.requestPostEscapeFlee(mob, formerRoot);
                return true;
            }
    }

    private static String entityReport(ServerLevel level, Entity entity) {
        if (entity == null) return "null";
        return entity.getClass().getName() + "/" + entity.getUUID() + "/id=" + entity.getId()
                + "/loaded=" + (entity.level() == level && entity.isAddedToLevel() && !entity.isRemoved()
                && level.getEntity(entity.getUUID()) == entity) + "/pos=" + entity.position();
    }
}
