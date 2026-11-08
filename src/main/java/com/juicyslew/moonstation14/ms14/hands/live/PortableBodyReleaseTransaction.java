package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyReference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Shared exact-root transaction seam for future escape/throw/disposal release adapters. */
public final class PortableBodyReleaseTransaction {
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }
    record Diagnostic(UUID bodyId, UUID rootId, Result result, String branch) { }
    private static volatile String lastDiagnostic = "not-run";
    private static volatile Diagnostic lastReleaseDiagnostic;
    private static final java.util.concurrent.CopyOnWriteArrayList<Consumer<Diagnostic>> DIAGNOSTIC_OBSERVERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    static String lastDiagnostic() { return lastDiagnostic; }
    static Diagnostic lastReleaseDiagnostic() { return lastReleaseDiagnostic; }
    static AutoCloseable observeDiagnostics(Consumer<Diagnostic> observer) {
        DIAGNOSTIC_OBSERVERS.add(Objects.requireNonNull(observer));
        return () -> DIAGNOSTIC_OBSERVERS.remove(observer);
    }

    /** Existing root-specific staged transfer/publisher; this class does not duplicate its pipeline. */
    private interface RootMutation {
        boolean prepare(Snapshot snapshot);
        boolean remove(Snapshot snapshot);
        boolean restore(Snapshot snapshot);
        default String diagnostic() { return ""; }
    }

    public record Placement(Vec3 position, Vec3 velocity, BooleanSupplier collisionValid) {
        public Placement {
            Objects.requireNonNull(position); Objects.requireNonNull(velocity); Objects.requireNonNull(collisionValid);
            if (!finite(position) || !finite(velocity)) throw new IllegalArgumentException("nonfinite portable release placement");
        }
    }

    public static final class Snapshot {
        private final LivingEntity body;
        private final ServerLevel level;
        private final PortableBodyReference reference;
        private final ContainedBodyOwner owner;
        private final UUID rootId;
        private final String locationToken;
        private final String rootToken;
        private final long rootRevision;
        private final PortableBodyCarrying.ReferenceLocation carrierLocation;
        private final ItemEntity worldRoot;
        private final ItemStack worldStackReference;
        private final ItemStack worldStackEvidence;
        private final ItemStack keyEvidence;
        private final ItemStack rootEvidence;
        private final Vec3 originalPosition;
        private final BodyContainmentService.ContainedBodyFlags originalFlags;

        private Snapshot(LivingEntity body, ServerLevel level, PortableBodyReference reference,
                         ContainedBodyOwner owner, UUID rootId, String locationToken, String rootToken,
                         long rootRevision, PortableBodyCarrying.ReferenceLocation carrierLocation,
                         ItemEntity worldRoot, ItemStack worldStackReference,
                         ItemStack worldStackEvidence, ItemStack keyEvidence, ItemStack rootEvidence, Vec3 originalPosition,
                         BodyContainmentService.ContainedBodyFlags originalFlags) {
            this.body = body; this.level = level; this.reference = reference; this.owner = owner; this.rootId = rootId;
            this.locationToken = locationToken; this.rootToken = rootToken; this.rootRevision = rootRevision;
            this.carrierLocation = carrierLocation;
            this.worldRoot = worldRoot; this.worldStackReference = worldStackReference;
            this.worldStackEvidence = worldStackEvidence == null ? null : worldStackEvidence.copy();
            this.keyEvidence = keyEvidence.copy(); this.rootEvidence = rootEvidence.copy();
            this.originalPosition = originalPosition; this.originalFlags = originalFlags;
        }

        public LivingEntity body() { return body; }
        public PortableBodyReference reference() { return reference; }
        public ContainedBodyOwner rootOwner() { return owner; }
        public UUID rootId() { return rootId; }
        public String locationToken() { return locationToken; }
        public String rootToken() { return rootToken; }
        /** Carrier roots use the live-hands revision; world roots use -1 and exact stack evidence. */
        public long rootRevision() { return rootRevision; }
        public ItemStack keyEvidence() { return keyEvidence.copy(); }
        public ItemStack rootEvidence() { return rootEvidence.copy(); }
        public ItemStack worldStackEvidence() { return worldStackEvidence == null ? ItemStack.EMPTY : worldStackEvidence.copy(); }
    }

    private PortableBodyReleaseTransaction() { }

    /** Captures an exact loaded original body and its current canonical root locator without mutation. */
    public static Snapshot capture(LivingEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !BodyContainmentService.isValidBody(body, level)) return null;
        var containment = BodyContainmentService.forLevel(level);
        ContainedBodyOwner owner = containment.registeredOwnerOf(body).orElse(null);
        if (owner == null || !owner.isValid()) return null;
        var flags = containment.export(body, owner).orElse(null);
        if (flags == null) return null;
        PortableBodyReference reference;
        UUID rootId;
        if (owner instanceof PortableBodyCarrierOwner carrierOwner) {
            reference = carrierOwner.reference();
            LivingEntity carrier = carrierOwner.carrier();
            var location = PortableBodyCarrying.locateReference(carrier, reference);
            if (location == null || !location.childToken().equals(carrierOwner.handToken())) return null;
            return new Snapshot(body, level, reference, owner, carrier.getUUID(), location.childToken(),
                    location.rootToken(), location.rootRevision(), location, null, null, null, location.keyStack(), location.rootStack(),
                    body.position(), flags);
        }
        if (owner instanceof PortableWorldStorageOwner worldOwner) {
            reference = worldOwner.reference(); rootId = worldOwner.rootId();
            ItemEntity root = worldOwner.root();
            if (root == null || !worldOwner.isValid() || !PortableBodyCarrying.exactWorldEntry(root.getItem(),
                    worldOwner.childToken(), reference)) return null;
            var grid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(root.getItem()).orElse(null);
            var key = grid == null ? null : grid.entries().stream().filter(entry -> entry.token().equals(worldOwner.childToken())
                    && reference.equals(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.reference(entry.stack())))
                    .map(com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry::stack).findFirst().orElse(null);
            if (key == null) return null;
            return new Snapshot(body, level, reference, owner, rootId, worldOwner.childToken(),
                    worldOwner.childToken(), -1, null, root, root.getItem(), root.getItem().copy(), key, root.getItem(),
                    body.position(), flags);
        }
        return null;
    }

    /** Removes the key via the caller's existing root transfer route before restoring/placing the body. */
    private static Result releaseStaged(Snapshot snapshot, Placement placement, RootMutation mutation) {
        lastDiagnostic = "start";
        if (snapshot == null || placement == null || mutation == null || !current(snapshot)) {
            lastDiagnostic = "snapshot not current"; return Result.DENIED;
        }
        if (!placementValid(placement)) { lastDiagnostic = "placement invalid before prepare"; return Result.DENIED; }
        try { if (!mutation.prepare(snapshot)) { lastDiagnostic = "root prepare denied"; return Result.DENIED; } }
        catch (RuntimeException failure) { lastDiagnostic = "root prepare threw " + failure.getClass().getSimpleName(); return Result.DENIED; }
        if (!current(snapshot)) { lastDiagnostic = "snapshot became stale during prepare"; return Result.DENIED; }
        if (!placementValid(placement)) { lastDiagnostic = "placement invalid after prepare"; return Result.DENIED; }
        boolean removed;
        try { removed = mutation.remove(snapshot); }
        catch (RuntimeException failure) { removed = false; }
        var containment = BodyContainmentService.forLevel(snapshot.level);
        boolean rootCleared = !referenceStillInRoot(snapshot);
        if (!removed || !rootCleared) { lastDiagnostic = "root removal denied=" + removed + ",cleared=" + rootCleared
                + ",detail=" + mutation.diagnostic();
            return compensate(snapshot, mutation, false) ? Result.DENIED : Result.RECOVERY_REQUIRED; }
        ContainedBodyOwner remainingOwner = containment.registeredOwnerOf(snapshot.body).orElse(null);
        if (remainingOwner == snapshot.owner) containment.unbind(snapshot.body, snapshot.owner);
        else if (remainingOwner != null) { lastDiagnostic = "unexpected remaining owner";
            return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED; }
        if (containment.registeredOwnerOf(snapshot.body).isPresent() || !flagsRestored(snapshot.body, snapshot.originalFlags))
            { lastDiagnostic = "containment/flags not restored"; return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED; }
        if (!placementValid(placement)) { lastDiagnostic = "placement invalid after unbind";
            return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED; }
        try {
            snapshot.body.teleportTo(placement.position().x, placement.position().y, placement.position().z);
            snapshot.body.setDeltaMovement(placement.velocity());
            snapshot.body.hasImpulse = true;
        } catch (RuntimeException failure) { lastDiagnostic = "placement teleport threw"; return compensate(snapshot, mutation, true)
                ? Result.DENIED : Result.RECOVERY_REQUIRED; }
        if (!exactLoadedBody(snapshot) || !placementValid(placement)
                || snapshot.body.position().distanceToSqr(placement.position()) > 1.0e-10)
            { lastDiagnostic = "post-teleport validation denied"; return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED; }
        lastDiagnostic = "success";
        return Result.SUCCESS;
    }

    /** Canonical built-in adapter for current hand/equipment/world-grid roots. */
    public static Result release(Snapshot snapshot, Placement placement) {
        if (snapshot == null) return Result.DENIED;
        Result result = LiveHandsCommitGateway.batch(() -> releaseStaged(snapshot, placement, currentRootMutation(snapshot)));
        UUID rootId = snapshot.rootId;
        var diagnostic = new Diagnostic(snapshot.body.getUUID(), rootId, result, lastDiagnostic);
        lastReleaseDiagnostic = diagnostic;
        for (var observer : DIAGNOSTIC_OBSERVERS) try { observer.accept(diagnostic); }
        catch (RuntimeException ignored) { }
        return result;
    }

    /**
     * Atomically transfers an exact contained body from its portable root to an already prepared
     * destination owner. The destination's admission predicate is checked around root preparation;
     * no entity is spawned, cloned, or temporarily exposed during the transfer.
     */
    public static Result transfer(Snapshot snapshot, ContainedBodyOwner destinationOwner,
                                  BooleanSupplier admissionGate) {
        if (snapshot == null || destinationOwner == null || admissionGate == null) return Result.DENIED;
        return LiveHandsCommitGateway.batch(() -> transferStaged(snapshot, destinationOwner, admissionGate));
    }

    private static Result transferStaged(Snapshot snapshot, ContainedBodyOwner destinationOwner,
                                         BooleanSupplier admissionGate) {
        if (!current(snapshot) || destinationOwner.level() != snapshot.level || !destinationOwner.isValid()
                || !admitted(admissionGate)) return Result.DENIED;
        RootMutation mutation = currentRootMutation(snapshot);
        try { if (!mutation.prepare(snapshot) || !current(snapshot) || !destinationOwner.isValid()
                || !admitted(admissionGate)) return Result.DENIED; }
        catch (RuntimeException failure) { return Result.DENIED; }
        boolean removed;
        try { removed = mutation.remove(snapshot); }
        catch (RuntimeException failure) { removed = false; }
        var containment = BodyContainmentService.forLevel(snapshot.level);
        if (!removed || referenceStillInRoot(snapshot))
            return compensate(snapshot, mutation, false) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        ContainedBodyOwner remaining = containment.registeredOwnerOf(snapshot.body).orElse(null);
        if (remaining == snapshot.owner) containment.unbind(snapshot.body, snapshot.owner);
        else if (remaining != null) return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        if (containment.registeredOwnerOf(snapshot.body).isPresent()
                || !flagsRestored(snapshot.body, snapshot.originalFlags)
                || !destinationOwner.isValid() || !admitted(admissionGate))
            return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        if (!containment.bindRestored(snapshot.body, destinationOwner, snapshot.originalFlags))
            return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        if (containment.registeredOwnerOf(snapshot.body).orElse(null) != destinationOwner
                || !destinationOwner.isValid() || !admitted(admissionGate)) {
            containment.unbind(snapshot.body, destinationOwner);
            return compensate(snapshot, mutation, true) ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return Result.SUCCESS;
    }

    private static boolean admitted(BooleanSupplier gate) {
        try { return gate.getAsBoolean(); } catch (RuntimeException denied) { return false; }
    }

    public static boolean isCurrent(Snapshot snapshot) { return snapshot != null && current(snapshot); }

    private static RootMutation currentRootMutation(Snapshot snapshot) {
        if (snapshot == null) return new RootMutation() {
            public boolean prepare(Snapshot ignored) { return false; }
            public boolean remove(Snapshot ignored) { return false; }
            public boolean restore(Snapshot ignored) { return false; }
        };
        return new PreparedRootMutation(snapshot);
    }

    /** One prevalidated root plan is shared by escape and held-key direct release. */
    private static final class PreparedRootMutation implements RootMutation {
        private final Snapshot snapshot;
        private LivingEntity carrier;
        private LiveHands before;
        private LiveHands staged;
        private PortableBodyCarrying.ReferenceLocation location;
        private ItemStack keyToRestore;
        private BodyWorldWorldGridTransfer.PreparedPortableKeyRelease worldPlan;
        private String mutationDiagnostic = "";

        private PreparedRootMutation(Snapshot snapshot) { this.snapshot = snapshot; }

        @Override public boolean prepare(Snapshot ignored) {
            if (snapshot.owner instanceof PortableBodyCarrierOwner owner) {
                carrier = owner.carrier();
                before = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                if (before == null || !before.compatible(carrier)) return false;
                location = PortableBodyCarrying.locateReference(carrier, snapshot.reference);
                if (!matchesCapturedLocator(snapshot, location)) return false;
                keyToRestore = location.keyStack();
                Optional<LiveHands> calculated;
                if (location.directKey()) {
                    var taken = before.takeWhole(before.revision(), location.location(),
                            new com.juicyslew.moonstation14.ms14.hands.ItemToken(location.childToken()));
                    if (!taken.succeeded() || !samePortableKey(taken.takenStack().orElse(ItemStack.EMPTY), snapshot.reference)) return false;
                    calculated = Optional.of(taken.state());
                } else {
                    var equipment = LiveHands.equipmentSnapshot(carrier, before).orElse(null);
                    if (equipment == null || location.anchor() == null || location.rotation() == null) return false;
                    boolean worn = equipment.slots().stream().anyMatch(slot -> slot.id().equals(location.location()));
                    var declared = equipment.slots().stream().map(LiveHands.EquipmentSlot::id).toList();
                    calculated = before.removeGridChild(before.revision(), declared,
                            new LiveHands.GridHost(worn, location.location(), location.rootToken()),
                            location.childToken(), location.anchor(), location.rotation());
                }
                staged = calculated.orElse(null);
                return staged != null && com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.representable(staged, carrier)
                        && carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == before;
            }
            if (!(snapshot.owner instanceof PortableWorldStorageOwner) || snapshot.worldRoot == null
                    || snapshot.level.getEntity(snapshot.rootId) != snapshot.worldRoot) return false;
            var grid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(snapshot.worldRoot.getItem()).orElse(null);
            var key = grid == null ? null : grid.entries().stream().filter(entry -> entry.token().equals(snapshot.locationToken)
                    && snapshot.reference.equals(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.reference(entry.stack())))
                    .map(com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry::stack).findFirst().orElse(null);
            if (key == null) return false;
            keyToRestore = key.copy();
            worldPlan = BodyWorldWorldGridTransfer.preparePortableKeyRelease(snapshot.worldRoot, snapshot.locationToken,
                    snapshot.reference, keyToRestore);
            return worldPlan != null;
        }

        @Override public boolean remove(Snapshot ignored) {
            if (carrier != null) {
                if (carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != before
                        || !matchesCapturedLocator(snapshot, PortableBodyCarrying.locateReference(carrier, snapshot.reference))) return false;
                try { LiveHandsCommitGateway.commitOwned(carrier, before, staged); }
                catch (RuntimeException failure) { return false; }
                var containment = BodyContainmentService.forLevel(snapshot.level);
                return carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == staged
                        && PortableBodyCarrying.locateReference(carrier, snapshot.reference) == null
                        && containment.registeredOwnerOf(snapshot.body).isEmpty()
                        && flagsRestored(snapshot.body, snapshot.originalFlags);
            }
            boolean removed = worldPlan != null && worldPlan.remove();
            mutationDiagnostic = worldPlan == null ? "world plan missing" : worldPlan.diagnostic();
            return removed;
        }

        @Override public String diagnostic() { return mutationDiagnostic; }

        @Override public boolean restore(Snapshot ignored) {
            if (carrier != null) {
                LiveHands current = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                if (current == null || !current.compatible(carrier)
                        || PortableBodyCarrying.locateReference(carrier, snapshot.reference) != null) return false;
                Optional<LiveHands> restored;
                if (location.directKey()) {
                    var put = current.putWhole(current.revision(), location.location(),
                            new com.juicyslew.moonstation14.ms14.hands.ItemToken(location.childToken()), keyToRestore);
                    restored = put.succeeded() ? Optional.of(put.state()) : Optional.empty();
                } else {
                    var equipment = LiveHands.equipmentSnapshot(carrier, current).orElse(null);
                    if (equipment == null) return false;
                    var declared = equipment.slots().stream().map(LiveHands.EquipmentSlot::id).toList();
                    boolean worn = equipment.slots().stream().anyMatch(slot -> slot.id().equals(location.location()));
                    restored = current.addGridChild(current.revision(), declared,
                            new LiveHands.GridHost(worn, location.location(), location.rootToken()), location.childToken(),
                            keyToRestore, location.anchor(), location.rotation());
                }
                if (restored.isEmpty()) return false;
                try { LiveHandsCommitGateway.commitOwned(carrier, current, restored.orElseThrow()); }
                catch (RuntimeException failure) { return false; }
                return carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                        == restored.orElseThrow() && PortableBodyCarrying.locateReference(carrier, snapshot.reference) != null;
            }
            return worldPlan != null && worldPlan.restore();
        }
    }

    private static boolean current(Snapshot s) {
        if (!exactLoadedBody(s) || BodyContainmentService.forLevel(s.level).registeredOwnerOf(s.body).orElse(null) != s.owner
                || !s.owner.isValid()) return false;
        if (s.owner instanceof PortableBodyCarrierOwner owner) {
            var location = PortableBodyCarrying.locateReference(owner.carrier(), s.reference);
            return matchesCapturedLocator(s, location);
        }
        return s.worldRoot != null && s.level.getEntity(s.rootId) == s.worldRoot && !s.worldRoot.isRemoved()
                && referenceStillInRoot(s);
    }

    private static boolean referenceStillInRoot(Snapshot s) {
        if (s.owner instanceof PortableBodyCarrierOwner owner)
            return PortableBodyCarrying.locateReference(owner.carrier(), s.reference) != null;
        return s.worldRoot != null && PortableBodyCarrying.exactWorldEntry(s.worldRoot.getItem(), s.locationToken, s.reference);
    }

    private static boolean matchesCapturedLocator(Snapshot s, PortableBodyCarrying.ReferenceLocation current) {
        return current != null && current.childToken().equals(s.locationToken)
                && current.rootToken().equals(s.rootToken) && current.location().equals(s.carrierLocation.location())
                && samePortableKey(current.keyStack(), s.reference);
    }

    private static boolean samePortableKey(ItemStack stack, PortableBodyReference reference) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
                && com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.isKey(stack)
                && reference.equals(com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.reference(stack));
    }

    private static boolean compensate(Snapshot s, RootMutation mutation, boolean removed) {
        if (!exactLoadedBody(s)) return false;
        try {
            s.body.teleportTo(s.originalPosition.x, s.originalPosition.y, s.originalPosition.z);
            s.body.setDeltaMovement(Vec3.ZERO);
        } catch (RuntimeException failure) { return false; }
        if (removed || !referenceStillInRoot(s)) {
            boolean restored;
            try { restored = mutation.restore(s); }
            catch (RuntimeException failure) { restored = false; }
            if (!restored || !referenceStillInRoot(s)) return false;
        }
        var containment = BodyContainmentService.forLevel(s.level);
        ContainedBodyOwner current = containment.registeredOwnerOf(s.body).orElse(null);
        if (current == null) {
            ContainedBodyOwner restoredOwner = restoredOwner(s);
            if (restoredOwner == null || !containment.bindRestored(s.body, restoredOwner, s.originalFlags)) return false;
            current = containment.registeredOwnerOf(s.body).orElse(null);
        }
        return restoredOwnerMatches(s, current)
                && containment.export(s.body, current).filter(s.originalFlags::equals).isPresent();
    }

    private static ContainedBodyOwner restoredOwner(Snapshot s) {
        if (s.owner instanceof PortableBodyCarrierOwner owner)
            return new PortableBodyCarrierOwner(owner.carrier(), s.reference, s.locationToken);
        if (s.owner instanceof PortableWorldStorageOwner owner)
            return new PortableWorldStorageOwner(s.level, s.rootId, s.locationToken, s.reference);
        return null;
    }

    private static boolean restoredOwnerMatches(Snapshot s, ContainedBodyOwner current) {
        if (s.owner instanceof PortableBodyCarrierOwner original)
            return current instanceof PortableBodyCarrierOwner restored
                    && restored.carrier() == original.carrier() && restored.reference().equals(s.reference)
                    && restored.handToken().equals(s.locationToken) && restored.isValid();
        return s.owner instanceof PortableWorldStorageOwner original
                && current instanceof PortableWorldStorageOwner restored
                && restored.rootId().equals(original.rootId()) && restored.childToken().equals(s.locationToken)
                && restored.reference().equals(s.reference) && restored.isValid();
    }

    private static boolean placementValid(Placement placement) {
        try { return finite(placement.position()) && finite(placement.velocity()) && placement.collisionValid().getAsBoolean(); }
        catch (RuntimeException invalid) { return false; }
    }

    private static boolean exactLoadedBody(Snapshot s) {
        return s.body.level() == s.level && !s.body.isRemoved() && s.body.isAddedToLevel()
                && s.level.getEntity(s.body.getUUID()) == s.body;
    }
    private static boolean flagsRestored(LivingEntity body, BodyContainmentService.ContainedBodyFlags flags) {
        return flags != null && body.isInvisible() == flags.invisible() && body.noPhysics == flags.noPhysics()
                && body.isNoGravity() == flags.noGravity();
    }
    private static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
}
